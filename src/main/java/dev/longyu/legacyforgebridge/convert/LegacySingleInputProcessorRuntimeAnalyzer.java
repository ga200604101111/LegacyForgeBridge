package dev.longyu.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Additional fail-closed runtime proof for an already admitted single-input processor topology.
 * This stage proves sided extraction and the optional legacy energy contract before a generated
 * machine is allowed to use the modern runtime.
 */
public final class LegacySingleInputProcessorRuntimeAnalyzer {
    private static final String ENERGY_HANDLER="cofh/api/energy/IEnergyHandler";

    public record Proof(boolean sidedExtractionProven, boolean legacyEnergyApiPresent,
                        int minUseEnergy, int maxUseEnergy, String energyNbtKey,
                        boolean energyAccelerationProven, List<String> diagnostics) {
        public Proof { energyNbtKey=energyNbtKey==null?"":energyNbtKey;diagnostics=List.copyOf(diagnostics); }
        public boolean complete(){return sidedExtractionProven&&(!legacyEnergyApiPresent
                || minUseEnergy>0&&maxUseEnergy>=minUseEnergy&&!energyNbtKey.isBlank()&&energyAccelerationProven);}
    }

    public Proof analyze(Path jarPath, LegacySingleInputProcessorAnalyzer.Rule machine)throws IOException{
        Map<String,ClassNode> classes=loadClasses(jarPath);List<String> diagnostics=new ArrayList<>();
        MethodNode take=effectiveMethod(classes,machine.sourceTileClass(),Set.of("canExtractItem","func_102008_b"),
                "(ILnet/minecraft/item/ItemStack;I)Z");
        boolean extraction=canonicalExtraction(take);
        if(!extraction)diagnostics.add("Sided extraction is not proven equivalent to side != DOWN || slot != inputSlot.");

        boolean energyPresent=hierarchyContains(classes,machine.sourceTileClass(),ENERGY_HANDLER);
        if(!energyPresent)return new Proof(extraction,false,0,0,"",false,diagnostics);
        MethodNode tick=effectiveMethod(classes,machine.sourceTileClass(),Set.of("updateEntity","func_145845_h"),"()V");
        String minName=findMinEnergyMethod(classes,machine.sourceTileClass(),tick);
        Integer min=minName==null?null:returnedInt(effectiveMethod(classes,machine.sourceTileClass(),Set.of(minName),"()I"));
        MethodNode receive=effectiveMethod(classes,machine.sourceTileClass(),Set.of("receiveEnergy"),
                "(Lnet/minecraftforge/common/util/ForgeDirection;IZ)I");
        Integer max=findMaxEnergy(classes,machine.sourceTileClass(),receive,min==null?0:min);
        String key=energyNbtKey(classes,machine.sourceTileClass());
        boolean accelerated=minName!=null&&tick!=null&&countCalls(tick,minName,"()I")>=3
                &&hasOpcodes(tick,Opcodes.IDIV,Opcodes.IADD,Opcodes.I2B,Opcodes.IMUL,Opcodes.ISUB);
        if(min==null||min<=0)diagnostics.add("Legacy energy minimum use constant is unresolved.");
        if(max==null||min==null||max<min)diagnostics.add("Legacy energy maximum storage/use constant is unresolved.");
        if(key.isBlank())diagnostics.add("Legacy energy NBT integer key is unresolved.");
        if(!accelerated)diagnostics.add("Legacy energy acceleration/consumption bytecode shape is unresolved.");
        return new Proof(extraction,true,min==null?0:min,max==null?0:max,key,accelerated,diagnostics);
    }

    static int sourceProgressStep(int progress,int energy,int minUseEnergy){
        if(progress==0||energy<=minUseEnergy)return 1;
        return (byte)(energy/minUseEnergy+1);
    }
    static int sourceEnergyAfterStep(int progress,int energy,int minUseEnergy){
        int step=sourceProgressStep(progress,energy,minUseEnergy);
        return progress==0||energy<=minUseEnergy?energy:energy-minUseEnergy*step;
    }

