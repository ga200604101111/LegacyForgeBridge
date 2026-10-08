package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Totally synthetic legacy Java 7 bytecode; no original mod/game class is copied. */
final class LegacyTilePivotAnimationFixture {
    static final String TILE="arbitrary/placed/GlowTile";
    static final String MODEL="arbitrary/client/GlowModel";
    static final String RENDERER="arbitrary/client/GlowTileRenderer";
    private static final String PART="net/minecraft/client/model/ModelRenderer";
    private static final String BASE="net/minecraft/client/model/ModelBase";
    private static final String TESR="net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer";
    private static final String TILEBASE="net/minecraft/tileentity/TileEntity";
    private static final String ANIM_DESC="(L"+TILE+";F)V";
    private static final String DRAW_DESC="(L"+TILEBASE+";DDDF)V";
    private static final String[] PIECES={"body","wing","head","tail"};

    enum Form {
        FIXED_FOUR, WRONG_RENDERER_CALL, DOUBLE_RENDERER_CALL, UNKNOWN_TILE_FIELD,
        FIELD_OWNED_BY_OTHER_CLASS, TILE_FIELD_STATIC, NO_SINE, NO_CLAMP, NO_PARTIAL,
        INVERTED_GUARD, DIFFERENT_GUARD_RECEIVER, PART_FIELD_UNCONSTRUCTED,
        PIVOT_X_WRITE, WRONG_PART_RECEIVER, DUPLICATE_PIVOT_RESET, EXTRA_DYNAMIC_PIVOT,
        UNKNOWN_FLOAT_HELPER, NO_DYNAMIC_TILE_FIELD, WRONG_PARTIAL_CALL,
        EXTRA_ANIMATOR_BRANCH, WRONG_MODEL_CALL_ARG, SOURCE_SRG_PIVOT,
        NO_RENDERER_MODEL_ALLOCATION, MISSING_MODEL, MISSING_TILE, REMOTE_TICK_ONLY,
        WRONG_CLAMP_BOUND, UNCONSTRUCTED_RENDERER_MODEL_FIELD, NULL_PART_FIELD,
        ANIMATOR_SINE_WITH_CONSTANT_INPUT, EXTRA_FLOAT_OFFSET, UNKNOWN_SIDE_EFFECT_CALL
    }
    static Path jar(Path target,Form form)throws IOException {
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(target))) {
            if(form!=Form.MISSING_TILE) put(out,TILE+".class",tile(form));
            if(form!=Form.MISSING_MODEL) put(out,MODEL+".class",model(form));
            put(out,RENDERER+".class",renderer(form));
        }
        return target;
    }
    private static byte[] tile(Form form) {
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,TILE,null,TILEBASE,null);
        w.visitField(Opcodes.ACC_PUBLIC,"yawDelay","I",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC,"desiredYaw","I",null,null).visitEnd();
        if(form!=Form.UNKNOWN_TILE_FIELD)
            w.visitField(Opcodes.ACC_PUBLIC | (form==Form.TILE_FIELD_STATIC?Opcodes.ACC_STATIC:0),
                    "actualYaw","I",null,null).visitEnd();
        MethodVisitor ctor=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.visitCode();ctor.visitVarInsn(Opcodes.ALOAD,0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,TILEBASE,"<init>","()V",false);
        ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(0,0);ctor.visitEnd();
        if(form==Form.REMOTE_TICK_ONLY){
            MethodVisitor tick=w.visitMethod(Opcodes.ACC_PUBLIC,"updateEntity","()V",null,null);tick.visitCode();
            tick.visitVarInsn(Opcodes.ALOAD,0);tick.visitInsn(Opcodes.ICONST_1);
            tick.visitFieldInsn(Opcodes.PUTFIELD,TILE,"actualYaw","I");
            tick.visitInsn(Opcodes.RETURN);tick.visitMaxs(0,0);tick.visitEnd();
        }
        w.visitEnd();return w.toByteArray();
    }
    private static byte[] model(Form form){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,MODEL,null,BASE,null);
        for(String piece:PIECES) w.visitField(Opcodes.ACC_PUBLIC,piece,"L"+PART+";",null,null).visitEnd();
        MethodVisitor ctor=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD,0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,BASE,"<init>","()V",false);
        for(String piece:PIECES) {
            if(form==Form.PART_FIELD_UNCONSTRUCTED && piece.equals("head"))continue;
            if(form==Form.NULL_PART_FIELD && piece.equals("head")){
                ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitInsn(Opcodes.ACONST_NULL);
                ctor.visitFieldInsn(Opcodes.PUTFIELD,MODEL,piece,"L"+PART+";");
                continue;
            }
            ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitTypeInsn(Opcodes.NEW,PART);ctor.visitInsn(Opcodes.DUP);
            ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitInsn(Opcodes.ICONST_0);ctor.visitInsn(Opcodes.ICONST_4);
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,PART,"<init>","(L"+BASE+";II)V",false);
            ctor.visitFieldInsn(Opcodes.PUTFIELD,MODEL,piece,"L"+PART+";");
        }
        ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(0,0);ctor.visitEnd();
        MethodVisitor anim=w.visitMethod(Opcodes.ACC_PUBLIC,"animate",ANIM_DESC,null,null);anim.visitCode();
        String pivot=form==Form.SOURCE_SRG_PIVOT?"field_78797_d":"rotationPointY";
        for(String piece:PIECES) reset(anim,piece,pivot);
        if(form==Form.DUPLICATE_PIVOT_RESET)reset(anim,"body",pivot);
        if(form==Form.ANIMATOR_SINE_WITH_CONSTANT_INPUT)anim.visitInsn(Opcodes.ICONST_1);
        else {anim.visitVarInsn(Opcodes.ALOAD,1);
            anim.visitFieldInsn(Opcodes.GETFIELD,TILE,"desiredYaw","I");}
        if(form==Form.ANIMATOR_SINE_WITH_CONSTANT_INPUT)anim.visitInsn(Opcodes.ICONST_0);
        else {anim.visitVarInsn(Opcodes.ALOAD,form==Form.FIELD_OWNED_BY_OTHER_CLASS?0:1);
            anim.visitFieldInsn(Opcodes.GETFIELD,form==Form.FIELD_OWNED_BY_OTHER_CLASS?MODEL:TILE,"actualYaw","I");}
        anim.visitInsn(Opcodes.ISUB);anim.visitInsn(Opcodes.I2F);
        if(form==Form.NO_PARTIAL)anim.visitInsn(Opcodes.FCONST_0);
        else anim.visitVarInsn(Opcodes.FLOAD,2);
        anim.visitInsn(Opcodes.FSUB);anim.visitVarInsn(Opcodes.FSTORE,3);
        anim.visitVarInsn(Opcodes.ALOAD,form==Form.DIFFERENT_GUARD_RECEIVER?0:1);
        anim.visitFieldInsn(Opcodes.GETFIELD,TILE,"yawDelay","I");
        Label end=new Label();
        anim.visitJumpInsn(form==Form.INVERTED_GUARD?Opcodes.IFEQ:Opcodes.IFNE,end);
        for(String piece:PIECES)dynamic(anim,piece,pivot,form);
        if(form==Form.EXTRA_DYNAMIC_PIVOT)dynamic(anim,"wing",pivot,form);
        if(form==Form.UNKNOWN_SIDE_EFFECT_CALL)
            anim.visitMethodInsn(Opcodes.INVOKESTATIC,"arbitrary/SourceServerOnly","tick","()V",false);
        if(form==Form.EXTRA_ANIMATOR_BRANCH){
            anim.visitInsn(Opcodes.ICONST_0);anim.visitJumpInsn(Opcodes.IFNE,end);
        }
        anim.visitLabel(end);anim.visitInsn(Opcodes.RETURN);anim.visitMaxs(0,0);anim.visitEnd();
        w.visitEnd();return w.toByteArray();
    }
    private static void reset(MethodVisitor m,String part,String pivot){
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,MODEL,part,"L"+PART+";");
        m.visitLdcInsn(7.0F);m.visitFieldInsn(Opcodes.PUTFIELD,PART,pivot,"F");
    }
    private static void dynamic(MethodVisitor m,String part,String pivot,Form form){
        m.visitVarInsn(Opcodes.ALOAD,0);
        m.visitFieldInsn(Opcodes.GETFIELD,MODEL,form==Form.WRONG_PART_RECEIVER?"head":part,"L"+PART+";");
        m.visitInsn(Opcodes.DUP);
        m.visitFieldInsn(Opcodes.GETFIELD,PART,pivot,"F");
        m.visitInsn(form==Form.WRONG_CLAMP_BOUND?Opcodes.FCONST_1:Opcodes.FCONST_0);
        m.visitVarInsn(Opcodes.FLOAD,3);m.visitInsn(Opcodes.FCONST_2);
        m.visitInsn(Opcodes.FDIV);
        if(!part.equals("body")){
            m.visitLdcInsn((float)(part.equals("wing")?1:part.equals("head")?2:3));
            m.visitInsn(Opcodes.FADD);
        }
        if(form==Form.NO_SINE)m.visitMethodInsn(Opcodes.INVOKESTATIC,"arbitrary/FakeMath","sin","(F)F",false);
        else m.visitMethodInsn(Opcodes.INVOKESTATIC,"net/minecraft/util/MathHelper","sin","(F)F",false);
        if(form==Form.NO_CLAMP)m.visitInsn(Opcodes.SWAP); // keeps verifier stack shape, but not a min call
        else m.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Math","min","(FF)F",false);
        if(form==Form.UNKNOWN_FLOAT_HELPER)
            m.visitMethodInsn(Opcodes.INVOKESTATIC,"arbitrary/FakeMath","mask","(F)F",false);
        if(form==Form.NO_DYNAMIC_TILE_FIELD){m.visitInsn(Opcodes.POP);m.visitInsn(Opcodes.FCONST_1);}
        if(form==Form.EXTRA_FLOAT_OFFSET){m.visitInsn(Opcodes.FCONST_1);m.visitInsn(Opcodes.FADD);}
        m.visitInsn(Opcodes.FADD);
        m.visitFieldInsn(Opcodes.PUTFIELD,PART,
                form==Form.PIVOT_X_WRITE?"rotationPointX":pivot,"F");
    }
    private static byte[] renderer(Form form) {
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,RENDERER,null,TESR,null);
        w.visitField(Opcodes.ACC_PRIVATE,"model","L"+MODEL+";",null,null).visitEnd();
        MethodVisitor ctor=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,TESR,"<init>","()V",false);
        if(form!=Form.NO_RENDERER_MODEL_ALLOCATION){
            if(form==Form.UNCONSTRUCTED_RENDERER_MODEL_FIELD){
                ctor.visitTypeInsn(Opcodes.NEW,MODEL);ctor.visitInsn(Opcodes.DUP);
                ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,MODEL,"<init>","()V",false);
                ctor.visitInsn(Opcodes.POP);
                ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitInsn(Opcodes.ACONST_NULL);
            }else {
                ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitTypeInsn(Opcodes.NEW,MODEL);
                ctor.visitInsn(Opcodes.DUP);ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,MODEL,"<init>","()V",false);
            }
            ctor.visitFieldInsn(Opcodes.PUTFIELD,RENDERER,"model","L"+MODEL+";");
        }
        ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(0,0);ctor.visitEnd();
        MethodVisitor draw=w.visitMethod(Opcodes.ACC_PUBLIC,"renderTileEntityAt",DRAW_DESC,null,null);draw.visitCode();
        if(form!=Form.WRONG_RENDERER_CALL)call(draw,form);
        if(form==Form.DOUBLE_RENDERER_CALL)call(draw,form);
        draw.visitInsn(Opcodes.RETURN);draw.visitMaxs(0,0);draw.visitEnd();
        w.visitEnd();return w.toByteArray();
    }
    private static void call(MethodVisitor m,Form form){
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,RENDERER,"model","L"+MODEL+";");
        if(form==Form.WRONG_MODEL_CALL_ARG)m.visitVarInsn(Opcodes.ALOAD,0);
        else{m.visitVarInsn(Opcodes.ALOAD,1);m.visitTypeInsn(Opcodes.CHECKCAST,TILE);}
        if(form==Form.WRONG_PARTIAL_CALL)m.visitInsn(Opcodes.FCONST_0);
        else m.visitVarInsn(Opcodes.FLOAD,8);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,MODEL,"animate",ANIM_DESC,false);
    }
    private static void put(JarOutputStream jar,String name,byte[] bytes)throws IOException{
        jar.putNextEntry(new JarEntry(name));jar.write(bytes);jar.closeEntry();
    }
}
