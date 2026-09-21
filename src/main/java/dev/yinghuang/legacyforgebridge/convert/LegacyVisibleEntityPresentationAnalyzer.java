package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Bounded source proof for visible legacy Entity renderer families that can be replayed without
 * loading source classes. Admission is structural: lifecycle identity, renderer binding, model
 * cuboids, transforms, watcher selectors and texture/palette tables must all agree.
 */
public final class LegacyVisibleEntityPresentationAnalyzer {
    private static final String MODEL_BASE="net/minecraft/client/model/ModelBase";
    private static final String MODEL_RENDERER="net/minecraft/client/model/ModelRenderer";
    private static final String DATA_WATCHER="net/minecraft/entity/DataWatcher";
    private static final String ITEM_STACK="net/minecraft/item/ItemStack";
    private static final String RESOURCE="net/minecraft/util/ResourceLocation";
    private static final String WORLD_CTOR="(Lnet/minecraft/world/World;)V";

    public enum Adapter { SLIDE_PANEL, TINTED_CUSHION, TRAY_ITEMS }

    public record Part(String field,int u,int v,float x,float y,float z,int width,int height,int depth,
                       float pivotX,float pivotY,float pivotZ,float xRot,float yRot,float zRot,boolean mirror) { }
    public record TextureVariant(int value,String texture,boolean translucent) { }
    public record Rule(String registryName,String sourceClass,String rendererClass,Adapter adapter,
                       int legacyNumericId,int trackingRange,int updateFrequency,boolean velocityUpdates,
                       float width,float height,int modelTextureWidth,int modelTextureHeight,List<Part> parts,
                       String fixedTexture,Map<String,Integer> watcherIndices,List<TextureVariant> textureVariants,
                       List<Integer> palette,int itemWatcherBase,int itemWatcherCount,String proof) {
        public Rule {
            parts=List.copyOf(parts);watcherIndices=Map.copyOf(watcherIndices);
            textureVariants=List.copyOf(textureVariants);palette=List.copyOf(palette);
        }
    }
    public record Analysis(List<Rule> rules,List<String> diagnostics) {
        public Analysis { rules=List.copyOf(rules);diagnostics=List.copyOf(diagnostics); }
    }
    private record Registration(String name,String sourceClass,int numericId,int tracking,int update,boolean velocity) { }
    private record ModelProof(int textureWidth,int textureHeight,List<Part> parts) { }

    private final Map<String,ClassNode> classes=new LinkedHashMap<>();
    private final List<String> diagnostics=new ArrayList<>();
    private Path source;

