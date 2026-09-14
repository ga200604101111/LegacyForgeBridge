package dev.longyu.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.nio.file.*;
import java.util.jar.*;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEventAnalyzerTest {
    @TempDir Path tempDir;

    @Test void selfRegisteredForgeAndDirectFmlHandlersAreRecoveredAcrossUnrelatedNamespace() throws Exception {
        Path jar=tempDir.resolve("ForeignEvents.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"foreign/events/SelfListener.class",selfListener());
            put(out,"foreign/events/CraftedListener.class",craftedListener());
            put(out,"foreign/events/UnusedListener.class",unusedListener());
            put(out,"foreign/events/Bootstrap.class",bootstrap());
        }

        var analysis=new LegacyEventAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(),String.join("\n",analysis.diagnostics()));
        assertEquals(2,analysis.bindings().size());

        var tooltip=analysis.forEvent("net/minecraftforge/event/entity/player/ItemTooltipEvent");
        assertEquals(1,tooltip.size());
        assertEquals("foreign/events/SelfListener",tooltip.getFirst().handlerClass());
        assertEquals(LegacyEventAnalyzer.Bus.FORGE,tooltip.getFirst().bus());
        assertEquals(LegacyEventAnalyzer.Side.CLIENT,tooltip.getFirst().side());
        assertEquals("HIGH",tooltip.getFirst().priority());
        assertTrue(tooltip.getFirst().receiveCanceled());
        assertEquals("<init>",tooltip.getFirst().registrationMethod());

        var crafted=analysis.forEvent("cpw/mods/fml/common/gameevent/PlayerEvent$ItemCraftedEvent");
        assertEquals(1,crafted.size());
        assertEquals("foreign/events/CraftedListener",crafted.getFirst().handlerClass());
        assertEquals(LegacyEventAnalyzer.Bus.FML,crafted.getFirst().bus());
        assertEquals(LegacyEventAnalyzer.Side.COMMON,crafted.getFirst().side());
        assertEquals("preInit",crafted.getFirst().registrationMethod());

        assertTrue(analysis.bindings().stream().noneMatch(binding -> binding.handlerClass().contains("UnusedListener")));
    }

    private static byte[] selfListener(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/events/SelfListener",null,"java/lang/Object",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
        c.visitFieldInsn(Opcodes.GETSTATIC,"net/minecraftforge/common/MinecraftForge","EVENT_BUS","Lcpw/mods/fml/common/eventhandler/EventBus;");c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"cpw/mods/fml/common/eventhandler/EventBus","register","(Ljava/lang/Object;)V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(2,1);c.visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"tooltip","(Lnet/minecraftforge/event/entity/player/ItemTooltipEvent;)V",null,null);
        AnnotationVisitor sub=m.visitAnnotation("Lcpw/mods/fml/common/eventhandler/SubscribeEvent;",true);sub.visitEnum("priority","Lcpw/mods/fml/common/eventhandler/EventPriority;","HIGH");sub.visit("receiveCanceled",true);sub.visitEnd();
        AnnotationVisitor side=m.visitAnnotation("Lcpw/mods/fml/relauncher/SideOnly;",true);side.visitEnum("value","Lcpw/mods/fml/relauncher/Side;","CLIENT");side.visitEnd();m.visitCode();m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,2);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] craftedListener(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/events/CraftedListener",null,"java/lang/Object",null);
        constructor(w,"foreign/events/CraftedListener");
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"crafted","(Lcpw/mods/fml/common/gameevent/PlayerEvent$ItemCraftedEvent;)V",null,null);AnnotationVisitor sub=m.visitAnnotation("Lcpw/mods/fml/common/eventhandler/SubscribeEvent;",true);sub.visitEnd();m.visitCode();m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,2);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] unusedListener(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/events/UnusedListener",null,"java/lang/Object",null);constructor(w,"foreign/events/UnusedListener");
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"ignored","(Lnet/minecraftforge/event/entity/living/LivingDeathEvent;)V",null,null);AnnotationVisitor sub=m.visitAnnotation("Lcpw/mods/fml/common/eventhandler/SubscribeEvent;",true);sub.visitEnd();m.visitCode();m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,2);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] bootstrap(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/events/Bootstrap",null,"java/lang/Object",null);constructor(w,"foreign/events/Bootstrap");
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"preInit","()V",null,null);m.visitCode();
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/FMLCommonHandler","instance","()Lcpw/mods/fml/common/FMLCommonHandler;",false);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"cpw/mods/fml/common/FMLCommonHandler","bus","()Lcpw/mods/fml/common/eventhandler/EventBus;",false);
        m.visitTypeInsn(Opcodes.NEW,"foreign/events/CraftedListener");m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/events/CraftedListener","<init>","()V",false);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"cpw/mods/fml/common/eventhandler/EventBus","register","(Ljava/lang/Object;)V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(3,1);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static void constructor(ClassWriter w,String owner){MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(1,1);c.visitEnd();}
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
