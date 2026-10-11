"""Execute production history/resume code with in-memory Room/Android boundaries.

Uses cached Gson and javac, not an Android UI/emulator test. No database migration.
"""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java"
P = "com/fongmi/android/tv/"
STUBS = {
    "android/text/TextUtils.java": "package android.text; public class TextUtils { public static boolean isEmpty(CharSequence s){return s==null||s.length()==0;} }",
    "android/view/View.java": "package android.view; public class View { public static final int GONE=8,VISIBLE=0; }",
    "android/os/Parcelable.java": "package android.os; public interface Parcelable { int describeContents(); void writeToParcel(Parcel p,int f); interface Creator<T>{T createFromParcel(Parcel p);T[] newArray(int s);} }",
    "android/os/Parcel.java": "package android.os; public class Parcel { public String readString(){return null;} public int readInt(){return 0;} public byte readByte(){return 0;} public void writeString(String s){} public void writeInt(int i){} public void writeByte(byte b){} public <T> java.util.List<T> createTypedArrayList(Parcelable.Creator<T> c){return null;} public void writeTypedList(java.util.List<?> l){} }",
    "androidx/media3/common/C.java": "package androidx.media3.common; public class C {public static final long TIME_UNSET=-9223372036854775807L;}",
    "com/github/catvod/utils/Trans.java": "package com.github.catvod.utils; public class Trans {public static boolean pass(){return true;} public static String s2t(String s){return s;} public static String t2s(String s){return s;}}",
    "com/github/catvod/utils/Prefers.java": "package com.github.catvod.utils; public class Prefers {public static final java.util.Map<String,String> values=new java.util.HashMap<>(); public static String getString(String k){return values.getOrDefault(k,\"\");} public static void put(String k,String v){values.put(k,v);} public static void remove(String k){values.remove(k);}}",
    P+"App.java": "package com.fongmi.android.tv; public class App {private static final com.google.gson.Gson gson=new com.google.gson.Gson(); public static com.google.gson.Gson gson(){return gson;}}",
    P+"Constant.java": "package com.fongmi.android.tv; public class Constant {public static long HISTORY_TIME=999999999L;}",
    P+"R.java": "package com.fongmi.android.tv; public class R {public static class string {public static int play_backward=1,play_forward=2,play_backward_hint=3,play_forward_hint=4;}}",
    P+"api/config/VodConfig.java": "package com.fongmi.android.tv.api.config; public class VodConfig {public static int getCid(){return 1;} public static VodConfig get(){return new VodConfig();} public Site getSite(String key){return new Site();} public static class Site {public String getName(){return \"site\";}}}",
    P+"setting/Setting.java": "package com.fongmi.android.tv.setting; public class Setting {public static boolean incognito=false; public static boolean isIncognito(){return incognito;}}",
    P+"event/RefreshEvent.java": "package com.fongmi.android.tv.event; public class RefreshEvent {public static void history(){}}",
    P+"utils/Task.java": "package com.fongmi.android.tv.utils; public class Task {public static java.util.ArrayDeque<Runnable> tasks=new java.util.ArrayDeque<>(); public static void executeSerial(Runnable r){tasks.add(r);} public static void flush(){while(!tasks.isEmpty())tasks.remove().run();}}",
    P+"bean/Vod.java": """package com.fongmi.android.tv.bean;
public record Vod(String name,String year,String typeName,java.util.List<Flag> flags){
 public String getArea(){return "";} public String getDirector(){return "";} public String getActor(){return "";} public String getName(){return name;} public String getYear(){return year;} public String getTypeName(){return typeName;} public java.util.List<Flag> getFlags(){return flags;}}
""",
    P+"db/AppDatabase.java": """package com.fongmi.android.tv.db;
public class AppDatabase {public static final String SYMBOL="@@@"; private static final AppDatabase db=new AppDatabase();
 public static AppDatabase get(){return db;} public final com.fongmi.android.tv.db.dao.HistoryDao history=new com.fongmi.android.tv.db.dao.HistoryDao();
 public com.fongmi.android.tv.db.dao.HistoryDao getHistoryDao(){return history;} public Track getTrackDao(){return new Track();} public static class Track{public void delete(String key){throw new AssertionError("must not delete source tracks");}}}
""",
    P+"db/dao/HistoryDao.java": """package com.fongmi.android.tv.db.dao;
import java.util.*; import com.fongmi.android.tv.bean.History; import com.fongmi.android.tv.App;
public class HistoryDao {
 public final Map<String,History> rows=new LinkedHashMap<>();
 private History copy(History h){return h==null?null:App.gson().fromJson(App.gson().toJson(h),History.class);}
 public History find(int cid,String key){History h=rows.get(key);return h!=null&&h.getCid()==cid?copy(h):null;}
 public List<History> find(int cid,long time){return rows.values().stream().filter(h->h.getCid()==cid).map(this::copy).toList();}
 public List<History> findAll(){return rows.values().stream().map(this::copy).toList();}
 public List<History> findRecent(long time){return rows.values().stream().map(this::copy).toList();}
 public List<History> findResumable(int cid){return find(cid,0).stream().filter(History::canSave).toList();}
 public List<History> findByName(int cid,String name){return find(cid,0).stream().filter(h->name.equals(h.getVodName())).sorted(Comparator.comparingLong(History::getCreateTime).reversed()).toList();}
 public void insertOrUpdate(History h){rows.put(h.getKey(),copy(h));}
 public void delete(int cid,String key){rows.remove(key);} public void delete(int cid){rows.values().removeIf(h->h.getCid()==cid);} public void delete(){rows.clear();}
}
""",
}
for annotation in ("androidx/annotation/NonNull", "androidx/annotation/Nullable", "androidx/room/Entity", "androidx/room/Ignore", "androidx/room/PrimaryKey"):
    package, name = annotation.rsplit("/", 1)
    package = package.replace("/", ".")
    STUBS[annotation+".java"] = f"package {package}; public @interface {name} {{}}"
