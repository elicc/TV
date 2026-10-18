package com.fongmi.android.tv.player.exo;

import android.net.Uri;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.Map;

/**
 * Player-side reader for {@link ExoReadAhead}. Serves bytes from the read-ahead cache, waits for a chunk that a worker is
 * downloading, and only fetches from upstream itself when it reaches a chunk nobody owns. Requests without an active
 * read-ahead session go straight to {@code fallback}. Transient upstream errors (spider proxies drop connections under
 * parallel load) are retried here a few times before they reach the player, where a load error could escalate into a
 * format retry or a line switch.
 */
final class ExoReadAheadDataSource extends BaseDataSource {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500;
    /**
     * How long to wait for a worker to finish the chunk the player needs before fetching it directly. A new request to a
     * spider proxy usually takes longer to deliver its first byte than a worker takes to finish a chunk.
     */
    private static final long TAKEOVER_MS = 3_000;

    private final DataSource fallback;

    @Nullable
    private ExoReadAhead session;
    @Nullable
    private DataSource source;
    @Nullable
    private DataSpec dataSpec;
    private boolean usingFallback;
    private boolean opened;
    private int failures;
    private long claimed;
    private long waitPosition;
    private long waitStartMs;
    private long position;
    private long end;

    ExoReadAheadDataSource(DataSource fallback) {
        super(true);
        this.fallback = fallback;
        this.claimed = C.INDEX_UNSET;
        this.waitPosition = C.INDEX_UNSET;
    }

    @Override
    public long open(@NonNull DataSpec dataSpec) throws IOException {
        this.dataSpec = dataSpec;
        this.failures = 0;
        transferInitializing(dataSpec);
        long length = openSession(dataSpec);
        if (length == C.LENGTH_UNSET || usingFallback) length = openFallback(dataSpec);
        opened = true;
        transferStarted(dataSpec);
        return length;
    }

    @Override
    public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        while (true) {
            if (usingFallback) return count(fallback.read(buffer, offset, length));
            if (position >= end) return C.RESULT_END_OF_INPUT;
            try {
                if (source == null) openSource();
                if (usingFallback) continue;
                int read = source.read(buffer, offset, length);
                if (read == C.RESULT_END_OF_INPUT) {
                    closeSource();
                    continue;
                }
                failures = 0;
                position += read;
                session.onRead(position);
                return count(read);
            } catch (InterruptedIOException e) {
                closeSourceQuietly();
                throw e;
            } catch (IOException e) {
                closeSourceQuietly();
                if (++failures > MAX_RETRIES || session == null || !session.isUsable()) throw e;
                sleep(RETRY_DELAY_MS * failures);
            }
        }
    }

    @Nullable
    @Override
    public Uri getUri() {
        if (usingFallback) return fallback.getUri();
        return dataSpec == null ? null : dataSpec.uri;
    }

    @NonNull
    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return usingFallback ? fallback.getResponseHeaders() : Map.of();
    }

    @Override
    public void close() throws IOException {
        try {
            if (usingFallback) fallback.close();
            else closeSource();
        } finally {
            session = null;
            dataSpec = null;
            usingFallback = false;
            if (opened) {
                opened = false;
                transferEnded();
            }
        }
    }

    private long openSession(DataSpec dataSpec) {
        session = ExoReadAhead.get(dataSpec);
        if (session == null || !"GET".equals(dataSpec.getHttpMethodString())) return C.LENGTH_UNSET;
        long length = session.prepare();
        if (length == C.LENGTH_UNSET || dataSpec.position >= length) return C.LENGTH_UNSET;
        position = dataSpec.position;
        end = dataSpec.length == C.LENGTH_UNSET ? length : Math.min(length, position + dataSpec.length);
        session.onOpen(position);
        return end - position;
    }

    private long openFallback(DataSpec dataSpec) throws IOException {
        session = null;
        usingFallback = true;
        return fallback.open(dataSpec);
    }

    private void openSource() throws IOException {
        while (true) {
            if (!session.isUsable()) {
                openFallback(dataSpec.subrange(position - dataSpec.position, end - position));
                return;
            }
            long version = session.getVersion();
            long cached = session.getCachedLength(position, end);
            if (cached > 0) {
                openSegment(session.createCacheReader(), cached);
                return;
            }
            long nowMs = SystemClock.elapsedRealtime();
            if (waitPosition != position) {
                waitPosition = position;
                waitStartMs = nowMs;
            }
            if (session.claimForPlayer(position, version, nowMs - waitStartMs >= TAKEOVER_MS)) {
                claimed = position;
                openSegment(session.createDownloader(true), session.getChunkEnd(position, end) - position);
                return;
            }
        }
    }

    private void openSegment(DataSource source, long length) throws IOException {
        this.source = source;
        source.open(session.buildDataSpec(position, length));
    }

    private void closeSourceQuietly() {
        try {
            closeSource();
        } catch (IOException ignored) {
        }
    }

    private static void sleep(long delayMs) throws InterruptedIOException {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException();
        }
    }

    private void closeSource() throws IOException {
        DataSource current = source;
        source = null;
        try {
            if (current != null) current.close();
        } finally {
            if (claimed != C.INDEX_UNSET) {
                session.releasePlayer(claimed);
                claimed = C.INDEX_UNSET;
            }
        }
    }

    private int count(int read) {
        if (read > 0) bytesTransferred(read);
        return read;
    }
}
