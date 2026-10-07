package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

import static org.junit.jupiter.api.Assertions.*;

class LegacyRecipeEnchantmentResultTest {
    @TempDir Path tempDir;

    @Test void analyzerCarriesItemStackEnchantmentMutationThroughHelperParameters() throws Exception {
        Path jar=tempDir.resolve("enchanted-helper.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            out.putNextEntry(new JarEntry("foreign/recipe/Bootstrap.class"));out.write(fixture());out.closeEntry();
        }
        var analysis=new LegacyRecipeAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(),String.join("\n",analysis.diagnostics()));
        var shaped=analysis.of(LegacyRecipeAnalyzer.Kind.SHAPED);
        assertEquals(1,shaped.size());
        var decorated=assertInstanceOf(LegacyRecipeAnalyzer.EnchantedObjectValue.class,shaped.getFirst().arguments().getFirst());
        assertEquals(1,decorated.enchantments().size());
        var mutation=decorated.enchantments().getFirst();
        var enchantment=assertInstanceOf(LegacyRecipeAnalyzer.FieldValue.class,mutation.enchantment());
        assertEquals("net/minecraft/enchantment/Enchantment",enchantment.owner());
        assertEquals("field_77347_r",enchantment.name());
        assertEquals(3,assertInstanceOf(LegacyRecipeAnalyzer.NumberValue.class,mutation.level()).value().intValue());
    }

    @Test void materializerWritesModern12111EnchantmentComponentAndRejectsUnknownLegacyEnchantments() {
        ConversionContext context=context();
        context.recordRegistryIdentity("items","fixture:blade","fixture:blade");
        LegacyOreDictionaryIndex empty=LegacyOreDictionaryIndex.build(new LegacyRecipeAnalyzer.Analysis(List.of(),List.of()),context);

        var output=new LegacyRecipeAnalyzer.ObjectValue(
                "net/minecraft/item/ItemStack","(Lnet/minecraft/item/Item;)V",List.of(modItem("blade")));
        var decorated=new LegacyRecipeAnalyzer.EnchantedObjectValue(output,List.of(
                new LegacyRecipeAnalyzer.EnchantmentValue(
                        new LegacyRecipeAnalyzer.FieldValue("net/minecraft/enchantment/Enchantment","field_77347_r","Lnet/minecraft/enchantment/Enchantment;"),
                        new LegacyRecipeAnalyzer.NumberValue(3))));
        var recipe=new LegacyRecipeAnalyzer.Registration(
                LegacyRecipeAnalyzer.Kind.SHAPED,
                List.of(decorated,new LegacyRecipeAnalyzer.ArrayValue(List.of(
                        new LegacyRecipeAnalyzer.TextValue("#"),
                        new LegacyRecipeAnalyzer.CharacterValue('#'),
                        new LegacyRecipeAnalyzer.RegistryValue(LegacyRegistryAnalyzer.Kind.ITEM,"stick","minecraft","net/minecraft/init/Items","field_151055_y")))),
                "foreign/recipe/Bootstrap","helper","()V");
        JsonObject json=LegacyRecipeJsonMaterializer.materialize(recipe,context,empty).orElseThrow();
        JsonObject ench=json.getAsJsonObject("result").getAsJsonObject("components").getAsJsonObject("minecraft:enchantments");
        assertEquals(3,ench.get("minecraft:unbreaking").getAsInt());

        var unknown=new LegacyRecipeAnalyzer.EnchantedObjectValue(output,List.of(
                new LegacyRecipeAnalyzer.EnchantmentValue(
                        new LegacyRecipeAnalyzer.FieldValue("foreign/enchant/ModEnchant","SPECIAL","Lnet/minecraft/enchantment/Enchantment;"),
                        new LegacyRecipeAnalyzer.NumberValue(1))));
        var rejected=new LegacyRecipeAnalyzer.Registration(
                LegacyRecipeAnalyzer.Kind.SHAPED,
                List.of(unknown,recipe.arguments().get(1)),"foreign/recipe/Bootstrap","helper","()V");
        assertTrue(LegacyRecipeJsonMaterializer.materialize(rejected,context,empty).isEmpty());
    }

    private LegacyRecipeAnalyzer.RegistryValue modItem(String name){
        return new LegacyRecipeAnalyzer.RegistryValue(LegacyRegistryAnalyzer.Kind.ITEM,name,"fixture","fixture/Items",name);
    }

    private ConversionContext context(){
        LegacyModMetadata metadata=new LegacyModMetadata("fixture.jar","test",
                List.of(new LegacyModMetadata.ModEntry("fixture","Fixture","1.0","1.7.10",List.of())));
        LegacyJarAnalyzer.Analysis analysis=new LegacyJarAnalyzer.Analysis(
                "fixture.jar",0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());
        return new ConversionContext(tempDir.resolve("fixture.jar"),tempDir.resolve("staging"),tempDir.resolve("candidate.jar"),
                "sha",0L,metadata,analysis,new DiagnosticCollector(),"generic-test");
    }

    private static byte[] fixture(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/recipe/Bootstrap",null,"java/lang/Object",null);

        MethodVisitor h=w.visitMethod(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"enchanted",
                "(Lnet/minecraft/item/Item;Lnet/minecraft/enchantment/Enchantment;I[Ljava/lang/Object;)V",null,null);
        h.visitCode();
        h.visitTypeInsn(Opcodes.NEW,"net/minecraft/item/ItemStack");h.visitInsn(Opcodes.DUP);h.visitVarInsn(Opcodes.ALOAD,0);
        h.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemStack","<init>","(Lnet/minecraft/item/Item;)V",false);
        h.visitVarInsn(Opcodes.ASTORE,4);
        Label noEnchant=new Label();h.visitVarInsn(Opcodes.ALOAD,1);h.visitJumpInsn(Opcodes.IFNULL,noEnchant);
        h.visitVarInsn(Opcodes.ALOAD,4);h.visitVarInsn(Opcodes.ALOAD,1);h.visitVarInsn(Opcodes.ILOAD,2);
        h.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_77966_a","(Lnet/minecraft/enchantment/Enchantment;I)V",false);
        h.visitLabel(noEnchant);h.visitVarInsn(Opcodes.ALOAD,4);h.visitVarInsn(Opcodes.ALOAD,3);
        h.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","addRecipe","(Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V",false);
        h.visitInsn(Opcodes.RETURN);h.visitMaxs(4,5);h.visitEnd();

        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"init","(Lcpw/mods/fml/common/event/FMLInitializationEvent;)V",null,null);
        m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true).visitEnd();m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC,"foreign/items/Items","BLADE","Lnet/minecraft/item/Item;");
        m.visitFieldInsn(Opcodes.GETSTATIC,"net/minecraft/enchantment/Enchantment","field_77347_r","Lnet/minecraft/enchantment/Enchantment;");
        m.visitInsn(Opcodes.ICONST_3);
        m.visitInsn(Opcodes.ICONST_3);m.visitTypeInsn(Opcodes.ANEWARRAY,"java/lang/Object");
        m.visitInsn(Opcodes.DUP);m.visitInsn(Opcodes.ICONST_0);m.visitLdcInsn("#");m.visitInsn(Opcodes.AASTORE);
        m.visitInsn(Opcodes.DUP);m.visitInsn(Opcodes.ICONST_1);m.visitIntInsn(Opcodes.BIPUSH,35);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Character","valueOf","(C)Ljava/lang/Character;",false);m.visitInsn(Opcodes.AASTORE);
        m.visitInsn(Opcodes.DUP);m.visitInsn(Opcodes.ICONST_2);
        m.visitFieldInsn(Opcodes.GETSTATIC,"net/minecraft/init/Items","field_151055_y","Lnet/minecraft/item/Item;");m.visitInsn(Opcodes.AASTORE);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"foreign/recipe/Bootstrap","enchanted",
                "(Lnet/minecraft/item/Item;Lnet/minecraft/enchantment/Enchantment;I[Ljava/lang/Object;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(8,2);m.visitEnd();
        w.visitEnd();return w.toByteArray();
    }
}
