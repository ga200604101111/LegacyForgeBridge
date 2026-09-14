package dev.longyu.legacyforgebridge.convert;

import dev.longyu.legacyforgebridge.behavior.LegacyBehaviorApi;
import org.objectweb.asm.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/**
 * Reachability-limited legacy callback compiler. Copies only validated method bodies, remapping
 * every legacy API boundary to the narrow native adapter API. Unknown dependencies reject the
 * callback, not the whole source mod. Original classes are never loaded or executed by analysis.
 */
public final class LegacyBehaviorCompiler {
    public static final String API="dev/longyu/legacyforgebridge/behavior/LegacyBehaviorApi";
    public static final String REG="dev/longyu/legacyforgebridge/behavior/LegacyBehaviorRegistry";
    private static final Map<String,String> TYPES=Map.ofEntries(
            Map.entry("net/minecraft/item/Item","Item"),Map.entry("net/minecraft/item/ItemSword","Sword"),
            Map.entry("net/minecraft/item/ItemArmor","Armor"),Map.entry("net/minecraft/item/ItemBow","Bow"),
            Map.entry("net/minecraft/item/ItemTool","Tool"),Map.entry("net/minecraft/item/ItemPickaxe","Pickaxe"),
            Map.entry("net/minecraft/item/ItemAxe","Axe"),Map.entry("net/minecraft/item/ItemSpade","Spade"),Map.entry("net/minecraft/item/ItemHoe","Hoe"),
            Map.entry("net/minecraft/item/ItemBlock","ItemBlock"),Map.entry("net/minecraft/block/Block","Block"),
            Map.entry("net/minecraft/item/ItemStack","Stack"),
            Map.entry("net/minecraft/item/Item$ToolMaterial","Material"),Map.entry("net/minecraft/item/ItemArmor$ArmorMaterial","Material"),
            Map.entry("net/minecraft/creativetab/CreativeTabs","CreativeTab"),Map.entry("net/minecraft/nbt/NBTTagCompound","Tag"),Map.entry("net/minecraft/nbt/NBTTagList","TagList"),
            Map.entry("net/minecraft/entity/Entity","Entity"),Map.entry("net/minecraft/entity/EntityLivingBase","Living"),
            Map.entry("net/minecraft/entity/player/EntityPlayer","Player"),Map.entry("net/minecraft/world/World","World"),
            Map.entry("net/minecraft/potion/Potion","Potion"),Map.entry("net/minecraft/potion/PotionEffect","Effect"),
            Map.entry("net/minecraft/util/DamageSource","Damage"),Map.entry("net/minecraft/client/resources/I18n","I18n"),Map.entry("net/minecraft/util/StatCollector","StatCollector"),
            Map.entry("net/minecraft/item/EnumAction","UseAction"),
            Map.entry("net/minecraft/entity/player/InventoryPlayer","Inventory"),
            Map.entry("net/minecraft/util/IChatComponent","Chat"),Map.entry("net/minecraft/util/ChatComponentText","Text"),
            Map.entry("net/minecraft/util/AxisAlignedBB","Box"),Map.entry("net/minecraft/util/Vec3","Vec"),
            Map.entry("net/minecraft/entity/effect/EntityLightningBolt","Lightning"),Map.entry("net/minecraft/entity/item/EntityItem","Drop"),
            Map.entry("net/minecraft/entity/SharedMonsterAttributes","Attributes"),
            Map.entry("net/minecraft/entity/ai/attributes/IAttribute","Attribute"),Map.entry("net/minecraft/entity/ai/attributes/AttributeModifier","Modifier"),
            Map.entry("net/minecraftforge/event/entity/living/LivingEvent$LivingJumpEvent","Event"),
            Map.entry("net/minecraftforge/event/entity/living/LivingFallEvent","Event"),
            Map.entry("net/minecraftforge/event/entity/living/LivingHurtEvent","Event"),
            Map.entry("net/minecraftforge/event/entity/EntityEvent","Event"),
            Map.entry("net/minecraftforge/event/entity/PlaySoundAtEntityEvent","Event"));
    private static final Set<String> JDK=Set.of("java/lang/Object","java/lang/String","java/lang/StringBuilder","java/lang/StringBuffer",
            "java/lang/Integer","java/lang/Long","java/lang/Short","java/lang/Float","java/lang/Double","java/lang/Boolean","java/lang/Math",
            "java/util/UUID","com/google/common/collect/Multimap","java/util/List","java/util/ArrayList","java/util/HashMap","java/util/Map","java/util/Iterator","java/util/Random","java/util/Collection");
    private static final Map<String,String> NAMES=Map.ofEntries(
            Map.entry("addInformation","func_77624_a"),Map.entry("onUpdate","func_77663_a"),
            Map.entry("onItemRightClick","func_77659_a"),Map.entry("getItemUseAction","func_77661_b"),
            Map.entry("getMaxItemUseDuration","func_77626_a"),Map.entry("onPlayerStoppedUsing","func_77615_a"),Map.entry("hitEntity","func_77644_a"));
    private final Map<String,Clazz> classes=new LinkedHashMap<>();
    private final Set<Ref> selected=new LinkedHashSet<>(),fields=new LinkedHashSet<>();
    private final Set<String> included=new LinkedHashSet<>(),syntheticParentConstructors=new LinkedHashSet<>(),syntheticNameConstructors=new LinkedHashSet<>();
    private final Map<Ref,LegacyConstantNameTableAnalyzer.Table> constantNameTables=new LinkedHashMap<>();
    private final List<String> diagnostics=new ArrayList<>();
    private String prefix;
    public record ItemBinding(String id,String sourceClass,Set<String> hooks,LegacyItemRenderAnalyzer.ItemAllocation allocation) {
        public ItemBinding { hooks=Collections.unmodifiableSet(new TreeSet<>(hooks)); }
    }
    public record EventBinding(String owner,String method,String descriptor,String kind,String targetItemId) { }
    public record Result(Map<String,byte[]> classes,List<ItemBinding> items,List<EventBinding> events,List<String> diagnostics) { }
    private record Ref(String owner,String name,String desc) { }
    private record FieldInfo(int access,Object value) { }
    private static final class MethodInfo {
        int access; boolean subscribe,unsupportedAnnotation; final List<Ref> calls=new ArrayList<>(),fields=new ArrayList<>();
        final List<String> types=new ArrayList<>(); final List<Object> ops=new ArrayList<>(); String invalid;
    }
    private static final class Clazz {
        byte[] bytes;String parent;final Map<Ref,MethodInfo> methods=new LinkedHashMap<>();final Map<Ref,FieldInfo> fields=new LinkedHashMap<>();
    }
    public Result compile(Path source,String mod,String bootstrap,Map<String,String> itemIds) throws IOException {
        return compile(source,mod,bootstrap,itemIds,List.of());
    }
    public Result compile(Path source,String mod,String bootstrap,Map<String,String> itemIds,List<LegacyItemRenderAnalyzer.ItemAllocation> provenAllocations) throws IOException {
        classes.clear();selected.clear();fields.clear();included.clear();syntheticParentConstructors.clear();syntheticNameConstructors.clear();constantNameTables.clear();diagnostics.clear();
        prefix=bootstrap+"Source/";
        var constantAnalysis=new LegacyConstantNameTableAnalyzer().analyze(source);
        diagnostics.addAll(constantAnalysis.diagnostics());
        for(var table:constantAnalysis.tables())constantNameTables.put(new Ref(table.field().owner(),table.field().name(),table.field().descriptor()),table);
        LegacyEventAnalyzer.Analysis eventAnalysis=new LegacyEventAnalyzer().analyze(source);
        diagnostics.addAll(eventAnalysis.diagnostics());
        Map<String,Set<String>> selfRegisterCtors=new LinkedHashMap<>();
        for(LegacyEventAnalyzer.Binding binding:eventAnalysis.bindings()) {
            if(binding.handlerClass().equals(binding.registrationOwner())&&"<init>".equals(binding.registrationMethod()))
                selfRegisterCtors.computeIfAbsent(binding.handlerClass(),ignored->new LinkedHashSet<>()).add(binding.registrationDescriptor());
        }
        LegacySelfRegistrationStripper stripper=new LegacySelfRegistrationStripper();
        try(JarFile jar=new JarFile(source.toFile())) {
            var entries=jar.entries();while(entries.hasMoreElements()) {var e=entries.nextElement();
                if(!e.getName().endsWith(".class"))continue;
                String className=e.getName().substring(0,e.getName().length()-6);
                try(InputStream input=jar.getInputStream(e)){
                    byte[] bytes=input.readAllBytes();Set<String> constructors=selfRegisterCtors.getOrDefault(className,Set.of());
                    if(!constructors.isEmpty()) {
                        var stripped=stripper.strip(bytes,constructors);
                        if(stripped.strippedSites()==0) diagnostics.add("Self-registration source site not safely strippable for "+className+" constructors "+constructors);
                        else bytes=stripped.bytes();
                    }
                    readClass(bytes);
                }
            }
        }
        var itemAnalysis=new LegacyItemRenderAnalyzer().analyzeItems(source);
        diagnostics.addAll(itemAnalysis.diagnostics());List<ItemBinding> items=new ArrayList<>();
        LinkedHashMap<String,LegacyItemRenderAnalyzer.ItemAllocation> allocations=new LinkedHashMap<>();
        for(var a:itemAnalysis.items())allocations.put(a.itemName(),a);
        for(var a:provenAllocations)allocations.putIfAbsent(a.itemName(),a);
        for(var a:allocations.values()) {
            String id=itemIds.get(a.itemName());if(id==null)continue;
            if(!admit(new Ref(a.itemClass(),"<init>",a.constructorDescriptor()),"constructor "+id))continue;
            Set<String> hooks=new LinkedHashSet<>();
            root(a.itemClass(),"func_77624_a","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Ljava/util/List;Z)V","tooltip",hooks);
            root(a.itemClass(),"func_77663_a","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;IZ)V","tick",hooks);
            root(a.itemClass(),"onArmorTick","(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;)V","armorTick",hooks);
            root(a.itemClass(),"func_77661_b","(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/EnumAction;","action",hooks);
            root(a.itemClass(),"func_77626_a","(Lnet/minecraft/item/ItemStack;)I","duration",hooks);
            // All admitted callbacks retain source branches; remote legacy server effects are never duplicated.
            root(a.itemClass(),"func_77659_a","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;","use",hooks);
            root(a.itemClass(),"func_77615_a","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;I)V","release",hooks);
            root(a.itemClass(),"func_77644_a","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/entity/EntityLivingBase;)Z","hit",hooks);
            if(a.inheritedSwordBlocking()){hooks.add("use");hooks.add("action");hooks.add("duration");}
            if(isSubclass(a.itemClass(),"net/minecraft/item/ItemBow")){hooks.add("use");hooks.add("action");hooks.add("duration");hooks.add("release");}
            if(hooks.isEmpty())hooks.add("identity");
            items.add(new ItemBinding(id,a.itemClass(),Set.copyOf(hooks),a));
        }

        List<EventBinding> events=new ArrayList<>();
        Set<String> eventKeys=new LinkedHashSet<>();
        LegacyEventHandlerConstructionAnalyzer constructionAnalyzer=new LegacyEventHandlerConstructionAnalyzer();
        for(LegacyEventAnalyzer.Binding binding:eventAnalysis.bindings()) {
            String type=binding.handlerClass();
            if(!classes.containsKey(type))continue;
            Ref r=new Ref(type,binding.method(),binding.descriptor());
            MethodInfo m=classes.get(type).methods.get(r);if(m==null)continue;
            String kind=switch(binding.eventType()) {
                case "net/minecraftforge/event/entity/living/LivingEvent$LivingJumpEvent"->"jump";
                case "net/minecraftforge/event/entity/living/LivingFallEvent"->"fall";
                case "net/minecraftforge/event/entity/living/LivingHurtEvent"->"hurt";
                case "net/minecraftforge/event/entity/PlaySoundAtEntityEvent"->"sound";
                default->null;
            };
            if(kind==null){
                diagnostics.add("Event "+binding.eventType()+" on "+type+"."+binding.method()+binding.descriptor()
                        +" is source-proven as "+LegacyEventPolicy.execution(binding.eventType())+" but has no callback runtime adapter yet");
                continue;
            }
            if(!"NORMAL".equals(binding.priority())||binding.receiveCanceled()){
                diagnostics.add("Unsupported event annotation settings "+r+" priority="+binding.priority()+" receiveCanceled="+binding.receiveCanceled());
                continue;
            }
            String eventKey=type+'\u0000'+binding.method()+'\u0000'+binding.descriptor()+'\u0000'+kind;
            if(!eventKeys.add(eventKey))continue;

            boolean instance=(m.access&Opcodes.ACC_STATIC)==0;
            List<ItemBinding> itemTargets=instance&&binding.handlerClass().equals(binding.registrationOwner())
                    &&"<init>".equals(binding.registrationMethod())
                    ?items.stream().filter(item->item.sourceClass().equals(type)
                    &&item.allocation().constructorDescriptor().equals(binding.registrationDescriptor())).toList():List.of();
            if(!itemTargets.isEmpty()){
                if(!admit(r,"event "+kind))continue;
                for(ItemBinding itemTarget:itemTargets)
                    events.add(new EventBinding(type,r.name,r.desc,kind,itemTarget.id()));
                continue;
            }

            LegacyEventHandlerConstructionAnalyzer.Strategy construction=null;
            if(instance){
                var plan=constructionAnalyzer.analyze(source,type,"()V");construction=plan.strategy();
                if(construction==LegacyEventHandlerConstructionAnalyzer.Strategy.SOURCE_CONSTRUCTOR){
                    if(!admit(new Ref(type,"<init>","()V"),"event constructor"))continue;
                }else if(construction==LegacyEventHandlerConstructionAnalyzer.Strategy.SYNTHESIZE_PARENT_ONLY){
                    Clazz handler=classes.get(type);
                    if(handler.parent==null||!admit(new Ref(handler.parent,"<init>","()V"),"event parent constructor"))continue;
                }else{
                    diagnostics.add("event constructor "+type+": "+plan.diagnostic());continue;
                }
            }
            if(admit(r,"event "+kind)){
                if(construction==LegacyEventHandlerConstructionAnalyzer.Strategy.SYNTHESIZE_PARENT_ONLY)syntheticParentConstructors.add(type);
                events.add(new EventBinding(type,r.name,r.desc,kind,null));
            }
        }
        Map<String,byte[]> output=new LinkedHashMap<>();
        for(String type:included)output.put(mapped(type)+".class",emit(type));
        for(int i=0;i<events.size();i++) output.put(bootstrap+"Event"+i+".class",eventAdapter(bootstrap+"Event"+i,events.get(i)));
        output.put(bootstrap+".class",bootstrap(bootstrap,mod,items,events));
        verifyGenerated(output);
        return new Result(Map.copyOf(output),List.copyOf(items),List.copyOf(events),List.copyOf(new LinkedHashSet<>(diagnostics)));
    }
    private static void verifyGenerated(Map<String,byte[]> output) {
        ClassLoader verifier=new ClassLoader(LegacyBehaviorCompiler.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                synchronized(getClassLoadingLock(name)) {
                    byte[] bytes=output.get(name.replace('.','/')+".class");
                    if(bytes==null)return super.loadClass(name,resolve);
                    Class<?> type=findLoadedClass(name);
                    if(type==null)type=defineClass(name,bytes,0,bytes.length);
                    if(resolve)resolveClass(type);return type;
                }
            }
        };
        for(String path:output.keySet())try {
            // Inspection forces JVM verification without running constructors or class initializers.
            Class<?> type=Class.forName(path.substring(0,path.length()-6).replace('/','.'),false,verifier);
            type.getDeclaredMethods();type.getDeclaredConstructors();
        }catch(ReflectiveOperationException|LinkageError invalid){throw new IllegalArgumentException("Generated callback failed JVM verification: "+path,invalid);}
    }
    private void root(String owner,String name,String desc,String hook,Set<String> hooks) {
        Ref r=resolve(new Ref(owner,name,desc));
        if(r!=null&&classes.containsKey(r.owner)&&admit(r,owner+" "+hook))hooks.add(hook);
    }
    private boolean admit(Ref root,String label) {
        Set<Ref> oldMethods=new LinkedHashSet<>(selected),oldFields=new LinkedHashSet<>(fields);Set<String> oldClasses=new LinkedHashSet<>(included);
        try{validate(root);return true;}catch(RuntimeException ex){selected.clear();selected.addAll(oldMethods);fields.clear();fields.addAll(oldFields);included.clear();included.addAll(oldClasses);diagnostics.add(label+": "+ex.getMessage());return false;}
    }
    private void readClass(byte[] bytes) {
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            String owner;Clazz c;
            @Override public void visit(int v,int a,String n,String s,String parent,String[]interfaces){owner=n;c=new Clazz();c.bytes=bytes;c.parent=parent;classes.put(n,c);}
            @Override public FieldVisitor visitField(int a,String n,String d,String s,Object value){c.fields.put(new Ref(owner,n,d),new FieldInfo(a,value));return null;}
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[]exceptions){
                MethodInfo m=new MethodInfo();m.access=access;c.methods.put(new Ref(owner,name,desc),m);
                if((access&(Opcodes.ACC_NATIVE|Opcodes.ACC_SYNCHRONIZED|Opcodes.ACC_ABSTRACT))!=0)m.invalid="native/synchronized method";
                return new MethodVisitor(Opcodes.ASM9){
                    @Override public AnnotationVisitor visitAnnotation(String d,boolean visible){if(d.endsWith("/SubscribeEvent;")){m.subscribe=true;return new AnnotationVisitor(Opcodes.ASM9){
                        @Override public void visit(String n,Object value){if(n.equals("receiveCanceled")&&Boolean.TRUE.equals(value))m.unsupportedAnnotation=true;}
                        @Override public void visitEnum(String n,String d,String value){if(n.equals("priority")&&!value.equals("NORMAL"))m.unsupportedAnnotation=true;}
                    };}return null;}
                    @Override public void visitMethodInsn(int op,String o,String n,String d,boolean itf){Ref r=new Ref(o,n,d);m.calls.add(r);m.ops.add(r);}
                    @Override public void visitFieldInsn(int op,String o,String n,String d){Ref r=new Ref(o,n,d);m.fields.add(r);m.ops.add(List.of(op,r));}
                    @Override public void visitTypeInsn(int op,String t){m.types.add(t);m.ops.add(List.of(op,t));}
                    @Override public void visitInsn(int op){m.ops.add(op);if(op==Opcodes.MONITORENTER||op==Opcodes.MONITOREXIT)m.invalid="monitor operation";}
                    @Override public void visitInvokeDynamicInsn(String n,String d,Handle b,Object...a){m.invalid="invokedynamic requires an explicit adapter";}
                    @Override public void visitLdcInsn(Object v){if(v instanceof Type||v instanceof Handle||v instanceof ConstantDynamic)m.invalid="reflective/dynamic constant";m.ops.add(v);}
                };
            }
        },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
    }
    private static String canonical(String name){return NAMES.getOrDefault(name,name);}
    private Ref resolve(Ref r) {
        for(String type=r.owner;type!=null;){Clazz c=classes.get(type);if(c==null)return new Ref(type,canonical(r.name),r.desc);
            for(Ref key:c.methods.keySet())if(canonical(key.name).equals(canonical(r.name))&&key.desc.equals(r.desc))return key;
            if(r.name.equals("<init>"))return null;type=c.parent;
        }return null;
    }
    private void include(String type){
        if(!classes.containsKey(type)){mapped(type);return;}
        if(included.add(type))include(classes.get(type).parent);
    }
    private void validate(Ref requested) {
        Ref r=resolve(requested);if(r==null)throw new IllegalArgumentException("Unresolved method "+requested);
        if(!classes.containsKey(r.owner)){externalMethod(r);return;}
        if(!selected.add(r))return;
        if(selected.size()>512)throw new IllegalArgumentException("Callback dependency budget exceeded");
        include(r.owner);MethodInfo m=classes.get(r.owner).methods.get(r);if(m.invalid!=null)throw new IllegalArgumentException(m.invalid+" in "+r);
        descriptor(r.desc);
        for(String t:m.types){mapped(t);if(classes.containsKey(t))include(t);}
        for(Ref f:m.fields)field(f);
        for(Ref call:m.calls)validate(call);
    }
    private void field(Ref r) {
        if(opaque(r.desc))return;
        descriptor(r.desc);
        if(classes.containsKey(r.owner)){
            Clazz c=classes.get(r.owner);FieldInfo info=c.fields.get(r);
            if(info==null) {
                if(c.parent==null)throw new IllegalArgumentException("Unknown source field "+r);
                include(r.owner);field(new Ref(c.parent,r.name,r.desc));return;
            }
            include(r.owner);fields.add(r);
            var table=constantNameTables.get(r);
            if(table!=null){
                Clazz value=classes.get(table.valueType());
                Ref nameField=new Ref(table.valueType(),table.nameField(),"Ljava/lang/String;");
                FieldInfo nameInfo=value==null?null:value.fields.get(nameField);
                if(value==null||!"java/lang/Object".equals(value.parent)||nameInfo==null||(nameInfo.access&Opcodes.ACC_STATIC)!=0)
                    throw new IllegalArgumentException("Unsupported constant name-table value type "+table.valueType());
                include(table.valueType());fields.add(nameField);syntheticNameConstructors.add(table.valueType());
            }
            if((info.access&Opcodes.ACC_STATIC)!=0&&info.value==null&&collectionInitializer(r)==null)
                throw new IllegalArgumentException("Unproven static field initializer "+r);
        }else {
            String owner=mapped(r.owner);if(JDK.contains(r.owner))return;
            try {var field=Class.forName(owner.replace('/','.')).getField(r.name);if(!Type.getDescriptor(field.getType()).equals(descriptor(r.desc)))throw new NoSuchFieldException();}
            catch(ReflectiveOperationException ex){throw new IllegalArgumentException("Unsupported API field "+r);}
        }
    }
    private String collectionInitializer(Ref field) {
        MethodInfo m=classes.get(field.owner).methods.get(new Ref(field.owner,"<clinit>","()V"));if(m==null)return null;
        for(int i=3;i<m.ops.size();i++)if(m.ops.get(i).equals(List.of(Opcodes.PUTSTATIC,field))){
            if(m.ops.get(i-1) instanceof Ref ctor&&ctor.name.equals("<init>")&&ctor.desc.equals("()V")
                    &&Set.of("java/util/HashMap","java/util/ArrayList").contains(ctor.owner)
                    &&m.ops.get(i-2).equals(Opcodes.DUP)&&m.ops.get(i-3).equals(List.of(Opcodes.NEW,ctor.owner)))return ctor.owner;
        }return null;
    }
    private static boolean opaque(String desc){return desc.equals("Lnet/minecraft/creativetab/CreativeTabs;")||desc.equals("Lnet/minecraft/item/Item$ToolMaterial;")||desc.equals("Lnet/minecraft/item/ItemArmor$ArmorMaterial;");}
    private void externalMethod(Ref r) {
        descriptor(r.desc);String owner=mapped(r.owner);
        if(JDK.contains(r.owner)){
            if(r.owner.equals("java/lang/Object")&&!r.name.equals("<init>"))throw new IllegalArgumentException("Unsupported Object operation "+r.name);
            return;
        }
        try {
            Class<?> c=Class.forName(owner.replace('/','.'));List<Class<?>>args=new ArrayList<>();
            for(Type t:Type.getArgumentTypes(descriptor(r.desc)))args.add(javaType(t));
            if(r.name.equals("<init>"))c.getConstructor(args.toArray(Class<?>[]::new));
            else c.getMethod(canonical(r.name),args.toArray(Class<?>[]::new));
        }catch(ReflectiveOperationException ex){throw new IllegalArgumentException("Unsupported API method "+r);}
    }
    private static Class<?> javaType(Type t)throws ClassNotFoundException{return switch(t.getSort()){
        case Type.BOOLEAN->boolean.class;case Type.BYTE->byte.class;case Type.CHAR->char.class;case Type.SHORT->short.class;case Type.INT->int.class;case Type.FLOAT->float.class;case Type.LONG->long.class;case Type.DOUBLE->double.class;
        case Type.ARRAY->Class.forName(t.getDescriptor().replace('/','.'));default->Class.forName(t.getClassName());};}
    private boolean isSubclass(String type,String parent){
        for(String current=type;current!=null;){
            if(current.equals(parent))return true;
            Clazz c=classes.get(current);if(c==null)return false;
            current=c.parent;
        }
        return false;
    }
    private String mapped(String type){
        if(type==null)return null;if(type.startsWith("["))return descriptor(type);
        if(classes.containsKey(type))return prefix+type;
        String api=TYPES.get(type);if(api!=null)return API+"$"+api;
        if(JDK.contains(type))return type;
        throw new IllegalArgumentException("Unsupported API type "+type);
    }
    private String descriptor(String desc){
        Type t=Type.getType(desc);return switch(t.getSort()){
            case Type.OBJECT->"L"+mapped(t.getInternalName())+";";
            case Type.ARRAY->"[".repeat(t.getDimensions())+descriptor(t.getElementType().getDescriptor());
            case Type.METHOD->{StringBuilder b=new StringBuilder("(");for(Type a:t.getArgumentTypes())b.append(descriptor(a.getDescriptor()));yield b.append(')').append(descriptor(t.getReturnType().getDescriptor())).toString();}
            default->desc;};
    }
    private ClassWriter writer(){return new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){
        @Override protected String getCommonSuperClass(String a,String b){
            if(a.equals(b))return a;Set<String> parents=new LinkedHashSet<>();for(String p=a;p!=null;p=parent(p))parents.add(p);
            for(String p=b;p!=null;p=parent(p))if(parents.contains(p))return p;return "java/lang/Object";
        }
    };}
    private String parent(String name){
        if(name.equals("java/lang/Object"))return null;
        if(name.startsWith(prefix))return mapped(classes.get(name.substring(prefix.length())).parent);
        try{Class<?>c=Class.forName(name.replace('/','.'));return c.getSuperclass()==null?"java/lang/Object":Type.getInternalName(c.getSuperclass());}
        catch(ClassNotFoundException ex){return "java/lang/Object";}
    }
    private byte[] emit(String type){
        Clazz c=classes.get(type);ClassWriter w=writer();w.visit(Opcodes.V21,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,mapped(type),null,mapped(c.parent),null);
        List<Ref> staticCollections=new ArrayList<>();
        for(Ref f:fields)if(f.owner.equals(type)){
            FieldInfo info=c.fields.get(f);w.visitField(info.access&~Opcodes.ACC_FINAL,f.name,descriptor(f.desc),null,info.value).visitEnd();
            if((info.access&Opcodes.ACC_STATIC)!=0&&info.value==null)staticCollections.add(f);
        }
        if(!staticCollections.isEmpty()){
            MethodVisitor m=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);m.visitCode();
            for(Ref f:staticCollections){
                String init=collectionInitializer(f);m.visitTypeInsn(Opcodes.NEW,init);m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,init,"<init>","()V",false);m.visitFieldInsn(Opcodes.PUTSTATIC,mapped(type),f.name,descriptor(f.desc));
                var table=constantNameTables.get(f);
                if(table!=null)for(var entry:table.entries()){
                    m.visitFieldInsn(Opcodes.GETSTATIC,mapped(type),f.name,descriptor(f.desc));
                    m.visitLdcInsn((int)entry.id());m.visitInsn(Opcodes.I2S);m.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Short","valueOf","(S)Ljava/lang/Short;",false);
                    m.visitTypeInsn(Opcodes.NEW,mapped(table.valueType()));m.visitInsn(Opcodes.DUP);m.visitLdcInsn(entry.name());m.visitMethodInsn(Opcodes.INVOKESPECIAL,mapped(table.valueType()),"<init>","(Ljava/lang/String;)V",false);
                    m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/util/HashMap","put","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false);m.visitInsn(Opcodes.POP);
                }
            }
            m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        }
        if(syntheticParentConstructors.contains(type)){
            MethodVisitor constructor=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);constructor.visitCode();
            constructor.visitVarInsn(Opcodes.ALOAD,0);constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,mapped(c.parent),"<init>","()V",false);
            constructor.visitInsn(Opcodes.RETURN);constructor.visitMaxs(0,0);constructor.visitEnd();
        }
        if(syntheticNameConstructors.contains(type)){
            var table=constantNameTables.values().stream().filter(value->value.valueType().equals(type)).findFirst().orElseThrow();
            MethodVisitor constructor=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Ljava/lang/String;)V",null,null);constructor.visitCode();
            constructor.visitVarInsn(Opcodes.ALOAD,0);constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
            constructor.visitVarInsn(Opcodes.ALOAD,0);constructor.visitVarInsn(Opcodes.ALOAD,1);constructor.visitFieldInsn(Opcodes.PUTFIELD,mapped(type),table.nameField(),"Ljava/lang/String;");
            constructor.visitInsn(Opcodes.RETURN);constructor.visitMaxs(0,0);constructor.visitEnd();
        }
        new ClassReader(c.bytes).accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String sig,String[]exceptions){
                if(!selected.contains(new Ref(type,n,d)))return null;
                MethodVisitor out=w.visitMethod((a&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC,canonical(n),descriptor(d),null,null);
                return new MethodVisitor(Opcodes.ASM9,out){
                    @Override public AnnotationVisitor visitAnnotation(String d,boolean v){return null;}
                    @Override public void visitCode(){super.visitCode();budget();}
                    private void budget(){super.visitMethodInsn(Opcodes.INVOKESTATIC,API,"step","()V",false);}
                    @Override public void visitLabel(Label l){super.visitLabel(l);budget();}
                    @Override public void visitTypeInsn(int op,String t){super.visitTypeInsn(op,mapped(t));}
                    @Override public void visitFieldInsn(int op,String o,String n,String d){
                        if(opaque(d)&&op==Opcodes.GETSTATIC){super.visitInsn(Opcodes.ACONST_NULL);return;}
                        super.visitFieldInsn(op,mapped(o),n,descriptor(d));
                    }
                    @Override public void visitMethodInsn(int op,String o,String n,String d,boolean itf){
                        Ref target=resolve(new Ref(o,n,d));String owner=target==null?o:target.owner;
                        super.visitMethodInsn(op,mapped(owner),canonical(n),descriptor(d),itf);
                    }
                    @Override public void visitTryCatchBlock(Label s,Label e,Label h,String t){super.visitTryCatchBlock(s,e,h,t==null?null:mapped(t));}
                    @Override public void visitLocalVariable(String n,String d,String sig,Label s,Label e,int i){ }
                };
            }
        },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        w.visitEnd();return w.toByteArray();
    }
    private byte[] eventAdapter(String name,EventBinding binding){
        ClassWriter w=writer();w.visit(Opcodes.V21,Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL,name,null,"java/lang/Object",new String[]{API+"$EventProgram"});
        boolean stat=(classes.get(binding.owner).methods.get(new Ref(binding.owner,binding.method,binding.descriptor)).access&Opcodes.ACC_STATIC)!=0;
        String receiver=mapped(binding.owner);w.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_FINAL,"target","L"+receiver+";",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(L"+receiver+";)V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,1);c.visitFieldInsn(Opcodes.PUTFIELD,name,"target","L"+receiver+";");c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"run","(L"+API+"$Event;)V",null,null);m.visitCode();if(!stat){m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,name,"target","L"+receiver+";");}m.visitVarInsn(Opcodes.ALOAD,1);m.visitMethodInsn(stat?Opcodes.INVOKESTATIC:Opcodes.INVOKEVIRTUAL,receiver,binding.method,descriptor(binding.descriptor),false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private byte[] bootstrap(String name,String mod,List<ItemBinding> items,List<EventBinding> events){
        ClassWriter w=writer();w.visit(Opcodes.V21,Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL,name,null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"initialize","()V",null,null);m.visitCode();m.visitLdcInsn(mod);m.visitMethodInsn(Opcodes.INVOKESTATIC,REG,"begin","(Ljava/lang/String;)Z",false);Label done=new Label();m.visitJumpInsn(Opcodes.IFEQ,done);
        Label start=new Label(),end=new Label(),failed=new Label();
        m.visitTryCatchBlock(start,end,failed,"java/lang/Throwable");m.visitLabel(start);
        for(ItemBinding item:items){var a=item.allocation;m.visitLdcInsn(item.id);m.visitTypeInsn(Opcodes.NEW,mapped(a.itemClass()));m.visitInsn(Opcodes.DUP);for(var arg:a.arguments()){if("Lnet/minecraft/block/Block;".equals(arg.descriptor())&&arg.value() instanceof String blockId){m.visitTypeInsn(Opcodes.NEW,API+"$Block");m.visitInsn(Opcodes.DUP);m.visitLdcInsn(blockId);m.visitMethodInsn(Opcodes.INVOKESPECIAL,API+"$Block","<init>","(Ljava/lang/String;)V",false);}else if(arg.value()==null)m.visitInsn(Opcodes.ACONST_NULL);else m.visitLdcInsn(arg.value());}m.visitMethodInsn(Opcodes.INVOKESPECIAL,mapped(a.itemClass()),"<init>",descriptor(a.constructorDescriptor()),false);m.visitLdcInsn(String.join(",",new TreeSet<>(item.hooks)));m.visitMethodInsn(Opcodes.INVOKESTATIC,REG,"registerItem","(Ljava/lang/String;L"+API+"$Item;Ljava/lang/String;)V",false);}
        Map<String,Integer> locals=new LinkedHashMap<>();
        for(int i=0;i<events.size();i++){EventBinding e=events.get(i);String receiver=mapped(e.owner);
            if(e.targetItemId()==null&&!locals.containsKey(e.owner)){
                int local=locals.size();locals.put(e.owner,local);boolean stat=events.stream().filter(x->x.owner.equals(e.owner)&&x.targetItemId()==null).allMatch(x->(classes.get(x.owner).methods.get(new Ref(x.owner,x.method,x.descriptor)).access&Opcodes.ACC_STATIC)!=0);
                if(stat)m.visitInsn(Opcodes.ACONST_NULL);else{m.visitTypeInsn(Opcodes.NEW,receiver);m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,receiver,"<init>","()V",false);}m.visitVarInsn(Opcodes.ASTORE,local);}
            m.visitLdcInsn(mod);m.visitLdcInsn(e.kind);m.visitTypeInsn(Opcodes.NEW,name+"Event"+i);m.visitInsn(Opcodes.DUP);
            if(e.targetItemId()!=null){
                m.visitLdcInsn(e.targetItemId());m.visitMethodInsn(Opcodes.INVOKESTATIC,REG,"bootstrapItem","(Ljava/lang/String;)L"+REG+"$ItemDefinition;",false);
                m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,REG+"$ItemDefinition","item","()L"+API+"$Item;",false);m.visitTypeInsn(Opcodes.CHECKCAST,receiver);
            }else m.visitVarInsn(Opcodes.ALOAD,locals.get(e.owner));
            m.visitMethodInsn(Opcodes.INVOKESPECIAL,name+"Event"+i,"<init>","(L"+receiver+";)V",false);m.visitMethodInsn(Opcodes.INVOKESTATIC,REG,"registerEvent","(Ljava/lang/String;Ljava/lang/String;L"+API+"$EventProgram;)V",false);
        }
        m.visitMethodInsn(Opcodes.INVOKESTATIC,REG,"finish","()V",false);m.visitLabel(end);
        m.visitJumpInsn(Opcodes.GOTO,done);m.visitLabel(failed);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,REG,"abort","()V",false);m.visitInsn(Opcodes.ATHROW);
        m.visitLabel(done);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
}
