package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
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
 * Presentation proof for inert legacy BlockContainer/TESR models made only from fixed ModelRenderer cuboids.
 * The admitted family has no dynamic part animation: raw metadata selects a 90-degree Y rotation and the
 * source model renders constructor-defined cuboids at one fixed model scale.
 */
public final class LegacyInertModelPresentationAnalyzer {
    private static final String TESR="net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer";
    private static final String MODEL_BASE="net/minecraft/client/model/ModelBase";
    private static final String MODEL_RENDERER="net/minecraft/client/model/ModelRenderer";

    public record Cuboid(String field,int u,int v,float x,float y,float z,int width,int height,int depth,
                         float pivotX,float pivotY,float pivotZ) {
        public Cuboid {
            if(field==null||field.isBlank()||u<0||v<0||width<=0||height<=0||depth<=0)throw new IllegalArgumentException("Invalid inert cuboid");
            for(float value:new float[]{x,y,z,pivotX,pivotY,pivotZ})if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite inert cuboid");
        }
        public float worldX(){return x+pivotX;}
        public float worldY(){return y+pivotY;}
        public float worldZ(){return z+pivotZ;}
    }
    public record Presentation(String sourceRendererClass,String sourceModelClass,String texture,
                               int imageWidth,int imageHeight,int modelTextureWidth,int modelTextureHeight,
                               List<Cuboid> cuboids,float modelScale,float translateX,float translateY,float translateZ,
                               int metadataMask,float yawDegreesPerMeta,boolean whiteColor,
                               boolean inventoryTransformProven,float inventoryTranslateY,float inventoryScale) {
        public Presentation { cuboids=List.copyOf(cuboids); }
    }
    public record Analysis(Optional<Presentation> presentation,List<String> diagnostics) {
        public Analysis { presentation=presentation==null?Optional.empty():presentation; diagnostics=List.copyOf(diagnostics); }
    }
    private record PngSize(int width,int height) { }
    private record Box(String field,int u,int v,float x,float y,float z,int width,int height,int depth) { }
    private record Pivot(float x,float y,float z) { }

