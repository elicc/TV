package com.fongmi.android.tv.db;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.Init;
import com.orhanobut.logger.Logger;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * The one place that knows whether the config vault — {@code /storage/emulated/0/TV/}, which is
 * the only storage that outlives an uninstall — is actually usable, and the only place that
 * classifies what an attempted write did.
 *
 * <p>Two rules keep this honest:
 *
 * <ul>
 *   <li><b>Never pre-gate a write.</b> Writes are always attempted and the outcome is classified
 *       afterwards. A writability pre-check cannot distinguish "no access" from "access but
 *       broken", and a wrong guess is exactly what made the old path fail invisibly.
 *   <li><b>Never decide UI here.</b> This class holds state; the leanback flavour renders it.
 *       Nothing in {@code main} can reach a dialog, so the phone flavour stays untouched.
 * </ul>
 *
 * <p>State lives in its own preferences file rather than the default one, because it describes
 * <em>this install</em> (the storage grant is per-install) and the full backup snapshots the
 * default prefs wholesale — keeping them apart means a restored snapshot can never resurrect a
 * stale permission flag.
 */
public final class ConfigVault {

    private static final String TAG = ConfigVault.class.getSimpleName();
    private static final String PREFS = "vault";
    private static final String KEY_STATE = "state";
    private static final String KEY_LAST_OK = "last_ok";
    private static final String KEY_ATTEMPTED = "attempted";
    private static final String KEY_DISMISSED = "dismissed";
    private static final String KEY_RESTORE_DISMISSED = "restore_dismissed";
    private static final String KEY_REPORTED = "reported";

    private ConfigVault() {
    }

    private static SharedPreferences prefs() {
        return Init.context().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Ground truth, read from the OS. On API 30+ this is the "All files access" app-op, which an
     * uninstall always revokes — so a freshly installed app is never writable until the viewer
     * grants it again, even though the files it wrote last time are still on disk.
     */
    public static boolean isWritable() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
    }

    public static VaultPolicy.State state() {
        try {
            return VaultPolicy.State.valueOf(prefs().getString(KEY_STATE, VaultPolicy.State.UNKNOWN.name()));
        } catch (IllegalArgumentException e) {
            return VaultPolicy.State.UNKNOWN;
        }
    }

    public static long lastOk() {
        return prefs().getLong(KEY_LAST_OK, 0L);
    }

    public static boolean shouldPrompt() {
        return VaultPolicy.shouldPrompt(isWritable(), prefs().getBoolean(KEY_DISMISSED, false));
    }

    /** The viewer declined the offer; never raise it unprompted again on this install. */
    public static void dismissPrompt() {
        prefs().edit().putBoolean(KEY_DISMISSED, true).apply();
    }

    /** Whether a fresh install should explain the grant needed to inspect the old vault. */
    public static boolean shouldPromptRestore(boolean hasSource) {
        return VaultPolicy.shouldPromptRestore(
                isWritable(), prefs().getBoolean(KEY_RESTORE_DISMISSED, false), hasSource);
    }

    /** The viewer declined restore for this install; keep the normal backup offer independent. */
    public static void dismissRestorePrompt() {
        prefs().edit().putBoolean(KEY_RESTORE_DISMISSED, true).apply();
    }

    /**
     * Whether the last failure still has to be told to the viewer. A missing grant is the
     * expected fresh-install state and the offer already covers it; a failure while storage was
     * reachable is a real fault and is worth saying once, not on every retry.
     */
    public static boolean needsReport() {
        return VaultPolicy.shouldReport(state(), reported());
    }

    public static void markReported() {
        prefs().edit().putString(KEY_REPORTED, state().name()).apply();
    }

    private static VaultPolicy.State reported() {
        try {
            return VaultPolicy.State.valueOf(prefs().getString(KEY_REPORTED, VaultPolicy.State.UNKNOWN.name()));
        } catch (IllegalArgumentException e) {
            return VaultPolicy.State.UNKNOWN;
        }
    }

    /** Cheap snapshot of the active VOD/LIVE source, refreshed on every successful load. */
    public static void save() {
        Task.executeSerial(() -> record(SourceBootstrap.write()));
    }

    /** The full snapshot (sites, keeps, history, preferences). Runs on every home exit already. */
    public static void backup() {
        Task.executeSerial(() -> {
            // Nothing configured means nothing to snapshot. BackupManager reports that case as a
            // failed write, which would otherwise show up as a storage fault on a healthy install.
            if (AppDatabase.get().getConfigDao().findAll().isEmpty()) return;
            record(BackupManager.save());
        });
    }

    /**
     * Restore because the viewer asked for it. No latch: an explicit request is always honoured.
     * The result is delivered on the main thread.
     */
    public static void restore(Consumer<VaultPolicy.Source> done) {
        Task.executeSerial(() -> {
            VaultPolicy.Source source = restoreNow();
            App.post(() -> done.accept(source));
        });
    }

    /**
     * Restore on cold start, but only when it cannot destroy anything: shared storage is
     * reachable, no source is configured, and this install has not already tried.
     *
     * <p>The attempt is latched <em>before</em> the restore runs, so a crash midway cannot turn
     * into a wipe-and-retry loop on the next launch.
     */
    public static void restoreIfFresh(Consumer<VaultPolicy.Source> done) {
        Task.executeSerial(() -> {
            boolean hasSource = !AppDatabase.get().getConfigDao().findAll().isEmpty();
            if (!VaultPolicy.canAutoRestore(isWritable(), prefs().getBoolean(KEY_ATTEMPTED, false), hasSource)) {
                App.post(() -> done.accept(VaultPolicy.Source.NONE));
                return;
            }
            prefs().edit().putBoolean(KEY_ATTEMPTED, true).apply();
            VaultPolicy.Source source = restoreNow();
            App.post(() -> done.accept(source));
        });
    }

    private static VaultPolicy.Source restoreNow() {
        List<File> backups = usableBackups();
        // Prefer the full snapshot — it carries sites, keeps, history and preferences — and
        // fall back to the lightweight one when it is absent or unreadable. BackupManager
        // refuses a corrupt file rather than wiping the database with an empty snapshot.
        boolean full = VaultPolicy.pickSource(!backups.isEmpty(), SourceBootstrap.exists()) == VaultPolicy.Source.BACKUP;
        if (full && BackupManager.restoreNow(backups.get(0))) return VaultPolicy.Source.BACKUP;
        return SourceBootstrap.apply() > 0 ? VaultPolicy.Source.BOOTSTRAP : VaultPolicy.Source.NONE;
    }

    private static List<File> usableBackups() {
        return BackupManager.getFiles();
    }

    /**
     * Classify an attempted write. A failure while shared storage is reachable is a real IO
     * fault and is worth surfacing; a failure without access is the expected fresh-install
     * state and only ever feeds the one-time offer.
     */
    private static void record(boolean ok) {
        VaultPolicy.State state = VaultPolicy.classify(ok, isWritable());
        SharedPreferences.Editor editor = prefs().edit().putString(KEY_STATE, state.name());
        if (ok) editor.putLong(KEY_LAST_OK, System.currentTimeMillis());
        editor.apply();
        if (!ok) Logger.t(TAG).e("Config vault write did not land: " + state);
    }
}
