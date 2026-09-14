#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API = ROOT / "src/main/java/dev/longyu/legacyforgebridge/behavior/LegacyBehaviorApi.java"
COMPILER = ROOT / "src/main/java/dev/longyu/legacyforgebridge/convert/LegacyBehaviorCompiler.java"
PASS = ROOT / "src/main/java/dev/longyu/legacyforgebridge/convert/pass/LegacyBehaviorPass.java"
TEST = ROOT / "src/test/java/dev/longyu/legacyforgebridge/convert/pass/LegacyBehaviorItemBlockPassTest.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)

api = API.read_text(encoding="utf-8")
if "public static class ItemBlock extends Item" not in api:
    api = replace_once(
        api,
        "    public static class CreativeTab { }\n    public static class Item {\n",
        "    public static class CreativeTab { }\n"
        "    public static class Block {\n"
        "        public final String id;\n"
        "        public Block(String id) { this.id=Objects.requireNonNull(id); }\n"
        "    }\n"
        "    public static class Item {\n",
        "behavior Block adapter",
    )
    api = replace_once(
        api,
        "    }\n    public static class Sword extends Item {\n",
        "    }\n"
        "    public static class ItemBlock extends Item {\n"
        "        public final Block field_150939_a;\n"
        "        public ItemBlock(Block block) { field_150939_a=Objects.requireNonNull(block); }\n"
        "    }\n"
        "    public static class Sword extends Item {\n",
        "behavior ItemBlock adapter",
    )
    API.write_text(api, encoding="utf-8")

compiler = COMPILER.read_text(encoding="utf-8")
if 'Map.entry("net/minecraft/item/ItemBlock","ItemBlock")' not in compiler:
    compiler = replace_once(
        compiler,
        '            Map.entry("net/minecraft/item/ItemAxe","Axe"),Map.entry("net/minecraft/item/ItemSpade","Spade"),Map.entry("net/minecraft/item/ItemHoe","Hoe"),\n            Map.entry("net/minecraft/item/ItemStack","Stack"),\n',
        '            Map.entry("net/minecraft/item/ItemAxe","Axe"),Map.entry("net/minecraft/item/ItemSpade","Spade"),Map.entry("net/minecraft/item/ItemHoe","Hoe"),\n            Map.entry("net/minecraft/item/ItemBlock","ItemBlock"),Map.entry("net/minecraft/block/Block","Block"),\n            Map.entry("net/minecraft/item/ItemStack","Stack"),\n',
        "compiler Block/ItemBlock type mapping",
    )
if '"Lnet/minecraft/block/Block;".equals(arg.descriptor())' not in compiler:
    compiler = replace_once(
        compiler,
        'for(var arg:a.arguments()){if(arg.value()==null)m.visitInsn(Opcodes.ACONST_NULL);else m.visitLdcInsn(arg.value());}',
        'for(var arg:a.arguments()){if("Lnet/minecraft/block/Block;".equals(arg.descriptor())&&arg.value() instanceof String blockId){m.visitTypeInsn(Opcodes.NEW,API+"$Block");m.visitInsn(Opcodes.DUP);m.visitLdcInsn(blockId);m.visitMethodInsn(Opcodes.INVOKESPECIAL,API+"$Block","<init>","(Ljava/lang/String;)V",false);}else if(arg.value()==null)m.visitInsn(Opcodes.ACONST_NULL);else m.visitLdcInsn(arg.value());}',
        "bootstrap Block constructor argument",
    )
COMPILER.write_text(compiler, encoding="utf-8")

behavior_pass = PASS.read_text(encoding="utf-8")
if 'blocks=root.getAsJsonArray("blocks")' not in behavior_pass:
    behavior_pass = replace_once(
        behavior_pass,
        '        JsonArray items=root.getAsJsonArray("items");if(items==null||items.isEmpty())return;\n',
        '        JsonArray items=root.getAsJsonArray("items"),blocks=root.getAsJsonArray("blocks");if((items==null||items.isEmpty())&&(blocks==null||blocks.isEmpty()))return;\n',
        "behavior pass block manifest gate",
    )
    behavior_pass = replace_once(
        behavior_pass,
        '        for(JsonElement e:items){\n',
        '        if(items!=null)for(JsonElement e:items){\n',
        "behavior pass optional item loop",
    )
    behavior_pass = replace_once(
        behavior_pass,
        '        }\n        ambiguous.forEach(ids::remove);\n',
        '        }\n'
        '        if(blocks!=null)for(JsonElement e:blocks){\n'
        '            JsonObject block=e.getAsJsonObject();String id=block.get("id").getAsString();String path=id.substring(id.indexOf(\':\')+1);\n'
        '            String old=ids.putIfAbsent(path,id);if(old!=null&&!old.equals(id))ambiguous.add(path);ids.put(id,id);\n'
        '            String legacy=block.has("legacyRegistryName")?block.get("legacyRegistryName").getAsString():path;ids.putIfAbsent(legacy,id);\n'
        '            if(block.has("sourceItemBlockClass")){\n'
        '                proven.add(new LegacyItemRenderAnalyzer.ItemAllocation(legacy,block.get("sourceItemBlockClass").getAsString(),"(Lnet/minecraft/block/Block;)V",\n'
        '                        List.of(new LegacyItemRenderAnalyzer.ConstructorArgument("Lnet/minecraft/block/Block;",id)),false,false,false));\n'
        '            }\n'
        '        }\n'
        '        ambiguous.forEach(ids::remove);\n',
        "behavior pass custom ItemBlock allocations",
    )
    PASS.write_text(behavior_pass, encoding="utf-8")

