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

/**
 * Source-only proof for legacy item icons selected by a bounded NBT byte.
 *
 * <p>The admitted family owns one IIcon array with a constructor-proven fixed length, fills every
 * array element from one {@code prefix + index} registerIcons loop, and returns that same array
 * indexed directly by one NBTTagCompound byte key with array[0] as the missing-tag fallback.
 * No item/class/registry names are allow-listed.</p>
 */
public final class LegacyNbtByteIconSelectorAnalyzer {
    private static final String ICON="net/minecraft/util/IIcon";
    private static final String ICON_ARRAY="[L"+ICON+";";
    private static final String ICON_REGISTER="net/minecraft/client/renderer/texture/IIconRegister";
    private static final String ITEM_STACK="net/minecraft/item/ItemStack";
    private static final String NBT="net/minecraft/nbt/NBTTagCompound";
    private static final Set<String> REGISTER_NAMES=Set.of("registerIcons","func_94581_a");
    private static final Set<String> GET_BYTE=Set.of("getByte","func_74771_c");
    private static final Set<String> HAS_KEY=Set.of("hasKey","func_74764_b");

    public record Rule(String registryName,String sourceClass,String nbtKey,String texturePrefix,
                       int variants,int defaultIndex){
        public Rule{
            if(registryName==null||registryName.isBlank()||sourceClass==null||sourceClass.isBlank()
                    ||nbtKey==null||nbtKey.isBlank()||texturePrefix==null||texturePrefix.isBlank()
                    ||variants<2||variants>32||defaultIndex<0||defaultIndex>=variants)
                throw new IllegalArgumentException("Invalid NBT byte icon selector rule");
        }
    }
    public record Skipped(String registryName,String sourceClass,String reason){}
    public record Analysis(List<Rule> rules,List<Skipped> skipped,List<String> diagnostics){
        public Analysis{rules=List.copyOf(rules);skipped=List.copyOf(skipped);diagnostics=List.copyOf(diagnostics);}
    }
    private record ArrayField(String owner,String name){}
    private record Selector(String key,int defaultIndex){}

    public Analysis analyze(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=load(jarPath);
        var registry=new LegacyRegistryAnalyzer().analyze(jarPath);
        List<Rule> rules=new ArrayList<>();List<Skipped> skipped=new ArrayList<>();
        LinkedHashSet<String> diagnostics=new LinkedHashSet<>(registry.diagnostics());

        for(var registration:registry.items()){
            String source=registration.implementationClass();if(source==null||!classes.containsKey(source))continue;
            List<ArrayField> arrays=iconArrays(classes,source);
            List<Rule> candidates=new ArrayList<>();
            for(ArrayField field:arrays){
                Integer count=arrayLength(classes,source,field);
                if(count==null||count<2||count>32)continue;
                String prefix=iconPrefix(classes,source,field,count);
                if(prefix==null)continue;
                Selector selector=selector(classes,source,field);
                if(selector==null||selector.defaultIndex()<0||selector.defaultIndex()>=count)continue;
                candidates.add(new Rule(registration.registryName(),source,selector.key(),prefix,count,selector.defaultIndex()));
            }
            if(candidates.size()==1)rules.add(candidates.getFirst());
            else if(candidates.size()>1)skipped.add(new Skipped(registration.registryName(),source,
                    "Multiple source-proven NBT byte icon arrays are present; selector ownership is ambiguous."));
        }
        rules.sort(Comparator.comparing(Rule::registryName));
        return new Analysis(rules,skipped,List.copyOf(diagnostics));
    }

    private static List<ArrayField> iconArrays(Map<String,ClassNode> classes,String source){
        LinkedHashSet<ArrayField> out=new LinkedHashSet<>();Set<String> seen=new HashSet<>();
        for(String current=source;current!=null&&seen.add(current);){
            ClassNode node=classes.get(current);if(node==null)break;
            for(FieldNode field:node.fields)if(ICON_ARRAY.equals(field.desc)&&!((field.access&Opcodes.ACC_STATIC)!=0))
                out.add(new ArrayField(node.name,field.name));
            current=node.superName;
        }
        return List.copyOf(out);
    }

