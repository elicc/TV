# Managed TV installation

Some Android 11 TV images expose `MANAGE_EXTERNAL_STORAGE` as an app-op but ship no Settings
activity for granting it. On those images the app cannot self-open the permission page. Use the
managed installer for a clean install or an uninstall/reinstall cycle:

```sh
./tools/android/install_leanback_debug.sh 192.168.1.22:5555
```

The script installs the ABI-matched Leanback APK, grants the app-op **before the first launch**,
and then starts `HomeActivity`. This ordering is required for `ConfigVault` to read
`/storage/emulated/0/TV/source-bootstrap.json` and the newest `TV/backup/*.tv` snapshot during the
guarded cold-start restore.

The grant is revoked by Android on uninstall, so repeat the script after every clean reinstall.
Never delete `/storage/emulated/0/TV` when testing recovery.
