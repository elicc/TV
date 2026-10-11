"""Compile the production scroll container against geometry-only Android stubs.

The stubs exercise focus/layout callbacks and pixel arithmetic, not Android rendering;
device screenshots and D-pad checks remain required for clipping validation.
"""
from pathlib import Path
import subprocess
import tempfile
import unittest
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "app/src/leanback/java/com/fongmi/android/tv/ui/custom/DetailScrollView.java"
A = "{http://schemas.android.com/apk/res/android}"
STUBS = {
    "android/content/Context.java": "package android.content; public class Context {}",
    "android/util/AttributeSet.java": "package android.util; public interface AttributeSet {}",
    "android/graphics/Rect.java": """package android.graphics; public class Rect {
        public int left,top,right,bottom;
        public void set(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
        public int height(){return bottom-top;}
    }""",
    "android/view/View.java": """package android.view; import android.graphics.Rect;
        public class View {
            public View parent; public int top,height; public boolean shown=true;
            public Object getParent(){return parent;}
            public int getHeight(){return height;}
            public boolean isShown(){return shown && (parent==null || parent.isShown());}
            public void getDrawingRect(Rect r){r.set(0,0,100,height);}
        }""",
    "androidx/core/widget/NestedScrollView.java": """package androidx.core.widget;
        import android.content.Context; import android.util.AttributeSet;
        import android.graphics.Rect; import android.view.View;
        public class NestedScrollView extends View {
            public View content,focused; public int y,paddingTop,paddingBottom,maxScroll=2000;
            public boolean dirty;
            public NestedScrollView(Context c,AttributeSet a,int s){}
            public int getChildCount(){return content==null?0:1;}
            public View getChildAt(int i){return content;}
            public View findFocus(){return focused;}
            public int getPaddingTop(){return paddingTop;}
            public int getPaddingBottom(){return paddingBottom;}
            public int getScrollY(){return y;} public int getScrollX(){return 0;}
            public boolean isLayoutRequested(){return dirty;}
            public void scrollBy(int x,int dy){scrollTo(0,y+dy);}
            public void scrollTo(int x,int value){y=Math.max(0,Math.min(maxScroll,value));}
            public void offsetDescendantRectToMyCoords(View v,Rect r){
                while(v!=this){r.top+=v.top;r.bottom+=v.top;v=v.parent;}
            }
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect r){return 0;}
            public void requestChildFocus(View c,View f){focused=f;}
            public boolean requestChildRectangleOnScreen(View c,Rect r,boolean i){return false;}
            protected void onLayout(boolean c,int l,int t,int r,int b){dirty=false;}
        }""",
}
HARNESS = """package com.fongmi.android.tv.ui.custom;
import android.view.View; import android.graphics.Rect;
public class ScrollHarness {
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    static View child(View p,int top,int height){View v=new View();v.parent=p;v.top=top;v.height=height;return v;}
    public static void main(String[] args){
        DetailScrollView s=new DetailScrollView(null,null);
        s.height=300;s.paddingTop=18;s.paddingBottom=8;
        s.content=child(s,18,1200);
        View row=child(s.content,0,72), item=child(row,18,36);
        s.y=36;s.requestChildFocus(s.content,item);
        check(s.y==0,"UP to first row restores whole row above top padding");
        row.top=400;s.y=480;s.requestChildFocus(s.content,item);
        check(s.y==400,"UP reveals row including label, not just short item");
        s.y=0;s.requestChildFocus(s.content,item);
        check(s.y==198,"DOWN reveals row bottom within bottom padding");
        s.requestChildFocus(s.content,item);check(s.y==198,"visible row does not jitter");
        row.top=650;s.onLayout(true,0,0,100,300);
        check(s.y==448,"async layout change keeps focused row visible");
        View section=child(s.content,700,500), grid=child(section,60,250), card=child(grid,10,230);
        s.y=0;s.requestChildFocus(s.content,card);
        check(s.y==736,"oversized section reveals card row rather than oversized heading section");
        s.y=800;s.requestChildRectangleOnScreen(s.content,new Rect(),false);
        check(s.y==760,"rectangle requests obey the same padding-aware row rule");
        s.focused=null;s.y=50;s.onLayout(true,0,0,100,300);
        check(s.y==50,"header focus does not scroll detail body");
        s.dirty=true;s.y=40;row.top=0;s.requestChildFocus(s.content,item);
        check(s.y==40,"dirty layout waits for measured bounds");
        s.onLayout(true,0,0,100,300);check(s.y==0,"deferred layout reveals first row");
        row.shown=false;s.y=30;s.onLayout(true,0,0,100,300);
        check(s.y==30,"hidden rows do not cause scroll");
        System.out.println("PASS row reveal: padding, both directions, first row, oversized section, relayout, idempotence");
    }
}
"""


