import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.security.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import com.google.gson.*;

/** Exact rev216 complete-main overlay for rev217 restart-guard expiry. */
public final class Integrate217 implements Opcodes {
    static final String ROOT="dev/yinghuang/legacyforgebridge/", D=ROOT+"desktop/";
    static final String PIN="74090805b9122516165245620fc947fabf02c2b1211f45aba5a5e11cd40e70b2";
    static final String OLD="0.2.0-alpha.27-rev216-local-test.1", VERSION="0.2.0-alpha.27-rev217-local-test.1";
    static final String OLD_GUARD_MSG="已阻止重複自動重啟；請檢查更新是否成功載入";
    static final String NEW_GUARD_MSG="15 分鐘內已執行過自動重啟；為避免循環暫停。若仍待重啟，稍後重新啟動會再嘗試";
    static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    static ClassNode read(byte[] b){ClassNode c=new ClassNode(ASM9);new ClassReader(b).accept(c,0);return c;}
    static byte[] write(ClassNode c){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);c.accept(w);return w.toByteArray();}
    static AbstractInsnNode nextCode(AbstractInsnNode i){for(AbstractInsnNode n=i.getNext();n!=null;n=n.getNext())if(!(n instanceof LabelNode||n instanceof LineNumberNode||n instanceof FrameNode))return n;return null;}
    static byte[] patchRestartPlan(byte[] bytes)throws Exception{
        ClassNode c=read(bytes);int methods=0,branches=0;
        for(MethodNode m:c.methods)if(m.name.equals("guardAllows")&&m.desc.equals("(Ljava/nio/file/Path;Ljava/lang/String;J)Z")){
            methods++;
            for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext()){
                if(i instanceof MethodInsnNode call&&call.getOpcode()==INVOKEVIRTUAL&&call.owner.equals("java/lang/String")&&call.name.equals("equals")&&call.desc.equals("(Ljava/lang/Object;)Z")){
                    AbstractInsnNode n=nextCode(i);
                    if(n instanceof JumpInsnNode j&&j.getOpcode()==IFNE){m.instructions.set(j,new InsnNode(POP));branches++;break;}
                }
            }
        }
        if(methods!=1||branches!=1)throw new IllegalStateException("RestartPlan guard anchor mismatch methods="+methods+" branches="+branches);
        return write(c);
    }
    static byte[] patchGuardMessage(byte[] bytes)throws Exception{
        ClassNode c=read(bytes);int count=0;
        for(MethodNode m:c.methods)for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof LdcInsnNode l&&OLD_GUARD_MSG.equals(l.cst)){l.cst=NEW_GUARD_MSG;count++;}
        if(count!=1)throw new IllegalStateException("Desktop guard message anchor mismatch "+count);
        return write(c);
    }
    static byte[] helperWithGuard(byte[] helperBytes,byte[] patchedGuard)throws Exception{
        TreeMap<String,byte[]> entries=new TreeMap<>();
        try(var z=new ZipInputStream(new ByteArrayInputStream(helperBytes))){ZipEntry e;while((e=z.getNextEntry())!=null)if(!e.isDirectory()){
            if(entries.putIfAbsent(e.getName(),z.readAllBytes())!=null)throw new IllegalStateException("Duplicate helper entry");
        }}
        String path=D+"RestartPlan.class";if(!entries.containsKey(path))throw new IllegalStateException("Helper RestartPlan missing");entries.put(path,patchedGuard);
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(var z=new ZipOutputStream(out)){z.setLevel(9);for(var e:entries.entrySet()){ZipEntry ze=new ZipEntry(e.getKey());ze.setTime(0);z.putNextEntry(ze);z.write(e.getValue());z.closeEntry();}}
        return out.toByteArray();
    }
    static int identity(TreeMap<String,byte[]> before,TreeMap<String,byte[]> after,String top)throws Exception{
        String path=ROOT+top+".class";ClassNode c=read(before.get(path));int count=0;
        for(FieldNode f:c.fields)if(f.value instanceof String s&&s.contains(OLD)){f.value=s.replace(OLD,VERSION);count++;}
        for(MethodNode m:c.methods)for(AbstractInsnNode i: m.instructions){
            if(i instanceof LdcInsnNode l&&l.cst instanceof String s&&s.contains(OLD)){l.cst=s.replace(OLD,VERSION);count++;}
            if(i instanceof InvokeDynamicInsnNode d)for(int j=0;j<d.bsmArgs.length;j++)if(d.bsmArgs[j] instanceof String s&&s.contains(OLD)){d.bsmArgs[j]=s.replace(OLD,VERSION);count++;}
        }
        if(count<1)throw new IllegalStateException("Missing identity anchor "+top);after.put(path,write(c));return count;
    }
    public static void main(String[] a)throws Exception{
        if(a.length!=3)throw new IllegalArgumentException("exactRev216.jar newMain.jar report.json");
        Path base=Path.of(a[0]),target=Path.of(a[1]),report=Path.of(a[2]);
        if(!sha(Files.readAllBytes(base)).equals(PIN)||Files.exists(target)||Files.exists(report))throw new IllegalArgumentException("Wrong base or existing output");
        TreeMap<String,byte[]> before=new TreeMap<>();try(var z=new ZipFile(base.toFile())){for(var e:Collections.list(z.entries()))if(!e.isDirectory())if(before.putIfAbsent(e.getName(),z.getInputStream(e).readAllBytes())!=null)throw new IllegalArgumentException("Duplicate base entry");}
        TreeMap<String,byte[]> after=new TreeMap<>(before);Map<String,Integer> anchors=new TreeMap<>();
        String guardPath=D+"RestartPlan.class";byte[] patchedGuard=patchRestartPlan(before.get(guardPath));after.put(guardPath,patchedGuard);anchors.put("restartGuardExpiryPatch",1);
        String sessionPath=D+"DesktopConversionSession.class";after.put(sessionPath,patchGuardMessage(before.get(sessionPath)));anchors.put("guardStatusMessage",1);
        after.put("META-INF/lfb/desktop-helper.jar",helperWithGuard(before.get("META-INF/lfb/desktop-helper.jar"),patchedGuard));anchors.put("helperRestartGuardPatched",1);
        for(String top:List.of("BuildInfo","LegacyFileLogger","network/FmlConnectionTrace"))anchors.put(top,identity(before,after,top));
        Gson gson=new GsonBuilder().setPrettyPrinting().create();JsonObject fabric=JsonParser.parseString(new String(before.get("fabric.mod.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        if(!fabric.get("version").getAsString().equals(OLD))throw new IllegalStateException("Metadata identity");fabric.addProperty("version",VERSION);after.put("fabric.mod.json",(gson.toJson(fabric)+"\n").getBytes(StandardCharsets.UTF_8));
        List<String> changed=new ArrayList<>();int same=0;for(var e:before.entrySet()){if(!after.containsKey(e.getKey()))throw new IllegalStateException("Base lost");if(Arrays.equals(e.getValue(),after.get(e.getKey())))same++;else changed.add(e.getKey());}
        var info=new LinkedHashMap<String,Object>();info.put("version",VERSION);info.put("base_sha256",PIN);info.put("guard_window_millis",900000);info.put("same_plan_guard_expires",true);info.put("different_plan_guard_expires",true);info.put("anchors",anchors);info.put("base_entries",before.size());info.put("unchanged_entries",same);info.put("changed_entries",changed);info.put("removed_entries",0);info.put("gameplay_semantics_changed",false);info.put("prism_launch_command_changed",false);info.put("force_exit_logic_changed",false);info.put("method","Exact rev216 ASM patch; JDK21 + ASM, not Gradle/Loom clean build");
        after.put("legacyforgebridge/rev217-build.json",(gson.toJson(info)+"\n").getBytes(StandardCharsets.UTF_8));
        try(var z=new ZipOutputStream(Files.newOutputStream(target,StandardOpenOption.CREATE_NEW))){z.setLevel(9);for(var e:after.entrySet()){ZipEntry ze=new ZipEntry(e.getKey());ze.setTime(0);z.putNextEntry(ze);z.write(e.getValue());z.closeEntry();}}
        info.put("main_sha256",sha(Files.readAllBytes(target)));info.put("main_bytes",Files.size(target));Files.writeString(report,gson.toJson(info)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);System.out.println(gson.toJson(info));
    }
}
