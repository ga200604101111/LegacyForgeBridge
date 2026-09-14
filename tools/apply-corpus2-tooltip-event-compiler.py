#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
API=ROOT/"src/main/java/dev/longyu/legacyforgebridge/behavior/LegacyBehaviorApi.java"
REG=ROOT/"src/main/java/dev/longyu/legacyforgebridge/behavior/LegacyBehaviorRegistry.java"
COMPILER=ROOT/"src/main/java/dev/longyu/legacyforgebridge/convert/LegacyBehaviorCompiler.java"
SUPPORT=ROOT/"src/main/java/dev/longyu/legacyforgebridge/convert/runtime/GeneratedModSupport.java"
TEST=ROOT/"src/test/java/dev/longyu/legacyforgebridge/convert/LegacyBehaviorTooltipEventCompilerTest.java"

def replace_once(text,old,new,label):
    count=text.count(old)
    if count!=1: raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old,new,1)

api=API.read_text(encoding="utf-8")
if "public Stack itemStack;" not in api:
    api=replace_once(api,
'''    public static class Event {
        public Entity entity;
        public Living entityLiving;
        public Damage source=new Damage();
        public String name;
''',
'''    public static class Event {
        public Entity entity;
        public Living entityLiving;
        public Stack itemStack;
        public List<String> toolTip;
        public Damage source=new Damage();
        public String name;
''','tooltip event API fields')
    API.write_text(api,encoding="utf-8")

reg=REG.read_text(encoding="utf-8")
if "presentationOnly" not in reg:
    reg=replace_once(reg,
'''    public record ItemDefinition(String mod, LegacyBehaviorApi.Item item, Set<String> hooks) {
        public ItemDefinition { hooks = Set.copyOf(hooks); Objects.requireNonNull(item); }
    }
''',
'''    public record ItemDefinition(String mod, LegacyBehaviorApi.Item item, Set<String> hooks, boolean presentationOnly) {
        public ItemDefinition { hooks = Set.copyOf(hooks); Objects.requireNonNull(item); }
        public ItemDefinition(String mod, LegacyBehaviorApi.Item item, Set<String> hooks) { this(mod,item,hooks,false); }
    }
''','presentation item definition')
    reg=replace_once(reg,
'''        var definition = new ItemDefinition(pending.mod(), item, hooks.isEmpty() ? Set.of() : Set.of(hooks.split(",")));
        if (pending.items().putIfAbsent(id, definition) != null) throw new IllegalArgumentException("Duplicate behavior item " + id);
    }
''',
'''        var definition = new ItemDefinition(pending.mod(), item, hooks.isEmpty() ? Set.of() : Set.of(hooks.split(",")), false);
        if (pending.items().putIfAbsent(id, definition) != null) throw new IllegalArgumentException("Duplicate behavior item " + id);
    }
    public static void registerPresentationItem(String id, LegacyBehaviorApi.Item item) {
        Pending pending = pending();
        if (!id.startsWith(pending.mod() + ':')) throw new IllegalArgumentException("Behavior presentation item owner mismatch");
        var definition = new ItemDefinition(pending.mod(), Objects.requireNonNull(item), Set.of(), true);
        if (pending.items().putIfAbsent(id, definition) != null) throw new IllegalArgumentException("Duplicate behavior item " + id);
    }
''','presentation item registration')
    reg=replace_once(reg,
'''        return (int) ITEMS.values().stream().filter(item -> item.mod().equals(mod)).count();
''',
'''        return (int) ITEMS.values().stream().filter(item -> item.mod().equals(mod) && !item.presentationOnly()).count();
''','presentation items excluded from behavior count')
    REG.write_text(reg,encoding="utf-8")

support=SUPPORT.read_text(encoding="utf-8")
if "source.presentationOnly()" not in support:
    support=replace_once(support,
'''        var source=LegacyBehaviorRegistry.item(idValue);
        if(source!=null){
''',
'''        var source=LegacyBehaviorRegistry.item(idValue);
        if(source!=null&&source.presentationOnly())source=null;
        if(source!=null){
''','presentation target must not alter native item properties')
    SUPPORT.write_text(support,encoding="utf-8")

