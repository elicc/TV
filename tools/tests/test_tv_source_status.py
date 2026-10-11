"""Execute the production footer adapter/state rendering with lightweight Android stubs.

Checks adapter notifications and status policy, not Android measurement or D-pad rendering.
ARTEMIS inspected the actual TCL detail screen before these tests were authored.
"""
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
TV = ROOT / "app/src/leanback"
JAVA = TV / "java/com/fongmi/android/tv"
A = "{http://schemas.android.com/apk/res/android}"
STUBS = {
    "androidx/annotation/NonNull.java": "package androidx.annotation; public @interface NonNull {}",
    "android/view/View.java": """package android.view; public class View {
        public interface OnClickListener { void onClick(View v); }
        public OnClickListener listener; public boolean clickable; public int id;
        public int getId(){return id;}
        public void setOnClickListener(OnClickListener l){listener=l;clickable=true;}
        public void setClickable(boolean b){clickable=b;}
        public void click(){if(clickable && listener!=null)listener.onClick(this);}
    }""",
    "android/view/ViewGroup.java": "package android.view; public class ViewGroup extends View { public Object getContext(){return null;} }",
    "android/view/LayoutInflater.java": """package android.view; public class LayoutInflater {
        public static LayoutInflater from(Object c){return new LayoutInflater();}
        public View inflate(int id,ViewGroup parent,boolean attach){return new android.widget.TextView();}
    }""",
    "android/widget/TextView.java": "package android.widget; public class TextView extends android.view.View { public int text; public void setText(int v){text=v;} }",
    "androidx/recyclerview/widget/RecyclerView.java": """package androidx.recyclerview.widget;
        import android.view.*; public class RecyclerView {
        public static class ViewHolder { public View itemView; public ViewHolder(View v){itemView=v;} }
        public abstract static class Adapter<VH extends ViewHolder> {
            public java.util.List<String> events=new java.util.ArrayList<>();
            public void notifyItemInserted(int p){events.add("insert:"+p);}
            public void notifyItemRemoved(int p){events.add("remove:"+p);}
            public void notifyItemChanged(int p){events.add("change:"+p);}
            public abstract int getItemCount();
            public abstract VH onCreateViewHolder(ViewGroup p,int t);
            public abstract void onBindViewHolder(VH h,int p);
        }}""",
    "com/fongmi/android/tv/R.java": """package com.fongmi.android.tv; public class R {
        public static class layout {public static int adapter_quick_status=1;}
        public static class id {public static int quickStatus=2;}
        public static class string {public static int detail_sources_loading=10,detail_sources_disabled=11,
            detail_sources_error=12,detail_sources_partial=13,detail_sources_empty=14,detail_sources_more=15;}
    }""",
}
ADAPTER_HARNESS = """package com.fongmi.android.tv.ui.adapter;
import android.widget.TextView; import android.view.ViewGroup;
public class FooterHarness {
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        int[] retries={0}; QuickStatusAdapter a=new QuickStatusAdapter(()->retries[0]++);
        check(a.getItemCount()==0,"idle has no footer");
        a.setStatus(10,false);check(a.getItemCount()==1,"loading footer inserted");
        var holder=a.onCreateViewHolder(new ViewGroup(),0);a.onBindViewHolder(holder,0);
        TextView text=(TextView)holder.itemView;
        check(text.text==10 && !text.clickable,"loading not retryable");text.click();
        a.setStatus(10,false);check(a.events.size()==1,"unchanged snapshot does not rebind");
        a.setStatus(13,true);a.onBindViewHolder(holder,0);text.click();
        check(retries[0]==1 && text.text==13,"partial failure retries");
        a.setStatus(10,false);text.listener.onClick(text);
        check(retries[0]==1,"stale bound listener cannot retry while loading");
        a.onBindViewHolder(holder,0);check(!text.clickable,"retry loading disables click");
        a.setStatus(0,false);check(a.getItemCount()==0,"successful completion removes footer");
        text.listener.onClick(text);check(retries[0]==1,"removed footer cannot retry");
        a.setStatus(11,false);a.onBindViewHolder(holder,0);text.click();
        check(retries[0]==1,"disabled cannot retry");
        a.setStatus(12,true);a.onBindViewHolder(holder,0);text.click();
        a.setStatus(14,true);a.onBindViewHolder(holder,0);text.click();
        check(retries[0]==3,"error and empty are retryable");
        a.setStatus(0,false);
        check(a.events.equals(java.util.List.of("insert:0","change:0","change:0","remove:0",
             "insert:0","change:0","change:0","remove:0")),"precise notifications: "+a.events);
        System.out.println("PASS footer transitions, notifications and stale-click guard");
    }
}
"""


