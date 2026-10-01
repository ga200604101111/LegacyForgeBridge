package dev.yinghuang.legacyforgebridge.behavior;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Client-side bridge from 1.7.10 ItemArmor/source stack armor into the real 1.21.11 ARMOR
 * attribute. It never replaces getArmorValue; vanilla HUD reads the normal entity attribute.
 */
public final class Rev234ArmorAttributeSync {
    private static final String RUNTIME_PREFIX = "legacyforgebridge:runtime_armor/";
    private static final String[] ARMOR_SLOTS = {"head", "chest", "legs", "feet"};

    private Rev234ArmorAttributeSync() { }

    public static void sync(Object entity) {
        if (entity == null) return;
        try {
            Object armorHolder = Class.forName("net.minecraft.class_5134").getField("field_23724").get(null);
            Object attributeInstance = invokeCompatible(entity, "method_5996", armorHolder);
            if (attributeInstance == null) return;

            if (!isLocal1710(entity)) {
                removeRuntimeModifiers(attributeInstance);
                return;
            }

            LinkedHashMap<String, Wanted> wanted = new LinkedHashMap<>();
            Set<String> alreadyApplied = existingNonRuntimeModifierIds(attributeInstance);
            for (String slotName : ARMOR_SLOTS) {
                Object slot = slot(slotName);
                if (slot == null) continue;
                Object stack = invokeCompatible(entity, "method_6118", slot);
                if (stack == null || isEmptyStack(stack)) continue;

                String itemId = itemId(stack);
                Object rule = itemId == null ? null : sourceRule(itemId);
                if (rule == null) continue;

                Object armorSlot = invokeNoArgs(rule, "armorSlot");
                Object points = invokeNoArgs(rule, "armorPoints");
                if (!(armorSlot instanceof Number) || !(points instanceof Number number) || number.doubleValue() <= 0D) continue;

                int legacyArmorSlot = ((Number) armorSlot).intValue();
                double armorPoints = number.doubleValue();
                String baseId = RUNTIME_PREFIX + "base/" + slotName;
                if (!baseArmorAlreadyApplied(alreadyApplied, itemId, legacyArmorSlot)) {
                    wanted.put(baseId, new Wanted(baseId, armorPoints, 0));
                }

                final int[] ordinal = {0};
                BiConsumer<Object, Object> consumer = (attribute, modifier) -> {
                    try {
                        if (!sameHolder(attribute, armorHolder) || modifier == null) return;
                        String originalId = modifierId(modifier);
                        if (isBridgeArmorCarrier(originalId) || (originalId != null && alreadyApplied.contains(originalId))) return;
                        double amount = modifierAmount(modifier);
                        int operation = modifierOperation(modifier);
                        String runtimeId = RUNTIME_PREFIX + "stack/" + slotName + "/"
                                + stableToken(originalId, ordinal[0]++, amount, operation);
                        wanted.put(runtimeId, new Wanted(runtimeId, amount, operation));
                    } catch (Throwable ignored) { }
                };
                Method apply = findCompatibleMethod(stack.getClass(), "method_57354", slot, consumer);
                if (apply != null) {
                    apply.setAccessible(true);
                    apply.invoke(stack, slot, consumer);
                }
            }

            reconcile(attributeInstance, wanted);
        } catch (Throwable ignored) { }
    }

    private static Set<String> existingNonRuntimeModifierIds(Object instance) throws Exception {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        Object currentObject = invokeNoArgs(instance, "method_6195");
        if (!(currentObject instanceof Collection<?> current)) return ids;
        for (Object modifier : current) {
            String id = modifierId(modifier);
            if (id != null && !id.startsWith(RUNTIME_PREFIX)) ids.add(id);
        }
        return ids;
    }

    private static boolean baseArmorAlreadyApplied(Set<String> ids, String itemId, int legacyArmorSlot) {
        if (ids.contains("legacyforgebridge:source_armor/" + legacyArmorSlot)) return true;
        if (itemId == null) return false;
        int colon = itemId.indexOf(':');
        if (colon > 0 && colon + 1 < itemId.length()) {
            String generated = itemId.substring(0, colon) + ":converted/" + itemId.substring(colon + 1) + "_armor";
            if (ids.contains(generated)) return true;
            String compat = "legacyforgebridge:compat_armor/" + itemId.substring(0, colon) + "/" + itemId.substring(colon + 1);
            if (ids.contains(compat)) return true;
        }
        return false;
    }

