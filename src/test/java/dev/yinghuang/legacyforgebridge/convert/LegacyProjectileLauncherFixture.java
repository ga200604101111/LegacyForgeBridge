package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Synthetic, renamed Java 7 bytecode with no real mod or game classes bundled. */
final class LegacyProjectileLauncherFixture {
    static final String TARGET = "arbitrary/throwable/AstralOrb";
    static final String OTHER = "arbitrary/throwable/OtherOrb";
    static final String ITEM = "arbitrary/items/ChargedCaster";
    static final String SECOND_ITEM = "arbitrary/items/SecondCaster";
    static final String ITEM_BASE = "arbitrary/items/CasterBase";
    static final String WORLD = "net/minecraft/world/World";
    static final String PLAYER = "net/minecraft/entity/player/EntityPlayer";
    static final String STACK = "net/minecraft/item/ItemStack";
    static final String ENTITY = "net/minecraft/entity/Entity";
    static final String SPAWN = "(L" + ENTITY + ";)Z";
    static final String STOP = "(L" + STACK + ";L" + WORLD + ";L" + PLAYER + ";I)V";
    static final String RIGHT = "(L" + STACK + ";L" + WORLD + ";L" + PLAYER + ";)L" + STACK + ";";
    static final String SHOT_CTOR = "(L" + WORLD + ";L" + PLAYER + ";)V";

    enum Shape {
        RELEASE_DIRECT, RIGHT_CLICK_ALIASES, SOURCE_INHERITED_CALLBACK, SPAWNS_OTHER_WITH_TARGET_ALLOCATED,
        FACTORY_RESULT, STATIC_WORLD_RECEIVER, NON_CALLBACK, MULTIPLE_SPAWNS,
        MERGED_TARGET_ALLOCATIONS, MISSING_CONSTRUCTOR_DESCRIPTOR
    }

