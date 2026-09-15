package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Fail-closed analyzer for decorative BlockContainer instances with exactly one ephemeral,
 * client-only oscillating float joint state. This family intentionally excludes NBT-backed or
 * gameplay-authoritative TileEntities.
 */
public final class LegacyOscillatingModelBlockAnalyzer {
    private static final String BLOCK_CONTAINER="net/minecraft/block/BlockContainer";
    private static final String TILE_ENTITY="net/minecraft/tileentity/TileEntity";

    public record Rule(String registryName,String legacyNamespace,String sourceBlockClass,String sourceTileClass,
                       String legacyTileId,int[] placementMetaByYawQuadrant,boolean fullCube,int lightEmission,
                       boolean nonOpaque,boolean nonNormalRender,int renderPass,String angleField,String directionField,
                       int randomInitialBound,float stepDegrees,float lowerBoundDegrees,float upperBoundDegrees,
                       boolean clientOnly,boolean nbtPersistent) {
        public Rule { placementMetaByYawQuadrant=placementMetaByYawQuadrant.clone(); }
        @Override public int[] placementMetaByYawQuadrant(){return placementMetaByYawQuadrant.clone();}
    }
    public record Skipped(String registryName,String sourceBlockClass,String reason) { }
    public record Analysis(List<Rule> rules,List<Skipped> skipped,List<String> diagnostics) {
        public Analysis { rules=List.copyOf(rules);skipped=List.copyOf(skipped);diagnostics=List.copyOf(diagnostics); }
    }
    private record Animation(String angleField,String directionField,int initialBound,float step,float lower,float upper) { }

