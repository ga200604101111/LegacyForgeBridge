package dev.longyu.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.jar.*;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBehaviorSelfRegisterEventTest {
    @TempDir Path tempDir;

    @Test void minimalSelfRegisterJumpHandlerUsesSynthesizedConstructor() throws Exception {
        Path jar=tempDir.resolve("self-register-event.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            out.putNextEntry(new JarEntry("foreign/events/SelfEvents.class"));
            out.write(handler());out.closeEntry();
        }

        var result=new LegacyBehaviorCompiler().compile(jar,"fixture","generated/SelfEventBootstrap",Map.of());
        assertEquals(1,result.events().size(),String.join("\n",result.diagnostics()));
        assertEquals("jump",result.events().getFirst().kind());
        assertEquals("foreign/events/SelfEvents",result.events().getFirst().owner());

        byte[] generated=result.classes().get("generated/SelfEventBootstrapSource/foreign/events/SelfEvents.class");
        assertNotNull(generated,result.classes().keySet().toString());
        String pool=new String(generated,StandardCharsets.ISO_8859_1);
        assertFalse(pool.contains("MinecraftForge"),"generated handler retained legacy Forge owner");
        assertFalse(pool.contains("EventBus"),"generated handler retained legacy event bus call");

        final int[] constructors={0};
        new ClassReader(generated).accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                if(name.equals("<init>")&&descriptor.equals("()V"))constructors[0]++;
                return null;
            }
        },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        assertEquals(1,constructors[0]);
    }

    private static byte[] handler(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,"foreign/events/SelfEvents",null,"java/lang/Object",null);

        MethodVisitor ctor=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD,0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
        ctor.visitFieldInsn(Opcodes.GETSTATIC,"net/minecraftforge/common/MinecraftForge","EVENT_BUS","Lcpw/mods/fml/common/eventhandler/EventBus;");
        ctor.visitVarInsn(Opcodes.ALOAD,0);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"cpw/mods/fml/common/eventhandler/EventBus","register","(Ljava/lang/Object;)V",false);
        ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(2,1);ctor.visitEnd();

        MethodVisitor jump=w.visitMethod(Opcodes.ACC_PUBLIC,"onJump","(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingJumpEvent;)V",null,null);
        AnnotationVisitor annotation=jump.visitAnnotation("Lcpw/mods/fml/common/eventhandler/SubscribeEvent;",true);annotation.visitEnd();
        jump.visitCode();jump.visitInsn(Opcodes.RETURN);jump.visitMaxs(0,2);jump.visitEnd();
        w.visitEnd();return w.toByteArray();
    }
}
