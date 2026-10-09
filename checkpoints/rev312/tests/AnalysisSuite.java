import dev.yinghuang.legacyforgebridge.convert.shared.SharedSourceSession;
import java.io.*;import java.lang.reflect.*;import java.nio.file.*;import java.security.*;
import java.util.*;import java.util.function.*;

/** Direct execution of source-only analyzers from the actual packaged main, not synthetic results. */
public class AnalysisSuite {
 static final List<String> ANALYZERS=List.of(
  "LegacyRegistryAnalyzer", "LegacyRegisteredBlockRenderTypeAnalyzer", "LegacyBlockInventoryRenderModeAnalyzer",
  "LegacyCombatItemAnalyzer", "LegacyEntityDataWatcherAnalyzer", "LegacyEntityPresentationAnalyzer",
  "LegacyProjectilePresentationAnalyzer", "LegacyFoodItemAnalyzer", "LegacyHeldItemVisibilityAnalyzer",
  "LegacyItemRenderAnalyzer", "LegacyFmlModAnnotationAnalyzer", "LegacyRandomDisplayParticleAnalyzer");
 static String canonical(Object o)throws Exception{
  if(o==null)return "null";
  if(o instanceof String)return quote((String)o);
  if(o instanceof Number||o instanceof Boolean)return o.toString();
  if(o instanceof Enum<?> e)return quote(e.name());
  if(o instanceof Map<?,?> map){
   TreeMap<String,String> m=new TreeMap<>();for(var e:map.entrySet())m.put(canonical(e.getKey()),canonical(e.getValue()));
   List<String> fields=new ArrayList<>();for(var e:m.entrySet())fields.add(e.getKey()+":"+e.getValue());return "{"+String.join(",",fields)+"}";
  }
  if(o instanceof Collection<?> c){List<String> values=new ArrayList<>();for(Object v:c)values.add(canonical(v));if(o instanceof Set<?>)Collections.sort(values);return "["+String.join(",",values)+"]";}
  if(o instanceof Optional<?> p)return canonical(p.orElse(null));
  if(o.getClass().isArray()){List<String> values=new ArrayList<>();for(int i=0;i<Array.getLength(o);i++)values.add(canonical(Array.get(o,i)));return "["+String.join(",",values)+"]";}
  if(o.getClass().isRecord()){
   List<String> fields=new ArrayList<>();for(RecordComponent c:o.getClass().getRecordComponents()){Method m=c.getAccessor();m.setAccessible(true);fields.add(quote(c.getName())+":"+canonical(m.invoke(o)));}
   return "{"+String.join(",",fields)+"}";
  }
  if(o instanceof Path)return quote(o.toString());
  throw new IllegalArgumentException("Unrecognized result type "+o.getClass());
 }
 static String quote(String s){return '"'+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+'"';}
 static String digest(String s)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
 public static void main(String[] args)throws Exception{
  boolean enabled=Boolean.parseBoolean(args[0]);int rounds=Integer.parseInt(args[1]);Path dir=Path.of(args[2]);Files.createDirectories(dir);
  for(int round=0;round<rounds;round++)for(int i=3;i<args.length;i++){
   Path p=Path.of(args[i]);
   // Existing lifecycle cache is cleared equally for each run; it is NOT replaced by rev312.
   Class.forName("dev.yinghuang.legacyforgebridge.convert.LegacySourceAnalysisCache").getMethod("clear").invoke(null);
   long start=System.nanoTime();Map<String,Object> outputs=new LinkedHashMap<>();Map<String,Long> timings=new LinkedHashMap<>();Map<String,Long> counters=Map.of();
   try(var scope=enabled?SharedSourceSession.open(p,SharedSourceSession.digest(p)):null){
    for(String name:ANALYZERS){
     long t=System.nanoTime();Class<?> c=Class.forName("dev.yinghuang.legacyforgebridge.convert."+name);
     Object result;
     try{result=c.getMethod("analyze",Path.class).invoke(c.getConstructor().newInstance(),p);}
     catch(InvocationTargetException e){throw new IllegalStateException(name,e.getCause());}
     timings.put(name,System.nanoTime()-t);outputs.put(name,result);
    }
    if(scope!=null){SharedSourceSession.beforePublication();counters=scope.counters();}
   }
   long elapsed=System.nanoTime()-start;String output=canonical(outputs);
   Files.writeString(dir.resolve(p.getFileName()+".round"+round+".outputs.json"),output+"\n");
   Map<String,Object> meta=new LinkedHashMap<>();meta.put("mode",enabled?"shared":"base");meta.put("round",round);meta.put("sourceFile",p.getFileName().toString());meta.put("sourceSha256",SharedSourceSession.digest(p));
   meta.put("durationNanos",elapsed);meta.put("analyzers",timings);meta.put("counters",counters);meta.put("outputSha256",digest(output));
   Files.writeString(dir.resolve(p.getFileName()+".round"+round+".metrics.json"),canonical(meta)+"\n");
   System.out.println("SUITE "+(enabled?"shared":"base")+" round="+round+" "+p.getFileName()+" ms="+elapsed/1e6+" digest="+digest(output)+" counters="+counters);
  }
 }
}
