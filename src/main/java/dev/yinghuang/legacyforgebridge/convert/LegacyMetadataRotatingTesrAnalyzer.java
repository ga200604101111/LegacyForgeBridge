package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Source-only proof for BlockContainer TESRs whose visual angle is accumulated client-side from
 * raw block metadata (degrees per tick). Gameplay TileEntity state may remain server-authoritative;
 * only the metadata-driven presentation is replayed.
 */
public final class LegacyMetadataRotatingTesrAnalyzer {
    private static final String BLOCK_CONTAINER="net/minecraft/block/BlockContainer";
    private static final String TILE="net/minecraft/tileentity/TileEntity";
    private static final String TESR="net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer";
    private static final String MODEL_BASE="net/minecraft/client/model/ModelBase";
    private static final String MODEL_RENDERER="net/minecraft/client/model/ModelRenderer";
    private static final Set<String> Y_ROT=Set.of("field_78796_g","rotateAngleY");

    public record Cuboid(String field,int u,int v,float x,float y,float z,int width,int height,int depth,
                         float pivotX,float pivotY,float pivotZ,boolean mirror){
        public Cuboid{
            if(field==null||field.isBlank()||u<0||v<0||width<=0||height<=0||depth<=0
                    ||!finite(x,y,z,pivotX,pivotY,pivotZ))throw new IllegalArgumentException("Invalid metadata-rotating cuboid");
        }
    }
    public record Rule(String registryName,String sourceBlockClass,String sourceTileClass,String legacyTileId,
                       String sourceRendererClass,String sourceModelClass,String texture,int textureWidth,int textureHeight,
                       List<Cuboid> cuboids,String animatedPart,float modelScale,
                       float translateX,float translateY,float translateZ,int metadataMask,float degreesPerMetadataPerTick,
                       boolean inventoryStaticZeroAngle){
        public Rule{
            cuboids=List.copyOf(cuboids);
            if(registryName==null||registryName.isBlank()||sourceBlockClass==null||sourceTileClass==null||legacyTileId==null
                    ||sourceRendererClass==null||sourceModelClass==null||texture==null||textureWidth<=0||textureHeight<=0
                    ||cuboids.isEmpty()||cuboids.size()>64||animatedPart==null||modelScale<=0F
                    ||metadataMask<1||metadataMask>15||!finite(modelScale,translateX,translateY,translateZ,degreesPerMetadataPerTick)
                    ||degreesPerMetadataPerTick<=0F||!inventoryStaticZeroAngle)
                throw new IllegalArgumentException("Invalid metadata-rotating TESR rule");
        }
    }
    public record Skipped(String registryName,String sourceBlockClass,String reason){}
    public record Analysis(List<Rule> rules,List<Skipped> skipped,List<String> diagnostics){
        public Analysis{rules=List.copyOf(rules);skipped=List.copyOf(skipped);diagnostics=List.copyOf(diagnostics);}
    }
    private record Box(String field,int u,int v,float x,float y,float z,int width,int height,int depth){}
    private record Pivot(float x,float y,float z){}
    private record PngSize(int width,int height){}

