# TV application diagnostics

The application persists its own runtime log, Java crash reports, and Android 11+
historical process-exit reports under its external app directory. This storage
does not require the all-files permission and survives process, device, and app
upgrade restarts. Android removes it when the app is uninstalled.

Default path for the current application ID:

```text
/storage/emulated/0/Android/data/com.elicc.android.tv/files/diagnostics/
```

Connect to the TV and pull the complete bundle:

```bash
adb connect <TV_IP>:5555
adb -s <TV_IP>:5555 pull \
  /storage/emulated/0/Android/data/com.elicc.android.tv/files/diagnostics \
  ./tv-diagnostics
```

Files include:

- `app.log` and `app.N.log`: current and rotated application logs.
- `crash-*.txt`: synchronously written Java crash reports with recent app logs.
- `exit-*.txt`: Android 11+ historical ANR, native crash, low-memory, or other
  relevant process-exit metadata.
- `exit-*-trace.bin`: the raw system trace when Android exposes one.

Application log files rotate at 2 MiB and retain eight archives. Diagnostic
reports retain the newest twenty files. Credentials in common authorization,
cookie, token, API-key, and password fields are redacted before persistence.

These files contain application-owned diagnostics only. A regular Android app
cannot persist complete system-wide logcat, kernel, or other-process output.
