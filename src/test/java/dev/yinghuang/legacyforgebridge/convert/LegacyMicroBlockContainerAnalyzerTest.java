package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyMicroBlockContainerAnalyzerTest {
    private static final String BLOCK="foreign/micro/MicroBlock";
    private static final String TILE="foreign/micro/MicroTile";
    private static final String RENDERER="foreign/micro/MicroRenderer";
    private static final String IDS="foreign/micro/Ids";

    @TempDir Path tempDir;

    @Test
    void unrelatedNamespaceRecoversOnlyStructurallyProvenMicroBlockFamily() throws Exception {
        Path jar=tempDir.resolve("foreign-micro.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,IDS+".class",ids());
            put(out,BLOCK+".class",block());
            put(out,TILE+".class",tile());
            put(out,RENDERER+".class",renderer());
            put(out,"foreign/micro/Bootstrap.class",bootstrap());
        }

        var analysis=new LegacyMicroBlockContainerAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(),analysis.diagnostics().toString());
        assertEquals(1,analysis.rules().size());
        var rule=analysis.rules().getFirst();
        assertEquals("micro",rule.registryName());
        assertEquals(BLOCK,rule.sourceBlockClass());
        assertEquals(TILE,rule.sourceTileClass());
        assertEquals(RENDERER,rule.sourceRendererClass());
        assertEquals("slotsNBT",rule.listNbtKey());
        assertEquals("this.slotLength",rule.sizeNbtKey());
        assertEquals(1,rule.minFieldSize());
        assertEquals(16,rule.maxFieldSize());
        assertEquals(3,rule.fallbackFieldSize());
        assertFalse(rule.translucentPass());
        assertTrue(rule.dynamicCellCollision());
    }

    private static byte[] ids(){
        ClassWriter w=new ClassWriter(0);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,IDS,null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"micro","I",null,null).visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] block(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,BLOCK,null,"net/minecraft/block/BlockContainer",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);m.visitCode();m.visitVarInsn(Opcodes.ALOAD,0);m.visitInsn(Opcodes.ACONST_NULL);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/block/BlockContainer","<init>","(Lnet/minecraft/block/material/Material;)V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"getRenderType","()I",null,null);m.visitCode();m.visitFieldInsn(Opcodes.GETSTATIC,IDS,"micro","I");m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"createNewTileEntity","(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;",null,null);m.visitCode();
        m.visitTypeInsn(Opcodes.NEW,TILE);m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,TILE,"<init>","()V",false);m.visitInsn(Opcodes.ARETURN);m.visitMaxs(0,0);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"setInnerPos","(BBBB)V",null,null);m.visitCode();m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        String collision="(Lnet/minecraft/world/World;IIILnet/minecraft/util/AxisAlignedBB;Ljava/util/List;Lnet/minecraft/entity/Entity;)V";
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"addCollisionBoxesToList",collision,null,null);m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitVarInsn(Opcodes.ILOAD,2);m.visitVarInsn(Opcodes.ILOAD,3);m.visitVarInsn(Opcodes.ILOAD,4);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","getTileEntity","(III)Lnet/minecraft/tileentity/TileEntity;",false);
        m.visitTypeInsn(Opcodes.CHECKCAST,TILE);m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.ACONST_NULL);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,TILE,"isExist","(III)Z",false);m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.FCONST_1);m.visitInsn(Opcodes.FCONST_1);m.visitInsn(Opcodes.FDIV);m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD,0);for(int i=0;i<6;i++)m.visitInsn(i==3||i==4||i==5?Opcodes.FCONST_1:Opcodes.FCONST_0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,BLOCK,"setBlockBounds","(FFFFFF)V",false);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ALOAD,1);m.visitVarInsn(Opcodes.ILOAD,2);m.visitVarInsn(Opcodes.ILOAD,3);m.visitVarInsn(Opcodes.ILOAD,4);
        m.visitVarInsn(Opcodes.ALOAD,5);m.visitVarInsn(Opcodes.ALOAD,6);m.visitVarInsn(Opcodes.ALOAD,7);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/block/BlockContainer","addCollisionBoxesToList",collision,false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        w.visitEnd();return w.toByteArray();
    }

    private static byte[] tile(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,TILE,null,"net/minecraft/tileentity/TileEntity",null);
        w.visitField(Opcodes.ACC_PRIVATE,"size","B",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PRIVATE,"slots","[[[Lnet/minecraft/item/ItemStack;",null,null).visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);m.visitCode();m.visitVarInsn(Opcodes.ALOAD,0);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/tileentity/TileEntity","<init>","()V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"setSize","(B)V",null,null);m.visitCode();
        m.visitInsn(Opcodes.ICONST_1);m.visitInsn(Opcodes.POP);m.visitIntInsn(Opcodes.BIPUSH,16);m.visitInsn(Opcodes.POP);m.visitInsn(Opcodes.ICONST_3);m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ILOAD,1);m.visitFieldInsn(Opcodes.PUTFIELD,TILE,"size","B");m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"getFieldSize","()B",null,null);m.visitCode();m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,TILE,"size","B");m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"getVisibleFlg","()[[[B",null,null);m.visitCode();m.visitInsn(Opcodes.ACONST_NULL);m.visitInsn(Opcodes.ARETURN);m.visitMaxs(0,0);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"isExist","(III)Z",null,null);m.visitCode();m.visitInsn(Opcodes.ICONST_1);m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();

        m=w.visitMethod(Opcodes.ACC_PUBLIC,"writeSlots","(Lnet/minecraft/nbt/NBTTagCompound;)V",null,null);m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitLdcInsn("slotsNBT");m.visitIntInsn(Opcodes.BIPUSH,10);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagCompound","getTagList","(Ljava/lang/String;I)Lnet/minecraft/nbt/NBTTagList;",false);m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,TILE,"slots","[[[Lnet/minecraft/item/ItemStack;");m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.ACONST_NULL);m.visitInsn(Opcodes.ACONST_NULL);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","writeToNBT","(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;",false);m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitLdcInsn("this.slotLength");m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,TILE,"size","B");
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagCompound","setByte","(Ljava/lang/String;B)V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();

        m=w.visitMethod(Opcodes.ACC_PUBLIC,"readSlots","(Lnet/minecraft/nbt/NBTTagCompound;)V",null,null);m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitLdcInsn("slotsNBT");m.visitIntInsn(Opcodes.BIPUSH,10);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagCompound","getTagList","(Ljava/lang/String;I)Lnet/minecraft/nbt/NBTTagList;",false);m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,TILE,"slots","[[[Lnet/minecraft/item/ItemStack;");m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.ACONST_NULL);m.visitMethodInsn(Opcodes.INVOKESTATIC,"net/minecraft/item/ItemStack","loadItemStackFromNBT","(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/item/ItemStack;",false);m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitLdcInsn("this.slotLength");m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagCompound","getByte","(Ljava/lang/String;)B",false);m.visitInsn(Opcodes.POP);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();

        m=w.visitMethod(Opcodes.ACC_PUBLIC,"getDescriptionPacket","()Lnet/minecraft/network/Packet;",null,null);m.visitCode();
        m.visitTypeInsn(Opcodes.NEW,"net/minecraft/network/play/server/S35PacketUpdateTileEntity");m.visitInsn(Opcodes.DUP);
        m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_5);m.visitInsn(Opcodes.ACONST_NULL);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/network/play/server/S35PacketUpdateTileEntity","<init>","(IIIILnet/minecraft/nbt/NBTTagCompound;)V",false);m.visitInsn(Opcodes.ARETURN);m.visitMaxs(0,0);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"onDataPacket","(Lnet/minecraft/network/NetworkManager;Lnet/minecraft/network/play/server/S35PacketUpdateTileEntity;)V",null,null);m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,2);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/network/play/server/S35PacketUpdateTileEntity","getNbtCompound","()Lnet/minecraft/nbt/NBTTagCompound;",false);m.visitInsn(Opcodes.POP);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        w.visitEnd();return w.toByteArray();
    }

    private static byte[] renderer(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,RENDERER,null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);m.visitCode();m.visitVarInsn(Opcodes.ALOAD,0);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        m=w.visitMethod(Opcodes.ACC_PUBLIC,"render","(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/block/Block;Lnet/minecraft/world/IBlockAccess;)V",null,null);m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,3);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);
        m.visitMethodInsn(Opcodes.INVOKEINTERFACE,"net/minecraft/world/IBlockAccess","getTileEntity","(III)Lnet/minecraft/tileentity/TileEntity;",true);m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.ACONST_NULL);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,TILE,"getFieldSize","()B",false);m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.ACONST_NULL);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,TILE,"getVisibleFlg","()[[[B",false);m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.FCONST_1);m.visitInsn(Opcodes.ICONST_1);m.visitInsn(Opcodes.I2F);m.visitInsn(Opcodes.FDIV);m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.ACONST_NULL);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,BLOCK,"setInnerPos","(BBBB)V",false);
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitVarInsn(Opcodes.ALOAD,2);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.ICONST_0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/client/renderer/RenderBlocks","renderStandardBlock","(Lnet/minecraft/block/Block;III)Z",false);m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] bootstrap(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/micro/Bootstrap",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"preInit","(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",null,null);
        AnnotationVisitor annotation=m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true);annotation.visitEnd();m.visitCode();
        m.visitTypeInsn(Opcodes.NEW,BLOCK);m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,BLOCK,"<init>","()V",false);m.visitLdcInsn("micro");
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerBlock","(Lnet/minecraft/block/Block;Ljava/lang/String;)V",false);
        m.visitTypeInsn(Opcodes.NEW,"java/util/HashMap");m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/util/HashMap","<init>","()V",false);m.visitVarInsn(Opcodes.ASTORE,2);
        m.visitVarInsn(Opcodes.ALOAD,2);m.visitFieldInsn(Opcodes.GETSTATIC,IDS,"micro","I");m.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Integer","valueOf","(I)Ljava/lang/Integer;",false);
        m.visitTypeInsn(Opcodes.NEW,RENDERER);m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,RENDERER,"<init>","()V",false);
        m.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/Map","put","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",true);m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,3);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{
        out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();
    }
}
