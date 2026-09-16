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

/** Package-private ASM helpers shared by the grid-pot source proofs. */
final class LegacyGridPotAsm {
    private static final String ITEM_STACK="net/minecraft/item/ItemStack";
    private LegacyGridPotAsm() { }

    static String fieldAssignedArray(MethodNode method,String owner,String desc,int opcode,int size){
        List<AbstractInsnNode> code=real(method);
        for(int i=1;i+1<code.size();i++){
            Integer value=intConstant(code.get(i-1)); AbstractInsnNode allocation=code.get(i);
            if(value==null||value!=size||allocation.getOpcode()!=opcode)continue;
            if(opcode==Opcodes.ANEWARRAY&&(!(allocation instanceof TypeInsnNode type)||!ITEM_STACK.equals(type.desc)))continue;
            for(int j=i+1;j<Math.min(code.size(),i+4);j++)if(code.get(j) instanceof FieldInsnNode field
                    && field.getOpcode()==Opcodes.PUTFIELD&&owner.equals(field.owner)&&desc.equals(field.desc))return field.name;
        }
        return null;
    }
    static boolean hasArrayStore(MethodNode method,String owner,String fieldName,int storeOpcode,Integer constant){
        if(method==null)return false; boolean field=false,value=constant==null;
        for(AbstractInsnNode insn:method.instructions){
            if(insn instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&owner.equals(f.owner)&&fieldName.equals(f.name))field=true;
            Integer c=intConstant(insn); if(constant!=null&&c!=null&&c.equals(constant))value=true;
            if(insn.getOpcode()==storeOpcode&&field&&value)return true;
        }
        return false;
    }
    static boolean readsArrayField(MethodNode method,String owner,String fieldName,int loadOpcode){
        if(method==null)return false; boolean field=false;
        for(AbstractInsnNode insn:method.instructions){
            if(insn instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&owner.equals(f.owner)&&fieldName.equals(f.name))field=true;
            if(field&&insn.getOpcode()==loadOpcode)return true;
        }
        return false;
    }
    static MethodNode uniqueStaticMethod(ClassNode owner,String desc){
        MethodNode result=null; for(MethodNode method:owner.methods){
            if((method.access&Opcodes.ACC_STATIC)==0||!desc.equals(method.desc))continue;
            if(result!=null)return null; result=method;
        } return result;
    }
    static MethodNode findMethod(ClassNode owner,String desc,java.util.function.Predicate<MethodNode> predicate){
        MethodNode result=null; for(MethodNode method:owner.methods){
            if((method.access&Opcodes.ACC_STATIC)!=0||!desc.equals(method.desc)||!predicate.test(method))continue;
            if(result!=null)return null; result=method;
        } return result;
    }
    static MethodNode ownMethod(ClassNode owner,Set<String> names,String desc){
        if(owner==null)return null;
        for(MethodNode method:owner.methods)if((method.access&Opcodes.ACC_STATIC)==0&&names.contains(method.name)&&desc.equals(method.desc))return method;
        return null;
    }
    static MethodNode method(Map<String,ClassNode> classes,String owner,Set<String> names,String desc){
        Set<String> visited=new HashSet<>();
        while(owner!=null&&visited.add(owner)){
            ClassNode node=classes.get(owner); if(node==null)return null;
            MethodNode found=ownMethod(node,names,desc); if(found!=null)return found; owner=node.superName;
        } return null;
    }
    static boolean inherits(Map<String,ClassNode> classes,String type,String base){
        Set<String> visited=new HashSet<>();
        while(type!=null&&visited.add(type)){if(base.equals(type))return true;ClassNode node=classes.get(type);if(node==null)return false;type=node.superName;}return false;
    }
    static String uniqueCreatedType(MethodNode method){
        if(method==null)return null; String result=null;
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW){
            if(result!=null&&!result.equals(type.desc))return null; result=type.desc;
        } return result;
    }
    static boolean calls(MethodNode method,String owner,String name,String desc){return countCalls(method,owner,name,desc)>0;}
    static boolean callsNamed(MethodNode method,String name,String desc){
        if(method==null)return false; for(AbstractInsnNode insn:method.instructions)
            if(insn instanceof MethodInsnNode call&&name.equals(call.name)&&desc.equals(call.desc))return true; return false;
    }
    static int countCalls(MethodNode method,String owner,String name,String desc){
        if(method==null)return 0; int count=0; for(AbstractInsnNode insn:method.instructions)
            if(insn instanceof MethodInsnNode call&&owner.equals(call.owner)&&name.equals(call.name)&&desc.equals(call.desc))count++; return count;
    }
    static boolean field(MethodNode method,String owner,String name,String desc){
        if(method==null)return false; for(AbstractInsnNode insn:method.instructions)
            if(insn instanceof FieldInsnNode f&&owner.equals(f.owner)&&name.equals(f.name)&&desc.equals(f.desc))return true; return false;
    }
    static boolean typed(MethodNode method,int opcode,String type){
        if(method==null)return false; for(AbstractInsnNode insn:method.instructions)
            if(insn instanceof TypeInsnNode t&&t.getOpcode()==opcode&&type.equals(t.desc))return true; return false;
    }
    static boolean opcode(MethodNode method,int opcode){if(method==null)return false;for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()==opcode)return true;return false;}
    static boolean string(MethodNode method,String value){if(method==null)return false;for(AbstractInsnNode insn:method.instructions)if(insn instanceof LdcInsnNode ldc&&value.equals(ldc.cst))return true;return false;}
    static boolean hasInt(MethodNode method,int expected){if(method==null)return false;for(AbstractInsnNode insn:method.instructions){Integer value=intConstant(insn);if(value!=null&&value==expected)return true;}return false;}
    static boolean containsFloat(MethodNode method,float expected){if(method==null)return false;for(AbstractInsnNode insn:method.instructions){Float value=floatConstant(insn);if(value!=null&&Float.compare(value,expected)==0)return true;}return false;}
    static Float constructorFloatBeforeCall(ClassNode owner,String callName,String callDesc,float expected){
        MethodNode ctor=ownMethod(owner,Set.of("<init>"),"()V"); if(ctor==null||!containsFloat(ctor,expected))return null;
        for(AbstractInsnNode insn:ctor.instructions)if(insn instanceof MethodInsnNode call&&callName.equals(call.name)&&callDesc.equals(call.desc))return expected;return null;
    }
    static boolean returnsNull(MethodNode method){List<AbstractInsnNode> code=real(method);return code.size()==2&&code.get(0).getOpcode()==Opcodes.ACONST_NULL&&code.get(1).getOpcode()==Opcodes.ARETURN;}
    static Boolean returnedBoolean(MethodNode method){
        List<AbstractInsnNode> code=real(method); if(code.size()!=2||code.get(1).getOpcode()!=Opcodes.IRETURN)return null;
        Integer value=intConstant(code.get(0)); return value==null||(value!=0&&value!=1)?null:value==1;
    }
    static boolean directBooleanReturn(MethodNode method,boolean value){
        if(method==null)return false; int op=value?Opcodes.ICONST_1:Opcodes.ICONST_0;
        for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()==op){AbstractInsnNode next=nextReal(insn);if(next!=null&&next.getOpcode()==Opcodes.IRETURN)return true;}return false;
    }
    static Integer intConstant(AbstractInsnNode insn){
        if(insn==null)return null; return switch(insn.getOpcode()){
            case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;
            case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;
            case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)insn).operand;
            case Opcodes.LDC->((LdcInsnNode)insn).cst instanceof Integer value?value:null;default->null;};
    }
    static Float floatConstant(AbstractInsnNode insn){
        if(insn==null)return null; return switch(insn.getOpcode()){
            case Opcodes.FCONST_0->0F;case Opcodes.FCONST_1->1F;case Opcodes.FCONST_2->2F;
            case Opcodes.LDC->((LdcInsnNode)insn).cst instanceof Float value?value:null;default->null;};
    }
    static List<AbstractInsnNode> real(MethodNode method){List<AbstractInsnNode> result=new ArrayList<>();if(method!=null)for(AbstractInsnNode insn:method.instructions)if(insn.getOpcode()>=0)result.add(insn);return result;}
    static AbstractInsnNode nextReal(AbstractInsnNode insn){for(AbstractInsnNode next=insn==null?null:insn.getNext();next!=null;next=next.getNext())if(next.getOpcode()>=0)return next;return null;}
    static Map<String,ClassNode> loadClasses(Path jarPath)throws IOException{
        Map<String,ClassNode> classes=new LinkedHashMap<>();
        try(JarFile jar=new JarFile(jarPath.toFile())){Enumeration<JarEntry> entries=jar.entries();while(entries.hasMoreElements()){
            JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class"))continue;
            try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException ignored){}
        }} return classes;
    }
}
