package com.fongmi.android.tv.api;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.github.catvod.utils.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Persistent, date-scoped EPG cache shared by activities and playback sessions. */
final class EpgCache {

    private static final long FRESHNESS = TimeUnit.MINUTES.toMillis(15);

    private EpgCache() {
    }

    static Epg read(Channel channel, String date) {
        File file = file(channel, date);
        if (!file.exists() || file.length() == 0) return null;
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Entry entry = App.gson().fromJson(reader, Entry.class);
            if (entry == null || entry.epg == null || !date.equals(entry.epg.getDate())) return null;
            entry.epg.setKey(channel.getTvgId());
            entry.epg.setFetchedAt(entry.savedAt);
            entry.epg.setError(false);
            return entry.epg;
        } catch (Exception ignored) {
            return null;
        }
    }

    static void write(Channel channel, Epg epg) {
        if (epg == null || epg.isError() || epg.getDate().isEmpty()) return;
        File target = file(channel, epg.getDate());
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            if (!temp.getParentFile().exists() && !temp.getParentFile().mkdirs()) return;
            Entry entry = new Entry();
            entry.savedAt = System.currentTimeMillis();
            entry.epg = epg;
            epg.setFetchedAt(entry.savedAt);
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8)) {
                App.gson().toJson(entry, writer);
            }
            if (!temp.renameTo(target)) {
                // Filesystems used by some TV devices do not replace atomically.
                try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8)) {
                    App.gson().toJson(entry, writer);
                }
                temp.delete();
            }
        } catch (Exception ignored) {
            temp.delete();
        }
    }

    static boolean isFresh(Epg epg) {
        return epg != null && epg.getFetchedAt() > 0 && System.currentTimeMillis() - epg.getFetchedAt() < FRESHNESS;
    }

    private static File file(Channel channel, String date) {
        String source = channel.getTvgId() + "\n" + channel.getEpg() + "\n" + date;
        return Path.files("epg/" + digest(source) + ".json");
    }

    private static String digest(String source) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value));
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(source.hashCode());
        }
    }

    private static class Entry {

        private long savedAt;
        private Epg epg;
    }
}
