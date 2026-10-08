package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/** Java 7 synthetic renamed TESR with the upstream-like third-rotation expression. */
final class LegacyTileDynamicYawFixture {
    static final String TILE=LegacyTileFacingRotationFixture.TILE;
    static final String RENDER=LegacyTileFacingRotationFixture.RENDERER;
    private static final String GL="org/lwjgl/opengl/GL11";
    enum Form {
        EXACT_INT, FLOAT_FIELD, LOCAL_ALIAS, SOURCE_CAST,
        WRONG_Y_AXIS, ARITHMETIC_ANGLE, WRONG_FIELD_OWNER, WRONG_RECEIVER,
        WRONG_FIELD_TYPE, UNDECLARED_FIELD, STATIC_FIELD, CONDITIONAL_RENDER,
        EXTRA_SOURCE_ROTATION, WRONG_INVOKE_OPCODE, NO_DYNAMIC_YAW, NEGATIVE_ANGLE,
        BAD_EXTRA_CAST, RENAMED_SOURCE_FIELD, LEGACY_SRG, TWO_YAW_ROTATIONS
    }
    static Path jar(Path target, Form form) throws IOException {
        Path original=target.resolveSibling("original-"+form+".jar");
        LegacyTileFacingRotationFixture.Form old=form==Form.LEGACY_SRG?
                LegacyTileFacingRotationFixture.Form.SRG:LegacyTileFacingRotationFixture.Form.SIX_FACING;
        LegacyTileFacingRotationFixture.jar(original,old);
        try(JarFile input=new JarFile(original.toFile());
            JarOutputStream output=new JarOutputStream(Files.newOutputStream(target))) {
            var entries=input.entries();
            while(entries.hasMoreElements()) {
                JarEntry entry=entries.nextElement();
                byte[] bytes;
                try(var stream=input.getInputStream(entry)) {bytes=stream.readAllBytes();}
                if(entry.getName().equals(RENDER+".class")) bytes=renderer(bytes,form);
                else if(entry.getName().equals(TILE+".class")) bytes=tile(bytes,form);
                output.putNextEntry(new JarEntry(entry.getName()));output.write(bytes);output.closeEntry();
            }
        }
        return target;
    }
    private static byte[] tile(byte[] bytes,Form form) {
        var node=new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node,0);
        if(form==Form.UNDECLARED_FIELD) node.fields.removeIf(f->f.name.equals("currentYaw"));
        if(form==Form.STATIC_FIELD) for(FieldNode f:node.fields)
            if(f.name.equals("currentYaw")) f.access|=Opcodes.ACC_STATIC;
        if(form==Form.FLOAT_FIELD || form==Form.WRONG_FIELD_TYPE)
            for(FieldNode f:node.fields)if(f.name.equals("currentYaw"))
                f.desc=form==Form.FLOAT_FIELD?"F":"J";
        if(form==Form.RENAMED_SOURCE_FIELD){
            for(FieldNode f:node.fields)if(f.name.equals("currentYaw"))f.name="anotherField";
        }
        var out=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(out);return out.toByteArray();
    }
    private static byte[] renderer(byte[] bytes,Form form) {
        var node=new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node,0);
        MethodNode draw=node.methods.stream().filter(m->m.name.equals("sourceDraw")).findFirst().orElseThrow();
        List<MethodInsnNode> rots=new ArrayList<>();
        for(AbstractInsnNode insn:draw.instructions)
            if(insn instanceof MethodInsnNode call && call.owner.equals(GL)&&call.name.equals("glRotatef"))
                rots.add(call);
        MethodInsnNode third=rots.get(2);
        AbstractInsnNode first=third;
        for(int i=0;i<6;i++){first=priorReal(first);if(first==null)throw new IllegalStateException("Not a direct int source sequence");}
        var replacement=new InsnList();
        if(form==Form.CONDITIONAL_RENDER) {
            replacement.add(new InsnNode(Opcodes.ICONST_1));
            LabelNode skip=new LabelNode();
            replacement.add(new JumpInsnNode(Opcodes.IFEQ,skip));
            expression(replacement,form);
            replacement.add(skip);
        } else if(form!=Form.NO_DYNAMIC_YAW) expression(replacement,form);
        draw.instructions.insertBefore(first,replacement);
        AbstractInsnNode cursor=first;
        while(cursor!=third) {
            AbstractInsnNode next=cursor.getNext();
            draw.instructions.remove(cursor);cursor=next;
        }
        draw.instructions.remove(third);
        var out=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(out);return out.toByteArray();
    }
    private static AbstractInsnNode priorReal(AbstractInsnNode at){
        for(AbstractInsnNode prev=at.getPrevious();prev!=null;prev=prev.getPrevious())
            if(prev.getOpcode()>=0)return prev;
        return null;
    }
    private static void expression(InsnList out,Form form) {
        boolean invalidOwner=form==Form.WRONG_FIELD_OWNER;
        boolean wrongReceiver=form==Form.WRONG_RECEIVER;
        if(form==Form.STATIC_FIELD) {
            out.add(new FieldInsnNode(Opcodes.GETSTATIC,TILE,"currentYaw","I"));
        }else {
            out.add(new VarInsnNode(Opcodes.ALOAD,wrongReceiver?0:1));
            if(form==Form.SOURCE_CAST || form==Form.BAD_EXTRA_CAST)
                out.add(new TypeInsnNode(Opcodes.CHECKCAST,
                        form==Form.BAD_EXTRA_CAST?"foreign/other/UnknownTile":TILE));
            String owner=invalidOwner?"foreign/other/FakeTile":TILE;
            String desc=form==Form.FLOAT_FIELD?"F":form==Form.WRONG_FIELD_TYPE?"J":"I";
            String field=form==Form.RENAMED_SOURCE_FIELD?"anotherField":"currentYaw";
            out.add(new FieldInsnNode(Opcodes.GETFIELD,owner,field,desc));
        }
        if(form==Form.NEGATIVE_ANGLE){
            out.add(new InsnNode(Opcodes.INEG));
        }
        if(form!=Form.FLOAT_FIELD && form!=Form.WRONG_FIELD_TYPE)
            out.add(new InsnNode(Opcodes.I2F));
        if(form==Form.ARITHMETIC_ANGLE) {
            out.add(new InsnNode(Opcodes.FCONST_1));out.add(new InsnNode(Opcodes.FADD));
        }
        if(form==Form.LOCAL_ALIAS){
            out.add(new VarInsnNode(Opcodes.FSTORE,12));
            out.add(new VarInsnNode(Opcodes.FLOAD,12));
        }
        out.add(new InsnNode(Opcodes.FCONST_0));
        out.add(new InsnNode(form==Form.WRONG_Y_AXIS?Opcodes.FCONST_0:Opcodes.FCONST_1));
        out.add(new InsnNode(Opcodes.FCONST_0));
        out.add(new MethodInsnNode(form==Form.WRONG_INVOKE_OPCODE?Opcodes.INVOKEVIRTUAL:Opcodes.INVOKESTATIC,
                GL,"glRotatef","(FFFF)V",false));
        if(form==Form.EXTRA_SOURCE_ROTATION || form==Form.TWO_YAW_ROTATIONS){
            out.add(new InsnNode(Opcodes.FCONST_1));
            out.add(new InsnNode(Opcodes.FCONST_0));
            out.add(new InsnNode(Opcodes.FCONST_1));
            out.add(new InsnNode(Opcodes.FCONST_0));
            out.add(new MethodInsnNode(Opcodes.INVOKESTATIC,GL,"glRotatef","(FFFF)V",false));
        }
    }
}
