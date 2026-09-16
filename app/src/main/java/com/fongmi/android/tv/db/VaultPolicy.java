package com.fongmi.android.tv.db;

/**
 * Pure decision rules for the config vault. Deliberately free of every Android type so the
 * rules that must not regress — when to ask for file access, when it is safe to restore over
 * the current data, which snapshot wins — are testable without a device or a stub tree.
 *
 * <p>{@link ConfigVault} owns the IO; this class owns the judgements.
 */
public final class VaultPolicy {

    /** Why the last vault write ended the way it did. */
    public enum State {
        /** Nothing has been attempted yet on this install. */
        UNKNOWN,
        OK,
        /** The write never happened: shared storage is not reachable without user consent. */
        NEED_PERMISSION,
        /** Storage was reachable but the write failed anyway (full disk, IO fault). */
        IO_ERROR
    }

    /** Which snapshot a restore ended up using. */
    public enum Source {
        NONE,
        /** The lightweight VOD/LIVE-url snapshot. */
        BOOTSTRAP,
        /** The full site/keep/history/prefs snapshot. */
        BACKUP
    }

    private VaultPolicy() {
    }

    /**
     * Classify an attempted write. Never called before the write is attempted — a pre-check
     * cannot tell "unwritable" from "writable but broken", and guessing wrong is what made the
     * old path fail silently.
     */
    public static State classify(boolean writeOk, boolean writable) {
        if (writeOk) return State.OK;
        return writable ? State.IO_ERROR : State.NEED_PERMISSION;
    }

    /**
     * Ask for file access only when the vault is unusable and the viewer has not already waved
     * the offer away. Someone who granted access for any other feature is silently already
     * covered, which is why {@code writable} short-circuits first.
     */
    public static boolean shouldPrompt(boolean writable, boolean dismissed) {
        return !writable && !dismissed;
    }

    /**
     * A restore grant is useful only while the new install has no source of its own. Its
     * dismissal is deliberately separate from the later backup offer: declining recovery must
     * not prevent someone who configures a new source from being offered uninstall-safe backup.
     */
    public static boolean shouldPromptRestore(boolean writable, boolean dismissed, boolean hasSource) {
        return !hasSource && shouldPrompt(writable, dismissed);
    }

    /**
     * Restore over the current data only when doing so cannot destroy anything: shared storage
     * is reachable, this install has not already tried, and no source is configured. An empty
     * source list is the proof that history/keeps have nothing to point at, since every row
     * carries the config it belongs to.
     */
    public static boolean canAutoRestore(boolean writable, boolean attempted, boolean hasSource) {
        return writable && !attempted && !hasSource;
    }

    /**
     * Whether a failure is worth saying out loud, once, rather than on every retry.
     *
     * <p>Only {@link State#IO_ERROR} qualifies. {@link State#NEED_PERMISSION} is not a fault —
     * it is the ordinary state of a fresh install, and it already has its own voice in the
     * one-time offer. Reporting it as an error would put "backup failed" on screen the first
     * time someone uses the app, which is both untrue and exactly the interruption the offer
     * exists to avoid.
     */
    public static boolean shouldReport(State state, State lastReported) {
        return state == State.IO_ERROR && lastReported != State.IO_ERROR;
    }

    /** The full snapshot is worth more than the lightweight one; take whichever exists. */
    public static Source pickSource(boolean backupUsable, boolean bootstrapUsable) {
        if (backupUsable) return Source.BACKUP;
        if (bootstrapUsable) return Source.BOOTSTRAP;
        return Source.NONE;
    }
}
