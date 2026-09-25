package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.behavior.LegacyBehaviorApi;
import dev.yinghuang.legacyforgebridge.behavior.LegacyBehaviorRegistry;
import dev.yinghuang.legacyforgebridge.convert.Hashing;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
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
            var event=LegacyBehaviorRegistry.events("sound").stream().filter(e->e.mod().equals(mod)).findFirst().orElseThrow();
            var player=new LegacyBehaviorApi.Player();player.health=6F;
            player.equipment[4]=new LegacyBehaviorApi.Stack(item,null);
            var sourceEvent=new LegacyBehaviorApi.Event();sourceEvent.entity=player;sourceEvent.name="game.player.hurt";sourceEvent.volume=.7F;sourceEvent.pitch=1.1F;
            LegacyBehaviorApi.begin(mod,(key,args)->key);
            try{event.program().run(sourceEvent);}
            finally{LegacyBehaviorApi.end();}
            assertEquals("mob.villager.idle",sourceEvent.name);
        }finally{LegacyBehaviorRegistry.removeMod(mod);}
    }

    private static byte[] sourceItemBlock(){
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,"foreign/block/ListeningItemBlock",null,"net/minecraft/item/ItemBlock",null);
        MethodVisitor ctor=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Lnet/minecraft/block/Block;)V",null,null);ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitVarInsn(Opcodes.ALOAD,1);ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemBlock","<init>","(Lnet/minecraft/block/Block;)V",false);
        ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitInsn(Opcodes.ICONST_1);ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"foreign/block/ListeningItemBlock","func_77625_d","(I)Lnet/minecraft/item/Item;",false);ctor.visitInsn(Opcodes.POP);
        ctor.visitFieldInsn(Opcodes.GETSTATIC,"net/minecraftforge/common/MinecraftForge","EVENT_BUS","Lcpw/mods/fml/common/eventhandler/EventBus;");ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"cpw/mods/fml/common/eventhandler/EventBus","register","(Ljava/lang/Object;)V",false);ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(2,2);ctor.visitEnd();
        MethodVisitor sound=writer.visitMethod(Opcodes.ACC_PUBLIC,"onSound","(Lnet/minecraftforge/event/entity/PlaySoundAtEntityEvent;)V",null,null);
        sound.visitAnnotation("Lcpw/mods/fml/common/eventhandler/SubscribeEvent;",true).visitEnd();sound.visitCode();
        Label done=new Label(),hit=new Label();
        sound.visitVarInsn(Opcodes.ALOAD,1);sound.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/PlaySoundAtEntityEvent","entity","Lnet/minecraft/entity/Entity;");sound.visitTypeInsn(Opcodes.INSTANCEOF,"net/minecraft/entity/player/EntityPlayer");sound.visitJumpInsn(Opcodes.IFEQ,done);
        sound.visitVarInsn(Opcodes.ALOAD,1);sound.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/PlaySoundAtEntityEvent","entity","Lnet/minecraft/entity/Entity;");sound.visitTypeInsn(Opcodes.CHECKCAST,"net/minecraft/entity/player/EntityPlayer");sound.visitInsn(Opcodes.ICONST_4);sound.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/entity/player/EntityPlayer","func_71124_b","(I)Lnet/minecraft/item/ItemStack;",false);sound.visitVarInsn(Opcodes.ASTORE,2);
        sound.visitVarInsn(Opcodes.ALOAD,2);sound.visitJumpInsn(Opcodes.IFNULL,done);sound.visitVarInsn(Opcodes.ALOAD,2);sound.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_77973_b","()Lnet/minecraft/item/Item;",false);sound.visitVarInsn(Opcodes.ALOAD,0);sound.visitJumpInsn(Opcodes.IF_ACMPNE,done);
        sound.visitVarInsn(Opcodes.ALOAD,1);sound.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/PlaySoundAtEntityEvent","name","Ljava/lang/String;");sound.visitLdcInsn("game.player.hurt");sound.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false);sound.visitJumpInsn(Opcodes.IFEQ,done);
        sound.visitVarInsn(Opcodes.ALOAD,1);sound.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/PlaySoundAtEntityEvent","entity","Lnet/minecraft/entity/Entity;");sound.visitTypeInsn(Opcodes.CHECKCAST,"net/minecraft/entity/player/EntityPlayer");sound.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/entity/player/EntityPlayer","func_110143_aJ","()F",false);sound.visitLdcInsn(3F);sound.visitInsn(Opcodes.FREM);sound.visitInsn(Opcodes.FCONST_0);sound.visitInsn(Opcodes.FCMPG);sound.visitJumpInsn(Opcodes.IFNE,hit);
        sound.visitVarInsn(Opcodes.ALOAD,1);sound.visitLdcInsn("mob.villager.idle");sound.visitFieldInsn(Opcodes.PUTFIELD,"net/minecraftforge/event/entity/PlaySoundAtEntityEvent","name","Ljava/lang/String;");sound.visitJumpInsn(Opcodes.GOTO,done);
        sound.visitLabel(hit);sound.visitVarInsn(Opcodes.ALOAD,1);sound.visitLdcInsn("mob.villager.hit");sound.visitFieldInsn(Opcodes.PUTFIELD,"net/minecraftforge/event/entity/PlaySoundAtEntityEvent","name","Ljava/lang/String;");
        sound.visitLabel(done);sound.visitInsn(Opcodes.RETURN);sound.visitMaxs(0,0);sound.visitEnd();
        writer.visitEnd();return writer.toByteArray();
    }
}
