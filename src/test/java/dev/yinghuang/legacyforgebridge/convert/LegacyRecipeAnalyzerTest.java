package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import java.nio.file.*;import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyRecipeAnalyzerTest {
 @TempDir Path tempDir;
 @Test void shapedHelperArrayAndOreRegistrationRemainSourceDerivedAcrossNamespace()throws Exception{
  Path jar=tempDir.resolve("RecipeFixture.jar");try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){put(out,"unrelated/cook/Bootstrap.class",bytes());}
  var a=new LegacyRecipeAnalyzer().analyze(jar);assertTrue(a.diagnostics().isEmpty(),String.join("\n",a.diagnostics()));
  assertEquals(1,a.of(LegacyRecipeAnalyzer.Kind.SHAPED).size());assertEquals(1,a.of(LegacyRecipeAnalyzer.Kind.ORE_REGISTER).size());
  var shaped=a.of(LegacyRecipeAnalyzer.Kind.SHAPED).getFirst();assertInstanceOf(LegacyRecipeAnalyzer.ArrayValue.class,shaped.arguments().get(1));
  var spec=(LegacyRecipeAnalyzer.ArrayValue)shaped.arguments().get(1);assertEquals(3,spec.elements().size());assertEquals("##",((LegacyRecipeAnalyzer.TextValue)spec.elements().getFirst()).value());assertEquals('#',((LegacyRecipeAnalyzer.CharacterValue)spec.elements().get(1)).value());
  assertEquals("stickWood",((LegacyRecipeAnalyzer.TextValue)a.of(LegacyRecipeAnalyzer.Kind.ORE_REGISTER).getFirst().arguments().getFirst()).value());
 }
 private static byte[] bytes(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"unrelated/cook/Bootstrap",null,"java/lang/Object",null);
  MethodVisitor h=w.visitMethod(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"shaped","(Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V",null,null);h.visitCode();h.visitVarInsn(Opcodes.ALOAD,0);h.visitVarInsn(Opcodes.ALOAD,1);h.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","addRecipe","(Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V",false);h.visitInsn(Opcodes.RETURN);h.visitMaxs(2,2);h.visitEnd();
  MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"init","(Lcpw/mods/fml/common/event/FMLInitializationEvent;)V",null,null);m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true).visitEnd();m.visitCode();
  m.visitInsn(Opcodes.ACONST_NULL);m.visitInsn(Opcodes.ICONST_3);m.visitTypeInsn(Opcodes.ANEWARRAY,"java/lang/Object");m.visitInsn(Opcodes.DUP);m.visitInsn(Opcodes.ICONST_0);m.visitLdcInsn("##");m.visitInsn(Opcodes.AASTORE);m.visitInsn(Opcodes.DUP);m.visitInsn(Opcodes.ICONST_1);m.visitIntInsn(Opcodes.BIPUSH,35);m.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Character","valueOf","(C)Ljava/lang/Character;",false);m.visitInsn(Opcodes.AASTORE);m.visitInsn(Opcodes.DUP);m.visitInsn(Opcodes.ICONST_2);m.visitLdcInsn("plankWood");m.visitInsn(Opcodes.AASTORE);m.visitMethodInsn(Opcodes.INVOKESTATIC,"unrelated/cook/Bootstrap","shaped","(Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V",false);
  m.visitLdcInsn("stickWood");m.visitInsn(Opcodes.ACONST_NULL);m.visitMethodInsn(Opcodes.INVOKESTATIC,"net/minecraftforge/oredict/OreDictionary","registerOre","(Ljava/lang/String;Lnet/minecraft/item/ItemStack;)V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(6,2);m.visitEnd();w.visitEnd();return w.toByteArray();}
 private static void put(JarOutputStream o,String n,byte[]b)throws Exception{o.putNextEntry(new JarEntry(n));o.write(b);o.closeEntry();}
}
