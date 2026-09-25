package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

import static dev.yinghuang.legacyforgebridge.convert.LegacySeatBedAsm.*;

/**
 * Fail-closed presentation proof for the special two-part legacy bed/seat family.
 *
 * <p>The analyzer never instantiates source classes. It admits only a bounded 1.7 TESR shape:
 * one lifecycle-bound renderer for the proven TileEntity, one fixed ModelBase with four fixed
 * ModelRenderer cuboids, two disjoint render groups selected by the helper's boolean branch,
 * a source-proven four-way metadata transform, two source ResourceLocation textures whose PNG
 * dimensions match the model baseline, and an expanded source render bounding box.</p>
 */
public final class LegacySeatBedPresentationAnalyzer {
    private static final String CLIENT_REGISTRY="cpw/mods/fml/client/registry/ClientRegistry";
    private static final String TESR="net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer";
    private static final String TILE="net/minecraft/tileentity/TileEntity";
    private static final String MODEL_BASE="net/minecraft/client/model/ModelBase";
    private static final String MODEL_RENDERER="net/minecraft/client/model/ModelRenderer";
    private static final String RESOURCE="net/minecraft/util/ResourceLocation";
    private static final String GL11="org/lwjgl/opengl/GL11";
    private static final String AABB="net/minecraft/util/AxisAlignedBB";

    public record Part(String field,int u,int v,float x,float y,float z,int width,int height,int depth,
                       float pivotX,float pivotY,float pivotZ,float xRot,float yRot,float zRot,boolean mirror) {
        public Part {
            if(field==null||field.isBlank()||u<0||v<0||width<=0||height<=0||depth<=0
                    ||!finite(x,y,z,pivotX,pivotY,pivotZ,xRot,yRot,zRot)) throw new IllegalArgumentException("Invalid seat-bed model part");
        }
    }
    public record Presentation(String sourceRendererClass,String sourceModelClass,String footTexture,String headTexture,
                               int imageWidth,int imageHeight,int modelTextureWidth,int modelTextureHeight,float modelScale,
                               List<Part> parts,List<String> footParts,List<String> headParts,
                               List<Float> translateXByDirection,List<Float> translateZByDirection,List<Float> yawDegreesByDirection,
                               boolean expandedRenderBoundsProven) {
        public Presentation {
            parts=List.copyOf(parts);footParts=List.copyOf(footParts);headParts=List.copyOf(headParts);
            translateXByDirection=List.copyOf(translateXByDirection);translateZByDirection=List.copyOf(translateZByDirection);yawDegreesByDirection=List.copyOf(yawDegreesByDirection);
            if(sourceRendererClass==null||sourceRendererClass.isBlank()||sourceModelClass==null||sourceModelClass.isBlank()
                    ||footTexture==null||footTexture.isBlank()||headTexture==null||headTexture.isBlank()||footTexture.equals(headTexture)
                    ||imageWidth<=0||imageHeight<=0||modelTextureWidth<=0||modelTextureHeight<=0||Float.compare(modelScale,0.0625F)!=0
                    ||parts.size()!=4||footParts.size()!=2||headParts.size()!=2||translateXByDirection.size()!=4||translateZByDirection.size()!=4||yawDegreesByDirection.size()!=4
                    ||!expandedRenderBoundsProven) throw new IllegalArgumentException("Invalid seat-bed presentation");
            LinkedHashSet<String> fields=new LinkedHashSet<>();parts.forEach(p->fields.add(p.field()));
            LinkedHashSet<String> rendered=new LinkedHashSet<>(footParts);if(rendered.size()!=2||!Collections.disjoint(rendered,headParts))throw new IllegalArgumentException("Overlapping seat-bed render groups");
            rendered.addAll(headParts);if(!rendered.equals(fields))throw new IllegalArgumentException("Seat-bed render groups do not cover all parts");
        }
    }
    public record Analysis(Optional<Presentation> presentation,List<String> diagnostics) {
        public Analysis { presentation=presentation==null?Optional.empty():presentation;diagnostics=List.copyOf(diagnostics); }
    }

