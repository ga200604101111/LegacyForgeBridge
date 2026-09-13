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

/** Lightweight OBJ renderer for converted 1.7.10 item models. */
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
        private final List<float[]> positions;
        private final List<float[]> texCoords;
        private final List<float[]> normals;
        private final List<Triangle> triangles;

        private ObjMesh(
                List<float[]> positions,
                List<float[]> texCoords,
                List<float[]> normals,
                List<Triangle> triangles
        ) {
            this.positions = positions;
            this.texCoords = texCoords;
            this.normals = normals;
            this.triangles = triangles;
        }

        static ObjMesh parse(List<String> lines) {
            List<float[]> positions = new ArrayList<>();
            List<float[]> texCoords = new ArrayList<>();
            List<float[]> normals = new ArrayList<>();
            List<Triangle> triangles = new ArrayList<>();

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
                        VertexRef first = ref(parts[1], positions.size(), texCoords.size(), normals.size());
                        VertexRef previous = ref(parts[2], positions.size(), texCoords.size(), normals.size());
                        for (int index = 3; index < parts.length; index++) {
                            VertexRef current = ref(parts[index], positions.size(), texCoords.size(), normals.size());
                            triangles.add(new Triangle(first, previous, current));
                            previous = current;
                        }
                    }
                    default -> {
                    }
                }
            }
            return new ObjMesh(List.copyOf(positions), List.copyOf(texCoords), List.copyOf(normals), List.copyOf(triangles));
        }

        void render(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, float scale) {
            for (Triangle triangle : triangles) {
                float[] faceNormal = computedNormal(triangle);
                emit(triangle.a, faceNormal, pose, buffer, light, overlay, scale);
                emit(triangle.b, faceNormal, pose, buffer, light, overlay, scale);
                emit(triangle.c, faceNormal, pose, buffer, light, overlay, scale);
            }
        }

        private void emit(
                VertexRef ref,
                float[] faceNormal,
                PoseStack.Pose pose,
                VertexConsumer buffer,
                int light,
                int overlay,
                float scale
        ) {
            float[] p = positions.get(ref.position);
            float[] uv = ref.texCoord >= 0 && ref.texCoord < texCoords.size()
                    ? texCoords.get(ref.texCoord)
                    : new float[]{0.0F, 0.0F};
            float[] n = ref.normal >= 0 && ref.normal < normals.size()
                    ? normals.get(ref.normal)
                    : faceNormal;
            buffer.addVertex(pose, p[0] * scale, p[1] * scale, p[2] * scale)
                    .setColor(255, 255, 255, 255)
                    .setUv(uv[0], 1.0F - uv[1])
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, n[0], n[1], n[2]);
        }

        private float[] computedNormal(Triangle triangle) {
            float[] a = positions.get(triangle.a.position);
            float[] b = positions.get(triangle.b.position);
            float[] c = positions.get(triangle.c.position);
            float ux = b[0] - a[0];
            float uy = b[1] - a[1];
            float uz = b[2] - a[2];
            float vx = c[0] - a[0];
            float vy = c[1] - a[1];
            float vz = c[2] - a[2];
            float nx = uy * vz - uz * vy;
            float ny = uz * vx - ux * vz;
            float nz = ux * vy - uy * vx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length <= 1.0E-6F) {
                return new float[]{0.0F, 1.0F, 0.0F};
            }
            return new float[]{nx / length, ny / length, nz / length};
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

        private record Triangle(VertexRef a, VertexRef b, VertexRef c) {
        }
    }
}
