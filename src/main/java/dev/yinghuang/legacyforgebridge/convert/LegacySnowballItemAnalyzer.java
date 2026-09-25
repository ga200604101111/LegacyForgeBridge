package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Proves the narrow case where a legacy ItemSnowball source type retains vanilla use semantics. */
public final class LegacySnowballItemAnalyzer {
    private static final String ITEM_SNOWBALL = "net/minecraft/item/ItemSnowball";

    public record Rule(String registryName, String sourceClass) { }
    public record Skipped(String registryName, String sourceClass, String reason) { }
    public record Analysis(List<Rule> rules, List<Skipped> skipped, List<String> diagnostics) {
        public Analysis { rules=List.copyOf(rules); skipped=List.copyOf(skipped); diagnostics=List.copyOf(diagnostics); }
    }
    private record ConstructorCall(String owner,String descriptor,List<Object> arguments) { }

    private final Map<String,ClassNode> classes=new LinkedHashMap<>();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear(); load(jarPath);
        LegacyRegistryAnalyzer.Analysis registry=new LegacyRegistryAnalyzer().analyze(jarPath);
        List<Rule> rules=new ArrayList<>(); List<Skipped> skipped=new ArrayList<>();
        for(var registration:registry.items()){
            String source=registration.implementationClass();
            if(!reachesSnowball(source))continue;
            if(ITEM_SNOWBALL.equals(source)){
                if("()V".equals(registration.constructorDescriptor()))rules.add(new Rule(registration.registryName(),source));
                else skipped.add(new Skipped(registration.registryName(),source,"Direct ItemSnowball allocation did not use the vanilla no-arg constructor."));
                continue;
            }
            String unsafe=customSourceMethod(source);
            if(unsafe!=null){skipped.add(new Skipped(registration.registryName(),source,"Source ItemSnowball subclass defines custom method "+unsafe+"; projectile/use semantics require the generic projectile adapter."));continue;}
            String descriptor=registration.constructorDescriptor()==null?"()V":registration.constructorDescriptor();
            Type[] types=Type.getArgumentTypes(descriptor);
            if(types.length!=registration.constructorArguments().size()){
                skipped.add(new Skipped(registration.registryName(),source,"Registered snowball constructor descriptor/argument proof is incomplete."));continue;
            }
            List<Object> supplied=new ArrayList<>(); boolean unresolved=false;
            for(var argument:registration.constructorArguments()){
                if(argument.value()==null&&argument.descriptor().startsWith("L"))unresolved=true;
                supplied.add(argument.value()==null?Unresolved.INSTANCE:argument.value());
            }
            if(unresolved){skipped.add(new Skipped(registration.registryName(),source,"Registered snowball constructor contains an unresolved object argument."));continue;}
            String failure=traceConstructor(source,descriptor,supplied,new HashSet<>());
            if(failure==null)rules.add(new Rule(registration.registryName(),source));
            else skipped.add(new Skipped(registration.registryName(),source,failure));
        }
        return new Analysis(rules,skipped,List.of());
    }

    private String customSourceMethod(String source){
        String current=source; Set<String> seen=new HashSet<>();
        while(current!=null&&seen.add(current)&&!ITEM_SNOWBALL.equals(current)){
            ClassNode node=classes.get(current); if(node==null)return "<unresolved source superclass>";
            for(MethodNode method:node.methods){
                if("<init>".equals(method.name)||"<clinit>".equals(method.name))continue;
                return current+"."+method.name+method.desc;
            }
            current=node.superName;
        }
        return null;
    }

    private String traceConstructor(String owner,String descriptor,List<Object> supplied,Set<String> visiting){
        if(!visiting.add(owner+descriptor))return "Recursive snowball constructor delegation is unsupported.";
        ClassNode node=classes.get(owner); if(node==null)return "Missing source snowball constructor owner "+owner+".";
        MethodNode constructor=node.methods.stream().filter(method->"<init>".equals(method.name)&&descriptor.equals(method.desc)).findFirst().orElse(null);
        if(constructor==null)return "Missing source snowball constructor "+owner+descriptor+".";
        if(!constructor.tryCatchBlocks.isEmpty()||hasBranch(constructor))return "Conditional/exceptional snowball constructor flow is outside the bounded vanilla proof.";
        ConstructorCall call;
        try{call=constructorDelegation(node,constructor,supplied);}catch(AnalyzerException exception){return "Could not prove snowball constructor path: "+exception.getMessage();}
        if(call==null)return "No unique this/super snowball constructor delegation was proven.";
        if(ITEM_SNOWBALL.equals(call.owner()))return "()V".equals(call.descriptor())?null:"Legacy ItemSnowball base constructor was not the vanilla no-arg form.";
        if(!call.owner().equals(owner)&&!call.owner().equals(node.superName))return "Unexpected snowball constructor delegation owner "+call.owner()+".";
        if(!classes.containsKey(call.owner()))return "Snowball constructor path leaves the source JAR before reaching ItemSnowball: "+call.owner()+".";
        return traceConstructor(call.owner(),call.descriptor(),call.arguments(),visiting);
    }

    private ConstructorCall constructorDelegation(ClassNode owner,MethodNode method,List<Object> supplied)throws AnalyzerException{
        Analyzer<SourceValue> analyzer=new Analyzer<>(new SourceInterpreter()); Frame<SourceValue>[] frames=analyzer.analyze(owner.name,method);
        Map<Integer,Object> parameters=parameterLocals(method.desc,supplied); ConstructorCall result=null; int index=0;
        for(AbstractInsnNode instruction:method.instructions){
            if(instruction instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL&&"<init>".equals(call.name)&&(owner.name.equals(call.owner)||owner.superName.equals(call.owner))){
                if(result!=null)return null; Frame<SourceValue> frame=frames[index]; if(frame==null)return null;
                Type[] types=Type.getArgumentTypes(call.desc); int start=frame.getStackSize()-types.length; if(start<=0)return null;
                List<Object> arguments=new ArrayList<>(); for(int i=0;i<types.length;i++){Object value=resolve(frame.getStack(start+i),parameters);if(value==Unresolved.INSTANCE)return null;arguments.add(value);} result=new ConstructorCall(call.owner,call.desc,List.copyOf(arguments));
            } index++;
        }
        return result;
    }

    private static Object resolve(SourceValue value,Map<Integer,Object> parameters){
        if(value==null||value.insns==null||value.insns.size()!=1)return Unresolved.INSTANCE; AbstractInsnNode source=value.insns.iterator().next();
        if(source instanceof LdcInsnNode ldc&&(ldc.cst instanceof Number||ldc.cst instanceof String))return ldc.cst;
        if(source instanceof IntInsnNode integer)return integer.operand;
        if(source instanceof VarInsnNode variable)return parameters.getOrDefault(variable.var,Unresolved.INSTANCE);
        if(source instanceof InsnNode instruction)return switch(instruction.getOpcode()){
            case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;case Opcodes.FCONST_0->0F;case Opcodes.FCONST_1->1F;case Opcodes.FCONST_2->2F;default->Unresolved.INSTANCE;};
        return Unresolved.INSTANCE;
    }
    private static Map<Integer,Object> parameterLocals(String descriptor,List<Object> supplied){Type[] types=Type.getArgumentTypes(descriptor);if(types.length!=supplied.size())return Map.of();Map<Integer,Object> result=new HashMap<>();int local=1;for(int i=0;i<types.length;i++){result.put(local,supplied.get(i));local+=types[i].getSize();}return result;}
    private boolean reachesSnowball(String source){if(source==null)return false;String current=source;Set<String> seen=new HashSet<>();while(current!=null&&seen.add(current)){if(ITEM_SNOWBALL.equals(current))return true;ClassNode node=classes.get(current);if(node==null)return false;current=node.superName;}return false;}
    private static boolean hasBranch(MethodNode method){for(AbstractInsnNode instruction:method.instructions)if(instruction instanceof JumpInsnNode||instruction instanceof TableSwitchInsnNode||instruction instanceof LookupSwitchInsnNode)return true;return false;}
    private void load(Path jarPath)throws IOException{try(JarFile jar=new JarFile(jarPath.toFile())){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode();new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}}}}
    private enum Unresolved{INSTANCE}
}
