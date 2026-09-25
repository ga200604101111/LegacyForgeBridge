package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockActivationEffectPlan;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyBlockActivationEffectsPass;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Applies a finite source-proven local metadata effect, never legacy code or gameplay packets. */
public final class LegacyBlockActivationEffectsRegistry {
    private static final Map<Identifier, LegacyBlockActivationEffectPlan> RULES = new ConcurrentHashMap<>();
    // Legacy flag 2 updates presentation, not neighbors. Modern shape propagation has no
    // equivalent in 1.7 metadata writes, so it must not invent neighbor updates here.
    static final int MODERN_NOTIFY_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private LegacyBlockActivationEffectsRegistry() { }

    public static InteractionResult activate(Identifier id, BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!RULES.containsKey(id)) return null;
        ItemStack held = player.getMainHandItem();
        Identifier heldId = held.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(held.getItem());
        var decision = decision(id, ConvertedLegacyBlock.legacyMeta(state), level.isClientSide(),
                player.isShiftKeyDown(), heldId, hit.getDirection());
        if (decision == null) return null;
        if (decision.writesMetadata()) {
            // Reject stale callback state rather than overwriting a different block or update.
            if (!level.getBlockState(pos).equals(state)) return InteractionResult.FAIL;
            BlockState next = ConvertedLegacyBlock.withLegacyMeta(state, decision.metadata());
            if (!next.equals(state)) level.setBlock(pos, next, MODERN_NOTIFY_FLAGS);
        }
        // A client-side table row preserves ONLY the source's local prediction. The ordinary
        // interaction path still sends its use packet; the legacy server remains authoritative.
        // Server-guarded source callbacks naturally have no write in their client table rows.
        // The source discards setBlockMetadataWithNotify's result, so we do not invent a branch.
        return decision.handled() ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    static LegacyBlockActivationEffectPlan.Decision decision(Identifier id, int meta, boolean client,
            boolean sneaking, Identifier heldId, Direction direction) {
        var plan = RULES.get(id);
        if (plan == null) return null;
        int kind = heldId == null ? LegacyBlockActivationEffectPlan.EMPTY_HAND
                : plan.heldItemId().equals(heldId.toString()) ? LegacyBlockActivationEffectPlan.MATCHING_ITEM
                : LegacyBlockActivationEffectPlan.OTHER_ITEM;
        return plan.evaluate(LegacyBlockPlacementRegistry.legacySide(direction), meta, client, sneaking, kind);
    }

    public static void loadMod(String modId) {
        var container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (container == null) return;
        var path = container.findPath(LegacyBlockActivationEffectsPass.RULES_PATH);
        if (path.isEmpty()) return;
        try (var reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (exactInt(root.get("schemaVersion")) != 1) throw new IllegalArgumentException("Unsupported activation effects schema");
            JsonArray rules = root.getAsJsonArray("rules");
            if (rules == null || rules.size() > 4096) throw new IllegalArgumentException("Missing/over-budget effect rules");
            Map<Identifier, LegacyBlockActivationEffectPlan> staged = new LinkedHashMap<>();
            for (JsonElement element : rules) {
                JsonObject rule = element.getAsJsonObject();
                Identifier id = Identifier.parse(rule.get("id").getAsString());
                if (!id.getNamespace().equals(modId) || !BuiltInRegistries.BLOCK.containsKey(id)
                        || !(BuiltInRegistries.BLOCK.getValue(id) instanceof ConvertedLegacyBlock)) {
                    throw new IllegalArgumentException("Unresolved/non-owned converted block " + id);
                }
                var plan = parsePlan(rule);
                if (!BuiltInRegistries.ITEM.containsKey(Identifier.parse(plan.heldItemId()))) {
                    throw new IllegalArgumentException("Unresolved held item " + plan.heldItemId());
                }
                if (staged.putIfAbsent(id, plan) != null || RULES.containsKey(id)) {
                    throw new IllegalArgumentException("Duplicate activation effect rule " + id);
                }
            }
            // A malformed file publishes nothing; no half-installed sidecar state.
            RULES.putAll(staged);
            LegacyForgeBridge.LOGGER.info("Loaded converted activation effects: mod={}, rules={}", modId, staged.size());
        } catch (Exception exception) {
            LegacyForgeBridge.LOGGER.error("Failed closed loading activation effects for {}", modId, exception);
        }
    }

    static LegacyBlockActivationEffectPlan parsePlan(JsonObject rule) {
        if (exactInt(rule.get("legacyNotifyFlags")) != LegacyBlockActivationEffectPlan.LEGACY_NOTIFY_FLAGS) {
            throw new IllegalArgumentException("Unsupported legacy notification flags");
        }
        JsonArray values = rule.getAsJsonArray("outcomes");
        if (values == null || values.size() != LegacyBlockActivationEffectPlan.INPUT_COUNT) {
            throw new IllegalArgumentException("Incomplete activation effect table");
        }
        List<Integer> outcomes = new ArrayList<>(values.size());
        for (JsonElement value : values) outcomes.add(exactInt(value));
        return new LegacyBlockActivationEffectPlan(rule.get("heldItemId").getAsString(), outcomes);
    }

    private static int exactInt(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected integer number");
        }
        return value.getAsBigDecimal().intValueExact();
    }

    static void installForTests(Identifier id, LegacyBlockActivationEffectPlan plan) { RULES.put(id, plan); }
    static void clearForTests() { RULES.clear(); }
}
