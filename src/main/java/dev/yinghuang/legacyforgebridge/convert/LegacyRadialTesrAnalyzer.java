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
 * Source-only proof for a bounded TESR family whose unconditional base presentation repeatedly
 * renders one ModelRenderer cuboid at a fixed set of constructor-derived X/Y rotations.
 *
 * <p>This is useful for legacy radial arrangements such as logs, spokes and petals. The proof is
 * deliberately independent of registry/class/method names. Conditional model groups in the TESR
 * are inventoried but are not claimed complete by this base-family adapter.</p>
 */
public final class LegacyRadialTesrAnalyzer {
    private static final String BLOCK_CONTAINER="net/minecraft/block/BlockContainer";
    private static final String TILE="net/minecraft/tileentity/TileEntity";
    private static final String TESR="net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer";
    private static final String MODEL_BASE="net/minecraft/client/model/ModelBase";
    private static final String MODEL_RENDERER="net/minecraft/client/model/ModelRenderer";
    private static final Set<String> X_ROT=Set.of("field_78795_f","rotateAngleX");
    private static final Set<String> Y_ROT=Set.of("field_78796_g","rotateAngleY");

    public record Cuboid(int u,int v,float x,float y,float z,int width,int height,int depth,
                         float pivotX,float pivotY,float pivotZ){
        public Cuboid{
            if(u<0||v<0||width<=0||height<=0||depth<=0)throw new IllegalArgumentException("Invalid radial cuboid");
            for(float f:new float[]{x,y,z,pivotX,pivotY,pivotZ})if(!Float.isFinite(f))throw new IllegalArgumentException("Non-finite radial cuboid");
        }
    }
    public record Pose(float xRot,float yRot,float zRot){
        public Pose{if(!Float.isFinite(xRot)||!Float.isFinite(yRot)||!Float.isFinite(zRot))throw new IllegalArgumentException("Non-finite radial pose");}
    }
    public record Rule(String registryName,String sourceBlockClass,String sourceTileClass,String legacyTileId,
                       String sourceRendererClass,String sourceModelClass,String texture,int textureWidth,int textureHeight,
                       Cuboid cuboid,List<Pose> poses,float modelScale,float translateX,float translateY,float translateZ,
                       int metadataMask,float yawDegreesPerMeta,float sourceLightLevel,int modernLightEmission,
                       float inventoryTranslateY,float inventoryScale,int conditionalModelCalls){
        public Rule{
            poses=List.copyOf(poses);
            if(registryName==null||registryName.isBlank()||sourceBlockClass==null||sourceTileClass==null||legacyTileId==null
                    ||sourceRendererClass==null||sourceModelClass==null||texture==null||textureWidth<=0||textureHeight<=0
                    ||cuboid==null||poses.size()<2||poses.size()>16||!Float.isFinite(modelScale)||modelScale<=0F
                    ||metadataMask<0||metadataMask>15||modernLightEmission<0||modernLightEmission>15
                    ||!Float.isFinite(inventoryScale)||inventoryScale<=0F||conditionalModelCalls<0)
                throw new IllegalArgumentException("Invalid radial TESR proof");
        }
    }
    public record Skipped(String registryName,String sourceBlockClass,String reason){}
    public record Analysis(List<Rule> rules,List<Skipped> skipped,List<String> diagnostics){
        public Analysis{rules=List.copyOf(rules);skipped=List.copyOf(skipped);diagnostics=List.copyOf(diagnostics);}
    }
    private record Box(String field,int u,int v,float x,float y,float z,int width,int height,int depth){}
    private record Pivot(float x,float y,float z){}
    private record RadialMethod(String field,String xArray,String yArray,int count,float scale){}
    private record ArrayFormula(String field,double perIndexRadians,Float evenRadians,Float oddRadians){}