for name in ("Attribute", "Text"):
    STUBS[f"org/simpleframework/xml/{name}.java"] = f"package org.simpleframework.xml; public @interface {name} {{String name() default \"\"; boolean required() default false;}}"

STUBS[P+"metadata/MetadataProvider.java"] = "package com.fongmi.android.tv.metadata; public enum MetadataProvider {SOURCE,DOUBAN,TMDB}"
STUBS[P+"metadata/MetadataRepository.java"] = """package com.fongmi.android.tv.metadata;
public class MetadataRepository {
 static final MetadataRepository repo=new MetadataRepository();
 public static final java.util.Map<String,PlaybackIdentity> identities=new java.util.HashMap<>();
 public static MetadataRepository get(){return repo;}
 public PlaybackIdentity getPlaybackIdentity(MovieIdentity i){return identities.get(i.sourceVodId());}
 public record PlaybackIdentity(String id,String type,boolean userVerified){public boolean sameWork(PlaybackIdentity o){return o!=null&&id.equals(o.id)&&type.equals(o.type);}}
}
"""
STUBS[P+"metadata/MovieMetadata.java"] = """package com.fongmi.android.tv.metadata;
public class MovieMetadata {
 String title="",year="",type="",externalType="",externalId="",directors="",actors="",area="";
 public String getTitle(){return title;}public void setTitle(String v){title=v;}
 public String getYear(){return year;}public void setYear(String v){year=v;}
 public String getType(){return type;}public void setType(String v){type=v;}
 public String getExternalType(){return externalType;}public void setExternalType(String v){externalType=v;}
 public String getExternalId(){return externalId;}public void setExternalId(String v){externalId=v;}
 public String getDirectors(){return directors;}public void setDirectors(String v){directors=v;}
 public String getActors(){return actors;}public void setActors(String v){actors=v;}
 public String getArea(){return area;}
}
"""

