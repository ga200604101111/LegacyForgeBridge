package dev.longyu.legacyforgebridge.convert;

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
 * Non-executing extractor for the common legacy pattern where a source base constructor stores
 * {@code short id -> this} into a static HashMap while also retaining a constant String name.
 *
 * <p>The table is accepted only when every emitted entry comes from a source {@code <clinit>}
 * allocation with direct constants and the allocated subclass constructor is a trivial forwarding
 * constructor to the proven base constructor. No source class is defined or initialized.</p>
 */
public final class LegacyConstantNameTableAnalyzer {
    private static final String HASH_MAP = "java/util/HashMap";
    private static final String SHORT = "java/lang/Short";
    private static final int MAX_ENTRIES = 4096;

    public record FieldRef(String owner, String name, String descriptor) { }
    public record Entry(short id, String name) { }
    public record Table(FieldRef field, String valueType, String nameField, List<Entry> entries) {
        public Table { entries = List.copyOf(entries); }
    }
    public record Analysis(List<Table> tables, List<String> diagnostics) {
        public Analysis { tables = List.copyOf(tables); diagnostics = List.copyOf(diagnostics); }
    }
    private record BasePlan(String valueType, String constructorDescriptor, String nameField, FieldRef mapField) { }

    private final Map<String, ClassNode> classes = new LinkedHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    public Analysis analyze(Path source) throws IOException {
        classes.clear(); diagnostics.clear(); load(source);
        Map<FieldRef, Table> found = new LinkedHashMap<>();
        for (ClassNode owner : classes.values()) {
            for (MethodNode method : owner.methods) {
                if (!method.name.equals("<init>")) continue;
                BasePlan plan = basePlan(owner, method);
                if (plan == null) continue;
                List<Entry> entries = entries(plan);
                if (entries.isEmpty()) continue;
                Table table = new Table(plan.mapField(), plan.valueType(), plan.nameField(), entries);
                Table previous = found.putIfAbsent(plan.mapField(), table);
                if (previous != null && !previous.equals(table)) {
                    found.remove(plan.mapField());
                    diagnostics.add("Conflicting constant name table evidence for " + plan.mapField());
                }
            }
        }
        return new Analysis(List.copyOf(found.values()), List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private void load(Path source) throws IOException {
        try (JarFile jar = new JarFile(source.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException malformed) {
                    diagnostics.add("Unreadable constant-table class " + entry.getName());
                }
            }
        }
    }

    private BasePlan basePlan(ClassNode owner, MethodNode constructor) {
        Type[] args = Type.getArgumentTypes(constructor.desc);
        if (args.length < 2 || args[0].getSort() != Type.INT
                || !args[1].getDescriptor().equals("Ljava/lang/String;")) return null;
        List<AbstractInsnNode> code = opcodes(constructor);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        LinkedHashSet<FieldRef> maps = new LinkedHashSet<>();
        for (int i = 2; i < code.size(); i++) {
            AbstractInsnNode a = code.get(i - 2), b = code.get(i - 1), c = code.get(i);
            if (aload(a, 0) && aload(b, 2) && c instanceof FieldInsnNode field
                    && c.getOpcode() == Opcodes.PUTFIELD && field.owner.equals(owner.name)
                    && field.desc.equals("Ljava/lang/String;")) names.add(field.name);
        }
        for (int i = 5; i < code.size(); i++) {
            AbstractInsnNode a = code.get(i - 5), b = code.get(i - 4), c = code.get(i - 3),
                    d = code.get(i - 2), e = code.get(i - 1), f = code.get(i);
            if (a instanceof FieldInsnNode field && a.getOpcode() == Opcodes.GETSTATIC
                    && field.desc.equals("Ljava/util/HashMap;") && iload(b, 1)
                    && c.getOpcode() == Opcodes.I2S
                    && d instanceof MethodInsnNode box && d.getOpcode() == Opcodes.INVOKESTATIC
                    && box.owner.equals(SHORT) && box.name.equals("valueOf")
                    && box.desc.equals("(S)Ljava/lang/Short;") && aload(e, 0)
                    && f instanceof MethodInsnNode put && put.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && put.owner.equals(HASH_MAP) && put.name.equals("put")
                    && put.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")) {
                maps.add(new FieldRef(field.owner, field.name, field.desc));
            }
        }
        if (names.size() != 1 || maps.size() != 1) return null;
        FieldRef map = maps.getFirst();
        if (!mapInitialized(map)) return null;
        return new BasePlan(owner.name, constructor.desc, names.getFirst(), map);
    }

    private boolean mapInitialized(FieldRef field) {
        ClassNode owner = classes.get(field.owner());
        if (owner == null) return false;
        MethodNode clinit = method(owner, "<clinit>", "()V");
        if (clinit == null) return false;
        List<AbstractInsnNode> code = opcodes(clinit);
        for (int i = 3; i < code.size(); i++) {
            if (code.get(i - 3) instanceof TypeInsnNode allocation
                    && allocation.getOpcode() == Opcodes.NEW && allocation.desc.equals(HASH_MAP)
                    && code.get(i - 2).getOpcode() == Opcodes.DUP
                    && code.get(i - 1) instanceof MethodInsnNode init
                    && init.getOpcode() == Opcodes.INVOKESPECIAL && init.owner.equals(HASH_MAP)
                    && init.name.equals("<init>") && init.desc.equals("()V")
                    && code.get(i) instanceof FieldInsnNode store && store.getOpcode() == Opcodes.PUTSTATIC
                    && store.owner.equals(field.owner()) && store.name.equals(field.name())
                    && store.desc.equals(field.descriptor())) return true;
        }
        return false;
    }

    private List<Entry> entries(BasePlan plan) {
        ClassNode owner = classes.get(plan.mapField().owner());
        if (owner == null) return List.of();
        MethodNode clinit = method(owner, "<clinit>", "()V");
        if (clinit == null) return List.of();
        List<AbstractInsnNode> code = opcodes(clinit);
        Type[] args = Type.getArgumentTypes(plan.constructorDescriptor());
        LinkedHashMap<Short, String> entries = new LinkedHashMap<>();
        Set<Short> conflicts = new HashSet<>();
        for (int i = 0; i < code.size(); i++) {
            if (!(code.get(i) instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESPECIAL
                    || !call.name.equals("<init>") || !call.desc.equals(plan.constructorDescriptor())
                    || !isSubclass(call.owner, plan.valueType())
                    || !forwardsConstructor(call.owner, plan.valueType(), plan.constructorDescriptor())) continue;
            int firstArg = i - args.length;
            if (firstArg < 2 || code.get(firstArg - 1).getOpcode() != Opcodes.DUP
                    || !(code.get(firstArg - 2) instanceof TypeInsnNode allocation)
                    || allocation.getOpcode() != Opcodes.NEW || !allocation.desc.equals(call.owner)) continue;
            Object idValue = constant(code.get(firstArg), args[0]);
            Object nameValue = constant(code.get(firstArg + 1), args[1]);
            if (!(idValue instanceof Number id) || !(nameValue instanceof String name)) continue;
            short key = id.shortValue();
            String previous = entries.putIfAbsent(key, name);
            if (previous != null && !previous.equals(name)) conflicts.add(key);
            if (entries.size() > MAX_ENTRIES) {
                diagnostics.add("Constant name table entry budget exceeded for " + plan.mapField());
                return List.of();
            }
        }
        conflicts.forEach(entries::remove);
        if (!conflicts.isEmpty()) diagnostics.add("Conflicting constant ids in " + plan.mapField() + ": " + conflicts);
        return entries.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new Entry(entry.getKey(), entry.getValue())).toList();
    }

    private boolean forwardsConstructor(String owner, String base, String descriptor) {
        if (owner.equals(base)) return true;
        ClassNode node = classes.get(owner);
        if (node == null || !isSubclass(owner, base)) return false;
        MethodNode constructor = method(node, "<init>", descriptor);
        if (constructor == null) return false;
        List<AbstractInsnNode> code = opcodes(constructor);
        Type[] args = Type.getArgumentTypes(descriptor);
        if (code.size() != args.length + 3 || !aload(code.getFirst(), 0)) return false;
        int local = 1;
        for (int i = 0; i < args.length; i++) {
            AbstractInsnNode load = code.get(i + 1);
            if (!(load instanceof VarInsnNode variable) || variable.var != local
                    || variable.getOpcode() != args[i].getOpcode(Opcodes.ILOAD)) return false;
            local += args[i].getSize();
        }
        AbstractInsnNode invoke = code.get(code.size() - 2);
        return invoke instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                && call.owner.equals(base) && call.name.equals("<init>") && call.desc.equals(descriptor)
                && code.getLast().getOpcode() == Opcodes.RETURN;
    }

    private boolean isSubclass(String type, String base) {
        for (int depth = 0; type != null && depth < 64; depth++) {
            if (type.equals(base)) return true;
            ClassNode node = classes.get(type);
            if (node == null) return false;
            type = node.superName;
        }
        return false;
    }

    private static Object constant(AbstractInsnNode instruction, Type expected) {
        if (instruction instanceof LdcInsnNode ldc) {
            if (expected.getSort() == Type.OBJECT && expected.getDescriptor().equals("Ljava/lang/String;")
                    && ldc.cst instanceof String value) return value;
            if (ldc.cst instanceof Number number) return number;
        }
        if (instruction instanceof IntInsnNode value
                && (instruction.getOpcode() == Opcodes.BIPUSH || instruction.getOpcode() == Opcodes.SIPUSH)) return value.operand;
        int opcode = instruction.getOpcode();
        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) return opcode - Opcodes.ICONST_0;
        if (opcode == Opcodes.FCONST_0) return 0F;
        if (opcode == Opcodes.FCONST_1) return 1F;
        if (opcode == Opcodes.FCONST_2) return 2F;
        if (opcode == Opcodes.LCONST_0) return 0L;
        if (opcode == Opcodes.LCONST_1) return 1L;
        if (opcode == Opcodes.DCONST_0) return 0D;
        if (opcode == Opcodes.DCONST_1) return 1D;
        return null;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        for (MethodNode method : owner.methods) if (method.name.equals(name) && method.desc.equals(descriptor)) return method;
        return null;
    }
    private static List<AbstractInsnNode> opcodes(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }
    private static boolean aload(AbstractInsnNode instruction, int local) {
        return instruction instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ALOAD && variable.var == local;
    }
    private static boolean iload(AbstractInsnNode instruction, int local) {
        return instruction instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ILOAD && variable.var == local;
    }
}
