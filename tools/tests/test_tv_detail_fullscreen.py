"""Source contracts for the independent synopsis affordance during fullscreen."""
from pathlib import Path
import unittest
import subprocess
import tempfile


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

    def test_fullscreen_transitions_do_not_dereference_an_unbound_player(self):
        for signature in ("private void enterFullscreen()", "private void hideControl()"):
            body = self.method(signature)
            self.assertIn("refreshFullscreenProgress();", body)
            self.assertNotIn("player().getPosition()", body)
        refresh = self.method("private void refreshFullscreenProgress()")
        self.assertIn("service() != null && isActivePlaybackOwner()", refresh)
        self.assertIn("C.TIME_UNSET", refresh)

    def test_input_waits_for_both_async_playback_connections(self):
        ready = self.method("private boolean canUsePlaybackControls()")
        for guard in ("service() != null", "controller() != null", "isBindingOwner()", "mHistory != null"):
            self.assertIn(guard, ready)
        for signature in ("private void showControl(View view)", "public void onKeyCenter()", "public void onKeyUp()", "public void onSeeking(long time)", "public void onSpeedUp()", "public void onSpeedEnd()"):
            body = self.method(signature)
            self.assertIn("if (!canUsePlaybackControls()) return;", body)
        for signature in ("private boolean onSeekBack()", "private boolean onSeekForward()"):
            self.assertIn("if (!canUsePlaybackControls()) return true;", self.method(signature))

    def test_production_progress_and_readiness_with_delayed_connections(self):
        production = "\n".join(self.method(signature) + "\n    }" for signature in (
            "private void refreshFullscreenProgress()", "private boolean canUsePlaybackControls()"))
        harness = r"""public class GateHarness {
 static class C {static final long TIME_UNSET=-1;}
 Object service,controller,mHistory;boolean binding,owner;int reads;long position,duration;
 Object service(){return service;}Object controller(){return controller;}
 boolean isBindingOwner(){return binding;}boolean isActivePlaybackOwner(){return binding&&owner;}
 GateHarness player(){if(service==null)throw new AssertionError("unbound player dereference");reads++;return this;}
 long getPosition(){return 1234;}long getDuration(){return 5678;}
 void updateFullscreenProgress(long p,long d){position=p;duration=d;}
 PRODUCTION
 static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
 public static void main(String[] args){
  GateHarness h=new GateHarness();h.refreshFullscreenProgress();check(h.reads==0&&h.position==C.TIME_UNSET);check(!h.canUsePlaybackControls());
  h.controller=new Object();h.refreshFullscreenProgress();check(h.reads==0);check(!h.canUsePlaybackControls());
  h.service=new Object();h.binding=true;h.mHistory=new Object();h.controller=null;check(!h.canUsePlaybackControls());
  h.controller=new Object();check(h.canUsePlaybackControls());h.refreshFullscreenProgress();check(h.reads==0);
  h.owner=true;h.refreshFullscreenProgress();check(h.position==1234&&h.duration==5678);
  h.service=null;h.binding=false;h.refreshFullscreenProgress();check(h.reads==2&&h.duration==C.TIME_UNSET);check(!h.canUsePlaybackControls());
  System.out.println("PASS delayed playback connection: "+checks+" checks");
 }
}""".replace("PRODUCTION", production)
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "GateHarness.java"
            source.write_text(harness)
            compiled = subprocess.run(["javac", str(source)], capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            run = subprocess.run(["java", "-cp", tmp, "GateHarness"], capture_output=True, text=True)
            self.assertEqual(0, run.returncode, run.stderr)
            print(run.stdout.strip())


if __name__ == "__main__":
    unittest.main()
