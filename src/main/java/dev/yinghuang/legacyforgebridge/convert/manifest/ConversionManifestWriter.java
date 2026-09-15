package dev.longyu.legacyforgebridge.convert.manifest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.longyu.legacyforgebridge.BuildInfo;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionDiagnostic;
import dev.longyu.legacyforgebridge.convert.api.ConversionStatus;
import dev.longyu.legacyforgebridge.convert.api.LegacyModMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ConversionManifestWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConversionManifestWriter() {
    }

    public static JsonObject create(ConversionContext context, ConversionStatus status, boolean installable) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", BuildInfo.CONVERSION_SCHEMA);
        root.addProperty("converterVersion", BuildInfo.VERSION);
        root.addProperty("converterRevision", BuildInfo.CONVERTER_REVISION);
        root.addProperty("profile", context.profileId());
        root.addProperty("status", status.name().toLowerCase());
        root.addProperty("installable", installable);

        JsonObject source = new JsonObject();
        source.addProperty("file", context.metadata().sourceFileName());
        source.addProperty("sha256", context.sourceHash());
        source.addProperty("size", context.sourceSize());
        root.add("source", source);

        JsonObject mod = new JsonObject();
        mod.addProperty("fabricId", context.metadata().fabricId());
        mod.addProperty("metadataSource", context.metadata().metadataSource());
        JsonArray logicalMods = new JsonArray();
        for (LegacyModMetadata.ModEntry entry : context.metadata().mods()) {
            JsonObject logical = new JsonObject();
            logical.addProperty("modid", entry.modId());
            logical.addProperty("name", entry.name());
            logical.addProperty("version", entry.version());
            logical.addProperty("mcversion", entry.mcVersion());
            JsonArray dependencies = new JsonArray();
            entry.dependencies().forEach(value -> dependencies.add(value));
            logical.add("dependencies", dependencies);
            logicalMods.add(logical);
        }
        mod.add("logicalMods", logicalMods);
        root.add("mod", mod);

        var analysis = context.analysis();
        JsonObject analysisJson = new JsonObject();
        analysisJson.addProperty("classes", analysis.classCount());
        analysisJson.addProperty("unreadableClasses", analysis.unreadableClasses());
        analysisJson.addProperty("hasMcmodInfo", analysis.hasMcmodInfo());
        analysisJson.addProperty("hasManifest", analysis.hasManifest());
        analysisJson.addProperty("forgeReferences", analysis.forgeReferenceCount());
        analysisJson.addProperty("minecraftReferences", analysis.minecraftReferenceCount());
        analysisJson.addProperty("coremodReferences", analysis.coremodReferenceCount());
        analysisJson.addProperty("openglReferences", analysis.openglReferenceCount());
        root.add("analysis", analysisJson);

        JsonArray passes = new JsonArray();
        context.appliedPasses().forEach(value -> passes.add(value));
        root.add("passes", passes);

        JsonObject registries = new JsonObject();
        for (Map.Entry<String, Map<String, String>> category : context.registryIdentities().entrySet()) {
            JsonObject identities = new JsonObject();
            category.getValue().forEach((legacy, modern) -> identities.addProperty(legacy, modern));
            registries.add(category.getKey(), identities);
        }
        root.add("registries", registries);

        JsonArray diagnostics = new JsonArray();
        for (ConversionDiagnostic diagnostic : context.diagnostics().snapshot()) {
            JsonObject value = new JsonObject();
            value.addProperty("ruleId", diagnostic.ruleId());
            value.addProperty("severity", diagnostic.severity().name().toLowerCase());
            value.addProperty("support", diagnostic.supportLevel().name().toLowerCase());
            value.addProperty("message", diagnostic.message());
            diagnostics.add(value);
        }
        root.add("diagnostics", diagnostics);
        return root;
    }

    public static void writeEmbedded(ConversionContext context, JsonObject manifest) throws IOException {
        Path output = context.stagingDir().resolve("legacyforgebridge/conversion-manifest.json");
        write(output, manifest);
    }

    public static void writeSidecar(Path output, JsonObject manifest) throws IOException {
        write(output, manifest);
    }

    private static void write(Path output, JsonObject manifest) throws IOException {
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(manifest) + "\n", StandardCharsets.UTF_8);
    }
}