HARNESS = r"""package com.fongmi.android.tv.playback.vod;
import java.util.*;
import com.fongmi.android.tv.bean.*;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.utils.Task;
import androidx.media3.common.C;
import com.fongmi.android.tv.metadata.*;

public class ResumeHarness {
 static int checks;
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static History history(String key,String title,String ep,long pos,long time){
  History h=new History();h.setKey(key);h.setCid(1);h.setVodName(title);h.setVodFlag("line");h.setVodRemarks(ep);h.setEpisodeUrl("old/"+ep);
  h.setEpisodeIndex(0);h.setPosition(pos);h.setDuration(2700000);h.setCreateTime(time);h.setProgressTime(time);h.setResumeYear("2023");h.setResumeType("电视剧");return h;
 }
 static Vod vod(String name,String year,String... episodes){
  Flag f=Flag.create("line");for(String ep:episodes)f.getEpisodes().add(Episode.create(ep,"new/"+ep));return new Vod(name,year,"电视剧",List.of(f));
 }
 static void save(History h){VodResumeStore.persist(h,true);}
 static History read(String key){return VodResumeStore.read(History.find(key));}
 public static void main(String[] args){
  VodHistoryPolicy policy=new VodHistoryPolicy();
  History old=history("a","狂飙","第30集",2160000,200);
  History stale=history("b","狂飙","第1集",0,100);
  old.setEpisodeIndex(1);save(old);save(stale);
  Vod target=vod("狂飙","2023","01","30","31");
  var resolved=policy.resolve("b","",target,null);
  check(!resolved.needsConfirmation(),"known same work resumes automatically");
  check(resolved.history().getVodRemarks().equals("30")&&resolved.history().getPosition()==2160000,"latest other source beats old exact-key row");
  check(resolved.history().getEpisodeUrl().equals("new/30"),"target URL, not old source URL");
  check(resolved.history().getDuration()==C.TIME_UNSET,"do not invent target duration");
  policy.saveStarted(resolved.history(),300);Task.flush();
  check(read("a").getPosition()==2160000,"starting source B retains A");
  check(read("b").getVodRemarks().equals("第1集"),"visit cannot overwrite saved checkpoint before playback");
  check(read("b").getProgressTime()==100,"visit time is not progress time");
  policy.updateProgress(resolved.history(),400,2165000,2750000);policy.save(resolved.history());Task.flush();
  check(read("b").getVodRemarks().equals("30"),"actual progress commits mapped episode");
  check(read("a")!=null,"saving new source never deletes old source");
  old.merge().save();check(read("b")!=null,"legacy merge no longer deletes other history/tracks");
  History late=stale.copy();late.setCreateTime(9999);policy.saveStarted(late,9999);Task.flush();
  check(read("b").getPosition()==2165000&&read("b").getProgressTime()==400,"late old-page visit preserves new checkpoint");
  save(stale);check(read("b").getPosition()==2165000,"stale progress write rejected");
  var missing=policy.resolve("c","",vod("狂飙","2023","01"),null);
  check(missing.needsConfirmation()&&missing.match().episode()==null,"single episode is not automatically episode 30");
  var legacy=policy.resolve("d","",vod("狂飙","","01","30"),null);
  check(legacy.needsConfirmation()&&legacy.match().episode()!=null,"unknown year requires explicit consent");
  policy.applyResume(legacy.history(),legacy.match());check(legacy.history().getPosition()==2165000,"confirmed legacy resume works");
  var different=policy.resolve("e","",vod("狂飙","2024","30"),null);
  check(different.match()==null,"different year must not inherit");
  check(policy.resolve("f","",vod("狂飙第二季","2023","30"),null).match()==null,"different season must not inherit");
  check(policy.resolve("g","",vod("狂飙（高清）","2023","01","30"),null).history().getPosition()==2165000,"bounded quality suffix normalization");
  check(policy.resolve("h","",vod("狂飙导演剪辑版","2023","30"),null).match()==null,"edition remains part of identity");
  var ambiguous=policy.resolve("i","",vod("狂飙","2023","030","第30集"),null);
  check(!ambiguous.needsConfirmation()&&ambiguous.history().getEpisodeIndex()==1,"duplicate episode numbers do not affect ordinal mapping");
  History recentRewatch=history("j","狂飙","第1集",5000,10000);save(recentRewatch);
  check(policy.resolve("k","",target,null).history().getPosition()==5000,"latest intentional rewatch beats greatest episode/minute");
  History inFlight=history("pending","另一影片","02",55000,11000);inFlight.setEpisodeIndex(1);policy.save(inFlight);
  check(policy.resolve("next","",vod("另一影片","2023","01","02"),null).history().getPosition()==55000,"queued write visible immediately");
  Task.flush();
  History snapshot=history("snapshot","快切","03",77000,12000);
  check(policy.resolve("snapshot-target","",vod("快切","2023","03"),snapshot).history().getPosition()==77000,"source switch passes live snapshot");
  History invalid=history("invalid","无效","01",-1,99999);invalid.setDuration(C.TIME_UNSET);save(invalid);
  check(policy.resolve("invalid-target","",vod("无效","2023","01"),null).match()==null,"invalid history not a checkpoint");
  History episode=history("episode","测试","第30集",50000,100);
  Flag episodeLine=Flag.create("new");episodeLine.getEpisodes().add(Episode.create("30","url"));episodeLine.getEpisodes().add(Episode.create("31","url31"));episodeLine.indexEpisodes();
  policy.updateEpisode(episode,episodeLine,episodeLine.getEpisodes().get(0));
  check(episode.getPosition()==50000,"same ordinal preserves minutes across lines");
  policy.updateEpisode(episode,episodeLine,episodeLine.getEpisodes().get(1));
  check(episode.getPosition()==C.TIME_UNSET,"manual different episode resets minutes");
  History lineHistory=history("line-choice","线路恢复","第03集",77000,40000);
  lineHistory.setVodFlag("bf");save(lineHistory);
  Flag firstLine=Flag.create("ff"),savedLine=Flag.create("bf");
  firstLine.getEpisodes().add(Episode.create("03","ff/03"));
  savedLine.getEpisodes().add(Episode.create("03","bf/03"));
  Vod multiLine=new Vod("线路恢复","2023","电视剧",List.of(firstLine,savedLine));
  History resumedLine=policy.resolve("line-choice","",multiLine,null).history();
  check(resumedLine.getVodFlag().equals("bf") && resumedLine.getEpisodeUrl().equals("bf/03") && resumedLine.getPosition()==77000,"restore saved non-first line and its own episode URL");
  Vod reordered=new Vod("线路恢复","2023","电视剧",List.of(savedLine,firstLine));
  check(policy.resolve("line-choice","",reordered,null).history().getVodFlag().equals("bf"),"line identity survives provider reorder");
  History switched=resumedLine.copy();policy.updateEpisode(switched,firstLine,firstLine.getEpisodes().get(0));
  policy.saveStarted(switched,41000);
  check(policy.resolve("line-choice","",multiLine,null).history().getVodFlag().equals("ff"),"immediate reentry sees new line before queued write completes");
  Task.flush();
  check(read("line-choice").getVodFlag().equals("ff") && read("line-choice").getPosition()==77000,"successful same-episode line switch persists even before a new progress sample");
  check(policy.resolve("line-choice","",multiLine,null).history().getVodFlag().equals("ff"),"reentry restores newly selected line");
  History delayed=resumedLine.copy();policy.saveStarted(delayed,40500);Task.flush();
  check(read("line-choice").getVodFlag().equals("ff"),"older visit cannot overwrite newer selected line");
  Vod removedLine=new Vod("线路恢复","2023","电视剧",List.of(savedLine));
  check(policy.resolve("line-choice","",removedLine,null).history().getVodFlag().equals("bf"),"missing saved line maps episode to an available line");
  History noSample=history("no-sample","尚无时长","03",0,42000);noSample.setDuration(C.TIME_UNSET);noSample.setVodFlag("bf");noSample.save();
  Vod noSampleVod=new Vod("尚无时长","2023","电视剧",List.of(firstLine,savedLine));
  check(policy.resolve("no-sample","",noSampleVod,null).history().getVodFlag().equals("bf"),"line selection survives even without resumable minutes");
  History raw=history("raw","旧数据","01",34000,600);raw.save();
  policy.saveStarted(raw,700);Task.flush();check(read("raw").getProgressTime()==600,"first legacy touch freezes old timestamp");
  History live=read("b");policy.saveVisit(live,false,20000);Task.flush();
  check(read("b").getPosition()==2165000,"temporary live classification cannot erase VOD progress");
  com.github.catvod.utils.Prefers.put("vod_resume_v1_b","bad json");
  check(read("b").getPosition()==2165000,"corrupt auxiliary metadata does not destroy history");
  MovieMetadata minimal=new MovieMetadata();minimal.setTitle("狂飙");
  MovieIdentity sparse=new MovieIdentity("site","id","狂飙","","","","","");
  check(MetadataMatcher.score(sparse,minimal)==1,"existing artwork matcher can yield a title-only perfect score");
  check(!MetadataMatcher.isResumeSafe(sparse,minimal,false),"title-only score is not automatic resume evidence");
  check(MetadataMatcher.isResumeSafe(sparse,minimal,true),"explicit user confirmation is distinct evidence");
  MovieIdentity nextSeason=new MovieIdentity("site","id","狂飙第二季","2023","","电视剧","","");
  check(!MetadataMatcher.isResumeSafe(nextSeason,minimal,true),"manual mapping cannot bypass season conflict");
  History alias=history("alias-a","别名甲","32",600000,30000);save(alias);
  var trusted=new MetadataRepository.PlaybackIdentity("12345","tv",true);
  MetadataRepository.identities.put("alias-a",trusted);MetadataRepository.identities.put("alias-b",trusted);
  check(policy.resolve("alias-b","",vod("别名乙","2023","32"),null).history().getPosition()==600000,"trusted same provider ID supports different source titles");
  MetadataRepository.identities.put("alias-b",new MetadataRepository.PlaybackIdentity("99999","tv",true));
  check(policy.resolve("alias-b","",vod("别名甲","2023","32"),null).match()==null,"different trusted IDs override identical title/year");
  MetadataRepository.identities.put("alias-b",trusted);
  check(policy.resolve("alias-b","",vod("别名乙导演剪辑版","2023","32"),null).match()==null,"same ID does not override edition conflict");
  VodResumeGuard guard=new VodResumeGuard();guard.begin(600000,2700000);
  check(guard.check(0,2700000,100)==VodResumeGuard.Check.WAIT,"pre-seek zero cannot overwrite checkpoint");
  check(guard.check(0,2700000,8100)==VodResumeGuard.Check.UNSAFE,"ignored seek requires confirmation");
  guard.begin(600000,2700000);
  check(guard.check(600000,2700000,100)==VodResumeGuard.Check.ACCEPT,"successful seek accepts samples");
  check(guard.check(0,2700000,200)==VodResumeGuard.Check.ACCEPT,"later intentional rewind remains valid");
  guard.begin(600000,2700000);
  check(guard.check(599999,500000,100)==VodResumeGuard.Check.UNSAFE,"checkpoint outside target duration is rejected");
  guard.begin(600000,2700000);
  check(guard.check(600000,1800000,100)==VodResumeGuard.Check.UNSAFE,"large version duration mismatch is rejected");
  guard.clear();check(guard.check(0,1800000,200)==VodResumeGuard.Check.ACCEPT,"explicit user seek clears initial guard");
  History.clearAll();Task.flush();
  check(VodResumeStore.candidates(1).isEmpty(),"history deletion also clears pending resume records");
  check(com.github.catvod.utils.Prefers.values.isEmpty(),"history deletion removes auxiliary progress metadata");
  System.out.println("PASS production resume/history/store: "+checks+" checks");
 }
}
"""