    public Analysis analyze(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=load(jarPath);
        var registry=new LegacyRegistryAnalyzer().analyze(jarPath);
        var lifecycle=new LegacyLifecycleAnalyzer().analyze(jarPath);
        Map<String,String> tileIds=registeredTiles(lifecycle);
        List<Rule> rules=new ArrayList<>();List<Skipped> skipped=new ArrayList<>();
        LinkedHashSet<String> diagnostics=new LinkedHashSet<>();diagnostics.addAll(registry.diagnostics());diagnostics.addAll(lifecycle.diagnostics());

        for(var registration:registry.blocks()){
            String block=registration.implementationClass();
            if(block==null||!inherits(classes,block,BLOCK_CONTAINER))continue;
            MethodNode create=effective(classes,block,Set.of("createNewTileEntity","func_149915_a"),
                    "(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;");
            String tile=uniqueCreatedType(create);
            if(tile==null||!tileIds.containsKey(tile)||!inherits(classes,tile,TILE))continue;
            ClassNode blockNode=classes.get(block),tileNode=classes.get(tile);
            if(blockNode==null||tileNode==null)continue;

            MethodNode opaque=effective(classes,block,Set.of("isOpaqueCube","func_149662_c"),"()Z");
            MethodNode normal=effective(classes,block,Set.of("renderAsNormalBlock","func_149686_d"),"()Z");
            if(!Boolean.FALSE.equals(returnedBoolean(opaque))||!Boolean.FALSE.equals(returnedBoolean(normal)))continue;

            String renderer=findRendererRegistration(classes,tile,tileIds.get(tile));
            if(renderer==null)continue;
            ClassNode rendererNode=classes.get(renderer);String modelClass=rendererModel(rendererNode,classes);
            ClassNode model=classes.get(modelClass);if(model==null)continue;
            String texture=singleTexture(rendererNode);PngSize png=pngSize(jarPath,texture);
            if(texture==null||png==null){skipped.add(new Skipped(registration.registryName(),block,"TESR texture is missing or ambiguous"));continue;}

            MethodNode world=worldRender(rendererNode,tile,modelClass);
            if(world==null||!centeredWorldTransform(world)){skipped.add(new Skipped(registration.registryName(),block,"TESR world transform is outside centered metadata-rotating family"));continue;}
            MethodNode inventory=ownMethod(rendererNode,"renderInv","()V");
            if(inventory==null||!calls(inventory,modelClass,"renderInv","()V")){skipped.add(new Skipped(registration.registryName(),block,"TESR inventory path is missing static source model draw"));continue;}

            int[] textureSize=modelTexture(model);Map<String,Box> boxes=boxes(model);Map<String,Pivot> pivots=pivots(model);Set<String> mirrors=mirrors(model);
            if(boxes.isEmpty()||!pivots.keySet().containsAll(boxes.keySet())){skipped.add(new Skipped(registration.registryName(),block,"ModelRenderer cuboid/pivot proof is incomplete"));continue;}

            float worldScale=worldModelScale(world,modelClass);if(!Float.isFinite(worldScale)||worldScale<=0F){skipped.add(new Skipped(registration.registryName(),block,"TESR model scale argument is unresolved"));continue;}
            DynamicModel dynamic=dynamicModel(model,tile,boxes.keySet());
            if(dynamic==null){skipped.add(new Skipped(registration.registryName(),block,"Model does not prove one metadata-driven Y-rotated part"));continue;}
            if(!staticInventory(model,dynamic.animatedPart(),boxes.keySet(),worldScale)){skipped.add(new Skipped(registration.registryName(),block,"Inventory model does not reset animated part to zero and render all cuboids at the world model scale"));continue;}
            if(!tileAnimation(classes,tile,dynamic.tileGetter())){skipped.add(new Skipped(registration.registryName(),block,"Tile client tick does not prove roll += block metadata with 360-degree wrap"));continue;}

            List<Cuboid> cuboids=new ArrayList<>();
            for(Box box:boxes.values()){Pivot pivot=pivots.get(box.field());cuboids.add(new Cuboid(box.field(),box.u(),box.v(),box.x(),box.y(),box.z(),
                    box.width(),box.height(),box.depth(),pivot.x(),pivot.y(),pivot.z(),mirrors.contains(box.field())));}
            cuboids.sort(Comparator.comparing(Cuboid::field));
            rules.add(new Rule(registration.registryName(),block,tile,tileIds.get(tile),renderer,modelClass,texture,
                    textureSize[0],textureSize[1],cuboids,dynamic.animatedPart(),worldScale,.5F,.5F,.5F,15,1F,true));
        }
        return new Analysis(rules,skipped,List.copyOf(diagnostics));
    }

    private record DynamicModel(String animatedPart,String tileGetter){}

