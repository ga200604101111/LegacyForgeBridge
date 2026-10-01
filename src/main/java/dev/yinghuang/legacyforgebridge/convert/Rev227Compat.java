package dev.yinghuang.legacyforgebridge.convert;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

/**
 * rev227 compatibility helpers used by the cumulative delivered binary.
 *
 * <p>Projectile additions are client presentation only. They do not execute legacy server
 * damage, removal, drop, potion or explosion logic.</p>
 */
public final class Rev227Compat {
    private static final Set<Integer> IY_IMPACT_CRIT_DAGGERS = Set.of(4800, 4801, 4802, 4803);
    private static final int IY_MAGIC_BULLET = 4805;
    private static volatile Object CRIT_PARTICLE;
    private static volatile Object BUBBLE_PARTICLE;
    private static volatile Object SPELL_PARTICLE;

    private Rev227Compat() { }

    public static boolean booleanEntryValue(Object entry) {
        Object value = entryValue(entry);
        if (value instanceof Boolean bool) return bool;
        throw new IllegalStateException("Cloth Config boolean entry returned " + typeName(value));
    }

    public static int intEntryValue(Object entry) {
        Object value = entryValue(entry);
        if (value instanceof Number number) return number.intValue();
        throw new IllegalStateException("Cloth Config integer entry returned " + typeName(value));
    }

    public static String stringEntryValue(Object entry) {
        Object value = entryValue(entry);
        if (value instanceof String text) return text;
        throw new IllegalStateException("Cloth Config string entry returned " + typeName(value));
    }

    public static void projectileTick(Object projectile) {
        if (!isIyProjectile(projectile, IY_MAGIC_BULLET)) return;
        try {
            spawnAtProjectile(projectile, spellParticle(), 0.0D, 0.0D, 0.0D);
            Object inWater = invokeNoArgs(projectile, "method_5799");
            if (Boolean.TRUE.equals(inWater)) {
                double[] velocity = velocity(projectile);
                double x = number(invokeNoArgs(projectile, "method_23317"));
                double y = number(invokeNoArgs(projectile, "method_23318"));
                double z = number(invokeNoArgs(projectile, "method_23321"));
                double px = x - velocity[0] * 0.25D;
                double py = y - velocity[1] * 0.25D;
                double pz = z - velocity[2] * 0.25D;
                Object bubble = bubbleParticle();
                for (int i = 0; i < 4; i++) {
                    spawn(projectile, bubble, px, py, pz, velocity[0], velocity[1], velocity[2]);
                }
            }
        } catch (Throwable ignored) {
            // Rendering compatibility must not take down the client if a future mapping changes.
        }
    }

    public static void projectileImpact(Object projectile, boolean collided) {
        if (!collided) return;
        int numericId = projectileNumericId(projectile);
        if (!IY_IMPACT_CRIT_DAGGERS.contains(numericId) || !isIyProjectile(projectile, numericId)) return;
        try {
            Object crit = critParticle();
            for (int i = 0; i < 8; i++) {
                spawnAtProjectile(projectile, crit, 0.0D, 0.3D, 0.0D);
            }
        } catch (Throwable ignored) {
            // Rendering compatibility must not take down the client if a future mapping changes.
        }
    }