    public Analysis analyze(Path jarPath, LegacySeatBedAnalyzer.Rule sourceRule) throws IOException {
        Objects.requireNonNull(jarPath,"jarPath");Objects.requireNonNull(sourceRule,"sourceRule");
        Map<String,ClassNode> classes=loadClasses(jarPath);List<String> diagnostics=new ArrayList<>();
        String renderer=findRendererBinding(classes,sourceRule.sourceTileClass());
        if(renderer==null){diagnostics.add("seat-bed TileEntitySpecialRenderer registration is not uniquely source-proven");return new Analysis(Optional.empty(),diagnostics);}
        ClassNode rendererNode=classes.get(renderer);
        if(rendererNode==null||!inherits(classes,renderer,TESR)){diagnostics.add("seat-bed renderer is not a source-defined TileEntitySpecialRenderer");return new Analysis(Optional.empty(),diagnostics);}
        String model=uniqueModel(rendererNode,classes);
        if(model==null){diagnostics.add("seat-bed renderer does not construct exactly one source ModelBase");return new Analysis(Optional.empty(),diagnostics);}
        ClassNode modelNode=classes.get(model);
        if(modelNode==null){diagnostics.add("seat-bed model class is unavailable");return new Analysis(Optional.empty(),diagnostics);}

        RenderProof render=parseRendererProof(rendererNode,model);
        if(render==null){diagnostics.add("seat-bed TESR does not prove the bounded two-group/two-texture/four-way transform shape");return new Analysis(Optional.empty(),diagnostics);}
        Map<String,String> textures=textureFields(rendererNode);
        String footTexture=textures.get(render.footTextureField()),headTexture=textures.get(render.headTextureField());
        if(footTexture==null||headTexture==null||footTexture.equals(headTexture)){diagnostics.add("seat-bed TESR textures are not uniquely source-proven");return new Analysis(Optional.empty(),diagnostics);}
        int[] footSize=pngSize(jarPath,footTexture),headSize=pngSize(jarPath,headTexture);
        if(footSize==null||headSize==null||footSize[0]!=headSize[0]||footSize[1]!=headSize[1]){diagnostics.add("seat-bed source textures are missing or have incompatible dimensions");return new Analysis(Optional.empty(),diagnostics);}

        ModelProof modelProof=parseModel(modelNode,render.footMethod(),render.headMethod());
        if(modelProof==null){diagnostics.add("seat-bed ModelBase is not the admitted four-fixed-cuboid/two-disjoint-group shape");return new Analysis(Optional.empty(),diagnostics);}
        if(footSize[0]!=modelProof.textureWidth()||footSize[1]!=modelProof.textureHeight()){
            diagnostics.add("seat-bed texture dimensions do not match the source model baseline");return new Analysis(Optional.empty(),diagnostics);
        }
        boolean expanded=expandedBounds(classes.get(sourceRule.sourceTileClass()));
        if(!expanded){diagnostics.add("seat-bed expanded TileEntity render bounds are not source-proven");return new Analysis(Optional.empty(),diagnostics);}

        float[][] offsets=render.offsets();int[] remap=render.directionRemap();List<Float> tx=new ArrayList<>(),tz=new ArrayList<>(),yaw=new ArrayList<>();
        for(int source=0;source<4;source++){int d=remap[source];tx.add(offsets[d][0]);tz.add(offsets[d][1]);yaw.add(d*90F);}
        return new Analysis(Optional.of(new Presentation(renderer,model,footTexture,headTexture,footSize[0],footSize[1],modelProof.textureWidth(),modelProof.textureHeight(),.0625F,modelProof.parts(),modelProof.footParts(),modelProof.headParts(),tx,tz,yaw,true)),diagnostics);
    }

    private record RenderProof(String footMethod,String headMethod,String footTextureField,String headTextureField,float[][] offsets,int[] directionRemap) { }
    private record ModelProof(int textureWidth,int textureHeight,List<Part> parts,List<String> footParts,List<String> headParts) { }

