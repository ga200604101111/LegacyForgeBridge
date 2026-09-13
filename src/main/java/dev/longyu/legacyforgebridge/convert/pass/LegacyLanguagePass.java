package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Converts Forge/Minecraft 1.7.10 .lang resources to modern JSON.
 *
 * <p>Legacy translation keys are collision-prone because Minecraft merges language entries from
 * every resource namespace into one global translation table. Generated entries therefore use
 * collision-free LFB aliases and the old->new identity is recorded in the conversion manifest.
 * Later bytecode/content passes must retarget translated content to those aliases.</p>
 */
public final class LegacyLanguagePass implements ConversionPass {
    private static final Pattern NUMERIC_PLACEHOLDER = Pattern.compile("%(\\d+\\$)?[\\d.]*[df]");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-lang-to-json";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        List<Path> langFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(context.stagingDir())) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> isLegacyLanguagePath(context.stagingDir(), path))
                    .sorted()
                    .forEach(langFiles::add);
        }

        int entries = 0;
        for (Path langFile : langFiles) {
            entries += convertLanguageFile(context, langFile);
        }

        context.diagnostics().info(
                "LFB-CONVERT-LANG-0001",
                SupportLevel.AUTO,
                "Converted " + langFiles.size() + " legacy .lang files containing " + entries + " entries to modern JSON resources."
        );

        if (entries > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-LANG-0002",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Legacy translation keys were moved to collision-free LFB aliases. Content/bytecode translation-key references must use the manifest mapping before this candidate is installable."
            );
        }
    }

    private static boolean isLegacyLanguagePath(Path stagingDir, Path file) {
        Path relative = stagingDir.relativize(file);
        if (relative.getNameCount() < 4) {
            return false;
        }
        return relative.getName(0).toString().equals("assets")
                && relative.getName(2).toString().equals("lang")
                && relative.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".lang");
    }

    private static int convertLanguageFile(ConversionContext context, Path source) throws IOException {
        String fileName = source.getFileName().toString();
        String locale = fileName.substring(0, fileName.length() - ".lang".length()).toLowerCase(Locale.ROOT);
        Path output = source.resolveSibling(locale + ".json");

        JsonObject translations = new JsonObject();
        if (Files.isRegularFile(output)) {
            try (Reader reader = Files.newBufferedReader(output, StandardCharsets.UTF_8)) {
                JsonElement existing = JsonParser.parseReader(reader);
                if (existing.isJsonObject()) {
                    existing.getAsJsonObject().entrySet()
                            .forEach(entry -> translations.add(entry.getKey(), entry.getValue()));
                }
            } catch (RuntimeException ignored) {
                // A malformed pre-existing JSON resource should not prevent conversion of the
                // authoritative legacy .lang source.
            }
        }

        int count = 0;
        for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String legacyKey = line.substring(0, separator);
            String alias = translationAlias(context, legacyKey);
            String value = normalizePlaceholders(line.substring(separator + 1));
            translations.addProperty(alias, value);
            context.recordRegistryIdentity("translations", legacyKey, alias);
            count++;
        }

        Files.writeString(output, GSON.toJson(translations) + "\n", StandardCharsets.UTF_8);
        return count;
    }

    static String translationAlias(ConversionContext context, String legacyKey) {
        return "lfb.converted." + context.metadata().fabricId() + "." + legacyKey;
    }

    static String normalizePlaceholders(String value) {
        Matcher matcher = NUMERIC_PLACEHOLDER.matcher(value);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            String index = matcher.group(1) == null ? "" : matcher.group(1);
            matcher.appendReplacement(output, Matcher.quoteReplacement("%" + index + "s"));
        }
        matcher.appendTail(output);
        return output.toString();
    }
}
