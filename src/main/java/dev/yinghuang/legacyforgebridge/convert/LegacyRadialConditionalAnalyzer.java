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
 * Source-only proof for conditional sub-model groups hanging off a radial TESR base.
 *
 * <p>The admitted family is intentionally structural: a world renderer selects branches from
 * {@code metadata >> shift}, each branch calls bounded ModelBase methods, and those methods render
 * constructor-proven ModelRenderer cuboids. A single int-parameter render method may be adapted
 * when its parameter is sourced from a source tile getter whose backing field is randomized once
 * and then increments with a source-proven wrap period every tile tick.</p>
 *
 * <p>No registry, class, field or method name from any corpus mod is used as an allow-list.</p>
 */
public final class LegacyRadialConditionalAnalyzer {
    private static final String MODEL_RENDERER="net/minecraft/client/model/ModelRenderer";
    private static final Set<String> RENDER=Set.of("render","func_78785_a");
    private static final Set<String> PIVOT=Set.of("setRotationPoint","func_78793_a");
    private static final Set<String> ADD_BOX=Set.of("addBox","func_78789_a");
    private static final Set<String> ADD_CHILD=Set.of("addChild","func_78792_a");
    private static final Set<String> X_ROT=Set.of("rotateAngleX","field_78795_f");
    private static final Set<String> Y_ROT=Set.of("rotateAngleY","field_78796_g");
    private static final Set<String> Z_ROT=Set.of("rotateAngleZ","field_78808_h");
    private static final Set<String> META=Set.of("getBlockMetadata","func_145832_p");
    private static final Set<String> TICK=Set.of("updateEntity","func_145845_h");

    public enum Axis{X,Y,Z}

    public record Cuboid(int u,int v,float x,float y,float z,int width,int height,int depth,
                         float pivotX,float pivotY,float pivotZ){
        public Cuboid{
            if(u<0||v<0||width<0||height<0||depth<0||(width==0&&height==0&&depth==0)
                    ||!finite(x,y,z,pivotX,pivotY,pivotZ))throw new IllegalArgumentException("Invalid conditional TESR cuboid");
        }
    }
    public record Pose(float xRot,float yRot,float zRot){
        public Pose{if(!finite(xRot,yRot,zRot))throw new IllegalArgumentException("Invalid conditional TESR pose");}
    }
    public record Animation(Axis axis,float degreesPerTick,int periodTicks,boolean randomizedPhase){
        public Animation{
            if(axis==null||!Float.isFinite(degreesPerTick)||degreesPerTick==0F||periodTicks<=1)
                throw new IllegalArgumentException("Invalid conditional TESR animation");
        }
    }
    public record Part(Cuboid cuboid,List<Pose> poses,Animation animation){
        public Part{
            poses=List.copyOf(poses);
            if(cuboid==null||poses.isEmpty()||poses.size()>32)throw new IllegalArgumentException("Invalid conditional TESR part");
        }
    }
    public record Group(int selectorValue,List<Part> parts){
        public Group{
            parts=List.copyOf(parts);
            if(selectorValue<0||selectorValue>15||parts.isEmpty())throw new IllegalArgumentException("Invalid conditional TESR group");
        }
    }
    public record Rule(String registryName,String sourceBlockClass,String sourceTileClass,String sourceRendererClass,
                       String sourceModelClass,int metadataShift,int selectorMask,List<Group> groups,int coveredModelCalls){
        public Rule{
            groups=List.copyOf(groups);
            if(registryName==null||registryName.isBlank()||sourceBlockClass==null||sourceTileClass==null
                    ||sourceRendererClass==null||sourceModelClass==null||metadataShift<0||metadataShift>3
                    ||selectorMask<1||selectorMask>15||groups.isEmpty()||coveredModelCalls<=0)
                throw new IllegalArgumentException("Invalid conditional TESR rule");
        }
    }
    public record Skipped(String registryName,String sourceBlockClass,String reason){}
    public record Analysis(List<Rule> rules,List<Skipped> skipped){
        public Analysis{rules=List.copyOf(rules);skipped=List.copyOf(skipped);}
    }

