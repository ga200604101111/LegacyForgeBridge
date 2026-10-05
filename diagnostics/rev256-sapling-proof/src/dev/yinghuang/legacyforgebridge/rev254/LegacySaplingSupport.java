package dev.yinghuang.legacyforgebridge.rev254;
import net.minecraft.*;
import dev.yinghuang.legacyforgebridge.rev256.*;
/** ABI-compatible replacement of rev254's raw-class-at-runtime admission. */
public final class LegacySaplingSupport {
 public static void discover(){SaplingRegistry.discover();}
 public static boolean isSapling(class_2960 id){return SaplingRegistry.rule(id)!=null;}
 public static boolean emptyCollision(class_2960 id){var r=SaplingRegistry.rule(id);return r!=null&&r.proof().emptyCollision();}
 public static class_265 selectionShape(class_2960 id){var r=SaplingRegistry.rule(id);return r==null?null:r.selection();}
 public static class_4970.class_2251 properties(class_2960 id,class_4970.class_2251 p){return SaplingRegistry.properties(id,p);}
 public static void installClientPresentation(){SaplingRegistry.install();}
 public static String debugAnalyzeClassRoot(java.nio.file.Path root,String source)throws Exception{
  var c=SaplingProof.analyze(n->{var p=root.resolve(n);return java.nio.file.Files.isRegularFile(p)?java.util.Optional.of(p):java.util.Optional.empty();},source.replace('.','/'));
  return c==null?"NONE":c.toString();
 }
 private LegacySaplingSupport(){}
}
