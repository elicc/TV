package com.fongmi.android.tv.utils;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.databinding.ViewProgressBinding;
import com.fongmi.android.tv.databinding.ViewToastBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class Notify {

    public static final String DEFAULT = "default";
    public static final int ID = 9527;
    /** How long a toast stays up, and how long it takes to appear and leave. */
    private static final long DURATION = 2600;
    private static final long FADE = 160;
    private AlertDialog mDialog;
    private Toast mToast;
    private View mOverlay;
    /** Stable instance so App.post can replace the pending hide instead of stacking one. */
    private final Runnable mHide = this::remove;

    private static class Loader {
        static volatile Notify INSTANCE = new Notify();
    }

    private static Notify get() {
        return Loader.INSTANCE;
    }

    public static void createChannel() {
        NotificationManagerCompat notifyMgr = NotificationManagerCompat.from(App.get());
        notifyMgr.createNotificationChannel(new NotificationChannelCompat.Builder(DEFAULT, NotificationManagerCompat.IMPORTANCE_LOW).setName("TV").build());
    }

    public static String getError(int resId, Throwable e) {
        if (TextUtils.isEmpty(e.getMessage())) return ResUtil.getString(resId);
        return ResUtil.getString(resId) + "\n" + e.getMessage();
    }

    public static void show(Notification notification) {
        if (ContextCompat.checkSelfPermission(App.get(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManagerCompat.from(App.get()).notify(ID, notification);
    }

    public static void show(int resId) {
        if (resId != 0) show(ResUtil.getString(resId));
    }

    public static void show(String text) {
        if (!TextUtils.isEmpty(text)) get().makeText(text);
    }

    public static void progress(Context context) {
        dismiss();
        get().create(context);
    }

    public static void dismiss() {
        try {
            if (get().mDialog != null) get().mDialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    private void create(Context context) {
        ViewProgressBinding binding = ViewProgressBinding.inflate(LayoutInflater.from(context));
        mDialog = new MaterialAlertDialogBuilder(context).setView(binding.getRoot()).create();
        mDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        mDialog.show();
    }

    /**
     * Draw the toast ourselves rather than settling for the system one.
     *
     * <p>Since Android 11 the platform stamps the app's launcher tile next to the message, which
     * puts a second, competing brand mark on screen beside the in-app wordmark; and a text toast
     * cannot be restyled, because {@code setView()} is ignored for apps targeting API 30+. So the
     * overlay in {@code view_toast.xml} replaces it, carrying the same transparent ribbon the
     * home toolbar uses. When there is no window to draw into the system toast is still the
     * fallback — a message delivered plainly beats one not delivered.
     */
    private void makeText(String text) {
        App.post(() -> showOverlay(text));
    }

    private void showOverlay(String text) {
        Activity activity = App.activity();
        ViewGroup content = activity == null || activity.isFinishing() || activity.isDestroyed() ? null : activity.findViewById(android.R.id.content);
        if (content == null) {
            showSystem(text);
            return;
        }
        remove();
        // Inflated against the parent on purpose: a root inflated without one gets no
        // LayoutParams, so the layout's own gravity and margins would be silently dropped and
        // the toast would land in the top-left corner.
        ViewToastBinding binding = ViewToastBinding.inflate(LayoutInflater.from(activity), content, false);
        binding.toastText.setText(text);
        binding.getRoot().setAlpha(0f);
        content.addView(binding.getRoot());
        binding.getRoot().animate().alpha(1f).setDuration(FADE).start();
        mOverlay = binding.getRoot();
        // App.post drops the previous pending call first, so a run of toasts keeps one timer
        // rather than stacking one per message.
        App.post(mHide, DURATION);
    }

    private void showSystem(String text) {
        if (mToast != null) mToast.cancel();
        mToast = Toast.makeText(App.get(), text, Toast.LENGTH_LONG);
        mToast.show();
    }

    private void remove() {
        App.removeCallbacks(mHide);
        View overlay = mOverlay;
        mOverlay = null;
        if (overlay == null) return;
        overlay.animate().alpha(0f).setDuration(FADE).withEndAction(() -> {
            if (overlay.getParent() instanceof ViewGroup parent) parent.removeView(overlay);
        }).start();
    }
}