    private static final class PartBuilder{
        final String field;Integer u,v,width,height,depth;Float x,y,z,pivotX=0F,pivotY=0F,pivotZ=0F,xRot=0F,yRot=0F,zRot=0F;
        final List<String> children=new ArrayList<>();
        PartBuilder(String field){this.field=field;}
        boolean complete(){return u!=null&&v!=null&&width!=null&&height!=null&&depth!=null&&x!=null&&y!=null&&z!=null;}
        Cuboid cuboid(float px,float py,float pz){return new Cuboid(u,v,x,y,z,width,height,depth,px,py,pz);}
    }
    private record Pivot(float x,float y,float z){}
    private record Overrides(Pivot pivot,Float xRot,Float yRot,Float zRot){}
    private record Loop(int variable,int count){}
    private record ArrayRotation(Axis axis,String arrayField){}
    private record DynamicRotation(Axis axis,double radiansPerUnit,double radiansOffset){}
    private record Ticker(int period,boolean randomized){}
    private record SourceField(String owner,String name){}
    private record CallSpec(String name,String desc,String intGetter){}
    private record Branch(int shift,int selector,List<CallSpec> calls){}

    public Analysis analyze(Path jarPath,List<LegacyRadialTesrAnalyzer.Rule> bases)throws IOException{
        Map<String,ClassNode> classes=load(jarPath);
        List<Rule> rules=new ArrayList<>();List<Skipped> skipped=new ArrayList<>();
        for(var base:bases){
            if(base.conditionalModelCalls()<=0)continue;
            ClassNode renderer=classes.get(base.sourceRendererClass()),model=classes.get(base.sourceModelClass());
            if(renderer==null||model==null){skipped.add(new Skipped(base.registryName(),base.sourceBlockClass(),"Conditional renderer/model class is missing"));continue;}
            MethodNode world=worldRender(renderer,base.sourceTileClass(),base.sourceModelClass());
            if(world==null){skipped.add(new Skipped(base.registryName(),base.sourceBlockClass(),"Conditional world render method is ambiguous"));continue;}
            Integer metaLocal=metadataLocal(world,classes,base.sourceTileClass());
            if(metaLocal==null){skipped.add(new Skipped(base.registryName(),base.sourceBlockClass(),"Conditional selector metadata local is unresolved"));continue;}
            List<Branch> branches=branches(world,classes,base.sourceTileClass(),base.sourceModelClass(),metaLocal);
            if(branches.isEmpty()){skipped.add(new Skipped(base.registryName(),base.sourceBlockClass(),"Conditional metadata branch chain is outside the admitted shifted-selector family"));continue;}
            int shift=branches.getFirst().shift();boolean sameShift=branches.stream().allMatch(b->b.shift()==shift);
            if(!sameShift){skipped.add(new Skipped(base.registryName(),base.sourceBlockClass(),"Conditional branches use different metadata shifts"));continue;}
            Map<String,PartBuilder> defs=definitions(model);
            if(defs.isEmpty()){skipped.add(new Skipped(base.registryName(),base.sourceBlockClass(),"Conditional ModelRenderer constructor definitions are unresolved"));continue;}

            List<Group> groups=new ArrayList<>();int covered=0;String failure=null;
            for(Branch branch:branches){
                List<Part> parts=new ArrayList<>();
                for(CallSpec call:branch.calls()){
                    List<Part> resolved=resolveCall(classes,base.sourceTileClass(),model,defs,call);
                    if(resolved==null){failure="Conditional model call "+call.name()+call.desc()+" is outside the admitted fixed/repeated/ticked cuboid family";break;}
                    parts.addAll(resolved);covered++;
                }
                if(failure!=null)break;
                if(!parts.isEmpty())groups.add(new Group(branch.selector(),parts));
            }
            if(failure!=null){skipped.add(new Skipped(base.registryName(),base.sourceBlockClass(),failure));continue;}
            if(covered!=base.conditionalModelCalls()){
                skipped.add(new Skipped(base.registryName(),base.sourceBlockClass(),"Conditional branch coverage "+covered+" does not match source conditional call inventory "+base.conditionalModelCalls()));continue;
            }
            int mask=(1<<(4-shift))-1;
            LinkedHashSet<Integer> selectorValues=new LinkedHashSet<>();boolean selectorOk=true;
            for(Group group:groups)if(group.selectorValue()>mask||!selectorValues.add(group.selectorValue()))selectorOk=false;
            if(!selectorOk||groups.isEmpty()){skipped.add(new Skipped(base.registryName(),base.sourceBlockClass(),"Conditional selectors are duplicated or outside legacy metadata width"));continue;}
            groups.sort(Comparator.comparingInt(Group::selectorValue));
            rules.add(new Rule(base.registryName(),base.sourceBlockClass(),base.sourceTileClass(),base.sourceRendererClass(),
                    base.sourceModelClass(),shift,mask,groups,covered));
        }
        return new Analysis(rules,skipped);
    }

