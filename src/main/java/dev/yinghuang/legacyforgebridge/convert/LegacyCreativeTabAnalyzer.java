package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Best-effort semantic extractor for Forge 1.7.10 creative tabs.
 *
 * <p>The extractor never loads legacy classes. It recognises both common registration styles:
 * callers that invoke {@code Item#setCreativeTab} directly and custom Item subclasses whose
 * constructors choose a tab internally. The latter is especially common in 1.7.10 mods, where a
 * content holder simply instantiates {@code new CustomSword("id", ...)} and the CustomSword
 * constructor assigns its tab.</p>
 */
public final class LegacyCreativeTabAnalyzer {
    private static final String CREATIVE_TABS = "net/minecraft/creativetab/CreativeTabs";
    private static final String ITEM = "net/minecraft/item/Item";
    private static final String BLOCK = "net/minecraft/block/Block";
    private static final String MINECRAFT_ITEM_PREFIX = "net/minecraft/item/Item";
    private static final String MINECRAFT_BLOCK_PREFIX = "net/minecraft/block/Block";

    public Analysis analyze(Path jarPath) throws IOException {
        Map<String, String> superByClass = readHierarchy(jarPath);
        Map<String, FieldRef> defaultTabByItemClass = readConstructorDefaultTabs(jarPath, superByClass);
        Map<FieldRef,String> registryNamesByField = new LinkedHashMap<>();
        var registry = new LegacyRegistryAnalyzer().analyze(jarPath);
        for (var binding : registry.fieldBindings()) {
            if (binding.kind() != LegacyRegistryAnalyzer.Kind.ITEM
                    && binding.kind() != LegacyRegistryAnalyzer.Kind.BLOCK) continue;
            registryNamesByField.put(
                    new FieldRef(binding.owner(), binding.name(), binding.descriptor()),
                    binding.registryName()
            );
        }

        Map<FieldRef, MutableTab> tabs = new LinkedHashMap<>();
        Map<FieldRef, MutableItem> items = new LinkedHashMap<>();
        Map<String, FieldRef> tabIconFields = new LinkedHashMap<>();

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream input = jar.getInputStream(entry)) {
                    inspectClass(
                            new ClassReader(input),
                            superByClass,
                            defaultTabByItemClass,
                            tabs,
                            items,
                            tabIconFields
                    );
                } catch (RuntimeException ignored) {
                    // Best-effort analysis: one unusual class must not discard recoverable
                    // presentation semantics from the rest of the source JAR.
                }
            }
        }

        List<Tab> result = new ArrayList<>();
        for (Map.Entry<FieldRef, MutableTab> tabEntry : tabs.entrySet()) {
            FieldRef tabField = tabEntry.getKey();
            MutableTab source = tabEntry.getValue();
            LinkedHashSet<String> tabItems = new LinkedHashSet<>();
            for (Map.Entry<FieldRef, MutableItem> itemEntry : items.entrySet()) {
                MutableItem item = itemEntry.getValue();
                if (!tabField.equals(item.creativeTab)) {
                    continue;
                }
                tabItems.add(itemName(itemEntry.getKey(), item, registryNamesByField));
            }

            String icon = null;
            FieldRef iconField = tabIconFields.get(source.implementationClass);
            if (iconField != null) {
                icon = itemName(iconField, items.get(iconField), registryNamesByField);
            }
            if (icon == null && !tabItems.isEmpty()) {
                icon = tabItems.getFirst();
            }

            result.add(new Tab(
                    source.label,
                    tabField.owner,
                    tabField.name,
                    icon,
                    List.copyOf(tabItems)
            ));
        }
        return new Analysis(List.copyOf(result));
    }

    private static Map<String, String> readHierarchy(Path jarPath) throws IOException {
        Map<String, String> superByClass = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream input = jar.getInputStream(entry)) {
                    new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                            superByClass.put(name, superName);
                        }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
            }
        }
        return superByClass;
    }

    /**
     * Extracts constant tab choices made by custom Item constructors. If one class has constructors
     * that select different tabs, that class is intentionally treated as ambiguous rather than
     * guessed; direct allocation-site assignments can still be recovered later.
     */
    private static Map<String, FieldRef> readConstructorDefaultTabs(
            Path jarPath,
            Map<String, String> superByClass
    ) throws IOException {
        Map<String, FieldRef> defaults = new LinkedHashMap<>();
        Set<String> ambiguous = new LinkedHashSet<>();

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream input = jar.getInputStream(entry)) {
                    new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                        private String className;

                        @Override
                        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                            className = name;
                        }

                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            if (!"<init>".equals(name) || !isCreativeContentType(className, superByClass)) {
                                return null;
                            }
                            return new MethodVisitor(Opcodes.ASM9) {
                                private FieldRef recentCreativeTabField;

                                @Override
                                public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
                                    if (opcode != Opcodes.GETSTATIC) {
                                        return;
                                    }
                                    String type = objectType(fieldDescriptor);
                                    if (isCreativeTabType(type, superByClass)) {
                                        recentCreativeTabField = new FieldRef(owner, fieldName, fieldDescriptor);
                                    }
                                }

                                @Override
                                public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                                    if (isSetCreativeTab(methodName, methodDescriptor) && recentCreativeTabField != null) {
                                        FieldRef existing = defaults.get(className);
                                        if (existing == null && !ambiguous.contains(className)) {
                                            defaults.put(className, recentCreativeTabField);
                                        } else if (existing != null && !existing.equals(recentCreativeTabField)) {
                                            defaults.remove(className);
                                            ambiguous.add(className);
                                        }
                                        recentCreativeTabField = null;
                                    }
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                } catch (RuntimeException ignored) {
                }
            }
        }
        return Map.copyOf(defaults);
    }

    private static void inspectClass(
            ClassReader reader,
            Map<String, String> superByClass,
            Map<String, FieldRef> defaultTabByItemClass,
            Map<FieldRef, MutableTab> tabs,
            Map<FieldRef, MutableItem> items,
            Map<String, FieldRef> tabIconFields
    ) {
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            private String className;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                this.className = name;
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                boolean tabIconMethod = isCreativeTabType(className, superByClass)
                        && descriptor.equals("()L" + ITEM + ";")
                        && (name.equals("getTabIconItem") || name.equals("func_78016_d"));

                return new MethodVisitor(Opcodes.ASM9) {
                    private String recentString;
                    private String recentNewType;
                    private FieldRef recentCreativeTabField;
                    private FieldRef recentItemField;
                    private String pendingItemName;
                    private String pendingItemImplementationClass;
                    private FieldRef pendingCreativeTab;
                    private String pendingTabLabel;
                    private String pendingTabImplementationClass;

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW) {
                            recentNewType = type;
                        }
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String string) {
                            recentString = string;
                        }
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
                        FieldRef field = new FieldRef(owner, fieldName, fieldDescriptor);
                        String type = objectType(fieldDescriptor);
                        if (opcode == Opcodes.GETSTATIC) {
                            if (isCreativeTabType(type, superByClass)) {
                                recentCreativeTabField = field;
                            }
                            if (isCreativeContentType(type, superByClass)) {
                                recentItemField = field;
                            }
                            return;
                        }

                        if (opcode != Opcodes.PUTSTATIC) {
                            return;
                        }

                        if (isCreativeTabType(type, superByClass) && pendingTabImplementationClass != null) {
                            String label = pendingTabLabel == null || pendingTabLabel.isBlank()
                                    ? field.name
                                    : pendingTabLabel;
                            tabs.put(field, new MutableTab(label, pendingTabImplementationClass));
                            pendingTabLabel = null;
                            pendingTabImplementationClass = null;
                        }

                        if (isCreativeContentType(type, superByClass)) {
                            MutableItem item = items.computeIfAbsent(field, ignored -> new MutableItem());
                            if (pendingItemName != null && !pendingItemName.isBlank()) {
                                item.unlocalizedName = pendingItemName;
                            }
                            FieldRef effectiveTab = pendingCreativeTab;
                            if (effectiveTab == null && pendingItemImplementationClass != null) {
                                effectiveTab = resolveDefaultTab(
                                        pendingItemImplementationClass,
                                        defaultTabByItemClass,
                                        superByClass
                                );
                            }
                            if (effectiveTab != null) {
                                item.creativeTab = effectiveTab;
                            }
                            pendingItemName = null;
                            pendingItemImplementationClass = null;
                            pendingCreativeTab = null;
                            recentItemField = null;
                            recentNewType = null;
                        }
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                        if (methodName.equals("<init>") && isCreativeTabType(owner, superByClass)) {
                            pendingTabLabel = recentString == null ? "" : recentString;
                            pendingTabImplementationClass = owner;
                            recentString = null;
                            recentNewType = null;
                            return;
                        }

                        if (methodName.equals("<init>")
                                && recentNewType != null
                                && recentNewType.equals(owner)
                                && isCreativeContentType(owner, superByClass)) {
                            pendingItemImplementationClass = owner;
                            // A fresh allocation owns the following fluent calls. Do not let a
                            // GETSTATIC left over from an earlier registration steal its tab.
                            recentItemField = null;
                            if (methodDescriptor.startsWith("(Ljava/lang/String;") && recentString != null) {
                                pendingItemName = recentString;
                            }
                            recentString = null;
                            recentNewType = null;
                            return;
                        }

                        if (isSetUnlocalizedName(methodName, methodDescriptor)) {
                            if (recentString != null) {
                                pendingItemName = recentString;
                            }
                            recentString = null;
                            return;
                        }

                        if (isSetCreativeTab(methodName, methodDescriptor)) {
                            if (recentCreativeTabField != null) {
                                if (recentItemField != null) {
                                    items.computeIfAbsent(recentItemField, ignored -> new MutableItem()).creativeTab = recentCreativeTabField;
                                } else {
                                    pendingCreativeTab = recentCreativeTabField;
                                }
                            }
                            recentCreativeTabField = null;
                            recentItemField = null;
                        }
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (tabIconMethod && opcode == Opcodes.ARETURN && recentItemField != null) {
                            tabIconFields.put(className, recentItemField);
                        }
                        if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                            recentCreativeTabField = null;
                            recentItemField = null;
                            recentString = null;
                            recentNewType = null;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private static FieldRef resolveDefaultTab(
            String itemClass,
            Map<String, FieldRef> defaultTabByItemClass,
            Map<String, String> superByClass
    ) {
        String current = itemClass;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            FieldRef direct = defaultTabByItemClass.get(current);
            if (direct != null) {
                return direct;
            }
            current = superByClass.get(current);
        }
        return null;
    }

    private static boolean isSetUnlocalizedName(String methodName, String descriptor) {
        return (methodName.equals("setUnlocalizedName") || methodName.equals("func_77655_b")
                || methodName.equals("setBlockName") || methodName.equals("func_149663_c"))
                && descriptor.startsWith("(Ljava/lang/String;)");
    }

    private static boolean isSetCreativeTab(String methodName, String descriptor) {
        return (methodName.equals("setCreativeTab") || methodName.equals("func_77637_a"))
                && descriptor.startsWith("(L" + CREATIVE_TABS + ";)");
    }

    private static String itemName(FieldRef field, MutableItem item, Map<FieldRef,String> registryNamesByField) {
        String registered=registryNamesByField.get(field);
        if(registered!=null&&!registered.isBlank())return stripLegacyItemPrefix(registered);
        if (item != null && item.unlocalizedName != null && !item.unlocalizedName.isBlank()) {
            return stripLegacyItemPrefix(item.unlocalizedName);
        }
        return stripLegacyItemPrefix(field.name).toLowerCase(Locale.ROOT);
    }

    private static String stripLegacyItemPrefix(String value) {
        String result = value;
        if (result.startsWith("item.") || result.startsWith("tile.")) {
            result = result.substring(5);
        }
        int namespace = result.indexOf(':');
        return namespace >= 0 ? result.substring(namespace + 1) : result;
    }

    private static boolean isCreativeTabType(String type, Map<String, String> superByClass) {
        return isTypeOrSubclass(type, CREATIVE_TABS, superByClass, false);
    }

    private static boolean isItemType(String type, Map<String, String> superByClass) {
        return isTypeOrSubclass(type, ITEM, superByClass, true);
    }

    private static boolean isCreativeContentType(String type, Map<String, String> superByClass) {
        return isItemType(type, superByClass) || isBlockType(type, superByClass);
    }

    private static boolean isBlockType(String type, Map<String, String> superByClass) {
        if (type == null) return false;
        String current = type;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            if (current.equals(BLOCK)) return true;
            if (current.startsWith(MINECRAFT_BLOCK_PREFIX) && !current.contains("$")) return true;
            current = superByClass.get(current);
        }
        return false;
    }



    private static boolean isTypeOrSubclass(
            String type,
            String target,
            Map<String, String> superByClass,
            boolean acceptVanillaItemFamily
    ) {
        if (type == null) {
            return false;
        }
        String current = type;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            if (current.equals(target)) {
                return true;
            }
            if (acceptVanillaItemFamily
                    && current.startsWith(MINECRAFT_ITEM_PREFIX)
                    && !current.equals("net/minecraft/item/ItemStack")
                    && !current.contains("$")) {
                return true;
            }
            current = superByClass.get(current);
        }
        return false;
    }

    private static String objectType(String descriptor) {
        try {
            Type type = Type.getType(descriptor);
            return type.getSort() == Type.OBJECT ? type.getInternalName() : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record Analysis(List<Tab> tabs) {
        public Analysis {
            tabs = List.copyOf(tabs);
        }
    }

    public record Tab(
            String label,
            String fieldOwner,
            String fieldName,
            String iconItemName,
            List<String> itemNames
    ) {
        public Tab {
            itemNames = List.copyOf(itemNames);
        }
    }

    private record FieldRef(String owner, String name, String descriptor) {
    }

    private static final class MutableTab {
        private final String label;
        private final String implementationClass;

        private MutableTab(String label, String implementationClass) {
            this.label = label;
            this.implementationClass = implementationClass;
        }
    }

    private static final class MutableItem {
        private String unlocalizedName;
        private FieldRef creativeTab;
    }
}
