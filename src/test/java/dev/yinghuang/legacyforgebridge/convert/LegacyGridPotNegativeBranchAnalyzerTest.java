package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyGridPotNegativeBranchAnalyzerTest {
    private static final String BLOCK="foreign/grid/Block",TILE="foreign/grid/Tile",WORLD="net/minecraft/world/World",PLAYER="net/minecraft/entity/player/EntityPlayer",CAPS="net/minecraft/entity/player/PlayerCapabilities",STACK="net/minecraft/item/ItemStack";

    @Test void canonicalFallbackAndRemoveSlotHelperAreProven(){
        var shape=shape();ClassNode block=block(true,shape);MethodNode activation=activation(shape,block.methods.getFirst(),false);
        assertEquals(List.of(),LegacyGridPotNegativeBranchAnalyzer.proveNegativeBranch(block,activation,TILE,shape));
    }

    @Test void missingCreativeGateFailsClosed(){
        var shape=shape();ClassNode block=block(false,shape);MethodNode activation=activation(shape,block.methods.getFirst(),false);
        var blockers=LegacyGridPotNegativeBranchAnalyzer.proveNegativeBranch(block,activation,TILE,shape);
        assertTrue(blockers.contains("canonical-remove-slot-helper-missing"),blockers.toString());
    }

    @Test void invertedSymbolicRenderRouteFailsClosed(){
        var shape=shape();ClassNode block=block(true,shape);MethodNode activation=activation(shape,block.methods.getFirst(),true);
        var blockers=LegacyGridPotNegativeBranchAnalyzer.proveNegativeBranch(block,activation,TILE,shape);
        assertTrue(blockers.contains("render-predicate-negative-route-unproven"),blockers.toString());
    }

    private static LegacyGridPotProofs.TileShape shape(){
        return new LegacyGridPotProofs.TileShape("enabled","items",
                method("slot","(FFLnet/minecraftforge/common/util/ForgeDirection;)I"),
                method("enable","(I)V"),method("removeCell","(I)L"+STACK+";"),method("removeItem","(I)L"+STACK+";"),
                method("setItem","(ILnet/minecraft/item/Item;I)V"),method("item","(I)Lnet/minecraft/item/Item;"),method("meta","(I)I"),
                method("enabled","(I)Z"),method("dropAll","(L"+WORLD+";)V"),method("empty","()Z"));
    }

    private static ClassNode block(boolean creative,LegacyGridPotProofs.TileShape s){
        ClassNode c=new ClassNode(Opcodes.ASM9);c.name=BLOCK;c.superName="net/minecraft/block/Block";
        String desc="(L"+WORLD+";L"+TILE+";IIIIL"+PLAYER+";)V";MethodNode m=new MethodNode(Opcodes.ASM9,Opcodes.ACC_PRIVATE,"removeSlot",desc,null,null);c.methods.add(m);
        call(m,TILE,s.enabledGetter());call(m,TILE,s.removeCell());call(m,TILE,s.emptyCheck());
        m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,WORLD,"field_72995_K","Z"));
        m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,PLAYER,"field_71075_bZ","L"+CAPS+";"));
        if(creative)m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,CAPS,"field_75098_d","Z"));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,BLOCK,"drop","(L"+WORLD+";IIIL"+STACK+";)V",false));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/minecraft/item/Item","func_150898_a","(Lnet/minecraft/block/Block;)Lnet/minecraft/item/Item;",false));
        m.instructions.add(new TypeInsnNode(Opcodes.NEW,STACK));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,WORLD,"func_147480_a","(IIIZ)Z",false));
        m.instructions.add(new InsnNode(Opcodes.RETURN));return c;
    }

    private static MethodNode activation(LegacyGridPotProofs.TileShape s,MethodNode helper,boolean invertSymbolicRoute){
        MethodNode m=new MethodNode(Opcodes.ASM9,Opcodes.ACC_PUBLIC,"use","()V",null,null);LabelNode positive=new LabelNode(),negative=new LabelNode();
        m.instructions.add(new InsnNode(Opcodes.ACONST_NULL));m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/minecraft/block/Block","func_149634_a","(Lnet/minecraft/item/Item;)Lnet/minecraft/block/Block;",false));
        for(int v:new int[]{1,13,40}){m.instructions.add(new InsnNode(Opcodes.DUP));m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/block/Block","func_149645_b","()I",false));push(m,v);m.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPEQ,positive));}
        m.instructions.add(new InsnNode(Opcodes.DUP));m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/block/Block","func_149645_b","()I",false));m.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,"foreign/grid/Ids","cross","I"));
        m.instructions.add(new JumpInsnNode(invertSymbolicRoute?Opcodes.IF_ICMPEQ:Opcodes.IF_ICMPNE,negative));

        m.instructions.add(positive);m.instructions.add(new InsnNode(Opcodes.POP));
        // Canonical held stack -> setter call lives only on the positive render-identity route.
        m.instructions.add(new InsnNode(Opcodes.ACONST_NULL));m.instructions.add(new InsnNode(Opcodes.ICONST_0));m.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,STACK,"func_77973_b","()Lnet/minecraft/item/Item;",false));m.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,STACK,"func_77960_j","()I",false));call(m,TILE,s.setItem());m.instructions.add(new InsnNode(Opcodes.RETURN));

        m.instructions.add(negative);m.instructions.add(new InsnNode(Opcodes.POP));
        for(int i=0;i<3;i++){call(m,TILE,s.itemGetter());call(m,TILE,s.removeItem());}
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,BLOCK,"drop","(L"+WORLD+";IIIL"+STACK+";)V",false));
        for(int i=0;i<2;i++)m.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,BLOCK,helper.name,helper.desc,false));m.instructions.add(new InsnNode(Opcodes.RETURN));return m;
    }

    private static MethodNode method(String name,String desc){return new MethodNode(Opcodes.ASM9,Opcodes.ACC_PUBLIC,name,desc,null,null);}
    private static void call(MethodNode into,String owner,MethodNode target){into.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,owner,target.name,target.desc,false));}
    private static void push(MethodNode m,int v){if(v>=0&&v<=5)m.instructions.add(new InsnNode(Opcodes.ICONST_0+v));else m.instructions.add(new IntInsnNode(Opcodes.BIPUSH,v));}
}
