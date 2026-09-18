package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Conservative source-constructor proof for converted single-input processor blocks.
 *
 * <p>The proof is intentionally narrower than general Block conversion. It accepts a source-owned
 * no-arg constructor chain ending at the vanilla 1.7.10 BlockContainer(Material) constructor and
 * only constructor-local Block property setters that have an exact modern Properties equivalent.
 * Allocation-site fluent setters are deliberately outside this proof and remain a separate source
 * allocation retirement gate.</p>
 */
public final class LegacySingleInputProcessorBlockConstructionAnalyzer {
    private static final String BLOCK = "net/minecraft/block/Block";
    private static final String BLOCK_CONTAINER = "net/minecraft/block/BlockContainer";
    private static final String MATERIAL = "net/minecraft/block/material/Material";
    private static final String MATERIAL_DESC = "L" + MATERIAL + ";";
    private static final String SOUND_TYPE = "net/minecraft/block/Block$SoundType";
    private static final String SOUND_DESC = "L" + SOUND_TYPE + ";";

    public record Properties(
            float destroyTime,
            float explosionResistance,
            String soundType,
            String mapColor,
            int lightLevel) { }

    public record Proof(
            String registryName,
            String sourceBlockClass,
            boolean constructorPresent,
            boolean constructorChainComplete,
            boolean constructorControlFlowSimple,
            boolean materialSemanticsProven,
            boolean propertyEffectsSupported,
            boolean noAdditionalEffects,
            boolean replacementProofComplete,
            String materialOwner,
            String materialField,
            String materialDescriptor,
            List<String> constructorChain,
            Properties properties,
            List<String> blockers) {
        public Proof {
            constructorChain = List.copyOf(constructorChain);
            blockers = List.copyOf(blockers);
        }
    }

