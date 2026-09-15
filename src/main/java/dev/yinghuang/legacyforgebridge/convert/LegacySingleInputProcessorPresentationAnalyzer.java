package dev.longyu.legacyforgebridge.convert;

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
 * Fail-closed presentation proof for the first generic rotating two-layer processor family.
 *
 * <p>The admitted family is deliberately narrow: a normal 1.7 GuiContainer with one 256x256
 * texture and proven motion/progress strips, plus a TileEntitySpecialRenderer that renders two
 * 16x8x16 ModelRenderer cuboids from one 64x64 texture. The lower cuboid rotates around Y from a
 * client-side roll value driven by synchronized raw block metadata. No Bamboo identity or class
 * name is used as an admission condition.</p>
 */
public final class LegacySingleInputProcessorPresentationAnalyzer {
    private static final String GUI_CONTAINER="net/minecraft/client/gui/inventory/GuiContainer";
    private static final String CONTAINER="net/minecraft/inventory/Container";
    private static final String TESR="net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer";
    private static final String MODEL_BASE="net/minecraft/client/model/ModelBase";
    private static final String MODEL_RENDERER="net/minecraft/client/model/ModelRenderer";
    private static final String RESOURCE="net/minecraft/util/ResourceLocation";

    public record Cuboid(String field,int u,int v,float x,float y,float z,int width,int height,int depth) { }

    public record Presentation(
            String sourceBlockClass,
            String sourceTileClass,
            String sourceGuiClass,
            String guiTexture,
            int guiTextureWidth,
            int guiTextureHeight,
            int guiWidth,
            int guiHeight,
            int motionX,
            int motionY,
            int motionU,
            int motionVStride,
            int motionWidth,
            int motionHeight,
            int motionFrames,
            int progressX,
            int progressY,
            int progressU,
            int progressVStride,
            int progressWidth,
            int progressHeight,
            int progressStages,
            String sourceRendererClass,
            String sourceModelClass,
            String entityTexture,
            int entityTextureWidth,
            int entityTextureHeight,
            int modelTextureWidth,
            int modelTextureHeight,
            Cuboid rotatingLower,
            Cuboid staticUpper,
            float renderScale,
            boolean centeredAtBlock,
            boolean metadataDrivesRoll,
            boolean inventoryUsesZeroRotation
    ) { }

    public record Analysis(Optional<Presentation> presentation,List<String> diagnostics) {
        public Analysis { presentation=presentation==null?Optional.empty():presentation; diagnostics=List.copyOf(diagnostics); }
    }

    private record PngSize(int width,int height) { }
    private record RegisteredRenderer(String rendererClass) { }
    private record ModelProof(String modelClass,String texture,int textureWidth,int textureHeight,
                              Cuboid lower,Cuboid upper,float scale,boolean centered,
                              boolean metadataRoll,boolean inventoryZero) { }