    private static DynamicModel dynamicModel(ClassNode model,String tile,Set<String> fields){
        DynamicModel found=null;
        for(MethodNode method:model.methods){
            Type[] args=Type.getArgumentTypes(method.desc);
            if(args.length!=7||args[0].getSort()!=Type.OBJECT||!args[0].getInternalName().equals(tile)||Type.getReturnType(method.desc).getSort()!=Type.VOID)continue;
            int scaleLocal=parameterLocal(method,args.length-1);if(scaleLocal<0)continue;
            List<AbstractInsnNode> code=real(method);String animated=null,getter=null;Set<String> rendered=new LinkedHashSet<>();
            for(int i=0;i<code.size();i++){
                AbstractInsnNode insn=code.get(i);
                if(insn instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(MODEL_RENDERER)&&Y_ROT.contains(put.name)){
                    for(int j=i-1;j>=Math.max(0,i-12);j--)if(code.get(j) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD
                            &&field.owner.equals(model.name)&&fields.contains(field.name)){animated=field.name;break;}
                    for(int j=i-1;j>=Math.max(0,i-12);j--)if(code.get(j) instanceof MethodInsnNode call&&call.owner.equals(tile)&&call.desc.equals("()I")){getter=call.name;break;}
                    if(!containsDoubleBetween(code,Math.PI,Math.max(0,i-12),i)||!containsFloatBetween(code,180F,Math.max(0,i-12),i))return null;
                }
                if(insn instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&Set.of("render","func_78785_a").contains(call.name)&&call.desc.equals("(F)V")){
                    String field=null;for(int j=i-1;j>=Math.max(0,i-4);j--)if(code.get(j) instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&f.owner.equals(model.name)&&fields.contains(f.name)){field=f.name;break;}
                    AbstractInsnNode previous=previousReal(insn);
                    if(field==null||!(previous instanceof VarInsnNode load)||load.getOpcode()!=Opcodes.FLOAD||load.var!=scaleLocal)return null;
                    rendered.add(field);
                }
            }
            if(animated==null||getter==null||!rendered.containsAll(fields))continue;
            DynamicModel candidate=new DynamicModel(animated,getter);if(found!=null&&!found.equals(candidate))return null;found=candidate;
        }
        return found;
    }

    private static int parameterLocal(MethodNode method,int argumentIndex){
        if(method==null||argumentIndex<0)return -1;Type[] args=Type.getArgumentTypes(method.desc);if(argumentIndex>=args.length)return -1;
        int local=(method.access&Opcodes.ACC_STATIC)==0?1:0;for(int i=0;i<argumentIndex;i++)local+=args[i].getSize();return local;
    }

    private static float worldModelScale(MethodNode world,String modelClass){
        if(world==null)return Float.NaN;Float scale=null;for(AbstractInsnNode insn:world.instructions){
            if(!(insn instanceof MethodInsnNode call)||!call.owner.equals(modelClass)||Type.getReturnType(call.desc).getSort()!=Type.VOID)continue;
            Type[] args=Type.getArgumentTypes(call.desc);if(args.length==0||args[args.length-1].getSort()!=Type.FLOAT)continue;
            Float value=floatConstant(previousReal(insn));if(value==null||value<=0F)return Float.NaN;
            if(scale!=null&&Float.compare(scale,value)!=0)return Float.NaN;scale=value;
        }
        return scale==null?Float.NaN:scale;
    }