    private static String findRendererBinding(Map<String,ClassNode> classes,String tile){LinkedHashSet<String> found=new LinkedHashSet<>();for(ClassNode node:classes.values())for(MethodNode method:node.methods){List<AbstractInsnNode> code=real(method);for(int i=0;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&CLIENT_REGISTRY.equals(call.owner)&&Set.of("bindTileEntitySpecialRenderer","registerTileEntity").contains(call.name)){String renderer=rendererFromRegistration(code,i,call,tile);if(renderer!=null)found.add(renderer);}}return found.size()==1?found.getFirst():null;}
    private static String rendererFromRegistration(List<AbstractInsnNode> code,int index,MethodInsnNode call,String tile){if("bindTileEntitySpecialRenderer".equals(call.name)&&"(Ljava/lang/Class;Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;)V".equals(call.desc)){if(index<4)return null;String type=classConst(code.get(index-4));String renderer=newType(code.get(index-3));if(type==null||renderer==null||!tile.equals(type)||code.get(index-2).getOpcode()!=Opcodes.DUP)return null;return renderer;}if("registerTileEntity".equals(call.name)&&"(Ljava/lang/Class;Ljava/lang/String;Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;)V".equals(call.desc)){if(index<5)return null;String type=classConst(code.get(index-5));String renderer=newType(code.get(index-3));if(type==null||renderer==null||!tile.equals(type)||!(code.get(index-4) instanceof LdcInsnNode l)||!(l.cst instanceof String)||code.get(index-2).getOpcode()!=Opcodes.DUP)return null;return renderer;}return null;}
    private static String uniqueModel(ClassNode renderer,Map<String,ClassNode> classes){LinkedHashSet<String> models=new LinkedHashSet<>();for(MethodNode method:renderer.methods)for(AbstractInsnNode insn:method.instructions)if(insn instanceof TypeInsnNode t&&t.getOpcode()==Opcodes.NEW&&inherits(classes,t.desc,MODEL_BASE))models.add(t.desc);return models.size()==1?models.getFirst():null;}