    private static void reconcile(Object instance, Map<String, Wanted> wanted) throws Exception {
        Object currentObject = invokeNoArgs(instance, "method_6195");
        Collection<?> current = currentObject instanceof Collection<?> c ? new ArrayList<>(c) : List.of();
        HashMap<String, Object> runtimeCurrent = new HashMap<>();
        for (Object modifier : current) {
            String id = modifierId(modifier);
            if (id != null && id.startsWith(RUNTIME_PREFIX)) runtimeCurrent.put(id, modifier);
        }

        for (Map.Entry<String, Object> entry : runtimeCurrent.entrySet()) {
            Wanted target = wanted.get(entry.getKey());
            if (target == null || !matches(entry.getValue(), target)) {
                invokeCompatible(instance, "method_6200", identifier(entry.getKey()));
            }
        }

        for (Wanted target : wanted.values()) {
            Object existing = runtimeCurrent.get(target.id());
            if (existing != null && matches(existing, target)) continue;
            invokeCompatible(instance, "method_26835", newModifier(target));
        }
    }

    private static void removeRuntimeModifiers(Object instance) throws Exception {
        Object currentObject = invokeNoArgs(instance, "method_6195");
        if (!(currentObject instanceof Collection<?> current)) return;
        for (Object modifier : new ArrayList<>(current)) {
            String id = modifierId(modifier);
            if (id != null && id.startsWith(RUNTIME_PREFIX)) invokeCompatible(instance, "method_6200", identifier(id));
        }
    }

    private static boolean matches(Object modifier, Wanted wanted) throws Exception {
        return Double.compare(modifierAmount(modifier), wanted.amount()) == 0
                && modifierOperation(modifier) == wanted.operation();
    }

    private static Object newModifier(Wanted wanted) throws Exception {
        Class<?> modifierClass = Class.forName("net.minecraft.class_1322");
        Class<?> identifierClass = Class.forName("net.minecraft.class_2960");
        Class<?> operationClass = Class.forName("net.minecraft.class_1322$class_1323");
        Constructor<?> constructor = modifierClass.getConstructor(identifierClass, double.class, operationClass);
        return constructor.newInstance(identifier(wanted.id()), wanted.amount(), operation(wanted.operation()));
    }

    private static Object operation(int operation) throws Exception {
        Class<?> type = Class.forName("net.minecraft.class_1322$class_1323");
        String field = switch (operation) { case 1 -> "field_6330"; case 2 -> "field_6331"; default -> "field_6328"; };
        return type.getField(field).get(null);
    }

    private static Object identifier(String id) throws Exception {
        Class<?> type = Class.forName("net.minecraft.class_2960");
        return type.getMethod("method_60654", String.class).invoke(null, id);
    }

    private static boolean isLocal1710(Object entity) throws Exception {
        Class<?> backend = Class.forName("dev.yinghuang.legacyforgebridge.protocol.ViaFabricPlusBackend");
        Object instance = backend.getField("INSTANCE").get(null);
        if (!Boolean.TRUE.equals(invokeNoArgs(instance, "isMinecraft1710Target"))) return false;
        Class<?> minecraft = Class.forName("net.minecraft.class_310");
        Object client = minecraft.getMethod("method_1551").invoke(null);
        Field player = findField(minecraft, "field_1724");
        if (player == null) return false;
        player.setAccessible(true);
        return player.get(client) == entity;
    }

    private static Object slot(String name) throws Exception {
        Class<?> type = Class.forName("net.minecraft.class_1304");
        return type.getMethod("method_5924", String.class).invoke(null, name);
    }

    private static boolean isEmptyStack(Object stack) {
        try {
            Method method = findMethod(stack.getClass(), "method_7960", 0);
            if (method == null) return false;
            method.setAccessible(true);
            return Boolean.TRUE.equals(method.invoke(stack));
        } catch (Throwable ignored) { return false; }
    }

    private static String itemId(Object stack) throws Exception {
        Object item = invokeNoArgs(stack, "method_7909");
        if (item == null) return null;
        Class<?> registries = Class.forName("net.minecraft.class_7923");
        Object registry = registries.getField("field_41178").get(null);
        Object id = invokeCompatible(registry, "method_10221", item);
        return id == null ? null : String.valueOf(id);
    }