    private static String findMinEnergyMethod(Map<String,ClassNode> classes,String tileClass,MethodNode tick){
        if(tick==null)return null;Map<String,Integer> counts=new LinkedHashMap<>();
        for(AbstractInsnNode insn:tick.instructions)if(insn instanceof MethodInsnNode call&&call.desc.equals("()I")
                &&sourceHierarchyOwns(classes,tileClass,call.owner))counts.merge(call.name,1,Integer::sum);
        String result=null;
        for(var entry:counts.entrySet())if(entry.getValue()>=3){Integer value=returnedInt(effectiveMethod(classes,tileClass,Set.of(entry.getKey()),"()I"));
            if(value!=null&&value>0){if(result!=null)return null;result=entry.getKey();}}
        return result;
    }
    private static Integer findMaxEnergy(Map<String,ClassNode> classes,String tileClass,MethodNode receive,int min){
        if(receive==null)return null;Set<Integer> values=new LinkedHashSet<>();
        for(AbstractInsnNode insn:receive.instructions)if(insn instanceof MethodInsnNode call&&call.desc.equals("()I")
                &&sourceHierarchyOwns(classes,tileClass,call.owner)){
            Integer value=returnedInt(effectiveMethod(classes,tileClass,Set.of(call.name),"()I"));if(value!=null&&value>=min)values.add(value);
        }
        return values.size()==1?values.iterator().next():null;
    }
    private static int countCalls(MethodNode method,String name,String desc){int count=0;for(AbstractInsnNode insn:method.instructions)
        if(insn instanceof MethodInsnNode call&&call.name.equals(name)&&call.desc.equals(desc))count++;return count;}
    private static String energyNbtKey(Map<String,ClassNode> classes,String tileClass){
        ClassNode tile=classes.get(tileClass);String owner=tile==null?null:tile.superName;
        while(owner!=null){ClassNode node=classes.get(owner);if(node==null)break;
            MethodNode read=instanceMethod(node,Set.of("readFromNBT","func_145839_a"),"(Lnet/minecraft/nbt/NBTTagCompound;)V");
            MethodNode write=instanceMethod(node,Set.of("writeToNBT","func_145841_b"),"(Lnet/minecraft/nbt/NBTTagCompound;)V");
            if(read!=null&&write!=null&&calls(read,"net/minecraft/nbt/NBTTagCompound","func_74762_e","(Ljava/lang/String;)I")
                    &&calls(write,"net/minecraft/nbt/NBTTagCompound","func_74768_a","(Ljava/lang/String;I)V")){
                Set<String> common=strings(read);common.retainAll(strings(write));if(common.size()==1)return common.iterator().next();
            }
            owner=node.superName;
        }
        return "";
    }

    private static boolean canonicalExtraction(MethodNode method){
        if(method==null)return false;
        for(int side=0;side<6;side++)for(int slot=0;slot<3;slot++){
            Boolean value=evaluate(method,slot,side);if(value==null||value!=(side!=0||slot!=0))return false;
        }
        return true;
    }
    private static Boolean evaluate(MethodNode method,int slot,int side){
        List<AbstractInsnNode> code=real(method);Map<AbstractInsnNode,Integer> index=new IdentityHashMap<>();for(int i=0;i<code.size();i++)index.put(code.get(i),i);
        ArrayDeque<Integer> stack=new ArrayDeque<>();int pc=0,steps=0;
        while(pc>=0&&pc<code.size()&&++steps<128){AbstractInsnNode insn=code.get(pc);int op=insn.getOpcode();
            if(insn instanceof VarInsnNode variable&&op==Opcodes.ILOAD){if(variable.var==1)stack.push(slot);else if(variable.var==3)stack.push(side);else return null;pc++;continue;}
            Integer constant=intConstant(insn);if(constant!=null){stack.push(constant);pc++;continue;}
            if(insn instanceof JumpInsnNode jump){int target=target(jump.label,index);if(target<0)return null;boolean take;
                switch(op){
                    case Opcodes.IFEQ->take=!stack.isEmpty()&&stack.pop()==0;
                    case Opcodes.IFNE->take=!stack.isEmpty()&&stack.pop()!=0;
                    case Opcodes.IF_ICMPEQ,Opcodes.IF_ICMPNE,Opcodes.IF_ICMPLT,Opcodes.IF_ICMPGE,Opcodes.IF_ICMPGT,Opcodes.IF_ICMPLE->{
                        if(stack.size()<2)return null;int right=stack.pop(),left=stack.pop();take=switch(op){
                            case Opcodes.IF_ICMPEQ->left==right;case Opcodes.IF_ICMPNE->left!=right;case Opcodes.IF_ICMPLT->left<right;
                            case Opcodes.IF_ICMPGE->left>=right;case Opcodes.IF_ICMPGT->left>right;default->left<=right;};}
                    case Opcodes.GOTO->{pc=target;continue;}
                    default->{return null;}
                }
                pc=take?target:pc+1;continue;
            }
            if(op==Opcodes.IRETURN)return stack.isEmpty()?null:stack.pop()!=0;
            return null;
        }
        return null;
    }
    private static int target(LabelNode label,Map<AbstractInsnNode,Integer> indices){for(AbstractInsnNode node=label;node!=null;node=node.getNext()){Integer value=indices.get(node);if(value!=null)return value;}return -1;}

