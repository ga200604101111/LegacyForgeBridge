package dev.yinghuang.legacyforgebridge.rev308;

import dev.yinghuang.legacyforgebridge.behavior.LegacyBehaviorRuntime;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1839;
import net.minecraft.class_1937;
import net.minecraft.class_1657;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_9334;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Forge 1.7 ItemSword's inherited BLOCK use fallback, restricted to source-classified converted swords. */
public final class Rev308SwordBridge {
    private static final Set<String> SWORD_DESCRIPTION_KEYS=ConcurrentHashMap.newKeySet();
    private Rev308SwordBridge() {}
    public static void configure(String kind, String descriptionKey, class_1792.class_1793 properties) {
        if (!"sword".equals(kind) || descriptionKey==null || descriptionKey.isBlank()) return;
        SWORD_DESCRIPTION_KEYS.add(descriptionKey);
        properties.method_57349(class_9334.field_56396, LegacyBehaviorRuntime.legacyBlocking());
    }
    public static boolean isConvertedSword(class_1799 stack) {
        return stack != null && !stack.method_7960()
                && SWORD_DESCRIPTION_KEYS.contains(stack.method_7909().method_7876());
    }
    public static class_1269 afterUse(class_1269 old, class_1937 level, class_1657 player, class_1268 hand) {
        if (old != class_1269.field_5811 || player == null || !isConvertedSword(player.method_5998(hand))) return old;
        player.method_6019(hand);
        return class_1269.field_21466;
    }
    public static class_1839 afterAction(class_1799 stack, class_1839 old) {
        return old == class_1839.field_8952 && isConvertedSword(stack) ? class_1839.field_8949 : old;
    }
    public static int afterDuration(class_1799 stack, int original) {
        return original <= 0 && isConvertedSword(stack) ? 72000 : original;
    }
}