INDEX_HARNESS = r"""package com.fongmi.android.tv.playback.vod;
import java.util.*;
import com.fongmi.android.tv.bean.*;
import com.fongmi.android.tv.utils.Task;
import androidx.media3.common.C;
import com.github.catvod.utils.Prefers;
public class IndexHarness {
 static int checks;
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static Vod vod(String... names){Flag f=Flag.create("line");for(int i=0;i<names.length;i++)f.getEpisodes().add(Episode.create(names[i],"url/"+i));return new Vod("序号测试","2023","电视剧",List.of(f));}
 static History checkpoint(String key,int index){History h=new History();h.setKey(key);h.setCid(1);h.setVodName("序号测试");h.setResumeYear("2023");h.setResumeType("电视剧");h.setVodFlag("line");h.setVodRemarks("第99集");h.setEpisodeUrl("old/url");h.setEpisodeIndex(index);h.setPosition(600000);h.setDuration(2700000);h.setCreateTime(100);h.setProgressTime(100);return h;}
 public static void main(String[] args){
  VodHistoryPolicy p=new VodHistoryPolicy();History source=checkpoint("source",1);
  VodResumeStore.persist(source,true);
  check(VodResumeStore.read(History.find("source")).getEpisodeIndex()==1,"index survives disk round trip");
  check(source.copy().getEpisodeIndex()==1,"snapshot carries index");
  var r=p.resolve("target","",vod("第99集","完全不同的名称","第二集"),null);
  check(!r.needsConfirmation()&&r.history().getVodRemarks().equals("完全不同的名称"),"list ordinal wins over matching episode name/number");
  check(r.history().getEpisodeIndex()==1&&r.history().getPosition()==600000&&r.history().getEpisodeUrl().equals("url/1"),"index mapping retains minutes with target URL");
  var duplicates=p.resolve("duplicates","",vod("重复","重复","重复"),null);
  check(!duplicates.needsConfirmation()&&duplicates.history().getEpisodeUrl().equals("url/1"),"duplicate labels do not matter");
  var shortList=p.resolve("short","",vod("第99集"),null);
  check(shortList.needsConfirmation()&&shortList.match().episode()==null,"out of range cannot fall back by matching name or first item");
  Vod reversed=vod("前","中","后","尾");Flag f=reversed.getFlags().get(0);f.indexEpisodes();Collections.reverse(f.getEpisodes());
  var reverseMatch=p.resolve("reversed","",reversed,null);
  check(reverseMatch.history().getEpisodeUrl().equals("url/1"),"UI reverse preserves provider ordinal");
  p.updateEpisode(reverseMatch.history(),f,f.getEpisodes().get(0));
  check(reverseMatch.history().getEpisodeIndex()==3&&reverseMatch.history().getPosition()==C.TIME_UNSET,"manual pick in reversed UI stores original ordinal and clears minutes");
  Flag next=vod("next-A","next-B","next-C").getFlags().get(0);next.indexEpisodes();
  History line=checkpoint("line-switch",1);p.updateEpisode(line,next,next.getEpisodes().get(1));
  check(line.getPosition()==600000&&line.getVodRemarks().equals("next-B"),"line switch preserves position despite completely different labels");
  VodResumeStore.persist(checkpoint("line-switch",1),true);p.saveStarted(line,200);Task.flush();
  History stored=VodResumeStore.read(History.find("line-switch"));
  check(stored.getEpisodeIndex()==1&&stored.getVodRemarks().equals("next-B")&&stored.getPosition()==600000,"visit stores target label and ordinal without losing checkpoint");
  f.mergeEpisodes(List.of(Episode.create("新条目","new")),true);
  check(f.getEpisodes().get(0).getIndex()==4&&VodResumeMatcher.findEpisode(f,2).getUrl().equals("url/2"),"appending while reversed does not shift old ordinals");
  History.clearAll();Task.flush();History legacy=checkpoint("legacy",-1);VodResumeStore.persist(legacy,true);
  var unknown=p.resolve("other","",vod("第99集","x"),null);
  check(unknown.needsConfirmation()&&unknown.match().episode()==null,"legacy cross-source history cannot invent index from title");
  var own=p.resolve("legacy","",vod("x","第99集"),null);
  check(own.history().getEpisodeIndex()==1&&own.history().getPosition()==600000,"own-source legacy lookup learns ordinal without DB migration");
  p.save(own.history());Task.flush();
  check(p.resolve("after-legacy","",vod("one","two"),null).history().getEpisodeIndex()==1,"learned ordinal enables subsequent cross-source resume");
  String pref=Prefers.getString("vod_resume_v1_legacy");
  Prefers.put("vod_resume_v1_legacy",pref.replace("\"episodeIndex\":1,",""));
  check(VodResumeStore.read(History.find("legacy")).getEpisodeIndex()==-1,"older preference entries must not default to first episode");
  Prefers.put("vod_resume_v1_legacy",pref);History replacement=History.find("legacy");replacement.setEpisodeUrl("replaced");replacement.save();
  check(VodResumeStore.read(History.find("legacy")).getEpisodeIndex()==-1,"replaced checkpoint does not inherit stale ordinal");
  System.out.println("PASS ordinal resume: "+checks+" checks");
 }
}
"""

