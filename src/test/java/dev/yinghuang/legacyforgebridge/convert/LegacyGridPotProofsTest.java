package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import static org.junit.jupiter.api.Assertions.*;

class LegacyGridPotProofsTest {
    private static final String TILE="foreign/grid/Tile", STACK="net/minecraft/item/ItemStack", DIR="net/minecraftforge/common/util/ForgeDirection";

    @Test void nineCellTileAndExecutableInsertionBranchAreProven(){
        var shape=LegacyGridPotProofs.tileShape(tile());
        assertNotNull(shape);assertNotNull(shape.setItem());
        assertTrue(LegacyGridPotProofs.canonicalContentInsertionPredicate(insertion(),TILE,shape));
        assertFalse(LegacyGridPotProofs.canonicalContentInsertionPredicate(loose(),TILE,shape));
    }

    private static ClassNode tile(){
        ClassNode c=new ClassNode(Opcodes.ASM9);c.name=TILE;c.superName="net/minecraft/tileentity/TileEntity";
        c.fields.add(new FieldNode(Opcodes.ACC_PRIVATE,"enabled","[Z",null,null));c.fields.add(new FieldNode(Opcodes.ACC_PRIVATE,"items","[L"+STACK+";",null,null));
        MethodNode m=method(c,0,"<init>","()V");m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));push(m,9);m.instructions.add(new IntInsnNode(Opcodes.NEWARRAY,Opcodes.T_BOOLEAN));m.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,TILE,"enabled","[Z"));m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));push(m,9);m.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY,STACK));m.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,TILE,"items","[L"+STACK+";"));m.instructions.add(new InsnNode(Opcodes.RETURN));
        m=method(c,0,"canUpdate","()Z");m.instructions.add(new InsnNode(Opcodes.ICONST_0));m.instructions.add(new InsnNode(Opcodes.IRETURN));
        m=method(c,Opcodes.ACC_STATIC,"slot","(FFL"+DIR+";)I");m.instructions.add(new VarInsnNode(Opcodes.ALOAD,2));m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,DIR,"offsetX","I"));m.instructions.add(new InsnNode(Opcodes.I2F));m.instructions.add(new InsnNode(Opcodes.FCONST_1));m.instructions.add(new InsnNode(Opcodes.FMUL));m.instructions.add(new InsnNode(Opcodes.F2I));m.instructions.add(new VarInsnNode(Opcodes.ALOAD,2));m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,DIR,"offsetZ","I"));m.instructions.add(new InsnNode(Opcodes.I2F));m.instructions.add(new InsnNode(Opcodes.FCONST_1));m.instructions.add(new InsnNode(Opcodes.FMUL));m.instructions.add(new InsnNode(Opcodes.F2I));push(m,3);m.instructions.add(new InsnNode(Opcodes.IMUL));m.instructions.add(new InsnNode(Opcodes.IADD));push(m,9);m.instructions.add(new InsnNode(Opcodes.POP));push(m,8);m.instructions.add(new InsnNode(Opcodes.POP));m.instructions.add(new InsnNode(Opcodes.IRETURN));
        m=method(c,0,"enable","(I)V");arrayStore(m,"enabled","[Z",Opcodes.ICONST_1,Opcodes.BASTORE);dirty(m);
        m=method(c,0,"removeItem","(I)L"+STACK+";");arrayStore(m,"items","[L"+STACK+";",Opcodes.ACONST_NULL,Opcodes.AASTORE);m.instructions.add(new InsnNode(Opcodes.ACONST_NULL));m.instructions.add(new InsnNode(Opcodes.ARETURN));
        m=method(c,0,"removeCell","(I)L"+STACK+";");arrayStore(m,"enabled","[Z",Opcodes.ICONST_0,Opcodes.BASTORE);m.instructions.add(new InsnNode(Opcodes.ACONST_NULL));m.instructions.add(new InsnNode(Opcodes.ARETURN));
        m=method(c,0,"setItem","(ILnet/minecraft/item/Item;I)V");readArray(m,"enabled","[Z",Opcodes.BALOAD);m.instructions.add(new InsnNode(Opcodes.POP));m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,TILE,"items","[L"+STACK+";"));m.instructions.add(new VarInsnNode(Opcodes.ILOAD,1));m.instructions.add(new TypeInsnNode(Opcodes.NEW,STACK));m.instructions.add(new InsnNode(Opcodes.DUP));m.instructions.add(new VarInsnNode(Opcodes.ALOAD,2));m.instructions.add(new InsnNode(Opcodes.ICONST_1));m.instructions.add(new VarInsnNode(Opcodes.ILOAD,3));m.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,STACK,"<init>","(Lnet/minecraft/item/Item;II)V",false));m.instructions.add(new InsnNode(Opcodes.AASTORE));dirty(m);
        m=method(c,0,"item","(I)Lnet/minecraft/item/Item;");m.instructions.add(new InsnNode(Opcodes.ACONST_NULL));m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,STACK,"func_77973_b","()Lnet/minecraft/item/Item;",false));m.instructions.add(new InsnNode(Opcodes.ARETURN));
        m=method(c,0,"meta","(I)I");m.instructions.add(new InsnNode(Opcodes.ACONST_NULL));m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,STACK,"func_77960_j","()I",false));m.instructions.add(new InsnNode(Opcodes.IRETURN));
        m=method(c,0,"enabled","(I)Z");readArray(m,"enabled","[Z",Opcodes.BALOAD);m.instructions.add(new InsnNode(Opcodes.IRETURN));
        m=method(c,0,"dropAll","(Lnet/minecraft/world/World;)V");readArray(m,"enabled","[Z",Opcodes.BALOAD);m.instructions.add(new InsnNode(Opcodes.POP));readArray(m,"items","[L"+STACK+";",Opcodes.AALOAD);m.instructions.add(new InsnNode(Opcodes.POP));push(m,9);m.instructions.add(new InsnNode(Opcodes.POP));m.instructions.add(new TypeInsnNode(Opcodes.NEW,STACK));m.instructions.add(new InsnNode(Opcodes.POP));m.instructions.add(new InsnNode(Opcodes.RETURN));
        m=method(c,0,"empty","()Z");readArray(m,"enabled","[Z",Opcodes.BALOAD);m.instructions.add(new InsnNode(Opcodes.POP));push(m,9);m.instructions.add(new InsnNode(Opcodes.POP));m.instructions.add(new InsnNode(Opcodes.ICONST_0));m.instructions.add(new InsnNode(Opcodes.IRETURN));m.instructions.add(new InsnNode(Opcodes.ICONST_1));m.instructions.add(new InsnNode(Opcodes.IRETURN));
        return c;
    }

    private static MethodNode insertion(){
        MethodNode m=new MethodNode(Opcodes.ASM9,0,"use","()V",null,null);LabelNode yes=new LabelNode(),no=new LabelNode();
        m.instructions.add(new InsnNode(Opcodes.ACONST_NULL));m.instructions.add(new InsnNode(Opcodes.ICONST_0));m.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,STACK,"func_77973_b","()Lnet/minecraft/item/Item;",false));m.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,STACK,"func_77960_j","()I",false));m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,TILE,"setItem","(ILnet/minecraft/item/Item;I)V",false));
        m.instructions.add(new InsnNode(Opcodes.ACONST_NULL));m.instructions.add(new InsnNode(Opcodes.ICONST_0));m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,TILE,"removeItem","(I)L"+STACK+";",false));m.instructions.add(new InsnNode(Opcodes.POP));
        m.instructions.add(new InsnNode(Opcodes.ACONST_NULL));m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/minecraft/block/Block","func_149634_a","(Lnet/minecraft/item/Item;)Lnet/minecraft/block/Block;",false));
        for(int v:new int[]{1,13,40}){m.instructions.add(new InsnNode(Opcodes.DUP));m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/block/Block","func_149645_b","()I",false));push(m,v);m.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPEQ,yes));}
        m.instructions.add(new InsnNode(Opcodes.DUP));m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/block/Block","func_149645_b","()I",false));m.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,"foreign/grid/Ids","cross","I"));m.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPNE,no));m.instructions.add(yes);m.instructions.add(new InsnNode(Opcodes.POP));m.instructions.add(new InsnNode(Opcodes.RETURN));m.instructions.add(no);m.instructions.add(new InsnNode(Opcodes.POP));m.instructions.add(new InsnNode(Opcodes.RETURN));return m;
    }
    private static MethodNode loose(){MethodNode m=new MethodNode(Opcodes.ASM9,0,"use","()V",null,null);m.instructions.add(new InsnNode(Opcodes.ACONST_NULL));m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/minecraft/block/Block","func_149634_a","(Lnet/minecraft/item/Item;)Lnet/minecraft/block/Block;",false));for(int v:new int[]{1,13,40}){push(m,v);m.instructions.add(new InsnNode(Opcodes.POP));}m.instructions.add(new InsnNode(Opcodes.RETURN));return m;}
    private static MethodNode method(ClassNode c,int access,String name,String desc){MethodNode m=new MethodNode(Opcodes.ASM9,access,name,desc,null,null);c.methods.add(m);return m;}
    private static void dirty(MethodNode m){m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,TILE,"func_70296_d","()V",false));m.instructions.add(new InsnNode(Opcodes.RETURN));}
    private static void arrayStore(MethodNode m,String field,String desc,int valueOp,int store){m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,TILE,field,desc));m.instructions.add(new VarInsnNode(Opcodes.ILOAD,1));m.instructions.add(new InsnNode(valueOp));m.instructions.add(new InsnNode(store));}
    private static void readArray(MethodNode m,String field,String desc,int load){m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,TILE,field,desc));m.instructions.add(new InsnNode(Opcodes.ICONST_0));m.instructions.add(new InsnNode(load));}
    private static void push(MethodNode m,int v){if(v>=0&&v<=5)m.instructions.add(new InsnNode(Opcodes.ICONST_0+v));else m.instructions.add(new IntInsnNode(Opcodes.BIPUSH,v));}
}