compiler=COMPILER.read_text(encoding="utf-8")
if "syntheticPresentationConstructors" not in compiler:
    compiler=replace_once(compiler,
'''            Map.entry("net/minecraftforge/event/entity/EntityEvent","Event"),
            Map.entry("net/minecraftforge/event/entity/PlaySoundAtEntityEvent","Event"));
''',
'''            Map.entry("net/minecraftforge/event/entity/EntityEvent","Event"),
            Map.entry("net/minecraftforge/event/entity/PlaySoundAtEntityEvent","Event"),
            Map.entry("net/minecraftforge/event/entity/player/ItemTooltipEvent","Event"));
''','ItemTooltip event type')
    compiler=replace_once(compiler,
'''    private static final Set<String> JDK=Set.of("java/lang/Object","java/lang/String","java/lang/StringBuilder","java/lang/StringBuffer",
''',
'''    private static final Set<String> PRESENTATION_STATEFUL_ITEM_API=Set.of(
            "net/minecraft/item/Item","net/minecraft/item/ItemSword","net/minecraft/item/ItemArmor","net/minecraft/item/ItemBow",
            "net/minecraft/item/ItemTool","net/minecraft/item/ItemPickaxe","net/minecraft/item/ItemAxe","net/minecraft/item/ItemSpade",
            "net/minecraft/item/ItemHoe","net/minecraft/item/ItemBlock");
    private static final Set<String> JDK=Set.of("java/lang/Object","java/lang/String","java/lang/StringBuilder","java/lang/StringBuffer",
''','presentation stateful API guard')
    compiler=replace_once(compiler,
'''    private final Set<String> included=new LinkedHashSet<>(),syntheticParentConstructors=new LinkedHashSet<>(),syntheticNameConstructors=new LinkedHashSet<>();
''',
'''    private final Set<String> included=new LinkedHashSet<>(),syntheticParentConstructors=new LinkedHashSet<>(),syntheticNameConstructors=new LinkedHashSet<>(),syntheticPresentationConstructors=new LinkedHashSet<>();
''','presentation constructor state')
    compiler=replace_once(compiler,
'''    public record EventBinding(String owner,String method,String descriptor,String kind,String targetItemId) { }
    public record Result(Map<String,byte[]> classes,List<ItemBinding> items,List<EventBinding> events,List<String> diagnostics) { }
''',
'''    public record EventBinding(String owner,String method,String descriptor,String kind,String targetItemId) { }
    private record PresentationBinding(String id,String sourceClass) { }
    public record Result(Map<String,byte[]> classes,List<ItemBinding> items,List<EventBinding> events,List<String> diagnostics) { }
''','presentation binding record')
    compiler=replace_once(compiler,
'''        classes.clear();selected.clear();fields.clear();included.clear();syntheticParentConstructors.clear();syntheticNameConstructors.clear();constantNameTables.clear();diagnostics.clear();
''',
'''        classes.clear();selected.clear();fields.clear();included.clear();syntheticParentConstructors.clear();syntheticNameConstructors.clear();syntheticPresentationConstructors.clear();constantNameTables.clear();diagnostics.clear();
''','presentation state reset')
    compiler=replace_once(compiler,
'''        List<EventBinding> events=new ArrayList<>();
        Set<String> eventKeys=new LinkedHashSet<>();
''',
'''        List<EventBinding> events=new ArrayList<>();
        LinkedHashMap<String,PresentationBinding> presentationTargets=new LinkedHashMap<>();
        Set<String> eventKeys=new LinkedHashSet<>();
''','presentation target collection')
    compiler=replace_once(compiler,
'''                case "net/minecraftforge/event/entity/PlaySoundAtEntityEvent"->"sound";
                default->null;
''',
'''                case "net/minecraftforge/event/entity/PlaySoundAtEntityEvent"->"sound";
                case "net/minecraftforge/event/entity/player/ItemTooltipEvent"->"tooltipEvent";
                default->null;
''','tooltip event kind')
    compiler=replace_once(compiler,
'''            if(!itemTargets.isEmpty()){
                if(!admit(r,"event "+kind))continue;
                for(ItemBinding itemTarget:itemTargets)
                    events.add(new EventBinding(type,r.name,r.desc,kind,itemTarget.id()));
                continue;
            }

            LegacyEventHandlerConstructionAnalyzer.Strategy construction=null;
''',
'''            if(!itemTargets.isEmpty()){
                if(!admit(r,"event "+kind))continue;
                for(ItemBinding itemTarget:itemTargets)
                    events.add(new EventBinding(type,r.name,r.desc,kind,itemTarget.id()));
                continue;
            }
            if(kind.equals("tooltipEvent")&&instance&&binding.handlerClass().equals(binding.registrationOwner())
                    &&"<init>".equals(binding.registrationMethod())){
                List<PresentationBinding> candidates=allocations.values().stream()
                        .filter(allocation->allocation.itemClass().equals(type)
                                &&allocation.constructorDescriptor().equals(binding.registrationDescriptor()))
                        .map(allocation->{String id=itemIds.get(allocation.itemName());return id==null?null:new PresentationBinding(id,type);})
                        .filter(Objects::nonNull).distinct().toList();
                if(candidates.size()==1){
                    PresentationBinding candidate=candidates.getFirst();
                    if(presentationParentConstructor(type)==null){
                        diagnostics.add("presentation event constructor "+type+": unsupported direct item parent "+classes.get(type).parent);
                    }else if(admitPresentation(r,type,"event "+kind)){
                        PresentationBinding previous=presentationTargets.putIfAbsent(candidate.id(),candidate);
                        if(previous==null||previous.equals(candidate)){
                            syntheticPresentationConstructors.add(type);
                            events.add(new EventBinding(type,r.name,r.desc,kind,candidate.id()));
                            continue;
                        }
                        diagnostics.add("Ambiguous presentation target identity "+candidate.id());
                    }
                }else if(candidates.size()>1)diagnostics.add("Ambiguous presentation event source identity for "+type+": "+candidates);
            }

            LegacyEventHandlerConstructionAnalyzer.Strategy construction=null;
''','presentation tooltip target fallback')
    compiler=replace_once(compiler,
'''        output.put(bootstrap+".class",bootstrap(bootstrap,mod,items,events));
''',
'''        output.put(bootstrap+".class",bootstrap(bootstrap,mod,items,new ArrayList<>(presentationTargets.values()),events));
''','bootstrap presentation targets')
    compiler=replace_once(compiler,
'''    private boolean admit(Ref root,String label) {
        Set<Ref> oldMethods=new LinkedHashSet<>(selected),oldFields=new LinkedHashSet<>(fields);Set<String> oldClasses=new LinkedHashSet<>(included);
        try{validate(root);return true;}catch(RuntimeException ex){selected.clear();selected.addAll(oldMethods);fields.clear();fields.addAll(oldFields);included.clear();included.addAll(oldClasses);diagnostics.add(label+": "+ex.getMessage());return false;}
    }
''',
'''    private boolean admit(Ref root,String label) {
        Set<Ref> oldMethods=new LinkedHashSet<>(selected),oldFields=new LinkedHashSet<>(fields);Set<String> oldClasses=new LinkedHashSet<>(included),oldParent=new LinkedHashSet<>(syntheticParentConstructors),oldNames=new LinkedHashSet<>(syntheticNameConstructors),oldPresentation=new LinkedHashSet<>(syntheticPresentationConstructors);
        try{validate(root);return true;}catch(RuntimeException ex){restore(oldMethods,oldFields,oldClasses,oldParent,oldNames,oldPresentation);diagnostics.add(label+": "+ex.getMessage());return false;}
    }
    private boolean admitPresentation(Ref root,String target,String label) {
        Set<Ref> oldMethods=new LinkedHashSet<>(selected),oldFields=new LinkedHashSet<>(fields);Set<String> oldClasses=new LinkedHashSet<>(included),oldParent=new LinkedHashSet<>(syntheticParentConstructors),oldNames=new LinkedHashSet<>(syntheticNameConstructors),oldPresentation=new LinkedHashSet<>(syntheticPresentationConstructors);
        try{validate(root);validatePresentationState(root,target);return true;}catch(RuntimeException ex){restore(oldMethods,oldFields,oldClasses,oldParent,oldNames,oldPresentation);diagnostics.add(label+": "+ex.getMessage());return false;}
    }
    private void restore(Set<Ref> methods,Set<Ref> oldFields,Set<String> oldClasses,Set<String> oldParent,Set<String> oldNames,Set<String> oldPresentation){
        selected.clear();selected.addAll(methods);fields.clear();fields.addAll(oldFields);included.clear();included.addAll(oldClasses);
        syntheticParentConstructors.clear();syntheticParentConstructors.addAll(oldParent);syntheticNameConstructors.clear();syntheticNameConstructors.addAll(oldNames);syntheticPresentationConstructors.clear();syntheticPresentationConstructors.addAll(oldPresentation);
    }
    private void validatePresentationState(Ref root,String target){
        LinkedHashSet<String> hierarchy=new LinkedHashSet<>();
        for(String current=target;current!=null&&classes.containsKey(current);current=classes.get(current).parent)hierarchy.add(current);
        ArrayDeque<Ref> pending=new ArrayDeque<>();LinkedHashSet<Ref> seen=new LinkedHashSet<>();pending.add(root);
        while(!pending.isEmpty()){
            Ref requested=pending.removeFirst(),resolved=resolve(requested);if(resolved==null||!classes.containsKey(resolved.owner)||!seen.add(resolved))continue;
            MethodInfo method=classes.get(resolved.owner).methods.get(resolved);
            for(Object op:method.ops)if(op instanceof List<?> parts&&parts.size()==2&&parts.get(0) instanceof Integer opcode&&parts.get(1) instanceof Ref field
                    &&(opcode==Opcodes.GETFIELD||opcode==Opcodes.PUTFIELD)&&hierarchy.contains(field.owner))
                throw new IllegalArgumentException("presentation callback depends on source instance field "+field);
            for(Ref call:method.calls){
                Ref next=resolve(call);
                if(next!=null&&classes.containsKey(next.owner))pending.add(next);
                else if(PRESENTATION_STATEFUL_ITEM_API.contains(call.owner))
                    throw new IllegalArgumentException("presentation callback depends on legacy item instance API "+call);
            }
        }
    }
    private String presentationParentConstructor(String type){
        Clazz clazz=classes.get(type);if(clazz==null)return null;
        return switch(clazz.parent){
            case "net/minecraft/item/Item","net/minecraft/item/ItemBow"->"()V";
            case "net/minecraft/item/ItemSword","net/minecraft/item/ItemTool","net/minecraft/item/ItemPickaxe","net/minecraft/item/ItemAxe","net/minecraft/item/ItemSpade","net/minecraft/item/ItemHoe"->"(Lnet/minecraft/item/Item$ToolMaterial;)V";
            default->null;
        };
    }
''','presentation-safe admission')
    compiler=replace_once(compiler,
'''        if(syntheticNameConstructors.contains(type)){
            var table=constantNameTables.values().stream().filter(value->value.valueType().equals(type)).findFirst().orElseThrow();
            MethodVisitor constructor=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Ljava/lang/String;)V",null,null);constructor.visitCode();
            constructor.visitVarInsn(Opcodes.ALOAD,0);constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
            constructor.visitVarInsn(Opcodes.ALOAD,0);constructor.visitVarInsn(Opcodes.ALOAD,1);constructor.visitFieldInsn(Opcodes.PUTFIELD,mapped(type),table.nameField(),"Ljava/lang/String;");
            constructor.visitInsn(Opcodes.RETURN);constructor.visitMaxs(0,0);constructor.visitEnd();
        }
        new ClassReader(c.bytes).accept(new ClassVisitor(Opcodes.ASM9){
''',
'''        if(syntheticNameConstructors.contains(type)){
            var table=constantNameTables.values().stream().filter(value->value.valueType().equals(type)).findFirst().orElseThrow();
            MethodVisitor constructor=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Ljava/lang/String;)V",null,null);constructor.visitCode();
            constructor.visitVarInsn(Opcodes.ALOAD,0);constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
            constructor.visitVarInsn(Opcodes.ALOAD,0);constructor.visitVarInsn(Opcodes.ALOAD,1);constructor.visitFieldInsn(Opcodes.PUTFIELD,mapped(type),table.nameField(),"Ljava/lang/String;");
            constructor.visitInsn(Opcodes.RETURN);constructor.visitMaxs(0,0);constructor.visitEnd();
        }
        if(syntheticPresentationConstructors.contains(type)){
            String sourceDescriptor=presentationParentConstructor(type);MethodVisitor constructor=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);constructor.visitCode();constructor.visitVarInsn(Opcodes.ALOAD,0);
            for(Type argument:Type.getArgumentTypes(sourceDescriptor)){
                switch(argument.getSort()){
                    case Type.OBJECT,Type.ARRAY->constructor.visitInsn(Opcodes.ACONST_NULL);
                    case Type.LONG->constructor.visitInsn(Opcodes.LCONST_0);case Type.FLOAT->constructor.visitInsn(Opcodes.FCONST_0);case Type.DOUBLE->constructor.visitInsn(Opcodes.DCONST_0);
                    default->constructor.visitInsn(Opcodes.ICONST_0);
                }
            }
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,mapped(c.parent),"<init>",descriptor(sourceDescriptor),false);constructor.visitInsn(Opcodes.RETURN);constructor.visitMaxs(0,0);constructor.visitEnd();
        }
        new ClassReader(c.bytes).accept(new ClassVisitor(Opcodes.ASM9){
''','synthetic presentation constructor emission')
    compiler=replace_once(compiler,
'''    private byte[] bootstrap(String name,String mod,List<ItemBinding> items,List<EventBinding> events){
''',
'''    private byte[] bootstrap(String name,String mod,List<ItemBinding> items,List<PresentationBinding> presentations,List<EventBinding> events){
''','bootstrap signature')
    compiler=replace_once(compiler,
'''        for(ItemBinding item:items){var a=item.allocation;m.visitLdcInsn(item.id);m.visitTypeInsn(Opcodes.NEW,mapped(a.itemClass()));m.visitInsn(Opcodes.DUP);for(var arg:a.arguments()){if("Lnet/minecraft/block/Block;".equals(arg.descriptor())&&arg.value() instanceof String blockId){m.visitTypeInsn(Opcodes.NEW,API+"$Block");m.visitInsn(Opcodes.DUP);m.visitLdcInsn(blockId);m.visitMethodInsn(Opcodes.INVOKESPECIAL,API+"$Block","<init>","(Ljava/lang/String;)V",false);}else if(arg.value()==null)m.visitInsn(Opcodes.ACONST_NULL);else m.visitLdcInsn(arg.value());}m.visitMethodInsn(Opcodes.INVOKESPECIAL,mapped(a.itemClass()),"<init>",descriptor(a.constructorDescriptor()),false);m.visitLdcInsn(String.join(",",new TreeSet<>(item.hooks)));m.visitMethodInsn(Opcodes.INVOKESTATIC,REG,"registerItem","(Ljava/lang/String;L"+API+"$Item;Ljava/lang/String;)V",false);}
        Map<String,Integer> locals=new LinkedHashMap<>();
''',
'''        for(ItemBinding item:items){var a=item.allocation;m.visitLdcInsn(item.id);m.visitTypeInsn(Opcodes.NEW,mapped(a.itemClass()));m.visitInsn(Opcodes.DUP);for(var arg:a.arguments()){if("Lnet/minecraft/block/Block;".equals(arg.descriptor())&&arg.value() instanceof String blockId){m.visitTypeInsn(Opcodes.NEW,API+"$Block");m.visitInsn(Opcodes.DUP);m.visitLdcInsn(blockId);m.visitMethodInsn(Opcodes.INVOKESPECIAL,API+"$Block","<init>","(Ljava/lang/String;)V",false);}else if(arg.value()==null)m.visitInsn(Opcodes.ACONST_NULL);else m.visitLdcInsn(arg.value());}m.visitMethodInsn(Opcodes.INVOKESPECIAL,mapped(a.itemClass()),"<init>",descriptor(a.constructorDescriptor()),false);m.visitLdcInsn(String.join(",",new TreeSet<>(item.hooks)));m.visitMethodInsn(Opcodes.INVOKESTATIC,REG,"registerItem","(Ljava/lang/String;L"+API+"$Item;Ljava/lang/String;)V",false);}
        for(PresentationBinding presentation:presentations){m.visitLdcInsn(presentation.id());m.visitTypeInsn(Opcodes.NEW,mapped(presentation.sourceClass()));m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,mapped(presentation.sourceClass()),"<init>","()V",false);m.visitMethodInsn(Opcodes.INVOKESTATIC,REG,"registerPresentationItem","(Ljava/lang/String;L"+API+"$Item;)V",false);}
        Map<String,Integer> locals=new LinkedHashMap<>();
''','presentation registration before events')
    COMPILER.write_text(compiler,encoding="utf-8")

