package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Renamed 1.7.10 bytecode; no specific mod, block ID or actual server bytecode is bundled. */
final class LegacyBlockTileVisualStateFixture {
    static final String BLOCK = "foreign/decor/DirectionalLantern";
    static final String TILE = "foreign/tile/RotatingDecoration";
    static final String RENDER = "foreign/client/DecorationTESR";
    static final String MODEL = "foreign/client/DecorationModel";
    static final String PART = "net/minecraft/client/model/ModelRenderer";
    static final String MC_TILE = "net/minecraft/tileentity/TileEntity";
    static final String GL = "org/lwjgl/opengl/GL11";

    enum Form { FULL, NO_TICK, TICK_WRITES_OTHER, DRAW_WITHOUT_ANIMATION,
        NO_TILE_FIELD_READS, PACKET_HOOK, NBT_ONLY, NO_METADATA_OR_GL,
        UNREACHABLE_ANIMATION, NO_DRAW, UNDECLARED_TILE_FIELD, UNCALLED_MODEL_PIVOT }

    static Path jar(Path file, Form mode) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file))) {
            put(out, BLOCK, minimal(BLOCK, "net/minecraft/block/Block"));
            put(out, TILE, tile(mode));
            put(out, RENDER, renderer(mode));
            put(out, MODEL, model(mode));
        }
        return file;
    }
    static LegacyBlockTileModelPreflight.Candidate candidate() {
        return new LegacyBlockTileModelPreflight.Candidate(
                "unnamed_glow_block", BLOCK, TILE, "Unnamed Light", RENDER, MODEL,
                BLOCK, BLOCK, true, true, true, true, 14, true);
    }
    private static ClassWriter writer(String name, String parent) {
        var c = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        c.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, parent, null);
        var ctor = c.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(0,0); ctor.visitEnd();
        return c;
    }
    private static byte[] minimal(String name, String parent) {
        var c = writer(name, parent); c.visitEnd(); return c.toByteArray();
    }
    private static byte[] tile(Form form) {
        var c = writer(TILE, MC_TILE);
        for (String field : new String[]{"yaw", "delay", "other"})
            c.visitField(Opcodes.ACC_PUBLIC, field, "I", null, null).visitEnd();
        if (form != Form.NO_TICK) {
            var tick = c.visitMethod(Opcodes.ACC_PUBLIC,"updateEntity","()V",null,null);
            tick.visitCode();
            String target=form==Form.TICK_WRITES_OTHER?"other":"yaw";
            tick.visitVarInsn(Opcodes.ALOAD,0);
            tick.visitVarInsn(Opcodes.ALOAD,0);
            tick.visitFieldInsn(Opcodes.GETFIELD,TILE,target,"I");
            tick.visitInsn(Opcodes.ICONST_1);
            tick.visitInsn(Opcodes.IADD);
            tick.visitFieldInsn(Opcodes.PUTFIELD,TILE,target,"I");
            tick.visitInsn(Opcodes.RETURN);tick.visitMaxs(0,0);tick.visitEnd();
        }
        if(form==Form.PACKET_HOOK){
            var packet = c.visitMethod(Opcodes.ACC_PUBLIC,"getDescriptionPacket",
                    "()Lnet/minecraft/network/Packet;",null,null);
            packet.visitCode();packet.visitInsn(Opcodes.ACONST_NULL);
            packet.visitInsn(Opcodes.ARETURN);packet.visitMaxs(0,0);packet.visitEnd();
        }
        if(form==Form.NBT_ONLY){
            var write = c.visitMethod(Opcodes.ACC_PUBLIC,"writeToNBT",
                    "(Lnet/minecraft/nbt/NBTTagCompound;)V",null,null);
            write.visitCode();write.visitInsn(Opcodes.RETURN);write.visitMaxs(0,0);write.visitEnd();
            var read = c.visitMethod(Opcodes.ACC_PUBLIC,"readFromNBT",
                    "(Lnet/minecraft/nbt/NBTTagCompound;)V",null,null);
            read.visitCode();read.visitInsn(Opcodes.RETURN);read.visitMaxs(0,0);read.visitEnd();
        }
        c.visitEnd();return c.toByteArray();
    }
    private static byte[] renderer(Form form){
        var c = writer(RENDER,"net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer");
        if(form!=Form.NO_DRAW){
            var entry=c.visitMethod(Opcodes.ACC_PUBLIC,"renderTileEntityAt",
                    "(Lnet/minecraft/tileentity/TileEntity;DDDF)V",null,null);
            entry.visitCode();
            entry.visitVarInsn(Opcodes.ALOAD,0);
            entry.visitVarInsn(Opcodes.ALOAD,1);
            entry.visitTypeInsn(Opcodes.CHECKCAST,TILE);
            entry.visitMethodInsn(Opcodes.INVOKESPECIAL,RENDER,"drawPart","(L"+TILE+";)V",false);
            entry.visitInsn(Opcodes.RETURN);entry.visitMaxs(0,0);entry.visitEnd();
            var draw=c.visitMethod(Opcodes.ACC_PRIVATE,"drawPart","(L"+TILE+";)V",null,null);
            draw.visitCode();
            if(form!=Form.NO_TILE_FIELD_READS){
                draw.visitVarInsn(Opcodes.ALOAD,1);
                draw.visitFieldInsn(Opcodes.GETFIELD,TILE,
                        form==Form.UNDECLARED_TILE_FIELD?"undeclared":"yaw","I");
                draw.visitInsn(Opcodes.POP);
            }
            if(form!=Form.NO_METADATA_OR_GL){
                draw.visitVarInsn(Opcodes.ALOAD,1);
                draw.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MC_TILE,"getBlockMetadata","()I",false);
                draw.visitInsn(Opcodes.POP);
                draw.visitLdcInsn(90.0F);
                draw.visitInsn(Opcodes.FCONST_1);
                draw.visitInsn(Opcodes.FCONST_0);
                draw.visitInsn(Opcodes.FCONST_0);
                draw.visitMethodInsn(Opcodes.INVOKESTATIC,GL,"glRotatef","(FFFF)V",false);
                draw.visitInsn(Opcodes.FCONST_1);
                draw.visitLdcInsn(-1.0F);
                draw.visitLdcInsn(-1.0F);
                draw.visitMethodInsn(Opcodes.INVOKESTATIC,GL,"glScalef","(FFF)V",false);
            }
            if(form!=Form.DRAW_WITHOUT_ANIMATION && form!=Form.UNCALLED_MODEL_PIVOT){
                draw.visitTypeInsn(Opcodes.NEW,MODEL);draw.visitInsn(Opcodes.DUP);
                draw.visitMethodInsn(Opcodes.INVOKESPECIAL,MODEL,"<init>","()V",false);
                draw.visitVarInsn(Opcodes.ALOAD,1);draw.visitInsn(Opcodes.FCONST_0);
                draw.visitMethodInsn(Opcodes.INVOKEVIRTUAL,MODEL,"animate","(L"+TILE+";F)V",false);
            }
            draw.visitInsn(Opcodes.RETURN);draw.visitMaxs(0,0);draw.visitEnd();
        }
        c.visitEnd();return c.toByteArray();
    }
    private static byte[] model(Form form) {
        var c = writer(MODEL,"net/minecraft/client/model/ModelBase");
        var animate = c.visitMethod(Opcodes.ACC_PUBLIC,"animate","(L"+TILE+";F)V",null,null);
        animate.visitCode();
        if(form!=Form.UNREACHABLE_ANIMATION){
            animate.visitVarInsn(Opcodes.ALOAD,1);
            animate.visitFieldInsn(Opcodes.GETFIELD,TILE,"delay","I");
            animate.visitInsn(Opcodes.POP);
            // An explicit ModelRenderer part pivot write shows a source client animation path.
            animate.visitTypeInsn(Opcodes.NEW, PART);animate.visitInsn(Opcodes.DUP);
            animate.visitMethodInsn(Opcodes.INVOKESPECIAL,PART,"<init>","()V",false);
            animate.visitInsn(Opcodes.FCONST_1);
            animate.visitFieldInsn(Opcodes.PUTFIELD,PART,"rotationPointY","F");
        }
        animate.visitInsn(Opcodes.RETURN);animate.visitMaxs(0,0);animate.visitEnd();
        c.visitEnd();return c.toByteArray();
    }
    private static void put(JarOutputStream jar, String owner, byte[] data) throws IOException {
        jar.putNextEntry(new JarEntry(owner+".class"));jar.write(data);jar.closeEntry();
    }
}