    private static RenderProof parseRendererProof(ClassNode renderer,String model){
        List<MethodNode> helperCandidates=renderer.methods.stream().filter(m->m.desc.endsWith("Z)V")&&callsOwner(m,model)).toList();
        if(helperCandidates.size()!=1)return null;MethodNode helper=helperCandidates.getFirst();int booleanLocal=booleanArgumentLocal(helper);if(booleanLocal<0)return null;
        MethodNode main=null;for(MethodNode candidate:renderer.methods){if(candidate==helper||!candidate.desc.startsWith("(L")||!candidate.desc.endsWith(";DDDF)V"))continue;List<Boolean> flags=helperCallFlags(all(candidate),renderer.name,helper);if(flags.size()==2&&flags.contains(Boolean.TRUE)&&flags.contains(Boolean.FALSE)){if(main!=null)return null;main=candidate;}}
        if(main==null)return null;List<AbstractInsnNode> hc=all(helper);
        if(!containsFloat(hc,.0625F)||!callsName(hc,GL11,"glPushMatrix")||!callsName(hc,GL11,"glPopMatrix")||!callsName(hc,GL11,"glTranslatef")||!callsName(hc,GL11,"glRotatef")||!containsFloat(hc,90F))return null;
        RenderProof proof=null;
        for(int i=0;i<hc.size();i++)if(hc.get(i) instanceof JumpInsnNode j&&(j.getOpcode()==Opcodes.IFEQ||j.getOpcode()==Opcodes.IFNE)&&loadsLocal(previousReal(hc,i),booleanLocal)){
            int target=hc.indexOf(j.label);if(target<0||target<=i)continue;BranchProof fall=branchRange(hc,i+1,target,renderer.name,model,booleanLocal),taken=branchRange(hc,target+1,hc.size(),renderer.name,model,booleanLocal);if(fall==null||taken==null||fall.modelMethod().equals(taken.modelMethod()))continue;
            boolean fallValue=j.getOpcode()==Opcodes.IFEQ;String fallTexture=resolveTexture(renderer,fall,fallValue),takenTexture=resolveTexture(renderer,taken,!fallValue);if(fallTexture==null||takenTexture==null||fallTexture.equals(takenTexture))continue;
            int[] remap=directionRemap(hc);float[][] offsets=offsetTable(renderer);if(remap==null||offsets==null)return null;RenderProof candidate=new RenderProof(fall.modelMethod(),taken.modelMethod(),fallTexture,takenTexture,offsets,remap);if(proof!=null&&!proof.equals(candidate))return null;proof=candidate;
        }
        return proof;
    }
    private record BranchProof(String textureField,String textureSelectorMethod,String modelMethod) { }
    private static BranchProof branchRange(List<AbstractInsnNode> code,int start,int end,String renderer,String model,int booleanLocal){LinkedHashSet<String> textures=new LinkedHashSet<>(),selectors=new LinkedHashSet<>(),models=new LinkedHashSet<>();boolean directBind=false;for(int i=Math.max(0,start);i<Math.min(code.size(),end);i++){AbstractInsnNode n=code.get(i);if(n instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETSTATIC&&renderer.equals(f.owner)&&("L"+RESOURCE+";").equals(f.desc))textures.add(f.name);if(n instanceof MethodInsnNode c){if(model.equals(c.owner)&&"(F)V".equals(c.desc))models.add(c.name);if(renderer.equals(c.owner)&&"(Z)V".equals(c.desc)&&loadsLocal(previousReal(code,i),booleanLocal))selectors.add(c.name);if("(Lnet/minecraft/util/ResourceLocation;)V".equals(c.desc)&&(TESR.equals(c.owner)||renderer.equals(c.owner)))directBind=true;}}if(models.size()!=1||textures.size()>1||selectors.size()>1)return null;String texture=textures.size()==1&&directBind?textures.getFirst():null;String selector=selectors.size()==1?selectors.getFirst():null;if(texture!=null&&selector!=null)return null;return new BranchProof(texture,selector,models.getFirst());}
    private static String resolveTexture(ClassNode renderer,BranchProof branch,boolean value){if(branch.textureField()!=null)return branch.textureField();return branch.textureSelectorMethod()==null?null:textureFromSelector(renderer,branch.textureSelectorMethod(),value);}
    private static String textureFromSelector(ClassNode renderer,String methodName,boolean value){MethodNode selector=ownMethod(renderer,Set.of(methodName),"(Z)V");if(selector==null)return null;int local=booleanArgumentLocal(selector);if(local<0)return null;List<AbstractInsnNode> code=all(selector);String resolved=null;for(int i=0;i<code.size();i++)if(code.get(i) instanceof JumpInsnNode j&&(j.getOpcode()==Opcodes.IFEQ||j.getOpcode()==Opcodes.IFNE)&&loadsLocal(previousReal(code,i),local)){int target=code.indexOf(j.label);if(target<0||target<=i)continue;String fall=textureFieldInRange(code,i+1,target,renderer),taken=textureFieldInRange(code,target+1,code.size(),renderer);if(fall==null||taken==null||fall.equals(taken))continue;boolean fallValue=j.getOpcode()==Opcodes.IFEQ;String candidate=value==fallValue?fall:taken;if(resolved!=null&&!resolved.equals(candidate))return null;resolved=candidate;}return resolved;}
    private static String textureFieldInRange(List<AbstractInsnNode> code,int start,int end,ClassNode renderer){LinkedHashSet<String> fields=new LinkedHashSet<>();boolean bind=false;for(int i=Math.max(0,start);i<Math.min(code.size(),end);i++){AbstractInsnNode n=code.get(i);if(n instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETSTATIC&&renderer.name.equals(f.owner)&&("L"+RESOURCE+";").equals(f.desc))fields.add(f.name);if(n instanceof MethodInsnNode c&&"(Lnet/minecraft/util/ResourceLocation;)V".equals(c.desc)&&(TESR.equals(c.owner)||renderer.name.equals(c.owner)||Objects.equals(renderer.superName,c.owner)))bind=true;}return fields.size()==1&&bind?fields.getFirst():null;}
    private static List<Boolean> helperCallFlags(List<AbstractInsnNode> code,String renderer,MethodNode helper){List<Boolean> out=new ArrayList<>();for(int i=0;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode c&&renderer.equals(c.owner)&&helper.name.equals(c.name)&&helper.desc.equals(c.desc)){Integer v=intConst(previousReal(code,i));if(v==null||(v!=0&&v!=1))return List.of();out.add(v==1);}return out;}
    private static int booleanArgumentLocal(MethodNode method){Type[] args=Type.getArgumentTypes(method.desc);if(args.length==0||args[args.length-1].getSort()!=Type.BOOLEAN)return -1;int local=(method.access&Opcodes.ACC_STATIC)==0?1:0;for(int i=0;i<args.length-1;i++)local+=args[i].getSize();return local;}
    private static boolean loadsLocal(AbstractInsnNode node,int local){return node instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ILOAD&&v.var==local;}
    private static AbstractInsnNode previousReal(List<AbstractInsnNode> code,int index){for(int i=index-1;i>=0;i--)if(code.get(i).getOpcode()>=0)return code.get(i);return null;}
    private static List<AbstractInsnNode> all(MethodNode method){List<AbstractInsnNode> out=new ArrayList<>();if(method!=null)for(AbstractInsnNode i:method.instructions)out.add(i);return out;}

