package dev.yinghuang.legacyforgebridge.behavior;

import java.lang.reflect.*;

/** Exact 1.21.11 intermediary signatures, shared by the local-only adapters. */
final class Rev242ClientAccess {
    private static volatile Rev242ClientAccess instance;
    final Method getClient, onThread, legacy, connection, server, velocity, setVelocity, getY,
            isGround, isWater, isLava, isClimbing, hasVehicle, abilities, getId, packetId, packetVelocity;
    final Field player, world, age, hurt, flying, verticalCollision, vectorX, vectorY, vectorZ;
    static Rev242ClientAccess get() throws ReflectiveOperationException {
        Rev242ClientAccess a = instance;
        if (a == null) synchronized (Rev242ClientAccess.class) {
            a = instance;
            if (a == null) instance = a = new Rev242ClientAccess();
        }
        return a;
    }
    private Rev242ClientAccess() throws ReflectiveOperationException {
        Class<?> mc = Class.forName("net.minecraft.class_310"), e = Class.forName("net.minecraft.class_1297"),
                l = Class.forName("net.minecraft.class_1309"), p = Class.forName("net.minecraft.class_1657"),
                v = Class.forName("net.minecraft.class_243"), packet = Class.forName("net.minecraft.class_2743");
        getClient = mc.getMethod("method_1551"); onThread = mc.getMethod("method_18854");
        player = mc.getField("field_1724"); world = mc.getField("field_1687");
        connection = mc.getMethod("method_1562"); server = mc.getMethod("method_1576");
        legacy = Class.forName("dev.yinghuang.legacyforgebridge.session.LegacySessionController").getMethod("isLegacy1710");
        age = e.getField("field_6012"); hurt = l.getField("field_6235"); verticalCollision = e.getField("field_5992");
        velocity = e.getMethod("method_18798"); setVelocity = e.getMethod("method_18800", double.class, double.class, double.class);
        getY = e.getMethod("method_23318"); isGround = e.getMethod("method_24828");
        isWater = e.getMethod("method_5799"); isLava = e.getMethod("method_5771");
        isClimbing = l.getMethod("method_6101"); hasVehicle = e.getMethod("method_5765");
        abilities = p.getMethod("method_31549"); flying = Class.forName("net.minecraft.class_1656").getField("field_7479");
        getId = e.getMethod("method_5628");
        vectorX = v.getField("field_1352"); vectorY = v.getField("field_1351"); vectorZ = v.getField("field_1350");
        packetId = packet.getMethod("method_11818"); packetVelocity = packet.getMethod("method_73085");
    }
    Object client() throws ReflectiveOperationException { return getClient.invoke(null); }
    boolean onThread(Object c) throws ReflectiveOperationException { return (Boolean)onThread.invoke(c); }
    boolean legacyMultiplayer(Object c) throws ReflectiveOperationException {
        return (Boolean)legacy.invoke(null) && player.get(c) != null && world.get(c) != null
                && connection.invoke(c) != null && server.invoke(c) == null;
    }
    long age(Object p) throws ReflectiveOperationException { return age.getInt(p); }
    int id(Object p) throws ReflectiveOperationException { return ((Number)getId.invoke(p)).intValue(); }
    double y(Object p) throws ReflectiveOperationException { return ((Number)getY.invoke(p)).doubleValue(); }
    double vy(Object p) throws ReflectiveOperationException { return vectorY.getDouble(velocity.invoke(p)); }
    boolean stationary(Object p) throws ReflectiveOperationException {
        Object v = velocity.invoke(p);
        return Math.abs(vectorX.getDouble(v)) <= 1E-8 && Math.abs(vectorZ.getDouble(v)) <= 1E-8;
    }
    boolean ground(Object p) throws ReflectiveOperationException { return (Boolean)isGround.invoke(p); }
    boolean dry(Object p) throws ReflectiveOperationException {
        return !(Boolean)isWater.invoke(p) && !(Boolean)isLava.invoke(p) && !(Boolean)isClimbing.invoke(p)
                && !(Boolean)hasVehicle.invoke(p) && !flying.getBoolean(abilities.invoke(p));
    }
    boolean safe(Object p) throws ReflectiveOperationException { return hurt.getInt(p) == 0 && dry(p); }
    static void fatal(Throwable t) {
        while (t instanceof InvocationTargetException && t.getCause() != null) t=t.getCause();
        if (t instanceof VirtualMachineError e) throw e;
        if (t instanceof ThreadDeath e) throw e;
    }
}
