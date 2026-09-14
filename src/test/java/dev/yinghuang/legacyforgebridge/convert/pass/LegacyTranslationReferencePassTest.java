package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.Hashing;
import dev.yinghuang.legacyforgebridge.convert.LegacyConversionEngine;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class LegacyTranslationReferencePassTest {
    @TempDir Path temp;
    private static final String KEY = "item.example.name";
    private static final String ALIAS = "lfb.converted.examplelegacy." + KEY;

    private static byte[] fixture(String owner, String name, boolean interrupted) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "example/TranslationFixture", null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "translate", "()Ljava/lang/String;", null, null);
        method.visitCode(); method.visitLdcInsn(KEY);
        if (interrupted) method.visitInsn(Opcodes.NOP);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, "(Ljava/lang/String;)Ljava/lang/String;", false);
        method.visitInsn(Opcodes.ARETURN); method.visitMaxs(1, 0); method.visitEnd(); writer.visitEnd();
        return writer.toByteArray();
    }
    private static List<String> constants(byte[] data) {
        var strings = new ArrayList<String>();
        new ClassReader(data).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitLdcInsn(Object value) { if (value instanceof String text) strings.add(text); }
                };
            }
        }, 0);
        return strings;
    }
    @Test void retargetsOnlyKnownDirectDeobfuscatedAndSrgCalls() {
        for (String name : List.of("translateToLocal", "func_74838_a")) {
            byte[] input = fixture("net/minecraft/util/StatCollector", name, false);
            var result = LegacyTranslationReferencePass.rewrite(input, Map.of(KEY, ALIAS));
            assertEquals(1, result.calls()); assertEquals(List.of(ALIAS), constants(result.bytecode()));
            assertEquals(List.of(KEY), constants(input));
        }
    }
    @Test void unrelatedStringsOwnersAndInterruptedStackUseRemainByteIdentical() {
        for (byte[] input : List.of(fixture("example/Other", "translateToLocal", false),
                fixture("net/minecraft/util/StatCollector", "unrelated", false),
                fixture("net/minecraft/util/StatCollector", "translateToLocal", true))) {
            var result = LegacyTranslationReferencePass.rewrite(input, Map.of(KEY, ALIAS));
            assertEquals(0, result.calls()); assertArrayEquals(input, result.bytecode());
        }
    }
    @Test void unrecognizedKeyAndSecondRewriteAreNoops() {
        byte[] input = fixture("net/minecraft/util/StatCollector", "translateToLocal", false);
        assertArrayEquals(input, LegacyTranslationReferencePass.rewrite(input, Map.of("other", ALIAS)).bytecode());
        var first = LegacyTranslationReferencePass.rewrite(input, Map.of(KEY, ALIAS));
        var second = LegacyTranslationReferencePass.rewrite(first.bytecode(), Map.of(KEY, ALIAS));
        assertEquals(0, second.calls()); assertArrayEquals(first.bytecode(), second.bytecode());
    }
    @Test void realConversionPipelineRewritesReferencesButRemainsPartialAndDeterministic() throws Exception {
        Path source = temp.resolve("ExampleLegacy.jar");
        try (var jar = new JarOutputStream(Files.newOutputStream(source))) {
            entry(jar, "mcmod.info", "[{\"modid\":\"examplelegacy\",\"name\":\"Example\",\"version\":\"1.0\",\"mcversion\":\"1.7.10\",\"dependencies\":[]}]".getBytes(StandardCharsets.UTF_8));
            entry(jar, "assets/examplelegacy/lang/en_US.lang", (KEY + "=Example\n").getBytes(StandardCharsets.UTF_8));
            entry(jar, "example/TranslationFixture.class", fixture("net/minecraft/util/StatCollector", "translateToLocal", false));
        }
        var engine = new LegacyConversionEngine();
        var first = engine.convert(source, temp.resolve("a"), temp.resolve("ma"));
        var second = engine.convert(source, temp.resolve("b"), temp.resolve("mb"));
        assertEquals(ConversionStatus.PARTIAL, first.status()); assertFalse(first.installable());
        assertTrue(first.appliedPasses().contains("legacy-translation-direct-references"));
        assertEquals(Hashing.sha256(first.candidateJar().orElseThrow()), Hashing.sha256(second.candidateJar().orElseThrow()));
        try (var jar = new JarFile(first.candidateJar().orElseThrow().toFile())) {
            try (var stream = jar.getInputStream(jar.getJarEntry("example/TranslationFixture.class"))) {
                assertEquals(List.of(ALIAS), constants(stream.readAllBytes()));
            }
            var manifest = JsonParser.parseString(Files.readString(first.manifestFile())).getAsJsonObject();
            assertFalse(manifest.get("installable").getAsBoolean());
        }
    }
    private static void entry(JarOutputStream jar, String name, byte[] data) throws Exception {
        jar.putNextEntry(new JarEntry(name)); jar.write(data); jar.closeEntry();
    }
}
