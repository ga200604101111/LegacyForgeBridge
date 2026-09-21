package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Interprocedural, non-executing extractor for ordinary Forge 1.7.x GameRegistry registrations.
 *
 * <p>The analyzer uses ASM source frames to prove where registration arguments came from, then
 * propagates parameterized registration templates through source helper methods until an FML
 * lifecycle root supplies concrete names/types. It never defines source classes or invokes legacy
 * code. Ambiguous merged values are rejected rather than guessed.</p>
 */
public final class LegacyRegistryAnalyzer {
    private static final String GAME_REGISTRY = "cpw/mods/fml/common/registry/GameRegistry";
    private static final int MAX_PROPAGATION_ROUNDS = 64;
    private static final int MAX_TEMPLATE_COUNT = 16_384;

    public enum Kind { ITEM, BLOCK }

    public record Registration(
            Kind kind,
            String registryName,
            String legacyNamespace,
            String implementationClass,
            String itemBlockClass,
            String constructorDescriptor,
            List<ConstructorArgument> constructorArguments,
            String sourceOwner,
            String sourceMethod,
            String sourceDescriptor
    ) {
        public Registration { constructorArguments = List.copyOf(constructorArguments); }
    }

    public record ConstructorArgument(String descriptor, Object value) { }

    public record FieldBinding(String owner, String name, String descriptor, Kind kind, String registryName,
                               String legacyNamespace, String implementationClass) { }

    public record Analysis(List<Registration> registrations, List<FieldBinding> fieldBindings, List<String> diagnostics) {
        public Analysis {
            registrations = List.copyOf(registrations);
            fieldBindings = List.copyOf(fieldBindings);
            diagnostics = List.copyOf(diagnostics);
        }

        public List<Registration> items() {
            return registrations.stream().filter(value -> value.kind() == Kind.ITEM).toList();
        }

        public List<Registration> blocks() {
            return registrations.stream().filter(value -> value.kind() == Kind.BLOCK).toList();
        }
    }

    private sealed interface Symbol permits TextSymbol, NumberSymbol, TypeSymbol, ObjectSymbol, ParamSymbol, NullSymbol, UnknownSymbol { }
    private record TextSymbol(String value) implements Symbol { }
    private record NumberSymbol(Number value) implements Symbol { }
    private record TypeSymbol(String internalName) implements Symbol { }
    private record ObjectSymbol(String internalName, String constructorDescriptor, List<Symbol> constructorArgs) implements Symbol {
        ObjectSymbol(String internalName) { this(internalName, null, List.of()); }
        ObjectSymbol { constructorArgs = List.copyOf(constructorArgs); }
    }
    private record ParamSymbol(int local) implements Symbol { }
    private enum NullSymbol implements Symbol { INSTANCE }
    private enum UnknownSymbol implements Symbol { INSTANCE }

    private record MethodKey(String owner, String name, String descriptor) { }
    private record Template(Kind kind, Symbol object, Symbol name, Symbol namespace, Symbol itemBlock,
                            MethodKey directSource) { }
    private record MethodContext(ClassNode owner, MethodNode method, Frame<SourceValue>[] frames,
                                 Map<AbstractInsnNode, Integer> indices, Set<Integer> parameterLocals) { }

    private final Map<String, ClassNode> classes = new LinkedHashMap<>();
    private final Map<MethodKey, MethodContext> methods = new LinkedHashMap<>();
    private final Map<MethodKey, LinkedHashSet<Template>> templates = new LinkedHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear(); methods.clear(); templates.clear(); diagnostics.clear();
        load(jarPath);
        analyzeFrames();
        collectDirectTemplates();
        propagateTemplates();

        LinkedHashSet<Registration> output = new LinkedHashSet<>();
        for (Map.Entry<MethodKey, MethodContext> entry : methods.entrySet()) {
            if (!isRoot(entry.getValue())) continue;
            for (Template template : templates.getOrDefault(entry.getKey(), new LinkedHashSet<>())) {
                Registration registration = materialize(template);
                if (registration != null) output.add(registration);
            }
        }

