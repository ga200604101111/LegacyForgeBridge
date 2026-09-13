package dev.longyu.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Bounded symbolic interpretation of ordinary Forge IItemRenderer registrations and render methods.
 * Never defines/loads source classes, invokes their code, reflects into a game, or executes OpenGL.
 * Unknown control flow and unmodelled rendering side effects are explicit unsupported results.
 */
public final class LegacyItemRenderAnalyzer {
    private static final String RENDERER = "net/minecraftforge/client/IItemRenderer";
    private static final String RENDER_TYPE = RENDERER + "$ItemRenderType";
    private static final String HELPER = RENDERER + "$ItemRendererHelper";
    private static final String REGISTER_OWNER = "net/minecraftforge/client/MinecraftForgeClient";
    private static final Object UNKNOWN = new Object();
    private static final int BUDGET = 20_000;
    private int remainingSteps;
    private final Map<String, ClassInfo> classes = new LinkedHashMap<>();
    private final Map<Field, Object> statics = new LinkedHashMap<>();
    private final Set<String> initialized = new LinkedHashSet<>();
    private final List<Registration> registrations = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();

    public record Operation(String op, List<Float> values) {
        public Operation { values = List.copyOf(values); }
        public static Operation of(String op, float... values) {
            List<Float> list = new ArrayList<>(); for (float value : values) list.add(value);
            return new Operation(op, list);
        }
    }
    public record Draw(String model, String texture, List<Operation> operations) {
        public Draw { operations = List.copyOf(operations); }
    }
    public record Context(boolean custom, List<Draw> draws, Map<String, Boolean> helpers) {
        public Context { draws = List.copyOf(draws); helpers = Collections.unmodifiableMap(new LinkedHashMap<>(helpers)); }
    }
    public record Binding(String fieldOwner, String fieldName, String rendererClass, Map<String, Context> contexts) {
        public Binding { contexts = Collections.unmodifiableMap(new LinkedHashMap<>(contexts)); }
    }
    public record Analysis(List<Binding> bindings, List<String> diagnostics) {
        public Analysis { bindings = List.copyOf(bindings); diagnostics = List.copyOf(diagnostics); }
    }
    /** Pure numeric expression recovered from source; no original bytecode is executed at runtime. */
    public record Expression(String op, float value, List<Expression> args) {
        public Expression { args = List.copyOf(args); }
        public static Expression constant(float n) { return new Expression("constant", n, List.of()); }
        public static Expression input(int i) { return new Expression("input", i, List.of()); }
        public static Expression of(String op, Object... values) {
            List<Expression> args = new ArrayList<>();
            for (Object v : values) {
                if (v instanceof Expression e) args.add(e);
                else if (v instanceof Number n && Float.isFinite(n.floatValue())) args.add(constant(n.floatValue()));
                else throw new Unsupported("unresolved equipment expression");
            }
            Expression e = new Expression(op, 0, args);
            if (e.nodes() > 256) throw new Unsupported("equipment expression budget exceeded");
            return e;
        }
        private int nodes() { return 1 + args.stream().mapToInt(Expression::nodes).sum(); }
        public float evaluate(float[] inputs) {
            return switch (op) {
                case "constant" -> value;
                case "input" -> inputs[(int)value];
                case "add" -> args.get(0).evaluate(inputs) + args.get(1).evaluate(inputs);
                case "sub" -> args.get(0).evaluate(inputs) - args.get(1).evaluate(inputs);
                case "mul" -> args.get(0).evaluate(inputs) * args.get(1).evaluate(inputs);
                case "div" -> args.get(0).evaluate(inputs) / args.get(1).evaluate(inputs);
                case "neg" -> -args.get(0).evaluate(inputs);
                case "cos" -> (float)Math.cos(args.get(0).evaluate(inputs));
                case "sin" -> (float)Math.sin(args.get(0).evaluate(inputs));
                default -> throw new IllegalArgumentException("Unknown expression " + op);
            };
        }
    }
    public record AnimatedOperation(String op, List<Expression> values) {
        public AnimatedOperation { values = List.copyOf(values); }
    }
    public record EquipmentDraw(String model, String texture, List<AnimatedOperation> operations,
                                boolean lighting, boolean cull) {
        public EquipmentDraw { operations = List.copyOf(operations); }
    }
    public record EquipmentBinding(String itemName, String itemClass, String rendererClass, int armorSlot,
                                   List<EquipmentDraw> standing, List<EquipmentDraw> crouching) {
        public EquipmentBinding { standing = List.copyOf(standing); crouching = List.copyOf(crouching); }
    }
    public record EquipmentAnalysis(List<EquipmentBinding> bindings, List<String> diagnostics) {
        public EquipmentAnalysis { bindings = List.copyOf(bindings); diagnostics = List.copyOf(diagnostics); }
    }
    private record EntityState(boolean crouching) { }

