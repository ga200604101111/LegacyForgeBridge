package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Source-only proof for remote FML projectile presentation families.
 *
 * <p>Admission is intentionally semantic rather than name-based: a registered entity must inherit
 * a supported 1.7 projectile base, have one renderer binding, have no source additional-spawn-data
 * interface, and be spawned directly by one registered item from a supported item-use callback.
 * Vanilla RenderSnowball bindings prove their presentation carrier independently from the launcher,
 * matching legacy mods that deliberately render an arrow-family projectile as another registered
 * item (including an intentionally transparent carrier). Custom throwable renderers remain stricter:
 * their icon selector must still bind to the spawning item. Arrow-family custom mesh renderers are
 * admitted only when source yaw/pitch orientation and one fixed source texture are proven.</p>
 */
public final class LegacyProjectilePresentationAnalyzer {
    private static final String ENTITY_ARROW="net/minecraft/entity/projectile/EntityArrow";
    private static final String ENTITY_THROWABLE="net/minecraft/entity/projectile/EntityThrowable";
    private static final String I_PROJECTILE="net/minecraft/entity/IProjectile";
    private static final String ADDITIONAL_SPAWN="cpw/mods/fml/common/registry/IEntityAdditionalSpawnData";
    private static final String WORLD="net/minecraft/world/World";
    private static final String ITEM="net/minecraft/item/Item";
    private static final String DATA_WATCHER="net/minecraft/entity/DataWatcher";
    private static final String RENDER_SNOWBALL="net/minecraft/client/renderer/entity/RenderSnowball";
    private static final String RENDERING_REGISTRY="cpw/mods/fml/client/registry/RenderingRegistry";
    private static final Set<String> ITEM_SPAWN_CALLBACKS=Set.of(
            "onItemRightClick","func_77659_a","onPlayerStoppedUsing","func_77615_a");
    private static final Set<String> SPAWN=Set.of("spawnEntityInWorld","func_72838_d");
    private static final Set<String> WATCH_BYTE=Set.of("getWatchableObjectByte","func_75683_a");
    private static final Set<String> WATCH_SHORT=Set.of("getWatchableObjectShort","func_75693_b");
    private static final Set<String> WATCH_INT=Set.of("getWatchableObjectInt","func_75679_c");

    public enum Adapter { THROWN_ITEM, ORIENTED_ITEM }
    public enum BaseFamily { ARROW, THROWABLE }

    public record Rule(String registryName,String sourceClass,String rendererClass,
                       int legacyNumericId,int trackingRange,int updateFrequency,boolean velocityUpdates,
                       float width,float height,BaseFamily baseFamily,Adapter adapter,
                       String sourceItemClass,String sourceItemRegistryName,
                       int metadataWatcherIndex,int metadataWatcherWireType,int metadataOffset,int defaultItemMetadata,
                       String fixedTexture,String proof) {
        public Rule {
            if(registryName==null||registryName.isBlank()||sourceClass==null||rendererClass==null
                    ||legacyNumericId<0||trackingRange<=0||updateFrequency<=0||width<=0F||height<=0F
                    ||baseFamily==null||adapter==null||sourceItemClass==null||sourceItemRegistryName==null
                    ||metadataWatcherIndex<-1||metadataWatcherWireType<-1||metadataWatcherWireType>6)
                throw new IllegalArgumentException("Invalid projectile presentation proof");
            if(defaultItemMetadata<0)
                throw new IllegalArgumentException("Negative projectile presentation metadata");
            if(metadataWatcherIndex<0 && (metadataWatcherWireType!=-1||metadataOffset!=0))
                throw new IllegalArgumentException("Unselected projectile metadata cannot carry dynamic selector state");
        }
    }
    public record Skipped(String registryName,String sourceClass,String reason){}
    public record Analysis(List<Rule> rules,List<Skipped> skipped,List<String> diagnostics){
        public Analysis{rules=List.copyOf(rules);skipped=List.copyOf(skipped);diagnostics=List.copyOf(diagnostics);}
    }