        // Static initializers are implicit class-load roots. Include only fully concrete
        // registrations; this preserves the common 1.7.x static-content-holder pattern without
        // treating arbitrary unused helper methods as registrations.
        for (Map.Entry<MethodKey, MethodContext> entry : methods.entrySet()) {
            if (!entry.getKey().name().equals("<clinit>")) continue;
            for (Template template : templates.getOrDefault(entry.getKey(), new LinkedHashSet<>())) {
                Registration registration = materialize(template);
                if (registration != null) output.add(registration);
            }
        }

        LinkedHashSet<FieldBinding> fieldBindings = recoverFieldBindings();
        if (output.isEmpty()) diagnostics.add("No concrete GameRegistry item/block registrations were proven from reachable lifecycle roots.");
        return new Analysis(List.copyOf(output), List.copyOf(fieldBindings), List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private LinkedHashSet<FieldBinding> recoverFieldBindings() {
        LinkedHashSet<FieldBinding> output = new LinkedHashSet<>();
        for (var entry : methods.entrySet()) {
            MethodContext context = entry.getValue();
            for (int i = 0; i < context.method().instructions.size(); i++) {
                AbstractInsnNode instruction = context.method().instructions.get(i);
                if (!(instruction instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.PUTSTATIC) continue;
                Frame<SourceValue> frame = context.frames()[i];
                if (frame == null || frame.getStackSize() < 1) continue;
                SourceValue source = frame.getStack(frame.getStackSize() - 1);
                if (source == null || source.insns == null || source.insns.size() != 1) continue;
                AbstractInsnNode producer = source.insns.iterator().next();
                if (!(producer instanceof MethodInsnNode call)) continue;
                MethodKey callee = new MethodKey(call.owner, call.name, call.desc);
                LinkedHashSet<Template> possible = templates.get(callee);
                MethodContext calleeContext = methods.get(callee);
                Integer callIndex = context.indices().get(call);
                if (possible == null || possible.isEmpty() || calleeContext == null || callIndex == null) continue;
                Frame<SourceValue> callFrame = context.frames()[callIndex];
                if (callFrame == null) continue;
                List<Symbol> actual = invocationValuesIncludingReceiver(context, callIndex, call, callFrame);
                if (actual == null) continue;
                Map<Integer, Symbol> substitution = parameterSubstitution(calleeContext.method(), actual);
                LinkedHashSet<Registration> resolved = new LinkedHashSet<>();
                for (Template template : possible) {
                    Template instantiated = new Template(template.kind(), substitute(template.object(), substitution),
                            substitute(template.name(), substitution), substitute(template.namespace(), substitution),
                            substitute(template.itemBlock(), substitution), template.directSource());
                    Registration registration = materialize(instantiated);
                    if (registration != null) resolved.add(registration);
                }
                if (resolved.size() != 1) continue;
                Registration registration = resolved.getFirst();
                output.add(new FieldBinding(field.owner, field.name, field.desc, registration.kind(),
                        registration.registryName(), registration.legacyNamespace(), registration.implementationClass()));
            }
        }
        return output;
    }

    public String classifyItem(String implementationClass) {
        if (implementationClass == null) return "item";
        if (isSubclass(implementationClass, "net/minecraft/item/ItemSword")) return "sword";
        if (isSubclass(implementationClass, "net/minecraft/item/ItemArmor")) return "armor";
        if (isSubclass(implementationClass, "net/minecraft/item/ItemBow")) return "bow";
        if (isSubclass(implementationClass, "net/minecraft/item/ItemHoe")) return "hoe";
        if (isSubclass(implementationClass, "net/minecraft/item/ItemPickaxe")) return "pickaxe";
        if (isSubclass(implementationClass, "net/minecraft/item/ItemAxe")) return "axe";
        if (isSubclass(implementationClass, "net/minecraft/item/ItemSpade")) return "shovel";
        if (isSubclass(implementationClass, "net/minecraft/item/ItemTool")) return "tool";
        return "item";
    }

    private void load(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException malformed) {
                    diagnostics.add("Unreadable registry-analysis class " + entry.getName() + ": " + malformed.getClass().getSimpleName());
                }
            }
        }
    }

    private void analyzeFrames() {
        for (ClassNode owner : classes.values()) {
            for (MethodNode method : owner.methods) {
                MethodKey key = new MethodKey(owner.name, method.name, method.desc);
                try {
                    Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
                    Frame<SourceValue>[] frames = analyzer.analyze(owner.name, method);
                    Map<AbstractInsnNode, Integer> indices = new HashMap<>();
                    for (int i = 0; i < method.instructions.size(); i++) indices.put(method.instructions.get(i), i);
                    methods.put(key, new MethodContext(owner, method, frames, indices, parameterLocals(method)));
                    templates.put(key, new LinkedHashSet<>());
                } catch (AnalyzerException | RuntimeException unsupported) {
                    diagnostics.add("Registry dataflow unavailable for " + owner.name + "." + method.name + method.desc + ": " + unsupported.getMessage());
                }
            }
        }
    }

    private static Set<Integer> parameterLocals(MethodNode method) {
        LinkedHashSet<Integer> slots = new LinkedHashSet<>();
        int local = 0;
        if ((method.access & Opcodes.ACC_STATIC) == 0) slots.add(local++);
        for (Type type : Type.getArgumentTypes(method.desc)) {
            slots.add(local);
            local += type.getSize();
        }
        return Collections.unmodifiableSet(slots);
    }

    private void collectDirectTemplates() {
        for (Map.Entry<MethodKey, MethodContext> entry : methods.entrySet()) {
            MethodKey key = entry.getKey(); MethodContext context = entry.getValue();
            for (int i = 0; i < context.method().instructions.size(); i++) {
                AbstractInsnNode instruction = context.method().instructions.get(i);
                if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals(GAME_REGISTRY)) continue;
                Kind kind = switch (call.name) {
                    case "registerItem" -> Kind.ITEM;
                    case "registerBlock" -> Kind.BLOCK;
                    default -> null;
                };
                if (kind == null) continue;
                Frame<SourceValue> frame = context.frames()[i];
                if (frame == null) continue;
                List<Symbol> args = invocationArgs(context, i, call, frame);
                if (args == null || args.isEmpty()) {
                    diagnostics.add("Unable to recover GameRegistry arguments at " + key);
                    continue;
                }
                Template template = directTemplate(kind, call, args, key);
                if (template != null) templates.get(key).add(template);
            }
        }
    }

    private Template directTemplate(Kind kind, MethodInsnNode call, List<Symbol> args, MethodKey source) {
        Type[] types = Type.getArgumentTypes(call.desc);
        if (types.length != args.size() || args.isEmpty()) return null;
        Symbol object = args.getFirst();
        Symbol name = UnknownSymbol.INSTANCE;
        Symbol namespace = NullSymbol.INSTANCE;
        Symbol itemBlock = NullSymbol.INSTANCE;

        if (kind == Kind.ITEM) {
            for (int i = 1; i < types.length; i++) {
                if (types[i].getSort() == Type.OBJECT && types[i].getInternalName().equals("java/lang/String")) {
                    if (name == UnknownSymbol.INSTANCE) name = args.get(i); else namespace = args.get(i);
                }
            }
        } else {
            for (int i = 1; i < types.length; i++) {
                if (types[i].getSort() == Type.OBJECT && types[i].getInternalName().equals("java/lang/Class")) itemBlock = args.get(i);
                if (types[i].getSort() == Type.OBJECT && types[i].getInternalName().equals("java/lang/String")) name = args.get(i);
            }
        }
        if (name == UnknownSymbol.INSTANCE) {
            diagnostics.add("Registration name is not represented by a String argument at " + source);
            return null;
        }
        return new Template(kind, object, name, namespace, itemBlock, source);
    }

    private void propagateTemplates() {
        for (int round = 0; round < MAX_PROPAGATION_ROUNDS; round++) {
            boolean changed = false;
            for (Map.Entry<MethodKey, MethodContext> entry : methods.entrySet()) {
                MethodKey caller = entry.getKey(); MethodContext context = entry.getValue();
                LinkedHashSet<Template> callerTemplates = templates.get(caller);
                for (int i = 0; i < context.method().instructions.size(); i++) {
                    AbstractInsnNode instruction = context.method().instructions.get(i);
                    if (!(instruction instanceof MethodInsnNode call)) continue;
                    MethodKey callee = new MethodKey(call.owner, call.name, call.desc);
                    LinkedHashSet<Template> calleeTemplates = templates.get(callee);
                    MethodContext calleeContext = methods.get(callee);
                    if (calleeTemplates == null || calleeTemplates.isEmpty() || calleeContext == null) continue;
                    Frame<SourceValue> frame = context.frames()[i];
                    if (frame == null) continue;
                    List<Symbol> actual = invocationValuesIncludingReceiver(context, i, call, frame);
                    if (actual == null) continue;
                    Map<Integer, Symbol> substitution = parameterSubstitution(calleeContext.method(), actual);
                    for (Template template : calleeTemplates) {
                        Template instantiated = new Template(
                                template.kind(),
                                substitute(template.object(), substitution),
                                substitute(template.name(), substitution),
                                substitute(template.namespace(), substitution),
                                substitute(template.itemBlock(), substitution),
                                template.directSource()
                        );
                        if (callerTemplates.add(instantiated)) {
                            changed = true;
                            if (totalTemplateCount() > MAX_TEMPLATE_COUNT) throw new IllegalArgumentException("Registry template propagation budget exceeded");
                        }
                    }
                }
            }
            if (!changed) return;
        }
        diagnostics.add("Registry helper propagation reached its round budget; deeply recursive registration helpers were not trusted.");
    }

    private int totalTemplateCount() {
        return templates.values().stream().mapToInt(Set::size).sum();
    }

    private static Map<Integer, Symbol> parameterSubstitution(MethodNode method, List<Symbol> actual) {
        LinkedHashMap<Integer, Symbol> result = new LinkedHashMap<>();
        int actualIndex = 0, local = 0;
        if ((method.access & Opcodes.ACC_STATIC) == 0) {
            if (actualIndex >= actual.size()) return Map.of();
            result.put(local++, actual.get(actualIndex++));
        }
        for (Type type : Type.getArgumentTypes(method.desc)) {
            if (actualIndex >= actual.size()) return Map.of();
            result.put(local, actual.get(actualIndex++));
            local += type.getSize();
        }
        return result;
    }

    private static Symbol substitute(Symbol symbol, Map<Integer, Symbol> substitution) {
        if (symbol instanceof ParamSymbol param) return substitution.getOrDefault(param.local(), UnknownSymbol.INSTANCE);
        if (symbol instanceof ObjectSymbol object && !object.constructorArgs().isEmpty()) {
            List<Symbol> args = object.constructorArgs().stream().map(value -> substitute(value, substitution)).toList();
            return new ObjectSymbol(object.internalName(), object.constructorDescriptor(), args);
        }
        return symbol;
    }

    private Registration materialize(Template template) {
        if (!(template.name() instanceof TextSymbol name) || name.value().isBlank()) return null;
        ObjectSymbol concreteObject = template.object() instanceof ObjectSymbol object ? object : null;
        String implementation = concreteObject != null ? concreteObject.internalName()
                : template.object() instanceof TypeSymbol type ? type.internalName() : null;
        // The registry call itself proves Item/Block kind even when a value travelled through a
        // broad-typed static field, so an unknown implementation class does not invalidate ID proof.
        String namespace = template.namespace() instanceof TextSymbol text && !text.value().isBlank() ? text.value() : null;
        String itemBlock = template.itemBlock() instanceof TypeSymbol type ? type.internalName()
                : template.itemBlock() instanceof ObjectSymbol object ? object.internalName() : null;
        List<ConstructorArgument> constructor = new ArrayList<>();
        String constructorDescriptor = concreteObject == null ? null : concreteObject.constructorDescriptor();
        if (concreteObject != null && constructorDescriptor != null) {
            Type[] argumentTypes = Type.getArgumentTypes(constructorDescriptor);
            if (argumentTypes.length == concreteObject.constructorArgs().size()) {
                for (int i = 0; i < argumentTypes.length; i++) {
                    Symbol value = concreteObject.constructorArgs().get(i);
                    Object raw = value instanceof TextSymbol text ? text.value()
                            : value instanceof NumberSymbol number ? number.value()
                            : value == NullSymbol.INSTANCE ? null : null;
                    constructor.add(new ConstructorArgument(argumentTypes[i].getDescriptor(), raw));
                }
            }
        }
        MethodKey source = template.directSource();
        return new Registration(template.kind(), name.value(), namespace, implementation, itemBlock,
                constructorDescriptor, constructor, source.owner(), source.name(), source.descriptor());
    }

    private boolean isRoot(MethodContext context) {
        MethodNode method = context.method();
        if (hasAnnotation(method.visibleAnnotations, "Lcpw/mods/fml/common/Mod$EventHandler;")
                || hasAnnotation(method.invisibleAnnotations, "Lcpw/mods/fml/common/Mod$EventHandler;")) return true;
        // Some 1.7.x compilers/mods omit the annotation in transformed fixtures. Event parameter
        // types are still a bounded, source-derived lifecycle root signal.
        return method.desc.contains("Lcpw/mods/fml/common/event/FML") && method.desc.endsWith(")V");
    }

    private static boolean hasAnnotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations == null) return false;
        return annotations.stream().anyMatch(annotation -> descriptor.equals(annotation.desc));
    }

    private List<Symbol> invocationArgs(MethodContext context, int index, MethodInsnNode call, Frame<SourceValue> frame) {
        Type[] args = Type.getArgumentTypes(call.desc);
        int count = args.length;
        if (frame.getStackSize() < count) return null;
        List<Symbol> result = new ArrayList<>(count);
        int start = frame.getStackSize() - count;
        for (int i = 0; i < count; i++) result.add(resolve(context, frame.getStack(start + i), index, 0, new LinkedHashSet<>()));
        return result;
    }

    private List<Symbol> invocationValuesIncludingReceiver(MethodContext context, int index, MethodInsnNode call, Frame<SourceValue> frame) {
        int argCount = Type.getArgumentTypes(call.desc).length;
        boolean isStatic = call.getOpcode() == Opcodes.INVOKESTATIC;
        int count = argCount + (isStatic ? 0 : 1);
        if (frame.getStackSize() < count) return null;
        List<Symbol> result = new ArrayList<>(count);
        int start = frame.getStackSize() - count;
        for (int i = 0; i < count; i++) result.add(resolve(context, frame.getStack(start + i), index, 0, new LinkedHashSet<>()));
        return result;
    }

    private Symbol resolve(MethodContext context, SourceValue value, int currentIndex, int depth, Set<String> guard) {
        if (value == null || depth > 32) return UnknownSymbol.INSTANCE;
        if (value.insns == null || value.insns.isEmpty()) return UnknownSymbol.INSTANCE;
        LinkedHashSet<Symbol> possibilities = new LinkedHashSet<>();
        for (AbstractInsnNode producer : value.insns) {
            Integer producerIndex = context.indices().get(producer);
            if (producerIndex == null) continue;
            String guardKey = context.owner().name + ":" + context.method().name + context.method().desc + ":" + producerIndex;
            if (!guard.add(guardKey)) continue;
            possibilities.add(resolveProducer(context, producer, producerIndex, depth + 1, guard));
            guard.remove(guardKey);
        }
        possibilities.remove(UnknownSymbol.INSTANCE);
        if (possibilities.size() == 1) return possibilities.getFirst();
        return UnknownSymbol.INSTANCE;
    }

    private Symbol resolveProducer(MethodContext context, AbstractInsnNode producer, int producerIndex, int depth, Set<String> guard) {
        if (producer instanceof LdcInsnNode ldc) {
            if (ldc.cst instanceof String text) return new TextSymbol(text);
            if (ldc.cst instanceof Number number) return new NumberSymbol(number);
            if (ldc.cst instanceof Type type && type.getSort() == Type.OBJECT) return new TypeSymbol(type.getInternalName());
            return UnknownSymbol.INSTANCE;
        }
        if (producer instanceof TypeInsnNode typeInsn) {
            if (typeInsn.getOpcode() == Opcodes.NEW) return constructorObject(context, typeInsn.desc, producerIndex, depth, guard);
            if (typeInsn.getOpcode() == Opcodes.CHECKCAST) {
                Frame<SourceValue> frame = context.frames()[producerIndex];
                if (frame != null && frame.getStackSize() > 0)
                    return resolve(context, frame.getStack(frame.getStackSize() - 1), producerIndex, depth + 1, guard);
            }
        }
        if (producer instanceof InsnNode insn) {
            if (insn.getOpcode() == Opcodes.ACONST_NULL) return NullSymbol.INSTANCE;
            if (insn.getOpcode() >= Opcodes.ICONST_M1 && insn.getOpcode() <= Opcodes.ICONST_5)
                return new NumberSymbol(insn.getOpcode() - Opcodes.ICONST_0);
            if (insn.getOpcode() == Opcodes.FCONST_0) return new NumberSymbol(0.0F);
            if (insn.getOpcode() == Opcodes.FCONST_1) return new NumberSymbol(1.0F);
            if (insn.getOpcode() == Opcodes.FCONST_2) return new NumberSymbol(2.0F);
            if (insn.getOpcode() == Opcodes.DCONST_0) return new NumberSymbol(0.0D);
            if (insn.getOpcode() == Opcodes.DCONST_1) return new NumberSymbol(1.0D);
            if (insn.getOpcode() == Opcodes.LCONST_0) return new NumberSymbol(0L);
            if (insn.getOpcode() == Opcodes.LCONST_1) return new NumberSymbol(1L);
            if (insn.getOpcode() == Opcodes.DUP) {
                Frame<SourceValue> frame = context.frames()[producerIndex];
                if (frame != null && frame.getStackSize() > 0)
                    return resolve(context, frame.getStack(frame.getStackSize() - 1), producerIndex, depth + 1, guard);
            }
        }
        if (producer instanceof VarInsnNode variable && isLoad(variable.getOpcode())) {
            if (context.parameterLocals().contains(variable.var)) return new ParamSymbol(variable.var);
            Frame<SourceValue> frame = context.frames()[producerIndex];
            if (frame != null && variable.var < frame.getLocals()) return resolve(context, frame.getLocal(variable.var), producerIndex, depth + 1, guard);
            return UnknownSymbol.INSTANCE;
        }
        if (producer instanceof VarInsnNode variable && isStore(variable.getOpcode())) {
            Frame<SourceValue> frame=context.frames()[producerIndex];
            if(frame!=null&&frame.getStackSize()>0)
                return resolve(context,frame.getStack(frame.getStackSize()-1),producerIndex,depth+1,guard);
            return UnknownSymbol.INSTANCE;
        }
        if (isNumericBinary(producer.getOpcode())) {
            Frame<SourceValue> frame=context.frames()[producerIndex];
            if(frame==null||frame.getStackSize()<2)return UnknownSymbol.INSTANCE;
            Symbol left=resolve(context,frame.getStack(frame.getStackSize()-2),producerIndex,depth+1,guard);
            Symbol right=resolve(context,frame.getStack(frame.getStackSize()-1),producerIndex,depth+1,guard);
            return numericBinary(producer.getOpcode(),left,right);
        }
        if (isNumericUnary(producer.getOpcode())) {
            Frame<SourceValue> frame=context.frames()[producerIndex];
            if(frame==null||frame.getStackSize()<1)return UnknownSymbol.INSTANCE;
            return numericUnary(producer.getOpcode(),resolve(context,frame.getStack(frame.getStackSize()-1),producerIndex,depth+1,guard));
        }
        if (producer instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC) {
            ClassNode owner = classes.get(field.owner);
            if (owner != null) {
                for (FieldNode candidate : owner.fields) if (candidate.name.equals(field.name) && candidate.desc.equals(field.desc)) {
                    if (candidate.value instanceof String text) return new TextSymbol(text);
                    if (candidate.value instanceof Type type && type.getSort() == Type.OBJECT) return new TypeSymbol(type.getInternalName());
                }
            }
            Type fieldType = Type.getType(field.desc);
            if (fieldType.getSort() == Type.OBJECT) return new ObjectSymbol(fieldType.getInternalName());
            return UnknownSymbol.INSTANCE;
        }
        if (producer instanceof MethodInsnNode call) {
            Type returnType = Type.getReturnType(call.desc);
            if (returnType.getSort() == Type.VOID) return UnknownSymbol.INSTANCE;
            Frame<SourceValue> frame = context.frames()[producerIndex];
            if (frame == null) return UnknownSymbol.INSTANCE;
            int argCount = Type.getArgumentTypes(call.desc).length;
            boolean isStatic = call.getOpcode() == Opcodes.INVOKESTATIC;
            if (!isStatic && returnType.getSort() == Type.OBJECT && frame.getStackSize() >= argCount + 1) {
                // Fluent legacy setters and source helpers commonly return the receiver. Following
                // the receiver is conservative for implementation-type proof; the registration
                // name itself is proved independently by the String argument.
                SourceValue receiver = frame.getStack(frame.getStackSize() - argCount - 1);
                Symbol resolved = resolve(context, receiver, producerIndex, depth + 1, guard);
                if (resolved instanceof ObjectSymbol || resolved instanceof ParamSymbol) return resolved;
            }
            if (returnType.getSort() == Type.OBJECT && returnType.getInternalName().equals("java/lang/Class")) {
                return new TypeSymbol(call.owner);
            }
            return UnknownSymbol.INSTANCE;
        }
        if (producer instanceof IntInsnNode integer && (integer.getOpcode() == Opcodes.BIPUSH || integer.getOpcode() == Opcodes.SIPUSH))
            return new NumberSymbol(integer.operand);
        return UnknownSymbol.INSTANCE;
    }

    private Symbol constructorObject(MethodContext context, String type, int newIndex, int depth, Set<String> guard) {
        int limit = Math.min(context.method().instructions.size(), newIndex + 160);
        for (int i = newIndex + 1; i < limit; i++) {
            AbstractInsnNode next = context.method().instructions.get(i);
            if (!(next instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESPECIAL
                    || !call.name.equals("<init>") || !call.owner.equals(type)) continue;
            Frame<SourceValue> frame = context.frames()[i];
            if (frame == null) break;
            Type[] types = Type.getArgumentTypes(call.desc);
            int count = types.length;
            if (frame.getStackSize() < count + 1) break;
            List<Symbol> arguments = new ArrayList<>(count);
            int start = frame.getStackSize() - count;
            for (int arg = 0; arg < count; arg++)
                arguments.add(resolve(context, frame.getStack(start + arg), i, depth + 1, guard));
            return new ObjectSymbol(type, call.desc, arguments);
        }
        return new ObjectSymbol(type);
    }

    private static boolean isLoad(int opcode) {
        return opcode==Opcodes.ALOAD||opcode==Opcodes.ILOAD||opcode==Opcodes.LLOAD||opcode==Opcodes.FLOAD||opcode==Opcodes.DLOAD;
    }

    private static boolean isStore(int opcode) {
        return opcode==Opcodes.ASTORE||opcode==Opcodes.ISTORE||opcode==Opcodes.LSTORE||opcode==Opcodes.FSTORE||opcode==Opcodes.DSTORE;
    }

    private static boolean isNumericBinary(int opcode){
        return switch(opcode){
            case Opcodes.IADD,Opcodes.ISUB,Opcodes.IMUL,Opcodes.IDIV,Opcodes.IREM,
                    Opcodes.LADD,Opcodes.LSUB,Opcodes.LMUL,Opcodes.LDIV,Opcodes.LREM,
                    Opcodes.FADD,Opcodes.FSUB,Opcodes.FMUL,Opcodes.FDIV,Opcodes.FREM,
                    Opcodes.DADD,Opcodes.DSUB,Opcodes.DMUL,Opcodes.DDIV,Opcodes.DREM -> true;
            default -> false;
        };
    }

    private static boolean isNumericUnary(int opcode){
        return switch(opcode){
            case Opcodes.INEG,Opcodes.LNEG,Opcodes.FNEG,Opcodes.DNEG,
                    Opcodes.I2L,Opcodes.I2F,Opcodes.I2D,Opcodes.L2I,Opcodes.L2F,Opcodes.L2D,
                    Opcodes.F2I,Opcodes.F2L,Opcodes.F2D,Opcodes.D2I,Opcodes.D2L,Opcodes.D2F -> true;
            default -> false;
        };
    }

    private static Symbol numericBinary(int opcode,Symbol left,Symbol right){
        if(!(left instanceof NumberSymbol a)||!(right instanceof NumberSymbol b))return UnknownSymbol.INSTANCE;
        try{
            return switch(opcode){
                case Opcodes.IADD->new NumberSymbol(a.value().intValue()+b.value().intValue());
                case Opcodes.ISUB->new NumberSymbol(a.value().intValue()-b.value().intValue());
                case Opcodes.IMUL->new NumberSymbol(a.value().intValue()*b.value().intValue());
                case Opcodes.IDIV->b.value().intValue()==0?UnknownSymbol.INSTANCE:new NumberSymbol(a.value().intValue()/b.value().intValue());
                case Opcodes.IREM->b.value().intValue()==0?UnknownSymbol.INSTANCE:new NumberSymbol(a.value().intValue()%b.value().intValue());
                case Opcodes.LADD->new NumberSymbol(a.value().longValue()+b.value().longValue());
                case Opcodes.LSUB->new NumberSymbol(a.value().longValue()-b.value().longValue());
                case Opcodes.LMUL->new NumberSymbol(a.value().longValue()*b.value().longValue());
                case Opcodes.LDIV->b.value().longValue()==0L?UnknownSymbol.INSTANCE:new NumberSymbol(a.value().longValue()/b.value().longValue());
                case Opcodes.LREM->b.value().longValue()==0L?UnknownSymbol.INSTANCE:new NumberSymbol(a.value().longValue()%b.value().longValue());
                case Opcodes.FADD->new NumberSymbol(a.value().floatValue()+b.value().floatValue());
                case Opcodes.FSUB->new NumberSymbol(a.value().floatValue()-b.value().floatValue());
                case Opcodes.FMUL->new NumberSymbol(a.value().floatValue()*b.value().floatValue());
                case Opcodes.FDIV->new NumberSymbol(a.value().floatValue()/b.value().floatValue());
                case Opcodes.FREM->new NumberSymbol(a.value().floatValue()%b.value().floatValue());
                case Opcodes.DADD->new NumberSymbol(a.value().doubleValue()+b.value().doubleValue());
                case Opcodes.DSUB->new NumberSymbol(a.value().doubleValue()-b.value().doubleValue());
                case Opcodes.DMUL->new NumberSymbol(a.value().doubleValue()*b.value().doubleValue());
                case Opcodes.DDIV->new NumberSymbol(a.value().doubleValue()/b.value().doubleValue());
                case Opcodes.DREM->new NumberSymbol(a.value().doubleValue()%b.value().doubleValue());
                default->UnknownSymbol.INSTANCE;
            };
        }catch(ArithmeticException invalid){return UnknownSymbol.INSTANCE;}
    }

    private static Symbol numericUnary(int opcode,Symbol input){
        if(!(input instanceof NumberSymbol n))return UnknownSymbol.INSTANCE;
        return switch(opcode){
            case Opcodes.INEG->new NumberSymbol(-n.value().intValue());
            case Opcodes.LNEG->new NumberSymbol(-n.value().longValue());
            case Opcodes.FNEG->new NumberSymbol(-n.value().floatValue());
            case Opcodes.DNEG->new NumberSymbol(-n.value().doubleValue());
            case Opcodes.I2L,Opcodes.F2L,Opcodes.D2L->new NumberSymbol(n.value().longValue());
            case Opcodes.I2F,Opcodes.L2F,Opcodes.D2F->new NumberSymbol(n.value().floatValue());
            case Opcodes.I2D,Opcodes.L2D,Opcodes.F2D->new NumberSymbol(n.value().doubleValue());
            case Opcodes.L2I,Opcodes.F2I,Opcodes.D2I->new NumberSymbol(n.value().intValue());
            default->UnknownSymbol.INSTANCE;
        };
    }

    private boolean isSubclass(String type, String target) {
        for (int depth = 0; type != null && depth < 64; depth++) {
            if (type.equals(target)) return true;
            ClassNode node = classes.get(type);
            if (node == null) {
                if (target.equals("net/minecraft/item/Item")) return type.startsWith("net/minecraft/item/Item");
                if (target.equals("net/minecraft/block/Block")) return type.startsWith("net/minecraft/block/Block");
                return false;
            }
            type = node.superName;
        }
        return false;
    }
}
