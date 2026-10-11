package com.fongmi.android.tv.playback.vod;

/** Validate the first prepared samples before allowing them to replace a resume checkpoint. */
public final class VodResumeGuard {
    public enum Check { ACCEPT, WAIT, UNSAFE }

    private long target;
    private long previousDuration;
    private long firstSample = -1;

    public void begin(long position, long duration) {
        target = Math.max(0, position);
        previousDuration = duration;
        firstSample = -1;
    }

    public void clear() { target = 0; }

    public Check check(long position, long duration, long time) {
        if (target <= 0) return Check.ACCEPT;
        if (position < 0 || duration <= 0) return Check.WAIT;
        if (target >= duration || (previousDuration > 0
                && Math.abs(previousDuration - duration) > Math.max(120000, previousDuration / 5))) return Check.UNSAFE;
        if (position + 5000 < target) {
            if (firstSample < 0) firstSample = time;
            return time - firstSample < 8000 ? Check.WAIT : Check.UNSAFE;
        }
        clear();
        return Check.ACCEPT;
    }
}