    private static List<Branch> branches(MethodNode world,Map<String,ClassNode> classes,String tile,String model,int metaLocal){
        List<AbstractInsnNode> real=real(world);List<AbstractInsnNode> all=new ArrayList<>();
        for(AbstractInsnNode insn:world.instructions)all.add(insn);
        IdentityHashMap<AbstractInsnNode,Integer> allIndex=new IdentityHashMap<>();for(int i=0;i<all.size();i++)allIndex.put(all.get(i),i);
        List<Branch> out=new ArrayList<>();
        for(int i=0;i+4<real.size();i++){
            if(!(real.get(i) instanceof VarInsnNode load)||load.getOpcode()!=Opcodes.ILOAD||load.var!=metaLocal)continue;
            Integer shift=integer(real.get(i+1)),selector=integer(real.get(i+3));
            if(shift==null||shift<0||shift>3||real.get(i+2).getOpcode()!=Opcodes.ISHR||selector==null
                    ||!(real.get(i+4) instanceof JumpInsnNode jump)||jump.getOpcode()!=Opcodes.IF_ICMPNE)continue;
            Integer start=allIndex.get(jump),end=allIndex.get(jump.label);if(start==null||end==null||end<=start)continue;
            List<CallSpec> calls=new ArrayList<>();
            for(int j=start+1;j<end;j++){
                AbstractInsnNode insn=all.get(j);
                if(!(insn instanceof MethodInsnNode call)||!call.owner.equals(model))continue;
                if(call.desc.equals("()V"))calls.add(new CallSpec(call.name,call.desc,null));
                else if(call.desc.equals("(I)V")){
                    String getter=precedingIntGetter(all,j,classes,tile);
                    if(getter==null)return List.of();
                    calls.add(new CallSpec(call.name,call.desc,getter));
                }else return List.of();
            }
            if(!calls.isEmpty())out.add(new Branch(shift,selector,calls));
        }
        return out;
    }

    private static String precedingIntGetter(List<AbstractInsnNode> all,int modelCallIndex,Map<String,ClassNode> classes,String tile){
        for(int i=modelCallIndex-1;i>=0&&i>=modelCallIndex-6;i--){
            AbstractInsnNode insn=all.get(i);
            if(insn instanceof MethodInsnNode call&&call.desc.equals("()I")&&ownerInHierarchy(classes,tile,call.owner))return call.name;
            if(insn instanceof JumpInsnNode||insn instanceof TableSwitchInsnNode||insn instanceof LookupSwitchInsnNode)break;
        }
        return null;
    }

    private static List<Part> resolveCall(Map<String,ClassNode> classes,String tile,ClassNode model,Map<String,PartBuilder> defs,CallSpec call){
        MethodNode method=ownMethod(model,call.name(),call.desc());if(method==null)return null;
        if(call.desc().equals("(I)V"))return dynamicParts(classes,tile,model,defs,method,call.intGetter());
        boolean branched=false;for(AbstractInsnNode insn:method.instructions)if(insn instanceof JumpInsnNode||insn instanceof TableSwitchInsnNode||insn instanceof LookupSwitchInsnNode){branched=true;break;}
        return branched?repeatedParts(model,defs,method):fixedParts(model,defs,method);
    }

    private static List<Part> fixedParts(ClassNode model,Map<String,PartBuilder> defs,MethodNode method){
        LinkedHashSet<String> fields=renderedFields(model,method);if(fields.isEmpty())return null;
        List<Part> out=new ArrayList<>();
        for(String field:fields){
            PartBuilder def=defs.get(field);if(def==null||!def.complete())return null;
            Overrides overrides=overrides(model,method,field);
            Pose pose=pose(def,overrides,null,0F);
            List<Part> expanded=expandIdentityChildren(defs,def,overrides,List.of(pose),null);if(expanded==null)return null;out.addAll(expanded);
        }
        return out;
    }

    private static List<Part> repeatedParts(ClassNode model,Map<String,PartBuilder> defs,MethodNode method){
        LinkedHashSet<String> rendered=renderedFields(model,method);if(rendered.size()!=1)return null;
        String field=rendered.getFirst();PartBuilder def=defs.get(field);if(def==null||!def.complete())return null;
        Loop loop=loop(method);if(loop==null)return null;ArrayRotation rotation=arrayRotation(model,method,field);if(rotation==null)return null;
        List<Float> values=arrayValues(model,rotation.arrayField(),loop.variable(),loop.count());if(values==null||values.size()!=loop.count())return null;
        Overrides overrides=overrides(model,method,field);List<Pose> poses=new ArrayList<>();
        for(float value:values)poses.add(pose(def,overrides,rotation.axis(),value));
        return expandIdentityChildren(defs,def,overrides,poses,null);
    }

