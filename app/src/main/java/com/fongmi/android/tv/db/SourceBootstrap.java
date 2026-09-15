package com.fongmi.android.tv.db;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;
import com.google.gson.annotations.SerializedName;
import com.orhanobut.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Stores the active VOD/live source metadata in shared storage so a fresh install can recover it
 * after the viewer grants file access again.
 *
 * <p>This class owns the on-disk format and nothing else; whether the write is even possible, and
 * what a failure means, belongs to {@link ConfigVault}. The format is unchanged from the version
 * already deployed, so snapshots written by earlier installs still restore.
 */
public final class SourceBootstrap {

    private static final String TAG = SourceBootstrap.class.getSimpleName();
    private static final String FILE_NAME = "source-bootstrap.json";
    private static final int VERSION = 1;
    private static final long MAX_BYTES = 64 * 1024;

    private SourceBootstrap() {
    }

    public static void save() {
        ConfigVault.save();
    }

    public static void find(int type, Consumer<Config> callback) {
        Task.execute(() -> {
            Source source = read(type);
            App.post(() -> callback.accept(source == null ? null : source.toConfig()));
        });
    }

    /**
     * Persist the snapshot.
     *
     * @return whether the file on disk now reflects the active source; {@code false} means the
     *     caller must not report success. An empty snapshot deletes the file, and that deletion
     *     is a real result too — reporting it as success without checking is how a vault that
     *     was never written ends up looking healthy.
     */
    static boolean write() {
        File file = getFile();
        try {
            Snapshot snapshot = Snapshot.create();
            if (snapshot.sources.isEmpty()) {
                // Nothing to store, so the snapshot file has to go. A file that is simply not
                // there is ambiguous — "no source configured" and "no access to look" read the
                // same — so the directory is materialised first: if that works the absence is
                // real, and if it does not the vault is genuinely out of reach.
                File dir = file.getParentFile();
                if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return false;
                if (file.exists()) return file.delete();
                return dir != null && dir.isDirectory();
            }
            byte[] data = App.gson().toJson(snapshot).getBytes(StandardCharsets.UTF_8);
            FileUtil.writeAtomically(data, file);
            return true;
        } catch (IOException | RuntimeException e) {
            Logger.t(TAG).e(e, "Unable to persist source bootstrap");
            return false;
        }
    }

    static boolean exists() {
        File file = getFile();
        return file.isFile() && file.length() > 0;
    }

    /**
     * Write every source in the snapshot back into the database.
     *
     * @return how many sources landed, so the caller can tell "restored" from "nothing there".
     */
    static int apply() {
        int count = 0;
        for (Source source : readAll()) {
            if (source == null || !source.isValid()) continue;
            source.toConfig().update();
            count++;
        }
        return count;
    }

    private static Source read(int type) {
        for (Source source : readAll()) if (source != null && source.type == type && source.isValid()) return source;
        return null;
    }

    private static List<Source> readAll() {
        File file = getFile();
        if (!file.isFile() || file.length() <= 0 || file.length() > MAX_BYTES) return Collections.emptyList();
        try {
            Snapshot snapshot = App.gson().fromJson(Path.read(file), Snapshot.class);
            if (snapshot == null || snapshot.version != VERSION || snapshot.sources == null) return Collections.emptyList();
            return snapshot.sources;
        } catch (RuntimeException e) {
            Logger.t(TAG).e(e, "Unable to read source bootstrap");
            return Collections.emptyList();
        }
    }

    private static File getFile() {
        return new File(new File(Path.root(), "TV"), FILE_NAME);
    }

    private static final class Snapshot {

        @SerializedName("version")
        private int version;
        @SerializedName("sources")
        private List<Source> sources;

        private static Snapshot create() {
            Snapshot snapshot = new Snapshot();
            snapshot.version = VERSION;
            snapshot.sources = new ArrayList<>();
            snapshot.add(0);
            snapshot.add(1);
            return snapshot;
        }

        private void add(int type) {
            String url = Prefers.getString("config_" + type);
            Config config = TextUtils.isEmpty(url) ? null : AppDatabase.get().getConfigDao().find(url, type);
            if (config != null && !config.isEmpty()) sources.add(new Source(config));
        }
    }

    private static final class Source {

        @SerializedName("type")
        private int type;
        @SerializedName("url")
        private String url;
        @SerializedName("name")
        private String name;
        @SerializedName("home")
        private String home;
        @SerializedName("parse")
        private String parse;

        private Source() {
        }

        private Source(Config config) {
            type = config.getType();
            url = config.getUrl();
            name = config.getName();
            home = config.getHome();
            parse = config.getParse();
        }

        private boolean isValid() {
            return (type == 0 || type == 1) && !TextUtils.isEmpty(url);
        }

        private Config toConfig() {
            Config config = TextUtils.isEmpty(name) ? Config.find(url, type) : Config.find(url, name, type);
            config.setHome(home);
            config.setParse(parse);
            return config;
        }
    }
}