if not TEST.exists():
    TEST.parent.mkdir(parents=True, exist_ok=True)
    TEST.write_text(r'''package dev.longyu.legacyforgebridge.convert.pass;

import dev.longyu.legacyforgebridge.behavior.LegacyBehaviorApi;
import dev.longyu.legacyforgebridge.behavior.LegacyBehaviorRegistry;
import dev.longyu.legacyforgebridge.convert.Hashing;
import dev.longyu.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.longyu.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.nio.file.*;
import java.util.jar.*;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBehaviorItemBlockPassTest {
    @TempDir Path temp;

    @Test void blockManifestBuildsSanitizedSourceItemBlockAndReusesItAsEventTarget() throws Exception {
        Path source=temp.resolve("foreign-block.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(source))){
            out.putNextEntry(new JarEntry("foreign/block/ListeningItemBlock.class"));
            out.write(sourceItemBlock());
            out.closeEntry();
        }
        var metadata=LegacyModMetadata.read(source);
        String mod=metadata.fabricId(),id=mod+":altar";
        Path staging=temp.resolve("staging");Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve("legacyforgebridge/converted-content.json"),
                "{\"items\":[],\"blocks\":[{\"id\":\""+id+"\",\"legacyRegistryName\":\"altar\",\"sourceItemBlockClass\":\"foreign/block/ListeningItemBlock\"}]}\n");
        var context=new ConversionContext(source,staging,temp.resolve("candidate.jar"),Hashing.sha256(source),Files.size(source),
                metadata,new LegacyJarAnalyzer().analyze(source),new DiagnosticCollector(),"fixture");
        new LegacyBehaviorPass().apply(context);
        String bootstrap=Files.readString(staging.resolve(LegacyBehaviorPass.MARKER)).trim();
        String report=Files.readString(staging.resolve("legacyforgebridge/behavior-analysis.json"));
        assertTrue(report.contains(id),report);
        assertTrue(report.contains("targetItemId"),report);

        ClassLoader loader=new ClassLoader(getClass().getClassLoader()){
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException{
                Path file=staging.resolve(name.replace('.','/')+".class");
                if(!Files.isRegularFile(file))throw new ClassNotFoundException(name);
                try{byte[] bytes=Files.readAllBytes(file);return defineClass(name,bytes,0,bytes.length);}
                catch(java.io.IOException e){throw new ClassNotFoundException(name,e);}
            }
        };
        Class.forName(bootstrap.replace('/','.'),true,loader).getMethod("initialize").invoke(null);
        try{
            var definition=LegacyBehaviorRegistry.item(id);assertNotNull(definition);
            assertInstanceOf(LegacyBehaviorApi.ItemBlock.class,definition.item());
            var item=(LegacyBehaviorApi.ItemBlock)definition.item();
            assertEquals(id,item.field_150939_a.id);
            assertEquals(1,item.maximumStackSize);
            var event=LegacyBehaviorRegistry.events("jump").stream().filter(e->e.mod().equals(mod)).findFirst().orElseThrow();
            var sourceEvent=new LegacyBehaviorApi.Event();event.program().run(sourceEvent);
        }finally{LegacyBehaviorRegistry.removeMod(mod);}
    }

    private static byte[] sourceItemBlock(){
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,"foreign/block/ListeningItemBlock",null,"net/minecraft/item/ItemBlock",null);
        MethodVisitor ctor=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Lnet/minecraft/block/Block;)V",null,null);ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitVarInsn(Opcodes.ALOAD,1);ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemBlock","<init>","(Lnet/minecraft/block/Block;)V",false);
        ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitInsn(Opcodes.ICONST_1);ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"foreign/block/ListeningItemBlock","func_77625_d","(I)Lnet/minecraft/item/Item;",false);ctor.visitInsn(Opcodes.POP);
        ctor.visitFieldInsn(Opcodes.GETSTATIC,"net/minecraftforge/common/MinecraftForge","EVENT_BUS","Lcpw/mods/fml/common/eventhandler/EventBus;");ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"cpw/mods/fml/common/eventhandler/EventBus","register","(Ljava/lang/Object;)V",false);ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(2,2);ctor.visitEnd();
        MethodVisitor jump=writer.visitMethod(Opcodes.ACC_PUBLIC,"onJump","(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingJumpEvent;)V",null,null);
        jump.visitAnnotation("Lcpw/mods/fml/common/eventhandler/SubscribeEvent;",true).visitEnd();jump.visitCode();jump.visitInsn(Opcodes.RETURN);jump.visitMaxs(0,2);jump.visitEnd();
        writer.visitEnd();return writer.toByteArray();
    }
}
''',encoding="utf-8")

print("Applied generic Block -> source ItemBlock behavior bridge")
