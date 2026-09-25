package dev.yinghuang.legacyforgebridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Consumer;

/** Generic native special-model renderer; context selection is in normal modern item model JSON. */
public final class ObjSpecialRenderer implements NoDataSpecialModelRenderer {
    private final LegacyObjModel mesh;
    private final Unbaked definition;

    private ObjSpecialRenderer(LegacyObjModel mesh, Unbaked definition) {
        this.mesh = mesh; this.definition = definition;
    }

    @Override
    public void submit(ItemDisplayContext type, PoseStack matrices, SubmitNodeCollector queue,
                       int light, int overlay, boolean hasFoil, int outlineColor) {
        matrices.pushPose();
        applyLocalTransform(matrices);
        queue.submitCustomGeometry(matrices,
                definition.translucent() ? RenderTypes.entityTranslucent(definition.texture())
                        : RenderTypes.entityCutoutNoCull(definition.texture()),
                (pose, buffer) -> mesh.render(pose, buffer, light, overlay, 1.0F, 0, 0, 0));
        matrices.popPose();
    }

    /** Rendering and GUI/model bounds use exactly the same local matrix, not different auto-fit paths. */
    private void applyLocalTransform(PoseStack matrices) {
        if (definition.centered()) matrices.translate(0.5F, 0.5F, 0.5F);
        for (Transform operation : definition.transforms()) operation.apply(matrices);
        matrices.scale(definition.scale(), definition.scale(), definition.scale());
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void getExtents(Consumer output) {
        PoseStack matrices = new PoseStack();
        applyLocalTransform(matrices);
        var bounds = mesh.bounds();
        for (float x : new float[]{bounds.minX(), bounds.maxX()})
            for (float y : new float[]{bounds.minY(), bounds.maxY()})
                for (float z : new float[]{bounds.minZ(), bounds.maxZ()})
                    output.accept(matrices.last().pose().transformPosition(new Vector3f(x, y, z)));
    }

    public record Transform(String op, List<Float> values) {
        public static final Codec<Transform> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("op").forGetter(Transform::op),
                Codec.FLOAT.listOf().fieldOf("values").forGetter(Transform::values)
        ).apply(instance, Transform::new));
        public Transform {
            values = List.copyOf(values);
            int count = switch (op) { case "translate", "scale" -> 3; case "rotate" -> 4;
                default -> throw new IllegalArgumentException("Unsupported OBJ transform " + op); };
            if (values.size() != count || values.stream().anyMatch(v -> !Float.isFinite(v)))
                throw new IllegalArgumentException("Invalid OBJ transform " + op);
            if (op.equals("rotate") && values.get(1) == 0 && values.get(2) == 0 && values.get(3) == 0)
                throw new IllegalArgumentException("OBJ rotation has a zero axis");
        }
        void apply(PoseStack matrices) {
            float a = values.get(0), b = values.get(1), c = values.get(2);
            switch (op) {
                case "translate" -> matrices.translate(a, b, c);
                case "scale" -> matrices.scale(a, b, c);
                case "rotate" -> {
                    Vector3f axis = new Vector3f(b, c, values.get(3)).normalize();
                    matrices.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(a), axis));
                }
                default -> throw new IllegalStateException(op);
            }
        }
    }

    public record Unbaked(Identifier model, Identifier texture, float scale, boolean translucent,
                          boolean centered, List<Transform> transforms) implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("model").forGetter(Unbaked::model),
                Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture),
                Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(Unbaked::scale),
                Codec.BOOL.optionalFieldOf("translucent", false).forGetter(Unbaked::translucent),
                Codec.BOOL.optionalFieldOf("centered", false).forGetter(Unbaked::centered),
                Transform.CODEC.listOf().optionalFieldOf("transforms", List.of()).forGetter(Unbaked::transforms)
        ).apply(instance, Unbaked::new));
        public Unbaked {
            transforms = List.copyOf(transforms);
            if (!Float.isFinite(scale)) throw new IllegalArgumentException("Invalid OBJ scale");
        }
        public Unbaked(Identifier model, Identifier texture, float scale, boolean translucent) {
            this(model, texture, scale, translucent, false, List.of());
        }
        @Override public SpecialModelRenderer bake(SpecialModelRenderer.BakingContext context) {
            try { return new ObjSpecialRenderer(LegacyObjModel.load(model), this); }
            catch (Exception ex) { LegacyForgeBridge.LOGGER.error("Failed to bake converted OBJ {}", model, ex); return null; }
        }
        @Override public MapCodec<Unbaked> type() { return MAP_CODEC; }
    }
}
