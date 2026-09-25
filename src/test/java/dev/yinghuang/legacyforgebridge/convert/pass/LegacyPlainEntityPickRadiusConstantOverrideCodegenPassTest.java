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
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyPlainEntityPickRadiusConstantOverrideCodegenPassTest {
    @TempDir Path tempDir;

    @Test void patchesGeneratedEntityWithConstantGetPickRadiusFloat() throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        ConversionContext context = context(staging);
        writeAdmission(staging);

        new LegacyPlainEntityCodegenPass().apply(context);
        new LegacyPlainEntityConstantOverrideCodegenPass().apply(context);

        JsonObject generated = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyPlainEntityCodegenPass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(generated.get("typedConstantBehaviorOverrideCodegenWired").getAsBoolean());
        JsonObject rule = generated.getAsJsonArray("generatedClasses").get(0).getAsJsonObject();
        assertTrue(rule.get("constantBehaviorOverrideCodegenComplete").getAsBoolean());
        String internal = rule.get("generatedInternalName").getAsString();
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(Files.readAllBytes(staging.resolve(internal + ".class"))).accept(node, 0);
        MethodNode method = node.methods.stream()
                .filter(value -> value.name.equals("getPickRadius") && value.desc.equals("()F"))
                .findFirst().orElse(null);
        assertNotNull(method);
        assertEquals(1, method.maxLocals);
        assertEquals(1, method.maxStack);
        var executable = method.instructions.iterator();
        org.objectweb.asm.tree.AbstractInsnNode first = null, second = null;
        while (executable.hasNext()) {
            var instruction = executable.next();
            if (instruction.getOpcode() < 0) continue;
            if (first == null) first = instruction; else { second = instruction; break; }
        }
        assertInstanceOf(LdcInsnNode.class, first);
        assertEquals(0.25F, ((LdcInsnNode) first).cst);
        assertNotNull(second);
        assertEquals(Opcodes.FRETURN, second.getOpcode());
    }

    private ConversionContext context(Path staging) throws Exception {
        Path source = tempDir.resolve("source.jar");
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(source))) { }
        LegacyModMetadata metadata = new LegacyModMetadata("source.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis("source.jar", 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(source, staging, tempDir.resolve("candidate.jar"), "sha",
                Files.size(source), metadata, analysis, new DiagnosticCollector(), "generic-test");
    }

    private static void writeAdmission(Path staging) throws Exception {
        Files.writeString(staging.resolve(LegacyEntityRuntimeAdmissionPass.OUTPUT), """
                {
                  "schemaVersion":1,"sourceSha256":"sha","rules":[{
                    "id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"foreign/Orb","legacyNumericId":17,
                    "trackingRange":80,"updateFrequency":2,"velocityUpdates":true,"width":0.5,"height":0.75,
                    "family":"PLAIN_ENTITY_SYNCHED_DATA_ONLY","admitted":true,"synchedDataEntries":[],
                    "constantBehaviorOverrides":[{
                      "sourceKind":"COLLISION_BORDER_SIZE","sourceOwner":"foreign/Orb","sourceMethod":"getCollisionBorderSize","sourceDescriptor":"()F",
                      "targetOwner":"net/minecraft/world/entity/Entity","targetMethod":"getPickRadius","targetDescriptor":"()F",
                      "mappingSemantics":"PICK_RADIUS_FLOAT_IDENTITY","constantKind":"float","sourceConstantProofComplete":true,
                      "constantFloat":0.25,"runtimeCodegenReady":true
                    }]
                  }]
                }
                """, StandardCharsets.UTF_8);
    }
}
