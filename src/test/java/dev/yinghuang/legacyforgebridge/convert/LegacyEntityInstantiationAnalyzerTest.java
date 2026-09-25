package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEntityInstantiationAnalyzerTest {
    @TempDir Path tempDir;

    @Test void inventoriesDirectConstructionAndProvesOnlyResolvableWorldSpawnArguments() throws Exception {
        Path jar = tempDir.resolve("Instantiation.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "third/entity/Orb.class", orb());
            put(out, "third/entity/Spawner.class", spawner());
            put(out, "third/entity/Bootstrap.class", bootstrap());
        }

        var analysis = new LegacyEntityInstantiationAnalyzer().analyze(jar);
        assertEquals(1, analysis.registrations().size(), analysis.diagnostics().toString());
        assertEquals("orb", analysis.registrations().getFirst().registryName());
        assertEquals("third/entity/Orb", analysis.registrations().getFirst().sourceClass());

        assertEquals(1, analysis.constructions().size());
        var construction = analysis.constructions().getFirst();
        assertEquals("third/entity/Orb", construction.sourceClass());
        assertEquals("(Lnet/minecraft/world/World;)V", construction.constructorDescriptor());
        assertEquals("third/entity/Spawner", construction.sourceOwner());

        assertEquals(2, analysis.worldSpawns().size());
        assertTrue(analysis.worldSpawns().stream().anyMatch(spawn -> spawn.sourceClassProven()
                && "third/entity/Orb".equals(spawn.sourceClass())
                && "spawnOrb".equals(spawn.sourceMethod())));
        assertTrue(analysis.worldSpawns().stream().anyMatch(spawn -> !spawn.sourceClassProven()
                && spawn.sourceClass() == null
                && "spawnUnknown".equals(spawn.sourceMethod())));
        assertEquals(1, analysis.unresolvedWorldSpawnCount());
    }

    private static byte[] orb() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/entity/Orb", null,
                "net/minecraft/entity/Entity", null);
        MethodVisitor ctor = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(Lnet/minecraft/world/World;)V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 1);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/entity/Entity", "<init>",
                "(Lnet/minecraft/world/World;)V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] spawner() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/entity/Spawner", null, "java/lang/Object", null);

        MethodVisitor spawn = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "spawnOrb",
                "(Lnet/minecraft/world/World;)V", null, null);
        spawn.visitCode();
        spawn.visitTypeInsn(Opcodes.NEW, "third/entity/Orb");
        spawn.visitInsn(Opcodes.DUP);
        spawn.visitVarInsn(Opcodes.ALOAD, 0);
        spawn.visitMethodInsn(Opcodes.INVOKESPECIAL, "third/entity/Orb", "<init>",
                "(Lnet/minecraft/world/World;)V", false);
        spawn.visitVarInsn(Opcodes.ASTORE, 1);
        spawn.visitVarInsn(Opcodes.ALOAD, 0);
        spawn.visitVarInsn(Opcodes.ALOAD, 1);
        spawn.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "func_72838_d",
                "(Lnet/minecraft/entity/Entity;)Z", false);
        spawn.visitInsn(Opcodes.POP);
        spawn.visitInsn(Opcodes.RETURN);
        spawn.visitMaxs(0, 0);
        spawn.visitEnd();

        MethodVisitor unknown = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "spawnUnknown",
                "(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;)V", null, null);
        unknown.visitCode();
        unknown.visitVarInsn(Opcodes.ALOAD, 0);
        unknown.visitVarInsn(Opcodes.ALOAD, 1);
        unknown.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "func_72838_d",
                "(Lnet/minecraft/entity/Entity;)Z", false);
        unknown.visitInsn(Opcodes.POP);
        unknown.visitInsn(Opcodes.RETURN);
        unknown.visitMaxs(0, 0);
        unknown.visitEnd();

        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/entity/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        m.visitCode();
        m.visitLdcInsn(Type.getObjectType("third/entity/Orb"));
        m.visitLdcInsn("orb");
        m.visitIntInsn(Opcodes.BIPUSH, 17);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitIntInsn(Opcodes.BIPUSH, 80);
        m.visitInsn(Opcodes.ICONST_2);
        m.visitInsn(Opcodes.ICONST_1);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/EntityRegistry", "registerModEntity",
                "(Ljava/lang/Class;Ljava/lang/String;ILjava/lang/Object;IIZ)V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
