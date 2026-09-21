package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Source-only proof for the Forge 1.7 connected cuboid renderer family used by post/beam style
 * blocks. The proof is structural: a registered block must be bound to one renderer identity,
 * implement the full connected-cuboid interface surface, expose source constructor dimensions,
 * and have a source-proven collision policy. No source class is defined or executed.
 */
public final class LegacyConnectedCuboidRendererAnalyzer {
    private static final String BLOCK_ACCESS="net/minecraft/world/IBlockAccess";
    private static final String FORGE_DIRECTION="net/minecraftforge/common/util/ForgeDirection";
    private static final String SET_BOUNDS_DESC="(L"+BLOCK_ACCESS+";IIIZ)Z";
    private static final String LINK_DESC="(L"+BLOCK_ACCESS+";IIIL"+FORGE_DIRECTION+";)Z";
    private static final String CORE_DESC="(L"+BLOCK_ACCESS+";III)V";
    private static final String COLLISION_LIST_DESC="(Lnet/minecraft/world/World;IIILnet/minecraft/util/AxisAlignedBB;Ljava/util/List;Lnet/minecraft/entity/Entity;)V";
    private static final String COLLISION_BOX_DESC="(Lnet/minecraft/world/World;III)Lnet/minecraft/util/AxisAlignedBB;";

    public record Rule(String registryName,String sourceClass,String rendererClass,String interfaceClass,
                       double minWidth,double maxWidth,double minHeight,double maxHeight,
                       boolean axisLocked,boolean sameMetadataOnly,
                       boolean connectFullBlocks,boolean connectWood,boolean connectRock,
                       String collision,String proof) {
        public Rule {
            if(registryName==null||registryName.isBlank()||sourceClass==null||sourceClass.isBlank()
                    ||rendererClass==null||rendererClass.isBlank()||interfaceClass==null||interfaceClass.isBlank())
                throw new IllegalArgumentException("Missing connected cuboid identity");
            if(!finiteRange(minWidth,maxWidth)||!finiteRange(minHeight,maxHeight))
                throw new IllegalArgumentException("Invalid connected cuboid dimensions");
            if(!Set.of("empty","full","inherited").contains(collision))
                throw new IllegalArgumentException("Invalid connected cuboid collision");
            if(proof==null||proof.isBlank())throw new IllegalArgumentException("Missing connected cuboid proof");
        }
        public double size(){return maxWidth-minWidth;}
        private static boolean finiteRange(double min,double max){
            return Double.isFinite(min)&&Double.isFinite(max)&&min>=0&&max<=1&&min<max;
        }
    }
    public record Analysis(List<Rule> rules,List<String> diagnostics) {
        public Analysis {rules=List.copyOf(rules);diagnostics=List.copyOf(diagnostics);}
    }

    public Analysis analyze(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=loadClasses(jarPath);
        LegacyRegistryAnalyzer.Analysis registry=new LegacyRegistryAnalyzer().analyze(jarPath);
        LegacyRegisteredBlockRenderTypeAnalyzer.Analysis renderTypes=new LegacyRegisteredBlockRenderTypeAnalyzer().analyze(jarPath);
        Map<String,LegacyRegistryAnalyzer.Registration> registrations=new HashMap<>();
        for(var registration:registry.blocks())registrations.put(registration.registryName(),registration);
        List<Rule> out=new ArrayList<>();LinkedHashSet<String> diagnostics=new LinkedHashSet<>();
        for(var render:renderTypes.rules()){
            if(render.renderIdentity().constant()!=null)continue;
            LegacyRegistryAnalyzer.Registration registration=registrations.get(render.registryName());
            if(registration==null||registration.implementationClass()==null)continue;
            String renderer=boundRenderer(classes,render.renderIdentity());
            if(renderer==null)continue;
            String iface=connectedInterface(classes,registration.implementationClass());
            if(iface==null||!rendererUsesInterface(classes.get(renderer),iface))continue;
            ClassNode interfaceNode=classes.get(iface);
            String skipName=singleMethodName(interfaceNode,"()Z");
            String metaName=singleMethodName(interfaceNode,"(II)Z");
            String linkName=singleMethodName(interfaceNode,LINK_DESC);
            if(skipName==null||metaName==null||linkName==null)continue;
            MethodNode skip=effectiveMethod(classes,registration.implementationClass(),skipName,"()Z");
            MethodNode meta=effectiveMethod(classes,registration.implementationClass(),metaName,"(II)Z");
            MethodNode link=effectiveMethod(classes,registration.implementationClass(),linkName,LINK_DESC);
            Boolean axisLocked=constantBoolean(skip);
            Boolean sameMetadataOnly=differentMetadataRejected(meta);
            if(axisLocked==null||sameMetadataOnly==null||link==null)continue;
            double[] dimensions=dimensions(classes,registration,axisLocked);
            if(dimensions==null)continue;
            String collision=collisionPolicy(classes,registration.implementationClass());
            if(collision==null)continue;
            boolean fullBlocks=calls(link,"net/minecraft/block/Block",Set.of("func_149721_r","isNormalCube"));
            boolean wood=referencesMaterial(link,Set.of("field_151575_d","wood"));
            boolean rock=referencesMaterial(link,Set.of("field_151578_c","rock"));
            if(!fullBlocks&&!wood&&!rock&&!referencesInterfaceNeighbour(link,iface)){
                diagnostics.add("Connected cuboid link predicate has no admitted neighbour source: "+registration.implementationClass());
                continue;
            }
            out.add(new Rule(render.registryName(),registration.implementationClass(),renderer,iface,
                    dimensions[0],dimensions[1],dimensions[2],dimensions[3],
                    axisLocked,sameMetadataOnly,fullBlocks,wood,rock,collision,
                    "Bound custom renderer + six-direction connected-cuboid interface + constructor-derived dimensions + source link/collision policy"));
        }
        return new Analysis(out,List.copyOf(diagnostics));
    }

