package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEntityAttackabilityConstantOverridePipelineTest {
    @TempDir Path tempDir;

    @Test void provesAdmitsAndGeneratesModernIsAttackableConstant() throws Exception {
        Path source = tempDir.resolve("source.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            out.putNextEntry(new JarEntry("foreign/Orb.class"));
            out.write(sourceEntity());
            out.closeEntry();
        }
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        ConversionContext context = context(source, staging);
        writeBehavior(staging);
        writeRuntimePlan(staging);
        writeConstruction(staging);

        new LegacyEntityConstantOverridePass().apply(context);
        new LegacyEntityRuntimeAdmissionPass().apply(context);
        JsonObject admission = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyEntityRuntimeAdmissionPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject admitted = admission.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(admitted.get("admitted").getAsBoolean());
        JsonObject mapping = admitted.getAsJsonArray("constantBehaviorOverrides").get(0).getAsJsonObject();
        assertEquals("CAN_ATTACK_WITH_ITEM", mapping.get("sourceKind").getAsString());
        assertEquals("isAttackable", mapping.get("targetMethod").getAsString());
        assertEquals("ATTACKABILITY_BOOLEAN_IDENTITY", mapping.get("mappingSemantics").getAsString());
        assertFalse(mapping.get("constantBoolean").getAsBoolean());

        new LegacyPlainEntityCodegenPass().apply(context);
        new LegacyPlainEntityConstantOverrideCodegenPass().apply(context);

        JsonObject generated = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyPlainEntityCodegenPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject rule = generated.getAsJsonArray("generatedClasses").get(0).getAsJsonObject();
        assertTrue(rule.get("constantBehaviorOverrideCodegenComplete").getAsBoolean());
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(Files.readAllBytes(staging.resolve(rule.get("generatedInternalName").getAsString() + ".class"))).accept(node, 0);
        MethodNode method = node.methods.stream().filter(value -> value.name.equals("isAttackable") && value.desc.equals("()Z"))
                .findFirst().orElse(null);
        assertNotNull(method);
        int[] opcodes = method.instructions.iterator().hasNext() ? executable(method) : new int[0];
        assertArrayEquals(new int[]{Opcodes.ICONST_0, Opcodes.IRETURN}, opcodes);
    }

    private ConversionContext context(Path source, Path staging) throws Exception {
        LegacyModMetadata metadata = new LegacyModMetadata("source.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis("source.jar", 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(source, staging, tempDir.resolve("candidate.jar"), "sha",
                Files.size(source), metadata, analysis, new DiagnosticCollector(), "generic-test");
    }

    private static byte[] sourceEntity() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/Orb", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_70075_an", "()Z", null, null);
        method.visitCode(); method.visitInsn(Opcodes.ICONST_0); method.visitInsn(Opcodes.IRETURN); method.visitMaxs(0, 0); method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void writeBehavior(Path staging) throws Exception {
        Files.writeString(staging.resolve(LegacyEntityBehaviorSurfacePass.OUTPUT), """
                {"schemaVersion":1,"sourceSha256":"sha","rules":[{"id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"foreign/Orb","externalBaseClass":"net/minecraft/entity/Entity","sourceOwnedBehaviorInventoryComplete":true,"unclassifiedSourceMethodCount":0,
                "callbacks":[{"kind":"ENTITY_INIT","owner":"foreign/Orb","method":"func_70088_a","descriptor":"()V"},{"kind":"READ_NBT","owner":"foreign/Orb","method":"func_70037_a","descriptor":"(Lnet/minecraft/nbt/NBTTagCompound;)V"},{"kind":"WRITE_NBT","owner":"foreign/Orb","method":"func_70014_b","descriptor":"(Lnet/minecraft/nbt/NBTTagCompound;)V"},{"kind":"CAN_ATTACK_WITH_ITEM","owner":"foreign/Orb","method":"func_70075_an","descriptor":"()Z"}],
                "sourceMethods":[{"owner":"foreign/Orb","method":"func_70088_a","descriptor":"()V","callbackKind":"ENTITY_INIT","trivialNoOp":false},{"owner":"foreign/Orb","method":"func_70037_a","descriptor":"(Lnet/minecraft/nbt/NBTTagCompound;)V","callbackKind":"READ_NBT","trivialNoOp":true},{"owner":"foreign/Orb","method":"func_70014_b","descriptor":"(Lnet/minecraft/nbt/NBTTagCompound;)V","callbackKind":"WRITE_NBT","trivialNoOp":true},{"owner":"foreign/Orb","method":"func_70075_an","descriptor":"()Z","callbackKind":"CAN_ATTACK_WITH_ITEM","trivialNoOp":false}]}]}
                """, StandardCharsets.UTF_8);
    }

    private static void writeRuntimePlan(Path staging) throws Exception {
        Files.writeString(staging.resolve(LegacyEntityRuntimePlanPass.OUTPUT), """
                {"schemaVersion":1,"sourceSha256":"sha","rules":[{"id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"foreign/Orb","legacyNumericId":17,"trackingRange":80,"updateFrequency":2,"velocityUpdates":true,"synchedDataMappingComplete":true,"sourceWideDataWatcherCallClosureComplete":true,"sourceOwnedDataWatcherReadCount":0,"sourceOwnedDataWatcherWriteCount":0,"postInitSourceDataWatcherMutationFree":true,"synchedDataEntries":[]}]}
                """, StandardCharsets.UTF_8);
    }

    private static void writeConstruction(Path staging) throws Exception {
        Files.writeString(staging.resolve(LegacyEntityConstructionPass.OUTPUT), """
                {"schemaVersion":1,"sourceSha256":"sha","rules":[{"id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"foreign/Orb","externalBaseClass":"net/minecraft/entity/Entity","worldConstructorPresent":true,"constructorChainComplete":true,"constructorControlFlowSimple":true,"sourceSetSizeOverridePresent":false,"sizeProofComplete":true,"width":0.5,"height":0.75,"unmappedConstructorEffectCount":0}]}
                """, StandardCharsets.UTF_8);
    }

    private static int[] executable(MethodNode method) {
        java.util.ArrayList<Integer> values = new java.util.ArrayList<>();
        for (var instruction : method.instructions) if (instruction.getOpcode() >= 0) values.add(instruction.getOpcode());
        return values.stream().mapToInt(Integer::intValue).toArray();
    }
}
