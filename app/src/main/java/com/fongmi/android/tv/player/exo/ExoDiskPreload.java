package com.fongmi.android.tv.player.exo;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.common.Timeline;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.preload.DiskPreloadManager;

import com.fongmi.android.tv.setting.PreloadSetting;

/**
 * Disk preload for the current item. Progressive HTTP files use {@link ExoReadAhead}, which downloads ahead of the
 * player with parallel range requests and keeps working while the player itself is loading. Other formats (HLS, DASH)
 * keep using {@link DiskPreloadManager}, which only runs while the player is idle.
 */
public class ExoDiskPreload implements Player.Listener {

    private final PriorityTaskManager priorityTaskManager;
    private DiskPreloadManager manager;
    private ExoReadAhead readAhead;
    private MediaItem mediaItem;
    private ExoPlayer player;

    public ExoDiskPreload() {
        this.priorityTaskManager = new PriorityTaskManager();
    }

    public void start(ExoPlayer player, MediaItem mediaItem) {
        if (this.player != player) {
            if (this.player != null) this.player.removeListener(this);
            player.addListener(this);
        }
        this.mediaItem = mediaItem;
        this.player = player;
        restart();
    }

    public void stop() {
        stopManager();
        stopReadAhead();
        if (player != null) player.removeListener(this);
        player = null;
        mediaItem = null;
    }

    public void release() {
        stop();
    }

    @Override
    public void onTimelineChanged(@NonNull Timeline timeline, int reason) {
        if (readAhead != null && player != null) readAhead.setDurationMs(player.getDuration());
    }

    private void restart() {
        stopManager();
        if (player == null || mediaItem == null || !PreloadSetting.isEnabled()) {
            stopReadAhead();
        } else if (ExoReadAhead.isSupported(mediaItem)) {
            startReadAhead();
        } else {
            stopReadAhead();
            startManager();
        }
    }

    private void startReadAhead() {
        if (readAhead != null && readAhead.isFor(mediaItem)) return;
        stopReadAhead();
        readAhead = ExoReadAhead.start(mediaItem, ExoUtil.extractHeaders(mediaItem));
    }

    private void stopReadAhead() {
        if (readAhead == null) return;
        readAhead.release();
        readAhead = null;
    }

    private void startManager() {
        manager = createManager(mediaItem);
        player.setPriorityTaskManager(priorityTaskManager);
        manager.start(player, mediaItem, createOptions());
    }

    private void stopManager() {
        if (manager == null) return;
        manager.release();
        manager = null;
        if (player != null) player.setPriorityTaskManager(null);
    }

    private DiskPreloadManager createManager(MediaItem mediaItem) {
        return new DiskPreloadManager.Builder(ExoMediaSourceFactory.getCache(), ExoMediaSourceFactory.createUpstreamDataSourceFactory(ExoUtil.extractHeaders(mediaItem)), ExoUtil.buildRenderersFactory()).setPriorityTaskManager(priorityTaskManager).build();
    }

    private DiskPreloadManager.Options createOptions() {
        return DiskPreloadManager.Options.builder().setDurationMs(PreloadSetting.getDurationMs()).setMaxThreads(PreloadSetting.getThreads()).build();
    }
}
