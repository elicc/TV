package com.fongmi.android.tv.playback.vod;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.db.dao.HistoryDao;
import com.github.catvod.utils.Prefers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Keeps pending progress visible to the next detail page without waiting on disk IO. */
public final class VodResumeStore {
    private static final LinkedHashMap<String, History> pending = new LinkedHashMap<>();
    private static final String PREFIX = "vod_resume_v1_";

    private VodResumeStore() {}

    static History read(History history) {
        if (history == null) return null;
        try {
            Info info = App.gson().fromJson(Prefers.getString(PREFIX + history.getKey()), Info.class);
            if (info != null && Objects.equals(info.name, history.getVodName())) {
                history.setResumeYear(info.year);
                history.setResumeType(info.type);
                // An imported/replaced row must not inherit a stale timestamp.
                if (info.writtenAt == history.getCreateTime() && info.position == history.getPosition() && info.duration == history.getDuration()
                        && Objects.equals(info.episode, history.getVodRemarks())) {
                    history.setProgressTime(info.time);
                    if (info.episodeIndex != null && Objects.equals(info.flag, history.getVodFlag())
                            && Objects.equals(info.url, history.getEpisodeUrl())) history.setEpisodeIndex(info.episodeIndex);
                }
            }
        } catch (Exception ignored) {
            // Missing/corrupt auxiliary metadata leaves the original history usable.
        }
        return history;
    }

    static synchronized void remember(History history) {
        if (!history.canSave()) return;
        History previous = pending.get(history.getKey());
        if (previous != null && isNewer(previous, history)) return;
        pending.remove(history.getKey());
        pending.put(history.getKey(), history.copy());
        while (pending.size() > 128) pending.remove(pending.keySet().iterator().next());
    }

    public static synchronized void forget(String key) {
        pending.remove(key);
        Prefers.remove(PREFIX + key);
    }

    static synchronized List<History> candidates(int cid) {
        LinkedHashMap<String, History> items = new LinkedHashMap<>();
        for (History history : dao().findResumable(cid)) items.put(history.getKey(), read(history));
        for (History history : pending.values()) {
            if (history.getCid() != cid) continue;
            History stored = items.get(history.getKey());
            if (stored == null || !isNewer(stored, history)) items.put(history.getKey(), history.copy());
        }
        return new ArrayList<>(items.values());
    }

    /** Called on the serial writer. A visit never replaces the stored episode or position. */
    static void persist(History incoming, boolean progress) {
        History stored = read(dao().find(incoming.getCid(), incoming.getKey()));
        History target = incoming;
        if (stored != null && (!progress || !incoming.canSave() || isNewer(stored, incoming))) {
            // A successfully opened line can change before the player reports a
            // new duration/progress sample. Keep the checkpoint, but remember the
            // new route only for that same episode and a non-stale visit.
            if (!progress && incoming.getCreateTime() >= stored.getCreateTime()
                    && Objects.equals(stored.getVodName(), incoming.getVodName())
                    && VodResumeMatcher.sameEpisode(stored, incoming)
                    && incoming.getVodFlag() != null && !incoming.getVodFlag().isEmpty()) {
                stored.setVodFlag(incoming.getVodFlag());
                stored.setEpisodeUrl(incoming.getEpisodeUrl());
                stored.setVodRemarks(incoming.getVodRemarks());
                stored.setEpisodeIndex(incoming.getEpisodeIndex());
            }
            target = stored;
            target.setProgressTime(stored.getProgressTime());
            target.setCreateTime(Math.max(stored.getCreateTime(), incoming.getCreateTime()));
            if (Objects.equals(stored.getVodName(), incoming.getVodName())) {
                if (!incoming.getResumeYear().isEmpty()) target.setResumeYear(incoming.getResumeYear());
                if (!incoming.getResumeType().isEmpty()) target.setResumeType(incoming.getResumeType());
                if (incoming.getVodPic() != null && !incoming.getVodPic().isEmpty()) target.setVodPic(incoming.getVodPic());
            }
        }
        // Freeze the old progress timestamp before updating the visit timestamp on later writes.
        long progressTime = target.getProgressTime();
        target.save();
        Prefers.put(PREFIX + target.getKey(), App.gson().toJson(new Info(target, progressTime)));
        synchronized (VodResumeStore.class) {
            History cached = pending.get(incoming.getKey());
            if (cached != null && !isNewer(cached, incoming)) pending.remove(incoming.getKey());
        }
    }

    private static boolean isNewer(History left, History right) {
        return left.getProgressTime() > right.getProgressTime()
                || left.getProgressTime() == right.getProgressTime() && left.getCreateTime() > right.getCreateTime();
    }

    private static HistoryDao dao() { return AppDatabase.get().getHistoryDao(); }

    private static class Info {
        String name, year, type, episode, flag, url;
        Integer episodeIndex;
        long time, position, duration, writtenAt;

        Info(History history, long time) {
            name = history.getVodName();
            year = history.getResumeYear();
            type = history.getResumeType();
            episode = history.getVodRemarks();
            flag = history.getVodFlag();
            url = history.getEpisodeUrl();
            episodeIndex = history.getEpisodeIndex();
            position = history.getPosition();
            duration = history.getDuration();
            writtenAt = history.getCreateTime();
            this.time = time;
        }
    }
}
