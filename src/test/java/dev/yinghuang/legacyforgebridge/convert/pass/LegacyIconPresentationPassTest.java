package dev.yinghuang.legacyforgebridge.convert.pass;
import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.*;
import dev.yinghuang.legacyforgebridge.convert.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyIconPresentationPassTest {
 @TempDir Path temp;
 @Test void sourceIconsBecomeNativeModelVariantsThenCandidateOwnedTextures()throws Exception{
  Path source=LegacyIconFixtures.create(temp.resolve("fixture"),Map.of()),staging=temp.resolve("staging");Files.createDirectories(staging);
  var context=new ConversionContext(source,staging,temp.resolve("output.jar"),"test-sha",Files.size(source),new LegacyModMetadata("source.jar","test",List.of(new LegacyModMetadata.ModEntry("Example","Example","1","1.7.10",List.of()))),new LegacyJarAnalyzer().analyze(source),new DiagnosticCollector(),"test");
  JsonObject content=new JsonObject();content.addProperty("namespace","example");JsonArray blocks=new JsonArray(),items=new JsonArray();
  for(var registration:new LegacyRegistryAnalyzer().analyze(source).registrations()){
   JsonObject def=new JsonObject();String id="example:"+registration.registryName().toLowerCase(Locale.ROOT);def.addProperty("id",id);def.addProperty("legacyRegistryName",registration.registryName());def.addProperty("sourceClass",registration.implementationClass());
   (registration.kind()==LegacyRegistryAnalyzer.Kind.BLOCK?blocks:items).add(def);
  }
  content.add("blocks",blocks);content.add("items",items);
  Path manifest=staging.resolve(LegacyClientContentBaselinePass.CONTENT);Files.createDirectories(manifest.getParent());Files.writeString(manifest,content.toString());
  for(String path:List.of("items/food0","items/food1","blocks/wood","blocks/tile")){Path p=staging.resolve("assets/source/textures/"+path+".png");Files.createDirectories(p.getParent());Files.write(p,new byte[]{1,2,3});}
  new LegacyClientContentBaselinePass().apply(context);new LegacyIconPresentationPass().apply(context);new LegacyTextureAtlasPass().apply(context);
  JsonObject report=JsonParser.parseString(Files.readString(staging.resolve(LegacyIconPresentationPass.OUTPUT))).getAsJsonObject();
  assertEquals(3,report.get("defaultPlaceholderModelsReplaced").getAsInt());assertEquals(288,report.get("metadataItemDefinitionsGenerated").getAsInt());
  assertTrue(report.getAsJsonObject("items").getAsJsonObject("example:unknownfoodname").has("255"));
  JsonObject state=JsonParser.parseString(Files.readString(staging.resolve("assets/example/blockstates/half.json"))).getAsJsonObject();assertEquals(16,state.getAsJsonObject("variants").size());
  String worldModel=state.getAsJsonObject("variants").getAsJsonObject("legacy_meta=8").get("model").getAsString();
  JsonObject world=JsonParser.parseString(Files.readString(staging.resolve("assets/example/models/"+worldModel.split(":",2)[1]+".json"))).getAsJsonObject();
  assertEquals(8d,world.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonArray("from").get(1).getAsDouble());
  JsonObject atlas=JsonParser.parseString(Files.readString(staging.resolve(LegacyTextureAtlasPass.OUTPUT))).getAsJsonObject();assertEquals(4,atlas.get("spritesMaterialized").getAsInt());
 }
}
