"""Execute the production footer with deterministic Android/animation boundaries.

Requires java/javac, no new dependencies. This checks event/lifecycle behavior,
not Android rendering, frame timing or device focus (verified separately on TV).
"""
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/leanback/java"
RES = ROOT / "app/src/leanback/res"
P = "com/fongmi/android/tv/"

STUBS = {
    "android/content/Context.java": "package android.content; public class Context { public Object getContentResolver(){return null;} }",
    "android/provider/Settings.java": "package android.provider; public class Settings { public static class Global { public static final String ANIMATOR_DURATION_SCALE=\"animator\"; public static float scale=1; public static float getFloat(Object r,String k,float d){return scale;} } }",
    "android/util/AttributeSet.java": "package android.util; public interface AttributeSet {}",
    "android/graphics/Typeface.java": "package android.graphics; public class Typeface { public static final Typeface MONOSPACE=new Typeface(); }",
    "android/graphics/drawable/GradientDrawable.java": "package android.graphics.drawable; public class GradientDrawable { public int stroke=0x14ffffff; public GradientDrawable mutate(){return this;} public void setStroke(int w,int c){stroke=c;} }",
    "android/view/Gravity.java": "package android.view; public class Gravity { public static final int CENTER=1,CENTER_VERTICAL=2; }",
    "android/view/KeyEvent.java": """package android.view;
public class KeyEvent {
 public static final int ACTION_DOWN=0,ACTION_UP=1;
 private final int action,code,repeat;
 public KeyEvent(int a,int c,int r){action=a;code=c;repeat=r;}
 public int getAction(){return action;} public int getKeyCode(){return code;} public int getRepeatCount(){return repeat;}
}
""",
    "android/view/View.java": """package android.view;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
public class View {
 public static final int IMPORTANT_FOR_ACCESSIBILITY_NO=2;
 private final Context context;
 private GradientDrawable background=new GradientDrawable();
 public final java.util.List<Runnable> callbacks=new java.util.ArrayList<>();
 public boolean focusable;
 public View(Context c){context=c;} public Context getContext(){return context;}
 public void setBackgroundResource(int id){background=new GradientDrawable();}
 public GradientDrawable getBackground(){return background;}
 public void setLayoutParams(Object p){} public void setPadding(int a,int b,int c,int d){}
 public void setMinimumWidth(int w){} public void setFocusable(boolean f){focusable=f;}
 public void setImportantForAccessibility(int i){}
 public boolean postDelayed(Runnable r,long ms){callbacks.add(r);return true;}
 public boolean removeCallbacks(Runnable r){return callbacks.remove(r);}
 protected void onDetachedFromWindow(){}
 public void detach(){onDetachedFromWindow();}
 public void runCallbacks(){for(Runnable r:java.util.List.copyOf(callbacks))r.run();}
}
""",
    "android/widget/TextView.java": """package android.widget;
public class TextView extends android.view.View {
 public int color; public CharSequence text;
 public TextView(android.content.Context c,Object a,int s){super(c);}
 public void setText(CharSequence t){text=t;} public void setTextColor(int c){color=c;}
 public void setTypeface(android.graphics.Typeface t){} public void setTextSize(float s){} public void setGravity(int g){}
}
""",
    "androidx/appcompat/widget/LinearLayoutCompat.java": """package androidx.appcompat.widget;
public class LinearLayoutCompat extends android.view.View {
 public static final int HORIZONTAL=0;
 public final java.util.List<android.view.View> children=new java.util.ArrayList<>();
 public LinearLayoutCompat(android.content.Context c,android.util.AttributeSet a){super(c);}
 public void setOrientation(int v){} public void setGravity(int v){} public void setClipChildren(boolean b){} public void setClipToPadding(boolean b){}
 public void addView(android.view.View v){children.add(v);} public void removeAllViews(){children.clear();}
 public static class LayoutParams { public static final int WRAP_CONTENT=-2; public int leftMargin; public LayoutParams(int w,int h){} }
}
""",
    "androidx/annotation/Nullable.java": "package androidx.annotation; public @interface Nullable {}",
    "androidx/core/content/ContextCompat.java": "package androidx.core.content; public class ContextCompat { public static int getColor(android.content.Context c,int color){return color;} }",
    "androidx/core/graphics/ColorUtils.java": """package androidx.core.graphics;
public class ColorUtils {
 public static int blendARGB(int a,int b,float f){int r=0;for(int s=0;s<32;s+=8)r|=((int)(((a>>>s)&255)*(1-f)+((b>>>s)&255)*f))<<s;return r;}
}
""",
    "android/view/animation/LinearInterpolator.java": "package android.view.animation; public class LinearInterpolator {}",
    "android/animation/Animator.java": "package android.animation; public class Animator {}",
    "android/animation/AnimatorListenerAdapter.java": "package android.animation; public class AnimatorListenerAdapter { public void onAnimationEnd(Animator a){} }",
    "android/animation/ValueAnimator.java": """package android.animation;
public class ValueAnimator extends Animator {
 public interface AnimatorUpdateListener {void onAnimationUpdate(ValueAnimator a);}
 public static int starts; public static ValueAnimator last;
 public long duration; public boolean cancelled; private float value;
 private final java.util.List<AnimatorUpdateListener> updates=new java.util.ArrayList<>();
 private final java.util.List<AnimatorListenerAdapter> listeners=new java.util.ArrayList<>();
 public static ValueAnimator ofFloat(float... v){last=new ValueAnimator();return last;}
 public void setDuration(long d){duration=d;} public void setInterpolator(Object o){}
 public Object getAnimatedValue(){return value;}
 public void addUpdateListener(AnimatorUpdateListener l){updates.add(l);} public void addListener(AnimatorListenerAdapter l){listeners.add(l);}
 public void removeAllListeners(){listeners.clear();} public void removeAllUpdateListeners(){updates.clear();}
 public void start(){starts++;tick(0);}
 public void tick(float ms){value=ms;for(var l:java.util.List.copyOf(updates))l.onAnimationUpdate(this);}
 public void end(){tick(duration);for(var l:java.util.List.copyOf(listeners))l.onAnimationEnd(this);}
 public void cancel(){cancelled=true;for(var l:java.util.List.copyOf(listeners))l.onAnimationEnd(this);}
}
""",
    P + "R.java": """package com.fongmi.android.tv;
public class R {
 public static class drawable { public static final int tv_keycap=1; }
 public static class color { public static final int tv_text_secondary=0xff9b9ba1,tv_keycap_text_pressed=0xffb8b8be,tv_keycap_border_pressed=0x33ffffff; }
 public static class attr { public static final int tvColorGlassBorder=0x14ffffff; }
}
""",
    P + "utils/ResUtil.java": "package com.fongmi.android.tv.utils; public class ResUtil { public static int dp2px(int d){return d;} }",
    P + "utils/TvTheme.java": "package com.fongmi.android.tv.utils; public class TvTheme { public static int color(android.content.Context c,int a){return a;} }",
}

