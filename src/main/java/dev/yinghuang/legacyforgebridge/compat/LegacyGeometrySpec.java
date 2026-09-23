package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.*;
import java.util.*;

/** Strict optional candidate-owned presentation schema. No numeric registry ID is serialized. */
public final class LegacyGeometrySpec {
    public static final String PATH="legacyforgebridge/block-geometry.json";
    public static final String INVENTORY_FALLBACK="inventory_fallback";
    private static final Set<String> FAMILIES=Set.of("box","stairs","pane","mimic_box","mimic_stairs","connected_cuboid");
    public record Variant(LegacyGeometry.Box bounds,LegacyGeometry.Box inventory,int copyFace,boolean edges,String collision) {
        public Variant {if(copyFace < -1||copyFace>5||!Set.of("inherited","empty","full","unsupported").contains(collision))throw new IllegalArgumentException("Invalid geometry variant");}
    }
    public record ConnectedCuboid(double minWidth,double maxWidth,double minHeight,double maxHeight,
                                  boolean axisLocked,boolean sameMetadataOnly,
                                  boolean connectFullBlocks,boolean connectWood,boolean connectRock) {
        public ConnectedCuboid {
            if(!range(minWidth,maxWidth)||!range(minHeight,maxHeight))throw new IllegalArgumentException("Invalid connected cuboid dimensions");
        }
        public double size(){return maxWidth-minWidth;}
        private static boolean range(double min,double max){return Double.isFinite(min)&&Double.isFinite(max)&&min>=0&&max<=1&&min<max;}
    }
    public record Rule(String id,String family,boolean opaque,Map<Integer,Variant> variants,ConnectedCuboid connectedCuboid,boolean modelOwned) {
        public Rule {
            if(!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")||!FAMILIES.contains(family)||variants.isEmpty()||variants.size()>16)throw new IllegalArgumentException("Invalid geometry rule");
            if(Arrays.stream(id.split(":",2)[1].split("/")).anyMatch(v->v.equals("..")||v.equals(".")))throw new IllegalArgumentException("Invalid path segment");
            if(family.equals("connected_cuboid")!=(connectedCuboid!=null))throw new IllegalArgumentException("Connected cuboid config mismatch");
            variants=Map.copyOf(variants);
        }
        public Rule(String id,String family,boolean opaque,Map<Integer,Variant> variants,ConnectedCuboid connectedCuboid){
            this(id,family,opaque,variants,connectedCuboid,false);
        }
        public Rule(String id,String family,boolean opaque,Map<Integer,Variant> variants){this(id,family,opaque,variants,null,false);}
        public boolean stairs(){return family.equals("stairs")||family.equals("mimic_stairs");}
        public boolean mimic(){return family.startsWith("mimic_");}
        public boolean pane(){return family.equals("pane");}
        public boolean connected(){return family.equals("connected_cuboid");}
        public Variant variant(int metadata){return variants.get(metadata);}
    }
    private LegacyGeometrySpec() { }
    public static Map<String,Rule> parse(JsonObject root) {
        if(integer(root.get("schemaVersion"))!=1||!root.has("blocks"))throw new IllegalArgumentException("Unknown geometry schema");
        Map<String,Rule> out=new LinkedHashMap<>();
        for(var entry:root.getAsJsonObject("blocks").entrySet()) {
            if(out.size()>=4096)throw new IllegalArgumentException("Geometry registry budget");
            JsonObject o=entry.getValue().getAsJsonObject();String family=o.get("family").getAsString();Map<Integer,Variant> variants=new LinkedHashMap<>();
            for(var ve:o.getAsJsonObject("variants").entrySet()){
                int meta=Integer.parseInt(ve.getKey());if(meta<0||meta>15||!ve.getKey().equals(String.valueOf(meta)))throw new IllegalArgumentException("Metadata outside legacy range");
                JsonObject v=ve.getValue().getAsJsonObject();int copy=integer(v.get("copyFace"));
                boolean fallback=v.has("materialMode");
                if(fallback&&(!v.get("materialMode").isJsonPrimitive()
                        ||!v.get("materialMode").getAsJsonPrimitive().isString()
                        ||!INVENTORY_FALLBACK.equals(v.get("materialMode").getAsString())))
                    throw new IllegalArgumentException("Unknown geometry material mode");
                if(family.startsWith("mimic_")?(copy<0&&!fallback):(copy!=-1||fallback))
                    throw new IllegalArgumentException("Mimic without source direction or explicit material fallback");
                if(fallback&&(copy!=-1||!v.has("materialFallbackReason")||!v.get("materialFallbackReason").isJsonPrimitive()
                        ||!v.get("materialFallbackReason").getAsJsonPrimitive().isString()||v.get("materialFallbackReason").getAsString().isBlank()))
                    throw new IllegalArgumentException("Invalid inventory material fallback");
                variants.put(meta,new Variant(box(v.getAsJsonArray("bounds")),box(v.getAsJsonArray("inventoryBounds")),copy,v.get("edges").getAsBoolean(),v.get("collision").getAsString()));
            }
            ConnectedCuboid connected=null;
            if(family.equals("connected_cuboid")){
                JsonObject c=o.has("connectedCuboid")&&o.get("connectedCuboid").isJsonObject()?o.getAsJsonObject("connectedCuboid"):null;
                if(c==null)throw new IllegalArgumentException("Missing connected cuboid config");
                connected=new ConnectedCuboid(number(c,"minWidth"),number(c,"maxWidth"),number(c,"minHeight"),number(c,"maxHeight"),
                        bool(c,"axisLocked"),bool(c,"sameMetadataOnly"),bool(c,"connectFullBlocks"),bool(c,"connectWood"),bool(c,"connectRock"));
            }else if(o.has("connectedCuboid"))throw new IllegalArgumentException("Unexpected connected cuboid config");
            out.put(entry.getKey(),new Rule(entry.getKey(),family,o.get("opaque").getAsBoolean(),variants,connected,o.has("modelOwned")&&bool(o,"modelOwned")));
        }
        return Map.copyOf(out);
    }
    private static LegacyGeometry.Box box(JsonArray a){List<Double> values=new ArrayList<>();for(var n:a)values.add(n.getAsDouble());return LegacyGeometry.Box.from(values);}
    private static int integer(JsonElement e){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Numeric integer required");return e.getAsBigDecimal().intValueExact();}
    private static double number(JsonObject o,String key){JsonElement e=o.get(key);if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Missing numeric "+key);double v=e.getAsDouble();if(!Double.isFinite(v))throw new IllegalArgumentException("Non-finite "+key);return v;}
    private static boolean bool(JsonObject o,String key){JsonElement e=o.get(key);if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Missing boolean "+key);return e.getAsBoolean();}
}
