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
 * Recovers source-proven max-damage and legacy durability-bar semantics for ordinary registered
 * items. Evidence is allocation-specific: a shared implementation class may receive different
 * constructor constants in different registrations.
 */
public final class LegacyDurabilityItemAnalyzer {
    private static final Set<String> MAX_DAMAGE=Set.of("setMaxDamage","func_77656_e");
    private static final String ITEM_STACK="net/minecraft/item/ItemStack";

    public record Rule(String registryName,String sourceClass,int durability,
                       boolean alwaysShowBar,boolean inverseProgressBar) {
        public Rule {
            if(registryName==null||registryName.isBlank()||sourceClass==null||sourceClass.isBlank()||durability<=0)
                throw new IllegalArgumentException("Invalid durability rule");
        }
    }
    public record Analysis(List<Rule> rules,List<String> diagnostics) {
        public Analysis { rules=List.copyOf(rules);diagnostics=List.copyOf(diagnostics); }
    }

    private final Map<String,ClassNode> classes=new LinkedHashMap<>();
    private final List<String> diagnostics=new ArrayList<>();

    public Analysis analyze(Path jarPath)throws IOException{
        classes.clear();diagnostics.clear();load(jarPath);
        var registry=new LegacyRegistryAnalyzer().analyze(jarPath);
        List<Rule> rules=new ArrayList<>();
        for(var item:registry.items()){
            if(item.implementationClass()==null)continue;
            int durability=uniqueDurability(item);
            if(durability<=0)continue;
            boolean always=provesAlwaysShowBar(item.implementationClass());
            boolean inverse=provesInverseProgressBar(item.implementationClass());
            rules.add(new Rule(item.registryName(),item.implementationClass(),durability,always,inverse));
        }
        return new Analysis(rules,List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private int uniqueDurability(LegacyRegistryAnalyzer.Registration registration){
        Set<Integer> values=new LinkedHashSet<>();
        for(ClassNode node:sourceLineage(registration.implementationClass()))for(MethodNode method:node.methods){
            if(!"<init>".equals(method.name))continue;
            for(AbstractInsnNode instruction:method.instructions){
                if(!(instruction instanceof MethodInsnNode call)||!MAX_DAMAGE.contains(call.name)
                        ||!"(I)Lnet/minecraft/item/Item;".equals(call.desc))continue;
                AbstractInsnNode source=previousReal(instruction.getPrevious());
                Integer value=intConstant(source);
                if(value==null)value=registrationIntValue(registration,node,method,source);
                if(value!=null&&value>0)values.add(value);
            }
        }
        return values.size()==1?values.iterator().next():0;
    }

    private Integer registrationIntValue(LegacyRegistryAnalyzer.Registration registration,ClassNode owner,
                                         MethodNode constructor,AbstractInsnNode source){
        if(!owner.name.equals(registration.implementationClass())
                ||registration.constructorDescriptor()==null
                ||!registration.constructorDescriptor().equals(constructor.desc))return null;
        if(!(source instanceof VarInsnNode load)||load.getOpcode()!=Opcodes.ILOAD)return null;
        int argument=constructorArgumentIndex(constructor.desc,load.var);
        if(argument<0||argument>=registration.constructorArguments().size())return null;
        Object value=registration.constructorArguments().get(argument).value();
        if(!(value instanceof Number number))return null;
        double raw=number.doubleValue();
        return Double.isFinite(raw)&&raw==Math.rint(raw)&&raw>0&&raw<=Integer.MAX_VALUE?(int)raw:null;
    }

    private boolean provesAlwaysShowBar(String sourceClass){
        MethodNode method=effective(sourceClass,Set.of("showDurabilityBar"),
                "(Lnet/minecraft/item/ItemStack;)Z");
        if(method==null)return false;
        List<AbstractInsnNode> code=real(method);
        return code.size()==2&&code.get(0).getOpcode()==Opcodes.ICONST_1&&code.get(1).getOpcode()==Opcodes.IRETURN;
    }

    /**
     * Exact legacy hook: 1 - stack.getItemDamage()/stack.getMaxDamage(). This is intentionally
     * narrow; arbitrary custom durability formulas are not approximated.
     */
    private boolean provesInverseProgressBar(String sourceClass){
        MethodNode method=effective(sourceClass,Set.of("getDurabilityForDisplay"),
                "(Lnet/minecraft/item/ItemStack;)D");
        if(method==null)return false;
        List<AbstractInsnNode> c=real(method);
        if(c.size()!=10||c.get(0).getOpcode()!=Opcodes.DCONST_1
                ||!aload(c.get(1),1)||!itemStackIntCall(c.get(2),Set.of("getItemDamage","func_77952_i"))
                ||c.get(3).getOpcode()!=Opcodes.I2D||!aload(c.get(4),1)
                ||!itemStackIntCall(c.get(5),Set.of("getMaxDamage","func_77958_k"))
                ||c.get(6).getOpcode()!=Opcodes.I2D||c.get(7).getOpcode()!=Opcodes.DDIV
                ||c.get(8).getOpcode()!=Opcodes.DSUB||c.get(9).getOpcode()!=Opcodes.DRETURN)return false;
        return true;
    }

    private static boolean aload(AbstractInsnNode instruction,int local){
        return instruction instanceof VarInsnNode load&&load.getOpcode()==Opcodes.ALOAD&&load.var==local;
    }
    private static boolean itemStackIntCall(AbstractInsnNode instruction,Set<String> names){
        return instruction instanceof MethodInsnNode call&&call.getOpcode()!=Opcodes.INVOKESTATIC
                &&ITEM_STACK.equals(call.owner)&&names.contains(call.name)&&"()I".equals(call.desc);
    }

    private MethodNode effective(String sourceClass,Set<String> names,String descriptor){
        Set<String> seen=new HashSet<>();
        for(String current=sourceClass;current!=null&&seen.add(current);){
            ClassNode node=classes.get(current);if(node==null)return null;
            for(MethodNode method:node.methods)if(names.contains(method.name)&&descriptor.equals(method.desc))return method;
            current=node.superName;
        }
        return null;
    }
    private List<ClassNode> sourceLineage(String sourceClass){
        List<ClassNode> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(String current=sourceClass;current!=null&&seen.add(current);){
            ClassNode node=classes.get(current);if(node==null)break;result.add(node);current=node.superName;
        }
        return result;
    }
    private static int constructorArgumentIndex(String descriptor,int localIndex){
        Type[] args=Type.getArgumentTypes(descriptor);int local=1;
        for(int i=0;i<args.length;i++){if(local==localIndex)return i;local+=args[i].getSize();}
        return -1;
    }
    private static List<AbstractInsnNode> real(MethodNode method){
        List<AbstractInsnNode> out=new ArrayList<>();
        for(AbstractInsnNode instruction:method.instructions)if(instruction.getOpcode()>=0)out.add(instruction);
        return out;
    }
    private static AbstractInsnNode previousReal(AbstractInsnNode instruction){
        AbstractInsnNode current=instruction;while(current!=null&&current.getOpcode()<0)current=current.getPrevious();return current;
    }
    private static Integer intConstant(AbstractInsnNode instruction){
        if(instruction instanceof InsnNode insn)return switch(insn.getOpcode()){
            case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;
            case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;default->null;};
        if(instruction instanceof IntInsnNode value)return value.operand;
        if(instruction instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer value)return value;
        return null;
    }
    private void load(Path jarPath)throws IOException{
        try(JarFile jar=new JarFile(jarPath.toFile())){
            var entries=jar.entries();while(entries.hasMoreElements()){
                JarEntry entry=entries.nextElement();
                if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;
                try(InputStream input=jar.getInputStream(entry)){
                    ClassNode node=new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                    classes.put(node.name,node);
                }catch(RuntimeException malformed){
                    diagnostics.add("Unreadable durability-item class "+entry.getName()+": "+malformed.getClass().getSimpleName());
                }
            }
        }
    }
}
