import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.*;
import dev.yinghuang.legacyforgebridge.behavior.*;
import dev.yinghuang.legacyforgebridge.session.LegacySessionController;
import dev.yinghuang.legacyforgebridge.LegacyFileLogger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Executes the real patched LegacyClientJumpMotion lifecycle and new helper classes. */
public final class JumpLifecycleTest {
 static final class_310 MC=class_310.method_1551();
 static final double BEFORE=(double)0.42F;
 static boolean fixed;static long assertions;static int matched,passed;
 static void check(boolean b,String msg){assertions++;if(!b)throw new AssertionError(msg);}
 static void eq(double a,double b,String msg){check(Math.abs(a-b)<1E-10,msg+" actual="+a+" expected="+b);}
 static class_746 reset(){
  MC.field_1724=new class_746();MC.field_1687=new class_638();MC.connection=new class_634();MC.server=null;
  LegacySessionController.legacy=true;LegacyBehaviorRuntime.delta=0.15;end();return MC.field_1724;
 }
 static void end(){ClientTickEvents.END_CLIENT_TICK.listener.onEndTick(MC);}
 static void jump(class_746 p){
  p.ground=true;p.field_5992=true;p.method_18800(0,BEFORE,0);int before=LegacyBehaviorRuntime.calls;
  LegacyClientJumpMotion.sourceJump(p);check(LegacyBehaviorRuntime.calls==before+1,"exactly one source event");
  eq(p.method_18798().field_1351,BEFORE+LegacyBehaviorRuntime.delta,"source result preserved");
 }
 static void physics(class_746 p){
  var v=p.method_18798();p.method_18800(v.field_1352,v.field_1351,v.field_1350); // horizontal-only write
  p.method_5814(p.x+v.field_1352,p.y+v.field_1351,p.z+v.field_1350);
  p.ground=false;p.field_5992=false;
  p.method_18800(v.field_1352,Rev241JumpHistory.gravity(v.field_1351),v.field_1350);end();
 }
 static void prepare(class_746 p){jump(p);physics(p);p.field_6012++;physics(p);}
 static class_2743 echo(class_746 p){return new class_2743(p.id,0,Rev241JumpHistory.encodedY(BEFORE+0.15,1),0);}
 static double packet(class_746 p,class_2743 packet,boolean netty,Runnable during)throws Exception {
  double before=p.method_18798().field_1351;
  if(netty){Thread t=new Thread(()->LegacyClientJumpMotion.velocityHead(packet),"Netty-test-double");t.start();t.join();
   eq(p.method_18798().field_1351,before,"Netty dispatch is not application");}
  LegacyClientJumpMotion.velocityHead(packet);var incoming=packet.method_73085();
  p.method_18800(incoming.field_1352,incoming.field_1351,incoming.field_1350);
  if(during!=null)during.run();
  LegacyClientJumpMotion.velocityTail(packet);
  return p.method_18798().field_1351;
 }
 static void passAfter(String name,java.util.function.Consumer<class_746> invalidate)throws Exception {
  var p=reset();prepare(p);invalidate.accept(p);var q=echo(p);
  double y=packet(p,q,true,null);eq(y,q.method_73085().field_1351,name+" passes full server Y");
 }
 record P(int age,double x,double y,double z){}
 static void replay(Path fixture)throws Exception {
  Map<Integer,List<P>> rows=new LinkedHashMap<>();
  for(String line:Files.readAllLines(fixture).subList(1,Files.readAllLines(fixture).size())){
   String[] c=line.split("\t");var list=rows.computeIfAbsent(Integer.parseInt(c[0]),k->new ArrayList<>());
   if(Integer.parseInt(c[3])>=0)list.add(new P(Integer.parseInt(c[3]),Double.parseDouble(c[4]),Double.parseDouble(c[5]),Double.parseDouble(c[6])));
  }
  for(var row:rows.entrySet()){
   var p=reset();jump(p);double peak=0;int localMatches=0;
   for(int age=0;age<60;age++){
    p.field_6012=100+age;physics(p);peak=Math.max(peak,p.y);
    for(P sample:row.getValue())if(sample.age()==age){
     double prior=p.method_18798().field_1351;var q=new class_2743(p.id,sample.x(),sample.y(),sample.z());
     double after=packet(p,q,true,null);boolean expectedMatch=fixed&&sample.y()>0;
     eq(after,expectedMatch?prior:sample.y(),"fixture "+row.getKey()+" age="+age);
     eq(p.method_18798().field_1352,sample.x(),"packet X unchanged");
     eq(p.method_18798().field_1350,sample.z(),"packet Z unchanged");
     if(expectedMatch){matched++;localMatches++;}else passed++;
    }
    if(p.y<0&&age>1){p.ground=true;end();break;}
   }
   if(fixed)eq(peak,2.1658178319081456,"single-arc peak for fixture "+row.getKey());
   else {
    double[] originalPeaks={0,3.223608354257026,2.1658178319081456,2.1658178319081456,2.1658178319081456,
     2.7348881914227547,3.2150881879180018,5.846647832927157,3.2150881879180018,5.388496558792802,
     3.2150881879180018,3.2150881879180018,3.2150881879180018,3.2150881879180018,3.2150881879180018};
    eq(peak,originalPeaks[row.getKey()],"baseline peak reproduces supplied diagnostic "+row.getKey());
   }
   System.out.println("REPLAY jump="+row.getKey()+" peak="+peak+" reconciled="+localMatches);
  }
  check(rows.size()==14,"all 14 jumps replayed");
  if(fixed){check(matched==13,"13 positive historical packets reconciled");check(passed==1,"one downward packet unchanged");}
 }
 static void coreTests(){
  Rev241JumpHistory h=new Rev241JumpHistory();
  check(!h.arm(0,BEFORE,BEFORE,true),"no source delta unarmed");
  check(!h.arm(0,BEFORE,BEFORE+.15,false),"unsafe source unarmed");
  check(!h.arm(0,BEFORE,Double.NaN,true),"nonfinite source unarmed");
  h.arm(0,BEFORE,BEFORE+.15,true);h.observePosition(BEFORE+.15,Double.NaN);check(!h.active(),"nonfinite position invalidates");
  h.arm(0,BEFORE,BEFORE+.15,true);h.observePosition(BEFORE+.15,0);check(h.active(),"zero movement does not fabricate gravity");
  h.observeVelocity(0,BEFORE+.15,Rev241JumpHistory.gravity(BEFORE+.15),true);
  h.tick(41,h.expectedY(),true);check(!h.active(),"bounded lifetime");
  for(double source:new double[]{.52,.57,.87,1.25,2.5})for(int count=1;count<=8;count++){
   h.arm(0,BEFORE,source,true);double current=source;
   for(int n=0;n<count;n++){double next=Rev241JumpHistory.gravity(current);h.observeVelocity(n,current,next,true);current=next;}
   double scale=Math.ceil(source);double incoming=Rev241JumpHistory.encodedY(source,scale);
   // compare to the ACTUAL codec shipped in the base main JAR
   double raw=LegacyMotionWireCodec.quantizeLegacy(source);
   double[] codec=LegacyMotionWireCodec.translated(0,raw,0);
   eq(codec[1],incoming,"exact shipped packed-vector codec");
   var d=h.decide(count,current,0,incoming,0,true);check(d.reconcile(),"arbitrary source-driven boost "+source);
   eq(d.replacementY(),current,"history keeps current predicted phase");
  }
 }
 static void edgeTests()throws Exception {
  passAfter("hurt",p->p.field_6235=10);
  passAfter("water",p->p.water=true);passAfter("lava",p->p.lava=true);
  passAfter("climbing",p->p.climbing=true);passAfter("flying",p->p.abilities.field_7479=true);
  passAfter("vehicle",p->p.vehicle=true);passAfter("landed",p->p.ground=true);
  passAfter("ceiling collision",p->p.field_5992=true);
  passAfter("unexpected local impulse",p->p.method_18800(0,.333,0));
  passAfter("clipped displacement",p->p.method_5814(p.x,p.y+.1,p.z));
  passAfter("modern protocol",p->LegacySessionController.legacy=false);
  passAfter("integrated server",p->MC.server=new class_1132());
  passAfter("connection changed",p->MC.connection=new class_634());
  passAfter("world changed",p->MC.field_1687=new class_638());
  passAfter("expired",p->p.field_6012+=41);
  for(String reason:List.of("damage-or-entity-status","explosion","position-correction-or-respawn")){
   var p=reset();prepare(p);
   Thread t=new Thread(()->LegacyClientJumpMotion.barrier(reason),"Netty-barrier");t.start();t.join();
   var q=echo(p);eq(packet(p,q,true,null),q.method_73085().field_1351,reason+" barrier");
  }
  for(double incoming:new double[]{-.2,0,.4,1.8,3.9,Double.POSITIVE_INFINITY}){
   var p=reset();prepare(p);var q=new class_2743(p.id,.21,incoming,-.19);double got=packet(p,q,true,null);
   check(Double.doubleToLongBits(got)==Double.doubleToLongBits(incoming),"unmatched/downward/zero/large full pass");
  }
  {var p=reset();prepare(p);var q=echo(p);
   double got=packet(p,q,true,()->LegacyClientJumpMotion.barrier("explosion"));eq(got,q.method_73085().field_1351,"barrier between head and tail");}
  {var p=reset();prepare(p);var q=echo(p);double got=packet(p,q,true,()->p.method_18800(.2,.44,.3));
   eq(got,.44,"other packet handler velocity retained");eq(p.method_18798().field_1352,.2,"other handler X retained");}
  {var p=reset();prepare(p);var q=echo(p);double got=packet(p,q,true,()->p.method_5814(0,p.y+1,0));
   eq(got,q.method_73085().field_1351,"position change during packet");}
  {var p=reset();prepare(p);var q=echo(p);LegacyClientJumpMotion.velocityHead(q);end();
   p.method_18800(0,q.method_73085().field_1351,0);LegacyClientJumpMotion.velocityTail(q);
   eq(p.method_18798().field_1351,q.method_73085().field_1351,"missing tail cannot poison next tick");}
  {var p=reset();LegacyBehaviorRuntime.delta=0;jump(p);physics(p);p.field_6012++;physics(p);var q=echo(p);
   eq(packet(p,q,true,null),q.method_73085().field_1351,"unmodified vanilla jump not admitted");}
  {var p=reset();prepare(p);double before=p.method_18798().field_1351;var q=new class_2743(2,0,.6,0);
   LegacyClientJumpMotion.velocityHead(q);LegacyClientJumpMotion.velocityTail(q);eq(p.method_18798().field_1351,before,"remote entity ignored");}
  // Exhaust/disable trace deliberately: new hooks must still execute at the head of old methods.
  {var p=reset();prepare(p);LegacyMotionTraceLog.close();double before=p.method_18798().field_1351;var q=echo(p);
   eq(packet(p,q,true,null),before,"reconciliation independent of diagnostic active window");}
 }
 static void repeated()throws Exception {
  var p=reset();double priorY=0;
  for(int j=0;j<1000;j++){
   p.y=0;p.ground=true;p.field_5992=true;p.field_6012+=1;jump(p);double peak=0;
   for(int age=0;age<40;age++){
    if(age>0)p.field_6012++;physics(p);peak=Math.max(peak,p.y);
    if(age==1||age==7){double before=p.method_18798().field_1351;var q=echo(p);eq(packet(p,q,false,null),before,"repeat "+j+" no upward reset");}
    if(p.y<0&&age>1){p.y=0;p.ground=true;p.method_18800(0,-.0784000015258789,0);end();break;}
   }
   eq(peak,2.1658178319081456,"repeat "+j+" one source-height arc");priorY=peak;
  }
  Field total=LegacyMotionTraceLog.class.getDeclaredField("total");total.setAccessible(true);
  check(total.getInt(null)>=30000,"diagnostic line cap reached during long repeat test");
  System.out.println("REPEATED jumps=1000 sameSession=true peak="+priorY+" worksAfterTraceCap=true");
 }
 public static void main(String[] args)throws Exception {
  fixed=args[0].equals("fixed");LegacyClientJumpMotion.initialize();
  coreTests();replay(Path.of(args[1]));
  if(fixed){edgeTests();repeated();check(LegacyFileLogger.ready==1,"startup readiness updated");}
  System.out.println("RESULT mode="+args[0]+" assertions="+assertions+" fixtureMatched="+matched+" fixturePassed="+passed+" minecraftTestDoubles=true lifecycleFromMainJar=true");
 }
}