    public Analysis analyze(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=load(jarPath);var registry=new LegacyRegistryAnalyzer().analyze(jarPath);
        var lifecycle=new LegacyLifecycleAnalyzer().analyze(jarPath);Map<String,String> tileIds=registeredTiles(lifecycle);
        List<Rule> rules=new ArrayList<>();List<Skipped> skipped=new ArrayList<>();LinkedHashSet<String> diagnostics=new LinkedHashSet<>();
        diagnostics.addAll(registry.diagnostics());diagnostics.addAll(lifecycle.diagnostics());

        for(var registration:registry.blocks()){
            String block=registration.implementationClass();if(block==null||!inherits(classes,block,BLOCK_CONTAINER))continue;
            MethodNode create=effective(classes,block,Set.of("createNewTileEntity","func_149915_a"),
                    "(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;");
            String tile=uniqueCreatedType(create);if(tile==null||!tileIds.containsKey(tile)||!inherits(classes,tile,TILE))continue;
            String renderer=findRendererRegistration(classes,tile,tileIds.get(tile));if(renderer==null)continue;
            ClassNode rendererNode=classes.get(renderer);String modelClass=rendererModel(rendererNode,classes);
            if(modelClass==null){skipped.add(new Skipped(registration.registryName(),block,"TESR has no unique source ModelBase allocation"));continue;}
            ClassNode model=classes.get(modelClass);if(model==null)continue;
            String texture=singleTexture(rendererNode);if(texture==null){skipped.add(new Skipped(registration.registryName(),block,"TESR texture is missing or ambiguous"));continue;}

            MethodNode world=worldRender(rendererNode,tile,modelClass);
            if(world==null||!rootTransform(world,tile,modelClass)){
                skipped.add(new Skipped(registration.registryName(),block,"TESR root metadata/yaw transform is outside the admitted quadrant family"));continue;
            }
            String baseMethod=unconditionalBaseMethod(world,modelClass);if(baseMethod==null){
                skipped.add(new Skipped(registration.registryName(),block,"TESR has no unique unconditional source-model draw before state branches"));continue;
            }
            MethodNode base=ownMethod(model,baseMethod,"()V");RadialMethod radial=radialMethod(base,model.name);
            if(radial==null){skipped.add(new Skipped(registration.registryName(),block,"Unconditional model draw is not a bounded repeated radial cuboid"));continue;}

            Map<String,Box> boxes=boxes(model);Map<String,Pivot> pivots=pivots(model);Box box=boxes.get(radial.field());Pivot pivot=pivots.get(radial.field());
            if(box==null||pivot==null){skipped.add(new Skipped(registration.registryName(),block,"Radial ModelRenderer cuboid/pivot constructor proof is incomplete"));continue;}
            Map<String,ArrayFormula> formulas=arrayFormulas(model);
            ArrayFormula xf=formulas.get(radial.xArray()),yf=formulas.get(radial.yArray());
            if(xf==null||yf==null||xf.evenRadians()==null||xf.oddRadians()==null||yf.perIndexRadians()==0D){
                skipped.add(new Skipped(registration.registryName(),block,"Radial rotation arrays are not source-proven constant index formulas"));continue;}
            List<Pose> poses=new ArrayList<>();
            for(int i=0;i<radial.count();i++)poses.add(new Pose((i&1)==0?xf.evenRadians():xf.oddRadians(),
                    (float)(yf.perIndexRadians()*i),0F));

            Float light=sourceLightLevel(classes,block);if(light==null||light<0F||light>1F||!directYawPlacement(effective(classes,block,
                    Set.of("onBlockPlacedBy","func_149689_a"),
                    "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;)V"))){
                skipped.add(new Skipped(registration.registryName(),block,"Block light/orientation source contract is incomplete"));continue;
            }
            float[] inventory=inventoryTransform(rendererNode,modelClass,baseMethod);if(inventory==null){
                skipped.add(new Skipped(registration.registryName(),block,"TESR inventory transform for the unconditional radial base is unresolved"));continue;
            }
            int[] textureSize=modelTexture(model);int conditional=countConditionalModelCalls(world,modelClass,baseMethod);
            Cuboid cuboid=new Cuboid(box.u(),box.v(),box.x(),box.y(),box.z(),box.width(),box.height(),box.depth(),pivot.x(),pivot.y(),pivot.z());
            rules.add(new Rule(registration.registryName(),block,tile,tileIds.get(tile),renderer,modelClass,texture,
                    textureSize[0],textureSize[1],cuboid,poses,radial.scale(),0.5F,0F,0.5F,3,90F,
                    light,Math.max(0,Math.min(15,(int)(15F*light))),inventory[0],inventory[1],conditional));
        }
        return new Analysis(rules,skipped,List.copyOf(diagnostics));
    }

