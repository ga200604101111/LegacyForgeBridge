package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

final class LegacySeatBedAsm {
    private LegacySeatBedAsm() { }

    static Map<String, ClassNode> loadClasses(Path jarPath) throws IOException {
        Map<String, ClassNode> out = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile(), false)) {
            var e = jar.entries();
            while (e.hasMoreElements()) {
                var entry = e.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (var in = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    out.put(node.name, node);
                } catch (RuntimeException ignored) { }
            }
        }
        return out;
    }

    static boolean inherits(Map<String, ClassNode> classes, String owner, String target) {
        Set<String> seen = new HashSet<>();
        while (owner != null && seen.add(owner)) {
            if (target.equals(owner)) return true;
            ClassNode node = classes.get(owner);
            owner = node == null ? null : node.superName;
        }
        return false;
    }

    static MethodNode method(Map<String, ClassNode> classes, String owner, Set<String> names, String desc) {
        Set<String> seen = new HashSet<>();
        while (owner != null && seen.add(owner)) {
            ClassNode node = classes.get(owner);
            if (node == null) return null;
            MethodNode own = ownMethod(node, names, desc);
            if (own != null) return own;
            owner = node.superName;
        }
        return null;
    }

    static MethodNode ownMethod(ClassNode owner, Set<String> names, String desc) {
        if (owner == null) return null;
        for (MethodNode m : owner.methods) if ((m.access & Opcodes.ACC_STATIC) == 0 && names.contains(m.name) && desc.equals(m.desc)) return m;
        return null;
    }

    static List<AbstractInsnNode> real(MethodNode m) {
        List<AbstractInsnNode> out = new ArrayList<>();
        if (m != null) for (AbstractInsnNode i : m.instructions) if (i.getOpcode() >= 0) out.add(i);
        return out;
    }

    static boolean constantBoolean(MethodNode m, boolean value) {
        Integer v = constantIntReturn(m);
        return v != null && v == (value ? 1 : 0);
    }

    static Integer constantIntReturn(MethodNode m) {
        Integer result = null;
        List<AbstractInsnNode> code = real(m);
        for (int i = 1; i < code.size(); i++) if (code.get(i).getOpcode() == Opcodes.IRETURN) {
            Integer v = intConstant(code.get(i - 1));
            if (v == null || (result != null && !result.equals(v))) return null;
            result = v;
        }
        return result;
    }

    static boolean emptyVoidMethod(MethodNode m) {
        List<AbstractInsnNode> code = real(m);
        return code.size() == 1 && code.getFirst().getOpcode() == Opcodes.RETURN;
    }

    static boolean containsInt(MethodNode m, int expected) { return real(m).stream().anyMatch(i -> Objects.equals(intConstant(i), expected)); }
    static boolean containsFloat(MethodNode m, float expected) { return real(m).stream().anyMatch(i -> { Float v=floatConstant(i); return v!=null&&Float.compare(v,expected)==0; }); }
    static boolean containsFloat(List<AbstractInsnNode> code, float expected) { return code != null && code.stream().anyMatch(i -> { Float v=floatConstant(i); return v!=null&&Float.compare(v,expected)==0; }); }
    static boolean containsDouble(MethodNode m, double expected) { return real(m).stream().anyMatch(i -> { Double v=doubleConstant(i); return v!=null&&Double.compare(v,expected)==0; }); }
    static boolean containsLong(MethodNode m, long expected) { return real(m).stream().anyMatch(i -> Objects.equals(longConstant(i), expected)); }
    static boolean containsOpcode(MethodNode m, int opcode) { return real(m).stream().anyMatch(i -> i.getOpcode() == opcode); }

    static int countCalls(MethodNode m, String owner, String desc) {
        int n=0; if(m!=null) for(AbstractInsnNode i:m.instructions) if(i instanceof MethodInsnNode c&&owner.equals(c.owner)&&desc.equals(c.desc)) n++; return n;
    }
    static boolean calls(MethodNode m, String owner, String desc) { return countCalls(m, owner, desc) > 0; }
    static int countCallsByDescriptor(MethodNode m, String owner, String desc) { return countCalls(m, owner, desc); }
    static boolean callsName(MethodNode m, String owner, Set<String> names, String desc) {
        if(m!=null) for(AbstractInsnNode i:m.instructions) if(i instanceof MethodInsnNode c&&owner.equals(c.owner)&&desc.equals(c.desc)&&names.contains(c.name)) return true; return false;
    }
    static boolean callsName(List<AbstractInsnNode> code, String owner, String name) {
        if(code!=null) for(AbstractInsnNode i:code) if(i instanceof MethodInsnNode c&&owner.equals(c.owner)&&name.equals(c.name)) return true; return false;
    }
    static boolean callsOwner(MethodNode m, String owner) {
        if(m!=null) for(AbstractInsnNode i:m.instructions) if(i instanceof MethodInsnNode c&&owner.equals(c.owner)) return true; return false;
    }
    static boolean callsHierarchyName(MethodNode m, Map<String,ClassNode> classes, String target, Set<String> names, String desc) {
        if(m!=null) for(AbstractInsnNode i:m.instructions) if(i instanceof MethodInsnNode c&&desc.equals(c.desc)&&names.contains(c.name)&&(target.equals(c.owner)||inherits(classes,c.owner,target))) return true; return false;
    }

    static boolean hasField(MethodNode m,int opcode,String owner,String desc,Set<String> names){
        if(m!=null)for(AbstractInsnNode i:m.instructions)if(i instanceof FieldInsnNode f&&f.getOpcode()==opcode&&owner.equals(f.owner)&&desc.equals(f.desc)&&(names==null||names.contains(f.name)))return true;return false;
    }
    static boolean hasHierarchyField(MethodNode m,int opcode,Map<String,ClassNode> classes,String target,String desc,Set<String> names){
        if(m!=null)for(AbstractInsnNode i:m.instructions)if(i instanceof FieldInsnNode f&&f.getOpcode()==opcode&&desc.equals(f.desc)&&(names==null||names.contains(f.name))&&(target.equals(f.owner)||inherits(classes,f.owner,target)))return true;return false;
    }

    static boolean fieldBranch(MethodNode m,int opcode,String owner,String desc,Set<String> names,int... branchOpcodes){
        List<AbstractInsnNode> code=real(m);Set<Integer>b=new HashSet<>();for(int x:branchOpcodes)b.add(x);
        for(int i=0;i<code.size();i++)if(code.get(i) instanceof FieldInsnNode f&&f.getOpcode()==opcode&&owner.equals(f.owner)&&desc.equals(f.desc)&&(names==null||names.contains(f.name)))
            for(int j=i+1;j<Math.min(code.size(),i+4);j++)if(b.contains(code.get(j).getOpcode()))return true;
        return false;
    }
    static boolean intMaskBranch(MethodNode m,int mask,int branch){
        List<AbstractInsnNode> c=real(m);for(int i=2;i<c.size();i++)if(c.get(i).getOpcode()==branch&&c.get(i-1).getOpcode()==Opcodes.IAND&&Objects.equals(intConstant(c.get(i-2)),mask))return true;return false;
    }
    static boolean intLocalCompareBranch(MethodNode m,int local,int expected,int... branchOpcodes){
        List<AbstractInsnNode> c=real(m);Set<Integer>b=new HashSet<>();for(int x:branchOpcodes)b.add(x);
        for(int i=0;i+2<c.size();i++)if(c.get(i) instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ILOAD&&v.var==local&&Objects.equals(intConstant(c.get(i+1)),expected)&&b.contains(c.get(i+2).getOpcode()))return true;return false;
    }
    static boolean sourceBooleanStaticFieldBranch(MethodNode m,Map<String,ClassNode> classes,int... branchOpcodes){
        List<AbstractInsnNode> c=real(m);Set<Integer>b=new HashSet<>();for(int x:branchOpcodes)b.add(x);
        for(int i=0;i<c.size();i++)if(c.get(i) instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETSTATIC&&"Z".equals(f.desc)&&classes.containsKey(f.owner))
            for(int j=i+1;j<Math.min(c.size(),i+4);j++)if(b.contains(c.get(j).getOpcode()))return true;return false;
    }

    static boolean checkcasts(MethodNode m,String type){if(m!=null)for(AbstractInsnNode i:m.instructions)if(i instanceof TypeInsnNode t&&t.getOpcode()==Opcodes.CHECKCAST&&type.equals(t.desc))return true;return false;}
    static String uniqueNewSubtype(MethodNode m,Map<String,ClassNode> classes,String target){LinkedHashSet<String>s=new LinkedHashSet<>();if(m!=null)for(AbstractInsnNode i:m.instructions)if(i instanceof TypeInsnNode t&&t.getOpcode()==Opcodes.NEW&&inherits(classes,t.desc,target))s.add(t.desc);return s.size()==1?s.getFirst():null;}
    static String directSourceSubclassOf(Map<String,ClassNode> classes,String owner,String externalBase){Set<String>s=new HashSet<>();while(owner!=null&&s.add(owner)){ClassNode n=classes.get(owner);if(n==null)return null;if(externalBase.equals(n.superName))return owner;owner=n.superName;}return null;}

    static boolean writesBooleanField(MethodNode m,String owner,String name,boolean value){
        List<AbstractInsnNode> c=real(m);int expected=value?Opcodes.ICONST_1:Opcodes.ICONST_0;
        for(int i=1;i<c.size();i++)if(c.get(i) instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.PUTFIELD&&owner.equals(f.owner)&&name.equals(f.name)&&"Z".equals(f.desc)&&c.get(i-1).getOpcode()==expected)return true;return false;
    }
    static String uniqueBooleanFieldUsedBy(MethodNode m,ClassNode owner){
        Set<String>d=new LinkedHashSet<>();for(FieldNode f:owner.fields)if("Z".equals(f.desc)&&(f.access&Opcodes.ACC_STATIC)==0)d.add(f.name);
        Set<String>u=new LinkedHashSet<>();if(m!=null)for(AbstractInsnNode i:m.instructions)if(i instanceof FieldInsnNode f&&owner.name.equals(f.owner)&&"Z".equals(f.desc)&&d.contains(f.name)&&(f.getOpcode()==Opcodes.GETFIELD||f.getOpcode()==Opcodes.PUTFIELD))u.add(f.name);return u.size()==1?u.iterator().next():null;
    }

    static Map<String,LegacyRegistryAnalyzer.FieldBinding> bindingMap(LegacyRegistryAnalyzer.Analysis a){Map<String,LegacyRegistryAnalyzer.FieldBinding>m=new LinkedHashMap<>();for(var b:a.fieldBindings())m.put(key(b.owner(),b.name(),b.descriptor()),b);return m;}
    static LegacyRegistryAnalyzer.FieldBinding uniqueBinding(MethodNode m,Map<String,LegacyRegistryAnalyzer.FieldBinding>bindings,LegacyRegistryAnalyzer.Kind kind){Set<LegacyRegistryAnalyzer.FieldBinding>f=new LinkedHashSet<>();if(m!=null)for(AbstractInsnNode i:m.instructions)if(i instanceof FieldInsnNode x&&x.getOpcode()==Opcodes.GETSTATIC){var b=bindings.get(key(x.owner,x.name,x.desc));if(b!=null&&b.kind()==kind)f.add(b);}return f.size()==1?f.iterator().next():null;}
    private static String key(String owner,String name,String desc){return owner+"."+name+":"+desc;}

    static String classConst(AbstractInsnNode i){
        return i instanceof LdcInsnNode l&&l.cst instanceof Type t&&t.getSort()==Type.OBJECT?t.getInternalName():null;
    }
    static String newType(AbstractInsnNode i){
        return i instanceof TypeInsnNode t&&t.getOpcode()==Opcodes.NEW?t.desc:null;
    }

    private static Integer intConstant(AbstractInsnNode i){if(i==null)return null;return switch(i.getOpcode()){case Opcodes.ICONST_M1->-1;case Opcodes.ICONST_0->0;case Opcodes.ICONST_1->1;case Opcodes.ICONST_2->2;case Opcodes.ICONST_3->3;case Opcodes.ICONST_4->4;case Opcodes.ICONST_5->5;case Opcodes.BIPUSH,Opcodes.SIPUSH->((IntInsnNode)i).operand;case Opcodes.LDC->i instanceof LdcInsnNode l&&l.cst instanceof Integer v?v:null;default->null;};}
    private static Float floatConstant(AbstractInsnNode i){if(i==null)return null;return switch(i.getOpcode()){case Opcodes.FCONST_0->0F;case Opcodes.FCONST_1->1F;case Opcodes.FCONST_2->2F;case Opcodes.LDC->i instanceof LdcInsnNode l&&l.cst instanceof Float v?v:null;default->null;};}
    private static Double doubleConstant(AbstractInsnNode i){if(i==null)return null;return switch(i.getOpcode()){case Opcodes.DCONST_0->0D;case Opcodes.DCONST_1->1D;case Opcodes.LDC->i instanceof LdcInsnNode l&&l.cst instanceof Double v?v:null;default->null;};}
    private static Long longConstant(AbstractInsnNode i){if(i==null)return null;return switch(i.getOpcode()){case Opcodes.LCONST_0->0L;case Opcodes.LCONST_1->1L;case Opcodes.LDC->i instanceof LdcInsnNode l&&l.cst instanceof Long v?v:null;default->null;};}
}
