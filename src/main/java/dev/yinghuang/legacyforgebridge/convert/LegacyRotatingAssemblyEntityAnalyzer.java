package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Source-only proof for large legacy Entity assemblies rendered as rotating radial cuboids.
 *
 * <p>The family is structural: complete inherited byte DataWatcher schema, source renderer/model
 * binding, custom AABB mutation, player-removable collision, client-local roll, and one of two
 * bounded ModelRenderer radial programs must all agree. No registry or class name allowlist is
 * used.</p>
 */
public final class LegacyRotatingAssemblyEntityAnalyzer {
    private static final String ENTITY="net/minecraft/entity/Entity";
    private static final String MODEL_BASE="net/minecraft/client/model/ModelBase";
    private static final String MODEL_RENDERER="net/minecraft/client/model/ModelRenderer";
    private static final String DATA_WATCHER="net/minecraft/entity/DataWatcher";
    private static final Set<String> WATCH_BYTE=Set.of("getWatchableObjectByte","func_75683_a");
    private static final Set<String> RENDER=Set.of("render","func_78785_a");
    private static final Set<String> X_ROT=Set.of("rotateAngleX","field_78795_f");
    private static final Set<String> Y_ROT=Set.of("rotateAngleY","field_78796_g");
    private static final Set<String> Z_ROT=Set.of("rotateAngleZ","field_78808_h");

    public enum Adapter { VARIABLE_Z_RADIAL, FLUID_X_RADIAL }

    public record Cuboid(String field,int u,int v,float x,float y,float z,int width,int height,int depth,
                         float pivotX,float pivotY,float pivotZ,float xRot,float yRot,float zRot,boolean mirror){
        public Cuboid{
            if(field==null||field.isBlank()||u<0||v<0||width<=0||height<=0||depth<=0
                    ||!finite(x,y,z,pivotX,pivotY,pivotZ,xRot,yRot,zRot))
                throw new IllegalArgumentException("Invalid rotating assembly cuboid");
        }
    }
    public record Rule(String registryName,String sourceClass,String rendererClass,String sourceModelClass,
                       int legacyNumericId,int trackingRange,int updateFrequency,boolean velocityUpdates,
                       Adapter adapter,List<Cuboid> staticParts,List<Cuboid> repeatedPrimary,List<Cuboid> repeatedSecondary,
                       int directionWatcher,int sizeWatcher,int countWatcher,int textureWatcher,int reverseWatcher,
                       int directionDefault,int sizeDefault,int sizeMin,int sizeMax,int countDefault,int textureDefault,int reverseDefault,
                       int countBase,int countMax,int fixedRepeatCount,float secondaryPhaseDegrees,float modelScale,
                       List<String> textures,boolean physicalCollision,boolean playerAttackRemoves,boolean randomInitialPhase){
        public Rule{
            staticParts=List.copyOf(staticParts);repeatedPrimary=List.copyOf(repeatedPrimary);
            repeatedSecondary=List.copyOf(repeatedSecondary);textures=List.copyOf(textures);
            if(registryName==null||registryName.isBlank()||sourceClass==null||rendererClass==null||sourceModelClass==null
                    ||legacyNumericId<0||trackingRange<=0||updateFrequency<=0||adapter==null
                    ||staticParts.isEmpty()||repeatedPrimary.isEmpty()||directionWatcher<0||sizeWatcher<0
                    ||directionDefault<0||sizeDefault<=0||sizeMin<=0||sizeMax<sizeMin||sizeDefault<sizeMin||sizeDefault>sizeMax
                    ||modelScale<=0F||textures.isEmpty()||!physicalCollision||!playerAttackRemoves||!randomInitialPhase)
                throw new IllegalArgumentException("Invalid rotating assembly rule");
            if(adapter==Adapter.VARIABLE_Z_RADIAL&&(countWatcher<0||textureWatcher<0||countDefault<0||textureDefault<0||countBase<=0||countMax<countBase||textures.size()<2))
                throw new IllegalArgumentException("Incomplete variable radial assembly rule");
            if(adapter==Adapter.FLUID_X_RADIAL&&(reverseWatcher<0||reverseDefault<0||fixedRepeatCount<=1||!repeatedSecondary.isEmpty()&&secondaryPhaseDegrees==0F))
                throw new IllegalArgumentException("Incomplete fluid radial assembly rule");
        }
    }
    public record Skipped(String registryName,String sourceClass,String reason){}
    public record Analysis(List<Rule> rules,List<Skipped> skipped,List<String> diagnostics){
        public Analysis{rules=List.copyOf(rules);skipped=List.copyOf(skipped);diagnostics=List.copyOf(diagnostics);}
    }

    private record MutablePart(String field,int u,int v,float x,float y,float z,int width,int height,int depth,
                               float pivotX,float pivotY,float pivotZ,float xRot,float yRot,float zRot,boolean mirror){}
    private record CountProof(int watcherIndex,int base){}
    private record IntBounds(int min,int max){
        private IntBounds{if(max<min)throw new IllegalArgumentException("Invalid watcher bounds");}
    }
    private record Loop(int start,int end,int variable,Integer fixedCount,String countField){}

    private final Map<String,ClassNode> classes=new LinkedHashMap<>();
    private final List<String> diagnostics=new ArrayList<>();
    private Path sourceJar;

