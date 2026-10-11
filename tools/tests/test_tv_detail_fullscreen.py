"""Source contracts for the independent synopsis affordance during fullscreen."""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java"


class DetailFullscreenTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.code = ACTIVITY.read_text()

    def method(self, signature):
        start = self.code.index(signature)
        return self.code[start:self.code.index("\n    }", start)]

    def test_async_overflow_cannot_show_more_in_fullscreen(self):
        method = self.method("private void updateDescriptionOverflow()")
        self.assertIn("boolean expandable = !isFullscreen() &&", method)
        self.assertIn("isVisible(mBinding.description)", method)
        self.assertIn("getEllipsisCount", method)
        self.assertIn("descriptionMore.setVisibility(expandable ? View.VISIBLE : View.GONE)", method)

    def test_transition_hides_more_before_scheduling_detail_restore(self):
        method = self.method("private void setFullscreen(boolean fullscreen)")
        state = method.index("this.fullscreen = fullscreen;")
        hide = method.index("mBinding.descriptionMore.setVisibility(View.GONE);")
        restore = method.index("if (!fullscreen) mBinding.description.post(this::updateDescriptionOverflow);")
        self.assertLess(state, hide)
        self.assertLess(hide, restore)

    def test_both_transitions_use_shared_fullscreen_state(self):
        for method, value in (("enterFullscreen", "true"), ("exitFullscreen", "false")):
            self.assertIn(f"setFullscreen({value});", self.method(f"private void {method}()"))


if __name__ == "__main__":
    unittest.main()
