import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Adds branch-free getFluidState/getRenderType delegations to the cumulative ConvertedLegacyBlock binary. */
public final class PatchRev239 {
    static final String BRIDGE="dev/yinghuang/legacyforgebridge/behavior/Rev239VanillaLiquidBridge";
    public static void main(String[] args)throws Exception{
        Path input=Paths.get(args[0]), output=Paths.get(args[1]);
        ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(input)).accept(node,0);
        node.methods.removeIf(m->(m.name.equals("method_9545")&&m.desc.equals("(Lnet/minecraft/class_2680;)Lnet/minecraft/class_3610;"))
                ||(m.name.equals("method_9604")&&m.desc.equals("(Lnet/minecraft/class_2680;)Lnet/minecraft/class_2464;")));
        MethodNode fluid=new MethodNode(Opcodes.ACC_PROTECTED,"method_9545","(Lnet/minecraft/class_2680;)Lnet/minecraft/class_3610;",null,null);
        fluid.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));fluid.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));
        fluid.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"fluidStateOrSuper","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false));
        fluid.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST,"net/minecraft/class_3610"));fluid.instructions.add(new InsnNode(Opcodes.ARETURN));
        fluid.maxStack=2;fluid.maxLocals=2;node.methods.add(fluid);
        MethodNode render=new MethodNode(Opcodes.ACC_PROTECTED,"method_9604","(Lnet/minecraft/class_2680;)Lnet/minecraft/class_2464;",null,null);
        render.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));render.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));
        render.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"renderTypeOrSuper","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false));
        render.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST,"net/minecraft/class_2464"));render.instructions.add(new InsnNode(Opcodes.ARETURN));
        render.maxStack=2;render.maxLocals=2;node.methods.add(render);
        ClassWriter writer=new ClassWriter(0);node.accept(writer);Files.createDirectories(output.getParent());Files.write(output,writer.toByteArray());
    }
}
