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

print()  { printf '\033[1;36m[deploy]\033[0m %s\n' "$*"; }
warn()   { printf '\033[1;33m[deploy]\033[0m %s\n' "$*" >&2; }
fail()   { printf '\033[1;31m[deploy]\033[0m %s\n' "$*" >&2; exit "${2:-1}"; }

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
adb -s "$SERIAL" get-state >/dev/null 2>&1 || fail "device $SERIAL not reachable" 1
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
APK_BYTES=$(stat -f%z "$APK" 2>/dev/null || stat -c%s "$APK")
print "APK built: $APK (${APK_BYTES} bytes)"

# 5. Install
PKG="com.elicc.android.tv"
INSTALLED_VERSION=$(adb -s "$SERIAL" shell dumpsys package "$PKG" 2>/dev/null | grep -oE 'versionCode=[0-9]+' | head -1 | cut -d= -f2 || true)
print "Installed versionCode: ${INSTALLED_VERSION:-<none>}"

INSTALL_FLAGS=(-r -t)
[[ "$FORCE_REINSTALL" == true ]] && INSTALL_FLAGS=(-r -t -d -g)
adb -s "$SERIAL" install "${INSTALL_FLAGS[@]}" "$APK" | tail -3 || fail "adb install failed" 3
print "Install complete."

# 6. Launch verify
if [[ "$VERIFY_LAUNCH" == true ]]; then
  print "Launching $PKG/.ui.activity.HomeActivity ..."
  adb -s "$SERIAL" shell am start -n "$PKG/.ui.activity.HomeActivity" -W >/dev/null 2>&1 || warn "am start returned non-zero (TV may still be booting)"
  sleep 1
  TOP_ACTIVITY=$(adb -s "$SERIAL" shell dumpsys activity activities 2>/dev/null | grep -oE 'mResumedActivity: ActivityRecord\{[^ ]+ [^/]+/(com\.fongmi\.android\.tv|com\.elicc\.android\.tv)[^}]*\}' | head -1 || true)
  if [[ -n "$TOP_ACTIVITY" ]]; then
    print "Top activity: $TOP_ACTIVITY"
  else
    warn "Could not confirm top activity (dumpsys limited on some TVs)"
  fi
fi

print "Done."