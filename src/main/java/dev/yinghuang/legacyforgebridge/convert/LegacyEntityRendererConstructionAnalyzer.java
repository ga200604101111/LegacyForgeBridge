package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Proves the no-arg source constructor chain used by a legacy entity renderer registration. */
public final class LegacyEntityRendererConstructionAnalyzer {
    private static final String VANILLA_RENDER = "net/minecraft/client/renderer/entity/Render";

    public record Proof(String rendererClass, boolean proven, List<String> sourceConstructorChain,
                        String terminalBaseClass, String reason) {
        public Proof { sourceConstructorChain = List.copyOf(sourceConstructorChain); }
    }

    private final Map<String,ClassNode> classes = new LinkedHashMap<>();

    public Proof prove(Path sourceJar, String rendererClass) throws IOException {
        classes.clear();load(sourceJar);
        if(rendererClass==null||rendererClass.isBlank())return new Proof(rendererClass,false,List.of(),null,"renderer-class-missing");
        List<String> chain=new ArrayList<>();String current=rendererClass;
        while(true){
            ClassNode node=classes.get(current);
            if(node==null){
                if(VANILLA_RENDER.equals(current))return new Proof(rendererClass,true,chain,current,"known-vanilla-render-base");
                return new Proof(rendererClass,false,chain,current,"constructor-chain-left-source-at-unsupported-base");
            }
            MethodNode ctor=null;
            for(MethodNode method:node.methods)if(method.name.equals("<init>")&&method.desc.equals("()V")){
                if(ctor!=null)return new Proof(rendererClass,false,chain,node.name,"ambiguous-noarg-constructor");ctor=method;
            }
            if(ctor==null)return new Proof(rendererClass,false,chain,node.name,"noarg-constructor-missing");
            if(!trivialDelegatingConstructor(node,ctor))return new Proof(rendererClass,false,chain,node.name,"noarg-constructor-has-side-effects");
            chain.add(node.name);current=node.superName;
            if(current==null)return new Proof(rendererClass,false,chain,null,"constructor-chain-has-no-terminal-base");
        }
    }

    private static boolean trivialDelegatingConstructor(ClassNode owner,MethodNode ctor){
        List<AbstractInsnNode> opcodes=new ArrayList<>();for(AbstractInsnNode instruction:ctor.instructions)if(instruction.getOpcode()>=0)opcodes.add(instruction);
        if(opcodes.size()!=3)return false;
        if(!(opcodes.get(0) instanceof VarInsnNode load)||load.getOpcode()!=Opcodes.ALOAD||load.var!=0)return false;
        if(!(opcodes.get(1) instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESPECIAL||!call.name.equals("<init>")||!call.desc.equals("()V")||!call.owner.equals(owner.superName))return false;
        return opcodes.get(2).getOpcode()==Opcodes.RETURN;
    }

    private void load(Path sourceJar)throws IOException{
        try(JarFile jar=new JarFile(sourceJar.toFile())){var entries=jar.entries();while(entries.hasMoreElements()){
            JarEntry entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().endsWith(".class")||entry.getName().equals("module-info.class"))continue;
            try(InputStream input=jar.getInputStream(entry)){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);}catch(RuntimeException ignored){}
        }}
    }
}
