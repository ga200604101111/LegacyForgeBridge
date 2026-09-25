package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

/**
 * Bounded, non-executing interpretation of legacy icon registration and metadata selectors.
 * Source classes are never defined. Results are admitted only when concrete strings reach
 * IIconRegister and the selected getter returns that exact icon token. Unsupported control flow,
 * ambiguous allocations, external state and custom world renderers remain explicit exclusions.
 */
public final class LegacyIconTableAnalyzer implements Opcodes {
    public record Variant(int metadata, List<String> faceIcons, List<Double> bounds, List<Double> inventoryBounds, int renderType, List<Integer> tints) {
        public Variant { faceIcons=List.copyOf(faceIcons); bounds=List.copyOf(bounds); inventoryBounds=List.copyOf(inventoryBounds); tints=List.copyOf(tints); }
        public Variant(int metadata,List<String> faceIcons,List<Double> bounds,List<Double> inventoryBounds,int renderType){this(metadata,faceIcons,bounds,inventoryBounds,renderType,Collections.nCopies(faceIcons.size(),0xFFFFFF));}
    }
    public record Result(String registryName, boolean block, List<Variant> variants, String limitation) {
        public Result { variants=List.copyOf(variants); }
    }
    private record Icon(String id) { }
    private record Symbol(String owner, String name) { }
    private enum Unknown { VALUE }
    private enum Coordinate { X, Y, Z }
    // Geometry probes keep coordinates tainted: offsets may be added, but coordinates cannot
    // silently become constants or be used to choose an arbitrary position-dependent branch.
    private record Offset(Coordinate axis, int offset) { }
    private record ProbeWorld(Obj self, int metadata) { }
    private record Neighbour(int dx, int dy, int dz) { }
    private boolean geometryMode;
    private boolean geometryBoundsWrite;
    private final Map<String,LegacyRegistryAnalyzer.Registration> geometryFields = new HashMap<>();
    private final Map<String,Obj> geometryObjects = new HashMap<>();
    private final Set<String> geometryConstructing = new HashSet<>();
    private final Set<Obj> iconsReady = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Obj> iconsInitializing = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Exact registry field identities temporarily bound during dynamic item-presentation probes. */
    private final Map<String,Obj> provenRegistryFields = new HashMap<>();

    private static final Object U=Unknown.VALUE;
    private static final class Obj {
        final String type; final Map<String,Object> fields=new HashMap<>();
        Obj(String type) { this.type=type; }
    }
    private record Method(ClassNode owner, MethodNode node, Frame<SourceValue>[] frames) { }
    private final Map<String,ClassNode> classes=new TreeMap<>();
    private final Map<MethodNode,Method> methods=new IdentityHashMap<>();
    private final Map<String,Object> statics=new HashMap<>();
    private final Set<String> initialized=new HashSet<>();
    private final Map<AbstractInsnNode,Object> values=new IdentityHashMap<>();
    private int budget;
    private boolean readOnly;
    private Obj creativeOutput;
    private int symbolicRenderId=65536;
    private final Set<String> failedStatics=new HashSet<>();
    private static final class Unproven extends RuntimeException {
        Unproven(String message) { super(message,null,false,false); }
    }

    public List<Result> analyze(Path jarPath, List<LegacyRegistryAnalyzer.Registration> registrations) throws IOException {
        load(jarPath);
        Map<String,LegacySimpleBlockRendererAnalyzer.Mode> simpleRenderModes=new HashMap<>();
        for(var rule:new LegacySimpleBlockRendererAnalyzer().analyze(jarPath).rules())simpleRenderModes.put(rule.registryName(),rule.mode());
        List<Result> out=new ArrayList<>();
        for(var r:registrations) {
            boolean block=r.kind()==LegacyRegistryAnalyzer.Kind.BLOCK;
            List<Variant> table=new ArrayList<>(); String limitation="";
            try {
                budget=200_000; values.clear(); readOnly=false;
                Obj obj=allocation(r);
                String register="(Lnet/minecraft/client/renderer/texture/IIconRegister;)V";
                Method reg=find(obj.type,block?List.of("registerBlockIcons","func_149651_a"):List.of("registerIcons","func_94581_a"),register);
                if(reg!=null) run(reg,obj,List.of(new Symbol("lfb","icon-register")),0);
                else {
                    Object name=obj.fields.get("$texture");
                    if(!(name instanceof String s)) throw fail("no source-proven texture setter or icon registration");
                    obj.fields.put(block?"blockIcon":"itemIcon",new Icon(s));
                    obj.fields.put(block?"field_149761_L":"field_77791_bV",new Icon(s));
                }
                Method getter=find(obj.type,block?List.of("getIcon","func_149691_a"):List.of("getIconFromDamage","func_77617_a"),block?"(II)Lnet/minecraft/util/IIcon;":"(I)Lnet/minecraft/util/IIcon;");
                int render=0;boolean multipass=false;
                if(block) {
                    Method rt=find(obj.type,List.of("getRenderType","func_149645_b"),"()I");
                    render=rt==null?baseRenderType(obj.type):num(run(rt,obj,List.of(),0)).intValue();
                    if(render!=0 && render!=1 && render!=6 && !simpleRenderModes.containsKey(r.registryName())) throw fail("custom world/inventory renderer requires geometric conversion; renderType="+render);
                    // BlockContainer alone is not evidence of custom geometry: many 1.7 blocks use
                    // a TileEntity only for gameplay/state while still rendering as an ordinary
                    // renderType-0 block. Exclude only when the source actually binds a TESR.
                    if(subclass(obj.type,"net/minecraft/block/BlockContainer")&&hasCustomTileRenderer(obj.type))
                        throw fail("BlockEntity renderer not represented by an icon table");
                } else {
                    Method multi=find(obj.type,List.of("requiresMultipleRenderPasses","func_77623_v"),"()Z");
                    multipass=multi!=null && num(run(multi,obj,List.of(),0)).intValue()!=0;
                    if(find(obj.type,List.of("getIcon"),"(Lnet/minecraft/item/ItemStack;I)Lnet/minecraft/util/IIcon;")!=null)
                        throw fail("stack-dependent getIcon override is not a metadata-only selector");
                }
                int max=block?16:256;
                for(int meta=0;meta<max;meta++) {
                    budget=50_000;
                    try {
                        List<String> icons=new ArrayList<>();List<Integer> tints=new ArrayList<>();
                        Method passCount=find(obj.type,List.of("getRenderPasses"),"(I)I");
                        int count=block?6:multipass?(passCount==null?2:num(run(passCount,obj,List.of(meta),0)).intValue()):1;
                        if(count<1||count>5 && !block)throw fail("unsupported number of item layers");
                        Method passIcon=find(obj.type,List.of("getIconFromDamageForRenderPass","func_77618_c"),"(II)Lnet/minecraft/util/IIcon;");
                        Method worldColor=block?find(obj.type,List.of("colorMultiplier","func_149720_d"),"(Lnet/minecraft/world/IBlockAccess;III)I"):null;
                        Method worldIcon=block?find(obj.type,List.of("getIcon","func_149673_e"),"(Lnet/minecraft/world/IBlockAccess;IIII)Lnet/minecraft/util/IIcon;"):null;
                        Method color=find(obj.type,block?List.of("getRenderColor","func_149741_i"):List.of("getColorFromItemStack","func_82790_a"),block?"(I)I":"(Lnet/minecraft/item/ItemStack;I)I");
                        Obj stack=new Obj("net/minecraft/item/ItemStack");stack.fields.put("$metadata",meta);stack.fields.put("$item",obj);
                        for(int side=0;side<count;side++) {
                            readOnly=true;
                            Object value=!block&&multipass&&passIcon!=null?run(passIcon,obj,List.of(meta,side),0):getter==null?
                                    obj.fields.getOrDefault(block?"blockIcon":"itemIcon",obj.fields.get(block?"field_149761_L":"field_77791_bV")):
                                    run(getter,obj,block?List.of(side,meta):List.of(meta),0);
                            if(!(value instanceof Icon icon)) throw fail("getter did not return a registered icon");
                            icons.add(icon.id());
                            int tint=color==null?0xFFFFFF:num(run(color,obj,block?List.of(meta):List.of(stack,side),0)).intValue()&0xFFFFFF;
                            if(block) {
                                Object world=new Symbol("lfb-world",String.valueOf(meta));
                                if(worldColor!=null && (num(run(worldColor,obj,List.of(world,Coordinate.X,Coordinate.Y,Coordinate.Z),0)).intValue()&0xFFFFFF)!=tint)
                                    throw fail("world and inventory tints differ");
                                if(worldIcon!=null && !value.equals(run(worldIcon,obj,List.of(world,Coordinate.X,Coordinate.Y,Coordinate.Z,side),0)))
                                    throw fail("world and inventory icons differ");
                            }
                            tints.add(tint);
                        }
                        readOnly=false;
                        List<Double> bounds=List.of(0d,0d,0d,1d,1d,1d),inventoryBounds=bounds;
                        if(block) {
                            Obj inv=copy(obj);
                            Method inventoryShape=find(obj.type,List.of("setBlockBoundsForItemRender","func_149683_g"),"()V");
                            if(inventoryShape!=null)run(inventoryShape,inv,List.of(),0);
                            if(inv.fields.get("$bounds") instanceof List<?> box){List<Double> coords=new ArrayList<>();for(Object n:box)coords.add(num(n).doubleValue());inventoryBounds=List.copyOf(coords);}
                            Obj state=copy(obj);
                            Method shape=find(obj.type,List.of("setBlockBoundsBasedOnState","func_149719_a"),"(Lnet/minecraft/world/IBlockAccess;III)V");
                            if(shape!=null) run(shape,state,List.of(new Symbol("lfb-world",String.valueOf(meta)),Coordinate.X,Coordinate.Y,Coordinate.Z),0);
                            Object b=state.fields.get("$bounds");
                            if(b instanceof List<?> list) {
                                List<Double> box=new ArrayList<>(); for(Object n:list) box.add(num(n).doubleValue());
                                bounds=List.copyOf(box);
                            }
                            if(bounds.size()!=6 || bounds.stream().anyMatch(d->!Double.isFinite(d)||d<0||d>1)
                                    || bounds.get(0)>bounds.get(3)||bounds.get(1)>bounds.get(4)||bounds.get(2)>bounds.get(5))
                                throw fail("unsupported block bounds");
                        }
                        if(inventoryBounds.size()!=6||inventoryBounds.stream().anyMatch(d->!Double.isFinite(d)||d<0||d>1))throw fail("unsupported inventory bounds");
                        int presentationRender=render;
                        LegacySimpleBlockRendererAnalyzer.Mode simpleMode=simpleRenderModes.get(r.registryName());
                        if(block&&render!=0&&render!=1&&render!=6&&simpleMode!=null)presentationRender=switch(simpleMode){case CROSS,HELD_ITEM_CROSS->1;case CROP->6;case META_ZERO_CROP_ELSE_STANDARD->meta==0?6:0;};
                        table.add(new Variant(meta,icons,bounds,inventoryBounds,presentationRender,tints));
                    } catch(RuntimeException unsupported) {
                        if(meta==0) throw fail("metadata 0: "+unsupported.getMessage());
                        limitation="some metadata values require unsupported state or lie outside a source icon table";
                    }
                }
                if(!block && limitation.isEmpty()) limitation="metadata-only icon surface 0..255; custom IItemRenderer geometry remains a separate gate";
            } catch(RuntimeException unsupported) {
                table.clear(); limitation=unsupported.getMessage();
            }
            out.add(new Result(r.registryName(),block,table,limitation));
        }
        return List.copyOf(out);
    }


