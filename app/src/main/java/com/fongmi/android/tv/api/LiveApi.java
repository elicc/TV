package com.fongmi.android.tv.api;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.parser.EpgParser;
import com.fongmi.android.tv.api.parser.LiveParser;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.utils.Formatters;
import com.github.catvod.net.OkHttp;

import java.time.LocalDate;
import java.time.ZoneId;

public class LiveApi {

    public static void parse(@NonNull Live item) throws Exception {
        LiveParser.start(item.recent());
        item.getGroups().removeIf(Group::isEmpty);
        if (item.getGroups().isEmpty() || item.getGroups().get(0).isKeep()) return;
        item.getGroups().add(0, Group.create(R.string.keep));
        LiveConfig.get().applyKeepsToGroups(item.getGroups());
    }

    public static boolean parseXml(@NonNull Live item) {
        return item.getEpgXml().stream().map(url -> startXml(item, url)).reduce(false, Boolean::logicalOr);
    }

    public static Epg getCachedEpg(@NonNull Channel item, @NonNull ZoneId zoneId) {
        String today = LocalDate.now(zoneId).format(Formatters.DATE);
        Epg result = item.getDataList().stream().filter(epg -> epg.equal(today) && !epg.isError()).findFirst().orElse(null);
        if (result == null) result = EpgCache.read(item, today);
        if (result == null) return null;
        result.setKey(item.getTvgId());
        if (result.getFetchedAt() == 0) EpgCache.write(item, result);
        item.setData(result);
        return result.selected();
    }

    public static boolean isEpgFresh(Epg epg) {
        return EpgCache.isFresh(epg);
    }

    public static void cancelEpg(String tag) {
        if (tag != null && !tag.isEmpty()) OkHttp.cancel(tag);
    }

    @NonNull
    public static Epg getEpg(@NonNull Channel item, @NonNull ZoneId zoneId) {
        return getEpg(item, zoneId, null);
    }

    @NonNull
    public static Epg getEpg(@NonNull Channel item, @NonNull ZoneId zoneId, String tag) {
        String today = LocalDate.now(zoneId).format(Formatters.DATE);
        Epg result = fetchEpgDay(item, zoneId, today, true, tag);
        if (result == null) result = Epg.create(item.getTvgId(), today);
        result.setKey(item.getTvgId());
        if (!result.isError()) {
            item.setData(result);
            EpgCache.write(item, result);
        }
        return result.selected();
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item) throws Exception {
        Source.get().stop();
        Result result = item.result();
        result.setUrl(Source.get().fetch(result));
        return result;
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item, @NonNull EpgData data) throws Exception {
        Result result = getUrl(item);
        result.setUrl(item.getCatchup().format(result.getRealUrl(), data));
        if (item.isRtsp()) result.getHeader().put("rtsp_range", data.getRange());
        return result;
    }

    private static boolean startXml(Live item, String url) {
        try {
            EpgParser.start(item, url);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static Epg fetchEpgDay(@NonNull Channel item, @NonNull ZoneId zoneId, @NonNull String date, boolean force, String tag) {
        String url = item.getEpg().replace("{date}", date);
        Epg existing = item.getDataList().stream().filter(epg -> epg.equal(date) && !epg.isError()).findFirst().orElse(null);
        if (!force && existing != null) return existing;
        if (!url.startsWith("http")) return existing;
        String body = tag == null ? OkHttp.string(url) : OkHttp.string(url, tag);
        Epg epg = Epg.objectFrom(body, item.getTvgId(), zoneId);
        if (epg.isError()) {
            epg.setDate(date);
            return epg;
        }
        epg.setKey(item.getTvgId());
        if (!date.equals(epg.getDate())) epg.setDate(date);
        item.setData(epg);
        return epg;
    }
}
