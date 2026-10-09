import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;

/** Exact-base transform. Existing analyzer algorithms and method frames are retained verbatim. */
public final class TransformRev312 implements Opcodes {
    static final String PREFIX="dev/yinghuang/legacyforgebridge/convert/";
    static final String SHARED=PREFIX+"shared/";
    static final String REG=PREFIX+"LegacyRegistryAnalyzer", ENGINE=PREFIX+"LegacyConversionEngine";
    static final String ANALYSIS="L"+REG+"$Analysis;", SNAP="L"+SHARED+"SharedRegistryAnalysis$Snapshot;";
    static final String CONVERT_DESC="(Ljava/nio/file/Path;Ljava/lang/String;L"+PREFIX+"LegacyJarAnalyzer$Analysis;Ljava/nio/file/Path;Ljava/nio/file/Path;)L"+PREFIX+"api/ConversionResult;";
    static final String BASE_SHA="a21281ef417669490546e4176e38f8670bdc3dd3e28c973ae828b90ce59c4913";
    static int readers=0,modified=0,publication=0;
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("base-rev311.jar output-class-dir");
        Path input=Path.of(args[0]),out=Path.of(args[1]);
        if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input))).equals(BASE_SHA))
            throw new IllegalStateException("Wrong rev311 base; refusing to downgrade or patch unknown bytecode");
        try(JarFile jar=new JarFile(input.toFile())) {
            for(JarEntry entry:Collections.list(jar.entries())) {
                String name=entry.getName();
                if(!name.startsWith(PREFIX)||!name.endsWith(".class")||name.startsWith(SHARED))continue;
                byte[] bytes=jar.getInputStream(entry).readAllBytes();
                ClassReader reader=new ClassReader(bytes);ClassNode node=new ClassNode(ASM9);reader.accept(node,0);
                int count=patchStreams(node);boolean changed=count>0;
                if(node.name.equals(REG)){registry(node);changed=true;}
                if(node.name.equals(ENGINE)){engine(node);changed=true;}
                if(!changed)continue;
                ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);node.accept(writer);
                Path p=out.resolve(name);Files.createDirectories(p.getParent());Files.write(p,writer.toByteArray());
                System.out.println(name+"\tsourceStreamSites="+count); modified++;
            }
        }
        if(readers<50||publication!=1)throw new AssertionError("Incomplete transformation streams="+readers+" publication="+publication);
        System.out.println("TOTAL modifiedClasses="+modified+" sourceStreamSites="+readers+" publicationGuards="+publication);
    }
    static int patchStreams(ClassNode n) {
        int count=0;
        for(MethodNode m:n.methods)for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext()) {
            if(i instanceof MethodInsnNode call&&call.getOpcode()==INVOKEVIRTUAL&&call.owner.equals("java/util/jar/JarFile")
                    &&call.name.equals("getInputStream")&&call.desc.equals("(Ljava/util/zip/ZipEntry;)Ljava/io/InputStream;")) {
                call.setOpcode(INVOKESTATIC);call.owner=SHARED+"SharedSourceSession";call.name="openEntry";
                call.desc="(Ljava/util/jar/JarFile;Ljava/util/zip/ZipEntry;)Ljava/io/InputStream;";call.itf=false;count++;
            }
        }
        readers+=count;return count;
    }
    static MethodNode method(ClassNode n,String name,String desc) {
        List<MethodNode> found=n.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).toList();
        if(found.size()!=1)throw new AssertionError("Missing/duplicate "+n.name+"#"+name+desc);return found.getFirst();
    }
    static MethodNode add(ClassNode n,String name,String desc) {
        if(n.methods.stream().anyMatch(m->m.name.equals(name)&&m.desc.equals(desc)))throw new AssertionError("Already patched "+name);
        MethodNode m=new MethodNode(ACC_PUBLIC|(name.startsWith("lfb$")?ACC_SYNTHETIC:0),name,desc,null,null);n.methods.add(m);return m;
    }
    static void load(MethodNode m,int local) {m.instructions.add(new VarInsnNode(ALOAD,local));}
    static void call(MethodNode m,int op,String owner,String name,String desc,boolean itf){m.instructions.add(new MethodInsnNode(op,owner,name,desc,itf));}
    static void registry(ClassNode n) {
        n.interfaces.add(SHARED+"SharedRegistryAnalysis$Access");
        n.fields.add(new FieldNode(ACC_PRIVATE|ACC_VOLATILE|ACC_SYNTHETIC,"lfb$rev312Snapshot",SNAP,null,null));
        MethodNode rawAnalyze=method(n,"analyze","(Ljava/nio/file/Path;)"+ANALYSIS); rawAnalyze.name="lfb$rev312AnalyzeUncached";
        MethodNode rawClassify=method(n,"classifyItem","(Ljava/lang/String;)Ljava/lang/String;");rawClassify.name="lfb$rev312ClassifyUncached";
        MethodNode rawHidden=method(n,"hiddenRegistrationKeys","()Ljava/util/Set;");rawHidden.name="lfb$rev312HiddenUncached";
        MethodNode m=add(n,"analyze","(Ljava/nio/file/Path;)"+ANALYSIS);m.exceptions.add("java/io/IOException");m.signature=rawAnalyze.signature;
        load(m,0);load(m,1);call(m,INVOKESTATIC,SHARED+"SharedRegistryAnalysis","analyze","(L"+REG+";Ljava/nio/file/Path;)"+ANALYSIS,false);m.instructions.add(new InsnNode(ARETURN));
        m=add(n,"classifyItem","(Ljava/lang/String;)Ljava/lang/String;");m.signature=rawClassify.signature;load(m,0);load(m,1);
        call(m,INVOKESTATIC,SHARED+"SharedRegistryAnalysis","classify","(L"+REG+";Ljava/lang/String;)Ljava/lang/String;",false);m.instructions.add(new InsnNode(ARETURN));
        m=add(n,"hiddenRegistrationKeys","()Ljava/util/Set;");m.signature=rawHidden.signature;load(m,0);
        call(m,INVOKESTATIC,SHARED+"SharedRegistryAnalysis","hidden","(L"+REG+";)Ljava/util/Set;",false);m.instructions.add(new InsnNode(ARETURN));
        m=add(n,"lfb$rev312ClassNames","()Ljava/util/Set;");load(m,0);
        m.instructions.add(new FieldInsnNode(GETFIELD,n.name,"classes","Ljava/util/Map;"));
        call(m,INVOKEINTERFACE,"java/util/Map","keySet","()Ljava/util/Set;",true);
        call(m,INVOKESTATIC,"java/util/Set","copyOf","(Ljava/util/Collection;)Ljava/util/Set;",true);m.instructions.add(new InsnNode(ARETURN));
        m=add(n,"lfb$rev312Snapshot","()"+SNAP);load(m,0);
        m.instructions.add(new FieldInsnNode(GETFIELD,n.name,"lfb$rev312Snapshot",SNAP));m.instructions.add(new InsnNode(ARETURN));
        m=add(n,"lfb$rev312Snapshot","("+SNAP+")V");load(m,0);load(m,1);
        m.instructions.add(new FieldInsnNode(PUTFIELD,n.name,"lfb$rev312Snapshot",SNAP));m.instructions.add(new InsnNode(RETURN));
        m=add(n,"lfb$rev312ClearWorkingState","()V");
        for(String f:List.of("classes","methods","templates","creativeEvidence")){
            if(n.fields.stream().noneMatch(field->field.name.equals(f)&&field.desc.equals("Ljava/util/Map;")))throw new AssertionError("Unexpected field layout "+f);
            load(m,0);m.instructions.add(new FieldInsnNode(GETFIELD,n.name,f,"Ljava/util/Map;"));
            call(m,INVOKEINTERFACE,"java/util/Map","clear","()V",true);
        }
        load(m,0);m.instructions.add(new FieldInsnNode(GETFIELD,n.name,"diagnostics","Ljava/util/List;"));
        call(m,INVOKEINTERFACE,"java/util/List","clear","()V",true);m.instructions.add(new InsnNode(RETURN));
    }
    static void engine(ClassNode n) {
        n.interfaces.add(SHARED+"ScopedConversionRunner$Access");
        MethodNode original=method(n,"convertAnalyzed",CONVERT_DESC);original.name="lfb$rev312ConvertUncached";
        for(AbstractInsnNode i=original.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode c
                &&c.owner.equals(PREFIX+"DesktopPackHook")&&c.name.equals("pack")&&c.desc.equals("(Ljava/nio/file/Path;Ljava/nio/file/Path;)V")){
            original.instructions.insertBefore(i,new MethodInsnNode(INVOKESTATIC,SHARED+"SharedSourceSession","beforePublication","()V",false));publication++;
        }
        MethodNode m=add(n,"convertAnalyzed",CONVERT_DESC);m.exceptions.add("java/io/IOException");
        for(int i=0;i<=5;i++)load(m,i);
        call(m,INVOKESTATIC,SHARED+"ScopedConversionRunner","convert","(L"+ENGINE+";"+CONVERT_DESC.substring(1),false);
        m.instructions.add(new InsnNode(ARETURN));
    }
}
