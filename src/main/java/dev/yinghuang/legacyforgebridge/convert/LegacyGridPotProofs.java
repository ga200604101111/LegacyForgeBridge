package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static dev.yinghuang.legacyforgebridge.convert.LegacyGridPotAsm.*;

/** Structural proof fragments for the admitted legacy 3x3 grid-pot family. */
final class LegacyGridPotProofs {
    static final String ITEM_STACK="net/minecraft/item/ItemStack";
    private static final String ITEM_BLOCK="net/minecraft/item/ItemBlock";
    private static final String FORGE_DIRECTION="net/minecraftforge/common/util/ForgeDirection";
    private static final String SLOT_DESC="(FFL"+FORGE_DIRECTION+";)I";
    private LegacyGridPotProofs() { }

    record TileShape(String enabledField,String itemField,MethodNode slotMapper,MethodNode enableCell,MethodNode removeCell,
                     MethodNode removeItem,MethodNode setItem,MethodNode itemGetter,MethodNode itemMetaGetter,
                     MethodNode enabledGetter,MethodNode dropAll,MethodNode emptyCheck) { }

    static TileShape tileShape(ClassNode tile){
        if(tile==null)return null; MethodNode ctor=ownMethod(tile,Set.of("<init>"),"()V");
        String enabled=fieldAssignedArray(ctor,tile.name,"[Z",Opcodes.NEWARRAY,9);
        String items=fieldAssignedArray(ctor,tile.name,"[L"+ITEM_STACK+";",Opcodes.ANEWARRAY,9);
        if(enabled==null||items==null||!Boolean.FALSE.equals(returnedBoolean(ownMethod(tile,Set.of("canUpdate"),"()Z"))))return null;
        MethodNode mapper=uniqueStaticMethod(tile,SLOT_DESC); if(!slotMapper(mapper))return null;
        MethodNode enable=findMethod(tile,"(I)V",m->hasArrayStore(m,tile.name,enabled,Opcodes.BASTORE,1)&&callsNamed(m,"func_70296_d","()V"));
        MethodNode removeItem=findMethod(tile,"(I)Lnet/minecraft/item/ItemStack;",m->hasArrayStore(m,tile.name,items,Opcodes.AASTORE,null)&&!hasArrayStore(m,tile.name,enabled,Opcodes.BASTORE,0));
        MethodNode removeCell=findMethod(tile,"(I)Lnet/minecraft/item/ItemStack;",m->hasArrayStore(m,tile.name,enabled,Opcodes.BASTORE,0));
        MethodNode setItem=findMethod(tile,"(ILnet/minecraft/item/Item;I)V",m->readsArrayField(m,tile.name,enabled,Opcodes.BALOAD)
                &&hasArrayStore(m,tile.name,items,Opcodes.AASTORE,null)&&canonicalSingleItemStackConstructor(m)
                &&callsNamed(m,"func_70296_d","()V"));
        MethodNode itemGetter=findMethod(tile,"(I)Lnet/minecraft/item/Item;",m->calls(m,ITEM_STACK,"func_77973_b","()Lnet/minecraft/item/Item;"));
        MethodNode itemMeta=findMethod(tile,"(I)I",m->calls(m,ITEM_STACK,"func_77960_j","()I"));
        MethodNode enabledGetter=findMethod(tile,"(I)Z",m->readsArrayField(m,tile.name,enabled,Opcodes.BALOAD));
        MethodNode dropAll=findMethod(tile,"(Lnet/minecraft/world/World;)V",m->readsArrayField(m,tile.name,enabled,Opcodes.BALOAD)
                &&readsArrayField(m,tile.name,items,Opcodes.AALOAD)&&hasInt(m,9)&&typed(m,Opcodes.NEW,ITEM_STACK));
        MethodNode empty=findMethod(tile,"()Z",m->readsArrayField(m,tile.name,enabled,Opcodes.BALOAD)&&hasInt(m,9)
                &&directBooleanReturn(m,false)&&directBooleanReturn(m,true));
        if(enable==null||removeItem==null||removeCell==null||setItem==null||itemGetter==null||itemMeta==null||enabledGetter==null||dropAll==null||empty==null)return null;
        return new TileShape(enabled,items,mapper,enable,removeCell,removeItem,setItem,itemGetter,itemMeta,enabledGetter,dropAll,empty);
    }