    public Analysis analyze(Path jarPath,LegacySingleInputProcessorAnalyzer.Rule machine)throws IOException {
        Objects.requireNonNull(machine,"machine");
        Map<String,ClassNode> classes=loadClasses(jarPath);
        List<String> diagnostics=new ArrayList<>();

        String gui=findGuiClass(classes,machine.guiId(),machine.sourceTileClass());
        if(gui==null){
            diagnostics.add("Processor client GUI case is not uniquely proven for legacy GUI id "+machine.guiId()+".");
            return new Analysis(Optional.empty(),diagnostics);
        }
        ClassNode guiNode=classes.get(gui);
        String guiTexture=singleResourceTexture(guiNode,"textures/guis/");
        PngSize guiSize=pngSize(jarPath,guiTexture);
        if(guiTexture==null||guiSize==null||guiSize.width()!=256||guiSize.height()!=256
                ||!canonicalGui(guiNode,machine.sourceTileClass())){
            diagnostics.add("Processor GUI texture/widgets are not the admitted 256x256 motion/progress layout: "+gui+".");
            return new Analysis(Optional.empty(),diagnostics);
        }

        RegisteredRenderer registration=findRendererRegistration(classes,machine.sourceTileClass(),machine.legacyTileId());
        if(registration==null){
            diagnostics.add("Processor TileEntitySpecialRenderer registration is not uniquely source-proven.");
            return new Analysis(Optional.empty(),diagnostics);
        }
        ModelProof model=proveRendererAndModel(jarPath,classes,machine.sourceTileClass(),registration.rendererClass());
        if(model==null){
            diagnostics.add("Processor TESR/model is not the admitted centered two-layer rotating cuboid family.");
            return new Analysis(Optional.empty(),diagnostics);
        }

        Presentation result=new Presentation(
                machine.sourceBlockClass(),machine.sourceTileClass(),gui,guiTexture,
                guiSize.width(),guiSize.height(),176,166,
                80,28,176,16,16,16,4,
                80,46,192,6,16,6,3,
                registration.rendererClass(),model.modelClass(),model.texture(),model.textureWidth(),model.textureHeight(),
                64,64,model.lower(),model.upper(),model.scale(),model.centered(),model.metadataRoll(),model.inventoryZero());
        return new Analysis(Optional.of(result),List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static boolean canonicalGui(ClassNode gui,String tileClass){
        if(gui==null||!GUI_CONTAINER.equals(gui.superName))return false;
        MethodNode constructor=ownMethod(gui,Set.of("<init>"),
                "(Lnet/minecraft/entity/player/InventoryPlayer;Lnet/minecraft/tileentity/TileEntity;)V");
        if(constructor==null||!hasType(constructor,Opcodes.CHECKCAST,tileClass))return false;
        boolean container=false;
        for(AbstractInsnNode insn:constructor.instructions)if(insn instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW){
            // The exact class hierarchy is checked in findGuiClass; here only prove the GUI owns a source container.
            if(!type.desc.equals(gui.name))container=true;
        }
        if(!container)return false;
        MethodNode background=null;
        for(MethodNode method:gui.methods)if(method.desc.equals("(FII)V")&&drawCalls(method)>=3){background=method;break;}
        if(background==null)return false;
        Set<Integer> required=Set.of(80,28,176,16,4,46,192,6);
        Set<Integer> constants=new HashSet<>();
        for(AbstractInsnNode insn:background.instructions){Integer value=intConstant(insn);if(value!=null)constants.add(value);}
        if(!constants.containsAll(required))return false;
        int tileIntFields=0;
        boolean multiply=false;
        for(AbstractInsnNode insn:background.instructions){
            if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD
                    &&field.owner.equals(tileClass)&&field.desc.equals("I"))tileIntFields++;
            if(insn.getOpcode()==Opcodes.IMUL)multiply=true;
        }
        return tileIntFields>=3&&multiply
                &&hasDrawWindow(background,80,28,176,16,16,16)
                &&hasDrawWindow(background,80,46,192,6,16,6);
    }

    private static int drawCalls(MethodNode method){
        int count=0;
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call
                &&call.desc.equals("(IIIIII)V")&&(call.name.equals("func_73729_b")||call.name.equals("drawTexturedModalRect")))count++;
        return count;
    }

    private static boolean hasDrawWindow(MethodNode method,int...required){
        List<AbstractInsnNode> code=real(method);
        for(int i=0;i<code.size();i++){
            if(!(code.get(i) instanceof MethodInsnNode call)||!call.desc.equals("(IIIIII)V"))continue;
            Set<Integer> values=new HashSet<>();
            for(int j=Math.max(0,i-24);j<i;j++){Integer value=intConstant(code.get(j));if(value!=null)values.add(value);}
            boolean all=true;for(int value:required)all&=values.contains(value);
            if(all)return true;
        }
        return false;
    }

