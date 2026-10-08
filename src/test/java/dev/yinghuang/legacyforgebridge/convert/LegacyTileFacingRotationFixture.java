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

/** Synthetic Java 7 (unrelated to any legacy mod ID/name) TESR direction bytecode. */
final class LegacyTileFacingRotationFixture {
    static final String TILE="foreign/tiles/PrismLamp";
    static final String RENDERER="foreign/client/LampRenderer";
    static final String MC_TILE="net/minecraft/tileentity/TileEntity";
    static final String GL="org/lwjgl/opengl/GL11";
    static final String SOURCE_RENDER="(L"+MC_TILE+";DDDF)V";
    static final String SOURCE_HELPER="(L"+TILE+";DDDF)V";
    enum Form { SIX_FACING, MASK7, SRG, DIRECT, MISSING_CAST, TWO_HELPERS,
        NO_META, WRONG_METADATA_RECEIVER, DYNAMIC_AXIS, DYNAMIC_ANGLE,
        NO_Z_ROTATION, UNEXPECTED_GL_CALL, UNKNOWN_COMPARISON, CHANGED_METADATA,
        NO_POSE_VARIATION, CHANGED_ANGLE_AFTER_X, BAD_TILE_PARENT,
        MULTIPLE_METADATA_CALLS, BAD_ROTATE_OPCODE, WORLD_CALL_IN_PROLOGUE,
        SOURCE_TILE_SUPERCLASS, MISSING_TILE_SUPERCLASS, ENTRY_SIDE_EFFECT, IINC_METADATA,
        BAD_RENDERER_PARENT }
    static Path jar(Path target, Form form) throws IOException {
        try(JarOutputStream archive=new JarOutputStream(Files.newOutputStream(target))) {
            put(archive,TILE,tile(form));
            if(form==Form.SOURCE_TILE_SUPERCLASS)
                put(archive,"foreign/tiles/PrimitiveTile",baseTile());
            put(archive,RENDERER,renderer(form));
        }
        return target;
    }
    private static byte[] baseTile() {
        var out=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        out.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/tiles/PrimitiveTile",null,MC_TILE,null);
        var ctor=out.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.visitCode();ctor.visitVarInsn(Opcodes.ALOAD,0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,MC_TILE,"<init>","()V",false);
        ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(0,0);ctor.visitEnd();
        out.visitEnd();return out.toByteArray();
    }
    private static byte[] tile(Form form) {
        String parent=form==Form.BAD_TILE_PARENT?"java/lang/Object":
                form==Form.SOURCE_TILE_SUPERCLASS?"foreign/tiles/PrimitiveTile":
                form==Form.MISSING_TILE_SUPERCLASS?"foreign/tiles/NotInSource":MC_TILE;
        var out=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        out.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,TILE,null,parent,null);
        out.visitField(Opcodes.ACC_PUBLIC,"currentYaw","I",null,null).visitEnd();
        var ctor=out.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.visitCode();ctor.visitVarInsn(Opcodes.ALOAD,0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false);
        ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(0,0);ctor.visitEnd();
        out.visitEnd();return out.toByteArray();
    }
    private static void put(JarOutputStream out,String name,byte[] bytes)throws IOException {
        out.putNextEntry(new JarEntry(name+".class"));out.write(bytes);out.closeEntry();
    }
    private static void pushAngle(MethodVisitor m,float value) {
        if(value==0F)m.visitInsn(Opcodes.FCONST_0);
        else if(value==1F)m.visitInsn(Opcodes.FCONST_1);
        else m.visitLdcInsn(value);
    }
    private static void conditional(MethodVisitor m,int facing,float value,int target,Form form){
        m.visitVarInsn(Opcodes.ILOAD,9);
        if(form==Form.UNKNOWN_COMPARISON && facing==3){
            m.visitInsn(Opcodes.ICONST_1);
            m.visitInsn(Opcodes.IADD);
        }
        m.visitIntInsn(Opcodes.BIPUSH,facing);
        Label skip=new Label();m.visitJumpInsn(Opcodes.IF_ICMPNE,skip);
        pushAngle(m,value);m.visitVarInsn(Opcodes.FSTORE,target);
        m.visitLabel(skip);
    }
    private static void rot(MethodVisitor m,int local,float a,float b,float c,Form form) {
        m.visitVarInsn(Opcodes.FLOAD,local);
        pushAngle(m,a);pushAngle(m,b);pushAngle(m,c);
        m.visitMethodInsn(form==Form.BAD_ROTATE_OPCODE?Opcodes.INVOKEVIRTUAL:Opcodes.INVOKESTATIC,
                GL,"glRotatef","(FFFF)V",false);
    }
    private static byte[] renderer(Form form){
        var out=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String parent=form==Form.BAD_RENDERER_PARENT?"java/lang/Object":
                "net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer";
        out.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,RENDERER,null,parent,null);
        var ctor=out.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.visitCode();ctor.visitVarInsn(Opcodes.ALOAD,0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false);
        ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(0,0);ctor.visitEnd();
        var root=out.visitMethod(Opcodes.ACC_PUBLIC,"renderTileEntityAt",SOURCE_RENDER,null,null);
        root.visitCode();
        if(form==Form.DIRECT){draw(root,form);}
        else {
            if(form==Form.ENTRY_SIDE_EFFECT){
                root.visitInsn(Opcodes.FCONST_0);root.visitInsn(Opcodes.FCONST_1);
                root.visitInsn(Opcodes.FCONST_0);root.visitInsn(Opcodes.FCONST_0);
                root.visitMethodInsn(Opcodes.INVOKESTATIC,GL,"glRotatef","(FFFF)V",false);
            }
            invokeHelper(root,form);
            if(form==Form.TWO_HELPERS)invokeHelper(root,form);
        }
        root.visitInsn(Opcodes.RETURN);root.visitMaxs(0,0);root.visitEnd();
        if(form!=Form.DIRECT){
            var helper=out.visitMethod(Opcodes.ACC_PRIVATE,"sourceDraw",SOURCE_HELPER,null,null);
            helper.visitCode();draw(helper,form);
            helper.visitInsn(Opcodes.RETURN);helper.visitMaxs(0,0);helper.visitEnd();
        }
        out.visitEnd();return out.toByteArray();
    }
    private static void invokeHelper(MethodVisitor m,Form form){
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ALOAD,1);
        if(form!=Form.MISSING_CAST)m.visitTypeInsn(Opcodes.CHECKCAST,TILE);
        for(int i:new int[]{2,4,6})m.visitVarInsn(Opcodes.DLOAD,i);
        m.visitVarInsn(Opcodes.FLOAD,8);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,RENDERER,"sourceDraw",SOURCE_HELPER,false);
    }
    private static void draw(MethodVisitor m,Form form) {
        if(form!=Form.NO_META){
            m.visitVarInsn(Opcodes.ALOAD,form==Form.WRONG_METADATA_RECEIVER?0:1);
            m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,MC_TILE,
                    form==Form.SRG?"func_145832_p":"getBlockMetadata","()I",false);
            if(form==Form.MASK7){m.visitIntInsn(Opcodes.BIPUSH,7);m.visitInsn(Opcodes.IAND);}
            m.visitVarInsn(Opcodes.ISTORE,9);
        }else {m.visitInsn(Opcodes.ICONST_0);m.visitVarInsn(Opcodes.ISTORE,9);}
        if(form==Form.MULTIPLE_METADATA_CALLS){
            m.visitVarInsn(Opcodes.ALOAD,1);
            m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,MC_TILE,"getBlockMetadata","()I",false);
            m.visitVarInsn(Opcodes.ISTORE,9);
        }
        pushAngle(m,90);m.visitVarInsn(Opcodes.FSTORE,10);
        pushAngle(m,0);m.visitVarInsn(Opcodes.FSTORE,11);
        if(form!=Form.NO_POSE_VARIATION){
            conditional(m,3,0,11,form);
            conditional(m,4,180,11,form);
            conditional(m,1,-90,11,form);
            conditional(m,2,90,11,form);
            conditional(m,5,0,10,form);
            conditional(m,6,180,10,form);
        }
        if(form==Form.CHANGED_METADATA){
            m.visitInsn(Opcodes.ICONST_1);m.visitVarInsn(Opcodes.ISTORE,9);
        }
        if(form==Form.IINC_METADATA)m.visitIincInsn(9,1);
        if(form==Form.WORLD_CALL_IN_PROLOGUE){
            m.visitMethodInsn(Opcodes.INVOKESTATIC,"foreign/helper/RemoteValues","get","()V",false);
        }
        if(form==Form.UNEXPECTED_GL_CALL){
            m.visitInsn(Opcodes.FCONST_1);m.visitInsn(Opcodes.FCONST_1);m.visitInsn(Opcodes.FCONST_1);
            m.visitMethodInsn(Opcodes.INVOKESTATIC,GL,"glScalef","(FFF)V",false);
        }
        for(int i:new int[]{2,4,6}){
            m.visitVarInsn(Opcodes.DLOAD,i);m.visitInsn(Opcodes.D2F);
            m.visitLdcInsn(.5F);m.visitInsn(Opcodes.FADD);
        }
        m.visitMethodInsn(Opcodes.INVOKESTATIC,GL,"glTranslatef","(FFF)V",false);
        if(form==Form.DYNAMIC_ANGLE){
            m.visitVarInsn(Opcodes.ALOAD,1);m.visitFieldInsn(Opcodes.GETFIELD,TILE,"currentYaw","I");
            m.visitInsn(Opcodes.I2F);m.visitVarInsn(Opcodes.FSTORE,10);
        }
        rot(m,10,1,form==Form.DYNAMIC_AXIS?1:0,0,form);
        if(form==Form.CHANGED_ANGLE_AFTER_X){
            pushAngle(m,35);m.visitVarInsn(Opcodes.FSTORE,11);
        }
        if(form!=Form.NO_Z_ROTATION)rot(m,11,0,0,1,form);
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitFieldInsn(Opcodes.GETFIELD,TILE,"currentYaw","I");
        m.visitInsn(Opcodes.I2F);m.visitInsn(Opcodes.FCONST_0);m.visitInsn(Opcodes.FCONST_1);
        m.visitInsn(Opcodes.FCONST_0);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,GL,"glRotatef","(FFFF)V",false);
    }
}
