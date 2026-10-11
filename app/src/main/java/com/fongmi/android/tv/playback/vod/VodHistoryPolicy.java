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

import java.util.List;

public class VodHistoryPolicy {

    public record Resolution(History history, VodResumeMatcher.Match match) {
        public boolean needsConfirmation() { return match != null && (!match.confirmed() || match.episode() == null); }
    }

    public Resolution resolve(String key, String mark, Vod item, History snapshot) {
        item.getFlags().forEach(Flag::indexEpisodes);
        History history = VodResumeStore.read(History.find(key));
        history = history == null ? create(key, item) : history;
        List<History> candidates = VodResumeStore.candidates(VodConfig.getCid());
        if (snapshot != null && snapshot.getCid() == VodConfig.getCid() && snapshot.canSave()) candidates.add(snapshot);
        VodResumeMatcher.Match match = VodResumeMatcher.latest(key, item, candidates);
        if (Setting.isIncognito()) history.delete();
        history.setVodName(item.getName());
        history.setResumeYear(item.getYear());
        history.setResumeType(item.getTypeName());
        if (!TextUtils.isEmpty(mark)) {
            // An explicit episode launch is not a request to reuse a different episode's minutes.
            if (!VodResumeMatcher.sameEpisode(Episode.create(mark, ""), history.getEpisode())) clearProgress(history);
            history.setVodRemarks(mark);
            history.setEpisodeIndex(-1);
            restoreSelection(history, item.getFlags());
            match = null;
        } else if (match == null) {
            if (!restoreSelection(history, item.getFlags())) startOver(history, item.getFlags());
        } else if (match.confirmed() && match.episode() != null) applyResume(history, match);
        return new Resolution(history, match);
    }

    private boolean restoreSelection(History history, List<Flag> flags) {
        // A history row may have a selected line/episode but no valid duration yet.
        // Its selection is still useful even when it is not a resumable checkpoint.
        for (Flag flag : flags) {
            if (!flag.getFlag().equals(history.getVodFlag())) continue;
            Episode episode = VodResumeMatcher.findEpisode(flag, history);
            if (episode == null) continue;
            history.setVodRemarks(episode.getName());
            history.setEpisodeUrl(episode.getUrl());
            history.setEpisodeIndex(episode.getIndex());
            return true;
        }
        return false;
    }

    private History create(String key, Vod item) {
        History history = new History();
        history.setKey(key);
        history.setCid(VodConfig.getCid());
        history.setVodName(item.getName());
        if (!item.getFlags().isEmpty()) {
            Flag first = item.getFlags().get(0);
            history.setVodFlag(first.getFlag());
            if (!first.getEpisodes().isEmpty()) {
                Episode episode = first.getEpisodes().get(0);
                history.setVodRemarks(episode.getName());
                history.setEpisodeIndex(episode.getIndex());
            }
        }
        return history;
    }

    public void applyResume(History history, VodResumeMatcher.Match match) {
        History source = match.source();
        history.setVodFlag(match.flag().getFlag());
        history.setVodRemarks(match.episode().getName());
        history.setEpisodeUrl(match.episode().getUrl());
        history.setEpisodeIndex(match.episode().getIndex());
        history.setPosition(source.getPosition());
        // The new media's duration is unknown until it is prepared.
        history.setDuration(source.getKey().equals(history.getKey()) ? source.getDuration() : C.TIME_UNSET);
        history.setProgressTime(source.getProgressTime());
        history.setOpening(source.getOpening());
        history.setEnding(source.getEnding());
    }

    public void startOver(History history, List<Flag> flags) {
        clearProgress(history);
        if (flags.isEmpty()) return;
        history.setVodFlag(flags.get(0).getFlag());
        if (!flags.get(0).getEpisodes().isEmpty()) {
            history.setVodRemarks(flags.get(0).getEpisodes().get(0).getName());
            history.setEpisodeUrl(flags.get(0).getEpisodes().get(0).getUrl());
            history.setEpisodeIndex(flags.get(0).getEpisodes().get(0).getIndex());
        }
    }

    private void clearProgress(History history) {
        history.setPosition(C.TIME_UNSET);
        history.setDuration(C.TIME_UNSET);
        history.setProgressTime(0);
    }

    public void save(History history) {
        save(history, false);
    }

    public void save(History history, boolean exit) {
        if (history == null || !history.canSave() || Setting.isIncognito()) return;
        History copy = copyForSave(history);
        VodResumeStore.remember(copy);
        Task.executeSerial(() -> {
            VodResumeStore.persist(copy, true);
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
        saveTouch(history, exit, time);
    }

    /** Persist a visit timestamp without discarding an existing resume point. */
    private void saveTouch(History history, boolean exit, long time) {
        if (history == null || Setting.isIncognito()) return;
        history.setProgressTime(history.getProgressTime());
        history.setCreateTime(time);
        History copy = copyForSave(history);
        // Make a same-episode line switch visible to an immediate re-entry,
        // without waiting for the serial database writer. Invalid checkpoints
        // are deliberately rejected by remember().
        VodResumeStore.remember(copy);
        Task.executeSerial(() -> {
            VodResumeStore.persist(copy, false);
            if (exit) RefreshEvent.history();
        });
    }

    public void saveCurrent(History history) {
        if (history == null || Setting.isIncognito()) return;
        if (history.canSave()) save(history);
        else saveTouch(history, false, history.getCreateTime());
    }

    /** Record that a VOD playback session actually started. */
    public void saveStarted(History history, long time) {
        saveTouch(history, false, time);
    }

    public void updateEpisode(History history, Flag flag, Episode episode) {
        if (history == null || flag == null || episode == null) return;
        boolean match = VodResumeMatcher.sameEpisode(history, episode);
        if (!match) clearProgress(history);
        history.setVodFlag(flag.getFlag());
        history.setVodRemarks(episode.getName());
        history.setEpisodeUrl(episode.getUrl());
        history.setEpisodeIndex(episode.getIndex());
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
        history.setProgressTime(time);
    }

    private History copyForSave(History history) {
        history.markSaveScheduled();
        return history.copy();
    }

    public long startPositionMs(History history) {
        return history == null ? C.TIME_UNSET : Math.max(history.getOpening(), history.getPosition());
    }
}
