package dev.yinghuang.legacyforgebridge.compat;
import com.viaversion.nbt.tag.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyItemCarrierStateTest {
 @Test void subtypeSurvivesWithoutADurabilityComponent(){
  var marker=LegacyItemCarrierState.capture(new CompoundTag(),"example:decoration",165,13);
  assertTrue(LegacyItemCarrierState.matches(marker,"example:decoration",165));
  assertEquals(13,LegacyItemCarrierState.metadata(marker));
 }
 @Test void exactSourceMarkerAndDamageTagsAreRestored(){
  CompoundTag root=new CompoundTag();root.putString(LegacyItemCarrierState.KEY,"source-owned");root.putShort("Damage",(short)7);root.putString("other","keep");
  var marker=LegacyItemCarrierState.capture(root,"example:item",180,9);root.put(LegacyItemCarrierState.KEY,marker);root.putInt("Damage",0);
  LegacyItemCarrierState.restoreSourceTag(root,marker);
  assertEquals("source-owned",root.getString(LegacyItemCarrierState.KEY));assertEquals(new ShortTag((short)7),root.get("Damage"));assertEquals("keep",root.getString("other"));
 }
 @Test void generatedDamageAndBookkeepingDoNotReachTheServer(){
  CompoundTag root=new CompoundTag();var marker=LegacyItemCarrierState.capture(root,"example:item",190,15);root.put(LegacyItemCarrierState.KEY,marker);root.putInt("Damage",15);
  LegacyItemCarrierState.restoreSourceTag(root,marker);assertTrue(root.isEmpty());
 }
 @Test void forgedOrStaleSessionIdentityIsNotAccepted(){
  var marker=LegacyItemCarrierState.capture(null,"example:item",165,65535);
  assertFalse(LegacyItemCarrierState.matches(marker,"example:item",166));assertFalse(LegacyItemCarrierState.matches(marker,"example:other",165));
  assertEquals(65535,LegacyItemCarrierState.metadata(marker));marker.putInt("legacy_data",65536);assertFalse(LegacyItemCarrierState.matches(marker,"example:item",165));
 }
 @Test void snapshotDoesNotAliasSourceNbt(){
  CompoundTag root=new CompoundTag(),original=new CompoundTag();original.putString("x","old");root.put(LegacyItemCarrierState.KEY,original);
  var marker=LegacyItemCarrierState.capture(root,"example:item",165,2);original.putString("x","changed");
  root.put(LegacyItemCarrierState.KEY,marker);LegacyItemCarrierState.restoreSourceTag(root,marker);
  assertEquals("old",root.getCompoundTag(LegacyItemCarrierState.KEY).getString("x"));
 }
 @Test void outOfRangeIdentityFailsBeforeWriting(){
  assertThrows(IllegalArgumentException.class,()->LegacyItemCarrierState.capture(null,"example:item",32768,0));
  assertThrows(IllegalArgumentException.class,()->LegacyItemCarrierState.capture(null,"example:item",165,-1));
 }
}
