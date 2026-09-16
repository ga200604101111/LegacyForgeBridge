package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static dev.yinghuang.legacyforgebridge.convert.LegacySeatBedAsm.*;

/** Proof-only detector for a two-part legacy bed that falls back to a transient seat after failed sleep. */
public final class LegacySeatBedAnalyzer {
    private static final String BED="net/minecraft/block/BlockBed", ITEM_BED="net/minecraft/item/ItemBed";
    private static final String TILE="net/minecraft/tileentity/TileEntity", ENTITY="net/minecraft/entity/Entity";
    private static final String PLAYER="net/minecraft/entity/player/EntityPlayer", WORLD="net/minecraft/world/World";
    private static final String WORLD_INFO="net/minecraft/world/storage/WorldInfo", ITEM="net/minecraft/item/Item", STACK="net/minecraft/item/ItemStack";

    public record Rule(String registryName,String legacyNamespace,String sourceBlockClass,String sourcePlacementItemClass,
                       String placementItemRegistryName,String sourceTileClass,String legacyTileId,String sourceSeatEntityClass,
                       String sourceSeatRuntimeClass,String legacySeatEntityName,float blockHeight,boolean twoPartPlacementProven,
                       boolean footOnlyTileProven,boolean sleepFallbackToSeatProven,boolean transientOccupancyProven,
                       boolean seatLifecycleProven,boolean timeAccelerationSourceProven,boolean specialPresentationRequired){
        public boolean coreSourceProofComplete(){return twoPartPlacementProven&&footOnlyTileProven&&sleepFallbackToSeatProven&&transientOccupancyProven&&seatLifecycleProven;}
    }
    public record Skipped(String registryName,String sourceBlockClass,String reason){}
    public record Analysis(List<Rule> rules,List<Skipped> skipped,List<String> diagnostics){public Analysis{rules=List.copyOf(rules);skipped=List.copyOf(skipped);diagnostics=List.copyOf(diagnostics);}}

