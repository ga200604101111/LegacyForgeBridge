package dev.yinghuang.legacyforgebridge.convert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyIconTableAnalyzerTest {
 @TempDir Path temp;
 private List<LegacyIconTableAnalyzer.Result> analyze(Map<String,String> changes)throws Exception {
  Path jar=LegacyIconFixtures.create(temp,changes);
  return new LegacyIconTableAnalyzer().analyze(jar,new LegacyRegistryAnalyzer().analyze(jar).registrations());
 }
 private static LegacyIconTableAnalyzer.Result named(List<LegacyIconTableAnalyzer.Result> list,String name){return list.stream().filter(r->r.registryName().equals(name)).findFirst().orElseThrow();}
 @Test void usesSourceStringsNotRegistryNamesAndPreservesMetadata()throws Exception {
  var result=named(analyze(Map.of()),"unknownFoodName");
  assertEquals(256,result.variants().size());assertEquals("source:food0",result.variants().get(0).faceIcons().getFirst());
  assertEquals("source:food1",result.variants().get(255).faceIcons().getFirst());
 }
 @Test void fluentVarargsAndBooleanConstructorsDistinguishHalfBlocks()throws Exception {
  var all=analyze(Map.of());var solid=named(all,"solid");var half=named(all,"half");
  assertEquals(16,half.variants().size());assertEquals(1d,solid.variants().getFirst().bounds().get(4));
  assertEquals(0.5d,half.variants().getFirst().bounds().get(4));assertEquals("source:tile",half.variants().get(1).faceIcons().getFirst());
 }
 @Test void upperWorldSlabAndInventoryLowerSlabAreSeparate()throws Exception {
  var result=named(analyze(Map.of()),"half").variants().get(8);
  assertEquals(0.5d,result.bounds().get(1));assertEquals(1d,result.bounds().get(4));
  assertEquals(0d,result.inventoryBounds().get(1));assertEquals(0.5d,result.inventoryBounds().get(4));
 }
 @Test void rejectsStatefulIconGetters()throws Exception {
  var result=named(analyze(Map.of("fixture/PaletteItem.java","""
   package fixture; import net.minecraft.item.Item;import net.minecraft.util.IIcon;import net.minecraft.client.renderer.texture.IIconRegister;
   public class PaletteItem extends Item {int counter; public void registerIcons(IIconRegister r){itemIcon=r.registerIcon("source:food0");} public IIcon getIconFromDamage(int m){counter=m;return itemIcon;}}
   """)),"unknownFoodName");
  assertTrue(result.variants().isEmpty());assertTrue(result.limitation().contains("stateful"));
 }
 @Test void neverExecutesSourceOrInventsCustomGeometry()throws Exception {
  var all=analyze(Map.of("fixture/PaletteItem.java","""
   package fixture; import net.minecraft.item.Item;import net.minecraft.util.IIcon;import net.minecraft.client.renderer.texture.IIconRegister;
   public class PaletteItem extends Item {public void registerIcons(IIconRegister r){System.setProperty("lfb.source.executed","yes");itemIcon=r.registerIcon("source:food0");}}
   """));
  assertTrue(named(all,"unknownFoodName").variants().isEmpty());assertNull(System.getProperty("lfb.source.executed"));
 }
 @Test void rejectsCustomRenderTypeInsteadOfGeneratingACube()throws Exception {
  var all=analyze(Map.of("fixture/PaletteBlock.java","""
   package fixture; import net.minecraft.block.Block; import net.minecraft.util.IIcon; import net.minecraft.client.renderer.texture.IIconRegister;
   public class PaletteBlock extends Block {public PaletteBlock(boolean half){}public PaletteBlock textures(String... names){return this;}public void registerBlockIcons(IIconRegister r){blockIcon=r.registerIcon("source:wood");}public int getRenderType(){return 123;}}
   """));
  assertTrue(named(all,"half").variants().isEmpty());assertTrue(named(all,"half").limitation().contains("renderer"));
 }
 @Test void rejectsAmbiguousRepeatedIdentityAllocations()throws Exception {
  var all=analyze(Map.of("fixture/Content.java","""
   package fixture;import cpw.mods.fml.common.registry.GameRegistry;public class Content {static{GameRegistry.registerBlock(new PaletteBlock(false).textures("source:wood","source:tile"),"same");GameRegistry.registerBlock(new PaletteBlock(true).textures("source:wood","source:tile"),"same");}}
   """));
  assertFalse(all.isEmpty());for(var r:all){assertTrue(r.variants().isEmpty());assertTrue(r.limitation().contains("ambiguous"));}
 }
 @Test void allowsLogicOnlyBlockContainersButRejectsActualTesrGeometry()throws Exception {
  Map<String,String> base=new LinkedHashMap<>();
  base.put("net/minecraft/block/BlockContainer.java","""
   package net.minecraft.block; public abstract class BlockContainer extends Block {
    public BlockContainer(){super();} public abstract net.minecraft.tileentity.TileEntity createNewTileEntity(net.minecraft.world.World w,int meta);
   }
   """);
  base.put("net/minecraft/tileentity/TileEntity.java","package net.minecraft.tileentity; public class TileEntity {}");
  base.put("net/minecraft/world/World.java","package net.minecraft.world; public class World {}");
  base.put("net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer.java",
   "package net.minecraft.client.renderer.tileentity; public class TileEntitySpecialRenderer {}");
  base.put("cpw/mods/fml/client/registry/ClientRegistry.java","""
   package cpw.mods.fml.client.registry; public class ClientRegistry {
    public static void registerTileEntity(Class c,String id,net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer r){}
   }
   """);
  base.put("fixture/LogicTile.java","package fixture; public class LogicTile extends net.minecraft.tileentity.TileEntity {}");
  base.put("fixture/LogicContainer.java","""
   package fixture; public class LogicContainer extends net.minecraft.block.BlockContainer {
    public LogicContainer(){super();} public net.minecraft.tileentity.TileEntity createNewTileEntity(net.minecraft.world.World w,int m){return new LogicTile();}
    public void registerBlockIcons(net.minecraft.client.renderer.texture.IIconRegister r){blockIcon=r.registerIcon("source:logic");}
   }
   """);
  base.put("fixture/TesrTile.java","package fixture; public class TesrTile extends net.minecraft.tileentity.TileEntity {}");
  base.put("fixture/TesrContainer.java","""
   package fixture; public class TesrContainer extends net.minecraft.block.BlockContainer {
    public TesrContainer(){super();} public net.minecraft.tileentity.TileEntity createNewTileEntity(net.minecraft.world.World w,int m){return new TesrTile();}
    public void registerBlockIcons(net.minecraft.client.renderer.texture.IIconRegister r){blockIcon=r.registerIcon("source:tesr");}
   }
   """);
  base.put("fixture/Tesr.java","package fixture; public class Tesr extends net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer {}");
  base.put("fixture/TesrBinding.java","""
   package fixture; public class TesrBinding { static {
    cpw.mods.fml.client.registry.ClientRegistry.registerTileEntity(TesrTile.class,"tesr",new Tesr());
   }}
   """);
  base.put("fixture/Content.java","""
   package fixture; import cpw.mods.fml.common.registry.GameRegistry; public class Content {static {
    GameRegistry.registerBlock(new LogicContainer(),"logicContainer");
    GameRegistry.registerBlock(new TesrContainer(),"tesrContainer");
   }}
   """);
  var all=analyze(base);
  var logic=named(all,"logicContainer");assertEquals(16,logic.variants().size(),logic.limitation());
  assertEquals("source:logic",logic.variants().getFirst().faceIcons().getFirst());
  var tesr=named(all,"tesrContainer");assertTrue(tesr.variants().isEmpty());
  assertTrue(tesr.limitation().contains("BlockEntity renderer"),tesr.limitation());
 }

}
