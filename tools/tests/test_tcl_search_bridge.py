"""Execute the production pure-Java policy; no Android/JUnit dependencies."""
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
POLICY = ROOT / "app/src/leanback/java/com/fongmi/android/tv/voice/TclSearchBridgePolicy.java"
HARNESS = r'''import com.fongmi.android.tv.voice.TclSearchBridgePolicy;
public class BridgeHarness {
 static void ok(boolean v,String s){if(!v)throw new AssertionError(s);}
 static String title(String s){return "“"+s+"”的全部影视结果（24条）";}
 public static void main(String[] args){
  String a=title("流浪地球"),b=title("Avatar 2：水之道");
  ok("流浪地球".equals(TclSearchBridgePolicy.parse(a)),"Chinese");
  ok("Avatar 2：水之道".equals(TclSearchBridgePolicy.parse(b)),"punctuation");
  String game="“让子弹飞”的游戏视频结果（1条）";
  ok("让子弹飞".equals(TclSearchBridgePolicy.parse(game)),"observed TCL game-video category");
  ok("张译的电影".equals(TclSearchBridgePolicy.parse("“张译的电影”的电影结果（51条）")),"observed actor query category");
  for(int i=0;i<100;i++)
   ok("片名".equals(TclSearchBridgePolicy.parse("“片名”的新分类"+i+"结果（1条）")),"category is not enumerated");
  ok("电影".equals(TclSearchBridgePolicy.parse("“电影”的电影结果（0条）")),"zero count does not prevent local search");
  ok(TclSearchBridgePolicy.parse("“没有找到电影,为你推荐以下资源”的游戏视频结果（1条）")==null,"category does not bypass recommendation rejection");
  TclSearchBridgePolicy category=new TclSearchBridgePolicy();
  ok(category.observe(1,game,0)==400,"category candidate");
  ok("让子弹飞".equals(category.take(1,game,400)),"category dispatch");
  ok(category.observe(1,title("让子弹飞"),500)<0,"same query in another category does not replay");
  for(String s:new String[]{null,"",title(" "),title("没有找到电影太空旅行,为你推荐以下资源"),title("a\nb"),title("a".repeat(101)),"流浪地球",a+"垃圾", "“电影”的结果（1条）", "“电影”的电\n影结果（1条）"})
   ok(TclSearchBridgePolicy.parse(s)==null,"reject: "+s);
  TclSearchBridgePolicy p=new TclSearchBridgePolicy();
  ok(p.observe(1,a,0)==400,"initial delay");
  for(int i=1;i<100;i++)p.observe(1,a,i);
  ok(p.take(1,a,399)==null,"not early");
  ok("流浪地球".equals(p.take(1,a,400)),"stable takes once");
  ok(p.observe(1,a,10000)<0 && p.take(1,a,11000)==null,"never TTL replay");
  ok(p.observe(2,a,12000)<0,"old page new window rejected");
  p.observe(2,b,13000);ok(p.take(2,b,13400)!=null,"different query");
  ok(p.observe(1,a,14000)<0,"old earlier title rejected");
  p.observe(1,null,15000);ok(p.observe(1,a,15001)<0,"missing node not new query");
  p.observe(1,"",16000);p.observe(1,a,16001);
  ok(p.take(1,a,16401)!=null,"explicit clearing permits repeat");
  p.observe(3,title("盗梦空间"),17000);p.cancel();ok(p.take(3,title("盗梦空间"),18000)==null,"cancel");
  p.observe(4,title("A"),19000);p.observe(4,title("B"),19100);
  ok(p.take(4,title("A"),19500)==null,"old timer mismatch");
  ok("B".equals(p.take(4,title("B"),19500)),"latest wins");
  p.observe(4,title("C"),20000);ok(p.take(5,title("C"),20400)==null,"window mismatch");
  p.prime(title("D"));ok(p.observe(6,title("D"),21000)<0,"baseline ignored");
  p.observe(7,title("E"),22000);ok(p.take(7,title("E"),25001)==null,"expired");
  System.out.println("PASS: parser, stability, 100-event flood, old pages, repeat, cancellation, freshness, expiry");
 }
}'''

