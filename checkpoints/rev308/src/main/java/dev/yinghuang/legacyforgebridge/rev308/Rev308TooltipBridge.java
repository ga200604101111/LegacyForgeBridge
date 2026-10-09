package dev.yinghuang.legacyforgebridge.rev308;

import net.minecraft.class_1799;
import net.minecraft.class_1074;
import java.util.List;

/** Display-only preservation of converted 1.7 language .tooltip aliases. */
public final class Rev308TooltipBridge {
    private Rev308TooltipBridge() {}
    public static void append(class_1799 stack, List<String> tooltip) {
        if (stack == null || stack.method_7960() || tooltip == null || tooltip.size() >= 128) return;
        String key = stack.method_7909().method_7876();
        if (key == null || !key.startsWith("lfb.converted.") || !key.endsWith(".name")) return;
        String tooltipKey = key.substring(0, key.length()-5) + ".tooltip";
        if (!class_1074.method_4663(tooltipKey)) return;
        String value = class_1074.method_4662(tooltipKey, new Object[0]);
        if (value != null && !value.isBlank() && value.length() <= 4096 && !tooltip.contains(value)) tooltip.add(value);
    }
}