    /**
     * Recover ItemArmor#getArmorModel allocations and render(Entity, FFFFFF) programs. The six
     * float inputs keep the legacy method order: limb phase, amplitude, age, head yaw, pitch, scale.
     * Only straight-line allocations and proven standing/crouching branches are admitted.
     */
    public EquipmentAnalysis analyzeEquipment(Path source) throws IOException {
        loadSource(source);
        Map<String, EquipmentBinding> found = new LinkedHashMap<>();
        Set<String> conflicts = new LinkedHashSet<>();
        for (ClassInfo info : classes.values()) for (Method method : info.methods.values()) {
            for (int at = 0; at < method.code.size(); at++) {
                Insn instruction = method.code.get(at);
                if (instruction.op != Opcodes.NEW || !isSubclass((String)instruction.data, "net/minecraft/item/ItemArmor")) continue;
                String type = (String)instruction.data;
                String armorDesc = "(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;I)Lnet/minecraft/client/model/ModelBiped;";
                Method chooseModel = find(type, "getArmorModel", armorDesc);
                if (chooseModel == null) continue;
                try {
                    int end = at + 1;
                    while (end < method.code.size()) {
                        Insn next = method.code.get(end);
                        if (next.op == Opcodes.INVOKESPECIAL && next.data instanceof Call c && c.owner.equals(type) && c.name.equals("<init>")) break;
                        end++;
                    }
                    if (end == method.code.size() || end - at > 100) throw new Unsupported("unbounded armor allocation");
                    Method allocation = new Method(Opcodes.ACC_STATIC, method.owner, "<allocation>", "()Ljava/lang/Object;");
                    allocation.code.addAll(method.code.subList(at, end + 1));
                    allocation.code.add(new Insn(Opcodes.ARETURN, null));
                    Object value = run(allocation, null, new Object[0], null, 0);
                    if (!(value instanceof Instance item) || item.itemName == null) throw new Unsupported("unproven armor item identity");
                    List<List<EquipmentDraw>> poses = new ArrayList<>();
                    String rendererType = null;
                    for (boolean crouch : new boolean[]{false, true}) {
                        Object model = run(chooseModel, item, new Object[]{new EntityState(crouch), UNKNOWN, item.armorSlot}, null, 0);
                        if (!(model instanceof Instance renderer) || !isSubclass(renderer.type, "net/minecraft/client/model/ModelBiped"))
                            throw new Unsupported("unresolved armor model result");
                        if (rendererType != null && !rendererType.equals(renderer.type)) throw new Unsupported("posture changes renderer class");
                        rendererType = renderer.type;
                        String desc = "(Lnet/minecraft/entity/Entity;FFFFFF)V";
                        Method render = find(renderer.type, "func_78088_a", desc);
                        if (render == null) render = find(renderer.type, "render", desc);
                        if (render == null) throw new Unsupported("missing armor render method");
                        Object[] args = new Object[7]; args[0] = new EntityState(crouch);
                        for (int i = 1; i < args.length; i++) args[i] = Expression.input(i - 1);
                        Trace trace = new Trace(); trace.equipment = true;
                        run(render, renderer, args, trace, 0);
                        if (!trace.animatedStack.isEmpty()) throw new Unsupported("unbalanced armor matrix stack");
                        if (trace.equipmentDraws.isEmpty()) throw new Unsupported("no proven equipment OBJ draw");
                        poses.add(List.copyOf(trace.equipmentDraws));
                    }
                    EquipmentBinding binding = new EquipmentBinding(item.itemName, type, rendererType, item.armorSlot, poses.get(0), poses.get(1));
                    EquipmentBinding old = found.putIfAbsent(item.itemName, binding);
                    if (old != null && !old.equals(binding)) conflicts.add(item.itemName);
                } catch (RuntimeException ex) {
                    String diagnostic = type + " at " + method.owner + "." + method.name + ": " + ex.getMessage();
                    if (!diagnostics.contains(diagnostic)) diagnostics.add(diagnostic);
                }
            }
        }
        for (String name : conflicts) { found.remove(name); diagnostics.add("Conflicting source armor identity " + name); }
        return new EquipmentAnalysis(new ArrayList<>(found.values()), diagnostics);
    }

    public record ConstructorArgument(String descriptor, Object value) { }
    public record ItemAllocation(String itemName, String itemClass, String constructorDescriptor,
                                 List<ConstructorArgument> arguments, boolean inheritedSwordBlocking,
                                 boolean full3D, boolean rotates) { }
    public record ItemAnalysis(List<ItemAllocation> items, List<String> diagnostics) { }

