package com.fongmi.android.tv.player.exo;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.media3.common.Timeline;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link DefaultLoadControl} with an adaptive resume threshold after a rebuffer.
 * <p>
 * The default resumes once 2 s are buffered. When the network can't keep up with the bitrate, those 2 s drain quickly and
 * playback stalls every few seconds. Each rebuffer that follows another within {@link #RECOVER_MS} doubles the amount
 * required before resuming (2 s → 4 s → 8 s → 16 s → 20 s), turning many short stalls into a few longer ones. The level
 * resets once playback has run smoothly for {@link #RECOVER_MS}. Playback always resumes when the buffer is full.
 */
final class ExoLoadControl implements LoadControl {

    private static final long BASE_REBUFFER_US = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS * 1000L;
    private static final long MAX_REBUFFER_US = 20_000_000;
    private static final long RECOVER_MS = 90_000;

    private final Map<PlayerId, Rebuffer> states;
    private final DefaultLoadControl delegate;

    ExoLoadControl(DefaultLoadControl delegate) {
        this.delegate = delegate;
        this.states = new ConcurrentHashMap<>();
    }

    @Override
    public void onPrepared(@NonNull PlayerId playerId) {
        delegate.onPrepared(playerId);
    }

    @Override
    public void onTracksSelected(@NonNull Parameters parameters, @NonNull TrackGroupArray trackGroups, @NonNull ExoTrackSelection[] trackSelections) {
        delegate.onTracksSelected(parameters, trackGroups, trackSelections);
    }

    @Override
    public void onStopped(@NonNull PlayerId playerId) {
        states.remove(playerId);
        delegate.onStopped(playerId);
    }

    @Override
    public void onReleased(@NonNull PlayerId playerId) {
        states.remove(playerId);
        delegate.onReleased(playerId);
    }

    @NonNull
    @Override
    public Allocator getAllocator(@NonNull PlayerId playerId) {
        return delegate.getAllocator(playerId);
    }

    @Override
    public long getBackBufferDurationUs(@NonNull PlayerId playerId) {
        return delegate.getBackBufferDurationUs(playerId);
    }

    @Override
    public boolean retainBackBufferFromKeyframe(@NonNull PlayerId playerId) {
        return delegate.retainBackBufferFromKeyframe(playerId);
    }

    @Override
    public boolean shouldContinueLoading(@NonNull Parameters parameters) {
        boolean continueLoading = delegate.shouldContinueLoading(parameters);
        getState(parameters.playerId).bufferFull = !continueLoading;
        return continueLoading;
    }

    @Override
    public boolean shouldContinuePreloading(@NonNull PlayerId playerId, @NonNull Timeline timeline, @NonNull MediaSource.MediaPeriodId mediaPeriodId, long bufferedDurationUs) {
        return delegate.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs);
    }

    @Override
    public boolean shouldStartPlayback(@NonNull Parameters parameters) {
        if (!delegate.shouldStartPlayback(parameters)) return false;
        if (!parameters.rebuffering) return true;
        Rebuffer state = getState(parameters.playerId);
        long nowMs = SystemClock.elapsedRealtime();
        long requiredUs = (long) (state.getRequiredUs(nowMs) * Math.max(1f, parameters.playbackSpeed));
        if (parameters.bufferedDurationUs < requiredUs && !state.bufferFull) return false;
        state.onResumed(nowMs);
        return true;
    }

    private Rebuffer getState(PlayerId playerId) {
        return states.computeIfAbsent(playerId, ignored -> new Rebuffer());
    }

    private static final class Rebuffer {

        private volatile boolean bufferFull;
        private long lastResumeMs;
        private int level;

        private long getRequiredUs(long nowMs) {
            if (nowMs - lastResumeMs > RECOVER_MS) level = 0;
            return Math.min(MAX_REBUFFER_US, BASE_REBUFFER_US << level);
        }

        private void onResumed(long nowMs) {
            lastResumeMs = nowMs;
            if ((BASE_REBUFFER_US << level) < MAX_REBUFFER_US) level++;
        }
    }
}
