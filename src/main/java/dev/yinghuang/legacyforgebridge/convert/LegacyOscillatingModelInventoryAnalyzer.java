package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Fail-closed inventory-presentation proof for an already-proven oscillating world renderer. */
public final class LegacyOscillatingModelInventoryAnalyzer {
    public record Proof(String handlerClass,String uidField,String routeClass,String inventoryMapField,
                        String inventoryRendererClass,String sourceRendererClass,
                        float yawDegrees,float translateY,float scale,float dynamicAngleDegrees) { }
    public record Analysis(Optional<Proof> proof,List<String> diagnostics) {
        public Analysis { proof=proof==null?Optional.empty():proof;diagnostics=List.copyOf(diagnostics); }
    }
    private record Uid(String owner,String field) { }
    private record Route(String routeClass,String mapField,String inventoryInterface) { }

    public Analysis analyze(Path jarPath,LegacyOscillatingModelBlockAnalyzer.Rule rule,
                            LegacyOscillatingModelPresentationAnalyzer.Presentation presentation)throws IOException{
        Objects.requireNonNull(rule,"rule");Objects.requireNonNull(presentation,"presentation");
        Map<String,ClassNode> classes=loadClasses(jarPath);List<String> diagnostics=new ArrayList<>();
        Uid uid=renderUid(classes,rule.sourceBlockClass());
        if(uid==null){diagnostics.add("Oscillating block render type is not a direct source UID field.");return new Analysis(Optional.empty(),diagnostics);}
        Route route=registered3dRoute(classes,uid);
        if(route==null){diagnostics.add("Oscillating render UID is not proven to use the source 3D inventory routing map.");return new Analysis(Optional.empty(),diagnostics);}
        String inventoryRenderer=inventoryRenderer(classes,uid,route);
        if(inventoryRenderer==null){diagnostics.add("Oscillating inventory map does not uniquely bind the block UID to one renderer.");return new Analysis(Optional.empty(),diagnostics);}
        if(!wrapperTargets(classes.get(inventoryRenderer),route.inventoryInterface(),presentation.sourceRendererClass())){
            diagnostics.add("Oscillating inventory wrapper does not delegate to the proven world renderer renderInv().");return new Analysis(Optional.empty(),diagnostics);}
        if(!renderInv(classes.get(presentation.sourceRendererClass()),presentation.sourceModelClass())){
            diagnostics.add("Oscillating renderInv transform/model calls are not the admitted source shape.");return new Analysis(Optional.empty(),diagnostics);}
        return new Analysis(Optional.of(new Proof(uid.owner(),uid.field(),route.routeClass(),route.mapField(),inventoryRenderer,
                presentation.sourceRendererClass(),180F,-.25F,1F,0F)),List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static Uid renderUid(Map<String,ClassNode> classes,String blockClass){
        MethodNode method=effectiveMethod(classes,blockClass,Set.of("getRenderType","func_149645_b"),"()I");if(method==null)return null;List<AbstractInsnNode> code=real(method);
        if(code.size()!=2||!(code.get(0) instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.GETSTATIC||!field.desc.equals("I")||code.get(1).getOpcode()!=Opcodes.IRETURN)return null;
        return classes.containsKey(field.owner)?new Uid(field.owner,field.name):null;
    }
    private static Route registered3dRoute(Map<String,ClassNode> classes,Uid uid){
        ClassNode handler=classes.get(uid.owner());if(handler==null)return null;MethodNode clinit=ownMethod(handler,Set.of("<clinit>"),"()V");if(clinit==null)return null;
        String generator=null;List<AbstractInsnNode> init=real(clinit);
        for(int i=1;i<init.size();i++)if(init.get(i) instanceof FieldInsnNode put&&put.getOpcode()==Opcodes.PUTSTATIC&&put.owner.equals(uid.owner())&&put.name.equals(uid.field())&&put.desc.equals("I")&&init.get(i-1) instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals(uid.owner())&&call.desc.equals("()I"))generator=call.name;
        if(generator==null)return null;MethodNode make=ownMethod(handler,Set.of(generator),"()I");if(make==null||!calls(make,"cpw/mods/fml/client/registry/RenderingRegistry","getNextAvailableRenderId","()I")||!calls(make,"cpw/mods/fml/client/registry/RenderingRegistry","registerBlockHandler","(ILcpw/mods/fml/client/registry/ISimpleBlockRenderingHandler;)V"))return null;
        Set<String> routeClasses=new LinkedHashSet<>();for(AbstractInsnNode insn:make.instructions)if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(uid.owner())&&field.desc.startsWith("L")&&field.desc.endsWith(";")){String type=Type.getType(field.desc).getInternalName();if(classes.containsKey(type))routeClasses.add(type);}if(routeClasses.size()!=1)return null;
        String routeClass=routeClasses.iterator().next();ClassNode routeNode=classes.get(routeClass);MethodNode routeMethod=null;for(MethodNode method:routeNode.methods)if(method.desc.equals("(Lnet/minecraft/block/Block;IILnet/minecraft/client/renderer/RenderBlocks;)V")){routeMethod=method;break;}if(routeMethod==null)return null;
        Set<String> mapFields=new LinkedHashSet<>(),interfaces=new LinkedHashSet<>();boolean key=false,get=false;
        for(AbstractInsnNode insn:routeMethod.instructions){if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC&&field.owner.equals(uid.owner())&&field.desc.equals("Ljava/util/HashMap;"))mapFields.add(field.name);if(insn instanceof MethodInsnNode call&&call.owner.equals("java/util/HashMap")&&call.name.equals("get")&&call.desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;"))get=true;if(insn instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEINTERFACE&&call.desc.equals("(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/block/Block;I)V"))interfaces.add(call.owner);if(insn instanceof MethodInsnNode call&&call.owner.equals("java/lang/Integer")&&call.name.equals("valueOf")&&call.desc.equals("(I)Ljava/lang/Integer;"))key=true;}
        return mapFields.size()==1&&interfaces.size()==1&&get&&key?new Route(routeClass,mapFields.iterator().next(),interfaces.iterator().next()):null;
    }
    private static String inventoryRenderer(Map<String,ClassNode> classes,Uid uid,Route route){
        ClassNode handler=classes.get(uid.owner());Set<String> renderers=new LinkedHashSet<>();
        for(MethodNode method:handler.methods){List<AbstractInsnNode> code=real(method);for(int i=0;i<code.size();i++){
            if(!(code.get(i) instanceof MethodInsnNode call)||!call.owner.equals("java/util/HashMap")||!call.name.equals("put")||!call.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))continue;
            boolean map=false,key=false;String renderer=null;for(int j=Math.max(0,i-10);j<i;j++){AbstractInsnNode value=code.get(j);if(value instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC&&field.owner.equals(uid.owner())&&field.name.equals(route.mapField())&&field.desc.equals("Ljava/util/HashMap;"))map=true;if(value instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC&&field.owner.equals(uid.owner())&&field.name.equals(uid.field())&&field.desc.equals("I"))key=true;if(value instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW&&classes.containsKey(type.desc))renderer=type.desc;}if(map&&key&&renderer!=null)renderers.add(renderer);
        }}
        return renderers.size()==1?renderers.iterator().next():null;
    }
    private static boolean wrapperTargets(ClassNode wrapper,String inventoryInterface,String sourceRenderer){
        if(wrapper==null||!wrapper.interfaces.contains(inventoryInterface))return false;
        for(MethodNode method:wrapper.methods)if(method.desc.equals("(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/block/Block;I)V"))for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(sourceRenderer)&&call.name.equals("renderInv")&&call.desc.equals("()V"))return true;
        return false;
    }
    private static boolean renderInv(ClassNode renderer,String model){
        MethodNode method=ownMethod(renderer,Set.of("renderInv"),"()V");if(method==null||!calls(method,"org/lwjgl/opengl/GL11","glPushMatrix","()V")||!calls(method,"org/lwjgl/opengl/GL11","glPopMatrix","()V"))return false;
        List<AbstractInsnNode> code=real(method);boolean rotate=false,translate=false,scale=false,normal=false,dynamic=false;
        for(int i=0;i<code.size();i++){AbstractInsnNode insn=code.get(i);
            if(insn instanceof MethodInsnNode call&&call.owner.equals("org/lwjgl/opengl/GL11")&&call.name.equals("glRotatef")&&call.desc.equals("(FFFF)V")&&i>=4&&Float.valueOf(180F).equals(floatConstant(code.get(i-4)))&&Float.valueOf(0F).equals(floatConstant(code.get(i-3)))&&Float.valueOf(1F).equals(floatConstant(code.get(i-2)))&&Float.valueOf(0F).equals(floatConstant(code.get(i-1))))rotate=true;
            if(insn instanceof MethodInsnNode call&&call.owner.equals("org/lwjgl/opengl/GL11")&&call.name.equals("glTranslatef")&&call.desc.equals("(FFF)V")&&i>=3&&Float.valueOf(0F).equals(floatConstant(code.get(i-3)))&&Float.valueOf(-.25F).equals(floatConstant(code.get(i-2)))&&Float.valueOf(0F).equals(floatConstant(code.get(i-1))))translate=true;
            if(insn instanceof MethodInsnNode call&&call.owner.equals("org/lwjgl/opengl/GL11")&&call.name.equals("glScalef")&&call.desc.equals("(FFF)V")&&i>=3&&Float.valueOf(1F).equals(floatConstant(code.get(i-3)))&&Float.valueOf(1F).equals(floatConstant(code.get(i-2)))&&Float.valueOf(1F).equals(floatConstant(code.get(i-1))))scale=true;
            if(insn instanceof MethodInsnNode call&&call.owner.equals(model)&&call.desc.equals("(Lnet/minecraft/entity/Entity;FFFFFF)V")&&i>0&&Float.valueOf(.0625F).equals(floatConstant(code.get(i-1))))normal=true;
            if(insn instanceof MethodInsnNode call&&call.owner.equals(model)&&call.desc.equals("(F)V")&&i>0&&Float.valueOf(0F).equals(floatConstant(code.get(i-1))))dynamic=true;
        }
        return rotate&&translate&&scale&&normal&&dynamic;
    }
    private static MethodNode effectiveMethod(Map<String,ClassNode> classes,String owner,Set<String> names,String desc){Set<String> seen=new HashSet<>();while(owner!=null&&seen.add(owner)){ClassNode node=classes.get(owner);if(node==null)return null;MethodNode method=ownMethod(node,names,desc);if(method!=null)return method;owner=node.superName;}return null;}
    private static MethodNode ownMethod(ClassNode owner,Set<String> names,String desc){if(owner==null)return null;for(MethodNode method:owner.methods)if(names.contains(method.name)&&desc.equals(method.desc))return method;return null;}
    private static boolean calls(MethodNode method,String owner,String name,String desc){if(method==null)return false;for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(desc))return true;return false;}
    private static Float floatConstant(AbstractInsnNode insn){return switch(insn.getOpcode()){case Opcodes.FCONST_0->0F;case Opcodes.FCONST_1->1F;case Opcodes.FCONST_2->2F;case Opcodes.LDC->insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Float value?value:null;default->null;};}
    private static List<AbstractInsnNode> real(MethodNode method){List<AbstractInsnNode> result=new ArrayList<>();if(method!=null)for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)result.add(insn);return result;}
    private static Map<String,ClassNode> loadClasses(Path jarPath)throws IOException{Map<String,ClassNode> classes=new LinkedHashMap<>();try(JarFile jar=new JarFile(jarPath.toFile(),false)){var entries=jar.entries();while(entries.hasMoreElements()){JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class"))continue;try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException ignored){}}}return classes;}
}