HARNESS = """import android.animation.ValueAnimator;
import android.content.Context;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.TextView;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.custom.TvKeycapsBar;
public class KeycapsHarness {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static KeyEvent down(int code){return new KeyEvent(0,code,0);}
 static void rest(TextView v,String why){check(v.color==R.color.tv_text_secondary && v.getBackground().stroke==0x14ffffff,why);}
 static TvKeycapsBar.Cap[] caps(){return new TvKeycapsBar.Cap[]{TvKeycapsBar.Cap.of("directions",19,20,21,22),TvKeycapsBar.Cap.of("OK",23,66),TvKeycapsBar.Cap.of("Menu",82),TvKeycapsBar.Cap.of("Back",4)};}
 public static void main(String[] args){
  TvKeycapsBar bar=new TvKeycapsBar(new Context());
  String[] hints={"move","open","menu","back"};bar.setCaps(caps(),hints);
  TextView arrows=(TextView)bar.children.get(0), ok=(TextView)bar.children.get(3), menu=(TextView)bar.children.get(6), back=(TextView)bar.children.get(9);
  check(!bar.focusable,"footer cannot take focus");
  bar.onKeyEvent(new KeyEvent(1,19,0));bar.onKeyEvent(new KeyEvent(0,19,1));bar.onKeyEvent(down(99));
  check(ValueAnimator.starts==0,"up, long-press repeats and unmapped keys do not animate");
  bar.onKeyEvent(down(19));ValueAnimator first=ValueAnimator.last;
  check(first.duration==260,"260ms feedback");rest(arrows,"starts at rest");
  first.tick(30);check(arrows.color!=R.color.tv_text_secondary && arrows.color!=R.color.tv_keycap_text_pressed,"smooth fade-in");
  first.tick(60);check(arrows.color==R.color.tv_keycap_text_pressed,"full at 60ms");
  check(arrows.getBackground().stroke==R.color.tv_keycap_border_pressed,"neutral active border");
  rest(ok,"background mutation does not affect other chips");
  first.tick(140);check(arrows.color==R.color.tv_keycap_text_pressed,"holds through 140ms");
  first.tick(200);check(arrows.color!=R.color.tv_keycap_text_pressed && arrows.color!=R.color.tv_text_secondary,"smooth fade-out");
  bar.onKeyEvent(down(22));bar.onKeyEvent(new KeyEvent(0,19,3));
  check(ValueAnimator.starts==1 && ValueAnimator.last==first,"same cap does not restart or extend");
  bar.onKeyEvent(down(23));check(first.cancelled,"switch cancels old animation");rest(arrows,"switch restores old chip");
  ValueAnimator second=ValueAnimator.last;second.tick(60);
  first.tick(60);rest(arrows,"cancelled animation cannot recolor old chip");
  check(ok.color==R.color.tv_keycap_text_pressed,"new chip highlighted");second.end();rest(ok,"end restores chip");
  bar.onKeyEvent(down(66));check(ValueAnimator.starts==3,"ENTER alias starts new pulse after completion");
  bar.onKeyEvent(down(82));rest(ok,"OK to menu restores OK");ValueAnimator.last.tick(60);
  bar.onKeyEvent(down(4));rest(menu,"menu to back restores menu");ValueAnimator.last.tick(60);bar.detach();rest(back,"detach restores back");
  check(ValueAnimator.last.cancelled && bar.callbacks.isEmpty(),"detach cancels work");
  bar.onKeyEvent(down(19));ValueAnimator.last.tick(60);bar.setCaps(caps(),hints);rest(arrows,"rebuild restores removed chip");
  check(ValueAnimator.last.cancelled,"rebuild cancels animation");
  Settings.Global.scale=0;int starts=ValueAnimator.starts;
  arrows=(TextView)bar.children.get(0);ok=(TextView)bar.children.get(3);
  bar.onKeyEvent(down(20));check(arrows.color==R.color.tv_keycap_text_pressed,"disabled motion uses static feedback");
  check(ValueAnimator.starts==starts && bar.callbacks.size()==1,"no animator when disabled");
  bar.onKeyEvent(down(21));check(bar.callbacks.size()==1,"static feedback is not extended");
  bar.onKeyEvent(down(23));rest(arrows,"static switch restores old chip");check(bar.callbacks.size()==1,"only one revert pending");
  bar.runCallbacks();rest(ok,"static timeout resets");check(bar.callbacks.isEmpty(),"timeout leaves no work");
  bar.onKeyEvent(down(23));bar.detach();rest(ok,"static detach resets");check(bar.callbacks.isEmpty(),"static detach removes timeout");
  bar.onKeyEvent(down(19));bar.setCaps(caps(),hints);rest(arrows,"static rebuild resets");check(bar.callbacks.isEmpty(),"static rebuild removes timeout");
  System.out.println(checks+" footer behavior checks passed");
 }
}
"""


