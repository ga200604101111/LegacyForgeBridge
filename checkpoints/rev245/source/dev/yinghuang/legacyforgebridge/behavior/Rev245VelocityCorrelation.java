package dev.yinghuang.legacyforgebridge.behavior;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Observation-only correlation for rev245 motion diagnostics.
 *
 * It never writes player state or packet contents. Exact links are only claimed when an
 * existing identifier proves them (legacy wire id or modern packet object identity).
 * The gap between Via output bytes and the later decoded packet object has no shared id in
 * the retained hooks, so matching that gap is explicitly labelled vector_time_candidate.
 */
public final class Rev245VelocityCorrelation {
    private static final int MAX_RECENT=512;
    private static final long MATCH_NS=750_000_000L;
    private static final long CONTEXT_NS=1_500_000_000L;
    private static final double EPS=1.0E-9;
    private static final AtomicLong IDS=new AtomicLong();
    private static final LinkedHashMap<Long,Flow> BY_WIRE=new LinkedHashMap<>();
    private static final LinkedHashMap<Integer,Flow> BY_PACKET=new LinkedHashMap<>();
    private static final ArrayDeque<Flow> RECENT_VIA=new ArrayDeque<>();
    private static long capture,captureStartNs,groundGeneration,lastPositiveNs,lastPositiveJump,lastPositiveGroundGeneration;
    private static long flowCount,exactWireLinks,exactPacketLinks,candidateLinks,unmatchedPacketStages,positiveApplies,repeatPositiveCandidates;
    private static long lastContextNs;
    private static Flow lastContext,lastPositive;

    private record Vec(double x,double y,double z) {
        boolean same(Vec v) { return v!=null&&Math.abs(x-v.x)<=EPS&&Math.abs(y-v.y)<=EPS&&Math.abs(z-v.z)<=EPS; }
        boolean positiveY() { return y>EPS; }
    }
    private static final class Flow {
        final long id,createdNs;
        long wire=-1,viaNs=-1,dispatchNs=-1,applyHeadNs=-1,applyTailNs=-1;
        int packetObject=Integer.MIN_VALUE;
        Vec vector;
        String viaBasis="";
        Flow(long id,long createdNs){this.id=id;this.createdNs=createdNs;}
    }
    private Rev245VelocityCorrelation() {}

    public static synchronized void captureStarted(long id,long startNs) {
        capture=id;captureStartNs=startNs;groundGeneration=0;
        BY_WIRE.clear();BY_PACKET.clear();RECENT_VIA.clear();
        flowCount=exactWireLinks=exactPacketLinks=candidateLinks=unmatchedPacketStages=positiveApplies=repeatPositiveCandidates=0;
        lastPositiveNs=lastPositiveJump=lastPositiveGroundGeneration=0;
        lastContextNs=0;lastContext=null;lastPositive=null;
    }

