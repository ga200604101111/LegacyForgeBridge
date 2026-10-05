package dev.yinghuang.legacyforgebridge.rev254;
import net.minecraft.*;
import dev.yinghuang.legacyforgebridge.compat.LegacyBlockGeometryRegistry;
/** Ordered composition of existing geometry, source-bound Bamboo sound, and generic sapling properties. */
public final class BlockSupport {
 public static class_4970.class_2251 properties(class_2960 id,class_4970.class_2251 p){
  class_4970.class_2251 out=LegacyBlockGeometryRegistry.properties(id,p);
  out=BambooSupport.soundProperties(id,out);
  return LegacySaplingSupport.properties(id,out);
 }
 private BlockSupport(){}
}