    public record Analysis(List<Proof> proofs, List<String> diagnostics) {
        public Analysis {
            proofs = List.copyOf(proofs);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record MaterialRef(String owner, String field, String descriptor) { }

    private static final class State {
        float hardness;
        float internalResistance;
        String soundType = "STONE";
        String mapColor = "STONE";
        int lightLevel;

        void hardness(float value) {
            hardness = value;
            float minimumResistance = value * 5.0F;
            if (internalResistance < minimumResistance) {
                internalResistance = minimumResistance;
            }
        }

        void resistance(float value) {
            internalResistance = value * 3.0F;
        }

        Properties finish() {
            return new Properties(
                    hardness,
                    internalResistance / 5.0F,
                    soundType,
                    mapColor,
                    lightLevel);
        }
    }

    private record Chain(
            List<String> constructors,
            boolean complete,
            boolean simple,
            boolean materialProven,
            boolean propertiesSupported,
            boolean noAdditionalEffects,
            MaterialRef material,
            State state,
            List<String> blockers) { }

    private final Map<String, ClassNode> classes = new LinkedHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    public Analysis analyze(Path sourceJar) throws IOException {
        classes.clear();
        diagnostics.clear();
        load(sourceJar);

        LegacySingleInputProcessorAnalyzer.Analysis processors =
                new LegacySingleInputProcessorAnalyzer().analyze(sourceJar);
        diagnostics.addAll(processors.diagnostics());

        List<Proof> proofs = new ArrayList<>();
        for (LegacySingleInputProcessorAnalyzer.Rule rule : processors.rules()) {
            proofs.add(prove(rule));
        }
        return new Analysis(
                List.copyOf(proofs),
                List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    public Proof prove(
            Path sourceJar,
            LegacySingleInputProcessorAnalyzer.Rule rule) throws IOException {
        classes.clear();
        diagnostics.clear();
        load(sourceJar);
        return prove(rule);
    }

    private Proof prove(LegacySingleInputProcessorAnalyzer.Rule rule) {
        ClassNode block = classes.get(rule.sourceBlockClass());
        MethodNode constructor = findConstructor(block, "()V");
        if (block == null || constructor == null) {
            return new Proof(
                    rule.registryName(),
                    rule.sourceBlockClass(),
                    false, false, false, false, false, false, false,
                    null, null, null,
                    List.of(), null,
                    List.of("source-block-noarg-constructor-missing"));
        }

        Chain chain = collect(rule.sourceBlockClass(), new LinkedHashSet<>());
        LinkedHashSet<String> blockers = new LinkedHashSet<>(chain.blockers());
        if (!chain.complete()) {
            blockers.add("source-block-constructor-chain-incomplete");
        }
        if (!chain.simple()) {
            blockers.add("source-block-constructor-control-flow-not-simple");
        }
        if (!chain.materialProven()) {
            blockers.add("source-block-material-semantics-not-proven");
        }
        if (!chain.propertiesSupported()) {
            blockers.add("source-block-constructor-property-effect-unsupported");
        }
        if (!chain.noAdditionalEffects()) {
            blockers.add("source-block-constructor-additional-effect");
        }

        boolean complete = chain.complete()
                && chain.simple()
                && chain.materialProven()
                && chain.propertiesSupported()
                && chain.noAdditionalEffects()
                && blockers.isEmpty();
        MaterialRef material = chain.material();
        Properties properties = complete ? chain.state().finish() : null;

        return new Proof(
                rule.registryName(),
                rule.sourceBlockClass(),
                true,
                chain.complete(),
                chain.simple(),
                chain.materialProven(),
                chain.propertiesSupported(),
                chain.noAdditionalEffects(),
                complete,
                material == null ? null : material.owner(),
                material == null ? null : material.field(),
                material == null ? null : material.descriptor(),
                chain.constructors(),
                properties,
                List.copyOf(blockers));
    }

    private Chain collect(String ownerName, Set<String> visiting) {
        if (!visiting.add(ownerName)) {
            return failed("recursive-source-block-constructor-chain:" + ownerName);
        }

        ClassNode owner = classes.get(ownerName);
        MethodNode constructor = findConstructor(owner, "()V");
        if (owner == null || constructor == null) {
            visiting.remove(ownerName);
            return failed("source-block-constructor-missing:" + ownerName);
        }

        boolean simple = simpleControlFlow(constructor);
        List<AbstractInsnNode> code = real(constructor);
        List<String> blockers = new ArrayList<>();
        List<String> constructors = new ArrayList<>();
        constructors.add(ownerName + "()V");

        State local = new State();
        boolean complete = false;
        boolean materialProven = false;
        boolean propertiesSupported = true;
        boolean noAdditionalEffects = true;
        boolean delegated = false;
        String sourceParent = null;
        MaterialRef material = null;

        for (int index = 0; index < code.size();) {
            AbstractInsnNode instruction = code.get(index);

            if (instruction.getOpcode() == Opcodes.NOP
                    || instruction.getOpcode() == Opcodes.RETURN) {
                index++;
                continue;
            }

            if (index + 1 < code.size()
                    && aload0(code.get(index))
                    && code.get(index + 1) instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && "<init>".equals(call.name)
                    && "()V".equals(call.desc)
                    && classes.containsKey(call.owner)) {
                if (delegated) {
                    blockers.add("multiple-source-block-constructor-delegations:" + ownerName);
                    noAdditionalEffects = false;
                } else if (!call.owner.equals(owner.superName)) {
                    blockers.add("unexpected-source-block-constructor-owner:" + call.owner);
                    noAdditionalEffects = false;
                } else {
                    sourceParent = call.owner;
                    delegated = true;
                }
                index += 2;
                continue;
            }

            if (index + 2 < code.size()
                    && aload0(code.get(index))
                    && code.get(index + 1) instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.GETSTATIC
                    && MATERIAL_DESC.equals(field.desc)
                    && code.get(index + 2) instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && "<init>".equals(call.name)
                    && BLOCK_CONTAINER.equals(call.owner)
                    && ("(" + MATERIAL_DESC + ")V").equals(call.desc)) {
                if (delegated) {
                    blockers.add("multiple-source-block-constructor-delegations:" + ownerName);
                    noAdditionalEffects = false;
                } else if (!BLOCK_CONTAINER.equals(owner.superName)) {
                    blockers.add("unexpected-block-container-constructor-owner:" + owner.superName);
                    noAdditionalEffects = false;
                } else {
                    delegated = true;
                    complete = true;
                    material = new MaterialRef(field.owner, field.name, field.desc);
                    materialProven = stoneMaterial(field);
                    if (!materialProven) {
                        blockers.add("unsupported-source-block-material:"
                                + field.owner + "." + field.name + field.desc);
                    }
                }
                index += 3;
                continue;
            }

            PropertyMatch property = property(code, index);
            if (property != null) {
                if (property.blocker() != null) {
                    blockers.add(property.blocker());
                    propertiesSupported = false;
                } else {
                    apply(local, property);
                }
                index = property.nextIndex();
                continue;
            }

            if (instruction instanceof MethodInsnNode call) {
                blockers.add("source-block-constructor-method-call:"
                        + call.owner + "." + call.name + call.desc);
            } else if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.PUTSTATIC) {
                blockers.add("source-block-constructor-static-write:"
                        + field.owner + "." + field.name + field.desc);
            } else {
                blockers.add("unsupported-source-block-constructor-opcode:"
                        + ownerName + ":" + instruction.getOpcode());
            }
            noAdditionalEffects = false;
            index++;
        }

        if (!delegated) {
            blockers.add("source-block-constructor-super-call-missing:" + ownerName);
            complete = false;
        }

        State state = local;
        if (sourceParent != null) {
            Chain parent = collect(sourceParent, visiting);
            constructors.addAll(parent.constructors());
            complete = parent.complete();
            simple &= parent.simple();
            materialProven = parent.materialProven();
            propertiesSupported &= parent.propertiesSupported();
            noAdditionalEffects &= parent.noAdditionalEffects();
            material = parent.material();
            blockers.addAll(parent.blockers());

            // Parent effects happen before child effects. Replay the child-local property stream
            // on top of the parent result so setHardness/setResistance ordering stays exact.
            State combined = copy(parent.state());
            replayProperties(code, combined, blockers);
            state = combined;
        }

        visiting.remove(ownerName);
        return new Chain(
                List.copyOf(constructors),
                complete,
                simple,
                materialProven,
                propertiesSupported,
                noAdditionalEffects,
                material,
                state,
                List.copyOf(blockers));
    }

    private record PropertyMatch(
            String kind,
            Float floatValue,
            Integer intValue,
            String textValue,
            int nextIndex,
            String blocker) { }

    private static PropertyMatch property(List<AbstractInsnNode> code, int index) {
        if (!aload0(code.get(index)) || index + 2 >= code.size()) return null;
        AbstractInsnNode argument = code.get(index + 1);
        AbstractInsnNode rawCall = code.get(index + 2);
        if (!(rawCall instanceof MethodInsnNode call)
                || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                || !blockSetterOwner(call.owner)) {
            return null;
        }

        int next = index + 3;
        if (next < code.size() && code.get(next).getOpcode() == Opcodes.POP) {
            next++;
        }

        if (method(call, "setHardness", "func_149711_c", "(F)Lnet/minecraft/block/Block;")) {
            Float value = floatConstant(argument);
            return value == null
                    ? new PropertyMatch("hardness", null, null, null, next,
                    "source-block-hardness-not-constant")
                    : new PropertyMatch("hardness", value, null, null, next, null);
        }
        if (method(call, "setResistance", "func_149752_b", "(F)Lnet/minecraft/block/Block;")) {
            Float value = floatConstant(argument);
            return value == null
                    ? new PropertyMatch("resistance", null, null, null, next,
                    "source-block-resistance-not-constant")
                    : new PropertyMatch("resistance", value, null, null, next, null);
        }
        if (method(call, "setLightLevel", "func_149715_a", "(F)Lnet/minecraft/block/Block;")) {
            Float value = floatConstant(argument);
            if (value == null || !Float.isFinite(value) || value < 0.0F || value > 1.0F) {
                return new PropertyMatch("light", value, null, null, next,
                        "source-block-light-level-not-supported");
            }
            return new PropertyMatch("light", value, null, null, next, null);
        }
        if (method(call, "setStepSound", "setSoundType", "func_149672_a", "(" + SOUND_DESC + ")Lnet/minecraft/block/Block;")) {
            if (!(argument instanceof FieldInsnNode field)
                    || field.getOpcode() != Opcodes.GETSTATIC
                    || !SOUND_DESC.equals(field.desc)) {
                return new PropertyMatch("sound", null, null, null, next,
                        "source-block-sound-type-not-static");
            }
            String sound = sound(field);
            return sound == null
                    ? new PropertyMatch("sound", null, null, null, next,
                    "source-block-sound-type-unsupported:"
                            + field.owner + "." + field.name)
                    : new PropertyMatch("sound", null, null, sound, next, null);
        }

        if (methodName(call, "setLightOpacity", "func_149713_g")
                || methodName(call, "setTickRandomly", "func_149675_a")
                || methodName(call, "setBlockBounds", "func_149676_a")
                || methodName(call, "setBlockUnbreakable", "func_149722_s")
                || methodName(call, "setCreativeTab", "func_149647_a")
                || methodName(call, "setBlockName", "setUnlocalizedName", "func_149663_c")
                || methodName(call, "setBlockTextureName", "func_149658_d")) {
            return new PropertyMatch("unsupported", null, null, null, next,
                    "source-block-constructor-setter-not-yet-mapped:"
                            + call.owner + "." + call.name + call.desc);
        }
        return null;
    }

    private static void apply(State state, PropertyMatch property) {
        switch (property.kind()) {
            case "hardness" -> state.hardness(property.floatValue());
            case "resistance" -> state.resistance(property.floatValue());
            case "light" ->
                    state.lightLevel = (int) (15.0F * property.floatValue());
            case "sound" -> state.soundType = property.textValue();
            default -> throw new IllegalArgumentException(
                    "Unsupported proven block property " + property.kind());
        }
    }

    private static void replayProperties(
            List<AbstractInsnNode> code,
            State state,
            List<String> blockers) {
        for (int index = 0; index < code.size();) {
            PropertyMatch property = property(code, index);
            if (property == null) {
                index++;
                continue;
            }
            if (property.blocker() == null) {
                apply(state, property);
            } else if (!blockers.contains(property.blocker())) {
                blockers.add(property.blocker());
            }
            index = property.nextIndex();
        }
    }

    private static State copy(State original) {
        State copy = new State();
        copy.hardness = original.hardness;
        copy.internalResistance = original.internalResistance;
        copy.soundType = original.soundType;
        copy.mapColor = original.mapColor;
        copy.lightLevel = original.lightLevel;
        return copy;
    }

    private static boolean stoneMaterial(FieldInsnNode field) {
        return MATERIAL.equals(field.owner)
                && MATERIAL_DESC.equals(field.desc)
                && ("field_151576_e".equals(field.name) || "rock".equals(field.name));
    }

    private static String sound(FieldInsnNode field) {
        if (!BLOCK.equals(field.owner)) return null;
        return switch (field.name) {
            case "field_149769_e", "soundTypeStone" -> "STONE";
            case "field_149766_f", "soundTypeWood" -> "WOOD";
            case "field_149767_g", "soundTypeGravel" -> "GRAVEL";
            case "field_149779_h", "soundTypeGrass" -> "GRASS";
            case "field_149780_i", "soundTypePiston" -> "STONE";
            case "field_149777_j", "soundTypeMetal" -> "METAL";
            case "field_149778_k", "soundTypeGlass" -> "GLASS";
            case "field_149775_l", "soundTypeCloth" -> "WOOL";
            case "field_149776_m", "soundTypeSand" -> "SAND";
            case "field_149773_n", "soundTypeSnow" -> "SNOW";
            case "field_149774_o", "soundTypeLadder" -> "LADDER";
            case "field_149788_p", "soundTypeAnvil" -> "ANVIL";
            default -> null;
        };
    }

    private static boolean blockSetterOwner(String owner) {
        return owner != null
                && (BLOCK.equals(owner)
                || BLOCK_CONTAINER.equals(owner)
                || !owner.startsWith("net/minecraft/"));
    }

    private static boolean method(
            MethodInsnNode call,
            String deobfuscated,
            String srg,
            String descriptor) {
        return (deobfuscated.equals(call.name) || srg.equals(call.name))
                && descriptor.equals(call.desc);
    }

    private static boolean method(
            MethodInsnNode call,
            String deobfuscatedA,
            String deobfuscatedB,
            String srg,
            String descriptor) {
        return (deobfuscatedA.equals(call.name)
                || deobfuscatedB.equals(call.name)
                || srg.equals(call.name))
                && descriptor.equals(call.desc);
    }

    private static boolean methodName(MethodInsnNode call, String... names) {
        for (String name : names) {
            if (name.equals(call.name)) return true;
        }
        return false;
    }

    private static Float floatConstant(AbstractInsnNode instruction) {
        if (instruction instanceof InsnNode insn) {
            return switch (insn.getOpcode()) {
                case Opcodes.FCONST_0 -> 0.0F;
                case Opcodes.FCONST_1 -> 1.0F;
                case Opcodes.FCONST_2 -> 2.0F;
                default -> null;
            };
        }
        if (instruction instanceof LdcInsnNode ldc
                && ldc.cst instanceof Number number) {
            return number.floatValue();
        }
        return null;
    }

    private static boolean simpleControlFlow(MethodNode method) {
        if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) {
            return false;
        }
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof JumpInsnNode
                    || instruction instanceof LookupSwitchInsnNode
                    || instruction instanceof TableSwitchInsnNode) {
                return false;
            }
        }
        return true;
    }

    private static MethodNode findConstructor(ClassNode owner, String descriptor) {
        if (owner == null) return null;
        for (MethodNode method : owner.methods) {
            if ("<init>".equals(method.name) && descriptor.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> output = new ArrayList<>();
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof LabelNode
                    || instruction instanceof LineNumberNode
                    || instruction instanceof FrameNode) {
                continue;
            }
            output.add(instruction);
        }
        return output;
    }

    private static boolean aload0(AbstractInsnNode instruction) {
        return instruction instanceof VarInsnNode variable
                && variable.getOpcode() == Opcodes.ALOAD
                && variable.var == 0;
    }

    private static Chain failed(String blocker) {
        return new Chain(
                List.of(), false, false, false, false, false,
                null, new State(), List.of(blocker));
    }

    private void load(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()
                        || !entry.getName().endsWith(".class")
                        || entry.getName().equals("module-info.class")) {
                    continue;
                }
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(
                            node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException malformed) {
                    diagnostics.add(
                            "Unreadable processor block-construction class "
                                    + entry.getName() + ": "
                                    + malformed.getClass().getSimpleName());
                }
            }
        }
    }
}
