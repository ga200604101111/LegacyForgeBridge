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
 * Fail-closed world-presentation proof for a decorative legacy model made from fixed cuboids plus
 * exactly one X-axis joint driven by the source TileEntity's already-proven oscillating angle.
 * Inventory ownership/rendering is intentionally a later, independent gate.
 */
public final class LegacyOscillatingModelPresentationAnalyzer {
    private static final String TESR="net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer";
    private static final String MODEL_BASE="net/minecraft/client/model/ModelBase";
    private static final String MODEL_RENDERER="net/minecraft/client/model/ModelRenderer";
    private static final Set<String> ROT_X=Set.of("field_78795_f","rotateAngleX");
    private static final Set<String> ROT_Y=Set.of("field_78796_g","rotateAngleY");
    private static final Set<String> ROT_Z=Set.of("field_78808_h","rotateAngleZ");
    private static final Set<String> MIRROR=Set.of("field_78809_i","mirror");

    public record Part(String field,int u,int v,float x,float y,float z,int width,int height,int depth,
                       float pivotX,float pivotY,float pivotZ,boolean mirror,
                       float baseXRot,float baseYRot,float baseZRot,boolean animated) { }
    public record Presentation(String sourceRendererClass,String clientTileId,String sourceModelClass,String texture,
                               int imageWidth,int imageHeight,int modelTextureWidth,int modelTextureHeight,
                               List<Part> parts,String animatedPartField,float modelScale,
                               float translateX,float translateY,float translateZ,int metadataMask,
                               float yawDegreesPerMeta,float yawOffsetDegrees,boolean whiteColor,
                               boolean dynamicAngleUsesDegrees,boolean inventoryPresentationProven) {
        public Presentation { parts=List.copyOf(parts); }
    }
    public record Analysis(Optional<Presentation> presentation,List<String> diagnostics) {
        public Analysis { presentation=presentation==null?Optional.empty():presentation;diagnostics=List.copyOf(diagnostics); }
    }

    private record Registration(String renderer,String clientId) { }
    private record Size(int width,int height) { }
    private record Box(String field,int u,int v,float x,float y,float z,int width,int height,int depth) { }
    private record Pivot(float x,float y,float z) { }
    private record Rotation(float x,float y,float z) { }
    private record Dynamic(String field,String method,float scale) { }
    private record ModelProof(String owner,int textureWidth,int textureHeight,List<Part> parts,Dynamic dynamic) { }

