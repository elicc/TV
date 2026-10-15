"""Execute production search/parse/callback code with deterministic HTTP boundaries.
No live service or Android UI is required; dependencies come from the existing Gradle cache.
"""
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java"
P = "com/fongmi/android/tv/"
STUBS = {
    "android/text/TextUtils.java": "package android.text; public class TextUtils { public static boolean isEmpty(CharSequence s){return s==null||s.length()==0;} }",
    "android/net/Uri.java": 'package android.net; public class Uri { public static String encode(String s){return java.net.URLEncoder.encode(s,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20");} }',
    "android/util/Log.java": 'package android.util; public class Log { public static String messages=""; public static int w(String t,String s){messages+=s;return 0;} }',
    "androidx/collection/ArrayMap.java": "package androidx.collection; public class ArrayMap<K,V> extends java.util.LinkedHashMap<K,V> {}",
    "androidx/annotation/NonNull.java": "package androidx.annotation; public @interface NonNull {}",
    "androidx/annotation/Nullable.java": "package androidx.annotation; public @interface Nullable {}",
    P+"App.java": """package com.fongmi.android.tv; public class App {
        public static java.util.ArrayDeque<Runnable> queue=new java.util.ArrayDeque<>();
        public static com.google.gson.Gson gson(){return new com.google.gson.Gson();}
        public static void post(Runnable r){queue.add(r);}
        public static void drain(){while(!queue.isEmpty())queue.remove().run();}
    }""",
    P+"setting/DanmakuSetting.java": """package com.fongmi.android.tv.setting; public class DanmakuSetting {
        public static String url; public static boolean load=true,auto=true;
        public static boolean isLoad(){return load;} public static boolean isAuto(){return auto;}
        public static String getEffectiveApiUrl(){return url;}
    }""",
    P+"utils/UrlUtil.java": "package com.fongmi.android.tv.utils; public class UrlUtil { public static android.net.Uri uri(String s){return null;} }",
    P+"utils/ResUtil.java": """package com.fongmi.android.tv.utils; public class ResUtil {
        public static java.util.Map<Integer,String> names=new java.util.HashMap<>();
        public static String getString(int id,Object... args){return names.get(id)+java.util.Arrays.toString(args);}
    }""",
    "com/github/catvod/utils/Trans.java": "package com.github.catvod.utils; public class Trans { public static String t2s(String s){return s;} }",
    "com/github/catvod/net/OkHttp.java": """package com.github.catvod.net;
        import okhttp3.*; import java.util.*;
        public class OkHttp {
            public static List<Request> requests=new ArrayList<>();
            public static List<Callback> callbacks=new ArrayList<>();
            public static List<Call> calls=new ArrayList<>();
            public static void cancel(String tag){}
            public static Call newCall(String url,String tag){return create(new Request.Builder().url(url).build());}
            public static Call newCall(String url,RequestBody body,String tag){return create(new Request.Builder().url(url).post(body).build());}
            public static FormBody toBody(androidx.collection.ArrayMap<String,String> params){
                FormBody.Builder b=new FormBody.Builder();params.forEach(b::add);return b.build();
            }
            static Call create(Request r){
                requests.add(r);
                return (Call)java.lang.reflect.Proxy.newProxyInstance(Call.class.getClassLoader(),new Class[]{Call.class},(p,m,a)->{
                    if(m.getName().equals("request"))return r;
                    if(m.getName().equals("enqueue")){callbacks.add((Callback)a[0]);calls.add((Call)p);}
                    return null;
                });
            }
            public static void respond(int i,int code,String text)throws Exception{
                Response r=new Response.Builder().request(requests.get(i)).protocol(Protocol.HTTP_1_1).code(code).message("test")
                    .body(ResponseBody.create(MediaType.get("application/json"),text)).build();
                callbacks.get(i).onResponse(calls.get(i),r);
            }
        }""",
}

