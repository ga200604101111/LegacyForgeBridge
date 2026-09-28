import dev.yinghuang.legacyforgebridge.desktop.RestartPlan;
import java.nio.file.*;
public final class RestartGuard217Test {
  static int n;static void ok(boolean b,String m){n++;if(!b)throw new AssertionError(m);}
  public static void main(String[] a)throws Exception{
    Path d=Path.of(a[0]);Files.createDirectories(d);Path g=d.resolve("restart-guard.properties");long now=System.currentTimeMillis();String key="same-plan";
    ok(RestartPlan.guardAllows(g,key,now),"fresh guard allows");
    RestartPlan.guardClaim(g,"first",key,now);
    ok(!RestartPlan.guardAllows(g,key,now+1),"same plan immediate retry blocked");
    ok(!RestartPlan.guardAllows(g,"other-plan",now+1),"different plan immediate retry blocked");
    ok(!RestartPlan.guardAllows(g,key,now+899_999L),"same plan blocked before 15 minutes");
    ok(RestartPlan.guardAllows(g,key,now+900_000L),"same plan allowed at 15 minutes");
    ok(RestartPlan.guardAllows(g,"other-plan",now+900_000L),"different plan allowed at 15 minutes");
    RestartPlan.guardClaim(g,"retry",key,now+900_000L);
    ok(!RestartPlan.guardAllows(g,key,now+900_001L),"retry creates a new short loop guard");
    ok(RestartPlan.guardAllows(g,key,now+4L*60*60*1000),"four-hour-old same plan no longer blocks user retry");
    System.out.println("RestartGuard217Test assertions="+n+" failures=0");
  }
}