    private static List<Part> dynamicParts(Map<String,ClassNode> classes,String tile,ClassNode model,Map<String,PartBuilder> defs,MethodNode method,String getter){
        LinkedHashSet<String> rendered=renderedFields(model,method);if(rendered.size()!=1||getter==null)return null;
        String field=rendered.getFirst();PartBuilder def=defs.get(field);if(def==null||!def.complete())return null;
        DynamicRotation dynamic=dynamicRotation(model,method,field);if(dynamic==null)return null;
        Ticker ticker=ticker(classes,tile,getter);if(ticker==null)return null;
        double degrees=dynamic.radiansPerUnit()*180D/Math.PI;
        if(!Double.isFinite(degrees)||Math.abs(degrees)<1.0E-6D)return null;
        Overrides overrides=overrides(model,method,field);
        Pose base=pose(def,overrides,dynamic.axis(),(float)dynamic.radiansOffset());
        Animation animation=new Animation(dynamic.axis(),(float)degrees,ticker.period(),ticker.randomized());
        return expandIdentityChildren(defs,def,overrides,List.of(base),animation);
    }

    private static List<Part> expandIdentityChildren(Map<String,PartBuilder> defs,PartBuilder root,Overrides overrides,List<Pose> poses,Animation animation){
        float px=overrides.pivot()!=null?overrides.pivot().x():root.pivotX,py=overrides.pivot()!=null?overrides.pivot().y():root.pivotY,pz=overrides.pivot()!=null?overrides.pivot().z():root.pivotZ;
        List<Part> out=new ArrayList<>();out.add(new Part(root.cuboid(px,py,pz),poses,animation));
        ArrayDeque<String> queue=new ArrayDeque<>(root.children);Set<String> seen=new HashSet<>();
        while(!queue.isEmpty()){
            String childName=queue.removeFirst();if(!seen.add(childName))return null;PartBuilder child=defs.get(childName);
            if(child==null||!child.complete()||!zero(child.pivotX)||!zero(child.pivotY)||!zero(child.pivotZ)
                    ||!turnIdentity(child.xRot)||!turnIdentity(child.yRot)||!turnIdentity(child.zRot))return null;
            out.add(new Part(child.cuboid(px,py,pz),poses,animation));queue.addAll(child.children);
        }
        return out;
    }

    private static Pose pose(PartBuilder def,Overrides overrides,Axis replaced,float replacement){
        float x=overrides.xRot()!=null?overrides.xRot():def.xRot,y=overrides.yRot()!=null?overrides.yRot():def.yRot,z=overrides.zRot()!=null?overrides.zRot():def.zRot;
        if(replaced==Axis.X)x=replacement;else if(replaced==Axis.Y)y=replacement;else if(replaced==Axis.Z)z=replacement;
        return new Pose(x,y,z);
    }

    private static Overrides overrides(ClassNode model,MethodNode method,String field){
        Pivot pivot=null;Float xr=null,yr=null,zr=null;List<AbstractInsnNode> code=real(method);
        for(int i=0;i<code.size();i++){
            AbstractInsnNode insn=code.get(i);
            if(insn instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&PIVOT.contains(call.name)&&call.desc.equals("(FFF)V")&&i>=4
                    &&code.get(i-4) instanceof FieldInsnNode origin&&origin.getOpcode()==Opcodes.GETFIELD&&origin.owner.equals(model.name)&&origin.name.equals(field)){
                Float x=number(code.get(i-3)),y=number(code.get(i-2)),z=number(code.get(i-1));if(x!=null&&y!=null&&z!=null)pivot=new Pivot(x,y,z);
            }
            if(insn instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(MODEL_RENDERER)&&i>=2
                    &&code.get(i-2) instanceof FieldInsnNode origin&&origin.getOpcode()==Opcodes.GETFIELD&&origin.owner.equals(model.name)&&origin.name.equals(field)){
                Float value=number(code.get(i-1));if(value==null)continue;
                Axis axis=axis(put.name);if(axis==Axis.X)xr=value;else if(axis==Axis.Y)yr=value;else if(axis==Axis.Z)zr=value;
            }
        }
        return new Overrides(pivot,xr,yr,zr);
    }

