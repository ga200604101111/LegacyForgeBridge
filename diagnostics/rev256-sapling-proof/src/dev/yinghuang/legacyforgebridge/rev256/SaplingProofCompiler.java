package dev.yinghuang.legacyforgebridge.rev256;
import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.LegacyRegistryAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyIconTableAnalyzer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
/** Hooked before LegacyClientOnlySourceStripPass removes the input JVM classes. */
public final class SaplingProofCompiler {
 public static void capture(ConversionContext context)throws IOException {
  Path stage=context.stagingDir();Path manifest=stage.resolve("legacyforgebridge/converted-content.json");
  if(!Files.isRegularFile(manifest))return;
  try{
   JsonObject content=read(manifest);if(!content.has("sourceSha256")||!content.has("blocks"))return;
   String hash=content.get("sourceSha256").getAsString();
   if(!hash.equalsIgnoreCase(context.sourceHash()))throw new IOException("Sapling proof input identity mismatch");
   Properties p=SaplingProof.header(hash);int count=0;
   Map<String,JsonObject> definitions=new LinkedHashMap<>();Map<String,SaplingProof.Candidate> admitted=new LinkedHashMap<>();
   try(FileSystem fs=FileSystems.newFileSystem(context.sourceJar(),Map.of())){
    for(JsonElement e:content.getAsJsonArray("blocks")){
     if(!e.isJsonObject())continue;JsonObject b=e.getAsJsonObject();
     if(!b.has("id")||!b.has("sourceClass"))continue;
     String id=b.get("id").getAsString(),source=b.get("sourceClass").getAsString().replace('.','/');
     if(!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))continue;
     SaplingProof.Candidate c=SaplingProof.analyze(n->{Path path=fs.getPath("/"+n);return Files.isRegularFile(path)?Optional.of(path):Optional.empty();},source);
     if(c==null)continue;
     SaplingProof.add(p,count++,id,source,c);admitted.put(id,c);
     if(b.has("legacyRegistryName"))definitions.put(b.get("legacyRegistryName").getAsString(),b);
    }
   }
   p.setProperty("count",Integer.toString(count));
   // Persist before stripping. No executable original class is retained.
   SaplingProof.write(stage.resolve(SaplingProof.PATH),p);
   if(count>0)models(context,definitions,admitted);
  }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Unable to compile source sapling proof",e);}
 }
 /** Only the existing bounded source icon interpreter may authorize new cross resources.
  * Unsupported metadata or renderers keep their prior models; this is not a name-based guess. */
 private static void models(ConversionContext c,Map<String,JsonObject> definitions,Map<String,SaplingProof.Candidate> admitted)throws Exception{
  var registry=new LegacyRegistryAnalyzer().analyze(c.sourceJar());
  var icons=new LegacyIconTableAnalyzer().analyze(c.sourceJar(),registry.registrations());
  Path stage=c.stagingDir();
  for(var result:icons){
   if(!result.block()||!definitions.containsKey(result.registryName()))continue;
   JsonObject d=definitions.get(result.registryName());String id=d.get("id").getAsString();
   if(!admitted.get(id).cutout()||result.variants().size()!=16)continue;
   String[] split=id.split(":",2);String ns=split[0],name=split[1];
   Map<Integer,Path> textures=new TreeMap<>();boolean valid=true;
   for(var v:result.variants()){
    if(v.metadata()<0||v.metadata()>15||v.renderType()!=1||v.faceIcons().isEmpty()||new HashSet<>(v.faceIcons()).size()!=1||v.tints().stream().anyMatch(t->t!=0xFFFFFF)) {valid=false;break;}
    Path texture=texture(stage,v.faceIcons().getFirst());if(texture==null){valid=false;break;}textures.put(v.metadata(),texture);
   }
   if(!valid||textures.size()!=16)continue;
   JsonObject variants=new JsonObject(),itemRefs=new JsonObject();
   for(var entry:textures.entrySet()){
    int meta=entry.getKey();String path="lfb_saplings/"+name+"/"+meta;
    copySprite(entry.getValue(),stage.resolve("assets/"+ns+"/textures/block/"+path+".png"));
    copySprite(entry.getValue(),stage.resolve("assets/"+ns+"/textures/item/"+path+".png"));
    JsonObject world=model("minecraft:block/cross","cross",ns+":block/"+path);
    JsonObject item=model("minecraft:item/generated","layer0",ns+":item/"+path);
    write(stage.resolve("assets/"+ns+"/models/block/"+path+".json"),world);
    write(stage.resolve("assets/"+ns+"/models/item/"+path+".json"),item);
    JsonObject ref=new JsonObject();ref.addProperty("model",ns+":block/"+path);variants.add("legacy_meta="+meta,ref);
    JsonObject def=definition(ns+":item/"+path);write(stage.resolve("assets/"+ns+"/items/"+path+".json"),def);
    itemRefs.addProperty(Integer.toString(meta),ns+":"+path);
    if(meta==0){write(stage.resolve("assets/"+ns+"/models/block/"+name+".json"),world);write(stage.resolve("assets/"+ns+"/models/item/"+name+".json"),item);write(stage.resolve("assets/"+ns+"/items/"+name+".json"),def);}
   }
   JsonObject states=new JsonObject();states.add("variants",variants);write(stage.resolve("assets/"+ns+"/blockstates/"+name+".json"),states);
   Path iconFile=stage.resolve("legacyforgebridge/icon-presentation.json");
   if(Files.isRegularFile(iconFile)){JsonObject root=read(iconFile);if(root.has("items")){root.getAsJsonObject("items").add(id,itemRefs);write(iconFile,root);}}
  }
 }
 private static void copySprite(Path from,Path to)throws IOException{
  Files.createDirectories(to.getParent());Files.copy(from,to,StandardCopyOption.REPLACE_EXISTING);
  Path meta=from.resolveSibling(from.getFileName()+".mcmeta");if(Files.isRegularFile(meta))Files.copy(meta,to.resolveSibling(to.getFileName()+".mcmeta"),StandardCopyOption.REPLACE_EXISTING);
 }
 private static Path texture(Path stage,String icon){
  String raw=icon.contains(":")?icon:"minecraft:"+icon;String[] p=raw.split(":",2);
  if(p.length!=2||!p[0].matches("[a-z0-9_.-]+")||!p[1].matches("[a-zA-Z0-9/._-]+")||p[1].contains(".."))return null;
  String name=p[1];List<Path> found=new ArrayList<>();
  for(String prefix:List.of("","blocks/","block/")){Path f=stage.resolve("assets/"+p[0]+"/textures/"+prefix+name+".png");if(Files.isRegularFile(f))found.add(f);}
  return found.size()==1?found.getFirst():null;
 }
 private static JsonObject model(String parent,String key,String sprite){JsonObject m=new JsonObject(),t=new JsonObject();m.addProperty("parent",parent);t.addProperty(key,sprite);m.add("textures",t);return m;}
 private static JsonObject definition(String model){JsonObject r=new JsonObject(),m=new JsonObject();m.addProperty("type","minecraft:model");m.addProperty("model",model);r.add("model",m);return r;}
 private static JsonObject read(Path p)throws IOException{try(Reader r=Files.newBufferedReader(p,StandardCharsets.UTF_8)){return JsonParser.parseReader(r).getAsJsonObject();}}
 private static void write(Path p,JsonObject o)throws IOException{Files.createDirectories(p.getParent());Files.writeString(p,o.toString()+"\n",StandardCharsets.UTF_8);}
 private SaplingProofCompiler(){}
}
