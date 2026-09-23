package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.compat.*;
import dev.yinghuang.legacyforgebridge.convert.api.*;
import dev.yinghuang.legacyforgebridge.convert.pass.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.tools.ToolProvider;

/** Executable checks also wrapped by JUnit. Fixture source classes are never loaded or run. */
public final class Geometry141Checks {
    private Geometry141Checks() { }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void same(double expected,double actual){check(Math.abs(expected-actual)<1e-8,"expected="+expected+" actual="+actual);}
    private static void rejects(Runnable action){try{action.run();}catch(IllegalArgumentException|ArithmeticException expected){return;}throw new AssertionError("Invalid geometry accepted");}
    private static double volume(List<LegacyGeometry.Box> boxes){return boxes.stream().mapToDouble(b->(b.x1()-b.x0())*(b.y1()-b.y0())*(b.z1()-b.z0())).sum();}
    private static double area(List<LegacyGeometry.Face> faces){double sum=0;for(var f:faces){double[] a=new double[3],b=new double[3];for(int i=0;i<3;i++){a[i]=f.positions().get(3+i)-f.positions().get(i);b[i]=f.positions().get(9+i)-f.positions().get(i);}double x=a[1]*b[2]-a[2]*b[1],y=a[2]*b[0]-a[0]*b[2],z=a[0]*b[1]-a[1]*b[0];sum+=Math.sqrt(x*x+y*y+z*z);}return sum;}
    public static void cubeAndWinding(){
        var faces=LegacyGeometry.surfaces(List.of(LegacyGeometry.FULL));check(faces.size()==6,"cube needs six exposed faces");same(6,area(faces));
        double[][] normal={{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};
        for(var f:faces){double[] a=new double[3],b=new double[3];for(int i=0;i<3;i++){a[i]=f.positions().get(3+i)-f.positions().get(i);b[i]=f.positions().get(6+i)-f.positions().get(i);}double[] n={a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};for(int i=0;i<3;i++)same(normal[f.side()][i],n[i]);}
    }
    public static void slabDimensions(){LegacyGeometry.Box lower=new LegacyGeometry.Box(0,0,0,1,.5,1),upper=new LegacyGeometry.Box(0,.5,0,1,1,1);same(.5,volume(List.of(lower)));same(.5,volume(List.of(upper)));same(4,area(LegacyGeometry.surfaces(List.of(lower))));}
    public static void connectedCuboidDimensions(){
        var liang=new LegacyGeometrySpec.ConnectedCuboid(.15,.85,.15,.85,false,false,true,true,true);
        check(LegacyGeometry.connectedCore(liang,0).equals(new LegacyGeometry.Box(.15,.15,.15,.85,.85,.85)),"liang core");
        check(LegacyGeometry.connectedArm(.15,.85,.15,.85,false,0,5).equals(new LegacyGeometry.Box(.85,.15,.15,1,.85,.85)),"liang east arm");
        var pillar=new LegacyGeometrySpec.ConnectedCuboid(.3,.7,.2,.8,true,true,false,true,false);
        check(LegacyGeometry.connectedCore(pillar,1).equals(new LegacyGeometry.Box(.3,0,.3,.7,.2,.7)),"pillar up core");
        check(LegacyGeometry.connectedArm(.3,.7,.2,.8,true,1,5).equals(new LegacyGeometry.Box(.7,0,.3,1,.2,.7)),"pillar east arm");
        rejects(()->LegacyGeometry.connectedCore(pillar,6));
    }
    public static void everyStairOrientation(){for(int meta=0;meta<16;meta++){var b=LegacyGeometry.stairs(meta);same(.75,volume(b));same(5.5,area(LegacyGeometry.surfaces(b)));check(LegacyGeometry.stairs(meta&7).equals(b),"bit 3 belongs to source state, not shape orientation");}}
    public static void outerAndInnerCorners(){
        int outer=LegacyGeometry.stairMask(0,(x,z)->x==1&&z==0?2:-1);same(.625,volume(LegacyGeometry.stairs(0,outer)));
        int inner=LegacyGeometry.stairMask(0,(x,z)->x==-1&&z==0?2:-1);same(.875,volume(LegacyGeometry.stairs(0,inner)));
        int oppositeHalf=LegacyGeometry.stairMask(0,(x,z)->x==1&&z==0?6:-1);same(.75,volume(LegacyGeometry.stairs(0,oppositeHalf)));
    }
    public static void cornerContinuationGuard(){int mask=LegacyGeometry.stairMask(0,(x,z)->x==1&&z==0?2:x==0&&z==-1?0:-1);same(.75,volume(LegacyGeometry.stairs(0,mask)));}
    public static void everyPaneConnection(){for(int mask=0;mask<16;mask++){var boxes=LegacyGeometry.panes(mask);check(volume(boxes)<.25,"pane became full cube");check(!LegacyGeometry.surfaces(boxes).isEmpty(),"empty pane");}check(LegacyGeometry.panes(0).equals(LegacyGeometry.panes(15)),"isolated legacy pane is a cross");}
    public static void curtainsHaveNoHorizontalCaps(){for(int mask=0;mask<16;mask++)for(var face:LegacyGeometry.curtainFaces(mask))check(face.side()>=2,"curtain got a solid horizontal cap");}
    public static void invalidGeometryRejected(){rejects(()->new LegacyGeometry.Box(0,0,0,Double.NaN,1,1));rejects(()->new LegacyGeometry.Box(0,0,0,2,1,1));rejects(()->new LegacyGeometry.Box(0,0,0,0,1,1));rejects(()->LegacyGeometry.stairMask(16,(x,z)->-1));rejects(()->LegacyGeometry.panes(16));}
    public static void heldModelsUseBlockTransforms(){var model=LegacyBlockGeometryPass.model(LegacyGeometry.stairs(0),Collections.nCopies(6,"test:block/face"));check(model.get("parent").getAsString().equals("minecraft:block/block"),"flat generated item parent");check(model.getAsJsonArray("elements").size()>6,"stair reduced to a cube");check(!model.getAsJsonObject("textures").has("layer0"),"2D icon rather than geometry");}
    public static void strictSchema(){
        JsonObject root=JsonParser.parseString("{\"schemaVersion\":1,\"blocks\":{\"test:slab\":{\"family\":\"box\",\"opaque\":false,\"variants\":{\"0\":{\"bounds\":[0,0,0,1,0.5,1],\"inventoryBounds\":[0,0,0,1,0.5,1],\"copyFace\":-1,\"edges\":true,\"collision\":\"inherited\"}}}}}").getAsJsonObject();
        check(LegacyGeometrySpec.parse(root).size()==1,"valid schema rejected");
        root.getAsJsonObject("blocks").getAsJsonObject("test:slab").addProperty("renderOffset","xyz");
        check(LegacyGeometrySpec.parse(root).get("test:slab").renderOffset()==LegacyGeometrySpec.RenderOffset.XYZ,"xyz render offset rejected");
        root.getAsJsonObject("blocks").getAsJsonObject("test:slab").addProperty("renderOffset","mystery");rejects(()->LegacyGeometrySpec.parse(root));
        root.getAsJsonObject("blocks").getAsJsonObject("test:slab").remove("renderOffset");
        root.addProperty("schemaVersion",1.5);rejects(()->LegacyGeometrySpec.parse(root));root.addProperty("schemaVersion",1);
        root.getAsJsonObject("blocks").getAsJsonObject("test:slab").getAsJsonObject("variants").getAsJsonObject("0").addProperty("copyFace",0.5);rejects(()->LegacyGeometrySpec.parse(root));
    }
    public static void mimicDirectionsCyclesAndBudget(){
        check(LegacyMimicResolver.resolve(0,p->p<3?5:-1,(p,d)->p+1,10)==3,"direction chain did not reach material");
        check(LegacyMimicResolver.resolve(0,p->5,(p,d)->1-p,10)==null,"cycle was not stopped");
        check(LegacyMimicResolver.resolve(0,p->5,(p,d)->p+1,8)==null,"budget not respected");
        check(LegacyMimicResolver.resolve(0,p->-2,(p,d)->p+1,8)==null,"unknown direction treated as material");
        rejects(()->LegacyMimicResolver.resolve(0,p->-1,(p,d)->p,257));
    }
    public static void sourceGeometry(Path root)throws Exception {
        Path jar=fixture(root);var result=new LegacyBlockGeometryAnalyzer().analyze(jar);Map<String,LegacyBlockGeometryAnalyzer.Rule> rules=new HashMap<>();result.rules().forEach(r->rules.put(r.input().registryName(),r));
        var slab=rules.get("slab");check(slab!=null,"source slab excluded");same(.5,slab.input().variants().getFirst().bounds().get(4));same(.5,slab.input().variants().get(8).bounds().get(1));same(0,slab.input().variants().get(8).inventoryBounds().get(1));
        check(rules.get("stair").family().equals("stairs"),"inherited stair not admitted");
        for(String name:List.of("unknown","position","stateful","external"))check(!rules.containsKey(name),"unsupported source admitted: "+name);
        check(System.getProperty("lfb.geometry.source.executed")==null,"source JVM execution occurred");
    }
    public static void sourcePassAndSpecialOwnership(Path root)throws Exception {
        Path jar=fixture(root),staging=root.resolve("staging");Files.createDirectories(staging);var c=context(jar,staging);
        for(var pass:List.<ConversionPass>of(new CopyLegacyJarPass(),new GenericContentPass(),new LegacyClientContentBaselinePass(),new LegacyIconPresentationPass()))pass.apply(c);
        Path special=staging.resolve("assets/geometryfixture/items/solid.json");String specialText="{\"model\":{\"type\":\"minecraft:special\",\"base\":\"geometryfixture:block/solid\",\"model\":{\"type\":\"test:renderer\"}}}";Files.writeString(special,specialText);
        new LegacyBlockGeometryPass().apply(c);
        check(Files.readString(special).equals(specialText),"special inventory renderer overwritten");
        var spec=LegacyGeometrySpec.parse(JsonParser.parseString(Files.readString(staging.resolve(LegacyGeometrySpec.PATH))).getAsJsonObject());
        check(spec.containsKey("geometryfixture:slab")&&spec.containsKey("geometryfixture:stair"),"source shapes not connected to output");check(!spec.containsKey("geometryfixture:solid"),"special renderer got geometry override");
        var item=JsonParser.parseString(Files.readString(staging.resolve("legacyforgebridge/icon-presentation.json"))).getAsJsonObject();check(item.getAsJsonObject("items").getAsJsonObject("geometryfixture:slab").has("8"),"held source metadata lost");
        var held=JsonParser.parseString(Files.readString(staging.resolve("assets/geometryfixture/models/item/lfb_geometry/slab/8.json"))).getAsJsonObject();check(held.get("parent").getAsString().equals("minecraft:block/block"),"no inherited 3D transforms");
    }
    private static ConversionContext context(Path jar,Path staging)throws Exception{return new ConversionContext(jar,staging,staging.resolveSibling("candidate.jar"),Hashing.sha256(jar),Files.size(jar),LegacyModMetadata.read(jar),new LegacyJarAnalyzer().analyze(jar),new DiagnosticCollector(),"generic");}
    private static Path fixture(Path root)throws Exception {
        Map<String,String> sources=new LinkedHashMap<>();
        sources.put("net/minecraft/util/IIcon.java","package net.minecraft.util;public interface IIcon {}");
        sources.put("net/minecraft/world/IBlockAccess.java","package net.minecraft.world;public interface IBlockAccess{int getBlockMetadata(int x,int y,int z);}");
        sources.put("net/minecraft/client/renderer/texture/IIconRegister.java","package net.minecraft.client.renderer.texture;public interface IIconRegister{net.minecraft.util.IIcon registerIcon(String s);}");
        sources.put("net/minecraft/block/Block.java","package net.minecraft.block;public class Block{protected net.minecraft.util.IIcon blockIcon;public Block setBlockTextureName(String s){return this;}public void setBlockBounds(float a,float b,float c,float d,float e,float f){}public net.minecraft.util.IIcon getIcon(int side,int meta){return blockIcon;}public int getRenderType(){return 0;}public void registerBlockIcons(net.minecraft.client.renderer.texture.IIconRegister r){}public void setBlockBoundsBasedOnState(net.minecraft.world.IBlockAccess w,int x,int y,int z){}public void setBlockBoundsForItemRender(){}}");
        sources.put("net/minecraft/block/BlockStairs.java","package net.minecraft.block;public class BlockStairs extends Block{public BlockStairs(Block source,int metadata){}}");
        sources.put("cpw/mods/fml/common/registry/GameRegistry.java","package cpw.mods.fml.common.registry;public class GameRegistry{public static void registerBlock(net.minecraft.block.Block b,String n){}}");
        sources.put("other/Content.java","package other;import cpw.mods.fml.common.registry.GameRegistry;public class Content{static{GameRegistry.registerBlock(new Shape(true),\"slab\");GameRegistry.registerBlock(new Shape(false),\"solid\");GameRegistry.registerBlock(new Step(),\"stair\");GameRegistry.registerBlock(new Unknown(),\"unknown\");GameRegistry.registerBlock(new Position(),\"position\");GameRegistry.registerBlock(new Stateful(),\"stateful\");GameRegistry.registerBlock(new External(),\"external\");}}");
        sources.put("other/Shape.java","package other;import net.minecraft.block.Block;import net.minecraft.world.IBlockAccess;public class Shape extends Block{final boolean half;public Shape(boolean half){this.half=half;setBlockTextureName(\"other:surface\");}public void setBlockBoundsBasedOnState(IBlockAccess w,int x,int y,int z){if(half){if((w.getBlockMetadata(x,y,z)&8)!=0)setBlockBounds(0,.5f,0,1,1,1);else setBlockBounds(0,0,0,1,.5f,1);}else setBlockBounds(0,0,0,1,1,1);}public void setBlockBoundsForItemRender(){if(half)setBlockBounds(0,0,0,1,.5f,1);else setBlockBounds(0,0,0,1,1,1);}}");
        sources.put("other/Step.java","package other;public class Step extends net.minecraft.block.BlockStairs{public Step(){super(new Shape(false),0);}}");
        sources.put("other/Unknown.java","package other;public class Unknown extends Shape{public Unknown(){super(false);}public int getRenderType(){return 123;}}");
        sources.put("other/Position.java","package other;public class Position extends Shape{public Position(){super(false);}public void setBlockBoundsBasedOnState(net.minecraft.world.IBlockAccess w,int x,int y,int z){if(x==0)setBlockBounds(0,0,0,1,.5f,1);else setBlockBounds(0,0,0,1,1,1);}}");
        sources.put("other/Stateful.java","package other;public class Stateful extends Shape{int changed;public Stateful(){super(false);}public void setBlockBoundsBasedOnState(net.minecraft.world.IBlockAccess w,int x,int y,int z){changed++;setBlockBounds(0,0,0,1,.5f,1);}}");
        sources.put("other/External.java","package other;public class External extends Shape{public External(){super(false);System.setProperty(\"lfb.geometry.source.executed\",\"bad\");}}");
        Path src=root.resolve("src"),bin=root.resolve("bin");Files.createDirectories(bin);List<String> args=new ArrayList<>(List.of("--release","8","-Xlint:-options","-d",bin.toString()));
        for(var e:sources.entrySet()){Path p=src.resolve(e.getKey());Files.createDirectories(p.getParent());Files.writeString(p,e.getValue(),StandardCharsets.UTF_8);args.add(p.toString());}
        var compiler=ToolProvider.getSystemJavaCompiler();if(compiler==null||compiler.run(null,null,null,args.toArray(String[]::new))!=0)throw new IllegalStateException("Fixture compilation failed");
        Path jar=root.resolve("source.jar");try(var out=new JarOutputStream(Files.newOutputStream(jar));var files=Files.walk(bin.resolve("other"))){
            for(Path p:files.filter(Files::isRegularFile).sorted().toList()){out.putNextEntry(new JarEntry(bin.relativize(p).toString().replace('\\','/')));out.write(Files.readAllBytes(p));out.closeEntry();}
            out.putNextEntry(new JarEntry("mcmod.info"));out.write("[{\"modid\":\"geometryfixture\",\"name\":\"Fixture\",\"version\":\"1\"}]".getBytes(StandardCharsets.UTF_8));out.closeEntry();
            out.putNextEntry(new JarEntry("assets/other/textures/blocks/surface.png"));BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);for(int x=0;x<16;x++)for(int y=0;y<16;y++)image.setRGB(x,y,0xFF775533);ImageIO.write(image,"PNG",out);out.closeEntry();
        }return jar;
    }
    public static void main(String[]args)throws Exception {cubeAndWinding();slabDimensions();connectedCuboidDimensions();everyStairOrientation();outerAndInnerCorners();cornerContinuationGuard();everyPaneConnection();curtainsHaveNoHorizontalCaps();invalidGeometryRejected();heldModelsUseBlockTransforms();strictSchema();mimicDirectionsCyclesAndBudget();sourceGeometry(Files.createTempDirectory("lfb-shape-source"));sourcePassAndSpecialOwnership(Files.createTempDirectory("lfb-shape-pass"));System.out.println("14 geometry checks passed");}
}
