"""Production source discovery policy/state/runner with deterministic IO boundaries.

Uses the project's cached Guava and real timeout/executor implementation. Android,
site IO and playback side effects are doubles; this does not certify TV focus/UI.
Prerequisite: Gradle dependencies downloaded, JDK 21+, Python 3.
"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com/fongmi/android/tv"
MAIN = ROOT / "app/src/main/java"
STUBS = {
    "androidx/annotation/Nullable.java": "package androidx.annotation; public @interface Nullable {}",
    "androidx/annotation/NonNull.java": "package androidx.annotation; public @interface NonNull {}",
    "androidx/media3/common/MediaMetadata.java": "package androidx.media3.common; public class MediaMetadata {}",
    PACKAGE + "/App.java": """package com.fongmi.android.tv;
public class App {
    public static final java.util.concurrent.BlockingQueue<Runnable> queue = new java.util.concurrent.LinkedBlockingQueue<>();
    public static void post(Runnable r) { queue.add(r); }
}""",
    PACKAGE + "/Constant.java": "package com.fongmi.android.tv; public class Constant { public static final long TIMEOUT_SEARCH = 2000; }",
    "com/github/catvod/utils/Trans.java": "package com.github.catvod.utils; public class Trans { public static String t2s(String s) { return s; } }",
    PACKAGE + "/bean/Vod.java": """package com.fongmi.android.tv.bean;
public record Vod(String key, String id, String name) {
    public String getSiteKey(){return key;} public String getId(){return id;} public String getName(){return name;}
}""",
    PACKAGE + "/bean/Result.java": """package com.fongmi.android.tv.bean;
public record Result(java.util.List<Vod> list, String msg) {
    public static Result empty(){return new Result(java.util.List.of(), "");}
    public java.util.List<Vod> getList(){return list;} public String getMsg(){return msg;}
}""",
    PACKAGE + "/bean/Site.java": """package com.fongmi.android.tv.bean;
public record Site(String key, boolean searchable, boolean quick, boolean changeable) {
    public boolean isSearchable(){return searchable;} public boolean isQuickSearch(){return quick;}
    public boolean isChangeable(){return changeable;}
}""",
    PACKAGE + "/bean/History.java": "package com.fongmi.android.tv.bean; public class History {}",
    PACKAGE + "/bean/Episode.java": "package com.fongmi.android.tv.bean; public class Episode {}",
    PACKAGE + "/bean/Flag.java": """package com.fongmi.android.tv.bean;
public record Flag(java.util.List<Episode> episodes, boolean selected) {
    public java.util.List<Episode> getEpisodes(){return episodes;}
    public boolean isSelected(){return selected;} public int getPosition(){return 0;}
}""",
    PACKAGE + "/api/config/VodConfig.java": """package com.fongmi.android.tv.api.config;
import java.util.*; import com.fongmi.android.tv.bean.Site;
public class VodConfig {
    static final VodConfig INSTANCE=new VodConfig(); public static VodConfig get(){return INSTANCE;}
    public List<Site> sites=new ArrayList<>(); public List<Site> getSites(){return sites;}
    public Site getSite(String key){return sites.stream().filter(s->s.key().equals(key)).findFirst().orElseThrow();}
}""",
    PACKAGE + "/api/SiteApi.java": """package com.fongmi.android.tv.api;
import java.util.*; import java.util.concurrent.*; import com.fongmi.android.tv.bean.*;
public class SiteApi {
    public static final Map<String, Callable<Result>> jobs = new ConcurrentHashMap<>();
    public static final BlockingQueue<String> started = new LinkedBlockingQueue<>();
    public static Result searchContent(Site site,String word,boolean quick,String page) throws Exception {
        started.add(site.key()); return jobs.get(site.key()).call();
    }
}""",
    PACKAGE + "/playback/vod/VodPlayRequest.java": """package com.fongmi.android.tv.playback.vod;
import com.fongmi.android.tv.bean.*;
public class VodPlayRequest { public boolean matches(String key,Flag f,Episode e){return false;} }
""",
    PACKAGE + "/playback/vod/VodPlaybackController.java": """package com.fongmi.android.tv.playback.vod;
