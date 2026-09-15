package dev.longyu.legacyforgebridge.convert;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves version-owned recipe values after source extraction, without changing analyzer evidence.
 * Unknown platform fields remain FieldValue so later passes can fail closed rather than inventing
 * an identity.
 */
public final class LegacyRecipeValueResolver {
    public LegacyRecipeAnalyzer.Analysis resolve(LegacyRecipeAnalyzer.Analysis analysis) {
        List<LegacyRecipeAnalyzer.Registration> registrations = new ArrayList<>(analysis.registrations().size());
        for (var registration : analysis.registrations()) registrations.add(resolve(registration));
        return new LegacyRecipeAnalyzer.Analysis(registrations, analysis.diagnostics());
    }

    public LegacyRecipeAnalyzer.Registration resolve(LegacyRecipeAnalyzer.Registration registration) {
        return new LegacyRecipeAnalyzer.Registration(
                registration.kind(),
                registration.arguments().stream().map(this::resolve).toList(),
                registration.sourceOwner(),
                registration.sourceMethod(),
                registration.sourceDescriptor());
    }

    public LegacyRecipeAnalyzer.Value resolve(LegacyRecipeAnalyzer.Value value) {
        if (value instanceof LegacyRecipeAnalyzer.FieldValue field) {
            var platform = LegacyVanillaRegistry1710.resolve(field);
            if (platform.isPresent()) {
                var entry = platform.get();
                return new LegacyRecipeAnalyzer.RegistryValue(
                        entry.kind(), entry.registryName(), "minecraft", field.owner(), field.name());
            }
            return field;
        }
        if (value instanceof LegacyRecipeAnalyzer.ObjectValue object) {
            return new LegacyRecipeAnalyzer.ObjectValue(
                    object.internalName(),
                    object.constructorDescriptor(),
                    object.constructorArguments().stream().map(this::resolve).toList());
        }
        if (value instanceof LegacyRecipeAnalyzer.ArrayValue array) {
            return new LegacyRecipeAnalyzer.ArrayValue(array.elements().stream().map(this::resolve).toList());
        }
        return value;
    }
}