    private static RadialMethod radialMethod(MethodNode method,String modelOwner){
        if(method==null)return null;List<AbstractInsnNode> code=real(method);String rendered=null,xArray=null,yArray=null;Float scale=null;Integer count=null;
        for(int i=0;i<code.size();i++){
            AbstractInsnNode insn=code.get(i);
            if(insn instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.IF_ICMPGE&&i>0){
                Integer value=integer(code.get(i-1));if(value!=null&&value>=2&&value<=16)count=value;
            }
            if(insn instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(MODEL_RENDERER)
                    &&(X_ROT.contains(put.name)||Y_ROT.contains(put.name))){
                String array=arrayOrigin(code,i);if(array==null)return null;
                if(X_ROT.contains(put.name))xArray=array;else yArray=array;
            }
            if(insn instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&call.owner.equals(MODEL_RENDERER)
                    &&Set.of("render","func_78785_a").contains(call.name)&&call.desc.equals("(F)V")){
                if(i<2||!(code.get(i-2) instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.GETFIELD||!field.owner.equals(modelOwner))return null;
                Float s=floatConstant(code.get(i-1));if(s==null||s<=0F)return null;
                if(rendered!=null&&!rendered.equals(field.name))return null;rendered=field.name;scale=s;
            }
        }
        return rendered!=null&&xArray!=null&&yArray!=null&&count!=null&&scale!=null?new RadialMethod(rendered,xArray,yArray,count,scale):null;
    }
    private static String arrayOrigin(List<AbstractInsnNode> code,int putIndex){
        for(int i=putIndex-1;i>=Math.max(0,putIndex-8);i--){
            if(code.get(i) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.desc.equals("[F"))return field.name;
            if(code.get(i) instanceof MethodInsnNode||code.get(i) instanceof JumpInsnNode)return null;
        }
        return null;
    }
    private static Map<String,ArrayFormula> arrayFormulas(ClassNode model){
        MethodNode ctor=ownMethod(model,"<init>","()V");if(ctor==null)return Map.of();List<AbstractInsnNode> code=real(ctor);
        Map<String,ArrayFormula> out=new LinkedHashMap<>();
        for(int i=0;i<code.size();i++)if(code.get(i).getOpcode()==Opcodes.FASTORE){
            String field=null;for(int j=i-1;j>=Math.max(0,i-20);j--){
                if(code.get(j) instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&f.owner.equals(model.name)&&f.desc.equals("[F")){field=f.name;break;}
            }
            if(field==null)continue;List<Float> floats=new ArrayList<>();Double factor=null;boolean rem2=false,div180=false,dmul=false;
            for(int j=Math.max(0,i-18);j<i;j++){
                AbstractInsnNode n=code.get(j);Float fv=floatConstant(n);if(fv!=null)floats.add(fv);
                if(n instanceof LdcInsnNode ldc&&ldc.cst instanceof Double d&&Double.isFinite(d))factor=d;
                if(n.getOpcode()==Opcodes.IREM&&j>0&&Integer.valueOf(2).equals(integer(code.get(j-1))))rem2=true;
                if(n.getOpcode()==Opcodes.DMUL)dmul=true;
                if(n.getOpcode()==Opcodes.FDIV&&j>0){Float v=floatConstant(code.get(j-1));if(v!=null&&Float.compare(v,180F)==0)div180=true;}
            }
            ArrayFormula prior=out.get(field);double per=prior==null?0D:prior.perIndexRadians();Float even=prior==null?null:prior.evenRadians(),odd=prior==null?null:prior.oddRadians();
            if(factor!=null&&dmul&&div180)per=factor/180D;
            if(rem2){
                LinkedHashSet<Float> small=new LinkedHashSet<>();for(Float v:floats)if(v>0F&&v<1F)small.add(v);
                if(small.size()==2){Iterator<Float> it=small.iterator();even=it.next();odd=it.next();}
            }
            out.put(field,new ArrayFormula(field,per,even,odd));
        }
        return out;
    }

    private static String unconditionalBaseMethod(MethodNode world,String modelClass){
        String result=null;
        for(AbstractInsnNode insn=world.instructions.getFirst();insn!=null;insn=insn.getNext()){
            if(insn instanceof JumpInsnNode||insn instanceof TableSwitchInsnNode||insn instanceof LookupSwitchInsnNode)break;
            if(insn instanceof MethodInsnNode call&&call.owner.equals(modelClass)&&call.desc.equals("()V")){
                if(result!=null&&!result.equals(call.name))return null;result=call.name;
            }
        }
        return result;
    }
    private static int countConditionalModelCalls(MethodNode world,String modelClass,String base){
        int count=0;boolean branch=false;
        for(AbstractInsnNode insn:world.instructions){
            if(insn instanceof JumpInsnNode||insn instanceof TableSwitchInsnNode||insn instanceof LookupSwitchInsnNode)branch=true;
            if(branch&&insn instanceof MethodInsnNode call&&call.owner.equals(modelClass)&&!call.name.equals(base))count++;
        }
        return count;
    }
    private static MethodNode worldRender(ClassNode renderer,String tileClass,String modelClass){
        MethodNode found=null;
        for(MethodNode method:renderer.methods){
            boolean metadata=false,model=false,push=false;
            for(AbstractInsnNode insn:method.instructions){
                if(insn instanceof MethodInsnNode call){
                    if(call.owner.equals(tileClass)&&call.desc.equals("()I")&&Set.of("func_145832_p","getBlockMetadata").contains(call.name))metadata=true;
                    if(call.owner.equals(modelClass)&&(call.desc.equals("()V")||call.desc.equals("(I)V")))model=true;
                    if(call.owner.equals("org/lwjgl/opengl/GL11")&&call.name.equals("glPushMatrix"))push=true;
                }
            }
            if(metadata&&model&&push){if(found!=null)return null;found=method;}
        }
        return found;
    }
    private static boolean rootTransform(MethodNode method,String tileClass,String modelClass){
        boolean metadata=false,translate=false,rotate=false,mask=false,ninety=false;int half=0;
        for(AbstractInsnNode insn:method.instructions){
            if(insn instanceof MethodInsnNode call){
                if(call.owner.equals(tileClass)&&call.desc.equals("()I")&&Set.of("func_145832_p","getBlockMetadata").contains(call.name))metadata=true;
                if(call.owner.equals("org/lwjgl/opengl/GL11")&&call.name.equals("glTranslatef")&&call.desc.equals("(FFF)V"))translate=true;
                if(call.owner.equals("org/lwjgl/opengl/GL11")&&call.name.equals("glRotatef")&&call.desc.equals("(FFFF)V"))rotate=true;
            }
            if(insn.getOpcode()==Opcodes.IAND)mask=true;Float f=floatConstant(insn);if(f!=null){if(Float.compare(f,.5F)==0)half++;if(Float.compare(f,90F)==0)ninety=true;}
        }
        return metadata&&translate&&rotate&&mask&&half>=2&&ninety&&containsIntBeforeOpcode(method,3,Opcodes.IAND);
    }

    private static float[] inventoryTransform(ClassNode renderer,String modelClass,String baseMethod){
        for(MethodNode method:renderer.methods)if(method.desc.equals("()V")){
            boolean base=false,translate=false,scaleCall=false;List<Float> floats=new ArrayList<>();
            for(AbstractInsnNode insn:method.instructions){
                if(insn instanceof MethodInsnNode call){
                    if(call.owner.equals(modelClass)&&call.name.equals(baseMethod)&&call.desc.equals("()V"))base=true;
                    if(call.owner.equals("org/lwjgl/opengl/GL11")&&call.name.equals("glTranslatef"))translate=true;
                    if(call.owner.equals("org/lwjgl/opengl/GL11")&&call.name.equals("glScalef"))scaleCall=true;
                }
                Float f=floatConstant(insn);if(f!=null)floats.add(f);
            }
            if(!base||!translate||!scaleCall)continue;
            Float negative=null,scale=null;Map<Float,Integer> counts=new LinkedHashMap<>();
            for(Float f:floats){if(f<0F&&negative==null)negative=f;counts.merge(f,1,Integer::sum);}
            for(var e:counts.entrySet())if(e.getKey()>0F&&e.getValue()>=3){scale=e.getKey();break;}
            if(negative!=null&&scale!=null)return new float[]{negative,scale};
        }
        return null;
    }
    private static Float sourceLightLevel(Map<String,ClassNode> classes,String block){
        Set<Float> values=new LinkedHashSet<>();Set<String> seen=new HashSet<>();
        for(String current=block;current!=null&&seen.add(current);){
            ClassNode node=classes.get(current);if(node==null)break;
            for(MethodNode method:node.methods)if(method.name.equals("<init>"))for(AbstractInsnNode insn:method.instructions)
                if(insn instanceof MethodInsnNode call&&Set.of("setLightLevel","func_149715_a").contains(call.name)
                        &&call.desc.equals("(F)Lnet/minecraft/block/Block;")){
                    Float f=floatConstant(previousReal(insn));if(f!=null)values.add(f);
                }
            current=node.superName;
        }
        return values.size()==1?values.iterator().next():null;
    }
    private static boolean directYawPlacement(MethodNode method){
        if(method==null)return false;boolean yaw=false,four=false,threeSixty=false,half=false,floor=false,mask=false,set=false;
        List<AbstractInsnNode> code=real(method);
        for(int i=0;i<code.size();i++){
            AbstractInsnNode insn=code.get(i);
            if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD
                    &&field.owner.equals("net/minecraft/entity/EntityLivingBase")&&field.desc.equals("F")
                    &&Set.of("rotationYaw","field_70177_z").contains(field.name))yaw=true;
            Float f=floatConstant(insn);if(f!=null){if(Float.compare(f,4F)==0)four=true;if(Float.compare(f,360F)==0)threeSixty=true;}
            if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Double d&&Double.compare(d,.5D)==0)half=true;
            if(insn instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals("net/minecraft/util/MathHelper")
                    &&Set.of("floor_double","func_76128_c").contains(call.name)&&call.desc.equals("(D)I"))floor=true;
            if(insn.getOpcode()==Opcodes.IAND&&i>0&&Integer.valueOf(3).equals(integer(code.get(i-1))))mask=true;
            if(insn instanceof MethodInsnNode call&&call.owner.equals("net/minecraft/world/World")
                    &&Set.of("setBlockMetadataWithNotify","func_72921_c").contains(call.name)&&call.desc.equals("(IIIII)Z"))set=true;
        }
        return yaw&&four&&threeSixty&&half&&floor&&mask&&set;
    }

    private static int[] modelTexture(ClassNode model){
        int width=64,height=32;MethodNode ctor=ownMethod(model,"<init>","()V");List<AbstractInsnNode> code=real(ctor);
        for(int i=1;i<code.size();i++)if(code.get(i) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.desc.equals("I")){
            Integer value=integer(code.get(i-1));if(value==null||value<=0)continue;
            if(Set.of("field_78090_t","textureWidth").contains(field.name))width=value;
            if(Set.of("field_78089_u","textureHeight").contains(field.name))height=value;
        }
        return new int[]{width,height};
    }
    private static Map<String,Box> boxes(ClassNode model){
        MethodNode ctor=ownMethod(model,"<init>","()V");if(ctor==null)return Map.of();List<AbstractInsnNode> code=real(ctor);
        Map<String,int[]> uv=new LinkedHashMap<>();Map<String,Box> result=new LinkedHashMap<>();
        for(int i=2;i+1<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL
                &&call.owner.equals(MODEL_RENDERER)&&call.name.equals("<init>")&&call.desc.equals("(Lnet/minecraft/client/model/ModelBase;II)V")){
            Integer u=integer(code.get(i-2)),v=integer(code.get(i-1));
            if(u!=null&&v!=null&&code.get(i+1) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(model.name))uv.put(field.name,new int[]{u,v});
        }
        for(int i=7;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&call.owner.equals(MODEL_RENDERER)
                &&call.desc.equals("(FFFIII)Lnet/minecraft/client/model/ModelRenderer;")&&code.get(i-7) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(model.name)){
            Float x=floatConstant(code.get(i-6)),y=floatConstant(code.get(i-5)),z=floatConstant(code.get(i-4));Integer w=integer(code.get(i-3)),h=integer(code.get(i-2)),d=integer(code.get(i-1));int[] tex=uv.get(field.name);
            if(x!=null&&y!=null&&z!=null&&w!=null&&h!=null&&d!=null&&tex!=null)result.put(field.name,new Box(field.name,tex[0],tex[1],x,y,z,w,h,d));
        }
        return result;
    }
    private static Map<String,Pivot> pivots(ClassNode model){
        MethodNode ctor=ownMethod(model,"<init>","()V");if(ctor==null)return Map.of();List<AbstractInsnNode> code=real(ctor);Map<String,Pivot> result=new LinkedHashMap<>();
        for(int i=4;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&call.owner.equals(MODEL_RENDERER)
                &&call.desc.equals("(FFF)V")&&code.get(i-4) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(model.name)){
            Float x=floatConstant(code.get(i-3)),y=floatConstant(code.get(i-2)),z=floatConstant(code.get(i-1));if(x!=null&&y!=null&&z!=null)result.put(field.name,new Pivot(x,y,z));
        }
        return result;
    }

    private static String findRendererRegistration(Map<String,ClassNode> classes,String tileClass,String tileId){
        LinkedHashSet<String> renderers=new LinkedHashSet<>();
        for(ClassNode owner:classes.values())for(MethodNode method:owner.methods)for(AbstractInsnNode insn:method.instructions){
            if(!(insn instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESTATIC
                    ||!call.owner.equals("cpw/mods/fml/client/registry/ClientRegistry")||!call.name.equals("registerTileEntity")
                    ||!call.desc.equals("(Ljava/lang/Class;Ljava/lang/String;Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;)V"))continue;
            LegacyDirectCallArguments.ClassStringNew args=LegacyDirectCallArguments.classStringNew(owner,method,call);
            if(args!=null&&args.classInternalName().equals(tileClass)&&args.stringValue().equals(tileId)&&inherits(classes,args.newTypeInternalName(),TESR))
                renderers.add(args.newTypeInternalName());
        }
        return renderers.size()==1?renderers.getFirst():null;
    }
    private static String rendererModel(ClassNode renderer,Map<String,ClassNode> classes){
        LinkedHashSet<String> models=new LinkedHashSet<>();if(renderer==null)return null;
        for(MethodNode method:renderer.methods)for(AbstractInsnNode insn:method.instructions)
            if(insn instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW&&inherits(classes,type.desc,MODEL_BASE))models.add(type.desc);
        return models.size()==1?models.getFirst():null;
    }
    private static String singleTexture(ClassNode renderer){
        LinkedHashSet<String> values=new LinkedHashSet<>();if(renderer==null)return null;
        for(MethodNode method:renderer.methods)for(AbstractInsnNode insn:method.instructions)
            if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof String text&&text.indexOf(':')>0&&text.contains("textures/")&&text.endsWith(".png"))values.add(text);
        return values.size()==1?values.getFirst():null;
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
    private static MethodNode effective(Map<String,ClassNode> classes,String owner,Set<String> names,String desc){
        Set<String> seen=new HashSet<>();while(owner!=null&&seen.add(owner)){ClassNode node=classes.get(owner);if(node==null)return null;
            for(MethodNode method:node.methods)if(names.contains(method.name)&&desc.equals(method.desc))return method;owner=node.superName;}return null;
    }
    private static MethodNode ownMethod(ClassNode node,String name,String desc){if(node==null)return null;for(MethodNode m:node.methods)if(m.name.equals(name)&&m.desc.equals(desc))return m;return null;}
    private static boolean inherits(Map<String,ClassNode> classes,String owner,String target){Set<String> seen=new HashSet<>();while(owner!=null&&seen.add(owner)){if(owner.equals(target))return true;ClassNode n=classes.get(owner);owner=n==null?null:n.superName;}return false;}
    private static boolean containsIntBeforeOpcode(MethodNode method,int value,int opcode){List<AbstractInsnNode> code=real(method);for(int i=1;i<code.size();i++)if(code.get(i).getOpcode()==opcode&&Integer.valueOf(value).equals(integer(code.get(i-1))))return true;return false;}
    private static AbstractInsnNode previousReal(AbstractInsnNode insn){for(AbstractInsnNode c=insn==null?null:insn.getPrevious();c!=null;c=c.getPrevious())if(c.getOpcode()>=0)return c;return null;}
    private static Integer integer(AbstractInsnNode insn){if(insn==null)return null;return switch(insn.getOpcode()){case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer v?v:null;default->null;};}
    private static Float floatConstant(AbstractInsnNode insn){if(insn==null)return null;return switch(insn.getOpcode()){case Opcodes.FCONST_0->0F;case Opcodes.FCONST_1->1F;case Opcodes.FCONST_2->2F;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Float v?v:null;default->null;};}
    private static List<AbstractInsnNode> real(MethodNode method){List<AbstractInsnNode> out=new ArrayList<>();if(method!=null)for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)out.add(insn);return out;}
    private static Map<String,ClassNode> load(Path jarPath)throws IOException{
        Map<String,ClassNode> out=new LinkedHashMap<>();try(JarFile jar=new JarFile(jarPath.toFile(),false)){var entries=jar.entries();while(entries.hasMoreElements()){
            JarEntry e=entries.nextElement();if(e.isDirectory()||!e.getName().endsWith(".class")||e.getName().equals("module-info.class"))continue;
            try(InputStream in=jar.getInputStream(e)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(in).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);out.put(node.name,node);}catch(RuntimeException ignored){}
        }}return out;
    }
}
