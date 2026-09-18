package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyCandidateReferenceAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballRuntimeFixture;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyVariantSnowballRegistrationStripPassTest {
    @TempDir Path tempDir;

    @Test
    void stripsOnlyProofMatchedProjectileRegistrationFromStagedCandidate() throws Exception {
        Path source = VariantSnowballRuntimeFixture.write(tempDir.resolve("variant.jar"));
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        ConversionContext context = context(source, staging);

        new CopyLegacyJarPass().apply(context);
        runRuntimeInputs(context);
        new LegacyVariantSnowballRuntimePass().apply(context);
        new LegacyVariantSnowballRegistrationStripPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyVariantSnowballRegistrationStripPass.OUTPUT),
                StandardCharsets.UTF_8)).getAsJsonObject();

        assertTrue(root.get("projectileRegistrationStripWired").getAsBoolean());
        assertFalse(root.get("itemRegistrationStripWired").getAsBoolean());
        assertFalse(root.get("sourceClassDeletionWired").getAsBoolean());
        assertEquals(1, root.get("projectileRegistrationStripCompleteRules").getAsInt());
        assertEquals(1, root.get("strippedProjectileRegistrationSites").getAsInt());
        assertEquals(0, root.get("blockedProjectileRegistrationStripRules").getAsInt());

        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(rule.get("projectileRegistrationStripComplete").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").isEmpty());
        assertEquals("foreign/Bootstrap", rule.get("sourceOwner").getAsString());

        var refs = new LegacyCandidateReferenceAnalyzer().analyze(
                staging, Set.of("foreign/entity/VariantProjectile"));
        assertFalse(refs.forTarget("foreign/entity/VariantProjectile")
                        .incomingClassReferences().contains("foreign/Bootstrap"),
                () -> "Bootstrap still references projectile after strip: "
                        + projectileReferenceSites(
                                Files.readAllBytes(staging.resolve("foreign/Bootstrap.class"))));
        // The launch item still legitimately constructs the legacy projectile in the copied
        // source cohort until the later retirement/registration-strip closure removes it.
        assertTrue(refs.forTarget("foreign/entity/VariantProjectile")
                .incomingClassReferences().contains("foreign/item/VariantBall"));
    }

    private static java.util.List<String> projectileReferenceSites(byte[] bytes) {
        java.util.List<String> sites = new java.util.ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            private String owner;

            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                owner = name;
                if ("foreign/entity/VariantProjectile".equals(superName)) {
                    sites.add("class-super:" + name);
                }
                if (interfaces != null) {
                    for (String value : interfaces) {
                        if ("foreign/entity/VariantProjectile".equals(value)) {
                            sites.add("class-interface:" + name);
                        }
                    }
                }
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (descriptor.contains("Lforeign/entity/VariantProjectile;")) {
                    sites.add("method-descriptor:" + owner + "." + name + descriptor);
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if ("foreign/entity/VariantProjectile".equals(type)) {
                            sites.add("type-insn:" + name + descriptor + ":" + opcode);
                        }
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String fieldOwner, String fieldName,
                                               String fieldDescriptor) {
                        if ("foreign/entity/VariantProjectile".equals(fieldOwner)
                                || fieldDescriptor.contains("Lforeign/entity/VariantProjectile;")) {
                            sites.add("field-insn:" + name + descriptor + ":"
                                    + fieldOwner + "." + fieldName + fieldDescriptor);
                        }
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String callOwner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if ("foreign/entity/VariantProjectile".equals(callOwner)
                                || methodDescriptor.contains("Lforeign/entity/VariantProjectile;")) {
                            sites.add("method-insn:" + name + descriptor + ":"
                                    + callOwner + "." + methodName + methodDescriptor);
                        }
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof Type type
                                && type.getSort() == Type.OBJECT
                                && "foreign/entity/VariantProjectile".equals(type.getInternalName())) {
                            sites.add("ldc-type:" + name + descriptor);
                        }
                        if ("foreign/entity/VariantProjectile".equals(value)
                                || "foreign.entity.VariantProjectile".equals(value)) {
                            sites.add("ldc-string:" + name + descriptor);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return sites;
    }

    private static void runRuntimeInputs(ConversionContext context) throws Exception {
        new GenericContentPass().apply(context);
        new LegacyVariantSnowballPass().apply(context);
        new LegacyVariantSnowballLaunchPass().apply(context);
        new LegacyVariantSnowballRuntimeCandidatePass().apply(context);
        new LegacyEntityDataWatcherPass().apply(context);
    }

    private ConversionContext context(Path source, Path staging) throws Exception {
        LegacyModMetadata metadata = new LegacyModMetadata(
                source.getFileName().toString(),
                "test",
                List.of(new LegacyModMetadata.ModEntry(
                        "foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                source.getFileName().toString(),
                0, 0, false, false,
                0, 0, 0, 0,
                Set.of(), Set.of(), Set.of());
        return new ConversionContext(
                source,
                staging,
                tempDir.resolve("candidate.jar"),
                "sha",
                Files.size(source),
                metadata,
                analysis,
                new DiagnosticCollector(),
                "generic-test");
    }
}
