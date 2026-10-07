package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.nio.file.*;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyCloningRecipeAnalyzerTest {
    @TempDir Path tempDir;

    @Test void renamedCloneFamilyIsAdmittedButChangedOutputCountFailsClosed()throws Exception {
        Path jar=tempDir.resolve("clone-fixture.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"foreign/clone/Bootstrap.class",bootstrap());
            put(out,"foreign/clone/ReplicaRule.class",recipe("foreign/clone/ReplicaRule",false));
            put(out,"foreign/clone/BrokenRule.class",recipe("foreign/clone/BrokenRule",true));
        }
        var registry=new LegacyRegistryAnalyzer.Analysis(List.of(),List.of(
                new LegacyRegistryAnalyzer.FieldBinding("foreign/items/Items","FULL","Lnet/minecraft/item/Item;",
                        LegacyRegistryAnalyzer.Kind.ITEM,"filled","fixture","net/minecraft/item/Item"),
                new LegacyRegistryAnalyzer.FieldBinding("foreign/items/Items","BLANK","Lnet/minecraft/item/Item;",
                        LegacyRegistryAnalyzer.Kind.ITEM,"blank","fixture","net/minecraft/item/Item")
        ),List.of());

        var result=new LegacyCloningRecipeAnalyzer().analyze(jar,registry);
        assertEquals(1,result.rules().size(),String.join("\n",result.diagnostics()));
        var rule=result.rules().getFirst();
        assertEquals("filled",rule.fullItem().registryName());
        assertEquals("blank",rule.blankItem().registryName());
        assertEquals("fixture",rule.fullItem().legacyNamespace());
        assertTrue(rule.copyCustomName());
        assertEquals("foreign/clone/ReplicaRule",rule.sourceRecipeClass());
    }

    private static byte[] bootstrap(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/clone/Bootstrap",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"init","(Lcpw/mods/fml/common/event/FMLInitializationEvent;)V",null,null);
        m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true).visitEnd();m.visitCode();
        register(m,"foreign/clone/ReplicaRule");
        register(m,"foreign/clone/BrokenRule");
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(4,2);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static void register(MethodVisitor m,String type){
        m.visitTypeInsn(Opcodes.NEW,type);m.visitInsn(Opcodes.DUP);
        m.visitFieldInsn(Opcodes.GETSTATIC,"foreign/items/Items","FULL","Lnet/minecraft/item/Item;");
        m.visitFieldInsn(Opcodes.GETSTATIC,"foreign/items/Items","BLANK","Lnet/minecraft/item/Item;");
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,type,"<init>","(Lnet/minecraft/item/Item;Lnet/minecraft/item/Item;)V",false);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","addRecipe","(Lnet/minecraft/item/crafting/IRecipe;)V",false);
    }

    private static byte[] recipe(String owner,boolean brokenCount){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,owner,null,"java/lang/Object",new String[]{"net/minecraft/item/crafting/IRecipe"});
        w.visitField(Opcodes.ACC_PUBLIC,"alpha","Lnet/minecraft/item/Item;",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC,"beta","Lnet/minecraft/item/Item;",null,null).visitEnd();

        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Lnet/minecraft/item/Item;Lnet/minecraft/item/Item;)V",null,null);c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,1);c.visitFieldInsn(Opcodes.PUTFIELD,owner,"alpha","Lnet/minecraft/item/Item;");
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,2);c.visitFieldInsn(Opcodes.PUTFIELD,owner,"beta","Lnet/minecraft/item/Item;");
        c.visitInsn(Opcodes.RETURN);c.visitMaxs(2,3);c.visitEnd();

        matches(w,owner);assemble(w,owner,brokenCount);

        MethodVisitor size=w.visitMethod(Opcodes.ACC_PUBLIC,"q","()I",null,null);size.visitCode();size.visitIntInsn(Opcodes.BIPUSH,9);size.visitInsn(Opcodes.IRETURN);size.visitMaxs(1,1);size.visitEnd();
        MethodVisitor preview=w.visitMethod(Opcodes.ACC_PUBLIC,"r","()Lnet/minecraft/item/ItemStack;",null,null);preview.visitCode();preview.visitInsn(Opcodes.ACONST_NULL);preview.visitInsn(Opcodes.ARETURN);preview.visitMaxs(1,1);preview.visitEnd();
        w.visitEnd();return w.toByteArray();
    }

    private static void matches(ClassWriter w,String owner){
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"a","(Lnet/minecraft/inventory/InventoryCrafting;Lnet/minecraft/world/World;)Z",null,null);m.visitCode();
        Label loop=new Label(),next=new Label(),notFull=new Label(),takeFull=new Label(),after=new Label(),finish=new Label(),falseLabel=new Label();
        m.visitInsn(Opcodes.ICONST_0);m.visitVarInsn(Opcodes.ISTORE,3);
        m.visitInsn(Opcodes.ACONST_NULL);m.visitVarInsn(Opcodes.ASTORE,4);
        m.visitInsn(Opcodes.ICONST_0);m.visitVarInsn(Opcodes.ISTORE,5);
        m.visitLabel(loop);m.visitVarInsn(Opcodes.ILOAD,5);m.visitVarInsn(Opcodes.ALOAD,1);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/inventory/InventoryCrafting","func_70302_i_","()I",false);m.visitJumpInsn(Opcodes.IF_ICMPGE,finish);
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitVarInsn(Opcodes.ILOAD,5);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/inventory/InventoryCrafting","func_70301_a","(I)Lnet/minecraft/item/ItemStack;",false);m.visitVarInsn(Opcodes.ASTORE,6);
        m.visitVarInsn(Opcodes.ALOAD,6);m.visitJumpInsn(Opcodes.IFNULL,next);
        m.visitVarInsn(Opcodes.ALOAD,6);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_77973_b","()Lnet/minecraft/item/Item;",false);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,owner,"alpha","Lnet/minecraft/item/Item;");m.visitJumpInsn(Opcodes.IF_ACMPNE,notFull);
        m.visitVarInsn(Opcodes.ALOAD,4);m.visitJumpInsn(Opcodes.IFNULL,takeFull);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.IRETURN);
        m.visitLabel(takeFull);m.visitVarInsn(Opcodes.ALOAD,6);m.visitVarInsn(Opcodes.ASTORE,4);m.visitJumpInsn(Opcodes.GOTO,next);
        m.visitLabel(notFull);m.visitVarInsn(Opcodes.ALOAD,6);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_77973_b","()Lnet/minecraft/item/Item;",false);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,owner,"beta","Lnet/minecraft/item/Item;");m.visitJumpInsn(Opcodes.IF_ACMPEQ,after);
        m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.IRETURN);
        m.visitLabel(after);m.visitIincInsn(3,1);
        m.visitLabel(next);m.visitIincInsn(5,1);m.visitJumpInsn(Opcodes.GOTO,loop);
        m.visitLabel(finish);m.visitVarInsn(Opcodes.ALOAD,4);m.visitJumpInsn(Opcodes.IFNULL,falseLabel);m.visitVarInsn(Opcodes.ILOAD,3);m.visitJumpInsn(Opcodes.IFLE,falseLabel);
        m.visitInsn(Opcodes.ICONST_1);m.visitJumpInsn(Opcodes.GOTO,finish=new Label());m.visitLabel(falseLabel);m.visitInsn(Opcodes.ICONST_0);m.visitLabel(finish);m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(3,7);m.visitEnd();
    }

    private static void assemble(ClassWriter w,String owner,boolean brokenCount){
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"b","(Lnet/minecraft/inventory/InventoryCrafting;)Lnet/minecraft/item/ItemStack;",null,null);m.visitCode();
        Label loop=new Label(),next=new Label(),notFull=new Label(),takeFull=new Label(),blank=new Label(),finish=new Label(),fail=new Label(),noName=new Label();
        m.visitInsn(Opcodes.ICONST_0);m.visitVarInsn(Opcodes.ISTORE,2);m.visitInsn(Opcodes.ACONST_NULL);m.visitVarInsn(Opcodes.ASTORE,3);m.visitInsn(Opcodes.ICONST_0);m.visitVarInsn(Opcodes.ISTORE,4);
        m.visitLabel(loop);m.visitVarInsn(Opcodes.ILOAD,4);m.visitVarInsn(Opcodes.ALOAD,1);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/inventory/InventoryCrafting","func_70302_i_","()I",false);m.visitJumpInsn(Opcodes.IF_ICMPGE,finish);
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitVarInsn(Opcodes.ILOAD,4);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/inventory/InventoryCrafting","func_70301_a","(I)Lnet/minecraft/item/ItemStack;",false);m.visitVarInsn(Opcodes.ASTORE,5);
        m.visitVarInsn(Opcodes.ALOAD,5);m.visitJumpInsn(Opcodes.IFNULL,next);
        m.visitVarInsn(Opcodes.ALOAD,5);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_77973_b","()Lnet/minecraft/item/Item;",false);m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,owner,"alpha","Lnet/minecraft/item/Item;");m.visitJumpInsn(Opcodes.IF_ACMPNE,notFull);
        m.visitVarInsn(Opcodes.ALOAD,3);m.visitJumpInsn(Opcodes.IFNULL,takeFull);m.visitInsn(Opcodes.ACONST_NULL);m.visitInsn(Opcodes.ARETURN);
        m.visitLabel(takeFull);m.visitVarInsn(Opcodes.ALOAD,5);m.visitVarInsn(Opcodes.ASTORE,3);m.visitJumpInsn(Opcodes.GOTO,next);
        m.visitLabel(notFull);m.visitVarInsn(Opcodes.ALOAD,5);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_77973_b","()Lnet/minecraft/item/Item;",false);m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,owner,"beta","Lnet/minecraft/item/Item;");m.visitJumpInsn(Opcodes.IF_ACMPEQ,blank);m.visitInsn(Opcodes.ACONST_NULL);m.visitInsn(Opcodes.ARETURN);
        m.visitLabel(blank);m.visitIincInsn(2,1);m.visitLabel(next);m.visitIincInsn(4,1);m.visitJumpInsn(Opcodes.GOTO,loop);
        m.visitLabel(finish);m.visitVarInsn(Opcodes.ALOAD,3);m.visitJumpInsn(Opcodes.IFNULL,fail);m.visitVarInsn(Opcodes.ILOAD,2);m.visitInsn(Opcodes.ICONST_1);m.visitJumpInsn(Opcodes.IF_ICMPLT,fail);
        m.visitTypeInsn(Opcodes.NEW,"net/minecraft/item/ItemStack");m.visitInsn(Opcodes.DUP);m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,owner,"alpha","Lnet/minecraft/item/Item;");
        m.visitVarInsn(Opcodes.ILOAD,2);m.visitInsn(brokenCount?Opcodes.ICONST_2:Opcodes.ICONST_1);m.visitInsn(Opcodes.IADD);
        m.visitVarInsn(Opcodes.ALOAD,3);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_77960_j","()I",false);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemStack","<init>","(Lnet/minecraft/item/Item;II)V",false);m.visitVarInsn(Opcodes.ASTORE,4);
        m.visitVarInsn(Opcodes.ALOAD,3);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_82837_s","()Z",false);m.visitJumpInsn(Opcodes.IFEQ,noName);
        m.visitVarInsn(Opcodes.ALOAD,4);m.visitVarInsn(Opcodes.ALOAD,3);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_82833_r","()Ljava/lang/String;",false);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_151001_c","(Ljava/lang/String;)Lnet/minecraft/item/ItemStack;",false);m.visitInsn(Opcodes.POP);
        m.visitLabel(noName);m.visitVarInsn(Opcodes.ALOAD,4);m.visitInsn(Opcodes.ARETURN);
        m.visitLabel(fail);m.visitInsn(Opcodes.ACONST_NULL);m.visitInsn(Opcodes.ARETURN);m.visitMaxs(6,6);m.visitEnd();
    }

    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
