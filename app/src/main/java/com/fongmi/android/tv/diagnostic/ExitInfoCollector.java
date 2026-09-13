package com.fongmi.android.tv.diagnostic;

import android.app.ActivityManager;
import android.app.Application;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;

public final class ExitInfoCollector {

    private static final String PREFS = "diagnostics";
    private static final String LAST_EXIT = "last_exit_timestamp";
    private static final int MAX_TRACE_BYTES = 1024 * 1024;

    private ExitInfoCollector() {
    }

    public static void collect(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        if (!context.getPackageName().equals(Application.getProcessName())) return;
        try {
            collectSupported(context.getApplicationContext());
        } catch (RuntimeException ignored) {
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static void collectSupported(Context context) {
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        if (manager == null) return;
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long previous = preferences.getLong(LAST_EXIT, 0L);
        long newest = previous;
        boolean failed = false;
        List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 32);
        exits.sort(Comparator.comparingLong(ApplicationExitInfo::getTimestamp));
        for (ApplicationExitInfo exit : exits) {
            if (exit.getTimestamp() <= previous) continue;
            newest = Math.max(newest, exit.getTimestamp());
            if (!isRelevant(exit.getReason())) continue;
            if (!DiagnosticStore.get().recordExit("exit-" + reasonName(exit.getReason()), describe(exit), readTrace(exit))) failed = true;
        }
        if (!failed && newest > previous) preferences.edit().putLong(LAST_EXIT, newest).apply();
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static boolean isRelevant(int reason) {
        return reason == ApplicationExitInfo.REASON_CRASH
                || reason == ApplicationExitInfo.REASON_CRASH_NATIVE
                || reason == ApplicationExitInfo.REASON_ANR
                || reason == ApplicationExitInfo.REASON_SIGNALED
                || reason == ApplicationExitInfo.REASON_LOW_MEMORY
                || reason == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE
                || reason == ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE
                || reason == ApplicationExitInfo.REASON_DEPENDENCY_DIED
                || reason == ApplicationExitInfo.REASON_OTHER;
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static String describe(ApplicationExitInfo exit) {
        return "kind=historical_exit\n"
                + "timestamp=" + exit.getTimestamp() + '\n'
                + "process=" + exit.getProcessName() + '\n'
                + "reason=" + reasonName(exit.getReason()) + " (" + exit.getReason() + ")\n"
                + "status=" + exit.getStatus() + '\n'
                + "importance=" + exit.getImportance() + '\n'
                + "pssKb=" + exit.getPss() + '\n'
                + "rssKb=" + exit.getRss() + '\n'
                + "description=" + String.valueOf(exit.getDescription()) + '\n';
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static byte[] readTrace(ApplicationExitInfo exit) {
        try (InputStream input = exit.getTraceInputStream()) {
            if (input == null) return null;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1 && total < MAX_TRACE_BYTES) {
                int write = Math.min(count, MAX_TRACE_BYTES - total);
                output.write(buffer, 0, write);
                total += write;
            }
            return output.toByteArray();
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static String reasonName(int reason) {
        return switch (reason) {
            case ApplicationExitInfo.REASON_CRASH -> "java-crash";
            case ApplicationExitInfo.REASON_CRASH_NATIVE -> "native-crash";
            case ApplicationExitInfo.REASON_ANR -> "anr";
            case ApplicationExitInfo.REASON_SIGNALED -> "signaled";
            case ApplicationExitInfo.REASON_LOW_MEMORY -> "low-memory";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization-failure";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive-resource";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency-died";
            case ApplicationExitInfo.REASON_OTHER -> "system-kill";
            default -> "reason-" + reason;
        };
    }
}
