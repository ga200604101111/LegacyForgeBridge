package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.convert.LegacyItemRenderAnalyzer;
import dev.longyu.legacyforgebridge.convert.LegacyItemRenderAnalyzer.AnimatedOperation;
import dev.longyu.legacyforgebridge.convert.LegacyItemRenderAnalyzer.EquipmentBinding;
import dev.longyu.legacyforgebridge.convert.LegacyItemRenderAnalyzer.EquipmentDraw;
import dev.longyu.legacyforgebridge.convert.LegacyItemRenderAnalyzer.Expression;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compile proven source ItemArmor/ModelBiped rendering into ordinary candidate-owned JVM code. */
public final class LegacyEquipmentRenderPass implements ConversionPass {
    public static final String MARKER = "legacyforgebridge/generated-equipment.marker";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PROGRAM = "dev/longyu/legacyforgebridge/render/LegacyEquipmentProgram";
    private static final String SINK = PROGRAM + "$Sink";
    private static final String SUPPORT = "dev/longyu/legacyforgebridge/render/SourceEquipmentSupport";
    @Override public String id() { return "source-equipment-render-codegen"; }

    @Override public void apply(ConversionContext context) throws IOException {
        var analysis = new LegacyItemRenderAnalyzer().analyzeEquipment(context.sourceJar());
        Path report = context.stagingDir().resolve("legacyforgebridge/equipment-render-analysis.json");
        Files.createDirectories(report.getParent());
        Files.writeString(report, GSON.toJson(analysis) + "\n", StandardCharsets.UTF_8);
        for (String diagnostic : analysis.diagnostics()) context.diagnostics().warning(
                "LFB-EQUIPMENT-0002", SupportLevel.MANUAL_REQUIRED, diagnostic);
        Path manifest = context.stagingDir().resolve("legacyforgebridge/converted-content.json");
        if (!Files.isRegularFile(manifest)) return;
        JsonObject root = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        if (!root.has("items")) return;
        List<JsonObject> items = new ArrayList<>();
        for (JsonElement item : root.getAsJsonArray("items")) if (item.isJsonObject()) items.add(item.getAsJsonObject());
        String owner = GeneratedModEntrypointPass.generatedClientClass(context.metadata()).replace('.', '/') + "Equipment";
        ClassWriter registry = writer(owner, null); constructor(registry);
        MethodVisitor init = registry.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "initialize", "()V", null, null);
        init.visitCode();
        int converted = 0;
        for (EquipmentBinding binding : analysis.bindings()) {
            List<JsonObject> candidates = items.stream().filter(item -> matches(item, binding.itemName())).toList();
            if (candidates.size() != 1) {
                context.diagnostics().info("LFB-EQUIPMENT-0003", SupportLevel.MANUAL_REQUIRED,
                        "No unambiguous converted item for source equipment " + binding.itemName());
                continue;
            }
            JsonObject item = candidates.getFirst();
            try {
                if (binding.armorSlot() < 0 || binding.armorSlot() > 3) throw new IllegalArgumentException("Unknown armor slot");
                validateResources(context.stagingDir(), binding.standing());
                validateResources(context.stagingDir(), binding.crouching());
                String program = owner + "$Pose" + converted;
                writeClass(context.stagingDir(), program, compileProgram(program, binding, context.stagingDir()));
                init.visitLdcInsn(item.get("id").getAsString());
                init.visitLdcInsn(binding.armorSlot());
                init.visitTypeInsn(Opcodes.NEW, program); init.visitInsn(Opcodes.DUP);
                init.visitMethodInsn(Opcodes.INVOKESPECIAL, program, "<init>", "()V", false);
                init.visitMethodInsn(Opcodes.INVOKESTATIC, SUPPORT, "register", "(Ljava/lang/String;IL" + PROGRAM + ";)V", false);
                // Avoid double ArmorRenderer registration: the generated source program replaces
                // the temporary auto-fit definition, not the item's native inventory resources.
                item.remove("equipmentRender");
                item.addProperty("sourceEquipmentProgram", program.replace('/', '.'));
                converted++;
            } catch (IOException | IllegalArgumentException exception) {
                context.diagnostics().warning("LFB-EQUIPMENT-0004", SupportLevel.MANUAL_REQUIRED,
                        binding.itemName() + ": " + exception.getMessage());
            }
        }
        init.visitInsn(Opcodes.RETURN); init.visitMaxs(0, 0); init.visitEnd(); registry.visitEnd();
        if (converted > 0) {
            writeClass(context.stagingDir(), owner, registry.toByteArray());
            Files.writeString(context.stagingDir().resolve(MARKER), owner + "\n", StandardCharsets.UTF_8);
        }
        Files.writeString(manifest, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        context.diagnostics().info("LFB-EQUIPMENT-0001", SupportLevel.ADAPTED,
                "Compiled source equipment programs=" + converted + "; authored root pivots, textures, ordered transforms and age/crouch expressions retained. Fixed-function diffuse lighting remains an approximation; lightmap is not forced fullbright.");
    }

