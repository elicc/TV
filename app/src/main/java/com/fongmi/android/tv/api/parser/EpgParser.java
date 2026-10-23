package com.fongmi.android.tv.api.parser;

import android.util.Xml;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Tv;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Formatters;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.utils.Path;
import com.orhanobut.logger.Logger;

import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class EpgParser {

    private static final String TAG = EpgParser.class.getSimpleName();

    private static OffsetDateTime parseFull(String source, ZoneId zoneId) {
        OffsetDateTime fast = EpgTime.parse(source);
        if (fast != null) return fast;
        String s = source.trim();
        try {
            String time = s.length() > 14 ? s.substring(0, 14) : s;
            String offset = s.length() > 14 ? s.substring(14).trim() : "";
            if (!offset.isEmpty()) return parseOffset(time + " " + offset);
            return LocalDateTime.parse(time, Formatters.EPG_FULL_NO_TZ).atZone(zoneId).toOffsetDateTime();
        } catch (Exception e) {
            Logger.t(TAG).e(e, "parseFull failed: " + s);
            return OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);
        }
    }

    private static OffsetDateTime parseOffset(String source) {
        try {
            return OffsetDateTime.parse(source, Formatters.EPG_FULL);
        } catch (Exception ignored) {
            return OffsetDateTime.parse(source, Formatters.EPG_FULL_COLON);
        }
    }

    /** Synchronized: the live config and the live screen both ask for the same file when it is opened. */
    public static synchronized void start(Live live, String url) throws Exception {
        long t0 = System.currentTimeMillis();
        File file = Path.epg(UrlUtil.path(url));
        String reason = refreshReason(file);
        boolean refresh = reason != null;
        if (!refresh && live.isEpgBound(url, file.lastModified())) {
            Logger.t(TAG).i("start url=" + url + " skipped, already bound");
            return;
        }
        Logger.t(TAG).i("start url=" + url + " file=" + file.getName() + " refresh=" + refresh + (refresh ? " reason=" + reason : ""));
        if (refresh) Download.create(url, file).get();
        boolean gzip = isGzip(file);
        if (gzip) readGzip(live, file, refresh);
        else readXml(live, file);
        live.setEpgBound(url, file.lastModified());
        Logger.t(TAG).i("start done elapsed=" + (System.currentTimeMillis() - t0) + "ms");
    }

    public static Epg getEpg(String xml, String key, ZoneId zoneId) {
        try {
            Tv tv = new Persister().read(Tv.class, xml, false);
            String rawDate = tv.getDate();
            String date = rawDate.isEmpty() ? LocalDate.now(zoneId).format(Formatters.DATE) : parseFull(rawDate, zoneId).atZoneSameInstant(zoneId).format(Formatters.DATE);
            Epg epg = Epg.create(key, date);
            tv.getProgramme().forEach(programme -> epg.getList().add(getEpgData(programme, zoneId)));
            return epg;
        } catch (Exception e) {
            Logger.t(TAG).e(e, "getEpg parse failed key=" + key);
            return Epg.error(key);
        }
    }

    private static String refreshReason(File file) {
        if (!Path.exists(file)) return "file-missing";
        if (!isToday(file.lastModified())) return "not-today";
        if (System.currentTimeMillis() - file.lastModified() > TimeUnit.HOURS.toMillis(6)) return "older-than-6h";
        return null;
    }

    private static boolean isGzip(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return (fis.read() | (fis.read() << 8)) == 0x8B1F;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isToday(long millis) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).equals(LocalDate.now());
    }

    private static void readGzip(Live live, File file, boolean refresh) throws Exception {
        File xml = Path.epg(file.getName() + ".xml");
        boolean needsDecompression = refresh || !Path.exists(xml);
        if (needsDecompression && !FileUtil.gzipDecompress(file, xml)) Logger.t(TAG).w("gzip decompress failed file=" + file);
        readXml(live, xml);
    }

    private static void readXml(Live live, File file) throws Exception {
        ZoneId zoneId = live.getZoneId();
        Map<String, Channel> liveChannelMap = prepareLiveChannels(live);
        long t0 = System.currentTimeMillis();
        EpgXml xml = parseXmlData(file);
        long t1 = System.currentTimeMillis();
        ProgrammeResult result = processProgramme(xml, liveChannelMap, zoneId);
        long t2 = System.currentTimeMillis();
        bindResultsToLive(live, result);
        Logger.t(TAG).i("readXml programmes=" + xml.programmes.size() + " read=" + (t1 - t0) + "ms process=" + (t2 - t1) + "ms");
    }

    private static Map<String, Channel> prepareLiveChannels(Live live) {
        Map<String, Channel> map = new HashMap<>();
        live.getGroups().stream()
                .flatMap(group -> group.getChannel().stream())
                .forEach(channel -> {
                    if (!channel.getTvgId().isEmpty()) map.putIfAbsent(channel.getTvgId(), channel);
                    if (!channel.getTvgName().isEmpty()) map.putIfAbsent(channel.getTvgName(), channel);
                    if (!channel.getName().isEmpty()) map.putIfAbsent(channel.getName(), channel);
                });
        return map;
    }

    private static EpgXml parseXmlData(File file) throws Exception {
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file), 64 * 1024)) {
            return EpgXml.parse(Xml.newPullParser(), input);
        }
    }

    private static ProgrammeResult processProgramme(EpgXml data, Map<String, Channel> liveChannelMap, ZoneId zoneId) {
        Map<String, Map<String, Epg>> epgMap = new HashMap<>();
        Map<String, String> srcMap = new HashMap<>();
        Map<String, Channel> channelCache = new HashMap<>();
        Set<String> channelMiss = new HashSet<>();
        int skipped = 0;
        for (EpgXml.Programme programme : data.programmes) {
            String xmlChannelId = programme.channel;
            Channel targetChannel;
            if (channelCache.containsKey(xmlChannelId)) {
                targetChannel = channelCache.get(xmlChannelId);
            } else if (channelMiss.contains(xmlChannelId)) {
                targetChannel = null;
            } else {
                targetChannel = findTargetChannel(xmlChannelId, liveChannelMap, data.channels);
                if (targetChannel != null) channelCache.put(xmlChannelId, targetChannel);
                else channelMiss.add(xmlChannelId);
            }
            if (targetChannel == null) {
                skipped++;
                continue;
            }
            OffsetDateTime startDate = parseFull(programme.start, zoneId);
            OffsetDateTime endDate = parseFull(programme.stop, zoneId);
            String liveTvgId = targetChannel.getTvgId();
            String programmeDate = EpgTime.date(startDate, zoneId);
            epgMap.computeIfAbsent(liveTvgId, k -> new HashMap<>())
                    .computeIfAbsent(programmeDate, d -> Epg.create(liveTvgId, d))
                    .getList().add(getEpgData(startDate, endDate, zoneId, programme.title));
            if (!srcMap.containsKey(liveTvgId)) {
                List<EpgXml.Channel> xmlChannels = data.channels.get(xmlChannelId);
                if (xmlChannels != null) {
                    for (EpgXml.Channel ch : xmlChannels) {
                        if (!ch.src.isEmpty()) {
                            srcMap.put(liveTvgId, ch.src);
                            break;
                        }
                    }
                }
            }
        }
        Logger.t(TAG).i("processProgramme skipped(no match)=" + skipped + " matched channels=" + epgMap.size());
        return new ProgrammeResult(epgMap, srcMap);
    }

    private static Channel findTargetChannel(String xmlChannelId, Map<String, Channel> liveChannelMap, Map<String, List<EpgXml.Channel>> xmlChannelIdMap) {
        Channel targetChannel = liveChannelMap.get(xmlChannelId);
        if (targetChannel != null) return targetChannel;
        List<EpgXml.Channel> channels = xmlChannelIdMap.get(xmlChannelId);
        if (channels == null) return null;
        return channels.stream().flatMap(xmlChannel -> xmlChannel.names.stream()).filter(liveChannelMap::containsKey).findFirst().map(liveChannelMap::get).orElse(null);
    }

    private static void bindResultsToLive(Live live, ProgrammeResult result) {
        int[] counts = {0, 0};
        live.getGroups().stream()
                .flatMap(group -> group.getChannel().stream())
                .forEach(channel -> {
                    String tvgId = channel.getTvgId();
                    Map<String, Epg> dateMap = result.epgMap.get(tvgId);
                    if (dateMap != null) {
                        channel.setDataList(new ArrayList<>(dateMap.values()));
                        counts[0]++;
                    } else {
                        counts[1]++;
                    }
                    if (channel.getLogo().isEmpty()) {
                        String src = result.srcMap.get(tvgId);
                        if (src != null) channel.setLogo(src);
                    }
                });
        Logger.t(TAG).i("bindResultsToLive with-epg=" + counts[0] + " without-epg=" + counts[1]);
    }

    private static EpgData getEpgData(Tv.Programme programme, ZoneId zoneId) {
        OffsetDateTime startDate = parseFull(programme.getStart(), zoneId);
        OffsetDateTime endDate = parseFull(programme.getStop(), zoneId);
        return getEpgData(startDate, endDate, zoneId, programme.getTitle());
    }

    private static EpgData getEpgData(OffsetDateTime startDate, OffsetDateTime endDate, ZoneId zoneId, String title) {
        try {
            EpgData epgData = new EpgData();
            epgData.setTitle(title);
            epgData.setStart(EpgTime.time(startDate, zoneId));
            epgData.setEnd(EpgTime.time(endDate, zoneId));
            epgData.setStartTime(startDate.toInstant().toEpochMilli());
            epgData.setEndTime(endDate.toInstant().toEpochMilli());
            epgData.trans();
            return epgData;
        } catch (Exception e) {
            return new EpgData();
        }
    }

    private static class ProgrammeResult {

        Map<String, Map<String, Epg>> epgMap;
        Map<String, String> srcMap;

        public ProgrammeResult(Map<String, Map<String, Epg>> epgMap, Map<String, String> srcMap) {
            this.epgMap = epgMap;
            this.srcMap = srcMap;
        }
    }
}
