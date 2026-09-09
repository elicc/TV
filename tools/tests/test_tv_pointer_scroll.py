#!/usr/bin/env python3
"""Compile the production input-mode code against explicit Android boundary stubs.

Requires Python + JDK only. Tests mode transitions, not Android scrolling/layout;
real Leanback drag/fling/relayout remains an emulator integration check.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "app/src/leanback/java/com/fongmi/android/tv/ui/custom/TvVerticalGridView.java"
STUBS = {
    "android/annotation/SuppressLint.java": 'package android.annotation; public @interface SuppressLint { String[] value(); }',
    "androidx/annotation/NonNull.java": 'package androidx.annotation; public @interface NonNull {}',
    "androidx/annotation/Nullable.java": 'package androidx.annotation; public @interface Nullable {}',
    "android/content/Context.java": 'package android.content; public class Context {}',
    "android/util/AttributeSet.java": 'package android.util; public interface AttributeSet {}',
    "android/view/InputDevice.java": 'package android.view; public class InputDevice { public static final int SOURCE_CLASS_POINTER=2; }',
    "android/view/MotionEvent.java": '''package android.view;
        public class MotionEvent {
            public static final int ACTION_DOWN=0, ACTION_UP=1, ACTION_CANCEL=3, ACTION_SCROLL=8;
            int action, source; public MotionEvent(int a, int s){action=a;source=s;}
            public int getActionMasked(){return action;}
            public boolean isFromSource(int s){return (source&s)==s;}
        }''',
    "android/view/KeyEvent.java": '''package android.view;
        public class KeyEvent {
            public static final int ACTION_DOWN=0, ACTION_UP=1, KEYCODE_DPAD_UP=19,
                KEYCODE_DPAD_DOWN=20, KEYCODE_DPAD_LEFT=21, KEYCODE_DPAD_RIGHT=22,
                KEYCODE_DPAD_CENTER=23, KEYCODE_ENTER=66, KEYCODE_NUMPAD_ENTER=160;
            int action,key; public KeyEvent(int a,int k){action=a;key=k;}
            public int getAction(){return action;} public int getKeyCode(){return key;}
        }''',
    "android/view/View.java": '''package android.view;
        public class View {
            public static final int VISIBLE=0; public int top,bottom,visibility=0,requests;
            public boolean focusable=true,touchFocusable;
            public int getTop(){return top;} public int getBottom(){return bottom;}
            public int getVisibility(){return visibility;} public boolean hasFocusable(){return focusable;}
            public boolean requestFocus(){requests++;return focusable;}
            public boolean isFocusableInTouchMode(){return touchFocusable;}
            public void setFocusableInTouchMode(boolean b){touchFocusable=b;}
        }''',
    "androidx/leanback/widget/VerticalGridView.java": '''package androidx.leanback.widget;
        import android.view.*; import android.content.Context; import android.util.AttributeSet;
        public class VerticalGridView extends View {
            public static final int FOCUS_SCROLL_ITEM=1;
            public int strategy=0,stops,keys,touches; public boolean disabled;
            public View[] children=new View[0];
            public VerticalGridView(Context c,AttributeSet a,int d){}
            public int getFocusScrollStrategy(){return strategy;}
            public void setFocusScrollStrategy(int s){strategy=s;}
            public boolean isFocusSearchDisabled(){return disabled;}
            public void setFocusSearchDisabled(boolean b){disabled=b;}
            public boolean dispatchTouchEvent(MotionEvent e){touches++;return true;}
            public boolean dispatchGenericMotionEvent(MotionEvent e){return true;}
            public boolean dispatchKeyEvent(KeyEvent e){keys++;return false;}
            public void stopScroll(){stops++;} public int getChildCount(){return children.length;}
            public View getChildAt(int i){return children[i];} public int getHeight(){return 500;}
            public int getPaddingTop(){return 10;} public int getPaddingBottom(){return 10;}
        }''',
}
HARNESS = '''
import android.view.*;
import com.fongmi.android.tv.ui.custom.TvVerticalGridView;
public class PointerRegression {
    static int checks;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    static TvVerticalGridView grid(){return new TvVerticalGridView(null);}
    static MotionEvent touch(int a){return new MotionEvent(a,2);}
    public static void main(String[] args){
        TvVerticalGridView g=grid();
        g.dispatchKeyEvent(new KeyEvent(0,20));
        check(g.strategy==0 && !g.disabled && g.keys==1,"D-pad-only behavior unchanged");
        g.dispatchTouchEvent(touch(0));
        check(g.strategy==1 && g.disabled && g.touchFocusable,"pointer disables stale alignment and child focus");
        g.dispatchTouchEvent(touch(1));
        check(g.strategy==1 && g.disabled,"UP must not re-enable alignment before fling/late layouts");
        g.dispatchTouchEvent(touch(0));g.dispatchTouchEvent(touch(3));
        check(g.strategy==1,"CANCEL/nested interception retains pointer browsing");
        View old=new View();old.top=-400;old.bottom=-100;
        View header=new View();header.top=10;header.bottom=40;header.focusable=false;
        View partial=new View();partial.top=-30;partial.bottom=80;
        View visible=new View();visible.top=100;visible.bottom=400;
        g.children=new View[]{old,header,partial,visible};
        int keys=g.keys;
        check(g.dispatchKeyEvent(new KeyEvent(0,23)),"first OK only establishes visible focus");
        check(visible.requests==1 && old.requests==0 && header.requests==0,"do not focus offscreen selection or header");
        check(g.strategy==0 && !g.disabled && !g.touchFocusable && g.stops==1,"restore original state, stop fling");
        check(g.keys==keys,"first OK does not accidentally activate content");
        g.dispatchKeyEvent(new KeyEvent(0,23));
        check(g.keys==keys+1,"subsequent OK forwarded normally");
        TvVerticalGridView wheel=grid();wheel.strategy=2;
        wheel.dispatchGenericMotionEvent(new MotionEvent(8,2));
        check(wheel.strategy==1 && wheel.disabled,"mouse wheel enters pointer mode");
        wheel.dispatchKeyEvent(new KeyEvent(0,20));
        check(wheel.strategy==2 && !wheel.disabled,"empty grid restores previous strategy without swallowing key");
        TvVerticalGridView other=grid();other.dispatchGenericMotionEvent(new MotionEvent(8,4));
        check(other.strategy==0,"non-pointer motion untouched");
        System.out.println("PASS: "+checks+" pointer-mode checks (Android/Leanback boundary stubs)");
    }
}
'''

if __name__ == "__main__":
    with tempfile.TemporaryDirectory(prefix="tv-pointer-") as tmp:
        directory = Path(tmp)
        for name, code in {**STUBS, "PointerRegression.java": HARNESS}.items():
            path = directory / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(code)
        subprocess.run(["javac", "-d", tmp, str(SOURCE), *map(str, directory.rglob("*.java"))], check=True)
        subprocess.run(["java", "-cp", tmp, "PointerRegression"], check=True)
