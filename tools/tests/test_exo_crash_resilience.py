"""Execute the production reader/range validation with deterministic IO fault injection.

Host JVM tests, not UI automation: ARTEMIS read-only inspection returned no hierarchy.
No device key events, playback interruption, or network dependency is needed.
"""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java"
P = "com/fongmi/android/tv/player/exo/"
STUBS = {
    "android/net/Uri.java": """package android.net;
public class Uri {final java.net.URI u; private Uri(String s){u=java.net.URI.create(s);} public static Uri parse(String s){return new Uri(s);} public String getScheme(){return u.getScheme();} public String getHost(){return u.getHost();} public String getPath(){return u.getPath();} public String toString(){return u.toString();}}""",
    "android/os/SystemClock.java": "package android.os; public class SystemClock {public static long elapsedRealtime(){return 100000;}}",
    "androidx/annotation/NonNull.java": "package androidx.annotation; public @interface NonNull {}",
    "androidx/annotation/Nullable.java": "package androidx.annotation; public @interface Nullable {}",
    "androidx/media3/common/C.java": "package androidx.media3.common; public class C {public static final int LENGTH_UNSET=-1,RESULT_END_OF_INPUT=-1,INDEX_UNSET=-1;}",
    "androidx/media3/common/MediaItem.java": "package androidx.media3.common; public class MediaItem {public LocalConfiguration localConfiguration; public static class LocalConfiguration {public android.net.Uri uri;}}",
    "androidx/media3/datasource/DataSource.java": """package androidx.media3.datasource; import java.io.*; import java.util.*; import android.net.Uri;
public interface DataSource {long open(DataSpec d)throws IOException; int read(byte[] b,int o,int l)throws IOException; void close()throws IOException; default Uri getUri(){return null;} default Map<String,List<String>> getResponseHeaders(){return Map.of();}}""",
    "androidx/media3/datasource/BaseDataSource.java": """package androidx.media3.datasource;
public abstract class BaseDataSource implements DataSource {public int transferred,started,ended; public BaseDataSource(boolean b){} protected void transferInitializing(DataSpec s){} protected void transferStarted(DataSpec s){started++;} protected void transferEnded(){ended++;} protected void bytesTransferred(int n){transferred+=n;}}""",
    "androidx/media3/datasource/DataSpec.java": """package androidx.media3.datasource; import android.net.Uri;
public class DataSpec {public final Uri uri; public final long position,length; public DataSpec(Uri u,long p,long l){uri=u;position=p;length=l;} public String getHttpMethodString(){return "GET";} public DataSpec subrange(long n){if(length!=-1&&n>=length)throw new IllegalArgumentException("empty range");return new DataSpec(uri,position+n,length==-1?-1:length-n);}}""",
    "androidx/media3/datasource/cache/Cache.java": "package androidx.media3.datasource.cache; public interface Cache {class CacheException extends java.io.IOException {}}",
    "okhttp3/Request.java": """package okhttp3; import java.util.*;
public class Request {String url; public Map<String,String> headers; public Object url(){return url;} public static class Builder {Request r=new Request();public Builder(){r.headers=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);} public Builder url(String s){r.url=s;return this;} public Builder header(String k,String v){r.headers.put(k,v);return this;}public Request build(){return r;}}}""",
    "okhttp3/Call.java": "package okhttp3; public interface Call {Response execute() throws java.io.IOException; void cancel();}",
    "okhttp3/Response.java": """package okhttp3; import java.io.*; import java.util.*;
public class Response implements AutoCloseable {public int code=206;public String range="bytes 0-3/4",encoding;public long length=4;public byte[] bytes={1,2,3,4};public boolean closed;public Request request;
 public int code(){return code;} public String header(String s){return s.equals("Content-Range")?range:encoding;}public Body body(){return new Body();}public Request request(){return request;}public Headers headers(){return new Headers();}public void close(){closed=true;}
 public class Body {public long contentLength(){return length;}public InputStream byteStream(){return new ByteArrayInputStream(bytes);}}
 public class Headers {public Map<String,List<String>> toMultimap(){return Map.of();}}}
""",
    "com/github/catvod/net/OkHttp.java": """package com.github.catvod.net; import okhttp3.*;
public class OkHttp {public static Response next;public static FakeCall last;public static OkHttp player(){return new OkHttp();} public Call newCall(Request r){next.request=r;last=new FakeCall(next);return last;} public static class FakeCall implements Call {final Response r;public boolean cancelled;FakeCall(Response r){this.r=r;}public Response execute(){return r;}public void cancel(){cancelled=true;}}}""",
    P+"ExoReadAhead.java": """package com.fongmi.android.tv.player.exo; import androidx.media3.datasource.*; import java.util.*; import okhttp3.Call;
class ExoReadAhead {
 static ExoReadAhead next;boolean usable=true,prepareBad,lookupBad,reportBad;int releases,failures;long total=8;DataSource segment;
 Set<Call> calls=new HashSet<>();static ExoReadAhead get(DataSpec d){return next;}
 long prepare(){if(prepareBad)throw new IllegalStateException();return total;}boolean isUsable(){return usable;}void onOpen(long n){}void onRead(long n){if(reportBad)throw new IllegalStateException();}
 void failCache(){failures++;usable=false;for(Call c:calls)c.cancel();}void track(Call c){calls.add(c);if(!usable)c.cancel();}void untrack(Call c){calls.remove(c);}
 long getVersion(){return 0;}long getCachedLength(long p,long e){if(lookupBad)throw new IllegalStateException();return 0;}boolean claimForPlayer(long p,long v,boolean t){return true;}
 void releasePlayer(long p){releases++;}long getChunkEnd(long p,long e){return Math.min(e,p+4);}DataSource createDownloader(boolean p){return segment;}DataSource createCacheReader(){return segment;}
 DataSpec buildDataSpec(long p,long l){return new DataSpec(android.net.Uri.parse("https://example.test/file"),p,l);}
}""",
}

