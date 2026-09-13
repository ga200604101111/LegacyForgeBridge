package dev.longyu.legacyforgebridge.convert.pass;

import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Removes obsolete .lang inputs only after semantic passes have finished inspecting them. */
public final class LegacyLanguageCleanupPass implements ConversionPass {
    @Override
    public String id() {
        return "legacy-lang-cleanup";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        List<Path> legacyFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(context.stagingDir())) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> LegacyLanguagePass.isLegacyLanguagePath(context.stagingDir(), path))
                    .sorted()
                    .forEach(legacyFiles::add);
        }

        for (Path file : legacyFiles) {
            Files.deleteIfExists(file);
        }

        context.diagnostics().info(
                "LFB-CONVERT-LANG-0003",
                SupportLevel.AUTO,
                "Removed " + legacyFiles.size() + " obsolete legacy .lang source files after semantic conversion completed."
        );
    }
}
