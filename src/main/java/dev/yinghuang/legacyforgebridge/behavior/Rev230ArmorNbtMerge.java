package dev.yinghuang.legacyforgebridge.behavior;

import dev.yinghuang.legacyforgebridge.compat.LegacySourceItemContract;
import dev.yinghuang.legacyforgebridge.compat.LegacySourceItemRuntime;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Keeps 1.7.10 armor-item semantics when a legacy stack also has NBT AttributeModifiers.
 *
 * <p>In 1.21.11 an explicit stack ATTRIBUTE_MODIFIERS component replaces the item's base
 * component. 1.7.10 armor protection is not part of that NBT attribute list, so a legacy armor
 * stack with NBT attributes still keeps its ordinary armor protection. This bridge therefore
 * merges one hidden modern ARMOR carrier into the converted stack component while leaving every
 * legacy/NBT modifier unchanged and visible. The carrier is stripped again before the stack is
 * translated back to the unchanged 1.7.10 server.</p>
 */
public final class Rev230ArmorNbtMerge {
    private static final String CARRIER_PREFIX = "legacyforgebridge:compat_armor/";
    private static final String KEY_CLASS = "com.viaversion.viaversion.api.minecraft.data.StructuredDataKey";
    private static final String MODIFIERS_CLASS = "com.viaversion.viaversion.api.minecraft.item.data.AttributeModifiers1_21";
    private static final String MODIFIER_CLASS = MODIFIERS_CLASS + "$AttributeModifier";
    private static final String MODIFIER_DATA_CLASS = MODIFIERS_CLASS + "$ModifierData";
    private static final String DISPLAY_CLASS = MODIFIERS_CLASS + "$Display";

    private Rev230ArmorNbtMerge() { }

    public static void toClient(Object viaItem, Object protocol) {
        if (viaItem == null || protocol == null) return;
        try {
            Object container = invokeNoArgs(viaItem, "dataContainer");
            if (container == null) return;
            Object key = key("ATTRIBUTE_MODIFIERS1_21_6");
            Object existing = invokeOne(container, "get", key);
            if (existing == null) return;

            String itemId = markerModernId(container);
            if (itemId == null || itemId.isBlank()) return;
            LegacySourceItemContract.Rule rule = LegacySourceItemRuntime.rule(itemId);
            if (rule == null || rule.armorSlot() == null || rule.armorPoints() == null || rule.armorPoints() <= 0) return;

            int armorAttributeId = armorAttributeId(protocol);
            if (armorAttributeId < 0) return;
            Object merged = merge(existing, itemId, rule.armorPoints(), rule.armorSlot(), armorAttributeId);
            if (merged != existing) invokeTwo(container, "set", key, merged);
        } catch (Throwable ignored) {
            // Compatibility presentation must never make the client unusable if Via internals change.
        }
    }

    public static void toServer(Object viaItem) {
        if (viaItem == null) return;
        try {
            Object container = invokeNoArgs(viaItem, "dataContainer");
            if (container == null) return;
            Object key = key("ATTRIBUTE_MODIFIERS1_21_6");
            Object existing = invokeOne(container, "get", key);
            if (existing == null) return;
            Object stripped = strip(existing);
            if (stripped != existing) invokeTwo(container, "set", key, stripped);
        } catch (Throwable ignored) {
            // Never block a serverbound item packet because a compatibility carrier could not be removed.
        }
    }

    public static Object mergeForTest(Object existing, String itemId, int armorPoints, int armorSlot, int armorAttributeId) throws Exception {
        return merge(existing, itemId, armorPoints, armorSlot, armorAttributeId);
    }

    public static Object stripForTest(Object existing) throws Exception { return strip(existing); }

    private static Object merge(Object existing, String itemId, int armorPoints, int armorSlot, int armorAttributeId) throws Exception {
        Object modifiers = invokeNoArgs(existing, "modifiers");
        int length = Array.getLength(modifiers);
        String carrierId = carrierId(itemId);
        for (int i = 0; i < length; i++) {
            Object modifier = Array.get(modifiers, i);
            if (carrierId.equals(modifierId(modifier))) return existing;
        }

        Class<?> modifierClass = Class.forName(MODIFIER_CLASS);
        Object expanded = Array.newInstance(modifierClass, length + 1);
        for (int i = 0; i < length; i++) Array.set(expanded, i, Array.get(modifiers, i));

        Class<?> modifierDataClass = Class.forName(MODIFIER_DATA_CLASS);
        Constructor<?> dataCtor = modifierDataClass.getConstructor(String.class, double.class, int.class);
        Object data = dataCtor.newInstance(carrierId, (double) armorPoints, 0);

        Class<?> displayClass = Class.forName(DISPLAY_CLASS);
        Object hidden = displayClass.getConstructor(int.class).newInstance(1);
        Constructor<?> modifierCtor = modifierClass.getConstructor(int.class, modifierDataClass, int.class, displayClass);
        Object base = modifierCtor.newInstance(armorAttributeId, data, slotType(armorSlot), hidden);
        Array.set(expanded, length, base);

        boolean show = booleanValue(invokeNoArgs(existing, "showInTooltip"), true);
        Class<?> modifiersClass = Class.forName(MODIFIERS_CLASS);
        return modifiersClass.getConstructor(expanded.getClass(), boolean.class).newInstance(expanded, show);
    }

