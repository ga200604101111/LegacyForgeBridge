package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Proves the bounded legacy "filled item + one-or-more blank items -> N+1 filled copies" recipe
 * family without executing the source IRecipe implementation.
 *
 * <p>The source recipe class name, field names and registered item names are not admission inputs.
 * The analyzer requires constructor-bound Item fields plus closed matching/assembly bytecode
 * semantics. Unknown calls or changed control-flow surfaces fail closed.</p>
 */
public final class LegacyCloningRecipeAnalyzer {
    private static final String GAME_REGISTRY = "cpw/mods/fml/common/registry/GameRegistry";
    private static final String IRECIPE = "net/minecraft/item/crafting/IRecipe";
    private static final String ITEM = "net/minecraft/item/Item";
    private static final String ITEM_DESC = "Lnet/minecraft/item/Item;";
    private static final String STACK = "net/minecraft/item/ItemStack";
    private static final String STACK_DESC = "Lnet/minecraft/item/ItemStack;";
    private static final String INVENTORY = "net/minecraft/inventory/InventoryCrafting";
    private static final int MAX_REACHABLE_METHODS = 32_768;

    public record ItemRef(String registryName,String legacyNamespace,String sourceOwner,String sourceField) {
        public ItemRef {
            Objects.requireNonNull(registryName,"registryName");
            Objects.requireNonNull(sourceOwner,"sourceOwner");
            Objects.requireNonNull(sourceField,"sourceField");
        }
    }

    public record Rule(ItemRef fullItem,ItemRef blankItem,boolean copyCustomName,
                       String sourceRecipeClass,String sourceOwner,String sourceMethod) { }

    public record Analysis(List<Rule> rules,List<String> diagnostics) {
        public Analysis { rules=List.copyOf(rules);diagnostics=List.copyOf(diagnostics); }
    }

    private record MethodKey(String owner,String name,String descriptor) { }
    private record MethodContext(ClassNode owner,MethodNode method,Frame<SourceValue>[] frames,
                                 Map<AbstractInsnNode,Integer> indices) { }
    private record Fields(String full,String blank) { }
    private record Candidate(String type,TypeInsnNode allocation,MethodInsnNode constructor,
                             ItemRef full,ItemRef blank) { }

    private final Map<String,ClassNode> classes=new LinkedHashMap<>();
    private final Map<MethodKey,MethodContext> methods=new LinkedHashMap<>();
    private final Map<String,LegacyRegistryAnalyzer.FieldBinding> registryFields=new LinkedHashMap<>();
    private final List<String> diagnostics=new ArrayList<>();

    public Analysis analyze(Path jarPath)throws IOException {
        return analyze(jarPath,new LegacyRegistryAnalyzer().analyze(jarPath));
    }

