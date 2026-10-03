package dev.yinghuang.lfbnativejumpdiag;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Mod(
        modid = LFBNativeJumpDiag.MODID,
        name = "LFB Native 1.7.10 Jump Diagnostic",
        version = LFBNativeJumpDiag.VERSION,
        acceptedMinecraftVersions = "[1.7.10]",
        acceptableRemoteVersions = "*",
        clientSideOnly = true
)
public final class LFBNativeJumpDiag {
    public static final String MODID = "lfbnativejumpdiag";
    public static final String VERSION = "1.0.0";
    private static final NativeLog LOG = new NativeLog();
    private static final Reflect R = new Reflect();
    private static final AtomicLong JUMPS = new AtomicLong();
    private static volatile long currentJump;
    private static volatile long lastVelocityRecvNs = -1L;
    private static volatile long lastVelocityPacket = 0L;
    private static volatile String lastVelocityVector = "none";
    private static volatile Boolean previousGround;
    private static volatile boolean initialized;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (initialized) return;
        initialized = true;
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
        LOG.notice("mod initialized version=" + VERSION + " mode=observation-only autoCapture=true commands=false");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onJumpHighest(LivingJumpEvent event) {
        Object entity = R.field(event, "entityLiving");
        if (!R.isLocalPlayer(entity)) return;
        long id = JUMPS.incrementAndGet();
        currentJump = id;
        LOG.event("JUMP_EVENT_HIGHEST", "jump=" + id + " " + R.state(entity)
                + " note=before_normal_priority_mod_handlers_when_standard_priority_order_applies");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onJumpLowest(LivingJumpEvent event) {
        Object entity = R.field(event, "entityLiving");
        if (!R.isLocalPlayer(entity)) return;
        LOG.event("JUMP_EVENT_LOWEST", "jump=" + currentJump + " " + R.state(entity)
                + " note=after_normal_priority_mod_handlers_when_standard_priority_order_applies");
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!LOG.active()) return;
        Object player = R.localPlayer();
        if (player == null) return;
        String phase = String.valueOf(R.field(event, "phase"));
        boolean ground = R.boolField(player, false, "field_70122_E", "onGround");
        String detail = "phase=" + phase + " jump=" + currentJump + " " + R.state(player)
                + " input=" + R.input(player) + " " + recentVelocity();
        LOG.event("CLIENT_TICK", detail);
        Boolean old = previousGround;
        if (old == null || old.booleanValue() != ground) {
            previousGround = Boolean.valueOf(ground);
            LOG.event(ground ? "GROUND_CONTACT" : "LEAVE_GROUND",
                    "jump=" + currentJump + " phase=" + phase + " " + R.state(player) + " " + recentVelocity());
        }
    }