    private static boolean matches(JsonObject item, String name) {
        if (!item.has("id")) return false;
        if (name.startsWith("item.")) name = name.substring(5);
        String id = item.get("id").getAsString();
        return name.indexOf(':') >= 0 ? id.equals(name) : id.substring(id.indexOf(':') + 1).equals(name);
    }
    private static Path resource(Path staging, String name) throws IOException {
        String id = name.toLowerCase(Locale.ROOT);
        int split = id.indexOf(':');
        if (split < 1 || id.contains("..") || id.contains("\\")) throw new IOException("Unsafe resource " + name);
        Path root = staging.resolve("assets").toAbsolutePath().normalize();
        Path path = root.resolve(id.substring(0, split)).resolve(id.substring(split + 1)).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new IOException("Missing resource " + id);
        return path;
    }
    private static void validateResources(Path staging, List<EquipmentDraw> draws) throws IOException {
        for (EquipmentDraw draw : draws) { resource(staging, draw.model()); resource(staging, draw.texture()); }
    }
    private static boolean translucent(Path staging, String texture) throws IOException {
        var image = ImageIO.read(resource(staging, texture).toFile());
        if (image == null) throw new IOException("Unreadable texture " + texture);
        if (!image.getColorModel().hasAlpha()) return false;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int alpha = image.getRGB(x,y) >>> 24;
            if (alpha > 0 && alpha < 255) return true;
        }
        return false;
    }
    private static ClassWriter writer(String owner, String[] interfaces) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                owner, null, "java/lang/Object", interfaces);
        return writer;
    }
    private static void constructor(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode(); method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        method.visitInsn(Opcodes.RETURN); method.visitMaxs(0,0); method.visitEnd();
    }
    static byte[] compileProgram(String owner, EquipmentBinding binding, Path staging) throws IOException {
        ClassWriter writer = writer(owner, new String[]{PROGRAM}); constructor(writer);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "render", "(L" + SINK + ";[FZ)V", null, null);
        method.visitCode(); Label standing = new Label(), done = new Label();
        method.visitVarInsn(Opcodes.ILOAD, 3); method.visitJumpInsn(Opcodes.IFEQ, standing);
        emitDraws(method, binding.crouching(), staging); method.visitJumpInsn(Opcodes.GOTO, done);
        method.visitLabel(standing); emitDraws(method, binding.standing(), staging);
        method.visitLabel(done); method.visitInsn(Opcodes.RETURN); method.visitMaxs(0,0); method.visitEnd();
        writer.visitEnd(); return writer.toByteArray();
    }
    private static void emitDraws(MethodVisitor method, List<EquipmentDraw> draws, Path staging) throws IOException {
        for (EquipmentDraw draw : draws) {
            method.visitVarInsn(Opcodes.ALOAD, 1); callSink(method, "push", "()V");
            for (AnimatedOperation operation : draw.operations()) {
                method.visitVarInsn(Opcodes.ALOAD, 1);
                for (Expression expression : operation.values()) emitExpression(method, expression);
                callSink(method, operation.op(), operation.op().equals("rotate") ? "(FFFF)V" : "(FFF)V");
            }
            method.visitVarInsn(Opcodes.ALOAD, 1);
            method.visitLdcInsn(draw.model().toLowerCase(Locale.ROOT));
            method.visitLdcInsn(draw.texture().toLowerCase(Locale.ROOT));
            method.visitInsn(draw.lighting() ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            method.visitInsn(draw.cull() ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            method.visitInsn(translucent(staging, draw.texture()) ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            callSink(method, "draw", "(Ljava/lang/String;Ljava/lang/String;ZZZ)V");
            method.visitVarInsn(Opcodes.ALOAD, 1); callSink(method, "pop", "()V");
        }
    }
    private static void callSink(MethodVisitor method, String name, String desc) {
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, SINK, name, desc, true);
    }
    private static void emitExpression(MethodVisitor method, Expression expression) {
        switch (expression.op()) {
            case "constant" -> method.visitLdcInsn(expression.value());
            case "input" -> {
                int index = (int)expression.value();
                if (index < 0 || index > 5) throw new IllegalArgumentException("Invalid animation input");
                method.visitVarInsn(Opcodes.ALOAD, 2); method.visitLdcInsn(index); method.visitInsn(Opcodes.FALOAD);
            }
            case "add", "sub", "mul", "div" -> {
                emitExpression(method, expression.args().get(0)); emitExpression(method, expression.args().get(1));
                method.visitInsn(switch (expression.op()) {
                    case "add" -> Opcodes.FADD; case "sub" -> Opcodes.FSUB; case "mul" -> Opcodes.FMUL; default -> Opcodes.FDIV;
                });
            }
            case "neg" -> { emitExpression(method, expression.args().getFirst()); method.visitInsn(Opcodes.FNEG); }
            case "cos", "sin" -> {
                emitExpression(method, expression.args().getFirst());
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/longyu/legacyforgebridge/render/LegacyRenderMath", expression.op(), "(F)F", false);
            }
            default -> throw new IllegalArgumentException("Unknown animation expression " + expression.op());
        }
    }
    private static void writeClass(Path staging, String owner, byte[] bytes) throws IOException {
        Path path = staging.resolve(owner + ".class"); Files.createDirectories(path.getParent()); Files.write(path, bytes);
    }
}