    private static boolean canonicalSingleItemStackConstructor(MethodNode method){
        if(method==null)return false;
        for(AbstractInsnNode insn:method.instructions){
            if(!(insn instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESPECIAL||!ITEM_STACK.equals(call.owner)
                    ||!"<init>".equals(call.name)||!"(Lnet/minecraft/item/Item;II)V".equals(call.desc))continue;
            AbstractInsnNode meta=previousReal(call);AbstractInsnNode count=previousReal(meta);
            if(meta instanceof VarInsnNode metaVar&&metaVar.getOpcode()==Opcodes.ILOAD
                    &&Integer.valueOf(1).equals(intConstant(count)))return true;
        }
        return false;
    }

    static boolean canonicalPersistence(ClassNode tile,TileShape s){
        MethodNode write=ownMethod(tile,Set.of("writeToNBT","func_145841_b"),"(Lnet/minecraft/nbt/NBTTagCompound;)V");
        MethodNode read=ownMethod(tile,Set.of("readFromNBT","func_145839_a"),"(Lnet/minecraft/nbt/NBTTagCompound;)V");
        return write!=null&&read!=null&&string(write,"slotsNBT")&&string(write,"matrix")&&string(write,"itemNBT")
                &&string(read,"slotsNBT")&&string(read,"matrix")&&string(read,"itemNBT")
                &&readsArrayField(write,tile.name,s.enabledField(),Opcodes.BALOAD)&&readsArrayField(write,tile.name,s.itemField(),Opcodes.AALOAD)
                &&hasArrayStore(read,tile.name,s.enabledField(),Opcodes.BASTORE,null)&&hasArrayStore(read,tile.name,s.itemField(),Opcodes.AASTORE,null)
                &&calls(write,ITEM_STACK,"func_77955_b","(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;")
                &&calls(read,ITEM_STACK,"func_77949_a","(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/item/ItemStack;")&&hasInt(write,9)&&hasInt(read,9);
    }

    static boolean canonicalPlacement(Map<String,ClassNode> classes,String itemBlockClass,String tileClass,TileShape s){
        MethodNode place=method(classes,itemBlockClass,Set.of("placeBlockAt"),
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;IIIIFFFI)Z");
        return place!=null&&calls(place,ITEM_BLOCK,"placeBlockAt","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;IIIIFFFI)Z")
                &&typed(place,Opcodes.CHECKCAST,tileClass)&&calls(place,tileClass,s.enableCell().name,s.enableCell().desc)
                &&calls(place,tileClass,s.slotMapper().name,s.slotMapper().desc)&&calls(place,FORGE_DIRECTION,"getOpposite","()Lnet/minecraftforge/common/util/ForgeDirection;");
    }

    static boolean canonicalEmptyHandAndSelfItemActivation(MethodNode a,String tileClass,TileShape s){
        return a!=null&&field(a,"net/minecraft/world/World","field_72995_K","Z")
                &&calls(a,"net/minecraft/entity/player/EntityPlayer","func_71045_bC","()Lnet/minecraft/item/ItemStack;")
                &&countCalls(a,tileClass,s.slotMapper().name,s.slotMapper().desc)>=2&&calls(a,tileClass,s.itemGetter().name,s.itemGetter().desc)
                &&calls(a,tileClass,s.removeItem().name,s.removeItem().desc)&&calls(a,tileClass,s.enableCell().name,s.enableCell().desc)
                &&calls(a,"net/minecraft/item/Item","func_150898_a","(Lnet/minecraft/block/Block;)Lnet/minecraft/item/Item;")&&directBooleanReturn(a,true);
    }

    static boolean canonicalContentInsertionPredicate(MethodNode a,String tileClass,TileShape s){
        if(a==null||s==null||!calls(a,"net/minecraft/block/Block","func_149634_a","(Lnet/minecraft/item/Item;)Lnet/minecraft/block/Block;"))return false;
        if(!canonicalHeldItemSetterCall(a,tileClass,s.setItem())||!calls(a,tileClass,s.removeItem().name,s.removeItem().desc))return false;
        Set<Integer> constants=new HashSet<>();int symbolic=0,comparisons=0;
        for(AbstractInsnNode insn:a.instructions){
            if(!(insn instanceof MethodInsnNode call)||!"net/minecraft/block/Block".equals(call.owner)
                    ||!"func_149645_b".equals(call.name)||!"()I".equals(call.desc))continue;
            AbstractInsnNode identity=nextReal(call);AbstractInsnNode branch=nextReal(identity);
            if(!(branch instanceof JumpInsnNode jump)||!integerComparison(jump.getOpcode()))continue;
            Integer value=intConstant(identity);
            if(value!=null){constants.add(value);comparisons++;continue;}
            if(identity instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC&&"I".equals(field.desc)){symbolic++;comparisons++;}
        }
        return comparisons>=4&&constants.contains(1)&&constants.contains(13)&&constants.contains(40)&&symbolic>=1;
    }

    /** Canonical owner#field keys for symbolic render identities compared directly by the admitted predicate. */
    static Set<String> symbolicContentInsertionRenderFields(MethodNode method){
        if(method==null)return Set.of();
        LinkedHashSet<String> result=new LinkedHashSet<>();
        for(AbstractInsnNode insn:method.instructions){
            if(!(insn instanceof MethodInsnNode call)||!"net/minecraft/block/Block".equals(call.owner)
                    ||!"func_149645_b".equals(call.name)||!"()I".equals(call.desc))continue;
            AbstractInsnNode identity=nextReal(call);AbstractInsnNode branch=nextReal(identity);
            if(identity instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC&&"I".equals(field.desc)
                    &&branch instanceof JumpInsnNode jump&&integerComparison(jump.getOpcode()))result.add(field.owner+"#"+field.name);
        }
        return Set.copyOf(result);
    }

    private static boolean canonicalHeldItemSetterCall(MethodNode method,String tileClass,MethodNode setter){
        if(method==null||setter==null)return false;
        for(AbstractInsnNode insn:method.instructions){
            if(!(insn instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKEVIRTUAL||!tileClass.equals(call.owner)
                    ||!setter.name.equals(call.name)||!setter.desc.equals(call.desc))continue;
            AbstractInsnNode damageCall=previousReal(call),damageReceiver=previousReal(damageCall),itemCall=previousReal(damageReceiver),itemReceiver=previousReal(itemCall);
            if(!(damageCall instanceof MethodInsnNode damage)||!ITEM_STACK.equals(damage.owner)||!"func_77960_j".equals(damage.name)||!"()I".equals(damage.desc))continue;
            if(!(itemCall instanceof MethodInsnNode item)||!ITEM_STACK.equals(item.owner)||!"func_77973_b".equals(item.name)||!"()Lnet/minecraft/item/Item;".equals(item.desc))continue;
            if(damageReceiver instanceof VarInsnNode damageVar&&damageVar.getOpcode()==Opcodes.ALOAD
                    &&itemReceiver instanceof VarInsnNode itemVar&&itemVar.getOpcode()==Opcodes.ALOAD&&damageVar.var==itemVar.var)return true;
        }
        return false;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode insn){for(AbstractInsnNode p=insn==null?null:insn.getPrevious();p!=null;p=p.getPrevious())if(p.getOpcode()>=0)return p;return null;}
    private static boolean integerComparison(int opcode){return opcode==Opcodes.IF_ICMPEQ||opcode==Opcodes.IF_ICMPNE||opcode==Opcodes.IF_ICMPLT||opcode==Opcodes.IF_ICMPGE||opcode==Opcodes.IF_ICMPGT||opcode==Opcodes.IF_ICMPLE;}

    static boolean canonicalDynamicCellShape(Map<String,ClassNode> classes,String blockClass,String tileClass,TileShape s,Float baseHeight,Float cellHeight){
        if(baseHeight==null||cellHeight==null)return false;
        MethodNode collision=method(classes,blockClass,Set.of("addCollisionBoxesToList","func_149743_a"),"(Lnet/minecraft/world/World;IIILnet/minecraft/util/AxisAlignedBB;Ljava/util/List;Lnet/minecraft/entity/Entity;)V");
        MethodNode ray=method(classes,blockClass,Set.of("collisionRayTrace","func_149731_a"),"(Lnet/minecraft/world/World;IIILnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;)Lnet/minecraft/util/MovingObjectPosition;");
        MethodNode base=method(classes,blockClass,Set.of("getCollisionBoundingBoxFromPool","func_149633_g"),"(Lnet/minecraft/world/World;III)Lnet/minecraft/util/AxisAlignedBB;");
        return dynamicCellMethod(collision,tileClass,s.enabledGetter(),"func_149743_a","(Lnet/minecraft/world/World;IIILnet/minecraft/util/AxisAlignedBB;Ljava/util/List;Lnet/minecraft/entity/Entity;)V",baseHeight,cellHeight)
                &&dynamicCellMethod(ray,tileClass,s.enabledGetter(),"func_149731_a","(Lnet/minecraft/world/World;IIILnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;)Lnet/minecraft/util/MovingObjectPosition;",baseHeight,cellHeight)
                &&base!=null&&containsFloat(base,baseHeight)&&callsNamed(base,"func_149676_a","(FFFFFF)V");
    }
    private static boolean dynamicCellMethod(MethodNode m,String tileClass,MethodNode getter,String superName,String superDesc,float base,float cell){return m!=null&&calls(m,"net/minecraft/world/World","func_147438_o","(III)Lnet/minecraft/tileentity/TileEntity;")&&typed(m,Opcodes.CHECKCAST,tileClass)&&calls(m,tileClass,getter.name,getter.desc)&&callsNamed(m,superName,superDesc)&&callsNamed(m,"func_149676_a","(FFFFFF)V")&&containsFloat(m,base)&&containsFloat(m,cell)&&field(m,tileClass,"SQ","I")&&opcode(m,Opcodes.FDIV)&&opcode(m,Opcodes.FMUL)&&opcode(m,Opcodes.IMUL)&&opcode(m,Opcodes.IADD);}
    static boolean constantFalse(MethodNode m){return Boolean.FALSE.equals(returnedBoolean(m));}
    private static boolean slotMapper(MethodNode m){return m!=null&&field(m,FORGE_DIRECTION,"offsetX","I")&&field(m,FORGE_DIRECTION,"offsetZ","I")&&hasInt(m,9)&&hasInt(m,8)&&opcode(m,Opcodes.FMUL)&&opcode(m,Opcodes.F2I)&&opcode(m,Opcodes.IMUL)&&opcode(m,Opcodes.IADD);}
}
