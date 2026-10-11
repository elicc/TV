"""Host checks for TV search ordering, group transitions and layout sizing."""
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/leanback/res"
JAVA = ROOT / "app/src/leanback/java/com/fongmi/android/tv"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class SearchKeyboardTests(unittest.TestCase):
    def test_adapter_group_transitions(self):
        # Compile the real adapter; stubs replace only Android rendering APIs.
        sources = {
            "androidx/annotation/NonNull.java": "package androidx.annotation; public @interface NonNull {}",
            "android/view/View.java": """package android.view; public class View {
                public static final int VISIBLE=0, INVISIBLE=4;
                public int visibility=VISIBLE;
                public float textSize;
                public boolean focusable=true, focusableInTouchMode=true;
                public interface OnClickListener { void onClick(View v); }
                public interface OnLongClickListener { boolean onLongClick(View v); }
                public void setOnClickListener(OnClickListener l) {}
                public void setOnLongClickListener(OnLongClickListener l) {}
                public Object getContext() { return null; }
                public void setText(String s) {} public void setImageResource(int r) {}
                public void setTextSize(float s) { textSize=s; }
                public void setVisibility(int v) { visibility=v; }
                public void setFocusable(boolean v) { focusable=v; }
                public void setFocusableInTouchMode(boolean v) { focusableInTouchMode=v; }
            }""",
            "android/view/ViewGroup.java": "package android.view; public class ViewGroup extends View {}",
            "android/view/LayoutInflater.java": "package android.view; public class LayoutInflater { public static LayoutInflater from(Object c) { return new LayoutInflater(); } }",
            "androidx/recyclerview/widget/RecyclerView.java": """package androidx.recyclerview.widget;
                import android.view.*;
                public class RecyclerView {
                    public static final int NO_POSITION = -1;
                    public static class ViewHolder {
                        public View itemView;
                        public ViewHolder(View v) { itemView = v; }
                        public int getBindingAdapterPosition() { return NO_POSITION; }
                    }
                    public abstract static class Adapter<T extends ViewHolder> {
                        public abstract int getItemCount();
                        public int getItemViewType(int p) { return 0; }
                        public abstract T onCreateViewHolder(ViewGroup p, int t);
                        public abstract void onBindViewHolder(T h, int p);
                        public void notifyDataSetChanged() {}
                    }
                }""",
            "com/fongmi/android/tv/R.java": """package com.fongmi.android.tv;
                public class R { public static class drawable {
                    public static final int ic_keyboard_back=1, ic_keyboard_remote=2, ic_keyboard_search=3;
                }}""",
        }
        for kind, field in [("Text", "text"), ("Icon", "icon")]:
            name = "AdapterKeyboard" + kind + "Binding"
            sources["com/fongmi/android/tv/databinding/" + name + ".java"] = f"""
                package com.fongmi.android.tv.databinding; import android.view.*;
                public class {name} {{
                    public View {field} = new View();
                    public View getRoot() {{ return {field}; }}
                    public static {name} inflate(LayoutInflater i, ViewGroup p, boolean a) {{ return new {name}(); }}
                }}"""
        sources["KeyboardHarness.java"] = r'''import java.lang.reflect.*;
import java.util.*;
import com.fongmi.android.tv.ui.adapter.KeyboardAdapter;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
public class KeyboardHarness {
    static void check(boolean b, String m) { if (!b) throw new AssertionError(m); }
    static List<?> items(KeyboardAdapter a) throws Exception {
        Field f = KeyboardAdapter.class.getDeclaredField("mItems"); f.setAccessible(true);
        return (List<?>) f.get(a);
    }
    static void actions(KeyboardAdapter a) throws Exception {
        List<?> i = items(a); int n = i.size();
        check(i.subList(n-3,n).equals(Arrays.asList(1,2,3)), "delete / QR / search order");
        for (int p=n-3;p<n;p++) check(a.getItemViewType(p)==1, "action type");
    }
    public static void main(String[] args) throws Exception {
        KeyboardAdapter a = new KeyboardAdapter(null);
        check(a.getItemCount()==12, "nine groups plus three actions"); actions(a);
        String[] titles = {"1\n0","2\nABC","3\nDEF","4\nGHI","5\nJKL","6\nMNO","7\nPQRS","8\nTUV","9\nWXYZ"};
        String[] letters = {"10","ABC2","DEF3","GHI4","JKL5","MNO6","PQRS7","TUV8","WXYZ9"};
        for (int g=0;g<9;g++) {
            check(items(a).get(g).toString().equals(titles[g]), "group label " + g);
            a.expand(g); check(a.isExpanded(), "expanded");
            check(a.getItemCount()==6, "two-row secondary keyboard");
            check(items(a).get(3).equals(1), "delete at row 2 column 1");
            check(!items(a).contains(2) && !items(a).contains(3), "no QR or search when expanded");
            for (int c=0;c<letters[g].length();c++) {
                int p = c < 3 ? c : c + 1;
                check(items(a).get(p).equals(letters[g].substring(c,c+1)), "letter position");
                check(a.getItemViewType(p)==0, "letter type");
            }
            RecyclerView.ViewHolder text = a.onCreateViewHolder(new android.view.ViewGroup(), 0);
            for (int p=0;p<6;p++) {
                if (p==3) continue;
                a.onBindViewHolder(text,p);
                check(text.itemView.textSize==24, "large secondary label");
                boolean blank = items(a).get(p).equals("");
                check(text.itemView.visibility==(blank ? View.INVISIBLE : View.VISIBLE), "blank visibility");
                check(text.itemView.focusable==!blank && text.itemView.focusableInTouchMode==!blank, "blank not focusable");
            }
            // Reuse a possibly hidden holder for a real key after rebinding.
            a.onBindViewHolder(text,1);
            check(text.itemView.visibility==View.VISIBLE && text.itemView.focusable && text.itemView.focusableInTouchMode, "recycled key restored");
            a.expand(0); check(a.collapse()==g, "restore original group focus");
            check(!a.isExpanded() && a.getItemCount()==12, "collapsed");
            actions(a);
            a.onBindViewHolder(text,g);
            check(text.itemView.textSize==16, "group label restored after recycling");
        }
        for (int p : new int[]{-1,9,10,11,100}) { a.expand(p); check(!a.isExpanded(), "invalid position"); }
        check(a.collapse()==-1, "already collapsed");
        System.out.println("PASS: all nine groups, 0-9/A-Z, actions, bounds and focus restoration");
    }
}'''
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, content in sources.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
            command = ["javac", "-encoding", "UTF-8", "-d", directory,
                       str(JAVA / "ui/adapter/KeyboardAdapter.java")]
            subprocess.run(command + [str(root / name) for name in sources],
                           check=True, capture_output=True, text=True)
            result = subprocess.run(["java", "-cp", directory, "KeyboardHarness"],
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("PASS:", result.stdout)

    def test_secondary_focus_starts_in_first_row_middle(self):
        source = (JAVA / "ui/custom/CustomKeyboard.java").read_text()
        handler = source.split("public void onGroupClick(int position) {", 1)[1].split("}", 1)[0]
        self.assertIn("focusPosition(1);", handler)

    def test_search_entry_focuses_middle_group_without_resetting_expanded_focus(self):
        keyboard = (JAVA / "ui/custom/CustomKeyboard.java").read_text()
        self.assertIn("focusPosition(adapter.isExpanded() ? 1 : 4);", keyboard)
        activity = (JAVA / "ui/activity/SearchActivity.java").read_text()
        resume = activity.split("protected void onResume() {", 1)[1].split("}", 1)[0]
        self.assertIn("mKeyboard.focusDefault();", resume)
        self.assertNotIn("mBinding.keyword.requestFocus();", resume)

    def test_search_field_is_quiet_with_static_underline(self):
        search = ET.parse(RES / "layout/activity_search.xml").getroot()
        field = next(n for n in search.iter() if n.get(ANDROID + "id") == "@+id/keyword")
        self.assertEqual("com.fongmi.android.tv.ui.custom.CustomEditText", field.tag)
        self.assertEqual("false", field.get(ANDROID + "cursorVisible"))
        self.assertEqual("@color/tv_text_muted", field.get(ANDROID + "textColorHint"))
        self.assertEqual("?attr/tvColorTextPrimary", field.get(ANDROID + "textColor"))
        self.assertEqual("@null", field.get("{http://schemas.android.com/apk/res-auto}backgroundTint"))
        self.assertEqual("@drawable/tv_search_underline", field.get(ANDROID + "background"))
        background = ET.parse(RES / "drawable/tv_search_underline.xml").getroot()
        self.assertEqual("layer-list", background.tag)
        self.assertEqual(1, len(background))
        self.assertEqual("bottom", background[0].get(ANDROID + "gravity"))
        self.assertEqual("1dp", background[0].get(ANDROID + "height"))
        self.assertEqual("@color/tv_text_muted", background.find(".//solid").get(ANDROID + "color"))
        strings = ET.parse(RES / "values-zh-rCN/strings.xml").getroot()
        self.assertEqual("输入关键词搜索", next(s.text for s in strings if s.get("name") == "search_keyword"))

    def test_room_for_four_letter_labels_and_four_rows(self):
        search = ET.parse(RES / "layout/activity_search.xml").getroot()
        panel = search[0][0]
        self.assertEqual("232dp", panel.get(ANDROID + "layout_width"))
        self.assertEqual("16dp", panel.get(ANDROID + "layout_marginEnd"))
        keyboard = next(n for n in search.iter() if n.get(ANDROID + "id") == "@+id/keyboard")
        self.assertEqual("match_parent", keyboard.get(ANDROID + "layout_width"))
        self.assertEqual("3", keyboard.get("{http://schemas.android.com/apk/res-auto}spanCount"))
        for kind in ("text", "icon"):
            key = ET.parse(RES / f"layout/adapter_keyboard_{kind}.xml").getroot()
            self.assertEqual("match_parent", key.get(ANDROID + "layout_width"))
            self.assertEqual("56dp", key.get(ANDROID + "layout_height"))
        self.assertGreaterEqual((232 - 2 * 8) / 3, 72)
        self.assertLess(4 * 56 + 3 * 8 + 60, 540 - 2 * 24)

    def test_posters_and_skeleton_share_available_width(self):
        card = ET.parse(RES / "layout/adapter_hot_movie.xml").getroot()
        self.assertEqual("match_parent", card.get(ANDROID + "layout_width"))
        skeleton = ET.parse(RES / "layout/view_hot_movie_skeleton.xml").getroot()
        self.assertEqual("0dp", skeleton.get(ANDROID + "layout_width"))
        self.assertEqual("1", skeleton.get(ANDROID + "layout_weight"))
        row = ET.parse(RES / "layout/view_hot_movie_skeletons.xml").getroot()
        self.assertEqual(5, len(row.findall("include")))


if __name__ == "__main__":
    unittest.main()
