package com.fongmi.android.tv.utils;

import android.net.TrafficStats;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import com.fongmi.android.tv.App;

import java.text.DecimalFormat;

public class Traffic {

    private static final DecimalFormat format = new DecimalFormat("#.0");
    private static final int UID = App.get().getApplicationInfo().uid;
    private static final String UNIT_KB = " KB/s";
    private static final String UNIT_MB = " MB/s";

    private static long lastTotalRxBytes;
    private static long lastTimeStamp;

    public static void setSpeed(TextView view) {
        long nowTotalRxBytes = TrafficStats.getUidRxBytes(UID);
        if (nowTotalRxBytes == TrafficStats.UNSUPPORTED) return;
        long nowTimeStamp = SystemClock.elapsedRealtime();
        long elapsed = nowTimeStamp - lastTimeStamp;
        view.setVisibility(View.VISIBLE);
        if (lastTimeStamp == 0 || elapsed <= 0) view.setText("");
        else view.setText(format((nowTotalRxBytes - lastTotalRxBytes) * 1000 / elapsed / 1024));
        lastTimeStamp = nowTimeStamp;
        lastTotalRxBytes = nowTotalRxBytes;
    }

    private static String format(long speed) {
        return speed < 1000 ? speed + UNIT_KB : format.format(speed / 1024f) + UNIT_MB;
    }

    public static void reset() {
        lastTotalRxBytes = 0;
        lastTimeStamp = 0;
    }
}
