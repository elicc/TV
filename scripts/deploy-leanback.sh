#!/usr/bin/env bash
# Build the FongMi TV leanback flavor and install it onto a connected ADB
# device or emulator. Auto-detects ABI, runs :app:assembleLeanbackDebug,
# picks the right APK, installs, and verifies the launched activity.
#
# Usage:
#   scripts/deploy-leanback.sh                # build + install + launch verify
#   scripts/deploy-leanback.sh --no-launch    # skip launch verification
#   scripts/deploy-leanback.sh -s <serial>    # target a specific device
#   scripts/deploy-leanback.sh -f             # force reinstall (downgrade)
#   scripts/deploy-leanback.sh --abi <abi>    # override ABI (arm64-v8a|armeabi-v7a)
#
# Exit codes:
#   0 success
#   1 prerequisite missing (adb/jdk/sdk/aar)
#   2 build failed
#   3 install failed
#   4 launch verification failed

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

SERIAL=""
ABI=""
FORCE_REINSTALL=false
VERIFY_LAUNCH=true
DEVICE_READY_TIMEOUT=${DEPLOY_DEVICE_READY_TIMEOUT:-120}

print()  { printf '\033[1;36m[deploy]\033[0m %s\n' "$*"; }
warn()   { printf '\033[1;33m[deploy]\033[0m %s\n' "$*" >&2; }
fail()   { local message="${1:-}"; local code="${2:-1}"; printf '\033[1;31m[deploy]\033[0m %s\n' "$message" >&2; exit "$code"; }

wait_for_device_ready() {
  local deadline=$((SECONDS + DEVICE_READY_TIMEOUT))
  local state boot_completed package_service

  while (( SECONDS < deadline )); do
    state=$(adb -s "$SERIAL" get-state 2>/dev/null || true)
    if [[ "$state" == "device" ]]; then
      boot_completed=$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
      # get-state can report "device" while Android is still bringing up
      # system_server.  The package service must be available before dumpsys
      # or install is attempted, otherwise some TVs reboot during install.
      package_service=$(adb -s "$SERIAL" shell service check package 2>/dev/null | tr -d '\r' || true)
      if [[ "$boot_completed" == "1" && "$package_service" == *"Service package: found"* ]]; then
        return 0
      fi
    fi
    sleep 2
  done
  return 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -s|--serial)    SERIAL="$2"; shift 2 ;;
    --abi)          ABI="$2"; shift 2 ;;
    -f|--force)     FORCE_REINSTALL=true; shift ;;
    --no-launch)    VERIFY_LAUNCH=false; shift ;;
    -h|--help)      sed -n '2,18p' "$0"; exit 0 ;;
    *) fail "Unknown arg: $1" ;;
  esac
done

# 1. Prerequisites
command -v adb >/dev/null || fail "adb not found in PATH (install platform-tools)" 1
command -v java >/dev/null || fail "java not found (JDK 21+ required by build.gradle)" 1
JAVA_MAJOR=$(java -XshowSettings:properties -version 2>&1 | awk -F= '/java.specification.version/{gsub(/[^0-9]/,"",$2); print $2; exit}')
[[ -n "$JAVA_MAJOR" && "$JAVA_MAJOR" -ge 17 ]] || fail "JDK 17+ required (found major $JAVA_MAJOR)" 1
[[ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]] || {
  for cand in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" "$HOME/Android/sdk"; do
    [[ -d "$cand" ]] && export ANDROID_HOME="$cand" && warn "ANDROID_HOME inferred: $ANDROID_HOME" && break
  done
}
[[ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]] || fail "ANDROID_HOME / ANDROID_SDK_ROOT not set" 1
[[ -d app/libs ]] || fail "app/libs missing" 1
AARS=$(ls app/libs/lib-*.aar 2>/dev/null | wc -l | tr -d ' ')
[[ "$AARS" -gt 0 ]] || fail "no lib-*.aar in app/libs — README setup required" 1

