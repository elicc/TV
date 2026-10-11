package com.fongmi.android.tv.voice;

import android.app.SearchManager;
import android.content.Intent;
import android.os.SystemClock;

import com.github.catvod.utils.Prefers;

import java.text.DateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.UUID;

/** Process-local diagnostics never retain search text after the pending request expires. */
public final class TclSearchBridgeSettings {
    public static final String EXTRA_REQUEST = "com.elicc.android.tv.TCL_SEARCH_REQUEST";
    private static final String ENABLED = "tcl_search_bridge_enabled";
    private static final ArrayDeque<String> history = new ArrayDeque<>();
    private static String pendingId;
    private static String pendingWord;
    private static long expires;
    private static boolean connected;
    private static Runnable onChanged;

    private TclSearchBridgeSettings() {}

    public static boolean isEnabled() { return Prefers.getBoolean(ENABLED, false); }
    public static boolean isConnected() { return connected; }

    public static void setEnabled(boolean enabled) {
        Prefers.put(ENABLED, enabled);
        clearPending();
        if (onChanged != null) onChanged.run();
    }

    public static void connect(Runnable callback) { connected = true; onChanged = callback; }
    public static void disconnect() { connected = false; onChanged = null; clearPending(); }

    public static String begin(String word) {
        pendingId = UUID.randomUUID().toString();
        pendingWord = word;
        expires = SystemClock.elapsedRealtime() + 15000;
        return pendingId;
    }

    public static boolean isPending(String id) { return id != null && id.equals(pendingId); }

    /** Untrusted callers cannot forge a live bridge ticket or reuse a consumed one. */
    public static boolean consume(Intent intent) {
        if (!isEnabled() || !Intent.ACTION_SEARCH.equals(intent.getAction())) return false;
        if (pendingId == null || !pendingId.equals(intent.getStringExtra(EXTRA_REQUEST))) return false;
        if (SystemClock.elapsedRealtime() > expires || !pendingWord.equals(intent.getStringExtra(SearchManager.QUERY))) {
            clearPending();
            return false;
        }
        clearPending();
        return true;
    }

    public static void clearPending() { pendingId = null; pendingWord = null; }

    public static void record(String stage) {
        if (history.size() == 50) history.removeFirst();
        history.addLast(DateFormat.getTimeInstance().format(new Date()) + "  " + stage);
    }

    public static String diagnostics() { return String.join("\n", history); }
    public static void clearDiagnostics() { history.clear(); }
}
