package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Fail-closed source proof for a legacy CoFH IEnergyHandler receiver contract. */
public final class LegacySingleInputProcessorEnergyIngressAnalyzer {
    private static final String FD="Lnet/minecraftforge/common/util/ForgeDirection;";

    public record Proof(boolean legacyEnergyApiPresent, boolean allSidesConnect,
                        boolean extractionDisabled, boolean queryMethodsReturnZero,
                        boolean receiveSimulationProven, List<String> diagnostics) {
        public Proof { diagnostics=List.copyOf(diagnostics); }
        public boolean complete() {
            return !legacyEnergyApiPresent || allSidesConnect && extractionDisabled
                    && queryMethodsReturnZero && receiveSimulationProven;
        }
    }

    public Proof analyze(Path jarPath, LegacySingleInputProcessorAnalyzer.Rule machine,
                         LegacySingleInputProcessorRuntimeAnalyzer.Proof runtime) throws IOException {
        Objects.requireNonNull(machine,"machine");Objects.requireNonNull(runtime,"runtime");
        if(!runtime.legacyEnergyApiPresent())return new Proof(false,true,true,true,true,List.of());
        Map<String,ClassNode> classes=loadClasses(jarPath);List<String> diagnostics=new ArrayList<>();
        MethodNode connect=effectiveMethod(classes,machine.sourceTileClass(),Set.of("canConnectEnergy"),"("+FD+")Z");
        MethodNode extract=effectiveMethod(classes,machine.sourceTileClass(),Set.of("extractEnergy"),"("+FD+"IZ)I");
        MethodNode stored=effectiveMethod(classes,machine.sourceTileClass(),Set.of("getEnergyStored"),"("+FD+")I");
        MethodNode capacity=effectiveMethod(classes,machine.sourceTileClass(),Set.of("getMaxEnergyStored"),"("+FD+")I");
        MethodNode receive=effectiveMethod(classes,machine.sourceTileClass(),Set.of("receiveEnergy"),"("+FD+"IZ)I");
        boolean allSides=Boolean.TRUE.equals(returnedBoolean(connect))&&!loadsVariable(connect,1);
        boolean noExtract=Integer.valueOf(0).equals(returnedInt(extract));
        boolean zeroQueries=Integer.valueOf(0).equals(returnedInt(stored))&&Integer.valueOf(0).equals(returnedInt(capacity));
        boolean receiveProven=canonicalReceive(classes,machine.sourceTileClass(),receive,runtime.maxUseEnergy());
        if(!allSides)diagnostics.add("Legacy energy canConnectEnergy is not proven constant true for all directions.");
        if(!noExtract)diagnostics.add("Legacy energy extraction is not proven constant zero.");
        if(!zeroQueries)diagnostics.add("Legacy energy query methods are not proven to return source constant zero.");
        if(!receiveProven)diagnostics.add("Legacy receiveEnergy capacity/simulation mutation shape is unresolved.");
        return new Proof(true,allSides,noExtract,zeroQueries,receiveProven,diagnostics);
    }

    private static boolean canonicalReceive(Map<String,ClassNode> classes,String tileClass,MethodNode method,int maxEnergy){
        if(method==null||maxEnergy<=0||loadsVariable(method,1))return false;
        if(countVar(method,Opcodes.ILOAD,2)<2||countVar(method,Opcodes.ILOAD,3)<2
                ||countOpcode(method,Opcodes.IFNE)<2||countOpcode(method,Opcodes.IF_ICMPGE)<2
                ||countOpcode(method,Opcodes.ISUB)<2||countOpcode(method,Opcodes.IADD)<1)return false;
        if(!returnsLocal(method,4)||countVar(method,Opcodes.ISTORE,4)<2)return false;
        Set<String> energyFields=new LinkedHashSet<>();int gets=0,puts=0;
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof FieldInsnNode field&&field.desc.equals("I")
                &&sourceHierarchyOwns(classes,tileClass,field.owner)){
            energyFields.add(field.owner+"."+field.name);
            if(field.getOpcode()==Opcodes.GETFIELD)gets++;if(field.getOpcode()==Opcodes.PUTFIELD)puts++;
        }
        if(energyFields.size()!=1||gets<3||puts<2)return false;
        int maxCalls=0;
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.desc.equals("()I")
                &&sourceHierarchyOwns(classes,tileClass,call.owner)){
            Integer value=returnedInt(effectiveMethod(classes,tileClass,Set.of(call.name),"()I"));
            if(Integer.valueOf(maxEnergy).equals(value))maxCalls++;
        }
        return maxCalls>=3;
    }
    private static boolean returnsLocal(MethodNode method,int local){if(method==null)return false;List<AbstractInsnNode> code=real(method);for(int i=1;i<code.size();i++)if(code.get(i).getOpcode()==Opcodes.IRETURN&&code.get(i-1) instanceof VarInsnNode var&&var.getOpcode()==Opcodes.ILOAD&&var.var==local)return true;return false;}
    private static boolean loadsVariable(MethodNode method,int local){return countVar(method,Opcodes.ALOAD,local)>0||countVar(method,Opcodes.ILOAD,local)>0;}
    private static int countVar(MethodNode method,int opcode,int local){if(method==null)return 0;int count=0;for(AbstractInsnNode insn:method.instructions)if(insn instanceof VarInsnNode var&&var.getOpcode()==opcode&&var.var==local)count++;return count;}
    private static int countOpcode(MethodNode method,int opcode){if(method==null)return 0;int count=0;for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()==opcode)count++;return count;}
    private static Boolean returnedBoolean(MethodNode method){Integer value=returnedInt(method);return value==null?null:value!=0;}
    private static Integer returnedInt(MethodNode method){if(method==null)return null;Integer result=null;List<AbstractInsnNode> code=real(method);for(int i=1;i<code.size();i++)if(code.get(i).getOpcode()==Opcodes.IRETURN){Integer value=intConstant(code.get(i-1));if(value==null||result!=null&&!result.equals(value))return null;result=value;}return result;}
    private static Integer intConstant(AbstractInsnNode insn){return switch(insn.getOpcode()){case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer value?value:null;default->null;};}
    private static MethodNode effectiveMethod(Map<String,ClassNode> classes,String owner,Set<String> names,String desc){Set<String> visited=new HashSet<>();while(owner!=null&&visited.add(owner)){ClassNode node=classes.get(owner);if(node==null)return null;for(MethodNode method:node.methods)if((method.access&Opcodes.ACC_STATIC)==0&&names.contains(method.name)&&desc.equals(method.desc))return method;owner=node.superName;}return null;}
    private static boolean sourceHierarchyOwns(Map<String,ClassNode> classes,String tileClass,String owner){for(String current=tileClass;current!=null;){if(current.equals(owner))return true;ClassNode node=classes.get(current);current=node==null?null:node.superName;}return false;}
    private static List<AbstractInsnNode> real(MethodNode method){List<AbstractInsnNode> values=new ArrayList<>();if(method!=null)for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)values.add(insn);return values;}
    private static Map<String,ClassNode> loadClasses(Path jarPath)throws IOException{Map<String,ClassNode> classes=new LinkedHashMap<>();try(JarFile jar=new JarFile(jarPath.toFile(),false)){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException ignored){}}}return classes;}
}