    public Analysis analyze(Path jarPath,LegacyInertModelBlockAnalyzer.Rule rule)throws IOException{
        Objects.requireNonNull(rule,"rule");Map<String,ClassNode> classes=loadClasses(jarPath);List<String> diagnostics=new ArrayList<>();
        String renderer=findRendererRegistration(classes,rule.sourceTileClass(),rule.legacyTileId());
        if(renderer==null){diagnostics.add("Inert TileEntitySpecialRenderer registration is not uniquely source-proven.");return new Analysis(Optional.empty(),diagnostics);}
        ClassNode rendererNode=classes.get(renderer);String texture=singleResourceTexture(rendererNode,"textures/entitys/");PngSize image=pngSize(jarPath,texture);
        if(texture==null||image==null){diagnostics.add("Inert renderer texture resource is missing or ambiguous.");return new Analysis(Optional.empty(),diagnostics);}
        String modelClass=rendererModel(rendererNode,classes);ClassNode model=classes.get(modelClass);
        if(modelClass==null||model==null||!inherits(classes,modelClass,MODEL_BASE)){diagnostics.add("Inert renderer source ModelBase is unresolved.");return new Analysis(Optional.empty(),diagnostics);}
        MethodNode world=worldRender(rendererNode,rule.sourceTileClass(),modelClass);
        if(world==null||!worldTransformProven(world,rule.sourceTileClass(),modelClass)){
            diagnostics.add("Inert renderer world transform is not the admitted metadata-quadrant static model shape.");return new Analysis(Optional.empty(),diagnostics);
        }
        int[] logicalTexture=modelTexture(model);Map<String,Box> boxes=boxes(model);Map<String,Pivot> pivots=pivots(model);
        if(boxes.isEmpty()||boxes.size()>64||!pivots.keySet().containsAll(boxes.keySet())||dynamicPartMutation(model)){
            diagnostics.add("Inert ModelBase is not a bounded fixed-cuboid model.");return new Analysis(Optional.empty(),diagnostics);}
        float scale=modelScale(model,boxes.keySet());if(!Float.isFinite(scale)||scale<=0F){diagnostics.add("Inert model render scale is unresolved.");return new Analysis(Optional.empty(),diagnostics);}
        List<Cuboid> cuboids=new ArrayList<>();
        for(Box box:boxes.values()){Pivot pivot=pivots.get(box.field());cuboids.add(new Cuboid(box.field(),box.u(),box.v(),box.x(),box.y(),box.z(),box.width(),box.height(),box.depth(),pivot.x(),pivot.y(),pivot.z()));}
        cuboids.sort(Comparator.comparing(Cuboid::field));
        MethodNode inventory=ownMethod(rendererNode,Set.of("renderInv"),"()V");boolean inventoryProof=inventory!=null&&inventoryTransform(inventory,modelClass);
        return new Analysis(Optional.of(new Presentation(renderer,modelClass,texture,image.width(),image.height(),logicalTexture[0],logicalTexture[1],cuboids,scale,
                0.5F,0F,0.5F,3,90F,true,inventoryProof,-0.7F,1.3F)),List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static String findRendererRegistration(Map<String,ClassNode> classes,String tileClass,String tileId){
        Set<String> renderers=new LinkedHashSet<>();
        for(ClassNode owner:classes.values())for(MethodNode method:owner.methods){List<AbstractInsnNode> code=real(method);for(int i=0;i<code.size();i++){
            if(!(code.get(i) instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESTATIC
                    ||!call.owner.equals("cpw/mods/fml/client/registry/ClientRegistry")||!call.name.equals("registerTileEntity")
                    ||!call.desc.equals("(Ljava/lang/Class;Ljava/lang/String;Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;)V"))continue;
            boolean tile=false,id=false;String renderer=null;
            for(int j=Math.max(0,i-10);j<i;j++){AbstractInsnNode value=code.get(j);
                if(value instanceof LdcInsnNode ldc&&ldc.cst instanceof org.objectweb.asm.Type type&&type.getSort()==org.objectweb.asm.Type.OBJECT&&type.getInternalName().equals(tileClass))tile=true;
                if(value instanceof LdcInsnNode ldc&&tileId.equals(ldc.cst))id=true;
                if(value instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW&&inherits(classes,type.desc,TESR))renderer=type.desc;
            }
            if(tile&&id&&renderer!=null)renderers.add(renderer);
        }}
        return renderers.size()==1?renderers.iterator().next():null;
    }
    private static String rendererModel(ClassNode renderer,Map<String,ClassNode> classes){
        Set<String> models=new LinkedHashSet<>();for(MethodNode method:renderer.methods)for(AbstractInsnNode insn:method.instructions)
            if(insn instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW&&inherits(classes,type.desc,MODEL_BASE))models.add(type.desc);
        return models.size()==1?models.iterator().next():null;
    }
    private static MethodNode worldRender(ClassNode renderer,String tileClass,String modelClass){
        for(MethodNode method:renderer.methods)if(method.desc.equals("(Lnet/minecraft/tileentity/TileEntity;DDDF)V")
                &&calls(method,"org/lwjgl/opengl/GL11","glPushMatrix","()V")&&calls(method,modelClass,"renderAndon","()V"))return method;
        // Generic fallback: admit any zero-arg source model draw name when exactly one call targets modelClass.
        for(MethodNode method:renderer.methods)if(method.desc.equals("(Lnet/minecraft/tileentity/TileEntity;DDDF)V")&&calls(method,"org/lwjgl/opengl/GL11","glPushMatrix","()V")){
            int draws=0;for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(modelClass)&&call.desc.equals("()V"))draws++;
            if(draws==1)return method;
        }
        return null;
    }
    private static boolean worldTransformProven(MethodNode method,String tileClass,String modelClass){
        boolean metadata=false,mask=false,rotate=false,color=false,draw=false;
        int half=0,ninety=0,ones=0;
        for(AbstractInsnNode insn:method.instructions){
            if(insn instanceof MethodInsnNode call&&call.owner.equals("net/minecraft/tileentity/TileEntity")&&call.desc.equals("()I")
                    &&(call.name.equals("func_145832_p")||call.name.equals("getBlockMetadata")))metadata=true;
            if(insn.getOpcode()==Opcodes.IAND)mask=true;
            if(insn instanceof MethodInsnNode call&&call.owner.equals("org/lwjgl/opengl/GL11")&&call.name.equals("glRotatef")&&call.desc.equals("(FFFF)V"))rotate=true;
            if(insn instanceof MethodInsnNode call&&call.owner.equals("org/lwjgl/opengl/GL11")&&call.name.equals("glColor3f")&&call.desc.equals("(FFF)V"))color=true;
            if(insn instanceof MethodInsnNode call&&call.owner.equals(modelClass)&&call.desc.equals("()V"))draw=true;
            Float value=floatConstant(insn);if(value!=null){if(Float.compare(value,0.5F)==0)half++;if(Float.compare(value,90F)==0)ninety++;if(Float.compare(value,1F)==0)ones++;}
        }
        return calls(method,"org/lwjgl/opengl/GL11","glPushMatrix","()V")&&calls(method,"org/lwjgl/opengl/GL11","glPopMatrix","()V")
                &&calls(method,"org/lwjgl/opengl/GL11","glTranslatef","(FFF)V")&&metadata&&mask&&rotate&&color&&draw
                &&half>=2&&ninety>=1&&ones>=3&&containsIntBeforeOpcode(method,3,Opcodes.IAND);
    }
    private static boolean inventoryTransform(MethodNode method,String modelClass){
        return calls(method,"org/lwjgl/opengl/GL11","glPushMatrix","()V")&&calls(method,"org/lwjgl/opengl/GL11","glPopMatrix","()V")
                &&callsFloat3(method,"org/lwjgl/opengl/GL11","glTranslatef",0F,-0.7F,0F)
                &&callsFloat3(method,"org/lwjgl/opengl/GL11","glScalef",1.3F,1.3F,1.3F)
                &&countModelZeroArgCalls(method,modelClass)==1;
    }

    private static boolean callsFloat3(MethodNode method,String owner,String name,float a,float b,float c){
        List<AbstractInsnNode> code=real(method);
        for(int i=3;i<code.size();i++){
            if(!(code.get(i) instanceof MethodInsnNode call)||!call.owner.equals(owner)||!call.name.equals(name)||!call.desc.equals("(FFF)V"))continue;
            Float x=floatConstant(code.get(i-3)),y=floatConstant(code.get(i-2)),z=floatConstant(code.get(i-1));
            if(x!=null&&y!=null&&z!=null&&Float.compare(x,a)==0&&Float.compare(y,b)==0&&Float.compare(z,c)==0)return true;
        }
        return false;
    }

    private static int[] modelTexture(ClassNode model){
        int width=64,height=32;MethodNode ctor=ownMethod(model,Set.of("<init>"),"()V");List<AbstractInsnNode> code=real(ctor);
        for(int i=1;i<code.size();i++)if(code.get(i) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(model.name)&&field.desc.equals("I")){
            Integer value=intConstant(code.get(i-1));if(value==null||value<=0)continue;
            if(field.name.equals("field_78090_t")||field.name.equals("textureWidth"))width=value;
            if(field.name.equals("field_78089_u")||field.name.equals("textureHeight"))height=value;
        }
        return new int[]{width,height};
    }
    private static Map<String,Box> boxes(ClassNode model){
        MethodNode ctor=ownMethod(model,Set.of("<init>"),"()V");if(ctor==null)return Map.of();List<AbstractInsnNode> code=real(ctor);
        Map<String,int[]> uv=new LinkedHashMap<>();Map<String,Box> result=new LinkedHashMap<>();
        for(int i=2;i+1<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL
                &&call.owner.equals(MODEL_RENDERER)&&call.name.equals("<init>")&&call.desc.equals("(Lnet/minecraft/client/model/ModelBase;II)V")){
            Integer u=intConstant(code.get(i-2)),v=intConstant(code.get(i-1));
            if(u!=null&&v!=null&&code.get(i+1) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(model.name))uv.put(field.name,new int[]{u,v});
        }
        for(int i=7;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&call.owner.equals(MODEL_RENDERER)
                &&call.desc.equals("(FFFIII)Lnet/minecraft/client/model/ModelRenderer;")&&code.get(i-7) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(model.name)){
            Float x=floatConstant(code.get(i-6)),y=floatConstant(code.get(i-5)),z=floatConstant(code.get(i-4));Integer w=intConstant(code.get(i-3)),h=intConstant(code.get(i-2)),d=intConstant(code.get(i-1));int[] tex=uv.get(field.name);
            if(x!=null&&y!=null&&z!=null&&w!=null&&h!=null&&d!=null&&tex!=null)result.put(field.name,new Box(field.name,tex[0],tex[1],x,y,z,w,h,d));
        }
        return result;
    }
    private static Map<String,Pivot> pivots(ClassNode model){
        MethodNode ctor=ownMethod(model,Set.of("<init>"),"()V");if(ctor==null)return Map.of();List<AbstractInsnNode> code=real(ctor);Map<String,Pivot> result=new LinkedHashMap<>();
        for(int i=4;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&call.owner.equals(MODEL_RENDERER)&&call.desc.equals("(FFF)V")
                &&code.get(i-4) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(model.name)){
            Float x=floatConstant(code.get(i-3)),y=floatConstant(code.get(i-2)),z=floatConstant(code.get(i-1));if(x!=null&&y!=null&&z!=null)result.put(field.name,new Pivot(x,y,z));
        }
        return result;
    }
    private static boolean dynamicPartMutation(ClassNode model){
        Set<String> rotationFields=Set.of("field_78795_f","field_78796_g","field_78808_h","rotateAngleX","rotateAngleY","rotateAngleZ");
        for(MethodNode method:model.methods)if(!method.name.equals("<init>"))for(AbstractInsnNode insn:method.instructions)
            if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(MODEL_RENDERER)&&rotationFields.contains(field.name))return true;
        return false;
    }
    private static float modelScale(ClassNode model,Set<String> fields){
        MethodNode helper=null;for(MethodNode method:model.methods)if(method.desc.equals("()V")&&countFloat(method,0.0625F)>0)helper=method;
        if(helper==null)return Float.NaN;MethodNode render=null;
        for(MethodNode method:model.methods)if(method.desc.equals("(Lnet/minecraft/entity/Entity;FFFFFF)V")){render=method;break;}
        if(render==null)return Float.NaN;Set<String> rendered=new HashSet<>();
        for(AbstractInsnNode insn:render.instructions)if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(model.name)&&fields.contains(field.name))rendered.add(field.name);
        return rendered.containsAll(fields)?0.0625F:Float.NaN;
    }

