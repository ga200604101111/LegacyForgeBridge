import dev.yinghuang.legacyforgebridge.config.legacy.LegacyForgeCfgFile;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class LegacyForgeCfgFileSelfTest {
    private static int passed = 0;
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); passed++; }
    private static void reject(Runnable runnable) {
        try { runnable.run(); throw new AssertionError("Expected rejection"); }
        catch (RuntimeException expected) { passed++; }
    }
    public static void main(String[] ignored) throws Exception {
        Path root = Files.createTempDirectory("lfb-cfg-test-");
        var a = new LegacyForgeCfgFile.Property("BambooConfig.cfg", "bamboosettings", "WindPushPlayer",'B',"true","wind comment");
        var b = new LegacyForgeCfgFile.Property("BambooConfig.cfg", "bamboosettings", "MaxExplosionLv",'I',"3","explosion comment");
        var c = new LegacyForgeCfgFile.Property("BambooConfig.cfg", "other options", "Text",'S',"你好","text");
        Path p = root.resolve("BambooConfig.cfg");
        var empty = new LegacyForgeCfgFile(p);
        check(empty.value(a).equals("true"), "default on missing file");
        empty.save(List.of(a,b,c), Map.of(a,"false",b,"2",c,"中文"));
        String created=Files.readString(p);
        check(created.contains("B:\"WindPushPlayer\"=false"),"write bool");
        check(created.contains("I:\"MaxExplosionLv\"=2"),"write int");
        check(created.contains("S:\"Text\"=中文"),"write unicode");
        check(new LegacyForgeCfgFile(p).value(c).equals("中文"),"parse generated unicode");
        check(Files.notExists(root.resolve("BambooConfig.cfg.lfb-backup")),"new file has no backup");
        String original="# comment\r\n\"bamboosettings\" {\r\n  B:\"WindPushPlayer\"=true\r\n  I:unknown=100\r\n  S:other <\r\n    value\r\n  >\r\n}\r\n";
        Files.writeString(p,original,StandardCharsets.UTF_8);
        var existing=new LegacyForgeCfgFile(p);
        check(existing.value(a).equals("true"),"existing read");
        check(existing.existingProperties().size()==2,"arrays ignored");
        existing.save(List.of(a,b,c),Map.of(a,"false"));
        String result=Files.readString(p);
        check(result.contains("I:unknown=100"),"unknown property preserved");
        check(result.contains("S:other <\r\n    value\r\n  >"),"list preserved");
        check(result.contains("B:\"WindPushPlayer\"=false"),"updated existing");
        check(result.contains("I:\"MaxExplosionLv\"=3"),"missing inside existing cat");
        check(result.contains("\"other options\" {"),"missing category appended");
        check(result.contains("\r\n") && !result.replace("\r\n","").contains("\n"),"CRLF preserved");
        check(Files.readString(root.resolve("BambooConfig.cfg.lfb-backup")).equals(original),"backup identical");
        try {
            new LegacyForgeCfgFile(p).save(List.of(a),Map.of(a,"not a boolean"));
            throw new AssertionError("Invalid bool allowed");
        } catch (java.io.IOException expected) { passed++; }
        Path q=root.resolve("Change.cfg");
        Files.writeString(q,"cat {\nB:x=true\n}\n");
        var stale=new LegacyForgeCfgFile(q);
        Files.writeString(q,"cat {\nB:x=false\n}\n");
        try {
            stale.save(List.of(new LegacyForgeCfgFile.Property("Change.cfg","cat","x",'B',"true","")),Map.of());
            throw new AssertionError("Concurrent update overwritten");
        } catch (java.io.IOException expected) { passed++; }
        Path dup=root.resolve("dup.cfg");
        Files.writeString(dup,"cat {\nB:x=true\nB:x=false\n}\n");
        try { new LegacyForgeCfgFile(dup); throw new AssertionError("Duplicate accepted"); }
        catch (java.io.IOException expected) { passed++; }
        reject(() -> new LegacyForgeCfgFile.Property("../x.cfg","cat","x",'B',"true",""));
        System.out.println("LegacyForgeCfgFileSelfTest PASS " + passed + " checks");
    }
}
