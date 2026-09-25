package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Proves a bounded custom ItemSnowball -> EntitySnowball selector family without executing source code.
 * This is proof IR only: custom impact behavior is intentionally left closed until a dedicated runtime
 * compiler admits every selector branch.
 */
public final class LegacyVariantSnowballAnalyzer {
    private static final String ITEM_SNOWBALL = "net/minecraft/item/ItemSnowball";
    private static final String ENTITY_SNOWBALL = "net/minecraft/entity/projectile/EntitySnowball";
    private static final String WORLD = "net/minecraft/world/World";
    private static final String ENTITY = "net/minecraft/entity/Entity";
    private static final String LIVING = "net/minecraft/entity/EntityLivingBase";
    private static final String PLAYER = "net/minecraft/entity/player/EntityPlayer";
    private static final String ITEM_STACK = "net/minecraft/item/ItemStack";
    private static final Set<String> USE_NAMES = Set.of("onItemRightClick", "func_77659_a");
    private static final String USE_DESC = "(L"+ITEM_STACK+";L"+WORLD+";L"+PLAYER+";)L"+ITEM_STACK+";";
    private static final Set<String> SPAWN_NAMES = Set.of("spawnEntityInWorld", "func_72838_d");
    private static final Set<String> DAMAGE_NAMES = Set.of("getItemDamage", "func_77960_j");
    private static final Set<String> IMPACT_NAMES = Set.of("onImpact", "func_70184_a");
    private static final String IMPACT_DESC = "(Lnet/minecraft/util/MovingObjectPosition;)V";

    public record Variant(String enumField, int id, int damage) { }
    public record Rule(String registryName, String itemClass, String projectileClass, String selectorClass,
                       String selectorMapOwner, String selectorMapField, String selectorIdGetter,
                       String selectorDamageGetter, String impactMethod, String impactDescriptor,
                       List<Variant> variants) {
        public Rule { variants = List.copyOf(variants); }
    }
    public record Skipped(String registryName, String itemClass, String reason) { }
    public record Analysis(List<Rule> rules, List<Skipped> skipped, List<String> diagnostics) {
        public Analysis { rules=List.copyOf(rules); skipped=List.copyOf(skipped); diagnostics=List.copyOf(diagnostics); }
    }

    private record FieldRef(String owner,String name,String desc) { }
    private record SpawnBinding(String projectileClass,String selectorClass,FieldRef selectorMap) { }
    private record EnumShape(String idGetter,String damageGetter,List<Variant> variants) { }
    private record Flow(ClassNode owner,MethodNode method,Frame<SourceValue>[] frames,Map<AbstractInsnNode,Integer> indices) { }