    Analysis analyze(Path jarPath,LegacyRegistryAnalyzer.Analysis registry)throws IOException {
        classes.clear();methods.clear();registryFields.clear();diagnostics.clear();
        for(var binding:registry.fieldBindings()) {
            registryFields.put(binding.owner()+"."+binding.name()+binding.descriptor(),binding);
        }
        load(jarPath);
        analyzeFrames();
        LinkedHashSet<Rule> rules=new LinkedHashSet<>();
        for(MethodKey key:reachableMethods()){
            MethodContext context=methods.get(key);
            if(context==null)continue;
            for(int i=0;i<context.method().instructions.size();i++){
                AbstractInsnNode insn=context.method().instructions.get(i);
                if(!(insn instanceof MethodInsnNode call)
                        ||call.getOpcode()!=Opcodes.INVOKESTATIC
                        ||!GAME_REGISTRY.equals(call.owner)
                        ||!"addRecipe".equals(call.name)
                        ||!("("+IRECIPE_DESC()+")V").equals(call.desc))continue;
                Rule rule=registration(context,i,call);
                if(rule!=null)rules.add(rule);
            }
        }
        return new Analysis(List.copyOf(rules),List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static String IRECIPE_DESC(){return "L"+IRECIPE+";";}

    private void load(Path jarPath)throws IOException {
        try(JarFile jar=new JarFile(jarPath.toFile())){
            Enumeration<JarEntry> entries=jar.entries();
            while(entries.hasMoreElements()){
                JarEntry entry=entries.nextElement();
                if(entry.isDirectory()||!entry.getName().endsWith(".class"))continue;
                try(InputStream in=jar.getInputStream(entry)){
                    ClassNode node=new ClassNode(Opcodes.ASM9);
                    new ClassReader(in).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                    classes.put(node.name,node);
                }catch(RuntimeException invalid){
                    diagnostics.add("Unreadable clone-recipe class "+entry.getName()+": "+invalid.getMessage());
                }
            }
        }
    }

    private void analyzeFrames(){
        for(ClassNode owner:classes.values())for(MethodNode method:owner.methods){
            MethodKey key=new MethodKey(owner.name,method.name,method.desc);
            try{
                Frame<SourceValue>[] frames=new Analyzer<>(new SourceInterpreter()).analyze(owner.name,method);
                Map<AbstractInsnNode,Integer> indices=new IdentityHashMap<>();
                for(int i=0;i<method.instructions.size();i++)indices.put(method.instructions.get(i),i);
                methods.put(key,new MethodContext(owner,method,frames,indices));
            }catch(AnalyzerException|RuntimeException invalid){
                diagnostics.add("Clone-recipe dataflow unavailable for "+owner.name+"."+method.name+method.desc+": "+invalid.getMessage());
            }
        }
    }

    private Set<MethodKey> reachableMethods(){
        LinkedHashSet<MethodKey> reachable=new LinkedHashSet<>();
        ArrayDeque<MethodKey> queue=new ArrayDeque<>();
        for(var entry:methods.entrySet()){
            if(isRoot(entry.getValue())||"<clinit>".equals(entry.getKey().name()))queue.add(entry.getKey());
        }
        while(!queue.isEmpty()&&reachable.size()<MAX_REACHABLE_METHODS){
            MethodKey key=queue.removeFirst();
            if(!reachable.add(key))continue;
            MethodContext context=methods.get(key);
            if(context==null)continue;
            for(AbstractInsnNode insn:context.method().instructions){
                if(!(insn instanceof MethodInsnNode call))continue;
                MethodKey target=new MethodKey(call.owner,call.name,call.desc);
                if(methods.containsKey(target)&&!reachable.contains(target))queue.addLast(target);
            }
        }
        if(!queue.isEmpty())diagnostics.add("Clone-recipe reachable-method budget exceeded.");
        return reachable;
    }

    private static boolean isRoot(MethodContext context){
        MethodNode method=context.method();
        return hasAnnotation(method.visibleAnnotations,"Lcpw/mods/fml/common/Mod$EventHandler;")
                ||hasAnnotation(method.invisibleAnnotations,"Lcpw/mods/fml/common/Mod$EventHandler;")
                ||(method.desc.contains("Lcpw/mods/fml/common/event/FML")&&method.desc.endsWith(")V"));
    }

    private static boolean hasAnnotation(List<AnnotationNode> annotations,String descriptor){
        return annotations!=null&&annotations.stream().anyMatch(value->descriptor.equals(value.desc));
    }

    private Rule registration(MethodContext context,int callIndex,MethodInsnNode registration){
        Frame<SourceValue> frame=context.frames()[callIndex];
        if(frame==null||frame.getStackSize()<1)return null;
        TypeInsnNode allocation=uniqueAllocation(context,frame.getStack(frame.getStackSize()-1),0,new HashSet<>());
        if(allocation==null||allocation.getOpcode()!=Opcodes.NEW)return null;
        ClassNode recipe=classes.get(allocation.desc);
        if(recipe==null||!recipe.interfaces.contains(IRECIPE))return null;

        MethodInsnNode constructor=findConstructor(context,allocation,registration);
        if(constructor==null||!("("+ITEM_DESC+ITEM_DESC+")V").equals(constructor.desc))return null;
        Integer constructorIndex=context.indices().get(constructor);
        if(constructorIndex==null)return null;
        Frame<SourceValue> constructorFrame=context.frames()[constructorIndex];
        if(constructorFrame==null||constructorFrame.getStackSize()<3)return null;
        int base=constructorFrame.getStackSize()-3;
        ItemRef full=registeredItem(context,constructorFrame.getStack(base+1),constructorIndex,0,new HashSet<>());
        ItemRef blank=registeredItem(context,constructorFrame.getStack(base+2),constructorIndex,0,new HashSet<>());
        if(full==null||blank==null||full.equals(blank))return null;

        Fields fields=constructorFields(recipe,constructor.desc);
        if(fields==null)return null;
        if(!matchesSemantics(recipe,fields))return null;
        Boolean copyName=assemblySemantics(recipe,fields);
        if(copyName==null)return null;
        if(!constantNineSize(recipe)||!nullPreview(recipe))return null;

        return new Rule(full,blank,copyName,recipe.name,context.owner().name,context.method().name);
    }

    private MethodInsnNode findConstructor(MethodContext context,TypeInsnNode allocation,MethodInsnNode registration){
        int start=context.indices().getOrDefault(allocation,-1);
        int end=context.indices().getOrDefault(registration,-1);
        if(start<0||end<0||start>=end)return null;
        MethodInsnNode found=null;
        for(int i=start+1;i<end;i++){
            AbstractInsnNode insn=context.method().instructions.get(i);
            if(!(insn instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESPECIAL
                    ||!"<init>".equals(call.name)||!allocation.desc.equals(call.owner))continue;
            Frame<SourceValue> frame=context.frames()[i];
            int argc=Type.getArgumentTypes(call.desc).length;
            if(frame==null||frame.getStackSize()<argc+1)continue;
            SourceValue receiver=frame.getStack(frame.getStackSize()-argc-1);
            if(!originatesFrom(context,receiver,allocation,0,new HashSet<>()))continue;
            if(found!=null)return null;
            found=call;
        }
        return found;
    }

    private TypeInsnNode uniqueAllocation(MethodContext context,SourceValue value,int depth,Set<Integer> guard){
        if(value==null||value.insns==null||depth>32)return null;
        LinkedHashSet<TypeInsnNode> found=new LinkedHashSet<>();
        for(AbstractInsnNode producer:value.insns){
            Integer index=context.indices().get(producer);
            if(index==null||!guard.add(index))continue;
            try{
                if(producer instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW)found.add(type);
                else if(producer instanceof VarInsnNode variable&&isReferenceLoad(variable.getOpcode())){
                    Frame<SourceValue> frame=context.frames()[index];
                    if(frame!=null&&variable.var<frame.getLocals()){
                        TypeInsnNode nested=uniqueAllocation(context,frame.getLocal(variable.var),depth+1,guard);
                        if(nested!=null)found.add(nested);
                    }
                }else if(producer instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.CHECKCAST){
                    Frame<SourceValue> frame=context.frames()[index];
                    if(frame!=null&&frame.getStackSize()>0){
                        TypeInsnNode nested=uniqueAllocation(context,frame.getStack(frame.getStackSize()-1),depth+1,guard);
                        if(nested!=null)found.add(nested);
                    }
                }else if(producer.getOpcode()==Opcodes.DUP){
                    Frame<SourceValue> frame=context.frames()[index];
                    if(frame!=null&&frame.getStackSize()>0){
                        TypeInsnNode nested=uniqueAllocation(context,frame.getStack(frame.getStackSize()-1),depth+1,guard);
                        if(nested!=null)found.add(nested);
                    }
                }
            }finally{guard.remove(index);}
        }
        return found.size()==1?found.getFirst():null;
    }

    private ItemRef registeredItem(MethodContext context,SourceValue value,int current,int depth,Set<Integer> guard){
        if(value==null||value.insns==null||depth>32)return null;
        LinkedHashSet<ItemRef> found=new LinkedHashSet<>();
        for(AbstractInsnNode producer:value.insns){
            Integer index=context.indices().get(producer);
            if(index==null||index>current||!guard.add(index))continue;
            try{
                if(producer instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC){
                    LegacyRegistryAnalyzer.FieldBinding binding=registryFields.get(field.owner+"."+field.name+field.desc);
                    if(binding!=null&&binding.kind()==LegacyRegistryAnalyzer.Kind.ITEM){
                        found.add(new ItemRef(binding.registryName(),binding.legacyNamespace(),field.owner,field.name));
                    }
                }else if(producer instanceof VarInsnNode variable&&isReferenceLoad(variable.getOpcode())){
                    Frame<SourceValue> frame=context.frames()[index];
                    if(frame!=null&&variable.var<frame.getLocals()){
                        ItemRef nested=registeredItem(context,frame.getLocal(variable.var),index,depth+1,guard);
                        if(nested!=null)found.add(nested);
                    }
                }else if(producer instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.CHECKCAST){
                    Frame<SourceValue> frame=context.frames()[index];
                    if(frame!=null&&frame.getStackSize()>0){
                        ItemRef nested=registeredItem(context,frame.getStack(frame.getStackSize()-1),index,depth+1,guard);
                        if(nested!=null)found.add(nested);
                    }
                }
            }finally{guard.remove(index);}
        }
        return found.size()==1?found.getFirst():null;
    }

    private static boolean isReferenceLoad(int opcode){return opcode==Opcodes.ALOAD;}

    private static boolean originatesFrom(MethodContext context,SourceValue value,AbstractInsnNode target,int depth,Set<Integer> guard){
        if(value==null||value.insns==null||depth>32)return false;
        for(AbstractInsnNode producer:value.insns){
            if(producer==target)return true;
            Integer index=context.indices().get(producer);
            if(index==null||!guard.add(index))continue;
            try{
                if(producer instanceof VarInsnNode variable&&isReferenceLoad(variable.getOpcode())){
                    Frame<SourceValue> frame=context.frames()[index];
                    if(frame!=null&&variable.var<frame.getLocals()
                            &&originatesFrom(context,frame.getLocal(variable.var),target,depth+1,guard))return true;
                }else if(producer instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.CHECKCAST){
                    Frame<SourceValue> frame=context.frames()[index];
                    if(frame!=null&&frame.getStackSize()>0
                            &&originatesFrom(context,frame.getStack(frame.getStackSize()-1),target,depth+1,guard))return true;
                }else if(producer.getOpcode()==Opcodes.DUP){
                    Frame<SourceValue> frame=context.frames()[index];
                    if(frame!=null&&frame.getStackSize()>0
                            &&originatesFrom(context,frame.getStack(frame.getStackSize()-1),target,depth+1,guard))return true;
                }
            }finally{guard.remove(index);}
        }
        return false;
    }

    private static Fields constructorFields(ClassNode recipe,String descriptor){
        MethodNode constructor=recipe.methods.stream()
                .filter(method->"<init>".equals(method.name)&&descriptor.equals(method.desc)).findFirst().orElse(null);
        if(constructor==null)return null;
        String full=null,blank=null;
        for(AbstractInsnNode insn:constructor.instructions){
            if(!(insn instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.PUTFIELD
                    ||!recipe.name.equals(field.owner)||!ITEM_DESC.equals(field.desc))continue;
            AbstractInsnNode value=previousReal(field),self=previousReal(value);
            if(!(self instanceof VarInsnNode selfLoad)||selfLoad.getOpcode()!=Opcodes.ALOAD||selfLoad.var!=0
                    ||!(value instanceof VarInsnNode arg)||arg.getOpcode()!=Opcodes.ALOAD) return null;
            if(arg.var==1){if(full!=null)return null;full=field.name;}
            else if(arg.var==2){if(blank!=null)return null;blank=field.name;}
            else return null;
        }
        return full!=null&&blank!=null&&!full.equals(blank)?new Fields(full,blank):null;
    }

    private static boolean matchesSemantics(ClassNode recipe,Fields fields){
        MethodNode method=singleMethod(recipe,"(Lnet/minecraft/inventory/InventoryCrafting;Lnet/minecraft/world/World;)Z");
        if(method==null)return false;
        List<AbstractInsnNode> code=real(method);
        if(countCalls(code,INVENTORY,Set.of("func_70302_i_","getSizeInventory"),"()I")!=1
                ||countCalls(code,INVENTORY,Set.of("func_70301_a","getStackInSlot"),"(I)"+STACK_DESC)!=1
                ||countCalls(code,STACK,Set.of("func_77973_b","getItem"),"()"+ITEM_DESC)!=2
                ||otherCalls(code,Set.of(
                        callKey(INVENTORY,"func_70302_i_","()I"),callKey(INVENTORY,"getSizeInventory","()I"),
                        callKey(INVENTORY,"func_70301_a","(I)"+STACK_DESC),callKey(INVENTORY,"getStackInSlot","(I)"+STACK_DESC),
                        callKey(STACK,"func_77973_b","()"+ITEM_DESC),callKey(STACK,"getItem","()"+ITEM_DESC))) )return false;
        if(fieldReads(code,recipe.name,fields.full())!=1||fieldReads(code,recipe.name,fields.blank())!=1
                ||otherRecipeItemFields(code,recipe.name,fields))return false;
        return opcodeCount(code,Opcodes.IINC)==2
                &&opcodeCount(code,Opcodes.IF_ICMPGE)==1
                &&opcodeCount(code,Opcodes.IF_ACMPNE)==1
                &&opcodeCount(code,Opcodes.IF_ACMPEQ)==1
                &&opcodeCount(code,Opcodes.IFNULL)==3
                &&opcodeCount(code,Opcodes.IFLE)==1
                &&opcodeCount(code,Opcodes.IRETURN)==3
                &&opcodeCount(code,Opcodes.GOTO)==3
                &&hasBackwardJump(code);
    }

    /** @return copy-custom-name flag, or null when the assembly semantics are not this family. */
    private static Boolean assemblySemantics(ClassNode recipe,Fields fields){
        MethodNode method=singleMethod(recipe,"(Lnet/minecraft/inventory/InventoryCrafting;)"+STACK_DESC);
        if(method==null)return null;
        List<AbstractInsnNode> code=real(method);
        Set<String> basic=new LinkedHashSet<>(List.of(
                callKey(INVENTORY,"func_70302_i_","()I"),callKey(INVENTORY,"getSizeInventory","()I"),
                callKey(INVENTORY,"func_70301_a","(I)"+STACK_DESC),callKey(INVENTORY,"getStackInSlot","(I)"+STACK_DESC),
                callKey(STACK,"func_77973_b","()"+ITEM_DESC),callKey(STACK,"getItem","()"+ITEM_DESC),
                callKey(STACK,"func_77960_j","()I"),callKey(STACK,"getItemDamage","()I"),
                callKey(STACK,"<init>","("+ITEM_DESC+"II)V")
        ));
        int oldHas=countCalls(code,STACK,Set.of("func_82837_s","hasDisplayName"),"()Z");
        int oldGet=countCalls(code,STACK,Set.of("func_82833_r","getDisplayName"),"()Ljava/lang/String;");
        int oldSet=countCalls(code,STACK,Set.of("func_151001_c","setStackDisplayName"),"(Ljava/lang/String;)"+STACK_DESC);
        boolean copyName=oldHas==1&&oldGet==1&&oldSet==1;
        if(!copyName&&(oldHas!=0||oldGet!=0||oldSet!=0))return null;
        if(copyName){
            basic.add(callKey(STACK,"func_82837_s","()Z"));basic.add(callKey(STACK,"hasDisplayName","()Z"));
            basic.add(callKey(STACK,"func_82833_r","()Ljava/lang/String;"));basic.add(callKey(STACK,"getDisplayName","()Ljava/lang/String;"));
            basic.add(callKey(STACK,"func_151001_c","(Ljava/lang/String;)"+STACK_DESC));basic.add(callKey(STACK,"setStackDisplayName","(Ljava/lang/String;)"+STACK_DESC));
        }
        if(countCalls(code,INVENTORY,Set.of("func_70302_i_","getSizeInventory"),"()I")!=1
                ||countCalls(code,INVENTORY,Set.of("func_70301_a","getStackInSlot"),"(I)"+STACK_DESC)!=1
                ||countCalls(code,STACK,Set.of("func_77973_b","getItem"),"()"+ITEM_DESC)!=2
                ||countCalls(code,STACK,Set.of("func_77960_j","getItemDamage"),"()I")!=1
                ||countCalls(code,STACK,Set.of("<init>"),"("+ITEM_DESC+"II)V")!=1
                ||otherCalls(code,basic))return null;
        if(fieldReads(code,recipe.name,fields.full())!=2||fieldReads(code,recipe.name,fields.blank())!=1
                ||otherRecipeItemFields(code,recipe.name,fields))return null;
        if(opcodeCount(code,Opcodes.NEW)!=1||!newOnly(code,STACK)
                ||opcodeCount(code,Opcodes.IADD)!=1
                ||opcodeCount(code,Opcodes.IINC)!=2
                ||opcodeCount(code,Opcodes.IF_ICMPGE)!=1
                ||opcodeCount(code,Opcodes.IF_ACMPNE)!=1
                ||opcodeCount(code,Opcodes.IF_ACMPEQ)!=1
                ||opcodeCount(code,Opcodes.IFNULL)!=3
                ||opcodeCount(code,Opcodes.IF_ICMPLT)!=1
                ||opcodeCount(code,Opcodes.ARETURN)!=4
                ||opcodeCount(code,Opcodes.GOTO)!=2
                ||!hasBackwardJump(code))return null;
        if(copyName&&(opcodeCount(code,Opcodes.IFEQ)!=1||opcodeCount(code,Opcodes.POP)!=1))return null;
        return copyName;
    }

    private static boolean constantNineSize(ClassNode recipe){
        MethodNode found=null;
        for(MethodNode method:recipe.methods){
            if(!"()I".equals(method.desc))continue;
            List<AbstractInsnNode> code=real(method);
            if(code.size()==2&&integerConstant(code.getFirst())==9&&code.get(1).getOpcode()==Opcodes.IRETURN){
                if(found!=null)return false;found=method;
            }
        }
        return found!=null;
    }

    private static boolean nullPreview(ClassNode recipe){
        MethodNode found=null;
        for(MethodNode method:recipe.methods){
            if(!("()"+STACK_DESC).equals(method.desc))continue;
            List<AbstractInsnNode> code=real(method);
            if(code.size()==2&&code.getFirst().getOpcode()==Opcodes.ACONST_NULL&&code.get(1).getOpcode()==Opcodes.ARETURN){
                if(found!=null)return false;found=method;
            }
        }
        return found!=null;
    }

    private static MethodNode singleMethod(ClassNode owner,String descriptor){
        MethodNode found=null;
        for(MethodNode method:owner.methods)if(descriptor.equals(method.desc)){
            if(found!=null)return null;found=method;
        }
        return found;
    }

    private static int countCalls(List<AbstractInsnNode> code,String owner,Set<String> names,String descriptor){
        int count=0;
        for(AbstractInsnNode insn:code)if(insn instanceof MethodInsnNode call
                &&owner.equals(call.owner)&&names.contains(call.name)&&descriptor.equals(call.desc))count++;
        return count;
    }

    private static String callKey(String owner,String name,String descriptor){return owner+"\u0000"+name+"\u0000"+descriptor;}

    private static boolean otherCalls(List<AbstractInsnNode> code,Set<String> allowed){
        for(AbstractInsnNode insn:code)if(insn instanceof MethodInsnNode call){
            if(call.getOpcode()==Opcodes.INVOKESPECIAL&&"java/lang/Object".equals(call.owner)&&"<init>".equals(call.name))continue;
            if(!allowed.contains(callKey(call.owner,call.name,call.desc)))return true;
        }
        return false;
    }

    private static int fieldReads(List<AbstractInsnNode> code,String owner,String name){
        int count=0;
        for(AbstractInsnNode insn:code)if(insn instanceof FieldInsnNode field
                &&field.getOpcode()==Opcodes.GETFIELD&&owner.equals(field.owner)&&name.equals(field.name)&&ITEM_DESC.equals(field.desc))count++;
        return count;
    }

    private static boolean otherRecipeItemFields(List<AbstractInsnNode> code,String owner,Fields fields){
        for(AbstractInsnNode insn:code)if(insn instanceof FieldInsnNode field
                &&owner.equals(field.owner)&&ITEM_DESC.equals(field.desc)
                &&(!fields.full().equals(field.name)&&!fields.blank().equals(field.name)))return true;
        return false;
    }

    private static boolean newOnly(List<AbstractInsnNode> code,String expected){
        for(AbstractInsnNode insn:code)if(insn instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW&&!expected.equals(type.desc))return false;
        return true;
    }

    private static int opcodeCount(List<AbstractInsnNode> code,int opcode){
        int count=0;for(AbstractInsnNode insn:code)if(insn.getOpcode()==opcode)count++;return count;
    }

    private static boolean hasBackwardJump(List<AbstractInsnNode> code){
        Map<AbstractInsnNode,Integer> positions=new IdentityHashMap<>();
        for(int i=0;i<code.size();i++)positions.put(code.get(i),i);
        for(int i=0;i<code.size();i++)if(code.get(i) instanceof JumpInsnNode jump){
            Integer target=positions.get(jump.label);
            if(target!=null&&target<i)return true;
        }
        // Labels are removed from the real-instruction list, so resolve against the original linked
        // list when the direct label position is absent.
        for(AbstractInsnNode insn:code)if(insn instanceof JumpInsnNode jump){
            AbstractInsnNode target=nextReal(jump.label);
            Integer targetPosition=positions.get(target),sourcePosition=positions.get(insn);
            if(targetPosition!=null&&sourcePosition!=null&&targetPosition<sourcePosition)return true;
        }
        return false;
    }

    private static Integer integerConstant(AbstractInsnNode insn){
        int opcode=insn.getOpcode();
        if(opcode>=Opcodes.ICONST_M1&&opcode<=Opcodes.ICONST_5)return opcode-Opcodes.ICONST_0;
        if(insn instanceof IntInsnNode value&&(opcode==Opcodes.BIPUSH||opcode==Opcodes.SIPUSH))return value.operand;
        if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Integer value)return value;
        return null;
    }

    private static List<AbstractInsnNode> real(MethodNode method){
        ArrayList<AbstractInsnNode> out=new ArrayList<>();
        for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)out.add(insn);
        return out;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode node){
        for(AbstractInsnNode current=node==null?null:node.getPrevious();current!=null;current=current.getPrevious())
            if(current.getOpcode()>=0)return current;
        return null;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode node){
        for(AbstractInsnNode current=node==null?null:node.getNext();current!=null;current=current.getNext())
            if(current.getOpcode()>=0)return current;
        return null;
    }
}
