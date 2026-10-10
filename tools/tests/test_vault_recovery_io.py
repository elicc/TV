"""Execute production ConfigVault selection/consent logic with deterministic host IO doubles.

Android permission windows/lifecycle still require device validation. This suite isolates vault
selection from Android, Gson and Room; it does not assert that an on-device backup was restored.
"""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'app/src/main/java/com/fongmi/android/tv/db'

STUBS = {
    'android/content/SharedPreferences.java': '''package android.content;
public class SharedPreferences {
    private final java.util.Map<String, Object> values = new java.util.HashMap<>();
    public String getString(String k, String d) { return (String) values.getOrDefault(k, d); }
    public long getLong(String k, long d) { return (long) values.getOrDefault(k, d); }
    public boolean getBoolean(String k, boolean d) { return (boolean) values.getOrDefault(k, d); }
    public Editor edit() { return new Editor(); }
    public class Editor {
        public Editor putString(String k, String v) { values.put(k, v); return this; }
        public Editor putLong(String k, long v) { values.put(k, v); return this; }
        public Editor putBoolean(String k, boolean v) { values.put(k, v); return this; }
        public void apply() {}
    }
}''',
    'android/content/Context.java': '''package android.content;
public class Context {
    public static final int MODE_PRIVATE = 0;
    private final SharedPreferences prefs = new SharedPreferences();
    public SharedPreferences getSharedPreferences(String k, int mode) { return prefs; }
}''',
    'android/os/Build.java': '''package android.os;
public class Build {
    public static class VERSION { public static int SDK_INT = 30; }
    public static class VERSION_CODES { public static final int R = 30; }
}''',
    'android/os/Environment.java': '''package android.os;
public class Environment {
    public static boolean writable;
    public static boolean isExternalStorageManager() { return writable; }
}''',
    'com/github/catvod/Init.java': '''package com.github.catvod;
public class Init {
    public static android.content.Context instance = new android.content.Context();
    public static android.content.Context context() { return instance; }
}''',
    'com/orhanobut/logger/Logger.java': '''package com.orhanobut.logger;
public class Logger {
    public static Logger t(String tag) { return new Logger(); }
    public void e(String msg) {}
}''',
    'com/fongmi/android/tv/App.java': '''package com.fongmi.android.tv;
public class App { public static void post(Runnable work) { work.run(); } }''',
    'com/fongmi/android/tv/utils/Task.java': '''package com.fongmi.android.tv.utils;
public class Task { public static void executeSerial(Runnable work) { work.run(); } }''',
    'com/fongmi/android/tv/db/AppDatabase.java': '''package com.fongmi.android.tv.db;
public class AppDatabase {
    public static boolean configured;
    public static AppDatabase get() { return new AppDatabase(); }
    public AppDatabase getConfigDao() { return this; }
    public java.util.List<String> findAll() { return configured ? java.util.List.of("source") : java.util.List.of(); }
}''',
    'com/fongmi/android/tv/db/BackupManager.java': '''package com.fongmi.android.tv.db;
import java.io.File;
import java.util.*;
public class BackupManager {
    public static List<File> files = List.of();
    public static Set<File> valid = Set.of();
    public static File restored;
    public static int reads, writes;
    public static List<File> getFiles() { reads++; return files; }
    static boolean isRestorable(File file) { return valid.contains(file); }
    static boolean restoreNow(File file) { restored = file; return valid.contains(file); }
    static boolean save() { writes++; return true; }
}''',
    'com/fongmi/android/tv/db/SourceBootstrap.java': '''package com.fongmi.android.tv.db;
public class SourceBootstrap {
    public static boolean available;
    public static int applies, metadata, writes;
    static boolean exists() { return available; }
    static int apply() { applies++; return available ? 1 : 0; }
    static void applyMetadata() { metadata++; }
    static boolean write() { writes++; return true; }
}''',
}