    private final Map<String,ClassNode> classes = new LinkedHashMap<>();
    private final Map<String,Flow> flows = new HashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear(); flows.clear(); diagnostics.clear(); load(jarPath);
        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(jarPath);
        List<Rule> rules = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (var registration : registry.items()) {
            String itemClass = registration.implementationClass();
            if (itemClass == null || !reaches(itemClass, ITEM_SNOWBALL)) continue;
            ClassNode item = classes.get(itemClass);
            if (item == null) continue;
            MethodNode use = findDeclared(item, USE_NAMES, USE_DESC);
            if (use == null) continue; // inherited vanilla semantics belong to LegacySnowballItemAnalyzer
            try {
                SpawnBinding spawn = proveSpawnBinding(item, use);
                if (spawn == null) {
                    skipped.add(new Skipped(registration.registryName(), itemClass,
                            "Custom ItemSnowball use method did not prove a direct metadata-selected EntitySnowball spawn."));
                    continue;
                }
                String fieldFailure = proveProjectileSelectorField(spawn);
                if (fieldFailure != null) {
                    skipped.add(new Skipped(registration.registryName(), itemClass, fieldFailure));
                    continue;
                }
                EnumShape enumShape = proveEnumShape(spawn.selectorClass());
                if (enumShape == null) {
                    skipped.add(new Skipped(registration.registryName(), itemClass,
                            "Projectile selector type did not prove a constant enum id/damage table."));
                    continue;
                }
                MethodNode impact = findDeclared(classes.get(spawn.projectileClass()), IMPACT_NAMES, IMPACT_DESC);
                if (impact == null) {
                    skipped.add(new Skipped(registration.registryName(), itemClass,
                            "Custom EntitySnowball projectile did not declare the expected impact callback."));
                    continue;
                }
                rules.add(new Rule(registration.registryName(), itemClass, spawn.projectileClass(), spawn.selectorClass(),
                        spawn.selectorMap().owner(), spawn.selectorMap().name(), enumShape.idGetter(), enumShape.damageGetter(),
                        impact.name, impact.desc, enumShape.variants()));
            } catch (AnalyzerException error) {
                skipped.add(new Skipped(registration.registryName(), itemClass,
                        "Could not prove custom snowball dataflow: "+error.getMessage()));
            }
        }
        diagnostics.addAll(registry.diagnostics());
        return new Analysis(rules, skipped, diagnostics);
    }

    private SpawnBinding proveSpawnBinding(ClassNode item, MethodNode use) throws AnalyzerException {
        Flow flow = flow(item, use);
        for (int i=0;i<use.instructions.size();i++) {
            AbstractInsnNode insn = use.instructions.get(i);
            if (!(insn instanceof MethodInsnNode ctor) || ctor.getOpcode()!=Opcodes.INVOKESPECIAL || !"<init>".equals(ctor.name)) continue;
            if (!reaches(ctor.owner, ENTITY_SNOWBALL)) continue;
            Type[] args = Type.getArgumentTypes(ctor.desc);
            if (args.length!=3 || args[0].getSort()!=Type.OBJECT || !WORLD.equals(args[0].getInternalName())
                    || args[1].getSort()!=Type.OBJECT || !(LIVING.equals(args[1].getInternalName()) || PLAYER.equals(args[1].getInternalName()))
                    || args[2].getSort()!=Type.OBJECT) continue;
            AbstractInsnNode next = nextMeaningful(insn.getNext());
            if (!(next instanceof MethodInsnNode spawn) || !WORLD.equals(spawn.owner) || !SPAWN_NAMES.contains(spawn.name)
                    || !("(L"+ENTITY+";)Z").equals(spawn.desc)) continue;
            Frame<SourceValue> frame = flow.frames()[i];
            if (frame==null || frame.getStackSize()<args.length+1) continue;
            int receiver = frame.getStackSize()-args.length-1;
            SourceValue worldArg = frame.getStack(receiver+1);
            SourceValue throwerArg = frame.getStack(receiver+2);
            SourceValue selectorArg = frame.getStack(receiver+3);
            if (!isLocal(flow, worldArg, 2, 0, new HashSet<>()) || !isLocal(flow, throwerArg, 3, 0, new HashSet<>())) continue;
            String selector = args[2].getInternalName();
            FieldRef map = proveMetadataSelectorLookup(flow, selectorArg, selector);
            if (map==null) continue;
            return new SpawnBinding(ctor.owner, selector, map);
        }
        return null;
    }

    private FieldRef proveMetadataSelectorLookup(Flow flow, SourceValue value, String selectorClass) {
        AbstractInsnNode producer = singleProducer(value);
        if (!(producer instanceof TypeInsnNode cast) || cast.getOpcode()!=Opcodes.CHECKCAST || !selectorClass.equals(cast.desc)) return null;
        Integer castIndex = flow.indices().get(producer);
        if (castIndex==null) return null;
        Frame<SourceValue> castFrame = flow.frames()[castIndex];
        if (castFrame==null || castFrame.getStackSize()<1) return null;
        AbstractInsnNode rawProducer = singleProducer(castFrame.getStack(castFrame.getStackSize()-1));
        if (!(rawProducer instanceof MethodInsnNode get) || !"java/util/Map".equals(get.owner) || !"get".equals(get.name)
                || !"(Ljava/lang/Object;)Ljava/lang/Object;".equals(get.desc)) return null;
        Integer getIndex = flow.indices().get(rawProducer);
        if (getIndex==null) return null;
        Frame<SourceValue> getFrame = flow.frames()[getIndex];
        if (getFrame==null || getFrame.getStackSize()<2) return null;
        SourceValue receiver = getFrame.getStack(getFrame.getStackSize()-2);
        SourceValue key = getFrame.getStack(getFrame.getStackSize()-1);
        FieldRef map = staticField(receiver);
        if (map==null || !("Ljava/util/Map;".equals(map.desc()) || "Ljava/util/HashMap;".equals(map.desc()))) return null;
        return isMetadataBox(flow,key) ? map : null;
    }

    private boolean isMetadataBox(Flow flow, SourceValue value) {
        AbstractInsnNode producer = singleProducer(value);
        if (!(producer instanceof MethodInsnNode box) || box.getOpcode()!=Opcodes.INVOKESTATIC || !"java/lang/Integer".equals(box.owner)
                || !"valueOf".equals(box.name) || !"(I)Ljava/lang/Integer;".equals(box.desc)) return false;
        Integer index=flow.indices().get(producer); if(index==null)return false;
        Frame<SourceValue> frame=flow.frames()[index]; if(frame==null||frame.getStackSize()<1)return false;
        AbstractInsnNode metadataProducer=singleProducer(frame.getStack(frame.getStackSize()-1));
        if(!(metadataProducer instanceof MethodInsnNode metadata) || !ITEM_STACK.equals(metadata.owner) || !DAMAGE_NAMES.contains(metadata.name)
                || !"()I".equals(metadata.desc)) return false;
        Integer metadataIndex=flow.indices().get(metadataProducer); if(metadataIndex==null)return false;
        Frame<SourceValue> metadataFrame=flow.frames()[metadataIndex];
        return metadataFrame!=null&&metadataFrame.getStackSize()>0
                &&isLocal(flow,metadataFrame.getStack(metadataFrame.getStackSize()-1),1,0,new HashSet<>());
    }

    private String proveProjectileSelectorField(SpawnBinding binding) {
        ClassNode projectile=classes.get(binding.projectileClass());
        if(projectile==null)return "Missing custom EntitySnowball projectile class "+binding.projectileClass()+".";
        String desc="(L"+WORLD+";L"+LIVING+";L"+binding.selectorClass()+";)V";
        MethodNode ctor=findDeclared(projectile,Set.of("<init>"),desc);
        if(ctor==null)return "Missing projectile constructor "+binding.projectileClass()+desc+".";
        for(AbstractInsnNode insn=ctor.instructions.getFirst();insn!=null;insn=insn.getNext()){
            if(!(insn instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.PUTFIELD||!projectile.name.equals(field.owner)
                    ||!("L"+binding.selectorClass()+";").equals(field.desc))continue;
            AbstractInsnNode value=previousMeaningful(insn.getPrevious());
            AbstractInsnNode receiver=previousMeaningful(value==null?null:value.getPrevious());
            if(value instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ALOAD&&v.var==3
                    &&receiver instanceof VarInsnNode r&&r.getOpcode()==Opcodes.ALOAD&&r.var==0)return null;
        }
        return "Projectile constructor did not prove selector argument storage on the projectile instance.";
    }

    private EnumShape proveEnumShape(String selectorClass) throws AnalyzerException {
        ClassNode selector=classes.get(selectorClass);
        if(selector==null||(selector.access&Opcodes.ACC_ENUM)==0)return null;
        String idField=directGetterField(selector,Type.INT_TYPE);
        String damageField=directGetterField(selector,Type.BYTE_TYPE);
        if(idField==null||damageField==null)return null;
        MethodNode idGetter=getterForField(selector,idField,"I");
        MethodNode damageGetter=getterForField(selector,damageField,"B");
        if(idGetter==null||damageGetter==null)return null;
        MethodNode clinit=findDeclared(selector,Set.of("<clinit>"),"()V"); if(clinit==null)return null;
        Flow flow=flow(selector,clinit); List<Variant> variants=new ArrayList<>(); String constructorDesc=null;
        for(int i=0;i<clinit.instructions.size();i++){
            AbstractInsnNode insn=clinit.instructions.get(i);
            if(!(insn instanceof MethodInsnNode ctor)||ctor.getOpcode()!=Opcodes.INVOKESPECIAL||!selector.name.equals(ctor.owner)||!"<init>".equals(ctor.name))continue;
            Type[] args=Type.getArgumentTypes(ctor.desc);
            if(args.length!=4||args[0].getSort()!=Type.OBJECT||!"java/lang/String".equals(args[0].getInternalName())
                    ||args[1].getSort()!=Type.INT||args[2].getSort()!=Type.INT||args[3].getSort()!=Type.INT)return null;
            Frame<SourceValue> frame=flow.frames()[i]; if(frame==null||frame.getStackSize()<5)return null;
            int base=frame.getStackSize()-args.length;
            Integer id=scalarInt(frame.getStack(base+2)); Integer damage=scalarInt(frame.getStack(base+3));
            if(id==null||damage==null||damage<Byte.MIN_VALUE||damage>Byte.MAX_VALUE)return null;
            AbstractInsnNode next=nextMeaningful(insn.getNext());
            if(!(next instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.PUTSTATIC||!selector.name.equals(field.owner)
                    ||!("L"+selector.name+";").equals(field.desc))return null;
            FieldNode declared=selector.fields.stream().filter(f->f.name.equals(field.name)&&f.desc.equals(field.desc)&&(f.access&Opcodes.ACC_ENUM)!=0).findFirst().orElse(null);
            if(declared==null)return null;
            if(constructorDesc==null)constructorDesc=ctor.desc; else if(!constructorDesc.equals(ctor.desc))return null;
            variants.add(new Variant(field.name,id,damage));
        }
        if(variants.isEmpty()||constructorDesc==null||!constructorStores(selector,constructorDesc,idField,damageField))return null;
        variants.sort(Comparator.comparingInt(Variant::id));
        Set<Integer> ids=new HashSet<>(); for(Variant variant:variants)if(variant.id()<0||!ids.add(variant.id()))return null;
        return new EnumShape(idGetter.name,damageGetter.name,List.copyOf(variants));
    }

    private static boolean constructorStores(ClassNode owner,String descriptor,String idField,String damageField){
        MethodNode ctor=findDeclared(owner,Set.of("<init>"),descriptor);if(ctor==null)return false;
        boolean id=false,damage=false;
        for(AbstractInsnNode insn=ctor.instructions.getFirst();insn!=null;insn=insn.getNext()){
            if(!(insn instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.PUTFIELD||!owner.name.equals(field.owner))continue;
            if(field.name.equals(idField)&&"I".equals(field.desc)){
                AbstractInsnNode value=previousMeaningful(insn.getPrevious());AbstractInsnNode receiver=previousMeaningful(value==null?null:value.getPrevious());
                id=value instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ILOAD&&v.var==3&&receiver instanceof VarInsnNode r&&r.getOpcode()==Opcodes.ALOAD&&r.var==0;
            }else if(field.name.equals(damageField)&&"B".equals(field.desc)){
                AbstractInsnNode cast=previousMeaningful(insn.getPrevious());AbstractInsnNode value=previousMeaningful(cast==null?null:cast.getPrevious());AbstractInsnNode receiver=previousMeaningful(value==null?null:value.getPrevious());
                damage=cast instanceof InsnNode c&&c.getOpcode()==Opcodes.I2B&&value instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ILOAD&&v.var==4
                        &&receiver instanceof VarInsnNode r&&r.getOpcode()==Opcodes.ALOAD&&r.var==0;
            }
        }
        return id&&damage;
    }

    private static String directGetterField(ClassNode owner,Type returnType){
        String desc="()"+returnType.getDescriptor();String fieldDesc=returnType.getDescriptor();
        for(MethodNode method:owner.methods){
            if(!method.desc.equals(desc)||(method.access&Opcodes.ACC_STATIC)!=0)continue;
            List<AbstractInsnNode> meaningful=meaningful(method);
            int ret=returnType.equals(Type.BYTE_TYPE)||returnType.equals(Type.INT_TYPE)?Opcodes.IRETURN:-1;
            if(meaningful.size()==3&&meaningful.get(0) instanceof VarInsnNode load&&load.getOpcode()==Opcodes.ALOAD&&load.var==0
                    &&meaningful.get(1) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&owner.name.equals(field.owner)&&fieldDesc.equals(field.desc)
                    &&meaningful.get(2).getOpcode()==ret)return field.name;
        }
        return null;
    }

    private static MethodNode getterForField(ClassNode owner,String fieldName,String desc){
        for(MethodNode method:owner.methods){
            if(!method.desc.equals("()"+desc)||(method.access&Opcodes.ACC_STATIC)!=0)continue;
            for(AbstractInsnNode insn=method.instructions.getFirst();insn!=null;insn=insn.getNext())
                if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&owner.name.equals(field.owner)&&fieldName.equals(field.name))return method;
        }
        return null;
    }

    private Flow flow(ClassNode owner,MethodNode method)throws AnalyzerException{
        String key=owner.name+'\u0000'+method.name+'\u0000'+method.desc;Flow cached=flows.get(key);if(cached!=null)return cached;
        Analyzer<SourceValue> analyzer=new Analyzer<>(new SourceInterpreter());Frame<SourceValue>[] frames=analyzer.analyze(owner.name,method);
        Map<AbstractInsnNode,Integer> indices=new IdentityHashMap<>();for(int i=0;i<method.instructions.size();i++)indices.put(method.instructions.get(i),i);
        Flow result=new Flow(owner,method,frames,indices);flows.put(key,result);return result;
    }

    private boolean isLocal(Flow flow,SourceValue value,int local,int depth,Set<AbstractInsnNode> guard){
        if(value==null||depth>16||value.insns==null||value.insns.isEmpty())return false;
        for(AbstractInsnNode producer:value.insns){
            if(!guard.add(producer))return false;boolean ok;
            if(producer instanceof VarInsnNode variable&&variable.getOpcode()==Opcodes.ALOAD)ok=variable.var==local;
            else if(producer instanceof TypeInsnNode cast&&cast.getOpcode()==Opcodes.CHECKCAST){Integer index=flow.indices().get(producer);Frame<SourceValue> frame=index==null?null:flow.frames()[index];ok=frame!=null&&frame.getStackSize()>0&&isLocal(flow,frame.getStack(frame.getStackSize()-1),local,depth+1,guard);}else ok=false;
            guard.remove(producer);if(!ok)return false;
        }
        return true;
    }

    private static FieldRef staticField(SourceValue value){AbstractInsnNode producer=singleProducer(value);return producer instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC?new FieldRef(field.owner,field.name,field.desc):null;}
    private static AbstractInsnNode singleProducer(SourceValue value){return value!=null&&value.insns!=null&&value.insns.size()==1?value.insns.iterator().next():null;}
    private static Integer scalarInt(SourceValue value){AbstractInsnNode producer=singleProducer(value);if(producer instanceof IntInsnNode integer)return integer.operand;if(producer instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer integer)return integer;if(producer instanceof InsnNode insn&&insn.getOpcode()>=Opcodes.ICONST_M1&&insn.getOpcode()<=Opcodes.ICONST_5)return insn.getOpcode()-Opcodes.ICONST_0;return null;}
    private static List<AbstractInsnNode> meaningful(MethodNode method){List<AbstractInsnNode> out=new ArrayList<>();for(AbstractInsnNode i=method.instructions.getFirst();i!=null;i=i.getNext())if(!(i instanceof LabelNode||i instanceof LineNumberNode||i instanceof FrameNode))out.add(i);return out;}
    private static AbstractInsnNode nextMeaningful(AbstractInsnNode node){while(node instanceof LabelNode||node instanceof LineNumberNode||node instanceof FrameNode)node=node.getNext();return node;}
    private static AbstractInsnNode previousMeaningful(AbstractInsnNode node){while(node instanceof LabelNode||node instanceof LineNumberNode||node instanceof FrameNode)node=node.getPrevious();return node;}
    private static MethodNode findDeclared(ClassNode owner,Set<String> names,String desc){if(owner==null)return null;for(MethodNode method:owner.methods)if(names.contains(method.name)&&desc.equals(method.desc))return method;return null;}
    private boolean reaches(String source,String target){String current=source;Set<String> seen=new HashSet<>();while(current!=null&&seen.add(current)){if(target.equals(current))return true;ClassNode node=classes.get(current);if(node==null)return false;current=node.superName;}return false;}

    private void load(Path jarPath)throws IOException{
        try(JarFile jar=new JarFile(jarPath.toFile())){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException malformed){diagnostics.add("Unreadable variant-snowball class "+entry.getName()+": "+malformed.getClass().getSimpleName());}}}
    }
}
