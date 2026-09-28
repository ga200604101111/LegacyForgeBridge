import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.security.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import com.google.gson.*;

/** Exact rev213 complete-main overlay; no root-source recompilation or gameplay force-admission. */
public final class Integrate214 implements Opcodes {
    static final String ROOT="dev/yinghuang/legacyforgebridge/", D=ROOT+"desktop/";
    static final String PIN="9ecf5ed9c89ac9d6f2ea6105efb4a60b5e9c830a1b07de0b6ea341c2f6bd4dcd";
    static final String OLD="0.2.0-alpha.27-rev213-local-test.1", VERSION="0.2.0-alpha.27-rev214-local-test.1";
    static final Set<String> OVERLAY=Set.of("DesktopHelper", "DesktopConversionSession", "ConversionOutcomeReport", "DesktopFiles", "NativeDesktopTick", "DesktopRestartPump");
    static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    static ClassNode read(byte[] b){ClassNode c=new ClassNode(ASM9);new ClassReader(b).accept(c,0);return c;}
    static byte[] write(ClassNode c){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);return w.toByteArray();}
    static int identity(ClassNode c) {
        int count=0;
        for(FieldNode f:c.fields)if(f.value instanceof String s&&s.contains(OLD)){f.value=s.replace(OLD,VERSION);count++;}
        for(MethodNode m:c.methods)for(AbstractInsnNode i:m.instructions){
            if(i instanceof LdcInsnNode l&&l.cst instanceof String s&&s.contains(OLD)){l.cst=s.replace(OLD,VERSION);count++;}
            if(i instanceof InvokeDynamicInsnNode d)for(int j=0;j<d.bsmArgs.length;j++)if(d.bsmArgs[j] instanceof String s&&s.contains(OLD)){d.bsmArgs[j]=s.replace(OLD,VERSION);count++;}
        } return count;
    }
    public static void main(String[] a)throws Exception {
        if(a.length!=4)throw new IllegalArgumentException("exactRev213.jar productDir newMain.jar report.json");
        Path base=Path.of(a[0]),product=Path.of(a[1]),target=Path.of(a[2]),report=Path.of(a[3]);
        if(!sha(Files.readAllBytes(base)).equals(PIN)||Files.exists(target)||Files.exists(report))throw new IllegalArgumentException("Wrong base or existing output");
        TreeMap<String,byte[]> before=new TreeMap<>();
        try(var z=new ZipFile(base.toFile())){for(var e:Collections.list(z.entries()))if(!e.isDirectory()){
            if(before.putIfAbsent(e.getName(),z.getInputStream(e).readAllBytes())!=null)throw new IllegalArgumentException("Duplicate base entry");}}
        TreeMap<String,byte[]> after=new TreeMap<>(before); Set<String> seen=new HashSet<>();
        try(var walk=Files.walk(product)){for(Path p:walk.filter(Files::isRegularFile).sorted().toList()){
            String name=product.relativize(p).toString().replace('\\','/');
            if(!name.startsWith(D)||!name.endsWith(".class"))throw new IllegalArgumentException("Nonproduct overlay");
            String top=name.substring(D.length(),name.length()-6).split("\\$",2)[0];
            if(!OVERLAY.contains(top))throw new IllegalArgumentException("Unexpected class "+name);
            byte[] bytes=Files.readAllBytes(p);if(!new ClassReader(bytes).getClassName().concat(".class").equals(name))throw new IllegalArgumentException("Class mismatch");
            ClassNode node=read(bytes);identity(node);after.put(name,write(node));seen.add(top);
        }}
        if(!seen.equals(OVERLAY))throw new IllegalArgumentException("Incomplete overlay");
        Map<String,Integer> anchors=new TreeMap<>();
        for(String top:List.of("BuildInfo","LegacyFileLogger","network/FmlConnectionTrace")){
            String path=ROOT+top+".class";ClassNode c=read(before.get(path));int count=0;
            for(FieldNode f:c.fields)if(f.value instanceof String s&&s.contains(OLD)){f.value=s.replace(OLD,VERSION);count++;}
            for(MethodNode m:c.methods)for(AbstractInsnNode i:m.instructions){
                if(i instanceof LdcInsnNode l&&l.cst instanceof String s&&s.contains(OLD)){l.cst=s.replace(OLD,VERSION);count++;}
                if(i instanceof InvokeDynamicInsnNode d)for(int j=0;j<d.bsmArgs.length;j++)if(d.bsmArgs[j] instanceof String s&&s.contains(OLD)){d.bsmArgs[j]=s.replace(OLD,VERSION);count++;}
            }
            if(count<1)throw new IllegalStateException("Missing identity anchor "+top);
            anchors.put(top,count);after.put(path,write(c));
        }
        String path=ROOT+"LegacyForgeBridgeClient.class";ClassNode c=read(before.get(path));int count=0,ticks=0;
        for(MethodNode m:c.methods){
            if(m.name.equals("onInitializeClient")&&m.desc.equals("()V"))for(AbstractInsnNode i:m.instructions.toArray())if(i.getOpcode()==RETURN){
                m.instructions.insertBefore(i,new MethodInsnNode(INVOKESTATIC,D+"NativeDesktopTick","initialize","()V",false));count++;
            }
            for(AbstractInsnNode i:m.instructions)if(i instanceof MethodInsnNode x&&x.owner.equals(D+"NativeDesktopTick")&&x.name.equals("onTick"))ticks++;
        }
        if(count!=1||ticks!=1)throw new IllegalStateException("Client bootstrap/tick anchors: "+count+"/"+ticks);
        anchors.put("clientBootstrap",count);anchors.put("preservedEndTickHook",ticks);after.put(path,write(c));
        // Reuse the JDK-only helper dependencies byte-for-byte; never package game or Gson classes there.
        TreeMap<String,byte[]> helper=new TreeMap<>();
        try(var z=new ZipInputStream(new ByteArrayInputStream(before.get("META-INF/lfb/desktop-helper.jar")))){
            ZipEntry e;while((e=z.getNextEntry())!=null)if(!e.isDirectory())helper.put(e.getName(),z.readAllBytes());
        }
        for(var e:after.entrySet())if((e.getKey().startsWith(D+"DesktopHelper")||e.getKey().equals(D+"DesktopFiles.class"))&&e.getKey().endsWith(".class"))helper.put(e.getKey(),e.getValue());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(var z=new ZipOutputStream(bytes)){z.setLevel(9);for(var e:helper.entrySet()){var ze=new ZipEntry(e.getKey());ze.setTime(0);z.putNextEntry(ze);z.write(e.getValue());z.closeEntry();}}
        after.put("META-INF/lfb/desktop-helper.jar",bytes.toByteArray());
        Gson gson=new GsonBuilder().setPrettyPrinting().create();JsonObject fabric=JsonParser.parseString(new String(before.get("fabric.mod.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        if(!fabric.get("version").getAsString().equals(OLD))throw new IllegalStateException("Metadata identity");
        fabric.addProperty("version",VERSION);after.put("fabric.mod.json",(gson.toJson(fabric)+"\n").getBytes(StandardCharsets.UTF_8));
        List<String> changed=new ArrayList<>();int same=0;
        for(var e:before.entrySet()){if(!after.containsKey(e.getKey()))throw new IllegalStateException("Base lost");if(Arrays.equals(e.getValue(),after.get(e.getKey())))same++;else changed.add(e.getKey());}
        var info=new LinkedHashMap<String,Object>();info.put("version",VERSION);info.put("base_sha256",PIN);info.put("anchors",anchors);
        info.put("base_entries",before.size());info.put("unchanged_entries",same);info.put("changed_entries",changed);info.put("removed_entries",0);
        info.put("energy_jar_unchanged",Arrays.equals(before.get("META-INF/jars/energy-4.2.0.jar"),after.get("META-INF/jars/energy-4.2.0.jar")));
        info.put("semantic_converter_revision_changed",false);info.put("native_compile_declarations_used",true);info.put("compile_declarations_packaged",false);info.put("force_admission",false);info.put("game_windows_prism_validated",false);
        info.put("method","JDK21 javac + exact-base ASM full-main assembly, not Gradle/Loom clean build");
        after.put("legacyforgebridge/rev214-build.json",(gson.toJson(info)+"\n").getBytes(StandardCharsets.UTF_8));
        try(var z=new ZipOutputStream(Files.newOutputStream(target,StandardOpenOption.CREATE_NEW))){z.setLevel(9);for(var e:after.entrySet()){var ze=new ZipEntry(e.getKey());ze.setTime(0);z.putNextEntry(ze);z.write(e.getValue());z.closeEntry();}}
        info.put("main_sha256",sha(Files.readAllBytes(target)));info.put("main_bytes",Files.size(target));
        Files.writeString(report,gson.toJson(info)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);System.out.println(gson.toJson(info));
    }
}
