package com.fongmi.android.tv.service;

import android.accessibilityservice.AccessibilityService;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.voice.TclSearchBridgePolicy;
import com.fongmi.android.tv.voice.TclSearchBridgeSettings;

import java.util.List;

/** Only observes the OEM result-title field. Does not record audio or control the OEM. */
public final class TclSearchBridgeService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TclSearchBridgePolicy policy = new TclSearchBridgePolicy();
    private final Runnable dispatch = this::dispatch;
    private int failures;
    private String inFlight;
    private final Runnable timeout = () -> {
        if (TclSearchBridgeSettings.isPending(inFlight)) fail();
        else failures = 0;
        inFlight = null;
    };

    @Override
    protected void onServiceConnected() {
        TclSearchBridgeSettings.connect(this::reset);
        reset();
        TclSearchBridgeSettings.record("connected");
    }

    private void reset() {
        handler.removeCallbacksAndMessages(null);
        policy.cancel();
        inFlight = null;
        failures = 0;
        TclSearchBridgeSettings.clearPending();
        Snapshot snapshot = read();
        if (snapshot != null) policy.prime(snapshot.title);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!TclSearchBridgeSettings.isEnabled()) { reset(); return; }
        if (!TclSearchBridgePolicy.PACKAGE.contentEquals(event.getPackageName() == null ? "" : event.getPackageName())) return;
        if (inFlight != null && !TclSearchBridgeSettings.isPending(inFlight)) {
            handler.removeCallbacks(timeout);
            inFlight = null;
            failures = 0;
        }
        Snapshot snapshot = read();
        handler.removeCallbacks(dispatch);
        if (snapshot == null) { policy.cancel(); return; }
        long delay = policy.observe(snapshot.window, snapshot.title, SystemClock.uptimeMillis());
        if (delay >= 0 && inFlight == null) handler.postDelayed(dispatch, delay);
    }

    private void dispatch() {
        if (!TclSearchBridgeSettings.isEnabled() || inFlight != null) return;
        Snapshot snapshot = read();
        if (snapshot == null) { policy.cancel(); return; }
        String word = policy.take(snapshot.window, snapshot.title, SystemClock.uptimeMillis());
        if (word == null) return;
        launch(word);
    }

    private void launch(String word) {
        inFlight = TclSearchBridgeSettings.begin(word);
        Intent intent = new Intent(this, HomeActivity.class)
                .setAction(Intent.ACTION_SEARCH)
                .putExtra(SearchManager.QUERY, word)
                .putExtra(TclSearchBridgeSettings.EXTRA_REQUEST, inFlight)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        TclSearchBridgeSettings.record("launch_requested");
        try {
            startActivity(intent);
            handler.postDelayed(timeout, 15000);
        } catch (RuntimeException exception) {
            fail();
            inFlight = null;
        }
    }

    private void fail() {
        TclSearchBridgeSettings.clearPending();
        TclSearchBridgeSettings.record("failed");
        if (++failures >= 3) {
            TclSearchBridgeSettings.setEnabled(false);
            TclSearchBridgeSettings.record("paused_after_failures");
        }
    }

    @SuppressWarnings("deprecation")
    private Snapshot read() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        try {
            if (!TclSearchBridgePolicy.PACKAGE.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) return null;
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(TclSearchBridgePolicy.TITLE_ID);
            String title = null;
            int visible = 0;
            for (AccessibilityNodeInfo node : nodes) {
                if (node.isVisibleToUser() && node.getText() != null) {
                    visible++;
                    title = node.getText().toString();
                }
                node.recycle();
            }
            return new Snapshot(root.getWindowId(), visible == 1 ? title : null);
        } catch (IllegalStateException exception) {
            return null;
        } finally {
            root.recycle();
        }
    }

    @Override
    public void onInterrupt() { reset(); }

    @Override
    public boolean onUnbind(Intent intent) {
        reset();
        TclSearchBridgeSettings.disconnect();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        TclSearchBridgeSettings.disconnect();
        super.onDestroy();
    }

    private static final class Snapshot {
        final int window;
        final String title;
        Snapshot(int window, String title) { this.window = window; this.title = title; }
    }
}