    public record UseDurationStage(int minimumElapsedTicks,String icon) {
        public UseDurationStage {
            if(minimumElapsedTicks<1||icon==null||icon.isBlank())throw new IllegalArgumentException("Invalid use-duration stage");
        }
    }
    public record UseDurationResult(String registryName,String sourceClass,String baseIcon,List<UseDurationStage> stages,String limitation) {
        public UseDurationResult { stages=List.copyOf(stages); }
    }

    /**
     * Proves item icons selected only by "currently using this registered item" and elapsed use ticks.
     * This is intentionally narrower than arbitrary stack-dependent rendering: the interpreter binds
     * exact registry fields, supplies one synthetic ItemStack identity, and admits only simple integer
     * threshold control flow. No source class is defined or executed.
     */
    public List<UseDurationResult> analyzeUseDurationIcons(Path jarPath,LegacyRegistryAnalyzer.Analysis registry) throws IOException {
        load(jarPath);List<UseDurationResult> out=new ArrayList<>();
        for(var r:registry.registrations()){
            if(r.kind()!=LegacyRegistryAnalyzer.Kind.ITEM)continue;
            String limitation="";
            try{
                budget=300_000;values.clear();readOnly=false;provenRegistryFields.clear();
                Obj item=allocation(r);
                for(var binding:registry.fieldBindings())if(binding.kind()==LegacyRegistryAnalyzer.Kind.ITEM
                        &&Objects.equals(binding.registryName(),r.registryName())
                        &&Objects.equals(binding.implementationClass(),r.implementationClass()))
                    provenRegistryFields.put(binding.owner()+"."+binding.name(),item);
                Method registration=find(item.type,List.of("registerIcons","func_94581_a"),"(Lnet/minecraft/client/renderer/texture/IIconRegister;)V");
                if(registration!=null)run(registration,item,List.of(new Symbol("lfb","icon-register")),0);
                else if(item.fields.get("$texture") instanceof String texture){Icon icon=new Icon(texture);item.fields.put("itemIcon",icon);item.fields.put("field_77791_bV",icon);}
                Method dynamic=find(item.type,List.of("getIcon"),"(Lnet/minecraft/item/ItemStack;ILnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;I)Lnet/minecraft/util/IIcon;");
                if(dynamic==null)continue;
                if(hasComplexUseArithmetic(dynamic.node()))throw fail("use-duration selector contains non-threshold integer arithmetic");
                Obj stack=new Obj("net/minecraft/item/ItemStack");stack.fields.put("$metadata",0);stack.fields.put("$item",item);stack.fields.put("$maxUseDuration",1_000_000);
                Obj player=new Obj("net/minecraft/entity/player/EntityPlayer");
                readOnly=true;
                Object baseValue=run(dynamic,item,List.of(stack,0,player,null,1_000_000),0);
                if(!(baseValue instanceof Icon base))throw fail("non-using branch does not return one registered icon");
                TreeSet<Integer> probes=new TreeSet<>(List.of(0,1,999_999));
                for(int constant:integerConstants(dynamic.node()))if(constant>=0&&constant<999_999)
                    for(int delta=-2;delta<=2;delta++){long value=(long)constant+delta;if(value>=0&&value<1_000_000)probes.add((int)value);}
                String prior=base.id();int priorTick=0;List<UseDurationStage> stages=new ArrayList<>();
                for(int elapsed:probes){
                    Object value=run(dynamic,item,List.of(stack,0,player,stack,1_000_000-elapsed),0);
                    if(!(value instanceof Icon icon))throw fail("using branch does not return one registered icon");
                    if(!icon.id().equals(prior)){
                        if(elapsed!=priorTick+1)throw fail("icon transition is not pinned to a probed integer threshold");
                        if(elapsed==0)throw fail("using selector changes icon at zero elapsed ticks");
                        stages.add(new UseDurationStage(elapsed,icon.id()));prior=icon.id();
                        if(stages.size()>8)throw fail("too many use-duration icon stages");
                    }
                    priorTick=elapsed;
                }
                if(stages.isEmpty())continue;
                out.add(new UseDurationResult(r.registryName(),r.implementationClass(),base.id(),stages,""));
            }catch(RuntimeException excluded){limitation=excluded.getMessage();out.add(new UseDurationResult(r.registryName(),r.implementationClass(),null,List.of(),limitation));}
        }
        provenRegistryFields.clear();return List.copyOf(out);
    }
    private static boolean hasComplexUseArithmetic(MethodNode method){
        for(AbstractInsnNode insn:method.instructions)if(Set.of(IMUL,IDIV,IREM,ISHL,ISHR,IUSHR,IAND,IOR,IXOR).contains(insn.getOpcode()))return true;
        return false;
    }
    private static Set<Integer> integerConstants(MethodNode method){
        LinkedHashSet<Integer> values=new LinkedHashSet<>();for(AbstractInsnNode insn:method.instructions){
            Integer value=switch(insn.getOpcode()){case ICONST_M1->-1;case ICONST_0->0;case ICONST_1->1;case ICONST_2->2;case ICONST_3->3;case ICONST_4->4;case ICONST_5->5;case BIPUSH,SIPUSH->((IntInsnNode)insn).operand;case LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer v?v:null;default->null;};
            if(value!=null)values.add(value);
        }return values;
    }

    /** Geometry input data are not automatically evidence that an arbitrary custom renderer is a cube. */
    public record GeometryInput(String registryName,String sourceClass,String platform,List<Variant> variants,
                                Map<Integer,Integer> neighbourFaces,Map<Integer,Boolean> paneEdges,Map<Integer,String> collisions,
                                Integer originalRenderType,Boolean opaque,String limitation,Map<Integer,String> materialFallbacks) {
        public GeometryInput { variants=List.copyOf(variants);neighbourFaces=Map.copyOf(neighbourFaces);paneEdges=Map.copyOf(paneEdges);collisions=Map.copyOf(collisions);materialFallbacks=Map.copyOf(materialFallbacks); }
        public GeometryInput(String registryName,String sourceClass,String platform,List<Variant> variants,
                             Map<Integer,Integer> neighbourFaces,Map<Integer,Boolean> paneEdges,Map<Integer,String> collisions,
                             Integer originalRenderType,Boolean opaque,String limitation) {
            this(registryName,sourceClass,platform,variants,neighbourFaces,paneEdges,collisions,originalRenderType,opaque,limitation,Map.of());
        }
    }

