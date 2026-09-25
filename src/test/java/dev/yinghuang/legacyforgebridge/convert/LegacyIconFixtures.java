package dev.yinghuang.legacyforgebridge.convert;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.*;
import javax.tools.ToolProvider;

/** Synthetic bytecode fixtures; platform stubs are compiled but never packed/loaded as mods. */
public final class LegacyIconFixtures {
 private LegacyIconFixtures() { }
 public static Path create(Path root, Map<String,String> overrides)throws Exception {
  Map<String,String> source=new LinkedHashMap<>();
  source.put("cpw/mods/fml/common/registry/GameRegistry.java","package cpw.mods.fml.common.registry; public class GameRegistry { public static void registerItem(net.minecraft.item.Item i,String s){} public static void registerBlock(net.minecraft.block.Block i,String s){} }");
  source.put("fixture/Content.java","package fixture; import cpw.mods.fml.common.registry.GameRegistry; public class Content {static { GameRegistry.registerItem(new PaletteItem(),\"unknownFoodName\"); GameRegistry.registerBlock(new PaletteBlock(false).textures(\"source:wood\",\"source:tile\"),\"solid\"); GameRegistry.registerBlock(new PaletteBlock(true).textures(\"source:wood\",\"source:tile\"),\"half\");}}");
  source.put("fixture/PaletteBlock.java","package fixture; import net.minecraft.block.Block; import net.minecraft.util.IIcon; import net.minecraft.client.renderer.texture.IIconRegister; import net.minecraft.world.IBlockAccess;\npublic class PaletteBlock extends Block { private String[] names; private IIcon[] icons; private boolean half; public PaletteBlock(boolean half){super();this.half=half;} public PaletteBlock textures(String... n){names=n;return this;} public void registerBlockIcons(IIconRegister r){icons=new IIcon[names.length];for(int i=0;i<names.length;i++)icons[i]=r.registerIcon(names[i]);} public IIcon getIcon(int side,int meta){return icons[meta&1];} public void setBlockBoundsBasedOnState(IBlockAccess w,int x,int y,int z){if(half){if((w.getBlockMetadata(x,y,z)&8)!=0)setBlockBounds(0,0.5F,0,1,1,1);else setBlockBounds(0,0,0,1,0.5F,1);}else setBlockBounds(0,0,0,1,1,1);} public void setBlockBoundsForItemRender(){if(half)setBlockBounds(0,0,0,1,0.5F,1);else setBlockBounds(0,0,0,1,1,1);}}");
  source.put("fixture/PaletteItem.java","package fixture; import net.minecraft.item.Item; import net.minecraft.util.IIcon; import net.minecraft.client.renderer.texture.IIconRegister;\npublic class PaletteItem extends Item { private IIcon[] icons; public PaletteItem(){super();setHasSubtypes(true);} public void registerIcons(IIconRegister r){icons=new IIcon[2];for(int i=0;i<2;i++) icons[i]=r.registerIcon(\"source:food\"+i);} public IIcon getIconFromDamage(int meta){return icons[meta&1];}}");
  source.put("net/minecraft/block/Block.java","package net.minecraft.block; public class Block { protected net.minecraft.util.IIcon blockIcon; public void setBlockBounds(float a,float b,float c,float d,float e,float f){} public int getRenderType(){return 0;} public void setBlockBoundsBasedOnState(net.minecraft.world.IBlockAccess w,int x,int y,int z){} public net.minecraft.util.IIcon getIcon(int s,int m){return blockIcon;} }");
  source.put("net/minecraft/client/renderer/texture/IIconRegister.java","package net.minecraft.client.renderer.texture; public interface IIconRegister { net.minecraft.util.IIcon registerIcon(String name); }");
  source.put("net/minecraft/item/Item.java","package net.minecraft.item; public class Item { protected net.minecraft.util.IIcon itemIcon; public Item setHasSubtypes(boolean b){return this;} public Item setTextureName(String s){return this;} public void registerIcons(net.minecraft.client.renderer.texture.IIconRegister r){} public net.minecraft.util.IIcon getIconFromDamage(int i){return itemIcon;} }");
  source.put("net/minecraft/util/IIcon.java","package net.minecraft.util; public interface IIcon {}");
  source.put("net/minecraft/world/IBlockAccess.java","package net.minecraft.world; public interface IBlockAccess {int getBlockMetadata(int x,int y,int z);}");
  source.putAll(overrides);
  Path src=root.resolve("src"),bin=root.resolve("bin");Files.createDirectories(bin);
  List<String> args=new ArrayList<>(List.of("--release","8","-Xlint:-options","-d",bin.toString()));
  for(var e:source.entrySet()){Path p=src.resolve(e.getKey());Files.createDirectories(p.getParent());Files.writeString(p,e.getValue(),StandardCharsets.UTF_8);args.add(p.toString());}
  var compiler=ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new IllegalStateException("Tests require a JDK");
  if(compiler.run(null,null,null,args.toArray(String[]::new))!=0)throw new IllegalStateException("Fixture compilation failed");
  Path jar=root.resolve("icons.jar");
  try(var out=new JarOutputStream(Files.newOutputStream(jar));var files=Files.walk(bin.resolve("fixture"))){
   for(Path p:files.filter(Files::isRegularFile).sorted().toList()){out.putNextEntry(new JarEntry(bin.relativize(p).toString().replace('\\','/')));out.write(Files.readAllBytes(p));out.closeEntry();}
  }
  return jar;
 }
}
