"""Run the production ArtworkAdapter against small Android boundary doubles.

Checks selection/lifecycle behavior, not real Leanback layout or D-pad dispatch.
Device exploration: TCL Android 11, 狂飙 detail, 10-item artwork rail (ARTEMIS).
Run: python3 -m unittest tools.tests.test_tv_artwork_adapter
"""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
ADAPTER = "com/fongmi/android/tv/ui/adapter/ArtworkAdapter.java"
STUBS = {
    "androidx/annotation/NonNull.java": "package androidx.annotation; public @interface NonNull {}",
    "android/view/View.java": """package android.view;
public class View {
    public interface OnFocusChangeListener { void onFocusChange(View v, boolean focused); }
    public interface OnClickListener { void onClick(View v); }
    private boolean selected;
    public OnFocusChangeListener focus;
    public OnClickListener click;
    public void setSelected(boolean value) { selected = value; }
    public boolean isSelected() { return selected; }
    public void setOnFocusChangeListener(OnFocusChangeListener value) { focus = value; }
    public void setOnClickListener(OnClickListener value) { click = value; }
}""",
    "android/view/ViewGroup.java": """package android.view;
public class ViewGroup extends View { public Object getContext() { return null; } }""",
    "android/view/LayoutInflater.java": """package android.view;
public class LayoutInflater {
    public static LayoutInflater from(Object context) { return new LayoutInflater(); }
}""",
    "androidx/recyclerview/widget/RecyclerView.java": """package androidx.recyclerview.widget;
import android.view.*;
public class RecyclerView {
    public static final int NO_POSITION = -1;
    public static boolean computingLayout;
    public static int notifications;
    public static class ViewHolder {
        public final View itemView;
        public int position = NO_POSITION;
        public ViewHolder(View view) { itemView = view; }
        public int getBindingAdapterPosition() { return position; }
    }
    public abstract static class Adapter<T extends ViewHolder> {
        public abstract int getItemCount();
        public abstract T onCreateViewHolder(ViewGroup parent, int type);
        public abstract void onBindViewHolder(T holder, int position);
        public void onViewAttachedToWindow(T holder) {}
        public void onViewDetachedFromWindow(T holder) {}
        public void onViewRecycled(T holder) {}
        public void notifyItemChanged(int position) { notifyDataSetChanged(); }
        public void notifyDataSetChanged() {
            if (computingLayout) throw new IllegalStateException("notify during layout");
            notifications++;
        }
    }
}""",
    "com/fongmi/android/tv/databinding/AdapterArtworkBinding.java": """package com.fongmi.android.tv.databinding;
import android.view.*;
public class AdapterArtworkBinding {
    public final View image = new View();
    private final View root = new View();
    public View getRoot() { return root; }
    public static AdapterArtworkBinding inflate(LayoutInflater i, ViewGroup p, boolean attach) {
        return new AdapterArtworkBinding();
    }
}""",
    "com/fongmi/android/tv/metadata/MovieArtwork.java": """package com.fongmi.android.tv.metadata;
public class MovieArtwork { public String getUrl() { return "artwork"; } }""",
    "com/fongmi/android/tv/utils/ImgUtil.java": """package com.fongmi.android.tv.utils;
public class ImgUtil {
    public static int loads;
    public static void load(String a, String b, android.view.View view, boolean c) { loads++; }
}""",
}

