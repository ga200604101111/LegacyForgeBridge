package dev.longyu.legacyforgebridge.mixin.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViaComponentTranslationMixinBytecodeTest {
    @Test
    void jsonCallbacksUseCoercedObjectInsteadOfUnshadedGsonDescriptor() throws Exception {
        String resource = "/dev/longyu/legacyforgebridge/mixin/client/ViaComponentTranslationMixin.class";
        try (InputStream stream = ViaComponentTranslationMixinBytecodeTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "Missing compiled mixin class");

            Set<String> callbacks = Set.of(
                    "legacyforgebridge$captureJsonKey",
                    "legacyforgebridge$restoreJsonKey"
            );
            Set<String> visited = new HashSet<>();
            String coerceDescriptor = Type.getDescriptor(Coerce.class);

            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions
                ) {
                    if (!callbacks.contains(name)) {
                        return null;
                    }

                    visited.add(name);
                    assertTrue(
                            descriptor.contains("Ljava/lang/Object;"),
                            () -> name + " must accept the relocated Via Gson object through Object: " + descriptor
                    );
                    assertFalse(
                            descriptor.contains("Lcom/google/gson/"),
                            () -> name + " must not statically reference unshaded Gson: " + descriptor
                    );
                    assertFalse(
                            descriptor.contains("Lcom/viaversion/viaversion/libs/gson/"),
                            () -> name + " must not lock to Via's shaded Gson package either: " + descriptor
                    );

                    return new MethodVisitor(Opcodes.ASM9) {
                        private boolean coerceSeen;

                        @Override
                        public AnnotationVisitor visitParameterAnnotation(
                                int parameter,
                                String annotationDescriptor,
                                boolean visible
                        ) {
                            if (parameter == 1 && coerceDescriptor.equals(annotationDescriptor)) {
                                coerceSeen = true;
                            }
                            return super.visitParameterAnnotation(parameter, annotationDescriptor, visible);
                        }

                        @Override
                        public void visitEnd() {
                            assertTrue(coerceSeen, () -> name + " JSON object parameter must be annotated @Coerce");
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            assertTrue(visited.containsAll(callbacks), "Both JSON Mixin callbacks must be present");
        }
    }
}
