"""Host-side wiring contracts; these do not substitute for Android lifecycle/UI tests."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'app/src/leanback/java/com/fongmi/android/tv'
MAIN = ROOT / 'app/src/main/java/com/fongmi/android/tv'


class SourceDialogRecoveryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dialog = (JAVA / 'ui/dialog/ConfigDialog.java').read_text()
        cls.vault = (MAIN / 'db/ConfigVault.java').read_text()

    def method(self, name, next_name):
        return self.dialog.split(name, 1)[1].split(next_name, 1)[0]

    def test_open_checks_access_once_per_dialog_not_per_resume(self):
        resume = self.method('public void onResume()', 'public void onStart()')
        self.assertIn('if (!vaultEntryHandled) beginVaultFlow()', resume)
        entry = self.method('private void beginVaultFlow()', 'private void showVaultPrompt()')
        self.assertIn('vaultEntryHandled = true', entry)
        self.assertIn('ConfigVault.isWritable()', entry)
        self.assertIn('showVaultPrompt()', entry)
        self.assertNotIn('save()', entry)

    def test_permission_result_is_read_from_os_and_only_checks_backup(self):
        result = self.dialog.split('private final ActivityResultLauncher<Intent> vaultPermissionLauncher', 1)[1]
        result = result.split('private final ActivityResultLauncher<Intent> launcher', 1)[0]
        self.assertIn('ConfigVault.isWritable()', result)
        self.assertIn('checkRestoreSource()', result)
        self.assertNotIn('restoreVault()', result)
        self.assertNotIn('save()', result)

    def test_decline_or_back_stays_manual_and_consent_is_required_to_restore(self):
        prompt = self.method('private void showVaultPrompt()', 'private void requestVaultPermission()')
        self.assertIn('setNegativeButton(R.string.tv_vault_manual, (dialog, which) -> vaultPrompt = 0)', prompt)
        self.assertIn('setOnCancelListener(dialog -> vaultPrompt = 0)', prompt)
        self.assertIn('else restoreVault()', prompt)
        scan = self.method('private void checkRestoreSource()', 'private void restoreVault()')
        self.assertIn('case BACKUP -> 2', scan)
        self.assertIn('case BOOTSTRAP -> 3', scan)
        self.assertIn('default -> 0', scan)
        self.assertNotIn('ConfigVault.restore(', scan)

    def test_discovery_validates_full_backups_and_lightweight_sources(self):
        self.assertIn('filter(BackupManager::isRestorable)', self.vault)
        bootstrap = (MAIN / 'db/SourceBootstrap.java').read_text()
        exists = bootstrap.split('static boolean exists()', 1)[1].split('/**', 1)[0]
        self.assertIn('readSnapshot()', exists)
        self.assertIn('source.isValid()', exists)
        self.assertNotIn('toConfig()', exists)  # Config.find can insert into Room.

    def test_refusal_cannot_be_bypassed_by_next_cold_start(self):
        entry = self.method('private void beginVaultFlow()', 'private void showVaultPrompt()')
        self.assertIn('ConfigVault.deferAutoRestore()', entry)
        defer = self.vault.split('public static void deferAutoRestore()', 1)[1].split('/**', 1)[0]
        self.assertIn('putBoolean(KEY_ATTEMPTED, true)', defer)

    def test_confirm_restores_full_snapshot_and_fills_source_without_dismissing(self):
        restore = self.method('private void restoreVault()', 'private boolean isSourceType()')
        self.assertIn('ConfigVault.restore(', restore)
        self.assertNotIn('SourceBootstrap.find(', self.dialog)
        for statement in ['VodConfig.get().init()', 'LiveConfig.get().init()', 'WallConfig.get().init()',
                          'RefreshEvent.history()', 'RefreshEvent.keep()', 'binding.name.setText',
                          'binding.text.setText', 'ConfigEvent.common()']:
            self.assertIn(statement, restore)
        self.assertNotIn('dismiss()', restore)

    def test_round_trip_keeps_source_type_draft_and_prompt_state(self):
        for key in ['config.type', 'config.edit', 'config.url', 'config.text', 'config.name',
                    'vault.entryHandled', 'vault.checking', 'vault.prompt']:
            self.assertGreaterEqual(self.dialog.count('"' + key + '"'), 2, key)
        self.assertIn('public void onDestroyView()', self.dialog)
        self.assertIn('vaultDialog.dismiss()', self.dialog)

    def test_confirmation_copy_distinguishes_full_and_source_only_backups(self):
        keys = ['tv_vault_permission_title', 'tv_vault_permission_body', 'tv_vault_manual',
                'tv_vault_restore_accept', 'tv_vault_restore_confirm', 'tv_vault_restore_sources_confirm']
        for locale in ['values', 'values-zh-rCN', 'values-zh-rTW']:
            path = ROOT / 'app/src/leanback/res' / locale / 'tv_home_strings.xml'
            strings = {item.attrib['name']: item.text for item in ET.parse(path).getroot()}
            for key in keys:
                self.assertTrue(strings.get(key), (locale, key))


if __name__ == '__main__':
    unittest.main()