    private static LinkedHashSet<String> renderedFields(ClassNode model,MethodNode method){
        LinkedHashSet<String> out=new LinkedHashSet<>();List<AbstractInsnNode> code=real(method);
        for(int i=0;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)
                &&RENDER.contains(call.name)&&call.desc.equals("(F)V")){
            String field=null;for(int j=i-1;j>=Math.max(0,i-5);j--)if(code.get(j) instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD
                    &&f.owner.equals(model.name)&&f.desc.equals("L"+MODEL_RENDERER+";")){field=f.name;break;}
            if(field==null)return new LinkedHashSet<>();out.add(field);
        }
        return out;
    }

    private static Loop loop(MethodNode method){
        List<AbstractInsnNode> code=real(method);
        for(int i=2;i<code.size();i++)if(code.get(i) instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.IF_ICMPGE
                &&code.get(i-2) instanceof VarInsnNode load&&load.getOpcode()==Opcodes.ILOAD){
            Integer count=integer(code.get(i-1));if(count!=null&&count>=2&&count<=32)return new Loop(load.var,count);
        }
        return null;
    }

    private static ArrayRotation arrayRotation(ClassNode model,MethodNode method,String renderedField){
        List<AbstractInsnNode> code=real(method);ArrayRotation found=null;
        for(int i=0;i<code.size();i++)if(code.get(i) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(MODEL_RENDERER)){
            Axis axis=axis(put.name);if(axis==null)continue;String sourceArray=null,field=null;
            for(int j=i-1;j>=Math.max(0,i-8);j--){
                AbstractInsnNode n=code.get(j);
                if(n instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&f.owner.equals(model.name)){
                    if(f.desc.equals("[F")&&sourceArray==null)sourceArray=f.name;
                    if(f.desc.equals("L"+MODEL_RENDERER+";")&&field==null)field=f.name;
                }
            }
            if(renderedField.equals(field)&&sourceArray!=null){if(found!=null)return null;found=new ArrayRotation(axis,sourceArray);}
        }
        return found;
    }

    private static List<Float> arrayValues(ClassNode model,String arrayField,int loopVar,int count){
        MethodNode ctor=ownMethod(model,"<init>","()V");if(ctor==null)return null;List<AbstractInsnNode> code=real(ctor);
        for(int i=0;i<code.size();i++)if(code.get(i).getOpcode()==Opcodes.FASTORE){
            int fieldIndex=-1,arrayIndexLoad=-1;
            for(int j=i-1;j>=Math.max(0,i-24);j--)if(code.get(j) instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD
                    &&f.owner.equals(model.name)&&f.name.equals(arrayField)&&f.desc.equals("[F")){fieldIndex=j;break;}
            if(fieldIndex<0)continue;
            for(int j=fieldIndex+1;j<i;j++)if(code.get(j) instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ILOAD&&v.var==loopVar){arrayIndexLoad=j;break;}
            if(arrayIndexLoad<0)continue;List<Float> values=new ArrayList<>();
            for(int n=0;n<count;n++){Double value=evaluate(code,arrayIndexLoad+1,i,loopVar,n);if(value==null||!Double.isFinite(value))return null;values.add(value.floatValue());}
            return values;
        }
        return null;
    }

    private static DynamicRotation dynamicRotation(ClassNode model,MethodNode method,String renderedField){
        List<AbstractInsnNode> code=real(method);DynamicRotation found=null;
        for(int i=0;i<code.size();i++)if(code.get(i) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(MODEL_RENDERER)){
            Axis axis=axis(put.name);if(axis==null)continue;int origin=-1;
            for(int j=i-1;j>=Math.max(0,i-14);j--)if(code.get(j) instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD
                    &&f.owner.equals(model.name)&&f.name.equals(renderedField)&&f.desc.equals("L"+MODEL_RENDERER+";")){origin=j;break;}
            if(origin<0)continue;Double zero=evaluate(code,origin+1,i,1,0),one=evaluate(code,origin+1,i,1,1);
            if(zero==null||one==null||Math.abs(one-zero)<1.0E-8D)continue;
            if(found!=null)return null;found=new DynamicRotation(axis,one-zero,zero);
        }
        return found;
    }

    private static Double evaluate(List<AbstractInsnNode> code,int start,int end,int variable,int variableValue){
        ArrayDeque<Double> stack=new ArrayDeque<>();
        for(int i=start;i<end;i++){
            AbstractInsnNode insn=code.get(i);int op=insn.getOpcode();Double constant=numberDouble(insn);
            if(constant!=null){stack.push(constant);continue;}
            if(insn instanceof VarInsnNode var&&op==Opcodes.ILOAD){
                if(var.var!=variable)return null;stack.push((double)variableValue);continue;
            }
            if(op==Opcodes.I2D||op==Opcodes.I2F||op==Opcodes.D2F||op==Opcodes.F2D)continue;
            if(Set.of(Opcodes.IADD,Opcodes.ISUB,Opcodes.IMUL,Opcodes.IDIV,Opcodes.FADD,Opcodes.FSUB,Opcodes.FMUL,Opcodes.FDIV,Opcodes.DADD,Opcodes.DSUB,Opcodes.DMUL,Opcodes.DDIV).contains(op)){
                if(stack.size()<2)return null;double b=stack.pop(),a=stack.pop();double value=switch(op){
                    case Opcodes.IADD,Opcodes.FADD,Opcodes.DADD->a+b;
                    case Opcodes.ISUB,Opcodes.FSUB,Opcodes.DSUB->a-b;
                    case Opcodes.IMUL,Opcodes.FMUL,Opcodes.DMUL->a*b;
                    default->b==0D?Double.NaN:a/b;
                };stack.push(value);continue;
            }
            return null;
        }
        return stack.size()==1?stack.pop():null;
    }

    private static Ticker ticker(Map<String,ClassNode> classes,String tile,String getterName){
        MethodNode getter=effective(classes,tile,Set.of(getterName),"()I");if(getter==null)return null;SourceField field=returnedIntField(getter);
        if(field==null)return null;Integer randomBound=randomBound(classes,tile,field);if(randomBound==null||randomBound<=1)return null;
        MethodNode tick=effective(classes,tile,TICK,"()V");if(tick==null)return null;boolean wrapped=false;
        for(AbstractInsnNode insn:tick.instructions)if(insn instanceof MethodInsnNode call&&call.desc.equals("()V")&&ownerInHierarchy(classes,tile,call.owner)){
            MethodNode helper=effective(classes,call.owner,Set.of(call.name),"()V");if(helper!=null&&incrementWrap(helper,field,randomBound)){wrapped=true;break;}
        }
        if(!wrapped&&incrementWrap(tick,field,randomBound))wrapped=true;
        return wrapped?new Ticker(randomBound,true):null;
    }

    private static SourceField returnedIntField(MethodNode method){
        SourceField field=null;List<AbstractInsnNode> code=real(method);
        for(int i=0;i+1<code.size();i++)if(code.get(i) instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&f.desc.equals("I")
                &&code.get(i+1).getOpcode()==Opcodes.IRETURN){
            SourceField candidate=new SourceField(f.owner,f.name);if(field!=null&&!field.equals(candidate))return null;field=candidate;
        }
        return field;
    }

    private static Integer randomBound(Map<String,ClassNode> classes,String tile,SourceField field){
        Set<Integer> bounds=new LinkedHashSet<>();Set<String> seen=new HashSet<>();
        for(String current=tile;current!=null&&seen.add(current);){
            ClassNode node=classes.get(current);if(node==null)break;
            for(MethodNode method:node.methods)if(method.name.equals("<init>")){
                List<AbstractInsnNode> code=real(method);
                for(int i=1;i+1<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.owner.equals("java/util/Random")
                        &&call.name.equals("nextInt")&&call.desc.equals("(I)I")&&code.get(i+1) instanceof FieldInsnNode put
                        &&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(field.owner())&&put.name.equals(field.name())){
                    Integer bound=integer(code.get(i-1));if(bound!=null)bounds.add(bound);
                }
            }
            current=node.superName;
        }
        return bounds.size()==1?bounds.iterator().next():null;
    }

    private static boolean incrementWrap(MethodNode method,SourceField field,int bound){
        List<AbstractInsnNode> code=real(method);boolean compare=false,increment=false,zeroWrite=false;
        for(int i=0;i<code.size();i++){
            AbstractInsnNode insn=code.get(i);
            if(insn instanceof FieldInsnNode get&&get.getOpcode()==Opcodes.GETFIELD&&get.owner.equals(field.owner())&&get.name.equals(field.name())){
                for(int j=i+1;j<Math.min(code.size(),i+5);j++)if(Integer.valueOf(bound).equals(integer(code.get(j))))
                    for(int k=j+1;k<Math.min(code.size(),j+3);k++)if(code.get(k) instanceof JumpInsnNode jump&&Set.of(Opcodes.IF_ICMPGE,Opcodes.IF_ICMPGT,Opcodes.IF_ICMPLE,Opcodes.IF_ICMPLT).contains(jump.getOpcode()))compare=true;
                for(int j=i+1;j<Math.min(code.size(),i+7);j++)if(Integer.valueOf(1).equals(integer(code.get(j))))
                    for(int k=j+1;k<Math.min(code.size(),j+3);k++)if(code.get(k).getOpcode()==Opcodes.IADD)
                        for(int q=k+1;q<Math.min(code.size(),k+5);q++)if(code.get(q) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD
                                &&put.owner.equals(field.owner())&&put.name.equals(field.name()))increment=true;
            }
            if(insn instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(field.owner())&&put.name.equals(field.name())&&i>0
                    &&Integer.valueOf(0).equals(integer(code.get(i-1))))zeroWrite=true;
        }
        return compare&&increment&&zeroWrite;
    }

    private static Map<String,PartBuilder> definitions(ClassNode model){
        MethodNode ctor=ownMethod(model,"<init>","()V");if(ctor==null)return Map.of();List<AbstractInsnNode> code=real(ctor);
        Map<String,PartBuilder> out=new LinkedHashMap<>();
        for(int i=0;i<code.size();i++){
            AbstractInsnNode insn=code.get(i);
            if(insn instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(model.name)&&put.desc.equals("L"+MODEL_RENDERER+";")&&i>=3
                    &&code.get(i-1) instanceof MethodInsnNode init&&init.getOpcode()==Opcodes.INVOKESPECIAL&&init.owner.equals(MODEL_RENDERER)
                    &&init.name.equals("<init>")&&init.desc.equals("(Lnet/minecraft/client/model/ModelBase;II)V")){
                Integer u=integer(code.get(i-3)),v=integer(code.get(i-2));if(u!=null&&v!=null){PartBuilder b=out.computeIfAbsent(put.name,PartBuilder::new);b.u=u;b.v=v;}
            }
            if(insn instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&ADD_BOX.contains(call.name)&&call.desc.equals("(FFFIII)Lnet/minecraft/client/model/ModelRenderer;")&&i>=7
                    &&code.get(i-7) instanceof FieldInsnNode origin&&origin.getOpcode()==Opcodes.GETFIELD&&origin.owner.equals(model.name)){
                Float x=number(code.get(i-6)),y=number(code.get(i-5)),z=number(code.get(i-4));Integer w=integer(code.get(i-3)),h=integer(code.get(i-2)),d=integer(code.get(i-1));
                if(x!=null&&y!=null&&z!=null&&w!=null&&h!=null&&d!=null){PartBuilder b=out.computeIfAbsent(origin.name,PartBuilder::new);b.x=x;b.y=y;b.z=z;b.width=w;b.height=h;b.depth=d;}
            }
            if(insn instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&PIVOT.contains(call.name)&&call.desc.equals("(FFF)V")&&i>=4
                    &&code.get(i-4) instanceof FieldInsnNode origin&&origin.getOpcode()==Opcodes.GETFIELD&&origin.owner.equals(model.name)){
                Float x=number(code.get(i-3)),y=number(code.get(i-2)),z=number(code.get(i-1));if(x!=null&&y!=null&&z!=null){PartBuilder b=out.computeIfAbsent(origin.name,PartBuilder::new);b.pivotX=x;b.pivotY=y;b.pivotZ=z;}
            }
            if(insn instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTFIELD&&put.owner.equals(MODEL_RENDERER)&&i>=2
                    &&code.get(i-2) instanceof FieldInsnNode origin&&origin.getOpcode()==Opcodes.GETFIELD&&origin.owner.equals(model.name)){
                Float value=number(code.get(i-1));Axis axis=axis(put.name);if(value!=null&&axis!=null){PartBuilder b=out.computeIfAbsent(origin.name,PartBuilder::new);if(axis==Axis.X)b.xRot=value;else if(axis==Axis.Y)b.yRot=value;else b.zRot=value;}
            }
            if(insn instanceof MethodInsnNode call&&call.owner.equals(MODEL_RENDERER)&&ADD_CHILD.contains(call.name)&&call.desc.equals("(Lnet/minecraft/client/model/ModelRenderer;)V")){
                List<String> fields=new ArrayList<>();for(int j=i-1;j>=Math.max(0,i-6);j--)if(code.get(j) instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD
                        &&f.owner.equals(model.name)&&f.desc.equals("L"+MODEL_RENDERER+";"))fields.add(f.name);
                if(fields.size()>=2){String child=fields.get(0),parent=fields.get(1);out.computeIfAbsent(parent,PartBuilder::new).children.add(child);}
            }
        }
        return out;
    }

    private static MethodNode worldRender(ClassNode renderer,String tile,String model){
        MethodNode found=null;for(MethodNode method:renderer.methods){
            boolean metadata=false,modelCall=false,push=false;
            for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call){
                if(call.desc.equals("()I")&&META.contains(call.name)&&ownerNameCompatible(call.owner,tile))metadata=true;
                if(call.owner.equals(model)&&(call.desc.equals("()V")||call.desc.equals("(I)V")))modelCall=true;
                if(call.owner.equals("org/lwjgl/opengl/GL11")&&call.name.equals("glPushMatrix"))push=true;
            }
            if(metadata&&modelCall&&push){if(found!=null)return null;found=method;}
        }return found;
    }

    private static Integer metadataLocal(MethodNode method,Map<String,ClassNode> classes,String tile){
        List<AbstractInsnNode> code=real(method);
        for(int i=0;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode call&&call.desc.equals("()I")&&META.contains(call.name)&&ownerInHierarchy(classes,tile,call.owner)){
            for(int j=i+1;j<Math.min(code.size(),i+4);j++)if(code.get(j) instanceof VarInsnNode store&&store.getOpcode()==Opcodes.ISTORE)return store.var;
        }return null;
    }

    private static boolean ownerNameCompatible(String owner,String expected){return owner.equals(expected)||owner.endsWith("/"+simple(expected));}
    private static String simple(String name){int i=name.lastIndexOf('/');return i<0?name:name.substring(i+1);}

    private static Axis axis(String name){if(X_ROT.contains(name))return Axis.X;if(Y_ROT.contains(name))return Axis.Y;if(Z_ROT.contains(name))return Axis.Z;return null;}
    private static boolean zero(float value){return Math.abs(value)<1.0E-5F;}
    private static boolean turnIdentity(float value){double turns=value/(Math.PI*2D);return Math.abs(turns-Math.rint(turns))<1.0E-4D;}

    private static MethodNode effective(Map<String,ClassNode> classes,String owner,Set<String> names,String desc){
        Set<String> seen=new HashSet<>();while(owner!=null&&seen.add(owner)){ClassNode node=classes.get(owner);if(node==null)return null;
            for(MethodNode method:node.methods)if(names.contains(method.name)&&desc.equals(method.desc))return method;owner=node.superName;}return null;
    }
    private static MethodNode ownMethod(ClassNode node,String name,String desc){if(node==null)return null;for(MethodNode method:node.methods)if(method.name.equals(name)&&method.desc.equals(desc))return method;return null;}
    private static boolean ownerInHierarchy(Map<String,ClassNode> classes,String child,String owner){
        Set<String> seen=new HashSet<>();for(String current=child;current!=null&&seen.add(current);){if(current.equals(owner))return true;ClassNode node=classes.get(current);current=node==null?null:node.superName;}return false;
    }

    private static Integer integer(AbstractInsnNode insn){if(insn==null)return null;return switch(insn.getOpcode()){
        case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;
        case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer value?value:null;default->null;};}
    private static Float number(AbstractInsnNode insn){Double value=numberDouble(insn);return value==null?null:value.floatValue();}
    private static Double numberDouble(AbstractInsnNode insn){if(insn==null)return null;return switch(insn.getOpcode()){
        case Opcodes.ICONST_M1->-1D;case Opcodes.ICONST_0,Opcodes.FCONST_0,Opcodes.DCONST_0->0D;
        case Opcodes.ICONST_1,Opcodes.FCONST_1,Opcodes.DCONST_1->1D;case Opcodes.ICONST_2,Opcodes.FCONST_2->2D;
        case Opcodes.ICONST_3->3D;case Opcodes.ICONST_4->4D;case Opcodes.ICONST_5->5D;
        case Opcodes.BIPUSH,Opcodes.SIPUSH->(double)((IntInsnNode)insn).operand;
        case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Number n?n.doubleValue():null;default->null;};}
    private static List<AbstractInsnNode> real(MethodNode method){List<AbstractInsnNode> out=new ArrayList<>();if(method!=null)for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)out.add(insn);return out;}
    private static boolean finite(float... values){for(float value:values)if(!Float.isFinite(value))return false;return true;}

    private static Map<String,ClassNode> load(Path jarPath)throws IOException{
        Map<String,ClassNode> out=new LinkedHashMap<>();try(JarFile jar=new JarFile(jarPath.toFile(),false)){Enumeration<JarEntry> entries=jar.entries();while(entries.hasMoreElements()){
            JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;
            try(InputStream in=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(in).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);out.put(node.name,node);}catch(RuntimeException ignored){}
        }}return out;
    }
}
