"""Run production SourceGridView against a Leanback insertion-offset model.

The stub models delayed selection shifts, not Android rendering. TCL observation
already confirmed that the initially selected footer moved to the list end.
"""
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
TV = ROOT / "app/src/leanback"
SOURCE = TV / "java/com/fongmi/android/tv/ui/custom/SourceGridView.java"
A = "{http://schemas.android.com/apk/res/android}"
STUBS = {
    "android/content/Context.java": "package android.content; public class Context {}",
    "android/util/AttributeSet.java": "package android.util; public interface AttributeSet {}",
    "android/graphics/Rect.java": "package android.graphics; public class Rect {}",
    "androidx/leanback/widget/HorizontalGridView.java": """package androidx.leanback.widget;
        import android.content.Context;import android.graphics.Rect;import android.util.AttributeSet;
        public class HorizontalGridView {
            public int selected,pendingInsertOffset,layoutRequests,focusTarget=-1;
            public boolean descendantFocused,selfFocused;
            public static class Adapter {public int count;public int getItemCount(){return count;}}
            public Adapter adapter=new Adapter();
            public HorizontalGridView(Context c,AttributeSet a,int style){}
            public Adapter getAdapter(){return adapter;}
            public void requestLayout(){layoutRequests++;}
            public void setSelectedPosition(int position){selected=position;}
            public boolean hasFocus(){return descendantFocused||selfFocused;}
            public boolean isFocused(){return selfFocused;}
            protected void onLayout(boolean c,int l,int t,int r,int b){selected+=pendingInsertOffset;pendingInsertOffset=0;}
            public boolean onRequestFocusInDescendants(int direction,Rect rect){
                focusTarget=selected;descendantFocused=true;selfFocused=false;return adapter!=null&&adapter.count>0;
            }
        }""",
}
HARNESS = """package com.fongmi.android.tv.ui.custom;
public class SourceFocusHarness {
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    static void layout(SourceGridView v){v.onLayout(false,0,0,800,100);}
    public static void main(String[] args){
        SourceGridView v=new SourceGridView(null);v.adapter.count=1;
        // The old footer was index zero. Inserting twenty sources shifts it at layout.
        v.pendingInsertOffset=20;v.adapter.count=21;v.resetToStartAfterLayout();
        check(v.layoutRequests==1,"deferred reset schedules layout");
        layout(v);check(v.selected==0&&!v.hasFocus(),"first results start left without stealing header focus");
        v.selected=5;layout(v);check(v.selected==5,"later layout preserves browsing position");
        v.descendantFocused=true;v.selected=8;v.onRequestFocusInDescendants(66,null);
        check(v.focusTarget==8,"internal horizontal focus never resets");
        v.descendantFocused=false;v.selected=20;v.onRequestFocusInDescendants(130,null);
        check(v.focusTarget==0,"DOWN entry ignores remembered footer");
        v.descendantFocused=false;v.selected=7;v.onRequestFocusInDescendants(33,null);
        check(v.focusTarget==0,"UP reentry ignores remembered source");
        v.descendantFocused=false;v.selfFocused=true;v.selected=20;v.onRequestFocusInDescendants(130,null);
        check(v.focusTarget==0,"grid self-focus transfers to first card");
        v.descendantFocused=false;v.adapter.count=0;v.selected=9;v.resetToStartAfterLayout();layout(v);
        check(v.selected==9,"empty intermediate layout retains pending reset");
        v.adapter.count=4;v.pendingInsertOffset=3;layout(v);check(v.selected==0,"deferred reset survives empty layout");
        v.descendantFocused=true;v.selected=3;v.pendingInsertOffset=6;v.adapter.count=10;
        v.resetToStartAfterLayout();layout(v);check(v.selected==0,"focused initial footer yields to first real source");
        System.out.println("PASS source focus: deferred offsets, external entry, internal navigation and empty layout");
    }
}
"""


class SourceFocusTests(unittest.TestCase):
    def test_production_focus_and_notification_offset_order(self):
        with tempfile.TemporaryDirectory() as temp:
            files = []
            for name, code in {**STUBS, "SourceFocusHarness.java": HARNESS}.items():
                p = Path(temp) / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(code)
                files.append(str(p))
            subprocess.run(["javac", "-d", temp, *files, str(SOURCE)], check=True, timeout=30)
            subprocess.run(["java", "-cp", temp, "com.fongmi.android.tv.ui.custom.SourceFocusHarness"], check=True, timeout=15)

    def test_only_source_rail_uses_reset_policy(self):
        root = ET.parse(TV / "res/layout/activity_video.xml").getroot()
        grids = [v for v in root.iter() if v.tag.endswith("SourceGridView")]
        self.assertEqual(["@+id/quick"], [v.get(A + "id") for v in grids])
        code = (TV / "java/com/fongmi/android/tv/ui/activity/VideoActivity.java").read_text()
        self.assertIn("if (firstItems) mBinding.quick.resetToStartAfterLayout();", code)
        self.assertNotIn("if (firstItems && !statusFocused) mBinding.quick.setSelectedPosition(0)", code)


if __name__ == "__main__":
    unittest.main()
