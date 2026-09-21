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
 * Source-only proof for the common 1.7 randomDisplayTick family that emits one or more legacy
 * particles at a fixed block-local center with symmetric X/Z random spread and zero velocity.
 *
 * <p>The admission is structural and namespace-agnostic. Particle names are retained as legacy
 * platform identifiers; the client runtime separately decides whether a given 1.7 particle name
 * has an exact modern presentation mapping.</p>
 */
public final class LegacyRandomDisplayParticleAnalyzer {
    private static final String RANDOM_DISPLAY_DESC="(Lnet/minecraft/world/World;IIILjava/util/Random;)V";
    private static final String SPAWN_DESC="(Ljava/lang/String;DDDDDD)V";

    public record Rule(String registryName,String sourceBlockClass,List<String> particles,
                       float centerX,float centerY,float centerZ,float spreadX,float spreadZ) {
        public Rule {
            particles=List.copyOf(particles);
            if(registryName==null||registryName.isBlank()||sourceBlockClass==null||sourceBlockClass.isBlank()
                    ||particles.isEmpty()||particles.size()>8||particles.stream().anyMatch(v->v==null||v.isBlank())
                    ||!finite(centerX,centerY,centerZ,spreadX,spreadZ)||spreadX<0F||spreadZ<0F)
                throw new IllegalArgumentException("Invalid random-display particle rule");
        }
    }
    public record Skipped(String registryName,String sourceBlockClass,String reason){}
    public record Analysis(List<Rule> rules,List<Skipped> skipped,List<String> diagnostics){
        public Analysis{rules=List.copyOf(rules);skipped=List.copyOf(skipped);diagnostics=List.copyOf(diagnostics);}
    }
    private record LocalOffset(int local,float value){}
    private record Jitter(int local,float amplitude,float halfRange){}

    public Analysis analyze(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=load(jarPath);
        var registry=new LegacyRegistryAnalyzer().analyze(jarPath);
        List<Rule> rules=new ArrayList<>(),dummy=rules; // keeps declaration formatting compact
        List<Skipped> skipped=new ArrayList<>();
        for(var registration:registry.blocks()){
            String source=registration.implementationClass();if(source==null)continue;
            MethodNode method=effective(classes,source,Set.of("randomDisplayTick","func_149734_b"),RANDOM_DISPLAY_DESC);
            if(method==null)continue;
            Rule rule=prove(registration.registryName(),source,method);
            if(rule!=null)rules.add(rule);
            else skipped.add(new Skipped(registration.registryName(),source,
                    "randomDisplayTick is outside the admitted fixed-center symmetric-spread zero-velocity particle family"));
        }
        return new Analysis(rules,skipped,registry.diagnostics());
    }

    private static Rule prove(String registryName,String source,MethodNode method){
        for(AbstractInsnNode insn:method.instructions)
            if(insn instanceof JumpInsnNode||insn instanceof TableSwitchInsnNode||insn instanceof LookupSwitchInsnNode)
                return null;
        LocalOffset x=center(method,2),y=center(method,3),z=center(method,4);
        if(x==null||y==null||z==null)return null;
        List<Jitter> jitters=jitters(method,5);
        if(jitters.size()!=2)return null;
        Jitter jx=jitters.get(0),jz=jitters.get(1);
        if(Float.compare(jx.amplitude(),jz.amplitude())!=0||Float.compare(jx.halfRange(),jz.halfRange())!=0
                ||Math.abs(jx.amplitude()-jx.halfRange()*2F)>0.0001F)return null;

        List<AbstractInsnNode> code=real(method);LinkedHashSet<String> particles=new LinkedHashSet<>();
        for(int i=0;i<code.size();i++){
            if(!(code.get(i) instanceof MethodInsnNode call)
                    ||!call.owner.equals("net/minecraft/world/World")
                    ||!Set.of("spawnParticle","func_72869_a").contains(call.name)
                    ||!SPAWN_DESC.equals(call.desc))continue;
            int start=Math.max(0,i-28);String particle=null;Set<Integer> floatLoads=new LinkedHashSet<>();
            int zeros=0,fadd=0,f2d=0;
            for(int j=start;j<i;j++){
                AbstractInsnNode n=code.get(j);
                if(n instanceof LdcInsnNode ldc&&ldc.cst instanceof String text)particle=text;
                if(n instanceof VarInsnNode load&&load.getOpcode()==Opcodes.FLOAD)floatLoads.add(load.var);
                if(n.getOpcode()==Opcodes.DCONST_0)zeros++;
                if(n.getOpcode()==Opcodes.FADD)fadd++;
                if(n.getOpcode()==Opcodes.F2D)f2d++;
            }
            if(particle==null||!floatLoads.containsAll(Set.of(x.local(),y.local(),z.local(),jx.local(),jz.local()))
                    ||zeros<3||fadd<2||f2d<3)return null;
            particles.add(particle);
        }
        if(particles.isEmpty()||particles.size()>8)return null;
        return new Rule(registryName,source,List.copyOf(particles),x.value(),y.value(),z.value(),jx.halfRange(),jz.halfRange());
    }

