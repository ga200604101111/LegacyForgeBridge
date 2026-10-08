package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * A source-only, fail-closed proof for a static Techne/ModelRenderer projectile silhouette.
 * It does not register a client renderer or promise gameplay adaptation. In particular, a
 * model's other animation entrypoints are irrelevant only when the proved renderer cannot
 * reach them from its selected projectile draw method.
 */
public final class LegacyFixedModelProjectileAnalyzer {
    private static final String RENDER = "net/minecraft/client/renderer/entity/Render";
    private static final String MODEL = "net/minecraft/client/model/ModelBase";
    private static final String PART = "net/minecraft/client/model/ModelRenderer";
    private static final String RESOURCE = "net/minecraft/util/ResourceLocation";
    private static final String GL = "org/lwjgl/opengl/GL11";
    private static final String DRAW_DESC = "(Lnet/minecraft/entity/Entity;DDDFF)V";
    private static final String PART_DESC = "L" + PART + ";";

    public record Cuboid(String name, int u, int v, float x, float y, float z,
                         int width, int height, int depth, float pivotX, float pivotY, float pivotZ) { }
    public record Proof(String rendererClass, String modelClass, String texture, int textureWidth,
                        int textureHeight, float scale, float angle, float axisX, float axisY,
                        float axisZ, List<Cuboid> cuboids) {
        public Proof { cuboids = List.copyOf(cuboids); }
    }
    public record Analysis(Optional<Proof> proof, List<String> diagnostics) {
        public Analysis { proof = Objects.requireNonNull(proof); diagnostics = List.copyOf(diagnostics); }
    }
    private record Texture(String identifier, int width, int height, String field) { }
    private record Transform(float scale, float angle, float x, float y, float z) { }
    private record Box(int u, int v, float x, float y, float z, int w, int h, int d) { }
    private record Pivot(float x, float y, float z) { }

    public Analysis analyze(Path jarPath, String rendererClass) throws IOException {
        Objects.requireNonNull(jarPath, "jarPath");
        Map<String, ClassNode> classes = load(jarPath);
        ClassNode renderer = classes.get(rendererClass);
        if (renderer == null || !RENDER.equals(renderer.superName))
            return blocked("Renderer is not source-owned with an exact vanilla Render base");
        String modelName = uniqueModel(renderer, classes);
        if (modelName == null) return blocked("Renderer does not construct one uniquely-owned ModelBase field");
        ClassNode model = classes.get(modelName);
        Texture texture = fixedTexture(jarPath, renderer);
        if (texture == null) return blocked("Renderer texture is not one fixed, present PNG resource");
        if (!fixedTextureGetter(renderer, texture.field()))
            return blocked("Renderer entity-texture accessor does not return the bound fixed texture");
        Transform transform = fixedTransform(renderer, modelName, texture.field());
        if (transform == null) return blocked("Projectile draw has an unproved call, pose, texture or transform");
        List<Cuboid> cuboids = fixedCuboids(model, texture.width(), texture.height());
        if (cuboids == null) return blocked("Projectile model draw is not a closed, fixed cuboid path");
        return new Analysis(Optional.of(new Proof(renderer.name, modelName, texture.identifier(),
                texture.width(), texture.height(), transform.scale(), transform.angle(),
                transform.x(), transform.y(), transform.z(), cuboids)), List.of());
    }

    private static Analysis blocked(String reason) {
        return new Analysis(Optional.empty(), List.of(reason));
    }