    private static Object sourceRule(String id) throws Exception {
        Class<?> runtime = Class.forName("dev.yinghuang.legacyforgebridge.compat.LegacySourceItemRuntime");
        return runtime.getMethod("rule", String.class).invoke(null, id);
    }

    private static boolean sameHolder(Object a, Object b) { return a == b || (a != null && a.equals(b)); }

    private static boolean isBridgeArmorCarrier(String id) {
        if (id == null) return false;
        return id.startsWith("legacyforgebridge:source_armor/")
                || id.startsWith("legacyforgebridge:compat_armor/")
                || (id.contains(":converted/") && id.endsWith("_armor"))
                || id.startsWith(RUNTIME_PREFIX);
    }

    private static String stableToken(String originalId, int ordinal, double amount, int operation) {
        String seed = String.valueOf(originalId) + "|" + ordinal + "|" + Double.toHexString(amount) + "|" + operation;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String modifierId(Object modifier) throws Exception {
        for (Field field : modifier.getClass().getDeclaredFields()) {
            if (field.getType().getName().equals("net.minecraft.class_2960")) {
                field.setAccessible(true);
                Object value = field.get(modifier);
                return value == null ? null : String.valueOf(value);
            }
        }
        for (Method method : modifier.getClass().getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType().getName().equals("net.minecraft.class_2960")) {
                method.setAccessible(true);
                Object value = method.invoke(modifier);
                return value == null ? null : String.valueOf(value);
            }
        }
        return null;
    }

    private static double modifierAmount(Object modifier) throws Exception {
        try {
            Method method = modifier.getClass().getDeclaredMethod("comp_2449");
            method.setAccessible(true);
            return ((Number) method.invoke(modifier)).doubleValue();
        } catch (NoSuchMethodException ignored) { }
        for (Field field : modifier.getClass().getDeclaredFields()) {
            if (field.getType() == double.class) { field.setAccessible(true); return field.getDouble(modifier); }
        }
        return 0D;
    }

    private static int modifierOperation(Object modifier) throws Exception {
        Object operation = null;
        for (Field field : modifier.getClass().getDeclaredFields()) {
            if (field.getType().getName().equals("net.minecraft.class_1322$class_1323")) {
                field.setAccessible(true); operation = field.get(modifier); break;
            }
        }
        if (operation == null) {
            for (Method method : modifier.getClass().getDeclaredMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType().getName().equals("net.minecraft.class_1322$class_1323")) {
                    method.setAccessible(true); operation = method.invoke(modifier); break;
                }
            }
        }
        if (operation == null) return 0;
        Method id = findMethod(operation.getClass(), "method_56082", 0);
        if (id == null) return 0;
        id.setAccessible(true);
        Object value = id.invoke(operation);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static Object invokeNoArgs(Object target, String name) throws Exception {
        Method method = findMethod(target.getClass(), name, 0);
        if (method == null) throw new NoSuchMethodException(target.getClass().getName() + "." + name + "()");
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object invokeCompatible(Object target, String name, Object... args) throws Exception {
        Method method = findCompatibleMethod(target.getClass(), name, args);
        if (method == null) throw new NoSuchMethodException(target.getClass().getName() + "." + name);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Method findMethod(Class<?> type, String name, int count) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) if (method.getName().equals(name) && method.getParameterCount() == count) return method;
        }
        for (Method method : type.getMethods()) if (method.getName().equals(name) && method.getParameterCount() == count) return method;
        return null;
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Object... args) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                Class<?>[] p = method.getParameterTypes(); boolean ok = true;
                for (int i = 0; i < p.length; i++) if (args[i] != null && !p[i].isAssignableFrom(args[i].getClass())) { ok = false; break; }
                if (ok) return method;
            }
        }
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] p = method.getParameterTypes(); boolean ok = true;
            for (int i = 0; i < p.length; i++) if (args[i] != null && !p[i].isAssignableFrom(args[i].getClass())) { ok = false; break; }
            if (ok) return method;
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { return current.getDeclaredField(name); } catch (NoSuchFieldException ignored) { }
        }
        return null;
    }

    private record Wanted(String id, double amount, int operation) { }
}