    public List<GeometryInput> analyzeGeometryInputs(Path jarPath,LegacyRegistryAnalyzer.Analysis registry) throws IOException {
        load(jarPath); geometryMode=true;geometryBoundsWrite=false;
        for(var binding:registry.fieldBindings()) {
            var matches=registry.registrations().stream().filter(r->r.kind()==binding.kind()&&r.registryName().equals(binding.registryName())
                    &&Objects.equals(r.implementationClass(),binding.implementationClass())).toList();
            if(matches.size()==1)geometryFields.put(binding.owner()+"."+binding.name(),matches.getFirst());
        }
        List<GeometryInput> out=new ArrayList<>();
        for(var r:registry.registrations()) {
            if(r.kind()!=LegacyRegistryAnalyzer.Kind.BLOCK)continue;
            List<Variant> variants=new ArrayList<>();Map<Integer,Integer> neighbours=new LinkedHashMap<>();Map<Integer,Boolean> edges=new LinkedHashMap<>();Map<Integer,String> collisions=new LinkedHashMap<>();
            String limitation="";Integer originalRender=null;Boolean opaque=null;Map<Integer,String> materialFallbacks=new LinkedHashMap<>();
            try {
                budget=500_000;values.clear();readOnly=false;
                Obj obj=geometryObject(r);ensureGeometryIcons(obj,0);
                Method opacity=find(obj.type,List.of("isOpaqueCube","func_149662_c"),"()Z");
                if(opacity!=null) {readOnly=true;try{opaque=num(run(opacity,obj,List.of(),0)).intValue()!=0;}catch(RuntimeException unknown){opaque=null;}readOnly=false;}
                else opaque=!Set.of("net/minecraft/block/BlockStairs","net/minecraft/block/BlockPane","net/minecraft/block/BlockLeavesBase","net/minecraft/block/BlockCarpet","net/minecraft/block/BlockPressurePlate").contains(externalBase(obj.type));
                Method original=find(obj.type,List.of("getOriginalRenderType"),"()I");
                if(original!=null){readOnly=true;originalRender=num(run(original,obj,List.of(),0)).intValue();readOnly=false;}
                Method getter=find(obj.type,List.of("getIcon","func_149691_a"),"(II)Lnet/minecraft/util/IIcon;");
                Method worldGetter=find(obj.type,List.of("getIcon","func_149673_e"),"(Lnet/minecraft/world/IBlockAccess;IIII)Lnet/minecraft/util/IIcon;");
                for(int meta=0;meta<16;meta++)try {
                    budget=100_000;List<String> icons=new ArrayList<>();List<Integer> tints=new ArrayList<>();
                    Method color=find(obj.type,List.of("getRenderColor","func_149741_i"),"(I)I");
                    readOnly=true;
                    for(int face=0;face<6;face++) {
                        Object value=getter==null?invoke(obj.type,"func_149691_a","(II)Lnet/minecraft/util/IIcon;",obj,List.of(face,meta),INVOKEVIRTUAL,0):run(getter,obj,List.of(face,meta),0);
                        if(!(value instanceof Icon icon))throw fail("no proven inventory icon");icons.add(icon.id());
                        tints.add(color==null?0xFFFFFF:num(run(color,obj,List.of(meta),0)).intValue()&0xFFFFFF);
                    }
                    Obj state=copy(obj),inventory=copy(obj);
                    readOnly=true;geometryBoundsWrite=true;
                    try {
                        Method bounds=find(obj.type,List.of("setBlockBoundsBasedOnState","func_149719_a"),"(Lnet/minecraft/world/IBlockAccess;III)V");
                        if(bounds!=null)run(bounds,state,List.of(new ProbeWorld(obj,meta),Coordinate.X,Coordinate.Y,Coordinate.Z),0);
                        else inheritedGeometryBounds(state,meta,false);
                        Method inventoryShape=find(obj.type,List.of("setBlockBoundsForItemRender","func_149683_g"),"()V");
                        if(inventoryShape!=null)run(inventoryShape,inventory,List.of(),0);
                        else inheritedGeometryBounds(inventory,meta,true);
                    } finally {geometryBoundsWrite=false;}
                    List<Double> box=geometryBox(state),inv=geometryBox(inventory);
                    // This optional observation is admitted later only by a matching source-family proof.
                    String materialFallback=null;
                    if(worldGetter!=null) try {
                        readOnly=true;Integer direction=null;
                        boolean copied=true;
                        for(int face=0;face<6;face++){
                            Object v=run(worldGetter,obj,List.of(new ProbeWorld(obj,meta),Coordinate.X,Coordinate.Y,Coordinate.Z,face),0);
                            if(!(v instanceof Icon icon))throw fail("unresolved world icon");
                            if(!icon.id().startsWith("lfb-neighbour:")){
                                if(!icon.id().equals(icons.get(face)))throw fail("world icon differs from inventory without a neighbour projection");
                                copied=false;continue;
                            }
                            String[] parts=icon.id().substring(14).split(",");
                            int dx=Integer.parseInt(parts[0]),dy=Integer.parseInt(parts[1]),dz=Integer.parseInt(parts[2]);
                            int d=cardinal(dx,dy,dz);
                            if(d<0||Integer.parseInt(parts[3])!=face||(direction!=null&&direction!=d))throw fail("inconsistent neighbour face source");
                            direction=d;
                        }
                        if(!copied&&direction!=null)throw fail("mixed static and neighbour faces require a separate renderer");
                        if(copied&&direction!=null)neighbours.put(meta,direction);
                    } catch(Unproven unsupportedMaterial) {
                        // No source renderer is executed and no configuration value is invented.
                        // Only an independently admitted mimic family may publish this shape, with
                        // an explicit inventory-material fallback; ordinary renderers remain closed.
                        if(originalRender==null || (originalRender!=0 && originalRender!=10))throw unsupportedMaterial;
                        materialFallback=unsupportedMaterial.getMessage();
                        budget=100_000;
                    }
                    Method worldColor=find(obj.type,List.of("colorMultiplier","func_149720_d"),"(Lnet/minecraft/world/IBlockAccess;III)I");
                    if(worldColor!=null&&!neighbours.containsKey(meta)&&materialFallback==null) {
                        readOnly=true;int tint=num(run(worldColor,obj,List.of(new ProbeWorld(obj,meta),Coordinate.X,Coordinate.Y,Coordinate.Z),0)).intValue()&0xFFFFFF;
                        if(tints.stream().anyMatch(v->v!=tint))throw fail("world and inventory colors need a separate projection");
                    }
                    Method edge=find(obj.type,List.of("isSideRender"),"(Lnet/minecraft/world/IBlockAccess;III)Z");
                    if(edge!=null){readOnly=true;edges.put(meta,num(run(edge,obj,List.of(new ProbeWorld(obj,meta),Coordinate.X,Coordinate.Y,Coordinate.Z),0)).intValue()!=0);}
                    readOnly=true;
                    String collision=Set.of("net/minecraft/block/BlockCarpet","net/minecraft/block/BlockPressurePlate")
                            .contains(externalBase(obj.type))?"empty":"inherited";
                    Method collisionMethod=find(obj.type,List.of("addCollisionBoxesToList","func_149743_a"),"(Lnet/minecraft/world/World;IIILnet/minecraft/util/AxisAlignedBB;Ljava/util/List;Lnet/minecraft/entity/Entity;)V");
                    if(collisionMethod!=null)try{
                        Obj sample=copy(obj);sample.fields.put("$collisionCalls",0);
                        geometryBoundsWrite=true;
                        try{run(collisionMethod,sample,Arrays.asList(new ProbeWorld(obj,meta),Coordinate.X,Coordinate.Y,Coordinate.Z,U,U,U),0);}
                        finally{geometryBoundsWrite=false;}
                        int calls=num(sample.fields.get("$collisionCalls")).intValue();
                        collision=calls==0?"empty":calls==1?"inherited":"unsupported";
                    }catch(RuntimeException excluded){collision="unsupported";}
                    Method collisionBox=find(obj.type,List.of("getCollisionBoundingBoxFromPool","func_149668_a"),"(Lnet/minecraft/world/World;III)Lnet/minecraft/util/AxisAlignedBB;");
                    if(collisionBox!=null)try{
                        readOnly=true;Object boxResult=run(collisionBox,obj,List.of(new ProbeWorld(obj,meta),Coordinate.X,Coordinate.Y,Coordinate.Z),0);
                        collision=boxResult==null?"empty":"unsupported";
                    }catch(RuntimeException excluded){collision="unsupported";}
                    collisions.put(meta,collision);
                    variants.add(new Variant(meta,icons,box,inv,0,tints));
                    if(materialFallback!=null)materialFallbacks.put(meta,materialFallback);
                }catch(RuntimeException excluded){limitation="metadata "+meta+": "+excluded.getMessage();}
            }catch(RuntimeException excluded){limitation=excluded.getMessage();}
            out.add(new GeometryInput(r.registryName(),r.implementationClass(),externalBase(r.implementationClass()),variants,neighbours,edges,collisions,originalRender,opaque,limitation,materialFallbacks));
        }
        geometryMode=false;return List.copyOf(out);
    }


    private boolean hasCustomTileRenderer(String blockClass) {
        Method create=find(blockClass,List.of("createNewTileEntity","func_149915_a"),
                "(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;");
        if(create==null)return true;
        LinkedHashSet<String> tileTypes=new LinkedHashSet<>();
        for(AbstractInsnNode insn:create.node().instructions)
            if(insn instanceof TypeInsnNode type&&type.getOpcode()==NEW)tileTypes.add(type.desc);
        if(tileTypes.size()!=1)return true;
        String tile=tileTypes.getFirst();boolean sawRegistration=false;
        for(ClassNode owner:classes.values())for(MethodNode method:owner.methods)for(AbstractInsnNode insn:method.instructions){
            if(!(insn instanceof MethodInsnNode call)||call.getOpcode()!=INVOKESTATIC
                    ||!call.owner.equals("cpw/mods/fml/client/registry/ClientRegistry")
                    ||!call.name.equals("registerTileEntity")
                    ||!call.desc.equals("(Ljava/lang/Class;Ljava/lang/String;Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;)V"))continue;
            sawRegistration=true;
            LegacyDirectCallArguments.ClassStringNew args=LegacyDirectCallArguments.classStringNew(owner,method,call);
            if(args==null)return true;
            if(args.classInternalName().equals(tile))return true;
        }
        return false;
    }

    private static String vanillaNamedBlockIcon1710(String icon) {
        return switch(icon){
            case "piston_top_normal"->"minecraft:block/piston_top";
            case "piston_inner"->"minecraft:block/piston_inner";
            case "piston_bottom"->"minecraft:block/piston_bottom";
            case "piston_side"->"minecraft:block/piston_side";
            default->throw fail("unmapped 1.7 vanilla named block icon: "+icon);
        };
    }

