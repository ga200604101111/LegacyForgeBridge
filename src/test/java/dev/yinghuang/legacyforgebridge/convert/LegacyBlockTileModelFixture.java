package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Renamed Java 7 ASM patterns, not a mod-specific production exception table. */
final class LegacyBlockTileModelFixture {
    static final String BASE = "arbitrary/decor/AttachedDisplayBase";
    static final String BLOCK = "arbitrary/decor/LuminousWallDecoration";
    static final String TILE_BASE = "arbitrary/tile/MovingVisualBase";
    static final String TILE = "arbitrary/tile/LuminousTile";
    static final String RENDERER = "arbitrary/client/LuminousTileRenderer";
    static final String MODEL = "arbitrary/client/FaceCuboidModel";
    static final String CLIENT = "arbitrary/client/ClientSetup";
    static final String MC_BLOCK = "net/minecraft/block/Block";
    static final String MC_TILE = "net/minecraft/tileentity/TileEntity";
    static final String MC_MODEL = "net/minecraft/client/model/ModelBase";
    static final String MC_RENDER = "net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer";
    static final String WORLD = "net/minecraft/world/World";
    static final String TILE_FACTORY = "(L"+WORLD+";I)L"+MC_TILE+";";
    static final String DRAW = "(L"+MC_TILE+";DDDF)V";
    enum Case {
        GOOD, MARKER_FALSE, BLOCK_FACTORY_WRONG_RETURN, CLIENT_BIND_MISSING,
        CLIENT_BIND_DUPLICATE, UNKNOWN_RENDERER_BASE, MODEL_ALLOCATION_MISSING,
        MODEL_ANIMATION_MISSING, BLOCK_NO_INHERITED_MARKER, NO_RENDER_DRAW,
        UNRESOLVED_CLIENT_BIND, CLIENT_REGISTER_WITH_ID, DYNAMIC_LIGHT, MISSING_TILE_SOURCE
    }
    static Path jar(Path output, Case variant) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(output))) {
            put(jar,BASE,blockBase(variant));
            put(jar,BLOCK,block(variant));
            put(jar,TILE_BASE,tileBase());
            if(variant!=Case.MISSING_TILE_SOURCE)put(jar,TILE,tile());
            put(jar,MODEL,model());
            put(jar,RENDERER,renderer(variant));
            put(jar,CLIENT,client(variant));
        }
        return output;
    }
    private static void put(JarOutputStream jar,String name,byte[] data) throws IOException {
        jar.putNextEntry(new JarEntry(name+".class"));jar.write(data);jar.closeEntry();
    }
    private static ClassWriter writer(String name,String parent) {
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,parent,null);
        MethodVisitor ctor=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.visitCode();ctor.visitVarInsn(Opcodes.ALOAD,0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false);
        ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(0,0);ctor.visitEnd();
        return w;
    }
    private static byte[] blockBase(Case v) {
        var w=writer(BASE,MC_BLOCK);
        if(v!=Case.BLOCK_NO_INHERITED_MARKER){
            MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"hasTileEntity","(I)Z",null,null);
            m.visitCode();m.visitInsn(v==Case.MARKER_FALSE?Opcodes.ICONST_0:Opcodes.ICONST_1);
            m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();
        }
        w.visitEnd();return w.toByteArray();
    }
    private static byte[] block(Case v) {
        var w=writer(BLOCK,BASE);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"createTileEntity",TILE_FACTORY,null,null);
        m.visitCode();m.visitTypeInsn(Opcodes.NEW,TILE);m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,TILE,"<init>","()V",false);
        if(v==Case.BLOCK_FACTORY_WRONG_RETURN) {
            m.visitInsn(Opcodes.POP);m.visitInsn(Opcodes.ACONST_NULL);
        }
        m.visitInsn(Opcodes.ARETURN);m.visitMaxs(0,0);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"setBlockBoundsBasedOnState",
            "(Lnet/minecraft/world/IBlockAccess;III)V",null,null);
        m.visitCode();m.visitVarInsn(Opcodes.ALOAD,1);
        m.visitVarInsn(Opcodes.ILOAD,2);m.visitVarInsn(Opcodes.ILOAD,3);m.visitVarInsn(Opcodes.ILOAD,4);
        m.visitMethodInsn(Opcodes.INVOKEINTERFACE,"net/minecraft/world/IBlockAccess",
            "getBlockMetadata","(III)I",true);m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD,0);
        for(int i=0;i<6;i++)m.visitInsn(Opcodes.FCONST_0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,MC_BLOCK,"setBlockBounds","(FFFFFF)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"getLightValue",
            "(Lnet/minecraft/world/IBlockAccess;III)I",null,null);
        m.visitCode();
        if(v==Case.DYNAMIC_LIGHT)m.visitVarInsn(Opcodes.ILOAD,2);
        else m.visitIntInsn(Opcodes.BIPUSH,14);
        m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"quantityDropped","(IILjava/util/Random;)I",null,null);
        m.visitCode();m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();
        w.visitEnd();return w.toByteArray();
    }
    private static byte[] tileBase() {
        var w=writer(TILE_BASE,MC_TILE);
        var m=w.visitMethod(Opcodes.ACC_PUBLIC,"updateEntity","()V",null,null);
        m.visitCode();m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        w.visitEnd();return w.toByteArray();
    }
    private static byte[] tile() {
        var w=writer(TILE,TILE_BASE);w.visitField(Opcodes.ACC_PUBLIC,"angle","I",null,null).visitEnd();
        w.visitEnd();return w.toByteArray();
    }
    private static byte[] model() {
        var w=writer(MODEL,MC_MODEL);
        var m=w.visitMethod(Opcodes.ACC_PUBLIC,"animate","(L"+TILE+";F)V",null,null);
        m.visitCode();m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"render","(F)V",null,null);
        m.visitCode();m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        w.visitEnd();return w.toByteArray();
    }
    private static byte[] renderer(Case v) {
        String parent=v==Case.UNKNOWN_RENDERER_BASE ? "arbitrary/client/UnknownRenderBase" : MC_RENDER;
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,RENDERER,null,parent,null);
        w.visitField(Opcodes.ACC_PRIVATE,"visual","L"+MODEL+";",null,null).visitEnd();
        MethodVisitor ctor=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.visitCode();ctor.visitVarInsn(Opcodes.ALOAD,0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false);
        if(v!=Case.MODEL_ALLOCATION_MISSING){
            ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitTypeInsn(Opcodes.NEW,MODEL);ctor.visitInsn(Opcodes.DUP);
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,MODEL,"<init>","()V",false);
            ctor.visitFieldInsn(Opcodes.PUTFIELD,RENDERER,"visual","L"+MODEL+";");
        }
        ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(0,0);ctor.visitEnd();
        if(v!=Case.NO_RENDER_DRAW){
            var m=w.visitMethod(Opcodes.ACC_PUBLIC,"renderTileEntityAt",DRAW,null,null);
            m.visitCode();m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ALOAD,1);
            m.visitTypeInsn(Opcodes.CHECKCAST,TILE);
            m.visitMethodInsn(Opcodes.INVOKESPECIAL,RENDERER,"drawInternal","(L"+TILE+";)V",false);
            m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
            m=w.visitMethod(Opcodes.ACC_PRIVATE,"drawInternal","(L"+TILE+";)V",null,null);
            m.visitCode();m.visitVarInsn(Opcodes.ALOAD,1);
            m.visitFieldInsn(Opcodes.GETFIELD,TILE,"angle","I");m.visitInsn(Opcodes.POP);
            if(v!=Case.MODEL_ANIMATION_MISSING){
                m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,RENDERER,"visual","L"+MODEL+";");
                m.visitVarInsn(Opcodes.ALOAD,1);m.visitInsn(Opcodes.FCONST_0);
                m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,MODEL,"animate","(L"+TILE+";F)V",false);
            }
            m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,RENDERER,"visual","L"+MODEL+";");
            m.visitLdcInsn(.0625F);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,MODEL,"render","(F)V",false);
            m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        }
        w.visitEnd();return w.toByteArray();
    }
    private static byte[] client(Case v) {
        var w=writer(CLIENT,"java/lang/Object");
        var m=w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"register","()V",null,null);
        m.visitCode();
        if(v!=Case.CLIENT_BIND_MISSING){
            if(v==Case.CLIENT_REGISTER_WITH_ID)emitRegisterWithId(m);
            else emitBinding(m,v==Case.UNRESOLVED_CLIENT_BIND);
            if(v==Case.CLIENT_BIND_DUPLICATE)emitBinding(m,false);
        }
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static void emitRegisterWithId(MethodVisitor m) {
        m.visitLdcInsn(Type.getObjectType(TILE));
        m.visitLdcInsn("Unrelated Registered Tile");
        m.visitTypeInsn(Opcodes.NEW,RENDERER);m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,RENDERER,"<init>","()V",false);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/client/registry/ClientRegistry",
                "registerTileEntity","(Ljava/lang/Class;Ljava/lang/String;L"+MC_RENDER+";)V",false);
    }
    private static void emitBinding(MethodVisitor m,boolean unresolved){
        m.visitLdcInsn(Type.getObjectType(TILE));
        if(unresolved)m.visitInsn(Opcodes.ACONST_NULL);
        else {m.visitTypeInsn(Opcodes.NEW,RENDERER);m.visitInsn(Opcodes.DUP);
            m.visitMethodInsn(Opcodes.INVOKESPECIAL,RENDERER,"<init>","()V",false);}
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/client/registry/ClientRegistry",
            "bindTileEntitySpecialRenderer","(Ljava/lang/Class;L"+MC_RENDER+";)V",false);
    }
}
