package dev.longyu.legacyforgebridge.convert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class OldModScanner {
    public List<Path> scan(Path oldModsDir) throws IOException {
        if (!Files.isDirectory(oldModsDir)) {
            return List.of();
        }

        try (var stream = Files.list(oldModsDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }
}