    @SubscribeEvent
    public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        LOG.startSession();
        JUMPS.set(0L);
        currentJump = 0L;
        previousGround = null;
        lastVelocityRecvNs = -1L;
        lastVelocityPacket = 0L;
        lastVelocityVector = "none";
        Object manager = R.field(event, "manager");
        LOG.event("SESSION_CONNECTED", "manager=" + R.identity(manager)
                + " local=" + R.boolField(event, false, "isLocal"));
        try {
            installPipeline(manager);
        } catch (Throwable t) {
            LOG.event("PIPELINE_INSTALL_FAILED", R.throwable(t));
        }
    }

    @SubscribeEvent
    public void onDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        LOG.event("SESSION_DISCONNECTED", "jump=" + currentJump + " " + recentVelocity());
        LOG.stopSession("disconnect");
        previousGround = null;
    }

    private static String recentVelocity() {
        long ns = lastVelocityRecvNs;
        long age = ns < 0L ? -1L : System.nanoTime() - ns;
        return "lastS12=" + lastVelocityPacket + " lastS12AgeUs=" + (age < 0L ? -1L : age / 1000L)
                + " lastS12Vector=" + lastVelocityVector;
    }

    private static void installPipeline(Object manager) throws Exception {
        if (manager == null) throw new IllegalStateException("network manager is null");
        Object channel = R.invoke(manager, new String[]{"channel"});
        if (channel == null) throw new IllegalStateException("NetworkManager.channel() returned null");
        Object pipeline = R.invoke(channel, new String[]{"pipeline"});
        if (pipeline == null) throw new IllegalStateException("Channel.pipeline() returned null");
        ClassLoader loader = manager.getClass().getClassLoader();
        Class<?> inbound = Class.forName("io.netty.channel.ChannelInboundHandler", false, loader);
        Class<?> outbound = Class.forName("io.netty.channel.ChannelOutboundHandler", false, loader);
        Object handler = Proxy.newProxyInstance(loader, new Class<?>[]{inbound, outbound}, new NettyObserver());
        Object existing = null;
        try { existing = R.invoke(pipeline, new String[]{"get"}, "lfb_native1710_jumpdiag"); } catch (Throwable ignored) { }
        if (existing != null) {
            LOG.event("PIPELINE_ALREADY_INSTALLED", "pipeline=" + R.identity(pipeline));
            return;
        }
        R.invoke(pipeline, new String[]{"addBefore"}, "packet_handler", "lfb_native1710_jumpdiag", handler);
        Object names = null;
        try { names = R.invoke(pipeline, new String[]{"names"}); } catch (Throwable ignored) { }
        LOG.event("PIPELINE_INSTALLED", "pipeline=" + R.identity(pipeline) + " names=" + String.valueOf(names));
    }

    private static final class NettyObserver implements InvocationHandler {
        private final AtomicLong velocityPackets = new AtomicLong();
        private final AtomicLong movePackets = new AtomicLong();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                if ("toString".equals(name)) return "LFB-Native1710-NettyObserver";
                if ("hashCode".equals(name)) return Integer.valueOf(System.identityHashCode(proxy));
                if ("equals".equals(name)) return Boolean.valueOf(proxy == (args == null ? null : args[0]));
            }
            Object ctx = args != null && args.length > 0 ? args[0] : null;
            if ("channelRead".equals(name)) {
                Object msg = args[1];
                observeInbound(msg, velocityPackets);
                return R.invoke(ctx, new String[]{"fireChannelRead"}, msg);
            }
            if ("write".equals(name)) {
                Object msg = args[1];
                observeOutbound(msg, movePackets);
                return R.invoke(ctx, new String[]{"write"}, msg, args[2]);
            }
            if ("flush".equals(name)) return R.invoke(ctx, new String[]{"flush"});
            if ("read".equals(name)) return R.invoke(ctx, new String[]{"read"});
            if ("bind".equals(name) || "connect".equals(name) || "disconnect".equals(name)
                    || "close".equals(name) || "deregister".equals(name)) {
                Object[] tail = Arrays.copyOfRange(args, 1, args.length);
                return R.invoke(ctx, new String[]{name}, tail);
            }
            if ("channelRegistered".equals(name)) return R.invoke(ctx, new String[]{"fireChannelRegistered"});
            if ("channelUnregistered".equals(name)) return R.invoke(ctx, new String[]{"fireChannelUnregistered"});
            if ("channelActive".equals(name)) return R.invoke(ctx, new String[]{"fireChannelActive"});
            if ("channelInactive".equals(name)) return R.invoke(ctx, new String[]{"fireChannelInactive"});
            if ("channelReadComplete".equals(name)) return R.invoke(ctx, new String[]{"fireChannelReadComplete"});
            if ("channelWritabilityChanged".equals(name)) return R.invoke(ctx, new String[]{"fireChannelWritabilityChanged"});
            if ("userEventTriggered".equals(name)) return R.invoke(ctx, new String[]{"fireUserEventTriggered"}, args[1]);
            if ("exceptionCaught".equals(name)) {
                LOG.event("NETTY_EXCEPTION", R.throwable((Throwable) args[1]));
                return R.invoke(ctx, new String[]{"fireExceptionCaught"}, args[1]);
            }
            if ("handlerAdded".equals(name) || "handlerRemoved".equals(name)) return null;
            return null;
        }

        private static void observeInbound(Object packet, AtomicLong counter) {
            if (!R.isPacket(packet, "S12PacketEntityVelocity")) return;
            long n = counter.incrementAndGet();
            int entity = R.intInvoke(packet, Integer.MIN_VALUE, "func_149412_c", "getEntityID", "getEntityId");
            int sx = R.intInvoke(packet, 0, "func_149411_d", "getMotionX");
            int sy = R.intInvoke(packet, 0, "func_149410_e", "getMotionY");
            int sz = R.intInvoke(packet, 0, "func_149409_f", "getMotionZ");
            Object player = R.localPlayer();
            int localId = R.entityId(player);
            String vector = vec(sx / 8000.0D, sy / 8000.0D, sz / 8000.0D);
            if (entity == localId) {
                lastVelocityRecvNs = System.nanoTime();
                lastVelocityPacket = n;
                lastVelocityVector = vector;
            }
            LOG.event("S12_ENTITY_VELOCITY_INBOUND",
                    "packet=" + n + " jump=" + currentJump + " entity=" + entity + " localEntity=" + localId
                            + " local=" + (entity == localId) + " shortXYZ=[" + sx + "," + sy + "," + sz + "]"
                            + " decoded=" + vector + " player=" + R.state(player));
        }

        private static void observeOutbound(Object packet, AtomicLong counter) {
            if (!R.isPacket(packet, "C03PacketPlayer")) return;
            long n = counter.incrementAndGet();
            boolean ground = R.boolInvoke(packet, false, "func_149465_i", "isOnGround");
            boolean moving = R.boolInvoke(packet, false, "func_149466_j", "isMoving");
            boolean rotating = R.boolInvoke(packet, false, "func_149463_k", "isRotating");
            double x = R.doubleInvoke(packet, Double.NaN, "func_149464_c", "getPositionX");
            double y = R.doubleInvoke(packet, Double.NaN, "func_149467_d", "getPositionY");
            double stance = R.doubleInvoke(packet, Double.NaN, "func_149471_f", "getStance");
            double z = R.doubleInvoke(packet, Double.NaN, "func_149472_e", "getPositionZ");
            float yaw = R.floatInvoke(packet, Float.NaN, "func_149462_g", "getYaw");
            float pitch = R.floatInvoke(packet, Float.NaN, "func_149470_h", "getPitch");
            LOG.event("C03_PLAYER_OUTBOUND",
                    "packet=" + n + " class=" + packet.getClass().getName() + " jump=" + currentJump
                            + " moving=" + moving + " rotating=" + rotating + " onGround=" + ground
                            + " pos=[" + x + "," + y + "," + z + "] stance=" + stance
                            + " rot=[" + yaw + "," + pitch + "] player=" + R.state(R.localPlayer()) + " " + recentVelocity());
        }
    }

    private static String vec(double x, double y, double z) {
        return "[" + x + "," + y + "," + z + "]";
    }

    private static final class NativeLog {
        private static final String POISON = new String("__LFB_NATIVE1710_CLOSE__");
        private final ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<String>(32768);
        private final AtomicLong seq = new AtomicLong();
        private final AtomicLong dropped = new AtomicLong();
        private volatile BufferedWriter writer;
        private volatile Thread thread;
        private volatile File file;
        private volatile boolean accepting;
        private volatile long session;

        NativeLog() {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override public void run() { stopSession("jvm-shutdown"); }
            }, "LFB-native1710-shutdown"));
        }

        synchronized void startSession() {
            stopSession("session-replaced");
            try {
                File dir = new File(gameDir(), "logs/lfb-native1710");
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("cannot create " + dir);
                String stamp = stamp();
                file = unique(dir, "native-1710-" + stamp + ".log");
                writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8), 65536);
                accepting = true;
                session++;
                final BufferedWriter owned = writer;
                thread = new Thread(new Runnable() {
                    @Override public void run() { writerLoop(owned); }
                }, "LFB-native1710-log-writer");
                thread.setDaemon(true);
                thread.start();
                event("TRACE_START", "version=" + VERSION + " session=" + session + " file=" + file.getAbsolutePath()
                        + " java=" + System.getProperty("java.version") + " observationOnly=true autoCapture=true");
            } catch (Throwable t) {
                accepting = false;
                notice("failed to start capture: " + t);
            }
        }

        synchronized void stopSession(String reason) {
            if (writer == null) return;
            if (accepting) event("TRACE_STOP", "reason=" + reason + " dropped=" + dropped.get());
            accepting = false;
            queue.offer(POISON);
            Thread t = thread;
            if (t != null && t != Thread.currentThread()) {
                try { t.join(2500L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            writer = null;
            thread = null;
        }

        boolean active() { return accepting; }

        void event(String stage, String detail) {
            if (!accepting) return;
            long s = seq.incrementAndGet();
            String line = "seq=" + s + " ns=" + System.nanoTime() + " utc=" + isoNow()
                    + " session=" + session + " thread=" + oneLine(Thread.currentThread().getName())
                    + " stage=" + oneLine(stage) + " " + oneLine(detail);
            if (!queue.offer(line)) dropped.incrementAndGet();
        }

        void notice(String text) {
            System.out.println("[LFB Native 1.7.10 JumpDiag] " + text);
        }

        private void writerLoop(BufferedWriter owned) {
            long written = 0L;
            try {
                while (true) {
                    String line = queue.poll(500L, TimeUnit.MILLISECONDS);
                    if (line == null) { owned.flush(); continue; }
                    if (line == POISON) break;
                    owned.write(line); owned.newLine(); written++;
                    if ((written & 255L) == 0L) owned.flush();
                }
                while (true) {
                    String line = queue.poll();
                    if (line == null) break;
                    if (line == POISON) continue;
                    owned.write(line); owned.newLine(); written++;
                }
                owned.write("TRACE_END written=" + written + " dropped=" + dropped.get() + " utc=" + isoNow());
                owned.newLine(); owned.flush();
            } catch (Throwable t) {
                notice("writer failed: " + t);
            } finally {
                try { owned.close(); } catch (Throwable ignored) { }
            }
        }

        private static File gameDir() {
            try {
                Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
                Object instance = R.invokeStatic(mc, new String[]{"func_71410_x", "getMinecraft"});
                Object value = R.field(instance, "field_71412_D", "mcDataDir");
                if (value instanceof File) return (File) value;
            } catch (Throwable ignored) { }
            return new File(System.getProperty("user.dir", "."));
        }

        private static File unique(File dir, String name) {
            File f = new File(dir, name);
            int i = 2;
            while (f.exists()) f = new File(dir, name.replace(".log", "-" + (i++) + ".log"));
            return f;
        }

        private static String stamp() {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss.SSS", Locale.ROOT);
            return f.format(new Date());
        }

        private static String isoNow() {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT);
            f.setTimeZone(TimeZone.getDefault());
            return f.format(new Date());
        }

        private static String oneLine(String value) {
            if (value == null) return "null";
            return value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        }
    }

    private static final class Reflect {
        private volatile Class<?> minecraft;
        private volatile Object minecraftInstance;

        Object localPlayer() {
            try {
                Object mc = minecraftInstance();
                return field(mc, "field_71439_g", "thePlayer");
            } catch (Throwable ignored) { return null; }
        }

        boolean isLocalPlayer(Object entity) {
            Object player = localPlayer();
            return entity != null && player != null && entity == player;
        }

        Object minecraftInstance() throws Exception {
            Object value = minecraftInstance;
            if (value != null) return value;
            synchronized (this) {
                value = minecraftInstance;
                if (value != null) return value;
                if (minecraft == null) minecraft = Class.forName("net.minecraft.client.Minecraft");
                value = invokeStatic(minecraft, new String[]{"func_71410_x", "getMinecraft"});
                minecraftInstance = value;
                return value;
            }
        }

        String state(Object entity) {
            if (entity == null) return "player=null";
            try {
                return "entity=" + entityId(entity)
                        + " tick=" + intField(entity, -1, "field_70173_aa", "ticksExisted")
                        + " pos=" + vec(doubleField(entity, Double.NaN, "field_70165_t", "posX"),
                                         doubleField(entity, Double.NaN, "field_70163_u", "posY"),
                                         doubleField(entity, Double.NaN, "field_70161_v", "posZ"))
                        + " vel=" + vec(doubleField(entity, Double.NaN, "field_70159_w", "motionX"),
                                         doubleField(entity, Double.NaN, "field_70181_x", "motionY"),
                                         doubleField(entity, Double.NaN, "field_70179_y", "motionZ"))
                        + " onGround=" + boolField(entity, false, "field_70122_E", "onGround")
                        + " collidedH=" + boolField(entity, false, "field_70123_F", "isCollidedHorizontally")
                        + " collidedV=" + boolField(entity, false, "field_70124_G", "isCollidedVertically")
                        + " velocityChanged=" + boolField(entity, false, "field_70133_I", "velocityChanged")
                        + " fallDistance=" + floatField(entity, Float.NaN, "field_70143_R", "fallDistance")
                        + " box=" + String.valueOf(field(entity, "field_70121_D", "boundingBox"));
            } catch (Throwable t) {
                return "entity=" + identity(entity) + " stateError=" + t.getClass().getSimpleName();
            }
        }

        String input(Object player) {
            if (player == null) return "null";
            try {
                Object in = field(player, "field_71158_b", "movementInput");
                if (in == null) return "null";
                return "jump=" + boolField(in, false, "field_78901_c", "jump")
                        + ",sneak=" + boolField(in, false, "field_78899_d", "sneak")
                        + ",forward=" + floatField(in, Float.NaN, "field_78900_b", "moveForward")
                        + ",strafe=" + floatField(in, Float.NaN, "field_78902_a", "moveStrafe");
            } catch (Throwable t) { return "error:" + t.getClass().getSimpleName(); }
        }

        int entityId(Object entity) {
            if (entity == null) return Integer.MIN_VALUE;
            return intInvoke(entity, Integer.MIN_VALUE, "func_145782_y", "getEntityId");
        }

        boolean isPacket(Object packet, String baseSimpleName) {
            if (packet == null) return false;
            for (Class<?> c = packet.getClass(); c != null; c = c.getSuperclass()) {
                if (baseSimpleName.equals(c.getSimpleName()) || c.getName().endsWith("." + baseSimpleName)) return true;
            }
            return false;
        }

        String identity(Object o) {
            return o == null ? "null" : o.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(o));
        }

        String throwable(Throwable t) {
            if (t == null) return "null";
            StringBuilder b = new StringBuilder(t.getClass().getName()).append(':').append(String.valueOf(t.getMessage()));
            StackTraceElement[] stack = t.getStackTrace();
            for (int i = 0; i < stack.length && i < 6; i++) b.append(" <- ").append(stack[i]);
            return b.toString();
        }

        Object field(Object owner, String... names) {
            if (owner == null) return null;
            Class<?> c = owner.getClass();
            for (String name : names) {
                for (Class<?> k = c; k != null; k = k.getSuperclass()) {
                    try {
                        Field f = k.getDeclaredField(name); f.setAccessible(true); return f.get(owner);
                    } catch (NoSuchFieldException ignored) { }
                    catch (Throwable t) { throw new RuntimeException(t); }
                }
            }
            return null;
        }

        boolean boolField(Object o, boolean d, String... n) { Object v = field(o, n); return v instanceof Boolean ? ((Boolean) v).booleanValue() : d; }
        int intField(Object o, int d, String... n) { Object v = field(o, n); return v instanceof Number ? ((Number) v).intValue() : d; }
        double doubleField(Object o, double d, String... n) { Object v = field(o, n); return v instanceof Number ? ((Number) v).doubleValue() : d; }
        float floatField(Object o, float d, String... n) { Object v = field(o, n); return v instanceof Number ? ((Number) v).floatValue() : d; }

        Object invoke(Object owner, String[] names, Object... args) throws Exception {
            if (owner == null) throw new NullPointerException("invoke owner");
            Method m = findCompatible(owner.getClass(), names, args, false);
            if (m == null) throw new NoSuchMethodException(owner.getClass() + " " + Arrays.toString(names) + " argc=" + args.length);
            m.setAccessible(true);
            return m.invoke(owner, args);
        }

        Object invokeStatic(Class<?> owner, String[] names, Object... args) throws Exception {
            Method m = findCompatible(owner, names, args, true);
            if (m == null) throw new NoSuchMethodException(owner + " " + Arrays.toString(names));
            m.setAccessible(true);
            return m.invoke(null, args);
        }

        int intInvoke(Object o, int d, String... names) { try { Object v = invoke(o, names); return v instanceof Number ? ((Number) v).intValue() : d; } catch (Throwable t) { return d; } }
        boolean boolInvoke(Object o, boolean d, String... names) { try { Object v = invoke(o, names); return v instanceof Boolean ? ((Boolean) v).booleanValue() : d; } catch (Throwable t) { return d; } }
        double doubleInvoke(Object o, double d, String... names) { try { Object v = invoke(o, names); return v instanceof Number ? ((Number) v).doubleValue() : d; } catch (Throwable t) { return d; } }
        float floatInvoke(Object o, float d, String... names) { try { Object v = invoke(o, names); return v instanceof Number ? ((Number) v).floatValue() : d; } catch (Throwable t) { return d; } }

        private Method findCompatible(Class<?> type, String[] names, Object[] args, boolean requireStatic) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                Method[] methods = c.getDeclaredMethods();
                for (String name : names) for (Method m : methods) {
                    if (!m.getName().equals(name)) continue;
                    if (requireStatic != java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length != args.length) continue;
                    boolean ok = true;
                    for (int i = 0; i < p.length; i++) if (args[i] != null && !wrap(p[i]).isAssignableFrom(args[i].getClass())) { ok = false; break; }
                    if (ok) return m;
                }
            }
            for (Method m : type.getMethods()) {
                boolean named = false; for (String name : names) if (m.getName().equals(name)) named = true;
                if (!named || requireStatic != java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                Class<?>[] p = m.getParameterTypes(); if (p.length != args.length) continue;
                boolean ok = true;
                for (int i = 0; i < p.length; i++) if (args[i] != null && !wrap(p[i]).isAssignableFrom(args[i].getClass())) { ok = false; break; }
                if (ok) return m;
            }
            return null;
        }

        private Class<?> wrap(Class<?> c) {
            if (!c.isPrimitive()) return c;
            if (c == boolean.class) return Boolean.class; if (c == byte.class) return Byte.class; if (c == short.class) return Short.class;
            if (c == int.class) return Integer.class; if (c == long.class) return Long.class; if (c == float.class) return Float.class;
            if (c == double.class) return Double.class; if (c == char.class) return Character.class; return c;
        }
    }
}
