package dev.longyu.legacyforgebridge.convert.pass;

import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Copies the legacy artifact into an isolated staging tree without loading its classes.
 */
public final class CopyLegacyJarPass implements ConversionPass {
    @Override
    public String id() {
        return "copy-legacy-jar";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        int copied = 0;
        try (JarFile jar = new JarFile(context.sourceJar().toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || shouldSkip(entry.getName())) {
                    continue;
                }

                Path target = context.stagingDir().resolve(entry.getName()).normalize();
                if (!target.startsWith(context.stagingDir())) {
                    throw new IOException("Unsafe path in legacy JAR: " + entry.getName());
                }

                Files.createDirectories(target.getParent());
                try (InputStream input = jar.getInputStream(entry)) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
                copied++;
            }
        }

        context.diagnostics().info(
                "LFB-CONVERT-RESOURCE-0001",
                SupportLevel.AUTO,
                "Copied " + copied + " legacy JAR entries into the isolated conversion staging tree."
        );
    }

    private static boolean shouldSkip(String name) {
        String normalized = name.replace('\\', '/');
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (normalized.equals("fabric.mod.json")
                || normalized.equals("legacyforgebridge/conversion-manifest.json")
                || upper.equals("META-INF/MANIFEST.MF")) {
            return true;
        }
        return upper.startsWith("META-INF/")
                && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA"));
    }
}
