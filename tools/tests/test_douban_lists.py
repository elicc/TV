"""Execute the real Java list parser with Gson; only Android/Vod boundaries stubbed."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
PKG = "com/fongmi/android/tv/metadata"
HARNESS = r'''package com.fongmi.android.tv.metadata;
import com.google.gson.*;
import java.util.*;
public class ListHarness {
 static void check(boolean ok,String why){ if(!ok) throw new AssertionError(why); }
 static List<MovieMetadata> parse(String json,boolean related){
  return DoubanListParser.parse(JsonParser.parseString(json), related);
 }
 public static void main(String[] args) throws Exception {
  var items = parse("{\"subject_collection_items\":[{\"id\":\"1\",\"title\":\"First\",\"type\":\"movie\",\"rating\":{\"value\":6.1},\"cover\":{\"url\":\"https://example.org/a.jpg\"},\"info\":\"US / Drama / Director / Actor\"},{\"id\":\"2\",\"title\":\"Second\",\"type\":\"movie\",\"rating\":{\"value\":9.3}}]}",false);
  check(items.size()==2 && items.get(0).getExternalId().equals("1"),"provider order, not score order");
  check(items.get(0).getDirectors().equals("Director") && items.get(0).getActors().equals("Actor"),"info field positions");
  check(items.get(0).getPoster().contains("@Referer="),"image headers");
  items = parse("[{\"id\":\"3\",\"title\":\"TV\",\"type\":\"tv\",\"pic\":{\"large\":\"https://example.org/b.jpg\"},\"card_subtitle\":\"2025 / 中国大陆 / 剧情 悬疑 / 导演 / 演员\",\"rating\":null},{\"id\":\"4\",\"title\":\"No score\",\"type\":\"movie\"}]",true);
  check(items.size()==2 && items.get(0).getRating()==0,"null rating retained");
  check(items.get(0).getYear().equals("2025") && items.get(0).getType().equals("剧情 悬疑"),"subtitle fallback");
  check(items.get(0).getPoster().startsWith("https://example.org/b.jpg"),"pic fallback");
  check(parse("[]",true).isEmpty(),"valid empty");
  check(parse("{\"subject_collection_items\":[]}",false).isEmpty(),"empty collection");
  check(parse("[null,1,{\"id\":{},\"title\":\"bad\"},{\"id\":\"8\",\"title\":\"book\",\"type\":\"book\"}]",true).isEmpty(),"invalid records safely skipped");
  for(String bad:new String[]{"{}","{\"msg\":\"denied\"}","null"}) {
   try {parse(bad,true); throw new AssertionError("bad envelope accepted");} catch(IllegalArgumentException expected) {}
  }
  MovieMetadata confirmed=items.get(0);
  var providers=new EnumMap<MetadataProvider,MovieMetadata>(MetadataProvider.class);
  providers.put(MetadataProvider.DOUBAN,confirmed);
  var resolved=new MetadataState(null,new MovieMetadata(),providers,List.of(),MetadataProvider.SOURCE,MetadataState.Status.SUCCESS,"");
  check(RecommendationState.resolvedMovie(resolved)==confirmed,"confirmed provider survives Source selection");
  var pending=new MetadataState(null,new MovieMetadata(),new EnumMap<>(MetadataProvider.class),List.of(new MetadataCandidate(confirmed,0.7)),MetadataProvider.DOUBAN,MetadataState.Status.FALLBACK,"");
  check(RecommendationState.resolvedMovie(pending)==null,"selected candidate never triggers recommendations");
  check(RecommendationState.resolvedMovie(null)==null,"no metadata");
  check(RecommendationState.success(List.of()).getStatus()==RecommendationState.Status.EMPTY,"empty != error");
  check(RecommendationState.error().getStatus()==RecommendationState.Status.ERROR,"failure state");
  confirmed.setExternalType("book");
  check(RecommendationState.resolvedMovie(resolved)==null,"unsupported kind rejected");
  // Minimal field projections from five real API responses, when passed by runner.
  for(String file:args){String json=java.nio.file.Files.readString(java.nio.file.Path.of(file));
   var root=JsonParser.parseString(json); var actual=DoubanListParser.parse(root,root.isJsonArray());
   var raw=root.isJsonArray()?root.getAsJsonArray():root.getAsJsonObject().getAsJsonArray("subject_collection_items");
   check(actual.size()==raw.size(),"live fixture count");
   for(int i=0;i<actual.size();i++) check(actual.get(i).getExternalId().equals(raw.get(i).getAsJsonObject().get("id").getAsString()),"live fixture order");
  }
  System.out.println("PASS Douban parser behavior and live fixture order");
 }
}'''

class DoubanListsTest(unittest.TestCase):
 def test_java_parser(self):
  jars=list((Path.home()/".gradle/caches/modules-2/files-2.1/com.google.code.gson/gson").glob("*/*/*.jar"))
  self.assertTrue(jars,"Build once to populate the existing Gson dependency")
  with tempfile.TemporaryDirectory() as td:
   root=Path(td)
   files={"android/text/TextUtils.java":"package android.text; public class TextUtils { public static boolean isEmpty(CharSequence s){return s==null||s.length()==0;} }",
    "com/fongmi/android/tv/bean/Vod.java":"package com.fongmi.android.tv.bean; public class Vod {"+"".join("public String "+x+"(){return \"\";}" for x in ["getName","getYear","getArea","getTypeName","getDirector","getActor","getContent","getPic"])+"}",
    PKG+"/ListHarness.java":HARNESS, PKG+"/MovieIdentity.java":"package com.fongmi.android.tv.metadata; public class MovieIdentity {}"}
   for name in ["DoubanListParser","MovieMetadata","MovieArtwork","MovieCast","MetadataProvider","RecommendationState","MetadataState","MetadataCandidate"]:
    files[PKG+"/"+name+".java"]=(ROOT/"app/src/main/java"/PKG/(name+".java")).read_text()
   for name,body in files.items():
    path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body)
   java_home=os.environ.get("JAVA_HOME")
   javac=str(Path(java_home)/"bin/javac") if java_home else "javac"
   java=str(Path(java_home)/"bin/java") if java_home else "java"
   subprocess.run([javac,"-cp",str(jars[0]),"-d",str(root/"out")]+[str(root/f) for f in files],check=True)
   fixtures=sorted((ROOT/"tools/tests/fixtures/douban_lists").glob("*.json"))
   subprocess.run([java,"-cp",str(root/"out")+os.pathsep+str(jars[0]),"com.fongmi.android.tv.metadata.ListHarness"]+[str(x) for x in fixtures],check=True)