    public Analysis analyze(Path jarPath,LegacyOscillatingModelBlockAnalyzer.Rule rule)throws IOException{
        Objects.requireNonNull(rule,"rule");
        Map<String,ClassNode> classes=loadClasses(jarPath);List<String> diagnostics=new ArrayList<>();
        Registration registration=registration(classes,rule.sourceTileClass());
        if(registration==null){diagnostics.add("Oscillating TileEntitySpecialRenderer registration is not uniquely source-proven.");return new Analysis(Optional.empty(),diagnostics);}
        ClassNode renderer=classes.get(registration.renderer());String texture=singleTexture(renderer,"textures/entitys/");Size image=size(jarPath,texture);
        if(renderer==null||texture==null||image==null){diagnostics.add("Oscillating renderer texture is missing or ambiguous.");return new Analysis(Optional.empty(),diagnostics);}
        String modelOwner=modelOwner(renderer,classes);ModelProof model=modelOwner==null?null:model(classes.get(modelOwner));
        if(model==null){diagnostics.add("Oscillating source model is not fixed parts plus exactly one degree-driven X joint.");return new Analysis(Optional.empty(),diagnostics);}
        MethodNode getter=directFloatGetter(classes.get(rule.sourceTileClass()),rule.angleField());
        if(getter==null){diagnostics.add("Oscillating angle getter is not a direct read of the proven source angle field.");return new Analysis(Optional.empty(),diagnostics);}
        Float worldScale=worldScale(renderer,rule.sourceTileClass(),getter.name,model.owner(),model.dynamic().method());
        if(worldScale==null||Float.compare(worldScale,model.dynamic().scale())!=0){diagnostics.add("Oscillating world transform/scale/angle binding is unresolved.");return new Analysis(Optional.empty(),diagnostics);}
        return new Analysis(Optional.of(new Presentation(registration.renderer(),registration.clientId(),model.owner(),texture,
                image.width(),image.height(),model.textureWidth(),model.textureHeight(),model.parts(),model.dynamic().field(),worldScale,
                .5F,.5F,.5F,3,90F,180F,true,true,false)),List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static Registration registration(Map<String,ClassNode> classes,String tile){
        Set<Registration> found=new LinkedHashSet<>();
        for(ClassNode owner:classes.values())for(MethodNode method:owner.methods){
            for(AbstractInsnNode insn:method.instructions){
                if(!(insn instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESTATIC
                        ||!call.owner.equals("cpw/mods/fml/client/registry/ClientRegistry")||!call.name.equals("registerTileEntity")
                        ||!call.desc.equals("(Ljava/lang/Class;Ljava/lang/String;Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;)V"))continue;
                LegacyDirectCallArguments.ClassStringNew arguments=
                        LegacyDirectCallArguments.classStringNew(owner,method,call);
                // An unresolved registration could overwrite this tile's renderer; do not ignore it.
                if(arguments==null)return null;
                if(arguments.classInternalName().equals(tile)
                        &&inherits(classes,arguments.newTypeInternalName(),TESR)){
                    found.add(new Registration(arguments.newTypeInternalName(),arguments.stringValue()));
                }
            }
        }
        return found.size()==1?found.iterator().next():null;
    }
    private static String modelOwner(ClassNode renderer,Map<String,ClassNode> classes){
        Set<String> found=new LinkedHashSet<>();for(MethodNode method:renderer.methods)for(AbstractInsnNode insn:method.instructions)
            if(insn instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW&&inherits(classes,type.desc,MODEL_BASE))found.add(type.desc);
        return found.size()==1?found.iterator().next():null;
    }

    private static ModelProof model(ClassNode model){
        if(model==null)return null;Set<String> fields=new LinkedHashSet<>();for(FieldNode field:model.fields)if(field.desc.equals("L"+MODEL_RENDERER+";"))fields.add(field.name);
        if(fields.size()<2||fields.size()>32)return null;
        int[] texture=modelTexture(model);Map<String,Box> boxes=boxes(model);Map<String,Pivot> pivots=pivots(model);Map<String,Rotation> rotations=rotations(model);Set<String> mirrors=mirrors(model);
        if(!boxes.keySet().equals(fields)||!pivots.keySet().containsAll(fields)||!rotations.keySet().containsAll(fields))return null;
        Dynamic dynamic=dynamic(model,fields);if(dynamic==null)return null;
        Set<String> staticParts=staticParts(model,fields);Set<String> expected=new LinkedHashSet<>(fields);expected.remove(dynamic.field());if(!staticParts.equals(expected))return null;
        List<Part> parts=new ArrayList<>();for(String field:fields){Box box=boxes.get(field);Pivot pivot=pivots.get(field);Rotation rotation=rotations.get(field);parts.add(new Part(field,box.u(),box.v(),box.x(),box.y(),box.z(),box.width(),box.height(),box.depth(),pivot.x(),pivot.y(),pivot.z(),mirrors.contains(field),rotation.x(),rotation.y(),rotation.z(),field.equals(dynamic.field())));}parts.sort(Comparator.comparing(Part::field));
        return new ModelProof(model.name,texture[0],texture[1],parts,dynamic);
    }
    private static Dynamic dynamic(ClassNode model,Set<String> fields){
        Set<Dynamic> found=new LinkedHashSet<>();
        for(MethodNode method:model.methods){if(!method.desc.equals("(F)V"))continue;List<AbstractInsnNode> code=real(method);boolean pi=false,deg180=false,param=false,mul=false,div=false;String field=null;Float scale=null;
            for(int i=0;i<code.size();i++){AbstractInsnNode insn=code.get(i);
                if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Double d){pi|=Math.abs(d-Math.PI)<1E-9;deg180|=Double.compare(d,180D)==0;}
                if(insn instanceof VarInsnNode var&&var.getOpcode()==Opcodes.FLOAD&&var.var==1)param=true;
                mul|=insn.getOpcode()==Opcodes.DMUL;div|=insn.getOpcode()==Opcodes.DDIV;
                if(insn instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(MODEL_RENDERER)&&ROT_X.contains(put.name))field=precedingField(put,model.name,fields);
                if(insn instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&call.desc.equals("(F)V")){String rendered=precedingField(call,model.name,fields);Float value=i>0?floatConstant(code.get(i-1)):null;if(rendered!=null&&rendered.equals(field)&&value!=null&&value>0F)scale=value;}
            }
            if(pi&&deg180&&param&&mul&&div&&field!=null&&scale!=null)found.add(new Dynamic(field,method.name,scale));
        }
        return found.size()==1?found.iterator().next():null;
    }
    private static Set<String> staticParts(ClassNode model,Set<String> fields){
        for(MethodNode method:model.methods)if(method.desc.equals("(Lnet/minecraft/entity/Entity;FFFFFF)V")){Set<String> rendered=new LinkedHashSet<>();for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&call.desc.equals("(F)V")){String field=precedingField(call,model.name,fields);if(field!=null)rendered.add(field);}return rendered;}return Set.of();
    }
    private static String precedingField(AbstractInsnNode start,String owner,Set<String> fields){int budget=24;for(AbstractInsnNode insn=start.getPrevious();insn!=null&&budget-->0;insn=insn.getPrevious())if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(owner)&&fields.contains(field.name))return field.name;return null;}

