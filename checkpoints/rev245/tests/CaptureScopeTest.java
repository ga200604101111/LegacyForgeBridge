package dev.yinghuang.legacyforgebridge.behavior;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.*;
import dev.yinghuang.legacyforgebridge.session.LegacySessionController;

/** Runs against real generated diagnostic classes and explicit game/writer/API doubles. */
public final class CaptureScopeTest {
    private static int checks;
    private static final class_310 CLIENT = class_310.method_1551();
    private static class_746 player() { return CLIENT.field_1724; }
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static long count(String name) throws Exception {
        Field f=Rev243Diagnostics.class.getDeclaredField(name);f.setAccessible(true);
        return ((AtomicLong)f.get(null)).get();
    }
    private static List<String> records() { return LegacyMotionTraceLog.writerForTest().records; }
    private static long occurrences(String needle) { return records().stream().filter(s->s.contains(needle)).count(); }
    private static boolean has(String needle) { return occurrences(needle)>0; }
    private static void session() {
        LegacyMotionTraceLog.initialize(s->{});
        LegacyMotionTraceLog.session(player().id);
    }
    private static void restart() {
        LegacyMotionTraceLog.stop("TEST_STOP");
        check(LegacyMotionTraceLog.start("TEST_START"),"new capture starts");
    }
    private static void camera() {
        class_4184 c=new class_4184();c.focus=player();
        Rev243Diagnostics.camera(c);
    }
    private static void move(boolean tail) {
        var requested=new class_243(.2,-.3,.4);
        Rev243Diagnostics.moveHead(player(),"TEST",requested);
        if(tail)Rev243Diagnostics.moveTail(player(),"TEST",requested);
    }
    private static LegacyBehaviorApi.Event event() {
        var e=new LegacyBehaviorApi.Event();e.entityLiving.handle=player();return e;
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("lfb.test.dir",System.getProperty("java.io.tmpdir"));
        String test=args[0];
        switch(test) {
            case "inactive-start" -> {
                Rev243Diagnostics.tickStart(CLIENT);
                check(count("STARTS")==0,"inactive callbacks must not count as captured starts");
            }
            case "local-start" -> {
                session();Rev243Diagnostics.tickStart(CLIENT);
                check(count("STARTS")==1,"local start counted exactly once");
                check(has("stage=TICK_START "),"local tick snapshot emitted");
            }
            case "nonlegacy-start" -> {
                session();LegacySessionController.legacy=false;Rev243Diagnostics.tickStart(CLIENT);
                check(count("STARTS")==0,"nonlegacy callback must not count");
            }
            case "offthread-start" -> {
                session();Thread t=new Thread(()->Rev243Diagnostics.tickStart(CLIENT));t.start();t.join();
                check(count("STARTS")==0,"off-thread callback must not count");
            }
            case "identity-start" -> {
                session();player().id++;Rev243Diagnostics.tickStart(CLIENT);
                check(count("STARTS")==0,"mismatched local identity must not count");
            }
            case "stop-start" -> {
                session();Rev243Diagnostics.tickStart(CLIENT);LegacyMotionTraceLog.stop("TEST_STOP");
                Rev243Diagnostics.tickStart(CLIENT);
                check(count("STARTS")==1,"stopped callbacks must not increment captured count");
            }
            case "restart-counts" -> {
                session();Rev243Diagnostics.tickStart(CLIENT);camera();move(true);
                check(count("STARTS")==1&&count("CAMERAS")==1&&count("MOVES")==1,"first capture seeded");
                var previous=LegacyMotionTraceLog.writerForTest();restart();
                check(previous!=LegacyMotionTraceLog.writerForTest(),"new capture owns new writer");
                check(count("STARTS")==0&&count("CAMERAS")==0&&count("MOVES")==0,"capture counters reset");
            }
            case "active-start-idempotent" -> {
                session();camera();move(true);Rev243Diagnostics.tickStart(CLIENT);
                var previous=LegacyMotionTraceLog.writerForTest();
                check(LegacyMotionTraceLog.start("ALREADY_ACTIVE"),"active capture remains valid");
                check(previous==LegacyMotionTraceLog.writerForTest(),"active start does not replace writer");
                check(count("STARTS")==1&&count("CAMERAS")==1&&count("MOVES")==1,"active start does not reset");
            }
            case "missing-camera-move" -> {
                session();camera();move(true);restart();
                for(int i=0;i<100;i++){Rev243Diagnostics.tickStart(CLIENT);Rev243Diagnostics.tickEnd(CLIENT);}
                check(has("hook=camera reason="),"old camera observations cannot mask missing new hook");
                check(has("hook=move reason="),"old move observations cannot mask missing new hook");
            }
            case "missing-tickstart" -> {
                session();for(int i=0;i<100;i++)Rev243Diagnostics.tickEnd(CLIENT);
                check(has("hook=tickStart reason="),"missing start hook disclosed");
            }
            case "pending-move-restart" -> {
                session();move(false);restart();Rev243Diagnostics.tickStart(CLIENT);
                check(!has("stage=MOVE_TAIL_MISSING "),"old capture move must not pollute new capture");
                Rev243Diagnostics.moveTail(player(),"TEST",new class_243(0,0,0));
                check(has("stage=MOVE_HEAD_MISSING "),"unpaired new tail remains explicit, not paired with old head");
            }
            case "pending-move-same-capture" -> {
                session();move(false);Rev243Diagnostics.tickStart(CLIENT);
                check(has("stage=MOVE_TAIL_MISSING "),"real same-capture missing tail remains visible");
            }
            case "failure-restart" -> {
                session();Rev243Diagnostics.camera(new Object());Rev243Diagnostics.camera(new Object());
                check(occurrences("stage=DIAGNOSTIC_COMPONENT_FAILED ")==1,"failure rate-limited within capture");
                restart();Rev243Diagnostics.camera(new Object());
                check(occurrences("stage=DIAGNOSTIC_COMPONENT_FAILED ")==1,"failure is redisclosed in new capture");
            }
            case "source-label" -> {
                session();var e=event();int[] runs={0};
                LegacyMotionTraceLog.wrap("fixture","jump","fixture",x->{runs[0]++;x.entityLiving.field_70133_I=true;}).run(e);
                check(runs[0]==1,"one source dispatch");
                check(has("velocityChanged=false")&&has("velocityChanged=true"),"SRG velocityChanged correctly labelled");
                check(!has("isAirBorne="),"incorrect alias removed");
            }
            case "source-semantics" -> {
                session();var e=event();e.canceled=true;e.distance=3.5f;e.entityLiving.field_70181_x=.42;
                int[] runs={0};LegacyMotionTraceLog.wrap("fixture","jump","fixture",x->{runs[0]++;x.entityLiving.field_70181_x+=.15;}).run(e);
                check(runs[0]==1&&e.canceled&&e.distance==3.5,"dispatch/cancellation/data preserved");
                check(Math.abs(e.entityLiving.field_70181_x-.57)<1e-12,"fixture source mutation preserved");
                RuntimeException sentinel=new IllegalStateException("sentinel");
                try{LegacyMotionTraceLog.wrap("fixture","jump","fixture",x->{throw sentinel;}).run(e);throw new AssertionError("missing exception");}
                catch(RuntimeException ex){check(ex==sentinel,"source exception identity preserved");}
                check(has("stage=SOURCE_HANDLER_THROW "),"throw recorded");
                e.diagnosticFailure=new IllegalStateException("diagnostic failure");
                LegacyMotionTraceLog.wrap("fixture","jump","fixture",x->{runs[0]++;}).run(e);
                check(runs[0]==2,"diagnostic snapshot exception cannot skip source program");
            }
            case "untraced-source" -> {
                session();LegacyMotionTraceLog.stop("TEST_STOP");int n=records().size();int[] runs={0};
                LegacyMotionTraceLog.wrap("fixture","jump","fixture",e->{runs[0]++;}).run(event());
                check(runs[0]==1&&records().size()==n,"stopped source executes once without records");
            }
            case "readonly-observers" -> {
                session();var p=player();var input=p.field_3913.field_54155;
                for(int i=0;i<200;i++) {
                    p.velocity=new class_243(i*.01-1,-i*.015+.8,.2-i*.005);
                    var v=p.velocity;double x=p.x,y=p.y,z=p.z;
                    CLIENT.field_1690.field_1903.pressed=(i%2==0);
                    var camera=new class_4184();camera.focus=p;var cameraPosition=camera.pos;
                    Rev243Diagnostics.tickStart(CLIENT);Rev243Diagnostics.camera(camera);move(true);Rev243Diagnostics.tickEnd(CLIENT);
                    check(p.velocity==v&&p.x==x&&p.y==y&&p.z==z,"observer must not mutate position or velocity");
                    check(p.field_3913.field_54155==input&&camera.pos==cameraPosition,"input and camera unchanged");
                    check(CLIENT.field_1690.field_1903.consumed==0&&CLIENT.field_1690.field_1832.consumed==0,"keypress not consumed");
                }
            }
            case "local-commands" -> {
                session();int n=records().size();
                check(Rev243Diagnostics.allowCommand("msg somebody private-message"),"unrelated command passes");
                check(records().size()==n,"unrelated command text not recorded");
                check(!Rev243Diagnostics.allowCommand("lfbtrace status"),"trace command remains local");
            }
            case "reconnect" -> {
                session();camera();move(false);Rev243Diagnostics.tickStart(CLIENT);LegacyMotionTraceLog.close();
                CLIENT.field_1724=new class_746();player().id=43;LegacyMotionTraceLog.session(43);
                check(count("CAMERAS")==0&&count("MOVES")==0&&count("STARTS")==0,"new connection resets observations");
                Rev243Diagnostics.tickStart(CLIENT);Rev243Diagnostics.tickEnd(CLIENT);
                check(has("entity=43 ")&&has("stage=INPUT_CHANGED ")&&has("stage=EFFECTS_CHANGED "),"new local state observed");
                check(!has("stage=MOVE_TAIL_MISSING "),"no previous connection move");
            }
            case "registration-once" -> {
                session();restart();Rev243Diagnostics.install();
                check(net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.listeners.size()==1,"one tick listener");
                check(net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.listeners.size()==1,"one command listener");
            }
            default -> throw new IllegalArgumentException(test);
        }
        LegacyMotionTraceLog.close();
        System.out.println("PASS "+test+" checks="+checks);
    }
}
