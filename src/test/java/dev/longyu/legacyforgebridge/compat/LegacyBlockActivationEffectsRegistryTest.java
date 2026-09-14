package dev.longyu.legacyforgebridge.compat;

import com.google.gson.*;
import dev.longyu.legacyforgebridge.convert.LegacyBlockActivationEffectPlan;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBlockActivationEffectsRegistryTest {
    private static final Identifier BLOCK = Identifier.parse("fixture:rotation");
    private static final Identifier TOOL = Identifier.parse("fixture:selector");
    @AfterEach void clear() { LegacyBlockActivationEffectsRegistry.clearForTests(); }

    @Test void mainHandIdentityAndAllDirectionsDriveExactMetadata() {
        LegacyBlockActivationEffectsRegistry.installForTests(BLOCK, table(false));
        Direction[] directions = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (int side = 0; side < 6; side++) {
            var result = LegacyBlockActivationEffectsRegistry.decision(BLOCK, 3, true, false, TOOL, directions[side]);
            assertTrue(result.handled()); assertEquals((side / 2) << 2, result.metadata());
            assertFalse(LegacyBlockActivationEffectsRegistry.decision(BLOCK, 3, false, false, null, directions[side]).writesMetadata());
            assertFalse(LegacyBlockActivationEffectsRegistry.decision(BLOCK, 3, false, false, Identifier.parse("fixture:other"), directions[side]).handled());
        }
    }

    @Test void serverOnlySourceDoesNotProduceClientMutation() {
        LegacyBlockActivationEffectsRegistry.installForTests(BLOCK, table(true));
        assertFalse(LegacyBlockActivationEffectsRegistry.decision(BLOCK, 3, true, false, TOOL, Direction.NORTH).writesMetadata());
        assertEquals(4, LegacyBlockActivationEffectsRegistry.decision(BLOCK, 3, false, false, TOOL, Direction.NORTH).metadata());
    }

    @Test void absentRulesLeaveExistingReadOnlyPathUntouched() {
        assertNull(LegacyBlockActivationEffectsRegistry.decision(BLOCK, 0, false, false, TOOL, Direction.UP));
    }

    @Test void sidecarRoundTripPreservesEveryOutcomeAndRejectsFractionalFlagsAndValues() {
        JsonObject rule = json();
        var parsed = LegacyBlockActivationEffectsRegistry.parsePlan(rule);
        assertEquals(table(false), parsed);
        rule.addProperty("legacyNotifyFlags", 2.5);
        assertThrows(ArithmeticException.class, () -> LegacyBlockActivationEffectsRegistry.parsePlan(rule));
        rule.addProperty("legacyNotifyFlags", 2);
        rule.getAsJsonArray("outcomes").set(2, new JsonPrimitive(3.2));
        assertThrows(ArithmeticException.class, () -> LegacyBlockActivationEffectsRegistry.parsePlan(rule));
    }

    @Test void malformedTableAndNotificationSemanticsFailClosed() {
        JsonObject rule = json(); rule.addProperty("legacyNotifyFlags", 3);
        assertThrows(IllegalArgumentException.class, () -> LegacyBlockActivationEffectsRegistry.parsePlan(rule));
        rule.addProperty("legacyNotifyFlags", 2); rule.getAsJsonArray("outcomes").remove(0);
        assertThrows(IllegalArgumentException.class, () -> LegacyBlockActivationEffectsRegistry.parsePlan(rule));
        JsonObject nonnumeric = json(); nonnumeric.addProperty("legacyNotifyFlags", "2");
        assertThrows(IllegalArgumentException.class, () -> LegacyBlockActivationEffectsRegistry.parsePlan(nonnumeric));
    }

    private static JsonObject json() {
        JsonObject rule = new JsonObject(); rule.addProperty("heldItemId", TOOL.toString()); rule.addProperty("legacyNotifyFlags", 2);
        JsonArray outcomes = new JsonArray(); table(false).outcomes().forEach(outcomes::add); rule.add("outcomes", outcomes); return rule;
    }
    private static LegacyBlockActivationEffectPlan table(boolean serverOnly) {
        var outcomes = new ArrayList<Integer>();
        for (int side = 0; side < 6; side++) for (int meta = 0; meta < 16; meta++)
            for (int client = 0; client < 2; client++) for (int sneak = 0; sneak < 2; sneak++) for (int held = 0; held < 3; held++) {
                int write = held == 2 && (!serverOnly || client == 0) ? ((side / 2) << 2) | ((meta + 1) & 3) : -1;
                outcomes.add(new LegacyBlockActivationEffectPlan.Decision(held == 2, write).encode());
            }
        return new LegacyBlockActivationEffectPlan(TOOL.toString(), outcomes);
    }
}