HARNESS = r"""package com.fongmi.android.tv.player.exo;
import java.io.*;import java.util.*;import android.net.Uri;import androidx.media3.datasource.*;import com.github.catvod.net.OkHttp;import okhttp3.Response;
public class ResilienceHarness {
 static int checks;
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static DataSpec spec(long p,long l){return new DataSpec(Uri.parse("https://example.test/file"),p,l);}
 static class Fake implements DataSource {
  boolean badClose,badOpen,eof,ioOpen;int opens,closes;long pos,limit;DataSpec opened;
  public long open(DataSpec d)throws IOException{opens++;opened=d;pos=d.position;limit=d.length==-1?8:pos+d.length;if(ioOpen)throw new IOException("offline");if(badOpen)throw new IllegalStateException();return limit-pos;}
  public int read(byte[] b,int o,int l){if(eof||pos>=limit)return -1;int n=(int)Math.min(l,limit-pos);for(int i=0;i<n;i++)b[o+i]=(byte)pos++;return n;}
  public void close(){closes++;if(badClose)throw new IllegalStateException();}
 }
 static ExoReadAhead session(Fake f){var s=new ExoReadAhead();s.segment=f;ExoReadAhead.next=s;return s;}
 static void expectInvalid(ExoRangeDataSource d)throws Exception{try{d.open(spec(0,4));throw new AssertionError("accepted invalid range");}catch(ExoRangeDataSource.InvalidRangeException expected){checks++;}finally{d.close();}}
 public static void main(String[] args)throws Exception {
  for(String url:List.of("http://127.0.0.1:9978/proxy?a=1","http://127.1.2.3/f","http://localhost/f","http://[::1]/f","http://192.168.1.2/proxy","https://example.test/proxy/drive"))check(ExoPreloadPolicy.isProxy(Uri.parse(url)),"proxy classification "+url);
  for(String url:List.of("https://cdn.test/a.mp4","https://localhost.example/a","file:///proxy","https://example.test/proxymovie"))check(!ExoPreloadPolicy.isProxy(Uri.parse(url)),"direct classification "+url);
  var cached=new Fake();var raw=new Fake();ExoReadAhead.next=null;
  var reader=new ExoReadAheadDataSource(cached,raw);reader.open(new DataSpec(Uri.parse("http://127.0.0.1/f"),0,-1));check(raw.opens==1&&cached.opens==0,"proxy bypasses cached fallback");reader.close();
  cached=new Fake();raw=new Fake();reader=new ExoReadAheadDataSource(cached,raw);reader.open(spec(0,-1));check(cached.opens==1&&raw.opens==0,"non-session direct/HLS cache preserved");reader.close();
  for(int fault=0;fault<4;fault++){
   var segment=new Fake();var s=session(segment);cached=new Fake();raw=new Fake();
   s.prepareBad=fault==0;s.lookupBad=fault==1;segment.badOpen=fault==2;segment.eof=fault==3;
   reader=new ExoReadAheadDataSource(cached,raw);reader.open(spec(2,-1));byte[] b=new byte[2];check(reader.read(b,0,2)==2&&b[0]==2,"fault fallback exact offset "+fault);
   check(raw.opens==1&&cached.opens==0&&!s.usable,"failed cache bypassed "+fault);reader.close();
  }
  var segment=new Fake();segment.badClose=true;var s=session(segment);raw=new Fake();cached=new Fake();reader=new ExoReadAheadDataSource(cached,raw);reader.open(spec(0,-1));
  byte[] all=new byte[8];check(reader.read(all,0,4)==4,"read first cached chunk");check(reader.read(all,4,4)==4,"close assertion falls back");
  check(Arrays.equals(all,new byte[]{0,1,2,3,4,5,6,7})&&s.releases==1,"no lost/duplicated bytes and claim released");reader.close();check(reader.started==1&&reader.ended==1,"transfer lifecycle balanced");
  segment=new Fake();s=session(segment);s.reportBad=true;raw=new Fake();reader=new ExoReadAheadDataSource(new Fake(),raw);reader.open(spec(0,8));all=new byte[8];reader.read(all,0,4);reader.read(all,4,4);
  check(Arrays.equals(all,new byte[]{0,1,2,3,4,5,6,7}),"report failure must return already-consumed bytes");check(reader.read(all,0,1)==-1,"finite fallback ends");reader.close();
  segment=new Fake();s=session(segment);s.usable=false;raw=new Fake();raw.ioOpen=true;reader=new ExoReadAheadDataSource(new Fake(),raw);reader.open(spec(0,-1));
  try{reader.read(new byte[1],0,1);throw new AssertionError("upstream error lost");}catch(IOException expected){checks++;}reader.close();
  // close() assertions must not escape even when the caller does not read another chunk.
  segment=new Fake();segment.badClose=true;s=session(segment);reader=new ExoReadAheadDataSource(new Fake(),new Fake());reader.open(spec(0,4));reader.read(new byte[1],0,1);reader.close();check(s.releases==1&&!s.usable,"teardown failure isolated");
  check(ExoRangeDataSource.parseTotal("bytes 1-1/100",1,1)==100,"valid probe");
  for(String header:List.of("bytes 0-1/100","bytes 1-1/*","bytes 1-1/1","bytes 1-1/9999999999999999999999","junk")){
   try{ExoRangeDataSource.parseTotal(header,1,1);throw new AssertionError(header);}catch(ExoRangeDataSource.InvalidRangeException expected){checks++;}
  }
  s=session(new Fake());OkHttp.next=new Response();var range=new ExoRangeDataSource(s,Map.of("Range","bytes=0-100","Accept-Encoding","gzip"),4);
  range.open(spec(0,4));check(OkHttp.next.request.headers.get("Range").equals("bytes=0-3")&&OkHttp.next.request.headers.get("Accept-Encoding").equals("identity"),"reserved range headers win");
  check(range.read(new byte[8],0,8)==4&&range.read(new byte[1],0,1)==-1,"exact range ends normally");range.close();check(s.calls.isEmpty()&&OkHttp.last.cancelled&&OkHttp.next.closed,"close unregisters and cancels request");
  for(int fault=0;fault<5;fault++){
   OkHttp.next=new Response();if(fault==0)OkHttp.next.code=200;if(fault==1)OkHttp.next.range="bytes 0-3/9";if(fault==2)OkHttp.next.range="bytes 1-4/5";if(fault==3)OkHttp.next.length=2;if(fault==4)OkHttp.next.encoding="gzip";
   expectInvalid(new ExoRangeDataSource(s,Map.of(),4));check(s.calls.isEmpty(),"failed open cleanup");
  }
  OkHttp.next=new Response();OkHttp.next.length=-1;OkHttp.next.bytes=new byte[]{1,2};range=new ExoRangeDataSource(s,Map.of(),4);range.open(spec(0,4));range.read(new byte[4],0,4);
  try{range.read(new byte[4],0,4);throw new AssertionError("short read became EOF");}catch(ExoRangeDataSource.InvalidRangeException expected){checks++;}range.close();
  OkHttp.next=new Response();range=new ExoRangeDataSource(s,Map.of(),4);range.open(spec(0,4));s.failCache();check(OkHttp.last.cancelled,"disable cancels inflight IO");range.close();
  System.out.println("PASS production reader/range resilience: "+checks+" checks");
 }
}
"""