    private static String connectedInterface(Map<String,ClassNode> classes,String sourceClass){
        LinkedHashSet<String> candidates=new LinkedHashSet<>();Set<String> seen=new HashSet<>();
        for(String current=sourceClass;current!=null&&seen.add(current);){
            ClassNode node=classes.get(current);if(node==null)break;
            for(String iface:node.interfaces){ClassNode i=classes.get(iface);if(i!=null&&connectedInterfaceShape(i))candidates.add(iface);}
            current=node.superName;
        }
        return candidates.size()==1?candidates.getFirst():null;
    }

    private static boolean connectedInterfaceShape(ClassNode iface){
        int set=0,link=0,core=0,bool=0,floats=0,meta=0,side=0;
        for(MethodNode method:iface.methods){
            if(method.desc.equals(SET_BOUNDS_DESC))set++;
            else if(method.desc.equals(LINK_DESC))link++;
            else if(method.desc.equals(CORE_DESC))core++;
            else if(method.desc.equals("()Z"))bool++;
            else if(method.desc.equals("()F"))floats++;
            else if(method.desc.equals("(II)Z"))meta++;
            else if(method.desc.equals("(I)V"))side++;
        }
        return set==6&&link==1&&core==1&&bool==1&&floats>=5&&meta==1&&side==1;
    }

    private static boolean rendererUsesInterface(ClassNode renderer,String iface){
        if(renderer==null)return false;
        int set=0,link=0,core=0,size=0,skip=0,meta=0;boolean standard=false;
        for(MethodNode method:renderer.methods)for(AbstractInsnNode insn:method.instructions){
            if(!(insn instanceof MethodInsnNode call))continue;
            if(call.owner.equals(iface)){
                if(call.desc.equals(SET_BOUNDS_DESC))set++;
                else if(call.desc.equals(LINK_DESC))link++;
                else if(call.desc.equals(CORE_DESC))core++;
                else if(call.desc.equals("()F"))size++;
                else if(call.desc.equals("()Z"))skip++;
                else if(call.desc.equals("(II)Z"))meta++;
            }
            if(call.owner.equals("net/minecraft/client/renderer/RenderBlocks")
                    &&Set.of("renderStandardBlock","func_147784_q").contains(call.name))standard=true;
        }
        return set>=6&&link>=1&&core>=1&&size>=2&&skip>=1&&meta>=1&&standard;
    }

    private static double[] dimensions(Map<String,ClassNode> classes,LegacyRegistryAnalyzer.Registration registration,boolean axisLocked){
        if(registration.constructorDescriptor()==null||registration.constructorArguments().size()!=Type.getArgumentTypes(registration.constructorDescriptor()).length)return null;
        Type[] types=Type.getArgumentTypes(registration.constructorDescriptor());List<Double> floats=new ArrayList<>();List<Integer> floatArgIndices=new ArrayList<>();
        for(int i=0;i<types.length;i++)if(types[i].getSort()==Type.FLOAT){
            Object raw=registration.constructorArguments().get(i).value();
            if(!(raw instanceof Number number)||!Double.isFinite(number.doubleValue()))return null;
            floats.add((double)number.floatValue());floatArgIndices.add(i);
        }
        double minW,maxW,minH,maxH;
        if(floats.size()==4&&!axisLocked){minW=floats.get(0);maxW=floats.get(1);minH=floats.get(2);maxH=floats.get(3);}
        else if(floats.size()==3&&axisLocked){
            minW=floats.get(0);maxW=floats.get(1);minH=floats.get(2);maxH=1d-minH;
            if(!provesComplementHeight(classes,registration.implementationClass(),registration.constructorDescriptor(),floatArgIndices.get(2)))return null;
        }else return null;
        if(!Rule.finiteRange(minW,maxW)||!Rule.finiteRange(minH,maxH))return null;
        return new double[]{minW,maxW,minH,maxH};
    }

