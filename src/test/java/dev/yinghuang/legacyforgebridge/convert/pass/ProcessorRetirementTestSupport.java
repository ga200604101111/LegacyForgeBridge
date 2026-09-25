package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;

final class ProcessorRetirementTestSupport {
    static final String BLOCK = "foreign/machine/MachineBlock";
    static final String TILE = "foreign/machine/MachineTile";
    static final String CONTAINER = "foreign/machine/MachineContainer";
    static final String SCREEN = "foreign/machine/MachineScreen";
    static final String HELPER = BLOCK + "$Helper";
    static final String BOOTSTRAP = "foreign/machine/Bootstrap";

    private ProcessorRetirementTestSupport() { }

    static void writeCandidate(Path staging, boolean externalReference) throws Exception {
        writeClass(staging, BLOCK, minimalClass(BLOCK, TILE));
        writeClass(staging, TILE, minimalClass(TILE));
        writeClass(staging, CONTAINER, minimalClass(CONTAINER, TILE));
        writeClass(staging, SCREEN, minimalClass(SCREEN, CONTAINER));
        writeClass(staging, HELPER, minimalClass(HELPER, BLOCK));
        writeClass(staging, BOOTSTRAP, externalReference
                ? minimalClass(BOOTSTRAP, BLOCK) : minimalClass(BOOTSTRAP));
    }

    static void writeReadiness(Path staging, List<String> companions) throws Exception {
        ProcessorRetirementReadinessFixture.write(staging, companions);
    }

    static ConversionContext context(Path source, Path staging, Path temp) throws Exception {
        LegacyModMetadata metadata = new LegacyModMetadata(
                source.getFileName().toString(), "test",
                List.of(new LegacyModMetadata.ModEntry(
                        "foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                source.getFileName().toString(), 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(source, staging, temp.resolve("candidate.jar"),
                "sha", Files.size(source), metadata, analysis,
                new DiagnosticCollector(), "generic-test");
    }

    static Path emptyJar(Path path) throws Exception {
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(path))) { }
        return path;
    }

    static void writeMinimalClass(Path root, String name, String... references) throws Exception {
        writeClass(root, name, minimalClass(name, references));
    }

    static void writeClass(Path root, String name, byte[] bytes) throws Exception {
        Path path = classPath(root, name);
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }

    static Path classPath(Path root, String name) { return root.resolve(name + ".class"); }

    static JsonObject result(Path staging) throws Exception {
        return JsonParser.parseString(Files.readString(
                staging.resolve(LegacySingleInputProcessorRetirementPass.OUTPUT),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static Set<String> strings(JsonArray values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        values.forEach(value -> result.add(value.getAsString()));
        return result;
    }

    private static byte[] minimalClass(String name, String... references) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name,
                null, "java/lang/Object", null);
        for (int index = 0; index < references.length; index++)
            writer.visitField(Opcodes.ACC_PRIVATE, "reference" + index,
                    "L" + references[index] + ";", null, null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
