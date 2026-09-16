package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockHarvestMaterialProofPassTest {
    private static final String MATERIAL = "net/minecraft/block/material/Material";
    private static final String MATERIAL_DESC = "Lnet/minecraft/block/material/Material;";

    @TempDir Path tempDir;

    @Test
    void completesOnlyTheExactNoToolMaterialFastPathAndBypassesHarvestCheckThere() throws Exception {
        Path jar = tempDir.resolve("HarvestMaterialFastPath.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/harvestmaterial/Wood.class",
                    block("foreign/harvestmaterial/Wood", "field_151575_d", Kind.CUSTOM_TOOL));
            put(out, "foreign/harvestmaterial/Rock.class",
                    block("foreign/harvestmaterial/Rock", "field_151576_e", Kind.PLAIN));
            put(out, "foreign/harvestmaterial/Unknown.class",
                    block("foreign/harvestmaterial/Unknown", null, Kind.PLAIN));
            put(out, "foreign/harvestmaterial/CustomMaterial.class",
                    block("foreign/harvestmaterial/CustomMaterial", "field_151575_d", Kind.CUSTOM_MATERIAL));
            put(out, "foreign/harvestmaterial/HarvestCheckListener.class", harvestCheckListener());
            put(out, "foreign/harvestmaterial/Bootstrap.class", bootstrap());
        }

        ConversionContext context = context(jar);
        for (String name : List.of("wood", "rock", "unknown", "custom_material")) {
            context.recordRegistryIdentity("blocks", "fixture:" + name, "fixture:" + name);
            context.recordRegistryIdentity("items", "fixture:" + name, "fixture:" + name);
        }

        new LegacyBlockDropAnalysisPass().apply(context);
        new LegacyBlockHarvestMaterialProofPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                tempDir.resolve("staging/" + LegacyBlockDropAnalysisPass.PLAN_PATH),
                StandardCharsets.UTF_8)).getAsJsonObject();

        assertEquals(6, root.get("schemaVersion").getAsInt());
        assertEquals(LegacyBlockHarvestMaterialProofPass.RULE_VERSION,
                root.get("materialHarvestRuleVersion").getAsString());
        assertEquals(1, root.get("harvestEligibilityProofCompletePlans").getAsInt());
        assertFalse(root.get("gameplayDropRuntimeWired").getAsBoolean());
        assertFalse(root.get("sourceHarvestCheckEventFree").getAsBoolean());
        assertEquals(1, root.get("harvestCheckEventHandlerCount").getAsInt());
        assertEquals(0, root.getAsJsonArray("materialProvenanceAnalysisDiagnostics").size());

        Map<String, JsonObject> plans = new HashMap<>();
        for (var element : root.getAsJsonArray("plans")) {
            JsonObject plan = element.getAsJsonObject();
            plans.put(plan.get("legacyRegistryName").getAsString(), plan);
        }

        JsonObject wood = plans.get("wood");
        assertFalse(wood.get("sourceHarvestEligibilityCustomizationFree").getAsBoolean());
        assertTrue(wood.get("materialFastPathSourceSafe").getAsBoolean());
        assertTrue(wood.get("legacyMaterialProvenanceComplete").getAsBoolean());
        assertTrue(wood.get("legacyMaterialHarvestRuleKnown").getAsBoolean());
        assertTrue(wood.get("materialFastPathHarvestEligibilityProofComplete").getAsBoolean());
        assertTrue(wood.get("harvestEligibilityProofComplete").getAsBoolean());
        assertEquals("material_tool_not_required_1_7_10", wood.get("harvestEligibilityMode").getAsString());
        assertEquals("wood", wood.getAsJsonObject("legacyMaterial").get("namedMaterial").getAsString());
        assertTrue(wood.getAsJsonObject("legacyMaterial").get("toolNotRequired").getAsBoolean());
        assertEquals(0, wood.getAsJsonArray("materialFastPathSourceReasons").size());
        assertFalse(contains(wood.getAsJsonArray("runtimeBlockers"), "harvest-eligibility-proof-pending"));
        assertFalse(contains(wood.getAsJsonArray("runtimeBlockers"), "harvest-check-event-runtime-pending"));
        assertFalse(contains(wood.getAsJsonArray("runtimeBlockers"),
                "harvest-eligibility-source-customization-runtime-pending"));
        assertTrue(contains(wood.getAsJsonArray("runtimeBlockers"),
                LegacyBlockHarvestMaterialProofPass.GAMEPLAY_RUNTIME_BLOCKER));

        JsonObject rock = plans.get("rock");
        assertTrue(rock.get("materialFastPathSourceSafe").getAsBoolean());
        assertTrue(rock.get("legacyMaterialProvenanceComplete").getAsBoolean());
        assertEquals("rock", rock.getAsJsonObject("legacyMaterial").get("namedMaterial").getAsString());
        assertFalse(rock.getAsJsonObject("legacyMaterial").get("toolNotRequired").getAsBoolean());
        assertFalse(rock.get("harvestEligibilityProofComplete").getAsBoolean());
        assertTrue(contains(rock.getAsJsonArray("runtimeBlockers"), "harvest-check-event-runtime-pending"));
        assertTrue(contains(rock.getAsJsonArray("runtimeBlockers"),
                LegacyBlockHarvestMaterialProofPass.TOOL_PLAYER_ROUTE_BLOCKER));
        assertTrue(contains(rock.getAsJsonArray("runtimeBlockers"),
                LegacyBlockHarvestMaterialProofPass.GAMEPLAY_RUNTIME_BLOCKER));

        JsonObject unknown = plans.get("unknown");
        assertTrue(unknown.get("materialFastPathSourceSafe").getAsBoolean());
        assertFalse(unknown.get("legacyMaterialProvenanceComplete").getAsBoolean());
        assertFalse(unknown.get("legacyMaterialHarvestRuleKnown").getAsBoolean());
        assertFalse(unknown.get("harvestEligibilityProofComplete").getAsBoolean());
        assertTrue(contains(unknown.getAsJsonArray("runtimeBlockers"),
                LegacyBlockHarvestMaterialProofPass.MATERIAL_PROVENANCE_BLOCKER));

        JsonObject customMaterial = plans.get("custom_material");
        assertFalse(customMaterial.get("materialFastPathSourceSafe").getAsBoolean());
        assertTrue(customMaterial.get("legacyMaterialProvenanceComplete").getAsBoolean());
        assertTrue(customMaterial.getAsJsonArray("materialFastPathSourceReasons").get(0)
                .getAsString().contains("getMaterial"));
        assertFalse(customMaterial.get("harvestEligibilityProofComplete").getAsBoolean());
        assertTrue(contains(customMaterial.getAsJsonArray("runtimeBlockers"),
                LegacyBlockHarvestMaterialProofPass.MATERIAL_FAST_PATH_SOURCE_BLOCKER));
    }

    private static boolean contains(JsonArray values, String expected) {
        for (var value : values) if (expected.equals(value.getAsString())) return true;
        return false;
    }

    private ConversionContext context(Path jar) throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        LegacyModMetadata metadata = new LegacyModMetadata(
                "fixture.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("fixture", "Fixture", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "fixture.jar", 0, 0, false, false, 0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(jar, staging, tempDir.resolve("candidate.jar"),
                "sha", Files.size(jar), metadata, analysis, new DiagnosticCollector(), "generic-test");
    }

    private static byte[] block(String owner, String materialField, Kind kind) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        if (materialField == null) init.visitInsn(Opcodes.ACONST_NULL);
        else init.visitFieldInsn(Opcodes.GETSTATIC, MATERIAL, materialField, MATERIAL_DESC);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(" + MATERIAL_DESC + ")V", false);
        init.visitInsn(Opcodes.RETURN);
        end(init);

        if (kind == Kind.CUSTOM_TOOL) {
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "getHarvestTool",
                    "(I)Ljava/lang/String;", null, null);
            method.visitCode();
            method.visitLdcInsn("axe");
            method.visitInsn(Opcodes.ARETURN);
            end(method);
        } else if (kind == Kind.CUSTOM_MATERIAL) {
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "getMaterial", "()" + MATERIAL_DESC,
                    null, null);
            method.visitCode();
            method.visitFieldInsn(Opcodes.GETSTATIC, MATERIAL, "field_151575_d", MATERIAL_DESC);
            method.visitInsn(Opcodes.ARETURN);
            end(method);
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] harvestCheckListener() {
        String owner = "foreign/harvestmaterial/HarvestCheckListener";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        constructor(writer, "java/lang/Object");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "onHarvestCheck",
                "(Lnet/minecraftforge/event/entity/player/PlayerEvent$HarvestCheck;)V", null, null);
        AnnotationVisitor subscribe = method.visitAnnotation("Lcpw/mods/fml/common/eventhandler/SubscribeEvent;", true);
        subscribe.visitEnd();
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] bootstrap() {
        String owner = "foreign/harvestmaterial/Bootstrap";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        constructor(writer, "java/lang/Object");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        register(method, "foreign/harvestmaterial/Wood", "wood");
        register(method, "foreign/harvestmaterial/Rock", "rock");
        register(method, "foreign/harvestmaterial/Unknown", "unknown");
        register(method, "foreign/harvestmaterial/CustomMaterial", "custom_material");
        method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraftforge/common/MinecraftForge", "EVENT_BUS",
                "Lcpw/mods/fml/common/eventhandler/EventBus;");
        method.visitTypeInsn(Opcodes.NEW, "foreign/harvestmaterial/HarvestCheckListener");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/harvestmaterial/HarvestCheckListener",
                "<init>", "()V", false);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "cpw/mods/fml/common/eventhandler/EventBus", "register",
                "(Ljava/lang/Object;)V", false);
        method.visitInsn(Opcodes.RETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void register(MethodVisitor method, String owner, String name) {
        method.visitTypeInsn(Opcodes.NEW, owner);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false);
        method.visitLdcInsn(name);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
    }

    private static void constructor(ClassWriter writer, String parent) {
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        end(init);
    }

    private static void end(MethodVisitor method) {
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }

    private enum Kind { PLAIN, CUSTOM_TOOL, CUSTOM_MATERIAL }
}
