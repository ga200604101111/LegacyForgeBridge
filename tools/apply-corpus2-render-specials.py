#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
API=ROOT/"src/main/java/dev/longyu/legacyforgebridge/behavior/LegacyBehaviorApi.java"
COMPILER=ROOT/"src/main/java/dev/longyu/legacyforgebridge/convert/LegacyBehaviorCompiler.java"
RUNTIME=ROOT/"src/main/java/dev/longyu/legacyforgebridge/behavior/LegacyBehaviorRuntime.java"
MIXIN=ROOT/"src/main/java/dev/longyu/legacyforgebridge/mixin/client/LegacyNameTagBehaviorMixin.java"
CONFIG=ROOT/"src/main/resources/legacyforgebridge.client.mixins.json"
COMPILER_TEST=ROOT/"src/test/java/dev/longyu/legacyforgebridge/convert/LegacyBehaviorRenderSpecialsCompilerTest.java"
RUNTIME_TEST=ROOT/"src/test/java/dev/longyu/legacyforgebridge/behavior/LegacyBehaviorRenderSpecialsRuntimeTest.java"
MIXIN_TEST=ROOT/"src/test/java/dev/longyu/legacyforgebridge/mixin/client/LegacyNameTagBehaviorMixinBytecodeTest.java"

def replace_once(text,old,new,label):
    count=text.count(old)
    if count!=1: raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old,new,1)

api=API.read_text(encoding="utf-8")
if "public String displayName" not in api:
    api=replace_once(api,
'''    public static class Player extends Living {
        public Stack requestedUse;
        public int requestedDuration;
''',
'''    public static class Player extends Living {
        public Stack requestedUse;
        public int requestedDuration;
        public String displayName="";
        public String getDisplayName() { return displayName; }
''','legacy player display name')
    API.write_text(api,encoding="utf-8")

compiler=COMPILER.read_text(encoding="utf-8")
if 'RenderLivingEvent$Specials$Pre' not in compiler:
    compiler=replace_once(compiler,
'''            Map.entry("net/minecraftforge/event/entity/PlaySoundAtEntityEvent","Event"),
            Map.entry("net/minecraftforge/event/entity/player/ItemTooltipEvent","Event"));
''',
'''            Map.entry("net/minecraftforge/event/entity/PlaySoundAtEntityEvent","Event"),
            Map.entry("net/minecraftforge/event/entity/player/ItemTooltipEvent","Event"),
            Map.entry("net/minecraftforge/client/event/RenderLivingEvent$Specials$Pre","Event"));
''','RenderLiving Specials event type')
    compiler=replace_once(compiler,
'''                case "net/minecraftforge/event/entity/player/ItemTooltipEvent"->"tooltipEvent";
                default->null;
''',
'''                case "net/minecraftforge/event/entity/player/ItemTooltipEvent"->"tooltipEvent";
                case "net/minecraftforge/client/event/RenderLivingEvent$Specials$Pre"->"renderSpecialsPre";
                default->null;
''','RenderLiving Specials event kind')
    compiler=replace_once(compiler,
'''    private String mapped(String type){
''',
'''    private static String canonicalField(String owner,String name,String desc){
        if(owner.equals("net/minecraftforge/client/event/RenderLivingEvent$Specials$Pre")
                &&name.equals("entity")&&desc.equals("Lnet/minecraft/entity/EntityLivingBase;"))return "entityLiving";
        return name;
    }
    private String mapped(String type){
''','event field alias helper')
    compiler=replace_once(compiler,
'''                        super.visitFieldInsn(op,mapped(o),n,descriptor(d));
''',
'''                        super.visitFieldInsn(op,mapped(o),canonicalField(o,n,d),descriptor(d));
''','event field alias emission')
    COMPILER.write_text(compiler,encoding="utf-8")

runtime=RUNTIME.read_text(encoding="utf-8")
if "renderSpecialsPrePrograms" not in runtime:
    runtime=replace_once(runtime,
'''    public record UseOutcome(boolean handled, boolean sustained) { }
''',
'''    /** Client-presentation equivalent of Forge 1.7 RenderLivingEvent.Specials.Pre. */
    public static boolean renderSpecialsPre(LivingEntity entity) {
        if(entity==null)return false;
        Snapshot snapshot=new Snapshot(entity.level());
        return renderSpecialsPrePrograms(snapshot.living(entity));
    }
    static boolean renderSpecialsPrePrograms(LegacyBehaviorApi.Living source) {
        if(source==null)return false;
        for(var program:LegacyBehaviorRegistry.events("renderSpecialsPre")) {
            var event=new LegacyBehaviorApi.Event();event.entityLiving=source;
            boolean completed=invoke(program.mod(),"event/renderSpecialsPre",()->{program.program().run(event);return true;},false);
            if(!completed||event.entityLiving!=source)return false;
            if(event.isCanceled())return true;
        }
        return false;
    }

    public record UseOutcome(boolean handled, boolean sustained) { }
''','render specials runtime bridge')
    runtime=replace_once(runtime,
'''                actor.field_71071_by.lookup = index -> index < 36
                        ? stack(player.getInventory().getItem(index)) : view.equipment[index - 35];
''',
'''                actor.displayName=player.getDisplayName().getString();
                actor.field_71071_by.lookup = index -> index < 36
                        ? stack(player.getInventory().getItem(index)) : view.equipment[index - 35];
''','native player display name snapshot')
    RUNTIME.write_text(runtime,encoding="utf-8")