    private record ItemBinding(String registryName,String sourceClass){}
    private record RenderSnowballBinding(ItemBinding item,int metadata){}
    private record Selector(int watcherIndex,int wireType,int offset,int defaultMetadata){}
    private final Map<String,ClassNode> classes=new LinkedHashMap<>();
    private final List<String> diagnostics=new ArrayList<>();
    private Path sourceJar;

    public Analysis analyze(Path jar)throws IOException{
        sourceJar=jar;classes.clear();diagnostics.clear();load(jar);
        var watchers=new LegacyEntityDataWatcherAnalyzer().analyze(jar);
        var renderAnalysis=new LegacyEntityPresentationAnalyzer().analyze(jar);
        var registry=new LegacyRegistryAnalyzer().analyze(jar);
        diagnostics.addAll(watchers.diagnostics());diagnostics.addAll(renderAnalysis.diagnostics());diagnostics.addAll(registry.diagnostics());

        Map<String,List<LegacyEntityPresentationAnalyzer.Registration>> rendererByEntity=new LinkedHashMap<>();
        for(var registration:renderAnalysis.registrations())
            rendererByEntity.computeIfAbsent(registration.entityClass(),ignored->new ArrayList<>()).add(registration);

        Map<String,LegacyEntityDataWatcherAnalyzer.Rule> watcherByClass=new LinkedHashMap<>();
        for(var rule:watchers.rules())watcherByClass.put(rule.sourceClass(),rule);

        List<Rule> rules=new ArrayList<>();List<Skipped> skipped=new ArrayList<>();
        for(var entity:watchers.rules()){
            boolean copiedArrowWire=copiedArrowWireFamily(entity);
            BaseFamily family=inherits(entity.sourceClass(),ENTITY_ARROW)?BaseFamily.ARROW
                    :inherits(entity.sourceClass(),ENTITY_THROWABLE)?BaseFamily.THROWABLE
                    :copiedArrowWire?BaseFamily.ARROW:null;
            if(family==null)continue;
            if(implementsInterface(entity.sourceClass(),ADDITIONAL_SPAWN)){
                skipped.add(new Skipped(entity.registryName(),entity.sourceClass(),"Projectile implements IEntityAdditionalSpawnData"));continue;
            }
            List<LegacyEntityPresentationAnalyzer.Registration> bindings=rendererByEntity.getOrDefault(entity.sourceClass(),List.of());
            if(bindings.size()!=1){
                skipped.add(new Skipped(entity.registryName(),entity.sourceClass(),"Projectile renderer binding is missing or ambiguous"));continue;
            }
            String renderer=bindings.getFirst().rendererClass();
            boolean vanillaSnowball=RENDER_SNOWBALL.equals(renderer);
            if(!vanillaSnowball&&!classes.containsKey(renderer)){
                skipped.add(new Skipped(entity.registryName(),entity.sourceClass(),"Projectile renderer class is not source-owned"));continue;
            }
            ItemBinding launcher=directUseItem(registry,entity.sourceClass());
            if(!vanillaSnowball&&launcher==null){
                skipped.add(new Skipped(entity.registryName(),entity.sourceClass(),
                        "No unique registered item directly spawns this projectile from a supported use/release callback"));continue;
            }
            float[] size=sourceSize(entity.sourceClass(),family);
            if(size==null){
                skipped.add(new Skipped(entity.registryName(),entity.sourceClass(),"Projectile dimensions are ambiguous"));continue;
            }

            Adapter adapter;Selector selector;String texture=null;String proof;ItemBinding presentationItem=launcher;
            if(vanillaSnowball){
                RenderSnowballBinding rendered=vanillaSnowballBinding(bindings.getFirst(),registry);
                if(rendered==null){
                    skipped.add(new Skipped(entity.registryName(),entity.sourceClass(),
                            "Vanilla RenderSnowball presentation item/metadata is not one uniquely source-proven registration"));continue;
                }
                presentationItem=rendered.item();
                selector=new Selector(-1,-1,0,rendered.metadata());
                adapter=Adapter.THROWN_ITEM;
                proof="Source "+family+" family"
                        +(copiedArrowWire?" via IProjectile + DataWatcher16 byte wire proof":"")
                        +" + exact RenderSnowball(registered presentation item, constant metadata) binding";
            }else if(family==BaseFamily.THROWABLE){
                selector=throwableItemSelector(renderer,entity.sourceClass(),launcher,registry,watcherByClass.get(entity.sourceClass()));
                if(selector==null){
                    skipped.add(new Skipped(entity.registryName(),entity.sourceClass(),"Throwable renderer does not source its icon from the direct-spawn registered item"));continue;
                }
                adapter=Adapter.THROWN_ITEM;
                proof="Source EntityThrowable family + registered launcher callback + renderer item-icon selector";
            }else{
                texture=singleFixedTexture(renderer);
                if(texture==null||!resourceExists(texture)||!provesOrientedProjectileRenderer(renderer)){
                    skipped.add(new Skipped(entity.registryName(),entity.sourceClass(),"Arrow renderer fixed-texture yaw/pitch orientation proof is incomplete"));continue;
                }
                selector=new Selector(-1,-1,0,0);adapter=Adapter.ORIENTED_ITEM;
                proof="Source EntityArrow family + registered launcher callback + fixed-texture yaw/pitch projectile renderer; modern carrier uses the launcher item identity";
            }
            rules.add(new Rule(entity.registryName(),entity.sourceClass(),renderer,entity.numericId(),entity.trackingRange(),
                    entity.updateFrequency(),entity.velocityUpdates(),size[0],size[1],family,adapter,presentationItem.sourceClass(),presentationItem.registryName(),
                    selector.watcherIndex(),selector.wireType(),selector.offset(),selector.defaultMetadata(),texture,proof));
        }
        rules.sort(Comparator.comparing(Rule::registryName));
        return new Analysis(rules,skipped,List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    /**
     * Some 1.7.10 mods copy EntityArrow's network/watch layout into a direct Entity subclass
     * instead of inheriting EntityArrow. Admit that presentation family only when the source
     * explicitly implements IProjectile and defines the canonical watcher-16 flags byte at zero.
     * Remote gameplay remains server-owned; this proof is used only to select the client carrier.
     */
    private boolean copiedArrowWireFamily(LegacyEntityDataWatcherAnalyzer.Rule entity){
        if(entity==null||!implementsInterface(entity.sourceClass(),I_PROJECTILE))return false;
        int matches=0;
        for(var entry:entity.entries())if(entry.index()==16&&"byte".equals(entry.valueKind())
                &&entry.defaultValue() instanceof Number number&&number.byteValue()==0)matches++;
        return matches==1;
    }

    private RenderSnowballBinding vanillaSnowballBinding(LegacyEntityPresentationAnalyzer.Registration registration,
                                                            LegacyRegistryAnalyzer.Analysis registry){
        ClassNode owner=classes.get(registration.sourceOwner());if(owner==null)return null;
        MethodNode method=null;
        for(MethodNode candidate:owner.methods)if(candidate.name.equals(registration.sourceMethod())
                &&candidate.desc.equals(registration.sourceDescriptor())){method=candidate;break;}
        if(method==null)return null;
        List<AbstractInsnNode> code=real(method);LinkedHashSet<RenderSnowballBinding> matches=new LinkedHashSet<>();
        for(int i=0;i<code.size();i++){
            if(!(code.get(i) instanceof MethodInsnNode register)||register.getOpcode()!=Opcodes.INVOKESTATIC
                    ||!register.owner.equals(RENDERING_REGISTRY)||!register.name.equals("registerEntityRenderingHandler")
                    ||!register.desc.equals("(Ljava/lang/Class;Lnet/minecraft/client/renderer/entity/Render;)V"))continue;
            int start=Math.max(0,i-12);boolean entity=false,newRenderer=false;MethodInsnNode constructor=null;
            for(int j=start;j<i;j++){
                AbstractInsnNode insn=code.get(j);
                if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Type type&&type.getSort()==Type.OBJECT
                        &&type.getInternalName().equals(registration.entityClass()))entity=true;
                if(insn instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW&&type.desc.equals(RENDER_SNOWBALL))newRenderer=true;
                if(insn instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL
                        &&call.owner.equals(RENDER_SNOWBALL)&&call.name.equals("<init>")
                        &&Set.of("(Lnet/minecraft/item/Item;)V","(Lnet/minecraft/item/Item;I)V").contains(call.desc))constructor=call;
            }
            if(!entity||!newRenderer||constructor==null)continue;
            int metadata=0;
            AbstractInsnNode itemSource=previousReal(constructor.getPrevious());
            if(constructor.desc.equals("(Lnet/minecraft/item/Item;I)V")){
                Integer fixed=intConstant(itemSource);if(fixed==null||fixed<0)continue;
                metadata=fixed;itemSource=previousReal(itemSource.getPrevious());
            }
            if(!(itemSource instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.GETSTATIC)continue;
            String fieldType=objectType(field.desc);
            if(fieldType==null||!inherits(fieldType,ITEM))continue;
            for(var binding:registry.fieldBindings())if(binding.kind()==LegacyRegistryAnalyzer.Kind.ITEM
                    &&binding.owner().equals(field.owner)&&binding.name().equals(field.name)&&binding.descriptor().equals(field.desc))
                matches.add(new RenderSnowballBinding(new ItemBinding(binding.registryName(),binding.implementationClass()),metadata));

            LegacyVanillaRegistry1710.resolve(field.owner,field.name)
                    .filter(entry->entry.kind()==LegacyRegistryAnalyzer.Kind.ITEM)
                    .ifPresent(entry->matches.add(new RenderSnowballBinding(
                            new ItemBinding(entry.registryName(),LegacyVanillaRegistry1710.ITEMS_OWNER),metadata)));
        }
        return matches.size()==1?matches.getFirst():null;
    }

    private ItemBinding directUseItem(LegacyRegistryAnalyzer.Analysis registry,String entityClass){
        List<ItemBinding> matches=new ArrayList<>();
        for(var item:registry.items()){
            if(item.implementationClass()==null)continue;
            boolean matched=false;
            Set<String> seen=new HashSet<>();
            for(String current=item.implementationClass();current!=null&&seen.add(current)&&!matched;){
                ClassNode node=classes.get(current);if(node==null)break;
                for(MethodNode method:node.methods){
                    if(!ITEM_SPAWN_CALLBACKS.contains(method.name)||!method.desc.contains("Lnet/minecraft/world/World;"))continue;
                    boolean creates=false,spawns=false;
                    for(AbstractInsnNode insn:method.instructions){
                        if(insn instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW&&type.desc.equals(entityClass))creates=true;
                        if(insn instanceof MethodInsnNode call&&call.owner.equals(WORLD)&&SPAWN.contains(call.name)
                                &&call.desc.equals("(Lnet/minecraft/entity/Entity;)Z"))spawns=true;
                    }
                    if(creates&&spawns){matched=true;break;}
                }
                current=node.superName;
            }
            if(matched)matches.add(new ItemBinding(item.registryName(),item.implementationClass()));
        }
        return matches.size()==1?matches.getFirst():null;
    }

    private Selector throwableItemSelector(String renderer,String entityClass,ItemBinding item,
                                           LegacyRegistryAnalyzer.Analysis registry,LegacyEntityDataWatcherAnalyzer.Rule watcherRule){
        MethodNode icon=effective(renderer,Set.of("getIcon"),"(Lnet/minecraft/entity/Entity;)Lnet/minecraft/util/IIcon;");
        if(icon==null)return null;
        LegacyRegistryAnalyzer.FieldBinding itemField=null;
        for(AbstractInsnNode insn:icon.instructions)if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC){
            String fieldType=objectType(field.desc);if(fieldType==null||!inherits(fieldType,ITEM))continue;
            for(var binding:registry.fieldBindings())if(binding.kind()==LegacyRegistryAnalyzer.Kind.ITEM
                    &&binding.owner().equals(field.owner)&&binding.name().equals(field.name)&&binding.descriptor().equals(field.desc)){
                if(itemField!=null&&!itemField.equals(binding))return null;itemField=binding;
            }
        }
        if(itemField==null||!itemField.registryName().equals(item.registryName())||!itemField.implementationClass().equals(item.sourceClass()))return null;
        boolean itemIcon=false;MethodInsnNode selectorCall=null;
        List<AbstractInsnNode> code=real(icon);
        for(AbstractInsnNode insn:code)if(insn instanceof MethodInsnNode call){
            if(call.owner.equals(ITEM)&&call.desc.equals("(I)Lnet/minecraft/util/IIcon;")
                    &&Set.of("getIconFromDamage","func_77617_a").contains(call.name))itemIcon=true;
            if(ownerInSourceHierarchy(entityClass,call.owner)
                    &&Set.of("()B","()S","()I").contains(call.desc)){if(selectorCall!=null)return null;selectorCall=call;}
        }
        if(!itemIcon)return null;
        if(selectorCall==null)return new Selector(-1,-1,0,0);
        MethodNode getter=effective(entityClass,Set.of(selectorCall.name),selectorCall.desc);if(getter==null)return null;
        int index=directWatcherIndex(getter);if(index<0)return null;
        int wireType=switch(selectorCall.desc){case "()B"->0;case "()S"->1;case "()I"->2;default->-1;};
        Object defaultValue=null;
        if(watcherRule!=null)for(var entry:watcherRule.entries())if(entry.index()==index){
            if(wireType!=wireType(entry.valueKind()))return null;defaultValue=entry.defaultValue();break;
        }
        if(!(defaultValue instanceof Number number))return null;
        int offset=selectorOffset(code,selectorCall);
        int metadata=Math.max(0,number.intValue()+offset);
        return new Selector(index,wireType,offset,metadata);
    }

    private static int selectorOffset(List<AbstractInsnNode> code,MethodInsnNode selectorCall){
        int index=code.indexOf(selectorCall);if(index<0)return 0;
        for(int i=index+1;i<Math.min(code.size(),index+5);i++){
            int opcode=code.get(i).getOpcode();
            if(opcode==Opcodes.ISUB&&i>index+1){Integer value=intConstant(code.get(i-1));if(value!=null)return -value;}
            if(opcode==Opcodes.IADD&&i>index+1){Integer value=intConstant(code.get(i-1));if(value!=null)return value;}
            if(code.get(i) instanceof MethodInsnNode)break;
        }
        return 0;
    }

    private static int directWatcherIndex(MethodNode getter){
        List<AbstractInsnNode> code=real(getter);
        for(int i=1;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals(DATA_WATCHER)
                &&(WATCH_BYTE.contains(call.name)||WATCH_SHORT.contains(call.name)||WATCH_INT.contains(call.name))){
            Integer value=intConstant(code.get(i-1));if(value!=null&&value>=0&&value<=31)return value;
        }
        return -1;
    }

    private float[] sourceSize(String entityClass,BaseFamily family){
        LinkedHashSet<String> seen=new LinkedHashSet<>();LinkedHashSet<String> values=new LinkedHashSet<>();
        for(String current=entityClass;current!=null&&seen.add(current);){
            ClassNode node=classes.get(current);if(node==null)break;
            for(MethodNode method:node.methods)if(method.name.equals("<init>")){
                List<AbstractInsnNode> code=real(method);
                for(int i=2;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call
                        &&Set.of("setSize","func_70105_a").contains(call.name)&&call.desc.equals("(FF)V")){
                    Float w=floatConstant(code.get(i-2)),h=floatConstant(code.get(i-1));
                    if(w!=null&&h!=null&&w>0F&&h>0F)values.add(w+","+h);
                }
            }
            current=node.superName;
        }
        if(values.size()==1){
            String[] split=values.getFirst().split(",");return new float[]{Float.parseFloat(split[0]),Float.parseFloat(split[1])};
        }
        if(values.isEmpty()&&family==BaseFamily.ARROW)return new float[]{.5F,.5F};
        if(values.isEmpty()&&family==BaseFamily.THROWABLE)return new float[]{.25F,.25F};
        return null;
    }

    private String singleFixedTexture(String renderer){
        LinkedHashSet<String> textures=new LinkedHashSet<>();Set<String> seen=new HashSet<>();
        for(String current=renderer;current!=null&&seen.add(current);){
            ClassNode node=classes.get(current);if(node==null)break;
            for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions)
                if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof String text&&text.indexOf(':')>0
                        &&text.contains("textures/")&&text.endsWith(".png"))textures.add(text);
            current=node.superName;
        }
        return textures.size()==1?textures.getFirst():null;
    }

    private boolean provesOrientedProjectileRenderer(String renderer){
        Set<String> seen=new HashSet<>();int rotations=0;boolean yawNinety=false,scale=false;
        for(String current=renderer;current!=null&&seen.add(current);){
            ClassNode node=classes.get(current);if(node==null)break;
            for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions){
                if(insn instanceof MethodInsnNode call&&call.owner.equals("org/lwjgl/opengl/GL11")){
                    if(call.name.equals("glRotatef")&&call.desc.equals("(FFFF)V"))rotations++;
                    if(call.name.equals("glScalef")&&call.desc.equals("(FFF)V"))scale=true;
                }
                Float value=floatConstant(insn);if(value!=null&&Float.compare(value,90F)==0)yawNinety=true;
            }
            current=node.superName;
        }
        return rotations>=2&&yawNinety&&scale;
    }

    private boolean resourceExists(String texture){
        int split=texture.indexOf(':');if(split<=0)return false;
        String path="assets/"+texture.substring(0,split)+"/"+texture.substring(split+1);
        try(JarFile jar=new JarFile(sourceJar.toFile(),false)){return jar.getJarEntry(path)!=null;}catch(IOException ignored){return false;}
    }

    private boolean implementsInterface(String sourceClass,String target){
        Set<String> seen=new HashSet<>();ArrayDeque<String> queue=new ArrayDeque<>();queue.add(sourceClass);
        while(!queue.isEmpty()){
            String current=queue.removeFirst();if(!seen.add(current))continue;if(current.equals(target))return true;
            ClassNode node=classes.get(current);if(node==null)continue;
            if(node.superName!=null)queue.add(node.superName);queue.addAll(node.interfaces);
        }
        return false;
    }
    private boolean inherits(String sourceClass,String target){
        Set<String> seen=new HashSet<>();for(String current=sourceClass;current!=null&&seen.add(current);){
            if(current.equals(target))return true;ClassNode node=classes.get(current);current=node==null?null:node.superName;
        }return false;
    }
    private boolean ownerInSourceHierarchy(String sourceClass,String owner){
        Set<String> seen=new HashSet<>();for(String current=sourceClass;current!=null&&seen.add(current);){
            if(current.equals(owner))return true;ClassNode node=classes.get(current);current=node==null?null:node.superName;
        }return false;
    }
    private MethodNode effective(String owner,Set<String> names,String desc){
        Set<String> seen=new HashSet<>();for(String current=owner;current!=null&&seen.add(current);){
            ClassNode node=classes.get(current);if(node==null)return null;
            for(MethodNode method:node.methods)if(names.contains(method.name)&&method.desc.equals(desc))return method;
            current=node.superName;
        }return null;
    }

    private static String objectType(String descriptor){
        try{
            Type type=Type.getType(descriptor);
            return type.getSort()==Type.OBJECT?type.getInternalName():null;
        }catch(IllegalArgumentException invalid){return null;}
    }

    private static int wireType(String kind){return switch(kind){case "byte"->0;case "short"->1;case "int"->2;case "float"->3;case "string"->4;case "itemstack"->5;case "coordinates"->6;default->-1;};}
    private static Integer intConstant(AbstractInsnNode insn){
        if(insn==null)return null;return switch(insn.getOpcode()){
            case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;
            case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;
            case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer value?value:null;default->null;};}
    private static Float floatConstant(AbstractInsnNode insn){
        if(insn==null)return null;return switch(insn.getOpcode()){
            case Opcodes.FCONST_0->0F;case Opcodes.FCONST_1->1F;case Opcodes.FCONST_2->2F;
            case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Number n?n.floatValue():null;default->null;};}
    private static List<AbstractInsnNode> real(MethodNode method){
        List<AbstractInsnNode> out=new ArrayList<>();if(method!=null)for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)out.add(insn);return out;
    }
    private void load(Path jar)throws IOException{
        try(JarFile input=new JarFile(jar.toFile(),false)){Enumeration<JarEntry> entries=input.entries();while(entries.hasMoreElements()){
            JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;
            try(InputStream stream=input.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(stream).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}
            catch(RuntimeException malformed){diagnostics.add("Unreadable projectile class "+entry.getName()+": "+malformed.getClass().getSimpleName());}
        }}
    }
}
