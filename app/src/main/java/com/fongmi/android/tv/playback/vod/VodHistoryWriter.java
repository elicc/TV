package com.fongmi.android.tv.playback.vod;

import android.os.SystemClock;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Task;
import com.orhanobut.logger.Logger;

import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

/** One serial drain, bounded snapshots, and a shared cooldown instead of an unbounded retry per progress tick. */
final class VodHistoryWriter {

    private static final int MAX_PENDING = 128;
    private static final int MAX_ATTEMPTS = 3;
    // Use the store's monitor so forget/move and an in-flight commit cannot resurrect a deleted record.
    private static final LinkedHashMap<String, Write> writes = new LinkedHashMap<>();
    private static boolean scheduled;
    private static long blockedUntil;

    private VodHistoryWriter() {}

    static void enqueue(History history, boolean progress, boolean exit) {
        synchronized (VodResumeStore.class) {
            Write write = writes.remove(history.getKey());
            if (write == null) write = new Write();
            writes.put(history.getKey(), write);
            if (progress) {
                if (write.progress == null || !VodResumeStore.isNewer(write.progress, history)) write.progress = history;
            } else if (write.visit == null || history.getCreateTime() >= write.visit.getCreateTime()) {
                write.visit = history;
            }
            write.exit |= exit;
            while (writes.size() > MAX_PENDING) writes.remove(writes.keySet().iterator().next());
            schedule();
        }
    }

    static void forget(String key) {
        synchronized (VodResumeStore.class) {
            writes.remove(key);
        }
    }

    static void forgetPending(int cid) {
        synchronized (VodResumeStore.class) {
            writes.values().removeIf(write -> cid == -1 || (write.progress != null ? write.progress : write.visit).getCid() == cid);
        }
    }

    private static void schedule() {
        if (scheduled || writes.isEmpty()) return;
        scheduled = true;
        long delay = blockedUntil - SystemClock.elapsedRealtime();
        if (delay > 0) Task.schedule(() -> Task.executeSerial(VodHistoryWriter::drain), delay, TimeUnit.MILLISECONDS);
        else Task.executeSerial(VodHistoryWriter::drain);
    }

    private static void drain() {
        boolean refresh = false;
        synchronized (VodResumeStore.class) {
            scheduled = false;
            if (Setting.isIncognito()) VodResumeStore.forgetPending(-1);
            if (writes.isEmpty()) return;
            if (SystemClock.elapsedRealtime() < blockedUntil) {
                schedule();
                return;
            }
            String key = writes.keySet().iterator().next();
            Write write = writes.get(key);
            try {
                // A later visit must not erase an earlier, not-yet-persisted checkpoint.
                if (write.progress != null) {
                    VodResumeStore.persist(write.progress, true);
                    write.progress = null;
                }
                if (write.visit != null) VodResumeStore.persist(write.visit, false);
                writes.remove(key);
                refresh = write.exit;
            } catch (RuntimeException e) {
                // Public only since API 33, but Android 11 also throws this hidden framework class.
                // Match its name without linking an API-33-only type on older TVs; do not swallow other failures.
                if (!"android.database.CursorWindowAllocationException".equals(e.getClass().getName())) {
                    writes.remove(key);
                    throw e;
                }
                int attempts = ++write.attempts;
                if (attempts >= MAX_ATTEMPTS) writes.remove(key);
                blockedUntil = SystemClock.elapsedRealtime() + (attempts >= MAX_ATTEMPTS ? 30_000 : 1_000L << attempts);
                // Keep the store's pending checkpoint visible; do not claim the disk write succeeded.
                Logger.t("VodHistoryWriter").w("History write deferred: cursor allocation failed, attempt=%d", attempts);
            } finally {
                schedule();
            }
        }
        if (refresh) RefreshEvent.history();
    }

    private static final class Write {
        History progress;
        History visit;
        boolean exit;
        int attempts;
    }
}
