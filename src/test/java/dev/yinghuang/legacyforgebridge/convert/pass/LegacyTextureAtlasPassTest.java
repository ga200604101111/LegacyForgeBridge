package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyTextureAtlasPassTest {
 @TempDir Path temp;
 private ConversionContext context(Path root)throws Exception{return new ConversionContext(temp.resolve("source.jar"),root,temp.resolve("out.jar"),"test-sha",0,new LegacyModMetadata("source.jar","test",List.of(new LegacyModMetadata.ModEntry("Example","Example","1","1.7.10",List.of()))),new LegacyJarAnalyzer.Analysis("source.jar",0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of()),new DiagnosticCollector(),"test");}
 private void put(String name,String value)throws Exception{Path p=temp.resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,value,StandardCharsets.UTF_8);}
 private JsonObject read(String name)throws Exception{return JsonParser.parseString(Files.readString(temp.resolve(name),StandardCharsets.UTF_8)).getAsJsonObject();}
 private String texture(String model)throws Exception{return read(model).getAsJsonObject("textures").get("all").getAsString();}
 @Test void rewritesModelAndMaterializesPngAndAnimationInsideCandidate()throws Exception{
  put("assets/source/textures/blocks/Tile.png","image-bytes");put("assets/source/textures/blocks/Tile.png.mcmeta","{\"animation\":{\"frametime\":3}}");
  String model="assets/example/models/block/tile.json";put(model,"{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"source:blocks/Tile\"}}");
  new LegacyTextureAtlasPass().apply(context(temp));String sprite=texture(model);
  assertTrue(sprite.startsWith("example:block/lfb_legacy/"));Path output=temp.resolve("assets/example/textures/"+sprite.split(":",2)[1]+".png");
  assertEquals("image-bytes",Files.readString(output));assertEquals("{\"animation\":{\"frametime\":3}}",Files.readString(output.resolveSibling(output.getFileName()+".mcmeta")));
  assertTrue(Files.isRegularFile(temp.resolve("assets/source/textures/blocks/Tile.png")));
  assertFalse(read(LegacyTextureAtlasPass.OUTPUT).get("requiresExternalRepairMod").getAsBoolean());
 }
 @Test void separatesItemAndBlockAtlasesIncludingUpperCaseFolders()throws Exception{
  put("assets/source/textures/Items/Icon.png","i");put("assets/source/textures/blocks/brick.png","b");
  put("assets/example/models/item/icon.json","{\"textures\":{\"all\":\"source:Items/Icon\"}}");put("assets/example/models/block/brick.json","{\"textures\":{\"all\":\"source:blocks/brick\"}}");
  new LegacyTextureAtlasPass().apply(context(temp));
  assertEquals(1,read("assets/minecraft/atlases/items.json").getAsJsonArray("sources").size());
  assertEquals(1,read("assets/minecraft/atlases/blocks.json").getAsJsonArray("sources").size());
  assertTrue(texture("assets/example/models/item/icon.json").startsWith("example:item/"));
 }
 @Test void preservesExistingAtlasSourcesAndNeverAddsTheSameSpriteTwice()throws Exception{
  put("assets/minecraft/atlases/blocks.json","{\"sources\":[{\"type\":\"minecraft:single\",\"resource\":\"example:block/existing\"}]}");
  put("assets/source/textures/blocks/shared.png","bytes");
  for(String name:List.of("a","b"))put("assets/example/models/block/"+name+".json","{\"textures\":{\"all\":\"source:blocks/shared\"}}");
  var pass=new LegacyTextureAtlasPass();pass.apply(context(temp));byte[] atlas=Files.readAllBytes(temp.resolve("assets/minecraft/atlases/blocks.json"));pass.apply(context(temp));
  assertArrayEquals(atlas,Files.readAllBytes(temp.resolve("assets/minecraft/atlases/blocks.json")));assertEquals(2,read("assets/minecraft/atlases/blocks.json").getAsJsonArray("sources").size());
 }
 @Test void ambiguousCaseFoldIsNotGuessed()throws Exception{
  put("assets/source/textures/blocks/A.png","one");put("assets/source/textures/blocks/a.png","two");
  put("assets/example/models/block/t.json","{\"textures\":{\"all\":\"SOURCE:blocks/a\"}}");
  new LegacyTextureAtlasPass().apply(context(temp));assertEquals("SOURCE:blocks/a",texture("assets/example/models/block/t.json"));
  assertEquals(1,read(LegacyTextureAtlasPass.OUTPUT).getAsJsonArray("ambiguousSprites").size());
 }
 @Test void exactCaseReferenceWinsWithoutOverwritingDistinctSourceFiles()throws Exception{
  put("assets/source/textures/blocks/A.png","one");put("assets/source/textures/blocks/a.png","two");
  put("assets/example/models/block/t.json","{\"textures\":{\"all\":\"source:blocks/A\"}}");
  new LegacyTextureAtlasPass().apply(context(temp));assertEquals(0,read(LegacyTextureAtlasPass.OUTPUT).getAsJsonArray("ambiguousSprites").size());
  String sprite=texture("assets/example/models/block/t.json");assertEquals("one",Files.readString(temp.resolve("assets/example/textures/"+sprite.split(":",2)[1]+".png")));
 }
 @Test void modernReferencesAliasesAndVanillaParentsRemainUnchanged()throws Exception{
  put("assets/source/textures/block/a.png","a");String raw="{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"source:block/a\",\"particle\":\"#all\"}}";
  put("assets/example/models/block/t.json",raw);new LegacyTextureAtlasPass().apply(context(temp));assertEquals(raw,Files.readString(temp.resolve("assets/example/models/block/t.json")));
 }
 @Test void missingOwnedSpriteIsReportedNotReplacedWithAnotherImage()throws Exception{
  put("assets/source/textures/blocks/a.png","a");put("assets/example/models/block/t.json","{\"textures\":{\"all\":\"source:blocks/missing\"}}");
  new LegacyTextureAtlasPass().apply(context(temp));assertFalse(read(LegacyTextureAtlasPass.OUTPUT).get("referencedOwnedSpritesResolved").getAsBoolean());
  assertEquals("source:blocks/missing",texture("assets/example/models/block/t.json"));
 }
 @Test void mixedInheritedItemModelUsesOneAtlasWithDistinctSpriteIdentities()throws Exception{
  put("assets/source/textures/items/front.png","item");put("assets/source/textures/blocks/frame.png","block");
  put("assets/example/models/item/parent.json","{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"source:items/front\"}}");
  put("assets/example/models/item/mixed.json","{\"parent\":\"example:item/parent\",\"textures\":{\"layer1\":\"source:blocks/frame\"}}");
  new LegacyTextureAtlasPass().apply(context(temp));
  var tex=read("assets/example/models/item/mixed.json").getAsJsonObject("textures");
  assertTrue(tex.get("layer0").getAsString().startsWith("example:block/"));assertTrue(tex.get("layer1").getAsString().startsWith("example:block/"));
  assertTrue(read("assets/example/models/item/parent.json").getAsJsonObject("textures").get("layer0").getAsString().startsWith("example:item/"));
 }

}
