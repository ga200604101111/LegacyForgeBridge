package dev.yinghuang.legacyforgebridge.convert;
import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.compat.LegacyGeometrySpec;
import dev.yinghuang.legacyforgebridge.convert.pass.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyPresentationBatchIntegrationTest {
    @TempDir Path temp;
    private static void put(Path root,String name,String content)throws Exception{
        Path file=root.resolve(name);Files.createDirectories(file.getParent());Files.writeString(file,content);
    }
    private static JsonObject read(Path root,String name)throws Exception{
        return JsonParser.parseString(Files.readString(root.resolve(name))).getAsJsonObject();
    }
    @Test void finalAtlasResolvesEveryPullStageAndAllItemDefinitionAliases()throws Exception{
        Path jar=LegacyCombatItemAnalyzerTest.sourceFixture(temp);
        var context=LegacyPresentationBatchTestContext.create(jar,temp.resolve("staging"));Path stage=context.stagingDir();
        put(stage,LegacyClientContentBaselinePass.CONTENT,"""
            {"items":[{"id":"example:foreign_bow","sourceClass":"foreign/weapons/BowLike","legacyRegistryName":"foreign_bow"}],"blocks":[]}
            """);
        put(stage,"assets/foreign/textures/items/bow_base.png","base");
        for(int i=0;i<3;i++)put(stage,"assets/foreign/textures/items/bow_pull_"+i+".png","stage-"+i);
        put(stage,"assets/example/models/item/foreign_bow.json","""
            {"parent":"minecraft:item/generated","textures":{"layer0":"foreign:items/bow_base"}}
            """);
        String definition="""
            {"model":{"type":"minecraft:model","model":"example:item/foreign_bow"}}
            """;
        put(stage,"assets/example/items/foreign_bow.json",definition);
        put(stage,"assets/example/items/lfb_meta/foreign_bow/2.json",definition);
        // Reproduce the old timing: original sprites have already been processed when the
        // bow stages are generated. The final pass must include these late assets as well.
        new LegacyTextureAtlasPass().apply(context);
        new LegacyCombatItemPass().apply(context);
        new LegacyTextureAtlasPass().apply(context);
        Set<String> sprites=new HashSet<>();
        for(int i=0;i<3;i++){
            var model=read(stage,"assets/example/models/item/foreign_bow_pulling_"+i+".json");
            assertEquals("minecraft:item/bow",model.get("parent").getAsString());
            String sprite=model.getAsJsonObject("textures").get("layer0").getAsString();
            assertTrue(sprite.startsWith("example:item/lfb_legacy/"),sprite);assertTrue(sprites.add(sprite));
            assertEquals("stage-"+i,Files.readString(stage.resolve("assets/example/textures/"+sprite.split(":",2)[1]+".png")));
        }
        var atlas=read(stage,"assets/minecraft/atlases/items.json");
        String atlasJson=atlas.toString();for(String sprite:sprites)assertTrue(atlasJson.contains(sprite),atlasJson);
        for(String path:List.of("assets/example/items/foreign_bow.json","assets/example/items/lfb_meta/foreign_bow/2.json")){
            var condition=read(stage,path).getAsJsonObject("model");
            assertEquals("minecraft:using_item",condition.get("property").getAsString());
            assertEquals(3,condition.getAsJsonObject("on_true").getAsJsonArray("entries").size());
        }
        assertTrue(read(stage,LegacyTextureAtlasPass.OUTPUT).get("referencedOwnedSpritesResolved").getAsBoolean());
    }
    @Test void crossedAndCropModelsRetainTheirOwnMeshAndSourceCollisionBounds()throws Exception{
        Path jar=LegacySimpleBlockRendererAnalyzerTest.sourceFixture(temp);
        var context=LegacyPresentationBatchTestContext.create(jar,temp.resolve("staging"));Path stage=context.stagingDir();
        JsonObject content=new JsonObject();JsonArray blocks=new JsonArray();
        for(String name:List.of("cross","crop","meta","noise")){
            JsonObject block=new JsonObject();block.addProperty("id","example:"+name);block.addProperty("legacyRegistryName",name);blocks.add(block);
            put(stage,"assets/example/models/block/"+name+".json","""
                {"parent":"minecraft:block/cube_all","textures":{"all":"foreign:blocks/plant"}}
                """);
            JsonObject variants=new JsonObject();for(int m=0;m<16;m++){JsonObject v=new JsonObject();v.addProperty("model","example:block/"+name);variants.add("legacy_meta="+m,v);}
            JsonObject state=new JsonObject();state.add("variants",variants);put(stage,"assets/example/blockstates/"+name+".json",state.toString());
        }
        content.add("blocks",blocks);content.add("items",new JsonArray());put(stage,LegacyClientContentBaselinePass.CONTENT,content.toString());
        put(stage,"assets/foreign/textures/blocks/plant.png","plant-sprite");
        var pass=new LegacySimpleBlockPresentationPass();pass.apply(context);pass.apply(context);
        new LegacyTextureAtlasPass().apply(context);
        var geometry=LegacyGeometrySpec.parse(read(stage,LegacyGeometrySpec.PATH));
        for(String name:List.of("cross","crop")){
            var model=read(stage,"assets/example/models/block/"+name+".json");
            assertEquals("minecraft:block/"+name,model.get("parent").getAsString());
            assertTrue(model.getAsJsonObject("textures").get(name).getAsString().startsWith("example:block/lfb_legacy/"));
            var rule=geometry.get("example:"+name);assertNotNull(rule);assertTrue(rule.modelOwned());assertFalse(rule.opaque());
            for(int m=0;m<16;m++){assertEquals("empty",rule.variant(m).collision());assertEquals(.25D,rule.variant(m).bounds().x0());assertEquals(.75D,rule.variant(m).bounds().z1());}
        }
        assertEquals("minecraft:block/cube_all",read(stage,"assets/example/models/block/noise.json").get("parent").getAsString());
        assertEquals(3,read(stage,LegacySimpleBlockPresentationPass.OUTPUT).get("shapeRulesMerged").getAsInt());
    }
    @Test void productionPipelineFinalizesAtlasAfterAllLateModelWriters()throws Exception{
        List<String> created=new ArrayList<>();
        try(var input=LegacyConversionEngine.class.getResourceAsStream("/"+LegacyConversionEngine.class.getName().replace('.','/')+".class")){
            assertNotNull(input);new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex){
                    return new MethodVisitor(Opcodes.ASM9){@Override public void visitTypeInsn(int opcode,String type){if(opcode==Opcodes.NEW)created.add(type.substring(type.lastIndexOf('/')+1));}};
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        int atlas=created.indexOf("LegacyTextureAtlasPass");assertTrue(atlas>=0);
        for(String producer:List.of("LegacyBlockGeometryPass","LegacyLiquidPresentationPass","LegacySimpleBlockPresentationPass","LegacyConnectedCuboidPresentationPass","LegacyCombatItemPass")){
            assertTrue(created.indexOf(producer)>=0&&created.indexOf(producer)<atlas,producer+" must finish before final sprites");
        }
        assertEquals(1,Collections.frequency(created,"LegacyTextureAtlasPass"));
    }
}
