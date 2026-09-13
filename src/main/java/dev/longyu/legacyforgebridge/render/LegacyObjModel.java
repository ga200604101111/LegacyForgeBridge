package dev.longyu.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared Forge-1.7-style Wavefront mesh used by converted items and wearable renderers.
 *
 * <p>This is intentionally namespace/mod agnostic. Conversion profiles only emit model and
 * texture identifiers; all OBJ parsing, legacy V flipping, face normals and UV inset behavior live
 * here so every converted 1.7.10 mod uses the same renderer.</p>
 */
public final class LegacyObjModel {
    private static final float LEGACY_UV_OFFSET = 0.0005F;
    private static final Map<Identifier, LegacyObjModel> CACHE = new ConcurrentHashMap<>();

    private final List<float[]> positions;
    private final List<float[]> texCoords;
    private final List<Face> faces;
    private final Bounds bounds;

    private LegacyObjModel(List<float[]> positions, List<float[]> texCoords, List<Face> faces) {
        this.positions = positions;
        this.texCoords = texCoords;
        this.faces = faces;
        this.bounds = Bounds.of(positions);
    }

    public static LegacyObjModel load(Identifier model) throws IOException {
        Resource resource = Minecraft.getInstance().getResourceManager()
                .getResource(model)
                .orElseThrow(() -> new IOException("Missing converted OBJ resource " + model));
        try (BufferedReader reader = resource.openAsReader()) {
            return parse(reader.lines().toList());
        }
    }

    /** Cached path for per-frame wearable rendering. Missing models are not cached. */
    public static LegacyObjModel loadCached(Identifier model) {
        LegacyObjModel existing = CACHE.get(model);
        if (existing != null) {
            return existing;
        }
        try {
            LegacyObjModel loaded = load(model);
            CACHE.put(model, loaded);
            return loaded;
        } catch (Exception exception) {
            LegacyForgeBridge.LOGGER.error("Failed to load converted wearable OBJ {}", model, exception);
            return null;
        }
    }

    public static void clearCache() {
        CACHE.clear();
    }