if not TEST.exists():
    TEST.write_text(r'''package dev.longyu.legacyforgebridge.convert;

import dev.longyu.legacyforgebridge.behavior.LegacyBehaviorApi;
import dev.longyu.legacyforgebridge.behavior.LegacyBehaviorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBehaviorTooltipEventCompilerTest {
    @TempDir Path temp;

    @Test void unsafeSelfRegisteredPickaxeGetsPresentationOnlyTargetAndRunsTooltipEvent() throws Exception {
        Path source=temp.resolve("foreign-tooltip.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(source))){
            write(out,"foreign/tooltip/NameBase.class",nameBase());
            write(out,"foreign/tooltip/NameA.class",nameA());
            write(out,"foreign/tooltip/Names.class",names());
            write(out,"foreign/tooltip/UnsafePickaxe.class",unsafePickaxe());
        }
        var allocation=new LegacyItemRenderAnalyzer.ItemAllocation("unsafe","foreign/tooltip/UnsafePickaxe","()V",List.of(),false,false,false);
        var result=new LegacyBehaviorCompiler().compile(source,"tooltip_fixture","generated/TooltipBootstrap",
                Map.of("unsafe","tooltip_fixture:unsafe"),List.of(allocation));
        assertTrue(result.items().isEmpty(),"unsafe constructor unexpectedly became a normal source item");
        assertEquals(1,result.events().size(),String.join("\n",result.diagnostics()));
        var eventBinding=result.events().getFirst();
        assertEquals("tooltipEvent",eventBinding.kind());
        assertEquals("tooltip_fixture:unsafe",eventBinding.targetItemId());
        byte[] generated=result.classes().get("generated/TooltipBootstrapSource/foreign/tooltip/UnsafePickaxe.class");
        assertNotNull(generated);
        assertFalse(new String(generated,StandardCharsets.ISO_8859_1).contains("ChestGenHooks"),"unsafe source constructor leaked into generated presentation target");

        ClassLoader loader=new ClassLoader(getClass().getClassLoader()){
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException{
                byte[] bytes=result.classes().get(name.replace('.','/')+".class");
                if(bytes==null)throw new ClassNotFoundException(name);
                return defineClass(name,bytes,0,bytes.length);
            }
        };
        Class.forName("generated.TooltipBootstrap",true,loader).getMethod("initialize").invoke(null);
        try{
            var definition=LegacyBehaviorRegistry.item("tooltip_fixture:unsafe");
            assertNotNull(definition);assertTrue(definition.presentationOnly());assertTrue(definition.hooks().isEmpty());
            var program=LegacyBehaviorRegistry.events("tooltipEvent").stream().filter(e->e.mod().equals("tooltip_fixture")).findFirst().orElseThrow();
            var root=new LegacyBehaviorApi.Tag();root.setInteger("toolLevel",3);
            var enchant=new LegacyBehaviorApi.Tag();enchant.setInteger("id",4);enchant.setInteger("lvl",2);
            root.values.put("spench",new LegacyBehaviorApi.TagList(List.of(enchant)));
            var sourceStack=new LegacyBehaviorApi.Stack(definition.item(),root);
            var lines=new ArrayList<>(List.of("Pickaxe","Stats"));
            var event=new LegacyBehaviorApi.Event();event.itemStack=sourceStack;event.toolTip=lines;
            LegacyBehaviorApi.begin("tooltip_fixture",(key,args)->key.endsWith("bambooEnch.echo")?"Echo":key.endsWith("enchantment.level.2")?" II":key);
            try{program.program().run(event);}finally{LegacyBehaviorApi.end();}
            assertEquals(List.of("Pickaxe Level:3","Echo II","Stats"),lines);
        }finally{LegacyBehaviorRegistry.removeMod("tooltip_fixture");}
    }

    private static void write(JarOutputStream out,String path,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(path));out.write(bytes);out.closeEntry();}

    private static byte[] nameBase(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);String owner="foreign/tooltip/NameBase";
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,owner,null,"java/lang/Object",null);w.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_FINAL,"name","Ljava/lang/String;",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(ILjava/lang/String;I)V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,2);c.visitFieldInsn(Opcodes.PUTFIELD,owner,"name","Ljava/lang/String;");c.visitFieldInsn(Opcodes.GETSTATIC,"foreign/tooltip/Names","TABLE","Ljava/util/HashMap;");c.visitVarInsn(Opcodes.ILOAD,1);c.visitInsn(Opcodes.I2S);c.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Short","valueOf","(S)Ljava/lang/Short;",false);c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/util/HashMap","put","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false);c.visitInsn(Opcodes.POP);c.visitInsn(Opcodes.RETURN);c.visitMaxs(3,4);c.visitEnd();
        MethodVisitor g=w.visitMethod(Opcodes.ACC_PUBLIC,"getName","()Ljava/lang/String;",null,null);g.visitCode();g.visitVarInsn(Opcodes.ALOAD,0);g.visitFieldInsn(Opcodes.GETFIELD,owner,"name","Ljava/lang/String;");g.visitInsn(Opcodes.ARETURN);g.visitMaxs(1,1);g.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static byte[] nameA(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,"foreign/tooltip/NameA",null,"foreign/tooltip/NameBase",null);MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(ILjava/lang/String;I)V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ILOAD,1);c.visitVarInsn(Opcodes.ALOAD,2);c.visitVarInsn(Opcodes.ILOAD,3);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/tooltip/NameBase","<init>","(ILjava/lang/String;I)V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(4,4);c.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static byte[] names(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);String owner="foreign/tooltip/Names";w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,owner,null,"java/lang/Object",null);w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"TABLE","Ljava/util/HashMap;",null,null).visitEnd();w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"A","Lforeign/tooltip/NameBase;",null,null).visitEnd();MethodVisitor s=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);s.visitCode();s.visitTypeInsn(Opcodes.NEW,"java/util/HashMap");s.visitInsn(Opcodes.DUP);s.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/util/HashMap","<init>","()V",false);s.visitFieldInsn(Opcodes.PUTSTATIC,owner,"TABLE","Ljava/util/HashMap;");s.visitTypeInsn(Opcodes.NEW,"foreign/tooltip/NameA");s.visitInsn(Opcodes.DUP);s.visitInsn(Opcodes.ICONST_4);s.visitLdcInsn("echo");s.visitInsn(Opcodes.ICONST_2);s.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/tooltip/NameA","<init>","(ILjava/lang/String;I)V",false);s.visitFieldInsn(Opcodes.PUTSTATIC,owner,"A","Lforeign/tooltip/NameBase;");s.visitInsn(Opcodes.RETURN);s.visitMaxs(5,0);s.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] unsafePickaxe(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);String owner="foreign/tooltip/UnsafePickaxe";w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,owner,null,"net/minecraft/item/ItemPickaxe",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitFieldInsn(Opcodes.GETSTATIC,"net/minecraft/item/Item$ToolMaterial","EMERALD","Lnet/minecraft/item/Item$ToolMaterial;");c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemPickaxe","<init>","(Lnet/minecraft/item/Item$ToolMaterial;)V",false);c.visitLdcInsn("villageBlacksmith");c.visitMethodInsn(Opcodes.INVOKESTATIC,"net/minecraftforge/common/ChestGenHooks","getInfo","(Ljava/lang/String;)Lnet/minecraftforge/common/ChestGenHooks;",false);c.visitInsn(Opcodes.POP);c.visitFieldInsn(Opcodes.GETSTATIC,"net/minecraftforge/common/MinecraftForge","EVENT_BUS","Lcpw/mods/fml/common/eventhandler/EventBus;");c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"cpw/mods/fml/common/eventhandler/EventBus","register","(Ljava/lang/Object;)V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(2,1);c.visitEnd();
        MethodVisitor init=w.visitMethod(Opcodes.ACC_PRIVATE,"initTagCompound","(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/nbt/NBTTagCompound;",null,null);init.visitCode();init.visitTypeInsn(Opcodes.NEW,"net/minecraft/nbt/NBTTagCompound");init.visitInsn(Opcodes.DUP);init.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/nbt/NBTTagCompound","<init>","()V",false);init.visitVarInsn(Opcodes.ASTORE,2);init.visitVarInsn(Opcodes.ALOAD,2);init.visitLdcInsn("toolLevel");init.visitInsn(Opcodes.ICONST_0);init.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagCompound","func_74768_a","(Ljava/lang/String;I)V",false);init.visitVarInsn(Opcodes.ALOAD,1);init.visitVarInsn(Opcodes.ALOAD,2);init.visitFieldInsn(Opcodes.PUTFIELD,"net/minecraft/item/ItemStack","field_77990_d","Lnet/minecraft/nbt/NBTTagCompound;");init.visitVarInsn(Opcodes.ALOAD,2);init.visitInsn(Opcodes.ARETURN);init.visitMaxs(3,3);init.visitEnd();
        MethodVisitor level=w.visitMethod(Opcodes.ACC_PRIVATE,"getLevel","(Lnet/minecraft/item/ItemStack;)I",null,null);level.visitCode();Label have=new Label();level.visitVarInsn(Opcodes.ALOAD,1);level.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/item/ItemStack","field_77990_d","Lnet/minecraft/nbt/NBTTagCompound;");level.visitJumpInsn(Opcodes.IFNONNULL,have);level.visitVarInsn(Opcodes.ALOAD,0);level.visitVarInsn(Opcodes.ALOAD,1);level.visitMethodInsn(Opcodes.INVOKEVIRTUAL,owner,"initTagCompound","(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/nbt/NBTTagCompound;",false);level.visitInsn(Opcodes.POP);level.visitLabel(have);level.visitVarInsn(Opcodes.ALOAD,1);level.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/item/ItemStack","field_77990_d","Lnet/minecraft/nbt/NBTTagCompound;");level.visitLdcInsn("toolLevel");level.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagCompound","func_74762_e","(Ljava/lang/String;)I",false);level.visitInsn(Opcodes.IRETURN);level.visitMaxs(2,2);level.visitEnd();
        MethodVisitor t=w.visitMethod(Opcodes.ACC_PUBLIC,"onItemTooltip","(Lnet/minecraftforge/event/entity/player/ItemTooltipEvent;)V",null,null);t.visitAnnotation("Lcpw/mods/fml/common/eventhandler/SubscribeEvent;",true).visitEnd();t.visitCode();Label done=new Label(),noEnchant=new Label();t.visitVarInsn(Opcodes.ALOAD,1);t.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/player/ItemTooltipEvent","itemStack","Lnet/minecraft/item/ItemStack;");t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_77973_b","()Lnet/minecraft/item/Item;",false);t.visitVarInsn(Opcodes.ALOAD,0);t.visitJumpInsn(Opcodes.IF_ACMPNE,done);
        t.visitVarInsn(Opcodes.ALOAD,1);t.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/player/ItemTooltipEvent","toolTip","Ljava/util/List;");t.visitInsn(Opcodes.ICONST_0);t.visitTypeInsn(Opcodes.NEW,"java/lang/StringBuilder");t.visitInsn(Opcodes.DUP);t.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/StringBuilder","<init>","()V",false);t.visitVarInsn(Opcodes.ALOAD,1);t.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/player/ItemTooltipEvent","toolTip","Ljava/util/List;");t.visitInsn(Opcodes.ICONST_0);t.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/List","get","(I)Ljava/lang/Object;",true);t.visitTypeInsn(Opcodes.CHECKCAST,"java/lang/String");t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","append","(Ljava/lang/String;)Ljava/lang/StringBuilder;",false);t.visitLdcInsn(" Level:");t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","append","(Ljava/lang/String;)Ljava/lang/StringBuilder;",false);t.visitVarInsn(Opcodes.ALOAD,0);t.visitVarInsn(Opcodes.ALOAD,1);t.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/player/ItemTooltipEvent","itemStack","Lnet/minecraft/item/ItemStack;");t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,owner,"getLevel","(Lnet/minecraft/item/ItemStack;)I",false);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","append","(I)Ljava/lang/StringBuilder;",false);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","toString","()Ljava/lang/String;",false);t.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/List","set","(ILjava/lang/Object;)Ljava/lang/Object;",true);t.visitInsn(Opcodes.POP);
        t.visitVarInsn(Opcodes.ALOAD,1);t.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/player/ItemTooltipEvent","itemStack","Lnet/minecraft/item/ItemStack;");t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_77942_o","()Z",false);t.visitJumpInsn(Opcodes.IFEQ,noEnchant);t.visitVarInsn(Opcodes.ALOAD,1);t.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/player/ItemTooltipEvent","itemStack","Lnet/minecraft/item/ItemStack;");t.visitFieldInsn(Opcodes.GETFIELD,"net/minecraft/item/ItemStack","field_77990_d","Lnet/minecraft/nbt/NBTTagCompound;");t.visitLdcInsn("spench");t.visitIntInsn(Opcodes.BIPUSH,10);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagCompound","func_150295_c","(Ljava/lang/String;I)Lnet/minecraft/nbt/NBTTagList;",false);t.visitVarInsn(Opcodes.ASTORE,2);t.visitVarInsn(Opcodes.ALOAD,2);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagList","func_74745_c","()I",false);t.visitJumpInsn(Opcodes.IFEQ,noEnchant);t.visitVarInsn(Opcodes.ALOAD,2);t.visitInsn(Opcodes.ICONST_0);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagList","func_150305_b","(I)Lnet/minecraft/nbt/NBTTagCompound;",false);t.visitVarInsn(Opcodes.ASTORE,3);t.visitVarInsn(Opcodes.ALOAD,1);t.visitFieldInsn(Opcodes.GETFIELD,"net/minecraftforge/event/entity/player/ItemTooltipEvent","toolTip","Ljava/util/List;");t.visitInsn(Opcodes.ICONST_1);t.visitTypeInsn(Opcodes.NEW,"java/lang/StringBuilder");t.visitInsn(Opcodes.DUP);t.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/StringBuilder","<init>","()V",false);t.visitLdcInsn("bambooEnch.");t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","append","(Ljava/lang/String;)Ljava/lang/StringBuilder;",false);t.visitFieldInsn(Opcodes.GETSTATIC,"foreign/tooltip/Names","TABLE","Ljava/util/HashMap;");t.visitVarInsn(Opcodes.ALOAD,3);t.visitLdcInsn("id");t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagCompound","func_74765_d","(Ljava/lang/String;)S",false);t.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Short","valueOf","(S)Ljava/lang/Short;",false);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/util/HashMap","get","(Ljava/lang/Object;)Ljava/lang/Object;",false);t.visitTypeInsn(Opcodes.CHECKCAST,"foreign/tooltip/NameBase");t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"foreign/tooltip/NameBase","getName","()Ljava/lang/String;",false);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","append","(Ljava/lang/String;)Ljava/lang/StringBuilder;",false);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","toString","()Ljava/lang/String;",false);t.visitMethodInsn(Opcodes.INVOKESTATIC,"net/minecraft/util/StatCollector","func_74838_a","(Ljava/lang/String;)Ljava/lang/String;",false);t.visitLdcInsn("enchantment.level.2");t.visitMethodInsn(Opcodes.INVOKESTATIC,"net/minecraft/util/StatCollector","func_74838_a","(Ljava/lang/String;)Ljava/lang/String;",false);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","append","(Ljava/lang/String;)Ljava/lang/StringBuilder;",false);t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/StringBuilder","toString","()Ljava/lang/String;",false);t.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/List","add","(ILjava/lang/Object;)V",true);t.visitLabel(noEnchant);t.visitLabel(done);t.visitInsn(Opcodes.RETURN);t.visitMaxs(6,4);t.visitEnd();w.visitEnd();return w.toByteArray();
    }
}
''',encoding="utf-8")

print("Applied presentation-only ItemTooltip event compiler slice")