    private Obj geometryObject(LegacyRegistryAnalyzer.Registration r) {
        String key=r.kind()+":"+r.registryName();Obj cached=geometryObjects.get(key);if(cached!=null)return cached;
        if(!geometryConstructing.add(key))throw fail("recursive registered geometry dependency");
        boolean oldReadOnly=readOnly;readOnly=false;
        try {Obj result=allocation(r);geometryObjects.put(key,result);return result;}
        finally {geometryConstructing.remove(key);readOnly=oldReadOnly;}
    }
    private void ensureGeometryIcons(Obj obj,int depth) {
        if(iconsReady.contains(obj)||iconsInitializing.contains(obj))return;
        iconsInitializing.add(obj);boolean oldReadOnly=readOnly;readOnly=false;
        try {
            Method registration=find(obj.type,List.of("registerBlockIcons","func_149651_a"),"(Lnet/minecraft/client/renderer/texture/IIconRegister;)V");
            if(registration!=null)run(registration,obj,List.of(new Symbol("lfb","icon-register")),depth+1);
            else if(obj.fields.get("$texture") instanceof String texture){obj.fields.put("blockIcon",new Icon(texture));obj.fields.put("field_149761_L",new Icon(texture));}
            else if(!obj.fields.containsKey("$stairModel")
                    && find(obj.type,List.of("getIcon","func_149691_a"),"(II)Lnet/minecraft/util/IIcon;")==null)
                throw fail("block icon initialization unavailable");
            // A source-owned getter may delegate all six faces to another proven registered block.
            // Do not invent a texture here: each returned face still has to be an actual Icon token.
            iconsReady.add(obj);
        }finally{readOnly=oldReadOnly;iconsInitializing.remove(obj);}
    }
    /** Exact inherited 1.7.10 presentation only; no pressure/redstone gameplay is synthesized. */
    private void inheritedGeometryBounds(Obj object,int metadata,boolean inventory) {
        String platform=externalBase(object.type);
        if(platform.equals("net/minecraft/block/BlockPressurePlate")) {
            if(find(object.type,List.of("func_150063_b"),"(I)V")!=null
                    ||find(object.type,List.of("func_150060_c"),"(I)I")!=null)
                throw fail("source overrides inherited pressure-plate shape/power metadata");
            object.fields.put("$bounds",inventory?List.of(0d,3d/8,0d,1d,5d/8,1d)
                    :List.of(1d/16,0d,1d/16,15d/16,metadata==1?1d/32:1d/16,15d/16));
        } else if(platform.equals("net/minecraft/block/BlockCarpet")) {
            if(find(object.type,List.of("func_150089_b"),"(I)V")!=null)
                throw fail("source overrides inherited carpet shape");
            object.fields.put("$bounds",List.of(0d,0d,0d,1d,1d/16,1d));
        }
    }
    private static List<Double> geometryBox(Obj object) {
        Object value=object.fields.get("$bounds");if(!(value instanceof List<?> list)||list.size()!=6)throw fail("no geometry bounds");
        List<Double> box=new ArrayList<>();for(Object coordinate:list)box.add(num(coordinate).doubleValue());
        for(double v:box)if(!Double.isFinite(v)||v<0||v>1)throw fail("geometry bounds outside one block");
        for(int axis=0;axis<3;axis++)if(box.get(axis)>=box.get(axis+3))throw fail("degenerate geometry bounds");
        return List.copyOf(box);
    }
    private static int[] probeOffset(List<Object> args) {
        if(args.size()!=3)throw fail("wrong geometry coordinate arity");int[] result=new int[3];
        for(int axis=0;axis<3;axis++) {
            Object arg=args.get(axis);Offset offset=arg instanceof Coordinate c?new Offset(c,0):arg instanceof Offset o?o:null;
            if(offset==null||offset.axis().ordinal()!=axis||Math.abs(offset.offset())>1)throw fail("non-local or unproven geometry coordinates");
            result[axis]=offset.offset();
        }
        return result;
    }
    private static int cardinal(int x,int y,int z){if(x==0&&y==-1&&z==0)return 0;if(x==0&&y==1&&z==0)return 1;if(x==0&&y==0&&z==-1)return 2;if(x==0&&y==0&&z==1)return 3;if(x==-1&&y==0&&z==0)return 4;if(x==1&&y==0&&z==0)return 5;return -1;}
    private boolean implementsType(String type,String wanted,Set<String> visited){if(type==null||!visited.add(type))return false;if(type.equals(wanted))return true;ClassNode c=classes.get(type);if(c==null)return false;if(implementsType(c.superName,wanted,visited))return true;for(String i:c.interfaces)if(implementsType(i,wanted,visited))return true;return false;}
    private static String vanillaBlockIcon(Symbol symbol,int side,int meta) {
        var entry=LegacyVanillaRegistry1710.resolve(symbol.owner(),symbol.name()).orElseThrow(()->fail("unmapped vanilla block identity"));
        String name=entry.registryName();
        if(name.equals("planks")){String[] wood={"oak","spruce","birch","jungle","acacia","dark_oak"};if(meta<0||meta>=wood.length)throw fail("invalid vanilla plank metadata");return "minecraft:block/"+wood[meta]+"_planks";}
        if(name.equals("log")||name.equals("log2")){
            String[] wood=name.equals("log")?new String[]{"oak","spruce","birch","jungle"}:new String[]{"acacia","dark_oak"};int variant=meta&3;if(variant>=wood.length)throw fail("invalid vanilla log metadata");int axis=meta&12;
            boolean end=axis==0&&side<2||axis==4&&(side==4||side==5)||axis==8&&(side==2||side==3);
            return "minecraft:block/"+wood[variant]+"_log"+(end?"_top":"");
        }
        if(name.equals("piston")){
            return "minecraft:block/"+(side==0?"piston_bottom":side==1?"piston_top":"piston_side");
        }
        if(Set.of("glass","stone","cobblestone","dirt","sand","gravel","bricks","obsidian").contains(name))return "minecraft:block/"+name;
        throw fail("vanilla block texture semantics not reconstructed: "+name);
    }

