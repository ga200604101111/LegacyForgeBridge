package dev.yinghuang.legacyforgebridge.convert;

import dev.yinghuang.legacyforgebridge.behavior.LegacyBehaviorApi;
import dev.yinghuang.legacyforgebridge.behavior.LegacyBehaviorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBehaviorRenderSpecialsCompilerTest {
    @TempDir Path temp;

    @Test void minimalSelfRegisteredClientListenerCompilesRenderSpecialsCancellation() throws Exception {
        Path source=temp.resolve("foreign-render-specials.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(source))){
            out.putNextEntry(new JarEntry("foreign/render/NameTagListener.class"));out.write(listener());out.closeEntry();
        }
        var result=new LegacyBehaviorCompiler().compile(source,"render_fixture","generated/RenderBootstrap",Map.of());
        assertEquals(1,result.events().size(),String.join("\n",result.diagnostics()));
        assertEquals("renderSpecialsPre",result.events().getFirst().kind());
        byte[] generated=result.classes().get("generated/RenderBootstrapSource/foreign/render/NameTagListener.class");
        assertNotNull(generated);
        String pool=new String(generated,StandardCharsets.ISO_8859_1);
        assertFalse(pool.contains("MinecraftForge"));assertFalse(pool.contains("EventBus"));

        ClassLoader loader=new ClassLoader(getClass().getClassLoader()){
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException{
                byte[] bytes=result.classes().get(name.replace('.','/')+".class");if(bytes==null)throw new ClassNotFoundException(name);return defineClass(name,bytes,0,bytes.length);
            }
        };
        Class.forName("generated.RenderBootstrap",true,loader).getMethod("initialize").invoke(null);
        try{
            var program=LegacyBehaviorRegistry.events("renderSpecialsPre").stream().filter(e->e.mod().equals("render_fixture")).findFirst().orElseThrow();
            var blank=new LegacyBehaviorApi.Player();blank.displayName="";var hidden=new LegacyBehaviorApi.Event();hidden.entityLiving=blank;
            LegacyBehaviorApi.begin("render_fixture",(key,args)->key);try{program.program().run(hidden);}finally{LegacyBehaviorApi.end();}
            assertTrue(hidden.isCanceled());
            var named=new LegacyBehaviorApi.Player();named.displayName="Alice";var visible=new LegacyBehaviorApi.Event();visible.entityLiving=named;
            LegacyBehaviorApi.begin("render_fixture",(key,args)->key);try{program.program().run(visible);}finally{LegacyBehaviorApi.end();}
            assertFalse(visible.isCanceled());
            var mob=new LegacyBehaviorApi.Living();var ordinary=new LegacyBehaviorApi.Event();ordinary.entityLiving=mob;
            LegacyBehaviorApi.begin("render_fixture",(key,args)->key);try{program.program().run(ordinary);}finally{LegacyBehaviorApi.end();}
            assertFalse(ordinary.isCanceled());
        }finally{LegacyBehaviorRegistry.removeMod("render_fixture");}
    }

    private static byte[] listener(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);String owner="foreign/render/NameTagListener";
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,owner,null,"java/lang/Object",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);c.visitFieldInsn(Opcodes.GETSTATIC,"net/minecraftforge/common/MinecraftForge","EVENT_BUS","Lcpw/mods/fml/common/eventhandler/EventBus;");c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"cpw/mods/fml/common/eventhandler/EventBus","register","(Ljava/lang/Object;)V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(2,1);c.visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"onSpecial","(Lnet/minecraftforge/client/event/RenderLivingEvent$Specials$Pre;)V",null,null);
        m.visitAnnotation("Lcpw/mods/fml/common/eventhandler/SubscribeEvent;",true).visitEnd();AnnotationVisitor side=m.visitAnnotation("Lcpw/mods/fml/relauncher/SideOnly;",true);side.visitEnum("value","Lcpw/mods/fml/relauncher/Side;","CLIENT");side.visitEnd();
        m.visitCode();Label done=new Label();m.visitVarInsn(Opcodes.ALOAD,1);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/client/event/RenderLivingEvent$Specials$Pre","entity","Lnet/minecraft/entity/EntityLivingBase;");m.visitTypeInsn(Opcodes.INSTANCEOF,"net/minecraft/entity/player/EntityPlayer");m.visitJumpInsn(Opcodes.IFEQ,done);m.visitVarInsn(Opcodes.ALOAD,1);m.visitVarInsn(Opcodes.ALOAD,1);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/client/event/RenderLivingEvent$Specials$Pre","entity","Lnet/minecraft/entity/EntityLivingBase;");m.visitTypeInsn(Opcodes.CHECKCAST,"net/minecraft/entity/player/EntityPlayer");m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/entity/player/EntityPlayer","getDisplayName","()Ljava/lang/String;",false);m.visitLdcInsn("");m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraftforge/client/event/RenderLivingEvent$Specials$Pre","setCanceled","(Z)V",false);m.visitLabel(done);m.visitInsn(Opcodes.RETURN);m.visitMaxs(3,2);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
}