    private static int[] directionRemap(List<AbstractInsnNode> code){for(int i=0;i<code.size();i++)if(code.get(i) instanceof TableSwitchInsnNode ts&&ts.min==0&&ts.max==3&&ts.labels.size()==4){int[] r=new int[4];boolean ok=true;for(int s=0;s<4;s++){int idx=code.indexOf(ts.labels.get(s));Integer v=null;if(idx<0){ok=false;break;}for(int j=idx+1;j<Math.min(code.size(),idx+8);j++){v=intConst(code.get(j));if(v!=null)break;}if(v==null||v<0||v>3){ok=false;break;}r[s]=v;}if(ok&&new LinkedHashSet<>(Arrays.stream(r).boxed().toList()).size()==4)return r;}return null;}
    private static float[][] offsetTable(ClassNode renderer){MethodNode clinit=null;for(MethodNode m:renderer.methods)if("<clinit>".equals(m.name)&&"()V".equals(m.desc)){clinit=m;break;}if(clinit==null)return null;List<AbstractInsnNode> c=real(clinit);List<Float> floats=new ArrayList<>();for(AbstractInsnNode n:c){Float f=floatConst(n);if(f!=null&&(Float.compare(f,0F)==0||Float.compare(f,.5F)==0||Float.compare(f,1F)==0))floats.add(f);}if(floats.size()<8)return null;for(int i=0;i+7<floats.size();i++){float[][] table={{floats.get(i),floats.get(i+1)},{floats.get(i+2),floats.get(i+3)},{floats.get(i+4),floats.get(i+5)},{floats.get(i+6),floats.get(i+7)}};Set<String> rows=new LinkedHashSet<>();for(float[] row:table)rows.add(row[0]+":"+row[1]);if(rows.size()==4)return table;}return null;}
    private static Map<String,String> textureFields(ClassNode renderer){Map<String,String> out=new LinkedHashMap<>();MethodNode clinit=null;for(MethodNode m:renderer.methods)if("<clinit>".equals(m.name)&&"()V".equals(m.desc)){clinit=m;break;}if(clinit==null)return out;List<AbstractInsnNode> code=real(clinit);for(int i=4;i<code.size();i++)if(code.get(i) instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.PUTSTATIC&&renderer.name.equals(f.owner)&&("L"+RESOURCE+";").equals(f.desc)){
            if(!(code.get(i-1) instanceof MethodInsnNode c)||c.getOpcode()!=Opcodes.INVOKESPECIAL||!RESOURCE.equals(c.owner)||!"<init>".equals(c.name)||!"(Ljava/lang/String;)V".equals(c.desc))continue;if(code.get(i-3).getOpcode()!=Opcodes.DUP||!(code.get(i-2) instanceof LdcInsnNode l)||!(l.cst instanceof String s))continue;out.put(f.name,s);
        }return out;}