    private static boolean sourceHierarchyOwns(Map<String,ClassNode> classes,String tileClass,String owner){
        for(String current=tileClass;current!=null;){if(current.equals(owner))return true;ClassNode node=classes.get(current);current=node==null?null:node.superName;}return false;
    }
    private static boolean hierarchyContains(Map<String,ClassNode> classes,String owner,String target){return inherits(classes,owner,target)||implementsType(classes,owner,target);}
    private static boolean inherits(Map<String,ClassNode> classes,String owner,String target){Set<String> visited=new HashSet<>();while(owner!=null&&visited.add(owner)){if(owner.equals(target))return true;ClassNode node=classes.get(owner);owner=node==null?null:node.superName;}return false;}
    private static boolean implementsType(Map<String,ClassNode> classes,String owner,String target){Set<String> visited=new HashSet<>();ArrayDeque<String> queue=new ArrayDeque<>();queue.add(owner);while(!queue.isEmpty()){String next=queue.removeFirst();if(!visited.add(next))continue;if(next.equals(target))return true;ClassNode node=classes.get(next);if(node==null)continue;if(node.superName!=null)queue.add(node.superName);queue.addAll(node.interfaces);}return false;}
    private static MethodNode effectiveMethod(Map<String,ClassNode> classes,String owner,Set<String> names,String desc){Set<String> visited=new HashSet<>();while(owner!=null&&visited.add(owner)){ClassNode node=classes.get(owner);if(node==null)return null;MethodNode method=instanceMethod(node,names,desc);if(method!=null)return method;owner=node.superName;}return null;}
    private static MethodNode instanceMethod(ClassNode owner,Set<String> names,String desc){for(MethodNode method:owner.methods)if((method.access&Opcodes.ACC_STATIC)==0&&names.contains(method.name)&&desc.equals(method.desc))return method;return null;}
    private static Integer returnedInt(MethodNode method){if(method==null)return null;Integer result=null;List<AbstractInsnNode> code=real(method);for(int i=1;i<code.size();i++)if(code.get(i).getOpcode()==Opcodes.IRETURN){Integer value=intConstant(code.get(i-1));if(value==null||result!=null&&!result.equals(value))return null;result=value;}return result;}
    private static Integer intConstant(AbstractInsnNode insn){return switch(insn.getOpcode()){case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer value?value:null;default->null;};}
    private static boolean calls(MethodNode method,String owner,String name,String desc){if(method==null)return false;for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(desc))return true;return false;}
    private static Set<String> strings(MethodNode method){Set<String> values=new LinkedHashSet<>();for(AbstractInsnNode insn:method.instructions)if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof String value)values.add(value);return values;}
    private static boolean hasOpcodes(MethodNode method,int...opcodes){Set<Integer> required=new HashSet<>();for(int op:opcodes)required.add(op);for(AbstractInsnNode insn:method.instructions)required.remove(insn.getOpcode());return required.isEmpty();}
    private static List<AbstractInsnNode> real(MethodNode method){List<AbstractInsnNode> values=new ArrayList<>();for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)values.add(insn);return values;}
    private static Map<String,ClassNode> loadClasses(Path jarPath)throws IOException{Map<String,ClassNode> classes=new LinkedHashMap<>();try(JarFile jar=new JarFile(jarPath.toFile(),false)){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException ignored){}}}return classes;}
}
