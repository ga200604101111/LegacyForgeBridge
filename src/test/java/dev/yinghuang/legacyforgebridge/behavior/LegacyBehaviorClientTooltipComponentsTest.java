package dev.longyu.legacyforgebridge.behavior;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBehaviorClientTooltipComponentsTest {
    @Test void appendedSuffixPreservesOriginalNameComponentStyleAndInsertionsStayFormatted() {
        List<Component> nativeLines=new ArrayList<>(List.of(
                Component.literal("Blade").withStyle(ChatFormatting.GOLD),
                Component.literal("Stats").withStyle(ChatFormatting.GRAY)));
        var working=new LegacyBehaviorClient.TooltipComponents(nativeLines);
        working.set(0,"Blade Level:3");working.add(1,"§bEcho II");

        assertEquals("Blade",nativeLines.get(0).getString(),"working buffer mutated native tooltip before commit");
        working.commitTo(nativeLines);
        assertEquals(List.of("Blade Level:3","Echo II","Stats"),nativeLines.stream().map(Component::getString).toList());
        assertNotNull(nativeLines.get(0).getStyle().getColor());
        assertEquals(ChatFormatting.GOLD.getColor(),nativeLines.get(0).getStyle().getColor().getValue());
    }
}
