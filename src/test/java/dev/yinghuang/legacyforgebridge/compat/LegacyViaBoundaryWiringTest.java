package dev.yinghuang.legacyforgebridge.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyViaBoundaryWiringTest {
    private static final String TARGET = "net/raphimc/vialegacy/protocol/release/r1_7_6_10tor1_8/rewriter/ItemRewriter";
    private static final String DESC = "(Lcom/viaversion/viaversion/api/connection/UserConnection;Lcom/viaversion/viaversion/api/minecraft/item/Item;)Lcom/viaversion/viaversion/api/minecraft/item/Item;";

    @Test
    void targetDeclaresBothExactRuntimeDescriptors() throws Exception {
        ClassNode target = read(TARGET);
        for (String method : List.of("handleItemToClient", "handleItemToServer")) {
            assertTrue(target.methods.stream().anyMatch(m -> m.name.equals(method) && m.desc.equals(DESC)));
        }
    }

    @Test
    void compiledMixinCapturesAtTheFirstEdgeAndRestoresAtTheLastEdge() throws Exception {
        ClassNode mixin = read("dev/yinghuang/legacyforgebridge/mixin/client/ViaLegacyModItemBoundaryMixin");
        AnnotationNode annotation = mixin.invisibleAnnotations.stream()
                .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
        assertEquals(List.of(TARGET.replace('/', '.')), value(annotation, "targets"));
        Map<String,String> hooks = new HashMap<>();
        for (MethodNode method : mixin.methods) {
            List<AnnotationNode> annotations = new ArrayList<>();
            if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
            if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
            for (AnnotationNode inject : annotations) {
                if (!inject.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) continue;
                String target = ((List<?>) value(inject, "method")).getFirst().toString();
                AnnotationNode at = (AnnotationNode) ((List<?>) value(inject, "at")).getFirst();
                hooks.put(target, (String) value(at, "value"));
            }
        }
        assertEquals("HEAD", hooks.get("handleItemToClient" + DESC));
        assertEquals("RETURN", hooks.get("handleItemToServer" + DESC));
        assertEquals(2, hooks.size());
    }

    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        throw new AssertionError("Missing annotation value: " + key);
    }
    private static ClassNode read(String name) throws Exception {
        try (var stream = LegacyViaBoundaryWiringTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream.readAllBytes()).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
            return node;
        }
    }
}