    public Analysis analyze(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=loadClasses(jarPath);
        var registry=new LegacyRegistryAnalyzer().analyze(jarPath);
        var lifecycle=new LegacyLifecycleAnalyzer().analyze(jarPath);
        Map<String,String> tileIds=registeredTiles(lifecycle);
        List<Rule> rules=new ArrayList<>();List<Skipped> skipped=new ArrayList<>();List<String> diagnostics=new ArrayList<>();
        diagnostics.addAll(registry.diagnostics());diagnostics.addAll(lifecycle.diagnostics());
        for(var registration:registry.blocks()){
            String blockClass=registration.implementationClass();if(blockClass==null||!inherits(classes,blockClass,BLOCK_CONTAINER))continue;
            MethodNode create=effectiveMethod(classes,blockClass,Set.of("createNewTileEntity","func_149915_a"),"(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;");
            String tileClass=uniqueCreatedType(create);if(tileClass==null||!tileIds.containsKey(tileClass)||!inherits(classes,tileClass,TILE_ENTITY))continue;
            ClassNode block=classes.get(blockClass),tile=classes.get(tileClass);if(block==null||tile==null)continue;
            Animation animation=animation(tile);
            if(animation==null)continue;
            if(hasOwnNbt(tile)){
                skipped.add(skip(registration,blockClass,"oscillating decorative TileEntity persists source state in NBT"));continue;
            }
            MethodNode tick=ownMethod(tile,Set.of("updateEntity","func_145845_h"),"()V");
            if(!clientOnlyTick(tick,animation.angleField(),animation.directionField())){
                skipped.add(skip(registration,blockClass,"oscillating TileEntity mutation is not proven client-only"));continue;
            }
            int[] orientation=yawMetadataTable(effectiveMethod(classes,blockClass,Set.of("onBlockPlacedBy","func_149689_a"),"(Lnet/minecraft/world/World;IIILnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;)V"));
            if(orientation==null){skipped.add(skip(registration,blockClass,"placement metadata is not a complete direct yaw-quadrant table"));continue;}
            if(!Boolean.FALSE.equals(returnedBoolean(ownMethod(block,Set.of("isOpaqueCube","func_149662_c"),"()Z")))
                    ||!Boolean.FALSE.equals(returnedBoolean(ownMethod(block,Set.of("renderAsNormalBlock","func_149686_d"),"()Z")))){
                skipped.add(skip(registration,blockClass,"animated decorative block is not proven non-opaque/non-normal"));continue;
            }
            Integer pass=returnedInt(ownMethod(block,Set.of("getRenderBlockPass","func_149656_h"),"()I"));
            if(pass==null||pass<0||pass>1){skipped.add(skip(registration,blockClass,"legacy render pass is unresolved"));continue;}
            if(!defaultFullCube(classes,blockClass)){
                skipped.add(skip(registration,blockClass,"animated decorative block changes source bounds/collision shape"));continue;
            }
            if(sourceLightLevel(classes,blockClass)!=0){skipped.add(skip(registration,blockClass,"animated decorative block has an unhandled source light override"));continue;}
            rules.add(new Rule(registration.registryName(),registration.legacyNamespace(),blockClass,tileClass,tileIds.get(tileClass),orientation,true,0,true,true,pass,
                    animation.angleField(),animation.directionField(),animation.initialBound(),animation.step(),animation.lower(),animation.upper(),true,false));
        }
        return new Analysis(rules,skipped,List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static Animation animation(ClassNode tile){
        List<FieldNode> instance=tile.fields.stream().filter(field->(field.access&Opcodes.ACC_STATIC)==0).toList();
        List<FieldNode> floats=instance.stream().filter(field->field.desc.equals("F")).toList();
        List<FieldNode> bools=instance.stream().filter(field->field.desc.equals("Z")).toList();
        if(floats.size()!=1||bools.size()!=1||instance.size()!=2)return null;
        boolean random=tile.fields.stream().anyMatch(field->(field.access&Opcodes.ACC_STATIC)!=0&&field.desc.equals("Ljava/util/Random;"));if(!random)return null;
        String angle=floats.getFirst().name,direction=bools.getFirst().name;
        MethodNode ctor=ownMethod(tile,Set.of("<init>"),"()V");MethodNode tick=ownMethod(tile,Set.of("updateEntity","func_145845_h"),"()V");if(ctor==null||tick==null)return null;
        Integer bound=randomInitialBound(ctor,tile.name,angle);if(bound==null||bound<=1)return null;
        if(!randomBooleanInitializes(ctor,tile.name,direction))return null;
        Set<Double> doubles=new LinkedHashSet<>();Set<Float> floatsInTick=new LinkedHashSet<>();int angleWrites=0,directionWrites=0;
        for(AbstractInsnNode insn:tick.instructions){
            if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Double d&&d>0)doubles.add(d);
            if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Float f&&f>0)floatsInTick.add(f);
            if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(tile.name)){if(field.name.equals(angle)&&field.desc.equals("F"))angleWrites++;if(field.name.equals(direction)&&field.desc.equals("Z"))directionWrites++;}
        }
        if(doubles.size()!=1||angleWrites<2||directionWrites<2||!hasOpcode(tick,Opcodes.DADD)||!hasOpcode(tick,Opcodes.DSUB))return null;
        float step=doubles.iterator().next().floatValue();
        float upper=Float.NaN;for(Float value:floatsInTick)if(value>step&&(Float.isNaN(upper)||value>upper))upper=value;
        if(!Float.isFinite(upper)||upper<=0||!containsZeroFloatCompare(tick))return null;
        return new Animation(angle,direction,bound,step,0F,upper);
    }

    private static Integer randomInitialBound(MethodNode ctor,String owner,String field){
        List<AbstractInsnNode> code=real(ctor);Set<Integer> bounds=new LinkedHashSet<>();
        for(int i=1;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals("java/util/Random")&&call.name.equals("nextInt")&&call.desc.equals("(I)I")){
            Integer value=intConstant(code.get(i-1));if(value==null)continue;boolean writes=false;
            for(int j=i+1;j<Math.min(code.size(),i+8);j++)if(code.get(j) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(owner)&&put.name.equals(field)&&put.desc.equals("F")){writes=true;break;}
            if(writes)bounds.add(value);
        }
        return bounds.size()==1?bounds.iterator().next():null;
    }
    private static boolean randomBooleanInitializes(MethodNode ctor,String owner,String field){
        List<AbstractInsnNode> code=real(ctor);for(int i=0;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals("java/util/Random")&&call.name.equals("nextBoolean")&&call.desc.equals("()Z"))
            for(int j=i+1;j<Math.min(code.size(),i+6);j++)if(code.get(j) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(owner)&&put.name.equals(field)&&put.desc.equals("Z"))return true;return false;
    }

    private static boolean clientOnlyTick(MethodNode tick,String angle,String direction){
        if(tick==null)return false;AbstractInsnNode guardField=null;JumpInsnNode guardJump=null;
        for(AbstractInsnNode insn=tick.instructions){
            if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals("net/minecraft/world/World")&&field.desc.equals("Z")&&(field.name.equals("field_72995_K")||field.name.equals("isRemote")))guardField=insn;
            if(guardField!=null&&insn instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.IFEQ){guardJump=jump;break;}
        }
        if(guardJump==null)return false;Map<AbstractInsnNode,Integer> positions=new IdentityHashMap<>();int index=0;for(AbstractInsnNode insn=tick.instructions)positions.put(insn,index++);Integer target=positions.get(guardJump.label);if(target==null)return false;
        int writes=0;for(AbstractInsnNode insn:tick.instructions)if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(tickOwner(tick))&&(field.name.equals(angle)||field.name.equals(direction))){Integer pos=positions.get(insn);if(pos==null||pos>=target)return false;writes++;}
        if(writes<4)return false;
        AbstractInsnNode at=guardJump.label;while(at!=null&&at.getOpcode()<0)at=at.getNext();return at!=null&&at.getOpcode()==Opcodes.RETURN;
    }
    private static String tickOwner(MethodNode method){
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD)return field.owner;return "";
    }

    private static int[] yawMetadataTable(MethodNode method){
        if(method==null)return null;List<AbstractInsnNode> code=real(method);TableSwitchInsnNode table=null;int tableIndex=-1;
        boolean yaw=false,four=false,threeSixty=false,half=false,floor=false,mask=false;
        for(int i=0;i<code.size();i++){AbstractInsnNode insn=code.get(i);
            if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals("net/minecraft/entity/EntityLivingBase")&&field.desc.equals("F")&&(field.name.equals("field_70177_z")||field.name.equals("rotationYaw")))yaw=true;
            Float f=floatConstant(insn);if(f!=null&&Float.compare(f,4F)==0)four=true;if(f!=null&&Float.compare(f,360F)==0)threeSixty=true;
            if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Double d&&Double.compare(d,.5D)==0)half=true;
            if(insn instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals("net/minecraft/util/MathHelper")&&call.desc.equals("(D)I")&&(call.name.equals("func_76128_c")||call.name.equals("floor_double")))floor=true;
            if(insn.getOpcode()==Opcodes.IAND&&i>0&&Integer.valueOf(3).equals(intConstant(code.get(i-1))))mask=true;
            if(insn instanceof TableSwitchInsnNode sw&&sw.min==0&&sw.max==3){table=sw;tableIndex=i;}
        }
        if(!(yaw&&four&&threeSixty&&half&&floor&&mask)||table==null)return null;
        int targetVar=-1;for(int i=tableIndex+1;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals("net/minecraft/world/World")&&call.desc.equals("(IIIII)Z")&&(call.name.equals("func_72921_c")||call.name.equals("setBlockMetadataWithNotify"))){if(i<2||!Integer.valueOf(3).equals(intConstant(code.get(i-1)))||!(code.get(i-2) instanceof VarInsnNode load)||load.getOpcode()!=Opcodes.ILOAD)return null;targetVar=load.var;break;}
        if(targetVar<0)return null;Map<LabelNode,Integer> realIndex=new IdentityHashMap<>();for(int i=0;i<code.size();i++)if(code.get(i) instanceof LabelNode label)realIndex.put(label,i);
        int[] out=new int[4];Arrays.fill(out,Integer.MIN_VALUE);
        for(int q=0;q<4;q++){LabelNode label=table.labels.get(q);int start=labelIndex(method,label);if(start<0)continue;for(AbstractInsnNode insn=label.getNext();insn!=null;insn=insn.getNext()){if(insn instanceof LabelNode other&&other!=label)break;if(insn instanceof VarInsnNode store&&store.getOpcode()==Opcodes.ISTORE&&store.var==targetVar){AbstractInsnNode prev=previousReal(insn);Integer value=prev==null?null:intConstant(prev);if(value!=null)out[q]=value;break;}}}
        for(int value:out)if(value==Integer.MIN_VALUE||value<0||value>15)return null;return out;
    }
    private static int labelIndex(MethodNode method,LabelNode label){int i=0;for(AbstractInsnNode insn:method.instructions){if(insn==label)return i;i++;}return -1;}
    private static AbstractInsnNode previousReal(AbstractInsnNode node){for(AbstractInsnNode prev=node.getPrevious();prev!=null;prev=prev.getPrevious())if(prev.getOpcode()>=0)return prev;return null;}

    private static boolean hasOwnNbt(ClassNode tile){for(MethodNode method:tile.methods)if((method.name.equals("readFromNBT")||method.name.equals("func_145839_a")||method.name.equals("writeToNBT")||method.name.equals("func_145841_b"))&&method.desc.equals("(Lnet/minecraft/nbt/NBTTagCompound;)V"))return true;return false;}
    private static boolean defaultFullCube(Map<String,ClassNode> classes,String blockClass){
        Set<String> shapeNames=Set.of("setBlockBoundsBasedOnState","func_149719_a","getCollisionBoundingBoxFromPool","func_149668_a","getSelectedBoundingBoxFromPool","func_149633_g","addCollisionBoxesToList","func_149743_a");
        for(String owner=blockClass;owner!=null;){ClassNode node=classes.get(owner);if(node==null)break;for(MethodNode method:node.methods){if(shapeNames.contains(method.name))return false;for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&(call.name.equals("setBlockBounds")||call.name.equals("func_149676_a")))return false;}owner=node.superName;}return true;
    }
    private static int sourceLightLevel(Map<String,ClassNode> classes,String blockClass){for(String owner=blockClass;owner!=null;){ClassNode node=classes.get(owner);if(node==null)break;for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&(call.name.equals("setLightLevel")||call.name.equals("func_149715_a")))return -1;owner=node.superName;}return 0;}

    private static Skipped skip(LegacyRegistryAnalyzer.Registration r,String owner,String reason){return new Skipped(r.registryName(),owner,reason);}
    private static Map<String,String> registeredTiles(LegacyLifecycleAnalyzer.Analysis lifecycle){Map<String,String> result=new LinkedHashMap<>();for(var registration:lifecycle.of(LegacyLifecycleAnalyzer.Kind.TILE_ENTITY))if(registration.arguments().size()>=2&&registration.arguments().get(0) instanceof LegacyLifecycleAnalyzer.TypeValue type&&registration.arguments().get(1) instanceof LegacyLifecycleAnalyzer.TextValue text)result.put(type.internalName(),text.value());return result;}
    private static String uniqueCreatedType(MethodNode method){if(method==null)return null;Set<String> values=new LinkedHashSet<>();for(AbstractInsnNode insn:method.instructions)if(insn instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW)values.add(type.desc);return values.size()==1?values.iterator().next():null;}
    private static MethodNode effectiveMethod(Map<String,ClassNode> classes,String owner,Set<String> names,String desc){Set<String> seen=new HashSet<>();while(owner!=null&&seen.add(owner)){ClassNode node=classes.get(owner);if(node==null)return null;MethodNode method=ownMethod(node,names,desc);if(method!=null)return method;owner=node.superName;}return null;}
    private static MethodNode ownMethod(ClassNode node,Set<String> names,String desc){if(node==null)return null;for(MethodNode method:node.methods)if(names.contains(method.name)&&desc.equals(method.desc))return method;return null;}
    private static boolean inherits(Map<String,ClassNode> classes,String owner,String target){Set<String> seen=new HashSet<>();while(owner!=null&&seen.add(owner)){if(owner.equals(target))return true;ClassNode node=classes.get(owner);owner=node==null?null:node.superName;}return false;}
    private static Boolean returnedBoolean(MethodNode method){Integer value=returnedInt(method);return value==null||value<0||value>1?null:value==1;}
    private static Integer returnedInt(MethodNode method){if(method==null)return null;Integer result=null;List<AbstractInsnNode> code=real(method);for(int i=1;i<code.size();i++)if(code.get(i).getOpcode()==Opcodes.IRETURN){Integer value=intConstant(code.get(i-1));if(value==null||result!=null&&!result.equals(value))return null;result=value;}return result;}
    private static boolean containsZeroFloatCompare(MethodNode method){List<AbstractInsnNode> code=real(method);for(int i=1;i<code.size();i++)if((code.get(i).getOpcode()==Opcodes.FCMPL||code.get(i).getOpcode()==Opcodes.FCMPG)&&Float.valueOf(0F).equals(floatConstant(code.get(i-1))))return true;return false;}
    private static boolean hasOpcode(MethodNode method,int opcode){for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()==opcode)return true;return false;}
    private static Integer intConstant(AbstractInsnNode insn){return switch(insn.getOpcode()){case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer value?value:null;default->null;};}
    private static Float floatConstant(AbstractInsnNode insn){return switch(insn.getOpcode()){case Opcodes.FCONST_0->0F;case Opcodes.FCONST_1->1F;case Opcodes.FCONST_2->2F;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Float value?value:null;default->null;};}
    private static List<AbstractInsnNode> real(MethodNode method){List<AbstractInsnNode> values=new ArrayList<>();if(method!=null)for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)values.add(insn);return values;}
    private static Map<String,ClassNode> loadClasses(Path jarPath)throws IOException{Map<String,ClassNode> classes=new LinkedHashMap<>();try(JarFile jar=new JarFile(jarPath.toFile(),false)){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException ignored){}}}return classes;}
}
