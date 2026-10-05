package dev.yinghuang.legacyforgebridge.rev256;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.minecraft.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import org.slf4j.LoggerFactory;
/** Cold start: registered after main content; never mutates the remote server. */
public final class SaplingClient implements ClientModInitializer {
 private static boolean builtin;
 @Override public void onInitializeClient(){
  SaplingRegistry.install();
  var bamboo=SaplingRegistry.rule(class_2960.method_60654("bamboomod:sakura"));
  if(bamboo!=null && dev.yinghuang.legacyforgebridge.rev254.BambooRules.admitSapling(bamboo.hash(),bamboo.source())){
   var self=FabricLoader.getInstance().getModContainer("legacyforgebridge").orElseThrow();
   builtin=ResourceLoader.registerBuiltinPack(class_2960.method_60654("legacyforgebridge:verified_sakura"),self,PackActivationType.ALWAYS_ENABLED);
   LoggerFactory.getLogger("LFB-rev256").info("Bamboo verified-source repair pack registered="+builtin);
  }
  ClientCommandRegistrationCallback.EVENT.register((dispatcher,registry)->dispatcher.register(
   literal("lfbtrace").then(literal("sapling").executes(c->inspect(c.getSource())))));
 }
 private static int say(FabricClientCommandSource s,String t){s.sendFeedback(class_2561.method_43470(t));return 1;}
 private static int inspect(FabricClientCommandSource s){
  var mc=s.getClient();say(s,"樹苗規則="+SaplingRegistry.size()+"；Bamboo 內建修復包="+builtin);
  if(mc.field_1687==null||!(mc.field_1765 instanceof class_3965 hit))return say(s,"請瞄準櫻樹苗再執行 /lfbtrace sapling。");
  var pos=hit.method_17777();var state=mc.field_1687.method_8320(pos);var block=state.method_26204();
  var id=class_7923.field_41175.method_10221(block);var r=SaplingRegistry.rule(id);
  say(s,"目標="+id+"；實際載體="+block.getClass().getName());
  say(s,"實際碰撞為空="+state.method_26220(mc.field_1687,pos).method_1110()+"；實際選取框="+state.method_26218(mc.field_1687,pos));
  if(r==null)return say(s,"沒有可驗證的樹苗規則。此結果不是已修復；需核對轉換包的 proof/來源雜湊。");
  return say(s,"規則來源="+r.origin()+"；CUTOUT已安裝="+SaplingRegistry.layerInstalled(id.toString())+"；選取框="+r.proof().selection());
 }
}
