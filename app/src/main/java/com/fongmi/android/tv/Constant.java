package com.fongmi.android.tv;

import java.util.concurrent.TimeUnit;

public class Constant {

    public static final long INTERVAL_SEEK = TimeUnit.SECONDS.toMillis(10);
    public static final long INTERVAL_HIDE = TimeUnit.SECONDS.toMillis(5);
    public static final long TIMEOUT_VOD = TimeUnit.SECONDS.toMillis(30);
    public static final long TIMEOUT_LIVE = TimeUnit.SECONDS.toMillis(30);
    public static final long TIMEOUT_EPG = TimeUnit.SECONDS.toMillis(5);
    public static final long TIMEOUT_XML = TimeUnit.SECONDS.toMillis(15);
    public static final long TIMEOUT_PLAY = TimeUnit.SECONDS.toMillis(15);
    public static final long TIMEOUT_SYNC = TimeUnit.SECONDS.toMillis(2);
    public static final long TIMEOUT_SEARCH = TimeUnit.SECONDS.toMillis(30);
    public static final long TIMEOUT_PARSE_DEF = TimeUnit.SECONDS.toMillis(15);
    public static final long TIMEOUT_PARSE_WEB = TimeUnit.SECONDS.toMillis(15);
    public static final long TIMEOUT_PARSE_LIVE = TimeUnit.SECONDS.toMillis(10);
    public static final long HISTORY_TIME = TimeUnit.DAYS.toMillis(60);

    /** Fixed tiers fit episodes; long films get a fifth of their length for credits, never more than half. */
    public static long getOpEdLimit(long duration) {
        long tier;
        if (duration < TimeUnit.MINUTES.toMillis(15)) tier = TimeUnit.MINUTES.toMillis(3);
        else if (duration < TimeUnit.MINUTES.toMillis(30)) tier = TimeUnit.MINUTES.toMillis(6);
        else tier = TimeUnit.MINUTES.toMillis(10);
        return Math.min(duration / 2, Math.max(tier, duration / 5));
    }
}