    private static boolean provesComplementHeight(Map<String,ClassNode> classes,String owner,String descriptor,int minHeightArgument){
        ClassNode node=classes.get(owner);if(node==null)return false;MethodNode constructor=find(node,"<init>",descriptor);if(constructor==null)return false;
        int wantedLocal=1;Type[] args=Type.getArgumentTypes(descriptor);
        for(int i=0;i<minHeightArgument;i++)wantedLocal+=args[i].getSize();
        List<AbstractInsnNode> code=real(constructor);
        for(int i=0;i+4<code.size();i++){
            if(!(code.get(i) instanceof VarInsnNode receiver)||receiver.getOpcode()!=Opcodes.ALOAD||receiver.var!=0)continue;
            if(code.get(i+1).getOpcode()!=Opcodes.FCONST_1)continue;
            if(!(code.get(i+2) instanceof VarInsnNode value)||value.getOpcode()!=Opcodes.FLOAD||value.var!=wantedLocal)continue;
            if(code.get(i+3).getOpcode()!=Opcodes.FSUB)continue;
            if(code.get(i+4) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&"F".equals(field.desc))return true;
        }
        return false;
    }

    private static String collisionPolicy(Map<String,ClassNode> classes,String sourceClass){
        MethodNode list=effectiveByDescriptor(classes,sourceClass,COLLISION_LIST_DESC);
        if(list!=null){List<AbstractInsnNode> code=real(list);if(code.size()==1&&code.getFirst().getOpcode()==Opcodes.RETURN)return "empty";}
        MethodNode box=effectiveByDescriptor(classes,sourceClass,COLLISION_BOX_DESC);
        if(box!=null&&fullBlockAabb(box))return "full";
        return null;
    }

    private static boolean fullBlockAabb(MethodNode method){
        int plusOne=0;boolean factory=false;
        for(AbstractInsnNode insn:method.instructions){
            if(insn.getOpcode()==Opcodes.IADD){
                AbstractInsnNode previous=previousReal(insn);
                if(previous!=null&&previous.getOpcode()==Opcodes.ICONST_1)plusOne++;
            }
            if(insn instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC
                    &&call.owner.equals("net/minecraft/util/AxisAlignedBB")
                    &&Set.of("func_72330_a","getBoundingBox").contains(call.name)
                    &&call.desc.startsWith("(DDDDDD)"))factory=true;
        }
        return factory&&plusOne>=3;
    }

    private static Boolean constantBoolean(MethodNode method){
        if(method==null)return null;List<AbstractInsnNode> code=real(method);
        if(code.size()!=2||code.get(1).getOpcode()!=Opcodes.IRETURN)return null;
        Integer value=integer(code.get(0));return value==null||value<0||value>1?null:value==1;
    }

    private static Boolean differentMetadataRejected(MethodNode method){
        if(method==null)return null;
        Boolean same=evaluateBoolean(method,2,2),different=evaluateBoolean(method,2,3);
        if(same==null||different==null)return null;
        if(!same&&different)return true;
        if(!same&&!different)return false;
        return null;
    }

    private static Boolean evaluateBoolean(MethodNode method,int a,int b){
        InsnList instructions=method.instructions;Map<LabelNode,Integer> labels=new IdentityHashMap<>();
        for(int i=0;i<instructions.size();i++)if(instructions.get(i) instanceof LabelNode label)labels.put(label,i);
        ArrayDeque<Integer> stack=new ArrayDeque<>();int pc=0,budget=128;
        while(pc>=0&&pc<instructions.size()&&budget-->0){
            AbstractInsnNode insn=instructions.get(pc);int opcode=insn.getOpcode();
            if(opcode<0){pc++;continue;}
            Integer constant=integer(insn);if(constant!=null){stack.push(constant);pc++;continue;}
            if(insn instanceof VarInsnNode load&&load.getOpcode()==Opcodes.ILOAD){
                if(load.var==1)stack.push(a);else if(load.var==2)stack.push(b);else return null;pc++;continue;
            }
            if(insn instanceof JumpInsnNode jump){
                Integer target=labels.get(jump.label);if(target==null)return null;
                if(opcode==Opcodes.GOTO){pc=target;continue;}
                if(stack.size()<2)return null;int right=stack.pop(),left=stack.pop();
                boolean take=switch(opcode){case Opcodes.IF_ICMPEQ->left==right;case Opcodes.IF_ICMPNE->left!=right;default->false;};
                if(opcode!=Opcodes.IF_ICMPEQ&&opcode!=Opcodes.IF_ICMPNE)return null;
                pc=take?target:pc+1;continue;
            }
            if(opcode==Opcodes.IRETURN){if(stack.size()!=1)return null;int value=stack.pop();return value==0?false:value==1?true:null;}
            return null;
        }
        return null;
    }

