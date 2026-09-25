package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacySeatBedAsmTest {
    @Test void inheritedMemberOwnersAreAcceptedOnlyThroughProvenSourceHierarchy() {
        Map<String,ClassNode> classes=new LinkedHashMap<>();
        ClassNode bed=new ClassNode();bed.name="foreign/BedLike";bed.superName="net/minecraft/block/BlockBed";classes.put(bed.name,bed);
        ClassNode unrelated=new ClassNode();unrelated.name="foreign/NotBed";unrelated.superName="java/lang/Object";classes.put(unrelated.name,unrelated);
        MethodNode method=new MethodNode(Opcodes.ASM9,Opcodes.ACC_PUBLIC,"x","()V",null,null);
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,bed.name,"func_149975_b","(I)Z",false));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,bed.name,"field_70145_X","Z"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        assertTrue(LegacySeatBedAsm.callsHierarchyName(method,classes,"net/minecraft/block/BlockBed",Set.of("func_149975_b"),"(I)Z"));
        assertTrue(LegacySeatBedAsm.hasHierarchyField(method,Opcodes.PUTFIELD,classes,"net/minecraft/block/BlockBed","Z",Set.of("field_70145_X")));
        MethodNode bad=new MethodNode(Opcodes.ASM9,Opcodes.ACC_PUBLIC,"x","()V",null,null);
        bad.instructions.add(new InsnNode(Opcodes.ICONST_0));
        bad.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,unrelated.name,"func_149975_b","(I)Z",false));
        bad.instructions.add(new InsnNode(Opcodes.POP));bad.instructions.add(new InsnNode(Opcodes.RETURN));
        assertFalse(LegacySeatBedAsm.callsHierarchyName(bad,classes,"net/minecraft/block/BlockBed",Set.of("func_149975_b"),"(I)Z"));
    }

    @Test void metadataMaskAndEnumBranchShapesStayStrict() {
        MethodNode mask=new MethodNode(Opcodes.ASM9,Opcodes.ACC_PUBLIC,"x","(I)V",null,null);
        mask.instructions.add(new VarInsnNode(Opcodes.ILOAD,1));mask.instructions.add(new IntInsnNode(Opcodes.BIPUSH,12));mask.instructions.add(new InsnNode(Opcodes.IAND));mask.instructions.add(new JumpInsnNode(Opcodes.IFNE,new LabelNode()));
        assertTrue(LegacySeatBedAsm.intMaskBranch(mask,12,Opcodes.IFNE));
        assertFalse(LegacySeatBedAsm.intMaskBranch(mask,8,Opcodes.IFNE));
        MethodNode branch=new MethodNode(Opcodes.ASM9,Opcodes.ACC_PUBLIC,"x","()V",null,null);
        branch.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,"net/minecraft/entity/player/EntityPlayer$EnumStatus","NOT_POSSIBLE_NOW","Lnet/minecraft/entity/player/EntityPlayer$EnumStatus;"));
        branch.instructions.add(new JumpInsnNode(Opcodes.IF_ACMPNE,new LabelNode()));
        assertTrue(LegacySeatBedAsm.fieldBranch(branch,Opcodes.GETSTATIC,"net/minecraft/entity/player/EntityPlayer$EnumStatus","Lnet/minecraft/entity/player/EntityPlayer$EnumStatus;",Set.of("NOT_POSSIBLE_NOW"),Opcodes.IF_ACMPNE));
    }
}
