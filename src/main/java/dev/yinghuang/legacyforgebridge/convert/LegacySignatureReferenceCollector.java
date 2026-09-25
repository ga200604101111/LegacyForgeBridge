package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.Set;

/** Collects class identities carried only by JVM generic Signature attributes. */
final class LegacySignatureReferenceCollector {
    private LegacySignatureReferenceCollector() { }

    static void classOrMethod(Set<String> references, String signature) {
        if (signature == null || signature.isBlank()) return;
        new SignatureReader(signature).accept(new Collector(references));
    }

    static void field(Set<String> references, String signature) {
        if (signature == null || signature.isBlank()) return;
        new SignatureReader(signature).acceptType(new Collector(references));
    }

    private static final class Collector extends SignatureVisitor {
        private final Set<String> references;
        private String currentClass;

        private Collector(Set<String> references) {
            super(Opcodes.ASM9);
            this.references = references;
        }

        private SignatureVisitor nested() {
            return new Collector(references);
        }

        @Override public SignatureVisitor visitClassBound() { return nested(); }
        @Override public SignatureVisitor visitInterfaceBound() { return nested(); }
        @Override public SignatureVisitor visitSuperclass() { return nested(); }
        @Override public SignatureVisitor visitInterface() { return nested(); }
        @Override public SignatureVisitor visitParameterType() { return nested(); }
        @Override public SignatureVisitor visitReturnType() { return nested(); }
        @Override public SignatureVisitor visitExceptionType() { return nested(); }
        @Override public SignatureVisitor visitArrayType() { return nested(); }
        @Override public SignatureVisitor visitTypeArgument(char wildcard) { return nested(); }

        @Override
        public void visitClassType(String name) {
            currentClass = name;
            if (name != null && !name.isBlank()) references.add(name);
        }

        @Override
        public void visitInnerClassType(String name) {
            if (name == null || name.isBlank()) return;
            currentClass = currentClass == null ? name : currentClass + "$" + name;
            references.add(currentClass);
        }
    }
}
