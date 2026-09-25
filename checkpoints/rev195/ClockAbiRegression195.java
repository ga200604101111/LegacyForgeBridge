import dev.yinghuang.legacyforgebridge.render.*;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyVisualEntity;
import net.minecraft.*;

/** Actual delivered wind extraction against corrected API fixtures, not a Minecraft launch. */
public final class ClockAbiRegression195 {
    private static float value(Object state, String name) throws Exception {
        var field = state.getClass().getDeclaredField(name); field.setAccessible(true);
        return field.getFloat(state);
    }
    public static void main(String[] args) throws Exception {
        if (!class_1936.class.getMethod("method_75260").isDefault()
            || class_638.class.getMethod("method_75260").getDeclaringClass() != class_1936.class)
            throw new AssertionError("Clock fixture must resolve the 1.21.11 default WorldAccess method");
        try { class_638.class.getMethod("method_8510"); throw new AssertionError("Obsolete clock exists in fixture"); }
        catch (NoSuchMethodException expected) { }
        boolean reject = args[0].equals("reject-rev194");
        var world = new class_638(); world.time = 100;
        class_310.INSTANCE.field_1687 = world;
        var entity = new ConvertedLegacyVisualEntity(); entity.id = 14;
        var state = new ConvertedLegacyVisualEntityRenderer.State();
        try {
            LegacySuspendedModelRenderer.extract(entity, state, 1F);
            if (reject) throw new AssertionError("Old wind clock was not rejected");
            var expected = new LegacyFurnitureVisualMath.Swing(14); expected.update(100);
            if (Float.floatToIntBits(value(state,"swingX")) != Float.floatToIntBits(expected.x(1F))
             || Float.floatToIntBits(value(state,"swingY")) != Float.floatToIntBits(expected.y(1F)))
                throw new AssertionError("World time was not delivered to source swing math");
            world.time = 101; expected.update(101); LegacySuspendedModelRenderer.extract(entity, state, 1F);
            if (Float.floatToIntBits(value(state,"swingX")) != Float.floatToIntBits(expected.x(1F)))
                throw new AssertionError("Changed world time was not delivered");
            class_310.INSTANCE.field_1687 = null; LegacySuspendedModelRenderer.extract(entity, state, 1F);
            if (value(state,"swingX") != 0 || value(state,"swingY") != 0) throw new AssertionError("Null world");
            System.out.println("PASS: released wind extraction resolves default WorldAccess clock; time changes and null-world reset checked");
        } catch (NoSuchMethodError error) {
            if (!reject || !error.getMessage().contains("method_8510")) throw error;
            System.out.println("PASS: old rev194 wind extraction reproduces obsolete clock NoSuchMethodError");
            System.out.println(error);
        }
    }
}
