package dev.yinghuang.legacyforgebridge.rev254;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import dev.yinghuang.legacyforgebridge.behavior.Rev242LandingBridge;
/** Keep rev242 gameplay landing fallback and reset barriers without rev243+ trace machinery. */
public final class Rev254Lifecycle {
 private static boolean initialized;
 public static synchronized void initialize(){
  if(initialized)return;initialized=true;
  ClientTickEvents.END_CLIENT_TICK.register(client->Rev242LandingBridge.tick(client));
 }
 public static void barrier(String kind){Rev242LandingBridge.barrier();}
 private Rev254Lifecycle(){}
}