    private static MethodNode directFloatGetter(ClassNode tile,String field){
        if(tile==null)return null;for(MethodNode method:tile.methods){if(!method.desc.equals("()F"))continue;List<AbstractInsnNode> code=real(method);if(code.size()==3&&code.get(0) instanceof VarInsnNode load&&load.getOpcode()==Opcodes.ALOAD&&load.var==0&&code.get(1) instanceof FieldInsnNode get&&get.getOpcode()==Opcodes.GETFIELD&&get.owner.equals(tile.name)&&get.name.equals(field)&&get.desc.equals("F")&&code.get(2).getOpcode()==Opcodes.FRETURN)return method;}return null;
    }
    private static Float worldScale(ClassNode renderer,String tile,String getter,String model,String dynamicMethod){
        Set<Float> scales=new LinkedHashSet<>();for(MethodNode method:renderer.methods){if(!method.desc.contains("L"+tile+";"))continue;List<AbstractInsnNode> code=real(method);boolean metadata=false,angle=false,dynamic=false;Float normalScale=null;
            for(int i=0;i<code.size();i++){AbstractInsnNode insn=code.get(i);
                if(insn instanceof MethodInsnNode call&&call.owner.equals(tile)&&call.desc.equals("()I"))metadata=true;
                if(insn instanceof MethodInsnNode call&&call.owner.equals(tile)&&call.name.equals(getter)&&call.desc.equals("()F"))angle=true;
                if(insn instanceof MethodInsnNode call&&call.owner.equals(model)&&call.desc.equals("(Lnet/minecraft/entity/Entity;FFFFFF)V")){Float value=i>0?floatConstant(code.get(i-1)):null;if(value!=null&&value>0F)normalScale=value;}
                if(insn instanceof MethodInsnNode call&&call.owner.equals(model)&&call.name.equals(dynamicMethod)&&call.desc.equals("(F)V")){AbstractInsnNode previous=previousReal(insn);dynamic=previous instanceof MethodInsnNode source&&source.owner.equals(tile)&&source.name.equals(getter)&&source.desc.equals("()F");}
            }
            if(calls(method,"org/lwjgl/opengl/GL11","glPushMatrix","()V")&&calls(method,"org/lwjgl/opengl/GL11","glPopMatrix","()V")&&translateHalf(method)&&yaw(method)&&white(method)&&metadata&&angle&&dynamic&&normalScale!=null)scales.add(normalScale);
        }
        return scales.size()==1?scales.iterator().next():null;
    }
    private static boolean translateHalf(MethodNode method){List<AbstractInsnNode> code=real(method);int halves=0;for(AbstractInsnNode insn:code){Float f=floatConstant(insn);if(f!=null&&Float.compare(f,.5F)==0)halves++;}return halves>=3&&calls(method,"org/lwjgl/opengl/GL11","glTranslatef","(FFF)V");}
    private static boolean yaw(MethodNode method){List<AbstractInsnNode> code=real(method);for(int i=8;i<code.size();i++){
        if(!(code.get(i) instanceof MethodInsnNode call)||!call.owner.equals("org/lwjgl/opengl/GL11")||!call.name.equals("glRotatef")||!call.desc.equals("(FFFF)V"))continue;
        if(code.get(i-1).getOpcode()!=Opcodes.FCONST_0||code.get(i-2).getOpcode()!=Opcodes.FCONST_1||code.get(i-3).getOpcode()!=Opcodes.FCONST_0||code.get(i-4).getOpcode()!=Opcodes.FADD||!Float.valueOf(180F).equals(floatConstant(code.get(i-5)))||code.get(i-6).getOpcode()!=Opcodes.FMUL||!Float.valueOf(90F).equals(floatConstant(code.get(i-7)))||code.get(i-8).getOpcode()!=Opcodes.I2F)continue;
        boolean mask=false;for(int j=Math.max(0,i-14);j<i-8;j++)if(code.get(j).getOpcode()==Opcodes.IAND&&j>0&&Integer.valueOf(3).equals(intConstant(code.get(j-1))))mask=true;if(mask)return true;
    }return false;}
    private static boolean white(MethodNode method){List<AbstractInsnNode> code=real(method);for(int i=3;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals("org/lwjgl/opengl/GL11")&&call.name.equals("glColor3f")&&call.desc.equals("(FFF)V")&&Float.valueOf(1F).equals(floatConstant(code.get(i-1)))&&Float.valueOf(1F).equals(floatConstant(code.get(i-2)))&&Float.valueOf(1F).equals(floatConstant(code.get(i-3))))return true;return false;}
    private static AbstractInsnNode previousReal(AbstractInsnNode node){for(AbstractInsnNode previous=node.getPrevious();previous!=null;previous=previous.getPrevious())if(previous.getOpcode()>=0)return previous;return null;}

