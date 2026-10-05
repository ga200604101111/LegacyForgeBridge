package dev.yinghuang.legacyforgebridge.rev255;

import net.minecraft.*;
import dev.yinghuang.legacyforgebridge.rev254.FastS12;
import dev.yinghuang.legacyforgebridge.rev254.Rev254Config;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import io.netty.buffer.ByteBuf;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.State;

/** Lightweight local-player measurements. No velocity/position/packet writes or packet cancellation. */
public final class S12Trace {
    public static final TraceStore STORE=new TraceStore();
    private static volatile boolean fast=Rev254Config.FAST_S12;
    private static volatile boolean watching;
    private record Context(Object listener,Object world,Object player,Object via,int id,boolean eligible) { }
    private static volatile Context context;
    private static volatile long tick,frameNs=-1;
    private static long lastFrameNs,watchNs;
    private static final AtomicLong errors=new AtomicLong(),viaHookCalls=new AtomicLong(),queueHookCalls=new AtomicLong(),applyHookCalls=new AtomicLong();
    public record Apply(long epoch, TraceStore.Stamp stamp,long start,long tick,boolean fast,boolean early,
                        int fps,long frame,double y,double before,double requested,boolean ground,Object player) { }
    public record ViaStamp(long epoch,long start,boolean fast) { }
    public static boolean fastEnabled(){return fast;}
    public static void setFast(boolean value){if(fast!=value){fast=value;STORE.modeChanged();}}
    public static boolean watching(){return watching;}
    public static void watch(boolean enabled){watching=enabled;watchNs=0;}
    public static void observerError(){errors.incrementAndGet();}
    public static void context(class_310 mc,Object via,boolean eligible){
        Context old=context;Object h=mc.method_1562(),w=mc.field_1687,p=mc.field_1724;
        if(h==null||w==null||p==null){context=null;return;}
        int id=mc.field_1724.method_5628();
        if(old==null||old.listener()!=h||old.world()!=w||old.player()!=p||old.id()!=id){STORE.reset();lastFrameNs=0;frameNs=-1;}
        context=new Context(h,w,p,via,id,eligible);
    }
    public static void tick(){tick++;}
    public static void frame(class_310 mc){
        long now=System.nanoTime();if(lastFrameNs!=0)frameNs=now-lastFrameNs;lastFrameNs=now;
        if(watching&&mc.field_1724!=null&&now-watchNs>=1_000_000_000L){
            watchNs=now;mc.field_1724.method_7353(class_2561.method_43470(shortStatus(mc)),true);
        }
    }
    public static void enqueue(Object listener,class_2596<?> packet){
        queueHookCalls.incrementAndGet();
        if(!(packet instanceof class_2743 v)||!(packet instanceof S12PacketAccess access))return;
        Context c=context;if(c==null||!c.eligible()||c.listener()!=listener||c.id()!=v.method_11818())return;
        if(access.rev255$getStamp()!=null){STORE.duplicate();return;}
        access.rev255$setStamp(STORE.enqueue(System.nanoTime(),tick,fast));
    }
    public static Apply beginApply(Object listener,class_2743 packet){
        class_310 mc=class_310.method_1551();if(!mc.method_18854())return null;
        applyHookCalls.incrementAndGet();Context c=context;
        if(c==null||!c.eligible()||!STORE.recording()||c.listener()!=listener||c.id()!=packet.method_11818()
                ||mc.field_1724!=c.player()||mc.field_1687!=c.world())return null;
        TraceStore.Stamp stamp=packet instanceof S12PacketAccess a?a.rev255$getStamp():null;
        return new Apply(STORE.epoch(),stamp,System.nanoTime(),tick,fast,FastS12.isDraining(),mc.method_47599(),frameNs,
                mc.field_1724.method_23318(),mc.field_1724.method_18798().field_1351,
                packet.method_73085().field_1351,mc.field_1724.method_24828(),c.player());
    }
    public static void finishApply(Apply a,boolean success){
        if(a==null)return;long end=System.nanoTime();class_310 mc=class_310.method_1551();
        double after=mc.field_1724==a.player()?mc.field_1724.method_18798().field_1351:Double.NaN;
        STORE.packet(a.epoch(),a.stamp(),a.start(),end,a.tick(),a.fast(),a.early(),success,a.fps(),a.frame(),a.y(),a.before(),after,a.requested(),a.ground());
    }
    public static ViaStamp beginVia(Object user,ByteBuf b){
        viaHookCalls.incrementAndGet();Context c=context;
        if(c==null||!c.eligible()||c.via()!=user||!STORE.recording()||b.readableBytes()!=11)return null;
        int i=b.readerIndex();
        if(b.getUnsignedByte(i)!=0x12||b.getInt(i+1)!=c.id())return null;
        if(!(user instanceof UserConnection u)||u.getProtocolInfo().getServerState()!=State.PLAY)return null;
        return new ViaStamp(STORE.epoch(),System.nanoTime(),fast);
    }
    public static void finishVia(ViaStamp s,boolean success){if(s!=null){long end=System.nanoTime();STORE.via(s.epoch(),end,s.fast(),success,end-s.start());}}
    public static String shortStatus(class_310 mc){
        TraceStore.Snapshot s=STORE.snapshot();TraceStore.Packet p=s.packets().isEmpty()?null:s.packets().getLast();
        String last=p==null?"N/A":TraceStore.ms(p.queueNs())+" ms";
        return "LFB | FPS "+mc.method_47599()+" | Fast S12 "+(fast?"ON":"OFF")+" | 等待 "+last+" | 提前 "+s.early()+"/"+s.applied()+(!s.recording()?" | 已停錄":"");
    }
    public static List<String> status(class_310 mc){
        TraceStore.Snapshot s=STORE.snapshot();Context c=context;List<String> out=new ArrayList<>();
        out.add("[LFB rev255] Fast S12="+(fast?"ON":"OFF")+"；有效連線="+(c!=null&&c.eligible()?"1.7.10 / protocol 5":"未就緒或非 1.7.10")+"；量測="+(s.recording()?"ON":"OFF"));
        out.add("目前 FPS="+mc.method_47599()+"（遊戲計數）｜最近幀間隔="+TraceStore.ms(frameNs)+" ms");
        out.add("本輪本地 S12：入列="+s.enqueued()+"，成功處理="+s.applied()+"，其中提前處理="+s.early()+"，例外="+s.failed());
        out.add("Fast ON 等待："+TraceStore.distribution(TraceStore.waits(s,true)));
        out.add("Fast OFF 等待："+TraceStore.distribution(TraceStore.waits(s,false)));
        long[] via=s.via().stream().filter(TraceStore.Via::success).mapToLong(TraceStore.Via::durationNs).toArray();
        out.add("Via S12 轉換："+TraceStore.distribution(via)+"（獨立樣本，不和單筆套用強配對）");
        if(!s.packets().isEmpty()){
            TraceStore.Packet p=s.packets().getLast();
            out.add("最近 S12 #"+p.sequence()+"：入列入口→套用="+TraceStore.ms(p.queueNs())+" ms；處理耗時="+TraceStore.ms(p.handlerNs())+" ms；跨 client tick="+(p.tickDelta()<0?"N/A":p.tickDelta()));
            out.add(String.format(Locale.ROOT,"當時 FPS=%d；幀間隔=%s ms；Y速度 %.5f → %.5f（Δ=%+.5f）；封包Y=%.5f；套用前接地=%s；提前=%s%s",p.fps(),TraceStore.ms(p.frameNs()),p.beforeYVelocity(),p.afterYVelocity(),p.afterYVelocity()-p.beforeYVelocity(),p.requestedYVelocity(),p.groundBefore(),p.early(),p.mixedMode()?"；跨ON/OFF切換（不列入A/B分布）":""));
        }
        out.add("樣本窗最多 "+TraceStore.LIMIT+" 筆；缺入列時戳="+s.unstamped()+"；舊epoch略過="+s.stale()+"；覆寫="+s.overwritten()+"；重複入列="+s.duplicate()+"；觀測錯誤="+errors.get());
        out.add("掛鉤呼叫：queue="+queueHookCalls.get()+"，apply(main)="+applyHookCalls.get()+"，Via="+viaHookCalls.get()+"。計數本身不代表成功減少延遲。");
        out.add("以上均為客戶端內部耗時，非 ping／伺服器送出到抵達時間；沒有量到的值顯示 N/A。");
        return out;
    }
    private S12Trace(){}
}
