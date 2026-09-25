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
 * Bounded analyzer for the first generic three-slot single-input processor family.
 * It proves inventory topology, source processing duration, GUI identity and the source-owned
 * recipe lookup API without executing legacy classes or recognizing mod/class names.
 */
public final class LegacySingleInputProcessorAnalyzer {
    private static final String BLOCK_CONTAINER="net/minecraft/block/BlockContainer";
    private static final String TILE_ENTITY="net/minecraft/tileentity/TileEntity";
    private static final String SIDED_INVENTORY="net/minecraft/inventory/ISidedInventory";
    private static final String ITEM_STACK="net/minecraft/item/ItemStack";

    public record Rule(String registryName,String legacyNamespace,String sourceBlockClass,
                       String sourceTileClass,String legacyTileId,int slots,int stackLimit,
                       int inputSlot,List<Integer> outputSlots,List<Integer> topSlots,
                       List<Integer> bottomSlots,List<Integer> sideSlots,int processTicks,
                       double interactionDistanceSq,int guiId,String recipeManagerOwner,
                       String recipeLookupName,String recipeLookupDescriptor,boolean comparator,
                       boolean dropContents,boolean legacyEnergyApiPresent) {
        public Rule {
            outputSlots=List.copyOf(outputSlots);topSlots=List.copyOf(topSlots);
            bottomSlots=List.copyOf(bottomSlots);sideSlots=List.copyOf(sideSlots);
        }
    }
    public record Skipped(String registryName,String sourceBlockClass,String reason) { }
    public record Analysis(List<Rule> rules,List<Skipped> skipped,List<String> diagnostics) {
        public Analysis {rules=List.copyOf(rules);skipped=List.copyOf(skipped);diagnostics=List.copyOf(diagnostics);}
    }
    private record Lookup(String owner,String name,String descriptor) { }
    private record Sided(List<Integer> top,List<Integer> bottom,List<Integer> sides) { }

