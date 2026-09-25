package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Determines whether legacy coremod bytecode is actually activated by the source artifact.
 *
 * <p>Some ordinary Forge mods bundled dormant IFMLLoadingPlugin/IClassTransformer classes without
 * declaring them in the manifest. Presence of those marker interfaces alone is therefore not proof
 * that Forge would execute the transformer. This analyzer keeps the safety gate strict for an
 * activated coremod while avoiding false-positive blocking for dormant helper classes.</p>
 */
public final class LegacyCoremodActivationAnalyzer {
    private static final int MAX_TRANSFORMERS = 64;

    public record Analysis(
            boolean markerBytecodePresent,
            boolean declared,
            String pluginClass,
            List<String> transformerClasses,
            boolean transformerListProven,
            List<String> diagnostics
    ) {
        public Analysis {
            transformerClasses = List.copyOf(transformerClasses);
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean activated() {
            return declared;
        }
    }

    public Analysis analyze(Path jarPath, LegacyJarAnalyzer.Analysis jarAnalysis) throws IOException {
        boolean markers = jarAnalysis != null && jarAnalysis.coremodReferenceCount() > 0;
        List<String> diagnostics = new ArrayList<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Manifest manifest = jar.getManifest();
            String plugin = manifestValue(manifest, "FMLCorePlugin");
            if (plugin == null || plugin.isBlank()) {
                if (markers) {
                    diagnostics.add("Coremod marker bytecode is present but META-INF/MANIFEST.MF does not declare FMLCorePlugin; transformer classes are dormant for ordinary Forge coremod discovery.");
                }
                return new Analysis(markers, false, null, List.of(), true, diagnostics);
            }

            plugin = plugin.trim();
            JarEntry pluginEntry = jar.getJarEntry(plugin.replace('.', '/') + ".class");
            if (pluginEntry == null) {
                diagnostics.add("Manifest declares FMLCorePlugin=" + plugin + " but the plugin class is missing from the source JAR.");
                return new Analysis(markers, true, plugin, List.of(), false, diagnostics);
            }

            byte[] bytes;
            try (InputStream input = jar.getInputStream(pluginEntry)) {
                bytes = input.readAllBytes();
            }
            TransformerArrayExtractor extractor = new TransformerArrayExtractor();
            new ClassReader(bytes).accept(extractor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (!extractor.found) {
                diagnostics.add("Declared coremod plugin does not expose a statically provable getASMTransformerClass() result.");
                return new Analysis(markers, true, plugin, List.of(), false, diagnostics);
            }
            if (!extractor.proven) {
                diagnostics.add("Declared coremod transformer list depends on unsupported/dynamic bytecode and requires manual review.");
            }
            return new Analysis(markers, true, plugin, extractor.values(), extractor.proven, diagnostics);
        }
    }

    private static String manifestValue(Manifest manifest, String name) {
        if (manifest == null) return null;
        Attributes attributes = manifest.getMainAttributes();
        String direct = attributes.getValue(name);
        if (direct != null) return direct;
        for (Object key : attributes.keySet()) {
            String candidate = String.valueOf(key);
            if (candidate.equalsIgnoreCase(name)) return attributes.getValue(candidate);
        }
        return null;
    }

    /** Accepts the normal compiler shape used by IFMLLoadingPlugin#getASMTransformerClass. */
    private static final class TransformerArrayExtractor extends ClassVisitor {
        private boolean found;
        private boolean proven;
        private final Set<String> values = new LinkedHashSet<>();

        TransformerArrayExtractor() {
            super(Opcodes.ASM9);
        }

        List<String> values() {
            return List.copyOf(values);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (!name.equals("getASMTransformerClass") || !descriptor.equals("()[Ljava/lang/String;")) return null;
            found = true;
            return new MethodVisitor(Opcodes.ASM9) {
                private final List<Object> stack = new ArrayList<>();
                private String[] array;
                private boolean invalid;

                private Object pop() {
                    if (stack.isEmpty()) {
                        invalid = true;
                        return null;
                    }
                    return stack.remove(stack.size() - 1);
                }

                private void push(Object value) {
                    if (stack.size() > 256) invalid = true;
                    else stack.add(value);
                }

                @Override public void visitInsn(int opcode) {
                    switch (opcode) {
                        case Opcodes.ICONST_M1 -> push(-1);
                        case Opcodes.ICONST_0 -> push(0);
                        case Opcodes.ICONST_1 -> push(1);
                        case Opcodes.ICONST_2 -> push(2);
                        case Opcodes.ICONST_3 -> push(3);
                        case Opcodes.ICONST_4 -> push(4);
                        case Opcodes.ICONST_5 -> push(5);
                        case Opcodes.ACONST_NULL -> push(null);
                        case Opcodes.DUP -> {
                            Object value = pop(); push(value); push(value);
                        }
                        case Opcodes.AASTORE -> {
                            Object value = pop(); Object index = pop(); Object target = pop();
                            if (!(target instanceof String[] strings) || !(index instanceof Integer i)
                                    || i < 0 || i >= strings.length || !(value instanceof String text)) {
                                invalid = true;
                            } else {
                                strings[i] = text;
                            }
                        }
                        case Opcodes.ARETURN -> {
                            Object result = pop();
                            if (!invalid && result instanceof String[] strings && strings.length <= MAX_TRANSFORMERS) {
                                for (String value : strings) {
                                    if (value == null || value.isBlank()) { invalid = true; break; }
                                    values.add(value);
                                }
                                proven = !invalid;
                            } else invalid = true;
                        }
                        default -> {
                            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) return;
                            // Array construction methods should be side-effect free; any other stack
                            // manipulation/control behavior is deliberately rejected.
                            if (opcode != Opcodes.NOP) invalid = true;
                        }
                    }
                }

                @Override public void visitIntInsn(int opcode, int operand) {
                    if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) push(operand);
                    else invalid = true;
                }

                @Override public void visitLdcInsn(Object value) {
                    if (value instanceof String text) push(text); else if (value instanceof Integer i) push(i); else invalid = true;
                }

                @Override public void visitTypeInsn(int opcode, String type) {
                    if (opcode == Opcodes.ANEWARRAY && type.equals("java/lang/String")) {
                        Object size = pop();
                        if (size instanceof Integer count && count >= 0 && count <= MAX_TRANSFORMERS) {
                            array = new String[count]; push(array);
                        } else invalid = true;
                    } else invalid = true;
                }

                @Override public void visitVarInsn(int opcode, int varIndex) { invalid = true; }
                @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) { invalid = true; }
                @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) { invalid = true; }
                @Override public void visitJumpInsn(int opcode, Label label) { invalid = true; }
                @Override public void visitInvokeDynamicInsn(String name, String descriptor, org.objectweb.asm.Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) { invalid = true; }
                @Override public void visitMultiANewArrayInsn(String descriptor, int numDimensions) { invalid = true; }
                @Override public void visitEnd() { if (invalid) proven = false; }
            };
        }
    }
}
