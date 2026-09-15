package dev.longyu.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySingleInputProcessorPresentationAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void unrelatedRotatingTwoLayerProcessorPresentationIsAdmittedFromEvidence() throws Exception {
        Path jar=tempDir.resolve("ForeignPresentation.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"foreign/presentation/Tile.class",tile());
            put(out,"foreign/presentation/Handler.class",handler());
            put(out,"foreign/presentation/Gui.class",gui());
            put(out,"foreign/presentation/Menu.class",simple("foreign/presentation/Menu","net/minecraft/inventory/Container"));
            put(out,"foreign/presentation/Client.class",client());
            put(out,"foreign/presentation/Renderer.class",renderer());
            put(out,"foreign/presentation/Model.class",model());
            put(out,"assets/foreign/textures/guis/processor.png",pngHeader(256,256));
            put(out,"assets/foreign/textures/entitys/processor.png",pngHeader(64,64));
        }
        var machine=new LegacySingleInputProcessorAnalyzer.Rule(
                "processor",null,"foreign/presentation/Block","foreign/presentation/Tile","Foreign Tile",
                3,64,0,List.of(1,2),List.of(0),List.of(2,1),List.of(0),400,64.0,7,
                "foreign/presentation/Recipes","lookup","(Lnet/minecraft/item/ItemStack;)Lforeign/presentation/Recipe;",
                true,true,false);
        var analysis=new LegacySingleInputProcessorPresentationAnalyzer().analyze(jar,machine);
        assertTrue(analysis.diagnostics().isEmpty(),analysis.diagnostics().toString());
        var value=analysis.presentation().orElseThrow();
        assertEquals("foreign/presentation/Gui",value.sourceGuiClass());
        assertEquals("foreign:textures/guis/processor.png",value.guiTexture());
        assertEquals(256,value.guiTextureWidth());
        assertEquals(4,value.motionFrames());
        assertEquals(3,value.progressStages());
        assertEquals("foreign/presentation/Renderer",value.sourceRendererClass());
        assertEquals("foreign/presentation/Model",value.sourceModelClass());
        assertEquals("foreign:textures/entitys/processor.png",value.entityTexture());
        assertEquals(25,value.rotatingLower().v());
        assertEquals(0F,value.rotatingLower().y());
        assertEquals(-8F,value.staticUpper().y());
        assertTrue(value.metadataDrivesRoll());
        assertTrue(value.inventoryUsesZeroRotation());
    }

    private static byte[] handler(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/presentation/Handler",null,"java/lang/Object",new String[]{"cpw/mods/fml/common/network/IGuiHandler"});
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"getClientGuiElement","(ILnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;III)Ljava/lang/Object;",null,null);m.visitCode();
        Label hit=new Label(),miss=new Label();m.visitVarInsn(Opcodes.ILOAD,1);m.visitTableSwitchInsn(7,7,miss,hit);m.visitLabel(hit);
        m.visitTypeInsn(Opcodes.NEW,"foreign/presentation/Gui");m.visitInsn(Opcodes.DUP);m.visitVarInsn(Opcodes.ALOAD,2);m.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/entity/player/EntityPlayer","field_71071_by","Lnet/minecraft/entity/player/InventoryPlayer;");
        m.visitVarInsn(Opcodes.ALOAD,3);m.visitVarInsn(Opcodes.ILOAD,4);m.visitVarInsn(Opcodes.ILOAD,5);m.visitVarInsn(Opcodes.ILOAD,6);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","func_147438_o","(III)Lnet/minecraft/tileentity/TileEntity;",false);m.visitTypeInsn(Opcodes.CHECKCAST,"foreign/presentation/Tile");
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/presentation/Gui","<init>","(Lnet/minecraft/entity/player/InventoryPlayer;Lnet/minecraft/tileentity/TileEntity;)V",false);m.visitInsn(Opcodes.ARETURN);
        m.visitLabel(miss);m.visitInsn(Opcodes.ACONST_NULL);m.visitInsn(Opcodes.ARETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] gui(){
        String n="foreign/presentation/Gui",tile="foreign/presentation/Tile";ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"net/minecraft/client/gui/inventory/GuiContainer",null);
        w.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"TEX","Lnet/minecraft/util/ResourceLocation;",null,null).visitEnd();w.visitField(Opcodes.ACC_PRIVATE,"tile","L"+tile+";",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Lnet/minecraft/entity/player/InventoryPlayer;Lnet/minecraft/tileentity/TileEntity;)V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitTypeInsn(Opcodes.NEW,"foreign/presentation/Menu");c.visitInsn(Opcodes.DUP);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/presentation/Menu","<init>","()V",false);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/client/gui/inventory/GuiContainer","<init>","(Lnet/minecraft/inventory/Container;)V",false);c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,2);c.visitTypeInsn(Opcodes.CHECKCAST,tile);c.visitFieldInsn(Opcodes.PUTFIELD,n,"tile","L"+tile+";");c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();
        MethodVisitor b=w.visitMethod(Opcodes.ACC_PROTECTED,"func_146976_a","(FII)V",null,null);b.visitCode();
        draw(b,0,0,0,0,176,166);
        b.visitVarInsn(Opcodes.ALOAD,0);b.visitFieldInsn(Opcodes.GETFIELD,n,"tile","L"+tile+";");b.visitFieldInsn(Opcodes.GETFIELD,tile,"isGrind","I");b.visitInsn(Opcodes.POP);
        b.visitVarInsn(Opcodes.ALOAD,0);b.visitFieldInsn(Opcodes.GETFIELD,n,"tile","L"+tile+";");b.visitFieldInsn(Opcodes.GETFIELD,tile,"grindMotion","I");b.visitIntInsn(Opcodes.BIPUSH,16);b.visitInsn(Opcodes.IMUL);b.visitInsn(Opcodes.POP);
        draw(b,80,28,176,16,16,16);
        b.visitVarInsn(Opcodes.ALOAD,0);b.visitFieldInsn(Opcodes.GETFIELD,n,"tile","L"+tile+";");b.visitFieldInsn(Opcodes.GETFIELD,tile,"progress","I");b.visitIntInsn(Opcodes.BIPUSH,6);b.visitInsn(Opcodes.IMUL);b.visitInsn(Opcodes.POP);
        draw(b,80,46,192,6,16,6);b.visitInsn(Opcodes.ICONST_4);b.visitInsn(Opcodes.POP);b.visitInsn(Opcodes.RETURN);b.visitMaxs(0,0);b.visitEnd();
        MethodVisitor s=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);s.visitCode();s.visitTypeInsn(Opcodes.NEW,"net/minecraft/util/ResourceLocation");s.visitInsn(Opcodes.DUP);s.visitLdcInsn("foreign:textures/guis/processor.png");s.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/util/ResourceLocation","<init>","(Ljava/lang/String;)V",false);s.visitFieldInsn(Opcodes.PUTSTATIC,n,"TEX","Lnet/minecraft/util/ResourceLocation;");s.visitInsn(Opcodes.RETURN);s.visitMaxs(0,0);s.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static void draw(MethodVisitor m,int x,int y,int u,int v,int width,int height){m.visitVarInsn(Opcodes.ALOAD,0);push(m,x);push(m,y);push(m,u);push(m,v);push(m,width);push(m,height);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"foreign/presentation/Gui","func_73729_b","(IIIIII)V",false);}

    private static byte[] client(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/presentation/Client",null,"java/lang/Object",null);MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"bind","()V",null,null);m.visitCode();m.visitLdcInsn(Type.getObjectType("foreign/presentation/Tile"));m.visitLdcInsn("Foreign Tile");m.visitTypeInsn(Opcodes.NEW,"foreign/presentation/Renderer");m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/presentation/Renderer","<init>","()V",false);m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/client/registry/ClientRegistry","registerTileEntity","(Ljava/lang/Class;Ljava/lang/String;Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;)V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] renderer(){
        String n="foreign/presentation/Renderer";ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer",null);w.visitField(Opcodes.ACC_PRIVATE,"model","Lforeign/presentation/Model;",null,null).visitEnd();w.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"TEX","Lnet/minecraft/util/ResourceLocation;",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer","<init>","()V",false);c.visitVarInsn(Opcodes.ALOAD,0);c.visitTypeInsn(Opcodes.NEW,"foreign/presentation/Model");c.visitInsn(Opcodes.DUP);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/presentation/Model","<init>","()V",false);c.visitFieldInsn(Opcodes.PUTFIELD,n,"model","Lforeign/presentation/Model;");c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();
        MethodVisitor r=w.visitMethod(Opcodes.ACC_PUBLIC,"func_147500_a","(Lnet/minecraft/tileentity/TileEntity;DDDF)V",null,null);r.visitCode();r.visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/opengl/GL11","glPushMatrix","()V",false);r.visitLdcInsn(0.5F);r.visitLdcInsn(0.5F);r.visitLdcInsn(0.5F);r.visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/opengl/GL11","glTranslatef","(FFF)V",false);r.visitVarInsn(Opcodes.ALOAD,1);r.visitTypeInsn(Opcodes.CHECKCAST,"foreign/presentation/Tile");r.visitInsn(Opcodes.POP);r.visitLdcInsn(0.0625F);r.visitInsn(Opcodes.POP);r.visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/opengl/GL11","glPopMatrix","()V",false);r.visitInsn(Opcodes.RETURN);r.visitMaxs(0,0);r.visitEnd();
        MethodVisitor inv=w.visitMethod(Opcodes.ACC_PUBLIC,"renderInv","()V",null,null);inv.visitCode();inv.visitVarInsn(Opcodes.ALOAD,0);inv.visitFieldInsn(Opcodes.GETFIELD,n,"model","Lforeign/presentation/Model;");inv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"foreign/presentation/Model","renderInv","()V",false);inv.visitInsn(Opcodes.RETURN);inv.visitMaxs(0,0);inv.visitEnd();
        MethodVisitor s=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);s.visitCode();s.visitTypeInsn(Opcodes.NEW,"net/minecraft/util/ResourceLocation");s.visitInsn(Opcodes.DUP);s.visitLdcInsn("foreign:textures/entitys/processor.png");s.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/util/ResourceLocation","<init>","(Ljava/lang/String;)V",false);s.visitFieldInsn(Opcodes.PUTSTATIC,n,"TEX","Lnet/minecraft/util/ResourceLocation;");s.visitInsn(Opcodes.RETURN);s.visitMaxs(0,0);s.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] model(){
        String n="foreign/presentation/Model",mr="net/minecraft/client/model/ModelRenderer",tile="foreign/presentation/Tile";ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"net/minecraft/client/model/ModelBase",null);w.visitField(Opcodes.ACC_PRIVATE,"lower","L"+mr+";",null,null).visitEnd();w.visitField(Opcodes.ACC_PRIVATE,"upper","L"+mr+";",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/client/model/ModelBase","<init>","()V",false);c.visitVarInsn(Opcodes.ALOAD,0);push(c,64);c.visitFieldInsn(Opcodes.PUTFIELD,n,"field_78090_t","I");c.visitVarInsn(Opcodes.ALOAD,0);push(c,64);c.visitFieldInsn(Opcodes.PUTFIELD,n,"field_78089_u","I");modelPart(c,n,"lower",0,25,-8F,0F,-8F);modelPart(c,n,"upper",0,0,-8F,-8F,-8F);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();
        MethodVisitor r=w.visitMethod(Opcodes.ACC_PUBLIC,"render","(L"+tile+";FFFFFF)V",null,null);r.visitCode();r.visitVarInsn(Opcodes.ALOAD,0);r.visitFieldInsn(Opcodes.GETFIELD,n,"lower","L"+mr+";");r.visitLdcInsn(Math.PI);r.visitVarInsn(Opcodes.ALOAD,1);r.visitMethodInsn(Opcodes.INVOKEVIRTUAL,tile,"getRoll","()I",false);r.visitInsn(Opcodes.I2D);r.visitInsn(Opcodes.DMUL);r.visitInsn(Opcodes.D2F);r.visitLdcInsn(180F);r.visitInsn(Opcodes.FDIV);r.visitFieldInsn(Opcodes.PUTFIELD,mr,"field_78796_g","F");renderPart(r,n,"lower");renderPart(r,n,"upper");r.visitInsn(Opcodes.RETURN);r.visitMaxs(0,0);r.visitEnd();
        MethodVisitor inv=w.visitMethod(Opcodes.ACC_PUBLIC,"renderInv","()V",null,null);inv.visitCode();inv.visitVarInsn(Opcodes.ALOAD,0);inv.visitFieldInsn(Opcodes.GETFIELD,n,"lower","L"+mr+";");inv.visitInsn(Opcodes.FCONST_0);inv.visitFieldInsn(Opcodes.PUTFIELD,mr,"field_78796_g","F");renderPartConst(inv,n,"lower");renderPartConst(inv,n,"upper");inv.visitInsn(Opcodes.RETURN);inv.visitMaxs(0,0);inv.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static void modelPart(MethodVisitor c,String owner,String field,int u,int v,float x,float y,float z){String mr="net/minecraft/client/model/ModelRenderer";c.visitVarInsn(Opcodes.ALOAD,0);c.visitTypeInsn(Opcodes.NEW,mr);c.visitInsn(Opcodes.DUP);c.visitVarInsn(Opcodes.ALOAD,0);push(c,u);push(c,v);c.visitMethodInsn(Opcodes.INVOKESPECIAL,mr,"<init>","(Lnet/minecraft/client/model/ModelBase;II)V",false);c.visitFieldInsn(Opcodes.PUTFIELD,owner,field,"L"+mr+";");c.visitVarInsn(Opcodes.ALOAD,0);c.visitFieldInsn(Opcodes.GETFIELD,owner,field,"L"+mr+";");c.visitLdcInsn(x);if(y==0)c.visitInsn(Opcodes.FCONST_0);else c.visitLdcInsn(y);c.visitLdcInsn(z);push(c,16);push(c,8);push(c,16);c.visitMethodInsn(Opcodes.INVOKEVIRTUAL,mr,"func_78789_a","(FFFIII)Lnet/minecraft/client/model/ModelRenderer;",false);c.visitInsn(Opcodes.POP);c.visitVarInsn(Opcodes.ALOAD,0);c.visitFieldInsn(Opcodes.GETFIELD,owner,field,"L"+mr+";");c.visitInsn(Opcodes.FCONST_0);c.visitInsn(Opcodes.FCONST_0);c.visitInsn(Opcodes.FCONST_0);c.visitMethodInsn(Opcodes.INVOKEVIRTUAL,mr,"func_78793_a","(FFF)V",false);c.visitVarInsn(Opcodes.ALOAD,0);c.visitFieldInsn(Opcodes.GETFIELD,owner,field,"L"+mr+";");c.visitInsn(Opcodes.ICONST_1);c.visitFieldInsn(Opcodes.PUTFIELD,mr,"field_78809_i","Z");}
    private static void renderPart(MethodVisitor m,String owner,String field){m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,owner,field,"Lnet/minecraft/client/model/ModelRenderer;");m.visitVarInsn(Opcodes.FLOAD,7);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/client/model/ModelRenderer","func_78785_a","(F)V",false);}
    private static void renderPartConst(MethodVisitor m,String owner,String field){m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,owner,field,"Lnet/minecraft/client/model/ModelRenderer;");m.visitLdcInsn(0.0625F);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/client/model/ModelRenderer","func_78785_a","(F)V",false);}

    private static byte[] tile(){
        String n="foreign/presentation/Tile";ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"net/minecraft/tileentity/TileEntity",null);w.visitField(Opcodes.ACC_PRIVATE,"roll","F",null,null).visitEnd();w.visitField(Opcodes.ACC_PUBLIC,"isGrind","I",null,null).visitEnd();w.visitField(Opcodes.ACC_PUBLIC,"grindMotion","I",null,null).visitEnd();w.visitField(Opcodes.ACC_PUBLIC,"progress","I",null,null).visitEnd();
        MethodVisitor g=w.visitMethod(Opcodes.ACC_PUBLIC,"getRoll","()I",null,null);g.visitCode();g.visitVarInsn(Opcodes.ALOAD,0);g.visitFieldInsn(Opcodes.GETFIELD,n,"roll","F");g.visitInsn(Opcodes.F2I);g.visitInsn(Opcodes.IRETURN);g.visitMaxs(0,0);g.visitEnd();
        MethodVisitor t=w.visitMethod(Opcodes.ACC_PUBLIC,"func_145845_h","()V",null,null);t.visitCode();t.visitVarInsn(Opcodes.ALOAD,0);t.visitVarInsn(Opcodes.ALOAD,0);t.visitFieldInsn(Opcodes.GETFIELD,n,"roll","F");t.visitVarInsn(Opcodes.ALOAD,0);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,n,"func_145832_p","()I",false);t.visitInsn(Opcodes.I2F);t.visitInsn(Opcodes.FADD);t.visitLdcInsn(360F);t.visitInsn(Opcodes.FREM);t.visitFieldInsn(Opcodes.PUTFIELD,n,"roll","F");for(int i=0;i<2;i++){t.visitVarInsn(Opcodes.ALOAD,0);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,n,"func_145832_p","()I",false);t.visitInsn(Opcodes.POP);}t.visitInsn(Opcodes.RETURN);t.visitMaxs(0,0);t.visitEnd();
        MethodVisitor u=w.visitMethod(Opcodes.ACC_PRIVATE,"updateMeta","(I)V",null,null);u.visitCode();u.visitVarInsn(Opcodes.ALOAD,0);u.visitFieldInsn(Opcodes.GETFIELD,n,"field_145850_b","Lnet/minecraft/world/World;");u.visitInsn(Opcodes.ICONST_0);u.visitInsn(Opcodes.ICONST_0);u.visitInsn(Opcodes.ICONST_0);u.visitVarInsn(Opcodes.ILOAD,1);u.visitInsn(Opcodes.ICONST_2);u.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/World","func_72921_c","(IIIII)Z",false);u.visitInsn(Opcodes.POP);u.visitInsn(Opcodes.RETURN);u.visitMaxs(0,0);u.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] simple(String name,String parent){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,parent,null);MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();w.visitEnd();return w.toByteArray();}
    private static byte[] pngHeader(int width,int height){byte[] value=new byte[24];byte[] sig={(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a};System.arraycopy(sig,0,value,0,8);value[11]=13;value[12]='I';value[13]='H';value[14]='D';value[15]='R';ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN).putInt(16,width).putInt(20,height);return value;}
    private static void push(MethodVisitor m,int value){if(value>=0&&value<=5)m.visitInsn(Opcodes.ICONST_0+value);else if(value<=Byte.MAX_VALUE)m.visitIntInsn(Opcodes.BIPUSH,value);else m.visitIntInsn(Opcodes.SIPUSH,value);}
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
