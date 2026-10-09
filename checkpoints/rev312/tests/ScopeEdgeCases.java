package dev.yinghuang.legacyforgebridge.convert.shared;
import dev.yinghuang.legacyforgebridge.convert.LegacyRegistryAnalyzer;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.util.jar.*;import java.util.zip.*;
public final class ScopeEdgeCases {
 static int checks;static void check(boolean b,String m){if(!b)throw new AssertionError(m);checks++;}
 public static void main(String[] args)throws Exception{
  Path a=Path.of(args[0]),b=Path.of(args[1]);
  var refA=new LegacyRegistryAnalyzer();var resultA=refA.analyze(a);var hiddenA=refA.hiddenRegistrationKeys();
  var refB=new LegacyRegistryAnalyzer();var resultB=refB.analyze(b);var hiddenB=refB.hiddenRegistrationKeys();
  LegacyRegistryAnalyzer reused=new LegacyRegistryAnalyzer();
  try(var s=SharedSourceSession.open(a,null)){
   check(reused.analyze(a).equals(resultA),"source A");
   try(var t=SharedSourceSession.open(b,null)){
    check(reused.analyze(b).equals(resultB),"source B");check(reused.hiddenRegistrationKeys().equals(hiddenB),"hidden B");
   }
   check(reused.analyze(a).equals(resultA),"restored outer source A");check(reused.hiddenRegistrationKeys().equals(hiddenA),"hidden A after B");
   check(s.counters().get("registryComputations")==1,"nested scopes retain only their own snapshots");
  }
  check(reused.hiddenRegistrationKeys().equals(hiddenA),"query snapshot remains valid after scope closes");
  check(reused.analyze(b).equals(resultB),"unscoped analyzer resets prior snapshot");check(reused.hiddenRegistrationKeys().equals(hiddenB),"unscoped hidden B");
  // A pending owner result can be waited upon without an interrupted waiter cancelling the owner.
  try(var scope=SharedSourceSession.open(a,null)){
   var s=SharedSourceSession.current(a);var pending=new CompletableFuture<SharedRegistryAnalysis.Snapshot>();
   synchronized(s.registryLock){s.registry=pending;s.registryOwner=Thread.currentThread();}
   AtomicBoolean interrupted=new AtomicBoolean(),expectedIo=new AtomicBoolean();
   Thread worker=new Thread(()->{
    try{scope.bind(()->new LegacyRegistryAnalyzer().analyze(a)).call();}
    catch(IOException expected){expectedIo.set(true);interrupted.set(Thread.currentThread().isInterrupted());}
    catch(Exception wrong){throw new RuntimeException(wrong);}
   },"lfb-test-waiter");worker.start();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
   while(s.registryWaits.sum()==0&&System.nanoTime()<end)Thread.sleep(1);
   check(s.registryWaits.sum()==1,"waiter entered single-flight");worker.interrupt();worker.join(5000);
   check(!worker.isAlive()&&expectedIo.get()&&interrupted.get(),"interrupt restored and waiter terminates");check(!pending.isCancelled(),"owner future not cancelled by waiter");
   synchronized(s.registryLock){s.registry=null;s.registryOwner=null;}pending.completeExceptionally(new IOException("fixture owner released"));
   check(new LegacyRegistryAnalyzer().analyze(a).equals(resultA),"retry after interrupted wait remains usable");
  }
  // Renaming a source JAR does not require any mod-name allowlist.
  Path dir=Files.createTempDirectory("lfb-renamed-");Path renamed=dir.resolve("not-a-known-mod-name.jar");Files.copy(a,renamed);
  try(var scope=SharedSourceSession.open(renamed,null)){
   check(new LegacyRegistryAnalyzer().analyze(renamed).equals(resultA),"generic rename");
   check(new LegacyRegistryAnalyzer().analyze(renamed).equals(resultA),"generic rename hit");
   check(scope.counters().get("registryComputations")==1,"generic rename coalesces");
  }
  Files.delete(renamed);Files.delete(dir);
  check(!SharedSourceSession.active(),"final thread-local clean");
  System.out.println("EDGE CASES PASS checks="+checks);
 }
}