class DetailScrollTests(unittest.TestCase):
    def test_production_body_navigation_for_every_visible_row_combination(self):
        code = (ROOT / "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java").read_text()
        rows = re.search(r"private static final int\[\] DETAIL_ROW_IDS = \{[^}]+\};", code).group(0)
        methods = []
        for signature in ("private int firstFocusRow()", "private int adjacentFocusRow(int currentId, boolean down)",
                          "private int firstMediaTabId()", "private int selectedMediaTabId()"):
            start = code.index(signature)
            methods.append(code[start:code.index("\n    }", start) + 6])
        # Execute the actual production navigation methods; only View lookup/visibility
        # is replaced. Header media stays visible for every body visibility combination.
        harness = """public class FocusHarness {
            static class R { static class id {
                static final int video=1,flag=2,quality=3,episode=4,array=5,part=6,
                    quick=7,recommendations=8,recommendationStatus=9,artworks=10,casts=11,
                    tabArtworks=12,tabCasts=13,quickStatus=14;
            }}
            static class Binding { int tabArtworks=12,tabCasts=13; }
            Binding mBinding=new Binding();boolean showArtworks;
            boolean[] visible=new boolean[15];
            int findViewById(int id){return id;}
            boolean isVisible(int id){return visible[id];}
            static void check(boolean ok){if(!ok)throw new AssertionError("focus chain mismatch");}
        """ + rows + "\n" + "\n".join(methods) + """
            public static void main(String[] args){
                FocusHarness h=new FocusHarness();h.visible[10]=h.visible[11]=true;
                for(int mask=0;mask<(1<<DETAIL_ROW_IDS.length);mask++){
                    java.util.List<Integer> active=new java.util.ArrayList<>();
                    for(int i=0;i<DETAIL_ROW_IDS.length;i++){
                        int id=DETAIL_ROW_IDS[i];h.visible[id]=(mask&(1<<i))!=0;if(h.visible[id])active.add(id);
                    }
                    check(h.firstFocusRow()==(active.isEmpty()?1:active.get(0)));
                    for(int i=0;i<active.size();i++){
                        check(h.adjacentFocusRow(active.get(i),false)==(i==0?1:active.get(i-1)));
                        check(h.adjacentFocusRow(active.get(i),true)==active.get(Math.min(i+1,active.size()-1)));
                    }
                }
                h.visible[12]=h.visible[13]=true;h.showArtworks=true;
                check(h.selectedMediaTabId()==12);
                h.showArtworks=false;check(h.selectedMediaTabId()==13);
                h.visible[13]=false;check(h.selectedMediaTabId()==12);
                h.visible[12]=false;h.visible[13]=true;h.showArtworks=true;
                check(h.selectedMediaTabId()==13);
                System.out.println("PASS production focus graph: 256 body combinations; artwork/cast UP targets and absent-tab fallback");
            }
        }"""
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "FocusHarness.java"
            path.write_text(harness)
            subprocess.run(["javac", "-d", temp, str(path)], check=True)
            subprocess.run(["java", "-cp", temp, "FocusHarness"], check=True)

    def test_player_and_body_navigation_excludes_header_media_rows(self):
        code = (ROOT / "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java").read_text()
        rows = re.search(r"DETAIL_ROW_IDS = \{([^}]+)\}", code).group(1)
        self.assertEqual(["flag", "quality", "episode", "array", "part", "quick", "recommendations", "recommendationStatus"],
                         re.findall(r"R.id.(\w+)", rows))
        self.assertIn("target = down ? firstFocusRow() : R.id.video;", code)
        self.assertIn("isDescendantOrSelf(focused, mBinding.mediaRail)", code)
        self.assertIn("target = down ? R.id.change : selectedMediaTabId();", code)
        self.assertIn("mBinding.artworks.setNextFocusDownId(R.id.change);", code)
        self.assertIn("mBinding.casts.setNextFocusDownId(R.id.change);", code)
        self.assertIn("mPartAdapter.setNextFocusUp(adjacentFocusRow(R.id.part, false))", code)
        self.assertNotIn("findFocusUp(", code)
        self.assertNotIn("findFocusDown(", code)

    def test_back_to_player_resets_body_after_immediate_or_deferred_focus(self):
        code = (ROOT / "app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java").read_text()
        start = code.index("private void focusPlayerFromDetail()")
        method = code[start:code.index("\n    }", start)]
        immediate, deferred = method.split("mBinding.video.post(() -> {")
        self.assertIn("mBinding.scroll.scrollTo(0, 0);", immediate)
        self.assertIn("if (mBinding.video.hasFocus()) mBinding.scroll.scrollTo(0, 0);", deferred)
        self.assertIn("isFinishing() || isDestroyed() || isFullscreen()", deferred)
        self.assertNotIn("smoothScroll", method)

    def test_layout_uses_one_row_aware_scroll_owner_and_compact_heading_gap(self):
        root = ET.parse(ROOT / "app/src/leanback/res/layout/activity_video.xml").getroot()
        views = {v.get(A + "id"): v for v in root.iter()}
        scroll = views["@+id/scroll"]
        self.assertEqual("com.fongmi.android.tv.ui.custom.DetailScrollView", scroll.tag)
        self.assertEqual("wrap_content", scroll[0].get(A + "layout_height"))
        self.assertEqual("true", scroll.get(A + "clipToPadding"))
        self.assertEqual("0dp", views["@+id/recommendationsRow"].get(A + "layout_marginTop"))

    def test_production_focus_and_layout_geometry(self):
        self.assertTrue(SOURCE.exists(), "production row-aware scroll container missing")
        with tempfile.TemporaryDirectory() as temp:
            work = Path(temp)
            files = []
            for name, code in STUBS.items():
                path = work / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(code)
                files.append(str(path))
            harness = work / "ScrollHarness.java"
            harness.write_text(HARNESS)
            subprocess.run(["javac", "-d", temp, *files, str(SOURCE), str(harness)], check=True)
            subprocess.run(["java", "-cp", temp, "com.fongmi.android.tv.ui.custom.ScrollHarness"], check=True)


if __name__ == "__main__":
    unittest.main()