    private static Object strip(Object existing) throws Exception {
        Object modifiers = invokeNoArgs(existing, "modifiers");
        int length = Array.getLength(modifiers), kept = 0;
        for (int i = 0; i < length; i++) if (!isCarrier(Array.get(modifiers, i))) kept++;
        if (kept == length) return existing;

        Class<?> modifierClass = Class.forName(MODIFIER_CLASS);
        Object filtered = Array.newInstance(modifierClass, kept);
        int out = 0;
        for (int i = 0; i < length; i++) {
            Object modifier = Array.get(modifiers, i);
            if (!isCarrier(modifier)) Array.set(filtered, out++, modifier);
        }
        boolean show = booleanValue(invokeNoArgs(existing, "showInTooltip"), true);
        Class<?> modifiersClass = Class.forName(MODIFIERS_CLASS);
        return modifiersClass.getConstructor(filtered.getClass(), boolean.class).newInstance(filtered, show);
    }

    private static boolean isCarrier(Object modifier) throws Exception {
        String id = modifierId(modifier);
        return id != null && id.startsWith(CARRIER_PREFIX);
    }

    private static String modifierId(Object modifier) throws Exception {
        Object data = invokeNoArgs(modifier, "modifier");
        Object id = data == null ? null : invokeNoArgs(data, "id");
        return id == null ? null : String.valueOf(id);
    }

    private static int armorAttributeId(Object protocol) throws Exception {
        Object mappingData = invokeNoArgs(protocol, "getMappingData");
        if (mappingData == null) return -1;
        Object mappings = invokeNoArgs(mappingData, "getAttributeMappings");
        if (mappings == null) return -1;
        Method mappedId = findMethod(mappings.getClass(), "mappedId", 1);
        if (mappedId == null) return -1;
        mappedId.setAccessible(true);
        Object value = mappedId.invoke(mappings, "minecraft:armor");
        return value instanceof Number n ? n.intValue() : -1;
    }

    private static String markerModernId(Object container) throws Exception {
        Object customDataKey = key("CUSTOM_DATA");
        Object customData = invokeOne(container, "get", customDataKey);
        if (customData == null) return null;
        Method compound = findMethod(customData.getClass(), "getCompoundTag", 1);
        if (compound == null) return null;
        compound.setAccessible(true);
        Object marker = compound.invoke(customData, "LFB|legacy_item");
        if (marker == null) return null;
        Method getString = findMethod(marker.getClass(), "getString", 1);
        if (getString == null) return null;
        getString.setAccessible(true);
        Object id = getString.invoke(marker, "modern_id");
        return id == null ? null : String.valueOf(id);
    }

    private static String carrierId(String itemId) {
        String value = itemId == null ? "unknown" : itemId.toLowerCase(java.util.Locale.ROOT);
        StringBuilder path = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '/') path.append(c);
            else if (c == ':') path.append('/');
            else path.append('_');
        }
        return CARRIER_PREFIX + path;
    }

    private static int slotType(int legacyArmorSlot) {
        return switch (legacyArmorSlot) {
            case 0 -> 7;
            case 1 -> 6;
            case 2 -> 5;
            case 3 -> 4;
            default -> 8;
        };
    }

    private static Object key(String fieldName) throws Exception {
        Field field = Class.forName(KEY_CLASS).getField(fieldName);
        return field.get(null);
    }

    private static Object invokeNoArgs(Object target, String name) throws Exception {
        Method method = findMethod(target.getClass(), name, 0);
        if (method == null) throw new NoSuchMethodException(target.getClass().getName() + "." + name + "()");
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object invokeOne(Object target, String name, Object arg) throws Exception {
        Method method = findCompatible(target.getClass(), name, 1, arg);
        if (method == null) throw new NoSuchMethodException(target.getClass().getName() + "." + name + "(1)");
        method.setAccessible(true);
        return method.invoke(target, arg);
    }

    private static Object invokeTwo(Object target, String name, Object first, Object second) throws Exception {
        Method method = findCompatible(target.getClass(), name, 2, first, second);
        if (method == null) throw new NoSuchMethodException(target.getClass().getName() + "." + name + "(2)");
        method.setAccessible(true);
        return method.invoke(target, first, second);
    }

    private static Method findMethod(Class<?> type, String name, int count) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) if (method.getName().equals(name) && method.getParameterCount() == count) return method;
        }
        for (Method method : type.getMethods()) if (method.getName().equals(name) && method.getParameterCount() == count) return method;
        return null;
    }

    private static Method findCompatible(Class<?> type, String name, int count, Object... args) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != count) continue;
                Class<?>[] p = method.getParameterTypes(); boolean ok = true;
                for (int i = 0; i < count; i++) if (args[i] != null && !p[i].isAssignableFrom(args[i].getClass())) { ok = false; break; }
                if (ok) return method;
            }
        }
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != count) continue;
            Class<?>[] p = method.getParameterTypes(); boolean ok = true;
            for (int i = 0; i < count; i++) if (args[i] != null && !p[i].isAssignableFrom(args[i].getClass())) { ok = false; break; }
            if (ok) return method;
        }
        return null;
    }

    private static boolean booleanValue(Object value, boolean fallback) { return value instanceof Boolean b ? b : fallback; }
}
