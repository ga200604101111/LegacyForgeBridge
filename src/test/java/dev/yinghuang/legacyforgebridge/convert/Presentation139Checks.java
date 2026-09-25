package dev.yinghuang.legacyforgebridge.convert;

import java.nio.file.*;
import java.util.*;
import java.lang.reflect.Proxy;
import com.viaversion.nbt.tag.*;
import com.viaversion.viaversion.api.data.FullMappings;
import com.viaversion.viaversion.api.minecraft.data.*;
import dev.yinghuang.legacyforgebridge.compat.LegacyItemNameBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacyItemCarrierState;

/** Also executable without JUnit; all checks call production logic and do not load source classes. */
public final class Presentation139Checks {
    private Presentation139Checks() { }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static List<LegacyIconTableAnalyzer.Result> analyze(Path root,Map<String,String> source)throws Exception {
        Path jar=LegacyIconFixtures.create(root,source);
        return new LegacyIconTableAnalyzer().analyze(jar,new LegacyRegistryAnalyzer().analyze(jar).registrations());
    }
    private static LegacyIconTableAnalyzer.Result item(List<LegacyIconTableAnalyzer.Result> results){return results.stream().filter(r->!r.block()).findFirst().orElseThrow();}
    private static LegacyIconTableAnalyzer.Result block(List<LegacyIconTableAnalyzer.Result> results){return results.stream().filter(r->r.block()).findFirst().orElseThrow();}
    public static void enumFieldsAndMapAndNullDefaults(Path root)throws Exception {
        var result=item(analyze(root,Map.of("fixture/PaletteItem.java","""
            package fixture;
            import java.util.HashMap;import net.minecraft.item.Item;import net.minecraft.util.IIcon;
            import net.minecraft.client.renderer.texture.IIconRegister;
            public class PaletteItem extends Item {
                enum Choice { A(0,"source:first"),B(1,"source:second"); final int id;final String texture;
                    Choice(int i,String t){id=i;texture=t;} }
                private IIcon[] icons;
                private HashMap<Integer,IIcon> byId=new HashMap<Integer,IIcon>();
                public void registerIcons(IIconRegister r){if(icons==null)icons=new IIcon[2];
                    for(Choice c:Choice.values()){icons[c.id]=r.registerIcon(c.texture);byId.put(c.id,icons[c.id]);}}
                public IIcon getIconFromDamage(int metadata){return byId.get(metadata&1);}
            }
            """)));
        check(result.variants().size()==256,result.limitation());
        check(result.variants().get(0).faceIcons().getFirst().equals("source:first"),"enum zero constructor was skipped");
        check(result.variants().get(1).faceIcons().getFirst().equals("source:second"),"enum field/ordinal collapsed");
    }
    public static void arrayCopyPreservesEachEntry(Path root)throws Exception {
        var result=item(analyze(root,Map.of("fixture/PaletteItem.java","""
            package fixture;import net.minecraft.item.Item;import net.minecraft.util.IIcon;import net.minecraft.client.renderer.texture.IIconRegister;
            public class PaletteItem extends Item {IIcon[] icons;
                public void registerIcons(IIconRegister r){String[] source={"source:a","source:b"};String[] names=new String[2];
                    System.arraycopy(source,0,names,0,2);icons=new IIcon[2];for(int i=0;i<2;i++)icons[i]=r.registerIcon(names[i]);}
                public IIcon getIconFromDamage(int m){return icons[m&1];}}
            """)));
        check(result.variants().size()==256,result.limitation());
        check(!result.variants().get(0).faceIcons().equals(result.variants().get(1).faceIcons()),"arraycopy flattened distinct icons");
    }
    private static Map<String,String> stackStub(){return new HashMap<>(Map.of("net/minecraft/item/ItemStack.java","package net.minecraft.item;public class ItemStack {public int getItemDamage(){return 0;}}"));}
    public static void multipleLayersRetainMetadataTints(Path root)throws Exception {
        var source=stackStub();source.put("fixture/PaletteItem.java","""
            package fixture;import net.minecraft.item.*;import net.minecraft.util.IIcon;import net.minecraft.client.renderer.texture.IIconRegister;
            public class PaletteItem extends Item {IIcon[] icons;public void registerIcons(IIconRegister r){icons=new IIcon[]{r.registerIcon("source:base"),r.registerIcon("source:overlay")};}
                public boolean requiresMultipleRenderPasses(){return true;}public int getRenderPasses(int metadata){return 2;}
                public IIcon getIconFromDamageForRenderPass(int metadata,int layer){return icons[layer];}
                public int getColorFromItemStack(ItemStack stack,int layer){return layer==0?16777215:(stack.getItemDamage()==0?16711680:255);}}
            """);
        var result=item(analyze(root,source));check(result.variants().size()==256,result.limitation());
        check(result.variants().getFirst().faceIcons().size()==2,"lost item overlay");
        check(result.variants().get(0).tints().get(1)==0xFF0000,"lost first tint");
        check(result.variants().get(1).tints().get(1)==0x0000FF,"lost metadata tint");
    }
    private static Map<String,String> worldSource(String expression){return Map.of("fixture/PaletteBlock.java","""
        package fixture;import net.minecraft.block.Block;import net.minecraft.util.IIcon;import net.minecraft.client.renderer.texture.IIconRegister;import net.minecraft.world.IBlockAccess;
        public class PaletteBlock extends Block {public PaletteBlock(boolean half){}public PaletteBlock textures(String... names){return this;}
            public void registerBlockIcons(IIconRegister r){blockIcon=r.registerIcon("source:tile");}
            public int colorMultiplier(IBlockAccess world,int x,int y,int z){return %s;}}
        """.formatted(expression));}
    public static void coordinatesAreNotInvented(Path root)throws Exception {
        check(block(analyze(root,worldSource("x==0?16777215:255"))).variants().isEmpty(),"position-dependent color was sampled as a constant");
    }
    public static void neighborMetadataIsNotSelfMetadata(Path root)throws Exception {
        check(block(analyze(root,worldSource("world.getBlockMetadata(x+1,y,z)==0?16777215:255"))).variants().isEmpty(),"neighbor state confused with own metadata");
    }
    public static void statefulBaseSetterRejected(Path root)throws Exception {
        var result=item(analyze(root,Map.of("fixture/PaletteItem.java","""
            package fixture;import net.minecraft.item.Item;import net.minecraft.util.IIcon;import net.minecraft.client.renderer.texture.IIconRegister;
            public class PaletteItem extends Item {public void registerIcons(IIconRegister r){itemIcon=r.registerIcon("source:a");}
                public IIcon getIconFromDamage(int metadata){setTextureName("source:b");return itemIcon;}}
            """)));
        check(result.variants().isEmpty(),"stateful native setter admitted");
    }
    private static Map<String,String> nameSource(String fields,String getter,String extra){
        var source=stackStub();
        source.put("net/minecraft/item/Item.java","package net.minecraft.item; public class Item {protected net.minecraft.util.IIcon itemIcon;public Item setHasSubtypes(boolean b){return this;}public Item setUnlocalizedName(String s){return this;}public String getUnlocalizedName(){return null;}public String getUnlocalizedName(ItemStack s){return null;}}");
        source.put("fixture/Content.java","package fixture;import net.minecraft.item.Item;import cpw.mods.fml.common.registry.GameRegistry;public class Content {static{register(new PaletteItem(),\"configured\");}private static void register(Item item,String name){item.setUnlocalizedName(name);GameRegistry.registerItem(item,name);}}");
        source.put("fixture/PaletteItem.java","package fixture;import net.minecraft.item.*;public class PaletteItem extends Item {"+fields+"public String getUnlocalizedName(ItemStack stack){return "+getter+";}"+extra+"}");
        return source;
    }
    private static LegacyIconTableAnalyzer.NameResult names(Path root,Map<String,String> source)throws Exception {
        Path jar=LegacyIconFixtures.create(root,source);
        return new LegacyIconTableAnalyzer().analyzeNames(jar,new LegacyRegistryAnalyzer().analyze(jar).registrations()).getFirst();
    }
    public static void namesFollowSourceVariantRules(Path root)throws Exception {
        var result=names(root,nameSource("","super.getUnlocalizedName()+\".\"+(stack.getItemDamage()&1)",""));
        check(result.keys().size()==256,result.limitation());
        check("item.configured.0.name".equals(result.keys().get(0)),"incorrect default key");
        check("item.configured.1.name".equals(result.keys().get(255)),"incorrect masked variant key");
    }
    public static void unknownNamingStateRejected(Path root)throws Exception {
        var result=names(root,nameSource("private boolean unknown;","unknown?\"a\":\"b\"",""));
        check(result.keys().isEmpty(),"unexecuted source constructor was silently zero-initialized");
    }
    public static void customDisplayNameExcluded(Path root)throws Exception {
        var result=names(root,nameSource("","super.getUnlocalizedName()","public String getItemStackDisplayName(ItemStack s){return \"custom\";}"));
        check(result.keys().isEmpty(),"custom source display-name method overwritten");
    }
    private static StructuredDataContainer data()throws Exception {
        // Only the serializer-ID lookup is synthetic. The container and NBT implementation are Via's.
        var data=new StructuredDataContainer();var field=StructuredDataContainer.class.getDeclaredField("lookup");field.setAccessible(true);
        field.set(data,Proxy.newProxyInstance(FullMappings.class.getClassLoader(),new Class<?>[]{FullMappings.class},(p,m,a)->{
            if(m.getReturnType()==int.class)return 1;if(m.getReturnType()==boolean.class)return false;
            throw new UnsupportedOperationException(m.getName());}));return data;
    }
    public static void carrierNamesRoundTripWithoutChangingCustomNames()throws Exception {
        var data=data();var marker=LegacyItemCarrierState.capture(null,"example:item",165,65535);
        Tag original=new StringTag("paper");Tag custom=new StringTag("server custom name");
        data.set(StructuredDataKey.ITEM_NAME,original);data.set(StructuredDataKey.CUSTOM_NAME,custom);
        LegacyItemNameBridge.toClient(data,marker,"lfb.converted.example.item.variant.3.name");
        check(data.get(StructuredDataKey.CUSTOM_NAME).equals(custom),"custom name overwritten clientbound");
        check(data.get(StructuredDataKey.ITEM_NAME) instanceof CompoundTag,"default name is not a translatable component");
        LegacyItemNameBridge.toServer(data,marker);
        check(original.equals(data.get(StructuredDataKey.ITEM_NAME)),"previous component not restored");
        check(custom.equals(data.get(StructuredDataKey.CUSTOM_NAME)),"custom name overwritten serverbound");
        check(LegacyItemCarrierState.metadata(marker)==65535,"names corrupted unsigned legacy metadata");
        var tag=new CompoundTag();tag.put(LegacyItemCarrierState.KEY,marker);LegacyItemCarrierState.restoreSourceTag(tag,marker);
        check(tag.isEmpty(),"name bookkeeping leaked into Forge NBT");
    }
    public static void externallyChangedDefaultNameIsNotErased()throws Exception {
        var data=data();var marker=new CompoundTag();LegacyItemNameBridge.toClient(data,marker,"example.key");
        Tag changed=new StringTag("external edit");data.set(StructuredDataKey.ITEM_NAME,changed);LegacyItemNameBridge.toServer(data,marker);
        check(changed.equals(data.get(StructuredDataKey.ITEM_NAME)),"external default-name edit erased");
    }
    public static void absentOriginalNameIsRemovedOnReturn()throws Exception {
        var data=data();var marker=new CompoundTag();LegacyItemNameBridge.toClient(data,marker,"example.key");LegacyItemNameBridge.toServer(data,marker);
        check(!data.has(StructuredDataKey.ITEM_NAME),"bridge default leaked to server");
    }
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]);Files.createDirectories(root);
        enumFieldsAndMapAndNullDefaults(root.resolve("enum"));arrayCopyPreservesEachEntry(root.resolve("array"));
        multipleLayersRetainMetadataTints(root.resolve("layers"));coordinatesAreNotInvented(root.resolve("coordinates"));
        neighborMetadataIsNotSelfMetadata(root.resolve("neighbors"));statefulBaseSetterRejected(root.resolve("stateful"));
        namesFollowSourceVariantRules(root.resolve("names"));unknownNamingStateRejected(root.resolve("unknown"));customDisplayNameExcluded(root.resolve("custom"));
        carrierNamesRoundTripWithoutChangingCustomNames();externallyChangedDefaultNameIsNotErased();absentOriginalNameIsRemovedOnReturn();
        System.out.println("12 standalone production checks passed (not a JUnit run)");
    }
}
