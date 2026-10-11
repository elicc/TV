"""Runs built MetadataRepository bytecode with deterministic platform/network boundaries.

Prerequisite: :app:compileLeanbackDebugJavaWithJavac. No live network or Android UI.
The production cache/dispatch methods are not copied into this test.
"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com/fongmi/android/tv/metadata"
STUBS = {
    "android/text/TextUtils.java": 'package android.text; public class TextUtils { public static boolean isEmpty(CharSequence s){return s==null||s.length()==0;} }',
    "android/util/Log.java": 'package android.util; public class Log { public static int w(String t,String m,Throwable e){return 0;} }',
    "com/fongmi/android/tv/App.java": '''package com.fongmi.android.tv;
        public class App {
            public static java.util.ArrayDeque<Runnable> queue=new java.util.ArrayDeque<>();
            public static com.google.gson.Gson gson(){return new com.google.gson.Gson();}
            public static void post(Runnable r){queue.add(r);}
            public static void drain(){while(!queue.isEmpty())queue.remove().run();}
        }''',
    "com/fongmi/android/tv/utils/Task.java": '''package com.fongmi.android.tv.utils;
        public class Task {
            public static java.util.ArrayDeque<Runnable> queue=new java.util.ArrayDeque<>();
            public static void execute(Runnable r){queue.add(r);}
            public static void drain(){while(!queue.isEmpty())queue.remove().run();}
        }''',
    "com/github/catvod/utils/Prefers.java": '''package com.github.catvod.utils;
        public class Prefers {
            public static java.util.Map<String,String> values=new java.util.HashMap<>();
            public static String getString(String k,String d){return values.getOrDefault(k,d);}
            public static void put(String k,Object v){values.put(k,String.valueOf(v));}
            public static void remove(String k){values.remove(k);}
        }''',
    PACKAGE + "/DoubanProvider.java": "package com.fongmi.android.tv.metadata; public class DoubanProvider {}",
    PACKAGE + "/MetadataAgentClient.java": "package com.fongmi.android.tv.metadata; public class MetadataAgentClient {}",
}

HARNESS = r'''
import com.fongmi.android.tv.metadata.*;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.utils.Prefers;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

public class CacheHarness {
    static int calls, successes, failures;
    static List<MovieMetadata> delivered;
    static Class<?> loaderType;
    static Method load;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void request(MetadataRepository repo,String key,List<MovieMetadata> items,boolean fail) throws Exception {
        Object loader=Proxy.newProxyInstance(loaderType.getClassLoader(),new Class[]{loaderType},(p,m,a)->{
            calls++; if(fail)throw new java.io.IOException("offline"); return items;
        });
        Consumer<List<MovieMetadata>> success=list->{successes++;delivered=list;};
        Consumer<Throwable> failure=error->{failures++;check(error!=null,"failure reason");};
        load.invoke(repo,key,loader,success,failure);
    }
    static Map<?,?> snapshots(MetadataRepository repo) throws Exception {
        Field f=MetadataRepository.class.getDeclaredField("lists");f.setAccessible(true);return (Map<?,?>)f.get(repo);
    }
    public static void main(String[] args) throws Exception {
        loaderType=Class.forName("com.fongmi.android.tv.metadata.MetadataRepository$ListLoader");
        load=MetadataRepository.class.getDeclaredMethod("loadList",String.class,loaderType,Consumer.class,Consumer.class);
        load.setAccessible(true);
        MetadataRepository repo=new MetadataRepository();
        MovieMetadata first=new MovieMetadata();first.setTitle("first");first.setRating(6);
        MovieMetadata second=new MovieMetadata();second.setTitle("second");second.setRating(9);
        var items=List.of(first,second);
        Prefers.values.put("metadata_douban_hot_movies","old sorted cache must not be read");
        request(repo,"collection:movie_hot_gaia",items,false);
        request(repo,"collection:movie_hot_gaia",items,false);
        check(Task.queue.size()==1 && successes==0,"inflight dedup, no inline callback");
        Task.drain();check(calls==1 && successes==0,"callbacks wait for main dispatch");App.drain();
        check(successes==2 && delivered.get(0).getTitle().equals("first"),"server order to all waiters");
        request(repo,"collection:movie_hot_gaia",items,true);check(Task.queue.isEmpty(),"fresh cache avoids network");App.drain();
        check(successes==3,"fresh callback");
        Object snap=snapshots(repo).get("collection:movie_hot_gaia");
        Field updated=snap.getClass().getDeclaredField("updatedAt");updated.setAccessible(true);updated.setLong(snap,1);
        request(repo,"collection:movie_hot_gaia",items,true);Task.drain();App.drain();
        check(successes==4 && failures==0,"same-key stale fallback");
        request(repo,"related:tv:99",items,true);Task.drain();App.drain();
        check(failures==1,"no cross-key fallback");
        request(repo,"collection:tv_hot",List.of(),false);Task.drain();App.drain();
        check(delivered.isEmpty(),"empty success is not failure");
        request(repo,"collection:tv_hot",items,true);check(Task.queue.isEmpty(),"valid empty cached");App.drain();
        for(int i=0;i<36;i++){request(repo,"related:movie:"+i,items,false);Task.drain();App.drain();}
        check(snapshots(repo).size()==32,"bounded cache");
        MetadataRepository reopened=new MetadataRepository();
        request(reopened,"related:movie:35",List.of(),true);check(Task.queue.isEmpty(),"disk cache survives repository recreation");App.drain();
        check(delivered.get(0).getTitle().equals("first"),"disk order retained");
        Prefers.values.put("metadata_douban_lists_v2","broken json");
        request(new MetadataRepository(),"related:movie:35",items,false);Task.drain();App.drain();
        check(delivered.size()==2,"corrupt disk cache recovers via network");
        System.out.println("PASS cache order, dispatch, dedup, isolation, empty, bound, persistence, recovery");
    }
}
'''


class DoubanCacheTest(unittest.TestCase):
    def test_production_cache_bytecode(self):
        classes = ROOT / "app/build/intermediates/javac/leanbackDebug/compileLeanbackDebugJavaWithJavac/classes"
        self.assertTrue((classes / PACKAGE / "MetadataRepository.class").exists(), "Run the leanback Java compile first")
        gson = next((Path.home() / ".gradle/caches/modules-2/files-2.1/com.google.code.gson/gson").glob("*/*/*.jar"))
        sdk = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Library/Android/sdk")))
        android = sorted((sdk / "platforms").glob("*/android.jar"))[-1]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sources = dict(STUBS)
            sources["CacheHarness.java"] = HARNESS
            for name, text in sources.items():
                target = root / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(text)
            cp = os.pathsep.join(map(str, [root / "classes", classes, gson, android]))
            subprocess.run(["javac", "-cp", cp, "-d", str(root / "classes")] + [str(root / name) for name in sources], check=True)
            subprocess.run(["java", "-cp", cp, "CacheHarness"], check=True)
