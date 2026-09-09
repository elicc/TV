#!/usr/bin/env python3
"""Dependency-free behavioral smoke test of the production TV RecordAdapter.

Run from any directory: python3 tools/tests/test_tv_record_adapter.py
Requires only Python 3 and a JDK (java/javac on PATH). Compiles the actual
production adapter in a temporary directory; leaves no generated repo files.

Android view/recycler bindings and Gson/preferences are minimal boundary stubs.
This tests list behavior and persistence calls, NOT Gson JSON roundtripping,
Android resource inflation, dialogs, layout, or real D-pad focus restoration.
Those remain Gradle/emulator integration checks.
"""

from pathlib import Path
import shutil
import subprocess
import tempfile


# Only platform/generated/storage boundaries are replaced; never copy the adapter.
STUBS = {'android/view/LayoutInflater.java': 'package android.view; public class LayoutInflater {public static '
                                     'LayoutInflater from(Object c) {return new LayoutInflater();} }',
 'android/view/View.java': 'package android.view; public class View { public interface '
                           'OnLongClickListener { boolean onLongClick(View v); } public interface '
                           'OnClickListener { void onClick(View v); } public void '
                           'setOnClickListener(OnClickListener l) {} public void '
                           'setOnLongClickListener(OnLongClickListener l) {} public void setText(String '
                           's) {} }',
 'android/view/ViewGroup.java': 'package android.view; public class ViewGroup extends View { public '
                                'Object getContext() {return null;} }',
 'androidx/annotation/NonNull.java': 'package androidx.annotation; public @interface NonNull {}',
 'androidx/recyclerview/widget/RecyclerView.java': 'package androidx.recyclerview.widget; import '
                                                   'android.view.*; public class RecyclerView { public '
                                                   'static final int NO_POSITION=-1; public static '
                                                   'class ViewHolder { public final View itemView; '
                                                   'public int position=0; public ViewHolder(View '
                                                   'v){itemView=v;} public int '
                                                   'getBindingAdapterPosition(){return position;} } '
                                                   'public abstract static class Adapter<T> {public '
                                                   'abstract int getItemCount(); public abstract T '
                                                   'onCreateViewHolder(ViewGroup p,int t); public '
                                                   'abstract void onBindViewHolder(T h,int p); public '
                                                   'void notifyDataSetChanged(){} public void '
                                                   'notifyItemRemoved(int p){} public void '
                                                   'notifyItemRangeRemoved(int p,int n){} } }',
 'com/fongmi/android/tv/App.java': 'package com.fongmi.android.tv; public class App {public static '
                                   'Serializer gson(){return new Serializer();} public static class '
                                   'Serializer {public <T> T fromJson(String s,Object t){throw new '
                                   'UnsupportedOperationException();} public String toJson(Object '
                                   'o){return o.toString();}}}',
 'com/fongmi/android/tv/databinding/AdapterSearchRecordBinding.java': 'package '
                                                                      'com.fongmi.android.tv.databinding; '
                                                                      'import android.view.*; public '
                                                                      'class AdapterSearchRecordBinding '
                                                                      '{public View text=new View(); '
                                                                      'public View getRoot(){return '
                                                                      'text;} public static '
                                                                      'AdapterSearchRecordBinding '
                                                                      'inflate(LayoutInflater '
                                                                      'l,ViewGroup v,boolean a){return '
                                                                      'new '
                                                                      'AdapterSearchRecordBinding();} }',
 'com/fongmi/android/tv/setting/Setting.java': 'package com.fongmi.android.tv.setting; public class '
                                               'Setting {public static String keyword=""; public static '
                                               'String getKeyword(){return keyword;} public static void '
                                               'putKeyword(String s){keyword=s;} }',
 'com/google/gson/reflect/TypeToken.java': 'package com.google.gson.reflect; public class TypeToken '
                                           '{public static TypeToken getParameterized(Class<?> c, '
                                           'Class<?> s){return new TypeToken();} public Object '
                                           'getType(){return null;} }'}

HARNESS = r"""
import com.fongmi.android.tv.ui.adapter.RecordAdapter;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.databinding.AdapterSearchRecordBinding;

public class RecordRegression {
    private static int count = -1;
    private static int checks;
    private static String longClicked;

    private static void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        checks++;
    }

    public static void main(String[] args) {
        RecordAdapter adapter = new RecordAdapter(new RecordAdapter.OnClickListener() {
            public void onItemClick(String text) {}
            public void onDataChanged(int size) { count = size; }
            public void onItemLongClick(String text) { longClicked = text; }
        });
        check(count == 0, "initial empty callback");
        for (int i = 0; i < 12; i++) adapter.add("term" + i);
        check(adapter.getItemCount() == 9, "history is capped at nine");
        check(adapter.getRecords()[0].equals("term11"), "newest term comes first");
        adapter.add("term5");
        check(adapter.getItemCount() == 9 && adapter.getRecords()[0].equals("term5"),
                "duplicate term moves to front without growing history");
        String[] snapshot = adapter.getRecords();
        snapshot[0] = "changed";
        check(adapter.getRecords()[0].equals("term5"), "caller cannot mutate records via snapshot");
        adapter.remove("missing");
        check(adapter.getItemCount() == 9, "stale deletion is harmless");

        RecordAdapter.ViewHolder holder = adapter.new ViewHolder(new AdapterSearchRecordBinding());
        check(holder.onLongClick(holder.itemView) && longClicked.equals("term5")
                        && adapter.getItemCount() == 9,
                "long press requests confirmation without deleting");
        holder.position = -1;
        check(!holder.onLongClick(holder.itemView), "detached holder cannot delete a term");

        adapter.remove("term5");
        check(count == 8 && !Setting.keyword.contains("term5"),
                "single deletion writes the remaining terms and reports the new count");
        adapter.clear();
        check(count == 0 && adapter.getItemCount() == 0 && Setting.keyword.equals("[]"),
                "clear writes an empty list and reports zero");
        adapter.clear();
        check(count == 0, "clearing an empty list is safe");
        adapter.add("last");
        adapter.remove("last");
        check(count == 0 && Setting.keyword.equals("[]"), "deleting final term reports zero");
        System.out.println("PASS: " + checks + " RecordAdapter behavior checks");
    }
}
"""


def main():
    for command in ("javac", "java"):
        if shutil.which(command) is None:
            raise SystemExit(f"Required JDK command not found: {command}")
    repository = Path(__file__).resolve().parents[2]
    adapter = repository / "app/src/leanback/java/com/fongmi/android/tv/ui/adapter/RecordAdapter.java"
    sources = dict(STUBS)
    sources["RecordRegression.java"] = HARNESS
    sources["com/fongmi/android/tv/ui/adapter/RecordAdapter.java"] = adapter.read_text()
    with tempfile.TemporaryDirectory(prefix="tv-record-regression-") as directory:
        root = Path(directory)
        for name, source in sources.items():
            target = root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(source)
        classes = root / "classes"
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(classes)]
                       + [str(root / name) for name in sources], check=True)
        subprocess.run(["java", "-cp", str(classes), "RecordRegression"], check=True)
    print("Boundary stubs: Android/Gson/preferences. Runtime focus is not tested.")


if __name__ == "__main__":
    main()