    private static boolean staticInventory(ClassNode model,String animated,Set<String> fields,float expectedScale){
        MethodNode method=ownMethod(model,"renderInv","()V");if(method==null)return false;List<AbstractInsnNode> code=real(method);
        boolean zero=false;Set<String> rendered=new LinkedHashSet<>();
        for(int i=0;i<code.size();i++){
            if(code.get(i) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(MODEL_RENDERER)&&Y_ROT.contains(put.name)
                    &&i>=2&&Float.valueOf(0F).equals(floatConstant(code.get(i-1)))&&code.get(i-2) instanceof FieldInsnNode field
                    &&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(model.name)&&field.name.equals(animated))zero=true;
            if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&Set.of("render","func_78785_a").contains(call.name)&&call.desc.equals("(F)V")){
                Float scale=floatConstant(previousReal(code.get(i)));if(scale==null||Float.compare(scale,expectedScale)!=0)return false;
                for(int j=i-1;j>=Math.max(0,i-4);j--)if(code.get(j) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(model.name)&&fields.contains(field.name)){rendered.add(field.name);break;}
            }
        }
        return zero&&rendered.containsAll(fields);
    }

    private static boolean tileAnimation(Map<String,ClassNode> classes,String tile,String getterName){
        MethodNode getter=effective(classes,tile,Set.of(getterName),"()I");String roll=returnedFloatAsIntField(getter);if(roll==null)return false;
        MethodNode tick=effective(classes,tile,Set.of("updateEntity","func_145845_h"),"()V");if(tick==null)return false;
        boolean client=false,metadata=false,add=false,wrap=false,zero=false,write=false;List<AbstractInsnNode> code=real(tick);
        for(int i=0;i<code.size();i++){
            AbstractInsnNode insn=code.get(i);
            if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals("net/minecraft/world/World")
                    &&field.desc.equals("Z")&&Set.of("field_72995_K","isRemote").contains(field.name))client=true;
            if(insn instanceof MethodInsnNode call&&call.desc.equals("()I")&&Set.of("func_145832_p","getBlockMetadata").contains(call.name))metadata=true;
            if(insn.getOpcode()==Opcodes.FADD)add=true;
            Float f=floatConstant(insn);if(f!=null&&Float.compare(f,360F)==0)wrap=true;
            if(insn instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.name.equals(roll)&&put.desc.equals("F")){
                write=true;AbstractInsnNode prev=previousReal(insn);Float v=floatConstant(prev);if(v!=null&&Float.compare(v,0F)==0)zero=true;
            }
        }
        return client&&metadata&&add&&wrap&&zero&&write;
    }

    private static String returnedFloatAsIntField(MethodNode method){
        if(method==null)return null;List<AbstractInsnNode> code=real(method);
        for(int i=0;i+2<code.size();i++)if(code.get(i) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.desc.equals("F")
                &&code.get(i+1).getOpcode()==Opcodes.F2I&&code.get(i+2).getOpcode()==Opcodes.IRETURN)return field.name;
        return null;
    }

    private static boolean centeredWorldTransform(MethodNode method){
        int halves=0;boolean push=false,pop=false,translate=false;
        for(AbstractInsnNode insn:method.instructions){
            if(insn instanceof MethodInsnNode call&&call.owner.equals("org/lwjgl/opengl/GL11")){
                push|=call.name.equals("glPushMatrix");pop|=call.name.equals("glPopMatrix");translate|=call.name.equals("glTranslatef")&&call.desc.equals("(FFF)V");
            }
            Float f=floatConstant(insn);if(f!=null&&Float.compare(f,.5F)==0)halves++;
        }
        return push&&pop&&translate&&halves>=3;
    }

    private static String findRendererRegistration(Map<String,ClassNode> classes,String tileClass,String tileId){
        LinkedHashSet<String> renderers=new LinkedHashSet<>();
        for(ClassNode owner:classes.values())for(MethodNode method:owner.methods)for(AbstractInsnNode insn:method.instructions){
            if(!(insn instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESTATIC
                    ||!call.owner.equals("cpw/mods/fml/client/registry/ClientRegistry")||!call.name.equals("registerTileEntity")
                    ||!call.desc.equals("(Ljava/lang/Class;Ljava/lang/String;Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;)V"))continue;
            LegacyDirectCallArguments.ClassStringNew args=LegacyDirectCallArguments.classStringNew(owner,method,call);
            if(args!=null&&args.classInternalName().equals(tileClass)&&args.stringValue().equals(tileId)
                    &&inherits(classes,args.newTypeInternalName(),TESR))renderers.add(args.newTypeInternalName());
        }
        return renderers.size()==1?renderers.getFirst():null;
    }

    private static String rendererModel(ClassNode renderer,Map<String,ClassNode> classes){
        if(renderer==null)return null;LinkedHashSet<String> models=new LinkedHashSet<>();
        for(MethodNode method:renderer.methods)for(AbstractInsnNode insn:method.instructions)
            if(insn instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW&&inherits(classes,type.desc,MODEL_BASE))models.add(type.desc);
        return models.size()==1?models.getFirst():null;
    }

    private static MethodNode worldRender(ClassNode renderer,String tile,String model){
        if(renderer==null)return null;MethodNode found=null;
        for(MethodNode method:renderer.methods)if(method.desc.equals("(Lnet/minecraft/tileentity/TileEntity;DDDF)V")){
            boolean cast=false,draw=false;
            for(AbstractInsnNode insn:method.instructions){
                if(insn instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.CHECKCAST&&type.desc.equals(tile))cast=true;
                if(insn instanceof MethodInsnNode call&&call.owner.equals(model)&&Type.getArgumentTypes(call.desc).length==7)draw=true;
            }
            if(cast&&draw){if(found!=null)return null;found=method;}
        }
        return found;
    }

    private static int[] modelTexture(ClassNode model){
        int width=64,height=32;MethodNode ctor=ownMethod(model,"<init>","()V");List<AbstractInsnNode> code=real(ctor);
        for(int i=1;i<code.size();i++)if(code.get(i) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(model.name)&&field.desc.equals("I")){
            Integer value=intConstant(code.get(i-1));if(value==null||value<=0)continue;
            if(Set.of("field_78090_t","textureWidth").contains(field.name))width=value;
            if(Set.of("field_78089_u","textureHeight").contains(field.name))height=value;
        }return new int[]{width,height};
    }

    private static Map<String,Box> boxes(ClassNode model){
        MethodNode ctor=ownMethod(model,"<init>","()V");if(ctor==null)return Map.of();List<AbstractInsnNode> code=real(ctor);
        Map<String,int[]> uv=new LinkedHashMap<>();Map<String,Box> out=new LinkedHashMap<>();
        for(int i=2;i+1<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL
                &&call.owner.equals(MODEL_RENDERER)&&call.name.equals("<init>")&&call.desc.equals("(Lnet/minecraft/client/model/ModelBase;II)V")){
            Integer u=intConstant(code.get(i-2)),v=intConstant(code.get(i-1));
            if(u!=null&&v!=null&&code.get(i+1) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(model.name))uv.put(field.name,new int[]{u,v});
        }
        for(int i=7;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)
                &&call.desc.equals("(FFFIII)Lnet/minecraft/client/model/ModelRenderer;")&&code.get(i-7) instanceof FieldInsnNode field
                &&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(model.name)){
            Float x=floatConstant(code.get(i-6)),y=floatConstant(code.get(i-5)),z=floatConstant(code.get(i-4));
            Integer w=intConstant(code.get(i-3)),h=intConstant(code.get(i-2)),d=intConstant(code.get(i-1));int[] tex=uv.get(field.name);
            if(x!=null&&y!=null&&z!=null&&w!=null&&h!=null&&d!=null&&tex!=null)out.put(field.name,new Box(field.name,tex[0],tex[1],x,y,z,w,h,d));
        }return out;
    }

    private static Map<String,Pivot> pivots(ClassNode model){
        MethodNode ctor=ownMethod(model,"<init>","()V");if(ctor==null)return Map.of();List<AbstractInsnNode> code=real(ctor);Map<String,Pivot> out=new LinkedHashMap<>();
        for(int i=4;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&call.desc.equals("(FFF)V")
                &&code.get(i-4) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(model.name)){
            Float x=floatConstant(code.get(i-3)),y=floatConstant(code.get(i-2)),z=floatConstant(code.get(i-1));
            if(x!=null&&y!=null&&z!=null)out.put(field.name,new Pivot(x,y,z));
        }return out;
    }

    private static Set<String> mirrors(ClassNode model){
        MethodNode ctor=ownMethod(model,"<init>","()V");if(ctor==null)return Set.of();List<AbstractInsnNode> code=real(ctor);Set<String> out=new LinkedHashSet<>();
        for(int i=2;i<code.size();i++)if(code.get(i) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(MODEL_RENDERER)
                &&put.desc.equals("Z")&&Set.of("field_78809_i","mirror").contains(put.name)&&Integer.valueOf(1).equals(intConstant(code.get(i-1)))
                &&code.get(i-2) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(model.name))out.add(field.name);
        return out;
    }

    private static String singleTexture(ClassNode renderer){
        LinkedHashSet<String> values=new LinkedHashSet<>();if(renderer==null)return null;
        for(MethodNode method:renderer.methods)for(AbstractInsnNode insn:method.instructions)
            if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof String text&&text.indexOf(':')>0&&text.contains("textures/")&&text.endsWith(".png"))values.add(text);
        return values.size()==1?values.getFirst():null;
    }

    private static PngSize pngSize(Path jarPath,String resource)throws IOException{
        if(resource==null)return null;int colon=resource.indexOf(':');if(colon<=0)return null;String name="assets/"+resource.substring(0,colon)+"/"+resource.substring(colon+1);
        try(JarFile jar=new JarFile(jarPath.toFile(),false)){JarEntry entry=jar.getJarEntry(name);if(entry==null)return null;try(InputStream input=jar.getInputStream(entry)){
            byte[] header=input.readNBytes(24);if(header.length<24)return null;byte[] sig={(byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A};
            for(int i=0;i<8;i++)if(header[i]!=sig[i])return null;ByteBuffer buffer=ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
            int w=buffer.getInt(16),h=buffer.getInt(20);return w>0&&h>0?new PngSize(w,h):null;
        }}
    }

    private static Map<String,String> registeredTiles(LegacyLifecycleAnalyzer.Analysis lifecycle){
        Map<String,String> out=new LinkedHashMap<>();
        for(var registration:lifecycle.of(LegacyLifecycleAnalyzer.Kind.TILE_ENTITY))if(registration.arguments().size()>=2
                &&registration.arguments().get(0) instanceof LegacyLifecycleAnalyzer.TypeValue type
                &&registration.arguments().get(1) instanceof LegacyLifecycleAnalyzer.TextValue text)out.put(type.internalName(),text.value());
        return out;
    }
    private static String uniqueCreatedType(MethodNode method){
        if(method==null)return null;LinkedHashSet<String> types=new LinkedHashSet<>();
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW)types.add(type.desc);
        return types.size()==1?types.getFirst():null;
    }
    private static Boolean returnedBoolean(MethodNode method){List<AbstractInsnNode> code=real(method);return code.size()==2&&code.get(1).getOpcode()==Opcodes.IRETURN?switch(code.get(0).getOpcode()){case Opcodes.ICONST_0->Boolean.FALSE;case Opcodes.ICONST_1->Boolean.TRUE;default->null;}:null;}
    private static MethodNode effective(Map<String,ClassNode> classes,String owner,Set<String> names,String desc){
        Set<String> seen=new HashSet<>();while(owner!=null&&seen.add(owner)){ClassNode node=classes.get(owner);if(node==null)return null;
            for(MethodNode method:node.methods)if(names.contains(method.name)&&method.desc.equals(desc))return method;owner=node.superName;}return null;
    }
    private static MethodNode ownMethod(ClassNode node,String name,String desc){if(node==null)return null;for(MethodNode method:node.methods)if(method.name.equals(name)&&method.desc.equals(desc))return method;return null;}
    private static boolean inherits(Map<String,ClassNode> classes,String owner,String target){Set<String> seen=new HashSet<>();while(owner!=null&&seen.add(owner)){if(owner.equals(target))return true;ClassNode node=classes.get(owner);owner=node==null?null:node.superName;}return false;}
    private static boolean calls(MethodNode method,String owner,String name,String desc){if(method==null)return false;for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(desc))return true;return false;}
    private static boolean containsDoubleBetween(List<AbstractInsnNode> code,double target,int start,int end){for(int i=start;i<end;i++)if(code.get(i) instanceof LdcInsnNode ldc&&ldc.cst instanceof Double d&&Math.abs(d-target)<1e-12)return true;return false;}
    private static boolean containsFloatBetween(List<AbstractInsnNode> code,float target,int start,int end){for(int i=start;i<end;i++){Float f=floatConstant(code.get(i));if(f!=null&&Float.compare(f,target)==0)return true;}return false;}
    private static AbstractInsnNode previousReal(AbstractInsnNode node){for(AbstractInsnNode p=node==null?null:node.getPrevious();p!=null;p=p.getPrevious())if(p.getOpcode()>=0)return p;return null;}
    private static Integer intConstant(AbstractInsnNode insn){if(insn==null)return null;return switch(insn.getOpcode()){case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer v?v:null;default->null;};}
    private static Float floatConstant(AbstractInsnNode insn){if(insn==null)return null;return switch(insn.getOpcode()){case Opcodes.FCONST_0->0F;case Opcodes.FCONST_1->1F;case Opcodes.FCONST_2->2F;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Number n?n.floatValue():null;default->null;};}
    private static List<AbstractInsnNode> real(MethodNode method){List<AbstractInsnNode> out=new ArrayList<>();if(method!=null)for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)out.add(insn);return out;}
    private static boolean finite(float... values){for(float value:values)if(!Float.isFinite(value))return false;return true;}
    private static Map<String,ClassNode> load(Path jarPath)throws IOException{
        Map<String,ClassNode> out=new LinkedHashMap<>();try(JarFile jar=new JarFile(jarPath.toFile(),false)){var entries=jar.entries();while(entries.hasMoreElements()){
            JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;
            try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);out.put(node.name,node);}catch(RuntimeException ignored){}
        }}return out;
    }
}
