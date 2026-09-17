package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LegacyRegisteredBlockRenderTypeAnalyzerTest {
    private static final String BLOCK = "foreign/render/BlockField";
    private static final String IDS = "foreign/render/Ids";

    @Test void directConstantRenderTypeIsProven() {
        MethodNode method=renderMethod();method.instructions.add(new IntInsnNode(Opcodes.BIPUSH,13));method.instructions.add(new InsnNode(Opcodes.IRETURN));
        var identity=LegacyRegisteredBlockRenderTypeAnalyzer.directRenderIdentity(method);assertNotNull(identity);assertTrue(identity.isConstant(13));assertNull(identity.fieldOwner());
    }

    @Test void directStaticFieldRenderTypeIsPreservedSymbolically() {
        MethodNode method=renderMethod();method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,IDS,"cross","I"));method.instructions.add(new InsnNode(Opcodes.IRETURN));
        var identity=LegacyRegisteredBlockRenderTypeAnalyzer.directRenderIdentity(method);assertNotNull(identity);assertNull(identity.constant());assertEquals(IDS,identity.fieldOwner());assertEquals("cross",identity.fieldName());
    }

    @Test void constructorBoundConstantAndStaticFieldAreProvenWithoutGuessing() {
        ClassNode block=blockClass();MethodNode render=block.methods.stream().filter(m->m.name.equals("func_149645_b")).findFirst().orElseThrow();
        Map<String,ClassNode> classes=new LinkedHashMap<>();classes.put(BLOCK,block);classes.put("foreign/render/Bootstrap",allocationClass("cross"));

        var numeric=registration(List.of(arg(9),arg(13),arg(1)));
        var numericIdentity=LegacyRegisteredBlockRenderTypeAnalyzer.constructorBoundRenderIdentity(classes,numeric,render);
        assertNotNull(numericIdentity);assertTrue(numericIdentity.isConstant(13));

        var symbolic=registration(List.of(arg(9),new LegacyRegistryAnalyzer.ConstructorArgument("I",null),arg(1)));
        var symbolicIdentity=LegacyRegisteredBlockRenderTypeAnalyzer.constructorBoundRenderIdentity(classes,symbolic,render);
        assertNotNull(symbolicIdentity);assertEquals(IDS,symbolicIdentity.fieldOwner());assertEquals("cross",symbolicIdentity.fieldName());
    }

    @Test void ambiguousConstructorAllocationsFailClosed() {
        ClassNode block=blockClass();MethodNode render=block.methods.stream().filter(m->m.name.equals("func_149645_b")).findFirst().orElseThrow();
        ClassNode caller=allocationClass("cross","other");
        Map<String,ClassNode> classes=Map.of(BLOCK,block,"foreign/render/Bootstrap",caller);
        var symbolic=registration(List.of(arg(9),new LegacyRegistryAnalyzer.ConstructorArgument("I",null),arg(1)));
        assertNull(LegacyRegisteredBlockRenderTypeAnalyzer.constructorBoundRenderIdentity(classes,symbolic,render));
    }

    @Test void dynamicOrMixedReturnsFailClosed() {
        MethodNode dynamic=renderMethod();dynamic.instructions.add(new VarInsnNode(Opcodes.ILOAD,1));dynamic.instructions.add(new InsnNode(Opcodes.IRETURN));assertNull(LegacyRegisteredBlockRenderTypeAnalyzer.directRenderIdentity(dynamic));
        MethodNode mixed=renderMethod();mixed.instructions.add(new InsnNode(Opcodes.ICONST_1));mixed.instructions.add(new InsnNode(Opcodes.IRETURN));mixed.instructions.add(new IntInsnNode(Opcodes.BIPUSH,13));mixed.instructions.add(new InsnNode(Opcodes.IRETURN));assertNull(LegacyRegisteredBlockRenderTypeAnalyzer.directRenderIdentity(mixed));
    }

    private static LegacyRegistryAnalyzer.ConstructorArgument arg(int value){return new LegacyRegistryAnalyzer.ConstructorArgument("I",value);}
    private static LegacyRegistryAnalyzer.Registration registration(List<LegacyRegistryAnalyzer.ConstructorArgument> args){
        return new LegacyRegistryAnalyzer.Registration(LegacyRegistryAnalyzer.Kind.BLOCK,"fixture","fixture",BLOCK,null,"(III)V",args,"foreign/render/Bootstrap","preInit","()V");
    }
    private static ClassNode blockClass(){
        ClassNode c=new ClassNode(Opcodes.ASM9);c.name=BLOCK;c.superName="net/minecraft/block/Block";c.fields.add(new FieldNode(Opcodes.ACC_PRIVATE|Opcodes.ACC_FINAL,"renderType","I",null,null));
        MethodNode ctor=new MethodNode(Opcodes.ASM9,Opcodes.ACC_PUBLIC,"<init>","(III)V",null,null);ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));ctor.instructions.add(new VarInsnNode(Opcodes.ILOAD,2));ctor.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,BLOCK,"renderType","I"));ctor.instructions.add(new InsnNode(Opcodes.RETURN));ctor.maxLocals=4;ctor.maxStack=2;c.methods.add(ctor);
        MethodNode render=renderMethod();render.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));render.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,BLOCK,"renderType","I"));render.instructions.add(new InsnNode(Opcodes.IRETURN));render.maxLocals=1;render.maxStack=1;c.methods.add(render);return c;
    }
    private static ClassNode allocationClass(String... fields){
        ClassNode c=new ClassNode(Opcodes.ASM9);c.name="foreign/render/Bootstrap";c.superName="java/lang/Object";
        MethodNode m=new MethodNode(Opcodes.ASM9,Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"preInit","()V",null,null);
        for(String field:fields)appendAllocation(m,field);
        m.instructions.add(new InsnNode(Opcodes.RETURN));m.maxLocals=0;m.maxStack=5;c.methods.add(m);return c;
    }
    private static void appendAllocation(MethodNode m,String field){
        m.instructions.add(new TypeInsnNode(Opcodes.NEW,BLOCK));m.instructions.add(new InsnNode(Opcodes.DUP));m.instructions.add(new IntInsnNode(Opcodes.BIPUSH,9));m.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,IDS,field,"I"));m.instructions.add(new InsnNode(Opcodes.ICONST_1));m.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,BLOCK,"<init>","(III)V",false));m.instructions.add(new InsnNode(Opcodes.POP));
    }
    private static MethodNode renderMethod(){return new MethodNode(Opcodes.ASM9,Opcodes.ACC_PUBLIC,"func_149645_b","()I",null,null);}
}
