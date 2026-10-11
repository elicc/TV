"""TV picker contracts and executable production paging methods (not device UI tests)."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
from tools.tests import test_vod_resume

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/leanback/java/com/fongmi/android/tv/ui"
ANDROID = "{http://schemas.android.com/apk/res/android}"
APP = "{http://schemas.android.com/apk/res-auto}"


def method(code, signature):
    start = code.index(signature)
    return code[start:code.index("\n    }", start) + 6]


class EpisodePickerTests(unittest.TestCase):
    def test_toolbar_order_and_focus(self):
        root = ET.parse(ROOT / "app/src/leanback/res/layout/view_control_vod_action.xml").getroot()
        actions = list(root)[0]
        ids = [x.get(ANDROID + "id", "").split("/")[-1] for x in actions]
        self.assertEqual(ids[ids.index("ending") + 1], "episodes")
        self.assertEqual(ids[ids.index("episodes") + 1], "scale")
        code = (JAVA / "activity/VideoActivity.java").read_text()
        focus = method(code, "private boolean movePlaybackActionFocus(boolean right)")
        self.assertIn("collectPlaybackActions(mBinding.control.action.getRoot(), visible)", focus)
        self.assertNotIn("View[] actions", focus)
        visible = method(code, "private void updateEpisodeButton()")
        self.assertIn("isFullscreen()", visible)
        self.assertIn("getItemCount() > 1", visible)


    def test_modal_lifecycle_and_existing_playback_route(self):
        code = (JAVA / "activity/VideoActivity.java").read_text()
        self.assertIn("isScrubbing() || episodePickerVisible", method(code, "private void setR1Callback()"))
        self.assertIn("App.removeCallbacks(mR1)", method(code, "private void showEpisodePicker()"))
        pick = method(code, "public void pickEpisode(int generation, Episode episode)")
        self.assertIn("episodePickerItems(generation).contains(episode)", pick)
        self.assertIn("if (!episode.isSelected()) mVod.selectEpisode(episode)", pick)
        close = method(code, "public void closeEpisodePicker(int generation, boolean picked)")
        self.assertIn("generation != episodePickerGeneration", close)
        self.assertIn("showControl(mBinding.control.action.episodes)", close)
        self.assertIn("hideControl()", close)
        for signature in ("public void prepareSource(Vod item)", "private void setEpisodeAdapter(List<Episode> items)", "protected void onDestroy()"):
            self.assertIn("invalidateEpisodePicker()", method(code, signature))
        dialog = (JAVA / "dialog/EpisodePickerDialog.java").read_text()
        self.assertIn("Gravity.BOTTOM", dialog)
        self.assertIn("setWidth(1f)", dialog)
        self.assertIn("params.y = host.episodePickerBottomOffset()", dialog)
        self.assertIn("holder.itemView.requestFocus()", dialog)
        self.assertIn("focused && !tab.isSelected()", dialog)
        layout = ET.parse(ROOT / "app/src/leanback/res/layout/dialog_episode_picker.xml").getroot()
        self.assertIsNotNone(layout.find(".//com.google.android.material.tabs.TabLayout"))
        row = layout.find(".//androidx.leanback.widget.HorizontalGridView")
        self.assertEqual("48dp", row.get(ANDROID + "layout_height"))
        self.assertNotIn("pause()", dialog)
        self.assertNotIn("startActivity", dialog)
        self.assertNotIn("getName()", dialog)
        adapter = (JAVA / "adapter/EpisodeAdapter.java").read_text()
        self.assertIn("View.NO_ID : nextFocusUp", adapter)
        self.assertIn("item.getIndex() : numberOffset + position", adapter)

    def test_detail_tab_style_and_upward_entrance(self):
        resources = ROOT / "app/src/leanback/res"
        layout = ET.parse(resources / "layout/dialog_episode_picker.xml").getroot()
        tabs = layout.find(".//com.google.android.material.tabs.TabLayout")
        self.assertEqual("start", tabs.get(APP + "tabGravity"))
        self.assertEqual("scrollable", tabs.get(APP + "tabMode"))
        self.assertEqual("0dp", tabs.get(APP + "tabMinWidth"))
        self.assertFalse(any(x.get(ANDROID + "text") == "@string/tv_episode_picker_hint" for x in layout.iter()))
        tab = ET.parse(resources / "layout/view_episode_picker_tab.xml").getroot()
        self.assertEqual("@drawable/selector_metadata_tab", tab.get(ANDROID + "background"))
        self.assertEqual("@color/selector_metadata_tab_text", tab.get(ANDROID + "textColor"))
        self.assertEqual("true", tab.get(ANDROID + "duplicateParentState"))
        dialog = (JAVA / "dialog/EpisodePickerDialog.java").read_text()
        animation = method(dialog, "private void animateEntrance()")
        self.assertIn("TvMotion.motionEnabled", animation)
        self.assertIn("panel.setTranslationY(panel.getHeight())", animation)
        self.assertIn("translationY(0f)", animation)
        self.assertIn("TvMotion.DRAWER", animation)
        self.assertIn("panel.animate().cancel()", method(dialog, "public void onStop()"))
        self.assertIn("window.setWindowAnimations(0)", dialog)

    def test_runtime_toolbar_actions_include_injected_buttons(self):
        code = (JAVA / "activity/VideoActivity.java").read_text()
        production = method(code, "private void collectPlaybackActions(View view, List<View> actions)")
        harness = r"""package com.fongmi.android.tv.playback.vod;
