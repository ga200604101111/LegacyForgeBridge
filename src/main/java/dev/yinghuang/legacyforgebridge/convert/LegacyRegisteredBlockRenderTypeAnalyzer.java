package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Source-only inventory of exact legacy Block#getRenderType() identities for registry-proven mod
 * blocks. The bounded proof accepts direct constants/static fields, an exact constructor-bound
 * instance-int field, plus a tiny set of exact 1.7.10 platform-base identities when the complete
 * source-owned lineage omits getRenderType entirely.
 */
public final class LegacyRegisteredBlockRenderTypeAnalyzer {
    private static final Set<String> RENDER_NAMES = Set.of("getRenderType", "func_149645_b");
    private static final String RENDER_DESC = "()I";

    public record RenderIdentity(Integer constant, String fieldOwner, String fieldName) {
        public RenderIdentity {
            boolean constantIdentity = constant != null;
            boolean fieldIdentity = fieldOwner != null && !fieldOwner.isBlank() && fieldName != null && !fieldName.isBlank();
            if (constantIdentity == fieldIdentity) throw new IllegalArgumentException("Render identity must be exactly one of constant or static field");
        }
        public static RenderIdentity constant(int value) { return new RenderIdentity(value, null, null); }
        public static RenderIdentity field(String owner, String name) { return new RenderIdentity(null, owner, name); }
        public boolean isConstant(int value) { return constant != null && constant == value; }
    }

    public record Rule(String registryName, String legacyNamespace, String sourceBlockClass,
                       RenderIdentity renderIdentity) { }
    public record Analysis(List<Rule> rules, List<String> diagnostics) {
        public Analysis { rules = List.copyOf(rules); diagnostics = List.copyOf(diagnostics); }
    }

    private record InstanceField(String owner, String name) { }
    private record MethodContext(ClassNode owner, MethodNode method, Frame<SourceValue>[] frames,
                                 Map<AbstractInsnNode,Integer> indices) { }

    public Analysis analyze(Path jarPath) throws IOException {
        Map<String, ClassNode> classes = loadClasses(jarPath);
        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(jarPath);
        List<Rule> rules = new ArrayList<>();
        LinkedHashSet<String> diagnostics = new LinkedHashSet<>();

        for (LegacyRegistryAnalyzer.Registration registration : registry.blocks()) {
            String sourceClass = registration.implementationClass();
            if (sourceClass == null) continue;
            MethodNode method = effectiveSourceMethod(classes, sourceClass);
            RenderIdentity identity;
            if (method == null) {
                identity = inheritedPlatformRenderIdentity(classes, sourceClass);
                if (identity == null) continue;
            } else {
                identity = directRenderIdentity(method);
                if (identity == null) identity = constructorBoundRenderIdentity(classes, registration, method);
                if (identity == null) {
                    diagnostics.add("Registered block render type is not a proven direct or constructor-bound constant/static-field identity: "
                            + sourceClass + "." + method.name + method.desc);
                    continue;
                }
            }
            rules.add(new Rule(registration.registryName(), registration.legacyNamespace(), sourceClass, identity));
        }
        return new Analysis(rules, List.copyOf(diagnostics));
    }

    static RenderIdentity inheritedPlatformRenderIdentity(Map<String,ClassNode> classes, String sourceClass) {
        if (classes == null || sourceClass == null) return null;
        Set<String> visited = new LinkedHashSet<>();
        for (String current = sourceClass; current != null && visited.add(current); ) {
            ClassNode node = classes.get(current);
            if (node == null) {
                OptionalInt value = LegacyBlockRenderType1710.effectiveRenderType(current);
                return value.isPresent() ? RenderIdentity.constant(value.getAsInt()) : null;
            }
            for (MethodNode method : node.methods) {
                if ((method.access & Opcodes.ACC_STATIC) == 0 && RENDER_NAMES.contains(method.name) && RENDER_DESC.equals(method.desc))
                    return null;
            }
            current = node.superName;
        }
        return null;
    }

