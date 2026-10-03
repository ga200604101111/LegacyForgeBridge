package dev.yinghuang.legacyforgebridge.convert;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import dev.yinghuang.legacyforgebridge.BuildInfo;

public class CacheTest {
 static int checks;static void ok(boolean value,String s){checks++;if(!value)throw new AssertionError(s);}
 static String f(String rev,String hash){return "0.2.0-alpha.27-corpus4-local.17-rev233-cache.1:schema=2:revision="+rev+":source="+hash;}
 public static void main(String[] args)throws Exception{
  String rev245="2026-10-03.245-deep-motion-correlation-diagnostics",rev246="2026-10-03.246-desktop-status-support-window",hash="a".repeat(64);
  String a=f(rev245,hash),b=f(rev246,hash);
  ok(ConversionCacheIdentity.current(hash).equals(a),"stable current fingerprint");
  ok(BuildInfo.VERSION.contains("rev247")&&BuildInfo.CONVERTER_REVISION.equals(rev245),"artifact/semantic identities separate");
  ok(Rev247CacheCompatibility.normalizeFingerprint(b).equals(a),"exact known alias");
  for(String unknown:new String[]{b.replace("schema=2","schema=3"),b+"x",b.replace(hash,"not-a-hash"),b.replace("local.17","local.18"),b.replace("2026-10-03.246","2026-10-03.999"),"",a})
   ok(Objects.equals(Rev247CacheCompatibility.normalizeFingerprint(unknown),unknown),"not broad normalization: "+unknown);
  ok(Rev247CacheCompatibility.normalizeFingerprint(null)==null,"null preserved");
  ok(!Rev247CacheCompatibility.normalizeFingerprint(f(rev246,"b".repeat(64))).equals(a),"different source never matches");
  Properties p=new Properties();p.setProperty("test.jar",b);Properties n=Rev247CacheCompatibility.normalizeCache(p);
  ok(p.getProperty("test.jar").equals(b)&&n.getProperty("test.jar").equals(a),"cache adapter no input mutation");
  Class<?> type=Class.forName("dev.yinghuang.legacyforgebridge.convert.ConversionStateStore$Snapshot");
  Constructor<?> ctor=type.getConstructors()[0];Class<?>[] types=ctor.getParameterTypes();Object[] values=new Object[types.length];
  for(int i=0;i<types.length;i++)values[i]=types[i]==String.class?"":types[i]==long.class?0L:types[i]==int.class?0:false;
  values[0]=b;Object s=ctor.newInstance(values);
  ok(type.getMethod("cacheFingerprint").invoke(s).equals(a),"actual packaged snapshot normalization");
  Path good=Files.createTempFile("lfb-cache-test-",".bin");Files.writeString(good,"synthetic-cache-artifact-for-hash-check");String digest=Hashing.sha256(good);
  ok(ConversionCacheIdentity.reusableResult("CONVERTED",good,digest),"valid converted result reusable");
  ok(ConversionCacheIdentity.reusableResult("PARTIAL",good,digest),"partial status still partial but reusable");
  for(String status:List.of("FAILED","","UNKNOWN"))ok(!ConversionCacheIdentity.reusableResult(status,good,digest),"failed/unknown never bypassed");
  ok(!ConversionCacheIdentity.reusableResult("CONVERTED",good,hash),"bad artifact hash rejected");
  ok(!ConversionCacheIdentity.reusableResult("CONVERTED",good.resolveSibling("missing.bin"),digest),"missing artifact rejected");
  ok(ConversionCacheIdentity.reusableResult("BLOCKED",null,""),"existing blocked cache semantics unchanged");
  ok(!ConversionCacheIdentity.reusableResult("BLOCKED",good,digest),"blocked candidate not admitted");
  ok(Rev247CacheCompatibility.equivalentRevision(rev245,rev246),"report retains exact alias");
  ok(!Rev247CacheCompatibility.equivalentRevision(rev245,"arbitrary")&&!Rev247CacheCompatibility.equivalentRevision(null,null),"unknown/null revisions denied");
  Files.delete(good);System.out.println("PASS cache checks="+checks);
 }
}
