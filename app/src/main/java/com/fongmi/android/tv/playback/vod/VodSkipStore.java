package com.fongmi.android.tv.playback.vod;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.metadata.MetadataMatcher;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.utils.Prefers;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opening/ending skips belong to a work, not to one provider's copy of it. History rows are
 * keyed by source, so a value stored there only followed the viewer when a resume match was
 * accepted. This store is keyed by config and normalized title and wins over the row values.
 */
public final class VodSkipStore {
    private static final String PREFIX = "vod_skip_v1_";
    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}");

    private VodSkipStore() {}

    /** Applies the work's skips to {@code history}; returns false when none are stored. */
    static boolean apply(History history, boolean series) {
        Entry entry = read(history);
        if (entry == null || !sameWork(entry, history, series)) return false;
        history.setOpening(entry.opening);
        history.setEnding(entry.ending);
        return true;
    }

    static boolean has(History history) {
        return read(history) != null;
    }

    static void save(History history, boolean series) {
        String key = key(history);
        if (key == null || Setting.isIncognito()) return;
        // Zero is kept on purpose: a cleared skip must not fall back to another source's row.
        Entry entry = new Entry();
        entry.opening = Math.max(0, history.getOpening());
        entry.ending = Math.max(0, history.getEnding());
        entry.year = history.getResumeYear();
        entry.type = history.getResumeType();
        entry.series = series;
        Prefers.put(key, App.gson().toJson(entry));
    }

    public static void clear(int cid) {
        remove(PREFIX + cid + "_");
    }

    public static void clearAll() {
        remove(PREFIX);
    }

    private static void remove(String prefix) {
        for (String key : new ArrayList<>(Prefers.getPrefers().getAll().keySet())) {
            if (key.startsWith(prefix)) Prefers.remove(key);
        }
    }

    private static Entry read(History history) {
        String key = key(history);
        if (key == null) return null;
        try {
            return App.gson().fromJson(Prefers.getString(key), Entry.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String key(History history) {
        if (history == null) return null;
        String title = MetadataMatcher.normalize(MetadataMatcher.queryTitle(history.getVodName()));
        return title.isEmpty() ? null : PREFIX + history.getCid() + "_" + title;
    }

    /** The title already carries season and edition; type and year only split same-named works. */
    private static boolean sameWork(Entry entry, History history, boolean series) {
        int left = MetadataMatcher.mediaKind(entry.type);
        int right = MetadataMatcher.mediaKind(history.getResumeType());
        if (left != 0 && right != 0 && left != right) return false;
        // Series providers report either the premiere or the latest update year.
        if (entry.series || series) return true;
        String a = year(entry.year);
        String b = year(history.getResumeYear());
        return a.isEmpty() || b.isEmpty() || Math.abs(Integer.parseInt(a) - Integer.parseInt(b)) <= 1;
    }

    private static String year(String value) {
        if (TextUtils.isEmpty(value)) return "";
        Matcher matcher = YEAR.matcher(value);
        return matcher.find() ? matcher.group() : "";
    }

    private static class Entry {
        long opening, ending;
        String year, type;
        boolean series;
    }
}
