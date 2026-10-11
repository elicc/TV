#!/usr/bin/env python3
"""Manage ONLY the TCL bridge accessibility grant; app opt-in stays in the TV UI.
No automatic device selection. Run `--serial HOST:5555 status|enable|disable`.
"""
import argparse
import json
import os
from pathlib import Path
import shlex
import subprocess
import tempfile

PACKAGE = "com.elicc.android.tv"
COMPONENT = PACKAGE + "/com.fongmi.android.tv.service.TclSearchBridgeService"
HOME = PACKAGE + "/com.fongmi.android.tv.ui.activity.HomeActivity"
KEY = "enabled_accessibility_services"


def canonical(component):
    package, separator, name = component.partition("/")
    return package + separator + (package + name if name.startswith(".") else name)


def merge_services(raw, enable):
    services = []
    seen = set()
    for item in (raw if raw not in ("null", "") else "").split(":"):
        if not item:
            continue
        name = canonical(item)
        if name == COMPONENT or name in seen:
            continue
        services.append(item)
        seen.add(name)
    if enable:
        services.append(COMPONENT)
    return ":".join(services)


class Device:
    def __init__(self, serial):
        self.serial = serial

    def shell(self, *args):
        # adb shell joins arguments through a remote shell; quote there too.
        result = subprocess.run(["adb", "-s", self.serial, "shell", shlex.join(args)],
                                text=True, capture_output=True, timeout=20, check=True)
        if "Error:" in result.stdout or "Exception" in result.stderr:
            raise RuntimeError(result.stdout + result.stderr)
        return result.stdout.strip()

    def read_settings(self):
        return {key: self.shell("settings", "get", "secure", key)
                for key in (KEY, "accessibility_enabled")}

    def status(self):
        values = self.read_settings()
        values["configured"] = COMPONENT in [canonical(s) for s in values[KEY].split(":")]
        # Do not equate a grant with a live binding. The TV UI reports connected state.
        dump = self.shell("dumpsys", "accessibility")
        start = dump.find("Bound services:")
        end = dump.find("Enabled services:", start)
        values["bound_services_evidence"] = dump[start:end].strip() if start >= 0 and end > start else "unavailable"
        values["app_opt_in"] = "Check the switch and Ready state in TV Settings > TCL search bridge"
        return values



def backup(values, path):
    if path is None:
        fd, name = tempfile.mkstemp(prefix="tcl-bridge-settings-", suffix=".json")
    else:
        name = str(path)
        fd = os.open(name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as stream:
        json.dump(values, stream, ensure_ascii=False, indent=2)
    return name


def change(device, enable, backup_path=None):
    before = device.read_settings()
    receipt = backup({"serial": device.serial, **before}, backup_path)
    if enable:
        info = device.shell("dumpsys", "package", PACKAGE)
        if "com.fongmi.android.tv.service.TclSearchBridgeService" not in info:
            raise RuntimeError("Bridge service is not installed; install the matching TV APK first")
        device.shell("am", "start", "-W", "-n", HOME)
    # Re-read after launch: OEM services may have changed the list in the meantime.
    current = device.read_settings()
    merged = merge_services(current[KEY], enable)
    device.shell("settings", "put", "secure", KEY, merged)
    if enable or not merged:
        device.shell("settings", "put", "secure", "accessibility_enabled", "1" if enable else "0")
    after = device.read_settings()
    configured = COMPONENT in [canonical(s) for s in after[KEY].split(":")]
    retained = {canonical(s) for s in merged.split(":") if s}
    actual = {canonical(s) for s in after[KEY].split(":") if s}
    if configured != enable or not retained.issubset(actual):
        raise RuntimeError("OEM changed the accessibility grant; inspect status before retrying")
    return {"backup": receipt, "configured": configured,
            "note": "Grant changed only. Verify the application opt-in and live binding in TV settings."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--backup", type=Path, help="Exclusive new settings backup file (default: private temp file)")
    parser.add_argument("action", choices=("status", "enable", "disable"))
    args = parser.parse_args()
    device = Device(args.serial)
    try:
        result = device.status() if args.action == "status" else change(device, args.action == "enable", args.backup)
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        parser.exit(1, str(error) + "\n")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
