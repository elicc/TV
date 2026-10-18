package com.fongmi.android.tv.playback.vod;

import androidx.media3.common.MediaMetadata;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.api.DanmakuCache;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.media.MediaItemFactory;
import com.fongmi.android.tv.setting.DanmakuSetting;

import java.util.function.Consumer;

public final class VodPlaybackMedia {

    public static MediaMetadata metadata(History history, Episode episode) {
        String title = history.getVodName();
        String name = episode.getName();
        if (name.equals(title)) name = "";
        return MediaItemFactory.buildMetadata(title, name, history.getVodPic(), name);
    }

    /** Starts the danmaku search together with the play URL request. */
    public static void prefetchDanmaku(String key, History history, Episode episode) {
        if (!canSearchDanmaku(key)) return;
        if (DanmakuCache.find(history.getVodName(), episode.getName()) != null) return;
        DanmakuApi.prefetch(history.getVodName(), episode.getName());
    }

    public static void searchDanmaku(Result result, History history, Episode episode, Consumer<Danmaku> set, Consumer<Danmaku> add) {
        if (!canSearchDanmaku(result.getKey())) return;
        String name = history.getVodName();
        String episodeName = episode.getName();
        Consumer<Danmaku> apply = danmaku -> {
            if (DanmakuSetting.isSpiderFirst() && !result.getDanmaku().isEmpty()) add.accept(danmaku);
            else set.accept(danmaku);
        };
        Danmaku cached = DanmakuCache.find(name, episodeName);
        if (cached == null) {
            DanmakuApi.search(name, episodeName, danmaku -> {
                DanmakuCache.track(danmaku, name, episodeName);
                apply.accept(danmaku);
            });
            return;
        }
        // A search still running for the previous episode must not replace this one.
        DanmakuApi.cancel();
        // The media item for this playback is created after this call returns.
        App.post(() -> apply.accept(cached));
    }

    private static boolean canSearchDanmaku(String key) {
        return DanmakuApi.canSearch() && VodConfig.get().getSite(key).getDanmaku() != 0;
    }
}
