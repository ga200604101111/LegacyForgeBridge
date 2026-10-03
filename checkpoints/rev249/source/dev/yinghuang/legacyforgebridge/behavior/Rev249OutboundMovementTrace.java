package dev.yinghuang.legacyforgebridge.behavior;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public final class Rev249OutboundMovementTrace {
    private static final int MAX_SNAPSHOT = 160;
    private static final int MAX_RECENT = 64;
    private static final long RESPONSE_WINDOW_NS = 1_000_000_000L;
    private static final AtomicLong IDS = new AtomicLong();
    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();
    private static final ArrayDeque<Flow> RECENT = new ArrayDeque<>();
    private static final Object LOCK = new Object();

    private record Pending(long id, long ns, long jump, byte[] modern, int modernPacketId,
                           String modernCandidate, String stateHead) {}
    private record Flow(long id, long ns, long jump, int modernPacketId,
                        String modernCandidate, String legacyId, String legacyY,
                        String legacyGround, String modernGround, String modernWall) {}

    private Rev249OutboundMovementTrace() {}

    public static void outboundHead(Object userConnection, Object buffer) {
        if (!LegacyMotionTraceLog.active()) return;
        Pending old = PENDING.get();
        if (old != null) {
            LegacyMotionTraceLog.event("OUTBOUND_FLOW_TAIL_MISSING",
                    "outCorr=" + old.id + " modernPacketId=" + hexId(old.modernPacketId)
                            + " replacementHead=true diagnosticOnly=true");
            PENDING.remove();
        }
        byte[] modern = snapshot(buffer);
        if (modern == null) return;
        long id = IDS.incrementAndGet();
        PENDING.set(new Pending(id, System.nanoTime(), LegacyMotionTraceLog.jump(), modern,
                packetId(modern), modernCandidate(modern), playerState()));
    }

    public static void outboundTail(Object userConnection, Object buffer) {
        Pending p = PENDING.get();
        PENDING.remove();
        if (p == null || !LegacyMotionTraceLog.active()) return;
        byte[] legacy = snapshot(buffer);
        if (legacy == null) return;
        String decoded = LegacyMotionWireCodec.serverboundMovement(legacy);
        if (decoded == null) return;

        long now = System.nanoTime();
        String modern = enrichModernCandidate(p.modernCandidate, decoded);
        String legacyId = token(decoded, "id=", "unknown");
        String legacyY = legacyY(decoded);
        String legacyGround = token(decoded, "onGround=", "unknown");
        String modernGround = token(modern, "onGround=", "unknown");
        String modernWall = token(modern, "horizontalCollision=", "unknown");
        String tailState = playerState();

        LegacyMotionTraceLog.event("OUTBOUND_MODERN_PRE_VIA",
                "outCorr=" + p.id + " capturedHeadNs=" + p.ns + " capturedAgeUs=" + ((now - p.ns) / 1_000L)
                        + " localJumpAtHead=" + p.jump + " modernPacketId=" + hexId(p.modernPacketId)
                        + " modernBytes=" + p.modern.length + " modernRaw=" + hex(p.modern)
                        + " modernCandidate={" + modern + "} stateAtHead={" + p.stateHead + "}"
                        + " byteCaptureExact=true semanticDecodeCandidate=true gameplayMutation=false");
        LegacyMotionTraceLog.event("OUTBOUND_1710_POST_VIA",
                "outCorr=" + p.id + " transformUs=" + ((now - p.ns) / 1_000L)
                        + " localJumpAtHead=" + p.jump + " legacyBytes=" + legacy.length
                        + " legacyRaw=" + hex(legacy) + " legacyDecoded={" + decoded + "}"
                        + " stateAtReturn={" + tailState + "} final1710DecodeExactForC03Layout=true gameplayMutation=false");

        synchronized (LOCK) {
            trim(now);
            RECENT.addLast(new Flow(p.id, now, p.jump, p.modernPacketId, modern, legacyId,
                    legacyY, legacyGround, modernGround, modernWall));
            while (RECENT.size() > MAX_RECENT) RECENT.removeFirst();
        }
    }

    public static String decorate(String stage, String detail, long ns, long localJump) {
        String base = Rev247GroundEvidence.decorate(stage, detail, ns, localJump);
        if ("CAPTURE_START".equals(stage)) {
            synchronized (LOCK) { RECENT.clear(); }
            PENDING.remove();
            return base + " outboundCorrelation=rev249-modern-pre-via-to-1710-c03"
                    + " responseWindowMs=1000 responseCorrelationCausalProof=false"
                    + " outboundBehaviorChanged=false";
        }
        if (!"RAW_1710_MOTION".equals(stage)) return base;
        int entity = intToken(detail, "entity=", Integer.MIN_VALUE);
        if (entity == Integer.MIN_VALUE || !LegacyMotionTraceLog.local(entity)) return base;
        double incomingY = vectorY(detail, "decoded=");
        return base + recentTags(ns, incomingY);
    }

    private static String recentTags(long ns, double incomingY) {
        List<Flow> flows = recent(ns, 4);
        if (flows.isEmpty()) return " recentOutboundCount=0 outboundResponseCausalProof=false";
        StringBuilder out = new StringBuilder()
                .append(" recentOutboundCount=").append(flows.size())
                .append(" incomingLegacyVy=").append(incomingY)
                .append(" outboundResponseCausalProof=false");
        int i = 0;
        for (Flow f : flows) {
            i++;
            long ageUs = (ns - f.ns) / 1_000L;
            out.append(" out").append(i).append("Corr=").append(f.id)
                    .append(" out").append(i).append("AgeUs=").append(ageUs)
                    .append(" out").append(i).append("Jump=").append(f.jump)
                    .append(" out").append(i).append("ModernPid=").append(hexId(f.modernPacketId))
                    .append(" out").append(i).append("LegacyId=").append(oneToken(f.legacyId))
                    .append(" out").append(i).append("LegacyY=").append(oneToken(f.legacyY))
                    .append(" out").append(i).append("LegacyGround=").append(oneToken(f.legacyGround))
                    .append(" out").append(i).append("ModernGround=").append(oneToken(f.modernGround))
                    .append(" out").append(i).append("ModernWall=").append(oneToken(f.modernWall));
        }
        return out.toString();
    }

    private static List<Flow> recent(long ns, int max) {
        ArrayList<Flow> out = new ArrayList<>();
        synchronized (LOCK) {
            trim(ns);
            var it = RECENT.descendingIterator();
            while (it.hasNext() && out.size() < max) {
                Flow f = it.next();
                long age = ns - f.ns;
                if (age >= 0 && age <= RESPONSE_WINDOW_NS) out.add(f);
            }
        }
        return out;
    }

    private static void trim(long ns) {
        while (!RECENT.isEmpty() && (ns - RECENT.peekFirst().ns > RESPONSE_WINDOW_NS || RECENT.size() > MAX_RECENT)) {
            RECENT.removeFirst();
        }
    }

    private static String playerState() {
        try {
            Rev242ClientAccess a = Rev242ClientAccess.get();
            Object c = a.client();
            Object p = a.player.get(c);
            if (p == null) return "player=null";
            Object v = a.velocity.invoke(p);
            return "tick=" + a.age(p) + " entity=" + a.id(p) + " y=" + a.y(p)
                    + " vy=" + a.vectorY.getDouble(v) + " ground=" + a.ground(p)
                    + " verticalCollision=" + a.verticalCollision.getBoolean(p)
                    + " safe=" + a.safe(p) + " dry=" + a.dry(p);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return "stateUnavailable=" + e.getClass().getSimpleName();
        }
    }

    private static byte[] snapshot(Object buffer) {
        if (buffer == null) return null;
        try {
            int readable = ((Number) invoke0(buffer, "readableBytes")).intValue();
            if (readable < 1 || readable > MAX_SNAPSHOT) return null;
            int index = ((Number) invoke0(buffer, "readerIndex")).intValue();
            byte[] data = new byte[readable];
            Method getBytes = compatible(buffer.getClass(), "getBytes", int.class, byte[].class);
            getBytes.invoke(buffer, index, data);
            return data;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Object invoke0(Object owner, String name) throws ReflectiveOperationException {
        Method m = compatible(owner.getClass(), name);
        return m.invoke(owner);
    }

    private static Method compatible(Class<?> type, String name, Class<?>... params) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { Method m = c.getDeclaredMethod(name, params); try { m.trySetAccessible(); } catch (RuntimeException ignored) {} return m; }
            catch (NoSuchMethodException ignored) {}
        }
        Method m = type.getMethod(name, params);
        try { m.trySetAccessible(); } catch (RuntimeException ignored) {}
        return m;
    }

    private static int packetId(byte[] b) {
        if (b == null || b.length == 0) return -1;
        int value = 0, position = 0;
        for (int i = 0; i < b.length && i < 5; i++) {
            int current = b[i] & 0xFF;
            value |= (current & 0x7F) << position;
            if ((current & 0x80) == 0) return value;
            position += 7;
        }
        return -1;
    }

    private static int varintBytes(byte[] b) {
        if (b == null) return -1;
        for (int i = 0; i < b.length && i < 5; i++) if ((b[i] & 0x80) == 0) return i + 1;
        return -1;
    }

    static String modernCandidate(byte[] b) {
        int idBytes = varintBytes(b);
        if (idBytes < 1 || b.length <= idBytes) return "layout=unavailable";
        int payload = b.length - idBytes;
        try {
            ByteBuffer in = ByteBuffer.wrap(b, idBytes, payload).order(ByteOrder.BIG_ENDIAN);
            return switch (payload) {
                case 25 -> {
                    double x = in.getDouble(), y = in.getDouble(), z = in.getDouble(); int flags = in.get() & 0xFF;
                    yield candidate("pos", x, y, z, null, null, flags, payload);
                }
                case 33 -> {
                    double x = in.getDouble(), y = in.getDouble(), z = in.getDouble();
                    float yaw = in.getFloat(), pitch = in.getFloat(); int flags = in.get() & 0xFF;
                    yield candidate("pos_rot", x, y, z, yaw, pitch, flags, payload);
                }
                case 9 -> {
                    float yaw = in.getFloat(), pitch = in.getFloat(); int flags = in.get() & 0xFF;
                    yield candidate("rot", null, null, null, yaw, pitch, flags, payload);
                }
                case 1 -> {
                    int flags = in.get() & 0xFF;
                    yield candidate("status", null, null, null, null, null, flags, payload);
                }
                default -> "layout=unknown payloadBytes=" + payload;
            };
        } catch (RuntimeException e) {
            return "layout=parse_failed payloadBytes=" + payload + " error=" + e.getClass().getSimpleName();
        }
    }

    private static String enrichModernCandidate(String candidate, String legacy) {
        String legacyId = token(legacy, "id=", "unknown");
        boolean plausible = switch (legacyId) {
            case "0x3" -> candidate.contains("kind=status");
            case "0x4" -> candidate.contains("kind=pos");
            case "0x5" -> candidate.contains("kind=rot");
            case "0x6" -> candidate.contains("kind=pos_rot");
            default -> false;
        };
        return candidate + " layoutMatchesLegacyKind=" + plausible + " layoutDecodeCausalProof=false";
    }

    private static String candidate(String kind, Double x, Double y, Double z, Float yaw, Float pitch, int flags, int payload) {
        StringBuilder s = new StringBuilder("layout=1.21.x-candidate kind=").append(kind).append(" payloadBytes=").append(payload);
        if (x != null) s.append(" pos=").append(LegacyMotionTraceLog.vector(x, y, z));
        if (yaw != null) s.append(" yaw=").append(yaw).append(" pitch=").append(pitch);
        s.append(" flags=0x").append(Integer.toHexString(flags))
                .append(" onGround=").append((flags & 1) != 0)
                .append(" horizontalCollision=").append((flags & 2) != 0);
        return s.toString();
    }

    private static String legacyY(String decoded) {
        int p = decoded.indexOf("pos=[");
        if (p < 0) return "na";
        p += 5;
        int e = decoded.indexOf(']', p);
        if (e < 0) return "na";
        String[] parts = decoded.substring(p, e).split(",", -1);
        return parts.length == 3 ? parts[1] : "na";
    }

    private static double vectorY(String s, String key) {
        int p = s.indexOf(key); if (p < 0) return Double.NaN; p += key.length();
        int e = s.indexOf(']', p); if (e < 0) return Double.NaN;
        String text = s.substring(p, e + 1);
        int a = text.indexOf('['), c1 = text.indexOf(',', a + 1), c2 = text.indexOf(',', c1 + 1);
        if (a < 0 || c1 < 0 || c2 < 0) return Double.NaN;
        try { return Double.parseDouble(text.substring(c1 + 1, c2)); } catch (NumberFormatException ex) { return Double.NaN; }
    }

    private static int intToken(String s, String key, int fallback) {
        String t = token(s, key, null);
        try { return t == null ? fallback : Integer.parseInt(t); } catch (NumberFormatException ex) { return fallback; }
    }

    private static String token(String s, String key, String fallback) {
        int p = s.indexOf(key); if (p < 0) return fallback; p += key.length();
        int e = p; while (e < s.length() && !Character.isWhitespace(s.charAt(e)) && s.charAt(e) != '}') e++;
        return s.substring(p, e);
    }

    private static String oneToken(String s) {
        if (s == null) return "null";
        return s.replace(' ', '_').replace('\t', '_').replace('\r', '_').replace('\n', '_');
    }

    private static String hexId(int id) { return id < 0 ? "unknown" : "0x" + Integer.toHexString(id); }
    private static String hex(byte[] data) {
        if (data == null) return "null";
        StringBuilder b = new StringBuilder(data.length * 2);
        for (byte v : data) b.append(String.format(Locale.ROOT, "%02x", v & 0xFF));
        return b.toString();
    }

    static void resetForTest() { synchronized (LOCK) { RECENT.clear(); } PENDING.remove(); IDS.set(0L); }
    static String modernCandidateForTest(byte[] b) { return modernCandidate(b); }
    static void addFlowForTest(long id,long ns,long jump,int modernPacketId,String legacyId,String legacyY,String legacyGround,String modernGround,String modernWall) {
        synchronized (LOCK) { RECENT.addLast(new Flow(id,ns,jump,modernPacketId,"test",legacyId,legacyY,legacyGround,modernGround,modernWall)); }
    }
    static String recentTagsForTest(long ns,double incomingY) { return recentTags(ns,incomingY); }
}