    private static LocalOffset center(MethodNode method,int coordinateLocal){
        List<AbstractInsnNode> code=real(method);LocalOffset result=null;
        for(int i=0;i+4<code.size();i++){
            if(!(code.get(i) instanceof VarInsnNode load)||load.getOpcode()!=Opcodes.ILOAD||load.var!=coordinateLocal
                    ||code.get(i+1).getOpcode()!=Opcodes.I2F||code.get(i+3).getOpcode()!=Opcodes.FADD
                    ||!(code.get(i+4) instanceof VarInsnNode store)||store.getOpcode()!=Opcodes.FSTORE)continue;
            Float value=floatConstant(code.get(i+2));if(value==null)return null;
            LocalOffset found=new LocalOffset(store.var,value);
            if(result!=null&&!result.equals(found))return null;result=found;
        }
        return result;
    }

    private static List<Jitter> jitters(MethodNode method,int randomLocal){
        List<AbstractInsnNode> code=real(method);LinkedHashMap<Integer,Jitter> values=new LinkedHashMap<>();
        for(int i=0;i+6<code.size();i++){
            if(!(code.get(i) instanceof VarInsnNode random)||random.getOpcode()!=Opcodes.ALOAD||random.var!=randomLocal
                    ||!(code.get(i+1) instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKEVIRTUAL
                    ||!call.owner.equals("java/util/Random")||!call.name.equals("nextFloat")||!call.desc.equals("()F")
                    ||code.get(i+3).getOpcode()!=Opcodes.FMUL||code.get(i+5).getOpcode()!=Opcodes.FSUB
                    ||!(code.get(i+6) instanceof VarInsnNode store)||store.getOpcode()!=Opcodes.FSTORE)continue;
            Float amplitude=floatConstant(code.get(i+2)),half=floatConstant(code.get(i+4));
            if(amplitude==null||half==null||amplitude<0F||half<0F)return List.of();
            Jitter found=new Jitter(store.var,amplitude,half);
            Jitter old=values.putIfAbsent(store.var,found);if(old!=null&&!old.equals(found))return List.of();
        }
        return List.copyOf(values.values());
    }

    private static MethodNode effective(Map<String,ClassNode> classes,String owner,Set<String> names,String desc){
        Set<String> seen=new HashSet<>();
        while(owner!=null&&seen.add(owner)){
            ClassNode node=classes.get(owner);if(node==null)return null;
            for(MethodNode method:node.methods)if(names.contains(method.name)&&desc.equals(method.desc))return method;
            owner=node.superName;
        }
        return null;
    }
    private static List<AbstractInsnNode> real(MethodNode method){
        List<AbstractInsnNode> out=new ArrayList<>();for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)out.add(insn);return out;
    }
    private static Float floatConstant(AbstractInsnNode insn){
        if(insn==null)return null;
        return switch(insn.getOpcode()){
            case Opcodes.FCONST_0->0F;case Opcodes.FCONST_1->1F;case Opcodes.FCONST_2->2F;
            case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Float value?value:null;
            default->null;
        };
    }
    private static boolean finite(float... values){for(float value:values)if(!Float.isFinite(value))return false;return true;}
    private static Map<String,ClassNode> load(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=new LinkedHashMap<>();
        try(JarFile jar=new JarFile(jarPath.toFile(),false)){
            var entries=jar.entries();while(entries.hasMoreElements()){
                JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;
                try(InputStream input=jar.getInputStream(entry)){
                    ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);
                }catch(RuntimeException ignored){}
            }
        }
        return classes;
    }
}
