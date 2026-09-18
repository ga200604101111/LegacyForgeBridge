package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyVariantSnowballItemRegistrationStripPassTest {
    @TempDir Path tempDir;

    @Test
    void neutralizesUniqueRootRegisterItemButPreservesItemConstructionReference()
            throws Exception {
        Path source = VariantSnowballRuntimeFixture.write(tempDir.resolve("variant.jar"));
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        ConversionContext context = context(source, staging);

        new CopyLegacyJarPass().apply(context);
        new GenericContentPass().apply(context);
        new LegacyVariantSnowballPass().apply(context);
        new LegacyVariantSnowballLaunchPass().apply(context);
        new LegacyVariantSnowballRuntimeCandidatePass().apply(context);
        new LegacyEntityDataWatcherPass().apply(context);
        new LegacyVariantSnowballRuntimePass().apply(context);
        new LegacyVariantSnowballItemRegistrationStripPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyVariantSnowballItemRegistrationStripPass.OUTPUT),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(root.get("itemRegistrationStripWired").getAsBoolean());
        assertTrue(root.get("argumentEvaluationPreserved").getAsBoolean());
        assertTrue(root.get("constructorSideEffectsPreserved").getAsBoolean());
        assertEquals(1, root.get("itemRegistrationStripCompleteRules").getAsInt());
        assertEquals(1, root.get("strippedItemRegistrationSites").getAsInt());

        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(rule.get("itemRegistrationStripComplete").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").isEmpty(), rule.toString());
        assertEquals("foreign/Bootstrap", rule.get("sourceOwner").getAsString());
        assertEquals("<clinit>", rule.get("sourceMethod").getAsString());

        byte[] bootstrap = Files.readAllBytes(staging.resolve("foreign/Bootstrap.class"));
        boolean[] registerItem = {false};
        boolean[] allocation = {false};
        new ClassReader(bootstrap).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if (owner.equals("cpw/mods/fml/common/registry/GameRegistry")
                                && methodName.equals("registerItem")) {
                            registerItem[0] = true;
                        }
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW
                                && type.equals("foreign/item/VariantBall")) {
                            allocation[0] = true;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertFalse(registerItem[0]);
        assertTrue(allocation[0]);
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
