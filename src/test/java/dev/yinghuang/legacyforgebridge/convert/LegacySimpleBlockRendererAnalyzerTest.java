package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class LegacySimpleBlockRendererAnalyzerTest {
    private static final String IDS = "foreign/simple/RenderIds";

    @TempDir
    Path tempDir;

    @Test
    void unrelatedNamespaceClassifiesOnlyBoundSimpleRendererFamilies() throws Exception {
        Path jar=sourceFixture(tempDir);

        var analysis = new LegacySimpleBlockRendererAnalyzer().analyze(jar);
        Map<String, LegacySimpleBlockRendererAnalyzer.Rule> rules = analysis.rules().stream()
                .collect(Collectors.toMap(LegacySimpleBlockRendererAnalyzer.Rule::registryName, Function.identity()));

        assertEquals(LegacySimpleBlockRendererAnalyzer.Mode.CROSS, rules.get("cross").mode());
        assertEquals("foreign/simple/CrossRenderer", rules.get("cross").sourceRendererClass());
        assertNotNull(rules.get("cross").bounds());assertEquals(.25F,rules.get("cross").bounds().minX(),0.0001F);
        assertEquals(.75F,rules.get("cross").bounds().maxZ(),0.0001F);assertTrue(rules.get("cross").emptyCollision());
        assertEquals(LegacySimpleBlockRendererAnalyzer.Mode.CROP, rules.get("crop").mode());
        assertNotNull(rules.get("crop").bounds());assertTrue(rules.get("crop").emptyCollision());
        assertEquals(LegacySimpleBlockRendererAnalyzer.Mode.META_ZERO_CROP_ELSE_STANDARD, rules.get("meta").mode());
        assertEquals(LegacySimpleBlockRendererAnalyzer.Mode.CROSS, rules.get("srgCtorCross").mode());
        assertEquals(LegacySimpleBlockRendererAnalyzer.Mode.CROP, rules.get("srgCtorCrop").mode());
        assertEquals(LegacySimpleBlockRendererAnalyzer.RenderOffset.NONE,rules.get("srgCtorCross").renderOffset());
        assertEquals(LegacySimpleBlockRendererAnalyzer.RenderOffset.NONE,rules.get("srgCtorCrop").renderOffset());
        assertFalse(rules.containsKey("fakeMeta"),
                "Reading metadata and then rendering both families sequentially must not prove a metadata branch");

        assertFalse(rules.containsKey("noise"),
                "An otherwise-cross renderer with an unproved source callback must fail closed");
        assertTrue(analysis.diagnostics().stream().anyMatch(value -> value.contains("noise") || value.contains("NoiseRenderer")),
                analysis.diagnostics().toString());
    }


    @Test
    void exactLegacyCoordinateSeedFormulaIsRequiredForXyzOffset() {
        MethodNode method=new MethodNode(Opcodes.ASM9,Opcodes.ACC_PUBLIC,"render","()V",null,null);
        method.instructions.add(new LdcInsnNode(3129871));
        method.instructions.add(new LdcInsnNode(116129781L));
        method.instructions.add(new LdcInsnNode(42317861L));
        method.instructions.add(new LdcInsnNode(11L));
        method.instructions.add(new LdcInsnNode(15L));
        method.instructions.add(new LdcInsnNode(15F));
        method.instructions.add(new LdcInsnNode(.5D));
        method.instructions.add(new LdcInsnNode(.2D));
        for(int shift:new int[]{16,20,24}){
            method.instructions.add(new LdcInsnNode(shift));
            method.instructions.add(new InsnNode(Opcodes.LSHR));
            method.instructions.add(new InsnNode(Opcodes.LAND));
        }
        assertTrue(LegacySimpleBlockRendererAnalyzer.provesVanillaXyzOffset(method));
        method.instructions.remove(method.instructions.getFirst());
        assertFalse(LegacySimpleBlockRendererAnalyzer.provesVanillaXyzOffset(method),
                "nearby random-offset code without the exact x seed constant must fail closed");
    }

    static Path sourceFixture(Path directory)throws Exception {
        Path jar = directory.resolve("foreign-simple-renderers.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, IDS + ".class", ids());
            put(out, "foreign/simple/CrossBlock.class", block("foreign/simple/CrossBlock", "cross"));
            put(out, "foreign/simple/CropBlock.class", block("foreign/simple/CropBlock", "crop"));
            put(out, "foreign/simple/MetaBlock.class", block("foreign/simple/MetaBlock", "meta"));
            put(out, "foreign/simple/FakeMetaBlock.class", block("foreign/simple/FakeMetaBlock", "fakeMeta"));
            put(out, "foreign/simple/NoiseBlock.class", block("foreign/simple/NoiseBlock", "noise"));
            put(out, "foreign/simple/ModeBlock.class", modeBlock());
            put(out, "foreign/simple/CrossRenderer.class", renderer("foreign/simple/CrossRenderer", "cross"));
            put(out, "foreign/simple/CropRenderer.class", renderer("foreign/simple/CropRenderer", "crop"));
            put(out, "foreign/simple/MetaRenderer.class", renderer("foreign/simple/MetaRenderer", "meta"));
            put(out, "foreign/simple/FakeMetaRenderer.class", renderer("foreign/simple/FakeMetaRenderer", "fakeMeta"));
            put(out, "foreign/simple/NoiseRenderer.class", renderer("foreign/simple/NoiseRenderer", "noise"));
            put(out, "foreign/simple/CoordinateRenderer.class", coordinateRenderer());
            put(out, "foreign/simple/Bootstrap.class", bootstrap());
            put(out, "foreign/simple/Bindings.class", bindings());
        }

        return jar;
    }

    private static byte[] ids() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, IDS, null, "java/lang/Object", null);
        for (String field : new String[]{"cross", "crop", "meta", "fakeMeta", "noise", "coord"}) {
            writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, field, "I", null, null).visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] block(String name, String renderField) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/block/Block", null);

        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        init.visitVarInsn(Opcodes.ALOAD,0);init.visitLdcInsn(.25F);init.visitInsn(Opcodes.FCONST_0);init.visitLdcInsn(.25F);
        init.visitLdcInsn(.75F);init.visitInsn(Opcodes.FCONST_1);init.visitLdcInsn(.75F);
        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"setBlockBounds","(FFFFFF)V",false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor render = writer.visitMethod(Opcodes.ACC_PUBLIC, "getRenderType", "()I", null, null);
        render.visitCode();
        render.visitFieldInsn(Opcodes.GETSTATIC, IDS, renderField, "I");
        render.visitInsn(Opcodes.IRETURN);
        render.visitMaxs(0, 0);
        render.visitEnd();

        MethodVisitor collision=writer.visitMethod(Opcodes.ACC_PUBLIC,"getCollisionBoundingBoxFromPool",
                "(Lnet/minecraft/world/World;III)Lnet/minecraft/util/AxisAlignedBB;",null,null);
        collision.visitCode();collision.visitInsn(Opcodes.ACONST_NULL);collision.visitInsn(Opcodes.ARETURN);
        collision.visitMaxs(0,0);collision.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] renderer(String name, String mode) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);

        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor render = writer.visitMethod(Opcodes.ACC_PUBLIC, "renderWorldBlock",
                "(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/block/Block;III)V", null, null);
        render.visitCode();
        switch (mode) {
            case "cross" -> renderCall(render, "func_147765_a");
            case "crop" -> renderCall(render, "renderBlockCrops");
            case "meta" -> {
                Label standard = new Label();
                Label end = new Label();
                metadata(render);
                render.visitJumpInsn(Opcodes.IFNE, standard);
                renderCall(render, "func_147796_n");
                render.visitJumpInsn(Opcodes.GOTO, end);
                render.visitLabel(standard);
                renderCall(render, "func_147784_q");
                render.visitLabel(end);
            }
            case "fakeMeta" -> {
                metadata(render);
                render.visitInsn(Opcodes.POP);
                renderCall(render, "renderBlockCrops");
                renderCall(render, "renderStandardBlock");
            }
            case "noise" -> {
                renderCall(render, "drawCrossedSquares");
                render.visitVarInsn(Opcodes.ALOAD, 0);
                render.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, "unprovedSourceCallback", "()V", false);
            }
            default -> throw new IllegalArgumentException(mode);
        }
        render.visitInsn(Opcodes.RETURN);
        render.visitMaxs(0, 0);
        render.visitEnd();

        if (mode.equals("noise")) {
            MethodVisitor helper = writer.visitMethod(Opcodes.ACC_PRIVATE, "unprovedSourceCallback", "()V", null, null);
            helper.visitCode();
            helper.visitInsn(Opcodes.RETURN);
            helper.visitMaxs(0, 0);
            helper.visitEnd();
        }

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void metadata(MethodVisitor method) {
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitVarInsn(Opcodes.ILOAD, 4);
        method.visitVarInsn(Opcodes.ILOAD, 5);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World",
                "getBlockMetadata", "(III)I", false);
    }

    private static void renderCall(MethodVisitor method, String name) {
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/renderer/RenderBlocks", name, "()V", false);
    }

    private static byte[] modeBlock() {
        String name = "foreign/simple/ModeBlock";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/block/Block", null);
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "mode", "I", null, null).visitEnd();

        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(I)V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitVarInsn(Opcodes.ILOAD, 1);
        init.visitFieldInsn(Opcodes.PUTFIELD, name, "mode", "I");
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor renderType = writer.visitMethod(Opcodes.ACC_PUBLIC, "getRenderType", "()I", null, null);
        renderType.visitCode();
        renderType.visitFieldInsn(Opcodes.GETSTATIC, IDS, "coord", "I");
        renderType.visitInsn(Opcodes.IRETURN);
        renderType.visitMaxs(0, 0);
        renderType.visitEnd();

        MethodVisitor mode = writer.visitMethod(Opcodes.ACC_PUBLIC, "getCoordinateRenderType", "()I", null, null);
        mode.visitCode();
        mode.visitVarInsn(Opcodes.ALOAD, 0);
        mode.visitFieldInsn(Opcodes.GETFIELD, name, "mode", "I");
        mode.visitInsn(Opcodes.IRETURN);
        mode.visitMaxs(0, 0);
        mode.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] coordinateRenderer() {
        String name = "foreign/simple/CoordinateRenderer";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor render = writer.visitMethod(Opcodes.ACC_PUBLIC, "renderWorldBlock",
                "(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/block/Block;III)V", null, null);
        render.visitCode();
        render.visitVarInsn(Opcodes.ALOAD, 2);
        render.visitTypeInsn(Opcodes.CHECKCAST, "foreign/simple/ModeBlock");
        render.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "foreign/simple/ModeBlock", "getCoordinateRenderType", "()I", false);
        render.visitInsn(Opcodes.POP);
        renderCall(render, "func_147765_a");
        renderCall(render, "func_147795_a");
        render.visitInsn(Opcodes.RETURN);
        render.visitMaxs(0, 0);
        render.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/simple/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        method.visitCode();
        register(method, "foreign/simple/CrossBlock", "cross");
        register(method, "foreign/simple/CropBlock", "crop");
        register(method, "foreign/simple/MetaBlock", "meta");
        register(method, "foreign/simple/FakeMetaBlock", "fakeMeta");
        register(method, "foreign/simple/NoiseBlock", "noise");
        registerMode(method, 0, "srgCtorCross");
        registerMode(method, 1, "srgCtorCrop");
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void registerMode(MethodVisitor method, int mode, String id) {
        String type = "foreign/simple/ModeBlock";
        method.visitTypeInsn(Opcodes.NEW, type);
        method.visitInsn(Opcodes.DUP);
        method.visitIntInsn(Opcodes.BIPUSH, mode);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, type, "<init>", "(I)V", false);
        method.visitLdcInsn(id);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
    }

    private static void register(MethodVisitor method, String type, String id) {
        method.visitTypeInsn(Opcodes.NEW, type);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, type, "<init>", "()V", false);
        method.visitLdcInsn(id);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
    }

    private static byte[] bindings() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/simple/Bindings", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "bind", "()V", null, null);
        method.visitCode();
        bind(method, "cross", "foreign/simple/CrossRenderer");
        bind(method, "crop", "foreign/simple/CropRenderer");
        bind(method, "meta", "foreign/simple/MetaRenderer");
        bind(method, "fakeMeta", "foreign/simple/FakeMetaRenderer");
        bind(method, "noise", "foreign/simple/NoiseRenderer");
        bind(method, "coord", "foreign/simple/CoordinateRenderer");
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void bind(MethodVisitor method, String field, String renderer) {
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitFieldInsn(Opcodes.GETSTATIC, IDS, field, "I");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        method.visitTypeInsn(Opcodes.NEW, renderer);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, renderer, "<init>", "()V", false);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
        method.visitInsn(Opcodes.POP);
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