    private static String singleMethodName(ClassNode owner,String desc){
        String result=null;for(MethodNode method:owner.methods)if(method.desc.equals(desc)){if(result!=null)return null;result=method.name;}return result;
    }

    private static MethodNode effectiveMethod(Map<String,ClassNode> classes,String sourceClass,String name,String desc){
        Set<String> seen=new HashSet<>();for(String current=sourceClass;current!=null&&seen.add(current);){
            ClassNode node=classes.get(current);if(node==null)return null;MethodNode method=find(node,name,desc);if(method!=null)return method;current=node.superName;
        }return null;
    }

    private static MethodNode effectiveByDescriptor(Map<String,ClassNode> classes,String sourceClass,String desc){
        Set<String> seen=new HashSet<>();for(String current=sourceClass;current!=null&&seen.add(current);){
            ClassNode node=classes.get(current);if(node==null)return null;MethodNode found=null;
            for(MethodNode method:node.methods)if(method.desc.equals(desc)&&(method.access&Opcodes.ACC_STATIC)==0){if(found!=null)return null;found=method;}
            if(found!=null)return found;current=node.superName;
        }return null;
    }

    private static boolean calls(MethodNode method,String owner,Set<String> names){
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(owner)&&names.contains(call.name))return true;return false;
    }
    private static boolean referencesMaterial(MethodNode method,Set<String> names){
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC
                &&field.owner.equals("net/minecraft/block/material/Material")&&names.contains(field.name))return true;return false;
    }
    private static boolean referencesInterfaceNeighbour(MethodNode method,String iface){
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.INSTANCEOF&&type.desc.equals(iface))return true;return false;
    }

    private static String boundRenderer(Map<String,ClassNode> classes,LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity id){
        if(id==null||id.constant()!=null)return null;Set<String> renderers=new LinkedHashSet<>();
        for(ClassNode owner:classes.values())for(MethodNode method:owner.methods)for(AbstractInsnNode insn:method.instructions){
            if(!(insn instanceof FieldInsnNode field)||insn.getOpcode()!=Opcodes.GETSTATIC||!field.owner.equals(id.fieldOwner())||!field.name.equals(id.fieldName()))continue;
            String candidate=null;boolean put=false;int budget=24;
            for(AbstractInsnNode next=insn.getNext();next!=null&&budget-->0;next=next.getNext()){
                if(next instanceof TypeInsnNode type&&next.getOpcode()==Opcodes.NEW)candidate=type.desc;
                if(next instanceof FieldInsnNode object&&next.getOpcode()==Opcodes.GETSTATIC&&object.desc.startsWith("L")&&object.desc.endsWith(";"))
                    candidate=object.desc.substring(1,object.desc.length()-1);
                if(next instanceof MethodInsnNode call&&call.name.equals("put")
                        &&call.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")){put=true;break;}
                if(next instanceof JumpInsnNode||next.getOpcode()==Opcodes.RETURN)break;
            }
            if(put&&candidate!=null&&classes.containsKey(candidate))renderers.add(candidate);
        }
        return renderers.size()==1?renderers.iterator().next():null;
    }

    private static MethodNode find(ClassNode owner,String name,String desc){
        if(owner==null)return null;for(MethodNode method:owner.methods)if(method.name.equals(name)&&method.desc.equals(desc))return method;return null;
    }
    private static List<AbstractInsnNode> real(MethodNode method){
        List<AbstractInsnNode> out=new ArrayList<>();if(method!=null)for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)out.add(insn);return out;
    }
    private static AbstractInsnNode previousReal(AbstractInsnNode insn){
        for(AbstractInsnNode current=insn==null?null:insn.getPrevious();current!=null;current=current.getPrevious())if(current.getOpcode()>=0)return current;return null;
    }
    private static Integer integer(AbstractInsnNode insn){
        if(insn==null)return null;
        return switch(insn.getOpcode()){
            case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;
            case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;
            case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;
            case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer value?value:null;
            default->null;
        };
    }

    private static Map<String,ClassNode> loadClasses(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=new LinkedHashMap<>();
        try(JarFile jar=new JarFile(jarPath.toFile(),false)){
            var entries=jar.entries();while(entries.hasMoreElements()){
                JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;
                try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}
                catch(RuntimeException ignored){}
            }
        }
        return classes;
    }
}