    private static ModelProof parseModel(ClassNode model,String footMethod,String headMethod){List<FieldNode> fields=model.fields.stream().filter(f->("L"+MODEL_RENDERER+";").equals(f.desc)&&(f.access&Opcodes.ACC_STATIC)==0).toList();if(fields.size()!=4)return null;MethodNode ctor=ownMethod(model,Set.of("<init>"),"()V");if(ctor==null)return null;Map<String,MutablePart> parts=new LinkedHashMap<>();for(FieldNode f:fields)parts.put(f.name,new MutablePart(f.name));List<AbstractInsnNode> code=real(ctor);
        for(int i=0;i<code.size();i++){
            AbstractInsnNode insn=code.get(i);
            if(insn instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL&&MODEL_RENDERER.equals(call.owner)&&"<init>".equals(call.name)&&"(Lnet/minecraft/client/model/ModelBase;II)V".equals(call.desc)&&i>=3&&i+1<code.size()&&code.get(i+1) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&model.name.equals(put.owner)&&parts.containsKey(put.name)){
                Integer u=intConst(code.get(i-2)),v=intConst(code.get(i-1));if(u==null||v==null)return null;parts.get(put.name).u=u;parts.get(put.name).v=v;
            }
            if(insn instanceof MethodInsnNode call&&MODEL_RENDERER.equals(call.owner)&&"(FFFIII)Lnet/minecraft/client/model/ModelRenderer;".equals(call.desc)&&i>=7){FieldInsnNode recv=getField(code.get(i-7),model.name);if(recv==null||!parts.containsKey(recv.name))continue;Float x=floatConst(code.get(i-6)),y=floatConst(code.get(i-5)),z=floatConst(code.get(i-4));Integer w=intConst(code.get(i-3)),h=intConst(code.get(i-2)),d=intConst(code.get(i-1));if(x==null||y==null||z==null||w==null||h==null||d==null)return null;MutablePart p=parts.get(recv.name);if(p.box)return null;p.x=x;p.y=y;p.z=z;p.w=w;p.h=h;p.d=d;p.box=true;}
            if(insn instanceof MethodInsnNode call&&MODEL_RENDERER.equals(call.owner)&&"(FFF)V".equals(call.desc)&&i>=4){FieldInsnNode recv=getField(code.get(i-4),model.name);if(recv==null||!parts.containsKey(recv.name))continue;Float x=floatConst(code.get(i-3)),y=floatConst(code.get(i-2)),z=floatConst(code.get(i-1));if(x==null||y==null||z==null)return null;MutablePart p=parts.get(recv.name);p.px=x;p.py=y;p.pz=z;}
            if(insn instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&MODEL_RENDERER.equals(put.owner)&&"F".equals(put.desc)&&i>=2){FieldInsnNode recv=getField(code.get(i-2),model.name);Float value=floatConst(code.get(i-1));if(recv==null||value==null||!parts.containsKey(recv.name))continue;MutablePart p=parts.get(recv.name);switch(put.name){case "rotateAngleX","field_78795_f"->p.rx=value;case "rotateAngleY","field_78796_g"->p.ry=value;case "rotateAngleZ","field_78808_h"->p.rz=value;default->{return null;}}}
        }
        List<Part> built=new ArrayList<>();for(MutablePart p:parts.values()){if(p.u==null||p.v==null||!p.box)return null;built.add(p.build());}
        List<String> foot=renderFields(ownMethod(model,Set.of(footMethod),"(F)V"),model.name),head=renderFields(ownMethod(model,Set.of(headMethod),"(F)V"),model.name);if(foot.size()!=2||head.size()!=2||!Collections.disjoint(foot,head))return null;LinkedHashSet<String> union=new LinkedHashSet<>(foot);union.addAll(head);if(!union.equals(new LinkedHashSet<>(parts.keySet())))return null;
        // ModelBase's unmodified 1.7 baseline is 64x32. If source writes those fields, this bounded family is rejected rather than guessed.
        if(writesModelTextureSize(ctor))return null;return new ModelProof(64,32,built,foot,head);}

