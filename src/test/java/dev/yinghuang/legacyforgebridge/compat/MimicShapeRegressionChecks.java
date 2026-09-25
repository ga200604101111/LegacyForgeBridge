package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyBlockGeometryPass;
import java.util.*;

/** Pure regression assertions; can also run with main without a game or a JUnit runner. */
public final class MimicShapeRegressionChecks {
    private MimicShapeRegressionChecks() { }
    public static void main(String[] args) {
        checkSchema();
        checkGeometry();
        System.out.println("PASS mimic material schema; 256 stair shapes; 32 pane/curtain meshes; held curtain");
    }
    public static void checkSchema() {
        LegacyGeometrySpec.parse(spec("mimic_box", 0, null, null));
        LegacyGeometrySpec.parse(spec("box", -1, null, null));
        LegacyGeometrySpec.parse(spec("mimic_box", -1, "inventory_fallback", "Unproven source configuration"));
        rejects(() -> LegacyGeometrySpec.parse(spec("mimic_box", -1, null, null)));
        rejects(() -> LegacyGeometrySpec.parse(spec("mimic_box", -1, "inventory_fallback", null)));
        rejects(() -> LegacyGeometrySpec.parse(spec("mimic_box", -1, "inventory_fallback", " ")));
        rejects(() -> LegacyGeometrySpec.parse(spec("mimic_box", 0, "inventory_fallback", "reason")));
        rejects(() -> LegacyGeometrySpec.parse(spec("box", -1, "inventory_fallback", "reason")));
        rejects(() -> LegacyGeometrySpec.parse(spec("mimic_box", 0, "invented_mode", "reason")));
        rejects(() -> LegacyGeometrySpec.parse(spec("mimic_box", -2, "inventory_fallback", "reason")));
        rejects(() -> LegacyGeometrySpec.parse(spec("mimic_box", 6, null, null)));
    }
    public static void checkGeometry() {
        for (int meta=0; meta<16; meta++) {
            for (int mask=0; mask<16; mask++) {
                var boxes=LegacyGeometry.stairs(meta, mask);
                double volume=boxes.stream().mapToDouble(b -> (b.x1()-b.x0())*(b.y1()-b.y0())*(b.z1()-b.z0())).sum();
                require(volume == .5 + Integer.bitCount(mask)*.125, "stair volume");
                validateFaces(LegacyGeometry.surfaces(boxes));
            }
            require(LegacyGeometry.stairs(meta).stream().noneMatch(LegacyGeometry.Box::full), "straight stair became cube");
        }
        var sprites=Collections.nCopies(6,"minecraft:block/stone");
        for (int mask=0; mask<16; mask++) {
            require(LegacyGeometry.paneFaces(mask,true).equals(LegacyGeometry.surfaces(LegacyGeometry.panes(mask))),"thick pane mismatch");
            require(LegacyGeometry.paneFaces(mask,false).equals(LegacyGeometry.curtainFaces(mask)),"curtain mismatch");
            validateFaces(LegacyGeometry.paneFaces(mask,true));
            validateFaces(LegacyGeometry.paneFaces(mask,false));
            var model=LegacyBlockGeometryPass.faceModel(LegacyGeometry.paneFaces(mask,false),sprites);
            require(model.getAsJsonArray("elements").size()==2*Integer.bitCount(mask==0?15:mask),"curtain face count");
        }
        var held=LegacyBlockGeometryPass.faceModel(LegacyGeometry.curtainFaces(12),sprites);
        require(held.getAsJsonArray("elements").size()==4,"two-sided held curtain halves");
        for (var e:held.getAsJsonArray("elements")) {
            var part=e.getAsJsonObject();
            require(part.getAsJsonArray("from").get(2).getAsDouble()==8 && part.getAsJsonArray("to").get(2).getAsDouble()==8,"held curtain thickness");
        }
        rejects(() -> LegacyGeometry.paneFaces(-1,false));
        rejects(() -> LegacyGeometry.paneFaces(16,false));
        rejects(() -> LegacyGeometry.paneFaces(16,true));
    }
    private static void validateFaces(List<LegacyGeometry.Face> faces) {
        require(!faces.isEmpty(),"empty mesh");
        var unique=new HashSet<LegacyGeometry.Face>();
        for (var f:faces) {
            require(unique.add(f),"duplicate quad");
            for (double v:f.positions()) require(Double.isFinite(v)&&v>=0&&v<=1,"invalid vertex");
            for (double v:f.uv()) require(Double.isFinite(v)&&v>=0&&v<=1,"invalid UV");
            var p=f.positions();
            double ax=p.get(3)-p.get(0),ay=p.get(4)-p.get(1),az=p.get(5)-p.get(2);
            double bx=p.get(6)-p.get(0),by=p.get(7)-p.get(1),bz=p.get(8)-p.get(2);
            double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
            double outward=switch(f.side()){case 0->-ny;case 1->ny;case 2->-nz;case 3->nz;case 4->-nx;default->nx;};
            require(outward>0,"degenerate/inverted quad");
        }
    }
    private static JsonObject spec(String family,int copy,String mode,String reason) {
        var root=new JsonObject();root.addProperty("schemaVersion",1);
        var blocks=new JsonObject();root.add("blocks",blocks);
        var block=new JsonObject();blocks.add("fixture:mimic",block);
        block.addProperty("family",family);block.addProperty("opaque",false);
        var variants=new JsonObject();block.add("variants",variants);
        var v=new JsonObject();variants.add("0",v);
        var bounds=new JsonArray();for(double n:new double[]{0,0,0,1,.5,1})bounds.add(n);
        v.add("bounds",bounds);v.add("inventoryBounds",bounds.deepCopy());
        v.addProperty("copyFace",copy);v.addProperty("edges",true);v.addProperty("collision","inherited");
        if(mode!=null)v.addProperty("materialMode",mode);
        if(reason!=null)v.addProperty("materialFallbackReason",reason);
        return root;
    }
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static void rejects(Runnable action){try{action.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Invalid input was admitted");}
}