    public Analysis analyze(Path jar)throws IOException{
        sourceJar=jar;classes.clear();diagnostics.clear();load(jar);
        var watcherAnalysis=new LegacyEntityDataWatcherAnalyzer().analyze(jar);
        var presentation=new LegacyEntityPresentationAnalyzer().analyze(jar);
        diagnostics.addAll(watcherAnalysis.diagnostics());diagnostics.addAll(presentation.diagnostics());
        Map<String,List<LegacyEntityPresentationAnalyzer.Registration>> bindings=new LinkedHashMap<>();
        for(var value:presentation.registrations())bindings.computeIfAbsent(value.entityClass(),ignored->new ArrayList<>()).add(value);

        List<Rule> rules=new ArrayList<>();List<Skipped> skipped=new ArrayList<>();
        for(var watcher:watcherAnalysis.rules()){
            if(watcher.velocityUpdates())continue;
            List<LegacyEntityPresentationAnalyzer.Registration> entityBindings=bindings.getOrDefault(watcher.sourceClass(),List.of());
            if(entityBindings.size()!=1)continue;
            String renderer=entityBindings.getFirst().rendererClass();ClassNode rendererNode=classes.get(renderer);
            if(rendererNode==null)continue;
            String modelName=uniqueModelType(rendererNode);ClassNode model=classes.get(modelName);
            if(model==null)continue;

            int direction=inheritedByteWatcher(watcher);if(direction<0)continue;
            MethodNode renderMethod=entityRenderMethod(rendererNode,watcher.sourceClass());
            if(renderMethod==null)continue;
            int size=dominantDirectByteWatcher(renderMethod,watcher.sourceClass(),direction);
            if(size<0)continue;
            IntBounds sizeBounds=watcherMutationBounds(watcher.sourceClass(),size);
            if(sizeBounds==null||sizeBounds.min()<=0)continue;
            String rollGetter=rollGetter(renderMethod,watcher.sourceClass());String rollField=rollGetter==null?null:returnedFloatField(watcher.sourceClass(),rollGetter);
            if(rollField==null||!randomInitialRoll(watcher.sourceClass(),rollField))continue;
            if(!provesPhysicalCollision(watcher.sourceClass())||!provesPlayerAttackRemoval(watcher.sourceClass()))continue;
            if(!aabbFamily(watcher.sourceClass(),size,direction))continue;

            List<MutablePart> parts=parseParts(model);if(parts.isEmpty())continue;
            float scale=modelScale(renderMethod,modelName);if(!(scale>0F))continue;
            List<String> textures=rendererTextures(rendererNode);if(textures.isEmpty())continue;

            Rule rule=null;
            if(textures.size()>=2){
                CountProof count=countProof(renderMethod,watcher.sourceClass(),modelName);
                int texture=textureWatcher(rendererNode,watcher.sourceClass());
                VariableModel variable=proveVariableModel(model,parts);
                int countMax=count==null?-1:variableMaxCount(model,count.base());
                IntBounds countBounds=count==null?null:watcherMutationBounds(watcher.sourceClass(),count.watcherIndex());
                if(count!=null&&texture>=0&&variable!=null&&countMax>=count.base()&&countBounds!=null
                        &&countBounds.min()==0&&countBounds.max()==countMax-count.base()
                        &&proveVariableRendererTransform(renderMethod,watcher.sourceClass(),direction,size,rollGetter)
                        &&proveConstantPositiveRoll(watcher.sourceClass(),rollField))
                    rule=new Rule(watcher.registryName(),watcher.sourceClass(),renderer,modelName,watcher.numericId(),watcher.trackingRange(),watcher.updateFrequency(),watcher.velocityUpdates(),
                            Adapter.VARIABLE_Z_RADIAL,variable.staticParts(),variable.repeated(),List.of(),direction,size,count.watcherIndex(),texture,-1,
                            watcherDefault(watcher,direction,0),watcherDefault(watcher,size,1),sizeBounds.min(),sizeBounds.max(),
                            watcherDefault(watcher,count.watcherIndex(),0),watcherDefault(watcher,texture,0),0,
                            count.base(),countMax,0,0F,scale,textures,true,true,true);
            }else{
                int reverse=remainingOwnByteWatcher(watcher,Set.of(size));
                FixedModel fixed=proveFixedModel(model,parts);
                if(reverse>=0&&fixed!=null
                        &&proveFluidRendererTransform(renderMethod,watcher.sourceClass(),direction,size,rollGetter)
                        &&proveFluidBidirectionalRoll(watcher.sourceClass(),rollField,reverse))
                    rule=new Rule(watcher.registryName(),watcher.sourceClass(),renderer,modelName,watcher.numericId(),watcher.trackingRange(),watcher.updateFrequency(),watcher.velocityUpdates(),
                            Adapter.FLUID_X_RADIAL,fixed.staticParts(),fixed.primary(),fixed.secondary(),direction,size,-1,-1,reverse,
                            watcherDefault(watcher,direction,0),watcherDefault(watcher,size,1),sizeBounds.min(),sizeBounds.max(),
                            0,0,watcherDefault(watcher,reverse,0),
                            0,0,fixed.count(),fixed.secondaryPhaseDegrees(),scale,textures,true,true,true);
            }
            if(rule!=null)rules.add(rule);
            else skipped.add(new Skipped(watcher.registryName(),watcher.sourceClass(),"Renderer/model/tick/AABB shape is outside the admitted rotating-assembly families"));
        }
        rules.sort(Comparator.comparing(Rule::registryName));
        return new Analysis(rules,skipped,List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private record VariableModel(List<Cuboid> staticParts,List<Cuboid> repeated){}
    private VariableModel proveVariableModel(ClassNode model,List<MutablePart> raw){
        if(raw.size()!=4)return null;MethodNode render=renderModelMethod(model);if(render==null)return null;
        Loop loop=findLoop(render);if(loop==null||loop.fixedCount()!=null||loop.countField()==null)return null;
        Set<String> repeated=renderedFields(render,loop.start(),loop.end(),model.name);
        Set<String> statics=renderedFieldsOutside(render,loop.start(),loop.end(),model.name);
        if(repeated.size()!=2||statics.size()!=2)return null;
        if(!sharedDuplicatedRotation(render,loop.start(),loop.end(),Z_ROT))return null;
        if(!variableAngleTable(model))return null;
        return new VariableModel(select(raw,statics),select(raw,repeated));
    }

    private record FixedModel(List<Cuboid> staticParts,List<Cuboid> primary,List<Cuboid> secondary,int count,float secondaryPhaseDegrees){}
    private FixedModel proveFixedModel(ClassNode model,List<MutablePart> raw){
        if(raw.size()!=6)return null;MethodNode render=renderModelMethod(model);if(render==null)return null;
        Loop loop=findLoop(render);if(loop==null||loop.fixedCount()==null||loop.fixedCount()<2)return null;
        Set<String> repeated=renderedFields(render,loop.start(),loop.end(),model.name);
        Set<String> statics=renderedFieldsOutside(render,loop.start(),loop.end(),model.name);
        if(repeated.size()!=5||statics.size()!=1)return null;
        Map<String,String> arrays=rotationArrayOrigins(render,loop.start(),loop.end(),model.name,X_ROT);
        if(!arrays.keySet().containsAll(repeated))return null;
        Map<String,Float> phases=arrayPhases(model,loop.fixedCount());
        if(phases.size()!=2)return null;
        String zero=null,offset=null;float offsetPhase=0F;
        for(var entry:phases.entrySet()){if(Math.abs(entry.getValue())<.001F)zero=entry.getKey();else{offset=entry.getKey();offsetPhase=entry.getValue();}}
        if(zero==null||offset==null||Math.abs(offsetPhase)>=360F)return null;
        Set<String> primary=new LinkedHashSet<>(),secondary=new LinkedHashSet<>();
        for(var entry:arrays.entrySet()){if(entry.getValue().equals(zero))primary.add(entry.getKey());else if(entry.getValue().equals(offset))secondary.add(entry.getKey());else return null;}
        if(primary.isEmpty()||secondary.isEmpty())return null;
        return new FixedModel(select(raw,statics),select(raw,primary),select(raw,secondary),loop.fixedCount(),offsetPhase);
    }

    private static List<Cuboid> select(List<MutablePart> parts,Set<String> names){
        List<Cuboid> out=new ArrayList<>();for(MutablePart p:parts)if(names.contains(p.field()))
            out.add(new Cuboid(p.field(),p.u(),p.v(),p.x(),p.y(),p.z(),p.width(),p.height(),p.depth(),p.pivotX(),p.pivotY(),p.pivotZ(),p.xRot(),p.yRot(),p.zRot(),p.mirror()));
        out.sort(Comparator.comparing(Cuboid::field));return List.copyOf(out);
    }

    private CountProof countProof(MethodNode renderer,String entity,String model){
        List<AbstractInsnNode> code=real(renderer);
        for(int i=1;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode setter&&setter.owner.equals(model)&&setter.desc.equals("(I)V")){
            MethodInsnNode getter=null;for(int j=i-1;j>=Math.max(0,i-5);j--)if(code.get(j) instanceof MethodInsnNode call&&ownerInHierarchy(entity,call.owner)&&call.desc.equals("()I")){getter=call;break;}
            if(getter==null)continue;MethodNode method=effective(entity,Set.of(getter.name),"()I");if(method==null)continue;
            List<AbstractInsnNode> body=real(method);Integer base=null,index=null;
            for(int q=0;q<body.size();q++){
                Integer value=intConstant(body.get(q));if(value!=null&&value>0&&value<=32&&base==null)base=value;
                if(body.get(q) instanceof MethodInsnNode call&&call.owner.equals(DATA_WATCHER)&&WATCH_BYTE.contains(call.name)&&q>0)index=intConstant(body.get(q-1));
            }
            if(base!=null&&index!=null&&containsOpcode(method,Opcodes.IADD))return new CountProof(index,base);
        }
        return null;
    }

    private int textureWatcher(ClassNode renderer,String entity){
        Integer found=null;
        for(MethodNode method:renderer.methods){
            if(Type.getReturnType(method.desc).getSort()!=Type.OBJECT)continue;
            boolean array=false;Integer index=null;
            List<AbstractInsnNode> code=real(method);
            for(int i=0;i<code.size();i++){
                if(code.get(i).getOpcode()==Opcodes.AALOAD)array=true;
                if(code.get(i) instanceof MethodInsnNode call&&ownerInHierarchy(entity,call.owner)&&call.desc.equals("()B")){
                    MethodNode getter=effective(entity,Set.of(call.name),"()B");Integer current=directWatcherIndex(getter);
                    if(current!=null)index=current;
                }
            }
            if(array&&index!=null){if(found!=null&&!found.equals(index))return -1;found=index;}
        }
        return found==null?-1:found;
    }

    private int dominantDirectByteWatcher(MethodNode render,String entity,int excluded){
        Map<Integer,Integer> counts=new LinkedHashMap<>();
        for(AbstractInsnNode insn:render.instructions)if(insn instanceof MethodInsnNode call&&ownerInHierarchy(entity,call.owner)&&call.desc.equals("()B")){
            MethodNode getter=effective(entity,Set.of(call.name),"()B");Integer index=directWatcherIndex(getter);
            if(index!=null&&index!=excluded)counts.merge(index,1,Integer::sum);
        }
        int best=-1,bestCount=1;for(var e:counts.entrySet())if(e.getValue()>bestCount){best=e.getKey();bestCount=e.getValue();}
        return best;
    }

    private static int inheritedByteWatcher(LegacyEntityDataWatcherAnalyzer.Rule rule){
        int result=-1;for(var e:rule.entries())if("byte".equals(e.valueKind())&&!e.declaredBy().equals(rule.sourceClass())){
            if(result!=-1)return -1;result=e.index();
        }return result;
    }
    private static int remainingOwnByteWatcher(LegacyEntityDataWatcherAnalyzer.Rule rule,Set<Integer> excluded){
        int result=-1;for(var e:rule.entries())if("byte".equals(e.valueKind())&&e.declaredBy().equals(rule.sourceClass())&&!excluded.contains(e.index())){
            if(result!=-1)return -1;result=e.index();
        }return result;
    }

    private IntBounds watcherMutationBounds(String entity,int watcherIndex){
        Integer min=null,max=null;Set<String> seen=new HashSet<>();
        for(String current=entity;current!=null&&seen.add(current);){
            ClassNode node=classes.get(current);if(node==null)break;
            for(MethodNode method:node.methods){
                List<AbstractInsnNode> code=real(method);boolean writes=false;
                for(int i=1;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals(DATA_WATCHER)
                        &&Set.of("updateObject","func_75692_b").contains(call.name)&&call.desc.equals("(ILjava/lang/Object;)V")){
                    for(int j=i-1;j>=Math.max(0,i-12);j--){Integer idx=intConstant(code.get(j));if(idx!=null&&idx==watcherIndex){writes=true;break;}}
                }
                if(!writes)continue;
                for(int i=1;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals(DATA_WATCHER)&&WATCH_BYTE.contains(call.name)){
                    Integer idx=intConstant(code.get(i-1));if(idx==null||idx!=watcherIndex)continue;
                    if(i+1<code.size()&&code.get(i+1) instanceof JumpInsnNode jump){
                        if(jump.getOpcode()==Opcodes.IFLE)min=min==null?0:Math.max(min,0);
                    }
                    if(i+2<code.size()){
                        Integer bound=intConstant(code.get(i+1));
                        if(bound!=null&&code.get(i+2) instanceof JumpInsnNode jump){
                            if(jump.getOpcode()==Opcodes.IF_ICMPLE)min=min==null?bound:Math.max(min,bound);
                            if(jump.getOpcode()==Opcodes.IF_ICMPGE)max=max==null?bound:Math.min(max,bound);
                        }
                    }
                }
            }
            current=node.superName;
        }
        return min!=null&&max!=null&&max>=min?new IntBounds(min,max):null;
    }

    private static int watcherDefault(LegacyEntityDataWatcherAnalyzer.Rule rule,int index,int fallback){
        for(var entry:rule.entries())if(entry.index()==index&&entry.defaultValue() instanceof Number number)return number.intValue();
        return fallback;
    }

    private int variableMaxCount(ClassNode model,int base){
        MethodNode ctor=model.methods.stream().filter(m->m.name.equals("<init>")).findFirst().orElse(null);
        if(ctor==null||base<=0)return -1;List<AbstractInsnNode> code=real(ctor);Integer upper=null;
        for(int i=2;i<code.size();i++){
            if(!(code.get(i) instanceof JumpInsnNode jump)||jump.getOpcode()!=Opcodes.IF_ICMPGT)continue;
            Integer bound=intConstant(code.get(i-1));
            if(bound==null||bound<base||!(code.get(i-2) instanceof VarInsnNode load)||load.getOpcode()!=Opcodes.ILOAD)continue;
            boolean initialized=false;
            for(int j=i-3;j>=Math.max(0,i-12);j--)if(code.get(j) instanceof VarInsnNode store&&store.getOpcode()==Opcodes.ISTORE&&store.var==load.var){
                Integer initial=intConstant(code.get(j-1));if(initial!=null&&initial==base)initialized=true;break;
            }
            if(initialized){if(upper!=null&&!upper.equals(bound))return -1;upper=bound;}
        }
        return upper==null?-1:upper;
    }

    private boolean proveVariableRendererTransform(MethodNode render,String entity,int direction,int size,String rollGetter){
        return proveCommonRendererTransform(render,entity,direction,size,rollGetter)
                &&containsOpcode(render,Opcodes.FDIV)&&containsOpcode(render,Opcodes.FADD)&&containsOpcode(render,Opcodes.FSUB)
                &&countInt(render,2)>=2&&rollAxis(render,entity,rollGetter,2);
    }

    private boolean proveFluidRendererTransform(MethodNode render,String entity,int direction,int size,String rollGetter){
        return proveCommonRendererTransform(render,entity,direction,size,rollGetter)
                &&containsOpcode(render,Opcodes.IDIV)&&containsOpcode(render,Opcodes.IADD)
                &&countInt(render,2)>=2&&containsInt(render,-1)&&rollAxis(render,entity,rollGetter,0);
    }

    private boolean proveCommonRendererTransform(MethodNode render,String entity,int direction,int size,String rollGetter){
        if(render==null||rollGetter==null)return false;boolean dir=false,sizeSeen=false,translate=false,scale=false,yaw=false,roll=false,ninety=false;
        for(AbstractInsnNode insn:render.instructions){
            if(insn instanceof MethodInsnNode call){
                if(ownerInHierarchy(entity,call.owner)&&call.desc.equals("()B")){
                    Integer index=directWatcherIndex(effective(entity,Set.of(call.name),"()B"));
                    if(index!=null){dir|=index==direction;sizeSeen|=index==size;}
                }
                if(ownerInHierarchy(entity,call.owner)&&call.name.equals(rollGetter)&&call.desc.equals("()F"))roll=true;
                if(call.owner.equals("org/lwjgl/opengl/GL11")){
                    translate|=call.name.equals("glTranslatef");scale|=call.name.equals("glScalef");
                    yaw|=call.name.equals("glRotatef");
                }
            }
            Float f=floatConstant(insn);if(f!=null&&Float.compare(f,90F)==0)ninety=true;
        }
        return dir&&sizeSeen&&translate&&scale&&yaw&&roll&&ninety;
    }

    private boolean rollAxis(MethodNode render,String entity,String getter,int axis){
        List<AbstractInsnNode> code=real(render);
        for(int i=0;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&ownerInHierarchy(entity,call.owner)
                &&call.name.equals(getter)&&call.desc.equals("()F")){
            for(int j=i+1;j<Math.min(code.size(),i+6);j++)if(code.get(j) instanceof MethodInsnNode gl&&gl.owner.equals("org/lwjgl/opengl/GL11")
                    &&gl.name.equals("glRotatef")&&gl.desc.equals("(FFFF)V")){
                List<Float> values=new ArrayList<>();for(int q=i+1;q<j;q++){Float v=floatConstant(code.get(q));if(v!=null)values.add(v);}
                if(values.size()>=3){
                    float x=values.get(values.size()-3),y=values.get(values.size()-2),z=values.get(values.size()-1);
                    return axis==0?x==1F&&y==0F&&z==0F:axis==2?x==0F&&y==0F&&z==1F:false;
                }
            }
        }
        return false;
    }

    private static int countInt(MethodNode method,int target){int count=0;for(AbstractInsnNode i:method.instructions)if(Integer.valueOf(target).equals(intConstant(i)))count++;return count;}
    private static boolean containsInt(MethodNode method,int target){return countInt(method,target)>0;}

    private String rollGetter(MethodNode renderer,String entity){
        String found=null;for(AbstractInsnNode insn:renderer.instructions)if(insn instanceof MethodInsnNode call&&ownerInHierarchy(entity,call.owner)&&call.desc.equals("()F")){
            if(found!=null&&!found.equals(call.name))return null;found=call.name;
        }return found;
    }
    private String returnedFloatField(String entity,String getter){
        MethodNode method=effective(entity,Set.of(getter),"()F");List<AbstractInsnNode> code=real(method);
        return code.size()==3&&code.get(1) instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&f.desc.equals("F")&&code.get(2).getOpcode()==Opcodes.FRETURN?f.name:null;
    }

    private boolean randomInitialRoll(String entity,String field){
        for(String current=entity;current!=null;){ClassNode node=classes.get(current);if(node==null)break;
            for(MethodNode method:node.methods)if(Set.of("entityInit","func_70088_a").contains(method.name)&&method.desc.equals("()V")){
                boolean random360=false,write=false;List<AbstractInsnNode> code=real(method);
                for(int i=0;i<code.size();i++){
                    if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals("java/util/Random")&&call.name.equals("nextInt")&&call.desc.equals("(I)I")
                            &&i>0&&Integer.valueOf(360).equals(intConstant(code.get(i-1))))random360=true;
                    if(code.get(i) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.name.equals(field)&&put.desc.equals("F"))write=true;
                }
                if(random360&&write)return true;
            }
            current=node.superName;
        }return false;
    }

    private boolean proveConstantPositiveRoll(String entity,String field){
        MethodNode tick=ownOrEffectiveTick(entity);if(tick==null)return false;boolean add=false,one=false,threeSixty=false,write=false,zero=false;
        for(AbstractInsnNode insn:tick.instructions){
            add|=insn.getOpcode()==Opcodes.FADD;Float f=floatConstant(insn);if(f!=null){one|=Float.compare(f,1F)==0;threeSixty|=Float.compare(f,360F)==0;zero|=Float.compare(f,0F)==0;}
            if(insn instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.name.equals(field)&&put.desc.equals("F"))write=true;
        }return add&&one&&threeSixty&&zero&&write;
    }

    private boolean proveFluidBidirectionalRoll(String entity,String field,int reverseWatcher){
        MethodNode tick=ownOrEffectiveTick(entity);if(tick==null)return false;boolean add=false,sub=false,one=false,threeSixty=false,zero=false,write=false,water=false,reverse=false;
        List<AbstractInsnNode> code=real(tick);
        for(int i=0;i<code.size();i++){
            AbstractInsnNode insn=code.get(i);add|=insn.getOpcode()==Opcodes.FADD;sub|=insn.getOpcode()==Opcodes.FSUB;
            Float f=floatConstant(insn);if(f!=null){one|=Float.compare(f,1F)==0;threeSixty|=Float.compare(f,360F)==0;zero|=Float.compare(f,0F)==0;}
            if(insn instanceof FieldInsnNode fieldInsn&&fieldInsn.getOpcode()==Opcodes.GETFIELD&&fieldInsn.desc.equals("Z"))water=true;
            if(insn instanceof MethodInsnNode call&&call.owner.equals(DATA_WATCHER)&&WATCH_BYTE.contains(call.name)&&i>0&&Integer.valueOf(reverseWatcher).equals(intConstant(code.get(i-1))))reverse=true;
            if(insn instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.name.equals(field)&&put.desc.equals("F"))write=true;
        }return add&&sub&&one&&threeSixty&&zero&&write&&water&&reverse;
    }

    private boolean aabbFamily(String entity,int sizeWatcher,int directionWatcher){
        MethodNode method=effective(entity,Set.of("setBounds"),"(DDD)V");if(method==null)return false;
        boolean aabb=false,size=false,dir=false,c15=false,c25=false,c05=false;
        for(AbstractInsnNode insn:method.instructions){
            if(insn instanceof MethodInsnNode call){
                if(call.owner.equals("net/minecraft/util/AxisAlignedBB")&&call.desc.equals("(DDDDDD)Lnet/minecraft/util/AxisAlignedBB;"))aabb=true;
                if(ownerInHierarchy(entity,call.owner)&&call.desc.equals("()B")){
                    Integer index=directWatcherIndex(effective(entity,Set.of(call.name),"()B"));
                    if(index!=null){size|=index==sizeWatcher;dir|=index==directionWatcher;}
                }
            }
            if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Double d){c15|=Double.compare(d,1.5D)==0;c25|=Double.compare(d,2.5D)==0;c05|=Double.compare(d,.5D)==0;}
        }
        return aabb&&size&&dir&&c15&&c25&&c05;
    }

