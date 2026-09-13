package com.fongmi.android.tv.diagnostic;

import android.util.Log;

import com.orhanobut.logger.LogAdapter;

public final class PersistentLogAdapter implements LogAdapter {

    @Override
    public boolean isLoggable(int priority, String tag) {
        return priority >= Log.DEBUG;
    }

    @Override
    public void log(int priority, String tag, String message) {
        DiagnosticStore.get().log(priority, tag, message);
    }
}
