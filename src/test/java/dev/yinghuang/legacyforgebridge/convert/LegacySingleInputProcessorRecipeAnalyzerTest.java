package dev.longyu.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySingleInputProcessorRecipeAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void sourceOwnedStaticRecipeApiUsesOnlyLifecycleReachableRegistrations() throws Exception {
        Path jar=tempDir.resolve("ForeignProcessorRecipes.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"x/y/Bootstrap.class",bootstrap());
            put(out,"x/y/Registrar.class",registrar());
            put(out,"x/y/Dormant.class",dormant());
            put(out,"x/y/RecipeBook.class",recipeBook());
        }
        var machine=new LegacySingleInputProcessorAnalyzer.Rule("machine",null,"x/y/Block","x/y/Tile","tile",3,64,0,
                List.of(1,2),List.of(0),List.of(2,1),List.of(0),400,64.0,7,
                "x/y/RecipeBook","lookup","(Lnet/minecraft/item/ItemStack;)Lx/y/Recipe;",true,true,false);
        var analysis=new LegacySingleInputProcessorRecipeAnalyzer().analyze(jar,machine);
        assertTrue(analysis.diagnostics().isEmpty(),analysis.diagnostics().toString());
        // Dormant.<clinit> also calls RecipeBook.register, but no lifecycle root reaches Dormant.
        assertEquals(1,analysis.recipes().size());
        var recipe=analysis.recipes().getFirst();
        assertEquals(0.25F,recipe.bonusChance());
        assertInstanceOf(LegacySingleInputProcessorRecipeAnalyzer.StackInput.class,recipe.input());
        var resolver=new LegacyRecipeValueResolver();
        var input=LegacyRecipeStackResolver.resolve(resolver.resolve(((LegacySingleInputProcessorRecipeAnalyzer.StackInput)recipe.input()).stack())).orElseThrow();
        var output=LegacyRecipeStackResolver.resolve(resolver.resolve(recipe.output())).orElseThrow();
        var bonus=LegacyRecipeStackResolver.resolve(resolver.resolve(recipe.bonus())).orElseThrow();
        assertEquals("gravel",input.registry().registryName());
        assertEquals(1,input.count());
        assertEquals("paper",output.registry().registryName());
        assertEquals(2,output.count());
        assertEquals("flint",bonus.registry().registryName());
    }

    private static byte[] bootstrap(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"x/y/Bootstrap",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"boot","(Lcpw/mods/fml/common/event/FMLInitializationEvent;)V",null,null);
        AnnotationVisitor a=m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true);a.visitEnd();m.visitCode();
        m.visitTypeInsn(Opcodes.NEW,"x/y/Registrar");m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"x/y/Registrar","<init>","()V",false);m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static byte[] registrar(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"x/y/Registrar",null,"java/lang/Object",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"x/y/Registrar","recipes","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PRIVATE,"recipes","()V",null,null);m.visitCode();
        itemStack(m,"net/minecraft/init/Items","field_151121_aF","Lnet/minecraft/item/Item;",2,0,false);
        itemStack(m,"net/minecraft/init/Items","field_151145_ak","Lnet/minecraft/item/Item;",1,0,false);
        itemStack(m,"net/minecraft/init/Blocks","field_150351_n","Lnet/minecraft/block/Block;",1,0,true);
        m.visitLdcInsn(0.25F);m.visitMethodInsn(Opcodes.INVOKESTATIC,"x/y/RecipeBook","register","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;F)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static byte[] dormant(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"x/y/Dormant",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);m.visitCode();
        itemStack(m,"net/minecraft/init/Items","field_151121_aF","Lnet/minecraft/item/Item;",9,0,false);
        itemStack(m,"net/minecraft/init/Items","field_151145_ak","Lnet/minecraft/item/Item;",9,0,false);
        itemStack(m,"net/minecraft/init/Blocks","field_150351_n","Lnet/minecraft/block/Block;",9,0,true);
        m.visitLdcInsn(1.0F);m.visitMethodInsn(Opcodes.INVOKESTATIC,"x/y/RecipeBook","register","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;F)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static byte[] recipeBook(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"x/y/RecipeBook",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"register","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;F)V",null,null);m.visitCode();m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static void itemStack(MethodVisitor m,String owner,String field,String fieldDesc,int count,int meta,boolean block){
        m.visitTypeInsn(Opcodes.NEW,"net/minecraft/item/ItemStack");m.visitInsn(Opcodes.DUP);m.visitFieldInsn(Opcodes.GETSTATIC,owner,field,fieldDesc);push(m,count);push(m,meta);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemStack","<init>",block?"(Lnet/minecraft/block/Block;II)V":"(Lnet/minecraft/item/Item;II)V",false);
    }
    private static void push(MethodVisitor m,int value){if(value>=0&&value<=5)m.visitInsn(Opcodes.ICONST_0+value);else if(value<=Byte.MAX_VALUE)m.visitIntInsn(Opcodes.BIPUSH,value);else m.visitLdcInsn(value);}
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
