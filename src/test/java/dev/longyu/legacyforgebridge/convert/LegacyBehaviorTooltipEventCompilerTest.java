package dev.longyu.legacyforgebridge.convert;

import dev.longyu.legacyforgebridge.behavior.LegacyBehaviorApi;
import dev.longyu.legacyforgebridge.behavior.LegacyBehaviorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBehaviorTooltipEventCompilerTest {
    @TempDir Path temp;

    @Test void unsafeSelfRegisteredPickaxeGetsPresentationOnlyTargetAndRunsTooltipEvent() throws Exception {
        Path source=temp.resolve("foreign-tooltip.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(source))){
            write(out,"foreign/tooltip/NameBase.class",nameBase());
            write(out,"foreign/tooltip/NameA.class",nameA());
            write(out,"foreign/tooltip/Names.class",names());
            write(out,"foreign/tooltip/UnsafePickaxe.class",unsafePickaxe());
        }
        var allocation=new LegacyItemRenderAnalyzer.ItemAllocation("unsafe","foreign/tooltip/UnsafePickaxe","()V",List.of(),false,false,false);
        var result=new LegacyBehaviorCompiler().compile(source,"tooltip_fixture","generated/TooltipBootstrap",
                Map.of("unsafe","tooltip_fixture:unsafe"),List.of(allocation));
        assertTrue(result.items().isEmpty(),"unsafe constructor unexpectedly became a normal source item");
        assertEquals(1,result.events().size(),String.join("\n",result.diagnostics()));
        var eventBinding=result.events().getFirst();
        assertEquals("tooltipEvent",eventBinding.kind());
        assertEquals("tooltip_fixture:unsafe",eventBinding.targetItemId());
        byte[] generated=result.classes().get("generated/TooltipBootstrapSource/foreign/tooltip/UnsafePickaxe.class");
        assertNotNull(generated);
        assertFalse(new String(generated,StandardCharsets.ISO_8859_1).contains("ChestGenHooks"),"unsafe source constructor leaked into generated presentation target");

        ClassLoader loader=new ClassLoader(getClass().getClassLoader()){
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException{
                byte[] bytes=result.classes().get(name.replace('.','/')+".class");
                if(bytes==null)throw new ClassNotFoundException(name);
                return defineClass(name,bytes,0,bytes.length);
            }
        };
        Class.forName("generated.TooltipBootstrap",true,loader).getMethod("initialize").invoke(null);
        try{
            var definition=LegacyBehaviorRegistry.item("tooltip_fixture:unsafe");
            assertNotNull(definition);assertTrue(definition.presentationOnly());assertTrue(definition.hooks().isEmpty());
            var program=LegacyBehaviorRegistry.events("tooltipEvent").stream().filter(e->e.mod().equals("tooltip_fixture")).findFirst().orElseThrow();
            assertEquals("tooltip_fixture:unsafe",program.targetItemId());
            var root=new LegacyBehaviorApi.Tag();root.setInteger("toolLevel",3);
            var enchant=new LegacyBehaviorApi.Tag();enchant.setInteger("id",4);enchant.setInteger("lvl",2);
            root.values.put("spench",new LegacyBehaviorApi.TagList(List.of(enchant)));
            var sourceStack=new LegacyBehaviorApi.Stack(definition.item(),root);
            var lines=new ArrayList<>(List.of("Pickaxe","Stats"));
            var event=new LegacyBehaviorApi.Event();event.itemStack=sourceStack;event.toolTip=lines;
            LegacyBehaviorApi.begin("tooltip_fixture",(key,args)->key.endsWith("bambooEnch.echo")?"Echo":key.endsWith("enchantment.level.2")?" II":key);
            try{program.program().run(event);}finally{LegacyBehaviorApi.end();}
            assertEquals(List.of("Pickaxe Level:3","Echo II","Stats"),lines);
        }finally{LegacyBehaviorRegistry.removeMod("tooltip_fixture");}
    }

    private static void write(JarOutputStream out,String path,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(path));out.write(bytes);out.closeEntry();}

    private static byte[] nameBase(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);String owner="foreign/tooltip/NameBase";
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,owner,null,"java/lang/Object",null);w.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_FINAL,"name","Ljava/lang/String;",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(ILjava/lang/String;I)V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,2);c.visitFieldInsn(Opcodes.PUTFIELD,owner,"name","Ljava/lang/String;");c.visitFieldInsn(Opcodes.GETSTATIC,"foreign/tooltip/Names","TABLE","Ljava/util/HashMap;");c.visitVarInsn(Opcodes.ILOAD,1);c.visitInsn(Opcodes.I2S);c.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Short","valueOf","(S)Ljava/lang/Short;",false);c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/util/HashMap","put","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false);c.visitInsn(Opcodes.POP);c.visitInsn(Opcodes.RETURN);c.visitMaxs(3,4);c.visitEnd();
        MethodVisitor g=w.visitMethod(Opcodes.ACC_PUBLIC,"getName","()Ljava/lang/String;",null,null);g.visitCode();g.visitVarInsn(Opcodes.ALOAD,0);g.visitFieldInsn(Opcodes.GETFIELD,owner,"name","Ljava/lang/String;");g.visitInsn(Opcodes.ARETURN);g.visitMaxs(1,1);g.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static byte[] nameA(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,"foreign/tooltip/NameA",null,"foreign/tooltip/NameBase",null);MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(ILjava/lang/String;I)V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ILOAD,1);c.visitVarInsn(Opcodes.ALOAD,2);c.visitVarInsn(Opcodes.ILOAD,3);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/tooltip/NameBase","<init>","(ILjava/lang/String;I)V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(4,4);c.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static byte[] names(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);String owner="foreign/tooltip/Names";w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,owner,null,"java/lang/Object",null);w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"TABLE","Ljava/util/HashMap;",null,null).visitEnd();w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"A","Lforeign/tooltip/NameBase;",null,null).visitEnd();MethodVisitor s=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);s.visitCode();s.visitTypeInsn(Opcodes.NEW,"java/util/HashMap");s.visitInsn(Opcodes.DUP);s.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/util/HashMap","<init>","()V",false);s.visitFieldInsn(Opcodes.PUTSTATIC,owner,"TABLE","Ljava/util/HashMap;");s.visitTypeInsn(Opcodes.NEW,"foreign/tooltip/NameA");s.visitInsn(Opcodes.DUP);s.visitInsn(Opcodes.ICONST_4);s.visitLdcInsn("echo");s.visitInsn(Opcodes.ICONST_2);s.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/tooltip/NameA","<init>","(ILjava/lang/String;I)V",false);s.visitFieldInsn(Opcodes.PUTSTATIC,owner,"A","Lforeign/tooltip/NameBase;");s.visitInsn(Opcodes.RETURN);s.visitMaxs(5,0);s.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] unsafePickaxe(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);String owner="foreign/tooltip/UnsafePickaxe";w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,owner,null,"net/minecraft/item/ItemPickaxe",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitFieldInsn(Opcodes.GETSTATIC,"net/minecraft/item/Item$ToolMaterial","EMERALD","Lnet/minecraft/item/Item$ToolMaterial;");c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemPickaxe","<init>","(Lnet/minecraft/item/Item$ToolMaterial;)V",false);c.visitLdcInsn("villageBlacksmith");c.visitMethodInsn(Opcodes.INVOKESTATIC,"net/minecraftforge/common/ChestGenHooks","getInfo","(Ljava/lang/String;)Lnet/minecraftforge/common/ChestGenHooks;",false);c.visitInsn(Opcodes.POP);c.visitFieldInsn(Opcodes.GETSTATIC,"net/minecraftforge/common/MinecraftForge","EVENT_BUS","Lcpw/mods/fml/common/eventhandler/EventBus;");c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"cpw/mods/fml/common/eventhandler/EventBus","register","(Ljava/lang/Object;)V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(2,1);c.visitEnd();
        MethodVisitor init=w.visitMethod(Opcodes.ACC_PRIVATE,"initTagCompound","(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/nbt/NBTTagCompound;",null,null);init.visitCode();init.visitTypeInsn(Opcodes.NEW,"net/minecraft/nbt/NBTTagCompound");init.visitInsn(Opcodes.DUP);init.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/nbt/NBTTagCompound","<init>","()V",false);init.visitVarInsn(Opcodes.ASTORE,2);init.visitVarInsn(Opcodes.ALOAD,2);init.visitLdcInsn("toolLevel");init.visitInsn(Opcodes.ICONST_0);init.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagCompound","func_74768_a","(Ljava/lang/String;I)V",false);init.visitVarInsn(Opcodes.ALOAD,1);init.visitVarInsn(Opcodes.ALOAD,2);init.visitFieldInsn(Opcodes.PUTFIELD,"net/minecraft/item/ItemStack","field_77990_d","Lnet/minecraft/nbt/NBTTagCompound;");init.visitVarInsn(Opcodes.ALOAD,2);init.visitInsn(Opcodes.ARETURN);init.visitMaxs(3,3);init.visitEnd();
        MethodVisitor level=w.visitMethod(Opcodes.ACC_PRIVATE,"getLevel","(Lnet/minecraft/item/ItemStack;)I",null,null);level.visitCode();Label have=new Label();level.visitVarInsn(Opcodes.ALOAD,1);level.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/item/ItemStack","field_77990_d","Lnet/minecraft/nbt/NBTTagCompound;");level.visitJumpInsn(Opcodes.IFNONNULL,have);level.visitVarInsn(Opcodes.ALOAD,0);level.visitVarInsn(Opcodes.ALOAD,1);level.visitMethodInsn(Opcodes.INVOKEVIRTUAL,owner,"initTagCompound","(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/nbt/NBTTagCompound;",false);level.visitInsn(Opcodes.POP);level.visitLabel(have);level.visitVarInsn(Opcodes.ALOAD,1);level.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/item/ItemStack","field_77990_d","Lnet/minecraft/nbt/NBTTagCompound;");level.visitLdcInsn("toolLevel");level.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagCompound","func_74762_e","(Ljava/lang/String;)I",false);level.visitInsn(Opcodes.IRETURN);level.visitMaxs(2,2);level.visitEnd();
        MethodVisitor t=w.visitMethod(Opcodes.ACC_PUBLIC,"onItemTooltip","(Lnet/minecraftforge/event/entity/player/ItemTooltipEvent;)V",null,null);t.visitAnnotation("Lcpw/mods/fml/common/eventhandler/SubscribeEvent;",true).visitEnd();t.visitCode();Label done=new Label(),noEnchant=new Label();t.visitVarInsn(Opcodes.ALOAD,1);t.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/player/ItemTooltipEvent","itemStack","Lnet/minecraft/item/ItemStack;");t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_77973_b","()Lnet/minecraft/item/Item;",false);t.visitVarInsn(Opcodes.ALOAD,0);t.visitJumpInsn(Opcodes.IF_ACMPNE,done);
        t.visitVarInsn(Opcodes.ALOAD,1);t.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/player/ItemTooltipEvent","toolTip","Ljava/util/List;");t.visitInsn(Opcodes.ICONST_0);t.visitTypeInsn(Opcodes.NEW,"java/lang/StringBuilder");t.visitInsn(Opcodes.DUP);t.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/StringBuilder","<init>","()V",false);t.visitVarInsn(Opcodes.ALOAD,1);t.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/player/ItemTooltipEvent","toolTip","Ljava/util/List;");t.visitInsn(Opcodes.ICONST_0);t.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/List","get","(I)Ljava/lang/Object;",true);t.visitTypeInsn(Opcodes.CHECKCAST,"java/lang/String");t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","append","(Ljava/lang/String;)Ljava/lang/StringBuilder;",false);t.visitLdcInsn(" Level:");t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","append","(Ljava/lang/String;)Ljava/lang/StringBuilder;",false);t.visitVarInsn(Opcodes.ALOAD,0);t.visitVarInsn(Opcodes.ALOAD,1);t.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/player/ItemTooltipEvent","itemStack","Lnet/minecraft/item/ItemStack;");t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,owner,"getLevel","(Lnet/minecraft/item/ItemStack;)I",false);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","append","(I)Ljava/lang/StringBuilder;",false);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","toString","()Ljava/lang/String;",false);t.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/List","set","(ILjava/lang/Object;)Ljava/lang/Object;",true);t.visitInsn(Opcodes.POP);
        t.visitVarInsn(Opcodes.ALOAD,1);t.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/player/ItemTooltipEvent","itemStack","Lnet/minecraft/item/ItemStack;");t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_77942_o","()Z",false);t.visitJumpInsn(Opcodes.IFEQ,noEnchant);t.visitVarInsn(Opcodes.ALOAD,1);t.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/player/ItemTooltipEvent","itemStack","Lnet/minecraft/item/ItemStack;");t.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/item/ItemStack","field_77990_d","Lnet/minecraft/nbt/NBTTagCompound;");t.visitLdcInsn("spench");t.visitIntInsn(Opcodes.BIPUSH,10);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagCompound","func_150295_c","(Ljava/lang/String;I)Lnet/minecraft/nbt/NBTTagList;",false);t.visitVarInsn(Opcodes.ASTORE,2);t.visitVarInsn(Opcodes.ALOAD,2);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagList","func_74745_c","()I",false);t.visitJumpInsn(Opcodes.IFEQ,noEnchant);t.visitVarInsn(Opcodes.ALOAD,2);t.visitInsn(Opcodes.ICONST_0);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagList","func_150305_b","(I)Lnet/minecraft/nbt/NBTTagCompound;",false);t.visitVarInsn(Opcodes.ASTORE,3);t.visitVarInsn(Opcodes.ALOAD,1);t.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/player/ItemTooltipEvent","toolTip","Ljava/util/List;");t.visitInsn(Opcodes.ICONST_1);t.visitTypeInsn(Opcodes.NEW,"java/lang/StringBuilder");t.visitInsn(Opcodes.DUP);t.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/StringBuilder","<init>","()V",false);t.visitLdcInsn("bambooEnch.");t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","append","(Ljava/lang/String;)Ljava/lang/StringBuilder;",false);t.visitFieldInsn(Opcodes.GETSTATIC,"foreign/tooltip/Names","TABLE","Ljava/util/HashMap;");t.visitVarInsn(Opcodes.ALOAD,3);t.visitLdcInsn("id");t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagCompound","func_74765_d","(Ljava/lang/String;)S",false);t.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Short","valueOf","(S)Ljava/lang/Short;",false);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/util/HashMap","get","(Ljava/lang/Object;)Ljava/lang/Object;",false);t.visitTypeInsn(Opcodes.CHECKCAST,"foreign/tooltip/NameBase");t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"foreign/tooltip/NameBase","getName","()Ljava/lang/String;",false);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","append","(Ljava/lang/String;)Ljava/lang/StringBuilder;",false);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","toString","()Ljava/lang/String;",false);t.visitMethodInsn(Opcodes.INVOKESTATIC,"net/minecraft/util/StatCollector","func_74838_a","(Ljava/lang/String;)Ljava/lang/String;",false);t.visitLdcInsn("enchantment.level.2");t.visitMethodInsn(Opcodes.INVOKESTATIC,"net/minecraft/util/StatCollector","func_74838_a","(Ljava/lang/String;)Ljava/lang/String;",false);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/String","concat","(Ljava/lang/String;)Ljava/lang/String;",false);t.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/List","add","(ILjava/lang/Object;)V",true);t.visitLabel(noEnchant);t.visitLabel(done);t.visitInsn(Opcodes.RETURN);t.visitMaxs(6,4);t.visitEnd();w.visitEnd();return w.toByteArray();
    }
}
