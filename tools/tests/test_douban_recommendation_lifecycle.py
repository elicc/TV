"""Exercise built VideoViewModel with queued metadata/recommendation responses.

Prerequisite: leanback Java compile. Lifecycle/transport boundaries are stubs;
the production ViewModel, state, matching gate and generation guards are real.
"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

from tools.tests.test_douban_list_cache import ROOT, PACKAGE, STUBS

BOUNDARIES = {
    "androidx/lifecycle/LiveData.java": "package androidx.lifecycle; public class LiveData<T> {protected T value; public T getValue(){return value;}}",
    "androidx/lifecycle/MutableLiveData.java": "package androidx.lifecycle; public class MutableLiveData<T> extends LiveData<T> {public MutableLiveData(){} public MutableLiveData(T v){value=v;} public void setValue(T v){value=v;}}",
    "com/fongmi/android/tv/model/SiteViewModel.java": "package com.fongmi.android.tv.model; public class SiteViewModel {protected void onCleared(){}}",
    "com/fongmi/android/tv/model/ViewModelTaskRunner.java": "package com.fongmi.android.tv.model; public class ViewModelTaskRunner<T> {public ViewModelTaskRunner(Class<T> t){} public void cancelAll(){}}",
    "com/fongmi/android/tv/playback/vod/VodPlaybackState.java": "package com.fongmi.android.tv.playback.vod; public class VodPlaybackState {public void reset(){}}",
    "com/fongmi/android/tv/event/RefreshEvent.java": "package com.fongmi.android.tv.event; public class RefreshEvent {public static void metadata(){}}",
    "com/fongmi/android/tv/bean/Vod.java": "package com.fongmi.android.tv.bean; public class Vod {" + "".join(
        'public String ' + name + '(){return "fixture";}' for name in ["getName", "getYear", "getArea", "getTypeName", "getDirector", "getActor", "getContent", "getPic"]) + "}",
    PACKAGE + "/MetadataRepository.java": '''package com.fongmi.android.tv.metadata;
        import java.util.*; import java.util.function.*; import com.fongmi.android.tv.bean.Vod;
        public class MetadataRepository {
            static final MetadataRepository INSTANCE=new MetadataRepository();
            public static MetadataRepository get(){return INSTANCE;}
            public final List<Consumer<MetadataState>> loads=new ArrayList<>();
            public final List<Consumer<List<MovieMetadata>>> successes=new ArrayList<>();
            public final List<Consumer<Throwable>> failures=new ArrayList<>();
            public void load(MovieIdentity i,Vod v,Consumer<MetadataState> c){loads.add(c);}
            public void confirm(MovieIdentity i,Vod v,MetadataCandidate m,Consumer<MetadataState> c){loads.add(c);}
            public void loadCast(MovieIdentity i,MovieMetadata m,Consumer<List<MovieCast>> c){c.accept(List.of());}
            public void loadRecommendations(MovieMetadata m,Consumer<List<MovieMetadata>> c,Consumer<Throwable> e){successes.add(c);failures.add(e);}
        }''',
}

HARNESS = r'''
import com.fongmi.android.tv.model.VideoViewModel;
import com.fongmi.android.tv.metadata.*;
import com.fongmi.android.tv.bean.Vod;
import java.util.*;
public class LifecycleHarness {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static MovieMetadata movie(String id){var m=new MovieMetadata();m.setProvider(MetadataProvider.DOUBAN);m.setExternalType("movie");m.setExternalId(id);m.setTitle(id);return m;}
    static MetadataState state(MovieMetadata m,boolean trusted){
        var providers=new EnumMap<MetadataProvider,MovieMetadata>(MetadataProvider.class);
        if(trusted)providers.put(MetadataProvider.DOUBAN,m);
        return new MetadataState(new MovieIdentity("site","id","title","","","","",""),new MovieMetadata(),providers,
            trusted?List.of():List.of(new MetadataCandidate(m,0.7)),MetadataProvider.DOUBAN,
            trusted?MetadataState.Status.SUCCESS:MetadataState.Status.FALLBACK,"");
    }
    static RecommendationState.Status status(VideoViewModel vm){return vm.getRecommendations().getValue().getStatus();}
    public static void main(String[] args) throws Exception {
        var repo=MetadataRepository.get();var vm=new VideoViewModel();
        var a=movie("1");var b=movie("2");var c=movie("3");
        vm.loadMetadata("s","a",new Vod());repo.loads.get(0).accept(state(a,true));
        check(repo.successes.size()==1 && status(vm)==RecommendationState.Status.LOADING,"resolved metadata starts optional load");
        repo.loads.get(0).accept(state(a,true));check(repo.successes.size()==1,"same key dedup in ViewModel");
        vm.selectMetadataProvider(MetadataProvider.SOURCE);
        repo.successes.get(0).accept(List.of(b));
        check(status(vm)==RecommendationState.Status.READY,"source tab does not invalidate related titles");
        vm.loadMetadata("s","b",new Vod());
        check(status(vm)==RecommendationState.Status.IDLE,"new film clears old rail");
        repo.loads.get(0).accept(state(a,true));check(repo.successes.size()==1,"old metadata response ignored");
        repo.loads.get(1).accept(state(b,true));
        repo.successes.get(0).accept(List.of(a));
        check(status(vm)==RecommendationState.Status.LOADING,"old recommendation response ignored");
        repo.successes.get(1).accept(List.of(c));
        check(vm.getRecommendations().getValue().getItems().get(0)==c,"new film result delivered");
        vm.loadMetadata("s","c",new Vod());repo.loads.get(2).accept(state(c,false));
        check(repo.successes.size()==2 && status(vm)==RecommendationState.Status.IDLE,"candidate excluded");
        vm.confirmMetadata(new MetadataCandidate(c,0.7));repo.loads.get(3).accept(state(c,true));
        check(repo.successes.size()==3,"confirmation starts recommendation load");
        repo.failures.get(1).accept(new Exception("late"));
        check(status(vm)==RecommendationState.Status.LOADING,"late failure ignored");
        repo.successes.get(2).accept(List.of());
        check(status(vm)==RecommendationState.Status.EMPTY,"valid empty state");
        vm.loadMetadata("s","d",new Vod());repo.loads.get(4).accept(state(a,true));
        repo.failures.get(3).accept(new Exception("offline"));
        check(status(vm)==RecommendationState.Status.ERROR && vm.getMetadata().getValue().getStatus()==MetadataState.Status.SUCCESS,"optional failure never fails metadata");
        vm.retryRecommendations();
        check(repo.successes.size()==5 && status(vm)==RecommendationState.Status.LOADING,"failed request can retry");
        vm.retryRecommendations();check(repo.successes.size()==5,"retry cannot duplicate in-flight request");
        repo.successes.get(4).accept(List.of());
        check(status(vm)==RecommendationState.Status.EMPTY,"retry can yield valid empty");
        vm.retryRecommendations();check(repo.successes.size()==5,"empty is not a failure to retry");
        vm.clearMetadata();repo.successes.get(3).accept(List.of(a));
        check(status(vm)==RecommendationState.Status.IDLE,"clear invalidates in-flight result");
        vm.loadMetadata("s","e",new Vod());repo.loads.get(5).accept(state(b,true));
        var clear=VideoViewModel.class.getDeclaredMethod("onCleared");clear.setAccessible(true);clear.invoke(vm);
        repo.successes.get(5).accept(List.of(a));
        check(status(vm)!=RecommendationState.Status.READY,"destroy invalidates in-flight result");
        System.out.println("PASS recommendation lifecycle: trust, tab independence, dedup, stale responses, clear, destroy, error");
    }
}
'''


class RecommendationLifecycleTest(unittest.TestCase):
    def test_production_viewmodel_bytecode(self):
        classes = ROOT / "app/build/intermediates/javac/leanbackDebug/compileLeanbackDebugJavaWithJavac/classes"
        self.assertTrue((classes / "com/fongmi/android/tv/model/VideoViewModel.class").exists(), "Compile leanback first")
        gson = next((Path.home() / ".gradle/caches/modules-2/files-2.1/com.google.code.gson/gson").glob("*/*/*.jar"))
        sdk = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Library/Android/sdk")))
        android = sorted((sdk / "platforms").glob("*/android.jar"))[-1]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sources = {**STUBS, **BOUNDARIES, "LifecycleHarness.java": HARNESS}
            for name, text in sources.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text)
            cp = os.pathsep.join(map(str, [root / "classes", classes, gson, android]))
            subprocess.run(["javac", "-cp", cp, "-d", str(root / "classes")] + [str(root / name) for name in sources], check=True)
            subprocess.run(["java", "-cp", cp, "LifecycleHarness"], check=True)