class SourceStatusTests(unittest.TestCase):
    def test_status_is_last_item_in_one_horizontal_rail(self):
        root = ET.parse(TV / "res/layout/activity_video.xml").getroot()
        row = next(v for v in root.iter() if v.get(A + "id") == "@+id/quickRow")
        self.assertEqual("horizontal", row.get(A + "orientation"))
        self.assertEqual(["@+id/quickLabel", "@+id/quick"], [v.get(A + "id") for v in row])
        self.assertFalse(any(v.get(A + "id") == "@+id/quickStatus" for v in root.iter()))
        code = (JAVA / "ui/activity/VideoActivity.java").read_text()
        self.assertIn("new ConcatAdapter(mQuickAdapter, mQuickStatusAdapter)", code)
        self.assertIn("new QuickStatusAdapter(this::onSourceStatusClick)", code)
        rows = code.split("DETAIL_ROW_IDS = {", 1)[1].split("}", 1)[0]
        self.assertNotIn("R.id.quickStatus", rows)
        footer = ET.parse(TV / "res/layout/adapter_quick_status.xml").getroot()
        self.assertEqual("true", footer.get(A + "focusable"))
        self.assertEqual("wrap_content", footer.get(A + "layout_height"))
        self.assertEqual("62dp", footer.get(A + "minHeight"))

    def compile_run(self, files, main):
        with tempfile.TemporaryDirectory() as temp:
            paths = []
            for name, code in {**STUBS, **files}.items():
                path = Path(temp) / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(code)
                paths.append(str(path))
            subprocess.run(["javac", "-d", temp, *paths], check=True, timeout=30)
            subprocess.run(["java", "-cp", temp, main], check=True, timeout=15)

    def test_production_footer_state_and_retry(self):
        self.compile_run({
            "com/fongmi/android/tv/ui/adapter/QuickStatusAdapter.java": (JAVA / "ui/adapter/QuickStatusAdapter.java").read_text(),
            "com/fongmi/android/tv/ui/adapter/FooterHarness.java": ADAPTER_HARNESS,
        }, "com.fongmi.android.tv.ui.adapter.FooterHarness")

    def test_production_render_policy_with_and_without_results(self):
        code = (JAVA / "ui/activity/VideoActivity.java").read_text()
        start = code.index("public void renderSourceSearch(")
        method = code[start:code.index("\n    }", start) + 6]
        for signature in ("private void onSourceStatusClick()", "private void loadMoreSourcesNearEnd(int position)"):
            start = code.index(signature)
            method += "\n" + code[start:code.index("\n    }", start) + 6]
        harness = """import android.view.View; import com.fongmi.android.tv.R;
        import java.util.*;
        public class RenderHarness {
            static class VodSourceResult {
                enum Status {IDLE,LOADING,READY,EMPTY,ERROR,DISABLED}
                Status s; boolean loading,more; int failed; List<Integer> items;
                VodSourceResult(Status s,int n,boolean l,int f){this.s=s;loading=l;failed=f;items=Collections.nCopies(n,1);}
                long generation(){return 1;} boolean hasMore(){return more;} Status status(){return s;} boolean isLoading(){return loading;}
                int failed(){return failed;} List<Integer> items(){return items;}
            }
            static class Quick {View focus;int selected=-1;boolean requested,focused;
                void resetToStartAfterLayout(){}boolean hasFocus(){return focused;}int getSelectedPosition(){return selected;}
                View findFocus(){return focus;}void setSelectedPosition(int p){selected=p;}void requestFocus(){requested=true;}}
            static class Binding {Quick quick=new Quick();Object quickRow=new Object();}
            static class Items {int n;void setItems(List<Integer> i){n=i.size();}boolean isEmpty(){return n==0;}int getItemCount(){return n;}}
            static class Footer {int message;boolean retry;void setStatus(int m,boolean r){message=m;retry=r;}}
            boolean mSourceHasMore,mSourceLoading;long mSourceGeneration;int mSourcePageRequestedAtCount=-1;
            Binding mBinding=new Binding();Items mQuickAdapter=new Items();Footer mQuickStatusAdapter=new Footer();boolean visible;
            static class Controller {int more,retry;void loadMoreSources(){more++;}void retrySources(){retry++;}}
            Controller mVod=new Controller();boolean finishing;
            boolean isFinishing(){return finishing;}boolean isDestroyed(){return false;}
            void setRowVisibility(Object r,Object q,boolean v){visible=v;}
        """ + method + """
            static void check(boolean b){if(!b)throw new AssertionError("render state mismatch");}
            public static void main(String[] args){
                RenderHarness h=new RenderHarness();
                for(int n:new int[]{0,3}) for(boolean loading:new boolean[]{false,true}) for(int failed:new int[]{0,2}){
                    var status=n>0?VodSourceResult.Status.READY:loading?VodSourceResult.Status.LOADING:
                        failed>0?VodSourceResult.Status.ERROR:VodSourceResult.Status.EMPTY;
                    h.renderSourceSearch(new VodSourceResult(status,n,loading,failed));
                    boolean footer=n==0||loading||failed>0;
                    check(h.visible);check((h.mQuickStatusAdapter.message!=0)==footer);
                    check(h.mQuickStatusAdapter.retry==(footer&&!loading));check(h.mQuickAdapter.n==n);
                    if(loading)check(h.mQuickStatusAdapter.message==R.string.detail_sources_loading);
                    if(n>0&&!loading&&failed>0)check(h.mQuickStatusAdapter.message==R.string.detail_sources_partial);
                }
                var paused=new VodSourceResult(VodSourceResult.Status.READY,3,false,2);paused.more=true;
                h.renderSourceSearch(paused);check(h.mQuickStatusAdapter.message==R.string.detail_sources_more&&h.mQuickStatusAdapter.retry);
                check(h.mSourceHasMore&&!h.mSourceLoading);
                h.renderSourceSearch(new VodSourceResult(VodSourceResult.Status.IDLE,0,false,0));
                check(!h.visible&&h.mQuickStatusAdapter.message==0);
                h.renderSourceSearch(new VodSourceResult(VodSourceResult.Status.DISABLED,0,false,0));
                check(h.visible&&!h.mQuickStatusAdapter.retry&&h.mQuickStatusAdapter.message==R.string.detail_sources_disabled);
                View v=new View();v.id=R.id.quickStatus;h.mBinding.quick.focus=v;
                h.renderSourceSearch(new VodSourceResult(VodSourceResult.Status.READY,3,false,0));
                check(h.mBinding.quick.selected==2&&h.mBinding.quick.requested);
                h.mQuickAdapter.n=10;h.mSourceHasMore=true;h.mSourceLoading=false;
                h.mBinding.quick.selected=6;h.loadMoreSourcesNearEnd(6);check(h.mVod.more==0);
                h.mBinding.quick.focused=true;h.mBinding.quick.selected=4;h.loadMoreSourcesNearEnd(4);check(h.mVod.more==0);
                h.mBinding.quick.selected=6;h.loadMoreSourcesNearEnd(6);check(h.mVod.more==1);
                h.loadMoreSourcesNearEnd(6);check(h.mVod.more==1);
                h.mBinding.quick.selected=10;h.loadMoreSourcesNearEnd(10);check(h.mVod.more==1);
                h.onSourceStatusClick();check(h.mVod.more==2);
                h.mQuickAdapter.n=13;h.mBinding.quick.selected=12;h.mSourceLoading=true;
                h.loadMoreSourcesNearEnd(12);check(h.mVod.more==2);
                h.mSourceLoading=false;h.loadMoreSourcesNearEnd(12);check(h.mVod.more==3);
                h.mSourceHasMore=false;h.onSourceStatusClick();check(h.mVod.retry==1);
                System.out.println("PASS render matrix, focus target and demand gating (no empty-page drain)");
            }
        }
        """
        self.compile_run({"RenderHarness.java": harness}, "RenderHarness")


if __name__ == "__main__":
    unittest.main()
