"""Run the production diagnostic against a local HTTP server; no device or service needed."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
P = "com/fongmi/android/tv/"
STUBS = {
    "android/os/Build.java": "package android.os; public class Build { public static class VERSION { public static int SDK_INT=0; } }",
    "android/util/Log.java": "package android.util; public class Log { public static boolean isLoggable(String tag,int level){return false;} public static int println(int level,String tag,String text){return 0;} public static String getStackTraceString(Throwable t){return t.toString();} }",
    "android/net/Uri.java": "package android.net; public class Uri { public static String encode(String s){return java.net.URLEncoder.encode(s,java.nio.charset.StandardCharsets.UTF_8).replace(\"+\",\"%20\");} }",
    "android/util/Xml.java": "package android.util; public class Xml { public static org.xmlpull.v1.XmlPullParser newPullParser(){return new org.kxml2.io.KXmlParser();} }",
    "androidx/annotation/NonNull.java": "package androidx.annotation; public @interface NonNull {}",
    P+"App.java": """package com.fongmi.android.tv; public class App {
        public static java.util.concurrent.ConcurrentLinkedQueue<Runnable> queue=new java.util.concurrent.ConcurrentLinkedQueue<>();
        public static void post(Runnable r){queue.add(r);}
        public static void drain(){Runnable r;while((r=queue.poll())!=null)r.run();}
    }""",
    "com/github/catvod/utils/Trans.java": "package com.github.catvod.utils; public class Trans { public static String t2s(String s){return s;} }",
    "com/github/catvod/net/OkHttp.java": """package com.github.catvod.net; public class OkHttp {
        private static final okhttp3.OkHttpClient CLIENT=new okhttp3.OkHttpClient();
        public static okhttp3.OkHttpClient client(){return CLIENT;}
    }""",
}
HARNESS = r"""
import com.fongmi.android.tv.api.DanmakuProbe;
import com.fongmi.android.tv.api.DanmakuProbe.Code;
import com.fongmi.android.tv.api.DanmakuProbe.Result;
import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ProbeHarness {
    static int checks;
    static String base;
    static final AtomicReference<Result> result=new AtomicReference<>();
    static final AtomicReference<String> method=new AtomicReference<>(), query=new AtomicReference<>(), form=new AtomicReference<>();
    static final AtomicInteger xmlRequests=new AtomicInteger();
    static final CountDownLatch slowStarted=new CountDownLatch(1), releaseSlow=new CountDownLatch(1);
    static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    static Result await()throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(22);
        while(result.get()==null && System.nanoTime()<end){App.drain();Thread.sleep(5);}
        check(result.get()!=null,"timely completion");return result.get();
    }
    static Result run(DanmakuProbe probe,String url,Code code)throws Exception {
        result.set(null);probe.start(url,"片名 & +","第1集",result::set);
        check(result.get()==null,"callback queued for main thread");
        Result actual=await();check(actual.code==code,"expected "+code+" got "+actual.code);return actual;
    }
    public static void main(String[] args)throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        ExecutorService executor=Executors.newCachedThreadPool();server.setExecutor(executor);
        base="http://127.0.0.1:"+server.getAddress().getPort();
        server.createContext("/",exchange->{
            String path=exchange.getRequestURI().getPath(),body="";int status=200;
            try {
                if(path.startsWith("/xml"))xmlRequests.incrementAndGet();
                if(path.equals("/search")){
                    method.set(exchange.getRequestMethod());query.set(exchange.getRequestURI().getRawQuery());
                    form.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                    body="[{\"url\":\""+base+"/xml\"},{\"name\":null,\"url\":\""+base+"/unused\"}]";
                } else if(path.equals("/empty"))body="[]";
                else if(path.equals("/config"))body="{\"spider\":\"plugin.jar\",\"sites\":[]}";
                else if(path.equals("/http")){status=403;body="secret-token denied";}
                else if(path.equals("/invalid"))body="[{\"url\":\"file:///secret\"}]";
                else if(path.equals("/bad-name"))body="[{\"name\":3,\"url\":\""+base+"/xml\"}]";
                else if(path.equals("/bad-second"))body="[{\"url\":\""+base+"/xml\"},{\"url\":3}]";
                else if(path.equals("/not-json"))body="<html>login required</html>";
                else if(path.equals("/big-search"))body=" ".repeat(1024*1024+1);
                else if(path.equals("/slow")){slowStarted.countDown();releaseSlow.await(20,TimeUnit.SECONDS);body="[]";}
                else if(path.equals("/timeout")){Thread.sleep(18000);body="[]";}
                else if(path.startsWith("/to-"))body="[{\"url\":\""+base+"/xml-"+path.substring(4)+"\"}]";
                else if(path.equals("/xml"))body="<?xml version=\"1.0\"?><i><chatserver>test</chatserver><d p=\"0,1,25,16777215\">一 &amp; 二</d><d p=\"1,1,25,16777215\">three</d></i>";
                else if(path.equals("/xml-empty"))body="<i/>";
                else if(path.equals("/xml-http")){status=401;body="denied";}
                else if(path.equals("/xml-html"))body="<html><body><d>not danmaku</d></body></html>";
                else if(path.equals("/xml-bad"))body="<i><d>broken</i>";
                else if(path.equals("/xml-entity"))body="<!DOCTYPE i [<!ENTITY x SYSTEM file:///etc/passwd>]><i><d>&x;</d></i>";
                else if(path.equals("/xml-dtd"))body="<!DOCTYPE i SYSTEM http://127.0.0.1:1/private><i/>";
                else if(path.equals("/xml-big"))body="x".repeat(8*1024*1024+1);
                else if(path.equals("/xml-deep"))body="<i>"+"<a>".repeat(70)+"</a>".repeat(70)+"</i>";
                else if(path.equals("/xml-trailing"))body="<i/><i/>";
                else if(path.equals("/xml-missing-p"))body="<i><d>not a playable comment</d></i>";
                else if(path.equals("/xml-blank-text"))body="<i><d p=\"0,1,25,16777215\"/></i>";
                else if(path.equals("/xml-positioned"))body="<i><d p=\"0,7,25,16777215\">[0.5,0.5,1,3,\"positioned text\"]</d></i>";
                else if(path.equals("/xml-bad-positioned"))body="<i><d p=\"0,7,25,16777215\">not a JSON array</d></i>";
                else if(path.startsWith("/xml-metadata/"))body="<i><d p=\""+path.substring("/xml-metadata/".length())+"\">text</d></i>";
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                // Chunked XML proves the streaming limit works without Content-Length.
                exchange.sendResponseHeaders(status,path.equals("/xml-big")?0:bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch(InterruptedException e){Thread.currentThread().interrupt();}
            catch(java.io.IOException ignored){} finally {exchange.close();}
        });server.start();
        try {
            DanmakuProbe probe=new DanmakuProbe();
            Result good=run(probe,base+"/search?name={name}&episode={episode}",Code.SUCCESS);
            check(good.candidateCount==2 && good.commentCount==2,"candidate and comment counts");
            check(xmlRequests.get()==1,"only first candidate fetched");
            check(method.get().equals("GET"),"placeholder GET");
            check(URLDecoder.decode(query.get(),StandardCharsets.UTF_8).equals("name=片名 & +&episode=第1集"),"GET encodes exactly once");
            run(probe,base+"/search",Code.SUCCESS);check(method.get().equals("POST"),"legacy POST");
            check(URLDecoder.decode(form.get(),StandardCharsets.UTF_8).equals("name=片名 & +&episode=第1集"),"POST form values");
            run(probe,base+"/empty",Code.NO_RESULTS);
            run(probe,base+"/config",Code.SITE_CONFIG);
            check(run(probe,base+"/http",Code.SEARCH_HTTP).httpStatus==403,"search HTTP code");
            for(String path:new String[]{"/invalid","/bad-name","/bad-second","/not-json"})run(probe,base+path,Code.SEARCH_FORMAT);
            run(probe,base+"/big-search",Code.LIMIT_EXCEEDED);
            Result failure=run(probe,base+"/to-http",Code.XML_HTTP);
            check(failure.httpStatus==401 && failure.candidateCount==1,"XML stage preserves count and status");
            for(String path:new String[]{"html","bad","entity","dtd","trailing"})run(probe,base+"/to-"+path,Code.XML_FORMAT);
            for(String path:new String[]{"missing-p","blank-text","bad-positioned"})run(probe,base+"/to-"+path,Code.XML_FORMAT);
            for(String metadata:new String[]{"0,1,25", "bad,1,25,1", "NaN,1,25,1", "Infinity,1,25,1", "-1,1,25,1", "1e30,1,25,1", "0,bad,25,1", "0,8,25,1", "0,0,25,1", "0,1,bad,1", "0,1,0,1", "0,1,-1,1", "0,1,25,bad", "0,1,25,-1", "0,1,25,16777216"})
                run(probe,base+"/to-metadata/"+metadata,Code.XML_FORMAT);
            check(run(probe,base+"/to-positioned",Code.SUCCESS).commentCount==1,"actual player accepts positioned JSON");
            for(int mode=1;mode<=6;mode++)check(run(probe,base+"/to-metadata/1.25,"+mode+",25,16777215",Code.SUCCESS).commentCount==1,"supported player mode "+mode);
            check(run(probe,base+"/to-metadata/0,1,18,0,123456,0,user,99",Code.SUCCESS).commentCount==1,"full p metadata and black color accepted");
            check(run(probe,base+"/to-empty",Code.SUCCESS).commentCount==0,"valid empty XML distinguished");
            run(probe,base+"/to-big",Code.LIMIT_EXCEEDED);
            run(probe,base+"/to-deep",Code.LIMIT_EXCEEDED);
            for(String url:new String[]{null,"","relative/path","file:///secret","ftp://example.com"})run(probe,url,Code.INVALID_URL);
            result.set(null);probe.start("http://{name}/", "", "1",result::set);
            check(await().code==Code.INVALID_URL,"replacement cannot create invalid host");
            run(probe,"http://127.0.0.1:1/unavailable",Code.NETWORK_ERROR);
            run(probe,base+"/timeout",Code.NETWORK_ERROR);
            AtomicInteger stale=new AtomicInteger();
            probe.start(base+"/slow","x","1",r->stale.incrementAndGet());
            check(slowStarted.await(3,TimeUnit.SECONDS),"slow request started");
            run(probe,base+"/empty",Code.NO_RESULTS);releaseSlow.countDown();
            AtomicInteger other=new AtomicInteger();DanmakuProbe independent=new DanmakuProbe();
            independent.start(base+"/empty","x","1",r->other.incrementAndGet());
            probe.start(base+"/empty","x","1",r->stale.incrementAndGet());probe.cancel();
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(other.get()==0 && System.nanoTime()<end){App.drain();Thread.sleep(5);}
            check(other.get()==1,"independent instance unaffected by cancellation");
            Thread.sleep(100);App.drain();check(stale.get()==0,"replaced and cancelled callbacks suppressed");
            System.out.println("PASS "+checks+" checks: local HTTP GET/POST, limits, errors, XML security, cancellation and dispatch");
        } finally {releaseSlow.countDown();server.stop(0);executor.shutdownNow();OkHttp.client().dispatcher().executorService().shutdownNow();OkHttp.client().connectionPool().evictAll();}
    }
}
"""


class DanmakuProbeTests(unittest.TestCase):
    def test_production_probe(self):
        cache = Path.home()/".gradle/caches"
        modules = cache/"modules-2/files-2.1"
        patterns = ["com.google.code.gson/gson/*/*/gson-*.jar", "com.squareup.okio/okio-jvm/*/*/okio-jvm-*.jar", "org.jetbrains.kotlin/kotlin-stdlib/*/*/*.jar", "net.sf.kxml/kxml2/*/*/*.jar", "com.google.guava/guava/*/*/*.jar", "org.json/json/*/*/*.jar"]
        jars = [next(p for p in modules.glob(pattern) if "sources" not in p.name) for pattern in patterns]
        jars.append(next(cache.glob("*/transforms/*/transformed/okhttp-runtime.jar")))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            player_jar = root/"player.jar"
            with zipfile.ZipFile(ROOT/"app/libs/lib-ui-danmaku-release.aar") as aar:
                player_jar.write_bytes(aar.read("classes.jar"))
            sources = dict(STUBS)
            sources[P+"api/DanmakuProbe.java"] = (ROOT/"app/src/leanback/java"/P/"api/DanmakuProbe.java").read_text()
            sources["ProbeHarness.java"] = HARNESS
            for name, content in sources.items():
                path = root/name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
            cp = os.pathsep.join(map(str, [root/"classes", player_jar, *jars]))
            subprocess.run(["javac", "-cp", cp, "-d", str(root/"classes"), *[str(root/name) for name in sources]], check=True, timeout=60)
            subprocess.run(["java", "-cp", cp, "ProbeHarness"], check=True, timeout=60)


if __name__ == "__main__":
    unittest.main()
