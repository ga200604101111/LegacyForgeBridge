package dev.yinghuang.legacyforgebridge.rev255;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import org.slf4j.LoggerFactory;
/** This light /lfbtrace replaces the disabled rev251 heavy auto-trace command backend. */
public final class TraceClient implements ClientModInitializer {
 @Override public void onInitializeClient(){
  ClientCommandRegistrationCallback.EVENT.register((dispatcher,registryAccess)->dispatcher.register(
   literal("lfbtrace").executes(c->status(c.getSource()))
   .then(literal("status").executes(c->status(c.getSource())))
   .then(literal("fps").executes(c->reply(c.getSource(),S12Trace.shortStatus(c.getSource().getClient()))))
   .then(literal("start").executes(c->{S12Trace.STORE.capture(true);return reply(c.getSource(),"S12 輕量量測已開啟；/lfbtrace status 查看，/lfbtrace export 匯出。Fast S12 開關未變。");}))
   .then(literal("stop").executes(c->{S12Trace.STORE.capture(false);S12Trace.watch(false);return reply(c.getSource(),"S12 量測已停止，既有樣本保留。Fast S12 開關未變；/lfbtrace export 可匯出。");}))
   .then(literal("reset").executes(c->{S12Trace.STORE.reset();return reply(c.getSource(),"已清空 S12 統計；未清除遊戲封包佇列，Fast S12 開關未變。");}))
   .then(literal("export").executes(c->{boolean started=TraceExport.save(S12Trace.STORE.snapshot(),S12Trace.status(c.getSource().getClient()));return reply(c.getSource(),started?"已提交匯出快照至 logs/lfb-s12；完成路徑見 /lfbtrace status 或 latest.log。":"已有一份快照正在匯出，未重複建立工作。");}))
   .then(literal("watch").executes(c->{S12Trace.watch(!S12Trace.watching());return reply(c.getSource(),"每秒狀態列="+(S12Trace.watching()?"ON":"OFF"));})
    .then(literal("on").executes(c->{S12Trace.watch(true);return reply(c.getSource(),"S12／FPS 狀態列 ON（每秒更新）。");}))
    .then(literal("off").executes(c->{S12Trace.watch(false);return reply(c.getSource(),"S12／FPS 狀態列 OFF。");})))
   .then(literal("fasts12")
    .then(literal("on").executes(c->fast(c.getSource(),true)))
    .then(literal("off").executes(c->fast(c.getSource(),false)))
    .then(literal("status").executes(c->status(c.getSource()))))
   .then(literal("help").executes(c->reply(c.getSource(),"/lfbtrace [status|fps|start|stop|reset|export|watch on/off|fasts12 on/off]。量測預設開啟；只存最近1024筆。Fast 切換僅本次遊戲有效。")))));
  LoggerFactory.getLogger("LFB-rev255").info("/lfbtrace ready: S12 queue/handler/Via timing + FPS + runtime Fast ON/OFF; ring buffers only; no heavy motion trace");
 }
 private static int reply(FabricClientCommandSource s,String text){s.sendFeedback(class_2561.method_43470(text));return 1;}
 private static int status(FabricClientCommandSource s){for(String line:S12Trace.status(s.getClient()))reply(s,line);return reply(s,"匯出："+TraceExport.status());}
 private static int fast(FabricClientCommandSource s,boolean enabled){S12Trace.setFast(enabled);return reply(s,"Fast S12="+(enabled?"ON":"OFF")+"（即時、本次遊戲有效）。正常S12仍保留；ON/OFF統計分開保留。切換中的封包不列入A/B分布。");}
}