    public Analysis analyze(Path sourceJar)throws IOException{
        source=sourceJar;classes.clear();diagnostics.clear();load(sourceJar);
        Map<String,List<LegacyEntityPresentationAnalyzer.Registration>> renderers=new LinkedHashMap<>();
        var presentation=new LegacyEntityPresentationAnalyzer().analyze(sourceJar);
        diagnostics.addAll(presentation.diagnostics());
        for(var value:presentation.registrations())renderers.computeIfAbsent(value.entityClass(),ignored->new ArrayList<>()).add(value);

        List<Rule> out=new ArrayList<>();
        for(Registration registration:registrations(sourceJar)){
            List<LegacyEntityPresentationAnalyzer.Registration> bindings=renderers.getOrDefault(registration.sourceClass(),List.of());
            if(bindings.size()!=1){continue;}
            String renderer=bindings.getFirst().rendererClass();
            ClassNode rendererNode=classes.get(renderer);ClassNode entity=classes.get(registration.sourceClass());
            if(rendererNode==null||entity==null)continue;
            float[] size=directSize(entity);if(size==null)continue;
            Rule rule=proveSlidePanel(registration,rendererNode,entity,size);
            if(rule==null)rule=proveTintedCushion(registration,rendererNode,entity,size);
            if(rule==null)rule=proveTray(registration,rendererNode,entity,size);
            if(rule!=null)out.add(rule);
        }
        out.sort(Comparator.comparing(Rule::registryName));
        return new Analysis(out,List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private Rule proveSlidePanel(Registration reg,ClassNode renderer,ClassNode entity,float[] size)throws IOException{
        String modelType=uniqueModelType(renderer);if(modelType==null)return null;
        ModelProof model=parseModel(classes.get(modelType));if(model==null||model.parts().size()!=1||model.textureWidth()!=64||model.textureHeight()!=64)return null;
        Part part=model.parts().getFirst();
        if(!partShape(part,0,0,0,16,32,2,8,16,-1,0,0,(float)Math.PI))return null;
        MethodNode render=renderMethod(renderer,entity.name);if(render==null||!containsFloat(render,.999375F)||!containsFloat(render,90F)||!containsFloat(render,.0625F)
                ||!calls(render,"org/lwjgl/opengl/GL11","glScalef","(FFF)V")
                ||!calls(render,"org/lwjgl/opengl/GL11","glTranslatef","(FFF)V")
                ||!calls(render,"org/lwjgl/opengl/GL11","glRotatef","(FFFF)V"))return null;

        Integer direction=null,mirror=null,doorId=null;
        String textureMethod=null;
        for(AbstractInsnNode insn:render.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(entity.name)){
            if(call.desc.equals("()B")){
                Integer value=watcherIndex(entity,call.name,call.desc,"(I)B",0,new HashSet<>());
                if(value!=null){if(direction!=null&&!direction.equals(value))return null;direction=value;}
            }else if(call.desc.equals("()Z")){
                Integer direct=watcherIndex(entity,call.name,call.desc,"(I)B",0,new HashSet<>());
                if(direct!=null){if(mirror!=null&&!mirror.equals(direct))return null;mirror=direct;}
                Integer nested=watcherIndex(entity,call.name,call.desc,"(I)S",0,new HashSet<>());
                if(nested!=null)doorId=nested;
            }else if(call.desc.equals("()Ljava/lang/String;")){
                textureMethod=call.name;
                Integer nested=watcherIndex(entity,call.name,call.desc,"(I)S",0,new HashSet<>());
                if(nested!=null)doorId=nested;
            }
        }
        if(direction==null||mirror==null||doorId==null||textureMethod==null||direction.equals(mirror)||doorId.equals(direction)||doorId.equals(mirror))return null;
        String enumType=enumTypeReach(entity,textureMethod,"()Ljava/lang/String;",0,new HashSet<>());
        List<TextureVariant> variants=parseTextureEnum(enumType);
        if(variants.size()<2||variants.stream().anyMatch(v->!resourceExists(v.texture())))return null;
        Map<String,Integer> watchers=new LinkedHashMap<>();watchers.put("direction",direction);watchers.put("mirror",mirror);watchers.put("texture",doorId);
        return new Rule(reg.name(),reg.sourceClass(),renderer.name,Adapter.SLIDE_PANEL,reg.numericId(),reg.tracking(),reg.update(),reg.velocity(),
                size[0],size[1],model.textureWidth(),model.textureHeight(),model.parts(),null,watchers,variants,List.of(),-1,0,
                "Source-bound dual-mirror single-cuboid renderer; direction/mirror/texture watchers and enum texture/translucency table proven");
    }

    private Rule proveTintedCushion(Registration reg,ClassNode renderer,ClassNode entity,float[] size)throws IOException{
        String modelType=uniqueModelType(renderer);if(modelType==null)return null;
        ModelProof model=parseModel(classes.get(modelType));if(model==null||model.parts().size()!=1)return null;
        Part part=model.parts().getFirst();
        if(!partShape(part,0,0,0,14,2,14,7,2,-7,0,0,(float)Math.PI))return null;
        MethodNode render=renderMethod(renderer,entity.name);if(render==null||!containsFloat(render,180F)||!containsFloat(render,.0625F)
                ||!calls(render,"org/lwjgl/opengl/GL11","glColor3f","(FFF)V")
                ||!calls(render,"org/lwjgl/opengl/GL11","glRotatef","(FFFF)V"))return null;
        String texture=fixedTexture(renderer);if(texture==null||!resourceExists(texture))return null;
        Integer color=null;String colorEnum=null;
        for(AbstractInsnNode insn:render.instructions)if(insn instanceof MethodInsnNode call){
            if(call.desc.equals("()B")){
                MethodNode sourceMethod=effectiveMethod(entity.name,call.name,call.desc);
                Integer value=sourceMethod==null?null:directWatcherIndex(sourceMethod,"(I)B");
                if(value!=null){if(color!=null&&!color.equals(value))return null;color=value;}
            }
            ClassNode owner=classes.get(call.owner);if(owner!=null&&"java/lang/Enum".equals(owner.superName)&&Type.getReturnType(call.desc).getSort()==Type.OBJECT)
                colorEnum=call.owner;
        }
        List<Integer> palette=parseColorEnum(colorEnum);
        if(color==null||palette.size()!=16)return null;
        Map<String,Integer> watchers=Map.of("color",color);
        return new Rule(reg.name(),reg.sourceClass(),renderer.name,Adapter.TINTED_CUSHION,reg.numericId(),reg.tracking(),reg.update(),reg.velocity(),
                size[0],size[1],model.textureWidth(),model.textureHeight(),model.parts(),texture,watchers,List.of(),palette,-1,0,
                "Source-bound single-cuboid renderer with fixed texture, entity yaw/pitch transform and 16-entry watcher-selected source palette");
    }

    private Rule proveTray(Registration reg,ClassNode renderer,ClassNode entity,float[] size)throws IOException{
        String modelType=uniqueModelType(renderer);if(modelType==null)return null;
        ModelProof model=parseModel(classes.get(modelType));if(model==null||model.parts().size()<5||model.textureWidth()!=64||model.textureHeight()!=32)return null;
        MethodNode render=renderMethod(renderer,entity.name);if(render==null||!containsFloat(render,.2F)||!containsFloat(render,.7F)||!containsFloat(render,180F)||!containsFloat(render,.0625F)
                ||!calls(render,"org/lwjgl/opengl/GL11","glScalef","(FFF)V")||!calls(render,"org/lwjgl/opengl/GL11","glRotatef","(FFFF)V"))return null;
        String itemGetter=null;
        for(AbstractInsnNode insn:render.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(entity.name)
                &&call.desc.equals("(I)Lnet/minecraft/item/ItemStack;"))itemGetter=call.name;
        if(itemGetter==null)return null;
        MethodNode getter=effectiveMethod(entity.name,itemGetter,"(I)Lnet/minecraft/item/ItemStack;");int[] range=itemWatcherRange(getter);
        if(range==null||range[1]!=5||!provesItemWatcherDefinitions(entity,range[0],range[1]))return null;
        String texture=fixedTexture(renderer);if(texture==null||!resourceExists(texture))return null;
        return new Rule(reg.name(),reg.sourceClass(),renderer.name,Adapter.TRAY_ITEMS,reg.numericId(),reg.tracking(),reg.update(),reg.velocity(),
                size[0],size[1],model.textureWidth(),model.textureHeight(),model.parts(),texture,Map.of(),List.of(),List.of(),range[0],range[1],
                "Source-bound fixed multi-cuboid tray renderer plus five consecutive legacy ItemStack watcher slots and radial modern-item presentation adapter");
    }

    private List<Registration> registrations(Path jar)throws IOException{
        List<Registration> out=new ArrayList<>();
        for(var r:new LegacyLifecycleAnalyzer().analyze(jar).of(LegacyLifecycleAnalyzer.Kind.ENTITY)){
            var a=r.arguments();if(a.size()<7||!(a.get(0) instanceof LegacyLifecycleAnalyzer.TypeValue type)
                    ||!(a.get(1) instanceof LegacyLifecycleAnalyzer.TextValue name))continue;
            Integer id=integer(a.get(2)),tracking=integer(a.get(4)),update=integer(a.get(5));Boolean velocity=bool(a.get(6));
            if(id==null||tracking==null||update==null||velocity==null||id<0||tracking<=0||update<=0)continue;
            out.add(new Registration(name.value(),type.internalName(),id,tracking,update,velocity));
        }
        return out;
    }
    private static Integer integer(LegacyLifecycleAnalyzer.Value v){if(!(v instanceof LegacyLifecycleAnalyzer.NumberValue n))return null;double d=n.value().doubleValue();return Double.isFinite(d)&&d==Math.rint(d)&&d>=Integer.MIN_VALUE&&d<=Integer.MAX_VALUE?(int)d:null;}
    private static Boolean bool(LegacyLifecycleAnalyzer.Value v){
        if(v instanceof LegacyLifecycleAnalyzer.BooleanValue b)return b.value();
        Integer i=integer(v);if(i==null||i<0||i>1)return null;return i==1;
    }

    private float[] directSize(ClassNode entity){
        MethodNode ctor=find(entity,"<init>",WORLD_CTOR);if(ctor==null)return null;float[] result=null;
        List<AbstractInsnNode> code=real(ctor);
        for(int i=2;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&Set.of("setSize","func_70105_a").contains(call.name)&&call.desc.equals("(FF)V")){
            Float w=floatConst(code.get(i-2)),h=floatConst(code.get(i-1));if(w==null||h==null||w<=0||h<=0)return null;
            if(result!=null&&(Float.compare(result[0],w)!=0||Float.compare(result[1],h)!=0))return null;result=new float[]{w,h};
        }
        return result;
    }

    private String uniqueModelType(ClassNode renderer){
        MethodNode ctor=find(renderer,"<init>","()V");if(ctor==null)return null;LinkedHashSet<String> models=new LinkedHashSet<>();
        for(AbstractInsnNode insn:ctor.instructions)if(insn instanceof TypeInsnNode t&&t.getOpcode()==Opcodes.NEW&&inherits(t.desc,MODEL_BASE))models.add(t.desc);
        return models.size()==1?models.getFirst():null;
    }

    private ModelProof parseModel(ClassNode model){
        if(model==null||!inherits(model.name,MODEL_BASE))return null;
        MethodNode ctor=model.methods.stream().filter(m->m.name.equals("<init>")).max(Comparator.comparingInt(m->m.instructions.size())).orElse(null);
        if(ctor==null)return null;
        int tw=64,th=32;List<AbstractInsnNode> code=real(ctor);
        for(int i=1;i<code.size();i++)if(code.get(i) instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.PUTFIELD&&"I".equals(f.desc)){
            Integer value=intConst(code.get(i-1));if(value==null)continue;
            if(Set.of("textureWidth","field_78090_t").contains(f.name))tw=value;
            if(Set.of("textureHeight","field_78089_u").contains(f.name))th=value;
        }
        Map<String,MutablePart> parts=new LinkedHashMap<>();
        for(FieldNode f:model.fields)if(("L"+MODEL_RENDERER+";").equals(f.desc)&&(f.access&Opcodes.ACC_STATIC)==0)parts.put(f.name,new MutablePart(f.name));
        for(int i=0;i<code.size();i++){
            AbstractInsnNode insn=code.get(i);
            if(insn instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(model.name)&&parts.containsKey(put.name)){
                for(int j=Math.max(0,i-14);j<i;j++)if(code.get(j) instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&call.name.equals("<init>")&&call.desc.equals("(Lnet/minecraft/client/model/ModelBase;II)V")){
                    Integer u=intConst(code.get(j-2)),v=intConst(code.get(j-1));if(u!=null&&v!=null){parts.get(put.name).u=u;parts.get(put.name).v=v;}
                }
                for(int j=Math.max(0,i-12);j<i;j++)if(code.get(j) instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&call.desc.equals("(FFFIII)Lnet/minecraft/client/model/ModelRenderer;"))
                    assignBox(parts.get(put.name),code,j);
            }
            if(insn instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&call.desc.equals("(FFFIII)Lnet/minecraft/client/model/ModelRenderer;")){
                FieldInsnNode recv=fieldAt(code,i-7,model.name);if(recv!=null&&parts.containsKey(recv.name))assignBox(parts.get(recv.name),code,i);
            }
            if(insn instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&call.desc.equals("(FFF)V")){
                FieldInsnNode recv=fieldAt(code,i-4,model.name);if(recv!=null&&parts.containsKey(recv.name)){
                    Float x=floatConst(code.get(i-3)),y=floatConst(code.get(i-2)),z=floatConst(code.get(i-1));if(x!=null&&y!=null&&z!=null){var p=parts.get(recv.name);p.px=x;p.py=y;p.pz=z;}
                }
            }
            if(insn instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(MODEL_RENDERER)&&i>=2){
                FieldInsnNode recv=fieldAt(code,i-2,model.name);if(recv==null||!parts.containsKey(recv.name))continue;var p=parts.get(recv.name);
                Float value=floatConst(code.get(i-1));
                if(value!=null&&"F".equals(put.desc)){
                    if(Set.of("rotateAngleX","field_78795_f").contains(put.name))p.rx=value;
                    else if(Set.of("rotateAngleY","field_78796_g").contains(put.name))p.ry=value;
                    else if(Set.of("rotateAngleZ","field_78808_h").contains(put.name))p.rz=value;
                }
                Integer iv=intConst(code.get(i-1));if(iv!=null&&"Z".equals(put.desc)&&Set.of("mirror","field_78809_i").contains(put.name))p.mirror=iv!=0;
            }
        }
        LinkedHashSet<String> rendered=new LinkedHashSet<>();
        for(MethodNode m:model.methods)if(m.desc.equals("(Lnet/minecraft/entity/Entity;FFFFFF)V")){
            List<AbstractInsnNode> mc=real(m);for(int i=1;i<mc.size();i++)if(mc.get(i) instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&call.desc.equals("(F)V")){
                FieldInsnNode f=fieldAt(mc,i-2,model.name);if(f!=null)rendered.add(f.name);
            }
        }
        if(rendered.isEmpty())return null;List<Part> out=new ArrayList<>();
        for(String name:rendered){MutablePart p=parts.get(name);if(p==null||p.u==null||p.v==null||!p.box)return null;out.add(p.build());}
        return new ModelProof(tw,th,out);
    }

    private static void assignBox(MutablePart p,List<AbstractInsnNode> code,int i){
        if(p==null||i<7)return;Float x=floatConst(code.get(i-6)),y=floatConst(code.get(i-5)),z=floatConst(code.get(i-4));
        Integer w=intConst(code.get(i-3)),h=intConst(code.get(i-2)),d=intConst(code.get(i-1));
        if(x!=null&&y!=null&&z!=null&&w!=null&&h!=null&&d!=null){p.x=x;p.y=y;p.z=z;p.w=w;p.h=h;p.d=d;p.box=true;}
    }
    private static final class MutablePart{
        final String field;Integer u,v;float x,y,z,px,py,pz,rx,ry,rz;int w,h,d;boolean box,mirror;
        MutablePart(String f){field=f;}Part build(){return new Part(field,u,v,x,y,z,w,h,d,px,py,pz,rx,ry,rz,mirror);}
    }

    private MethodNode renderMethod(ClassNode renderer,String entityType){
        MethodNode best=null;int bestScore=-1;boolean tie=false;
        for(MethodNode method:renderer.methods){
            if((method.access&(Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0
                    ||Type.getReturnType(method.desc).getSort()!=Type.VOID)continue;
            Type[] args=Type.getArgumentTypes(method.desc);if(args.length!=6||args[0].getSort()!=Type.OBJECT)continue;
            String first=args[0].getInternalName();
            if(!first.equals(entityType)&&!first.equals("net/minecraft/entity/Entity"))continue;
            int score=0;
            for(AbstractInsnNode insn:method.instructions){
                if(insn instanceof MethodInsnNode call){
                    if(call.owner.equals("org/lwjgl/opengl/GL11"))score+=8;
                    if(call.owner.equals(entityType))score+=5;
                    if(classes.containsKey(call.owner)&&inherits(call.owner,MODEL_BASE))score+=4;
                }
                if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Float)score++;
            }
            score+=Math.min(32,real(method).size()/8);
            if(score>bestScore){best=method;bestScore=score;tie=false;}
            else if(score==bestScore&&score>0)tie=true;
        }
        return bestScore>0&&!tie?best:null;
    }
    private String fixedTexture(ClassNode renderer){
        LinkedHashSet<String> values=new LinkedHashSet<>();
        for(MethodNode m:renderer.methods)for(AbstractInsnNode i:m.instructions)if(i instanceof LdcInsnNode l&&l.cst instanceof String s&&s.contains(":")&&s.endsWith(".png"))values.add(s);
        return values.size()==1?values.getFirst():null;
    }
    private boolean resourceExists(String id)throws IOException{
        int colon=id.indexOf(':');if(colon<=0||colon==id.length()-1)return false;String entry="assets/"+id.substring(0,colon)+"/"+id.substring(colon+1);
        try(JarFile jar=new JarFile(source.toFile(),false)){return jar.getJarEntry(entry)!=null;}
    }

    private Integer watcherIndex(ClassNode entity,String name,String desc,String getterDesc,int depth,Set<String> guard){
        if(depth>8)return null;String key=entity.name+"."+name+desc;if(!guard.add(key))return null;MethodNode method=effectiveMethod(entity.name,name,desc);if(method==null){guard.remove(key);return null;}
        Integer direct=directWatcherIndex(method,getterDesc);if(direct!=null){guard.remove(key);return direct;}
        LinkedHashSet<Integer> found=new LinkedHashSet<>();
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&classes.containsKey(call.owner)){
            ClassNode owner=classes.get(call.owner);Integer nested=watcherIndex(owner,call.name,call.desc,getterDesc,depth+1,guard);if(nested!=null)found.add(nested);
        }
        guard.remove(key);return found.size()==1?found.getFirst():null;
    }
    private static Integer directWatcherIndex(MethodNode method,String getterDesc){
        List<AbstractInsnNode> code=real(method);
        for(int i=1;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals(DATA_WATCHER)&&call.desc.equals(getterDesc)){
            Integer value=intConst(code.get(i-1));if(value!=null)return value;
        }return null;
    }
    private String enumTypeReach(ClassNode owner,String name,String desc,int depth,Set<String> guard){
        if(depth>8)return null;String key=owner.name+"."+name+desc;if(!guard.add(key))return null;MethodNode method=find(owner,name,desc);if(method==null){guard.remove(key);return null;}
        LinkedHashSet<String> enums=new LinkedHashSet<>();
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call){
            ClassNode target=classes.get(call.owner);if(target!=null&&"java/lang/Enum".equals(target.superName))enums.add(target.name);
            if(target!=null){String nested=enumTypeReach(target,call.name,call.desc,depth+1,guard);if(nested!=null)enums.add(nested);}
        }
        guard.remove(key);return enums.size()==1?enums.getFirst():null;
    }

    private List<TextureVariant> parseTextureEnum(String type){
        ClassNode node=classes.get(type);if(node==null||!"java/lang/Enum".equals(node.superName))return List.of();MethodNode clinit=find(node,"<clinit>","()V"),ctor=node.methods.stream().filter(m->m.name.equals("<init>")).findFirst().orElse(null);if(clinit==null||ctor==null)return List.of();
        String prefix=null,suffix=null;for(AbstractInsnNode i:ctor.instructions)if(i instanceof LdcInsnNode l&&l.cst instanceof String s){if(s.contains("/")&&s.contains(":"))prefix=s;if(s.startsWith("."))suffix=s;}
        if(prefix==null||suffix==null)return List.of();
        List<TextureVariant> out=new ArrayList<>();List<AbstractInsnNode> c=real(clinit);
        for(int i=5;i<c.size();i++)if(c.get(i) instanceof MethodInsnNode call&&call.name.equals("<init>")&&call.owner.equals(type)&&call.desc.endsWith("ILjava/lang/String;Z)V")){
            Integer id=intConst(c.get(i-3)),blend=intConst(c.get(i-1));String icon=stringConst(c.get(i-2));
            if(id!=null&&blend!=null&&icon!=null&&(blend==0||blend==1))out.add(new TextureVariant(id,prefix+icon.toLowerCase(Locale.ROOT)+suffix,blend==1));
        }
        out.sort(Comparator.comparingInt(TextureVariant::value));return List.copyOf(out);
    }
    private List<Integer> parseColorEnum(String type){
        ClassNode node=classes.get(type);if(node==null||!"java/lang/Enum".equals(node.superName))return List.of();MethodNode clinit=find(node,"<clinit>","()V");if(clinit==null)return List.of();
        List<Integer> out=new ArrayList<>();List<AbstractInsnNode> c=real(clinit);
        for(int i=3;i<c.size();i++)if(c.get(i) instanceof MethodInsnNode call&&call.name.equals("<init>")&&call.owner.equals(type)&&call.desc.endsWith("II)V")){
            Integer rgb=intConst(c.get(i-1));if(rgb!=null&&rgb>=0&&rgb<=0xFFFFFF)out.add(rgb);
        }
        return out.size()==16?List.copyOf(out):List.of();
    }

    private int[] itemWatcherRange(MethodNode method){
        if(method==null)return null;List<AbstractInsnNode> c=real(method);Integer base=null,count=null;
        for(int i=3;i<c.size();i++){
            if(c.get(i) instanceof MethodInsnNode call&&call.owner.equals(DATA_WATCHER)&&call.desc.equals("(I)Lnet/minecraft/item/ItemStack;")
                    &&c.get(i-1).getOpcode()==Opcodes.IADD&&c.get(i-2) instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ILOAD){
                Integer b=intConst(c.get(i-3));if(b!=null)base=b;
            }
            if(c.get(i) instanceof JumpInsnNode j&&Set.of(Opcodes.IF_ICMPGE,Opcodes.IF_ICMPGT,Opcodes.IF_ICMPLE,Opcodes.IF_ICMPLT).contains(j.getOpcode())
                    &&c.get(i-2) instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ILOAD&&v.var==1){
                Integer n=intConst(c.get(i-1));if(n!=null)count=n;
            }
        }
        return base!=null&&count!=null&&base>=0&&base+count<=32?new int[]{base,count}:null;
    }
    private boolean provesItemWatcherDefinitions(ClassNode entity,int base,int count){
        MethodNode init=effectiveMethod(entity.name,"func_70088_a","()V");if(init==null)init=effectiveMethod(entity.name,"entityInit","()V");if(init==null)return false;
        List<AbstractInsnNode> c=real(init);boolean add=false,baseSeen=false,countSeen=false;
        for(int i=0;i<c.size();i++){
            Integer v=intConst(c.get(i));if(Objects.equals(v,base))baseSeen=true;if(Objects.equals(v,count))countSeen=true;
            if(c.get(i) instanceof MethodInsnNode call&&call.owner.equals(DATA_WATCHER)&&Set.of("addObject","func_75682_a").contains(call.name)
                    &&call.desc.equals("(ILjava/lang/Object;)V")){
                for(int j=Math.max(0,i-8);j<i;j++)if(c.get(j) instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETSTATIC&&("L"+ITEM_STACK+";").equals(f.desc))add=true;
            }
        }
        return add&&baseSeen&&countSeen;
    }

    private MethodNode effectiveMethod(String type,String name,String desc){
        Set<String> seen=new HashSet<>();while(type!=null&&seen.add(type)){ClassNode n=classes.get(type);if(n==null)return null;MethodNode m=find(n,name,desc);if(m!=null)return m;type=n.superName;}return null;
    }
    private boolean inherits(String type,String target){Set<String> seen=new HashSet<>();while(type!=null&&seen.add(type)){if(type.equals(target))return true;ClassNode n=classes.get(type);type=n==null?null:n.superName;}return false;}
    private static boolean partShape(Part p,float x,float y,float z,int w,int h,int d,float px,float py,float pz,float rx,float ry,float rz){
        return Float.compare(p.x(),x)==0&&Float.compare(p.y(),y)==0&&Float.compare(p.z(),z)==0&&p.width()==w&&p.height()==h&&p.depth()==d
                &&Float.compare(p.pivotX(),px)==0&&Float.compare(p.pivotY(),py)==0&&Float.compare(p.pivotZ(),pz)==0
                &&Float.compare(p.xRot(),rx)==0&&Float.compare(p.yRot(),ry)==0&&Math.abs(p.zRot()-rz)<1e-5F;
    }
    private static boolean containsFloat(MethodNode m,float wanted){for(AbstractInsnNode i:m.instructions){Float f=floatConst(i);if(f!=null&&Float.compare(f,wanted)==0)return true;}return false;}
    private static boolean calls(MethodNode m,String owner,String name,String desc){for(AbstractInsnNode i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals(owner)&&c.name.equals(name)&&c.desc.equals(desc))return true;return false;}
    private static MethodNode find(ClassNode n,String name,String desc){if(n==null)return null;for(MethodNode m:n.methods)if(m.name.equals(name)&&m.desc.equals(desc))return m;return null;}
    private static FieldInsnNode fieldAt(List<AbstractInsnNode> c,int i,String owner){return i>=0&&i<c.size()&&c.get(i) instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&f.owner.equals(owner)?f:null;}
    private static List<AbstractInsnNode> real(MethodNode m){List<AbstractInsnNode> out=new ArrayList<>();if(m!=null)for(AbstractInsnNode i:m.instructions)if(i.getOpcode()>=0)out.add(i);return out;}
    private static String stringConst(AbstractInsnNode i){return i instanceof LdcInsnNode l&&l.cst instanceof String s?s:null;}
    private static Float floatConst(AbstractInsnNode i){if(i==null)return null;return switch(i.getOpcode()){case Opcodes.FCONST_0->0F;case Opcodes.FCONST_1->1F;case Opcodes.FCONST_2->2F;case Opcodes.LDC->i instanceof LdcInsnNode l&&l.cst instanceof Float f?f:null;default->null;};}
    private static Integer intConst(AbstractInsnNode i){if(i==null)return null;return switch(i.getOpcode()){case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)i).operand;case Opcodes.LDC->i instanceof LdcInsnNode l&&l.cst instanceof Integer v?v:null;default->null;};}

    private void load(Path jarPath)throws IOException{
        try(JarFile jar=new JarFile(jarPath.toFile(),false)){var entries=jar.entries();while(entries.hasMoreElements()){
            JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;
            try(InputStream in=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(in).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}
            catch(RuntimeException malformed){diagnostics.add("Unreadable visible-entity class "+entry.getName()+": "+malformed.getClass().getSimpleName());}
        }}
    }
}