    public static synchronized String decorate(String stage,String detail,long ns,long localJump) {
        if(stage==null)return "";
        if(detail==null)detail="";
        switch(stage) {
            case "RAW_1710_MOTION": {
                long wire=longValue(detail,"wire=",-1);
                Vec v=vector(detail,"decoded=");
                Flow f=wire>=0?BY_WIRE.get(wire):null;
                if(f==null){f=newFlow(ns);f.wire=wire;f.vector=v;if(wire>=0)putWire(wire,f);}
                return tags(f,"wire_exact",ns)+vectorTag(v);
            }
            case "VIA_OUTPUT_MOTION": {
                long wire=longValue(detail,"wire=",-1);
                Vec v=vector(detail,"decoded=");
                Flow f=wire>=0?BY_WIRE.get(wire):null;
                String basis;
                if(f!=null){exactWireLinks++;basis="wire_exact";}
                else {f=newFlow(ns);f.wire=wire;basis="wire_missing_new_flow";if(wire>=0)putWire(wire,f);}
                f.vector=v;f.viaNs=ns;f.viaBasis=basis;RECENT_VIA.addLast(f);trimRecent(ns);
                return tags(f,basis,ns)+vectorTag(v);
            }
            case "VIA_OUTPUT_UNDECODED": {
                long wire=longValue(detail,"wire=",-1);Flow f=wire>=0?BY_WIRE.get(wire):null;
                return f==null?" corrUnmatched=true corrStage=via_undecoded":tags(f,"wire_exact",ns)+" corrStage=via_undecoded";
            }
            case "NETTY_PACKET_DISPATCH": {
                int packet=intValue(detail,"packetObject=",Integer.MIN_VALUE);Vec v=vector(detail,"decoded=");
                Flow f=packet==Integer.MIN_VALUE?null:BY_PACKET.get(packet);String basis;
                if(f!=null){exactPacketLinks++;basis="packet_object_exact";}
                else {f=matchVia(v,ns);if(f!=null){candidateLinks++;basis="vector_time_candidate";}else{f=newFlow(ns);f.vector=v;unmatchedPacketStages++;basis="unmatched_new_flow";}}
                f.packetObject=packet;f.dispatchNs=ns;if(f.vector==null)f.vector=v;if(packet!=Integer.MIN_VALUE)putPacket(packet,f);
                return tags(f,basis,ns)+delta("viaToDispatchUs",f.viaNs,ns)+vectorTag(v);
            }
            case "CLIENT_APPLY_HEAD": {
                int packet=intValue(detail,"packetObject=",Integer.MIN_VALUE);Vec incoming=vector(detail,"incoming=");
                Flow f=packet==Integer.MIN_VALUE?null:BY_PACKET.get(packet);String basis;
                if(f!=null){exactPacketLinks++;basis="packet_object_exact";}
                else {f=matchVia(incoming,ns);if(f!=null){candidateLinks++;basis="vector_time_candidate";}else{f=newFlow(ns);f.vector=incoming;unmatchedPacketStages++;basis="unmatched_new_flow";}if(packet!=Integer.MIN_VALUE){f.packetObject=packet;putPacket(packet,f);}}
                f.applyHeadNs=ns;if(f.vector==null)f.vector=incoming;
                String upward=upwardTags(f,incoming,detail,ns,localJump);
                lastContext=f;lastContextNs=ns;
                return tags(f,basis,ns)+delta("dispatchToApplyHeadUs",f.dispatchNs,ns)+vectorTag(incoming)+upward;
            }
            case "CLIENT_APPLY_TAIL", "UPWARD_REWRITE": {
                int packet=intValue(detail,"packetObject=",Integer.MIN_VALUE);Flow f=packet==Integer.MIN_VALUE?null:BY_PACKET.get(packet);
                String basis=f==null?"unmatched_packet_object":"packet_object_exact";
                if(f==null){unmatchedPacketStages++;} else {exactPacketLinks++;f.applyTailNs=ns;lastContext=f;lastContextNs=ns;}
                Vec applied=vector(detail,"applied=");
                return f==null?" corrUnmatched=true corrBasis="+basis+vectorTag(applied):tags(f,basis,ns)+delta("applyDurationUs",f.applyHeadNs,ns)+vectorTag(applied);
            }
            case "GROUND_CONTACT":
                groundGeneration++;
                lastPositiveNs=0;lastPositive=null;lastPositiveJump=0;lastPositiveGroundGeneration=groundGeneration;
                return context(ns)+" groundGeneration="+groundGeneration;
            case "LEAVE_GROUND":
                return context(ns)+" groundGeneration="+groundGeneration;
            case "VELOCITY_WRITE", "POSITION_WRITE", "TICK_START", "TICK_SNAPSHOT_END", "MOVE_HEAD", "MOVE_TAIL", "CAMERA_FRAME", "JUMP_BEFORE_SOURCE", "JUMP_AFTER_SOURCE", "SOURCE_HANDLER_HEAD", "SOURCE_HANDLER_TAIL", "SOURCE_HANDLER_THROW":
                return context(ns);
            default:
                return "";
        }
    }

    private static String upwardTags(Flow f,Vec incoming,String detail,long ns,long localJump) {
        if(incoming==null||!incoming.positiveY())return "";
        positiveApplies++;
        boolean ground=booleanValue(detail,"ground=",false);
        long delta=lastPositiveNs==0?-1:ns-lastPositiveNs;
        boolean sameJump=localJump!=0&&lastPositiveJump==localJump;
        boolean sameAir=lastPositiveNs!=0&&lastPositiveGroundGeneration==groundGeneration&&!ground;
        boolean repeat=(sameJump||sameAir)&&delta>=0&&delta<=2_000_000_000L;
        int ordinal=repeat?2:1;
        if(repeat)repeatPositiveCandidates++;
        String out=" upwardApply=true upwardOrdinal="+ordinal+" groundAtApplyHead="+ground+" groundGeneration="+groundGeneration;
        if(lastPositiveNs!=0)out+=" sincePrevPositiveUs="+(delta/1_000L)+" prevPositiveCorr="+lastPositive.id;
        if(repeat)out+=" secondLiftCandidate=true candidateBasis="+(sameJump?"sameLocalJump":"noGroundContact")+" causalProof=false";
        lastPositiveNs=ns;lastPositiveJump=localJump;lastPositiveGroundGeneration=groundGeneration;lastPositive=f;
        return out;
    }