    static int projectileNumericId(Object projectile) {
        try {
            Object rule = invokeNoArgs(projectile, "projectileRule");
            Object id = invokeNoArgs(rule, "legacyNumericId");
            return id instanceof Number number ? number.intValue() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static boolean isIyProjectile(Object projectile, int expectedNumericId) {
        try {
            Object rule = invokeNoArgs(projectile, "projectileRule");
            Object modId = invokeNoArgs(rule, "legacyModId");
            Object numericId = invokeNoArgs(rule, "legacyNumericId");
            Object itemId = invokeNoArgs(rule, "itemId");
            return "iymts_mod".equals(String.valueOf(modId))
                    && numericId instanceof Number number && number.intValue() == expectedNumericId
                    && itemId != null && String.valueOf(itemId).startsWith("iymts_mod:");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object entryValue(Object entry) {
        if (entry == null) throw new IllegalArgumentException("entry");
        try {
            Method method = findMethod(entry.getClass(), "getValue", 0);
            if (method == null) throw new NoSuchMethodException(entry.getClass().getName() + ".getValue()");
            method.setAccessible(true);
            return method.invoke(entry);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to read Cloth Config entry value", exception);
        }
    }

    private static Object critParticle() throws ReflectiveOperationException {
        Object cached = CRIT_PARTICLE;
        if (cached != null) return cached;
        Class<?> bridge = Class.forName("dev.yinghuang.legacyforgebridge.compat.LegacyParticle1710");
        Method modern = bridge.getMethod("modern", String.class);
        cached = modern.invoke(null, "crit");
        if (cached == null) throw new IllegalStateException("Legacy crit particle mapping unavailable");
        CRIT_PARTICLE = cached;
        return cached;
    }

    private static Object bubbleParticle() throws ReflectiveOperationException {
        Object cached = BUBBLE_PARTICLE;
        if (cached != null) return cached;
        Class<?> types = Class.forName("net.minecraft.class_2398");
        Field field = types.getField("field_11247");
        cached = field.get(null);
        if (cached == null) throw new IllegalStateException("Modern bubble particle unavailable");
        BUBBLE_PARTICLE = cached;
        return cached;
    }

    private static Object spellParticle() throws ReflectiveOperationException {
        Object cached = SPELL_PARTICLE;
        if (cached != null) return cached;
        Class<?> types = Class.forName("net.minecraft.class_2398");
        Object effectType = types.getField("field_11245").get(null);
        Class<?> effect = Class.forName("net.minecraft.class_11979");
        Method factory = findStaticMethod(effect, "method_74419", 3);
        if (factory == null) throw new NoSuchMethodException("EffectParticleEffect.of(type,color,power)");
        factory.setAccessible(true);
        cached = factory.invoke(null, effectType, 0xFFFFFF, 1.0F);
        if (cached == null) throw new IllegalStateException("Modern spell particle unavailable");
        SPELL_PARTICLE = cached;
        return cached;
    }

    private static void spawnAtProjectile(Object projectile, Object particle, double vx, double vy, double vz)
            throws ReflectiveOperationException {
        double x = number(invokeNoArgs(projectile, "method_23317"));
        double y = number(invokeNoArgs(projectile, "method_23318"));
        double z = number(invokeNoArgs(projectile, "method_23321"));
        spawn(projectile, particle, x, y, z, vx, vy, vz);
    }

    private static void spawn(Object projectile, Object particle,
                              double x, double y, double z, double vx, double vy, double vz)
            throws ReflectiveOperationException {
        if (particle == null) return;
        Object level = invokeNoArgs(projectile, "method_73183");
        if (level == null) return;
        Object client = invokeNoArgs(level, "method_8608");
        if (!Boolean.TRUE.equals(client)) return;
        Method add = findCompatibleParticleMethod(level.getClass());
        if (add == null) throw new NoSuchMethodException(level.getClass().getName() + ".method_8406(particle,6d)");
        add.setAccessible(true);
        add.invoke(level, particle, x, y, z, vx, vy, vz);
    }

    private static Method findCompatibleParticleMethod(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals("method_8406") || method.getParameterCount() != 7) continue;
                Class<?>[] parameters = method.getParameterTypes();
                boolean sixDoubles = true;
                for (int i = 1; i < parameters.length; i++) {
                    if (parameters[i] != double.class) { sixDoubles = false; break; }
                }
                if (sixDoubles) return method;
            }
        }
        return null;
    }

    private static double[] velocity(Object projectile) {
        try {
            Object vector = invokeNoArgs(projectile, "method_18798");
            if (vector == null) return new double[]{0D, 0D, 0D};
            return new double[]{
                    fieldNumber(vector, "field_1352"),
                    fieldNumber(vector, "field_1351"),
                    fieldNumber(vector, "field_1350")
            };
        } catch (Throwable ignored) {
            return new double[]{0D, 0D, 0D};
        }
    }

    private static double fieldNumber(Object value, String name) throws ReflectiveOperationException {
        Field field = findField(value.getClass(), name);
        if (field == null) throw new NoSuchFieldException(name);
        field.setAccessible(true);
        return number(field.get(value));
    }

    private static Object invokeNoArgs(Object target, String name) throws ReflectiveOperationException {
        if (target == null) return null;
        Method method = findMethod(target.getClass(), name, 0);
        if (method == null) throw new NoSuchMethodException(target.getClass().getName() + "." + name + "()");
        method.setAccessible(true);
        return method.invoke(target);
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
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { }
        }
        return null;
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0D;
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }
}
