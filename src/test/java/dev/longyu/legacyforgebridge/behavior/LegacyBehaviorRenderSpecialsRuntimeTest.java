package dev.longyu.legacyforgebridge.behavior;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyBehaviorRenderSpecialsRuntimeTest {
    @Test void emptyPlayerDisplayNameCancelsWithoutMutatingSourceEntity() {
        String mod="render_runtime";
        try{
            assertTrue(LegacyBehaviorRegistry.begin(mod));
            LegacyBehaviorRegistry.registerEvent(mod,"renderSpecialsPre",event->{
                if(event.entityLiving instanceof LegacyBehaviorApi.Player player)event.setCanceled(player.getDisplayName().equals(""));
            });
            LegacyBehaviorRegistry.finish();
            var blank=new LegacyBehaviorApi.Player();blank.displayName="";assertTrue(LegacyBehaviorRuntime.renderSpecialsPrePrograms(blank));assertEquals("",blank.displayName);
            var named=new LegacyBehaviorApi.Player();named.displayName="Alice";assertFalse(LegacyBehaviorRuntime.renderSpecialsPrePrograms(named));assertEquals("Alice",named.displayName);
            assertFalse(LegacyBehaviorRuntime.renderSpecialsPrePrograms(new LegacyBehaviorApi.Living()));
        }finally{LegacyBehaviorRegistry.abort();LegacyBehaviorRegistry.removeMod(mod);}
    }
}
