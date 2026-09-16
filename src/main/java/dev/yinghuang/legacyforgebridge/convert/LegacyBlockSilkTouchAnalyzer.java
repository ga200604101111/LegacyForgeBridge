package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Proves the bounded Forge 1.7.10 default silk-touch branch for registered direct Blocks.
 *
 * <p>Forge's context-aware {@code canSilkHarvest(...)} stores metadata and delegates to the old
 * {@code canSilkHarvest()}, whose patched Block default is
 * {@code renderAsNormalBlock() && !hasTileEntity(meta)}. The base stacked item is the registered
 * BlockItem with count 1 and metadata preserved only when that Item reports subtypes.</p>
 *
 * <p>This analyzer therefore treats eligibility and stacked-item construction as separate proofs.
 * Source overrides of the silk callbacks, render-normal check, metadata tile-entity check or
 * stacked-item callback fail closed. A source class that directly implements
 * {@code ITileEntityProvider} is still fully provable: the Forge default simply disables silk
 * harvest for every metadata value.</p>
 */
public final class LegacyBlockSilkTouchAnalyzer {
    private static final String VANILLA_BLOCK = "net/minecraft/block/Block";
    private static final String ITEM = "net/minecraft/item/Item";
    private static final String ITEM_BLOCK = "net/minecraft/item/ItemBlock";
    private static final String TILE_PROVIDER = "net/minecraft/block/ITileEntityProvider";
    private static final List<MethodSpec> ELIGIBILITY_SPECS = List.of(
            new MethodSpec("canSilkHarvest", Set.of("canSilkHarvest", "func_149700_E"), "()Z"),
            new MethodSpec("canSilkHarvest(World,...)", Set.of("canSilkHarvest"),
                    "(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;IIII)Z"),
            new MethodSpec("renderAsNormalBlock", Set.of("renderAsNormalBlock", "func_149686_d"), "()Z"),
            new MethodSpec("hasTileEntity(metadata)", Set.of("hasTileEntity"), "(I)Z")
    );
    private static final MethodSpec STACKED = new MethodSpec(
            "createStackedBlock", Set.of("createStackedBlock", "func_149644_j"),
            "(I)Lnet/minecraft/item/ItemStack;"
    );
    private static final Set<String> SUBTYPE_SETTERS = Set.of("setHasSubtypes", "func_77627_a");
    private static final String SUBTYPE_SETTER_DESC = "(Z)Lnet/minecraft/item/Item;";

    public record Proof(
            String registryName,
            String legacyNamespace,
            String implementationClass,
            boolean eligibilityProofComplete,
            Boolean silkEligible,
            boolean stackedItemProofComplete,
            Integer stackedLegacyDamage,
            List<String> eligibilityReasons,
            List<String> stackedItemReasons
    ) {
        public Proof {
            eligibilityReasons = List.copyOf(eligibilityReasons);
            stackedItemReasons = List.copyOf(stackedItemReasons);
        }
    }