    private void load(Path jarPath) throws IOException {
        geometryMode=false; geometryFields.clear(); geometryObjects.clear(); geometryConstructing.clear(); iconsReady.clear(); iconsInitializing.clear(); provenRegistryFields.clear();
        classes.clear(); methods.clear(); statics.clear(); initialized.clear(); failedStatics.clear(); values.clear(); readOnly=false;creativeOutput=null;symbolicRenderId=65536;
        try (JarFile jar=new JarFile(jarPath.toFile())) {
            var entries=jar.entries();
            while(entries.hasMoreElements()) {
                var e=entries.nextElement();
                if(!e.getName().endsWith(".class")) continue;
                try(var in=jar.getInputStream(e)) {
                    ClassNode c=new ClassNode(ASM9);
                    new ClassReader(in.readAllBytes()).accept(c,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                    classes.put(c.name,c);
                }
            }
        }
    }

    public record NameResult(String registryName, boolean block, Map<Integer,String> keys, String limitation) {
        public NameResult { keys=Collections.unmodifiableMap(new LinkedHashMap<>(keys)); }
    }

    /**
     * Proves naming independently of rendering. A prototype has unknown source fields: this
     * permits parameter-only naming methods without pretending an unsupported constructor ran.
     * Its base name must be set by the actual registration helper, not inferred from an ID.
     */
    public List<NameResult> analyzeNames(Path jarPath, List<LegacyRegistryAnalyzer.Registration> registrations) throws IOException {
        load(jarPath); List<NameResult> result=new ArrayList<>();
        for(var r:registrations) {
            Map<Integer,String> keys=new LinkedHashMap<>();String limitation="";
            boolean block=r.kind()==LegacyRegistryAnalyzer.Kind.BLOCK;
            try {
                budget=200_000; readOnly=false; values.clear();
                Obj named=new Obj(r.implementationClass()==null ? (block?"net/minecraft/block/Block":"net/minecraft/item/Item"):r.implementationClass());
                Method helper=find(r.sourceOwner(),List.of(r.sourceMethod()),r.sourceDescriptor());
                if(helper==null)throw fail("registration naming helper unavailable");
                List<Object> args=new ArrayList<>();int textArgs=0,contentArgs=0;
                for(Type type:Type.getArgumentTypes(r.sourceDescriptor())) {
                    switch(type.getDescriptor()) {
                        case "Ljava/lang/String;" -> {args.add(r.registryName());textArgs++;}
                        case "Lnet/minecraft/block/Block;","Lnet/minecraft/item/Item;" -> {args.add(named);contentArgs++;}
                        case "Ljava/lang/Class;" -> args.add(Type.getObjectType(r.itemBlockClass()==null?"net/minecraft/item/ItemBlock":r.itemBlockClass()));
                        case "Lnet/minecraft/creativetab/CreativeTabs;" -> args.add(null);
                        default -> args.add(U);
                    }
                }
                if(textArgs!=1||contentArgs!=1)throw fail("ambiguous registry name parameter");
                run(helper,new Obj(r.sourceOwner()),args,0);
                if(!(named.fields.get("$name") instanceof String))throw fail("helper does not prove the unlocalized name");
                Obj item=named;
                if(block) {
                    item=new Obj(r.itemBlockClass()==null?"net/minecraft/item/ItemBlock":r.itemBlockClass());
                    item.fields.put("$block",named);item.fields.put("field_150939_a",named);
                    item.fields.put("$name",named.fields.get("$name"));
                }
                Method display=find(item.type,List.of("getItemStackDisplayName","func_77653_i"),"(Lnet/minecraft/item/ItemStack;)Ljava/lang/String;");
                if(display!=null)throw fail("custom display-name override requires a separate text rule");
                for(int metadata=0;metadata<256;metadata++) {
                    budget=30_000;readOnly=true;
                    Obj stack=new Obj("net/minecraft/item/ItemStack");stack.fields.put("$metadata",metadata);stack.fields.put("$item",item);
                    try {
                        Method selector=find(item.type,List.of("getUnlocalizedName","func_77667_c"),"(Lnet/minecraft/item/ItemStack;)Ljava/lang/String;");
                        Object key=selector==null?invoke(item.type,"func_77667_c","(Lnet/minecraft/item/ItemStack;)Ljava/lang/String;",item,List.of(stack),INVOKEVIRTUAL,0):run(selector,item,List.of(stack),0);
                        if(!(key instanceof String text)||text.isBlank())throw fail("nonconstant name");
                        keys.put(metadata,text+".name");
                    } catch(RuntimeException excluded) {limitation="some metadata names depend on unsupported source state";}
                }
            } catch(RuntimeException excluded) {limitation=excluded.getMessage();}
            result.add(new NameResult(r.registryName(),block,keys,limitation));
        }
        return List.copyOf(result);
    }

    public record CreativeResult(String registryName, boolean block, List<Integer> metadata, boolean proven, String limitation) {
        public CreativeResult { metadata=List.copyOf(metadata); }
    }

    /** Enumerates the actual source creative callback, not every value accepted by an icon getter. */
    public List<CreativeResult> analyzeCreative(Path jarPath, List<LegacyRegistryAnalyzer.Registration> registrations) throws IOException {
        load(jarPath); List<CreativeResult> result=new ArrayList<>();
        for(var r:registrations) {
            boolean block=r.kind()==LegacyRegistryAnalyzer.Kind.BLOCK;
            List<Integer> metadata=new ArrayList<>(); String limitation=""; boolean proven=false;
            try {
                budget=200_000;readOnly=false;values.clear();
                String type=r.implementationClass();if(type==null)throw fail("creative source class unavailable");
                Obj object;
                try {object=allocation(r);} catch(RuntimeException unavailable) {
                    // A prototype has NO zero-initialized source fields. Only fields assigned by
                    // a proved icon-registration method below may support a creative callback.
                    object=new Obj(type);budget=200_000;values.clear();
                }
                Method method=find(type,block?List.of("getSubBlocks","func_149666_a"):List.of("getSubItems","func_150895_a"),
                        "(Lnet/minecraft/item/Item;Lnet/minecraft/creativetab/CreativeTabs;Ljava/util/List;)V");
                if(method==null) {
                    String base=externalBase(type);
                    if(!Set.of("net/minecraft/block/Block","net/minecraft/block/BlockStairs","net/minecraft/block/BlockContainer",
                            "net/minecraft/item/Item","net/minecraft/item/ItemFood","net/minecraft/item/ItemSword","net/minecraft/item/ItemTool",
                            "net/minecraft/item/ItemPickaxe","net/minecraft/item/ItemArmor","net/minecraft/item/ItemBow",
                            "net/minecraft/item/ItemSeeds","net/minecraft/item/ItemSeedFood","net/minecraft/item/ItemReed").contains(base))
                        throw fail("inherited creative callback not reconstructed: "+base);
                    metadata.add(0);proven=true;
                }else {
                    Method icons=find(type,block?List.of("registerBlockIcons","func_149651_a"):List.of("registerIcons","func_94581_a"),
                            "(Lnet/minecraft/client/renderer/texture/IIconRegister;)V");
                    if(icons!=null)try{run(icons,object,List.of(new Symbol("lfb","icon-register")),0);}
                    catch(RuntimeException unused){budget=200_000;object=new Obj(type);}
                    Obj item=block?new Obj("net/minecraft/item/ItemBlock"):object;
                    if(block){item.fields.put("$block",object);object.fields.put("$itemBlock",item);}
                    Obj list=new Obj("java/util/ArrayList");List<Object> stacks=new ArrayList<>();list.fields.put("$list",stacks);
                    // Unknown tab prevents silently choosing a branch that depends on a particular tab.
                    creativeOutput=list;readOnly=true;
                    try{run(method,object,Arrays.asList(item,U,list),0);}finally{creativeOutput=null;readOnly=false;}
                    if(stacks.size()>4096)throw fail("creative output budget exceeded");
                    LinkedHashSet<Integer> unique=new LinkedHashSet<>();
                    for(Object value:stacks){
                        if(!(value instanceof Obj stack)||!stack.type.equals("net/minecraft/item/ItemStack"))throw fail("non-stack creative output");
                        Object identity=stack.fields.get("$item");
                        if(identity!=item&&identity!=object)throw fail("creative callback returned a different item");
                        int meta=num(stack.fields.get("$metadata")).intValue();
                        if(meta<0||meta>65535)throw fail("creative metadata outside unsigned-short range");
                        if(num(stack.fields.getOrDefault("$count",1)).intValue()!=1)throw fail("non-unit creative stack requires an explicit rule");
                        unique.add(meta);
                    }
                    metadata.addAll(unique);proven=true;
                }
            }catch(RuntimeException unavailable){metadata.clear();limitation=unavailable.getMessage();}
            result.add(new CreativeResult(r.registryName(),block,metadata,proven,limitation));
        }
        return List.copyOf(result);
    }

    /** JVM zero-initialization for source objects that are actually constructed by the interpreter. */
    private Obj newObject(String type) {
        Obj result=new Obj(type);Set<String> visited=new HashSet<>();String c=type;
        while(classes.containsKey(c)&&visited.add(c)) {
            ClassNode node=classes.get(c);
            for(FieldNode f:node.fields)if((f.access&ACC_STATIC)==0) {
                Object zero=switch(f.desc.charAt(0)) {case 'Z','B','C','S','I' -> 0;case 'J' -> 0L;case 'F' -> 0f;case 'D' -> 0d;default -> null;};
                if(result.fields.containsKey(f.name))throw fail("shadowed source field requires owner-qualified analysis: "+f.name);
                result.fields.put(f.name,zero);
            }
            c=node.superName;
        }
        return result;
    }

    private Obj allocation(LegacyRegistryAnalyzer.Registration r) {
        if(r.implementationClass()==null) throw fail("source implementation class unavailable");
        List<Obj> candidates=new ArrayList<>();String allocationFailure="";
        // Match a proved GameRegistry registration to its concrete call expression. A name alone
        // is never used as a texture: it is only an identity key from the existing registry proof.
        for(ClassNode c:classes.values()) for(MethodNode m:c.methods) {
            boolean relevant=false;
            for(AbstractInsnNode n:m.instructions) if(n instanceof LdcInsnNode l && r.registryName().equals(l.cst)) { relevant=true; break; }
            if(!relevant) continue;
            Method method=frames(c,m);
            if(method==null) continue;
            for(int i=0;i<m.instructions.size();i++) {
                if(!(m.instructions.get(i) instanceof MethodInsnNode call)) continue;
                if(!registrationSink(call.owner,call.name,call.desc,r.kind(),new HashSet<>()))continue;
                Type[] types=Type.getArgumentTypes(call.desc); Frame<SourceValue> frame=method.frames()[i];
                if(frame==null||frame.getStackSize()<types.length) continue;
                int offset=frame.getStackSize()-types.length; boolean named=false;
                for(int a=0;a<types.length;a++) if(types[a].getDescriptor().equals("Ljava/lang/String;")) {
                    try { named|=r.registryName().equals(value(method,frame.getStack(offset+a),0)); }
                    catch(Unproven ignored) { }
                }
                if(!named) continue;
                for(int a=0;a<types.length;a++) if(types[a].getSort()==Type.OBJECT &&
                        (types[a].getInternalName().equals("net/minecraft/block/Block")||types[a].getInternalName().equals("net/minecraft/item/Item"))) {
                    try {
                        Object v=value(method,frame.getStack(offset+a),0);
                        if(v instanceof Obj o && o.type.equals(r.implementationClass()) && !candidates.contains(o)) candidates.add(o);
                    } catch(Unproven excluded) { allocationFailure=excluded.getMessage(); }
                }
            }
        }
        if(candidates.size()>1) throw fail("ambiguous source allocation for registered identity");
        if(candidates.size()==1) return copy(candidates.getFirst());
        // A constructor alone cannot prove later fluent setters. Do not silently replace a
        // failed expression trace with an independently constructed, differently configured object.
        throw fail("registered allocation and fluent configuration not proven"+(allocationFailure.isEmpty()?"":": "+allocationFailure));
    }

    private boolean registrationSink(String owner,String name,String desc,LegacyRegistryAnalyzer.Kind kind,Set<String> seen){
        if(!seen.add(owner+"."+name+desc)||seen.size()>64)return false;
        if(owner.equals("cpw/mods/fml/common/registry/GameRegistry"))return name.equals(kind==LegacyRegistryAnalyzer.Kind.BLOCK?"registerBlock":"registerItem");
        Method method=find(owner,List.of(name),desc);if(method==null)return false;
        for(AbstractInsnNode ins:method.node().instructions)if(ins instanceof MethodInsnNode c&&registrationSink(c.owner,c.name,c.desc,kind,seen))return true;
        return false;
    }
    private Method frames(ClassNode c,MethodNode m) {
        if(methods.containsKey(m)) return methods.get(m);
        try {
            Method method=new Method(c,m,new Analyzer<>(new SourceInterpreter()).analyze(c.name,m));
            methods.put(m,method); return method;
        } catch(AnalyzerException | RuntimeException bad) { methods.put(m,null); return null; }
    }
    private Object value(Method method,SourceValue source,int depth) {
        tick(depth);
        if(source==null||source.insns.size()!=1) throw fail("merged or external expression");
        AbstractInsnNode ins=source.insns.iterator().next();
        if(values.containsKey(ins)) return values.get(ins);
        int i=method.node().instructions.indexOf(ins); Frame<SourceValue> f=method.frames()[i];
        int op=ins.getOpcode();
        if(ins instanceof LdcInsnNode l) return l.cst;
        if(ins instanceof VarInsnNode v && op>=ILOAD&&op<=ALOAD) return value(method,f.getLocal(v.var),depth+1);
        if(ins instanceof VarInsnNode && op>=ISTORE&&op<=ASTORE) return value(method,f.getStack(f.getStackSize()-1),depth+1);
        if(op==DUP) return value(method,f.getStack(f.getStackSize()-1),depth+1);
        if(op==GETSTATIC && ins instanceof FieldInsnNode field) return readStatic(field.owner,field.name,depth+1);
        if(op==GETFIELD && ins instanceof FieldInsnNode field) return field(value(method,f.getStack(f.getStackSize()-1),depth+1),field.name);
        if(ins instanceof IntInsnNode n && (op==BIPUSH||op==SIPUSH)) return n.operand;
        if(op>=ICONST_M1&&op<=ICONST_5) return op-ICONST_0;
        if(op==ACONST_NULL) return null;
        if(op>=FCONST_0&&op<=FCONST_2) return (float)(op-FCONST_0);
        if(op>=DCONST_0&&op<=DCONST_1) return (double)(op-DCONST_0);
        if(op==NEW && ins instanceof TypeInsnNode n) {
            Obj obj=newObject(n.desc); values.put(ins,obj);
            try {
                for(int j=i+1;j<method.node().instructions.size();j++) {
                    AbstractInsnNode next=method.node().instructions.get(j);
                    if(!(next instanceof MethodInsnNode init)||!init.name.equals("<init>")) continue;
                    Frame<SourceValue> ff=method.frames()[j]; if(ff==null)continue;
                    int start=ff.getStackSize()-Type.getArgumentTypes(init.desc).length-1;
                    if(start<0)continue;
                    if(value(method,ff.getStack(start),depth+1)!=obj)continue;
                    List<Object> args=new ArrayList<>();
                    for(int k=start+1;k<ff.getStackSize();k++) args.add(value(method,ff.getStack(k),depth+1));
                    invoke(init.owner,init.name,init.desc,obj,args,INVOKESPECIAL,depth+1); return obj;
                }
                throw fail("constructor not traced");
            } catch(RuntimeException ex) { values.remove(ins); throw ex; }
        }
        if(op==ANEWARRAY || op==NEWARRAY) {
            int size=num(value(method,f.getStack(f.getStackSize()-1),depth+1)).intValue();
            checkArray(size); Object[] array=new Object[size]; values.put(ins,array);
            for(int j=i+1;j<method.node().instructions.size();j++) {
                AbstractInsnNode next=method.node().instructions.get(j);
                if(next instanceof JumpInsnNode||next instanceof TableSwitchInsnNode||next instanceof LookupSwitchInsnNode)break;
                if(next instanceof MethodInsnNode)break;
                int store=next.getOpcode();
                if(store!=AASTORE&&store!=IASTORE)continue;
                Frame<SourceValue> ff=method.frames()[j]; if(ff==null||ff.getStackSize()<3)continue;
                try {
                    if(value(method,ff.getStack(ff.getStackSize()-3),depth+1)==array) array[num(value(method,ff.getStack(ff.getStackSize()-2),depth+1)).intValue()]=value(method,ff.getStack(ff.getStackSize()-1),depth+1);
                } catch(Unproven unrelated) { throw unrelated; }
            }
            return array;
        }
        if(ins instanceof MethodInsnNode call) {
            int count=Type.getArgumentTypes(call.desc).length,start=f.getStackSize()-count;
            List<Object> args=new ArrayList<>(); for(int k=start;k<f.getStackSize();k++)args.add(value(method,f.getStack(k),depth+1));
            Object receiver=op==INVOKESTATIC?null:value(method,f.getStack(start-1),depth+1);
            Object result=invoke(call.owner,call.name,call.desc,receiver,args,op,depth+1);
            values.put(ins,result); return result;
        }
        if(op==CHECKCAST) return value(method,f.getStack(f.getStackSize()-1),depth+1);
        if(op>=IADD&&op<=LXOR && f.getStackSize()>=2) return binary(op,value(method,f.getStack(f.getStackSize()-2),depth+1),value(method,f.getStack(f.getStackSize()-1),depth+1));
        throw fail("unsupported registration expression opcode "+op);
    }

    private Object run(Method method,Obj self,List<Object> args,int depth) {
        tick(depth); MethodNode m=method.node();
        if(!m.tryCatchBlocks.isEmpty() && !(m.name.equals("<clinit>") && m.tryCatchBlocks.stream().allMatch(t->"java/lang/NoSuchFieldError".equals(t.type))))throw fail("exception-dependent presentation");
        Object[] locals=new Object[Math.max(m.maxLocals,32)]; Arrays.fill(locals,U);
        int slot=0;if((m.access&ACC_STATIC)==0)locals[slot++]=self;
        Type[] types=Type.getArgumentTypes(m.desc);
        for(int a=0;a<args.size();a++){locals[slot]=args.get(a);slot+=types[a].getSize();}
        List<Object> stack=new ArrayList<>();
        for(int pc=0;pc<m.instructions.size();pc++) {
            tick(depth); AbstractInsnNode ins=m.instructions.get(pc); int op=ins.getOpcode(); if(op<0)continue;
            if(ins instanceof LdcInsnNode l){stack.add(l.cst);continue;}
            if(ins instanceof VarInsnNode v){if(op>=ILOAD&&op<=ALOAD)stack.add(locals[v.var]);else if(op>=ISTORE&&op<=ASTORE)locals[v.var]=pop(stack);else throw fail("local opcode");continue;}
            if(ins instanceof IincInsnNode inc){locals[inc.var]=binary(IADD,locals[inc.var],inc.incr);continue;}
            if(ins instanceof IntInsnNode n &&(op==BIPUSH||op==SIPUSH)){stack.add(n.operand);continue;}
            if(op>=ICONST_M1&&op<=ICONST_5){stack.add(op-ICONST_0);continue;}
            if(op>=FCONST_0&&op<=FCONST_2){stack.add((float)(op-FCONST_0));continue;}
            if(op>=DCONST_0&&op<=DCONST_1){stack.add((double)(op-DCONST_0));continue;}
            if(ins instanceof FieldInsnNode field){
                if(readOnly&&(op==PUTFIELD||op==PUTSTATIC))throw fail("stateful icon selector");
                switch(op){case GETSTATIC->stack.add(readStatic(field.owner,field.name,depth+1));case PUTSTATIC->statics.put(field.owner+"."+field.name,pop(stack));case GETFIELD->stack.add(field(pop(stack),field.name));case PUTFIELD->{Object val=pop(stack);Object obj=pop(stack);if(!(obj instanceof Obj o))throw fail("field receiver");o.fields.put(field.name,val);}default->throw fail("field opcode");}continue;
            }
            if(ins instanceof MethodInsnNode call){int count=Type.getArgumentTypes(call.desc).length;List<Object> actual=new ArrayList<>();for(int a=0;a<count;a++)actual.add(0,pop(stack));Object receiver=op==INVOKESTATIC?null:pop(stack);Object result=invoke(call.owner,call.name,call.desc,receiver,actual,op,depth+1);if(Type.getReturnType(call.desc).getSort()!=Type.VOID)stack.add(result);continue;}
            if(ins instanceof JumpInsnNode j){boolean jump;
                if(op==GOTO)jump=true;
                else if(op==IFNULL||op==IFNONNULL){Object a=pop(stack);if(a==U)throw fail("unknown null branch");jump=(a==null)==(op==IFNULL);}
                else if(op>=IF_ICMPEQ&&op<=IF_ACMPNE){Object b=pop(stack),a=pop(stack);if(a==U||b==U)throw fail("unknown branch");jump=switch(op){case IF_ICMPEQ->num(a).intValue()==num(b).intValue();case IF_ICMPNE->num(a).intValue()!=num(b).intValue();case IF_ICMPLT->num(a).intValue()<num(b).intValue();case IF_ICMPGE->num(a).intValue()>=num(b).intValue();case IF_ICMPGT->num(a).intValue()>num(b).intValue();case IF_ICMPLE->num(a).intValue()<=num(b).intValue();case IF_ACMPEQ->a==b;case IF_ACMPNE->a!=b;default->false;};}
                else {int a=num(pop(stack)).intValue();jump=switch(op){case IFEQ->a==0;case IFNE->a!=0;case IFLT->a<0;case IFGE->a>=0;case IFGT->a>0;case IFLE->a<=0;default->throw fail("jump opcode");};}
                if(jump)pc=m.instructions.indexOf(j.label);continue;
            }
            if(ins instanceof TableSwitchInsnNode sw){int key=num(pop(stack)).intValue();pc=m.instructions.indexOf(key>=sw.min&&key<=sw.max?sw.labels.get(key-sw.min):sw.dflt);continue;}
            if(ins instanceof LookupSwitchInsnNode sw){int idx=sw.keys.indexOf(num(pop(stack)).intValue());pc=m.instructions.indexOf(idx<0?sw.dflt:sw.labels.get(idx));continue;}
            if(op>=IADD&&op<=LXOR &&op!=INEG&&op!=FNEG&&op!=DNEG&&op!=LNEG){Object b=pop(stack),a=pop(stack);stack.add(binary(op,a,b));continue;}
            switch(op){
                case NOP->{} case ACONST_NULL->stack.add(null);
                case POP->pop(stack);case DUP->stack.add(stack.getLast());case DUP_X1->{Object a=pop(stack),b=pop(stack);stack.add(a);stack.add(b);stack.add(a);}case SWAP->{Object a=pop(stack),b=pop(stack);stack.add(a);stack.add(b);}
                case NEW->stack.add(newObject(((TypeInsnNode)ins).desc));
                case ANEWARRAY,NEWARRAY->{int size=num(pop(stack)).intValue();checkArray(size);stack.add(new Object[size]);}
                case ARRAYLENGTH->{Object a=pop(stack);if(!(a instanceof Object[] array))throw fail("array length");stack.add(array.length);}
                case AALOAD,IALOAD,FALOAD,DALOAD,BALOAD,SALOAD,CALOAD->{int at=num(pop(stack)).intValue();Object a=pop(stack);if(!(a instanceof Object[] array))throw fail("array read");Object v=array[at];stack.add(v==null&&op!=AALOAD?0:v);}
                case AASTORE,IASTORE,FASTORE,DASTORE,BASTORE,SASTORE,CASTORE->{if(readOnly)throw fail("stateful icon selector");Object val=pop(stack);int at=num(pop(stack)).intValue();Object a=pop(stack);if(!(a instanceof Object[] array))throw fail("array write");array[at]=val;}
                case CHECKCAST->{} case INSTANCEOF->{Object a=pop(stack);
                    if(geometryMode && a instanceof Neighbour){stack.add(0);}
                    else if(geometryMode && a instanceof Obj obj){stack.add(implementsType(obj.type,((TypeInsnNode)ins).desc,new HashSet<>())?1:0);}
                    else {if(a!=null)throw fail("instanceof hierarchy not proven");stack.add(0);}}
                case I2F,D2F->stack.add(num(pop(stack)).floatValue());case I2D,F2D->stack.add(num(pop(stack)).doubleValue());case F2I,D2I->stack.add(num(pop(stack)).intValue());case I2B->stack.add((int)num(pop(stack)).byteValue());case I2S->stack.add((int)num(pop(stack)).shortValue());case I2C->stack.add((int)(char)num(pop(stack)).intValue());
                case INEG->stack.add(-num(pop(stack)).intValue());case FNEG->stack.add(-num(pop(stack)).floatValue());case DNEG->stack.add(-num(pop(stack)).doubleValue());
                case FCMPL,FCMPG,DCMPL,DCMPG->{double b=num(pop(stack)).doubleValue(),a=num(pop(stack)).doubleValue();stack.add(Double.isNaN(a)||Double.isNaN(b)?(op==FCMPL||op==DCMPL?-1:1):a==b?0:a<b?-1:1);}
                case IRETURN,FRETURN,DRETURN,ARETURN->{return pop(stack);}case RETURN->{return null;}
                default->throw fail("unsupported presentation opcode "+op+" in "+method.owner().name+"."+m.name);
            }
        }
        throw fail("method has no completed return");
    }

    private Object invoke(String owner,String name,String desc,Object receiver,List<Object> args,int opcode,int depth) {
        tick(depth);
        if(geometryMode) {
            if(receiver instanceof ProbeWorld world) {
                int[] delta=probeOffset(args);
                if(Set.of("getBlockMetadata","func_72805_g").contains(name)){
                    if(delta[0]!=0||delta[1]!=0||delta[2]!=0)throw fail("neighbour metadata is not known");
                    return world.metadata();
                }
                if(Set.of("getBlock","func_147439_a").contains(name))return delta[0]==0&&delta[1]==0&&delta[2]==0
                        ?world.self():new Neighbour(delta[0],delta[1],delta[2]);
                throw fail("unsupported geometry world query "+name);
            }
            if(receiver instanceof Neighbour n) {
                if(Set.of("getMaterial","func_149688_o").contains(name))return new Symbol("lfb-probe","non-water-material");
                if(Set.of("getIcon","func_149673_e").contains(name) && args.size()==5){
                    int[] delta=probeOffset(args.subList(1,4));
                    if(!Arrays.equals(delta,new int[]{n.dx(),n.dy(),n.dz()}))throw fail("different neighbour icon coordinates");
                    return new Icon("lfb-neighbour:"+n.dx()+","+n.dy()+","+n.dz()+","+num(args.get(4)).intValue());
                }
                if(Set.of("colorMultiplier","func_149720_d").contains(name))return 0xFFFFFF;
                throw fail("unsupported neighbour operation "+name);
            }
            if(receiver instanceof Symbol symbol && symbol.owner().equals("net/minecraft/init/Blocks")
                    && Set.of("getIcon","func_149691_a").contains(name) && args.size()==2)
                return new Icon(vanillaBlockIcon(symbol,num(args.get(0)).intValue(),num(args.get(1)).intValue()));
            if(receiver instanceof Obj object && Set.of("getIcon","func_149691_a").contains(name)
                    && args.size()==2 && externalBase(object.type).startsWith("net/minecraft/block/")) {
                ensureGeometryIcons(object,depth+1);
                if(find(object.type,List.of("getIcon","func_149691_a"),"(II)Lnet/minecraft/util/IIcon;")==null
                        && object.fields.containsKey("$stairModel"))
                    return invoke("net/minecraft/block/Block","func_149691_a","(II)Lnet/minecraft/util/IIcon;",
                            object.fields.get("$stairModel"),List.of(args.get(0),object.fields.get("$stairMeta")),INVOKEVIRTUAL,depth+1);
            }
        }
        if(owner.equals("net/minecraft/client/renderer/texture/IIconRegister") && Set.of("registerIcon","func_94245_a").contains(name)) {
            if(args.size()!=1||!(args.getFirst() instanceof String s)||s.isBlank())throw fail("nonconstant icon name");return new Icon(s);
        }
        if(opcode==INVOKESTATIC && owner.equals("net/minecraft/block/BlockPistonBase")
                && Set.of("getPistonIcon","func_150074_e").contains(name)
                && desc.equals("(Ljava/lang/String;)Lnet/minecraft/util/IIcon;")){
            if(args.size()!=1||!(args.getFirst() instanceof String icon))throw fail("nonconstant piston icon name");
            return new Icon(vanillaNamedBlockIcon1710(icon));
        }
        if(opcode==INVOKESTATIC && owner.equals("cpw/mods/fml/client/registry/RenderingRegistry")) {
            if(name.equals("getNextAvailableRenderId"))return symbolicRenderId++; // Never mistaken for a vanilla render type.
            if(name.equals("registerBlockHandler"))return null;
        }
        if(receiver instanceof Symbol symbol && symbol.owner().equals("net/minecraft/init/Items") && Set.of("getIconFromDamage","func_77617_a").contains(name)) {
            var vanilla=LegacyVanillaRegistry1710.resolve(symbol.owner(),symbol.name()).orElseThrow(()->fail("unmapped vanilla item field"));
            if(Set.of("string","sugar","egg","feather","book","paper").contains(vanilla.registryName()))return new Icon("minecraft:"+vanilla.registryName());
            throw fail("vanilla item icon semantics not mapped: "+vanilla.registryName());
        }
        if(receiver instanceof Symbol s && s.owner().equals("lfb-world")) {
            if(Set.of("getBlockMetadata","func_72805_g").contains(name) && args.equals(List.of(Coordinate.X,Coordinate.Y,Coordinate.Z)))return Integer.parseInt(s.name());
            throw fail("world-dependent presentation: "+name);
        }
        if(receiver instanceof Object[] array && name.equals("clone") && args.isEmpty())return array.clone();
        if(receiver instanceof Number number) {
            if(name.equals("intValue"))return number.intValue();
            if(name.equals("shortValue"))return number.shortValue();
        }
        if(opcode==INVOKESTATIC && Set.of("java/lang/Integer","java/lang/Short","java/lang/Byte","java/lang/Character").contains(owner) && name.equals("valueOf"))return num(args.getFirst()).intValue();
        if(opcode==INVOKESTATIC && owner.equals("java/lang/System") && name.equals("arraycopy")) {
            if(readOnly)throw fail("stateful source array copy");
            if(!(args.get(0) instanceof Object[] from)||!(args.get(2) instanceof Object[] to))throw fail("unproven source array copy");
            System.arraycopy(from,num(args.get(1)).intValue(),to,num(args.get(3)).intValue(),num(args.get(4)).intValue());return null;
        }
        if(opcode==INVOKESTATIC && owner.equals("cpw/mods/fml/common/registry/GameRegistry") && Set.of("registerItem","registerBlock").contains(name))return args.getFirst();
        if(receiver instanceof Obj object) {
            if(owner.equals("java/lang/Enum")||externalBase(owner).equals("java/lang/Enum")) {
                if(owner.equals("java/lang/Enum")&&name.equals("<init>")){object.fields.put("$enumName",args.get(0));object.fields.put("$ordinal",args.get(1));return null;}
                if(name.equals("ordinal"))return object.fields.getOrDefault("$ordinal",U);
                if(name.equals("name"))return object.fields.getOrDefault("$enumName",U);
            }
            if(Set.of("java/util/HashMap","java/util/LinkedHashMap","java/util/Map").contains(owner)) {
                if(name.equals("<init>")){object.fields.put("$map",new LinkedHashMap<>());return null;}
                @SuppressWarnings("unchecked") Map<Object,Object> map=(Map<Object,Object>)object.fields.get("$map");
                if(map==null)throw fail("map not initialized");
                return switch(name){case "put"->{if(readOnly)throw fail("stateful icon selector");yield map.put(args.get(0),args.get(1));}case "get"->map.get(args.getFirst());case "size"->map.size();case "containsKey"->map.containsKey(args.getFirst())?1:0;default->throw fail("unsupported map operation "+name);};
            }
            if(owner.equals("net/minecraft/item/ItemStack")) {
                if(name.equals("<init>")){object.fields.put("$item",args.getFirst());object.fields.put("$count",args.size()>1?args.get(1):1);object.fields.put("$metadata",args.size()>2?args.get(2):0);return null;}
                if(Set.of("getItemDamage","func_77960_j").contains(name))return object.fields.getOrDefault("$metadata",U);
                if(Set.of("getItem","func_77973_b").contains(name))return object.fields.getOrDefault("$item",U);
                if(Set.of("getMaxItemUseDuration","func_77988_m").contains(name))return object.fields.getOrDefault("$maxUseDuration",U);
            }
            if(name.equals("<init>")&&Set.of("java/lang/Object","java/util/Random").contains(owner))return null;
            if(externalBase(owner).equals("net/minecraft/block/material/Material") && Set.of("setRequiresTool","func_76219_n","setImmovableMobility","func_76221_f","setNoPushMobility","func_76225_o","setTranslucent","func_76223_p").contains(name))return object;
            if(owner.equals("java/lang/StringBuilder")){
                if(name.equals("<init>")){object.fields.put("$text",args.isEmpty()?"":text(args.getFirst()));return null;}
                if(name.equals("append")){object.fields.put("$text",text(object.fields.getOrDefault("$text",""))+text(args.getFirst()));return object;}
                if(name.equals("toString"))return object.fields.getOrDefault("$text","");
            }
            if(owner.equals("java/util/ArrayList")||owner.equals("java/util/LinkedList")||owner.equals("java/util/List")) {
                if(name.equals("<init>")){object.fields.put("$list",new ArrayList<>());return null;}
                @SuppressWarnings("unchecked") List<Object> list=(List<Object>)object.fields.get("$list");
                if(list==null)throw fail("list not initialized");
                return switch(name){case "add"->{if(readOnly&&object!=creativeOutput)throw fail("stateful icon selector");if(list.size()>=4096)throw fail("creative/list output budget exceeded");list.add(args.getFirst());yield 1;}case "get"->list.get(num(args.getFirst()).intValue());case "size"->list.size();case "toArray"->list.toArray();default->throw fail("unsupported list operation "+name);};
            }
            Method target=name.equals("<init>")||opcode==INVOKESPECIAL?find(owner,List.of(name),desc):find(object.type,List.of(name),desc);
            if(target!=null)return run(target,object,args,depth+1);
            String platform=externalBase(owner);
            if(platform.startsWith("net/minecraft/block/")||platform.startsWith("net/minecraft/item/")) {
                if(Set.of("setUnlocalizedName","func_77655_b","setBlockName","func_149663_c").contains(name)) {
                    if(readOnly)throw fail("stateful name selector");
                    object.fields.put("$name",(platform.startsWith("net/minecraft/block/")?"tile.":"item.")+text(args.getFirst()));return object;
                }
                if(Set.of("getUnlocalizedName","func_77658_a","func_77667_c","func_149739_a").contains(name)) {
                    if(object.fields.get("$block") instanceof Obj b)return b.fields.getOrDefault("$name",U);
                    return object.fields.getOrDefault("$name",U);
                }
                if(geometryMode && Set.of("addCollisionBoxesToList","func_149743_a").contains(name)){
                    if(args.size()!=7||!(args.get(0) instanceof ProbeWorld))throw fail("unproven collision forwarding");
                    int[] offset=probeOffset(args.subList(1,4));
                    if(offset[0]!=0||offset[1]!=0||offset[2]!=0)throw fail("shifted collision forwarding");
                    object.fields.put("$collisionCalls",num(object.fields.getOrDefault("$collisionCalls",0)).intValue()+1);return null;
                }
                if(name.equals("<init>")) {
                    // Constructors of unknown vanilla subclasses may have visual semantics. A
                    // static icon can still be proved, but block geometry uses baseRenderType below.
                    object.fields.putIfAbsent("$bounds",List.of(0d,0d,0d,1d,1d,1d));
                    if(geometryMode && platform.equals("net/minecraft/block/BlockPressurePlate")
                            && args.size()==3 && args.getFirst() instanceof String texture){
                        // BlockBasePressurePlate's private icon name is supplied by the constructor,
                        // not by Block.setBlockTextureName.
                        object.fields.put("$texture",texture);
                        inheritedGeometryBounds(object,1,false);
                    }
                    if(geometryMode && platform.equals("net/minecraft/block/BlockCarpet") && args.isEmpty())
                        inheritedGeometryBounds(object,0,false);
                    if(geometryMode && platform.equals("net/minecraft/block/BlockStairs") && args.size()==2){
                        object.fields.put("$stairModel",args.get(0));object.fields.put("$stairMeta",args.get(1));
                    }
                    return null;
                }
                if(Set.of("setTextureName","func_111206_d","setBlockTextureName","func_149658_d").contains(name)) {if(readOnly)throw fail("stateful texture selector");object.fields.put("$texture",args.getFirst());return object;}
                if(Set.of("getIconString","func_111208_A","getTextureName","func_149641_N").contains(name))return object.fields.getOrDefault("$texture",U);
                if(Set.of("setBlockBounds","func_149676_a").contains(name)){if(readOnly&&!(geometryMode&&geometryBoundsWrite))throw fail("stateful bounds selector");object.fields.put("$bounds",new ArrayList<>(args));return null;}
                if(Set.of("getIconFromDamage","func_77617_a","getIcon","func_149691_a").contains(name))return object.fields.getOrDefault(platform.startsWith("net/minecraft/block/")?"blockIcon":"itemIcon",object.fields.getOrDefault(platform.startsWith("net/minecraft/block/")?"field_149761_L":"field_77791_bV",U));
                if(Set.of("setUnlocalizedName","func_77655_b","setBlockName","func_149663_c","setHardness","func_149711_c","setResistance","func_149752_b","setCreativeTab","func_77637_a","func_149647_a","setMaxStackSize","func_77625_d","setMaxDamage","func_77656_e","setHasSubtypes","func_77627_a","setAlwaysEdible","func_77848_i","disableStats","func_149649_H","setNoRepair","setContainerItem","func_77642_a","setStepSound","func_149672_a","setLightLevel","func_149715_a","setLightOpacity","func_149713_g","setTickRandomly","func_149675_a").contains(name))return Type.getReturnType(desc).getSort()==Type.VOID?null:object;
                if(Set.of("registerIcons","func_94581_a","registerBlockIcons","func_149651_a").contains(name)){
                    Object texture=object.fields.get("$texture");if(!(texture instanceof String s))throw fail("base icon has no texture string");Icon icon=new Icon(s);object.fields.put(platform.startsWith("net/minecraft/block/")?"blockIcon":"itemIcon",icon);object.fields.put(platform.startsWith("net/minecraft/block/")?"field_149761_L":"field_77791_bV",icon);return null;
                }
            }
        }
        if(receiver instanceof String s) return switch(name){case "toLowerCase"->s.toLowerCase(Locale.ROOT);case "length"->s.length();case "concat"->s+text(args.getFirst());case "equals"->s.equals(args.getFirst())?1:0;case "substring"->args.size()==1?s.substring(num(args.getFirst()).intValue()):s.substring(num(args.get(0)).intValue(),num(args.get(1)).intValue());default->throw fail("unsupported String operation "+name);};
        if(opcode==INVOKESTATIC){
            if(owner.equals("java/lang/String")&&name.equals("valueOf"))return text(args.getFirst());
            if(owner.equals("java/lang/Math"))return switch(name){case "min"->Math.min(num(args.get(0)).intValue(),num(args.get(1)).intValue());case "max"->Math.max(num(args.get(0)).intValue(),num(args.get(1)).intValue());default->throw fail("unsupported Math operation");};
            Method target=find(owner,List.of(name),desc);if(target!=null)return run(target,null,args,depth+1);
        }
        throw fail("unsupported call "+owner+"."+name+desc);
    }
    private Object readStatic(String owner,String name,int depth) {
        String key=owner+"."+name;
        if(provenRegistryFields.containsKey(key))return provenRegistryFields.get(key);
        if(geometryMode && geometryFields.containsKey(key))return geometryObject(geometryFields.get(key));
        if(failedStatics.contains(owner))throw fail("unproven class initializer "+owner);
        if(statics.containsKey(key))return statics.get(key);
        ClassNode c=classes.get(owner);
        if(c==null){Object symbol=new Symbol(owner,name);statics.put(key,symbol);return symbol;}
        for(FieldNode f:c.fields)if(f.name.equals(name)&&f.value!=null){statics.put(key,f.value);return f.value;}
        if(initialized.add(owner)){
            Method cl=find(owner,List.of("<clinit>"),"()V");
            if(cl!=null){boolean prior=readOnly;readOnly=false;try{run(cl,null,List.of(),depth+1);}catch(RuntimeException ex){failedStatics.add(owner);statics.keySet().removeIf(k->k.startsWith(owner+"."));throw ex;}finally{readOnly=prior;}}
        }
        return statics.getOrDefault(key,U);
    }
    private Method find(String owner,List<String> names,String desc) {
        Set<String> visited=new HashSet<>();
        while(owner!=null&&visited.add(owner)){ClassNode c=classes.get(owner);if(c==null)return null;for(MethodNode m:c.methods)if(names.contains(m.name)&&m.desc.equals(desc))return new Method(c,m,null);owner=c.superName;}return null;
    }
    private boolean subclass(String owner,String target){Set<String> visited=new HashSet<>();while(owner!=null&&visited.add(owner)){if(owner.equals(target))return true;ClassNode c=classes.get(owner);owner=c==null?null:c.superName;}return false;}
    private String externalBase(String owner){Set<String> seen=new HashSet<>();while(owner!=null&&classes.containsKey(owner)&&seen.add(owner))owner=classes.get(owner).superName;return owner==null?"":owner;}
    private int baseRenderType(String owner){Set<String> visited=new HashSet<>();while(classes.containsKey(owner)&&visited.add(owner))owner=classes.get(owner).superName;if(Set.of("net/minecraft/block/Block","net/minecraft/block/BlockContainer","net/minecraft/block/BlockLeavesBase").contains(owner))return 0;if(owner.equals("net/minecraft/block/BlockLiquid"))return 4;if(Set.of("net/minecraft/block/BlockBush","net/minecraft/block/BlockSapling").contains(owner))return 1;throw fail("vanilla superclass rendering not reconstructed: "+owner);}
    private static Object field(Object obj,String name){if(!(obj instanceof Obj o))throw fail("external instance field "+name);return o.fields.getOrDefault(name,U);}
    private static Obj copy(Obj obj){Obj c=new Obj(obj.type);for(var e:obj.fields.entrySet()){Object v=e.getValue();c.fields.put(e.getKey(),v instanceof Object[] a?a.clone():v instanceof List<?> l?new ArrayList<>(l):v);}return c;}
    private static Object pop(List<Object> stack){if(stack.isEmpty())throw fail("operand stack underflow");return stack.removeLast();}
    private static Number num(Object value){if(value instanceof Number n)return n;throw fail("nonconstant numeric value");}
    private static String text(Object value){if(value instanceof String||value instanceof Number||value==null)return String.valueOf(value);throw fail("nonconstant string value");}
    private static void checkArray(int size){if(size<0||size>4096)throw fail("array allocation outside bounded presentation limit");}
    private void tick(int depth){if(depth>32||--budget<0)throw fail("bounded presentation analysis limit");}
    private static Unproven fail(String why){return new Unproven(why);}
    private static Object binary(int op,Object av,Object bv){
        if(av instanceof Coordinate c)av=new Offset(c,0);
        if(bv instanceof Coordinate c)bv=new Offset(c,0);
        if(av instanceof Offset a && bv instanceof Integer b && (op==IADD||op==ISUB))return new Offset(a.axis(),Math.addExact(a.offset(),op==IADD?b:-b));
        if(av instanceof Integer a && bv instanceof Offset b && op==IADD)return new Offset(b.axis(),Math.addExact(b.offset(),a));
        Number a=num(av),b=num(bv);return switch(op){
        case IADD->a.intValue()+b.intValue();case ISUB->a.intValue()-b.intValue();case IMUL->a.intValue()*b.intValue();case IDIV->a.intValue()/b.intValue();case IREM->a.intValue()%b.intValue();case IAND->a.intValue()&b.intValue();case IOR->a.intValue()|b.intValue();case IXOR->a.intValue()^b.intValue();case ISHL->a.intValue()<<b.intValue();case ISHR->a.intValue()>>b.intValue();case IUSHR->a.intValue()>>>b.intValue();
        case FADD->a.floatValue()+b.floatValue();case FSUB->a.floatValue()-b.floatValue();case FMUL->a.floatValue()*b.floatValue();case FDIV->a.floatValue()/b.floatValue();case FREM->a.floatValue()%b.floatValue();case DADD->a.doubleValue()+b.doubleValue();case DSUB->a.doubleValue()-b.doubleValue();case DMUL->a.doubleValue()*b.doubleValue();case DDIV->a.doubleValue()/b.doubleValue();case DREM->a.doubleValue()%b.doubleValue();default->throw fail("unsupported arithmetic opcode "+op);};}
}