class VodResumeTests(unittest.TestCase):
    def test_ordinal_resume(self):
        self.run_harness(INDEX_HARNESS, "IndexHarness")

    def test_production_resume_and_persistence(self):
        self.run_harness(HARNESS, "ResumeHarness")

    def test_cache_only_trusted_metadata_gate(self):
        source = (MAIN/P/"metadata/MetadataRepository.java").read_text()
        # Execute the actual cache gate and fingerprint code, excluding provider/network construction.
        gate = source[source.index("    public record PlaybackIdentity"):source.index("    public MetadataState initial")]
        fingerprint = source[source.index("    private static String identityFingerprint"):source.index("    private static boolean mappingMatches")]
        fields = source[source.index("    private static class CachedMetadata"):source.rfind("\n}")]
        repository = """package com.fongmi.android.tv.metadata;
import com.fongmi.android.tv.App; import com.github.catvod.utils.Prefers;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest;
public class MetadataRepository {
 private static final long DISK_TTL=30L*24*60*60*1000;
 private static final MetadataRepository repo=new MetadataRepository(); public static MetadataRepository get(){return repo;}
""" + gate + fingerprint + fields + "}"
        harness = r'''package com.fongmi.android.tv.playback.vod;
import com.fongmi.android.tv.metadata.*; import com.github.catvod.utils.Prefers;
import com.fongmi.android.tv.App; import java.util.*;
public class CacheIdentityHarness {
 static int checks;
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static MovieIdentity identity(String name,String year){return new MovieIdentity("site","vod",name,year,"","电视剧","","");}
 static void cache(MovieIdentity i,boolean verified,boolean eligible,long time) throws Exception {
  MovieMetadata m=new MovieMetadata();m.setTitle("狂飙");m.setYear("2023");m.setExternalType("tv");m.setExternalId("12345");
  var f=MetadataRepository.class.getDeclaredMethod("identityFingerprint",MovieIdentity.class);f.setAccessible(true);
  Prefers.put(i.mappingKey(MetadataProvider.DOUBAN),App.gson().toJson(Map.of("metadata",m,"externalId","12345","fingerprint",f.invoke(null,i),"userVerified",verified,"resumeEligible",eligible,"updatedAt",time)));
 }
 public static void main(String[] args) throws Exception {
  var repo=MetadataRepository.get();var full=identity("狂飙","2023");var sparse=identity("狂飙","");long now=System.currentTimeMillis();
  check(repo.getPlaybackIdentity(full)==null,"no confirmed cache must not trigger network lookup");
  cache(full,false,false,now);check(repo.getPlaybackIdentity(full)!=null,"legacy full evidence can be revalidated");
  check(repo.getPlaybackIdentity(sparse)==null,"legacy title-only match cannot be promoted to resume identity");
  cache(full,false,true,now);check(repo.getPlaybackIdentity(sparse)!=null,"new cache preserves evidence from full detail");
  check(repo.getPlaybackIdentity(identity("狂飙","2024"))==null,"cached eligibility cannot bypass a new year conflict");
  check(repo.getPlaybackIdentity(identity("狂飙第二季","2023"))==null,"cache is bound to current source title/season");
  cache(full,true,false,now);check(repo.getPlaybackIdentity(sparse).userVerified(),"manual confirmation retained as provenance");
  check(repo.getPlaybackIdentity(identity("狂飙","2024"))==null,"manual confirmation cannot bypass current hard conflict");
  cache(full,true,true,now-31L*24*60*60*1000);check(repo.getPlaybackIdentity(full)==null,"expired mapping must not silently identify playback");
  Prefers.put(full.mappingKey(MetadataProvider.DOUBAN),"bad json");check(repo.getPlaybackIdentity(full)==null,"corrupt cache degrades safely");
  check(Prefers.getString(full.mappingKey(MetadataProvider.DOUBAN)).equals("bad json"),"lookup never rewrites display metadata cache");
  System.out.println("PASS production metadata cache gate: "+checks+" checks");
 }
}'''
        self.run_harness(harness, "CacheIdentityHarness", repository)

    def run_harness(self, harness, name, repository=None):
        gson = next((Path.home()/".gradle/caches/modules-2/files-2.1/com.google.code.gson/gson").rglob("*.jar"))
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            sources = dict(STUBS)
            if repository is not None:
                sources[P+"metadata/MetadataRepository.java"] = repository
            util = (MAIN/P/"utils/Util.java").read_text()
            number = util.split("    public static int getNumber(String text) {", 1)[1].split("    public static String clean", 1)[0]
            pattern = next(line.strip() for line in util.splitlines() if "Pattern EPISODE =" in line)
            sources[P+"utils/Util.java"] = "package com.fongmi.android.tv.utils; import java.util.regex.*; public class Util {"+pattern+" public static int getNumber(String text) {"+number+"}"
            sources[P+"playback/vod/"+name+".java"] = harness
            for path, code in sources.items():
                dest=root/path;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(code)
            production = [P+"bean/History.java",P+"bean/Episode.java",P+"bean/Flag.java",P+"impl/Diffable.java"]
            production += [P+"metadata/"+name+".java" for name in ("MovieIdentity","MetadataMatcher")]
            production += [P+"playback/vod/"+name+".java" for name in ("VodResumeMatcher","VodResumeStore","VodHistoryPolicy","VodResumeGuard")]
            cmd=["javac","-encoding","UTF-8","-cp",str(gson),"-d",str(root)]+[str(root/p) for p in sources]+[str(MAIN/p) for p in production]
            result=subprocess.run(cmd,capture_output=True,text=True)
            self.assertEqual(0,result.returncode,result.stderr)
            result=subprocess.run(["java","-cp",str(root)+":"+str(gson),"com.fongmi.android.tv.playback.vod."+name],capture_output=True,text=True)
            self.assertEqual(0,result.returncode,result.stdout+result.stderr)
            print(result.stdout.strip())

    def test_controller_and_schema_contract(self):
        controller=(MAIN/P/"playback/vod/VodPlaybackController.java").read_text()
        self.assertIn("sourceSnapshot = state.getHistory().copy()",controller)
        self.assertIn("state.getHistory() != history || host.isHostFinishing()",controller)
        self.assertIn("state.getHistory().getPosition() < 0",controller)
        self.assertNotIn("history.setVodRemarks(episode.getName());\n        selectEpisode",controller)
        self.assertIn("VERSION = 35",(MAIN/P/"db/AppDatabase.java").read_text())
        for flavor in ("leanback","mobile"):
            code=(ROOT/f"app/src/{flavor}/java"/P/"ui/activity/VideoActivity.java").read_text()
            self.assertIn("boolean owner = isActivePlaybackOwner();",code)
            self.assertIn("player().isVod() && !isIdle() && !isBuffering()",code)
            self.assertIn("VodResumeDialog.show(this",code)

if __name__ == "__main__":
    unittest.main()