    private static String context(long ns) {
        Flow f=lastContext;
        if(f==null||lastContextNs==0||ns-lastContextNs<0||ns-lastContextNs>CONTEXT_NS)return "";
        return " afterVelocityCorr="+f.id+" sinceVelocityApplyUs="+((ns-lastContextNs)/1_000L)+" contextBasis=temporal_after_apply";
    }
    private static String tags(Flow f,String basis,long ns) {
        return " corr="+f.id+" corrBasis="+basis+" corrAgeUs="+((ns-f.createdNs)/1_000L)+(f.wire>=0?" corrWire="+f.wire:"")+(f.packetObject!=Integer.MIN_VALUE?" corrPacketObject="+f.packetObject:"");
    }
    private static String vectorTag(Vec v){return v==null?"":" corrVector="+v.x+","+v.y+","+v.z;}
    private static String delta(String name,long from,long to){return from<0?"":" "+name+"="+((to-from)/1_000L);}

    private static Flow newFlow(long ns){flowCount++;return new Flow(IDS.incrementAndGet(),ns);}

    private static Flow matchVia(Vec v,long ns) {
        if(v==null)return null;trimRecent(ns);
        Iterator<Flow> it=RECENT_VIA.descendingIterator();
        while(it.hasNext()){
            Flow f=it.next();
            if(f.viaNs>=0&&ns-f.viaNs>=0&&ns-f.viaNs<=MATCH_NS&&f.packetObject==Integer.MIN_VALUE&&v.same(f.vector))return f;
        }
        return null;
    }
    private static void trimRecent(long ns){
        while(!RECENT_VIA.isEmpty()&&(RECENT_VIA.size()>MAX_RECENT||ns-RECENT_VIA.peekFirst().viaNs>MATCH_NS))RECENT_VIA.removeFirst();
    }
    private static void putWire(long k,Flow f){BY_WIRE.put(k,f);trim(BY_WIRE);}
    private static void putPacket(int k,Flow f){BY_PACKET.put(k,f);trim(BY_PACKET);}
    private static <K> void trim(LinkedHashMap<K,Flow> map){while(map.size()>MAX_RECENT){Iterator<K> i=map.keySet().iterator();i.next();i.remove();}}

    private static Vec vector(String s,String key) {
        int p=s.indexOf(key);if(p<0)return null;p+=key.length();
        if(p>=s.length()||s.charAt(p)!='[')return null;int e=s.indexOf(']',p+1);if(e<0)return null;
        String[] a=s.substring(p+1,e).split(",",-1);if(a.length!=3)return null;
        try{return new Vec(Double.parseDouble(a[0]),Double.parseDouble(a[1]),Double.parseDouble(a[2]));}catch(NumberFormatException ex){return null;}
    }
    private static long longValue(String s,String key,long fallback){String t=token(s,key);try{return t==null?fallback:Long.parseLong(t);}catch(NumberFormatException ex){return fallback;}}
    private static int intValue(String s,String key,int fallback){String t=token(s,key);try{return t==null?fallback:Integer.parseInt(t);}catch(NumberFormatException ex){return fallback;}}
    private static boolean booleanValue(String s,String key,boolean fallback){String t=token(s,key);return t==null?fallback:Boolean.parseBoolean(t);}
    private static String token(String s,String key){int p=s.indexOf(key);if(p<0)return null;p+=key.length();int e=p;while(e<s.length()&&!Character.isWhitespace(s.charAt(e)))e++;return s.substring(p,e);}

    public static synchronized String status(){return "corrCapture="+capture+" corrFlows="+flowCount+" exactWireLinks="+exactWireLinks+" exactPacketLinks="+exactPacketLinks+" candidateLinks="+candidateLinks+" positiveApplies="+positiveApplies+" secondLiftCandidates="+repeatPositiveCandidates;}
    public static synchronized String summary(){return status()+" unmatchedPacketStages="+unmatchedPacketStages+" correlationDoesNotProveServerCause=true";}

    // package-private test accessors; harmless in the production jar and never mutate game state.
    static synchronized void resetIdsForTest(){IDS.set(0);}
}