    public Analysis analyze(Path jarPath)throws IOException {
        Map<String,ClassNode> classes=loadClasses(jarPath);
        var registry=new LegacyRegistryAnalyzer().analyze(jarPath);
        var lifecycle=new LegacyLifecycleAnalyzer().analyze(jarPath);
        Map<String,String> registeredTiles=registeredTiles(lifecycle);
        List<Rule> rules=new ArrayList<>();List<Skipped> skipped=new ArrayList<>();List<String> diagnostics=new ArrayList<>();
        diagnostics.addAll(registry.diagnostics());diagnostics.addAll(lifecycle.diagnostics());
        for(var block:registry.blocks()){
            String owner=block.implementationClass();if(owner==null||!inherits(classes,owner,BLOCK_CONTAINER))continue;
            MethodNode create=effectiveMethod(classes,owner,Set.of("createNewTileEntity","func_149915_a"),"(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;");
            String tileClass=create==null?null:uniqueNewType(create);
            if(tileClass==null||!registeredTiles.containsKey(tileClass)||!inherits(classes,tileClass,TILE_ENTITY)||!implementsType(classes,tileClass,SIDED_INVENTORY))continue;
            Integer slots=inventoryArraySize(classes.get(tileClass));if(!Integer.valueOf(3).equals(slots))continue;
            Integer stackLimit=returnedInt(effectiveMethod(classes,tileClass,Set.of("getInventoryStackLimit","func_70297_j_"),"()I"));
            if(stackLimit==null||stackLimit<1||stackLimit>64){skipped.add(new Skipped(block.registryName(),owner,"Three-slot processor has no proven stack limit."));continue;}

            MethodNode valid=effectiveMethod(classes,tileClass,Set.of("isItemValidForSlot","func_94041_b"),"(ILnet/minecraft/item/ItemStack;)Z");
            var validity=valid==null?null:new LegacyPureIntFunctionCompiler().compile(valid,1);
            if(validity==null||!validity.supported()||validity.program().evaluate(0)!=1||validity.program().evaluate(1)!=0||validity.program().evaluate(2)!=0){
                skipped.add(new Skipped(block.registryName(),owner,"Three-slot processor must prove slot 0 input and slots 1/2 output-only."));continue;}
            Sided sided=sidedTopology(classes.get(tileClass));
            if(sided==null){skipped.add(new Skipped(block.registryName(),owner,"ISidedInventory topology is not the admitted top/sides input, bottom outputs layout."));continue;}
            MethodNode insert=effectiveMethod(classes,tileClass,Set.of("canInsertItem","func_102007_a"),"(ILnet/minecraft/item/ItemStack;I)Z");
            if(!calls(insert,tileClass,valid.name,valid.desc)){
                skipped.add(new Skipped(block.registryName(),owner,"Sided insertion does not delegate to the proven slot-validity rule."));continue;}

            Double distance=usableDistanceSq(effectiveMethod(classes,tileClass,Set.of("isUseableByPlayer","func_70300_a","canInteractWith"),"(Lnet/minecraft/entity/player/EntityPlayer;)Z"));
            if(distance==null||distance<=0||distance>4096){skipped.add(new Skipped(block.registryName(),owner,"Processor player validity is not source-proven."));continue;}
            MethodNode tick=effectiveMethod(classes,tileClass,Set.of("updateEntity","func_145845_h"),"()V");
            Integer processTicks=processingThreshold(tick);Lookup lookup=recipeLookup(classes,tileClass);
            if(processTicks==null||processTicks<=0||lookup==null){skipped.add(new Skipped(block.registryName(),owner,"Processor tick/recipe lookup contract is not uniquely source-proven."));continue;}
            if(!canonicalProcessorNbt(classes,tileClass)){skipped.add(new Skipped(block.registryName(),owner,"Processor inventory/progress NBT is not the admitted canonical shape."));continue;}

            Integer guiId=openGuiId(effectiveMethod(classes,owner,Set.of("onBlockActivated","func_149727_a"),"(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z"));
            if(guiId==null||!guiHandlerTargetsTile(classes,guiId,tileClass)){skipped.add(new Skipped(block.registryName(),owner,"Processor GUI id does not resolve to a source Container for the same TileEntity."));continue;}
            if(!canonicalInventoryDrop(effectiveMethod(classes,owner,Set.of("breakBlock","func_149749_a"),"(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;I)V"))){skipped.add(new Skipped(block.registryName(),owner,"Processor block removal does not prove content drops."));continue;}
            boolean comparator=Boolean.TRUE.equals(returnedBoolean(effectiveMethod(classes,owner,Set.of("hasComparatorInputOverride","func_149740_M"),"()Z")))
                    &&calls(effectiveMethod(classes,owner,Set.of("getComparatorInputOverride","func_149736_g"),"(Lnet/minecraft/world/World;IIII)I"),"net/minecraft/inventory/Container","func_94526_b","(Lnet/minecraft/inventory/IInventory;)I");
            if(!comparator){skipped.add(new Skipped(block.registryName(),owner,"Processor comparator behavior is not vanilla IInventory semantics."));continue;}

            rules.add(new Rule(block.registryName(),block.legacyNamespace(),owner,tileClass,registeredTiles.get(tileClass),3,stackLimit,0,List.of(1,2),
                    sided.top(),sided.bottom(),sided.sides(),processTicks,distance,guiId,lookup.owner(),lookup.name(),lookup.descriptor(),true,true,
                    hierarchyContains(classes,tileClass,"cofh/api/energy/IEnergyHandler")));
        }
        return new Analysis(rules,skipped,List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static Lookup recipeLookup(Map<String,ClassNode> classes,String tileClass){
        Map<Lookup,Integer> counts=new LinkedHashMap<>();
        for(ClassNode owner=classes.get(tileClass);owner!=null;owner=classes.get(owner.superName))for(MethodNode method:owner.methods)for(AbstractInsnNode insn:method.instructions){
            if(!(insn instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESTATIC||!classes.containsKey(call.owner))continue;
            Type[] args=Type.getArgumentTypes(call.desc);Type result=Type.getReturnType(call.desc);
            if(args.length!=1||!args[0].getDescriptor().equals("L"+ITEM_STACK+";")||result.getSort()!=Type.OBJECT||!classes.containsKey(result.getInternalName()))continue;
            counts.merge(new Lookup(call.owner,call.name,call.desc),1,Integer::sum);
        }
        List<Lookup> values=counts.entrySet().stream().filter(e->e.getValue()>=2).map(Map.Entry::getKey).toList();return values.size()==1?values.getFirst():null;
    }
    private static Integer processingThreshold(MethodNode tick){if(tick==null)return null;Set<Integer> values=new LinkedHashSet<>();List<AbstractInsnNode> real=real(tick);for(int i=1;i<real.size();i++){
        if(!(real.get(i) instanceof JumpInsnNode jump)||jump.getOpcode()<Opcodes.IF_ICMPEQ||jump.getOpcode()>Opcodes.IF_ICMPLE)continue;Integer constant=intConstant(real.get(i-1));if(constant!=null&&constant>=20)values.add(constant);
    }return values.size()==1?values.iterator().next():null;}

    private static boolean canonicalProcessorNbt(Map<String,ClassNode> classes,String tileClass){
        MethodNode read=effectiveMethod(classes,tileClass,Set.of("readFromNBT","func_145839_a"),"(Lnet/minecraft/nbt/NBTTagCompound;)V");
        MethodNode write=effectiveMethod(classes,tileClass,Set.of("writeToNBT","func_145841_b"),"(Lnet/minecraft/nbt/NBTTagCompound;)V");
        return read!=null&&write!=null&&hasString(read,"Items")&&hasString(read,"Slot")
                &&calls(read,"net/minecraft/nbt/NBTTagCompound","func_150295_c","(Ljava/lang/String;I)Lnet/minecraft/nbt/NBTTagList;")
                &&calls(read,"net/minecraft/item/ItemStack","func_77949_a","(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/item/ItemStack;")
                &&hasString(write,"Items")&&hasString(write,"Slot")
                &&calls(write,"net/minecraft/item/ItemStack","func_77955_b","(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;")
                &&calls(write,"net/minecraft/nbt/NBTTagList","func_74742_a","(Lnet/minecraft/nbt/NBTBase;)V")
                &&calls(write,"net/minecraft/nbt/NBTTagCompound","func_74782_a","(Ljava/lang/String;Lnet/minecraft/nbt/NBTBase;)V");
    }

    private static Sided sidedTopology(ClassNode tile){
        if(tile==null)return null;Map<String,List<Integer>> arrays=staticIntArrays(tile);
        MethodNode sides=instanceMethod(tile,Set.of("getAccessibleSlotsFromSide","func_94128_d"),"(I)[I");if(sides==null)return null;
        Set<String> returned=new LinkedHashSet<>();for(AbstractInsnNode insn:sides.instructions)if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC&&field.owner.equals(tile.name)&&field.desc.equals("[I"))returned.add(field.name);
        if(returned.size()!=3||!arrays.keySet().containsAll(returned))return null;
        List<List<Integer>> values=returned.stream().map(arrays::get).toList();
        long inputs=values.stream().filter(v->v.equals(List.of(0))).count();List<Integer> bottom=values.stream().filter(v->v.equals(List.of(2,1))).findFirst().orElse(null);
        if(inputs!=2||bottom==null)return null;
        // The source method order proves side 0 is bottom, side 1 top, all other sides the third field.
        List<AbstractInsnNode> real=real(sides);if(real.size()<8)return null;
        String bottomField=null,topField=null,sideField=null;
        for(int i=0;i<real.size();i++)if(real.get(i) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC&&returned.contains(field.name)){
            if(bottomField==null)bottomField=field.name;else if(topField==null)topField=field.name;else sideField=field.name;
        }
        if(bottomField==null||topField==null||sideField==null)return null;
        List<Integer> top=arrays.get(topField),side=arrays.get(sideField),bot=arrays.get(bottomField);
        return top.equals(List.of(0))&&side.equals(List.of(0))&&bot.equals(List.of(2,1))?new Sided(top,bot,side):null;
    }
    private static Map<String,List<Integer>> staticIntArrays(ClassNode owner){
        MethodNode clinit=anyMethod(owner,Set.of("<clinit>"),"()V");if(clinit==null)return Map.of();ArrayDeque<Object> stack=new ArrayDeque<>();Map<String,List<Integer>> output=new LinkedHashMap<>();
        for(AbstractInsnNode insn:real(clinit)){Integer constant=intConstant(insn);if(constant!=null){stack.push(constant);continue;}switch(insn.getOpcode()){
            case Opcodes.NEWARRAY->{if(!(insn instanceof IntInsnNode value)||value.operand!=Opcodes.T_INT||stack.isEmpty())return Map.of();int size=(Integer)stack.pop();if(size<0||size>64)return Map.of();stack.push(new int[size]);}
            case Opcodes.DUP->{if(stack.isEmpty())return Map.of();stack.push(stack.peek());}
            case Opcodes.IASTORE->{if(stack.size()<3)return Map.of();int value=(Integer)stack.pop(),index=(Integer)stack.pop();Object array=stack.pop();if(!(array instanceof int[] values)||index<0||index>=values.length)return Map.of();values[index]=value;}
            case Opcodes.PUTSTATIC->{if(!(insn instanceof FieldInsnNode field)||!field.owner.equals(owner.name)||!field.desc.equals("[I")||stack.isEmpty())return Map.of();Object array=stack.pop();if(!(array instanceof int[] values))return Map.of();output.put(field.name,Arrays.stream(values).boxed().toList());}
            case Opcodes.RETURN->{}default->{return Map.of();}}
        }return output;
    }

    private static Integer inventoryArraySize(ClassNode tile){if(tile==null)return null;MethodNode constructor=instanceMethod(tile,Set.of("<init>"),"()V");if(constructor==null)return null;Set<Integer> sizes=new LinkedHashSet<>();List<AbstractInsnNode> real=real(constructor);for(int i=1;i<real.size();i++)if(real.get(i) instanceof TypeInsnNode allocation&&allocation.getOpcode()==Opcodes.ANEWARRAY&&allocation.desc.equals(ITEM_STACK)){Integer size=intConstant(real.get(i-1));if(size!=null)sizes.add(size);}return sizes.size()==1?sizes.iterator().next():null;}
    private static Integer openGuiId(MethodNode method){if(method==null)return null;List<AbstractInsnNode> real=real(method);Set<Integer> ids=new LinkedHashSet<>();for(int i=0;i<real.size();i++)if(real.get(i) instanceof MethodInsnNode call&&call.owner.equals("net/minecraft/entity/player/EntityPlayer")&&call.name.equals("openGui")&&call.desc.equals("(Ljava/lang/Object;ILnet/minecraft/world/World;III)V")){for(int back=Math.max(0,i-8);back<i;back++){Integer value=intConstant(real.get(back));if(value!=null&&value>=0&&value<=255)ids.add(value);}}return ids.size()==1?ids.iterator().next():null;}

    private static boolean guiHandlerTargetsTile(Map<String,ClassNode> classes,int guiId,String tileClass){
        for(ClassNode owner:classes.values()){
            if(!owner.interfaces.contains("cpw/mods/fml/common/network/IGuiHandler"))continue;
            MethodNode method=instanceMethod(owner,Set.of("getServerGuiElement"),"(ILnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;III)Ljava/lang/Object;");
            if(method==null)continue;LabelNode start=switchCase(method,guiId);if(start==null)continue;
            Set<LabelNode> boundaries=switchLabels(method);boolean tile=false,container=false;
            for(AbstractInsnNode insn=start.getNext();insn!=null;insn=insn.getNext()){
                if(insn instanceof LabelNode label&&boundaries.contains(label))break;
                if(insn instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.CHECKCAST&&type.desc.equals(tileClass))tile=true;
                if(insn instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW){ClassNode candidate=classes.get(type.desc);if(candidate!=null&&hierarchyContains(classes,candidate.name,"net/minecraft/inventory/Container"))container=true;}
                if(insn.getOpcode()==Opcodes.ARETURN)return tile&&container;
            }
        }return false;
    }
    private static LabelNode switchCase(MethodNode method,int value){for(AbstractInsnNode insn:method.instructions){if(insn instanceof TableSwitchInsnNode table&&value>=table.min&&value<=table.max)return table.labels.get(value-table.min);if(insn instanceof LookupSwitchInsnNode lookup){int index=lookup.keys.indexOf(value);if(index>=0)return lookup.labels.get(index);}}return null;}
    private static Set<LabelNode> switchLabels(MethodNode method){Set<LabelNode> result=Collections.newSetFromMap(new IdentityHashMap<>());for(AbstractInsnNode insn:method.instructions){if(insn instanceof TableSwitchInsnNode table){result.add(table.dflt);result.addAll(table.labels);}else if(insn instanceof LookupSwitchInsnNode lookup){result.add(lookup.dflt);result.addAll(lookup.labels);}}return result;}

    private static boolean canonicalInventoryDrop(MethodNode method){return method!=null&&calls(method,"net/minecraft/world/World","func_147438_o","(III)Lnet/minecraft/tileentity/TileEntity;")&&hasType(method,Opcodes.NEW,"net/minecraft/entity/item/EntityItem")&&calls(method,"net/minecraft/world/World","func_72838_d","(Lnet/minecraft/entity/Entity;)Z");}
    private static Double usableDistanceSq(MethodNode method){if(method==null||!calls(method,"net/minecraft/world/World","func_147438_o","(III)Lnet/minecraft/tileentity/TileEntity;")||!calls(method,"net/minecraft/entity/player/EntityPlayer","func_70092_e","(DDD)D"))return null;boolean identity=false;Double threshold=null;for(AbstractInsnNode insn:method.instructions){if(insn.getOpcode()==Opcodes.IF_ACMPEQ||insn.getOpcode()==Opcodes.IF_ACMPNE)identity=true;if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Double value&&value>1)threshold=value;}return identity?threshold:null;}
    private static Map<String,String> registeredTiles(LegacyLifecycleAnalyzer.Analysis lifecycle){Map<String,String> result=new LinkedHashMap<>();for(var registration:lifecycle.of(LegacyLifecycleAnalyzer.Kind.TILE_ENTITY))if(registration.arguments().size()>=2&&registration.arguments().get(0) instanceof LegacyLifecycleAnalyzer.TypeValue type&&registration.arguments().get(1) instanceof LegacyLifecycleAnalyzer.TextValue id)result.put(type.internalName(),id.value());return result;}
    private static String uniqueNewType(MethodNode method){String type=null;for(AbstractInsnNode insn:method.instructions)if(insn instanceof TypeInsnNode value&&value.getOpcode()==Opcodes.NEW){if(type!=null&&!type.equals(value.desc))return null;type=value.desc;}return type;}
    private static boolean inherits(Map<String,ClassNode> classes,String owner,String target){Set<String> visited=new HashSet<>();while(owner!=null&&visited.add(owner)){if(owner.equals(target))return true;ClassNode node=classes.get(owner);owner=node==null?null:node.superName;}return false;}
    private static boolean implementsType(Map<String,ClassNode> classes,String owner,String target){Set<String> visited=new HashSet<>();ArrayDeque<String> queue=new ArrayDeque<>();queue.add(owner);while(!queue.isEmpty()){String next=queue.removeFirst();if(!visited.add(next))continue;if(next.equals(target))return true;ClassNode node=classes.get(next);if(node==null)continue;if(node.superName!=null)queue.add(node.superName);queue.addAll(node.interfaces);}return false;}
    private static boolean hierarchyContains(Map<String,ClassNode> classes,String owner,String target){return inherits(classes,owner,target)||implementsType(classes,owner,target);}
    private static MethodNode effectiveMethod(Map<String,ClassNode> classes,String owner,Set<String> names,String desc){Set<String> visited=new HashSet<>();while(owner!=null&&visited.add(owner)){ClassNode node=classes.get(owner);if(node==null)return null;MethodNode method=instanceMethod(node,names,desc);if(method!=null)return method;owner=node.superName;}return null;}
    private static MethodNode instanceMethod(ClassNode owner,Set<String> names,String desc){for(MethodNode method:owner.methods)if((method.access&Opcodes.ACC_STATIC)==0&&names.contains(method.name)&&desc.equals(method.desc))return method;return null;}
    private static MethodNode anyMethod(ClassNode owner,Set<String> names,String desc){for(MethodNode method:owner.methods)if(names.contains(method.name)&&desc.equals(method.desc))return method;return null;}
    private static Integer returnedInt(MethodNode method){if(method==null)return null;Integer result=null;List<AbstractInsnNode> real=real(method);for(int i=1;i<real.size();i++)if(real.get(i).getOpcode()==Opcodes.IRETURN){Integer value=intConstant(real.get(i-1));if(value==null||result!=null&&!result.equals(value))return null;result=value;}return result;}
    private static Boolean returnedBoolean(MethodNode method){Integer value=returnedInt(method);return value==null||value<0||value>1?null:value==1;}
    private static Integer intConstant(AbstractInsnNode insn){return switch(insn.getOpcode()){case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer value?value:null;default->null;};}
    private static boolean calls(MethodNode method,String owner,String name,String desc){if(method==null)return false;for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(desc))return true;return false;}
    private static boolean hasType(MethodNode method,int opcode,String type){for(AbstractInsnNode insn:method.instructions)if(insn instanceof TypeInsnNode value&&value.getOpcode()==opcode&&value.desc.equals(type))return true;return false;}
    private static boolean hasString(MethodNode method,String text){for(AbstractInsnNode insn:method.instructions)if(insn instanceof LdcInsnNode value&&text.equals(value.cst))return true;return false;}
    private static List<AbstractInsnNode> real(MethodNode method){List<AbstractInsnNode> result=new ArrayList<>();for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)result.add(insn);return result;}
    private static Map<String,ClassNode> loadClasses(Path jarPath)throws IOException{Map<String,ClassNode> classes=new LinkedHashMap<>();try(JarFile jar=new JarFile(jarPath.toFile(),false)){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException ignored){}}}return classes;}
}
