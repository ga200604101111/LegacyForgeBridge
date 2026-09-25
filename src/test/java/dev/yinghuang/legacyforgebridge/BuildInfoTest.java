package dev.yinghuang.legacyforgebridge;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class BuildInfoTest {
    @Test
    void converterMetadataIsRuntimeInitializedSoCallersCannotInlineStaleFingerprints() throws Exception {
        Set<String> expected = Set.of("VERSION", "CONVERSION_SCHEMA", "CONVERTER_REVISION");
        Set<String> visited = new HashSet<>();
        try (InputStream input = BuildInfo.class.getResourceAsStream("BuildInfo.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if (expected.contains(name)) {
                        visited.add(name);
                        assertNull(value, name + " must not carry a ConstantValue attribute");
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertEquals(expected, visited);
        assertEquals("2026-09-24.188-resource-case-fingerprint", BuildInfo.CONVERTER_REVISION);
    }
}
