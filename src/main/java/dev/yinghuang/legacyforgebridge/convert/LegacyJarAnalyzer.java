package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class LegacyJarAnalyzer {
    private static final String[] FORGE_PREFIXES = {
            "cpw/mods/fml/",
            "net/minecraftforge/"
    };

    private static final String[] COREMOD_MARKERS = {
            "cpw/mods/fml/relauncher/IFMLLoadingPlugin",
            "net/minecraft/launchwrapper/IClassTransformer"
    };

    private static final String[] OPENGL_PREFIXES = {
            "org/lwjgl/opengl/GL11",
            "org/lwjgl/opengl/GL12",
            "org/lwjgl/opengl/GL13",
            "org/lwjgl/opengl/GL14",
            "org/lwjgl/opengl/GL15",
            "org/lwjgl/opengl/GL20",
            "org/lwjgl/opengl/GL30"
    };

    public Analysis analyze(Path jarPath) throws IOException {
        MutableAnalysis state = new MutableAnalysis();

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            state.hasMcmodInfo = jar.getJarEntry("mcmod.info") != null;
            state.hasManifest = jar.getManifest() != null;

            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }

                state.classCount++;
                try (InputStream input = jar.getInputStream(entry)) {
                    inspectClass(new ClassReader(input), state);
                } catch (RuntimeException malformedClass) {
                    state.unreadableClasses++;
                }
            }
        }

        return new Analysis(
                jarPath.getFileName().toString(),
                state.classCount,
                state.unreadableClasses,
                state.hasMcmodInfo,
                state.hasManifest,
                state.forgeReferences.size(),
                state.minecraftReferences.size(),
                state.coremodReferences.size(),
                state.openglReferences.size(),
                Set.copyOf(state.forgeReferences),
                Set.copyOf(state.coremodReferences),
                Set.copyOf(state.openglReferences)
        );
    }

    private static void inspectClass(ClassReader reader, MutableAnalysis state) {
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                inspectReference(name, state);
                inspectReference(superName, state);
                if (interfaces != null) {
                    for (String itf : interfaces) {
                        inspectReference(itf, state);
                    }
                }
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                inspectReference(descriptor, state);
                return null;
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                inspectReference(descriptor, state);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                inspectReference(descriptor, state);
                if (exceptions != null) {
                    for (String exception : exceptions) {
                        inspectReference(exception, state);
                    }
                }

                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        inspectReference(type, state);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        inspectReference(owner, state);
                        inspectReference(descriptor, state);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        inspectReference(owner, state);
                        inspectReference(descriptor, state);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String stringValue) {
                            inspectReference(stringValue.replace('.', '/'), state);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private static void inspectReference(String reference, MutableAnalysis state) {
        if (reference == null || reference.isEmpty()) {
            return;
        }

        String normalized = reference.replace('.', '/');
        if (normalized.contains("net/minecraft/")) {
            state.minecraftReferences.add(normalized);
        }
        for (String prefix : FORGE_PREFIXES) {
            if (normalized.contains(prefix)) {
                state.forgeReferences.add(normalized);
            }
        }
        for (String marker : COREMOD_MARKERS) {
            if (normalized.contains(marker)) {
                state.coremodReferences.add(marker);
            }
        }
        for (String marker : OPENGL_PREFIXES) {
            if (normalized.contains(marker)) {
                state.openglReferences.add(marker);
            }
        }
    }

    public record Analysis(
            String fileName,
            int classCount,
            int unreadableClasses,
            boolean hasMcmodInfo,
            boolean hasManifest,
            int forgeReferenceCount,
            int minecraftReferenceCount,
            int coremodReferenceCount,
            int openglReferenceCount,
            Set<String> forgeReferences,
            Set<String> coremodReferences,
            Set<String> openglReferences
    ) {
        public boolean likelyForgeMod() {
            return hasMcmodInfo || forgeReferenceCount > 0;
        }

        public boolean requiresManualCoremodReview() {
            return coremodReferenceCount > 0;
        }
    }

    private static final class MutableAnalysis {
        int classCount;
        int unreadableClasses;
        boolean hasMcmodInfo;
        boolean hasManifest;
        final Set<String> forgeReferences = new LinkedHashSet<>();
        final Set<String> minecraftReferences = new LinkedHashSet<>();
        final Set<String> coremodReferences = new LinkedHashSet<>();
        final Set<String> openglReferences = new LinkedHashSet<>();
    }
}