import java.util.*;
public class ActionHarness {
 static class View {static final int VISIBLE=0,NO_ID=-1;static int next;int visibility,id=-1,down;boolean enabled=true,focusable,click;String name;
 View(String n){name=n;}int getVisibility(){return visibility;}boolean isEnabled(){return enabled;}boolean isFocusable(){return focusable;}boolean hasOnClickListeners(){return click;}
 void setFocusable(boolean b){focusable=b;}int getId(){return id;}void setId(int i){id=i;}static int generateViewId(){return ++next;}void setNextFocusDownId(int i){down=i;}}
 static class ViewGroup extends View {List<View> children=new ArrayList<>();ViewGroup(String n,View...c){super(n);children.addAll(List.of(c));}int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}}
 PRODUCTION
 public static void main(String[] args){ActionHarness h=new ActionHarness();View ending=new View("ending"),cloud=new View("cloud"),episodes=new View("episodes"),disabled=new View("disabled"),nested=new View("nested");ending.focusable=episodes.focusable=true;cloud.click=true;disabled.click=true;disabled.enabled=false;nested.click=true;
 ViewGroup advanced=new ViewGroup("advanced",nested);advanced.visibility=8;ViewGroup root=new ViewGroup("root",ending,cloud,episodes,disabled,advanced);List<View> list=new ArrayList<>();h.collectPlaybackActions(root,list);
 if(!list.equals(List.of(ending,cloud,episodes)))throw new AssertionError("injected button skipped or hidden action included");
 if(!cloud.focusable||cloud.id==-1||cloud.down!=cloud.id)throw new AssertionError("injected button is not safely focusable");
 advanced.visibility=0;list.clear();h.collectPlaybackActions(root,list);if(!list.equals(List.of(ending,cloud,episodes,nested)))throw new AssertionError("advanced visual order");
 ViewGroup custom=new ViewGroup("custom",new View("label"));custom.click=true;list.clear();h.collectPlaybackActions(custom,list);if(!list.equals(List.of(custom)))throw new AssertionError("clickable group lost");
 System.out.println("PASS runtime toolbar: injected, hidden, disabled, nested and clickable-group actions");}
}""".replace("PRODUCTION", production)
        test_vod_resume.VodResumeTests().run_harness(harness, "ActionHarness")

    def test_production_paging_and_numeric_ordinals(self):
        dialog = (JAVA / "dialog/EpisodePickerDialog.java").read_text()
        production = "\n".join(method(dialog, s) for s in (
            "private int number(int position)", "private int selectedPosition()", "private void showPage(int page)"))
        harness = r"""package com.fongmi.android.tv.playback.vod;
import java.util.*;import com.fongmi.android.tv.bean.Episode;
public class PickerHarness {
 static class R {static class id {static int grid=9;}}
 static final int PAGE_SIZE=16;List<Episode> episodes=new ArrayList<>();Adapter adapter=new Adapter();Binding binding=new Binding();
 static class Adapter {List<Episode> items;int offset;void setNextFocusUp(int id){}Adapter numbersOnly(int n){offset=n;return this;}void addAll(List<Episode> e){items=new ArrayList<>(e);}int getPosition(){for(int i=0;i<items.size();i++)if(items.get(i).isSelected())return i;return 0;}}
 static class Binding {Tabs tabs=new Tabs();Grid grid=new Grid();}
 static class View {int getId(){return 1;}View getParent(){return this;}}
 static class Tabs {TabLayout.Tab getTabAt(int i){return new TabLayout.Tab();}}
 static class TabLayout {static class Tab {View getCustomView(){return new View();}}}
 static class Child {boolean selected;void setSelected(boolean b){selected=b;}}
 static class Ranges {Child[] children={new Child(),new Child(),new Child()};int getChildCount(){return children.length;}Child getChildAt(int i){return children[i];}}
 static class Grid extends View {int position;boolean focused;void setSelectedPosition(int p){position=p;}void requestFocus(){focused=true;}}
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 PRODUCTION
 public static void main(String[] args){
  PickerHarness p=new PickerHarness();for(int i=0;i<41;i++){Episode e=Episode.create("duplicate nonnumeric name","url/"+i);e.setIndex(i);p.episodes.add(e);}
  p.episodes.get(32).setSelected(true);check(p.selectedPosition()==32,"initial position is current episode");
  p.showPage(p.selectedPosition()/PAGE_SIZE);check(p.adapter.items.size()==9&&p.adapter.offset==32,"initial page contains 33-41");
  check(p.binding.grid.position==0,"focus lands on current item in page");check(p.number(32)==33,"label is numeric ordinal, not name");
  p.showPage(2);check(p.adapter.items.size()==9&&p.number(40)==41,"last partial page");check(p.binding.grid.position==0,"other page focuses first item");
  p.showPage(0);check(p.adapter.items.size()==16&&p.adapter.offset==0,"page switching and range selection");
  Collections.reverse(p.episodes);check(p.number(0)==41&&p.selectedPosition()==8,"reversed order retains original numbers");
  p.showPage(0);check(p.binding.grid.position==8,"current focus preserved for reverse display");
  p.episodes.get(0).setIndex(-1);check(p.number(0)==1,"unindexed entry fallback is bounded numeric position");
  p.episodes=new ArrayList<>(p.episodes.subList(0,1));p.showPage(0);check(p.adapter.items.size()==1,"one-item paging is safe (host hides button)");
  System.out.println("PASS TV episode picker paging: "+checks+" checks");
 }
}
""".replace("PRODUCTION", production)
        test_vod_resume.VodResumeTests().run_harness(harness, "PickerHarness")


if __name__ == "__main__":
    unittest.main()