    static RenderIdentity directRenderIdentity(MethodNode method) {
        if (method == null || !RENDER_NAMES.contains(method.name) || !RENDER_DESC.equals(method.desc)) return null;
        RenderIdentity result = null;
        boolean sawReturn = false;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (instruction.getOpcode() != Opcodes.IRETURN) continue;
            sawReturn = true;
            RenderIdentity candidate = identity(previousReal(instruction));
            if (candidate == null) return null;
            if (result == null) result = candidate;
            else if (!result.equals(candidate)) return null;
        }
        return sawReturn ? result : null;
    }

    static RenderIdentity constructorBoundRenderIdentity(Map<String,ClassNode> classes,
                                                         LegacyRegistryAnalyzer.Registration registration,
                                                         MethodNode renderMethod) {
        if (classes == null || registration == null || renderMethod == null) return null;
        String sourceClass = registration.implementationClass();
        String descriptor = registration.constructorDescriptor();
        if (sourceClass == null || descriptor == null) return null;
        Type[] argumentTypes = Type.getArgumentTypes(descriptor);
        if (argumentTypes.length == 0 || argumentTypes.length != registration.constructorArguments().size()) return null;
        for (Type type : argumentTypes) if (type.getSort() != Type.INT) return null; // first bounded family: int-only constructors

        InstanceField field = directInstanceRenderField(renderMethod);
        if (field == null) return null;

        List<RenderIdentity> registrationArguments = new ArrayList<>(argumentTypes.length);
        boolean allKnown = true;
        for (var argument : registration.constructorArguments()) {
            Object proven = argument.value();
            Integer constant = exactInt(proven);
            if (constant != null) registrationArguments.add(RenderIdentity.constant(constant));
            else if (proven == null) { registrationArguments.add(null); allKnown = false; }
            else return null;
        }

        // First prefer the exact registry constructor arguments. This also follows bounded this(...)
        // constructor delegation, so wrappers such as (int,int)->(int,int,int) remain source-proven.
        if (allKnown) {
            RenderIdentity resolved = constructorFieldIdentity(
                    classes, sourceClass, descriptor, registrationArguments, field, 0, new HashSet<>());
            if (resolved != null) return resolved;
        }

        // A registry analyzer may leave one constructor argument symbolic. Recover it only from a
        // unique source allocation whose other arguments match the registry-proven constants, then
        // replay that exact allocation through the same constructor-delegation resolver.
        LinkedHashSet<RenderIdentity> candidates = new LinkedHashSet<>();
        for (ClassNode caller : classes.values()) for (MethodNode method : caller.methods) {
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
            MethodContext context;
            try { context = context(caller, method); }
            catch (AnalyzerException | RuntimeException ignored) { continue; }
            for (int i = 0; i < method.instructions.size(); i++) {
                AbstractInsnNode instruction = method.instructions.get(i);
                if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESPECIAL
                        || !"<init>".equals(call.name) || !sourceClass.equals(call.owner) || !descriptor.equals(call.desc)) continue;
                Frame<SourceValue> frame = context.frames()[i];
                if (frame == null || frame.getStackSize() < argumentTypes.length + 1) continue;
                int start = frame.getStackSize() - argumentTypes.length;
                SourceValue receiver = frame.getStack(start - 1);
                if (!newReceiver(context, receiver, sourceClass, 0, new HashSet<>())) continue;

                boolean matches = true;
                List<RenderIdentity> actualArguments = new ArrayList<>(argumentTypes.length);
                for (int arg = 0; arg < argumentTypes.length; arg++) {
                    RenderIdentity actual = intValue(context, frame.getStack(start + arg), 0, new HashSet<>());
                    if (actual == null) { matches = false; break; }
                    RenderIdentity expected = registrationArguments.get(arg);
                    if (expected != null && !expected.equals(actual)) { matches = false; break; }
                    actualArguments.add(actual);
                }
                if (!matches) continue;
                RenderIdentity target = constructorFieldIdentity(
                        classes, sourceClass, descriptor, actualArguments, field, 0, new HashSet<>());
                if (target != null) candidates.add(target);
            }
        }
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    private static InstanceField directInstanceRenderField(MethodNode method) {
        if (method == null || !RENDER_NAMES.contains(method.name) || !RENDER_DESC.equals(method.desc)) return null;
        List<AbstractInsnNode> code = real(method);
        if (code.size() != 3 || !(code.get(0) instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD || load.var != 0
                || !(code.get(1) instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETFIELD || !"I".equals(field.desc)
                || code.get(2).getOpcode() != Opcodes.IRETURN) return null;
        return new InstanceField(field.owner, field.name);
    }

    /**
     * Resolves one instance int field through a bounded chain of source-owned this(...) constructors.
     * Only direct int locals/constants/static-int identities are admitted; arithmetic and branches
     * fail closed rather than guessing constructor semantics.
     */
    private static RenderIdentity constructorFieldIdentity(Map<String,ClassNode> classes,String owner,String descriptor,
                                                           List<RenderIdentity> arguments,InstanceField target,
                                                           int depth,Set<String> guard) {
        if(owner==null||descriptor==null||depth>16||!guard.add(owner+descriptor))return null;
        ClassNode node=classes.get(owner);MethodNode ctor=findMethod(node,"<init>",descriptor);
        if(ctor==null){guard.remove(owner+descriptor);return null;}
        Type[] types=Type.getArgumentTypes(descriptor);
        if(types.length!=arguments.size()){guard.remove(owner+descriptor);return null;}
        Map<Integer,RenderIdentity> locals=new HashMap<>();int local=1;
        for(int i=0;i<types.length;i++){
            if(types[i].getSort()!=Type.INT){guard.remove(owner+descriptor);return null;}
            RenderIdentity value=arguments.get(i);if(value!=null)locals.put(local,value);local+=types[i].getSize();
        }

        LinkedHashSet<RenderIdentity> writes=new LinkedHashSet<>();
        List<AbstractInsnNode> code=real(ctor);
        for(int i=0;i<code.size();i++){
            AbstractInsnNode insn=code.get(i);
            if(insn instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD
                    &&target.owner().equals(put.owner)&&target.name().equals(put.name)&&"I".equals(put.desc)){
                if(i<2||!(code.get(i-2) instanceof VarInsnNode receiver)||receiver.getOpcode()!=Opcodes.ALOAD||receiver.var!=0){
                    guard.remove(owner+descriptor);return null;
                }
                RenderIdentity value=constructorInt(code.get(i-1),locals);
                if(value==null){guard.remove(owner+descriptor);return null;}
                writes.add(value);
            }
        }
        if(writes.size()>1){guard.remove(owner+descriptor);return null;}
        if(writes.size()==1){RenderIdentity result=writes.getFirst();guard.remove(owner+descriptor);return result;}

        LinkedHashSet<RenderIdentity> delegated=new LinkedHashSet<>();
        for(int i=0;i<code.size();i++){
            AbstractInsnNode insn=code.get(i);
            if(!(insn instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESPECIAL
                    ||!"<init>".equals(call.name)||!owner.equals(call.owner))continue;
            Type[] nestedTypes=Type.getArgumentTypes(call.desc);
            int first=i-nestedTypes.length;
            if(first<1||!(code.get(first-1) instanceof VarInsnNode receiver)
                    ||receiver.getOpcode()!=Opcodes.ALOAD||receiver.var!=0)continue;
            List<RenderIdentity> nested=new ArrayList<>(nestedTypes.length);boolean supported=true;
            for(int arg=0;arg<nestedTypes.length;arg++){
                if(nestedTypes[arg].getSort()!=Type.INT){supported=false;break;}
                RenderIdentity value=constructorInt(code.get(first+arg),locals);
                if(value==null){supported=false;break;}
                nested.add(value);
            }
            if(!supported)continue;
            RenderIdentity value=constructorFieldIdentity(classes,owner,call.desc,nested,target,depth+1,guard);
            if(value!=null)delegated.add(value);
        }
        guard.remove(owner+descriptor);
        return delegated.size()==1?delegated.getFirst():null;
    }

    private static RenderIdentity constructorInt(AbstractInsnNode insn,Map<Integer,RenderIdentity> locals){
        Integer constant=intConstant(insn);if(constant!=null)return RenderIdentity.constant(constant);
        if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC&&"I".equals(field.desc))
            return RenderIdentity.field(field.owner,field.name);
        if(insn instanceof VarInsnNode load&&load.getOpcode()==Opcodes.ILOAD)return locals.get(load.var);
        return null;
    }

    private static Integer constructorParameterAssignedToField(MethodNode constructor, InstanceField field, Type[] arguments) {
        List<AbstractInsnNode> code = real(constructor);
        Integer assignedLocal = null;
        int targetWrites = 0;
        for (int i = 0; i < code.size(); i++) {
            AbstractInsnNode instruction = code.get(i);
            if (!(instruction instanceof FieldInsnNode put) || put.getOpcode() != Opcodes.PUTFIELD
                    || !field.owner().equals(put.owner) || !field.name().equals(put.name) || !"I".equals(put.desc)) continue;
            targetWrites++;
            if (i < 2 || !(code.get(i - 2) instanceof VarInsnNode receiver) || receiver.getOpcode() != Opcodes.ALOAD || receiver.var != 0
                    || !(code.get(i - 1) instanceof VarInsnNode value) || value.getOpcode() != Opcodes.ILOAD) return null;
            assignedLocal = value.var;
        }
        if (targetWrites != 1 || assignedLocal == null) return null;
        int local = 1;
        for (int arg = 0; arg < arguments.length; arg++) {
            if (local == assignedLocal) return arg;
            local += arguments[arg].getSize();
        }
        return null;
    }

    private static MethodContext context(ClassNode owner, MethodNode method) throws AnalyzerException {
        Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
        Frame<SourceValue>[] frames = analyzer.analyze(owner.name, method);
        Map<AbstractInsnNode,Integer> indices = new IdentityHashMap<>();
        for (int i = 0; i < method.instructions.size(); i++) indices.put(method.instructions.get(i), i);
        return new MethodContext(owner, method, frames, indices);
    }

    private static boolean newReceiver(MethodContext context, SourceValue value, String type, int depth, Set<AbstractInsnNode> guard) {
        if (value == null || depth > 24 || value.insns == null || value.insns.isEmpty()) return false;
        for (AbstractInsnNode producer : value.insns) {
            if (!guard.add(producer)) return false;
            boolean proven;
            if (producer instanceof TypeInsnNode allocation && allocation.getOpcode() == Opcodes.NEW) proven = type.equals(allocation.desc);
            else if (producer instanceof InsnNode copy && copy.getOpcode() == Opcodes.DUP) {
                Integer index = context.indices().get(producer); Frame<SourceValue> frame = index == null ? null : context.frames()[index];
                proven = frame != null && frame.getStackSize() > 0
                        && newReceiver(context, frame.getStack(frame.getStackSize() - 1), type, depth + 1, guard);
            } else proven = false;
            guard.remove(producer);
            if (!proven) return false;
        }
        return true;
    }

    private static RenderIdentity intValue(MethodContext context, SourceValue value, int depth, Set<AbstractInsnNode> guard) {
        if (value == null || depth > 24 || value.insns == null || value.insns.isEmpty()) return null;
        RenderIdentity result = null;
        for (AbstractInsnNode producer : value.insns) {
            if (!guard.add(producer)) return null;
            RenderIdentity candidate;
            Integer constant = intConstant(producer);
            if (constant != null) candidate = RenderIdentity.constant(constant);
            else if (producer instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC && "I".equals(field.desc))
                candidate = RenderIdentity.field(field.owner, field.name);
            else if (producer instanceof VarInsnNode load && load.getOpcode() == Opcodes.ILOAD) {
                Integer index = context.indices().get(producer); Frame<SourceValue> frame = index == null ? null : context.frames()[index];
                candidate = frame != null && load.var < frame.getLocals()
                        ? intValue(context, frame.getLocal(load.var), depth + 1, guard) : null;
            } else candidate = null;
            guard.remove(producer);
            if (candidate == null) return null;
            if (result == null) result = candidate;
            else if (!result.equals(candidate)) return null;
        }
        return result;
    }

    private static RenderIdentity identity(AbstractInsnNode instruction) {
        Integer constant = intConstant(instruction);
        if (constant != null) return RenderIdentity.constant(constant);
        if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC && "I".equals(field.desc))
            return RenderIdentity.field(field.owner, field.name);
        return null;
    }

    private static Integer exactInt(Object value) {
        if (!(value instanceof Number number)) return null;
        double raw = number.doubleValue();
        return Double.isFinite(raw) && raw == Math.rint(raw) && raw >= Integer.MIN_VALUE && raw <= Integer.MAX_VALUE ? (int) raw : null;
    }

    private static Integer intConstant(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        return switch (instruction.getOpcode()) {
            case Opcodes.ICONST_M1 -> -1; case Opcodes.ICONST_0 -> 0; case Opcodes.ICONST_1 -> 1;
            case Opcodes.ICONST_2 -> 2; case Opcodes.ICONST_3 -> 3; case Opcodes.ICONST_4 -> 4; case Opcodes.ICONST_5 -> 5;
            case Opcodes.BIPUSH, Opcodes.SIPUSH -> ((IntInsnNode) instruction).operand;
            case Opcodes.LDC -> instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Integer value ? value : null;
            default -> null;
        };
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode instruction) {
        for (AbstractInsnNode current = instruction == null ? null : instruction.getPrevious(); current != null; current = current.getPrevious())
            if (current.getOpcode() >= 0) return current;
        return null;
    }
    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> output = new ArrayList<>();
        if (method != null) for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) output.add(instruction);
        return output;
    }
    private static MethodNode findMethod(ClassNode owner, String name, String descriptor) {
        if (owner == null) return null;
        for (MethodNode method : owner.methods) if (name.equals(method.name) && descriptor.equals(method.desc)) return method;
        return null;
    }
    private static MethodNode effectiveSourceMethod(Map<String, ClassNode> classes, String sourceClass) {
        Set<String> visited = new LinkedHashSet<>();
        for (String current = sourceClass; current != null && visited.add(current); ) {
            ClassNode node = classes.get(current); if (node == null) return null;
            for (MethodNode method : node.methods)
                if ((method.access & Opcodes.ACC_STATIC) == 0 && RENDER_NAMES.contains(method.name) && RENDER_DESC.equals(method.desc)) return method;
            current = node.superName;
        }
        return null;
    }
    private static Map<String, ClassNode> loadClasses(Path jarPath) throws IOException {
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile(), false)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException ignored) { }
            }
        }
        return classes;
    }
}
