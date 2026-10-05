package dev.yinghuang.legacyforgebridge.rev254;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.io.*;
import java.util.Properties;
import org.slf4j.LoggerFactory;
/** Independent switches; no user file is overwritten. Read once, restart required. */
public final class Rev254Config {
 public static final boolean FAST_S12, BAMBOO_SOUND, BAMBOO_PETALS;
 static {
  Properties p=new Properties();
  try {
   Path dir=FabricLoader.getInstance().getConfigDir();
   Path f=dir.resolve("legacyforgebridge-rev254.properties"), prior=dir.resolve("legacyforgebridge-rev253.properties");
   Path read=Files.isRegularFile(f)?f:Files.isRegularFile(prior)?prior:null;
   if(read!=null)try(Reader r=Files.newBufferedReader(read)){p.load(r);}
   else {Files.createDirectories(f.getParent());try {Files.writeString(f,"# LFB rev254 test switches; restart Minecraft after editing.\nfastS12=true\nbambooLeafSound=true\nbambooPetals=true\n",StandardOpenOption.CREATE_NEW);}catch(FileAlreadyExistsException ignored){}}
  }catch(IOException e){LoggerFactory.getLogger("LFB-rev254").warn("Could not read/write rev254 settings; using defaults",e);}
  FAST_S12=flag(p,"fastS12");BAMBOO_SOUND=flag(p,"bambooLeafSound");BAMBOO_PETALS=flag(p,"bambooPetals");
 }
 private static boolean flag(Properties p,String k){return Boolean.parseBoolean(System.getProperty("lfb.rev254."+k,p.getProperty(k,"true")));}
 private Rev254Config(){}
}
