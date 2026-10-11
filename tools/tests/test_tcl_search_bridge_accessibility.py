from pathlib import Path
import importlib.util
import tempfile
import unittest

PATH = Path(__file__).resolve().parents[1] / "android/tcl_search_bridge_accessibility.py"
spec = importlib.util.spec_from_file_location("bridge_tool", PATH)
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)

class FakeDevice:
    serial = "test-device"
    def __init__(self, services):
        self.values = {bridge.KEY: services, "accessibility_enabled": "1"}
        self.calls = []
    def read_settings(self): return self.values.copy()
    def shell(self, *args):
        self.calls.append(args)
        if args[:2] == ("dumpsys", "package"): return bridge.COMPONENT
        if args[:3] == ("settings", "put", "secure"): self.values[args[3]] = args[4]
        return ""

class GrantTests(unittest.TestCase):
    def test_merge_preserves_oem_and_deduplicates(self):
        original = "oem/.Service:other/other.S:oem/oem.Service"
        result = bridge.merge_services(original, True)
        self.assertEqual(result, "oem/.Service:other/other.S:" + bridge.COMPONENT)
        self.assertEqual(bridge.merge_services(result, True), result)
        self.assertEqual(bridge.merge_services(result, False), "oem/.Service:other/other.S")

    def test_empty(self):
        for raw in ("", "null"):
            self.assertEqual(bridge.merge_services(raw, True), bridge.COMPONENT)
            self.assertEqual(bridge.merge_services(raw, False), "")

    def test_disable_does_not_disable_other_services(self):
        device = FakeDevice("oem/.Service:" + bridge.COMPONENT)
        with tempfile.TemporaryDirectory() as directory:
            result = bridge.change(device, False, Path(directory) / "backup.json")
            self.assertFalse(result["configured"])
            self.assertEqual(device.values[bridge.KEY], "oem/.Service")
            self.assertFalse(any(c[:4] == ("settings", "put", "secure", "accessibility_enabled") for c in device.calls))

    def test_enable_requires_no_global_overwrite(self):
        device = FakeDevice("oem/.Service")
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "backup.json"
            bridge.change(device, True, target)
            self.assertEqual(target.stat().st_mode & 0o777, 0o600)
            self.assertTrue(device.values[bridge.KEY].startswith("oem/.Service:"))
            with self.assertRaises(FileExistsError): bridge.backup({}, target)

if __name__ == "__main__": unittest.main()