if not MIXIN.exists():
    MIXIN.parent.mkdir(parents=True,exist_ok=True)
    MIXIN.write_text(r'''package dev.longyu.legacyforgebridge.mixin.client;

import dev.longyu.legacyforgebridge.behavior.LegacyBehaviorRuntime;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class LegacyNameTagBehaviorMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",at=@At("TAIL"))
    private void legacyforgebridge$renderSpecialsPre(Entity entity,EntityRenderState state,float partialTicks,CallbackInfo ci){
        if(state.nameTag!=null&&entity instanceof LivingEntity living&&LegacyBehaviorRuntime.renderSpecialsPre(living)){
            state.nameTag=null;
            state.nameTagAttachment=null;
        }
    }
}
''',encoding="utf-8")

config=CONFIG.read_text(encoding="utf-8")
if '"LegacyNameTagBehaviorMixin"' not in config:
    config=replace_once(config,
'''    "LegacyHurtBehaviorMixin",
    "LegacyLocalPlayerSoundMixin"
''',
'''    "LegacyHurtBehaviorMixin",
    "LegacyLocalPlayerSoundMixin",
    "LegacyNameTagBehaviorMixin"
''','client mixin registration')
    CONFIG.write_text(config,encoding="utf-8")

if not COMPILER_TEST.exists():
    COMPILER_TEST.write_text(r'''package dev.longyu.legacyforgebridge.convert;

import dev.longyu.legacyforgebridge.behavior.LegacyBehaviorApi;
import dev.longyu.legacyforgebridge.behavior.LegacyBehaviorRegistry;
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
''',encoding="utf-8")

if not RUNTIME_TEST.exists():
    RUNTIME_TEST.write_text(r'''package dev.longyu.legacyforgebridge.behavior;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyBehaviorRenderSpecialsRuntimeTest {
    @Test void emptyPlayerDisplayNameCancelsWithoutMutatingSourceEntity() {
        String mod="render_runtime";
        try{
            assertTrue(LegacyBehaviorRegistry.begin(mod));
            LegacyBehaviorRegistry.registerEvent(mod,"renderSpecialsPre",event->{
                if(event.entityLiving instanceof LegacyBehaviorApi.Player player)event.setCanceled(player.getDisplayName().equals(""));
            });
            LegacyBehaviorRegistry.finish();
            var blank=new LegacyBehaviorApi.Player();blank.displayName="";assertTrue(LegacyBehaviorRuntime.renderSpecialsPrePrograms(blank));assertEquals("",blank.displayName);
            var named=new LegacyBehaviorApi.Player();named.displayName="Alice";assertFalse(LegacyBehaviorRuntime.renderSpecialsPrePrograms(named));assertEquals("Alice",named.displayName);
            assertFalse(LegacyBehaviorRuntime.renderSpecialsPrePrograms(new LegacyBehaviorApi.Living()));
        }finally{LegacyBehaviorRegistry.abort();LegacyBehaviorRegistry.removeMod(mod);}
    }
}
''',encoding="utf-8")

if not MIXIN_TEST.exists():
    MIXIN_TEST.write_text(r'''package dev.longyu.legacyforgebridge.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyNameTagBehaviorMixinBytecodeTest {
    @Test void exact12111ExtractRenderStateTargetExistsAndMixinClearsOnlyRenderStateNameTag() throws Exception {
        assertNotNull(EntityRenderer.class.getDeclaredMethod("extractRenderState",Entity.class,EntityRenderState.class,float.class));
        String resource="/"+LegacyNameTagBehaviorMixin.class.getName().replace('.','/')+".class";
        try(InputStream input=LegacyNameTagBehaviorMixin.class.getResourceAsStream(resource)){
            assertNotNull(input);byte[] bytes=input.readAllBytes();boolean[] runtimeCall={false},nameTagWrite={false};
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9){@Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                return new MethodVisitor(Opcodes.ASM9){@Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean itf){if(owner.endsWith("/LegacyBehaviorRuntime")&&name.equals("renderSpecialsPre"))runtimeCall[0]=true;}@Override public void visitFieldInsn(int opcode,String owner,String name,String descriptor){if(opcode==Opcodes.PUTFIELD&&owner.equals("net/minecraft/client/renderer/entity/state/EntityRenderState")&&name.equals("nameTag"))nameTagWrite[0]=true;}};
            }},ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);assertTrue(runtimeCall[0]);assertTrue(nameTagWrite[0]);
        }
    }
}
''',encoding="utf-8")

print("Applied generic RenderLiving Specials presentation bridge")
