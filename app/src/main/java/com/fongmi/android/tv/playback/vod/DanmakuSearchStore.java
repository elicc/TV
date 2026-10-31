package com.fongmi.android.tv.playback.vod;

import android.text.TextUtils;

import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Prefers;

/** Persists the viewer's preferred danmaku search title for one VOD work. */
public final class DanmakuSearchStore {

    public static final String EXTRA_SERIES_KEY = "danmaku_series_key";
    public static final String EXTRA_EPISODE_NUMBER = "danmaku_episode_number";
    public static final String EXTRA_EPISODE_INDEX = "danmaku_episode_index";
    private static final String PREFIX = "danmaku_search_v1_";

    private DanmakuSearchStore() {
    }

    public static String getTitle(String seriesKey) {
        if (TextUtils.isEmpty(seriesKey) || Setting.isIncognito()) return "";
        return Prefers.getString(key(seriesKey), "").trim();
    }

    public static void putTitle(String seriesKey, String title) {
        if (TextUtils.isEmpty(seriesKey) || Setting.isIncognito()) return;
        String value = title == null ? "" : title.trim();
        if (value.isEmpty()) Prefers.remove(key(seriesKey));
        else Prefers.put(key(seriesKey), value);
    }

    public static void clearTitle(String seriesKey) {
        if (TextUtils.isEmpty(seriesKey) || Setting.isIncognito()) return;
        Prefers.remove(key(seriesKey));
    }

    private static String key(String seriesKey) {
        return PREFIX + Crypto.md5(seriesKey);
    }
}
