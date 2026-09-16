#!/usr/bin/env python3
"""Behavioral test of the config-vault decision rules.

Run from any directory: python3 -m unittest tools.tests.test_config_vault
Requires only Python 3 and a JDK (javac/java on PATH).

Compiles the production `VaultPolicy` — which is deliberately free of every Android type —
together with a tiny harness into a temporary directory, runs it, and checks each rule
individually. Nothing here touches Gson, Room, the filesystem or the layout; the IO half of the
vault (`ConfigVault`, `SourceBootstrap`) is exercised on a device instead.

The rules pinned here are the ones whose regression is invisible: asking for file access when it
is not needed, restoring over data the viewer just created, or reporting a healthy vault after a
write that never landed.
"""

from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

HARNESS = r"""
import com.fongmi.android.tv.db.VaultPolicy;
import com.fongmi.android.tv.db.VaultPolicy.Source;
import com.fongmi.android.tv.db.VaultPolicy.State;

public class VaultPolicyHarness {

    public static void main(String[] args) {
        // --- classify: what an attempted write means ---
        report("classify.ok_writable", VaultPolicy.classify(true, true) == State.OK);
        report("classify.ok_unwritable", VaultPolicy.classify(true, false) == State.OK);
        report("classify.denied", VaultPolicy.classify(false, false) == State.NEED_PERMISSION);
        report("classify.io_error", VaultPolicy.classify(false, true) == State.IO_ERROR);

        // --- shouldPrompt: the one unprompted offer ---
        report("prompt.asks_when_needed", VaultPolicy.shouldPrompt(false, false));
        report("prompt.silent_when_granted", !VaultPolicy.shouldPrompt(true, false));
        report("prompt.silent_when_declined", !VaultPolicy.shouldPrompt(false, true));

        // --- shouldPromptRestore: fresh install recovery has its own one-time offer ---
        report("restore_prompt.fresh_empty_install", VaultPolicy.shouldPromptRestore(false, false, false));
        report("restore_prompt.silent_when_granted", !VaultPolicy.shouldPromptRestore(true, false, false));
        report("restore_prompt.silent_when_declined", !VaultPolicy.shouldPromptRestore(false, true, false));
        report("restore_prompt.never_over_a_source", !VaultPolicy.shouldPromptRestore(false, false, true));

        // --- canAutoRestore: never overwrite without proof it is safe ---
        report("restore.fresh_install", VaultPolicy.canAutoRestore(true, false, false));
        report("restore.needs_access", !VaultPolicy.canAutoRestore(false, false, false));
        report("restore.once_only", !VaultPolicy.canAutoRestore(true, true, false));
        report("restore.never_over_a_source", !VaultPolicy.canAutoRestore(true, false, true));

        // --- shouldReport: a failure is announced once, not on every retry ---
        report("report.first_failure", VaultPolicy.shouldReport(State.IO_ERROR, State.UNKNOWN));
        report("report.not_twice", !VaultPolicy.shouldReport(State.IO_ERROR, State.IO_ERROR));
        report("report.denial_is_not_a_fault", !VaultPolicy.shouldReport(State.NEED_PERMISSION, State.UNKNOWN));
        report("report.never_on_success", !VaultPolicy.shouldReport(State.OK, State.UNKNOWN));
        report("report.never_when_unknown", !VaultPolicy.shouldReport(State.UNKNOWN, State.UNKNOWN));

        // --- pickSource: the richer snapshot wins, and only if it exists ---
        report("source.prefers_backup", VaultPolicy.pickSource(true, true) == Source.BACKUP);
        report("source.falls_back", VaultPolicy.pickSource(false, true) == Source.BOOTSTRAP);
        report("source.none", VaultPolicy.pickSource(false, false) == Source.NONE);
    }

    private static void report(String name, boolean ok) {
        System.out.println(name + "=" + (ok ? "PASS" : "FAIL"));
    }
}
"""


class VaultPolicyTests(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        for command in ("javac", "java"):
            if shutil.which(command) is None:
                raise unittest.SkipTest(f"Required JDK command not found: {command}")
        repository = Path(__file__).resolve().parents[2]
        policy = repository / "app/src/main/java/com/fongmi/android/tv/db/VaultPolicy.java"
        cls._tmp = tempfile.TemporaryDirectory(prefix="tv-vault-policy-")
        root = Path(cls._tmp.name)
        sources = {"VaultPolicyHarness.java": HARNESS,
                   "com/fongmi/android/tv/db/VaultPolicy.java": policy.read_text()}
        for name, source in sources.items():
            target = root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(source)
        classes = root / "classes"
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(classes)]
                       + [str(root / name) for name in sources], check=True)
        result = subprocess.run(["java", "-cp", str(classes), "VaultPolicyHarness"],
                                check=True, capture_output=True, text=True)
        cls._results = dict(line.split("=", 1) for line in result.stdout.split())

    @classmethod
    def tearDownClass(cls):
        cls._tmp.cleanup()

    def assertRule(self, name):
        self.assertIn(name, self._results, f"rule {name} did not run")
        self.assertEqual("PASS", self._results[name], f"rule {name} regressed")

    def test_a_successful_write_is_ok_regardless_of_access(self):
        self.assertRule("classify.ok_writable")
        self.assertRule("classify.ok_unwritable")

    def test_a_failed_write_is_denied_without_access_and_an_io_fault_with_it(self):
        # The whole point of classifying after the attempt: the same `false` means two very
        # different things, and only the access check tells them apart.
        self.assertRule("classify.denied")
        self.assertRule("classify.io_error")

    def test_the_offer_is_raised_once_and_only_when_it_is_needed(self):
        self.assertRule("prompt.asks_when_needed")
        self.assertRule("prompt.silent_when_granted")
        self.assertRule("prompt.silent_when_declined")

    def test_auto_restore_requires_access_a_first_attempt_and_an_empty_source_list(self):
        self.assertRule("restore.fresh_install")
        self.assertRule("restore.needs_access")
        self.assertRule("restore.once_only")
        self.assertRule("restore.never_over_a_source")

    def test_restore_offer_only_appears_for_an_empty_ungranted_fresh_install(self):
        self.assertRule("restore_prompt.fresh_empty_install")
        self.assertRule("restore_prompt.silent_when_granted")
        self.assertRule("restore_prompt.silent_when_declined")
        self.assertRule("restore_prompt.never_over_a_source")

    def test_only_a_real_fault_is_ever_reported(self):
        # A missing grant is the ordinary state of a fresh install and already has its own
        # voice in the one-time offer; announcing it as a failure would be a lie, and the
        # first thing a new viewer would see.
        self.assertRule("report.denial_is_not_a_fault")

    def test_a_failure_is_reported_once_per_distinct_cause(self):
        self.assertRule("report.first_failure")
        self.assertRule("report.not_twice")
        self.assertRule("report.never_on_success")
        self.assertRule("report.never_when_unknown")

    def test_the_richer_snapshot_wins_and_only_when_it_exists(self):
        self.assertRule("source.prefers_backup")
        self.assertRule("source.falls_back")
        self.assertRule("source.none")


if __name__ == "__main__":
    unittest.main()
