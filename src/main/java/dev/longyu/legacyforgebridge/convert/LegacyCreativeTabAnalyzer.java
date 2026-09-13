package dev.longyu.legacyforgebridge.convert;

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
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Best-effort semantic extractor for Forge 1.7.10 creative tabs.
 *
 * <p>The extractor never loads legacy classes. It reads ordinary bytecode patterns used by
 * {@code CreativeTabs} construction, {@code Item#setUnlocalizedName}, and
 * {@code Item#setCreativeTab}. Only custom tabs created by the source JAR are returned; vanilla
 * tabs remain owned by the modern runtime/profile fallback.</p>
 */
public final class LegacyCreativeTabAnalyzer {
    private static final String CREATIVE_TABS = "net/minecraft/creativetab/CreativeTabs";
    private static final String ITEM = "net/minecraft/item/Item";

    public Analysis analyze(Path jarPath) throws IOException {
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
                    inspectClass(new ClassReader(input), superByClass, tabs, items, tabIconFields);
                } catch (RuntimeException ignored) {
                    // Analyzer is intentionally best-effort. Unsupported bytecode in one class must
                    // not prevent the rest of the source JAR from contributing presentation data.
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
                tabItems.add(itemName(itemEntry.getKey(), item));
            }

            String icon = null;
            FieldRef iconField = tabIconFields.get(source.implementationClass);
            if (iconField != null) {
                icon = itemName(iconField, items.get(iconField));
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

    private static void inspectClass(
            ClassReader reader,
            Map<String, String> superByClass,
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
                    private FieldRef recentCreativeTabField;
                    private FieldRef recentItemField;
                    private String pendingItemName;
                    private FieldRef pendingCreativeTab;
                    private String pendingTabLabel;
                    private String pendingTabImplementationClass;

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
                            if (isItemType(type, superByClass)) {
                                recentItemField = field;
                            }
                            return;
                        }

                        if (opcode != Opcodes.PUTSTATIC) {
                            return;
                        }

                        if (isCreativeTabType(type, superByClass) && pendingTabLabel != null) {
                            tabs.put(field, new MutableTab(pendingTabLabel, pendingTabImplementationClass));
                            pendingTabLabel = null;
                            pendingTabImplementationClass = null;
                        }

                        if (isItemType(type, superByClass)) {
                            MutableItem item = items.computeIfAbsent(field, ignored -> new MutableItem());
                            if (pendingItemName != null) {
                                item.unlocalizedName = pendingItemName;
                            }
                            if (pendingCreativeTab != null) {
                                item.creativeTab = pendingCreativeTab;
                            }
                            pendingItemName = null;
                            pendingCreativeTab = null;
                            recentItemField = null;
                        }
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                        if (methodName.equals("<init>")
                                && isCreativeTabType(owner, superByClass)
                                && methodDescriptor.contains("Ljava/lang/String;")
                                && recentString != null) {
                            pendingTabLabel = recentString;
                            pendingTabImplementationClass = owner;
                            recentString = null;
                            return;
                        }

                        if ((methodName.equals("setUnlocalizedName") || methodName.equals("func_77655_b"))
                                && methodDescriptor.startsWith("(Ljava/lang/String;)")) {
                            if (recentString != null) {
                                pendingItemName = recentString;
                            }
                            recentString = null;
                            return;
                        }

                        if ((methodName.equals("setCreativeTab") || methodName.equals("func_77637_a"))
                                && methodDescriptor.startsWith("(L" + CREATIVE_TABS + ";)")) {
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
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private static String itemName(FieldRef field, MutableItem item) {
        if (item != null && item.unlocalizedName != null && !item.unlocalizedName.isBlank()) {
            return stripLegacyItemPrefix(item.unlocalizedName);
        }
        return stripLegacyItemPrefix(field.name);
    }

    private static String stripLegacyItemPrefix(String value) {
        String result = value;
        if (result.startsWith("item.")) {
            result = result.substring("item.".length());
        }
        int namespace = result.indexOf(':');
        return namespace >= 0 ? result.substring(namespace + 1) : result;
    }

    private static boolean isCreativeTabType(String type, Map<String, String> superByClass) {
        return isTypeOrSubclass(type, CREATIVE_TABS, superByClass);
    }

    private static boolean isItemType(String type, Map<String, String> superByClass) {
        return isTypeOrSubclass(type, ITEM, superByClass);
    }

    private static boolean isTypeOrSubclass(String type, String target, Map<String, String> superByClass) {
        if (type == null) {
            return false;
        }
        String current = type;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            if (current.equals(target)) {
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
