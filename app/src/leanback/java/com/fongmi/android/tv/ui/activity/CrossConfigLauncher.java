package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.text.TextUtils;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.VodSiteIndex;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.playback.vod.VodResumeStore;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Opens a history or keep row that another VOD config wrote. A provider the current config shares
 * opens in place, indexing the row's config from its JSON first when it has not been loaded since
 * indexing began; otherwise the row's config is loaded, after asking unless the viewer chose to
 * always switch. The switch persists: home keeps the row's config afterwards.
 */
final class CrossConfigLauncher {

    private final Activity activity;
    private final int cid;
    private final String key;
    private final String siteKey;
    private final String vodId;
    private final String name;
    private final String pic;

    private CrossConfigLauncher(Activity activity, int cid, String key, String name, String pic) {
        String[] parts = key.split(AppDatabase.SYMBOL);
        this.activity = activity;
        this.cid = cid;
        this.key = key;
        this.siteKey = parts[0];
        this.vodId = parts[1];
        this.name = name;
        this.pic = pic;
    }

    /** {@code key} is the row's {@code site@@@vod@@@cid} key and {@code cid} its config, not the current one. */
    static void open(Activity activity, int cid, String key, String name, String pic) {
        new CrossConfigLauncher(activity, cid, key, name, pic).open();
    }

    /** A detail request against an empty or non-HTTP provider fails into a non-focusable empty page. */
    static boolean isPlayable(Site site) {
        return !site.isEmpty() && (site.getType() == 3 || site.getApi().startsWith("http"));
    }

    private void open() {
        Config target = Config.find(cid);
        if (!isPlayable(VodConfig.get().getSite(siteKey))) switchConfig(target);
        else if (VodSiteIndex.has(cid) || target == null) match(target);
        else index(target);
    }

    private void match(Config target) {
        if (VodSiteIndex.matches(cid, siteKey, VodConfig.get().getSite(siteKey))) openInPlace();
        else switchConfig(target);
    }

    /** Fetches only the row's config JSON, so a config not loaded since indexing began needs no switch. */
    private void index(Config target) {
        boolean[] canceled = {false};
        Notify.progress(activity, () -> {
            canceled[0] = true;
            OkHttp.cancel(VodSiteIndex.TAG);
        });
        Task.execute(() -> {
            try {
                VodSiteIndex.build(target);
            } catch (Exception ignored) {
                // Unreachable or undecodable: switching reports the config's own error.
            }
            App.post(() -> {
                if (canceled[0]) return;
                Notify.dismiss();
                if (isAlive()) match(target);
            });
        });
    }

    private void switchConfig(Config target) {
        if (target == null) {
            Notify.show(R.string.tv_history_source_missing);
            CollectActivity.start(activity, name);
        } else if (Setting.isHistoryAutoSwitch()) {
            load(VodConfig.get().getConfig(), target);
        } else {
            confirm(VodConfig.get().getConfig(), target);
        }
    }

    /** The row moves to the current config so the detail page resumes it and lists it only once. */
    private void openInPlace() {
        int current = VodConfig.getCid();
        Task.executeSerial(() -> {
            VodResumeStore.move(cid, key, current);
            App.post(() -> {
                RefreshEvent.history();
                RefreshEvent.keep();
                if (isAlive()) startVideo();
            });
        });
    }

    private void confirm(Config previous, Config target) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.tv_history_switch_title)
                .setMessage(activity.getString(R.string.tv_history_switch_message, target.getDesc(), previous.getDesc()))
                .setPositiveButton(R.string.tv_history_switch_confirm, (ignored, which) -> load(previous, target))
                .setNeutralButton(R.string.tv_history_switch_always, (ignored, which) -> {
                    Setting.putHistoryAutoSwitch(true);
                    load(previous, target);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus());
        dialog.show();
    }

    private void load(Config previous, Config target) {
        boolean[] canceled = {false};
        VodConfig.load(target, new Callback() {
            @Override
            public void start() {
                // Loading the previous config again also cancels this load.
                Notify.progress(activity, () -> {
                    canceled[0] = true;
                    restore(previous, null);
                });
                Notify.show(activity.getString(R.string.tv_history_switching, target.getDesc()));
            }

            @Override
            public void success() {
                if (canceled[0]) return;
                Notify.dismiss();
                if (!isAlive()) return;
                if (isPlayable(VodConfig.get().getSite(siteKey))) {
                    startVideo();
                } else {
                    Notify.show(activity.getString(R.string.tv_history_site_missing, target.getDesc()));
                    CollectActivity.start(activity, name);
                }
            }

            @Override
            public void error(String msg) {
                if (!canceled[0]) restore(previous, msg);
            }
        });
    }

    /** {@code msg} is the switch failure to report, or null when the viewer canceled the switch. */
    private void restore(Config previous, String msg) {
        VodConfig.load(previous, new Callback() {
            @Override
            public void start() {
                // Replaces the switch progress, whose Back would start this restore again.
                Notify.progress(activity);
            }

            @Override
            public void success() {
                Notify.dismiss();
                if (msg != null && isAlive()) offerSearch(msg);
            }

            @Override
            public void error(String restoreError) {
                Notify.dismiss();
                if (msg != null || !TextUtils.isEmpty(restoreError)) Notify.show(TextUtils.isEmpty(restoreError) ? msg : restoreError);
            }
        });
    }

    private void offerSearch(String msg) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.tv_history_switch_failed)
                .setMessage(msg)
                .setPositiveButton(R.string.tv_history_search_current, (ignored, which) -> CollectActivity.start(activity, name))
                .setNegativeButton(R.string.dialog_negative, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus());
        dialog.show();
    }

    private void startVideo() {
        VideoActivity.start(activity, siteKey, vodId, name, pic);
    }

    private boolean isAlive() {
        return !activity.isFinishing() && !activity.isDestroyed();
    }
}