    private static Integer arrayLength(Map<String,ClassNode> classes,String source,ArrayField field){
        LinkedHashSet<Integer> values=new LinkedHashSet<>();Set<String> seen=new HashSet<>();
        for(String current=source;current!=null&&seen.add(current);){
            ClassNode node=classes.get(current);if(node==null)break;
            for(MethodNode method:node.methods)if(method.name.equals("<init>")){
                List<AbstractInsnNode> code=real(method);
                for(int i=0;i<code.size();i++)if(code.get(i) instanceof FieldInsnNode put
                        &&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(field.owner())&&put.name.equals(field.name())
                        &&ICON_ARRAY.equals(put.desc)){
                    int newArray=-1;
                    for(int j=i-1;j>=Math.max(0,i-5);j--)if(code.get(j) instanceof TypeInsnNode type
                            &&type.getOpcode()==Opcodes.ANEWARRAY&&ICON.equals(type.desc)){newArray=j;break;}
                    if(newArray<1)continue;Integer count=integer(code.get(newArray-1));if(count!=null)values.add(count);
                }
            }
            current=node.superName;
        }
        return values.size()==1?values.getFirst():null;
    }

    private static String iconPrefix(Map<String,ClassNode> classes,String source,ArrayField field,int count){
        LinkedHashSet<String> prefixes=new LinkedHashSet<>();Set<String> seen=new HashSet<>();
        for(String current=source;current!=null&&seen.add(current);){
            ClassNode node=classes.get(current);if(node==null)break;
            for(MethodNode method:node.methods){
                if(!REGISTER_NAMES.contains(method.name)||!method.desc.equals("(L"+ICON_REGISTER+";)V"))continue;
                List<AbstractInsnNode> code=real(method);
                for(int i=0;i<code.size();i++){
                    AbstractInsnNode insn=code.get(i);
                    if(!(insn instanceof MethodInsnNode call)||!call.owner.equals(ICON_REGISTER)
                            ||!call.desc.equals("(Ljava/lang/String;)L"+ICON+";"))continue;
                    int store=-1;
                    for(int j=i+1;j<Math.min(code.size(),i+4);j++)if(code.get(j).getOpcode()==Opcodes.AASTORE){store=j;break;}
                    if(store<0)continue;
                    String prefix=null;Integer loopVar=null;boolean array=false,appendInt=false;
                    for(int j=Math.max(0,i-20);j<i;j++){
                        AbstractInsnNode previous=code.get(j);
                        if(previous instanceof LdcInsnNode ldc&&ldc.cst instanceof String s&&s.contains(":"))prefix=s;
                        if(previous instanceof MethodInsnNode append&&append.owner.equals("java/lang/StringBuilder")
                                &&append.name.equals("append")&&append.desc.equals("(I)Ljava/lang/StringBuilder;"))appendInt=true;
                        if(previous instanceof FieldInsnNode get&&get.getOpcode()==Opcodes.GETFIELD
                                &&get.owner.equals(field.owner())&&get.name.equals(field.name())&&ICON_ARRAY.equals(get.desc))array=true;
                        if(previous instanceof VarInsnNode load&&load.getOpcode()==Opcodes.ILOAD)loopVar=load.var;
                    }
                    if(prefix==null||loopVar==null||!array||!appendInt)continue;
                    if(!provesArrayLengthLoop(code,field,loopVar,count))continue;
                    prefixes.add(prefix);
                }
            }
            current=node.superName;
        }
        return prefixes.size()==1?prefixes.getFirst():null;
    }

    private static boolean provesArrayLengthLoop(List<AbstractInsnNode> code,ArrayField field,int loopVar,int count){
        boolean initZero=false,increment=false,bound=false;
        for(int i=0;i<code.size();i++){
            AbstractInsnNode insn=code.get(i);
            if(insn instanceof VarInsnNode store&&store.getOpcode()==Opcodes.ISTORE&&store.var==loopVar&&i>0
                    &&Integer.valueOf(0).equals(integer(code.get(i-1))))initZero=true;
            if(insn instanceof IincInsnNode inc&&inc.var==loopVar&&inc.incr==1)increment=true;
            if(insn instanceof FieldInsnNode get&&get.getOpcode()==Opcodes.GETFIELD&&get.owner.equals(field.owner())
                    &&get.name.equals(field.name())&&ICON_ARRAY.equals(get.desc)){
                AbstractInsnNode next=i+1<code.size()?code.get(i+1):null;
                if(next!=null&&next.getOpcode()==Opcodes.ARRAYLENGTH){
                    for(int j=Math.max(0,i-2);j<Math.min(code.size(),i+4);j++)
                        if(code.get(j) instanceof VarInsnNode load&&load.getOpcode()==Opcodes.ILOAD&&load.var==loopVar)bound=true;
                }
            }
        }
        return initZero&&increment&&bound&&count>=2;
    }

