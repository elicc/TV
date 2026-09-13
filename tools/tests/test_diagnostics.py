#!/usr/bin/env python3
"""Static contracts for durable on-device application diagnostics."""
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/fongmi/android/tv"


class DiagnosticTests(unittest.TestCase):
    def test_startup_installs_persistence_before_crash_handler(self):
        startup = (JAVA / "Startup.java").read_text()
        init = startup.index("DiagnosticStore.init(context)")
        caoc = startup.index("CaocConfig.Builder.create()")
        crash = startup.index("CrashRecorder.install()")
        adapter = startup.index("new PersistentLogAdapter()")
        self.assertLess(init, caoc)
        self.assertLess(caoc, crash)
        self.assertLess(crash, adapter)
        self.assertIn("ExitInfoCollector.collect(context)", startup)

    def test_store_is_bounded_pullable_and_redacted(self):
        store = (JAVA / "diagnostic/DiagnosticStore.java").read_text()
        self.assertIn('getExternalFilesDir("diagnostics")', store)
        self.assertIn("MAX_LOG_BYTES = 2L * 1024 * 1024", store)
        self.assertIn("MAX_LOG_FILES = 8", store)
        self.assertIn("MAX_REPORT_FILES = 20", store)
        self.assertIn("FileUtil.writeAtomically", store)
        self.assertIn("output.getFD().sync()", (ROOT / "app/src/main/java/com/fongmi/android/tv/utils/FileUtil.java").read_text())
        redactor = (JAVA / "diagnostic/LogRedactor.java").read_text().lower()
        for secret in ["authorization", "cookie", "token", "api[_-]?key", "password"]:
            self.assertIn(secret, redactor)

    def test_redactor_removes_common_credentials(self):
        source = JAVA / "diagnostic/LogRedactor.java"
        harness = """
import com.fongmi.android.tv.diagnostic.LogRedactor;
public class RedactorHarness {
    public static void main(String[] args) {
        String input = "Authorization: Bearer abc.def\\nCookie: sid=secret; uid=7\\n"
                + "https://user:pass@example.com/a?token=query-secret&api_key=key-secret "
                + "{\\\"password\\\":\\\"json-secret\\\"}";
        String output = LogRedactor.redact(input);
        for (String secret : new String[]{"abc.def", "sid=secret", "user:pass", "query-secret", "key-secret", "json-secret"}) {
            if (output.contains(secret)) throw new AssertionError(output);
        }
    }
}
"""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            test = root / "RedactorHarness.java"
            test.write_text(harness)
            subprocess.run(["javac", "-d", root, source, test], check=True)
            subprocess.run(["java", "-cp", root, "RedactorHarness"], check=True)

    def test_crash_is_written_synchronously_before_delegation(self):
        crash = (JAVA / "diagnostic/CrashRecorder.java").read_text()
        write = crash.index("recordCrash(thread, error)")
        delegate = crash.index("next.uncaughtException(thread, error)")
        self.assertLess(write, delegate)
        self.assertNotIn("Task.execute", crash)

    def test_android_11_exit_recovery_covers_failure_reasons(self):
        collector = (JAVA / "diagnostic/ExitInfoCollector.java").read_text()
        self.assertIn("Build.VERSION_CODES.R", collector)
        for reason in [
            "REASON_CRASH",
            "REASON_CRASH_NATIVE",
            "REASON_ANR",
            "REASON_SIGNALED",
            "REASON_LOW_MEMORY",
            "REASON_INITIALIZATION_FAILURE",
            "REASON_OTHER",
        ]:
            self.assertIn(reason, collector)
        self.assertIn("MAX_TRACE_BYTES", collector)

    def test_pull_instructions_match_application_id(self):
        docs = (ROOT / "docs/diagnostics.md").read_text()
        self.assertIn("com.elicc.android.tv/files/diagnostics", docs)
        self.assertIn("adb -s <TV_IP>:5555 pull", docs)
        self.assertIn("uninstalled", docs)


if __name__ == "__main__":
    unittest.main()
