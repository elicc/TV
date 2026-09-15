package com.fongmi.android.tv.ui.motion;

import android.content.Context;
import android.provider.Settings;
import android.view.View;

/** Motion tokens shared by every leanback screen. Mirrors values/tv_integers.xml. */
public final class TvMotion {

    public static final long FOCUS_ON = 180;
    public static final long FOCUS_OFF = 140;
    public static final long ENTRANCE = 600;
    public static final long ENTRANCE_DELAY_FIRST = 50;
    public static final long ENTRANCE_DELAY_STEP = 100;
    public static final long DRAWER = 280;
    public static final long OSD_FADE = 500;
    public static final long OSD_TIMEOUT = 3000;
    public static final long KEYCAP = 320;
    public static final long SKIP = 1500;
    public static final long PRESS = 350;
    public static final long SHIMMER = 4500;
    public static final long BREATH = 11000;
    public static final long DRIFT = 14000;
    /** Cold-start brand splash: mark settles in, holds, then cross-fades into the home. */
    public static final long SPLASH_MARK = 560;
    public static final long SPLASH_HOLD = 900;
    public static final long SPLASH_OUT = 420;
    /** Hard cap so the overlay can never outlive a config that never settles. */
    public static final long SPLASH_MAX = 2600;

    private TvMotion() {
    }

    /** Honor the system animator scale; developers can disable motion entirely. */
    public static boolean motionEnabled(View view) {
        return motionEnabled(view.getContext());
    }

    public static boolean motionEnabled(Context context) {
        return Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
    }
}