    private static Selector selector(Map<String,ClassNode> classes,String source,ArrayField field){
        LinkedHashSet<Selector> proven=new LinkedHashSet<>();Set<String> seen=new HashSet<>();
        for(String current=source;current!=null&&seen.add(current);){
            ClassNode node=classes.get(current);if(node==null)break;
            for(MethodNode method:node.methods){
                String ret="L"+ICON+";";
                if(!method.desc.endsWith(")"+ret))continue;
                List<AbstractInsnNode> code=real(method);
                boolean tagField=false,nullGuard=false,fallback=false;
                LinkedHashSet<String> byteKeys=new LinkedHashSet<>(),hasKeys=new LinkedHashSet<>();
                for(int i=0;i<code.size();i++){
                    AbstractInsnNode insn=code.get(i);
                    if(insn instanceof FieldInsnNode get&&get.getOpcode()==Opcodes.GETFIELD&&get.owner.equals(ITEM_STACK)
                            &&get.desc.equals("L"+NBT+";"))tagField=true;
                    if(insn instanceof JumpInsnNode jump&&(jump.getOpcode()==Opcodes.IFNULL||jump.getOpcode()==Opcodes.IFNONNULL))nullGuard=true;
                    if(insn instanceof MethodInsnNode call&&call.owner.equals(NBT)&&call.desc.equals("(Ljava/lang/String;)B")
                            &&GET_BYTE.contains(call.name)){
                        String key=previousString(code,i);if(key!=null&&directArrayIndex(code,i,field))byteKeys.add(key);
                    }
                    if(insn instanceof MethodInsnNode call&&call.owner.equals(NBT)&&HAS_KEY.contains(call.name)
                            &&call.desc.startsWith("(Ljava/lang/String;)")){
                        String key=previousString(code,i);if(key!=null)hasKeys.add(key);
                    }
                    if(insn.getOpcode()==Opcodes.AALOAD&&i>=2&&Integer.valueOf(0).equals(integer(code.get(i-1)))
                            &&code.get(i-2) instanceof FieldInsnNode get&&get.getOpcode()==Opcodes.GETFIELD
                            &&get.owner.equals(field.owner())&&get.name.equals(field.name()))fallback=true;
                }
                if(tagField&&nullGuard&&fallback&&byteKeys.size()==1&&hasKeys.contains(byteKeys.getFirst()))
                    proven.add(new Selector(byteKeys.getFirst(),0));
            }
            current=node.superName;
        }
        return proven.size()==1?proven.getFirst():null;
    }

    private static boolean directArrayIndex(List<AbstractInsnNode> code,int byteCall,ArrayField field){
        if(byteCall+1>=code.size()||code.get(byteCall+1).getOpcode()!=Opcodes.AALOAD)return false;
        for(int j=byteCall-1;j>=Math.max(0,byteCall-10);j--){
            AbstractInsnNode insn=code.get(j);
            if(insn instanceof FieldInsnNode get&&get.getOpcode()==Opcodes.GETFIELD
                    &&get.owner.equals(field.owner())&&get.name.equals(field.name())&&ICON_ARRAY.equals(get.desc))return true;
            if(insn instanceof JumpInsnNode)return false;
        }
        return false;
    }

    private static String previousString(List<AbstractInsnNode> code,int index){
        for(int i=index-1;i>=Math.max(0,index-3);i--)if(code.get(i) instanceof LdcInsnNode ldc&&ldc.cst instanceof String s)return s;
        return null;
    }

    private static Integer integer(AbstractInsnNode insn){
        if(insn==null)return null;return switch(insn.getOpcode()){
            case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;
            case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;
            case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;
            case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer value?value:null;default->null;};
    }
    private static List<AbstractInsnNode> real(MethodNode method){
        List<AbstractInsnNode> out=new ArrayList<>();if(method!=null)for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)out.add(insn);return out;
    }
    private static Map<String,ClassNode> load(Path jarPath)throws IOException{
        Map<String,ClassNode> out=new LinkedHashMap<>();try(JarFile jar=new JarFile(jarPath.toFile(),false)){var entries=jar.entries();while(entries.hasMoreElements()){
            JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;
            try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);out.put(node.name,node);}catch(RuntimeException ignored){}
        }}return out;
    }
}