    private static String findGuiClass(Map<String,ClassNode> classes,int guiId,String tileClass){
        Set<String> candidates=new LinkedHashSet<>();
        for(ClassNode owner:classes.values()){
            if(!owner.interfaces.contains("cpw/mods/fml/common/network/IGuiHandler"))continue;
            MethodNode method=ownMethod(owner,Set.of("getClientGuiElement"),
                    "(ILnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;III)Ljava/lang/Object;");
            if(method==null)continue;
            LabelNode start=switchCase(method,guiId);if(start==null)continue;
            Set<LabelNode> boundaries=switchLabels(method);
            for(AbstractInsnNode insn=start.getNext();insn!=null;insn=insn.getNext()){
                if(insn instanceof LabelNode label&&boundaries.contains(label))break;
                if(insn instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL&&call.name.equals("<init>")
                        &&call.desc.contains("Lnet/minecraft/tileentity/TileEntity;")&&inherits(classes,call.owner,GUI_CONTAINER)){
                    if(hasTypeBetween(start,insn,Opcodes.CHECKCAST,tileClass))candidates.add(call.owner);
                }
                if(insn.getOpcode()==Opcodes.ARETURN)break;
            }
        }
        return candidates.size()==1?candidates.iterator().next():null;
    }

    private static boolean hasTypeBetween(AbstractInsnNode start,AbstractInsnNode end,int opcode,String type){
        for(AbstractInsnNode insn=start;insn!=null&&insn!=end;insn=insn.getNext())
            if(insn instanceof TypeInsnNode value&&value.getOpcode()==opcode&&value.desc.equals(type))return true;
        return false;
    }

