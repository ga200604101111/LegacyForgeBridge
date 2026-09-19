package dev.yinghuang.legacyforgebridge.compat;

import net.minecraft.resources.Identifier;

import java.util.Locale;

/** Normalizes Forge 1.7.x registry names to the same modern identity shape used by conversion. */
final class LegacyRegistryIdentity {
    private LegacyRegistryIdentity() { }

    static Identifier normalize(String rawIdentity) {
        if (rawIdentity == null) return null;
        String raw = rawIdentity.trim();
        int separator = raw.indexOf(':');
        if (separator <= 0 || separator == raw.length() - 1) return null;

        String namespace = raw.substring(0, separator).trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_")
                .replaceAll("_+", "_");
        String path = raw.substring(separator + 1).trim().toLowerCase(Locale.ROOT)
                .replace('\\', '/')
                .replaceAll("[^a-z0-9/._-]", "_")
                .replaceAll("_+", "_");
        while (path.startsWith("/")) path = path.substring(1);
        if (namespace.isBlank() || path.isBlank()) return null;

        try {
            return Identifier.fromNamespaceAndPath(namespace, path);
        } catch (RuntimeException invalidIdentifier) {
            return null;
        }
    }
}