    private static String uniqueModel(ClassNode renderer, Map<String, ClassNode> classes) {
        MethodNode ctor = method(renderer, "<init>", "()V");
        if (ctor == null) return null;
        String found = null;
        for (AbstractInsnNode insn : ctor.instructions) {
            if (!(insn instanceof TypeInsnNode newType) || newType.getOpcode() != Opcodes.NEW) continue;
            if (!inherits(classes, newType.desc, MODEL)) continue;
            if (found != null) return null;
            found = newType.desc;
        }
        if (found == null) return null;
        // Require one construction and storage on this renderer: no borrowed/shared source model.
        int constructors = 0, fields = 0, baseConstructors = 0, shadows = 0;
        for (AbstractInsnNode insn : ctor.instructions) {
            if (insn instanceof MethodInsnNode call) {
                if (call.getOpcode() != Opcodes.INVOKESPECIAL || !call.name.equals("<init>")
                        || !call.desc.equals("()V")) return null;
                if (call.owner.equals(found)) constructors++;
                else if (call.owner.equals(RENDER)) baseConstructors++;
                else return null;
            }
            if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD) {
                if (field.owner.equals(renderer.name) && field.desc.equals("L" + found + ";")) fields++;
                else if (field.name.equals("shadowSize") && field.desc.equals("F")) shadows++;
                else return null;
            }
        }
        return constructors == 1 && fields == 1 && baseConstructors == 1 && shadows <= 1 ? found : null;
    }

    private static Texture fixedTexture(Path jar, ClassNode renderer) throws IOException {
        List<FieldNode> fields = new ArrayList<>();
        for (FieldNode field : renderer.fields) if ((field.access & Opcodes.ACC_STATIC) != 0
                && field.desc.equals("L" + RESOURCE + ";")) fields.add(field);
        if (fields.size() != 1) return null;
        String name = fields.getFirst().name;
        MethodNode init = method(renderer, "<clinit>", "()V");
        if (init == null) return null;
        List<AbstractInsnNode> code = real(init);
        String texture = null; int writes = 0;
        for (int i = 0; i < code.size(); i++) {
            if (!(code.get(i) instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.PUTSTATIC
                    || !field.owner.equals(renderer.name) || !field.name.equals(name)) continue;
            writes++;
            if (i < 2 || !(code.get(i - 1) instanceof MethodInsnNode call)
                    || !call.owner.equals(RESOURCE) || !call.name.equals("<init>")
                    || !call.desc.equals("(Ljava/lang/String;)V") || call.getOpcode() != Opcodes.INVOKESPECIAL)
                return null;
            if (!(code.get(i - 2) instanceof LdcInsnNode ldc) || !(ldc.cst instanceof String str)) return null;
            texture = str;
        }
        if (writes != 1 || texture == null || !texture.matches("[a-z0-9_.-]+:textures/[a-zA-Z0-9_./-]+\\.png")
                || texture.contains("..")) return null;
        int cut = texture.indexOf(':');
        String file = "assets/" + texture.substring(0, cut) + "/" + texture.substring(cut + 1);
        try (JarFile source = new JarFile(jar.toFile()); InputStream stream = open(source, file)) {
            if (stream == null) return null;
            byte[] header = stream.readNBytes(24);
            byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
            if (header.length != 24) return null;
            for (int i = 0; i < signature.length; i++) if (header[i] != signature[i]) return null;
            ByteBuffer values = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
            int width = values.getInt(16), height = values.getInt(20);
            if (width <= 0 || height <= 0 || width > 2048 || height > 2048) return null;
            return new Texture(texture, width, height, name);
        }
    }

    private static InputStream open(JarFile source, String path) throws IOException {
        JarEntry entry = source.getJarEntry(path);
        return entry == null ? null : source.getInputStream(entry);
    }

    private static boolean fixedTextureGetter(ClassNode renderer, String textureField) {
        MethodNode getter = method(renderer, "getEntityTexture",
                "(Lnet/minecraft/entity/Entity;)L" + RESOURCE + ";");
        if (getter == null) getter = method(renderer, "func_110775_a",
                "(Lnet/minecraft/entity/Entity;)L" + RESOURCE + ";");
        if (getter == null) return false;
        List<AbstractInsnNode> code = real(getter);
        return code.size() == 2 && code.getFirst() instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.GETSTATIC && field.owner.equals(renderer.name)
                && field.name.equals(textureField) && code.getLast().getOpcode() == Opcodes.ARETURN;
    }

    private static Transform fixedTransform(ClassNode renderer, String model, String textureField) {
        List<MethodNode> candidates = new ArrayList<>();
        for (MethodNode method : renderer.methods) if (method.desc.equals(DRAW_DESC)
                && (method.name.equals("doRender") || method.name.equals("func_76986_a"))
                && (method.access & (Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_ABSTRACT)) == 0)
            candidates.add(method);
        if (candidates.size() != 1) return null;
        MethodNode draw = candidates.getFirst();
        if (!draw.tryCatchBlocks.isEmpty()) return null;
        List<AbstractInsnNode> code = real(draw);
        int push = -1, translate = -1, rotate = -1, bind = -1, render = -1, pop = -1;
        float scale = Float.NaN, angle = Float.NaN, axisX = Float.NaN, axisY = Float.NaN, axisZ = Float.NaN;
        for (int i = 0; i < code.size(); i++) {
            AbstractInsnNode insn = code.get(i);
            if (insn instanceof JumpInsnNode || insn instanceof TableSwitchInsnNode
                    || insn instanceof LookupSwitchInsnNode || insn instanceof InvokeDynamicInsnNode
                    || insn.getOpcode() == Opcodes.ATHROW || insn.getOpcode() == Opcodes.PUTFIELD
                    || insn.getOpcode() == Opcodes.PUTSTATIC) return null;
            if (insn instanceof VarInsnNode var && !(var.getOpcode() == Opcodes.ALOAD && var.var == 0)
                    && !(var.getOpcode() == Opcodes.DLOAD && (var.var == 2 || var.var == 4 || var.var == 6)))
                return null;
            if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                    && (!field.owner.equals(renderer.name) || !field.desc.equals("L" + model + ";"))) return null;
            if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && (!field.owner.equals(renderer.name) || !field.name.equals(textureField))) return null;
            if (!(insn instanceof MethodInsnNode call)) continue;
            if (call.owner.equals(GL) && call.name.equals("glPushMatrix") && call.desc.equals("()V")) {
                if (push >= 0) return null; push = i;
            } else if (call.owner.equals(GL) && call.name.equals("glTranslatef") && call.desc.equals("(FFF)V")) {
                if (translate >= 0 || i < 6 || !worldTranslate(code, i)) return null; translate = i;
            } else if (call.owner.equals(GL) && call.name.equals("glRotatef") && call.desc.equals("(FFFF)V")) {
                if (rotate >= 0 || i < 4) return null;
                Float a = number(code.get(i - 4)), x = number(code.get(i - 3));
                Float y = number(code.get(i - 2)), z = number(code.get(i - 1));
                if (a == null || x == null || y == null || z == null || !Float.isFinite(a)
                        || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                        || (x == 0 && y == 0 && z == 0)) return null;
                angle = a; axisX = x; axisY = y; axisZ = z; rotate = i;
            } else if (call.name.equals("bindTexture") && call.desc.equals("(L" + RESOURCE + ";)V")
                    && (call.owner.equals(RENDER) || call.owner.equals(renderer.name))) {
                if (bind >= 0 || i < 1 || !(code.get(i - 1) instanceof FieldInsnNode field)
                        || field.getOpcode() != Opcodes.GETSTATIC || !field.owner.equals(renderer.name)
                        || !field.name.equals(textureField)) return null;
                bind = i;
            } else if (call.owner.equals(model) && call.name.equals("render") && call.desc.equals("(F)V")) {
                if (render >= 0 || i < 2 || !(code.get(i - 2) instanceof FieldInsnNode field)
                        || field.getOpcode() != Opcodes.GETFIELD || !field.owner.equals(renderer.name)
                        || !field.desc.equals("L" + model + ";")) return null;
                Float sourceScale = number(code.get(i - 1));
                if (sourceScale == null || !Float.isFinite(sourceScale) || sourceScale <= 0F || sourceScale > 1F) return null;
                scale = sourceScale; render = i;
            } else if (call.owner.equals(GL) && call.name.equals("glPopMatrix") && call.desc.equals("()V")) {
                if (pop >= 0) return null; pop = i;
            } else return null;
        }
        if (!(push >= 0 && push < translate && translate < rotate && rotate < bind && bind < render
                && render < pop && code.getLast().getOpcode() == Opcodes.RETURN)) return null;
        int returns = 0;
        for (AbstractInsnNode insn : code) if (insn.getOpcode() == Opcodes.RETURN) returns++;
        if (returns != 1) return null;
        return new Transform(scale, angle, axisX, axisY, axisZ);
    }

    private static boolean worldTranslate(List<AbstractInsnNode> code, int at) {
        for (int a = 0; a < 3; a++) {
            AbstractInsnNode source = code.get(at - 6 + a * 2);
            if (!(source instanceof VarInsnNode load) || load.getOpcode() != Opcodes.DLOAD
                    || load.var != 2 + a * 2 || code.get(at - 5 + a * 2).getOpcode() != Opcodes.D2F) return false;
        }
        return true;
    }

    private static List<Cuboid> fixedCuboids(ClassNode model, int pngWidth, int pngHeight) {
        if (model == null || !inherits(Map.of(model.name, model), model.name, MODEL)) return null;
        MethodNode ctor = method(model, "<init>", "()V"), draw = method(model, "render", "(F)V");
        if (ctor == null || draw == null || !draw.tryCatchBlocks.isEmpty()) return null;
        List<AbstractInsnNode> code = real(ctor);
        Map<String, int[]> uvs = new LinkedHashMap<>();
        Map<String, Box> boxes = new LinkedHashMap<>();
        Map<String, Pivot> pivots = new LinkedHashMap<>();
        Set<String> fields = new LinkedHashSet<>();
        for (FieldNode field : model.fields) if (PART_DESC.equals(field.desc)
                && (field.access & Opcodes.ACC_STATIC) == 0) fields.add(field.name);
        if (fields.isEmpty() || fields.size() > 64) return null;
        Integer logicalWidth = null, logicalHeight = null;
        for (int i = 0; i < code.size(); i++) {
            AbstractInsnNode insn = code.get(i);
            if (insn instanceof MethodInsnNode call) {
                boolean allowed = call.getOpcode() == Opcodes.INVOKESPECIAL
                        && call.name.equals("<init>") && call.desc.equals("()V")
                        && call.owner.equals(MODEL);
                allowed |= call.owner.equals(PART) && (call.name.equals("<init>")
                        && call.desc.equals("(L" + MODEL + ";II)V")
                        || call.name.equals("addBox")
                        && Set.of("(FFFIII)V", "(FFFIII)L" + PART + ";").contains(call.desc)
                        || call.name.equals("setRotationPoint") && call.desc.equals("(FFF)V"));
                if (!allowed) return null;
            }
            if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
                    && field.owner.equals(PART)) return null;
            if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
                    && field.desc.equals("I") && i > 0
                    && (field.name.equals("textureWidth") || field.name.equals("textureHeight"))) {
                Integer val = integer(code.get(i - 1));
                if (val == null || val < 1 || val > 2048) return null;
                if (field.name.equals("textureWidth")) { if (logicalWidth != null) return null; logicalWidth = val; }
                else { if (logicalHeight != null) return null; logicalHeight = val; }
            }
            if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && call.owner.equals(PART) && call.name.equals("<init>")
                    && call.desc.equals("(L" + MODEL + ";II)V") && i >= 2 && i + 1 < code.size()) {
                Integer u = integer(code.get(i - 2)), v = integer(code.get(i - 1));
                if (u == null || v == null || u < 0 || v < 0 || !(code.get(i + 1) instanceof FieldInsnNode field)
                        || field.getOpcode() != Opcodes.PUTFIELD || !field.owner.equals(model.name)
                        || !fields.contains(field.name) || uvs.putIfAbsent(field.name, new int[]{u, v}) != null) return null;
            }
            if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && call.owner.equals(PART) && call.name.equals("addBox")
                    && Set.of("(FFFIII)V", "(FFFIII)L" + PART + ";").contains(call.desc)) {
                if (i < 7 || !(code.get(i - 7) instanceof FieldInsnNode field)
                        || field.getOpcode() != Opcodes.GETFIELD || !field.owner.equals(model.name)
                        || !fields.contains(field.name)) return null;
                Float x = number(code.get(i - 6)), y = number(code.get(i - 5)), z = number(code.get(i - 4));
                Integer w = integer(code.get(i - 3)), h = integer(code.get(i - 2)), d = integer(code.get(i - 1));
                int[] uv = uvs.get(field.name);
                if (x == null || y == null || z == null || w == null || h == null || d == null
                        || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                        || w < 1 || h < 1 || d < 1 || uv == null || boxes.putIfAbsent(field.name,
                        new Box(uv[0], uv[1], x, y, z, w, h, d)) != null) return null;
            }
            if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && call.owner.equals(PART) && call.name.equals("setRotationPoint") && call.desc.equals("(FFF)V")) {
                if (i < 4 || !(code.get(i - 4) instanceof FieldInsnNode field)
                        || field.getOpcode() != Opcodes.GETFIELD || !field.owner.equals(model.name)
                        || !fields.contains(field.name)) return null;
                Float x = number(code.get(i - 3)), y = number(code.get(i - 2)), z = number(code.get(i - 1));
                if (x == null || y == null || z == null || !Float.isFinite(x) || !Float.isFinite(y)
                        || !Float.isFinite(z) || pivots.putIfAbsent(field.name, new Pivot(x, y, z)) != null) return null;
            }
        }
        if (logicalWidth == null || logicalHeight == null || logicalWidth != pngWidth
                || logicalHeight != pngHeight || !fields.equals(uvs.keySet())
                || !fields.equals(boxes.keySet()) || !fields.equals(pivots.keySet())) return null;
        // Constructor-defined geometry only; reject post-construction part mutators reachable from render(float).
        Set<String> rendered = new LinkedHashSet<>();
        List<AbstractInsnNode> drawCode = real(draw);
        // Exact draw closure: each part is rendered once; no hidden animation, GL calls or helpers.
        if (drawCode.size() != fields.size() * 4 + 1 || drawCode.getLast().getOpcode() != Opcodes.RETURN) return null;
        for (int i = 0; i < drawCode.size() - 1; i += 4) {
            if (!(drawCode.get(i) instanceof VarInsnNode self) || self.getOpcode() != Opcodes.ALOAD
                    || self.var != 0 || !(drawCode.get(i + 1) instanceof FieldInsnNode field)
                    || field.getOpcode() != Opcodes.GETFIELD || !field.owner.equals(model.name)
                    || !fields.contains(field.name) || !(drawCode.get(i + 2) instanceof VarInsnNode load)
                    || load.getOpcode() != Opcodes.FLOAD || load.var != 1
                    || !(drawCode.get(i + 3) instanceof MethodInsnNode call)
                    || call.getOpcode() != Opcodes.INVOKEVIRTUAL || !call.owner.equals(PART)
                    || !call.name.equals("render") || !call.desc.equals("(F)V")
                    || !rendered.add(field.name)) return null;
        }
        if (!rendered.equals(fields)) return null;
        List<Cuboid> result = new ArrayList<>();
        for (String name : fields) {
            Box box = boxes.get(name); Pivot pivot = pivots.get(name);
            result.add(new Cuboid(name, box.u(), box.v(), box.x(), box.y(), box.z(), box.w(), box.h(),
                    box.d(), pivot.x(), pivot.y(), pivot.z()));
        }
        result.sort(Comparator.comparing(Cuboid::name));
        return List.copyOf(result);
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        if (node == null) return null;
        MethodNode result = null;
        for (MethodNode method : node.methods) if (method.name.equals(name) && method.desc.equals(descriptor)) {
            if (result != null) return null;
            result = method;
        }
        return result;
    }
    private static boolean inherits(Map<String, ClassNode> classes, String owner, String base) {
        Set<String> seen = new HashSet<>();
        while (owner != null && seen.add(owner)) {
            if (owner.equals(base)) return true;
            ClassNode next = classes.get(owner); owner = next == null ? null : next.superName;
        }
        return false;
    }
    private static Float number(AbstractInsnNode insn) {
        if (insn == null) return null;
        return switch (insn.getOpcode()) {
            case Opcodes.FCONST_0 -> 0F;
            case Opcodes.FCONST_1 -> 1F;
            case Opcodes.FCONST_2 -> 2F;
            case Opcodes.LDC -> insn instanceof LdcInsnNode ldc && ldc.cst instanceof Number n ? n.floatValue() : null;
            default -> null;
        };
    }
    private static Integer integer(AbstractInsnNode insn) {
        if (insn == null) return null;
        return switch (insn.getOpcode()) {
            case Opcodes.ICONST_0 -> 0; case Opcodes.ICONST_1 -> 1;
            case Opcodes.ICONST_2 -> 2; case Opcodes.ICONST_3 -> 3;
            case Opcodes.ICONST_4 -> 4; case Opcodes.ICONST_5 -> 5;
            case Opcodes.BIPUSH, Opcodes.SIPUSH -> ((IntInsnNode) insn).operand;
            case Opcodes.LDC -> insn instanceof LdcInsnNode ldc && ldc.cst instanceof Integer value ? value : null;
            default -> null;
        };
    }
    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        if (method != null) for (AbstractInsnNode instruction : method.instructions)
            if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }
    private static Map<String, ClassNode> load(Path sourceJar) throws IOException {
        Map<String, ClassNode> result = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(sourceJar.toFile(), false)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    result.put(node.name, node);
                } catch (RuntimeException unreadable) {
                    // A malformed candidate may not become a positive proof; ignore its malformed class.
                }
            }
        }
        return result;
    }
}
