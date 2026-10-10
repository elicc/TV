#!/usr/bin/env python3
"""Regression contracts for storage-hostile Android TV firmware."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/fongmi/android/tv"
LEANBACK = ROOT / "app/src/leanback/java/com/fongmi/android/tv"


class StorageCompatibilityTests(unittest.TestCase):
    def test_all_files_access_distinguishes_unsupported_firmware(self):
        source = (MAIN / "utils/PermissionUtil.java").read_text()
        self.assertIn("enum AllFilesAccess", source)
        for state in ["GRANTED", "REQUESTABLE", "UNSUPPORTED"]:
            self.assertIn(state, source)
        self.assertIn("allFilesAccess(FragmentActivity activity)", source)

    def test_source_setup_keeps_manual_configuration_available(self):
        source = (LEANBACK / "ui/dialog/ConfigDialog.java").read_text()
        positive = source.split("private void onPositive(View view)", 1)[1].split("private boolean isDialogActive()", 1)[0]
        self.assertIn("requestFileChooser()", positive)
        self.assertNotIn("restore", positive.lower())
        request = source.split("private void requestVaultPermission()", 1)[1].split("private void checkRestoreSource()", 1)[0]
        self.assertIn("AllFilesAccess.UNSUPPORTED", request)
        self.assertIn("tv_vault_unsupported", request)

    def test_settings_reports_unsupported_firmware_separately(self):
        source = (LEANBACK / "ui/activity/SettingActivity.java").read_text()
        self.assertIn("AllFilesAccess.UNSUPPORTED", source)
        self.assertIn("tv_vault_status_unsupported", source)

    def test_managed_installer_grants_appop_before_first_launch(self):
        script = (ROOT / "tools/android/install_leanback_debug.sh").read_text()
        grant = script.index("cmd appops set")
        launch = script.index("am start")
        self.assertLess(grant, launch)
        self.assertIn("MANAGE_EXTERNAL_STORAGE allow", script)
        self.assertIn("device_serial", script)


if __name__ == "__main__":
    unittest.main()
