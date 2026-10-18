package com.fongmi.android.tv.player.exo;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheEvictor;
import androidx.media3.datasource.cache.CacheSpan;

import com.fongmi.android.tv.setting.PreloadSetting;

import java.io.File;
import java.util.ArrayList;
import java.util.TreeSet;

/**
 * Oldest-first eviction. Unlike {@code LeastRecentlyUsedCacheEvictor}, the limit follows the current preload size
 * setting and free storage instead of being fixed at creation, and spans inside an active {@link ExoReadAhead} window are
 * evicted last, so data downloaded ahead of the player survives while the bytes already played go first.
 * <p>
 * Spans are ordered by when they were written, not when they were last read. Touch tracking makes SimpleCache update its
 * index database on every span read, under the cache lock, which caps the read rate of a high-bitrate stream on slow
 * TV storage.
 */
final class ExoCacheEvictor implements CacheEvictor {

    private static final int SPACE_PERCENT = 80;

    private final TreeSet<CacheSpan> spans;
    private final File dir;
    private long currentSize;

    ExoCacheEvictor(File dir) {
        this.dir = dir;
        this.spans = new TreeSet<>(ExoCacheEvictor::compare);
    }

    static long getBudget(long usedBytes, File dir) {
        long storageBudget = (usedBytes + Math.max(0, dir.getUsableSpace())) * SPACE_PERCENT / 100;
        return Math.min(PreloadSetting.getSizeBytes(), storageBudget);
    }

    private static int compare(CacheSpan lhs, CacheSpan rhs) {
        long diff = lhs.lastTouchTimestamp - rhs.lastTouchTimestamp;
        return diff == 0 ? lhs.compareTo(rhs) : diff < 0 ? -1 : 1;
    }

    @Override
    public boolean requiresCacheSpanTouches() {
        return false;
    }

    @Override
    public void onCacheInitialized() {
    }

    @Override
    public void onStartFile(@NonNull Cache cache, @NonNull String key, long position, long length) {
        if (length != C.LENGTH_UNSET) evict(cache, length);
    }

    @Override
    public void onSpanAdded(@NonNull Cache cache, @NonNull CacheSpan span) {
        spans.add(span);
        currentSize += span.length;
        evict(cache, 0);
    }

    @Override
    public void onSpanRemoved(@NonNull Cache cache, @NonNull CacheSpan span) {
        spans.remove(span);
        currentSize -= span.length;
    }

    @Override
    public void onSpanTouched(@NonNull Cache cache, @NonNull CacheSpan oldSpan, @NonNull CacheSpan newSpan) {
        onSpanRemoved(cache, oldSpan);
        onSpanAdded(cache, newSpan);
    }

    private void evict(Cache cache, long requiredSpace) {
        long maxBytes = getBudget(currentSize, dir);
        if (currentSize + requiredSpace <= maxBytes) return;
        evict(cache, requiredSpace, maxBytes, true);
        evict(cache, requiredSpace, maxBytes, false);
    }

    private void evict(Cache cache, long requiredSpace, long maxBytes, boolean skipProtected) {
        for (CacheSpan span : new ArrayList<>(spans)) {
            if (currentSize + requiredSpace <= maxBytes) return;
            if (skipProtected && ExoReadAhead.isProtected(span)) continue;
            cache.removeSpan(span);
        }
    }
}
