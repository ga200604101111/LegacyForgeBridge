import java.nio.file.*;
import java.util.*;
import net.minecraft.*;
import dev.yinghuang.legacyforgebridge.behavior.*;
import dev.yinghuang.legacyforgebridge.session.LegacySessionController;

/** Real packaged motion/landing helpers and original source bodies; game APIs are test doubles. */
public final class RegressionTest {
 static int assertions;static boolean fixed,off;static class_310 mc=class_310.INSTANCE;
 static final double BOOST=(double).42F+.15D, PEAK=2.1658178319081456;
 static void check(boolean b,String m){assertions++;if(!b)throw new AssertionError(m);}
 static void eq(double a,double b,String m){check(Math.abs(a-b)<1E-8,m+" got="+a+" expected="+b);}
 static class_1657 fresh(int tick) {
  LegacySessionController.legacy=true;mc.mainThread=true;mc.server=null;
  mc.field_1724=new class_1657();mc.field_1687=new Object();mc.connection=new Object();
  mc.field_1724.field_6012=tick;Rev241JumpMotionBridge.tick(mc);return mc.field_1724;
 }
 static void start(class_1657 p,int tick) {
  p.field_6012=tick;p.ground=true;p.field_5992=true;
  p.method_18800(p.v.field_1352,(double).42F,p.v.field_1350);
  Rev241JumpMotionBridge.sourceBefore(p);
  p.method_18800(p.v.field_1352,LegacyBehaviorRuntime.sourceJump(p),p.v.field_1350);
  Rev241JumpMotionBridge.sourceAfter(p);eq(p.v.field_1351,p.wing?BOOST:(double).42F,"source boost not changed");
 }
 static void step(class_1657 p,int t) {
  p.field_6012=t;
  double next=p.y+p.v.field_1351;
  if(next<=4&&p.v.field_1351<0) {
   Rev241JumpMotionBridge.nativePosition(p,p.x,4,p.z);p.y=4;p.ground=true;p.field_5992=true;
   p.method_18800(p.v.field_1352,0,p.v.field_1350);
   p.method_18800(p.v.field_1352,Rev241JumpHistory.gravity(0),p.v.field_1350);
  } else {
   Rev241JumpMotionBridge.nativePosition(p,p.x,next,p.z);p.y=next;p.ground=false;p.field_5992=false;
   p.method_18800(p.v.field_1352,Rev241JumpHistory.gravity(p.v.field_1351),p.v.field_1350);
  }
 }
 static void packet(class_1657 p,double x,double y,double z) {
  var packet=new class_2743(p.id,x,y,z);Rev241JumpMotionBridge.packetHead(packet);
  p.method_18800(x,y,z);Rev241JumpMotionBridge.packetTail(packet);
  eq(p.v.field_1352,x,"packet X retained");eq(p.v.field_1350,z,"packet Z retained");
 }
 static double flight(class_1657 p,int start,List<double[]> packets) {
  start(p,start);double peak=0;
  for(int phase=1;phase<=100;phase++) {
   step(p,start+phase-1);peak=Math.max(peak,p.y-4);
   for(double[] v:packets)if((int)v[0]==phase)packet(p,v[1],v[2],v[3]);
   Rev241JumpMotionBridge.tick(mc);
   if(p.ground)return peak;
  }
  throw new AssertionError("never landed");
 }
 static double encoded(double y) {return Rev241JumpHistory.encodedY(y,1);}
 static double prevDown(){double v=BOOST;for(int i=0;i<10;i++)v=Rev241JumpHistory.gravity(v);return encoded(v);}
 static void fixture(String path)throws Exception {
  var p=fresh(6910);int full=0,shortened=0;
  for(String row:Files.readAllLines(Path.of(path))) {
   String[] f=row.split("\t",-1);int j=Integer.parseInt(f[0]),start=Integer.parseInt(f[1]);double expected=Double.parseDouble(f[2]);
   while(p.field_6012<start-1){p.field_6012++;Rev241JumpMotionBridge.tick(mc);}
   p.v=new class_243(0,0,0); // dry, stationary trajectory used to isolate recorded Y packets
   List<double[]> packets=new ArrayList<>();
   if(f.length>3&&!f[3].isEmpty())for(String v:f[3].split(";"))packets.add(Arrays.stream(v.split(",")).mapToDouble(Double::parseDouble).toArray());
   double peak=flight(p,start,packets);
   eq(peak,fixed?PEAK:expected,"recorded jump "+j);
   if(Math.abs(peak-PEAK)<1E-8)full++;else shortened++;
  }
  System.out.println("FIXTURE jumps=30 full="+full+" shortened="+shortened+" stationaryYReplay=true");
 }
 static void repeated() {
  var p=fresh(0);
  for(int j=0;j<1000;j++) {
   int t=p.field_6012+1;
   List<double[]> incoming=j==0?List.of():List.of(new double[]{3,0,prevDown(),0},new double[]{5,0,encoded(BOOST),0});
   eq(flight(p,t,incoming),PEAK,"continuous jump "+j);
  }
  System.out.println("REPEATED 1000 jumps: source-native peak retained; stale descent then ascent packet handled");
 }
 static class_1657 previous(boolean moving,int gap) {
  var p=fresh(0);p.v=new class_243(moving?.1:0,0,0);flight(p,1,List.of());
  int end=p.field_6012;while(p.field_6012<end+gap){p.field_6012++;Rev241JumpMotionBridge.tick(mc);}
  p.v=new class_243(0,0,0);start(p,p.field_6012+1);step(p,p.field_6012);Rev241JumpMotionBridge.tick(mc);return p;
 }
 static void guardTests() {
  var p=previous(false,0);double before=p.v.field_1351;
  packet(p,0,prevDown(),0);eq(p.v.field_1351,before,"previous descent does not cut new ascent");
  packet(p,0,prevDown(),0);eq(p.v.field_1351,prevDown(),"previous descent accepted at most once");
  p=previous(false,0);packet(p,0,-.4,0);eq(p.v.field_1351,-.4,"unmatched downward impulse retained");
  p=previous(false,0);packet(p,.3,prevDown(),0);eq(p.v.field_1351,prevDown(),"horizontal impulse not classified as stationary echo");
  p=previous(true,0);packet(p,0,prevDown(),0);eq(p.v.field_1351,prevDown(),"moving previous jump not eligible");
  p=previous(false,3);packet(p,0,prevDown(),0);eq(p.v.field_1351,prevDown(),"expired immediate-jump window");
  p=previous(false,0);p.field_6235=1;packet(p,0,prevDown(),0);eq(p.v.field_1351,prevDown(),"hurt state retains packet");
  p=previous(false,0);Rev241JumpMotionBridge.barrier("explosion");packet(p,0,prevDown(),0);eq(p.v.field_1351,prevDown(),"barrier retains packet");
  p=previous(false,0);p.water=true;packet(p,0,prevDown(),0);eq(p.v.field_1351,prevDown(),"water retains packet");
  p=previous(false,0);p.abilities.field_7479=true;packet(p,0,prevDown(),0);eq(p.v.field_1351,prevDown(),"flight retains packet");
  p=previous(false,0);p.method_18800(0,-.7,0);packet(p,0,prevDown(),0);eq(p.v.field_1351,prevDown(),"non-gravity motion invalidates archive");
  p=previous(false,0);mc.field_1687=new Object();packet(p,0,prevDown(),0);eq(p.v.field_1351,prevDown(),"world change clears archive");
  p=fresh(0);start(p,1);step(p,1);packet(p,0,prevDown(),0);eq(p.v.field_1351,prevDown(),"no prior observed descent passes");
  p=previous(false,0);before=p.v.field_1351;var remote=new class_2743(p.id+1,0,prevDown(),0);
  Rev241JumpMotionBridge.packetHead(remote);Rev241JumpMotionBridge.packetTail(remote);eq(p.v.field_1351,before,"remote entity ignored");
  var netty=new class_2743(p.id,0,prevDown(),0);mc.mainThread=false;
  Rev241JumpMotionBridge.packetHead(netty);Rev241JumpMotionBridge.packetTail(netty);mc.mainThread=true;eq(p.v.field_1351,before,"Netty observation is not apply");
  p=previous(false,0);var q=new class_2743(p.id,0,prevDown(),0);Rev241JumpMotionBridge.packetHead(q);
  p.method_18800(.1,-.8,0);Rev241JumpMotionBridge.packetTail(q);eq(p.v.field_1351,-.8,"another handler's changed result preserved");
 }
 static void particleTests() {
  LegacyBehaviorRuntime.particles.clear();LegacyBehaviorRuntime.falls=0;
  var p=fresh(200);double before=p.y;
  flight(p,201,List.of());check(LegacyBehaviorRuntime.falls==1,"fallback source fall event exactly once");
  check(LegacyBehaviorRuntime.particles.size()==10,"original wing body emits ten particles");
  eq(LegacyBehaviorRuntime.lastSourceY,4+(double)1.62F,"source local posY restored");
  eq(p.y,before,"live position not changed by visuals");eq(Rev242LandingBridge.sourceY(p,4),4,"scope cleared after fall");
  for(var v:LegacyBehaviorRuntime.particles) {
   check(v.type().equals("flame"),"original flame type");
   check(v.y()>=4+(double)1.62F-(double)1.8F && v.y()<=4+(double)1.62F,"legacy body-volume particle Y");
   check(Math.abs(v.x()-p.x)<=.600001&&Math.abs(v.z()-p.z)<=.600001,"original horizontal spread");
  }
  int n=LegacyBehaviorRuntime.particles.size();
  Rev242LandingBridge.fall(p,2,1,null);Rev241JumpMotionBridge.tick(mc);
  check(LegacyBehaviorRuntime.particles.size()==n,"native call plus fallback is deduplicated");
  p=fresh(300);start(p,301);
  for(int phase=1;phase<30;phase++) {
   step(p,300+phase);
   if(p.ground)Rev242LandingBridge.fall(p,2,1,null);
   Rev241JumpMotionBridge.tick(mc);if(p.ground)break;
  }
  check(LegacyBehaviorRuntime.particles.size()==n+10,"native before end-tick fallback also deduplicated");
  p=fresh(400);p.wing=false;n=LegacyBehaviorRuntime.particles.size();flight(p,401,List.of());
  check(LegacyBehaviorRuntime.particles.size()==n,"no equipped wing no invented particles");
  p=fresh(500);p.y=5;p.ground=false;p.field_6012++;Rev241JumpMotionBridge.tick(mc);
  p.y=4.5;p.field_6012++;Rev241JumpMotionBridge.tick(mc);Rev241JumpMotionBridge.barrier("teleport");
  p.y=4;p.ground=true;p.field_6012++;Rev241JumpMotionBridge.tick(mc);
  check(LegacyBehaviorRuntime.particles.size()==n,"teleport is not treated as landing");
  p=fresh(600);p.y=5;p.ground=false;p.water=true;p.field_6012++;Rev241JumpMotionBridge.tick(mc);
  p.y=4;p.ground=true;p.field_6012++;Rev241JumpMotionBridge.tick(mc);
  check(LegacyBehaviorRuntime.particles.size()==n,"water landing fallback omitted");
  p=fresh(700);LegacyBehaviorRuntime.throwNext=true;boolean thrown=false;
  try{Rev242LandingBridge.fall(p,2,1,null);}catch(IllegalStateException e){thrown=true;}
  check(thrown,"original source failure not swallowed");eq(Rev242LandingBridge.sourceY(p,4),4,"scope cleared on source exception");
  System.out.println("PARTICLES original source body: ten flames, legacy Y basis, dedupe, no-wing, water/teleport and exception cleanup pass");
 }
 public static void main(String[] args)throws Exception {
  fixed=args[0].equals("fixed");off=args[0].equals("off");
  if(!off)fixture(args[1]);
  if(fixed){repeated();guardTests();}
  if(fixed||off)particleTests();
  System.out.println("RESULT mode="+args[0]+" assertions="+assertions+" minecraftTestDoubles=true actualSourceBodies=true");
 }
}
