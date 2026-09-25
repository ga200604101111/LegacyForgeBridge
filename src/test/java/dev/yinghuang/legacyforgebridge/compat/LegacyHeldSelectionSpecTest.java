package dev.yinghuang.legacyforgebridge.compat;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class LegacyHeldSelectionSpecTest {
    private static final List<Double> FULL=List.of(0D,0D,0D,1D,1D,1D),EMPTY=List.of(0D,0D,0D,0D,0D,0D);
    @Test void currentHandControlsEveryMetadataStateWithoutATick(){
        var rule=new LegacyHeldSelectionSpec(FULL,EMPTY);
        for(int metadata=0;metadata<16;metadata++){
            assertEquals(EMPTY,rule.select(false));assertEquals(FULL,rule.select(true));
            assertEquals(EMPTY,rule.select(false));
        }
    }
    @Test void keepsArbitrarySourceProvenBoundsRatherThanInventingFullCubes(){
        List<Double> partial=List.of(.25D,0D,.25D,.75D,1D,.75D);
        assertEquals(partial,new LegacyHeldSelectionSpec(FULL,partial).select(false));
    }
    @Test void oldCandidatesHaveNoNewSelectionClaim(){assertNull(LegacyHeldSelectionSpec.parse(new JsonObject()));}
    @Test void validatesPairedFiniteBoundsAndRejectsPartialSidecars(){
        JsonObject o=new JsonObject();o.add("heldSelectionBounds",new Gson().toJsonTree(FULL));
        assertThrows(IllegalArgumentException.class,()->LegacyHeldSelectionSpec.parse(o));
        o.add("unheldSelectionBounds",new Gson().toJsonTree(EMPTY));
        assertEquals(FULL,LegacyHeldSelectionSpec.parse(o).select(true));
        o.getAsJsonArray("heldSelectionBounds").set(0,new JsonPrimitive("0"));
        assertThrows(IllegalArgumentException.class,()->LegacyHeldSelectionSpec.parse(o));
        assertThrows(IllegalArgumentException.class,()->new LegacyHeldSelectionSpec(List.of(Double.NaN,0D,0D,1D,1D,1D),EMPTY));
        assertThrows(IllegalArgumentException.class,()->new LegacyHeldSelectionSpec(List.of(1D,0D,0D,0D,1D,1D),EMPTY));
    }
}