    private static boolean writesModelTextureSize(MethodNode ctor){for(AbstractInsnNode i:ctor.instructions)if(i instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.PUTFIELD&&MODEL_BASE.equals(f.owner)&&(f.name.equals("textureWidth")||f.name.equals("textureHeight")||f.name.equals("field_78090_t")||f.name.equals("field_78089_u")))return true;return false;}
    private static List<String> renderFields(MethodNode method,String model){if(method==null)return List.of();List<AbstractInsnNode> code=real(method);List<String> out=new ArrayList<>();for(int i=1;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&MODEL_RENDERER.equals(call.owner)&&"(F)V".equals(call.desc)){FieldInsnNode recv=getField(code.get(i-2),model);if(recv==null)return List.of();out.add(recv.name);}return List.copyOf(new LinkedHashSet<>(out));}
    private static FieldInsnNode getField(AbstractInsnNode i,String owner){return i instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&owner.equals(f.owner)&&("L"+MODEL_RENDERER+";").equals(f.desc)?f:null;}

    private static final class MutablePart { final String field;Integer u,v;float x,y,z,px,py,pz,rx,ry,rz;int w,h,d;boolean box;MutablePart(String f){field=f;}Part build(){return new Part(field,u,v,x,y,z,w,h,d,px,py,pz,rx,ry,rz,false);} }

    private static boolean expandedBounds(ClassNode tile){if(tile==null)return false;MethodNode m=ownMethod(tile,Set.of("getRenderBoundingBox"),"()Lnet/minecraft/util/AxisAlignedBB;");if(m==null||!callsName(m,AABB,Set.of("getBoundingBox","func_72330_a"),"(DDDDDD)Lnet/minecraft/util/AxisAlignedBB;"))return false;List<AbstractInsnNode> c=real(m);int coordReads=0;for(AbstractInsnNode i:c)if(i instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&tile.name.equals(f.owner)&&"I".equals(f.desc))coordReads++;return coordReads>=6&&containsInt(m,1)&&containsInt(m,2)&&containsFloat(m,.25F)&&containsOpcode(m,Opcodes.ISUB)&&containsOpcode(m,Opcodes.IADD)&&containsOpcode(m,Opcodes.FADD);}

    private static int[] pngSize(Path jarPath,String resource){int colon=resource.indexOf(':');if(colon<=0||colon==resource.length()-1)return null;String entry="assets/"+resource.substring(0,colon)+"/"+resource.substring(colon+1);try(JarFile jar=new JarFile(jarPath.toFile(),false)){var e=jar.getJarEntry(entry);if(e==null)return null;try(var in=jar.getInputStream(e)){var image=ImageIO.read(in);return image==null?null:new int[]{image.getWidth(),image.getHeight()};}}catch(IOException invalid){return null;}}
    private static Integer intConst(AbstractInsnNode i){if(i==null)return null;return switch(i.getOpcode()){case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)i).operand;case Opcodes.LDC->i instanceof LdcInsnNode l&&l.cst instanceof Integer v?v:null;default->null;};}
    private static Float floatConst(AbstractInsnNode i){if(i==null)return null;return switch(i.getOpcode()){case Opcodes.FCONST_0->0F;case Opcodes.FCONST_1->1F;case Opcodes.FCONST_2->2F;case Opcodes.LDC->i instanceof LdcInsnNode l&&l.cst instanceof Float v?v:null;default->null;};}
    private static boolean finite(float... vs){for(float v:vs)if(!Float.isFinite(v))return false;return true;}
}