    public Analysis analyze(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=loadClasses(jarPath);
        var registry=new LegacyRegistryAnalyzer().analyze(jarPath);var lifecycle=new LegacyLifecycleAnalyzer().analyze(jarPath);
        var bindings=bindingMap(registry);Map<String,String> tiles=registered(lifecycle,LegacyLifecycleAnalyzer.Kind.TILE_ENTITY),entities=registered(lifecycle,LegacyLifecycleAnalyzer.Kind.ENTITY);
        Map<String,LegacyRegistryAnalyzer.Registration> items=new LinkedHashMap<>();for(var i:registry.items())if(i.registryName()!=null)items.put(i.registryName(),i);
        List<Rule> rules=new ArrayList<>();List<Skipped> skipped=new ArrayList<>();LinkedHashSet<String> diagnostics=new LinkedHashSet<>();diagnostics.addAll(registry.diagnostics());diagnostics.addAll(lifecycle.diagnostics());

        for(var reg:registry.blocks()){
            String block=reg.implementationClass();if(block==null||!inherits(classes,block,BED))continue;ClassNode blockNode=classes.get(block);if(blockNode==null)continue;
            MethodNode create=method(classes,block,Set.of("createNewTileEntity","func_149915_a"),"(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;");
            String tile=uniqueNewSubtype(create,classes,TILE);if(tile==null||!tiles.containsKey(tile)){skipped.add(skip(reg,block,"bed-style block TileEntity is not uniquely source-proven and lifecycle-registered"));continue;}
            ClassNode tileNode=classes.get(tile);if(tileNode==null){skipped.add(skip(reg,block,"bed-style source TileEntity class is unavailable"));continue;}
            MethodNode mount=mountMethod(classes,tileNode);String seatCreated=uniqueNewSubtype(mount,classes,ENTITY),seatRuntime=directSourceSubclassOf(classes,seatCreated,ENTITY);
            if(mount==null||seatCreated==null||seatRuntime==null||!entities.containsKey(seatRuntime)){skipped.add(skip(reg,block,"seat fallback entity is not uniquely source-proven and lifecycle-registered"));continue;}
            String occupancy=uniqueBooleanFieldUsedBy(mount,tileNode);if(occupancy==null||!writesBooleanField(mount,tile,occupancy,true)){skipped.add(skip(reg,block,"transient seat occupancy flag is not source-proven"));continue;}
            boolean tileProof=footOnlyTile(create,tile)&&constantBoolean(method(classes,tile,Set.of("canUpdate","func_145842_c"),"()Z"),false)&&mountSeatProof(classes,mount,tile,occupancy,seatCreated,seatRuntime)&&chairDeadReset(blockNode,tile,occupancy);
            if(!tileProof){skipped.add(skip(reg,block,"foot-only TileEntity occupancy/seat mounting lifecycle is not the admitted source shape"));continue;}

            MethodNode dropped=method(classes,block,Set.of("getItemDropped","func_149650_a"),"(ILjava/util/Random;I)Lnet/minecraft/item/Item;");
            var itemBinding=uniqueBinding(dropped,bindings,LegacyRegistryAnalyzer.Kind.ITEM);var itemReg=itemBinding==null?null:items.get(itemBinding.registryName());
            if(itemBinding==null||itemReg==null||itemReg.implementationClass()==null||!inherits(classes,itemReg.implementationClass(),ITEM_BED)||!bedDropProof(classes,dropped)){skipped.add(skip(reg,block,"foot-half drop does not uniquely resolve to a registered source ItemBed item"));continue;}
            String item=itemReg.implementationClass();MethodNode use=method(classes,item,Set.of("onItemUse","func_77648_a"),"(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;IIIIFFF)Z");
            var blockBinding=uniqueBinding(use,bindings,LegacyRegistryAnalyzer.Kind.BLOCK);if(blockBinding==null||!reg.registryName().equals(blockBinding.registryName())||!twoPartPlacement(classes,use)){skipped.add(skip(reg,block,"source ItemBed placement does not prove the admitted two-air/two-support two-part placement"));continue;}
            MethodNode activation=method(classes,block,Set.of("onBlockActivated","func_149727_a"),"(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z");
            if(!sleepFallback(classes,activation,tile,mount.name,mount.desc)){skipped.add(skip(reg,block,"bed activation does not prove server-side sleep followed by NOT_POSSIBLE_NOW seat fallback"));continue;}
            if(!seatLifecycle(classes,seatRuntime)){skipped.add(skip(reg,block,"registered seat entity lifecycle is not the admitted rider/block-bound transient anchor"));continue;}

            MethodNode bounds=boundsMethod(classes,blockNode,ownMethod(blockNode,Set.of("<init>"),"()V"));
            boolean geometry=bounds!=null&&setBoundsCall(classes,bounds)&&containsFloat(bounds,.25F)&&containsFloat(bounds,1F)&&containsFloat(bounds,0F);
            boolean special=Integer.valueOf(-1).equals(constantIntReturn(method(classes,block,Set.of("getRenderType","func_149645_b"),"()I")));
            boolean isBed=constantBoolean(method(classes,block,Set.of("isBed"),"(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraft/entity/EntityLivingBase;)Z"),true);
            if(!geometry||!special||!isBed){skipped.add(skip(reg,block,"bed bounds/render/bed-identity contract is not source-proven"));continue;}
            rules.add(new Rule(reg.registryName(),reg.legacyNamespace(),block,item,itemBinding.registryName(),tile,tiles.get(tile),seatCreated,seatRuntime,entities.get(seatRuntime),.25F,true,true,true,true,true,timeAcceleration(classes,seatCreated),true));
        }
        return new Analysis(rules,skipped,List.copyOf(diagnostics));
    }

