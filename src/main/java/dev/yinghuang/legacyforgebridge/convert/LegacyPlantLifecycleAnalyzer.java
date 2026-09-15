package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Splits source plant hooks into bounded lifecycle gates without executing legacy code.
 * Presentation-only overrides do not poison growth/survival proof; lifecycle callbacks do.
 * Forge 1.7 IPlantable overrides are tracked separately because canSustainPlant delegates through
 * getPlantType/getPlant/getPlantMetadata and therefore they can change both survival and growth.
 */
public final class LegacyPlantLifecycleAnalyzer {
    private static final Set<String> SURVIVAL = Set.of(
            "canPlaceBlockOn", "func_149854_a",
            "canBlockStay", "func_149718_j",
            "canPlaceBlockAt", "func_149742_c",
            "onNeighborBlockChange", "func_149695_a",
            "checkAndDropBlock", "func_149855_e");
    private static final Set<String> RANDOM_TICK = Set.of("updateTick", "func_149674_a");
    private static final Set<String> CROP_GROWTH = Set.of("func_149863_m");
    private static final Set<String> BONEMEAL = Set.of("func_149851_a", "func_149852_a", "func_149853_b");
    private static final Set<String> DROPS = Set.of(
            "getItemDropped", "func_149650_a",
            "quantityDropped", "func_149745_a",
            "dropBlockAsItemWithChance", "func_149690_a",
            "getDrops", "getItem", "func_149694_d",
            "func_149865_P", "func_149866_i");
    private static final Set<String> PRESENTATION = Set.of(
            "getIcon", "func_149691_a",
            "registerBlockIcons", "func_149651_a",
            "getRenderType", "func_149645_b",
            "setBlockBoundsBasedOnState", "func_149719_a");
    private static final Set<String> RANDOM_TICK_SETTER = Set.of("setTickRandomly", "func_149675_a");
    private static final String PLANT_ACCESS = "Lnet/minecraft/world/IBlockAccess;III";

    public enum AgeModel {
        NONE,
        LEGACY_META_0_7,
        LEGACY_META_TIMER_0_15,
        UNKNOWN
    }

    /** Forge 1.7 base behavior, not unpatched vanilla behavior. */
    public enum SurvivalModel {
        FORGE_CROPS_PLAINS,
        FORGE_REED_BEACH,
        FORGE_BUSH_PLAINS,
        CUSTOM_SOURCE
    }

    public record Proof(
            String registryName,
            String sourceClass,
            LegacyPlantBlockAnalyzer.Family family,
            boolean survivalInheritedVanilla,
            boolean growthInheritedVanilla,
            boolean bonemealInheritedVanilla,
            boolean dropsInheritedVanilla,
            AgeModel ageModel,
            SurvivalModel survivalModel,
            List<String> plantableHooks,
            List<String> survivalHooks,
            List<String> growthHooks,
            List<String> bonemealHooks,
            List<String> dropHooks,
            List<String> presentationHooks,
            List<String> constructorLifecycleMutations
    ) {
        public Proof {
            plantableHooks = List.copyOf(plantableHooks);
            survivalHooks = List.copyOf(survivalHooks);
            growthHooks = List.copyOf(growthHooks);
            bonemealHooks = List.copyOf(bonemealHooks);
            dropHooks = List.copyOf(dropHooks);
            presentationHooks = List.copyOf(presentationHooks);
            constructorLifecycleMutations = List.copyOf(constructorLifecycleMutations);
        }
    }

    public record Analysis(List<Proof> proofs, List<String> diagnostics) {
        public Analysis { proofs = List.copyOf(proofs); diagnostics = List.copyOf(diagnostics); }
    }

