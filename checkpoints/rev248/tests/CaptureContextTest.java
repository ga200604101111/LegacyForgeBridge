package dev.yinghuang.legacyforgebridge.behavior;
/** Uses the packaged trace/helper and the existing explicit API/writer doubles. */
public final class CaptureContextTest {
 static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static long count(){return LegacyMotionTraceLog.writerForTest().records.stream().filter(s->s.contains("stage=PROTOCOL_STARTUP_CONTEXT ")).count();}
 public static void main(String[] args){
  System.setProperty("lfb.test.dir",System.getProperty("java.io.tmpdir"));
  LegacyMotionTraceLog.initialize(s->{});LegacyMotionTraceLog.session(42);
  ok(count()==1,"one startup context per capture even if optional VFP API absent");
  LegacyMotionTraceLog.start("already active");ok(count()==1,"active start does not duplicate context");
  LegacyMotionTraceLog.stop("test");LegacyMotionTraceLog.start("new capture");ok(count()==1,"new capture has exactly one context");
  LegacyMotionTraceLog.close();ok(!LegacyMotionTraceLog.active(),"context never keeps capture alive");
  System.out.println("PASS capture-context checks="+checks);
 }
}
