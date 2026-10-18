#!/usr/bin/env bash
set -euo pipefail

package_name="com.elicc.android.tv"
device_serial="${1:-}"
apk_path="${2:-}"

if [[ -z "$device_serial" ]]; then
    printf 'Usage: %s <device_serial> [apk_path]\n' "$0" >&2
    exit 2
fi

if [[ -z "$apk_path" ]]; then
    device_abi="$(adb -s "$device_serial" shell getprop ro.product.cpu.abi | tr -d '\r')"
    apk_path="app/build/outputs/apk/leanback/debug/app-leanback-${device_abi}-debug.apk"
fi

if [[ ! -f "$apk_path" ]]; then
    printf 'APK not found: %s\n' "$apk_path" >&2
    exit 2
fi

# TCL MT5879 only accepts the v4-signed incremental install, and serving it from adb can kill system_server
# when a block arrives late (see scripts/deploy-leanback.sh). Install from local storage instead.
remote_apk="/data/local/tmp/${package_name}.apk"
adb -s "$device_serial" push "$apk_path" "$remote_apk" >/dev/null
adb -s "$device_serial" push "$apk_path.idsig" "$remote_apk.idsig" >/dev/null
install_output="$(adb -s "$device_serial" shell cmd package install-incremental -r "$remote_apk" 2>&1 | tr -d '\r' || true)"
adb -s "$device_serial" shell rm -f "$remote_apk" "$remote_apk.idsig" || true
printf '%s\n' "$install_output"
[[ "$install_output" == *Success* ]] || exit 3

# Android 11 TV firmware may implement the app-op while omitting both standard Settings
# activities used to grant it. Provision the app before its first launch so ConfigVault can read
# a previous install's /storage/emulated/0/TV snapshot and run its guarded cold-start restore.
adb -s "$device_serial" shell cmd appops set "$package_name" MANAGE_EXTERNAL_STORAGE allow
mode="$(adb -s "$device_serial" shell cmd appops get "$package_name" MANAGE_EXTERNAL_STORAGE)"
if [[ "$mode" != *"allow"* ]]; then
    printf 'Unable to grant MANAGE_EXTERNAL_STORAGE: %s\n' "$mode" >&2
    exit 1
fi

adb -s "$device_serial" shell am force-stop "$package_name"
adb -s "$device_serial" shell am start -W \
    -n "$package_name/com.fongmi.android.tv.ui.activity.HomeActivity"
