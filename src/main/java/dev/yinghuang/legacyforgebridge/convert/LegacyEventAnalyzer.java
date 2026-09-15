package dev.longyu.legacyforgebridge.convert;

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
 * Non-executing extractor for legacy FML/Forge event subscriptions.
 *
 * <p>The analyzer proves a handler from the value actually passed to
 * {@code EventBus.register(Object)}. It therefore covers both the common
 * {@code bus.register(new Handler())} shape and self-registering constructors
 * such as {@code EVENT_BUS.register(this)} without matching mod-specific class
 * names. Annotated methods are emitted only for source classes with a proven
 * registration site. Unsupported or ambiguous source flow is diagnosed rather
 * than guessed.</p>
 */
public final class LegacyEventAnalyzer {
    private static final String EVENT_BUS = "cpw/mods/fml/common/eventhandler/EventBus";
    private static final String MINECRAFT_FORGE = "net/minecraftforge/common/MinecraftForge";
    private static final String FML_COMMON_HANDLER = "cpw/mods/fml/common/FMLCommonHandler";
    private static final String SUBSCRIBE_EVENT = "Lcpw/mods/fml/common/eventhandler/SubscribeEvent;";
    private static final String SIDE_ONLY = "Lcpw/mods/fml/relauncher/SideOnly;";
    private static final int MAX_CLASSES = 16_384;
    private static final int MAX_METHODS = 131_072;
    private static final int MAX_BINDINGS = 16_384;

    public enum Bus { FORGE, FML, UNKNOWN }
    public enum Side { COMMON, CLIENT, SERVER }

    public record Binding(
            String handlerClass,
            String method,
            String descriptor,
            String eventType,
            Bus bus,
            Side side,
            String priority,
            boolean receiveCanceled,
            String registrationOwner,
            String registrationMethod,
            String registrationDescriptor
    ) { }

    public record Analysis(List<Binding> bindings, List<String> diagnostics) {
        public Analysis {
            bindings = List.copyOf(bindings);
            diagnostics = List.copyOf(diagnostics);
        }

        public List<Binding> forEvent(String eventType) {
            return bindings.stream().filter(binding -> binding.eventType().equals(eventType)).toList();
        }
    }

    private record MethodKey(String owner, String name, String descriptor) { }
    private record Subscription(MethodNode method, String eventType, Side side, String priority, boolean receiveCanceled) { }
    private record Registration(String handlerClass, Bus bus, MethodKey site) { }

    private final Map<String, ClassNode> classes = new LinkedHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear();
        diagnostics.clear();
        load(jarPath);

        Map<String, List<Subscription>> subscriptions = subscriptions();
        List<Registration> registrations = registrations(subscriptions.keySet());
        LinkedHashMap<String, Binding> bindings = new LinkedHashMap<>();

        for (Registration registration : registrations) {
            for (Subscription subscription : subscriptions.getOrDefault(registration.handlerClass(), List.of())) {
                MethodNode method = subscription.method();
                Binding binding = new Binding(
                        registration.handlerClass(),
                        method.name,
                        method.desc,
                        subscription.eventType(),
                        registration.bus(),
                        subscription.side(),
                        subscription.priority(),
                        subscription.receiveCanceled(),
                        registration.site().owner(),
                        registration.site().name(),
                        registration.site().descriptor()
                );
                String key = binding.handlerClass() + "\u0000" + binding.method() + "\u0000" + binding.descriptor()
                        + "\u0000" + binding.bus();
                bindings.putIfAbsent(key, binding);
                if (bindings.size() > MAX_BINDINGS) {
                    throw new IOException("Legacy event binding budget exceeded");
                }
            }
        }

