package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** Source-proven ray-selection bounds. Physical collision and outline metadata are separate. */
public record LegacyHeldSelectionSpec(List<Double> held,List<Double> unheld) {
    public LegacyHeldSelectionSpec {
        held=validate(held);unheld=validate(unheld);
    }
    public List<Double> select(boolean holdingOwnItem){return holdingOwnItem?held:unheld;}
    public static LegacyHeldSelectionSpec parse(JsonObject rule){
        boolean held=rule.has("heldSelectionBounds"),unheld=rule.has("unheldSelectionBounds");
        if(!held&&!unheld)return null; // Pre-revision candidate: no new shape claim.
        if(!held||!unheld)throw new IllegalArgumentException("Incomplete held selection bounds");
        return new LegacyHeldSelectionSpec(read(rule.get("heldSelectionBounds")),read(rule.get("unheldSelectionBounds")));
    }
    private static List<Double> read(JsonElement value){
        if(value==null||!value.isJsonArray()||value.getAsJsonArray().size()!=6)
            throw new IllegalArgumentException("Selection bounds must have six coordinates");
        List<Double> result=new ArrayList<>(6);
        for(JsonElement coordinate:value.getAsJsonArray()){
            if(!coordinate.isJsonPrimitive()||!coordinate.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Selection coordinate is not numeric");
            result.add(coordinate.getAsDouble());
        }
        return result;
    }
    private static List<Double> validate(List<Double> values){
        if(values==null||values.size()!=6)throw new IllegalArgumentException("Missing selection bounds");
        for(Double value:values)if(value==null||!Double.isFinite(value)||value<0D||value>1D)
            throw new IllegalArgumentException("Invalid selection coordinate");
        for(int axis=0;axis<3;axis++)if(values.get(axis)>values.get(axis+3))
            throw new IllegalArgumentException("Inverted selection bounds");
        return List.copyOf(values);
    }
}