    private static int[] modelTexture(ClassNode model){int width=64,height=32;MethodNode ctor=ownMethod(model,Set.of("<init>"),"()V");List<AbstractInsnNode> code=real(ctor);for(int i=1;i<code.size();i++)if(code.get(i) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(model.name)&&field.desc.equals("I")){Integer value=intConstant(code.get(i-1));if(value==null||value<=0)continue;if(field.name.equals("field_78090_t")||field.name.equals("textureWidth"))width=value;if(field.name.equals("field_78089_u")||field.name.equals("textureHeight"))height=value;}return new int[]{width,height};}
    private static Map<String,Box> boxes(ClassNode model){MethodNode ctor=ownMethod(model,Set.of("<init>"),"()V");if(ctor==null)return Map.of();List<AbstractInsnNode> code=real(ctor);Map<String,int[]> uv=new LinkedHashMap<>();Map<String,Box> result=new LinkedHashMap<>();for(int i=2;i+1<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL&&call.owner.equals(MODEL_RENDERER)&&call.name.equals("<init>")&&call.desc.equals("(Lnet/minecraft/client/model/ModelBase;II)V")){Integer u=intConstant(code.get(i-2)),v=intConstant(code.get(i-1));if(u!=null&&v!=null&&code.get(i+1) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(model.name))uv.put(field.name,new int[]{u,v});}for(int i=7;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&call.owner.equals(MODEL_RENDERER)&&call.desc.equals("(FFFIII)Lnet/minecraft/client/model/ModelRenderer;")&&code.get(i-7) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(model.name)){Float x=floatConstant(code.get(i-6)),y=floatConstant(code.get(i-5)),z=floatConstant(code.get(i-4));Integer w=intConstant(code.get(i-3)),h=intConstant(code.get(i-2)),d=intConstant(code.get(i-1));int[] t=uv.get(field.name);if(x!=null&&y!=null&&z!=null&&w!=null&&h!=null&&d!=null&&t!=null)result.put(field.name,new Box(field.name,t[0],t[1],x,y,z,w,h,d));}return result;}
    private static Map<String,Pivot> pivots(ClassNode model){MethodNode ctor=ownMethod(model,Set.of("<init>"),"()V");if(ctor==null)return Map.of();List<AbstractInsnNode> code=real(ctor);Map<String,Pivot> result=new LinkedHashMap<>();for(int i=4;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&call.owner.equals(MODEL_RENDERER)&&call.desc.equals("(FFF)V")&&code.get(i-4) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(model.name)){Float x=floatConstant(code.get(i-3)),y=floatConstant(code.get(i-2)),z=floatConstant(code.get(i-1));if(x!=null&&y!=null&&z!=null)result.put(field.name,new Pivot(x,y,z));}return result;}
    private static Map<String,Rotation> rotations(ClassNode model){MethodNode ctor=ownMethod(model,Set.of("<init>"),"()V");if(ctor==null)return Map.of();Set<String> helpers=new LinkedHashSet<>();for(MethodNode method:model.methods)if(method.desc.equals("(Lnet/minecraft/client/model/ModelRenderer;FFF)V")){boolean x=false,y=false,z=false;for(AbstractInsnNode insn:method.instructions)if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(MODEL_RENDERER)){x|=ROT_X.contains(field.name);y|=ROT_Y.contains(field.name);z|=ROT_Z.contains(field.name);}if(x&&y&&z)helpers.add(method.name);}if(helpers.isEmpty())return Map.of();List<AbstractInsnNode> code=real(ctor);Map<String,Rotation> result=new LinkedHashMap<>();for(int i=4;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals(model.name)&&helpers.contains(call.name)&&call.desc.equals("(Lnet/minecraft/client/model/ModelRenderer;FFF)V")&&code.get(i-4) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(model.name)){Float x=floatConstant(code.get(i-3)),y=floatConstant(code.get(i-2)),z=floatConstant(code.get(i-1));if(x!=null&&y!=null&&z!=null)result.put(field.name,new Rotation(x,y,z));}return result;}
    private static Set<String> mirrors(ClassNode model){MethodNode ctor=ownMethod(model,Set.of("<init>"),"()V");if(ctor==null)return Set.of();List<AbstractInsnNode> code=real(ctor);Set<String> result=new LinkedHashSet<>();for(int i=2;i<code.size();i++)if(code.get(i) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(MODEL_RENDERER)&&put.desc.equals("Z")&&MIRROR.contains(put.name)&&Integer.valueOf(1).equals(intConstant(code.get(i-1)))&&code.get(i-2) instanceof FieldInsnNode get&&get.getOpcode()==Opcodes.GETFIELD&&get.owner.equals(model.name))result.add(get.name);return result;}

    private static String singleTexture(ClassNode node,String fragment){if(node==null)return null;Set<String> values=new LinkedHashSet<>();for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions)if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof String text&&text.contains(fragment)&&text.endsWith(".png")&&text.indexOf(':')>0)values.add(text);return values.size()==1?values.iterator().next():null;}
    private static Size size(Path jarPath,String resource)throws IOException{if(resource==null)return null;int colon=resource.indexOf(':');if(colon<=0)return null;String name="assets/"+resource.substring(0,colon)+"/"+resource.substring(colon+1);try(JarFile jar=new JarFile(jarPath.toFile(),false)){JarEntry entry=jar.getJarEntry(name);if(entry==null)return null;try(InputStream input=jar.getInputStream(entry)){byte[] header=input.readNBytes(24);if(header.length<24)return null;byte[] signature={(byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A};for(int i=0;i<8;i++)if(header[i]!=signature[i])return null;ByteBuffer buffer=ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);int width=buffer.getInt(16),height=buffer.getInt(20);return width>0&&height>0?new Size(width,height):null;}}}
    private static boolean calls(MethodNode method,String owner,String name,String desc){if(method==null)return false;for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(desc))return true;return false;}
    private static MethodNode ownMethod(ClassNode node,Set<String> names,String desc){if(node==null)return null;for(MethodNode method:node.methods)if(names.contains(method.name)&&desc.equals(method.desc))return method;return null;}
    private static boolean inherits(Map<String,ClassNode> classes,String owner,String target){Set<String> seen=new HashSet<>();while(owner!=null&&seen.add(owner)){if(owner.equals(target))return true;ClassNode node=classes.get(owner);owner=node==null?null:node.superName;}return false;}
    private static Integer intConstant(AbstractInsnNode insn){return switch(insn.getOpcode()){case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer value?value:null;default->null;};}
    private static Float floatConstant(AbstractInsnNode insn){return switch(insn.getOpcode()){case Opcodes.FCONST_0->0F;case Opcodes.FCONST_1->1F;case Opcodes.FCONST_2->2F;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Float value?value:null;default->null;};}
    private static List<AbstractInsnNode> real(MethodNode method){List<AbstractInsnNode> result=new ArrayList<>();if(method!=null)for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)result.add(insn);return result;}
    private static Map<String,ClassNode> loadClasses(Path jarPath)throws IOException{Map<String,ClassNode> classes=new LinkedHashMap<>();try(JarFile jar=new JarFile(jarPath.toFile(),false)){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException ignored){}}}return classes;}
}
