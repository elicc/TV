package com.fongmi.android.tv.player.exo;

import android.net.Uri;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSourceUtil;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.FileDataSource;
import androidx.media3.datasource.PlaceholderDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSink;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheSpan;
import androidx.media3.datasource.cache.ContentMetadata;
import androidx.media3.datasource.cache.ContentMetadataMutations;

import com.fongmi.android.tv.setting.PreloadSetting;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.orhanobut.logger.Logger;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Request;
import okhttp3.Response;

/**
 * Disk read-ahead for progressive HTTP streams (large mkv/mp4 files).
 * <p>
 * ExoPlayer keeps its forward buffer in the Java heap, which caps it at roughly 140 MB (about 30 s of a 4K remux).
 * When the source throughput is close to the average bitrate, high-bitrate scenes drain that buffer and playback stalls,
 * even though calmer scenes download faster than real time. This session downloads the byte range ahead of the player
 * into {@link ExoMediaSourceFactory#getCache()} with several parallel range requests, so surplus bandwidth is banked on
 * disk and the player reads from it through {@link ExoReadAheadDataSource}.
 * <p>
 * The file is split into fixed chunks. Each chunk is downloaded by exactly one owner, so no byte is requested twice.
 * Workers take the chunks after the one the player is reading, and the player fetches its own chunk when nobody has it.
 * Workers and player share one connection budget, because spider proxies limit concurrent connections and refuse the
 * extra ones; when the player needs a connection, the worker farthest ahead gives up its slot.
 * <p>
 * Lock order is always cache → session: no cache method is called while holding this object's monitor.
 */
final class ExoReadAhead {

    private static final String TAG = ExoReadAhead.class.getSimpleName();
    private static final Map<String, ExoReadAhead> SESSIONS = new ConcurrentHashMap<>();
    private static final String METADATA_LENGTH = ContentMetadata.KEY_CUSTOM_PREFIX + "read_ahead_length";
    private static final long CHUNK_BYTES = 4L * 1024 * 1024;
    private static final long MIN_WINDOW_BYTES = 64L * 1024 * 1024;
    private static final long MAX_BACK_BYTES = 256L * 1024 * 1024;
    private static final long RETRY_DELAY_MS = 2_000;
    private static final long MAX_ERROR_DELAY_MS = 8_000;
    private static final long REPORT_INTERVAL_MS = 10_000;
    private static final int MAX_PROBE_FAILURES = 2;
    private static final int BUFFER_SIZE = 64 * 1024;

    private final Set<Long> busyChunks = new HashSet<>();
    private final TreeSet<Long> workerChunks = new TreeSet<>();
    private final Set<Long> yieldChunks = new HashSet<>();
    private final Map<Long, Long> retryAt = new HashMap<>();
    private final AtomicLong downloadedBytes = new AtomicLong();
    private final AtomicLong lastReportMs = new AtomicLong();
    private final DataSource.Factory upstreamFactory;
    private final Map<String, String> headers;
    private final Cache.Listener listener;
    private final Object probeLock;
    private final Cache cache;
    private final String key;
    private final Uri uri;

    private volatile long contentLength = C.LENGTH_UNSET;
    private volatile long windowBytes = MIN_WINDOW_BYTES;
    private volatile long durationMs = C.TIME_UNSET;
    private volatile long readPosition;
    private volatile long cachedChunks;
    private volatile long backBytes;
    private volatile boolean disabled;
    private volatile boolean released;
    private ExecutorService executor;
    private long lastReportBytes;
    private int playerConnections;
    private int maxConnections;
    private int probeFailures;
    private long version;

    private ExoReadAhead(MediaItem mediaItem, Map<String, String> headers) {
        MediaItem.LocalConfiguration config = mediaItem.localConfiguration;
        this.uri = config.uri;
        this.key = getKey(mediaItem);
        this.headers = headers;
        this.probeLock = new Object();
        this.cache = ExoMediaSourceFactory.getCache();
        this.upstreamFactory = ExoMediaSourceFactory.createUpstreamDataSourceFactory(headers);
        this.listener = new SpanListener();
        this.cache.addListener(key, listener);
    }

    static boolean isSupported(MediaItem mediaItem) {
        MediaItem.LocalConfiguration config = mediaItem.localConfiguration;
        if (config == null || config.drmConfiguration != null) return false;
        String scheme = config.uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
        return Util.inferContentTypeForUriAndMimeType(config.uri, config.mimeType) == C.CONTENT_TYPE_OTHER;
    }

    static ExoReadAhead start(MediaItem mediaItem, Map<String, String> headers) {
        ExoReadAhead session = new ExoReadAhead(mediaItem, headers);
        ExoReadAhead previous = SESSIONS.put(session.key, session);
        if (previous != null) previous.release();
        return session;
    }