import com.fongmi.android.tv.bean.*;
public class VodPlaybackController {
    public int switches, lines; public VodPlaybackHost host;
    public void fallbackSource(Vod v){switches++;host.prepareSource(v);}
    public void selectFlag(Flag f){lines++;}
}""",
}

POLICY_HARNESS = r"""
package com.fongmi.android.tv.playback.vod;
import java.lang.reflect.*; import java.util.*;
import com.fongmi.android.tv.bean.*;
import com.fongmi.android.tv.api.config.VodConfig;
public class PolicyHarness {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static class Data implements VodDataSource {
        int searches, pages; boolean incremental; long generation; List<Site> sites; String keyword;
        public void detailContent(String k,String id){} public void playerContent(VodPlayRequest r){}
        public void preloadContent(VodPlayRequest r){} public void cancelSourceSearch(){}
        public void searchSources(long g,List<Site> s,String k){searches++;generation=g;sites=s;keyword=k;}
        public void searchSources(long g,List<Site> s,String k,boolean lazy){incremental=lazy;searchSources(g,s,k);}
        public void loadMoreSources(long g){if(g==generation)pages++;}
        VodSourceResult result(List<Vod> items,int complete,int failed){return new VodSourceResult(generation,keyword,items,sites.size(),complete,failed);}
    }
    static class Fixture implements InvocationHandler {
        String key="a",id="1",name="Film"; boolean discover=true,changeable=true;
        VodSourceResult shown=VodSourceResult.idle();
        final Data data=new Data(); final VodPlaybackState state=new VodPlaybackState();
        final VodPlaybackController controller=new VodPlaybackController();
        final VodPlaybackHost host=(VodPlaybackHost)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{VodPlaybackHost.class},this);
        final VodFallbackPolicy policy=new VodFallbackPolicy(controller,state,host,data);
        Fixture(){controller.host=host;}
        public Object invoke(Object p,Method m,Object[] a){return switch(m.getName()){
            case "getVodKey" -> key; case "getVodId" -> id; case "getVodName" -> name;
            case "discoverSourcesOnDetail" -> discover; case "isSiteChangeable" -> changeable;
            case "isHostFinishing" -> false;
            case "renderSourceSearch" -> {shown=(VodSourceResult)a[0];yield null;}
            case "prepareSource" -> {Vod v=(Vod)a[0];key=v.key();id=v.id();yield null;}
            default -> null;
        };}
    }
    public static void main(String[] args) {
        var config=VodConfig.get();config.sites=List.of(new Site("a",true,true,true),new Site("b",true,true,true),
            new Site("c",true,true,false),new Site("off",false,true,true),new Site("slow",true,false,true));
        var f=new Fixture();f.policy.ensureSourcesLoaded();f.policy.ensureSourcesLoaded();
        check(f.data.searches==1 && f.data.sites.size()==3,"one eligible discovery per film");
        check(!f.state.isAutoFallback() && !f.state.isSelectFirstSource(),"discovery never arms switching");
        var sameId=new Vod("b","1","Film");var differentSeason=new Vod("b","2","Film 2");
        var disabled=new Vod("c","3","Film");
        f.policy.onSearchResult(f.data.result(List.of(new Vod("a","1","Film"),sameId,differentSeason,disabled),1,0));
        check(f.shown.items().size()==3 && f.shown.items().contains(sameId),"exclude current pair, not cross-site ID");
        check(f.controller.switches==0,"arrival does not change playback");
        f.policy.manualSwitchSource();
        check(f.controller.switches==1 && f.key.equals("b") && f.id.equals("1"),"manual switch uses discovered exact match");
        check(f.state.hasFailedId(new Vod("a","1","Film")) && !f.state.hasFailedId(sameId),"failures scoped by site+id");
        f.policy.onSearchResult(f.data.result(List.of(sameId,differentSeason,disabled),3,0));
        check(f.controller.switches==1,"later batches never cause a second unsolicited switch");
        f.policy.manualSwitchSource();
        check(f.controller.switches==1 && f.data.searches==2,"other season/non-changeable candidate cannot auto-play");

        var waiting=new Fixture();waiting.policy.ensureSourcesLoaded();waiting.policy.manualSwitchSource();
        check(waiting.data.searches==1 && waiting.controller.switches==0,"manual switch reuses in-flight search");
        waiting.policy.onSearchResult(waiting.data.result(List.of(sameId),1,0));
        check(waiting.controller.switches==1,"pending switch fires when exact candidate arrives");
        waiting.policy.retrySources();check(waiting.data.searches==1,"retry cannot duplicate active requests");

        var stale=new Fixture();stale.policy.ensureSourcesLoaded();var old=stale.data.result(List.of(sameId),3,0);
        stale.state.reset();stale.name="Other";stale.policy.ensureSourcesLoaded();
        stale.policy.onSearchResult(old);
        check(stale.state.getSources().isEmpty() && stale.controller.switches==0,"new film rejects old generation");
        stale.policy.onSearchResult(new VodSourceResult(stale.data.generation,"wrong",List.of(sameId),3,3,0));
        check(stale.state.getSources().isEmpty(),"wrong keyword rejected");
        stale.policy.onSearchResult(stale.data.result(List.of(),3,2));
        check(stale.shown.status()==VodSourceResult.Status.ERROR,"failed search is retryable state");
        stale.policy.retrySources();check(stale.data.searches==3 && !stale.state.isSelectFirstSource(),"retry is discovery only");

        var line=new Fixture();line.state.setFlags(List.of(new Flag(List.of(new Episode()),true),new Flag(List.of(),false),new Flag(List.of(new Episode()),false)));
        line.changeable=false;line.policy.playbackError();
        check(line.controller.lines==1 && line.data.searches==0,"try remaining line before cross-source fallback");
        line.state.setFlags(List.of());line.policy.playbackError();
        check(line.data.searches==0,"respect current site's no-auto-change policy");
        var paging=new Fixture();paging.policy.ensureSourcesLoaded();
        check(paging.data.incremental,"TV uses incremental site batches");
        paging.policy.onSearchResult(new VodSourceResult(paging.data.generation,"Film",List.of(),8,3,0,3));
        check(paging.data.pages==0,"discovery pauses at empty batch, never scans all sites silently");
        paging.policy.loadMoreSources();check(paging.data.pages==1,"explicit next batch");
        paging.policy.manualSwitchSource();check(paging.data.pages==2&&paging.data.searches==1,"manual switch continues pending sites rather than restarting");
        paging.policy.onSearchResult(new VodSourceResult(paging.data.generation,"Film",List.of(),8,6,1,6));
        check(paging.data.pages==3,"pending fallback advances past empty/failed batch");
        paging.policy.onSearchResult(new VodSourceResult(paging.data.generation,"Film",List.of(sameId),8,7,1,8));
        check(paging.controller.switches==1,"later batch exact match fulfills pending switch");
        paging.policy.loadMoreSources();check(paging.data.pages==3,"no duplicate while batch loading");
        var mobile=new Fixture();mobile.discover=false;mobile.policy.ensureSourcesLoaded();
        check(mobile.data.searches==0,"no proactive search for opt-out host");
        mobile.policy.manualSwitchSource();check(!mobile.data.incremental,"mobile explicit search retains exhaustive behavior");
        config.sites=List.of();var none=new Fixture();none.policy.ensureSourcesLoaded();
        check(none.shown.status()==VodSourceResult.Status.DISABLED,"no eligible sites is explicit");
        check(new VodSourceResult(1,"x",List.of(),1,1,0).status()==VodSourceResult.Status.EMPTY,"success empty != failure");
        check(new VodSourceResult(1,"x",List.of(sameId),3,3,1).status()==VodSourceResult.Status.READY,"partial success remains ready");
        System.out.println("PASS policy: discovery, filtering, fallback, retry, generation and opt-out");
    }
}
"""

RUNNER_HARNESS = r"""
package com.fongmi.android.tv.model;
import java.util.*; import java.util.concurrent.*; import java.util.function.*;
import com.fongmi.android.tv.*; import com.fongmi.android.tv.api.*; import com.fongmi.android.tv.bean.*;
import com.fongmi.android.tv.playback.vod.*; import com.fongmi.android.tv.utils.Task;
public class RunnerHarness {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void drainUntil(BooleanSupplier done) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(!done.getAsBoolean()) {
            if(System.nanoTime()>end)throw new AssertionError("main queue deadline");
            Runnable r=App.queue.poll(50,TimeUnit.MILLISECONDS);if(r!=null)r.run();
        }
    }
    static Site site(String id){return new Site(id,true,true,true);}
    public static void main(String[] args) throws Exception {
        var runner=new VodSourceSearchRunner();var snapshots=new ArrayList<VodSourceResult>();
        try {
            var releases=new HashMap<String,CountDownLatch>();
            for(String id:List.of("a","b","c","d")) {
                var release=new CountDownLatch(1);releases.put(id,release);
                SiteApi.jobs.put(id,()->{release.await();return new Result(List.of(new Vod(id,"1","Film"),new Vod(id,"1","Film")),"");});
            }
            runner.start(1,List.of(site("a"),site("b"),site("c"),site("d")),"Film",snapshots::add);
            var started=new HashSet<String>();for(int i=0;i<3;i++)started.add(SiteApi.started.poll(1,TimeUnit.SECONDS));
            check(started.equals(Set.of("a","b","c")) && SiteApi.started.isEmpty(),"bounded to three requests");
            releases.get("b").countDown();drainUntil(()->snapshots.get(snapshots.size()-1).completed()==1);
            check("d".equals(SiteApi.started.poll(1,TimeUnit.SECONDS)),"next site starts as slot frees");
            releases.values().forEach(CountDownLatch::countDown);
            drainUntil(()->!snapshots.get(snapshots.size()-1).isLoading());
            var last=snapshots.get(snapshots.size()-1);
            check(last.items().size()==4 && last.failed()==0,"accumulate unordered batches and dedup within site");
            check(snapshots.get(1).items().size()==1,"old snapshots immutable");
            try{last.items().clear();throw new AssertionError("mutable snapshot");}catch(UnsupportedOperationException expected){}

            // Finish IO but leave its delivery queued, then change films.
            snapshots.clear();SiteApi.jobs.put("old",()->new Result(List.of(new Vod("old","1","Old")),""));
            runner.start(2,List.of(site("old")),"Old",snapshots::add);
            Runnable stale=App.queue.poll(2,TimeUnit.SECONDS);check(stale!=null,"queued old response");
            runner.start(3,List.of(),"New",snapshots::add);int count=snapshots.size();stale.run();
            check(snapshots.size()==count && snapshots.get(count-1).status()==VodSourceResult.Status.DISABLED,"main-thread epoch rejects queued stale delivery");

            snapshots.clear();SiteApi.jobs.put("ok",()->new Result(List.of(new Vod("ok","1","Film")),""));
            SiteApi.jobs.put("bad",()->{throw new Exception("partial failure");});
            runner.start(4,List.of(site("ok"),site("bad")),"Film",snapshots::add);
            drainUntil(()->!snapshots.get(snapshots.size()-1).isLoading());last=snapshots.get(snapshots.size()-1);
            check(last.items().size()==1 && last.failed()==1 && last.status()==VodSourceResult.Status.READY,"partial failure cannot erase successful candidates");

            snapshots.clear();SiteApi.jobs.put("empty",Result::empty);
            SiteApi.jobs.put("fail",()->{throw new Exception("offline");});
            SiteApi.jobs.put("timeout",()->{new CountDownLatch(1).await();return Result.empty();});
            runner.start(4,List.of(site("empty"),site("fail"),site("timeout")),"Film",snapshots::add);
            drainUntil(()->!snapshots.get(snapshots.size()-1).isLoading());last=snapshots.get(snapshots.size()-1);
            check(last.completed()==3 && last.failed()==2 && last.status()==VodSourceResult.Status.ERROR,"failure and real timeout settle loading");
            snapshots.clear();runner.start(5,List.of(site("empty")),"Film",snapshots::add);
            drainUntil(()->!snapshots.get(snapshots.size()-1).isLoading());
            check(snapshots.get(snapshots.size()-1).status()==VodSourceResult.Status.EMPTY,"empty success distinct from timeout");
            snapshots.clear();SiteApi.started.clear();
            var sites=new ArrayList<Site>();
            for(int i=0;i<8;i++) {String id="page"+i;sites.add(site(id));SiteApi.jobs.put(id,()->new Result(List.of(new Vod(id,"1","Film")),""));}
            runner.start(10,sites,"Film",true,snapshots::add);
            runner.loadMore(10); // active batch: ignored
            drainUntil(()->!snapshots.get(snapshots.size()-1).isLoading());last=snapshots.get(snapshots.size()-1);
            check(last.completed()==3 && last.requested()==3 && last.hasMore(),"first lazy batch stops after three sites");
            check(SiteApi.started.size()==3,"remaining sites not contacted eagerly");
            int before=snapshots.size();runner.loadMore(9);check(snapshots.size()==before,"stale page demand ignored");
            runner.loadMore(10);runner.loadMore(10);
            drainUntil(()->!snapshots.get(snapshots.size()-1).isLoading());last=snapshots.get(snapshots.size()-1);
            check(last.completed()==6 && last.items().size()==6 && SiteApi.started.size()==6,"next batch appends and duplicate demand is ignored");
            check(last.withItems(List.of()).hasMore(),"filtering does not lose pagination state");
            runner.loadMore(10);drainUntil(()->!snapshots.get(snapshots.size()-1).isLoading());last=snapshots.get(snapshots.size()-1);
            check(last.completed()==8 && !last.hasMore() && last.items().size()==8,"short final batch completes all sites");
            before=snapshots.size();runner.loadMore(10);check(snapshots.size()==before,"exhausted queue is idempotent");
            runner.stop();runner.loadMore(10);check(snapshots.size()==before,"cancelled session cannot resume");
            snapshots.clear();SiteApi.started.clear();
            runner.start(11,List.of(site("empty"),site("fail"),site("timeout"),site("ok")),"Film",true,snapshots::add);
            drainUntil(()->!snapshots.get(snapshots.size()-1).isLoading());last=snapshots.get(snapshots.size()-1);
            check(last.hasMore() && last.completed()==3 && last.failed()==2 && SiteApi.started.size()==3,"failed/empty first batch settles without eager next page");
            runner.loadMore(11);drainUntil(()->!snapshots.get(snapshots.size()-1).isLoading());
            check(snapshots.get(snapshots.size()-1).items().size()==1,"explicit demand recovers after failed batch");
            System.out.println("PASS runner: lazy batches, demand dedup, cancellation, concurrency, accumulation, immutable snapshots, stale callback and timeout");
        } finally {runner.stop();Task.largeExecutor().shutdownNow();Task.scheduler().shutdownNow();}
    }
}
"""


class SourceDiscoveryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/com.google.guava"
        jars = []
        for name in ("guava", "failureaccess"):
            matches = sorted(p for p in (cache / name).rglob("*.jar") if not p.name.endswith(("-sources.jar", "-javadoc.jar")))
            if not matches:
                raise AssertionError(f"Run the Android Gradle build first: missing cached {name}")
            jars.append(str(matches[-1]))
        cls.temp = tempfile.TemporaryDirectory(prefix="vod-source-tests-")
        cls.addClassCleanup(cls.temp.cleanup)
        root = Path(cls.temp.name)
        sources = dict(STUBS)
        for name in ("VodFallbackPolicy", "VodPlaybackState", "VodPlaybackHost", "VodDataSource", "VodSourceResult"):
            path = PACKAGE + "/playback/vod/" + name + ".java"
            sources[path] = (MAIN / path).read_text()
        for path in (PACKAGE + "/model/VodSourceSearchRunner.java", PACKAGE + "/utils/Task.java"):
            sources[path] = (MAIN / path).read_text()
        sources[PACKAGE + "/playback/vod/PolicyHarness.java"] = POLICY_HARNESS
        sources[PACKAGE + "/model/RunnerHarness.java"] = RUNNER_HARNESS
        for name, content in sources.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
        cls.classpath = os.pathsep.join([str(root / "classes")] + jars)
        result = subprocess.run(["javac", "-encoding", "UTF-8", "-cp", os.pathsep.join(jars), "-d", str(root / "classes")]
                                + [str(root / name) for name in sources], capture_output=True, text=True, timeout=60)
        if result.returncode:
            raise AssertionError(result.stdout + result.stderr)

    def run_harness(self, name):
        result = subprocess.run(["java", "-cp", self.classpath, "com.fongmi.android.tv." + name],
                                capture_output=True, text=True, timeout=25)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_discovery_and_fallback_policy(self):
        self.run_harness("playback.vod.PolicyHarness")

    def test_parallel_runner_and_stale_delivery(self):
        self.run_harness("model.RunnerHarness")

    def test_controller_and_ui_wiring(self):
        controller = (MAIN / PACKAGE / "playback/vod/VodPlaybackController.java").read_text()
        detail = controller[controller.index("private void detailLoaded("):controller.index("private VodHistoryPolicy.Resolution applyDetail(")]
        self.assertLess(detail.index("restoreDetail("), detail.index("ensureSourcesLoaded();"))
        reset = controller[controller.index("public void reset()"):controller.index("public void checkId()")]
        self.assertIn("cancelSourceSearch();", reset)
        for flavor in ("leanback", "mobile"):
            source = (ROOT / f"app/src/{flavor}/java" / PACKAGE / "ui/activity/VideoActivity.java").read_text()
            self.assertIn("mViewModel.getSources(), this::onSearchObserved", source)
            self.assertNotIn("mQuickAdapter.addAll(items)", source)
        tv = (ROOT / "app/src/leanback/java" / PACKAGE / "ui/activity/VideoActivity.java").read_text()
        self.assertIn("R.id.quickStatus", tv)
        self.assertIn("new QuickStatusAdapter(this::onSourceStatusClick)", tv)


if __name__ == "__main__":
    unittest.main()
