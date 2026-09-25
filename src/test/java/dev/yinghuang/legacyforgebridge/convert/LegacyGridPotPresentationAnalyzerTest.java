package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyGridPotPresentationAnalyzerTest {
    @Test
    void linksExactRenderFieldToUniqueRendererConstruction() {
        Map<String,ClassNode> classes = new LinkedHashMap<>();
        classes.put("third/render/GridRenderer", rendererSkeleton());

        ClassNode bootstrap = clazz("third/render/Bootstrap", "java/lang/Object");
        MethodNode init = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "init", "()V", null, null);
        init.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, bootstrap.name, "MAP", "Ljava/util/HashMap;"));
        init.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "third/render/Ids", "MULTI", "I"));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
        init.instructions.add(new TypeInsnNode(Opcodes.NEW, "third/render/GridRenderer"));
        init.instructions.add(new InsnNode(Opcodes.DUP));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "third/render/GridRenderer", "<init>", "()V", false));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/util/HashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false));
        init.instructions.add(new InsnNode(Opcodes.POP));
        init.instructions.add(new InsnNode(Opcodes.RETURN));
        bootstrap.methods.add(init);
        classes.put(bootstrap.name, bootstrap);

        assertEquals("third/render/GridRenderer",
                LegacyGridPotPresentationAnalyzer.findRendererForIdentity(classes, "third/render/Ids", "MULTI"));
        assertNull(LegacyGridPotPresentationAnalyzer.findRendererForIdentity(classes, "third/render/Ids", "OTHER"));
    }

    @Test
    void provesBoundedStoredContentPresentationShape() {
        ClassNode renderer = rendererSkeleton();
        MethodNode ctor = renderer.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
        ctor.instructions.insertBefore(ctor.instructions.getLast(), floatArrayLiteral());

        MethodNode world = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "renderBlock", "()V", null, null);
        world.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "third/tile/GridTile", "isEnable", "(I)Z", false));
        world.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, renderer.name, "drawCell", "()V", false));
        world.instructions.add(new InsnNode(Opcodes.RETURN));
        renderer.methods.add(world);

        MethodNode draw = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE, "drawCell", "()V", null, null);
        draw.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "third/tile/GridTile", "getItem", "(I)Lnet/minecraft/item/Item;", false));
        draw.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "third/tile/GridTile", "getMeta", "(I)I", false));
        draw.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/block/Block", "func_149634_a", "(Lnet/minecraft/item/Item;)Lnet/minecraft/block/Block;", false));
        draw.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/block/Block", "func_149645_b", "()I", false));
        draw.instructions.add(new InsnNode(Opcodes.ICONST_1));
        draw.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 13));
        draw.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 40));
        draw.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "third/render/Ids", "CROSS", "I"));
        for (float value : new float[]{0.375F, 4.0F, 16.0F, 0.75F, 0.125F}) draw.instructions.add(new LdcInsnNode(value));
        for (int i = 0; i < 4; i++) draw.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/renderer/RenderBlocks", "bounds" + i, "(DDDDDD)V", false));
        for (int i = 0; i < 4; i++) draw.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/renderer/RenderBlocks", "standard" + i, "(Lnet/minecraft/block/Block;III)Z", false));
        draw.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/renderer/RenderBlocks", "cross", "(Lnet/minecraft/util/IIcon;DDDF)V", false));
        draw.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/renderer/Tessellator", "move", "(FFF)V", false));
        draw.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/renderer/Tessellator", "move", "(FFF)V", false));
        draw.instructions.add(new InsnNode(Opcodes.RETURN));
        renderer.methods.add(draw);

        assertTrue(LegacyGridPotPresentationAnalyzer.rendererPresentationShape(renderer,
                "third/tile/GridTile", "isEnable", "getItem", "getMeta", Set.of("third/render/Ids#CROSS")));
        assertFalse(LegacyGridPotPresentationAnalyzer.rendererPresentationShape(renderer,
                "third/tile/GridTile", "isEnable", "getItem", "getMeta", Set.of("third/render/Ids#OTHER")));
    }

    private static InsnList floatArrayLiteral() {
        InsnList list = new InsnList();
        list.add(new InsnNode(Opcodes.ICONST_3));
        list.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_FLOAT));
        list.add(new InsnNode(Opcodes.DUP)); list.add(new InsnNode(Opcodes.ICONST_0)); list.add(new LdcInsnNode(-0.333F)); list.add(new InsnNode(Opcodes.FASTORE));
        list.add(new InsnNode(Opcodes.DUP)); list.add(new InsnNode(Opcodes.ICONST_1)); list.add(new InsnNode(Opcodes.FCONST_0)); list.add(new InsnNode(Opcodes.FASTORE));
        list.add(new InsnNode(Opcodes.DUP)); list.add(new InsnNode(Opcodes.ICONST_2)); list.add(new LdcInsnNode(0.333F)); list.add(new InsnNode(Opcodes.FASTORE));
        list.add(new InsnNode(Opcodes.POP));
        return list;
    }

    private static ClassNode rendererSkeleton() {
        ClassNode node = clazz("third/render/GridRenderer", "java/lang/Object");
        MethodNode ctor = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(ctor);
        return node;
    }

    private static ClassNode clazz(String name, String superName) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_7;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = name;
        node.superName = superName;
        return node;
    }
}