    static ExoReadAhead get(DataSpec dataSpec) {
        return SESSIONS.get(dataSpec.key != null ? dataSpec.key : dataSpec.uri.toString());
    }

    static boolean isProtected(CacheSpan span) {
        ExoReadAhead session = SESSIONS.get(span.key);
        return session != null && session.protects(span);
    }

    private static String getKey(MediaItem mediaItem) {
        MediaItem.LocalConfiguration config = mediaItem.localConfiguration;
        return config.customCacheKey != null ? config.customCacheKey : config.uri.toString();
    }

    boolean isFor(MediaItem mediaItem) {
        return !released && isSupported(mediaItem) && key.equals(getKey(mediaItem));
    }

    boolean isUsable() {
        return !released && !disabled;
    }

    void setDurationMs(long durationMs) {
        if (durationMs == C.TIME_UNSET || durationMs <= 0 || durationMs == this.durationMs) return;
        this.durationMs = durationMs;
        updateWindow();
    }

    void release() {
        if (released) return;
        released = true;
        SESSIONS.remove(key, this);
        cache.removeListener(key, listener);
        if (executor != null) executor.shutdownNow();
        signal();
    }

    /**
     * Returns the total length of the resource, or {@link C#LENGTH_UNSET} when read-ahead can't be used for it, in which
     * case the caller must fall back to a plain upstream read.
     */
    long prepare() {
        synchronized (probeLock) {
            if (isUsable() && contentLength == C.LENGTH_UNSET) {
                // A length stored by an earlier probe means range requests already worked for this resource.
                long length = cache.getContentMetadata(key).get(METADATA_LENGTH, C.LENGTH_UNSET);
                if (length > 0) start(length);
                else probe();
            }
        }
        return isUsable() ? contentLength : C.LENGTH_UNSET;
    }

    long getVersion() {
        synchronized (this) {
            return version;
        }
    }

    void onOpen(long position) {
        readPosition = position;
        cachedChunks = 0;
        signal();
    }

    void onRead(long position) {
        long previous = readPosition;
        readPosition = position;
        if (previous / CHUNK_BYTES != position / CHUNK_BYTES) signal();
        report();
    }

    long getCachedLength(long position, long end) {
        return Math.max(0, cache.getCachedLength(key, position, end - position));
    }

    long getChunkEnd(long position, long end) {
        return Math.min(end, (position / CHUNK_BYTES + 1) * CHUNK_BYTES);
    }

    /**
     * Claims the chunk at {@code position} for the player and reserves a connection for it. Otherwise waits up to a second
     * for the cache or the chunk ownership to change since {@code seenVersion} and returns false, so the caller checks the
     * cache again. A worker downloading that chunk is only asked to stop when {@code takeOver} is set, keeping what it has
     * written; a worker holding the last free connection is always asked to stop.
     */
    boolean claimForPlayer(long position, long seenVersion, boolean takeOver) throws InterruptedIOException {
        long chunk = position / CHUNK_BYTES;
        synchronized (this) {
            if (workerChunks.contains(chunk)) {
                if (takeOver) yieldChunks.add(chunk);
            } else if (!busyChunks.contains(chunk)) {
                if (workerChunks.isEmpty() || workerChunks.size() + playerConnections < maxConnections) {
                    busyChunks.add(chunk);
                    playerConnections++;
                    return true;
                }
                yieldChunks.add(workerChunks.last());
            }
            if (version == seenVersion) waitLocked(1_000);
            return false;
        }
    }

    void releasePlayer(long position) {
        synchronized (this) {
            busyChunks.remove(position / CHUNK_BYTES);
            playerConnections--;
            version++;
            notifyAll();
        }
    }

    /**
     * Blocks until the cache or the chunk ownership changed since {@code seenVersion} was read.
     */
    private void await(long seenVersion) throws InterruptedIOException {
        synchronized (this) {
            awaitLocked(seenVersion);
        }
    }

    private void awaitLocked(long seenVersion) throws InterruptedIOException {
        while (version == seenVersion && isUsable()) waitLocked(1_000);
    }

    private void waitLocked(long timeoutMs) throws InterruptedIOException {
        try {
            wait(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException();
        }
    }

    DataSource createCacheReader() {
        return new CacheDataSource(cache, PlaceholderDataSource.INSTANCE, new FileDataSource(), null, 0, null);
    }

    /**
     * Each chunk is written as a single span: every span costs a commit and later an eviction in the cache index, and
     * readers only need a chunk once it's complete or while they download it themselves.
     */
    DataSource createDownloader(boolean player) {
        int flags = player ? CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR : 0;
        return new CacheDataSource(cache, upstreamFactory.createDataSource(), new FileDataSource(), new CacheDataSink(cache, CHUNK_BYTES), flags, null);
    }

    DataSpec buildDataSpec(long position, long length) {
        return new DataSpec.Builder().setUri(uri).setKey(key).setPosition(position).setLength(length).setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION).build();
    }

