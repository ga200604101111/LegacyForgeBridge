package dev.yinghuang.legacyforgebridge.compat;

import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.Map;

/** Normalizes Forge 1.7.x registry names and resolves conversion-manifest aliases. */
public final class LegacyRegistryIdentity {
    private LegacyRegistryIdentity() { }

    public static Identifier resolve(String rawIdentity, Map<String, Identifier> aliases) {
        Identifier normalized = normalize(rawIdentity);
        if (normalized == null) return null;
        Identifier aliased = aliases == null ? null : aliases.get(normalized.toString());
        return aliased == null ? normalized : aliased;
    }

    public static String canonical(String rawIdentity) {
        Identifier normalized = normalize(rawIdentity);
        return normalized == null ? null : normalized.toString();
    }

    public static Identifier normalize(String rawIdentity) {
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