    /** Proven, bounded construction sites; names are read from setUnlocalizedName, not filenames. */
    public ItemAnalysis analyzeItems(Path source) throws IOException {
        loadSource(source);
        Map<String,ItemAllocation> found=new LinkedHashMap<>();
        Set<String> conflicting=new LinkedHashSet<>();
        for (ClassInfo info:classes.values()) for (Method method:info.methods.values()) {
            for (int at=0;at<method.code.size();at++) {
                Insn in=method.code.get(at);
                if(in.op!=Opcodes.NEW || !isSubclass((String)in.data,"net/minecraft/item/Item")) continue;
                String type=(String)in.data;
                try {
                    int end=at+1;
                    while(end<method.code.size()) {
                        Insn next=method.code.get(end);
                        if(next.op==Opcodes.INVOKESPECIAL && next.data instanceof Call call
                                && call.owner.equals(type) && call.name.equals("<init>")) break;
                        end++;
                    }
                    if(end==method.code.size()||end-at>100) throw new Unsupported("unbounded item construction");
                    Method allocation=new Method(Opcodes.ACC_STATIC,method.owner,"<allocation>","()Ljava/lang/Object;");
                    allocation.code.addAll(method.code.subList(at,end+1)); allocation.code.add(new Insn(Opcodes.ARETURN,null));
                    Object object=run(allocation,null,new Object[0],null,0);
                    if(!(object instanceof Instance item)||item.itemName==null||item.constructorArgs==null)
                        throw new Unsupported("unresolved item constructor or unlocalized name");
                    Type[] types=Type.getArgumentTypes(item.constructorDesc); List<ConstructorArgument> args=new ArrayList<>();
                    for(int i=0;i<types.length;i++) {
                        Object value=item.constructorArgs[i]; String desc=types[i].getDescriptor();
                        if(value instanceof Field f && (f.desc.contains("ToolMaterial;")||f.desc.contains("ArmorMaterial;"))) value=null;
                        if(value!=null&&!(value instanceof String)&&!(value instanceof Number))
                            throw new Unsupported("nonconstant item constructor argument "+i);
                        args.add(new ConstructorArgument(desc,value));
                    }
                    boolean sword=isSubclass(type,"net/minecraft/item/ItemSword");
                    boolean inherited=sword;
                    for(String owner=type;classes.containsKey(owner);owner=classes.get(owner).parent) {
                        for(Method m:classes.get(owner).methods.values()) if(List.of("func_77659_a","onItemRightClick",
                                "func_77661_b","getItemUseAction","func_77626_a","getMaxItemUseDuration").contains(m.name)) inherited=false;
                    }
                    boolean full=sword;
                    Method fullMethod=find(type,"func_77662_d","()Z");
                    if(fullMethod==null)fullMethod=find(type,"isFull3D","()Z");
                    if(fullMethod!=null)full=((Number)run(fullMethod,item,new Object[0],null,0)).intValue()!=0;
                    Method rotation=find(type,"func_77629_n_","()Z");
                    if(rotation==null)rotation=find(type,"shouldRotateAroundWhenRendering","()Z");
                    boolean rotate=rotation!=null&&((Number)run(rotation,item,new Object[0],null,0)).intValue()!=0;
                    ItemAllocation binding=new ItemAllocation(item.itemName,type,item.constructorDesc,List.copyOf(args),inherited,full,rotate);
                    ItemAllocation previous=found.putIfAbsent(item.itemName,binding);
                    if(previous!=null&&!previous.equals(binding))conflicting.add(item.itemName);
                } catch(RuntimeException ex) {
                    String message=type+" in "+method.owner+"."+method.name+": "+ex.getMessage();
                    if(!diagnostics.contains(message)) diagnostics.add(message);
                }
            }
        }
        for(String name:conflicting){found.remove(name);diagnostics.add("Ambiguous item construction "+name);}
        return new ItemAnalysis(List.copyOf(found.values()),List.copyOf(diagnostics));
    }

    private boolean isSubclass(String type, String target) {
        for (int depth = 0; type != null && depth < 32; depth++) {
            if (type.equals(target)) return true;
            ClassInfo info = classes.get(type);
            if (info == null) return target.equals("net/minecraft/item/Item") && List.of(
                    "net/minecraft/item/ItemSword","net/minecraft/item/ItemArmor","net/minecraft/item/ItemTool",
                    "net/minecraft/item/ItemAxe","net/minecraft/item/ItemPickaxe","net/minecraft/item/ItemSpade",
                    "net/minecraft/item/ItemHoe","net/minecraft/item/ItemBow").contains(type);
            type = info.parent;
        }
        return false;
    }

    private record Field(String owner, String name, String desc) { }
    private record Call(String owner, String name, String desc) { }
    private record Insn(int op, Object data) { }
    private record Switch(int min, int[] keys, Label fallback, Label[] labels) { }
    private record Model(String path) { }
    private record Resource(String path) { }
    private record Registration(Field field, Instance instance) { }
    private static final class Instance {
        final String type;
        final Map<Field, Object> fields = new LinkedHashMap<>();
        Object value = UNKNOWN;
        String itemName;
        String constructorDesc;
        Object[] constructorArgs;
        int armorSlot = -1;
        Instance(String type) { this.type = type; }
    }
    private static final class Method {
        final int access;
        final String owner, name, desc;
        final List<Insn> code = new ArrayList<>();
        final Map<Label, Integer> labels = new LinkedHashMap<>();
        boolean hasExceptionHandlers;
        Method(int access, String owner, String name, String desc) {
            this.access = access; this.owner = owner; this.name = name; this.desc = desc;
        }
    }
    private static final class ClassInfo {
        String name, parent;
        List<String> interfaces = List.of();
        final Map<String, Method> methods = new LinkedHashMap<>();
    }
    private static final class Trace {
        final List<Operation> operations = new ArrayList<>();
        final List<List<Operation>> stack = new ArrayList<>();
        final List<Draw> draws = new ArrayList<>();
        String texture;
        boolean equipment;
        boolean lighting = true;
        boolean cull = true;
        final List<AnimatedOperation> animated = new ArrayList<>();
        final List<List<AnimatedOperation>> animatedStack = new ArrayList<>();
        final List<EquipmentDraw> equipmentDraws = new ArrayList<>();
    }
    private static final class Unsupported extends RuntimeException {
        Unsupported(String message) { super(message); }
    }

    private void loadSource(Path source) throws IOException {
        classes.clear(); statics.clear(); initialized.clear(); registrations.clear(); diagnostics.clear();
        try (JarFile jar = new JarFile(source.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                try (var input = jar.getInputStream(entry)) { read(new ClassReader(input)); }
                catch (RuntimeException ex) { diagnostics.add(entry.getName() + ": " + ex.getMessage()); }
            }
        }
    }

