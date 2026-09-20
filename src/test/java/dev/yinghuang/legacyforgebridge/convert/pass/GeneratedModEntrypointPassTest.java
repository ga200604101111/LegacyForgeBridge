package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedModEntrypointPassTest {
    @TempDir
    Path tempDir;

    @Test
    void convertedCandidateGetsRealMainAndClientEntrypointClass() throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        ConversionContext context = context(staging);

        GeneratedModEntrypointPass pass = new GeneratedModEntrypointPass();
        pass.apply(context);

        String binary = GeneratedModEntrypointPass.entrypointClass(context.metadata());
        Path classFile = staging.resolve(binary.replace('.', '/') + ".class");
        assertTrue(Files.isRegularFile(classFile));
        assertEquals(
                binary,
                Files.readString(staging.resolve(GeneratedModEntrypointPass.MARKER_PATH), StandardCharsets.UTF_8).trim()
        );

        Set<String> interfaces = new HashSet<>();
        Set<String> methods = new HashSet<>();
        Set<String> mainCalls = new HashSet<>();
        Set<String> clientCalls = new HashSet<>();
        new ClassReader(Files.readAllBytes(classFile)).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] implemented) {
                if (implemented != null) interfaces.addAll(List.of(implemented));
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                methods.add(name + descriptor);
                Set<String> calls = "onInitialize".equals(name) ? mainCalls
                        : "onInitializeClient".equals(name) ? clientCalls : null;
                if (calls == null) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                        calls.add(owner + "#" + methodName + methodDescriptor);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue(interfaces.contains("net/fabricmc/api/ModInitializer"));
        assertTrue(interfaces.contains("net/fabricmc/api/ClientModInitializer"));
        assertTrue(methods.contains("onInitialize()V"));
        assertTrue(methods.contains("onInitializeClient()V"));
        assertTrue(mainCalls.contains("dev/yinghuang/legacyforgebridge/compat/LegacyPlainEntityRegistry#loadMod(Ljava/lang/String;)V"));
        assertTrue(clientCalls.contains("dev/yinghuang/legacyforgebridge/compat/LegacyHeldItemVisibilityRegistry#loadMod(Ljava/lang/String;)V"));
        assertTrue(clientCalls.contains("dev/yinghuang/legacyforgebridge/render/ConvertedGridPotPresentationRuntime#initializeMod(Ljava/lang/String;)V"));
        assertTrue(clientCalls.contains("dev/yinghuang/legacyforgebridge/render/ConvertedPlainEntityPresentationRuntime#initializeMod(Ljava/lang/String;)V"));
        assertTrue(clientCalls.contains("dev/yinghuang/legacyforgebridge/render/ConvertedSeatBedPresentationRuntime#initializeMod(Ljava/lang/String;)V"));
        assertTrue(clientCalls.contains("dev/yinghuang/legacyforgebridge/render/ConvertedPlantPresentationRuntime#initializeMod(Ljava/lang/String;)V"));
        assertTrue(clientCalls.contains("dev/yinghuang/legacyforgebridge/render/ConvertedVariantSnowballPresentationRuntime#initializeMod(Ljava/lang/String;)V"));
    }

    private ConversionContext context(Path staging) {
        LegacyModMetadata metadata = new LegacyModMetadata(
                "ExampleLegacy.jar",
                "mcmod.info",
                List.of(new LegacyModMetadata.ModEntry("examplelegacy", "Example Legacy", "1.0", "1.7.10", List.of()))
        );
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "ExampleLegacy.jar", 1, 0, true, true, 1, 1, 0, 0,
                Set.of("cpw/mods/fml/common/Mod"), Set.of(), Set.of()
        );
        return new ConversionContext(
                tempDir.resolve("ExampleLegacy.jar"), staging, tempDir.resolve("candidate.jar"),
                "abc", 1L, metadata, analysis, new DiagnosticCollector(), "test"
        );
    }
}