    private boolean provesPhysicalCollision(String entity){
        MethodNode method=effective(entity,Set.of("canBeCollidedWith","func_70067_L"),"()Z");List<AbstractInsnNode> code=real(method);
        return code.size()>=3&&code.get(code.size()-1).getOpcode()==Opcodes.IRETURN&&containsField(method,"Z",Set.of("field_70128_L","isDead"));
    }
    private boolean provesPlayerAttackRemoval(String entity){
        MethodNode method=effective(entity,Set.of("attackEntityFrom","func_70097_a"),"(Lnet/minecraft/util/DamageSource;F)Z");if(method==null)return false;
        boolean player=false,dead=false;for(AbstractInsnNode insn:method.instructions){
            if(insn instanceof LdcInsnNode ldc&&"player".equals(ldc.cst))player=true;
            if(insn instanceof MethodInsnNode call&&Set.of("setDead","func_70106_y").contains(call.name)&&call.desc.equals("()V"))dead=true;
        }return player&&dead;
    }

    private record ParsedBox(String field,int u,int v,float x,float y,float z,int w,int h,int d){}
    private List<MutablePart> parseParts(ClassNode model){
        MethodNode ctor=model.methods.stream().filter(m->m.name.equals("<init>")).max(Comparator.comparingInt(m->m.instructions.size())).orElse(null);
        if(ctor==null)return List.of();List<AbstractInsnNode> code=real(ctor);Map<String,int[]> uv=new LinkedHashMap<>();Map<String,ParsedBox> boxes=new LinkedHashMap<>();
        Map<String,float[]> pivots=new LinkedHashMap<>(),rots=new LinkedHashMap<>();Set<String> mirrors=new HashSet<>();
        for(int i=0;i<code.size();i++){
            if(code.get(i) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL&&call.owner.equals(MODEL_RENDERER)&&call.name.equals("<init>")
                    &&call.desc.equals("(Lnet/minecraft/client/model/ModelBase;II)V")&&i>=2&&i+1<code.size()){
                Integer u=intConstant(code.get(i-2)),v=intConstant(code.get(i-1));if(u!=null&&v!=null&&code.get(i+1) instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.PUTFIELD&&f.owner.equals(model.name))uv.put(f.name,new int[]{u,v});
            }
            if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&call.desc.equals("(FFFIII)Lnet/minecraft/client/model/ModelRenderer;")&&i>=7){
                String field=fieldOrigin(code,i,model.name);int[] t=field==null?null:uv.get(field);Float x=floatConstant(code.get(i-6)),y=floatConstant(code.get(i-5)),z=floatConstant(code.get(i-4));
                Integer w=intConstant(code.get(i-3)),h=intConstant(code.get(i-2)),d=intConstant(code.get(i-1));
                if(field!=null&&t!=null&&x!=null&&y!=null&&z!=null&&w!=null&&h!=null&&d!=null)boxes.put(field,new ParsedBox(field,t[0],t[1],x,y,z,w,h,d));
            }
            if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&call.desc.equals("(FFF)V")&&i>=3){
                String field=fieldOrigin(code,i,model.name);Float x=floatConstant(code.get(i-3)),y=floatConstant(code.get(i-2)),z=floatConstant(code.get(i-1));
                if(field!=null&&x!=null&&y!=null&&z!=null)pivots.put(field,new float[]{x,y,z});
            }
            if(code.get(i) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(MODEL_RENDERER)&&i>=1){
                String field=fieldOrigin(code,i,model.name);Float value=floatConstant(code.get(i-1));if(field==null||value==null)continue;
                float[] r=rots.computeIfAbsent(field,k->new float[3]);if(X_ROT.contains(put.name))r[0]=value;else if(Y_ROT.contains(put.name))r[1]=value;else if(Z_ROT.contains(put.name))r[2]=value;
                else if(put.desc.equals("Z")&&Set.of("mirror","field_78809_i").contains(put.name)&&Integer.valueOf(1).equals(intConstant(code.get(i-1))))mirrors.add(field);
            }
        }
        List<MutablePart> out=new ArrayList<>();for(var b:boxes.values()){float[] p=pivots.getOrDefault(b.field(),new float[3]),r=rots.getOrDefault(b.field(),new float[3]);
            out.add(new MutablePart(b.field(),b.u(),b.v(),b.x(),b.y(),b.z(),b.w(),b.h(),b.d(),p[0],p[1],p[2],r[0],r[1],r[2],mirrors.contains(b.field())));}
        return out;
    }

    private Loop findLoop(MethodNode method){
        List<AbstractInsnNode> all=new ArrayList<>();for(AbstractInsnNode i:method.instructions)all.add(i);
        Map<AbstractInsnNode,Integer> pos=new IdentityHashMap<>();for(int i=0;i<all.size();i++)pos.put(all.get(i),i);
        for(int i=0;i<all.size();i++)if(all.get(i) instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.IF_ICMPGE){
            int realPrev=i-1;while(realPrev>=0&&all.get(realPrev).getOpcode()<0)realPrev--;if(realPrev<0)continue;
            Integer fixed=intConstant(all.get(realPrev));String countField=null;int variable=-1;
            if(fixed==null&&all.get(realPrev) instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&f.desc.equals("I"))countField=f.name;
            for(int j=realPrev-1;j>=Math.max(0,realPrev-4);j--)if(all.get(j) instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ILOAD){variable=v.var;break;}
            Integer end=pos.get(jump.label);if(variable>=0&&end!=null&&end>i)return new Loop(i,end,variable,fixed,countField);
        }return null;
    }

    private static Set<String> renderedFields(MethodNode method,int start,int end,String model){Set<String> out=new LinkedHashSet<>();List<AbstractInsnNode> all=new ArrayList<>();for(AbstractInsnNode i:method.instructions)all.add(i);
        for(int i=Math.max(0,start);i<Math.min(end,all.size());i++)if(all.get(i) instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&RENDER.contains(call.name)){
            String f=fieldOrigin(all,i,model);if(f!=null)out.add(f);}return out;}
    private static Set<String> renderedFieldsOutside(MethodNode method,int start,int end,String model){Set<String> out=new LinkedHashSet<>();List<AbstractInsnNode> all=new ArrayList<>();for(AbstractInsnNode i:method.instructions)all.add(i);
        for(int i=0;i<all.size();i++)if((i<start||i>=end)&&all.get(i) instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&RENDER.contains(call.name)){
            String f=fieldOrigin(all,i,model);if(f!=null)out.add(f);}return out;}
    private static boolean sharedDuplicatedRotation(MethodNode method,int start,int end,Set<String> names){
        List<AbstractInsnNode> all=new ArrayList<>();for(AbstractInsnNode i:method.instructions)all.add(i);
        int stores=0;boolean duplicate=false;
        for(int i=Math.max(0,start);i<Math.min(end,all.size());i++){
            AbstractInsnNode insn=all.get(i);
            if(insn.getOpcode()==Opcodes.DUP_X1||insn.getOpcode()==Opcodes.DUP)duplicate=true;
            if(insn instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(MODEL_RENDERER)&&names.contains(put.name))stores++;
        }
        return duplicate&&stores==2;
    }

    private static Set<String> rotationFields(MethodNode method,int start,int end,String model,Set<String> names){Set<String> out=new LinkedHashSet<>();List<AbstractInsnNode> all=new ArrayList<>();for(AbstractInsnNode i:method.instructions)all.add(i);
        for(int i=Math.max(0,start);i<Math.min(end,all.size());i++)if(all.get(i) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(MODEL_RENDERER)&&names.contains(put.name)){
            String f=fieldOrigin(all,i,model);if(f!=null)out.add(f);}return out;}
    private static Map<String,String> rotationArrayOrigins(MethodNode method,int start,int end,String model,Set<String> names){Map<String,String> out=new LinkedHashMap<>();List<AbstractInsnNode> all=new ArrayList<>();for(AbstractInsnNode i:method.instructions)all.add(i);
        for(int i=Math.max(0,start);i<Math.min(end,all.size());i++)if(all.get(i) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(MODEL_RENDERER)&&names.contains(put.name)){
            String part=fieldOrigin(all,i,model),array=null;for(int j=i-1;j>=Math.max(start,i-12);j--)if(all.get(j) instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&f.owner.equals(model)&&f.desc.equals("[F")){array=f.name;break;}
            if(part!=null&&array!=null)out.put(part,array);}return out;}

    private boolean variableAngleTable(ClassNode model){
        MethodNode ctor=model.methods.stream().filter(m->m.name.equals("<init>")).findFirst().orElse(null);if(ctor==null)return false;
        boolean lower4=false,upper8=false,angle=false;for(AbstractInsnNode insn:ctor.instructions){
            Integer v=intConstant(insn);if(v!=null){lower4|=v==4;upper8|=v==8;}
            if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Double d&&Math.abs(d/(180D)-Math.PI*2D)<1.0E-5D)angle=true;
        }return lower4&&upper8&&angle;
    }

    private Map<String,Float> arrayPhases(ClassNode model,int count){
        MethodNode ctor=model.methods.stream().filter(m->m.name.equals("<init>")).findFirst().orElse(null);if(ctor==null)return Map.of();List<AbstractInsnNode> code=real(ctor);Map<String,Float> out=new LinkedHashMap<>();
        for(int i=0;i<code.size();i++)if(code.get(i).getOpcode()==Opcodes.FASTORE){
            String array=null;for(int j=i-1;j>=Math.max(0,i-18);j--)if(code.get(j) instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&f.owner.equals(model.name)&&f.desc.equals("[F")){array=f.name;break;}
            if(array==null)continue;boolean step=false;Float phase=0F;
            for(int j=Math.max(0,i-16);j<i;j++){
                Number numeric=numberConstant(code.get(j));if(numeric!=null&&Double.compare(numeric.doubleValue(),30D)==0)step=true;
                if(code.get(j).getOpcode()==Opcodes.ISUB&&j>0){Integer p=intConstant(code.get(j-1));if(p!=null)phase=-p.floatValue();}
            }
            if(step)out.put(array,phase);
        }
        return out;
    }

    private static String fieldOrigin(List<AbstractInsnNode> code,int index,String model){
        for(int j=index-1;j>=Math.max(0,index-12);j--)if(code.get(j) instanceof FieldInsnNode f
                &&f.getOpcode()==Opcodes.GETFIELD&&f.owner.equals(model)&&f.desc.equals("L"+MODEL_RENDERER+";"))return f.name;
        return null;
    }

    private float modelScale(MethodNode renderer,String model){Float result=null;for(AbstractInsnNode insn:renderer.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(model)){
        Type[] args=Type.getArgumentTypes(call.desc);if(args.length==0||args[args.length-1].getSort()!=Type.FLOAT)continue;Float v=floatConstant(previousReal(insn));if(v==null||v<=0F)return Float.NaN;if(result!=null&&Float.compare(result,v)!=0)return Float.NaN;result=v;}
        return result==null?Float.NaN:result;}

    private List<String> rendererTextures(ClassNode renderer){LinkedHashSet<String> out=new LinkedHashSet<>();for(MethodNode m:renderer.methods)for(AbstractInsnNode i:m.instructions)
        if(i instanceof LdcInsnNode ldc&&ldc.cst instanceof String s&&s.indexOf(':')>0&&s.contains("textures/")&&s.endsWith(".png")&&resourceExists(s))out.add(s);return List.copyOf(out);}
    private boolean resourceExists(String id){int p=id.indexOf(':');if(p<=0)return false;try(JarFile j=new JarFile(sourceJar.toFile(),false)){return j.getJarEntry("assets/"+id.substring(0,p)+"/"+id.substring(p+1))!=null;}catch(IOException e){return false;}}

    private String uniqueModelType(ClassNode renderer){LinkedHashSet<String> out=new LinkedHashSet<>();for(MethodNode m:renderer.methods)for(AbstractInsnNode i:m.instructions)
        if(i instanceof TypeInsnNode t&&t.getOpcode()==Opcodes.NEW&&inherits(t.desc,MODEL_BASE))out.add(t.desc);return out.size()==1?out.getFirst():null;}
    private MethodNode entityRenderMethod(ClassNode renderer,String entity){
        MethodNode found=null;
        for(MethodNode m:renderer.methods){
            Type[] args=Type.getArgumentTypes(m.desc);
            if(args.length!=6||args[0].getSort()!=Type.OBJECT||!ownerInHierarchy(entity,args[0].getInternalName()))continue;
            boolean push=false;
            for(AbstractInsnNode i:m.instructions)if(i instanceof MethodInsnNode call&&call.owner.equals("org/lwjgl/opengl/GL11")&&call.name.equals("glPushMatrix"))push=true;
            if(push){if(found!=null)return null;found=m;}
        }
        return found;
    }

    private Integer directWatcherIndex(MethodNode method){
        if(method==null)return null;List<AbstractInsnNode> code=real(method);
        for(int i=1;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals(DATA_WATCHER)&&WATCH_BYTE.contains(call.name)){
            AbstractInsnNode source=code.get(i-1);Integer value=intConstant(source);
            if(value==null&&source instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC)value=staticInt(field);
            if(value!=null&&value>=0&&value<=31)return value;
        }
        return null;
    }

    private Integer staticInt(FieldInsnNode field){
        ClassNode owner=classes.get(field.owner);if(owner==null||!"I".equals(field.desc))return null;
        FieldNode source=owner.fields.stream().filter(f->f.name.equals(field.name)&&f.desc.equals(field.desc)).findFirst().orElse(null);
        if(source!=null&&source.value instanceof Integer value)return value;
        MethodNode clinit=owner.methods.stream().filter(m->m.name.equals("<clinit>")&&m.desc.equals("()V")).findFirst().orElse(null);
        if(clinit==null)return null;Integer result=null;List<AbstractInsnNode> code=real(clinit);
        for(int i=1;i<code.size();i++)if(code.get(i) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTSTATIC
                &&put.owner.equals(field.owner)&&put.name.equals(field.name)&&put.desc.equals(field.desc)){
            Integer value=intConstant(code.get(i-1));if(value==null)return null;
            if(result!=null&&!result.equals(value))return null;result=value;
        }
        return result;
    }
    private MethodNode ownOrEffectiveTick(String entity){return effective(entity,Set.of("onUpdate","func_70071_h_"),"()V");}
    private MethodNode effective(String owner,Set<String> names,String desc){Set<String> seen=new HashSet<>();for(String c=owner;c!=null&&seen.add(c);){ClassNode n=classes.get(c);if(n==null)return null;for(MethodNode m:n.methods)if(names.contains(m.name)&&m.desc.equals(desc))return m;c=n.superName;}return null;}
    private boolean ownerInHierarchy(String child,String owner){Set<String> seen=new HashSet<>();for(String c=child;c!=null&&seen.add(c);){if(c.equals(owner))return true;ClassNode n=classes.get(c);c=n==null?null:n.superName;}return false;}
    private boolean inherits(String child,String target){return ownerInHierarchy(child,target);}
    private MethodNode renderModelMethod(ClassNode model){for(MethodNode m:model.methods)if(m.desc.equals("(Lnet/minecraft/entity/Entity;FFFFFF)V"))return m;return null;}

    private static boolean containsOpcode(MethodNode m,int opcode){if(m!=null)for(AbstractInsnNode i:m.instructions)if(i.getOpcode()==opcode)return true;return false;}
    private static boolean containsField(MethodNode m,String desc,Set<String> names){if(m!=null)for(AbstractInsnNode i:m.instructions)if(i instanceof FieldInsnNode f&&f.desc.equals(desc)&&names.contains(f.name))return true;return false;}
    private static String fieldOrigin(List<AbstractInsnNode> code,int index,String modelOwner,int ignored){return fieldOrigin(code,index,modelOwner);}
    private static Integer intConstant(AbstractInsnNode i){if(i==null)return null;return switch(i.getOpcode()){case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)i).operand;case Opcodes.LDC->i instanceof LdcInsnNode l&&l.cst instanceof Integer v?v:null;default->null;};}
    private static Number numberConstant(AbstractInsnNode i){
        if(i instanceof LdcInsnNode ldc&&ldc.cst instanceof Number number)return number;
        return intConstant(i);
    }
    private static Float floatConstant(AbstractInsnNode i){if(i==null)return null;return switch(i.getOpcode()){case Opcodes.FCONST_0->0F;case Opcodes.FCONST_1->1F;case Opcodes.FCONST_2->2F;case Opcodes.LDC->i instanceof LdcInsnNode l&&l.cst instanceof Number n?n.floatValue():null;default->null;};}
    private static AbstractInsnNode previousReal(AbstractInsnNode i){for(AbstractInsnNode p=i==null?null:i.getPrevious();p!=null;p=p.getPrevious())if(p.getOpcode()>=0)return p;return null;}
    private static List<AbstractInsnNode> real(MethodNode m){List<AbstractInsnNode> out=new ArrayList<>();if(m!=null)for(AbstractInsnNode i:m.instructions)if(i.getOpcode()>=0)out.add(i);return out;}
    private static boolean finite(float... v){for(float f:v)if(!Float.isFinite(f))return false;return true;}

    private void load(Path jar)throws IOException{try(JarFile input=new JarFile(jar.toFile(),false)){var e=input.entries();while(e.hasMoreElements()){JarEntry entry=e.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;
        try(InputStream in=input.getInputStream(entry)){ClassNode n=new ClassNode(Opcodes.ASM9);new ClassReader(in).accept(n,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(n.name,n);}catch(RuntimeException bad){diagnostics.add("Unreadable rotating assembly class "+entry.getName()+": "+bad.getClass().getSimpleName());}}}}
}