    public Analysis analyze(Path source) throws IOException {
        loadSource(source);
        for (ClassInfo info : classes.values()) for (Method method : info.methods.values()) {
            boolean registers = method.code.stream().anyMatch(i -> i.data instanceof Call c
                    && c.owner.equals(REGISTER_OWNER) && c.name.equals("registerItemRenderer"));
            if (!registers) continue;
            int start = registrations.size();
            try {
                Object[] args = new Object[Type.getArgumentTypes(method.desc).length];
                java.util.Arrays.fill(args, UNKNOWN);
                run(method, new Instance(info.name), args, null, 0);
            } catch (RuntimeException ex) {
                registrations.subList(start, registrations.size()).clear();
                diagnostics.add(method.owner + "." + method.name + ": " + ex.getMessage());
            }
        }
        List<Binding> bindings = new ArrayList<>();
        for (Registration registration : registrations) {
            Instance renderer = registration.instance;
            Map<String, Context> contexts = new LinkedHashMap<>();
            for (String type : List.of("INVENTORY", "ENTITY", "EQUIPPED", "EQUIPPED_FIRST_PERSON", "FIRST_PERSON_MAP")) {
                try {
                    Method accepts = find(renderer.type, "handleRenderType", "(Lnet/minecraft/item/ItemStack;L" + RENDER_TYPE + ";)Z");
                    if (accepts == null) throw new Unsupported("missing handleRenderType");
                    Object value = run(accepts, renderer, new Object[]{UNKNOWN, enumField(RENDER_TYPE, type)}, null, 0);
                    if (!(value instanceof Number result)) throw new Unsupported("dynamic handleRenderType");
                    if (result.intValue() == 0) {
                        contexts.put(type, new Context(false, List.of(), Map.of())); continue;
                    }
                    Map<String, Boolean> helpers = new LinkedHashMap<>();
                    Method helper = find(renderer.type, "shouldUseRenderHelper", "(L" + RENDER_TYPE + ";Lnet/minecraft/item/ItemStack;L" + HELPER + ";)Z");
                    if (helper == null) throw new Unsupported("missing shouldUseRenderHelper");
                    for (String name : List.of("ENTITY_BOBBING", "ENTITY_ROTATION", "BLOCK_3D", "EQUIPPED_BLOCK", "INVENTORY_BLOCK")) {
                        Object answer = run(helper, renderer, new Object[]{enumField(RENDER_TYPE, type), UNKNOWN, enumField(HELPER, name)}, null, 0);
                        if (!(answer instanceof Number number)) throw new Unsupported("dynamic render helper " + name);
                        helpers.put(name, number.intValue() != 0);
                    }
                    Method render = find(renderer.type, "renderItem", "(L" + RENDER_TYPE + ";Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V");
                    if (render == null) throw new Unsupported("missing renderItem");
                    Trace trace = new Trace();
                    run(render, renderer, new Object[]{enumField(RENDER_TYPE, type), UNKNOWN, UNKNOWN}, trace, 0);
                    if (!trace.stack.isEmpty()) throw new Unsupported("unbalanced matrix stack");
                    if (trace.draws.isEmpty()) throw new Unsupported("no proven OBJ draw");
                    contexts.put(type, new Context(true, trace.draws, helpers));
                } catch (RuntimeException ex) {
                    diagnostics.add(renderer.type + " [" + type + "]: " + ex.getMessage());
                }
            }
            bindings.add(new Binding(registration.field.owner, registration.field.name, renderer.type, contexts));
        }
        return new Analysis(bindings, diagnostics);
    }