    private void probe() {
        // Start at byte 1: some spider proxies answer any range starting at 0 with a full 200 response.
        Request.Builder builder = new Request.Builder().url(uri.toString()).header("Range", "bytes=1-1");
        for (Map.Entry<String, String> entry : headers.entrySet()) builder.header(entry.getKey(), entry.getValue());
        try (Response response = OkHttp.player().newCall(builder.build()).execute()) {
            long length = response.code() == 206 ? getTotalLength(response.header("Content-Range")) : C.LENGTH_UNSET;
            if (length <= 0) {
                disable("range unsupported, code=" + response.code());
                return;
            }
            storeContentLength(length);
            start(length);
        } catch (Exception e) {
            Logger.t(TAG).w("Probe failed: %s", e.getMessage());
            if (++probeFailures >= MAX_PROBE_FAILURES) disable("probe failed");
        }
    }

    private void start(long length) {
        contentLength = length;
        updateWindow();
        startWorkers();
    }

    private static long getTotalLength(String contentRange) {
        if (contentRange == null) return C.LENGTH_UNSET;
        int index = contentRange.lastIndexOf('/');
        if (index < 0) return C.LENGTH_UNSET;
        try {
            return Long.parseLong(contentRange.substring(index + 1).trim());
        } catch (NumberFormatException e) {
            return C.LENGTH_UNSET;
        }
    }

    private void storeContentLength(long length) {
        try {
            ContentMetadataMutations mutations = new ContentMetadataMutations();
            ContentMetadataMutations.setContentLength(mutations, length);
            mutations.set(METADATA_LENGTH, length);
            cache.applyContentMetadataMutations(key, mutations);
        } catch (Cache.CacheException ignored) {
        }
    }

    private void updateWindow() {
        long budget = ExoCacheEvictor.getBudget(cache.getCacheSpace(), Path.exoCache());
        long back = Math.min(MAX_BACK_BYTES, budget / 8);
        long limit = Math.max(MIN_WINDOW_BYTES, budget - back);
        long length = contentLength;
        long duration = durationMs;
        long timed = length > 0 && duration > 0 ? length * PreloadSetting.getTimeSeconds() * 1000 / duration : limit;
        backBytes = back;
        windowBytes = Math.clamp(timed, MIN_WINDOW_BYTES, limit);
        signal();
    }

    private void disable(String reason) {
        Logger.t(TAG).d("Read-ahead disabled: %s", reason);
        disabled = true;
        signal();
    }

    private void signal() {
        synchronized (this) {
            version++;
            notifyAll();
        }
    }

    private synchronized void startWorkers() {
        if (executor != null || released) return;
        int threads = PreloadSetting.getThreads();
        maxConnections = threads;
        executor = Executors.newFixedThreadPool(threads, runnable -> new Thread(runnable, "ExoReadAhead"));
        for (int i = 0; i < threads; i++) executor.execute(this::work);
        Logger.t(TAG).d("Read-ahead started: length=%d, window=%d, threads=%d", contentLength, windowBytes, threads);
    }

    private boolean protects(CacheSpan span) {
        long position = readPosition;
        long length = contentLength;
        // The container header and index are read again on every start and seek.
        if (length > 0 && (span.position < CHUNK_BYTES || span.position + span.length > getLastChunkStart())) return true;
        return span.position + span.length > position - backBytes && span.position < position + windowBytes;
    }

    private long getLastChunkStart() {
        return (contentLength - 1) / CHUNK_BYTES * CHUNK_BYTES;
    }

    /**
     * The first and last chunk hold the container header and index. The player reads them on start before seeking to
     * the resume position, so nothing should be fetched after them.
     */
    private boolean isIndexChunk(long chunk) {
        return chunk == 0 || chunk == (contentLength - 1) / CHUNK_BYTES;
    }

    private long getLastChunk() {
        long end = Math.min(contentLength, readPosition + windowBytes);
        return (end - 1) / CHUNK_BYTES;
    }

    private boolean isWanted(long chunk) {
        return isUsable() && chunk >= readPosition / CHUNK_BYTES && chunk <= getLastChunk() && !isYielding(chunk);
    }

    private synchronized boolean isYielding(long chunk) {
        return yieldChunks.contains(chunk);
    }

