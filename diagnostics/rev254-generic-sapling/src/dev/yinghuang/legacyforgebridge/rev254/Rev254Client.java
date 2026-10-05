package dev.yinghuang.legacyforgebridge.rev254;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.LoggerFactory;
public final class Rev254Client implements ClientModInitializer {
 @Override public void onInitializeClient(){
  LoggerFactory.getLogger("LFB-rev254").info("rev254 generic Legacy Sapling + ordered Fast S12 candidate: fastS12="+Rev254Config.FAST_S12+", bambooLeafSound="+Rev254Config.BAMBOO_SOUND+", bambooPetals="+Rev254Config.BAMBOO_PETALS+"; normal remote velocity RESTORED");
  BambooSupport.discover();LegacySaplingSupport.discover();LegacySaplingSupport.installClientPresentation();
 }
}