class ExoCrashResilienceTests(unittest.TestCase):
    def test_production_reader_and_range_faults(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            sources = dict(STUBS)
            sources[P+"ResilienceHarness.java"] = HARNESS
            for path, content in sources.items():
                dest = root/path
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_text(content)
            production = [JAVA/P/(name+".java") for name in ("ExoRangeDataSource", "ExoReadAheadDataSource", "ExoPreloadPolicy")]
            result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(root), *[str(root/p) for p in sources], *map(str, production)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            result = subprocess.run(["java", "-cp", str(root), "com.fongmi.android.tv.player.exo.ResilienceHarness"], capture_output=True, text=True, timeout=15)
            self.assertEqual(0, result.returncode, result.stdout+result.stderr)
            print(result.stdout.strip())

    def test_session_and_preload_wiring(self):
        session = (JAVA/P/"ExoReadAhead.java").read_text()
        self.assertIn("UUID.randomUUID()", session)
        self.assertNotIn("METADATA_LENGTH", session)
        self.assertIn("for (Call call : calls) call.cancel()", session)
        self.assertIn("if (!isUsable()) call.cancel()", session)
        self.assertIn("new ExoRangeDataSource(this, headers, contentLength)", session)
        self.assertIn("catch (IllegalStateException e)", session[session.index("private void work()"):])
        self.assertNotIn("catch (Throwable", session)
        preload = (JAVA/P/"ExoDiskPreload.java").read_text()
        self.assertIn("!PreloadSetting.isEnabled() || ExoPreloadPolicy.isProxy(mediaItem)", preload)
        self.assertIn("resourceSampler.stop()", preload)
        next_episode = (JAVA/P/"ExoPlayerSession.java").read_text()
        self.assertIn("ExoPreloadPolicy.isProxy(player.getCurrentMediaItem())", next_episode)
        self.assertIn("ExoPreloadPolicy.isProxy(mediaItem)", next_episode)


if __name__ == "__main__":
    unittest.main()