    private static RegisteredRenderer findRendererRegistration(Map<String,ClassNode> classes,String tileClass,String tileId){
        Set<String> renderers=new LinkedHashSet<>();
        for(ClassNode owner:classes.values())for(MethodNode method:owner.methods){
            List<AbstractInsnNode> code=real(method);
            for(int i=0;i<code.size();i++){
                if(!(code.get(i) instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESTATIC
                        ||!call.owner.equals("cpw/mods/fml/client/registry/ClientRegistry")
                        ||!call.name.equals("registerTileEntity")
                        ||!call.desc.equals("(Ljava/lang/Class;Ljava/lang/String;Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;)V"))continue;
                String renderer=null;boolean tile=false,id=false;
                for(int j=Math.max(0,i-10);j<i;j++){
                    AbstractInsnNode value=code.get(j);
                    if(value instanceof LdcInsnNode ldc&&ldc.cst instanceof Type type&&type.getSort()==Type.OBJECT&&type.getInternalName().equals(tileClass))tile=true;
                    if(value instanceof LdcInsnNode ldc&&tileId.equals(ldc.cst))id=true;
                    if(value instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW&&inherits(classes,type.desc,TESR))renderer=type.desc;
                }
                if(tile&&id&&renderer!=null)renderers.add(renderer);
            }
        }
        return renderers.size()==1?new RegisteredRenderer(renderers.iterator().next()):null;
    }

    private static ModelProof proveRendererAndModel(Path jarPath,Map<String,ClassNode> classes,String tileClass,String rendererClass)throws IOException{
        ClassNode renderer=classes.get(rendererClass);if(renderer==null||!inherits(classes,rendererClass,TESR))return null;
        String texture=singleResourceTexture(renderer,"textures/entitys/");
        PngSize size=pngSize(jarPath,texture);if(texture==null||size==null||size.width()!=64||size.height()!=64)return null;
        String modelClass=rendererModel(renderer,classes);if(modelClass==null)return null;
        ClassNode model=classes.get(modelClass);if(model==null||!inherits(classes,modelClass,MODEL_BASE))return null;

        MethodNode worldRender=ownMethod(renderer,Set.of("func_147500_a","renderTileEntityAt"),
                "(Lnet/minecraft/tileentity/TileEntity;DDDF)V");
        if(worldRender==null||!calls(worldRender,"org/lwjgl/opengl/GL11","glPushMatrix","()V")
                ||!calls(worldRender,"org/lwjgl/opengl/GL11","glPopMatrix","()V")
                ||!calls(worldRender,"org/lwjgl/opengl/GL11","glTranslatef","(FFF)V")
                ||!hasType(worldRender,Opcodes.CHECKCAST,tileClass)
                ||countFloat(worldRender,0.5F)<3||!containsFloat(worldRender,0.0625F))return null;
        MethodNode renderInv=ownMethod(renderer,Set.of("renderInv"),"()V");
        if(renderInv==null)return null;

        Map<String,int[]> uv=rendererUv(model);
        Map<String,Cuboid> boxes=rendererBoxes(model,uv);
        if(boxes.size()!=2||!modelTexture64(model))return null;
        String rotating=rotatingField(model,tileClass);
        if(rotating==null)return null;
        String other=boxes.keySet().stream().filter(value->!value.equals(rotating)).findFirst().orElse(null);
        if(other==null)return null;
        Cuboid lower=boxes.get(rotating),upper=boxes.get(other);
        if(!cuboid(lower,0,25,-8F,0F,-8F,16,8,16)
                ||!cuboid(upper,0,0,-8F,-8F,-8F,16,8,16)
                ||!zeroPivots(model,Set.of(rotating,other))
                ||!mirrored(model,Set.of(rotating,other))
                ||!inventoryZeroRotation(model,rotating,Set.of(rotating,other)))return null;
        if(!metadataDrivesRoll(classes,tileClass,model,rotating))return null;
        return new ModelProof(modelClass,texture,size.width(),size.height(),lower,upper,0.0625F,true,true,true);
    }

    private static String rendererModel(ClassNode renderer,Map<String,ClassNode> classes){
        MethodNode ctor=ownMethod(renderer,Set.of("<init>"),"()V");if(ctor==null)return null;
        Set<String> values=new LinkedHashSet<>();
        for(AbstractInsnNode insn:ctor.instructions)if(insn instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW
                &&inherits(classes,type.desc,MODEL_BASE))values.add(type.desc);
        return values.size()==1?values.iterator().next():null;
    }

    private static Map<String,int[]> rendererUv(ClassNode model){
        MethodNode ctor=ownMethod(model,Set.of("<init>"),"()V");if(ctor==null)return Map.of();
        List<AbstractInsnNode> code=real(ctor);Map<String,int[]> result=new LinkedHashMap<>();
        for(int i=2;i+1<code.size();i++){
            if(!(code.get(i) instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESPECIAL
                    ||!call.owner.equals(MODEL_RENDERER)||!call.name.equals("<init>")
                    ||!call.desc.equals("(Lnet/minecraft/client/model/ModelBase;II)V"))continue;
            Integer u=intConstant(code.get(i-2)),v=intConstant(code.get(i-1));
            if(u==null||v==null||!(code.get(i+1) instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.PUTFIELD
                    ||!field.owner.equals(model.name)||!field.desc.equals("L"+MODEL_RENDERER+";"))continue;
            result.put(field.name,new int[]{u,v});
        }
        return result;
    }

    private static Map<String,Cuboid> rendererBoxes(ClassNode model,Map<String,int[]> uv){
        MethodNode ctor=ownMethod(model,Set.of("<init>"),"()V");if(ctor==null)return Map.of();
        List<AbstractInsnNode> code=real(ctor);Map<String,Cuboid> result=new LinkedHashMap<>();
        for(int i=7;i<code.size();i++){
            if(!(code.get(i) instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKEVIRTUAL
                    ||!call.owner.equals(MODEL_RENDERER)||!call.desc.equals("(FFFIII)Lnet/minecraft/client/model/ModelRenderer;"))continue;
            if(!(code.get(i-7) instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.GETFIELD||!field.owner.equals(model.name))continue;
            Float x=floatConstant(code.get(i-6)),y=floatConstant(code.get(i-5)),z=floatConstant(code.get(i-4));
            Integer w=intConstant(code.get(i-3)),h=intConstant(code.get(i-2)),d=intConstant(code.get(i-1));int[] tex=uv.get(field.name);
            if(x!=null&&y!=null&&z!=null&&w!=null&&h!=null&&d!=null&&tex!=null)
                result.put(field.name,new Cuboid(field.name,tex[0],tex[1],x,y,z,w,h,d));
        }
        return result;
    }

    private static boolean modelTexture64(ClassNode model){
        MethodNode ctor=ownMethod(model,Set.of("<init>"),"()V");if(ctor==null)return false;
        boolean width=false,height=false;List<AbstractInsnNode> code=real(ctor);
        for(int i=1;i<code.size();i++)if(code.get(i) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD
                &&field.owner.equals(model.name)&&field.desc.equals("I")&&Integer.valueOf(64).equals(intConstant(code.get(i-1)))){
            if(field.name.equals("field_78090_t")||field.name.equals("textureWidth"))width=true;
            if(field.name.equals("field_78089_u")||field.name.equals("textureHeight"))height=true;
        }
        return width&&height;
    }

    private static String rotatingField(ClassNode model,String tileClass){
        MethodNode render=null;
        for(MethodNode method:model.methods)if(method.desc.equals("(L"+tileClass+";FFFFFF)V")){render=method;break;}
        if(render==null||!containsDouble(render,Math.PI)||!containsFloat(render,180F)
                ||!hasOpcode(render,Opcodes.DMUL)||!hasOpcode(render,Opcodes.FDIV))return null;
        List<AbstractInsnNode> code=real(render);Set<String> fields=new LinkedHashSet<>();
        for(int i=1;i<code.size();i++)if(code.get(i) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD
                &&put.owner.equals(MODEL_RENDERER)&&(put.name.equals("field_78796_g")||put.name.equals("rotateAngleY"))){
            for(int j=i-1;j>=Math.max(0,i-14);j--)if(code.get(j) instanceof FieldInsnNode get&&get.getOpcode()==Opcodes.GETFIELD
                    &&get.owner.equals(model.name)&&get.desc.equals("L"+MODEL_RENDERER+";")){fields.add(get.name);break;}
        }
        boolean getter=false;
        for(AbstractInsnNode insn:render.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(tileClass)
                &&call.desc.equals("()I")){getter=true;break;}
        return getter&&fields.size()==1?fields.iterator().next():null;
    }

    private static boolean zeroPivots(ClassNode model,Set<String> fields){
        MethodNode ctor=ownMethod(model,Set.of("<init>"),"()V");if(ctor==null)return false;
        Set<String> proven=new HashSet<>();List<AbstractInsnNode> code=real(ctor);
        for(int i=4;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL
                &&call.owner.equals(MODEL_RENDERER)&&call.desc.equals("(FFF)V")
                &&Float.valueOf(0F).equals(floatConstant(code.get(i-3)))&&Float.valueOf(0F).equals(floatConstant(code.get(i-2)))
                &&Float.valueOf(0F).equals(floatConstant(code.get(i-1)))&&code.get(i-4) instanceof FieldInsnNode field
                &&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(model.name))proven.add(field.name);
        return proven.containsAll(fields);
    }

    private static boolean mirrored(ClassNode model,Set<String> fields){
        MethodNode ctor=ownMethod(model,Set.of("<init>"),"()V");if(ctor==null)return false;
        Set<String> proven=new HashSet<>();List<AbstractInsnNode> code=real(ctor);
        for(int i=2;i<code.size();i++)if(code.get(i) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD
                &&put.owner.equals(MODEL_RENDERER)&&(put.name.equals("field_78809_i")||put.name.equals("mirror"))
                &&Integer.valueOf(1).equals(intConstant(code.get(i-1)))&&code.get(i-2) instanceof FieldInsnNode field
                &&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(model.name))proven.add(field.name);
        return proven.containsAll(fields);
    }

    private static boolean inventoryZeroRotation(ClassNode model,String rotating,Set<String> fields){
        MethodNode method=ownMethod(model,Set.of("renderInv"),"()V");if(method==null||countFloat(method,0.0625F)<2)return false;
        boolean zero=false;Set<String> rendered=new HashSet<>();List<AbstractInsnNode> code=real(method);
        for(int i=2;i<code.size();i++){
            if(code.get(i) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(MODEL_RENDERER)
                    &&(put.name.equals("field_78796_g")||put.name.equals("rotateAngleY"))&&Float.valueOf(0F).equals(floatConstant(code.get(i-1)))
                    &&code.get(i-2) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(model.name)&&field.name.equals(rotating))zero=true;
            if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&call.desc.equals("(F)V")){
                for(int j=i-1;j>=Math.max(0,i-4);j--)if(code.get(j) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(model.name)){rendered.add(field.name);break;}
            }
        }
        return zero&&rendered.containsAll(fields);
    }

    private static boolean metadataDrivesRoll(Map<String,ClassNode> classes,String tileClass,ClassNode model,String rotating){
        MethodNode modelRender=null;String getterName=null;
        for(MethodNode method:model.methods)if(method.desc.equals("(L"+tileClass+";FFFFFF)V")){modelRender=method;break;}
        if(modelRender==null)return false;
        for(AbstractInsnNode insn:modelRender.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(tileClass)&&call.desc.equals("()I")){getterName=call.name;break;}
        if(getterName==null)return false;
        ClassNode tile=classes.get(tileClass);if(tile==null)return false;
        MethodNode getter=ownMethod(tile,Set.of(getterName),"()I");if(getter==null)return false;
        String rollField=null;List<AbstractInsnNode> getCode=real(getter);
        for(int i=0;i+2<getCode.size();i++)if(getCode.get(i) instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD
                &&field.owner.equals(tileClass)&&field.desc.equals("F")&&getCode.get(i+1).getOpcode()==Opcodes.F2I&&getCode.get(i+2).getOpcode()==Opcodes.IRETURN)rollField=field.name;
        if(rollField==null)return false;
        MethodNode tick=ownMethod(tile,Set.of("updateEntity","func_145845_h"),"()V");if(tick==null)return false;
        int metadataCalls=0;boolean add=false,limit=false,rollWrite=false;
        for(AbstractInsnNode insn:tick.instructions){
            if(insn instanceof MethodInsnNode call&&call.desc.equals("()I")&&(call.name.equals("func_145832_p")||call.name.equals("getBlockMetadata")))metadataCalls++;
            if(insn.getOpcode()==Opcodes.FADD)add=true;
            Float f=floatConstant(insn);if(f!=null&&Float.compare(f,360F)==0)limit=true;
            if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(tileClass)&&field.name.equals(rollField)&&field.desc.equals("F"))rollWrite=true;
        }
        boolean serverSync=false;
        for(MethodNode method:tile.methods)if(method.desc.equals("(I)V")&&calls(method,"net/minecraft/world/World","func_72921_c","(IIIII)Z"))serverSync=true;
        return metadataCalls>=3&&add&&limit&&rollWrite&&serverSync;
    }

    private static boolean cuboid(Cuboid value,int u,int v,float x,float y,float z,int w,int h,int d){
        return value!=null&&value.u()==u&&value.v()==v&&Float.compare(value.x(),x)==0&&Float.compare(value.y(),y)==0
                &&Float.compare(value.z(),z)==0&&value.width()==w&&value.height()==h&&value.depth()==d;
    }

    private static String singleResourceTexture(ClassNode node,String pathFragment){
        if(node==null)return null;Set<String> values=new LinkedHashSet<>();
        for(MethodNode method:node.methods)for(AbstractInsnNode insn:method.instructions)if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof String text
                &&text.contains(pathFragment)&&text.endsWith(".png")&&qualifiedResource(text))values.add(text);
        return values.size()==1?values.iterator().next():null;
    }

    private static PngSize pngSize(Path jarPath,String resource)throws IOException{
        if(resource==null)return null;int colon=resource.indexOf(':');if(colon<=0)return null;
        String entryName="assets/"+resource.substring(0,colon)+"/"+resource.substring(colon+1);
        try(JarFile jar=new JarFile(jarPath.toFile(),false)){
            JarEntry entry=jar.getJarEntry(entryName);if(entry==null)return null;
            try(InputStream input=jar.getInputStream(entry)){
                byte[] header=input.readNBytes(24);if(header.length<24)return null;
                byte[] signature={(byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A};
                for(int i=0;i<8;i++)if(header[i]!=signature[i])return null;
                if(header[12]!='I'||header[13]!='H'||header[14]!='D'||header[15]!='R')return null;
                ByteBuffer buffer=ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
                int width=buffer.getInt(16),height=buffer.getInt(20);
                return width>0&&height>0?new PngSize(width,height):null;
            }
        }
    }

    private static boolean qualifiedResource(String value){int colon=value.indexOf(':');return colon>0&&colon<value.length()-1&&value.indexOf(':',colon+1)<0;}
    private static boolean inherits(Map<String,ClassNode> classes,String owner,String target){Set<String> seen=new HashSet<>();while(owner!=null&&seen.add(owner)){if(owner.equals(target))return true;ClassNode node=classes.get(owner);owner=node==null?null:node.superName;}return false;}
    private static boolean hasType(MethodNode method,int opcode,String type){if(method==null)return false;for(AbstractInsnNode insn:method.instructions)if(insn instanceof TypeInsnNode value&&value.getOpcode()==opcode&&value.desc.equals(type))return true;return false;}
    private static boolean calls(MethodNode method,String owner,String name,String descriptor){if(method==null)return false;for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(descriptor))return true;return false;}
    private static MethodNode ownMethod(ClassNode node,Set<String> names,String descriptor){if(node==null)return null;for(MethodNode method:node.methods)if(names.contains(method.name)&&descriptor.equals(method.desc))return method;return null;}
    private static LabelNode switchCase(MethodNode method,int value){for(AbstractInsnNode insn:method.instructions){if(insn instanceof TableSwitchInsnNode table&&value>=table.min&&value<=table.max)return table.labels.get(value-table.min);if(insn instanceof LookupSwitchInsnNode lookup){int index=lookup.keys.indexOf(value);if(index>=0)return lookup.labels.get(index);}}return null;}
    private static Set<LabelNode> switchLabels(MethodNode method){Set<LabelNode> values=Collections.newSetFromMap(new IdentityHashMap<>());for(AbstractInsnNode insn:method.instructions){if(insn instanceof TableSwitchInsnNode table){values.add(table.dflt);values.addAll(table.labels);}else if(insn instanceof LookupSwitchInsnNode lookup){values.add(lookup.dflt);values.addAll(lookup.labels);}}return values;}
    private static boolean hasOpcode(MethodNode method,int opcode){for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()==opcode)return true;return false;}
    private static boolean containsFloat(MethodNode method,float value){return countFloat(method,value)>0;}
    private static int countFloat(MethodNode method,float value){int count=0;for(AbstractInsnNode insn:method.instructions){Float found=floatConstant(insn);if(found!=null&&Float.compare(found,value)==0)count++;}return count;}
    private static boolean containsDouble(MethodNode method,double value){for(AbstractInsnNode insn:method.instructions)if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Double d&&Double.compare(d,value)==0)return true;return false;}
    private static Integer intConstant(AbstractInsnNode insn){return switch(insn.getOpcode()){case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer value?value:null;default->null;};}
    private static Float floatConstant(AbstractInsnNode insn){return switch(insn.getOpcode()){case Opcodes.FCONST_0->0F;case Opcodes.FCONST_1->1F;case Opcodes.FCONST_2->2F;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Float value?value:null;default->null;};}
    private static List<AbstractInsnNode> real(MethodNode method){List<AbstractInsnNode> result=new ArrayList<>();for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)result.add(insn);return result;}

    private static Map<String,ClassNode> loadClasses(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=new LinkedHashMap<>();
        try(JarFile jar=new JarFile(jarPath.toFile(),false)){
            var entries=jar.entries();while(entries.hasMoreElements()){
                JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class"))continue;
                try(InputStream input=jar.getInputStream(entry)){
                    ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);
                }catch(RuntimeException ignored){ }
            }
        }
        return classes;
    }
}