    public record Analysis(List<Proof> proofs, List<String> diagnostics) {
        public Analysis {
            proofs = List.copyOf(proofs);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record FieldKey(String owner, String name, String descriptor) { }

    public Analysis analyze(Path jarPath) throws IOException {
        Map<String, ClassNode> classes = loadClasses(jarPath);
        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(jarPath);
        Set<FieldKey> ordinaryItemFields = new LinkedHashSet<>();
        for (LegacyRegistryAnalyzer.FieldBinding binding : registry.fieldBindings()) {
            if (binding.kind() == LegacyRegistryAnalyzer.Kind.ITEM) {
                ordinaryItemFields.add(new FieldKey(binding.owner(), binding.name(), binding.descriptor()));
            }
        }
        boolean unscopedSubtypeMutation = hasUnscopedSubtypeMutation(classes, ordinaryItemFields);

        List<Proof> proofs = new ArrayList<>();
        for (LegacyRegistryAnalyzer.Registration block : registry.blocks()) {
            List<String> eligibilityReasons = new ArrayList<>();
            List<String> stackReasons = new ArrayList<>();
            String externalBase = firstExternalSuperclass(classes, block.implementationClass());
            boolean directBlockDefaults = VANILLA_BLOCK.equals(externalBase);
            if (externalBase == null) {
                eligibilityReasons.add("source hierarchy could not prove its first external superclass");
                stackReasons.add("source hierarchy could not prove its first external superclass");
            } else if (!directBlockDefaults) {
                eligibilityReasons.add("external superclass " + externalBase
                        + " may override Forge silk eligibility");
                stackReasons.add("external superclass " + externalBase
                        + " may override createStackedBlock");
            }

            if (block.implementationClass() != null) {
                String current = block.implementationClass();
                Set<String> visited = new LinkedHashSet<>();
                while (current != null && visited.add(current)) {
                    ClassNode node = classes.get(current);
                    if (node == null) break;
                    for (MethodSpec spec : ELIGIBILITY_SPECS) {
                        if (declares(node, spec)) {
                            eligibilityReasons.add("source overrides " + spec.label()
                                    + "; Forge default silk eligibility is not proven");
                        }
                    }
                    if (declares(node, STACKED)) {
                        stackReasons.add("source overrides createStackedBlock; silk stacked item is not compiled");
                    }
                    current = node.superName;
                }
            }

            InterfaceProof tileProvider = proveTileProvider(classes, block.implementationClass());
            eligibilityReasons.addAll(tileProvider.reasons());

            if (block.itemBlockClass() != null && !ITEM_BLOCK.equals(block.itemBlockClass())) {
                stackReasons.add("custom ItemBlock class " + block.itemBlockClass()
                        + " may change getHasSubtypes/createStackedBlock metadata");
            }
            if (unscopedSubtypeMutation) {
                stackReasons.add("source contains an unscoped Item.setHasSubtypes mutation; default BlockItem subtype state is not immutable-proofed");
            }

            eligibilityReasons = new ArrayList<>(new LinkedHashSet<>(eligibilityReasons));
            stackReasons = new ArrayList<>(new LinkedHashSet<>(stackReasons));
            boolean eligibilityComplete = directBlockDefaults && eligibilityReasons.isEmpty() && tileProvider.complete();
            Boolean silkEligible = eligibilityComplete ? !tileProvider.provider() : null;
            boolean stackComplete = directBlockDefaults && stackReasons.isEmpty();
            Integer stackedDamage = stackComplete ? 0 : null;
            proofs.add(new Proof(
                    block.registryName(), block.legacyNamespace(), block.implementationClass(),
                    eligibilityComplete, silkEligible, stackComplete, stackedDamage,
                    eligibilityReasons, stackReasons
            ));
        }
        return new Analysis(proofs, registry.diagnostics());
    }

    private static boolean hasUnscopedSubtypeMutation(
            Map<String, ClassNode> classes,
            Set<FieldKey> ordinaryItemFields
    ) {
        for (ClassNode owner : classes.values()) {
            for (MethodNode method : owner.methods) {
                boolean containsSetter = method.instructions.iterator().hasNext();
                if (!containsSetter) continue;
                Frame<SourceValue>[] frames = null;
                try {
                    frames = new Analyzer<>(new SourceInterpreter()).analyze(owner.name, method);
                } catch (AnalyzerException | RuntimeException ignored) {
                    // If this method contains a subtype setter we will conservatively reject it below.
                }
                int index = 0;
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && isSubtypeSetter(call)) {
                        if (frames == null || index >= frames.length || frames[index] == null
                                || !receiverMutationIsObjectScoped(owner, method, frames[index], ordinaryItemFields)) {
                            return true;
                        }
                    }
                    index++;
                }
            }
        }
        return false;
    }