class BridgePolicyTests(unittest.TestCase):
    def test_production_behavior(self):
        self.assertTrue(POLICY.exists(), "production policy missing")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            harness = root / "BridgeHarness.java"
            harness.write_text(HARNESS)
            subprocess.run(["javac", "-encoding", "UTF-8", "-d", directory,
                            str(POLICY), str(harness)], check=True, capture_output=True, text=True)
            result = subprocess.run(["java", "-cp", directory, "BridgeHarness"],
                                    check=True, capture_output=True, text=True)
            self.assertIn("PASS:", result.stdout)

class BridgeContractsTests(unittest.TestCase):
    def test_leanback_only_and_minimal_scope(self):
        android = "{http://schemas.android.com/apk/res/android}"
        root = ET.parse(ROOT / "app/src/leanback/res/xml/tcl_search_bridge.xml").getroot()
        self.assertEqual(root.get(android + "packageNames"), "com.tcl.wholenetsearch")
        self.assertEqual(root.get(android + "accessibilityFlags"), "flagReportViewIds")
        manifest = ET.parse(ROOT / "app/src/leanback/AndroidManifest.xml")
        service = next(s for s in manifest.findall(".//service") if s.get(android+"name").endswith("TclSearchBridgeService"))
        self.assertEqual(service.get(android+"permission"), "android.permission.BIND_ACCESSIBILITY_SERVICE")
        for flavor in ("main", "mobile"):
            self.assertNotIn("TclSearchBridgeService", (ROOT/f"app/src/{flavor}/AndroidManifest.xml").read_text())

    def test_ticket_and_default_disabled(self):
        source = ROOT / "app/src/leanback/java/com/fongmi/android/tv/voice/TclSearchBridgeSettings.java"
        files = {
            "android/app/SearchManager.java": 'package android.app; public class SearchManager { public static final String QUERY="query"; }',
            "android/os/SystemClock.java": 'package android.os; public class SystemClock { public static long now=0; public static long elapsedRealtime(){return now;} }',
            "android/content/Intent.java": 'package android.content; public class Intent { public static final String ACTION_SEARCH="search"; private String action; private java.util.Map<String,String> extras=new java.util.HashMap<>(); public Intent(String a){action=a;} public String getAction(){return action;} public String getStringExtra(String k){return extras.get(k);} public Intent putExtra(String k,String v){extras.put(k,v);return this;} }',
            "com/github/catvod/utils/Prefers.java": 'package com.github.catvod.utils; public class Prefers {static boolean v; public static boolean getBoolean(String k,boolean d){return v;} public static void put(String k,boolean b){v=b;} }',
            "TicketHarness.java": r'''import com.fongmi.android.tv.voice.TclSearchBridgeSettings;
import android.content.Intent; import android.os.SystemClock;
public class TicketHarness {
 static void ok(boolean b){if(!b)throw new AssertionError();}
 static Intent request(String id,String word){return new Intent(Intent.ACTION_SEARCH).putExtra(TclSearchBridgeSettings.EXTRA_REQUEST,id).putExtra("query",word);}
 public static void main(String[] args){
  ok(!TclSearchBridgeSettings.isEnabled());
  TclSearchBridgeSettings.setEnabled(true);
  String id=TclSearchBridgeSettings.begin("test");
  ok(!TclSearchBridgeSettings.consume(request("forged","test")));
  ok(TclSearchBridgeSettings.consume(request(id,"test")));
  ok(!TclSearchBridgeSettings.consume(request(id,"test")));
  id=TclSearchBridgeSettings.begin("test");SystemClock.now=16000;
  ok(!TclSearchBridgeSettings.consume(request(id,"test")));
  id=TclSearchBridgeSettings.begin("test");TclSearchBridgeSettings.setEnabled(false);
  ok(!TclSearchBridgeSettings.consume(request(id,"test")));
  for(int i=0;i<60;i++)TclSearchBridgeSettings.record("stage");
  ok(TclSearchBridgeSettings.diagnostics().split("\n").length==50);
  TclSearchBridgeSettings.clearDiagnostics();ok(TclSearchBridgeSettings.diagnostics().isEmpty());
 }
}'''
        }
        with tempfile.TemporaryDirectory() as directory:
            paths = []
            for name, text in files.items():
                path = Path(directory)/name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text)
                paths.append(str(path))
            subprocess.run(["javac", "-encoding", "UTF-8", "-d", directory, str(source)] + paths, check=True)
            subprocess.run(["java", "-cp", directory, "TicketHarness"], check=True)

if __name__ == "__main__":
    unittest.main()