    static LegacyObjModel parse(List<String> lines) {
        List<float[]> positions = new ArrayList<>();
        List<float[]> texCoords = new ArrayList<>();
        List<Face> faces = new ArrayList<>();
        int normalCount = 0;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s+");
            switch (parts[0]) {
                case "v" -> positions.add(new float[]{f(parts, 1), f(parts, 2), f(parts, 3)});
                case "vt" -> texCoords.add(new float[]{f(parts, 1), f(parts, 2)});
                case "vn" -> normalCount++;
                case "f" -> {
                    if (parts.length < 4) {
                        continue;
                    }
                    List<VertexRef> vertices = new ArrayList<>(parts.length - 1);
                    for (int index = 1; index < parts.length; index++) {
                        vertices.add(ref(parts[index], positions.size(), texCoords.size(), normalCount));
                    }
                    faces.add(new Face(List.copyOf(vertices)));
                }
                default -> {
                    // g/o/s/mtllib/usemtl do not change geometry in the single-texture bridge.
                }
            }
        }
        return new LegacyObjModel(List.copyOf(positions), List.copyOf(texCoords), List.copyOf(faces));
    }

    public Bounds bounds() {
        return bounds;
    }

    public void emitExtents(java.util.function.Consumer<org.joml.Vector3f> output, float scale) {
        for (float[] position : positions) {
            output.accept(new org.joml.Vector3f(position[0] * scale, position[1] * scale, position[2] * scale));
        }
    }

    public void render(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            int light,
            int overlay,
            float scale,
            float originX,
            float originY,
            float originZ
    ) {
        for (Face face : faces) {
            if (face.vertices.size() < 3) {
                continue;
            }
            float[] faceNormal = computedNormal(face);
            float[] averageUv = averageUv(face);
            VertexRef first = face.vertices.get(0);
            VertexRef previous = face.vertices.get(1);
            for (int index = 2; index < face.vertices.size(); index++) {
                VertexRef current = face.vertices.get(index);
                emit(first, faceNormal, averageUv, pose, buffer, light, overlay, scale, originX, originY, originZ);
                emit(previous, faceNormal, averageUv, pose, buffer, light, overlay, scale, originX, originY, originZ);
                emit(current, faceNormal, averageUv, pose, buffer, light, overlay, scale, originX, originY, originZ);
                previous = current;
            }
        }
    }

    private void emit(
            VertexRef ref,
            float[] faceNormal,
            float[] averageUv,
            PoseStack.Pose pose,
            VertexConsumer buffer,
            int light,
            int overlay,
            float scale,
            float originX,
            float originY,
            float originZ
    ) {
        if (ref.position < 0 || ref.position >= positions.size()) {
            return;
        }
        float[] position = positions.get(ref.position);
        float[] uv = legacyUv(ref, averageUv);
        buffer.addVertex(
                        pose,
                        (position[0] - originX) * scale,
                        (position[1] - originY) * scale,
                        (position[2] - originZ) * scale
                )
                .setColor(255, 255, 255, 255)
                .setUv(uv[0], uv[1])
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, faceNormal[0], faceNormal[1], faceNormal[2]);
    }

    private float[] legacyUv(VertexRef ref, float[] averageUv) {
        if (ref.texCoord < 0 || ref.texCoord >= texCoords.size()) {
            return new float[]{0.0F, 0.0F};
        }
        float[] source = texCoords.get(ref.texCoord);
        float u = source[0];
        float v = 1.0F - source[1];
        u += u > averageUv[0] ? -LEGACY_UV_OFFSET : LEGACY_UV_OFFSET;
        v += v > averageUv[1] ? -LEGACY_UV_OFFSET : LEGACY_UV_OFFSET;
        return new float[]{u, v};
    }

    private float[] averageUv(Face face) {
        float u = 0.0F;
        float v = 0.0F;
        int count = 0;
        for (VertexRef ref : face.vertices) {
            if (ref.texCoord < 0 || ref.texCoord >= texCoords.size()) {
                continue;
            }
            float[] source = texCoords.get(ref.texCoord);
            u += source[0];
            v += 1.0F - source[1];
            count++;
        }
        return count == 0 ? new float[]{0.0F, 0.0F} : new float[]{u / count, v / count};
    }

    private float[] computedNormal(Face face) {
        VertexRef originRef = face.vertices.get(0);
        if (originRef.position < 0 || originRef.position >= positions.size()) {
            return new float[]{0.0F, 1.0F, 0.0F};
        }
        float[] origin = positions.get(originRef.position);

        for (int index = 1; index + 1 < face.vertices.size(); index++) {
            VertexRef bRef = face.vertices.get(index);
            VertexRef cRef = face.vertices.get(index + 1);
            if (bRef.position < 0 || cRef.position < 0
                    || bRef.position >= positions.size() || cRef.position >= positions.size()) {
                continue;
            }
            float[] b = positions.get(bRef.position);
            float[] c = positions.get(cRef.position);
            float ux = b[0] - origin[0];
            float uy = b[1] - origin[1];
            float uz = b[2] - origin[2];
            float vx = c[0] - origin[0];
            float vy = c[1] - origin[1];
            float vz = c[2] - origin[2];
            float nx = uy * vz - uz * vy;
            float ny = uz * vx - ux * vz;
            float nz = ux * vy - uy * vx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 1.0E-6F) {
                return new float[]{nx / length, ny / length, nz / length};
            }
        }
        return new float[]{0.0F, 1.0F, 0.0F};
    }

    private static float f(String[] parts, int index) {
        return index < parts.length ? Float.parseFloat(parts[index]) : 0.0F;
    }

    private static VertexRef ref(String token, int positions, int texCoords, int normals) {
        String[] components = token.split("/", -1);
        // Resolve the normal index even though Forge 1.7's Face renderer ultimately uses one
        // computed normal per face. Doing so preserves validation/negative-index behavior.
        objIndex(components, 2, normals);
        return new VertexRef(
                objIndex(components, 0, positions),
                objIndex(components, 1, texCoords)
        );
    }

    private static int objIndex(String[] components, int component, int size) {
        if (component >= components.length || components[component].isEmpty()) {
            return -1;
        }
        int parsed = Integer.parseInt(components[component]);
        return parsed > 0 ? parsed - 1 : size + parsed;
    }

    private record VertexRef(int position, int texCoord) {
    }

    private record Face(List<VertexRef> vertices) {
    }

    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        static Bounds of(List<float[]> positions) {
            if (positions.isEmpty()) {
                return new Bounds(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
            }
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (float[] position : positions) {
                minX = Math.min(minX, position[0]);
                minY = Math.min(minY, position[1]);
                minZ = Math.min(minZ, position[2]);
                maxX = Math.max(maxX, position[0]);
                maxY = Math.max(maxY, position[1]);
                maxZ = Math.max(maxZ, position[2]);
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        public float centerX() {
            return (minX + maxX) * 0.5F;
        }

        public float centerY() {
            return (minY + maxY) * 0.5F;
        }

        public float centerZ() {
            return (minZ + maxZ) * 0.5F;
        }

        public float maxExtent() {
            return Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        }

        public Bounds include(Bounds other) {
            return new Bounds(
                    Math.min(minX, other.minX),
                    Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ),
                    Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY),
                    Math.max(maxZ, other.maxZ)
            );
        }
    }
}