        List<Binding> ordered = new ArrayList<>(bindings.values());
        ordered.sort(Comparator.comparing(Binding::handlerClass)
                .thenComparing(Binding::method)
                .thenComparing(Binding::descriptor)
                .thenComparing(binding -> binding.bus().name()));
        return new Analysis(ordered, List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private void load(Path jarPath) throws IOException {
        int count = 0;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                if (++count > MAX_CLASSES) throw new IOException("Legacy event class budget exceeded");
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException malformed) {
                    diagnostics.add("Unreadable event-analysis class " + entry.getName() + ": "
                            + malformed.getClass().getSimpleName());
                }
            }
        }
    }

    private Map<String, List<Subscription>> subscriptions() throws IOException {
        LinkedHashMap<String, List<Subscription>> result = new LinkedHashMap<>();
        int methodCount = 0;
        for (ClassNode owner : classes.values()) {
            for (MethodNode method : owner.methods) {
                if (++methodCount > MAX_METHODS) throw new IOException("Legacy event method budget exceeded");
                AnnotationNode subscribe = annotation(method, SUBSCRIBE_EVENT);
                if (subscribe == null) continue;
                Type[] arguments = Type.getArgumentTypes(method.desc);
                if (arguments.length != 1 || arguments[0].getSort() != Type.OBJECT
                        || Type.getReturnType(method.desc).getSort() != Type.VOID) {
                    diagnostics.add("Unsupported @SubscribeEvent signature " + owner.name + "." + method.name + method.desc);
                    continue;
                }
                String priority = annotationEnum(subscribe, "priority", "NORMAL");
                boolean receiveCanceled = annotationBoolean(subscribe, "receiveCanceled", false);
                Side side = side(method);
                result.computeIfAbsent(owner.name, ignored -> new ArrayList<>())
                        .add(new Subscription(method, arguments[0].getInternalName(), side, priority, receiveCanceled));
            }
        }
        return result;
    }

    private List<Registration> registrations(Set<String> subscribedClasses) {
        LinkedHashMap<String, Registration> result = new LinkedHashMap<>();
        for (ClassNode owner : classes.values()) {
            for (MethodNode method : owner.methods) {
                boolean containsRegister = false;
                for (AbstractInsnNode instruction : method.instructions) {
                    if (isRegister(instruction)) { containsRegister = true; break; }
                }
                if (!containsRegister) continue;

                Frame<SourceValue>[] frames;
                try {
                    frames = new Analyzer<>(new SourceInterpreter()).analyze(owner.name, method);
                } catch (AnalyzerException | RuntimeException error) {
                    diagnostics.add("Event registration dataflow unavailable for " + owner.name + "." + method.name
                            + method.desc + ": " + String.valueOf(error.getMessage()));
                    continue;
                }

                for (int index = 0; index < method.instructions.size(); index++) {
                    AbstractInsnNode instruction = method.instructions.get(index);
                    if (!isRegister(instruction)) continue;
                    Frame<SourceValue> frame = frames[index];
                    if (frame == null || frame.getStackSize() < 2) {
                        diagnostics.add("Missing stack state for EventBus.register at " + owner.name + "."
                                + method.name + method.desc);
                        continue;
                    }
                    SourceValue receiver = frame.getStack(frame.getStackSize() - 2);
                    SourceValue argument = frame.getStack(frame.getStackSize() - 1);
                    Bus bus = bus(receiver);
                    Set<String> handlers = handlerTypes(argument, owner.name, method, instruction);
                    handlers.removeIf(handler -> !subscribedClasses.contains(handler));
                    if (handlers.isEmpty()) {
                        diagnostics.add("Unresolved subscribed handler passed to EventBus.register at " + owner.name + "."
                                + method.name + method.desc);
                        continue;
                    }
                    if (handlers.size() != 1) {
                        diagnostics.add("Ambiguous EventBus.register handler types " + handlers + " at " + owner.name
                                + "." + method.name + method.desc);
                        continue;
                    }
                    String handler = handlers.iterator().next();
                    MethodKey site = new MethodKey(owner.name, method.name, method.desc);
                    String key = handler + "\u0000" + bus + "\u0000" + site;
                    result.putIfAbsent(key, new Registration(handler, bus, site));
                }
            }
        }
        return List.copyOf(result.values());
    }

    private static boolean isRegister(AbstractInsnNode instruction) {
        return instruction instanceof MethodInsnNode call
                && call.owner.equals(EVENT_BUS)
                && call.name.equals("register")
                && call.desc.equals("(Ljava/lang/Object;)V");
    }

    private Set<String> handlerTypes(SourceValue value, String currentOwner, MethodNode method,
                                     AbstractInsnNode registerInstruction) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (AbstractInsnNode producer : value.insns) {
            if (producer instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) {
                result.add(type.desc);
            } else if (producer instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ALOAD
                    && variable.var == 0 && (method.access & Opcodes.ACC_STATIC) == 0) {
                result.add(currentOwner);
            } else if (producer instanceof FieldInsnNode field
                    && (field.getOpcode() == Opcodes.GETSTATIC || field.getOpcode() == Opcodes.GETFIELD)) {
                addObjectType(result, field.desc);
            } else if (producer instanceof MethodInsnNode call) {
                addObjectType(result, Type.getReturnType(call.desc).getDescriptor());
            }
        }

        // ASM SourceInterpreter intentionally replaces some copied source sets while
        // evaluating NEW/DUP/<init>.  javac's direct register(new Handler(...)) shape
        // leaves the constructor invocation immediately before EventBus.register and
        // the duplicate initialized reference on the operand stack.  Accept only that
        // exact adjacency as a fallback; any intervening instruction remains unresolved.
        if (result.isEmpty()) {
            AbstractInsnNode previous = previousOpcode(registerInstruction);
            if (previous instanceof MethodInsnNode constructor
                    && constructor.getOpcode() == Opcodes.INVOKESPECIAL
                    && constructor.name.equals("<init>")
                    && Type.getReturnType(constructor.desc).getSort() == Type.VOID) {
                result.add(constructor.owner);
            }
        }
        return result;
    }

    private static AbstractInsnNode previousOpcode(AbstractInsnNode instruction) {
        for (AbstractInsnNode previous = instruction.getPrevious(); previous != null; previous = previous.getPrevious()) {
            if (previous.getOpcode() >= 0) return previous;
        }
        return null;
    }

    private static void addObjectType(Set<String> output, String descriptor) {
        Type type = Type.getType(descriptor);
        if (type.getSort() == Type.OBJECT) output.add(type.getInternalName());
    }

    private static Bus bus(SourceValue receiver) {
        for (AbstractInsnNode producer : receiver.insns) {
            if (producer instanceof FieldInsnNode field && field.owner.equals(MINECRAFT_FORGE)
                    && field.desc.equals("L" + EVENT_BUS + ";")) return Bus.FORGE;
            if (producer instanceof MethodInsnNode call && call.owner.equals(FML_COMMON_HANDLER)
                    && call.name.equals("bus") && call.desc.equals("()L" + EVENT_BUS + ";")) return Bus.FML;
        }
        return Bus.UNKNOWN;
    }

    private static Side side(MethodNode method) {
        AnnotationNode annotation = annotation(method, SIDE_ONLY);
        if (annotation == null) return Side.COMMON;
        String value = annotationEnum(annotation, "value", "");
        if (value.equals("CLIENT")) return Side.CLIENT;
        if (value.equals("SERVER")) return Side.SERVER;
        return Side.COMMON;
    }

    private static AnnotationNode annotation(MethodNode method, String descriptor) {
        AnnotationNode found = annotation(method.visibleAnnotations, descriptor);
        return found != null ? found : annotation(method.invisibleAnnotations, descriptor);
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations == null) return null;
        for (AnnotationNode annotation : annotations) if (annotation.desc.equals(descriptor)) return annotation;
        return null;
    }

    private static boolean annotationBoolean(AnnotationNode annotation, String name, boolean fallback) {
        Object value = annotationValue(annotation, name);
        return value instanceof Boolean booleanValue ? booleanValue : fallback;
    }

    private static String annotationEnum(AnnotationNode annotation, String name, String fallback) {
        Object value = annotationValue(annotation, name);
        if (value instanceof String[] enumeration && enumeration.length == 2) return enumeration[1];
        return fallback;
    }

    private static Object annotationValue(AnnotationNode annotation, String name) {
        if (annotation.values == null) return null;
        for (int index = 0; index + 1 < annotation.values.size(); index += 2) {
            if (name.equals(annotation.values.get(index))) return annotation.values.get(index + 1);
        }
        return null;
    }
}
