package com.fongmi.android.tv.diagnostic;

import android.os.Process;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CrashRecorder implements Thread.UncaughtExceptionHandler {

    private static final AtomicBoolean HANDLING = new AtomicBoolean();
    private final Thread.UncaughtExceptionHandler next;

    private CrashRecorder(Thread.UncaughtExceptionHandler next) {
        this.next = next;
    }

    public static void install() {
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (current instanceof CrashRecorder) return;
        Thread.setDefaultUncaughtExceptionHandler(new CrashRecorder(current));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable error) {
        if (HANDLING.compareAndSet(false, true)) {
            try {
                DiagnosticStore.get().recordCrash(thread, error);
            } catch (Throwable ignored) {
            }
        }
        if (next != null) {
            next.uncaughtException(thread, error);
        } else {
            Process.killProcess(Process.myPid());
            System.exit(10);
        }
    }
}
