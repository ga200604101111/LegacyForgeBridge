package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.longyu.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyRecipeAnalysisPassTest {
    @TempDir Path tempDir;

    @Test void sidecarResolvesKnown1710VanillaFieldsAndRetainsUnknownEvidence() throws Exception {
        Path source=tempDir.resolve("foreign-recipe.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(source))){
            out.putNextEntry(new JarEntry("foreign/recipes/Bootstrap.class"));
            out.write(fixture());out.closeEntry();
        }
        Path staging=tempDir.resolve("staging");Files.createDirectories(staging);
        LegacyModMetadata metadata=new LegacyModMetadata("foreign-recipe.jar","test",
                List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));
        LegacyJarAnalyzer.Analysis analysis=new LegacyJarAnalyzer.Analysis(
                "foreign-recipe.jar",0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());
        ConversionContext context=new ConversionContext(source,staging,tempDir.resolve("candidate.jar"),
                "sha",Files.size(source),metadata,analysis,new DiagnosticCollector(),"generic-test");

        new LegacyRecipeAnalysisPass().apply(context);

        JsonObject root=JsonParser.parseString(Files.readString(
                staging.resolve("legacyforgebridge/recipe-analysis.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject shaped=root.getAsJsonArray("registrations").get(0).getAsJsonObject();
        assertEquals("shaped",shaped.get("kind").getAsString());
        JsonArray arguments=shaped.getAsJsonArray("arguments");

        JsonObject output=arguments.get(0).getAsJsonObject();
        JsonObject outputIdentity=output.getAsJsonArray("arguments").get(0).getAsJsonObject();
        assertRegistry(outputIdentity,"item","iron_ingot");

        JsonArray spec=arguments.get(1).getAsJsonObject().getAsJsonArray("elements");
        assertRegistry(spec.get(2).getAsJsonObject(),"block","cobblestone");
        JsonObject unknown=spec.get(4).getAsJsonObject();
        assertEquals("field",unknown.get("kind").getAsString());
        assertEquals("net/minecraft/init/Items",unknown.get("owner").getAsString());
        assertEquals("field_999999_x",unknown.get("name").getAsString());
    }

    private static void assertRegistry(JsonObject value,String kind,String name){
        assertEquals("registry",value.get("kind").getAsString());
        assertEquals(kind,value.get("registryKind").getAsString());
        assertEquals(name,value.get("name").getAsString());
        assertEquals("minecraft",value.get("legacyNamespace").getAsString());
    }

    private static byte[] fixture(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String owner="foreign/recipes/Bootstrap";
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,owner,null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"init","(Lcpw/mods/fml/common/event/FMLInitializationEvent;)V",null,null);
        m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true).visitEnd();m.visitCode();

        m.visitTypeInsn(Opcodes.NEW,"net/minecraft/item/ItemStack");m.visitInsn(Opcodes.DUP);
        m.visitFieldInsn(Opcodes.GETSTATIC,"net/minecraft/init/Items","field_151042_j","Lnet/minecraft/item/Item;");
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemStack","<init>","(Lnet/minecraft/item/Item;)V",false);

        m.visitInsn(Opcodes.ICONST_5);m.visitTypeInsn(Opcodes.ANEWARRAY,"java/lang/Object");
        m.visitInsn(Opcodes.DUP);m.visitInsn(Opcodes.ICONST_0);m.visitLdcInsn("#");m.visitInsn(Opcodes.AASTORE);
        m.visitInsn(Opcodes.DUP);m.visitInsn(Opcodes.ICONST_1);m.visitIntInsn(Opcodes.BIPUSH,'#');
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Character","valueOf","(C)Ljava/lang/Character;",false);m.visitInsn(Opcodes.AASTORE);
        m.visitInsn(Opcodes.DUP);m.visitInsn(Opcodes.ICONST_2);
        m.visitFieldInsn(Opcodes.GETSTATIC,"net/minecraft/init/Blocks","field_150347_e","Lnet/minecraft/block/Block;");m.visitInsn(Opcodes.AASTORE);
        m.visitInsn(Opcodes.DUP);m.visitInsn(Opcodes.ICONST_3);m.visitIntInsn(Opcodes.BIPUSH,'x');
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Character","valueOf","(C)Ljava/lang/Character;",false);m.visitInsn(Opcodes.AASTORE);
        m.visitInsn(Opcodes.DUP);m.visitInsn(Opcodes.ICONST_4);
        m.visitFieldInsn(Opcodes.GETSTATIC,"net/minecraft/init/Items","field_999999_x","Lnet/minecraft/item/Item;");m.visitInsn(Opcodes.AASTORE);

        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","addRecipe",
                "(Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(8,2);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
}
