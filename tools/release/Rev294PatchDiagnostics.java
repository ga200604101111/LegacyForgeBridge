import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Bounded binary delta over the user's complete rev293, never full source rebuild. */
public final class Rev294PatchDiagnostics {
    private static final String ROOT="dev/yinghuang/legacyforgebridge/";
    private static final String DESKTOP=ROOT+"desktop/";
    private static final String REPORT=DESKTOP+"LegacyConversionStatusDetails";
    private static final String SESSION=DESKTOP+"DesktopConversionSession";
    private static final String SUMMARY=DESKTOP+"ConversionOutcomeReport$Summary";
    private static final String BUILD=ROOT+"BuildInfo";
    private static final String UI=DESKTOP+"Rev247SupportWindow";

    private static ClassNode read(byte[] bytes){ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(bytes).accept(node,0);return node;}
    private static byte[] write(ClassNode node){ClassWriter out=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(out);return out.toByteArray();}
    private static MethodNode method(ClassNode n,String name,String desc){return n.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow();}
    private static InsnList propertySet(InsnList values){return values;}
    static byte[] patchSession(byte[] old){
        ClassNode node=read(old);
        MethodNode publish=method(node,"publish","()V");
        InsnList version=new InsnList();
        version.add(new VarInsnNode(Opcodes.ALOAD,0));
        version.add(new FieldInsnNode(Opcodes.GETFIELD,SESSION,"status","Ljava/util/Properties;"));
        version.add(new LdcInsnNode("converterVersion"));
        version.add(new FieldInsnNode(Opcodes.GETSTATIC,BUILD,"VERSION","Ljava/lang/String;"));
        version.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/util/Properties","setProperty","(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",false));
        version.add(new InsnNode(Opcodes.POP));
        publish.instructions.insert(version);
        MethodNode save=method(node,"saveReport","(Lcom/google/gson/JsonObject;)L"+SUMMARY+";");
        AbstractInsnNode returnLoad=null;int exits=0;
        for(AbstractInsnNode n:save.instructions){
            if(n.getOpcode()==Opcodes.ARETURN){exits++;AbstractInsnNode prior=n.getPrevious();while(prior!=null&&prior.getOpcode()<0)prior=prior.getPrevious();
                if(prior instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ALOAD&&v.var==2)returnLoad=prior;}
        }
        if(exits!=1||returnLoad==null)throw new IllegalStateException("Unexpected old summary return shape");
        InsnList statements=new InsnList();
        // status.message = read-only module details computed from already-verified outcome report
        statements.add(new VarInsnNode(Opcodes.ALOAD,0));
        statements.add(new FieldInsnNode(Opcodes.GETFIELD,SESSION,"status","Ljava/util/Properties;"));
        statements.add(new LdcInsnNode("message"));
        statements.add(new VarInsnNode(Opcodes.ALOAD,2));
        statements.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,SUMMARY,"document","()Lcom/google/gson/JsonObject;",false));
        statements.add(new MethodInsnNode(Opcodes.INVOKESTATIC,REPORT,"safeExplain","(Lcom/google/gson/JsonObject;)Ljava/lang/String;",false));
        statements.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/util/Properties","setProperty","(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",false));
        statements.add(new InsnNode(Opcodes.POP));
        // Old UI copies errorCount already but the parent never sets it; expose non-loadable count.
        statements.add(new VarInsnNode(Opcodes.ALOAD,0));
        statements.add(new FieldInsnNode(Opcodes.GETFIELD,SESSION,"status","Ljava/util/Properties;"));
        statements.add(new LdcInsnNode("errorCount"));
        statements.add(new VarInsnNode(Opcodes.ALOAD,2));
        statements.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,SUMMARY,"unusable","()I",false));
        statements.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/Integer","toString","(I)Ljava/lang/String;",false));
        statements.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/util/Properties","setProperty","(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",false));
        statements.add(new InsnNode(Opcodes.POP));
        save.instructions.insertBefore(returnLoad,statements);
        return write(node);
    }
    static byte[] patchUi(byte[] old){
        ClassNode node=read(old);
        MethodNode observe=method(node,"observe","(Ljavax/swing/JFrame;Ljava/util/Properties;Ljava/lang/String;)V");
        LdcInsnNode target=null;int matches=0;
        for(AbstractInsnNode i:observe.instructions)if(i instanceof LdcInsnNode ldc&&ldc.cst instanceof String s&&s.equals("0.2.0-alpha.27-corpus4-local.34-rev251-via-lifecycle.1")){target=ldc;matches++;}
        if(matches!=1)throw new IllegalStateException("Expected one stale UI version literal: "+matches);
        InsnList dynamic=new InsnList();
        dynamic.add(new VarInsnNode(Opcodes.ALOAD,1));
        dynamic.add(new LdcInsnNode("converterVersion"));
        dynamic.add(new LdcInsnNode("unreported-main-version"));
        dynamic.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/util/Properties","getProperty","(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",false));
        observe.instructions.insertBefore(target,dynamic);
        observe.instructions.remove(target);
        return write(node);
    }
    static byte[] patchBuild(byte[] old){
        ClassNode node=read(old);MethodNode init=method(node,"<clinit>","()V");int swapped=0;
        for(AbstractInsnNode i:init.instructions)if(i instanceof LdcInsnNode ldc&&ldc.cst instanceof String oldVersion&&oldVersion.equals("0.2.0-alpha.27-corpus4-local.46-rev293-item-localization-equipment-preview.1")){
            ldc.cst="0.2.0-alpha.27-corpus4-local.47-rev294-load-diagnostics-preview.1";swapped++;
        }
        if(swapped!=1)throw new IllegalStateException("BuildInfo version value changed unexpectedly");
        // Intentionally do NOT modify CONVERTER_REVISION: no conversion semantics changed, and
        // forcibly regenerating four expensive original-mod candidates would obscure diagnosis.
        return write(node);
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("Usage: original-complete-main.jar output-patches-dir");
        Path main=Path.of(args[0]),out=Path.of(args[1]);
        Map<String,byte[]> entries=new LinkedHashMap<>();
        try(JarFile jar=new JarFile(main.toFile())){
            for(String name:List.of(SESSION+".class", UI+".class",BUILD+".class")){
                JarEntry entry=jar.getJarEntry(name);if(entry==null)throw new IllegalStateException("Base class missing: "+name);
                byte[] original=jar.getInputStream(entry).readAllBytes();
                byte[] patched=name.equals(SESSION+".class")?patchSession(original):name.equals(UI+".class")?patchUi(original):patchBuild(original);
                entries.put(name,patched);
            }
            byte[] nested=jar.getInputStream(jar.getJarEntry("META-INF/lfb/desktop-helper.jar")).readAllBytes();
            Path nestedFile=out.resolve("desktop-helper-patched.jar");
            Files.createDirectories(out);
            try(java.util.zip.ZipInputStream in=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(nested));
                java.util.zip.ZipOutputStream dest=new java.util.zip.ZipOutputStream(Files.newOutputStream(nestedFile))){
                java.util.zip.ZipEntry entry;int count=0;
                while((entry=in.getNextEntry())!=null){
                    byte[] oldEntry=in.readAllBytes();
                    String name=entry.getName();
                    byte[] bytes=name.equals(UI+".class")?patchUi(oldEntry):oldEntry;
                    java.util.zip.ZipEntry replacement=new java.util.zip.ZipEntry(name);
                    if(entry.getTime()>=0)replacement.setTime(entry.getTime());
                    dest.putNextEntry(replacement);dest.write(bytes);dest.closeEntry();count++;
                }
                System.out.println("Desktop helper repacked: "+count+" entries");
            }
        }
        for(var item:entries.entrySet()){
            Path path=out.resolve(item.getKey());Files.createDirectories(path.getParent());Files.write(path,item.getValue());
            System.out.println("PATCH "+item.getKey()+" bytes="+item.getValue().length);
        }
    }
}