    private void work() {
        byte[] buffer = new byte[BUFFER_SIZE];
        int errors = 0;
        while (!Thread.currentThread().isInterrupted()) {
            long chunk = nextChunk();
            if (chunk < 0) return;
            long delayMs = 0;
            try {
                download(chunk, buffer);
                errors = 0;
            } catch (IOException e) {
                if (!isUsable()) return;
                Logger.t(TAG).w("Chunk %d failed: %s", chunk, e.getMessage());
                synchronized (this) {
                    retryAt.put(chunk, SystemClock.elapsedRealtime() + RETRY_DELAY_MS);
                }
                delayMs = Math.min(MAX_ERROR_DELAY_MS, RETRY_DELAY_MS << Math.min(errors++, 3));
            } finally {
                releaseWorker(chunk);
            }
            // Back off after releasing the chunk and its connection, so the player can take them meanwhile.
            if (delayMs > 0 && !sleep(delayMs)) return;
            report();
        }
    }

    /**
     * Returns the first missing chunk after the one the player is reading, which the player fetches itself.
     */
    private long nextChunk() {
        while (isUsable()) {
            long seenVersion = getVersion();
            long readChunk = readPosition / CHUNK_BYTES;
            long last = isIndexChunk(readChunk) ? readChunk : getLastChunk();
            boolean contiguous = true;
            for (long chunk = Math.max(readChunk + 1, cachedChunks); chunk <= last && hasWorkerSlot(); chunk++) {
                long start = chunk * CHUNK_BYTES;
                if (isBusy(chunk)) {
                    contiguous = false;
                } else if (cache.isCached(key, start, Math.min(contentLength, start + CHUNK_BYTES) - start)) {
                    if (contiguous) cachedChunks = chunk + 1;
                } else {
                    contiguous = false;
                    if (claimForWorker(chunk)) return chunk;
                }
            }
            try {
                await(seenVersion);
            } catch (InterruptedIOException e) {
                return -1;
            }
        }
        return -1;
    }

    private synchronized boolean hasWorkerSlot() {
        return workerChunks.size() + playerConnections < maxConnections;
    }

    private synchronized boolean claimForWorker(long chunk) {
        if (!hasWorkerSlot() || !busyChunks.add(chunk)) return false;
        workerChunks.add(chunk);
        return true;
    }

    private void releaseWorker(long chunk) {
        synchronized (this) {
            busyChunks.remove(chunk);
            workerChunks.remove(chunk);
            yieldChunks.remove(chunk);
            version++;
            notifyAll();
        }
    }

    private synchronized boolean isBusy(long chunk) {
        if (busyChunks.contains(chunk)) return true;
        Long retry = retryAt.get(chunk);
        if (retry == null) return false;
        if (retry > SystemClock.elapsedRealtime()) return true;
        retryAt.remove(chunk);
        return false;
    }

    private void download(long chunk, byte[] buffer) throws IOException {
        long position = chunk * CHUNK_BYTES;
        long end = Math.min(contentLength, position + CHUNK_BYTES);
        while (position < end && isWanted(chunk)) {
            long cached = cache.getCachedLength(key, position, end - position);
            if (cached > 0) {
                position += cached;
                continue;
            }
            DataSource source = createDownloader(false);
            try {
                source.open(buildDataSpec(position, -cached));
                int read;
                while (isWanted(chunk) && (read = source.read(buffer, 0, buffer.length)) != C.RESULT_END_OF_INPUT) {
                    downloadedBytes.addAndGet(read);
                    position += read;
                }
            } finally {
                DataSourceUtil.closeQuietly(source);
            }
        }
    }

    private boolean sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    private void report() {
        long now = SystemClock.elapsedRealtime();
        long last = lastReportMs.get();
        if (now - last < REPORT_INTERVAL_MS || !lastReportMs.compareAndSet(last, now)) return;
        long bytes = downloadedBytes.get();
        long speed = last == 0 ? 0 : (bytes - lastReportBytes) * 1000 / (now - last);
        lastReportBytes = bytes;
        long position = readPosition;
        long ahead = Math.max(0, cache.getCachedLength(key, position, Math.max(1, contentLength - position)));
        Logger.t(TAG).d("Read-ahead: speed=%dKB/s, ahead=%dMB/%dMB", speed / 1024, ahead >> 20, windowBytes >> 20);
    }

    private final class SpanListener implements Cache.Listener {

        @Override
        public void onSpanAdded(@NonNull Cache cache, @NonNull CacheSpan span) {
            signal();
        }

        @Override
        public void onSpanRemoved(@NonNull Cache cache, @NonNull CacheSpan span) {
        }

        @Override
        public void onSpanTouched(@NonNull Cache cache, @NonNull CacheSpan oldSpan, @NonNull CacheSpan newSpan) {
        }
    }
}
