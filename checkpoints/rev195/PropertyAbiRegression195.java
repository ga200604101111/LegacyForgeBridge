/** Isolated JVM regression against documented Property API fixtures; not a Minecraft launch. */
public final class PropertyAbiRegression195 {
    public static void main(String[] args) throws Exception {
        Class<?> base = Class.forName("net.minecraft.class_2769");
        Class<?> integer = Class.forName("net.minecraft.class_2758");
        Class<?> bool = Class.forName("net.minecraft.class_2746");
        if (!base.isAssignableFrom(integer) || !base.isAssignableFrom(bool) || bool.isAssignableFrom(integer))
            throw new AssertionError("Fixture hierarchy must model sibling IntProperty/BooleanProperty");
        Class<?> block = Class.forName("net.minecraft.class_2680");
        block.getMethod("method_28498", base);
        try { block.getMethod("method_28498", bool); throw new AssertionError("Invalid BooleanProperty overload in fixture"); }
        catch (NoSuchMethodException expected) { }
        boolean mustFail = args[0].equals("reject-rev194");
        try {
            Class<?> renderer = Class.forName("dev.yinghuang.legacyforgebridge.render.ConvertedLegacyGridPotRenderer");
            renderer.getDeclaredMethods(); renderer.getDeclaredConstructors();
            if (mustFail) throw new AssertionError("Bad rev194 bytecode was not rejected");
            System.out.println("PASS: actual delivered renderer bytecode passes JVM verification with correct Property API fixtures");
        } catch (VerifyError error) {
            if (!mustFail || !error.getMessage().contains("Bad type on operand stack")
                    || !error.getMessage().contains("class_2758") || !error.getMessage().contains("class_2746")) throw error;
            System.out.println("PASS: exact old rev194 reproduces user's VerifyError (IntegerProperty cannot be BooleanProperty)");
            System.out.println(error.getMessage());
        }
    }
}