HARNESS = """
import java.util.*;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import com.fongmi.android.tv.ui.adapter.ArtworkAdapter;
import com.fongmi.android.tv.metadata.MovieArtwork;
import com.fongmi.android.tv.utils.ImgUtil;

public class ArtworkRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static ArtworkAdapter.ViewHolder bind(ArtworkAdapter a, int position) {
        ArtworkAdapter.ViewHolder h = a.onCreateViewHolder(new ViewGroup(), 0);
        h.position = position;
        a.onBindViewHolder(h, position);
        a.onViewAttachedToWindow(h);
        return h;
    }
    public static void main(String[] args) {
        ArtworkAdapter[] reference = new ArtworkAdapter[1];
        ArtworkAdapter a = new ArtworkAdapter((p, artwork) -> reference[0].setSelectedPosition(p));
        reference[0] = a;
        a.addAll(Arrays.asList(new MovieArtwork(), new MovieArtwork(), new MovieArtwork()));
        ArtworkAdapter.ViewHolder first = bind(a, 0), second = bind(a, 1), third = bind(a, 2);
        check(first.itemView.isSelected() && !second.itemView.isSelected(), "initial bind");
        int notifications = RecyclerView.notifications, loads = ImgUtil.loads;
        RecyclerView.computingLayout = true;
        second.itemView.focus.onFocusChange(second.itemView, true);
        check(!first.itemView.isSelected() && second.itemView.isSelected(), "focus selection during layout");
        for (int i = 0; i < 30; i++) a.setSelectedPosition(i % 3);
        a.setSelectedPosition(1);
        check(second.itemView.isSelected() && !third.itemView.isSelected(), "carousel selection");
        check(RecyclerView.notifications == notifications && ImgUtil.loads == loads,
                "selection must not notify or reload images");
        a.setSelectedPosition(-1);
        a.setSelectedPosition(3);
        check(second.itemView.isSelected(), "invalid selection ignored");
        a.onViewDetachedFromWindow(second);
        a.setSelectedPosition(2);
        check(second.itemView.isSelected(), "detached holder no longer tracked");
        a.onViewAttachedToWindow(second);
        check(!second.itemView.isSelected(), "cached reattachment deselects without bind");
        a.onViewDetachedFromWindow(first);
        a.setSelectedPosition(0);
        a.onViewAttachedToWindow(first);
        check(first.itemView.isSelected(), "cached reattachment selects without bind");
        a.onViewDetachedFromWindow(first);
        first.position = 2;
        a.onBindViewHolder(first, 2);
        a.onViewAttachedToWindow(first);
        check(!first.itemView.isSelected(), "recycled bind resets old selection");
        second.position = RecyclerView.NO_POSITION;
        second.itemView.focus.onFocusChange(second.itemView, true);
        second.itemView.click.onClick(second.itemView);
        a.onViewDetachedFromWindow(second);
        a.onViewAttachedToWindow(second);
        check(!second.itemView.isSelected(), "invalid holder stays unselected");
        RecyclerView.computingLayout = false;
        a.clear();
        check(a.getItemCount() == 0, "clear data");
        a.onViewDetachedFromWindow(second);
        a.onViewAttachedToWindow(second);
        check(!second.itemView.isSelected(), "NO_POSITION must not match empty selection");
        a.addAll(Collections.singletonList(new MovieArtwork()));
        first.position = 0;
        a.onBindViewHolder(first, 0);
        check(first.itemView.isSelected(), "replacement resets selection to first");
        a.clear();
        a.addAll(null);
        a.setSelectedPosition(0);
        check(a.getItemCount() == 0, "empty replacement safe");
        System.out.println("PASS: layout-time selection, lifecycle, recycling and replacement");
    }
}
"""


class ArtworkAdapterTests(unittest.TestCase):
    def test_selection_without_adapter_notifications(self):
        for command in ("java", "javac"):
            self.assertIsNotNone(shutil.which(command), f"Required JDK command: {command}")
        sources = dict(STUBS)
        sources[ADAPTER] = (ROOT / "app/src/leanback/java" / ADAPTER).read_text()
        sources["ArtworkRegression.java"] = HARNESS
        with tempfile.TemporaryDirectory(prefix="tv-artwork-regression-") as directory:
            root = Path(directory)
            for name, content in sources.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
            compile_result = subprocess.run(
                ["javac", "-encoding", "UTF-8", "-d", str(root / "classes")]
                + [str(root / name) for name in sources], capture_output=True, text=True, timeout=60)
            self.assertEqual(0, compile_result.returncode, compile_result.stderr)
            result = subprocess.run(
                ["java", "-cp", str(root / "classes"), "ArtworkRegression"],
                capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
