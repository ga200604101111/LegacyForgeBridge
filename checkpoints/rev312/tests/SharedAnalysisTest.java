package dev.yinghuang.legacyforgebridge.convert.shared;

import dev.yinghuang.legacyforgebridge.convert.LegacyRegistryAnalyzer;
import java.io.*;import java.nio.file.*;import java.nio.file.attribute.FileTime;
import java.util.*;import java.util.jar.*;import java.util.concurrent.*;
import java.util.zip.*;

/** Runs actual original-JAR analysis; no Minecraft or Forge class is loaded. */
public final class SharedAnalysisTest {
    static int checks;
    static void check(boolean v,String m){if(!v)throw new AssertionError(m);checks++;}
    static void failure(Throwing r,Class<?> expected)throws Exception {
        try {r.run();throw new AssertionError("Expected "+expected.getName());}
        catch(Throwable e){if(!expected.isInstance(e))throw e;checks++;}
    }
    interface Throwing {void run()throws Exception;}
    static List<String> sourceNames(Path p)throws Exception{
        try(JarFile j=new JarFile(p.toFile())){
            List<String> names=new ArrayList<>(j.stream().map(ZipEntry::getName).filter(n->n.endsWith(".class"))
                    .map(n->n.substring(0,n.length()-6)).toList());
            names.addAll(List.of("net/minecraft/item/ItemSword","net/minecraft/item/ItemArmor","net/minecraft/item/ItemBow",
                    "net/minecraft/item/ItemHoe","net/minecraft/item/ItemPickaxe","net/minecraft/item/ItemAxe",
                    "net/minecraft/item/ItemSpade","net/minecraft/item/ItemTool","does/not/Exist"));names.add(null);return names;
        }
    }
    static void corpus(Path p)throws Exception {
        String hash=SharedSourceSession.digest(p);List<String> names=sourceNames(p);
        LegacyRegistryAnalyzer ref=new LegacyRegistryAnalyzer();var expected=ref.analyze(p);var hidden=ref.hiddenRegistrationKeys();
        Map<String,String> kinds=new HashMap<>();for(String n:names)kinds.put(n,ref.classifyItem(n));
        check(!SharedSourceSession.active(),"unscoped original analysis");
        try(var scope=SharedSourceSession.open(p,hash)){
            LegacyRegistryAnalyzer first=new LegacyRegistryAnalyzer();var result=first.analyze(p);
            check(result.equals(expected),"cold registry equality "+p);
            check(first.hiddenRegistrationKeys().equals(hidden),"cold creative flags "+p);
            for(String n:names)check(first.classifyItem(n).equals(kinds.get(n)),"cold classify "+n);
            for(int i=0;i<5;i++){
                LegacyRegistryAnalyzer fresh=new LegacyRegistryAnalyzer();
                check(fresh.analyze(p)==result,"same immutable cached result");
                check(fresh.hiddenRegistrationKeys().equals(hidden),"hot hidden flags");
                for(String n:names)check(fresh.classifyItem(n).equals(kinds.get(n)),"hot classify "+n);
            }
            Map<String,Long> c=scope.counters();check(c.get("registryComputations")==1,"one computation per scope");
            check(c.get("registryRequests")==6&&c.get("registryHits")==5,"cache counts");
            check(c.get("sourceEntryLoads")>0,"actual source entry cache wired");
            check(c.get("retainedSourceBytes")<=c.get("sourceCacheLimitBytes"),"byte cap");
            ((SharedRegistryAnalysis.Access)(Object)first).lfb$rev312ClearWorkingState();
            check(first.classifyItem(names.getFirst()).equals(kinds.get(names.getFirst())),"snapshot independent of ASM map");
            failure(()->result.registrations().clear(),UnsupportedOperationException.class);
            failure(()->first.hiddenRegistrationKeys().clear(),UnsupportedOperationException.class);
            scope.writeReport(Path.of(System.getProperty("lfb.testOutput", "test-results")).resolve(p.getFileName()+".cache-test.json"),true);
        }
        check(!SharedSourceSession.active(),"session cleanup");
        try(var scope=SharedSourceSession.open(p,hash)){
            check(new LegacyRegistryAnalyzer().analyze(p).equals(expected),"new session result");
            check(scope.counters().get("registryComputations")==1,"no stale process-global registry cache");
        }
        // Concurrent source readers and registry requests share ONE computation, never ASM trees.
        try(var scope=SharedSourceSession.open(p,hash);var pool=Executors.newFixedThreadPool(8)){
            CyclicBarrier start=new CyclicBarrier(8);List<Future<Object>> futures=new ArrayList<>();
            for(int i=0;i<8;i++)futures.add(pool.submit(scope.bind(()->{
                start.await();var a=new LegacyRegistryAnalyzer();var r=a.analyze(p);
                if(!r.equals(expected)||!a.hiddenRegistrationKeys().equals(hidden))throw new AssertionError("concurrent result");
                for(String n:names)if(!a.classifyItem(n).equals(kinds.get(n)))throw new AssertionError("concurrent classify");
                return r;
            })));
            Object shared=futures.getFirst().get(30,TimeUnit.SECONDS);
            for(Future<Object> f:futures)check(f.get(30,TimeUnit.SECONDS)==shared,"single-flight identity");
            check(scope.counters().get("registryComputations")==1,"single-flight compute count");
            check(scope.counters().get("registryHits")==7,"single-flight hit count");
        }
        check(!SharedSourceSession.active(),"parallel scope clean");
        // Disable switch runs the exact old analyzer, not a separate reimplementation.
        System.setProperty("legacyforgebridge.sharedAnalysis","false");
        try(var scope=SharedSourceSession.open(p,hash)){
            LegacyRegistryAnalyzer a=new LegacyRegistryAnalyzer();check(a.analyze(p).equals(expected),"disabled equality");
            check(a.hiddenRegistrationKeys().equals(hidden),"disabled hidden");
            check(a.analyze(p).equals(expected),"disabled second equality");
            check(scope.counters().get("registryComputations")==2&&scope.counters().get("registryHits")==0,"disable bypass");
        }finally{System.clearProperty("legacyforgebridge.sharedAnalysis");}
        System.out.println("CORPUS PASS "+p.getFileName()+" registrations="+expected.registrations().size()+" hidden="+hidden.size()+" classificationInputs="+names.size());
    }
    static void ioAndLifecycle(Path original)throws Exception{
        Path dir=Files.createTempDirectory("lfb312-test-");Path jar=dir.resolve("source.jar");Files.copy(original,jar);
        String sha=SharedSourceSession.digest(jar);
        failure(()->SharedSourceSession.open(jar,"0".repeat(64)),IOException.class);
        check(!SharedSourceSession.active(),"failed open leaves no scope");
        try(var scope=SharedSourceSession.open(jar,sha);JarFile j=new JarFile(jar.toFile())){
            var entry=j.stream().filter(e->e.getName().endsWith(".class")).findFirst().orElseThrow();
            byte[] reference;try(var in=j.getInputStream(entry)){reference=in.readAllBytes();}
            try(InputStream a=SharedSourceSession.openEntry(j,entry);InputStream b=SharedSourceSession.openEntry(j,entry)){
                check(a.read()==(reference[0]&255),"stream a first byte");a.close();
                check(Arrays.equals(b.readAllBytes(),reference),"stream b independent cursor/lifetime");
            }
            check(scope.counters().get("sourceEntryLoads")==1&&scope.counters().get("sourceEntryHits")==1,"byte cache counts");
            try(var nested=SharedSourceSession.open(jar,sha)){check(nested.counters().equals(scope.counters()),"nested identical scope");}
            check(SharedSourceSession.active(),"nested close retains parent");SharedSourceSession.beforePublication();
        }
        check(!SharedSourceSession.active(),"streams closed outside scope");
        System.setProperty("legacyforgebridge.sharedAnalysis.maxMiB","0");
        try(var scope=SharedSourceSession.open(jar,sha);JarFile j=new JarFile(jar.toFile())){
            var e=j.stream().filter(x->x.getName().endsWith(".class")).findFirst().orElseThrow();
            try(var in=SharedSourceSession.openEntry(j,e)){check(in.read()>=0,"budget bypass preserves bytes");}
            check(scope.counters().get("retainedSourceBytes")==0,"zero budget");
        }finally{System.clearProperty("legacyforgebridge.sharedAnalysis.maxMiB");}
        // Preserve size/mtime deliberately: final SHA guard must still reject mutation.
        FileTime before=Files.getLastModifiedTime(jar);byte[] input=Files.readAllBytes(jar);
        try(var scope=SharedSourceSession.open(jar,sha)){
            byte[] changed=input.clone();changed[changed.length-1]^=1;Files.write(jar,changed);Files.setLastModifiedTime(jar,before);
            failure(SharedSourceSession::beforePublication,IOException.class);
        }
        Files.write(jar,input);Files.setLastModifiedTime(jar,before);
        try(var scope=SharedSourceSession.open(jar,sha)){
            Files.setLastModifiedTime(jar,FileTime.fromMillis(before.toMillis()+2000));
            failure(()->new LegacyRegistryAnalyzer().analyze(jar),IOException.class);
        }
        Files.write(jar,input);Files.setLastModifiedTime(jar,before);
        try{try(var scope=SharedSourceSession.open(jar,sha)){throw new IOException("synthetic body failure");}}catch(IOException expected){checks++;}
        check(!SharedSourceSession.active(),"exception cleanup");
        try(var scope=SharedSourceSession.open(jar,sha)){
            var task=scope.bind(()->1);scope.close();failure(task::call,IOException.class);
        }
        check(!SharedSourceSession.active(),"closed scope cannot be resurrected by worker");
        Files.delete(jar);Files.delete(dir);
    }
    public static void main(String[] args)throws Exception{
        for(String arg:args)corpus(Path.of(arg));ioAndLifecycle(Path.of(args[0]));
        System.out.println("ALL PASS checks="+checks);
    }
}
