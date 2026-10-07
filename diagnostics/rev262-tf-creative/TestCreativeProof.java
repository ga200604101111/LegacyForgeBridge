package dev.yinghuang.legacyforgebridge.convert.pass;
import java.nio.file.*; import java.util.*;
import dev.yinghuang.legacyforgebridge.convert.LegacyRegistryAnalyzer;
public class TestCreativeProof {
 static final String O="twilightforest/block/TFBlocks", D="Lnet/minecraft/block/Block;";
 static LegacyRegistryAnalyzer.Registration reg(String name,String cls,String desc,String field){
   List<LegacyRegistryAnalyzer.ConstructorArgument> aa=new ArrayList<>();
   for(String x: desc.substring(desc.indexOf('(')+1,desc.indexOf(')')).isEmpty()?List.<String>of():List.of("Ljava/lang/Boolean;")) aa.add(new LegacyRegistryAnalyzer.ConstructorArgument(x,null));
   return new LegacyRegistryAnalyzer.Registration(LegacyRegistryAnalyzer.Kind.BLOCK,name,"TwilightForest",cls,null,desc,aa,"x","x","()V");
 }
 static LegacyRegistryAnalyzer.FieldBinding bind(String name,String cls,String field){return new LegacyRegistryAnalyzer.FieldBinding(O,field,D,LegacyRegistryAnalyzer.Kind.BLOCK,name,"TwilightForest",cls);}
 public static void main(String[] x){
   Path p=Path.of(x[0]);
   var regs=List.of(
    reg("TFFirefly","twilightforest/block/BlockTFFirefly","()V","firefly"),
    reg("CinderFurnaceIdle","twilightforest/block/BlockTFCinderFurnace","(Ljava/lang/Boolean;)V","cinderFurnace"),
    reg("CinderFurnaceLit","twilightforest/block/BlockTFCinderFurnace","(Ljava/lang/Boolean;)V","cinderFurnaceLit"),
    reg("TFPortal","twilightforest/block/BlockTFPortal","()V","portal")
   );
   var bs=List.of(
    bind("TFFirefly","twilightforest/block/BlockTFFirefly","firefly"),
    bind("CinderFurnaceIdle","twilightforest/block/BlockTFCinderFurnace","cinderFurnace"),
    bind("CinderFurnaceLit","twilightforest/block/BlockTFCinderFurnace","cinderFurnaceLit"),
    bind("TFPortal","twilightforest/block/BlockTFPortal","portal")
   );
   var an=new LegacyRegistryAnalyzer.Analysis(new ArrayList<>(regs),new ArrayList<>(bs),List.of());
   for(var r:regs)System.out.println(r.registryName()+" => "+GenericBlockCreativeMembership.proveTabForTesting(p,an,r));
 }
}
