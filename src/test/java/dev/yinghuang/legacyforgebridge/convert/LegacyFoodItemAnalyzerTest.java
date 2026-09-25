package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyFoodItemAnalyzerTest {
    @TempDir Path tempDir;

    @Test void unrelatedItemFoodSemanticsAreProvenAndUnsafeVariantsFailClosed() throws Exception {
        Path jar = tempDir.resolve("ForeignFood.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/snack/SimpleSnack.class", directFood("foreign/snack/SimpleSnack", 5, 0.7F, false, false, false, false));
            put(out, "foreign/snack/AlwaysSnack.class", directFood("foreign/snack/AlwaysSnack", 2, 0.2F, false, true, false, false));
            put(out, "foreign/snack/DefaultSaturation.class", twoArgFood("foreign/snack/DefaultSaturation", 3, false));
            put(out, "foreign/snack/ParamSnack.class", parameterFood());
            put(out, "foreign/snack/WolfMeat.class", directFood("foreign/snack/WolfMeat", 6, 0.8F, true, false, false, false));
            put(out, "foreign/snack/PotionSnack.class", directFood("foreign/snack/PotionSnack", 4, 0.4F, false, false, true, false));
            put(out, "foreign/snack/CustomSnack.class", directFood("foreign/snack/CustomSnack", 4, 0.4F, false, false, false, true));
            put(out, "foreign/snack/Bootstrap.class", bootstrap());
        }

        var analysis = new LegacyFoodItemAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), analysis.diagnostics().toString());
        assertEquals(5, analysis.rules().size(), analysis.skipped().toString());
        assertEquals(2, analysis.skipped().size());

        Map<String,LegacyFoodItemAnalyzer.Rule> rules = analysis.rules().stream()
                .collect(Collectors.toMap(LegacyFoodItemAnalyzer.Rule::registryName, Function.identity()));
        assertEquals(5, rules.get("simple").nutrition());
        assertEquals(0.7F, rules.get("simple").saturationModifier());
        assertFalse(rules.get("simple").alwaysEdible());
        assertFalse(rules.get("simple").wolfFavorite());
        assertEquals(2, rules.get("always").nutrition());
        assertTrue(rules.get("always").alwaysEdible());
        assertEquals(0.6F, rules.get("default_sat").saturationModifier());
        assertEquals(7, rules.get("param").nutrition());
        assertEquals(0.4F, rules.get("param").saturationModifier());
        assertTrue(rules.get("wolf").wolfFavorite());

        String skipped = analysis.skipped().toString();
        assertTrue(skipped.contains("potion-effect"), skipped);
        assertTrue(skipped.contains("Custom ItemFood consume callback"), skipped);
    }

    private static byte[] directFood(String name, int nutrition, float saturation, boolean wolf,
                                     boolean always, boolean potion, boolean custom) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/item/ItemFood", null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        pushInt(init, nutrition);
        init.visitLdcInsn(saturation);
        init.visitInsn(wolf ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/item/ItemFood", "<init>", "(IFZ)V", false);
        if (always) {
            init.visitVarInsn(Opcodes.ALOAD, 0);
            init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, "setAlwaysEdible", "()Lnet/minecraft/item/ItemFood;", false);
            init.visitInsn(Opcodes.POP);
        }
        if (potion) {
            init.visitVarInsn(Opcodes.ALOAD, 0);
            init.visitInsn(Opcodes.ICONST_1);
            init.visitInsn(Opcodes.ICONST_2);
            init.visitInsn(Opcodes.ICONST_0);
            init.visitLdcInsn(1F);
            init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, "setPotionEffect", "(IIIF)Lnet/minecraft/item/ItemFood;", false);
            init.visitInsn(Opcodes.POP);
        }
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        if (custom) {
            MethodVisitor eaten = w.visitMethod(Opcodes.ACC_PUBLIC, "onEaten",
                    "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
                    null, null);
            eaten.visitCode();
            eaten.visitVarInsn(Opcodes.ALOAD, 1);
            eaten.visitInsn(Opcodes.ARETURN);
            eaten.visitMaxs(0, 0);
            eaten.visitEnd();
        }
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] twoArgFood(String name, int nutrition, boolean wolf) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/item/ItemFood", null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        pushInt(init, nutrition);
        init.visitInsn(wolf ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/item/ItemFood", "<init>", "(IZ)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] parameterFood() {
        String name = "foreign/snack/ParamSnack";
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/item/ItemFood", null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(IF)V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitVarInsn(Opcodes.ILOAD, 1);
        init.visitVarInsn(Opcodes.FLOAD, 2);
        init.visitInsn(Opcodes.ICONST_0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/item/ItemFood", "<init>", "(IFZ)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String owner = "foreign/snack/Bootstrap";
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        m.visitCode();
        registerNoArg(m, "foreign/snack/SimpleSnack", "simple");
        registerNoArg(m, "foreign/snack/AlwaysSnack", "always");
        registerNoArg(m, "foreign/snack/DefaultSaturation", "default_sat");
        m.visitTypeInsn(Opcodes.NEW, "foreign/snack/ParamSnack");
        m.visitInsn(Opcodes.DUP);
        pushInt(m, 7);
        m.visitLdcInsn(0.4F);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/snack/ParamSnack", "<init>", "(IF)V", false);
        m.visitLdcInsn("param");
        register(m);
        registerNoArg(m, "foreign/snack/WolfMeat", "wolf");
        registerNoArg(m, "foreign/snack/PotionSnack", "potion");
        registerNoArg(m, "foreign/snack/CustomSnack", "custom");
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void registerNoArg(MethodVisitor m, String type, String id) {
        m.visitTypeInsn(Opcodes.NEW, type);
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, type, "<init>", "()V", false);
        m.visitLdcInsn(id);
        register(m);
    }

    private static void register(MethodVisitor m) {
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V", false);
    }

    private static void pushInt(MethodVisitor m, int value) {
        if (value >= -1 && value <= 5) m.visitInsn(Opcodes.ICONST_0 + value);
        else m.visitIntInsn(Opcodes.BIPUSH, value);
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