    private static MethodNode mountMethod(Map<String,ClassNode> classes,ClassNode tile){MethodNode out=null;String d="(Lnet/minecraft/world/World;IIILnet/minecraft/entity/Entity;)V";for(MethodNode m:tile.methods)if((m.access&Opcodes.ACC_STATIC)==0&&d.equals(m.desc)&&uniqueNewSubtype(m,classes,ENTITY)!=null){if(out!=null)return null;out=m;}return out;}
    private static boolean footOnlyTile(MethodNode m,String tile){return m!=null&&intMaskBranch(m,12,Opcodes.IFNE)&&newType(m,tile)&&containsOpcode(m,Opcodes.ACONST_NULL)&&countOpcode(m,Opcodes.ARETURN)>=2;}
    private static boolean newType(MethodNode m,String type){if(m!=null)for(AbstractInsnNode i:m.instructions)if(i instanceof TypeInsnNode t&&t.getOpcode()==Opcodes.NEW&&type.equals(t.desc))return true;return false;}
    private static int countOpcode(MethodNode m,int op){int n=0;for(var i:real(m))if(i.getOpcode()==op)n++;return n;}
    private static boolean mountSeatProof(Map<String,ClassNode> c,MethodNode m,String tile,String occ,String created,String runtime){return m!=null&&fieldBranch(m,Opcodes.GETFIELD,tile,"Z",Set.of(occ),Opcodes.IFNE,Opcodes.IFEQ)&&writesBooleanField(m,tile,occ,true)&&newType(m,created)&&callsHierarchyName(m,c,ENTITY,Set.of("setPosition","func_70107_b"),"(DDD)V")&&callsName(m,WORLD,Set.of("spawnEntityInWorld","func_72838_d"),"(Lnet/minecraft/entity/Entity;)Z")&&callsHierarchyName(m,c,ENTITY,Set.of("mountEntity","func_70078_a"),"(Lnet/minecraft/entity/Entity;)V")&&callsName(m,runtime,Set.of("setChairBlock"),"(III)V")&&(containsFloat(m,.5F)||containsDouble(m,.5D))&&(containsFloat(m,.25F)||containsDouble(m,.25D));}
    private static boolean chairDeadReset(ClassNode b,String tile,String occ){String d="(Lnet/minecraft/world/World;III)V";for(MethodNode m:b.methods)if((m.access&Opcodes.ACC_STATIC)==0&&d.equals(m.desc)&&calls(m,WORLD,"(III)Lnet/minecraft/tileentity/TileEntity;")&&checkcasts(m,tile)&&writesBooleanField(m,tile,occ,false))return true;return false;}
    private static boolean bedDropProof(Map<String,ClassNode> c,MethodNode m){return m!=null&&callsHierarchyName(m,c,BED,Set.of("isBlockHeadOfBed","func_149975_b"),"(I)Z")&&callsName(m,ITEM,Set.of("getItemById","func_150899_d","func_150898_a"),"(I)Lnet/minecraft/item/Item;")&&containsInt(m,0);}
    private static boolean twoPartPlacement(Map<String,ClassNode> c,MethodNode m){return m!=null&&fieldBranch(m,Opcodes.GETFIELD,WORLD,"Z",Set.of("isRemote","field_72995_K"),Opcodes.IFNE,Opcodes.IFEQ)&&intLocalCompareBranch(m,7,1,Opcodes.IF_ICMPEQ,Opcodes.IF_ICMPNE)&&hasHierarchyField(m,Opcodes.GETFIELD,c,PLAYER,"F",Set.of("rotationYaw","field_70177_z"))&&calls(m,"net/minecraft/util/MathHelper","(D)I")&&containsFloat(m,4F)&&containsFloat(m,360F)&&containsDouble(m,.5D)&&containsOpcode(m,Opcodes.IAND)&&containsInt(m,3)&&countCalls(m,PLAYER,"(IIIILnet/minecraft/item/ItemStack;)Z")==2&&countCalls(m,WORLD,"(III)Z")>=2&&solidTopCalls(m)==2&&countCalls(m,WORLD,"(IIILnet/minecraft/block/Block;II)Z")==2&&containsInt(m,8)&&hasField(m,Opcodes.PUTFIELD,STACK,"I",null);}
    private static int solidTopCalls(MethodNode m){int n=0;if(m!=null)for(AbstractInsnNode i:m.instructions)if(i instanceof MethodInsnNode x&&WORLD.equals(x.owner)&&x.getOpcode()==Opcodes.INVOKESTATIC&&x.desc.endsWith("III)Z"))n++;return n;}
    private static boolean sleepFallback(Map<String,ClassNode> c,MethodNode m,String tile,String mount,String mountDesc){String a="(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z";return m!=null&&fieldBranch(m,Opcodes.GETFIELD,WORLD,"Z",Set.of("isRemote","field_72995_K"),Opcodes.IFNE,Opcodes.IFEQ)&&calls(m,WORLD,"(III)I")&&callsHierarchyName(m,c,BED,Set.of("isBlockHeadOfBed","func_149975_b"),"(I)Z")&&callsHierarchyName(m,c,BED,Set.of("getDirection","func_149895_l"),"(I)I")&&calls(m,WORLD,"(III)Lnet/minecraft/block/Block;")&&callsName(m,PLAYER,Set.of("sleepInBedAt","func_71018_a"),"(III)Lnet/minecraft/entity/player/EntityPlayer$EnumStatus;")&&fieldBranch(m,Opcodes.GETSTATIC,"net/minecraft/entity/player/EntityPlayer$EnumStatus","Lnet/minecraft/entity/player/EntityPlayer$EnumStatus;",Set.of("NOT_POSSIBLE_NOW"),Opcodes.IF_ACMPEQ,Opcodes.IF_ACMPNE)&&calls(m,WORLD,"(III)Lnet/minecraft/tileentity/TileEntity;")&&checkcasts(m,tile)&&callsName(m,tile,Set.of(mount),mountDesc)&&callsName(m,BED,Set.of("onBlockActivated","func_149727_a"),a);}
    private static boolean seatLifecycle(Map<String,ClassNode> c,String seatClass){ClassNode s=c.get(seatClass);if(s==null)return false;MethodNode ctor=ownMethod(s,Set.of("<init>"),"(Lnet/minecraft/world/World;)V"),u=ownMethod(s,Set.of("onUpdate","func_70071_h_"),"()V"),dead=ownMethod(s,Set.of("setDead","func_70106_y"),"()V"),mounted=ownMethod(s,Set.of("getMountedYOffset","func_70042_X"),"()D");boolean noClip=hasHierarchyField(ctor,Opcodes.PUTFIELD,c,ENTITY,"Z",Set.of("noClip","field_70145_X"));boolean update=u!=null&&fieldBranch(u,Opcodes.GETFIELD,WORLD,"Z",Set.of("isRemote","field_72995_K"),Opcodes.IFNE,Opcodes.IFEQ)&&hasHierarchyField(u,Opcodes.GETFIELD,c,ENTITY,"Lnet/minecraft/entity/Entity;",Set.of("riddenByEntity","field_70153_n"))&&calls(u,WORLD,"(III)Z")&&calls(u,WORLD,"(III)Lnet/minecraft/block/Block;")&&interfaceCallback(u)&&callsName(u,seatClass,Set.of("setDead","func_70106_y"),"()V")&&callsName(u,ENTITY,Set.of("onUpdate","func_70071_h_"),"()V");boolean d=dead!=null&&hasHierarchyField(dead,Opcodes.GETFIELD,c,ENTITY,"Lnet/minecraft/entity/Entity;",Set.of("riddenByEntity","field_70153_n"))&&callsHierarchyName(dead,c,ENTITY,Set.of("mountEntity","func_70078_a"),"(Lnet/minecraft/entity/Entity;)V")&&callsName(dead,ENTITY,Set.of("setDead","func_70106_y"),"()V");List<AbstractInsnNode> mc=real(mounted);boolean zero=mc.size()==2&&mc.getFirst().getOpcode()==Opcodes.DCONST_0&&mc.getLast().getOpcode()==Opcodes.DRETURN;int nbt=0;for(MethodNode x:s.methods)if("(Lnet/minecraft/nbt/NBTTagCompound;)V".equals(x.desc)&&emptyVoidMethod(x))nbt++;return noClip&&update&&d&&zero&&emptyVoidMethod(ownMethod(s,Set.of("entityInit","func_70088_a"),"()V"))&&nbt>=2;}
    private static boolean interfaceCallback(MethodNode m){if(m!=null)for(AbstractInsnNode i:m.instructions)if(i instanceof MethodInsnNode x&&x.getOpcode()==Opcodes.INVOKEINTERFACE&&"(Lnet/minecraft/world/World;III)V".equals(x.desc))return true;return false;}
    private static boolean timeAcceleration(Map<String,ClassNode> c,String created){MethodNode m=ownMethod(c.get(created),Set.of("updateDummy"),"()V");return m!=null&&sourceBooleanStaticFieldBranch(m,c,Opcodes.IFEQ,Opcodes.IFNE)&&hasHierarchyField(m,Opcodes.GETFIELD,c,ENTITY,"Lnet/minecraft/entity/Entity;",Set.of("riddenByEntity","field_70153_n"))&&containsInt(m,100)&&(containsInt(m,1000)||containsLong(m,1000L))&&calls(m,WORLD,"()Lnet/minecraft/world/storage/WorldInfo;")&&calls(m,WORLD_INFO,"()J")&&calls(m,WORLD_INFO,"(J)V")&&countCallsByDescriptor(m,WORLD_INFO,"()Z")>=2&&countCallsByDescriptor(m,WORLD_INFO,"()I")>=2&&countCallsByDescriptor(m,WORLD_INFO,"(I)V")>=2&&calls(m,PLAYER,"(Lnet/minecraft/util/IChatComponent;)V");}
    private static boolean setBoundsCall(Map<String,ClassNode> c,MethodNode m){Set<String> n=Set.of("setBlockBounds","func_149676_a");return callsName(m,"net/minecraft/block/Block",n,"(FFFFFF)V")||callsHierarchyName(m,c,BED,n,"(FFFFFF)V");}
    private static MethodNode boundsMethod(Map<String,ClassNode> c,ClassNode b,MethodNode ctor){if(ctor==null)return null;if(setBoundsCall(c,ctor))return ctor;Set<MethodNode>x=new LinkedHashSet<>();for(AbstractInsnNode i:ctor.instructions)if(i instanceof MethodInsnNode call&&call.getOpcode()!=Opcodes.INVOKESTATIC&&"()V".equals(call.desc)&&b.name.equals(call.owner)){MethodNode h=ownMethod(b,Set.of(call.name),"()V");if(h!=null&&setBoundsCall(c,h))x.add(h);}return x.size()==1?x.iterator().next():null;}
    private static Map<String,String> registered(LegacyLifecycleAnalyzer.Analysis a,LegacyLifecycleAnalyzer.Kind k){Map<String,String>r=new LinkedHashMap<>();for(var x:a.of(k))if(x.arguments().size()>=2&&x.arguments().get(0) instanceof LegacyLifecycleAnalyzer.TypeValue t&&x.arguments().get(1) instanceof LegacyLifecycleAnalyzer.TextValue n)r.put(t.internalName(),n.value());return r;}
    private static Skipped skip(LegacyRegistryAnalyzer.Registration r,String owner,String reason){return new Skipped(r.registryName(),owner,reason);}
}