class TvKeycapsTests(unittest.TestCase):
    def test_production_feedback_lifecycle(self):
        with tempfile.TemporaryDirectory(prefix="tv-keycaps-") as directory:
            work = Path(directory)
            for name, source in {**STUBS, "KeycapsHarness.java": HARNESS}.items():
                path = work / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source)
            sources = [str(p) for p in work.rglob("*.java")]
            sources += [str(JAVA / P / name) for name in (
                "ui/custom/TvKeycapsBar.java", "ui/motion/TvMotion.java")]
            subprocess.run(["javac", "-d", str(work), *sources], check=True, capture_output=True, text=True, timeout=60)
            result = subprocess.run(["java", "-cp", str(work), "KeycapsHarness"], check=True, capture_output=True, text=True, timeout=15)
            self.assertIn("footer behavior checks passed", result.stdout)

    def test_neutral_geometry_and_motion_resources(self):
        code = (JAVA / P / "ui/custom/TvKeycapsBar.java").read_text()
        for forbidden in ("scaleX", "scaleY", "tvColorAccent", "tvColorOnAccent", "tv_keycap_active"):
            self.assertNotIn(forbidden, code)
        self.assertFalse((RES / "drawable/tv_keycap_active.xml").exists())
        durations = {n.get("name"): n.text for n in ET.parse(RES / "values/tv_integers.xml").iter("integer")}
        self.assertEqual("260", durations["tv_motion_keycap"])
        dimens = {n.get("name"): n.text for n in ET.parse(RES / "values/tv_dimens.xml").iter("dimen")}
        self.assertEqual("12dp", dimens["tv_keycaps_bottom_inset"])
        self.assertEqual("24dp", dimens["tv_keycaps_bar_height"])
        self.assertEqual("6dp", dimens["tv_keycaps_top_gap"])

    def test_home_forwards_only_the_consumed_down_path(self):
        home = (JAVA / P / "ui/activity/HomeActivity.java").read_text()
        dispatch = home.split("public boolean dispatchKeyEvent(KeyEvent event)", 1)[1].split("protected void onResume", 1)[0]
        self.assertEqual(1, dispatch.count("notifyKeycaps(event)"))
        self.assertLess(dispatch.index("mBinding.toolbar.hasFocus()"), dispatch.index("notifyKeycaps(event)"))
        self.assertIn("return super.dispatchKeyEvent(event);", dispatch)


if __name__ == "__main__":
    unittest.main()
