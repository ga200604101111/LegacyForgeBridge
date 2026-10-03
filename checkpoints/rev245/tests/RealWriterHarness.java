package dev.yinghuang.legacyforgebridge.behavior;
import java.nio.file.*;
import java.util.*;
public final class RealWriterHarness {
  public static void main(String[] a) throws Exception {
    Path root=Path.of(a[0]);System.setProperty("lfb.test.dir",root.toString());
    LegacyMotionTraceLog.initialize(s->System.out.println("NOTICE "+s));
    LegacyMotionTraceLog.session(42);
    for(int i=0;i<4000;i++){
      long wire=i+1;
      double y=(i%17==0)?.57:-.08;
      LegacyMotionTraceLog.event("RAW_1710_MOTION","wire="+wire+" connectionObject=1 entity=42 shortXYZ=[0,0,0] decoded=[0.0,"+y+",0.0] bytes=8");
      LegacyMotionTraceLog.event("VIA_OUTPUT_MOTION","wire="+wire+" entity=42 modernPacketId=0x5a decoded=[0.0,"+y+",0.0] expectedCodec=[0.0,"+y+",0.0] exactCodecMatch=true action=OBSERVE_ONLY");
    }
    Rev243TraceWriter w=LegacyMotionTraceLog.writerForTest();
    LegacyMotionTraceLog.stop("REAL_WRITER_HARNESS");
    if(!w.awaitClosed(10000))throw new AssertionError("writer close timeout");
    Path dir=root.resolve("logs/lfb-motion");
    var files=Files.list(dir).sorted().toList();
    if(files.isEmpty())throw new AssertionError("no output");
    long lines=0,corr=0,end=0;
    for(Path f:files)for(String line:Files.readAllLines(f)){lines++;if(line.contains("corr="))corr++;if(line.contains("TRACE_END"))end++;}
    if(corr<8000)throw new AssertionError("correlation annotations missing: "+corr);
    if(end<1)throw new AssertionError("TRACE_END missing");
    System.out.println("PASS real-writer files="+files.size()+" lines="+lines+" corrLines="+corr+" traceEnd="+end+" "+w.stats());
  }
}
