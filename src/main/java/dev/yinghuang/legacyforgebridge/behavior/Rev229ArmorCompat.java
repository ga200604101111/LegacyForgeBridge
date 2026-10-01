package dev.yinghuang.legacyforgebridge.behavior;

import dev.yinghuang.legacyforgebridge.compat.LegacySourceItemContract;
import dev.yinghuang.legacyforgebridge.compat.LegacySourceItemRuntime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * rev229 armor compatibility split.
 *
 * <p>The original 1.7.10 armor points are presented as their own tooltip row. A separate hidden
 * modern ARMOR modifier remains the only carrier that contributes to the 1.21.11 armor HUD.
 * Source movement-speed remains active but hidden; source knockback resistance and other source
 * modifiers remain visible. Stack/NBT-added modern modifiers are never filtered here.</p>
 */
public final class Rev229ArmorCompat {
    private Rev229ArmorCompat() { }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void appendLegacyArmorTooltip(Object stack, List lines) {
        if (stack == null || lines == null) return;
        try {
            String itemId = itemId(stack);
            if (itemId == null) return;
            LegacySourceItemContract.Rule rule = LegacySourceItemRuntime.rule(itemId);
            if (rule == null || rule.armorSlot() == null || rule.armorPoints() == null || rule.armorPoints() <= 0) return;

            String armorName = translate("attribute.name.generic.armor");
            Object component = formatted("§9+" + rule.armorPoints() + " " + armorName);
            if (component != null) lines.add(component);
        } catch (Throwable ignored) {
            // Presentation compatibility must never be allowed to crash the client.
        }
    }

    public static void addArmorSourceModifier(Object builder, Object attribute, Object modifier,
                                              Object slotGroup, String legacyAttribute) {
        if (builder == null || attribute == null || modifier == null || slotGroup == null) return;
        try {
            if ("generic.movementSpeed".equals(legacyAttribute)) {
                Class<?> displayType = Class.forName("net.minecraft.class_9285$class_11193");
                Method hidden = findStaticMethod(displayType, "method_70733", 0);
                if (hidden == null) throw new NoSuchMethodException("ItemAttributeModifiers.Display.hidden");
                hidden.setAccessible(true);
                Object display = hidden.invoke(null);
                Method add = findMethod(builder.getClass(), "method_70728", 4);
                if (add == null) throw new NoSuchMethodException("ItemAttributeModifiers.Builder.add(display)");
                add.setAccessible(true);
                add.invoke(builder, attribute, modifier, slotGroup, display);
            } else {
                Method add = findMethod(builder.getClass(), "method_57487", 3);
                if (add == null) throw new NoSuchMethodException("ItemAttributeModifiers.Builder.add");
                add.setAccessible(true);
                add.invoke(builder, attribute, modifier, slotGroup);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to add legacy armor source modifier", exception);
        }
    }

    private static String itemId(Object stack) throws ReflectiveOperationException {
        Method getItem = findMethod(stack.getClass(), "method_7909", 0);
        if (getItem == null) return null;
        getItem.setAccessible(true);
        Object item = getItem.invoke(stack);
        if (item == null) return null;

        Class<?> registries = Class.forName("net.minecraft.class_7923");
        Field itemRegistryField = registries.getField("field_41178");
        Object itemRegistry = itemRegistryField.get(null);
        if (itemRegistry == null) return null;
        Method getKey = findMethod(itemRegistry.getClass(), "method_10221", 1);
        if (getKey == null) return null;
        getKey.setAccessible(true);
        Object id = getKey.invoke(itemRegistry, item);
        return id == null ? null : String.valueOf(id);
    }

    private static String translate(String key) {
        try {
            Class<?> i18n = Class.forName("net.minecraft.class_1074");
            Method method = null;
            for (Method candidate : i18n.getDeclaredMethods()) {
                if (!Modifier.isStatic(candidate.getModifiers()) || !candidate.getName().equals("method_4662")) continue;
                Class<?>[] p = candidate.getParameterTypes();
                if (p.length == 2 && p[0] == String.class && p[1].isArray()) { method = candidate; break; }
            }
            if (method == null) return key;
            method.setAccessible(true);
            Object value = method.invoke(null, key, new Object[0]);
            return value instanceof String s && !s.isBlank() ? s : key;
        } catch (Throwable ignored) {
            return key;
        }
    }

    private static Object formatted(String text) {
        try {
            Class<?> legacyText = Class.forName("dev.yinghuang.legacyforgebridge.behavior.LegacyText");
            Method method = findMethod(legacyText, "formatted", 1);
            if (method == null) return null;
            method.setAccessible(true);
            return method.invoke(null, text);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) return method;
            }
        }
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) return method;
        }
        return null;
    }

    private static Method findStaticMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getName().equals(name)
                    && method.getParameterCount() == parameterCount) return method;
        }
        for (Method method : type.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getName().equals(name)
                    && method.getParameterCount() == parameterCount) return method;
        }
        return null;
    }
}
