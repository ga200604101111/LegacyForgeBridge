package dev.yinghuang.legacyforgebridge.convert;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class LegacyJarWriter {
    private LegacyJarWriter() {
    }

    static void writeDeterministic(Path stagingDir, Path outputJar) throws IOException {
        Files.createDirectories(outputJar.getParent());
        Path temporary = outputJar.resolveSibling(outputJar.getFileName().toString() + ".tmp");
        Files.deleteIfExists(temporary);

        List<Path> files;
        try (Stream<Path> stream = Files.walk(stagingDir)) {
            files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> entryName(stagingDir, path)))
                    .toList();
        }

        try (OutputStream output = Files.newOutputStream(temporary);
             ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) {
            byte[] buffer = new byte[8192];
            for (Path file : files) {
                ZipEntry entry = new ZipEntry(entryName(stagingDir, file));
                entry.setTime(0L);
                zip.putNextEntry(entry);
                try (var input = Files.newInputStream(file)) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) {
                            zip.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }

        try {
            Files.move(
                    temporary,
                    outputJar,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, outputJar, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String entryName(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
