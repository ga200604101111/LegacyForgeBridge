package dev.longyu.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Shared OBJ resource adapter. Geometry preparation is pure Java and never contains mod IDs. */
public final class LegacyObjModel {
    private static final Map<Identifier, LegacyObjModel> CACHE = new ConcurrentHashMap<>();
    private static final Set<Identifier> FAILED = ConcurrentHashMap.newKeySet();
    private final LegacyObjGeometry geometry;
    private final Bounds bounds;

    private LegacyObjModel(LegacyObjGeometry geometry) {
        this.geometry = geometry;
        this.bounds = Bounds.of(geometry.positions());
    }

    public static LegacyObjModel load(Identifier model) throws IOException {
        Resource resource = Minecraft.getInstance().getResourceManager().getResource(model)
                .orElseThrow(() -> new IOException("Missing converted OBJ resource " + model));
        try (BufferedReader reader = resource.openAsReader()) { return parse(reader.lines().toList()); }
    }

    /** Failed models are retried on resource reload, not once per frame with a repeated stack trace. */
    public static LegacyObjModel loadCached(Identifier model) {
        if (FAILED.contains(model)) return null;
        LegacyObjModel existing = CACHE.get(model);
        if (existing != null) return existing;
        try {
            LegacyObjModel loaded = load(model); CACHE.put(model, loaded); return loaded;
        } catch (Exception exception) {
            if (FAILED.add(model)) LegacyForgeBridge.LOGGER.error("Failed to load converted OBJ {}", model, exception);
            return null;
        }
    }

    public static void clearCache() { CACHE.clear(); FAILED.clear(); }
    static LegacyObjModel parse(List<String> lines) { return new LegacyObjModel(LegacyObjGeometry.parse(lines)); }
    public Bounds bounds() { return bounds; }

    public void emitExtents(java.util.function.Consumer<org.joml.Vector3f> output, float scale) {
        for (LegacyObjGeometry.Point p : geometry.positions()) {
            output.accept(new org.joml.Vector3f((float) p.x() * scale, (float) p.y() * scale, (float) p.z() * scale));
        }
    }

    /** Both supported entity RenderTypes are QUADS, including translucent sorting/index generation. */
    public void render(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay,
                       float scale, float originX, float originY, float originZ) {
        for (LegacyObjGeometry.Triangle triangle : geometry.triangles()) {
            LegacyObjGeometry.Point normal = triangle.normal();
            triangle.emitQuad(vertex -> buffer.addVertex(pose,
                            ((float) vertex.x() - originX) * scale,
                            ((float) vertex.y() - originY) * scale,
                            ((float) vertex.z() - originZ) * scale)
                    .setColor(255, 255, 255, 255)
                    .setUv((float) vertex.u(), (float) vertex.v())
                    .setOverlay(overlay).setLight(light)
                    .setNormal(pose, (float) normal.x(), (float) normal.y(), (float) normal.z()));
        }
    }

    /** Scalar sample utility only. Never apply fract independently to the vertices of a primitive. */
    static float repeatUv(float value) { return value - (float) Math.floor(value); }

    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        static Bounds of(List<LegacyObjGeometry.Point> positions) {
            if (positions.isEmpty()) return new Bounds(0, 0, 0, 0, 0, 0);
            float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
            float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
            for (LegacyObjGeometry.Point p : positions) {
                minX = Math.min(minX, (float) p.x()); minY = Math.min(minY, (float) p.y()); minZ = Math.min(minZ, (float) p.z());
                maxX = Math.max(maxX, (float) p.x()); maxY = Math.max(maxY, (float) p.y()); maxZ = Math.max(maxZ, (float) p.z());
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }
        public float centerX() { return (minX + maxX) * 0.5F; }
        public float centerY() { return (minY + maxY) * 0.5F; }
        public float centerZ() { return (minZ + maxZ) * 0.5F; }
        public float maxExtent() { return Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)); }
        public Bounds include(Bounds other) {
            return new Bounds(Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                    Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
        }
    }
}
