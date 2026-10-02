package dev.yinghuang.legacyforgebridge.behavior;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

public final class DiagnosticTest {
    static int checks;
    static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    static String files(Path p)throws Exception{
        try(var paths=Files.walk(p)){StringBuilder b=new StringBuilder();for(Path f:paths.filter(Files::isRegularFile).sorted().toList())b.append(Files.readString(f));return b.toString();}
    }
    static void stateSame(class_746 p,class_243 v,double x,double y,double z,boolean g){
        check(p.velocity==v&&p.x==x&&p.y==y&&p.z==z&&p.ground==g,"observer changed movement state");
    }
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);Files.createDirectories(root);System.setProperty("lfb.test.dir",root.toString());
        System.setProperty("legacyforgebridge.jumpReconcile","source-history"); // Deliberately ON: still inert.
        var notices=new CopyOnWriteArrayList<String>();
        LegacyMotionTraceLog.initialize(notices::add);
        check(ClientTickEvents.START_CLIENT_TICK.listeners.size()==1,"start event registered");
        check(ClientSendMessageEvents.ALLOW_COMMAND.listeners.size()==1,"local command event registered");
        class_310 c=class_310.method_1551();class_746 p=c.field_1724;c.field_1690.field_1903.pressed=true;
        LegacyMotionTraceLog.session(p.id);check(LegacyMotionTraceLog.active(),"automatic capture");
        check(LegacyMotionTraceLog.local(42)&&!LegacyMotionTraceLog.local(41),"wire scope");
        class_4184 camera=new class_4184();camera.focus=p;
        class_243 raw=p.velocity;double x=p.x,y=p.y,z=p.z;
        ClientTickEvents.START_CLIENT_TICK.listeners.get(0).onStartTick(c);
        Rev243Diagnostics.camera(camera);Rev243Diagnostics.tickEnd(c);
        stateSame(p,raw,x,y,z,false);check(c.field_1690.field_1903.consumed==0,"keys not consumed");
        var request=new class_243(.2,.5,-.2);Rev243Diagnostics.moveHead(p,"SELF",request);
        p.x+=.2;p.y+=.25;p.z-=.2; // Simulated engine collision. Observer must only read it.
        Rev243Diagnostics.moveTail(p,"SELF",request);
        check(p.y==y+.25&&p.velocity==raw,"move callbacks preserve clipped result");
        for(int i=0;i<200;i++){
            for(double sy:new double[]{.5698590001831167,-.2512360373557957,0,3.0,-3.0}){
                var packet=new class_2743(42,new class_243(.75,sy,-.2));
                Rev241JumpMotionBridge.sourceBefore(p);Rev241JumpMotionBridge.sourceAfter(p);
                Rev241JumpMotionBridge.packetHead(packet);
                p.velocity=packet.v(); // Simulated ordinary authoritative packet application.
                class_243 expected=p.velocity;
                Rev241JumpMotionBridge.packetTail(packet);Rev241JumpMotionBridge.nativeVelocity(p,expected);
                Rev241JumpMotionBridge.nativePosition(p,p.x,p.y,p.z);
                check(p.velocity==expected,"raw velocity modified while property on");
            }
        }
        System.out.println("PASS 1000 authoritative velocity cases unchanged (positive/negative/zero/XZ), including old enable property");
        var e=new LegacyBehaviorApi.Event();e.entityLiving=new LegacyBehaviorApi.Living();e.entity=e.entityLiving;
        e.entityLiving.handle=p;e.entityLiving.field_70170_p=new LegacyBehaviorApi.World();e.entityLiving.field_70181_x=.42;
        int[] calls={0};
        var jump=LegacyMotionTraceLog.wrap("fixture","jump",null,ev->{calls[0]++;ev.entityLiving.field_70181_x+=.15;});jump.run(e);
        check(calls[0]==1&&Math.abs(e.entityLiving.field_70181_x-.57)<1e-10,"source event invoked exactly once");
        var fall=LegacyMotionTraceLog.wrap("fixture","fall",null,ev->{calls[0]++;ev.setCanceled(true);for(int i=0;i<10;i++)ev.entityLiving.field_70170_p.spawnParticle("flame",2,4+i*.1,6,.01,.02,.03);});fall.run(e);
        check(calls[0]==2&&e.isCanceled()&&e.entityLiving.field_70170_p.particles.size()==10,"fall source results retained");
        var expectedError=new IllegalStateException("fixture");boolean same=false;
        try{LegacyMotionTraceLog.wrap("fixture","fall",null,ev->{throw expectedError;}).run(e);}catch(IllegalStateException err){same=err==expectedError;}
        check(same,"original source exception identity preserved");
        check(Rev243Diagnostics.allowCommand("say PRIVATE_NOT_TO_LOG"),"ordinary commands pass");
        check(Rev243Diagnostics.allowCommand("lfbtraceOther PRIVATE_NOT_TO_LOG"),"prefix collision passes");
        check(!ClientSendMessageEvents.ALLOW_COMMAND.listeners.get(0).allowSendCommand("lfbtrace mark"),"mark is local");
        Rev243TraceWriter first=LegacyMotionTraceLog.writerForTest();
        check(!Rev243Diagnostics.allowCommand("lfbtrace stop"),"stop is local");check(!LegacyMotionTraceLog.active(),"stop gates trace");
        check(first.awaitClosed(5000),"capture closed");
        String log=files(root.resolve("logs/lfb-motion"));
        for(String stage:List.of("TICK_START","TICK_SNAPSHOT_END","INPUT_SNAPSHOT","CAMERA_FRAME","MOVE_HEAD","MOVE_TAIL","USER_MARK","SOURCE_HANDLER_HEAD","SOURCE_HANDLER_TAIL","SOURCE_HANDLER_THROW","SOURCE_PARTICLE_QUEUED","TRACE_END"))check(log.contains("stage="+stage)||log.contains(stage+" writeUtc"),"missing "+stage);
        check(!log.contains("PRIVATE_NOT_TO_LOG"),"unrelated command content leaked");
        check(log.contains("actual=[0.20000000000000018,0.25,-0.20000000000000018]"),"actual clipped move recorded");
        check(log.contains("keyJump=true")&&log.contains("tickProgress=0.5"),"input/camera numbers captured");
        check(log.contains("heuristicReconciliation=DISABLED"),"disabled config recorded");
        check(!Rev243Diagnostics.allowCommand("lfbtrace start"),"start local");check(LegacyMotionTraceLog.active(),"manual restart");
        Rev243TraceWriter second=LegacyMotionTraceLog.writerForTest();LegacyMotionTraceLog.close();check(second.awaitClosed(5000),"second close");
        check(!first.firstPath().equals(second.firstPath()),"captures overwrite each other");
        System.out.println("PASS read-only tick/input/camera/collision snapshots; source event single dispatch and exception preservation; local start/stop/mark; command privacy");
        writerTests(root.resolve("writer-tests"));
        System.out.println("RESULT checks="+checks+" usesMinecraftTestDoubles=true actualPackagedHelpers=true liveMinecraftLaunch=false");
    }
    static void writerTests(Path root)throws Exception{
        var w=new Rev243TraceWriter(root,"normal","test=true",s->{},16384,1000000,2000000);
        Thread[] t=new Thread[4];for(int i=0;i<4;i++){final int n=i;t[i]=new Thread(()->{for(int j=0;j<1000;j++)w.offer("record="+n+":"+j);});t[i].start();}
        for(var th:t)th.join();w.requestClose("TEST_END");check(w.awaitClosed(5000),"writer normal close");
        check(w.accepted()==4000&&w.written()==4000&&w.dropped()==0&&w.discarded()==0,"concurrent records lost");
        String normal=Files.readString(w.firstPath());check(normal.contains("incomplete=false"),"normal integrity summary");
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var small=new Rev243TraceWriter(root,"overflow","test=true",s->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}},1,1000000,2000000);
        check(entered.await(5000,TimeUnit.MILLISECONDS),"writer notice latch");for(int i=0;i<100;i++)small.offer("n="+i);release.countDown();
        small.requestClose("TEST_OVERFLOW");check(small.awaitClosed(5000),"overflow close");
        check(small.accepted()==1&&small.dropped()==99,"queue bounds not enforced");
        check(Files.readString(small.firstPath()).contains("dropped=99")&&Files.readString(small.firstPath()).contains("incomplete=true"),"loss not disclosed");
        var limited=new Rev243TraceWriter(root,"limited","test=true",s->{},4096,1024,4096);
        for(int i=0;i<1000;i++)limited.offer("n="+i+" "+"x".repeat(100));
        check(limited.awaitClosed(5000),"disk budget stop");check(!limited.accepting()&&limited.discarded()>0,"disk budget admits more data");
        String all=files(root);check(all.contains("TRACE_LIMIT_REACHED")&&all.contains("CONTINUATION previousPart="),"rollover/limit not recorded");
        var trunc=new Rev243TraceWriter(root,"truncation","test=true",s->{},4,1000000,2000000);trunc.offer("X".repeat(20000));trunc.requestClose("TEST_TRUNCATION");
        check(trunc.awaitClosed(5000),"truncate close");check(Files.readString(trunc.firstPath()).contains("DETAIL_TRUNCATED=true"),"truncation not marked");
        System.out.println("PASS async writer: 4 concurrent producers/4000 complete records; controlled queue overflow disclosed; part rollover; total disk cap; truncation disclosed; clean close");
    }
}