# 2. Device selection
if [[ -z "$SERIAL" ]]; then
  DEVICES=$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | grep -v -e '^$' || true)
  if [[ -z "$DEVICES" ]]; then
    fail "no device listed by adb (run: adb connect <ip:5555>)" 1
  fi
  COUNT=$(printf '%s\n' "$DEVICES" | wc -l | tr -d ' ')
  if [[ "$COUNT" -eq 1 ]]; then
    SERIAL="$DEVICES"
  else
    warn "Multiple devices connected:"
    nl <<<"$DEVICES"
    fail "Use -s <serial> to disambiguate" 1
  fi
fi
wait_for_device_ready || fail "device $SERIAL not ready (boot/package service unavailable after ${DEVICE_READY_TIMEOUT}s)" 1
print "Target device: $SERIAL"

# 3. ABI detection
if [[ -z "$ABI" ]]; then
  ABI=$(adb -s "$SERIAL" shell getprop ro.product.cpu.abi | tr -d '\r')
  case "$ABI" in
    arm64-v8a|armeabi-v7a|x86|x86_64) ;;
    *) fail "unsupported ABI: $ABI" 1 ;;
  esac
fi
print "Target ABI: $ABI"

# 4. Build
print "Running :app:assembleLeanbackDebug ..."
./gradlew :app:assembleLeanbackDebug -q
APK="app/build/outputs/apk/leanback/debug/app-leanback-${ABI}-debug.apk"
[[ -f "$APK" ]] || fail "APK not found at $APK" 2
IDSIG="${APK}.idsig"
[[ -f "$IDSIG" ]] || fail "APK v4 signature not found at $IDSIG (rebuild with v4 signing enabled)" 2
APK_BYTES=$(stat -f%z "$APK" 2>/dev/null || stat -c%s "$APK")
print "APK built: $APK (${APK_BYTES} bytes)"

# The build can take long enough for a TV to reboot or finish booting after
# device selection. Re-check readiness immediately before talking to Package
# Manager instead of relying on the initial get-state result.
wait_for_device_ready || fail "device $SERIAL not ready after build (boot/package service unavailable after ${DEVICE_READY_TIMEOUT}s)" 1

# 5. Install
PKG="com.elicc.android.tv"
MAIN_ACTIVITY="com.fongmi.android.tv.ui.activity.HomeActivity"
INSTALLED_VERSION=$(adb -s "$SERIAL" shell dumpsys package "$PKG" 2>/dev/null | grep -oE 'versionCode=[0-9]+' | head -1 | cut -d= -f2 || true)
print "Installed versionCode: ${INSTALLED_VERSION:-<none>}"

INSTALL_FLAGS=(-r -t)
[[ "$FORCE_REINSTALL" == true ]] && INSTALL_FLAGS=(-r -t -d -g)
# This APK has a v4 .idsig because TCL MT5879's OverseasAppConfig rejects the
# streamed path with INSTALL_FAILED_VERIFICATION_FAILURE. Make the v4-aware
# transport explicit and wait until all blocks are materialized before adb
# exits; without --wait this TV can close the data loader and remove the app.
adb -s "$SERIAL" install --incremental --wait "${INSTALL_FLAGS[@]}" "$APK" || fail "adb install failed" 3
INSTALLED_PATH=$(adb -s "$SERIAL" shell pm path "$PKG" 2>/dev/null | tr -d '\r' | grep -m1 '^package:' || true)
[[ -n "$INSTALLED_PATH" ]] || fail "adb install reported success but $PKG is not registered" 3
print "Install complete."

# 6. Launch verify
if [[ "$VERIFY_LAUNCH" == true ]]; then
  print "Launching $PKG/$MAIN_ACTIVITY ..."
  adb -s "$SERIAL" shell am start -n "$PKG/$MAIN_ACTIVITY" -W >/dev/null 2>&1 || warn "am start returned non-zero (TV may still be booting)"
  sleep 1
  TOP_ACTIVITY=$(adb -s "$SERIAL" shell dumpsys activity activities 2>/dev/null | grep -oE 'mResumedActivity: ActivityRecord\{[^ ]+ [^/]+/(com\.fongmi\.android\.tv|com\.elicc\.android\.tv)[^}]*\}' | head -1 || true)
  if [[ -n "$TOP_ACTIVITY" ]]; then
    print "Top activity: $TOP_ACTIVITY"
  else
    warn "Could not confirm top activity (dumpsys limited on some TVs)"
  fi
fi

print "Done."