    private static boolean receiverMutationIsObjectScoped(
            ClassNode owner,
            MethodNode method,
            Frame<SourceValue> frame,
            Set<FieldKey> ordinaryItemFields
    ) {
        if (frame.getStackSize() < 2) return false;
        SourceValue receiver = frame.getStack(frame.getStackSize() - 2);
        if (receiver == null || receiver.insns == null || receiver.insns.size() != 1) return false;
        AbstractInsnNode source = receiver.insns.iterator().next();

        // The invokevirtual owner/descriptor already proves that the receiver must be an Item at
        // bytecode-verification time. When the source is exactly ALOAD 0, the mutation is therefore
        // scoped to this source Item/ItemBlock instance even when its first external superclass is
        // ItemFood, ItemSnowball, ItemBlock, or another vanilla/Forge Item subclass that is not in
        // the input JAR. Do not let that local constructor mutation poison every generated BlockItem.
        if (source instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ALOAD && variable.var == 0
                && (method.access & Opcodes.ACC_STATIC) == 0) {
            return true;
        }

        // Likewise a directly constructed receiver is object-local. A custom ItemBlock remains
        // fail-closed for the block that actually uses it via the per-registration itemBlockClass
        // gate above; it is no longer treated as an unscoped mutation affecting unrelated blocks.
        if (source instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) {
            return true;
        }
        if (source instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                && ordinaryItemFields.contains(new FieldKey(field.owner, field.name, field.desc))) {
            return true;
        }
        return false;
    }

    private static boolean isSubtypeSetter(MethodInsnNode call) {
        return call != null && SUBTYPE_SETTERS.contains(call.name) && SUBTYPE_SETTER_DESC.equals(call.desc);
    }

    private static InterfaceProof proveTileProvider(Map<String, ClassNode> classes, String implementationClass) {
        if (implementationClass == null || implementationClass.isBlank()) {
            return new InterfaceProof(false, false, List.of("missing source implementation class"));
        }
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        boolean provider = false;
        String current = implementationClass;
        Set<String> visitedClasses = new HashSet<>();
        Set<String> visitedInterfaces = new HashSet<>();
        while (current != null && visitedClasses.add(current)) {
            ClassNode node = classes.get(current);
            if (node == null) break;
            InterfaceResult result = inspectInterfaces(classes, node.interfaces, visitedInterfaces);
            provider |= result.provider();
            reasons.addAll(result.reasons());
            current = node.superName;
        }
        return new InterfaceProof(reasons.isEmpty(), provider, List.copyOf(reasons));
    }

    private static InterfaceResult inspectInterfaces(
            Map<String, ClassNode> classes,
            List<String> interfaces,
            Set<String> visited
    ) {
        boolean provider = false;
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        if (interfaces == null) return new InterfaceResult(false, List.of());
        for (String name : interfaces) {
            if (!visited.add(name)) continue;
            if (TILE_PROVIDER.equals(name)) {
                provider = true;
                continue;
            }
            ClassNode sourceInterface = classes.get(name);
            if (sourceInterface != null) {
                InterfaceResult nested = inspectInterfaces(classes, sourceInterface.interfaces, visited);
                provider |= nested.provider();
                reasons.addAll(nested.reasons());
            } else if (!name.startsWith("java/")) {
                reasons.add("external interface " + name + " may extend ITileEntityProvider");
            }
        }
        return new InterfaceResult(provider, List.copyOf(reasons));
    }

    private static boolean declares(ClassNode owner, MethodSpec spec) {
        for (MethodNode method : owner.methods) {
            if ((method.access & Opcodes.ACC_STATIC) == 0
                    && spec.names().contains(method.name)
                    && spec.descriptor().equals(method.desc)) return true;
        }
        return false;
    }

    private static boolean isSubclass(Map<String, ClassNode> classes, String type, String expectedBase) {
        if (type == null) return false;
        String current = type;
        Set<String> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            if (expectedBase.equals(current)) return true;
            ClassNode node = classes.get(current);
            if (node == null) return false;
            current = node.superName;
        }
        return false;
    }

    private static String firstExternalSuperclass(Map<String, ClassNode> classes, String implementationClass) {
        if (implementationClass == null || implementationClass.isBlank()) return null;
        String current = implementationClass;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            ClassNode node = classes.get(current);
            if (node == null) return current.equals(implementationClass) ? null : current;
            current = node.superName;
        }
        return null;
    }

    private static Map<String, ClassNode> loadClasses(Path jarPath) throws IOException {
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException ignored) {
                    // Missing class evidence remains fail-closed through the hierarchy/interface checks.
                }
            }
        }
        return classes;
    }

    private record MethodSpec(String label, Set<String> names, String descriptor) { }
    private record InterfaceProof(boolean complete, boolean provider, List<String> reasons) { }
    private record InterfaceResult(boolean provider, List<String> reasons) { }
}
