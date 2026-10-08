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

/** No mod-specific identifiers: synthetic 1.7.10 TileEntity/NBTTagCompound bytecode. */
final class LegacyTileNbtPersistenceFixture {
    static final String BASE="unrelated/decor/BaseDecorationTile";
    static final String TILE="unrelated/decor/AnimatedDecorationTile";
    static final String VANILLA_TILE="net/minecraft/tileentity/TileEntity";
    static final String NBT="net/minecraft/nbt/NBTTagCompound";
    static final String OTHER="unrelated/decor/OtherReceiver";
    static final String CALLBACK_DESC="(L"+NBT+";)V";
    enum Shape {
        EXACT, SRG, NO_WRITE, NO_READ, DIFFERENT_KEY, WRITE_OTHER_FIELD,
        EXTRA_ARITHMETIC, DUPLICATE_WRITE, BRANCHED_READ, UNPROVED_HELPER,
        WRITE_OTHER_COMPOUND, READ_OTHER_COMPOUND, FIELD_OTHER_RECEIVER,
        SOURCE_SUPER_CHAIN, OVERRIDE_WITHOUT_SUPER, SHADOWED_FIELD,
        PACKET_HOOKS_WITHOUT_NBT, GETTER_WITHOUT_STORE, OPAQUE_SETTER,
        SOURCE_EXTRA_WRITE, EXTRA_FLOAT_PAIR, SOURCE_NBT_GETTER_DYNAMIC_KEY,
        SUPER_WRONG_COMPOUND, SUPER_WRONG_RECEIVER, INHERITED_NO_STATE_PAIRS, EXTRA_RETURN
    }
    static Path jar(Path target,Shape shape) throws IOException {
        try(JarOutputStream output=new JarOutputStream(Files.newOutputStream(target))){
            put(output,BASE+".class",base(shape));
            put(output,TILE+".class",tile(shape));
            put(output,OTHER+".class",other());
        }
        return target;
    }
    private static void put(JarOutputStream output,String name,byte[] bytes)throws IOException {
        output.putNextEntry(new JarEntry(name));output.write(bytes);output.closeEntry();
    }
    private static byte[] base(Shape shape){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,BASE,null,VANILLA_TILE,null);
        w.visitField(Opcodes.ACC_PUBLIC,"framePhase","F",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC,"guardDelay","I",null,null).visitEnd();
        if(shape==Shape.SHADOWED_FIELD)w.visitField(Opcodes.ACC_PUBLIC,"currentYaw","I",null,null).visitEnd();
        ctor(w,BASE,VANILLA_TILE);
        if(shape==Shape.SOURCE_SUPER_CHAIN || shape==Shape.OVERRIDE_WITHOUT_SUPER
                || shape==Shape.SUPER_WRONG_COMPOUND || shape==Shape.SUPER_WRONG_RECEIVER
                || shape==Shape.INHERITED_NO_STATE_PAIRS){
            MethodVisitor set=w.visitMethod(Opcodes.ACC_PUBLIC,"writeToNBT",CALLBACK_DESC,null,null);
            set.visitCode();
            if(shape==Shape.INHERITED_NO_STATE_PAIRS){
                set.visitVarInsn(Opcodes.ALOAD,0);set.visitVarInsn(Opcodes.ALOAD,1);
                set.visitMethodInsn(Opcodes.INVOKESPECIAL,VANILLA_TILE,"writeToNBT",CALLBACK_DESC,false);
            }else store(set,BASE,"guardDelay","I","delay",false,false,shape);
            end(set);
            MethodVisitor get=w.visitMethod(Opcodes.ACC_PUBLIC,"readFromNBT",CALLBACK_DESC,null,null);
            get.visitCode();
            if(shape==Shape.INHERITED_NO_STATE_PAIRS){
                get.visitVarInsn(Opcodes.ALOAD,0);get.visitVarInsn(Opcodes.ALOAD,1);
                get.visitMethodInsn(Opcodes.INVOKESPECIAL,VANILLA_TILE,"readFromNBT",CALLBACK_DESC,false);
            }else load(get,BASE,"guardDelay","I","delay",false,false,shape);
            end(get);
        }
        w.visitEnd();return w.toByteArray();
    }
    private static byte[] tile(Shape shape){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,TILE,null,BASE,null);
        w.visitField(Opcodes.ACC_PUBLIC,"currentYaw","I",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC,"otherYaw","I",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC,"desiredYaw","I",null,null).visitEnd();
        ctor(w,TILE,BASE);
        String writeName=shape==Shape.SRG?"func_145841_b":"writeToNBT";
        String readName=shape==Shape.SRG?"func_145839_a":"readFromNBT";
        if(shape!=Shape.NO_WRITE && shape!=Shape.PACKET_HOOKS_WITHOUT_NBT
                && shape!=Shape.INHERITED_NO_STATE_PAIRS){
            MethodVisitor out=w.visitMethod(Opcodes.ACC_PUBLIC,writeName,CALLBACK_DESC,null,null);out.visitCode();
            if(shape==Shape.SOURCE_SUPER_CHAIN || shape==Shape.SUPER_WRONG_COMPOUND
                    || shape==Shape.SUPER_WRONG_RECEIVER){
                if(shape==Shape.SUPER_WRONG_RECEIVER)out.visitFieldInsn(Opcodes.GETSTATIC,OTHER,"otherTile","L"+TILE+";");
                else out.visitVarInsn(Opcodes.ALOAD,0);
                if(shape==Shape.SUPER_WRONG_COMPOUND)out.visitFieldInsn(Opcodes.GETSTATIC,OTHER,"otherNbt","L"+NBT+";");
                else out.visitVarInsn(Opcodes.ALOAD,1);
                out.visitMethodInsn(Opcodes.INVOKESPECIAL,BASE,"writeToNBT",CALLBACK_DESC,false);
            }
            store(out,TILE,shape==Shape.WRITE_OTHER_FIELD?"otherYaw":"currentYaw","I","yaw",
                    shape==Shape.WRITE_OTHER_COMPOUND,shape==Shape.FIELD_OTHER_RECEIVER,shape);
            if(shape==Shape.DUPLICATE_WRITE)store(out,TILE,"currentYaw","I","yaw",false,false,shape);
            if(shape==Shape.EXTRA_FLOAT_PAIR)store(out,BASE,"framePhase","F","phase",false,false,shape);
            if(shape==Shape.EXTRA_RETURN)out.visitInsn(Opcodes.RETURN);
            end(out);
        }
        if(shape!=Shape.NO_READ && shape!=Shape.PACKET_HOOKS_WITHOUT_NBT
                && shape!=Shape.INHERITED_NO_STATE_PAIRS){
            MethodVisitor in=w.visitMethod(Opcodes.ACC_PUBLIC,readName,CALLBACK_DESC,null,null);in.visitCode();
            if(shape==Shape.SOURCE_SUPER_CHAIN || shape==Shape.SUPER_WRONG_COMPOUND
                    || shape==Shape.SUPER_WRONG_RECEIVER){
                if(shape==Shape.SUPER_WRONG_RECEIVER)in.visitFieldInsn(Opcodes.GETSTATIC,OTHER,"otherTile","L"+TILE+";");
                else in.visitVarInsn(Opcodes.ALOAD,0);
                if(shape==Shape.SUPER_WRONG_COMPOUND)in.visitFieldInsn(Opcodes.GETSTATIC,OTHER,"otherNbt","L"+NBT+";");
                else in.visitVarInsn(Opcodes.ALOAD,1);
                in.visitMethodInsn(Opcodes.INVOKESPECIAL,BASE,"readFromNBT",CALLBACK_DESC,false);
            }
            if(shape==Shape.BRANCHED_READ){
                in.visitInsn(Opcodes.ICONST_0);Label l=new Label();in.visitJumpInsn(Opcodes.IFEQ,l);
                in.visitInsn(Opcodes.RETURN);in.visitLabel(l);
            }
            load(in,TILE,"currentYaw","I",shape==Shape.DIFFERENT_KEY?"differentYaw":"yaw",
                    shape==Shape.READ_OTHER_COMPOUND,
                    shape==Shape.EXTRA_ARITHMETIC,shape);
            if(shape==Shape.EXTRA_FLOAT_PAIR)load(in,BASE,"framePhase","F","phase",false,false,shape);
            end(in);
        }
        if(shape==Shape.PACKET_HOOKS_WITHOUT_NBT||shape==Shape.EXACT){
            MethodVisitor packet=w.visitMethod(Opcodes.ACC_PUBLIC,"getDescriptionPacket",
                    "()Lnet/minecraft/network/Packet;",null,null);packet.visitCode();
            packet.visitInsn(Opcodes.ACONST_NULL);packet.visitInsn(Opcodes.ARETURN);packet.visitMaxs(0,0);packet.visitEnd();
            MethodVisitor receiver=w.visitMethod(Opcodes.ACC_PUBLIC,"onDataPacket",
                    "(Lnet/minecraft/network/NetworkManager;Lnet/minecraft/network/play/server/S35PacketUpdateTileEntity;)V",null,null);
            receiver.visitCode();end(receiver);
        }
        w.visitEnd();return w.toByteArray();
    }
    private static void store(MethodVisitor out,String owner,String field,String desc,String tag,
                              boolean wrongCompound,boolean wrongReceiver,Shape shape){
        if(wrongCompound)out.visitFieldInsn(Opcodes.GETSTATIC,OTHER,"otherNbt","L"+NBT+";");
        else out.visitVarInsn(Opcodes.ALOAD,1);
        if(shape==Shape.SOURCE_NBT_GETTER_DYNAMIC_KEY){
            out.visitVarInsn(Opcodes.ALOAD,0);
            out.visitMethodInsn(Opcodes.INVOKEVIRTUAL,TILE,"readDynamicKey","()Ljava/lang/String;",false);
        }else out.visitLdcInsn(tag);
        if(wrongReceiver)out.visitFieldInsn(Opcodes.GETSTATIC,OTHER,"otherTile","L"+TILE+";");
        else out.visitVarInsn(Opcodes.ALOAD,0);
        out.visitFieldInsn(Opcodes.GETFIELD,owner,field,desc);
        if(shape==Shape.UNPROVED_HELPER)out.visitMethodInsn(Opcodes.INVOKESTATIC,OTHER,"mutate","(I)I",false);
        if(shape==Shape.OPAQUE_SETTER)out.visitMethodInsn(Opcodes.INVOKEVIRTUAL,OTHER,"setInteger","(Ljava/lang/String;I)V",false);
        else out.visitMethodInsn(Opcodes.INVOKEVIRTUAL,NBT,
                desc.equals("F") ? (shape==Shape.SRG?"func_74776_a":"setFloat")
                                 : (shape==Shape.SRG?"func_74768_a":"setInteger"),
                "(Ljava/lang/String;"+desc+")V",false);
        if(shape==Shape.SOURCE_EXTRA_WRITE){
            out.visitVarInsn(Opcodes.ALOAD,0);out.visitInsn(Opcodes.ICONST_2);
            out.visitFieldInsn(Opcodes.PUTFIELD,TILE,"currentYaw","I");
        }
    }
    private static void load(MethodVisitor in,String owner,String field,String desc,String tag,
                             boolean wrongCompound,boolean arithmetic,Shape shape){
        boolean orphan=shape==Shape.GETTER_WITHOUT_STORE;
        if(!orphan)in.visitVarInsn(Opcodes.ALOAD,0);
        if(wrongCompound)in.visitFieldInsn(Opcodes.GETSTATIC,OTHER,"otherNbt","L"+NBT+";");
        else in.visitVarInsn(Opcodes.ALOAD,1);
        in.visitLdcInsn(tag);
        in.visitMethodInsn(Opcodes.INVOKEVIRTUAL,NBT,
                desc.equals("F") ? (shape==Shape.SRG?"func_74760_g":"getFloat")
                                 : (shape==Shape.SRG?"func_74762_e":"getInteger"),
                "(Ljava/lang/String;)"+desc,false);
        if(arithmetic){in.visitInsn(Opcodes.ICONST_1);in.visitInsn(Opcodes.IADD);}
        if(orphan)in.visitInsn(Opcodes.POP);
        else in.visitFieldInsn(Opcodes.PUTFIELD,owner,field,desc);
    }
    private static byte[] other(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,OTHER,null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"otherNbt","L"+NBT+";",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"otherTile","L"+TILE+";",null,null).visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"mutate","(I)I",null,null);
        m.visitCode();m.visitVarInsn(Opcodes.ILOAD,0);m.visitInsn(Opcodes.IRETURN);end(m);
        w.visitEnd();return w.toByteArray();
    }
    private static void ctor(ClassWriter w,String name,String parent){
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false);end(m);
    }
    private static void end(MethodVisitor m){
        // The parent helper already writes RETURN for read/write/constructor handlers.
        // Repeated calls to method.visitMaxs are recomputed by COMPUTE_MAXS.
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
    }
}
