package com.fongmi.android.tv.playback.vod;

import android.text.TextUtils;

import androidx.media3.common.C;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Task;

public class VodHistoryPolicy {

    public History findOrCreate(String key, String mark, Vod item) {
        History history = History.find(key);
        history = history == null ? create(key, item) : history;
        if (!TextUtils.isEmpty(mark)) history.setVodRemarks(mark);
        if (Setting.isIncognito()) history.delete();
        history.setVodName(item.getName());
        return history;
    }

    private History create(String key, Vod item) {
        History history = new History();
        history.setKey(key);
        history.setCid(VodConfig.getCid());
        history.setVodName(item.getName());
        history.findEpisode(item.getFlags());
        return history;
    }

    public void save(History history) {
        save(history, false);
    }

    public void save(History history, boolean exit) {
        if (history == null || !history.canSave() || Setting.isIncognito()) return;
        History copy = copyForSave(history);
        Task.executeSerial(() -> {
            copy.merge().save();
            if (exit) RefreshEvent.history();
        });
    }

    public void saveProgress(History history, boolean exit, long time, long position, long duration) {
        // Some VOD responses do not expose a duration immediately (for
        // example, a live HLS manifest while its first segment is loading).
        // Keep the visit in history even when a resumable progress sample is
        // not available yet. Previously this path returned without writing
        // anything, so opening and watching such a title left the home page
        // history row empty forever.
        if (position < 0 || duration <= 0) {
            saveTouch(history, exit, time);
            return;
        }
        applyProgress(history, time, position, duration);
        save(history, exit);
    }

    public void saveVisit(History history, boolean exit, long time) {
        if (history == null || Setting.isIncognito()) return;
        history.setPosition(C.TIME_UNSET);
        history.setDuration(C.TIME_UNSET);
        saveTouch(history, exit, time);
    }

    /** Persist a visit timestamp without discarding an existing resume point. */
    private void saveTouch(History history, boolean exit, long time) {
        if (history == null || Setting.isIncognito()) return;
        history.setCreateTime(time);
        History copy = copyForSave(history);
        Task.executeSerial(() -> {
            copy.save();
            if (exit) RefreshEvent.history();
        });
    }

    public void saveCurrent(History history) {
        if (history == null || Setting.isIncognito()) return;
        History copy = copyForSave(history);
        Task.executeSerial(copy::save);
    }

    /** Record that a VOD playback session actually started. */
    public void saveStarted(History history, long time) {
        saveTouch(history, false, time);
    }

    public void updateEpisode(History history, Flag flag, Episode episode) {
        if (history == null || flag == null || episode == null) return;
        boolean match = episode.matchesName(history.getEpisode());
        if (!match) history.setPosition(C.TIME_UNSET);
        if (!match) history.setDuration(C.TIME_UNSET);
        history.setVodFlag(flag.getFlag());
        history.setVodRemarks(episode.getName());
        history.setEpisodeUrl(episode.getUrl());
    }

    public void updateProgress(History history, long time, long position, long duration) {
        applyProgress(history, time, position, duration);
        if (history != null && history.canSave() && history.canScheduleSave()) saveCurrent(history);
    }

    private void applyProgress(History history, long time, long position, long duration) {
        if (history == null || position < 0 || duration <= 0) return;
        history.setCreateTime(time);
        history.setPosition(position);
        history.setDuration(duration);
    }

    private History copyForSave(History history) {
        history.markSaveScheduled();
        return history.copy();
    }

    public long startPositionMs(History history) {
        return history == null ? C.TIME_UNSET : Math.max(history.getOpening(), history.getPosition());
    }
}
