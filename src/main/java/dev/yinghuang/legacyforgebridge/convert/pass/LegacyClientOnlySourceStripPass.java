package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyCandidateReferenceAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Finalizes a server-authoritative client compatibility candidate by removing legacy JVM classes.
 *
 * <p>The Forge 1.7.10 server remains authoritative for gameplay. The converted client artifact
 * contains only LFB-generated Java 21 entrypoint/registration/presentation bytecode plus resources
 * and evidence. Source bytes are removed as one transaction, then the live candidate is scanned for
 * incoming class/resource references. Any incomplete closure or surviving reference restores every
 * byte removed by this pass and rejects the candidate.</p>
 */
public final class LegacyClientOnlySourceStripPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/client-only-source-strip.json";
    private static final String GENERATED_PREFIX = "dev/yinghuang/legacyforgebridge/generated/";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-client-only-source-strip";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        // Do not turn a resource-only JAR with no generated client baseline into a misleading mod.
        if (!Files.isRegularFile(context.stagingDir().resolve(LegacyClientContentBaselinePass.OUTPUT))) return;

        List<String> sourceClasses = sourceClasses(context);
        LinkedHashMap<String, byte[]> originalBytes = new LinkedHashMap<>();
        int alreadyRetired = 0;
        for (String sourceClass : sourceClasses) {
            if (sourceClass.startsWith(GENERATED_PREFIX)) {
                throw new IOException("Legacy source class collides with reserved generated namespace: " + sourceClass);
            }
            Path classFile = safeResolve(context.stagingDir(), sourceClass + ".class");
            if (Files.isRegularFile(classFile)) originalBytes.put(sourceClass, Files.readAllBytes(classFile));
            else alreadyRetired++;
        }

        try {
            for (String sourceClass : originalBytes.keySet())
                Files.delete(safeResolve(context.stagingDir(), sourceClass + ".class"));
        } catch (IOException deletionFailure) {
            restore(context.stagingDir(), originalBytes);
            throw deletionFailure;
        }

        List<String> remaining = stagedClasses(context.stagingDir());
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        List<String> unexpected = remaining.stream()
                .filter(name -> !name.startsWith(GENERATED_PREFIX))
                .toList();
        unexpected.forEach(name -> blockers.add("unexpected-candidate-class:" + name));

        Set<String> targets = new LinkedHashSet<>(sourceClasses);
        LegacyCandidateReferenceAnalyzer.Analysis references;
        try {
            references = new LegacyCandidateReferenceAnalyzer().analyze(context.stagingDir(), targets);
        } catch (IOException scanFailure) {
            restore(context.stagingDir(), originalBytes);
            throw scanFailure;
        }
        if (!references.classReferenceClosureComplete())
            blockers.add("post-strip-class-reference-scan-incomplete");
        if (!references.resourceReferenceClosureComplete())
            blockers.add("post-strip-resource-reference-scan-incomplete");
        for (String target : sourceClasses) {
            var evidence = references.forTarget(target);
            for (String incoming : evidence.incomingClassReferences())
                blockers.add("post-strip-incoming-reference:" + target + "<-" + incoming);
            for (String resource : evidence.resourceReferences())
                blockers.add("post-strip-resource-reference:" + target + "<-" + resource);
        }

        boolean restored = !blockers.isEmpty();
        if (restored) restore(context.stagingDir(), originalBytes);
        writeEvidence(context, sourceClasses.size(), originalBytes.size(), alreadyRetired,
                remaining, unexpected, references, blockers, restored);

        if (restored) {
            context.diagnostics().warning(
                    "LFB-CONVERT-CLIENT-STRIP-0002",
                    SupportLevel.MANUAL_REQUIRED,
                    "Rejected client-only source strip and restored removed bytes; blockers=" + blockers + "."
            );
            throw new IOException("Client-only source strip reference closure failed: " + blockers);
        }

        context.diagnostics().info(
                "LFB-CONVERT-CLIENT-STRIP-0001",
                SupportLevel.ADAPTED,
                "Finalized server-authoritative client candidate: sourceClasses=" + sourceClasses.size()
                        + ", deleted=" + originalBytes.size()
                        + ", alreadyRetired=" + alreadyRetired
                        + ", generatedClasses=" + remaining.size()
                        + ", postStripReferenceClosure=true."
        );
    }

    private static void writeEvidence(
            ConversionContext context,
            int sourceClassCount,
            int deleted,
            int alreadyRetired,
            List<String> remaining,
            List<String> unexpected,
            LegacyCandidateReferenceAnalyzer.Analysis references,
            Set<String> blockers,
            boolean restored) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 2);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("candidateMode", "forge-1.7.10-server-authoritative-client");
        root.addProperty("sourceClassCount", sourceClassCount);
        root.addProperty("deletedSourceClasses", deleted);
        root.addProperty("alreadyRetiredSourceClasses", alreadyRetired);
        root.addProperty("remainingSourceClasses", restored ? deleted : 0);
        root.addProperty("generatedClassCount", remaining.size());
        root.addProperty("postStripReferenceCheckWired", true);
        root.addProperty("classReferenceClosureComplete", references.classReferenceClosureComplete());
        root.addProperty("resourceReferenceClosureComplete", references.resourceReferenceClosureComplete());
        root.addProperty("loaderClassClosureComplete", blockers.isEmpty());
        root.addProperty("restoredAfterFailedStrip", restored);
        root.add("generatedClasses", strings(remaining));
        root.add("unexpectedClasses", strings(unexpected));
        root.add("referenceDiagnostics", strings(references.diagnostics()));
        root.add("blockers", strings(blockers));
        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
    }

    private static void restore(Path staging, Map<String, byte[]> originalBytes) throws IOException {
        IOException failure = null;
        for (Map.Entry<String, byte[]> entry : originalBytes.entrySet()) {
            try {
                Path path = safeResolve(staging, entry.getKey() + ".class");
                Files.createDirectories(path.getParent());
                Files.write(path, entry.getValue());
            } catch (IOException restoreFailure) {
                if (failure == null) failure = restoreFailure;
                else failure.addSuppressed(restoreFailure);
            }
        }
        if (failure != null) throw failure;
    }

    private static List<String> sourceClasses(ConversionContext context) throws IOException {
        List<String> result = new ArrayList<>();
        try (JarFile jar = new JarFile(context.sourceJar().toFile())) {
            jar.stream()
                    .filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".class"))
                    .map(entry -> entry.getName().substring(0, entry.getName().length() - 6))
                    .sorted()
                    .forEach(result::add);
        }
        return List.copyOf(result);
    }

    private static List<String> stagedClasses(Path staging) throws IOException {
        List<String> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(staging)) {
            for (Path path : stream.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".class"))
                    .sorted(Comparator.naturalOrder()).toList()) {
                String relative = staging.relativize(path).toString().replace('\\', '/');
                result.add(relative.substring(0, relative.length() - 6));
            }
        }
        return List.copyOf(result);
    }

    private static Path safeResolve(Path root, String relative) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path result = normalizedRoot.resolve(relative).normalize();
        if (!result.startsWith(normalizedRoot)) throw new IOException("Unsafe staged class path: " + relative);
        return result;
    }

    private static JsonArray strings(Iterable<String> values) {
        JsonArray result = new JsonArray();
        for (String value : values) result.add(value);
        return result;
    }
}
