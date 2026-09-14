package dev.longyu.legacyforgebridge.behavior;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBehaviorTooltipEventRuntimeTest {
    @BeforeAll static void bootstrapMinecraft(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}

    @Test void targetFilteringUsesExactPresentationItemAndNeverCommitsTemporaryNbt() {
        String mod="minecraft";
        var stickItem=new LegacyBehaviorApi.Item();var stoneItem=new LegacyBehaviorApi.Item();var wrongRuns=new AtomicInteger();
        try {
            assertTrue(LegacyBehaviorRegistry.begin(mod));
            LegacyBehaviorRegistry.registerPresentationItem("minecraft:stick",stickItem);
            LegacyBehaviorRegistry.registerPresentationItem("minecraft:stone",stoneItem);
            LegacyBehaviorRegistry.registerEvent(mod,"tooltipEvent","minecraft:stick",event->{
                assertSame(stickItem,event.itemStack.item);
                if(event.itemStack.field_77990_d==null)event.itemStack.setTagCompound(new LegacyBehaviorApi.Tag());
                event.itemStack.field_77990_d.setInteger("toolLevel",9);
                event.toolTip.set(0,event.toolTip.get(0)+" Level:9");
            });
            LegacyBehaviorRegistry.registerEvent(mod,"tooltipEvent","minecraft:stone",event->wrongRuns.incrementAndGet());
            LegacyBehaviorRegistry.finish();

            ItemStack nativeStack=new ItemStack(Items.STICK);var lines=new ArrayList<>(List.of("Stick","Stats"));
            assertNull(nativeStack.get(DataComponents.CUSTOM_DATA));
            assertTrue(LegacyBehaviorRuntime.tooltipEvent(nativeStack,lines));
            assertEquals(List.of("Stick Level:9","Stats"),lines);
            assertEquals(0,wrongRuns.get());
            assertNull(nativeStack.get(DataComponents.CUSTOM_DATA),"presentation tooltip callback committed temporary source NBT");
        } finally {LegacyBehaviorRegistry.abort();LegacyBehaviorRegistry.removeMod(mod);}
    }

    @Test void invalidTooltipMutationFailsClosed() {
        String mod="minecraft";
        try {
            assertTrue(LegacyBehaviorRegistry.begin(mod));LegacyBehaviorRegistry.registerPresentationItem("minecraft:stick",new LegacyBehaviorApi.Item());
            LegacyBehaviorRegistry.registerEvent(mod,"tooltipEvent","minecraft:stick",event->event.toolTip.add("x".repeat(4097)));
            LegacyBehaviorRegistry.finish();
            var lines=new ArrayList<>(List.of("Stick"));
            assertFalse(LegacyBehaviorRuntime.tooltipEvent(new ItemStack(Items.STICK),lines));
            assertEquals(List.of("Stick"),lines);
        } finally {LegacyBehaviorRegistry.abort();LegacyBehaviorRegistry.removeMod(mod);}
    }
}