    static Path jar(Path destination, Shape shape) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(destination))) {
            put(output, TARGET + ".class", projectile(TARGET));
            put(output, OTHER + ".class", projectile(OTHER));
            boolean inherited = shape == Shape.SOURCE_INHERITED_CALLBACK;
            put(output, ITEM + ".class", item(ITEM, inherited ? ITEM_BASE : "net/minecraft/item/Item",
                    inherited ? null : shape));
            if (inherited) put(output, ITEM_BASE + ".class", item(ITEM_BASE, "net/minecraft/item/Item", shape));
            put(output, SECOND_ITEM + ".class", item(SECOND_ITEM, "net/minecraft/item/Item", Shape.RELEASE_DIRECT));
            put(output, "arbitrary/items/Factory.class", factory());
        }
        return destination;
    }

    private static byte[] projectile(String type) {
        ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        out.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, type, null,
                "net/minecraft/entity/projectile/EntityThrowable", null);
        MethodVisitor c = out.visitMethod(Opcodes.ACC_PUBLIC, "<init>", SHOT_CTOR, null, null);
        c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD, 0);
        c.visitVarInsn(Opcodes.ALOAD, 1);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/entity/projectile/EntityThrowable",
                "<init>", "(L" + WORLD + ";)V", false);
        c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();
        out.visitEnd(); return out.toByteArray();
    }

    private static byte[] item(String name, String parent, Shape shape) {
        ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        out.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, parent, null);
        if (shape == Shape.STATIC_WORLD_RECEIVER)
            out.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, "world", "L" + WORLD + ";", null,null).visitEnd();
        MethodVisitor ctor = out.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.visitCode();ctor.visitVarInsn(Opcodes.ALOAD,0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false);
        ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(0,0);ctor.visitEnd();
        if (shape != null) {
            boolean click=shape == Shape.RIGHT_CLICK_ALIASES;
            MethodVisitor callback=out.visitMethod(Opcodes.ACC_PUBLIC,
                    shape == Shape.NON_CALLBACK ? "arbitraryMethod" : click ? "onItemRightClick":"onPlayerStoppedUsing",
                    click?RIGHT:STOP,null,null);
            callback.visitCode();
            if (shape == Shape.MERGED_TARGET_ALLOCATIONS) {
                callback.visitVarInsn(Opcodes.ALOAD,3);
                Label branch=new Label(), exit=new Label();
                callback.visitJumpInsn(Opcodes.IFNULL,branch);
                allocation(callback, TARGET, 2,3);
                callback.visitVarInsn(Opcodes.ASTORE,5);
                callback.visitJumpInsn(Opcodes.GOTO,exit);
                callback.visitLabel(branch);
                allocation(callback, TARGET, 2,3);
                callback.visitVarInsn(Opcodes.ASTORE,5);
                callback.visitLabel(exit);
                callback.visitVarInsn(Opcodes.ALOAD,2);
                callback.visitVarInsn(Opcodes.ALOAD,5);
                spawn(callback);
            } else if (shape == Shape.SPAWNS_OTHER_WITH_TARGET_ALLOCATED) {
                allocation(callback,TARGET,2,3);
                callback.visitInsn(Opcodes.POP);
                callback.visitVarInsn(Opcodes.ALOAD,2);
                allocation(callback,OTHER,2,3);
                spawn(callback);
            } else if (shape == Shape.FACTORY_RESULT) {
                callback.visitVarInsn(Opcodes.ALOAD,2);
                callback.visitMethodInsn(Opcodes.INVOKESTATIC,"arbitrary/items/Factory", "get",
                        "()L" + ENTITY + ";",false);
                spawn(callback);
            } else if (shape == Shape.RIGHT_CLICK_ALIASES) {
                callback.visitVarInsn(Opcodes.ALOAD,2);
                callback.visitVarInsn(Opcodes.ASTORE,4);
                allocation(callback,TARGET,4,3);
                callback.visitVarInsn(Opcodes.ASTORE,5);
                callback.visitVarInsn(Opcodes.ALOAD,4);
                callback.visitVarInsn(Opcodes.ALOAD,5);
                spawn(callback);
            } else if (shape == Shape.STATIC_WORLD_RECEIVER) {
                callback.visitFieldInsn(Opcodes.GETSTATIC,name,"world","L"+WORLD+";");
                allocation(callback,TARGET,2,3);
                spawn(callback);
            } else {
                callback.visitVarInsn(Opcodes.ALOAD,2);
                if (shape == Shape.MISSING_CONSTRUCTOR_DESCRIPTOR) {
                    callback.visitTypeInsn(Opcodes.NEW,TARGET);callback.visitInsn(Opcodes.DUP);
                    callback.visitVarInsn(Opcodes.ALOAD,2);callback.visitVarInsn(Opcodes.ALOAD,3);
                    callback.visitMethodInsn(Opcodes.INVOKESPECIAL,TARGET,"<init>",
                            "(L"+WORLD+";Lnet/minecraft/entity/EntityLivingBase;)V",false);
                } else allocation(callback,TARGET,2,3);
                spawn(callback);
                if(shape == Shape.MULTIPLE_SPAWNS) {
                    callback.visitVarInsn(Opcodes.ALOAD,2);
                    allocation(callback,TARGET,2,3);
                    spawn(callback);
                }
            }
            if(click) {callback.visitVarInsn(Opcodes.ALOAD,1);callback.visitInsn(Opcodes.ARETURN);}
            else callback.visitInsn(Opcodes.RETURN);
            callback.visitMaxs(0,0);callback.visitEnd();
        }
        out.visitEnd(); return out.toByteArray();
    }

    private static void allocation(MethodVisitor m, String type, int worldIndex, int playerIndex) {
        m.visitTypeInsn(Opcodes.NEW,type);m.visitInsn(Opcodes.DUP);
        m.visitVarInsn(Opcodes.ALOAD,worldIndex);m.visitVarInsn(Opcodes.ALOAD,playerIndex);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,type,"<init>",SHOT_CTOR,false);
    }
    private static void spawn(MethodVisitor m) {
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,WORLD,"spawnEntityInWorld",SPAWN,false);
        m.visitInsn(Opcodes.POP);
    }
    private static byte[] factory(){
        ClassWriter out = new ClassWriter(0);
        out.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "arbitrary/items/Factory",null,"java/lang/Object",null);
        MethodVisitor m=out.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"get", "()L"+ENTITY+";",null,null);
        m.visitCode();m.visitInsn(Opcodes.ACONST_NULL);m.visitInsn(Opcodes.ARETURN);m.visitMaxs(1,0);m.visitEnd();
        out.visitEnd();return out.toByteArray();
    }
    private static void put(JarOutputStream output,String path,byte[] bytes) throws IOException {
        output.putNextEntry(new JarEntry(path));output.write(bytes);output.closeEntry();
    }
}
