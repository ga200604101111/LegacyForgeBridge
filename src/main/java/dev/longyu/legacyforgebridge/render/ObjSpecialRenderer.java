package dev.longyu.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lightweight OBJ renderer for converted 1.7.10 item models.
 *
 * <p>The geometry path intentionally follows the important Forge 1.7.10 Wavefront semantics:
 * V coordinates are flipped, one face normal is used for the whole polygon, and UVs are nudged
 * 0.0005 toward the face average to avoid atlas-edge bleeding. Polygon faces are kept intact until
 * submission so triangulation does not create a different UV center on the diagonal.</p>
 */
public final class ObjSpecialRenderer implements NoDataSpecialModelRenderer {
    private final ObjMesh mesh;
    private final Identifier texture;
    private final float scale;

    private ObjSpecialRenderer(ObjMesh mesh, Identifier texture, float scale) {
        this.mesh = mesh;
        this.texture = texture;
        this.scale = scale;
    }

    @Override
    public void submit(
            ItemDisplayContext type,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            int overlayCoords,
            boolean hasFoil,
            int outlineColor
    ) {
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityCutoutNoCull(texture),
                (pose, buffer) -> mesh.render(pose, buffer, lightCoords, overlayCoords, scale)
        );
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void getExtents(Consumer output) {
        for (float[] position : mesh.positions) {
            output.accept(new Vector3f(position[0] * scale, position[1] * scale, position[2] * scale));
        }
    }

    public record Unbaked(Identifier model, Identifier texture, float scale) implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("model").forGetter(Unbaked::model),
                Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture),
                Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(Unbaked::scale)
        ).apply(instance, Unbaked::new));

        @Override
        public SpecialModelRenderer bake(SpecialModelRenderer.BakingContext context) {
            try {
                Resource resource = Minecraft.getInstance().getResourceManager()
                        .getResource(model)
                        .orElseThrow(() -> new IOException("Missing converted OBJ resource " + model));
                try (BufferedReader reader = resource.openAsReader()) {
                    return new ObjSpecialRenderer(ObjMesh.parse(reader.lines().toList()), texture, scale);
                }
            } catch (Exception exception) {
                LegacyForgeBridge.LOGGER.error("Failed to bake converted OBJ model {}", model, exception);
                return null;
            }
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }

    private static final class ObjMesh {
        private static final float LEGACY_UV_OFFSET = 0.0005F;

        private final List<float[]> positions;
        private final List<float[]> texCoords;
        private final List<float[]> normals;
        private final List<Face> faces;

        private ObjMesh(
                List<float[]> positions,
                List<float[]> texCoords,
                List<float[]> normals,
                List<Face> faces
        ) {
            this.positions = positions;
            this.texCoords = texCoords;
            this.normals = normals;
            this.faces = faces;
        }

        static ObjMesh parse(List<String> lines) {
            List<float[]> positions = new ArrayList<>();
            List<float[]> texCoords = new ArrayList<>();
            List<float[]> normals = new ArrayList<>();
            List<Face> faces = new ArrayList<>();

            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "v" -> positions.add(new float[]{f(parts, 1), f(parts, 2), f(parts, 3)});
                    case "vt" -> texCoords.add(new float[]{f(parts, 1), f(parts, 2)});
                    case "vn" -> normals.add(new float[]{f(parts, 1), f(parts, 2), f(parts, 3)});
                    case "f" -> {
                        if (parts.length < 4) {
                            continue;
                        }
                        List<VertexRef> vertices = new ArrayList<>(parts.length - 1);
                        for (int index = 1; index < parts.length; index++) {
                            vertices.add(ref(parts[index], positions.size(), texCoords.size(), normals.size()));
                        }
                        faces.add(new Face(List.copyOf(vertices)));
                    }
                    default -> {
                        // o/g/s/mtllib/usemtl do not alter geometry for this single-texture bridge.
                    }
                }
            }
            return new ObjMesh(
                    List.copyOf(positions),
                    List.copyOf(texCoords),
                    List.copyOf(normals),
                    List.copyOf(faces)
            );
        }

        void render(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, float scale) {
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
                    emit(first, faceNormal, averageUv, pose, buffer, light, overlay, scale);
                    emit(previous, faceNormal, averageUv, pose, buffer, light, overlay, scale);
                    emit(current, faceNormal, averageUv, pose, buffer, light, overlay, scale);
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
                float scale
        ) {
            if (ref.position < 0 || ref.position >= positions.size()) {
                return;
            }
            float[] position = positions.get(ref.position);
            float[] uv = legacyUv(ref, averageUv);
            buffer.addVertex(pose, position[0] * scale, position[1] * scale, position[2] * scale)
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
            return new VertexRef(
                    objIndex(components, 0, positions),
                    objIndex(components, 1, texCoords),
                    objIndex(components, 2, normals)
            );
        }

        private static int objIndex(String[] components, int component, int size) {
            if (component >= components.length || components[component].isEmpty()) {
                return -1;
            }
            int parsed = Integer.parseInt(components[component]);
            return parsed > 0 ? parsed - 1 : size + parsed;
        }

        private record VertexRef(int position, int texCoord, int normal) {
        }

        private record Face(List<VertexRef> vertices) {
        }
    }
}
