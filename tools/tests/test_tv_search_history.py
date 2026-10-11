"""Host regression checks for inline TV search-history management."""
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/leanback/java/com/fongmi/android/tv"
RES = ROOT / "app/src/leanback/res"
A = "{http://schemas.android.com/apk/res/android}"


class SearchHistoryTests(unittest.TestCase):
    def test_history_header_shares_top_row_with_source(self):
        layout = ET.parse(RES / "layout/activity_search.xml").getroot()
        views = {n.get(A + "id"): n for n in layout.iter() if n.get(A + "id")}
        parents = {child: parent for parent in layout.iter() for child in parent}
        header = views["@+id/recordHeader"]
        source = views["@+id/sourceRow"]
        row = parents[header]
        self.assertIs(row, parents[source])
        self.assertEqual("horizontal", row.get(A + "orientation"))
        self.assertIn("center_vertical", row.get(A + "gravity"))
        self.assertEqual("1", header.get(A + "layout_weight"))
        content = list(views["@+id/scroll"])[0]
        self.assertIs(row, list(content)[0])
        self.assertIn(content.get(A + "paddingTop"), (None, "0dp"))
        self.assertNotIn(source, views["@+id/recordLayout"].iter())
        self.assertEqual("8dp", views["@+id/word"].get(A + "layout_marginTop"))
        activity = (JAVA / "ui/activity/SearchActivity.java").read_text()
        self.assertIn("mBinding.recordHeader.setVisibility(size == 0 ? View.GONE : View.VISIBLE)", activity)
        self.assertIn("mBinding.recordHeader.hasFocus()", activity)

    def test_real_adapter_routes_mode_clicks_and_persists_deletion(self):
        files = {
            "androidx/annotation/NonNull.java": "package androidx.annotation; public @interface NonNull {}",
            "android/content/Context.java": 'package android.content; public class Context { public String getString(int id, Object... args) { return args.length > 0 ? "Delete " + args[0] : id == 2 ? "更多" : "收起"; } }',
            "android/view/View.java": """package android.view; import android.content.Context;
                public class View {
                    public static final int VISIBLE=0, GONE=8; public int visibility;
                    public int background; public String text, description; public OnClickListener click; public OnLongClickListener hold;
                    public OnKeyListener key; public View label; private int measuredWidth; private boolean forced=true;
                    public interface OnKeyListener { boolean onKey(View v,int code,KeyEvent e); }
                    public interface OnFocusChangeListener { void onFocusChange(View v,boolean focused); }
                    public static class MeasureSpec { public static final int AT_MOST=1, UNSPECIFIED=0; public static int makeMeasureSpec(int n,int mode){return n;} }
                    private ViewGroup.LayoutParams params=new com.google.android.flexbox.FlexboxLayoutManager.LayoutParams();
                    public void setOnKeyListener(OnKeyListener l){key=l;}
                    public void setOnFocusChangeListener(OnFocusChangeListener l){}
                    public void setPressed(boolean p){}
                    public boolean performClick(){click.onClick(this);return true;}
                    public ViewGroup.LayoutParams getLayoutParams(){return params;}
                    public void setLayoutParams(ViewGroup.LayoutParams p){params=p;}
                    public int width=200; public int getWidth(){return width;}
                    public int getPaddingLeft(){return 0;} public int getPaddingRight(){return 0;}
                    public int getPaddingTop(){return 0;} public int getPaddingBottom(){return 0;}
                    public void measure(int w,int h){if(!forced)return;forced=false;measuredWidth=Math.min(w,28+(label==null?0:label.text.length()*10));}
                    public void forceLayout(){forced=true;}
                    public int getMeasuredWidth(){return measuredWidth;}
                    public int getMeasuredHeight(){return 32;}
                    public interface OnClickListener { void onClick(View v); }
                    public interface OnLongClickListener { boolean onLongClick(View v); }
                    public void setOnClickListener(OnClickListener l) {click=l;}
                    public void setOnLongClickListener(OnLongClickListener l) {hold=l;}
                    public Context getContext() { return new Context(); }
                    public void setVisibility(int v) {visibility=v;}
                    public void setText(String s) {text=s;}
                    public void setBackgroundResource(int r){background=r;} public void setTextColor(Object c){}
                    public void setContentDescription(String s) {description=s;}
                }""",
            "android/view/ViewGroup.java": """package android.view; public class ViewGroup extends View {
                public static class LayoutParams {public int height;}
                public static class MarginLayoutParams extends LayoutParams {public int leftMargin=6,rightMargin=6,topMargin=6,bottomMargin=6;}
            }""",
            "android/view/KeyEvent.java": """package android.view; public class KeyEvent {
                public static final int ACTION_DOWN=0,ACTION_UP=1; int action,repeat; boolean canceled;
                public KeyEvent(int a,int r,boolean c){action=a;repeat=r;canceled=c;}
                public int getAction(){return action;} public int getRepeatCount(){return repeat;}
                public boolean isCanceled(){return canceled;}
            }""",
            "com/fongmi/android/tv/utils/KeyUtil.java": "package com.fongmi.android.tv.utils; public class KeyUtil {public static boolean isEnterKey(android.view.KeyEvent e){return true;}}",
            "android/view/LayoutInflater.java": "package android.view; public class LayoutInflater {public static LayoutInflater from(Object c){return new LayoutInflater();}}",
            "androidx/recyclerview/widget/RecyclerView.java": """package androidx.recyclerview.widget;
                import android.view.*; public class RecyclerView {
                    public static final int NO_POSITION=-1;
                    public static class ViewHolder {
                        public View itemView; public int position;
                        public ViewHolder(View v){itemView=v;}
                        public int getBindingAdapterPosition(){return position;}
                    }
                    public abstract static class Adapter<T extends ViewHolder> {
                        public int getItemViewType(int p){return 0;}
                        public abstract int getItemCount();
                        public abstract T onCreateViewHolder(ViewGroup p,int t);
                        public abstract void onBindViewHolder(T h,int p);
                        public void notifyDataSetChanged(){}
                        public void notifyItemRemoved(int p){}
                        public void notifyItemRangeRemoved(int p,int c){}
                    }
                }""",
            "com/fongmi/android/tv/databinding/AdapterSearchRecordBinding.java": """package com.fongmi.android.tv.databinding;
                import android.view.*; public class AdapterSearchRecordBinding {
                    public static AdapterSearchRecordBinding last;
                    public View root=new View(), text=new View(), delete=new View();
                    public AdapterSearchRecordBinding(){root.label=text;}
                    public View getRoot(){return root;}
                    public static AdapterSearchRecordBinding inflate(LayoutInflater i,ViewGroup p,boolean a){return last=new AdapterSearchRecordBinding();}
                }""",
            "com/fongmi/android/tv/R.java": "package com.fongmi.android.tv; public class R {public static class string {public static final int tv_search_delete_term=1,tv_search_more=2,tv_search_less=3;} public static class drawable {public static final int tv_search_record_action=4;} public static class color {public static final int selector_search_record_action=5;}}",
            "androidx/appcompat/content/res/AppCompatResources.java": "package androidx.appcompat.content.res; public class AppCompatResources {public static Object getColorStateList(Object c,int r){return null;}}",
            "com/google/android/flexbox/FlexboxLayoutManager.java": "package com.google.android.flexbox; public class FlexboxLayoutManager {public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams {public int maxWidth=Integer.MAX_VALUE;public boolean wrapBefore;public void setMaxWidth(int v){maxWidth=v;}public void setWrapBefore(boolean v){wrapBefore=v;}}}",
            "com/fongmi/android/tv/setting/Setting.java": 'package com.fongmi.android.tv.setting; public class Setting {static String value=""; public static String getKeyword(){return value;} public static void putKeyword(String s){value=s;}}',
            "com/google/gson/reflect/TypeToken.java": "package com.google.gson.reflect; public class TypeToken {public static TypeToken getParameterized(Class<?> a,Class<?> b){return new TypeToken();} public Object getType(){return null;}}",
            "com/fongmi/android/tv/App.java": """package com.fongmi.android.tv; import java.util.*;
                public class App { public static Json gson(){return new Json();}
                    public static class Json {
                        public String toJson(List<String> items){return String.join("|",items);}
                        public List<String> fromJson(String s,Object t){return new ArrayList<>(Arrays.asList(s.split("[|]")));}
                    }
                }""",
            "HistoryHarness.java": r'''import com.fongmi.android.tv.ui.adapter.RecordAdapter;
import com.fongmi.android.tv.databinding.AdapterSearchRecordBinding;
import com.fongmi.android.tv.setting.Setting;
import android.view.*;
public class HistoryHarness {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static class Listener implements RecordAdapter.OnClickListener {
        int count, clicks, holds, deletes, toggles; String word; RecordAdapter removeFrom;
        public void onItemClick(String s){clicks++;word=s;}
        public void onItemLongClick(String s){holds++;word=s;}
        public void onItemDelete(String s){deletes++;word=s;if(removeFrom!=null)removeFrom.remove(s);}
        public void onDataChanged(int c){count=c;}
        public void onToggleExpanded(){toggles++;}
    }
    public static void main(String[] args){
        Listener l=new Listener(); RecordAdapter a=new RecordAdapter(l);
        a.add("A");a.add("B");a.add("C");
        RecordAdapter.ViewHolder h=a.onCreateViewHolder(new ViewGroup(),0);
        AdapterSearchRecordBinding b=AdapterSearchRecordBinding.last;
        h.position=1;a.onBindViewHolder(h,1);
        check(!a.isEditing() && b.delete.visibility==View.GONE,"normal badge hidden");
        h.itemView.click.onClick(h.itemView);h.itemView.hold.onLongClick(h.itemView);
        check(l.clicks==1 && l.holds==1 && l.deletes==0 && l.word.equals("B"),"normal click/hold preserved");
        a.setEditing(true);a.onBindViewHolder(h,1);
        check(b.delete.visibility==View.VISIBLE && b.root.description.equals("Delete B"),"edit badge/accessible label");
        h.itemView.click.onClick(h.itemView);h.itemView.hold.onLongClick(h.itemView);
        check(l.deletes==1 && l.clicks==1 && l.holds==1,"edit click deletes, hold has no dialog");
        check(a.remove(l.word)==1 && a.getItemCount()==2 && l.count==2,"middle removal index/count");
        check(Setting.getKeyword().equals("C|A"),"deletion persisted");
        a.onBindViewHolder(h,1);h.itemView.click.onClick(h.itemView);
        check(l.word.equals("A"),"recycled holder uses current position");
        check(a.remove("missing")==-1 && a.getItemCount()==2,"missing deletion safe");
        h.position=-1;h.itemView.click.onClick(h.itemView);
        check(!h.itemView.hold.onLongClick(h.itemView) && l.deletes==2,"NO_POSITION ignored");
        a.setEditing(false);h.position=0;a.onBindViewHolder(h,0);
        check(b.delete.visibility==View.GONE && b.root.description.equals("C"),"exit resets reused holder");
        h.itemView.click.onClick(h.itemView);check(l.clicks==2,"normal search restored");
        RecordAdapter reload=new RecordAdapter(new Listener());check(reload.getItemCount()==2,"reload reflects deletion");
        check(a.remove("A")==1 && a.remove("C")==0 && l.count==0,"last item reports empty");
        a.add("new");a.clear();check(l.count==0 && Setting.getKeyword().isEmpty(),"clear preserved");
        // Cap both in-memory and persisted history; promote duplicates, never lose neighbors.
        for(int i=0;i<55;i++)a.add("item"+i);
        check(a.getItemCount()==50 && Setting.getKeyword().split("[|]").length==50,"50 item cap persisted");
        a.add("item25");check(a.getItemCount()==50 && Setting.getKeyword().startsWith("item25|"),"deduplicate/promote");
        check(!Setting.getKeyword().contains("item4|"),"oldest evicted");
        a.clear();
        for(int i=0;i<12;i++)a.add(String.valueOf((char)('A'+i)));
        ViewGroup parent=new ViewGroup();a.updateCollapsedCount(parent);
        check(a.getItemCount()==7 && a.getTogglePosition()==6 && a.hasOverflow() && l.count==12,"two measured rows, total callback unchanged");
        check(parent.getLayoutParams().height==88,"two full rows measured including More");
        int toggle=a.getTogglePosition();
        check(a.getItemViewType(toggle)==1 && a.getItemViewType(toggle-1)==0,"action has separate recycling type");
        RecordAdapter.ViewHolder action=a.onCreateViewHolder(parent,1);
        AdapterSearchRecordBinding actionBinding=AdapterSearchRecordBinding.last;
        action.position=toggle;a.onBindViewHolder(action,toggle);
        check(actionBinding.text.text.equals("更多") && actionBinding.text.background==4 && actionBinding.delete.visibility==View.GONE,"More is styled text without delete badge");
        int clicks=l.clicks,holds=l.holds,deletes=l.deletes;String saved=Setting.getKeyword();
        action.onClick(action.itemView);action.onLongClick(action.itemView);
        check(l.toggles==1 && l.clicks==clicks && l.holds==holds && l.deletes==deletes && saved.equals(Setting.getKeyword()),"action cannot search, delete or persist history");
        parent.width=400;a.updateCollapsedCount(parent);
        check(a.getItemCount()==12 && a.getTogglePosition()==-1 && parent.getLayoutParams().height==88,"resize removes unnecessary action");
        parent.width=200;a.updateCollapsedCount(parent);
        a.setExpanded(true);a.updateCollapsedCount(parent);
        check(a.getItemCount()==13 && parent.getLayoutParams().height==176,"expanded full height includes trailing pair");
        h.position=11;a.onBindViewHolder(h,11);
        check(((com.google.android.flexbox.FlexboxLayoutManager.LayoutParams)h.itemView.getLayoutParams()).wrapBefore,"last term wraps with Less rather than leaving action alone");
        action.position=a.getTogglePosition();a.onBindViewHolder(action,action.position);
        check(actionBinding.text.text.equals("收起"),"expanded action label");
        action.onClick(action.itemView);check(l.toggles==2 && saved.equals(Setting.getKeyword()),"Less also only toggles");
        a.setExpanded(false);a.setEditing(true);a.updateCollapsedCount(parent);
        check(a.getItemCount()==12 && a.getTogglePosition()==-1,"editing bypasses folding without an action");
        l.removeFrom=a;h.position=4;a.onBindViewHolder(h,4);int before=l.deletes;
        h.itemView.key.onKey(h.itemView,23,new KeyEvent(0,0,false));
        for(int i=1;i<20;i++)h.itemView.key.onKey(h.itemView,23,new KeyEvent(0,i,false));
        check(a.getItemCount()==12,"held key never deletes before release");
        h.itemView.key.onKey(h.itemView,23,new KeyEvent(1,0,false));
        h.itemView.key.onKey(h.itemView,23,new KeyEvent(1,0,false));
        check(l.deletes==before+1 && a.getItemCount()==11 && Setting.getKeyword().split("[|]").length==11,"one press removes exactly one");
        check(Setting.getKeyword().equals("L|K|J|I|G|F|E|D|C|B|A"),"all non-target neighbors retained");
        h.itemView.key.onKey(h.itemView,23,new KeyEvent(0,0,false));
        h.itemView.key.onKey(h.itemView,23,new KeyEvent(1,0,true));
        check(l.deletes==before+1,"canceled press cannot delete");
        h.itemView.key.onKey(h.itemView,23,new KeyEvent(0,0,false));
        a.onBindViewHolder(h,4);
        h.itemView.key.onKey(h.itemView,23,new KeyEvent(1,0,false));
        check(l.deletes==before+1,"rebinding disarms a stale key press");
        a.setEditing(false);a.updateCollapsedCount(parent);
        check(a.getItemCount()==7 && a.hasOverflow(),"exit returns to two rows");
        a.clear();a.add("A");a.add("WWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW");a.add("B");
        a.updateCollapsedCount(parent);
        check(a.getItemCount()==3 && a.hasOverflow(),"oversized chip fills one row without overflow");
        a.setEditing(true);a.remove("B");a.setEditing(false);a.updateCollapsedCount(parent);
        check(a.getItemCount()==2 && !a.hasOverflow(),"more disappears when remaining terms fit");
        a.clear();
        for(int i=0;i<50;i++)a.add("W".repeat(40)+i);
        a.updateCollapsedCount(parent);
        check(a.getItemCount()==3 && parent.getLayoutParams().height==88,"50 full-width terms collapse to two rows including action");
        h.position=1;a.onBindViewHolder(h,1);
        check(((com.google.android.flexbox.FlexboxLayoutManager.LayoutParams)h.itemView.getLayoutParams()).maxWidth==128,"long final term reserves action width and margins");
        h.position=0;a.onBindViewHolder(h,0);
        check(((com.google.android.flexbox.FlexboxLayoutManager.LayoutParams)h.itemView.getLayoutParams()).maxWidth==Integer.MAX_VALUE,"recycled ordinary term resets action constraints");
        a.setExpanded(true);a.updateCollapsedCount(parent);
        check(a.getItemCount()==51 && parent.getLayoutParams().height==2200,"all 50 rows contribute to parent scroll height");
        a.clear();a.add("A");a.add("B");a.add("C");
        Setting.putKeyword("NEW|C|B|A");
        a.remove("B");
        check(Setting.getKeyword().equals("NEW|C|A"),"deletion must preserve newer records from another search screen");
        Setting.putKeyword("LATEST|NEW|C|A");a.add("D");
        check(Setting.getKeyword().equals("D|LATEST|NEW|C|A"),"adding must not overwrite newer saved records");
        System.out.println("PASS: click routing, key repeats, exact deletion, persistence, 50 cap, measured folding, expansion");
    }
}'''
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, content in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
            result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", directory,
                                     str(JAVA / "ui/adapter/RecordAdapter.java")]
                                    + [str(root / n) for n in files], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            result = subprocess.run(["java", "-cp", directory, "HistoryHarness"], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("PASS:", result.stdout)

    def test_manage_is_inline_and_back_has_priority(self):
        source = (JAVA / "ui/activity/SearchActivity.java").read_text()
        manage = source.split("private void manageRecords(View view) {", 1)[1].split("private void setRecordEditing", 1)[0]
        self.assertNotIn("MaterialAlertDialogBuilder", manage)
        self.assertIn("setRecordEditing(true)", manage)
        self.assertIn("focusRecord(0)", manage)
        for start, end in [("public boolean dispatchKeyEvent", "private boolean findFocus"),
                           ("protected void onBackInvoked", "private boolean findFocus")]:
            body = source.split(start, 1)[1].split(end, 1)[0]
            self.assertLess(body.index("exitRecordManagement()"), body.index("mKeyboard.onBack()"))
        self.assertIn("if (size == 0 && mRecordAdapter != null) setRecordEditing(false);", source)
        self.assertIn("if (restoreFocus) mKeyboard.focusDefault();", source)
        self.assertIn("Math.min(position, mRecordAdapter.getItemCount() - 1)", source)

    def test_deletion_reflows_without_scrolling_away_neighbors(self):
        adapter = (JAVA / "ui/adapter/RecordAdapter.java").read_text()
        remove = adapter.split("public int remove(String text)", 1)[1].split("public void clear", 1)[0]
        self.assertIn("mItems.remove(position)", remove)
        self.assertIn("notifyDataSetChanged()", remove)
        self.assertNotIn("notifyItemRemoved", remove)
        activity = (JAVA / "ui/activity/SearchActivity.java").read_text()
        focus = activity.split("private void focusRecord", 1)[1].split("public void onItemLongClick", 1)[0]
        self.assertNotIn("scrollToPosition", focus)
        self.assertIn("holder.itemView.requestFocus()", focus)

    def test_both_history_writers_share_fifty_item_limit(self):
        source = (JAVA / "ui/activity/CollectActivity.java").read_text()
        self.assertIn("items.subList(RecordAdapter.MAX_RECORDS, items.size()).clear()", source)
        self.assertNotIn("items.remove(9)", source)

    def test_toggle_is_inline_text_and_not_a_standalone_row(self):
        layout = ET.parse(RES / "layout/activity_search.xml").getroot()
        self.assertFalse(any(n.get(A + "id") == "@+id/moreRecords" for n in layout.iter()))
        chip = ET.parse(RES / "layout/adapter_search_record.xml").getroot()
        self.assertEqual("true", chip.get(A + "focusable"))
        adapter = (JAVA / "ui/adapter/RecordAdapter.java").read_text()
        self.assertIn("return !editing && overflow ? visibleRecords() : RecyclerView.NO_POSITION", adapter)
        activity = (JAVA / "ui/activity/SearchActivity.java").read_text()
        self.assertNotIn("mBinding.moreRecords", activity)
        self.assertIn("public void onToggleExpanded()", activity)
        self.assertIn("int target = expand ? Math.min(firstHidden, toggle - 1) : toggle", activity)
        drawable = ET.parse(RES / "drawable/tv_search_record_action.xml").getroot()
        normal = list(drawable)[-1]
        self.assertEqual("@android:color/transparent", normal.find("shape/solid").get(A + "color"))
        self.assertIsNone(normal.find("shape/stroke"))
        self.assertTrue(any(n.get(A + "state_focused") == "true" for n in drawable))
        focused = next(n for n in drawable if n.get(A + "state_focused") == "true")
        layers = focused.find("layer-list")
        self.assertEqual("stack", layers.get(A + "paddingMode"))
        text_padding = layers.find("item/shape/padding")
        underline = list(layers)[-1]
        self.assertEqual("8dp", text_padding.get(A + "bottom"))
        self.assertEqual("4dp", underline.get(A + "bottom"))
        self.assertEqual("1dp", underline.get(A + "height"))

    def test_row_refresh_is_deferred_and_remote_navigation_cannot_skip_long_chips(self):
        source = (JAVA / "ui/activity/SearchActivity.java").read_text()
        self.assertIn("if (right - left != oldRight - oldLeft) mBinding.recordRecycler.post(this::updateRecordLayout);", source)
        navigation = source.split("private boolean handleRecordKey", 1)[1].split("private boolean handleHotKey", 1)[0]
        self.assertIn("rowDistance < Math.abs(nearest.getTop() - item.getTop())", navigation)
        self.assertIn("nearest.requestFocus()", navigation)

    def test_delete_badge_has_no_separate_focus_target(self):
        layout = ET.parse(RES / "layout/adapter_search_record.xml").getroot()
        self.assertEqual("true", layout.get(A + "focusable"))
        text = next(n for n in layout if n.get(A + "id") == "@+id/text")
        badge = next(n for n in layout if n.get(A + "id") == "@+id/delete")
        self.assertEqual("true", text.get(A + "duplicateParentState"))
        self.assertEqual("false", badge.get(A + "focusable"))
        self.assertEqual("false", badge.get(A + "clickable"))
        self.assertEqual("top|end", badge.get(A + "layout_gravity"))
        self.assertEqual("gone", badge.get(A + "visibility"))
        self.assertEqual("12dp", badge.get(A + "layout_width"))
        self.assertEqual("12dp", badge.get(A + "layout_height"))
        self.assertEqual("4dp", badge.get(A + "layout_marginTop"))
        self.assertEqual("4dp", badge.get(A + "layout_marginEnd"))
        self.assertIsNone(badge.get(A + "background"))
        self.assertEqual("@drawable/ic_search_record_delete", badge.get(A + "src"))
        self.assertIn("Generated from Lucide 0.344.0 x.svg", (RES / "drawable/ic_search_record_delete.xml").read_text())


if __name__ == "__main__":
    unittest.main()
