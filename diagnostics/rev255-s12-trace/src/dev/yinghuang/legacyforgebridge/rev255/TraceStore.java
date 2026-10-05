package dev.yinghuang.legacyforgebridge.rev255;

import java.util.*;

/** Bounded evidence store. Times are local monotonic times, never server age or network RTT. */
public final class TraceStore {
    public static final int LIMIT = 1024;
    public record Stamp(long epoch, long sequence, long enqueueNs, long tick, boolean fast, long modeEpoch) { }
    public record Packet(long sequence, long atNs, boolean fastAtEnqueue, boolean fastAtApply,
                         boolean mixedMode, boolean early, boolean success, long queueNs,
                         long handlerNs, long tickDelta, int fps, long frameNs,
                         double y, double beforeYVelocity, double afterYVelocity,
                         double requestedYVelocity, boolean groundBefore) { }
    public record Via(long atNs, boolean fast, boolean success, long durationNs) { }
    public record Snapshot(long epoch, boolean recording, long enqueued, long applied, long early,
                           long failed, long unstamped, long stale, long overwritten, long duplicate,
                           long modeEpoch, List<Packet> packets, List<Via> via) { }
    private final Packet[] packets = new Packet[LIMIT];
    private final Via[] via = new Via[LIMIT];
    private int packetHead, packetSize, viaHead, viaSize;
    private long epoch=1, sequence, enqueued, applied, early, failed, unstamped, stale, overwritten, duplicate, modeEpoch;
    private boolean recording=true;
    public synchronized boolean recording(){return recording;}
    public synchronized long epoch(){return epoch;}
    public synchronized long modeEpoch(){return modeEpoch;}
    public synchronized void modeChanged(){modeEpoch++;}
    public synchronized void capture(boolean value){recording=value;epoch++;}
    public synchronized void reset(){
        epoch++;sequence=enqueued=applied=early=failed=unstamped=stale=overwritten=duplicate=0;
        packetHead=packetSize=viaHead=viaSize=0;Arrays.fill(packets,null);Arrays.fill(via,null);
    }
    public synchronized Stamp enqueue(long now,long tick,boolean fast){
        if(!recording)return null;enqueued++;
        return new Stamp(epoch,++sequence,now,tick,fast,modeEpoch);
    }
    public synchronized void duplicate(){duplicate++;}
    public synchronized void packet(long eventEpoch, Stamp stamp,long start,long end,long tick,boolean fast,
                                     boolean isEarly,boolean success,int fps,long frame,double y,
                                     double before,double after,double requested,boolean ground){
        if(!recording||epoch!=eventEpoch){stale++;return;}
        if(stamp!=null&&stamp.epoch()!=epoch){stale++;return;}
        long wait=stamp==null?-1:Math.max(0,start-stamp.enqueueNs());
        long dt=stamp==null?-1:Math.max(0,tick-stamp.tick());
        if(stamp==null)unstamped++;
        if(success){applied++;if(isEarly)early++;}else failed++;
        Packet p=new Packet(stamp==null?++sequence:stamp.sequence(),end,
                stamp==null?fast:stamp.fast(),fast,stamp!=null&&stamp.modeEpoch()!=modeEpoch,
                isEarly,success,wait,Math.max(0,end-start),dt,fps,frame,y,before,after,requested,ground);
        if(packetSize==LIMIT)overwritten++;else packetSize++;
        packets[packetHead]=p;packetHead=(packetHead+1)%LIMIT;
    }
    public synchronized void via(long eventEpoch,long end,boolean fast,boolean success,long duration){
        if(!recording||eventEpoch!=epoch)return;
        via[viaHead]=new Via(end,fast,success,Math.max(0,duration));
        viaHead=(viaHead+1)%LIMIT;if(viaSize<LIMIT)viaSize++;
    }
    public synchronized Snapshot snapshot(){
        List<Packet> p=new ArrayList<>(packetSize);for(int i=0;i<packetSize;i++)p.add(packets[(packetHead-packetSize+i+LIMIT)%LIMIT]);
        List<Via> v=new ArrayList<>(viaSize);for(int i=0;i<viaSize;i++)v.add(via[(viaHead-viaSize+i+LIMIT)%LIMIT]);
        return new Snapshot(epoch,recording,enqueued,applied,early,failed,unstamped,stale,overwritten,duplicate,modeEpoch,List.copyOf(p),List.copyOf(v));
    }
    public static String ms(long ns){return ns<0?"N/A":String.format(Locale.ROOT,"%.3f",ns/1_000_000D);}
    public static String distribution(long[] input){
        if(input.length==0)return "N/A（0 筆）";
        long[] a=input.clone();Arrays.sort(a);double mean=Arrays.stream(a).average().orElse(0)/1e6;
        return String.format(Locale.ROOT,"n=%d 平均 %.3f / P50 %s / P95 %s / 最大 %s ms",a.length,mean,
                ms(a[(int)Math.ceil(.5*a.length)-1]),ms(a[(int)Math.ceil(.95*a.length)-1]),ms(a[a.length-1]));
    }
    public static long[] waits(Snapshot s,boolean fast){
        return s.packets().stream().filter(p->p.success()&&!p.mixedMode()&&p.fastAtApply()==fast&&p.queueNs()>=0).mapToLong(Packet::queueNs).toArray();
    }
}