HARNESS = r'''
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.net.OkHttp;
import java.util.*;
public class DanmakuHarness {
    static int successes, failures, checks;
    static String error;
    static List<Danmaku> items;
    static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    static void search(){DanmakuApi.search("片名 & +", "第1集", x->{successes++;items=x;}, e->{failures++;error=e.getMessage();});}
    static void response(String body)throws Exception {
        search();OkHttp.respond(OkHttp.callbacks.size()-1,200,body);App.drain();
    }
    static void rejects(String body,String expected)throws Exception {
        int before=failures;response(body);check(failures==before+1 && error.startsWith(expected),"reject "+body+": "+error);
    }
    public static void main(String[] args)throws Exception {
        for(var f:com.fongmi.android.tv.R.string.class.getFields())ResUtil.names.put(f.getInt(null),f.getName());
        DanmakuSetting.url="https://example.com/private-token/api?name={name}&episode={episode}";
        check(DanmakuApi.canSearch(),"enabled");DanmakuSetting.auto=false;check(!DanmakuApi.canSearch(),"auto disabled");DanmakuSetting.auto=true;
        search();var get=OkHttp.requests.get(0);
        check(get.method().equals("GET"),"template GET");
        check(get.url().queryParameter("name").equals("片名 & +"),"name encoded once");
        check(get.url().queryParameter("episode").equals("第1集"),"episode encoded once");
        OkHttp.respond(0,200,"[{\"name\":\"来源\",\"url\":\"https://example.com/comment?format=xml\"}]");
        check(successes==0,"callback posted");App.drain();check(successes==1 && items.get(0).getName().equals("来源"),"valid result");
        DanmakuSetting.url="https://example.com/private-token/api";
        response("[]");var post=OkHttp.requests.get(1);check(post.method().equals("POST"),"plain URL stays POST");
        var form=(okhttp3.FormBody)post.body();check(form.value(0).equals("片名 & +") && form.name(1).equals("episode"),"form fields");
        check(items.isEmpty(),"valid empty array");
        response("[{\"url\":\"https://example.com/a\"},{\"name\":null,\"url\":\"https://example.com/b\"}]");
        check(items.size()==2 && items.get(0).getName().equals("https://example.com/a"),"optional name compatibility");
        rejects("{\"spider\":\"plugin.jar\",\"sites\":[]}","danmaku_api_site_config");
        for(String body:List.of("null","{}","<html>denied</html>","[null]","[1]","[{\"url\":3}]","[{\"url\":\" \"}]","[{\"name\":4,\"url\":\"https://example.com\"}]","[{\"name\":\"missing URL\"}]"))
            rejects(body,"danmaku_api_invalid_response");
        rejects("  ","danmaku_search_empty_response");
        int count=OkHttp.requests.size();search();OkHttp.respond(count,403,"private-token secret body");App.drain();
        check(error.equals("danmaku_search_http_error[403]"),"HTTP stage/code");
        check(OkHttp.requests.size()==count+1,"no GET retry on 403");
        check(!android.util.Log.messages.contains("private-token") && !android.util.Log.messages.contains("片名"),"logs redacted");
        count=OkHttp.requests.size();search();OkHttp.callbacks.get(count).onFailure(OkHttp.calls.get(count),new java.io.IOException("https://private-token"));App.drain();
        check(error.startsWith("danmaku_search_network_error") && !error.contains("private-token"),"safe network message");
        int before=failures;DanmakuSetting.url="not a url";search();check(failures==before,"invalid URL posts callback");App.drain();
        check(failures==before+1 && error.startsWith("danmaku_api_invalid_url"),"invalid URL no crash");
        check(!DanmakuApi.isValidUrl("ftp://example.com") && !DanmakuApi.isValidUrl(null),"invalid schemes");
        check(DanmakuApi.isValidUrl("https://example.com/api?name={name}"),"valid template");
        DanmakuSetting.url="https://example.com/api";before=successes;search();int old=OkHttp.requests.size()-1;search();
        OkHttp.respond(old,200,"[]");App.drain();check(successes==before,"stale callback ignored");
        DanmakuApi.cancel();OkHttp.respond(old+1,200,"[]");App.drain();check(successes==before,"cancelled callback ignored");
        System.out.println("PASS "+checks+" checks: GET/POST, parsing, errors, redaction, dispatch, cancellation");
    }
}
'''


class DanmakuApiTests(unittest.TestCase):
    def test_production_search(self):
        cache = Path.home() / ".gradle/caches"
        modules = cache / "modules-2/files-2.1"
        jars = [next(modules.glob("com.google.code.gson/gson/*/*/gson-*.jar")),
                next(cache.glob("*/transforms/*/transformed/okhttp-runtime.jar")),
                next(modules.glob("com.squareup.okio/okio-jvm/*/*/okio-jvm-*.jar")),
                next(p for p in modules.glob("org.jetbrains.kotlin/kotlin-stdlib/*/*/*.jar") if "sources" not in p.name)]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sources = dict(STUBS)
            for name in ["api/DanmakuApi.java", "bean/Danmaku.java", "impl/ApiCallback.java"]:
                sources[P+name] = (MAIN/P/name).read_text()
            names = sorted(set(re.findall(r"R.string.(\w+)", sources[P+"api/DanmakuApi.java"])))
            sources[P+"R.java"] = "package com.fongmi.android.tv; public class R { public static class string {" + "".join(
                f"public static final int {name}={i};" for i, name in enumerate(names)) + "}}"
            sources["DanmakuHarness.java"] = HARNESS
            for name, content in sources.items():
                path = root/name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
            cp = os.pathsep.join(map(str, [root/"classes", *jars]))
            subprocess.run(["javac", "-cp", cp, "-d", str(root/"classes"), *[str(root/name) for name in sources]], check=True)
            subprocess.run(["java", "-cp", cp, "DanmakuHarness"], check=True)

    def test_localized_messages(self):
        for locale in ["values", "values-zh-rCN", "values-zh-rTW"]:
            entries = {x.attrib["name"]: x.text for x in ET.parse(ROOT/"app/src/main/res"/locale/"danmaku_api_strings.xml").getroot()}
            self.assertEqual(len(entries), 7)
            self.assertIn("%1$d", entries["danmaku_search_http_error"])
            self.assertIn("{name}", entries["danmaku_api_help"])
            self.assertIn("{episode}", entries["danmaku_api_help"])


if __name__ == "__main__":
    unittest.main()