    private static String singleResourceTexture(ClassNode node,String fragment){if(node==null)return null;Set<String> values=new LinkedHashSet<>();for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions)if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof String text&&text.contains(fragment)&&text.endsWith(".png")&&text.indexOf(':')>0)values.add(text);return values.size()==1?values.iterator().next():null;}
    private static PngSize pngSize(Path jarPath,String resource)throws IOException{if(resource==null)return null;int colon=resource.indexOf(':');if(colon<=0)return null;String name="assets/"+resource.substring(0,colon)+"/"+resource.substring(colon+1);try(JarFile jar=new JarFile(jarPath.toFile(),false)){JarEntry entry=jar.getJarEntry(name);if(entry==null)return null;try(InputStream input=jar.getInputStream(entry)){byte[] header=input.readNBytes(24);if(header.length<24)return null;byte[] sig={(byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A};for(int i=0;i<8;i++)if(header[i]!=sig[i])return null;ByteBuffer buffer=ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);int w=buffer.getInt(16),h=buffer.getInt(20);return w>0&&h>0?new PngSize(w,h):null;}}}
    private static boolean containsIntBeforeOpcode(MethodNode method,int value,int opcode){List<AbstractInsnNode> code=real(method);for(int i=1;i<code.size();i++)if(code.get(i).getOpcode()==opcode&&Integer.valueOf(value).equals(intConstant(code.get(i-1))))return true;return false;}
    private static int countModelZeroArgCalls(MethodNode method,String owner){int n=0;for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.desc.equals("()V"))n++;return n;}
    private static int countFloat(MethodNode method,float target){int n=0;for(AbstractInsnNode insn:method.instructions){Float value=floatConstant(insn);if(value!=null&&Float.compare(value,target)==0)n++;}return n;}
    private static boolean calls(MethodNode method,String owner,String name,String desc){if(method==null)return false;for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(desc))return true;return false;}
    private static MethodNode ownMethod(ClassNode node,Set<String> names,String desc){if(node==null)return null;for(MethodNode method:node.methods)if(names.contains(method.name)&&desc.equals(method.desc))return method;return null;}
    private static boolean inherits(Map<String,ClassNode> classes,String owner,String target){Set<String> seen=new HashSet<>();while(owner!=null&&seen.add(owner)){if(owner.equals(target))return true;ClassNode node=classes.get(owner);owner=node==null?null:node.superName;}return false;}
    private static Integer intConstant(AbstractInsnNode insn){return switch(insn.getOpcode()){case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer v?v:null;default->null;};}
    private static Float floatConstant(AbstractInsnNode insn){return switch(insn.getOpcode()){case Opcodes.FCONST_0->0F;case Opcodes.FCONST_1->1F;case Opcodes.FCONST_2->2F;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Float v?v:null;default->null;};}
    private static List<AbstractInsnNode> real(MethodNode method){List<AbstractInsnNode> out=new ArrayList<>();if(method!=null)for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)out.add(insn);return out;}
    private static Map<String,ClassNode> loadClasses(Path jarPath)throws IOException{Map<String,ClassNode> classes=new LinkedHashMap<>();try(JarFile jar=new JarFile(jarPath.toFile(),false)){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException ignored){}}}return classes;}
}