    private void read(ClassReader reader) {
        ClassInfo info = new ClassInfo();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public void visit(int version, int access, String name, String signature, String parent, String[] interfaces) {
                info.name = name; info.parent = parent; info.interfaces = List.of(interfaces); classes.put(name, info);
            }
            @Override public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                if ((access & Opcodes.ACC_STATIC) != 0 && value != null) statics.put(new Field(info.name, name, desc), value);
                return null;
            }
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                Method m = new Method(access, info.name, name, desc); info.methods.put(name + desc, m);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitInsn(int op) { m.code.add(new Insn(op, null)); }
                    @Override public void visitIntInsn(int op, int value) { m.code.add(new Insn(op, value)); }
                    @Override public void visitVarInsn(int op, int local) { m.code.add(new Insn(op, local)); }
                    @Override public void visitTypeInsn(int op, String type) { m.code.add(new Insn(op, type)); }
                    @Override public void visitLdcInsn(Object value) { m.code.add(new Insn(Opcodes.LDC, value)); }
                    @Override public void visitFieldInsn(int op, String owner, String name, String desc) { m.code.add(new Insn(op, new Field(owner, name, desc))); }
                    @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) { m.code.add(new Insn(op, new Call(owner, name, desc))); }
                    @Override public void visitLabel(Label label) { m.labels.put(label, m.code.size()); }
                    @Override public void visitJumpInsn(int op, Label label) { m.code.add(new Insn(op, label)); }
                    @Override public void visitIincInsn(int local, int add) { m.code.add(new Insn(Opcodes.IINC, new int[]{local, add})); }
                    @Override public void visitTableSwitchInsn(int min, int max, Label fallback, Label... labels) { m.code.add(new Insn(Opcodes.TABLESWITCH, new Switch(min, null, fallback, labels))); }
                    @Override public void visitLookupSwitchInsn(Label fallback, int[] keys, Label[] labels) { m.code.add(new Insn(Opcodes.LOOKUPSWITCH, new Switch(0, keys, fallback, labels))); }
                    @Override public void visitTryCatchBlock(Label start, Label end, Label handler, String type) { m.hasExceptionHandlers = true; }
                    @Override public void visitMultiANewArrayInsn(String desc, int dimensions) { m.code.add(new Insn(Opcodes.MULTIANEWARRAY, null)); }
                    @Override public void visitInvokeDynamicInsn(String n, String d, org.objectweb.asm.Handle h, Object... a) { m.code.add(new Insn(Opcodes.INVOKEDYNAMIC, null)); }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private Method find(String owner, String name, String desc) {
        for (int depth = 0; owner != null && depth < 32; depth++) {
            ClassInfo info = classes.get(owner); if (info == null) return null;
            Method m = info.methods.get(name + desc); if (m != null) return m;
            owner = info.parent;
        }
        return null;
    }
    private boolean isRenderer(String type) {
        for (int depth = 0; type != null && depth < 32; depth++) {
            ClassInfo c = classes.get(type); if (c == null) return false;
            if (c.interfaces.contains(RENDERER)) return true; type = c.parent;
        }
        return false;
    }
    private Object readStatic(Field field, int depth) {
        if (statics.containsKey(field)) return statics.get(field);
        if (field.owner.equals(RENDER_TYPE) || field.owner.equals(HELPER)) return field;
        // Only initialise source renderer classes; ordinary item fields remain symbolic bindings.
        if ((isRenderer(field.owner) || isSubclass(field.owner, "net/minecraft/client/model/ModelBiped")) && initialized.add(field.owner)) {
            Method m = find(field.owner, "<clinit>", "()V");
            if (m != null) run(m, null, new Object[0], null, depth + 1);
        }
        return statics.getOrDefault(field, field);
    }

    private Object run(Method method, Instance self, Object[] args, Trace trace, int depth) {
        if (depth == 0) remainingSteps = BUDGET;
        if (depth > 32) throw new Unsupported("symbolic call depth limit");
        if (method.hasExceptionHandlers) throw new Unsupported("exception-dependent method");
        Map<Integer, Object> locals = new LinkedHashMap<>();
        int local = 0;
        if ((method.access & Opcodes.ACC_STATIC) == 0) locals.put(local++, self);
        Type[] parameters = Type.getArgumentTypes(method.desc);
        for (int i = 0; i < args.length; i++) { locals.put(local, args[i]); local += parameters[i].getSize(); }
        List<Object> stack = new ArrayList<>();
        for (int pc = 0; pc < method.code.size(); pc++) {
            if (--remainingSteps < 0) throw new Unsupported("symbolic instruction budget exceeded");
            Insn in = method.code.get(pc); int op = in.op;
            if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5) { stack.add(op - Opcodes.ICONST_0); continue; }
            if (op >= Opcodes.FCONST_0 && op <= Opcodes.FCONST_2) { stack.add((float) (op - Opcodes.FCONST_0)); continue; }
            if (op >= Opcodes.DCONST_0 && op <= Opcodes.DCONST_1) { stack.add((double) (op - Opcodes.DCONST_0)); continue; }
            if (op >= Opcodes.LCONST_0 && op <= Opcodes.LCONST_1) { stack.add((long) (op - Opcodes.LCONST_0)); continue; }
            if (op >= Opcodes.ILOAD && op <= Opcodes.ALOAD) { stack.add(locals.getOrDefault((int) in.data, UNKNOWN)); continue; }
            if (op >= Opcodes.ISTORE && op <= Opcodes.ASTORE) { locals.put((int) in.data, pop(stack)); continue; }
            switch (op) {
                case Opcodes.NOP -> { }
                case Opcodes.ACONST_NULL -> stack.add(null);
                case Opcodes.BIPUSH, Opcodes.SIPUSH, Opcodes.LDC -> stack.add(in.data);
                case Opcodes.POP -> pop(stack);
                case Opcodes.DUP -> stack.add(stack.getLast());
                case Opcodes.SWAP -> { Object a = pop(stack), b = pop(stack); stack.add(a); stack.add(b); }
                case Opcodes.NEW -> stack.add(new Instance((String) in.data));
                case Opcodes.CHECKCAST -> { }
                case Opcodes.GETSTATIC -> stack.add(readStatic((Field) in.data, depth));
                case Opcodes.PUTSTATIC -> {
                    if (!method.name.equals("<clinit>")) throw new Unsupported("runtime static-field mutation");
                    statics.put((Field) in.data, pop(stack));
                }
                case Opcodes.GETFIELD -> {
                    Object target = pop(stack); Field field = (Field) in.data;
                    if (field.desc.equals("Lnet/minecraft/client/renderer/texture/TextureManager;")) stack.add(new Instance("net/minecraft/client/renderer/texture/TextureManager"));
                    else stack.add(target instanceof Instance object ? object.fields.getOrDefault(field, UNKNOWN) : UNKNOWN);
                }
                case Opcodes.PUTFIELD -> {
                    if (!method.name.equals("<init>") && (trace == null || !trace.equipment)) throw new Unsupported("runtime instance-field mutation");
                    Object value = pop(stack), target = pop(stack);
                    if (!(target instanceof Instance object)) throw new Unsupported("unknown field receiver");
                    object.fields.put((Field) in.data, value);
                }
                case Opcodes.INVOKESPECIAL, Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESTATIC, Opcodes.INVOKEINTERFACE -> {
                    Call call = (Call) in.data; Type[] types = Type.getArgumentTypes(call.desc);
                    Object[] actual = new Object[types.length];
                    for (int i = actual.length - 1; i >= 0; i--) actual[i] = pop(stack);
                    Object receiver = op == Opcodes.INVOKESTATIC ? null : pop(stack);
                    Object result = invoke(call, receiver, actual, trace, depth, op);
                    if (Type.getReturnType(call.desc).getSort() != Type.VOID) stack.add(result);
                }
                case Opcodes.IADD, Opcodes.FADD, Opcodes.DADD, Opcodes.ISUB, Opcodes.FSUB, Opcodes.DSUB,
                        Opcodes.IMUL, Opcodes.FMUL, Opcodes.DMUL, Opcodes.IDIV, Opcodes.FDIV, Opcodes.DDIV -> {
                    Object b = pop(stack), a = pop(stack);
                    if ((a instanceof Expression || b instanceof Expression) && (op == Opcodes.FADD || op == Opcodes.FSUB || op == Opcodes.FMUL || op == Opcodes.FDIV)) {
                        stack.add(Expression.of(switch(op) { case Opcodes.FADD -> "add"; case Opcodes.FSUB -> "sub"; case Opcodes.FMUL -> "mul"; default -> "div"; }, a, b));
                    } else if (!(a instanceof Number x) || !(b instanceof Number y)) stack.add(UNKNOWN);
                    else {
                        Object value = switch (op) {
                            case Opcodes.IADD -> x.intValue() + y.intValue();
                            case Opcodes.ISUB -> x.intValue() - y.intValue();
                            case Opcodes.IMUL -> x.intValue() * y.intValue();
                            case Opcodes.IDIV -> x.intValue() / y.intValue();
                            case Opcodes.FADD -> x.floatValue() + y.floatValue();
                            case Opcodes.FSUB -> x.floatValue() - y.floatValue();
                            case Opcodes.FMUL -> x.floatValue() * y.floatValue();
                            case Opcodes.FDIV -> x.floatValue() / y.floatValue();
                            case Opcodes.DADD -> x.doubleValue() + y.doubleValue();
                            case Opcodes.DSUB -> x.doubleValue() - y.doubleValue();
                            case Opcodes.DMUL -> x.doubleValue() * y.doubleValue();
                            default -> x.doubleValue() / y.doubleValue();
                        };
                        stack.add(value);
                    }
                }
                case Opcodes.INEG, Opcodes.FNEG, Opcodes.DNEG -> {
                    Object a = pop(stack);
                    if (a instanceof Expression && op == Opcodes.FNEG) stack.add(Expression.of("neg", a));
                    else if (!(a instanceof Number n)) stack.add(UNKNOWN);
                    else { Object value = switch (op) {
                        case Opcodes.INEG -> -n.intValue();
                        case Opcodes.FNEG -> -n.floatValue();
                        default -> -n.doubleValue();
                    }; stack.add(value); }
                }
                case Opcodes.I2F, Opcodes.F2D, Opcodes.D2F, Opcodes.I2D -> {
                    Object a = pop(stack);
                    if (!(a instanceof Number n)) stack.add(UNKNOWN);
                    else if (op == Opcodes.I2F || op == Opcodes.D2F) stack.add(n.floatValue());
                    else stack.add(n.doubleValue());
                }
                case Opcodes.IINC -> { int[] a = (int[]) in.data; Object v = locals.get(a[0]); locals.put(a[0], v instanceof Number n ? n.intValue() + a[1] : UNKNOWN); }
                case Opcodes.GOTO -> pc = method.labels.get((Label) in.data) - 1;
                case Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE,
                        Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE,
                        Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE, Opcodes.IFNULL, Opcodes.IFNONNULL -> {
                    Object b = pop(stack);
                    Object a = op >= Opcodes.IF_ICMPEQ && op <= Opcodes.IF_ACMPNE ? pop(stack) : null;
                    if (branch(op, a, b)) pc = method.labels.get((Label) in.data) - 1;
                }
                case Opcodes.TABLESWITCH, Opcodes.LOOKUPSWITCH -> {
                    Object value = pop(stack); if (!(value instanceof Number n)) throw new Unsupported("dynamic switch");
                    Switch s = (Switch) in.data; int at = s.keys == null ? n.intValue() - s.min : -1;
                    if (s.keys != null) for (int i = 0; i < s.keys.length; i++) if (s.keys[i] == n.intValue()) at = i;
                    pc = method.labels.get(at >= 0 && at < s.labels.length ? s.labels[at] : s.fallback) - 1;
                }
                case Opcodes.IRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN, Opcodes.LRETURN -> { return pop(stack); }
                case Opcodes.RETURN -> { return null; }
                default -> throw new Unsupported("unsupported opcode " + op + " in " + method.owner + "." + method.name);
            }
        }
        throw new Unsupported("method has no proven return");
    }

    private Object invoke(Call call, Object receiver, Object[] args, Trace trace, int depth, int opcode) {
        String owner = call.owner, name = call.name;
        if (owner.equals(REGISTER_OWNER) && name.equals("registerItemRenderer")) {
            if (!(args[0] instanceof Field field) || !(args[1] instanceof Instance renderer) || !isRenderer(renderer.type)) {
                throw new Unsupported("unresolved renderer registration");
            }
            registrations.add(new Registration(field, renderer)); return null;
        }
        if (owner.equals("org/lwjgl/opengl/GL11")) {
            if (trace == null) throw new Unsupported("OpenGL outside render method");
            if (trace.equipment) {
                switch (name) {
                    case "glPushMatrix" -> trace.animatedStack.add(List.copyOf(trace.animated));
                    case "glPopMatrix" -> {
                        if (trace.animatedStack.isEmpty()) throw new Unsupported("armor matrix stack underflow");
                        var saved = trace.animatedStack.removeLast(); trace.animated.clear(); trace.animated.addAll(saved);
                    }
                    case "glTranslatef", "glScalef", "glRotatef" -> {
                        List<Expression> values = new ArrayList<>();
                        for (Object arg : args) {
                            if (arg instanceof Expression e) values.add(e);
                            else if (arg instanceof Number n && Float.isFinite(n.floatValue())) values.add(Expression.constant(n.floatValue()));
                            else throw new Unsupported("unproven armor transform " + name);
                        }
                        trace.animated.add(new AnimatedOperation(name.startsWith("glTranslate") ? "translate" : name.startsWith("glScale") ? "scale" : "rotate", values));
                    }
                    case "glEnable", "glDisable" -> {
                        if (!(args[0] instanceof Number flag)) throw new Unsupported("dynamic GL state");
                        if (flag.intValue() == 2896) trace.lighting = name.equals("glEnable");
                        else if (flag.intValue() == 2884) trace.cull = name.equals("glEnable");
                        else throw new Unsupported("unsupported GL flag " + flag);
                    }
                    default -> throw new Unsupported("unsupported equipment GL state " + name);
                }
                return null;
            }
            switch (name) {
                case "glPushMatrix" -> trace.stack.add(List.copyOf(trace.operations));
                case "glPopMatrix" -> {
                    if (trace.stack.isEmpty()) throw new Unsupported("matrix stack underflow");
                    var saved = trace.stack.removeLast(); trace.operations.clear(); trace.operations.addAll(saved);
                }
                case "glTranslatef", "glTranslated", "glScalef", "glScaled", "glRotatef", "glRotated" -> {
                    List<Float> values = new ArrayList<>();
                    for (Object arg : args) {
                        if (!(arg instanceof Number n) || !Float.isFinite(n.floatValue())) throw new Unsupported("dynamic/non-finite " + name);
                        values.add(n.floatValue());
                    }
                    trace.operations.add(new Operation(name.startsWith("glTranslate") ? "translate" : name.startsWith("glScale") ? "scale" : "rotate", values));
                }
                default -> throw new Unsupported("unsupported OpenGL state operation " + name);
            }
            return null;
        }
        if (owner.equals("net/minecraftforge/client/model/AdvancedModelLoader") && name.equals("loadModel")) {
            String path = resource(args[0]); if (path == null) throw new Unsupported("dynamic OBJ resource"); return new Model(path);
        }
        if (owner.equals("net/minecraftforge/client/model/IModelCustom")) {
            if (trace == null || !name.equals("renderAll") || !(receiver instanceof Model model) || trace.texture == null) {
                throw new Unsupported("unresolved OBJ draw/group selection");
            }
            if (trace.equipment) trace.equipmentDraws.add(new EquipmentDraw(model.path, trace.texture, trace.animated, trace.lighting, trace.cull));
            else trace.draws.add(new Draw(model.path, trace.texture, trace.operations));
            return null;
        }
        if (owner.equals("net/minecraft/client/renderer/texture/TextureManager")
                && (name.equals("bindTexture") || name.equals("func_110577_a"))) {
            if (trace == null) return null;
            trace.texture = resource(args[0]); if (trace.texture == null) throw new Unsupported("dynamic texture"); return null;
        }
        if (owner.equals("net/minecraft/client/Minecraft") && (name.equals("getMinecraft") || name.equals("func_71410_x"))) return new Instance(owner);
        if (receiver instanceof EntityState entity && owner.startsWith("net/minecraft/entity/")
                && (name.equals("isSneaking") || name.equals("func_70051_ag")) && call.desc.equals("()Z")) return entity.crouching ? 1 : 0;
        if (trace != null && trace.equipment && owner.equals("net/minecraft/util/MathHelper") && call.desc.equals("(F)F")) {
            if (name.equals("cos") || name.equals("func_76134_b")) return Expression.of("cos", args[0]);
            if (name.equals("sin") || name.equals("func_76126_a")) return Expression.of("sin", args[0]);
        }
        if (trace == null && receiver instanceof Instance item && isSubclass(item.type, "net/minecraft/item/Item")) {
            if ((name.equals("setUnlocalizedName") || name.equals("func_77655_b")) && args.length == 1 && args[0] instanceof String text) {
                item.itemName = text; return item;
            }
            if (List.of("setCreativeTab", "func_77637_a", "setTextureName", "func_111206_d", "setMaxDamage", "func_77656_e", "setMaxStackSize", "func_77625_d", "setFull3D", "func_77664_n").contains(name)) return item;
        }
        if (trace == null && owner.equals("java/util/List") && name.equals("add") && call.desc.equals("(Ljava/lang/Object;)Z")) return 1;
        if (trace==null && owner.equals("java/util/HashMap") && name.equals("put")) return null;
        if (name.equals("<init>")) {
            if (!(receiver instanceof Instance instance)) throw new Unsupported("unknown constructor receiver");
            if(owner.equals(instance.type)&&isSubclass(instance.type,"net/minecraft/item/Item")) {
                instance.constructorDesc=call.desc; instance.constructorArgs=args.clone();
            }
            if(owner.equals("net/minecraft/item/Item")||owner.equals("net/minecraft/item/ItemSword")) return null;
            if (owner.equals("java/lang/Object") || owner.equals("net/minecraft/client/model/ModelBiped") || owner.equals("net/minecraft/client/model/ModelBase")) return null;
            if (owner.equals("net/minecraft/item/ItemArmor") && args.length == 3 && args[2] instanceof Number n) {
                instance.armorSlot = n.intValue(); return null;
            }
            if (owner.equals("java/lang/StringBuilder") || owner.equals("java/lang/StringBuffer")) { instance.value = args.length == 0 ? "" : args[0]; return null; }
            if (owner.equals("net/minecraft/util/ResourceLocation")) {
                if (args.length == 1 && args[0] instanceof String text) instance.value = new Resource(text);
                else if (args.length == 2 && args[0] instanceof String ns && args[1] instanceof String path) instance.value = new Resource(ns + ":" + path);
                else throw new Unsupported("dynamic ResourceLocation");
                return null;
            }
            Method constructor = find(owner, name, call.desc);
            if (constructor == null) throw new Unsupported("unsupported constructor " + owner);
            return run(constructor, instance, args, trace, depth + 1);
        }
        if ((owner.equals("java/lang/StringBuilder") || owner.equals("java/lang/StringBuffer")) && receiver instanceof Instance instance) {
            if (name.equals("append")) {
                instance.value = instance.value instanceof String s && (args[0] instanceof String || args[0] instanceof Number) ? s + args[0] : UNKNOWN;
                return instance;
            }
            if (name.equals("toString")) return instance.value;
        }
        if (owner.equals("java/lang/String") && receiver instanceof String text) {
            if (name.equals("hashCode")) return text.hashCode();
            if (name.equals("equals") && args[0] != UNKNOWN) return text.equals(args[0]) ? 1 : 0;
            if (name.equals("concat") && args[0] instanceof String suffix) return text + suffix;
        }
        String dispatch = (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)
                && receiver instanceof Instance instance ? instance.type : owner;
        Method target = find(dispatch, name, call.desc);
        if (target != null) return run(target, receiver instanceof Instance i ? i : null, args, trace, depth + 1);
        throw new Unsupported("unsupported call " + owner + "." + name + call.desc);
    }
    private static String resource(Object value) {
        if (value instanceof Resource r) return r.path;
        return value instanceof Instance i && i.value instanceof Resource r ? r.path : null;
    }
    private static Field enumField(String owner, String name) { return new Field(owner, name, "L" + owner + ";"); }
    private static Object pop(List<Object> stack) {
        if (stack.isEmpty()) throw new Unsupported("symbolic stack underflow"); return stack.removeLast();
    }
    private static boolean branch(int op, Object a, Object b) {
        if (a == UNKNOWN || b == UNKNOWN) throw new Unsupported("dynamic branch; needs runtime adapter");
        if (op == Opcodes.IFNULL || op == Opcodes.IFNONNULL) {
            if (b instanceof Field f && !f.owner.equals(RENDER_TYPE) && !f.owner.equals(HELPER))
                throw new Unsupported("dynamic null comparison");
            return op == Opcodes.IFNULL ? b == null : b != null;
        }
        if (op == Opcodes.IF_ACMPEQ || op == Opcodes.IF_ACMPNE) {
            if (a instanceof Field f && !f.owner.equals(RENDER_TYPE) && !f.owner.equals(HELPER)) throw new Unsupported("dynamic reference comparison");
            if (b instanceof Field f && !f.owner.equals(RENDER_TYPE) && !f.owner.equals(HELPER)) throw new Unsupported("dynamic reference comparison");
            boolean equal = Objects.equals(a, b); return op == Opcodes.IF_ACMPEQ ? equal : !equal;
        }
        if (!(b instanceof Number y) || (a != null && !(a instanceof Number))) throw new Unsupported("dynamic numeric branch");
        int cmp = a instanceof Number x ? Double.compare(x.doubleValue(), y.doubleValue()) : Double.compare(y.doubleValue(), 0.0);
        return switch (op) {
            case Opcodes.IFEQ, Opcodes.IF_ICMPEQ -> cmp == 0;
            case Opcodes.IFNE, Opcodes.IF_ICMPNE -> cmp != 0;
            case Opcodes.IFLT, Opcodes.IF_ICMPLT -> cmp < 0;
            case Opcodes.IFGE, Opcodes.IF_ICMPGE -> cmp >= 0;
            case Opcodes.IFGT, Opcodes.IF_ICMPGT -> cmp > 0;
            case Opcodes.IFLE, Opcodes.IF_ICMPLE -> cmp <= 0;
            default -> throw new Unsupported("unsupported branch " + op);
        };
    }
}
