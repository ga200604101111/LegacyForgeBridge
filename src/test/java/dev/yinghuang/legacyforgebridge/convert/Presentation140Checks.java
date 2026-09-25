package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.api.*;
import dev.yinghuang.legacyforgebridge.convert.pass.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Shared by ordinary JUnit and a standalone runner; source classes are never loaded as mods. */
public final class Presentation140Checks {
    private Presentation140Checks() { }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Map<String,String> sources(String method) {
        Map<String,String> files=new LinkedHashMap<>();
        files.put("net/minecraft/item/ItemStack.java","package net.minecraft.item; public class ItemStack {public ItemStack(Item item,int count,int meta){} public void setTagCompound(Object data){} }");
        files.put("net/minecraft/creativetab/CreativeTabs.java","package net.minecraft.creativetab; public class CreativeTabs {} ");
        files.put("fixture/PaletteItem.java","""
            package fixture; import net.minecraft.item.*;import net.minecraft.util.IIcon;
            import net.minecraft.client.renderer.texture.IIconRegister;import net.minecraft.creativetab.CreativeTabs;import java.util.List;
            public class PaletteItem extends Item {
              int counter; private IIcon[] icons;
              public PaletteItem(){super();setHasSubtypes(true);}
              public void registerIcons(IIconRegister r){icons=new IIcon[2];for(int i=0;i<2;i++)icons[i]=r.registerIcon("source:food"+i);}
              public IIcon getIconFromDamage(int meta){return icons[meta&1];}
            """ + method + "}");
        return files;
    }
    private static LegacyIconTableAnalyzer.CreativeResult creative(String body)throws Exception {
        Path root=Files.createTempDirectory("lfb-creative-proof-");
        Path jar=LegacyIconFixtures.create(root,sources("public void getSubItems(Item item,CreativeTabs tab,List list){"+body+"}"));
        return new LegacyIconTableAnalyzer().analyzeCreative(jar,new LegacyRegistryAnalyzer().analyze(jar).registrations()).stream()
                .filter(r->r.registryName().equals("unknownFoodName")).findFirst().orElseThrow();
    }
    public static void creativeUsesSourceEnumeration()throws Exception {
        var r=creative("list.add(new ItemStack(item,1,5));list.add(new ItemStack(item,1,9));list.add(new ItemStack(item,1,5));");
        check(r.proven()&&r.metadata().equals(List.of(5,9)),"Creative variants must use source order, not all 256 icon-getter inputs");
    }
    public static void creativeEmptyIsNotDefaultZero()throws Exception {
        var r=creative("");check(r.proven()&&r.metadata().isEmpty(),"Proven empty creative output must stay empty");
    }
    public static void creativePreservesUnsignedMetadata()throws Exception {
        var r=creative("list.add(new ItemStack(item,1,65535));");check(r.proven()&&r.metadata().equals(List.of(65535)),"Unsigned source data lost");
    }
    public static void creativeRejectsOtherItem()throws Exception {
        var r=creative("list.add(new ItemStack(new Item(),1,0));");check(!r.proven()&&r.metadata().isEmpty(),"Foreign creative item silently rebound");
    }
    public static void creativeRejectsUnknownTabBranch()throws Exception {
        var r=creative("if(tab==null)list.add(new ItemStack(item,1,0));else list.add(new ItemStack(item,1,3));");
        check(!r.proven(),"Unknown tab must not be replaced by null");
    }
    public static void creativeRejectsMutation()throws Exception {
        var r=creative("counter++;list.add(new ItemStack(item,1,counter));");check(!r.proven(),"Stateful source callback accepted");
    }
    public static void creativeRejectsUnrepresentedNbt()throws Exception {
        var r=creative("ItemStack stack=new ItemStack(item,1,0);stack.setTagCompound(new Object());list.add(stack);");
        check(!r.proven(),"NBT-dependent creative subtype silently flattened");
    }
    public static void changedSpecializedModelRevokesOwnership()throws Exception {
        Path root=Files.createTempDirectory("lfb-model-owner-"),model=root.resolve("assets/example/models/item/a.json");Files.createDirectories(model.getParent());
        Files.writeString(model,"{\"parent\":\"minecraft:item/generated\"}");LegacyPresentationOwnership.record(root,model);
        check(LegacyPresentationOwnership.owns(root,model),"Recorded provisional model not recognized");
        Files.writeString(model,"{\"parent\":\"example:item/special\"}");
        check(!LegacyPresentationOwnership.owns(root,model),"Specialized overwrite remained owned by baseline");
    }
    public static void ownershipRejectsPathsOutsideStaging()throws Exception {
        Path root=Files.createTempDirectory("lfb-owner-root-"),outside=Files.createTempFile("outside-",".json");
        boolean rejected=false;try{LegacyPresentationOwnership.record(root,outside);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"External model path was recorded");
    }
    public static void sourceIconsReplaceTextureBackedBaseline()throws Exception { modelPass(false,false); }
    public static void sourceIconsPreserveSpecializedGeometry()throws Exception { modelPass(true,false); }
    public static void sourceIconsPreserveSpecializedItemRenderer()throws Exception { modelPass(false,true); }
    private static void modelPass(boolean specialGeometry,boolean specialItem)throws Exception {
        Path root=Files.createTempDirectory("lfb-provisional-proof-");Path jar=LegacyIconFixtures.create(root,sources(""));Path staging=root.resolve("staging");
        Files.createDirectories(staging);
        var metadata=new LegacyModMetadata("icons.jar","test",List.of(new LegacyModMetadata.ModEntry("Example","Example","1.0","1.7.10",List.of())));
        var context=new ConversionContext(jar,staging,root.resolve("candidate.jar"),Hashing.sha256(jar),Files.size(jar),metadata,new LegacyJarAnalyzer().analyze(jar),new DiagnosticCollector(),"test");
        for(String texture:List.of("example/textures/items/unknownfoodname.png","source/textures/items/food0.png","source/textures/items/food1.png")){
            Path p=staging.resolve("assets/"+texture);Files.createDirectories(p.getParent());ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB),"PNG",p.toFile());
        }
        new GenericContentPass().apply(context);new LegacyClientContentBaselinePass().apply(context);
        Path model=staging.resolve("assets/example/models/item/unknownfoodname.json"),definition=staging.resolve("assets/example/items/unknownfoodname.json");
        if(specialGeometry)Files.writeString(model,"{\"parent\":\"example:item/handmade\"}");
        if(specialItem)Files.writeString(definition,"{\"model\":{\"type\":\"minecraft:special\",\"base\":\"example:item/base\"}}");
        byte[] before=Files.readAllBytes(model);new LegacyIconPresentationPass().apply(context);
        JsonObject report=JsonParser.parseString(Files.readString(staging.resolve(LegacyIconPresentationPass.OUTPUT),StandardCharsets.UTF_8)).getAsJsonObject();
        if(specialGeometry||specialItem)check(Arrays.equals(before,Files.readAllBytes(model)),"Specialized presentation overwritten");
        else {
            String value=Files.readString(model);check(value.contains("source:items/food0")&&!value.contains("example:items/unknownfoodname"),"Wrong but non-missing baseline texture survived");
            check(report.get("provisionalTextureModelsReplaced").getAsInt()==1,"Provisional model counted as a placeholder");
            check(report.getAsJsonObject("items").getAsJsonObject("example:unknownfoodname").has("1"),"Subtype model map missing");
        }
    }
    public static void main(String[] args)throws Exception {
        creativeUsesSourceEnumeration();creativeEmptyIsNotDefaultZero();creativePreservesUnsignedMetadata();creativeRejectsOtherItem();creativeRejectsUnknownTabBranch();creativeRejectsMutation();creativeRejectsUnrepresentedNbt();changedSpecializedModelRevokesOwnership();ownershipRejectsPathsOutsideStaging();sourceIconsReplaceTextureBackedBaseline();sourceIconsPreserveSpecializedGeometry();sourceIconsPreserveSpecializedItemRenderer();
        System.out.println("Presentation140Checks: 12 checks passed");
    }
}
