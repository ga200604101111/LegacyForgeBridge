package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Extracts exact Forge 1.7.x {@code @Mod} network identities without loading source classes. */
public final class LegacyFmlModAnnotationAnalyzer {
    private static final String MOD_ANNOTATION = "Lcpw/mods/fml/common/Mod;";

    public Analysis analyze(Path jarPath) throws IOException {
        List<ModAnnotation> discovered = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            List<JarEntry> entries = jar.stream()
                    .filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".class"))
                    .sorted(Comparator.comparing(JarEntry::getName))
                    .toList();
            for (JarEntry entry : entries) {
                try (InputStream input = jar.getInputStream(entry)) {
                    read(new ClassReader(input), discovered);
                } catch (RuntimeException malformed) {
                    diagnostics.add("Unreadable @Mod annotation class " + entry.getName()
                            + ": " + malformed.getClass().getSimpleName());
                }
            }
        }

        Map<String, ModAnnotation> unique = new LinkedHashMap<>();
        LinkedHashSet<String> conflicting = new LinkedHashSet<>();
        for (ModAnnotation annotation : discovered) {
            String key = annotation.modId().toLowerCase(Locale.ROOT);
            if (conflicting.contains(key)) continue;
            ModAnnotation previous = unique.putIfAbsent(key, annotation);
            if (previous != null && !sameNetworkIdentity(previous, annotation)) {
                unique.remove(key);
                conflicting.add(key);
                diagnostics.add("Conflicting Forge @Mod network identities for " + annotation.modId()
                        + ": " + previous.sourceClass() + " versus " + annotation.sourceClass());
            }
        }
        return new Analysis(List.copyOf(unique.values()), List.copyOf(diagnostics));
    }

    private static void read(ClassReader reader, List<ModAnnotation> output) {
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            private String sourceClass;

            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                sourceClass = name;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!MOD_ANNOTATION.equals(descriptor)) return null;
                return new AnnotationVisitor(Opcodes.ASM9) {
                    private final Map<String, String> values = new LinkedHashMap<>();

                    @Override
                    public void visit(String name, Object value) {
                        if (value instanceof String text) values.put(name, text);
                    }

                    @Override
                    public void visitEnd() {
                        String modId = values.getOrDefault("modid", "").trim();
                        if (modId.isEmpty()) return;
                        output.add(new ModAnnotation(
                                sourceClass,
                                modId,
                                values.getOrDefault("name", modId),
                                values.getOrDefault("version", ""),
                                values.getOrDefault("acceptedMinecraftVersions", ""),
                                values.getOrDefault("acceptableRemoteVersions", ""),
                                values.getOrDefault("dependencies", "")
                        ));
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private static boolean sameNetworkIdentity(ModAnnotation first, ModAnnotation second) {
        return first.modId().equalsIgnoreCase(second.modId())
                && first.version().equals(second.version())
                && first.acceptableRemoteVersions().equals(second.acceptableRemoteVersions());
    }

    public record ModAnnotation(
            String sourceClass,
            String modId,
            String name,
            String version,
            String acceptedMinecraftVersions,
            String acceptableRemoteVersions,
            String dependencies
    ) { }

    public record Analysis(List<ModAnnotation> mods, List<String> diagnostics) {
        public Analysis {
            mods = List.copyOf(mods);
            diagnostics = List.copyOf(diagnostics);
        }

        public Optional<ModAnnotation> find(String modId) {
            if (modId == null) return Optional.empty();
            return mods.stream().filter(mod -> mod.modId().equalsIgnoreCase(modId)).findFirst();
        }
    }
}
