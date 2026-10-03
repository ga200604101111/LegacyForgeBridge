package dev.yinghuang.legacyforgebridge.behavior;

public final class CorrelationTest {
    private static int checks;
    private static void ok(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
    private static void has(String s,String x){ok(s.contains(x),"missing "+x+" in "+s);}
    private static void no(String s,String x){ok(!s.contains(x),"unexpected "+x+" in "+s);}
    public static void main(String[] args){
        Rev245VelocityCorrelation.resetIdsForTest();
        long t=1_000_000_000L;
        Rev245VelocityCorrelation.captureStarted(7,t);
        String raw=Rev245VelocityCorrelation.decorate("RAW_1710_MOTION","wire=77 connectionObject=1 entity=42 shortXYZ=[0,4560,0] decoded=[0.0,0.57,0.0] bytes=8",t+1000,25);
        has(raw,"corr=1");has(raw,"corrBasis=wire_exact");has(raw,"corrWire=77");
        String via=Rev245VelocityCorrelation.decorate("VIA_OUTPUT_MOTION","wire=77 entity=42 modernPacketId=0x5a decoded=[0.0,0.57,0.0] expectedCodec=[0.0,0.57,0.0] exactCodecMatch=true action=OBSERVE_ONLY",t+20_000,25);
        has(via,"corr=1");has(via,"corrBasis=wire_exact");has(via,"corrVector=0.0,0.57,0.0");
        String dispatch=Rev245VelocityCorrelation.decorate("NETTY_PACKET_DISPATCH","packetObject=9001 entity=42 decoded=[0.0,0.57,0.0]; not a client apply",t+60_000,25);
        has(dispatch,"corr=1");has(dispatch,"corrBasis=vector_time_candidate");has(dispatch,"corrPacketObject=9001");has(dispatch,"viaToDispatchUs=");
        String head=Rev245VelocityCorrelation.decorate("CLIENT_APPLY_HEAD","tick=100 pos=[1,64,1] vel=[0,-0.1,0] ground=false verticalCollision=false packetObject=9001 incoming=[0.0,0.57,0.0]",t+90_000,25);
        has(head,"corr=1");has(head,"corrBasis=packet_object_exact");has(head,"upwardApply=true");has(head,"upwardOrdinal=1");no(head,"secondLiftCandidate=true");
        String tail=Rev245VelocityCorrelation.decorate("CLIENT_APPLY_TAIL","tick=100 pos=[1,64,1] vel=[0,0.57,0] packetObject=9001 before=[0,-0.1,0] incoming=[0,0.57,0] applied=[0.0,0.57,0.0] deltaVY=0.67 changedPositionY=0 action=OBSERVE_ONLY_REV243 caller=x",t+110_000,25);
        has(tail,"corr=1");has(tail,"corrBasis=packet_object_exact");has(tail,"applyDurationUs=");
        String tick=Rev245VelocityCorrelation.decorate("TICK_SNAPSHOT_END","tick=100 pos=[1,64.3,1] vel=[0,0.5,0] ground=false",t+150_000,25);
        has(tick,"afterVelocityCorr=1");has(tick,"contextBasis=temporal_after_apply");
        String secondDispatch=Rev245VelocityCorrelation.decorate("NETTY_PACKET_DISPATCH","packetObject=9002 entity=42 decoded=[0.0,0.57,0.0]; not a client apply",t+400_000,25);
        has(secondDispatch,"corr=2");has(secondDispatch,"corrBasis=unmatched_new_flow");
        String second=Rev245VelocityCorrelation.decorate("CLIENT_APPLY_HEAD","tick=101 pos=[1,64.4,1] vel=[0,0.48,0] ground=false verticalCollision=false packetObject=9002 incoming=[0.0,0.57,0.0]",t+430_000,25);
        has(second,"upwardOrdinal=2");has(second,"secondLiftCandidate=true");has(second,"candidateBasis=sameLocalJump");has(second,"causalProof=false");has(second,"prevPositiveCorr=1");
        String status=Rev245VelocityCorrelation.status();has(status,"positiveApplies=2");has(status,"secondLiftCandidates=1");
        Rev245VelocityCorrelation.decorate("GROUND_CONTACT","tick=110 pos=[1,64,1] vel=[0,0,0] ground=true",t+800_000,25);
        String third=Rev245VelocityCorrelation.decorate("CLIENT_APPLY_HEAD","tick=111 pos=[1,64,1] vel=[0,0,0] ground=true packetObject=9003 incoming=[0.0,0.57,0.0]",t+850_000,26);
        has(third,"upwardOrdinal=1");no(third,"secondLiftCandidate=true");has(third,"groundAtApplyHead=true");
        String unmatched=Rev245VelocityCorrelation.decorate("VIA_OUTPUT_UNDECODED","wire=999 bytes=12",t+900_000,26);
        has(unmatched,"corrUnmatched=true");has(unmatched,"corrStage=via_undecoded");
        String unrelated=Rev245VelocityCorrelation.decorate("SOMETHING_ELSE","private payload",t+950_000,26);ok(unrelated.isEmpty(),"unrelated stage must remain undecorated");
        String late=Rev245VelocityCorrelation.decorate("CAMERA_FRAME","tick=200",t+3_000_000_000L,26);ok(late.isEmpty(),"expired velocity context must not leak");
        String summary=Rev245VelocityCorrelation.summary();has(summary,"correlationDoesNotProveServerCause=true");
        System.out.println("PASS correlation checks="+checks+" "+summary);
    }
}