    private final Map<String, ClassNode> classes = new LinkedHashMap<>();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear();
        load(jarPath);
        var plantAnalysis = new LegacyPlantBlockAnalyzer().analyze(jarPath);
        List<Proof> proofs = new ArrayList<>();
        for (var plant : plantAnalysis.rules()) {
            List<String> plantable = new ArrayList<>();
            List<String> survival = new ArrayList<>();
            List<String> growth = new ArrayList<>();
            List<String> bonemeal = new ArrayList<>();
            List<String> drops = new ArrayList<>();
            List<String> presentation = new ArrayList<>();
            List<String> constructorMutations = new ArrayList<>();
            inspectSourceLineage(plant, plantable, survival, growth, bonemeal, drops, presentation, constructorMutations);

            boolean tickMutation = !constructorMutations.isEmpty();
            boolean plantableBase = plantable.isEmpty();
            boolean survivalVanilla = survival.isEmpty() && plantableBase && !tickMutation;
            boolean growthVanilla = switch (plant.family()) {
                case CROPS, REED -> growth.isEmpty() && plantableBase && !tickMutation;
                case BUSH -> true;
            };
            boolean bonemealVanilla = plant.family() != LegacyPlantBlockAnalyzer.Family.CROPS || bonemeal.isEmpty();
            boolean dropsVanilla = drops.isEmpty();
            AgeModel ageModel = switch (plant.family()) {
                case CROPS -> growthVanilla && bonemealVanilla ? AgeModel.LEGACY_META_0_7 : AgeModel.UNKNOWN;
                case REED -> growthVanilla ? AgeModel.LEGACY_META_TIMER_0_15 : AgeModel.UNKNOWN;
                case BUSH -> AgeModel.NONE;
            };
            SurvivalModel survivalModel = survivalVanilla ? switch (plant.family()) {
                case CROPS -> SurvivalModel.FORGE_CROPS_PLAINS;
                case REED -> SurvivalModel.FORGE_REED_BEACH;
                case BUSH -> SurvivalModel.FORGE_BUSH_PLAINS;
            } : SurvivalModel.CUSTOM_SOURCE;

            proofs.add(new Proof(plant.registryName(), plant.sourceClass(), plant.family(),
                    survivalVanilla, growthVanilla, bonemealVanilla, dropsVanilla,
                    ageModel, survivalModel, plantable, survival, growth, bonemeal, drops,
                    presentation, constructorMutations));
        }
        return new Analysis(proofs, plantAnalysis.diagnostics());
    }

    private void inspectSourceLineage(
            LegacyPlantBlockAnalyzer.Rule plant,
            List<String> plantable,
            List<String> survival,
            List<String> growth,
            List<String> bonemeal,
            List<String> drops,
            List<String> presentation,
            List<String> constructorMutations
    ) {
        String base = baseClass(plant.family());
        String current = plant.sourceClass();
        Set<String> seen = new HashSet<>();
        while (current != null && seen.add(current) && !base.equals(current)) {
            ClassNode node = classes.get(current);
            if (node == null) break;
            for (MethodNode method : node.methods) {
                String signature = current + "#" + method.name + method.desc;
                if ("<init>".equals(method.name)) {
                    for (AbstractInsnNode instruction : method.instructions) {
                        if (instruction instanceof MethodInsnNode call && RANDOM_TICK_SETTER.contains(call.name)) {
                            constructorMutations.add(signature + " -> " + call.owner + "." + call.name + call.desc);
                        }
                    }
                    continue;
                }
                if ("<clinit>".equals(method.name)) continue;
                if (isPlantableHook(method)) plantable.add(signature);
                if (PRESENTATION.contains(method.name)) presentation.add(signature);
                if (DROPS.contains(method.name)) drops.add(signature);
                if (BONEMEAL.contains(method.name)) bonemeal.add(signature);
                if (SURVIVAL.contains(method.name)) survival.add(signature);
                if (RANDOM_TICK.contains(method.name)) {
                    if (plant.family() == LegacyPlantBlockAnalyzer.Family.BUSH) survival.add(signature);
                    else {
                        growth.add(signature);
                        survival.add(signature);
                    }
                }
                if (plant.family() == LegacyPlantBlockAnalyzer.Family.CROPS && CROP_GROWTH.contains(method.name)) {
                    growth.add(signature);
                }
            }
            current = node.superName;
        }
    }

    private static boolean isPlantableHook(MethodNode method) {
        return switch (method.name) {
            case "getPlantType" -> ("(" + PLANT_ACCESS + ")Lnet/minecraftforge/common/EnumPlantType;").equals(method.desc);
            case "getPlant" -> ("(" + PLANT_ACCESS + ")Lnet/minecraft/block/Block;").equals(method.desc);
            case "getPlantMetadata" -> ("(" + PLANT_ACCESS + ")I").equals(method.desc);
            default -> false;
        };
    }

    private static String baseClass(LegacyPlantBlockAnalyzer.Family family) {
        return switch (family) {
            case CROPS -> "net/minecraft/block/BlockCrops";
            case REED -> "net/minecraft/block/BlockReed";
            case BUSH -> "net/minecraft/block/BlockBush";
        };
    }

    private void load(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode();
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                }
            }
        }
    }
}