HARNESS = '''package com.fongmi.android.tv.db;
import java.io.File;
import java.util.*;
import com.github.catvod.Init;
import android.os.Environment;

public class RecoveryHarness {
    private static VaultPolicy.Source result;
    private static final File NEW = new File("new.tv"), OLD = new File("old.tv");
    private static void reset() {
        Init.instance = new android.content.Context();
        Environment.writable = true;
        BackupManager.files = List.of(NEW, OLD);
        BackupManager.valid = Set.of(OLD);
        BackupManager.restored = null;
        BackupManager.reads = BackupManager.writes = 0;
        SourceBootstrap.available = true;
        SourceBootstrap.applies = SourceBootstrap.metadata = SourceBootstrap.writes = 0;
        AppDatabase.configured = false;
        result = null;
    }
    private static void report(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
        System.out.println(name + "=PASS");
    }
    private static boolean untouched() {
        return BackupManager.restored == null && BackupManager.writes == 0
                && SourceBootstrap.applies == 0 && SourceBootstrap.writes == 0;
    }
    public static void main(String[] args) {
        reset();
        ConfigVault.findRestoreSource(s -> result = s);
        report("probe_full_is_read_only", result == VaultPolicy.Source.BACKUP && untouched());
        ConfigVault.restore(s -> result = s);
        report("skips_corrupt_newest", result == VaultPolicy.Source.BACKUP && OLD.equals(BackupManager.restored));
        report("full_keeps_metadata_overlay", SourceBootstrap.metadata == 1 && SourceBootstrap.applies == 0);

        reset();
        BackupManager.valid = Set.of();
        ConfigVault.findRestoreSource(s -> result = s);
        report("bootstrap_probe_is_read_only", result == VaultPolicy.Source.BOOTSTRAP && untouched());
        ConfigVault.restore(s -> result = s);
        report("bootstrap_restore", result == VaultPolicy.Source.BOOTSTRAP && SourceBootstrap.applies == 1);

        reset();
        BackupManager.valid = Set.of();
        SourceBootstrap.available = false;
        ConfigVault.findRestoreSource(s -> result = s);
        report("no_valid_backup_no_offer", result == VaultPolicy.Source.NONE && untouched());

        reset();
        Environment.writable = false;
        ConfigVault.findRestoreSource(s -> result = s);
        report("denied_no_probe_or_write", result == VaultPolicy.Source.NONE && BackupManager.reads == 0 && untouched());
        Environment.writable = true;
        ConfigVault.findRestoreSource(s -> result = s);
        report("grant_enables_probe_not_restore", result == VaultPolicy.Source.BACKUP && untouched());

        reset();
        ConfigVault.deferAutoRestore();
        ConfigVault.restoreIfFresh(s -> result = s);
        report("refusal_blocks_cold_restore", result == VaultPolicy.Source.NONE && untouched());
        ConfigVault.findRestoreSource(s -> result = s);
        report("next_dialog_can_offer_again", result == VaultPolicy.Source.BACKUP && untouched());
        ConfigVault.restore(s -> result = s);
        report("later_explicit_accept_still_restores", result == VaultPolicy.Source.BACKUP && OLD.equals(BackupManager.restored));
    }
}
'''


class VaultRecoveryIoTests(unittest.TestCase):
    def test_selection_and_consent_against_production_vault(self):
        if not shutil.which('javac') or not shutil.which('java'):
            self.skipTest('JDK required')
        with tempfile.TemporaryDirectory(prefix='tv-vault-io-') as tmp:
            root = Path(tmp)
            sources = dict(STUBS)
            for name in ['ConfigVault', 'VaultPolicy']:
                sources[f'com/fongmi/android/tv/db/{name}.java'] = (MAIN / f'{name}.java').read_text()
            sources['com/fongmi/android/tv/db/RecoveryHarness.java'] = HARNESS
            for name, contents in sources.items():
                target = root / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(contents)
            subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(root / 'classes')]
                           + [str(root / name) for name in sources], check=True, capture_output=True)
            result = subprocess.run(['java', '-cp', str(root / 'classes'),
                                     'com.fongmi.android.tv.db.RecoveryHarness'],
                                    check=True, capture_output=True, text=True)
            self.assertEqual(11, result.stdout.count('=PASS'))


if __name__ == '__main__':
    unittest.main()
