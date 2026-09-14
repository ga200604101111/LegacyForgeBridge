package dev.longyu.legacyforgebridge.convert.pass;

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
            var sourceEvent=new LegacyBehaviorApi.Event();
            LegacyBehaviorApi.begin(mod,(key,args)->key);
            try{event.program().run(sourceEvent);}
            finally{LegacyBehaviorApi.end();}
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
