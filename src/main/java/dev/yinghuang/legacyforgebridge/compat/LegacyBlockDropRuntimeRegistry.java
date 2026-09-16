package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyBlockDropRuntimeRulePass;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime registry for proof-complete 1.7.x block self-drop rules.
 *
 * <p>The conversion pass has already decided source eligibility. Runtime loading is intentionally
 * strict and fail-closed: a rule is executable only when the sidecar explicitly declares that the
 * bridge implementation is wired and every proof/shaping field still matches an admitted runtime
 * mode.</p>
 */
public final class LegacyBlockDropRuntimeRegistry {
    public static final int SCHEMA_VERSION = 1;
    private static final Map<Identifier, Rule> RULES = new ConcurrentHashMap<>();
    private static final Set<String> LOAD_ATTEMPTED = ConcurrentHashMap.newKeySet();

    public record Rule(Identifier id, boolean metadataIndependent, List<Integer> legacyDamageByBlockMeta) {
        public Rule {
            if (id == null) throw new IllegalArgumentException("Missing block id");
            legacyDamageByBlockMeta = List.copyOf(legacyDamageByBlockMeta);
            if (legacyDamageByBlockMeta.size() != 16) {
                throw new IllegalArgumentException("Legacy block-drop damage table must contain 16 entries");
            }
            for (int damage : legacyDamageByBlockMeta) {
                if (damage < 0 || damage > 15) {
                    throw new IllegalArgumentException("Legacy block-drop item damage outside 0..15: " + damage);
                }
            }
        }

        public int legacyDamage(int blockMeta) {
            if (blockMeta < 0 || blockMeta > 15) {
                throw new IllegalArgumentException("Legacy block metadata outside 0..15: " + blockMeta);
            }
            return legacyDamageByBlockMeta.get(blockMeta);
        }
    }

    private LegacyBlockDropRuntimeRegistry() { }

    public static Rule rule(Identifier blockId) {
        if (blockId == null) return null;
        Rule current = RULES.get(blockId);
        if (current != null) return current;
        if (LOAD_ATTEMPTED.add(blockId.getNamespace())) {
            loadMod(blockId.getNamespace());
        }
        return RULES.get(blockId);
    }

    public static boolean hasRule(Identifier blockId) {
        return rule(blockId) != null;
    }

    /**
     * Exact legacy per-affected-block explosion probability for admitted rules.
     *
     * <p>Minecraft/Forge 1.7.10 uses {@code rand.nextFloat() <= 1.0F / explosionSize}. The caller
     * supplies the modern level random sample so this method remains pure and directly testable.</p>
     */
    public static boolean shouldDropFromExplosion(Identifier blockId, float randomSample, float explosionRadius) {
        return hasRule(blockId) && randomSample <= 1.0F / explosionRadius;
    }

    /**
     * Loads a converted mod's sidecar lazily on first drop lookup. Gameplay lookups happen after
     * generated registry bootstrap, so the Block/BlockItem identity checks below can fail closed.
     */
    private static void loadMod(String modId) {
        ModContainer container = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (container == null) return;
        var path = container.findPath(LegacyBlockDropRuntimeRulePass.OUTPUT_PATH);
        if (path.isEmpty()) return;

        try (Reader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (integer(root, "schemaVersion", -1) != SCHEMA_VERSION) {
                LegacyForgeBridge.LOGGER.warn(
                        "Ignoring converted block-drop runtime rules with unsupported schema: mod={}, schema={}",
                        modId,
                        integer(root, "schemaVersion", -1)
                );
                return;
            }
            if (!bool(root, "runtimeImplementationWired")) {
                LegacyForgeBridge.LOGGER.info(
                        "Converted block-drop rules are proof-only and remain inactive: mod={}",
                        modId
                );
                return;
            }

            JsonArray values = root.getAsJsonArray("rules");
            if (values == null) return;
            int loaded = 0;
            int rejected = 0;
            for (JsonElement element : values) {
                if (!element.isJsonObject()) {
                    rejected++;
                    continue;
                }
                Rule parsed = parseRule(element.getAsJsonObject());
                if (parsed == null || !parsed.id().getNamespace().equals(modId)) {
                    rejected++;
                    continue;
                }

                Object block = BuiltInRegistries.BLOCK.getValue(parsed.id());
                Object item = BuiltInRegistries.ITEM.getValue(parsed.id());
                if (!(block instanceof ConvertedLegacyBlock converted)
                        || !(item instanceof BlockItem blockItem)
                        || blockItem.getBlock() != converted) {
                    rejected++;
                    LegacyForgeBridge.LOGGER.warn(
                            "Ignoring converted block-drop rule without matching generated Block/BlockItem: {}",
                            parsed.id()
                    );
                    continue;
                }

                RULES.put(parsed.id(), parsed);
                loaded++;
            }

            if (loaded > 0 || rejected > 0) {
                LegacyForgeBridge.LOGGER.info(
                        "Loaded converted legacy block-drop runtime rules: mod={}, loaded={}, rejected={}",
                        modId,
                        loaded,
                        rejected
                );
            }
        } catch (Exception exception) {
            LegacyForgeBridge.LOGGER.error(
                    "Failed to load converted legacy block-drop runtime rules for {}",
                    modId,
                    exception
            );
        }
    }

    static Rule parseRule(JsonObject object) {
        String idValue = string(object, "id");
        String mode = string(object, "mode");
        boolean commonProof = bool(object, "normalSilkSelfDropProofComplete")
                || bool(object, "normalSilkStaticSelfDropProofComplete");
        if (idValue == null
                || !"SELF_BLOCK_ITEM".equals(string(object, "dropKind"))
                || integer(object, "quantity", -1) != 1
                || !"inverse_explosion_radius".equals(string(object, "legacyExplosionChanceMode"))
                || !commonProof
                || !bool(object, "explosionSourceProofComplete")
                || !bool(object, "sourceExplosionDestructionOverrideFree")
                || !bool(object, "explosionDecayFormulaProofComplete")
                || !bool(object, "explosionAffectedSetSourceProofComplete")) {
            return null;
        }

        boolean metadataIndependent = bool(object, "metadataIndependent");
        List<Integer> damages;
        if (LegacyBlockDropRuntimeRulePass.MODE.equals(mode)) {
            if (!metadataIndependent || integer(object, "legacyDamage", -1) != 0) return null;
            damages = Collections.nCopies(16, 0);
        } else if (LegacyBlockDropRuntimeRulePass.METADATA_MODE.equals(mode)) {
            if (metadataIndependent) return null;
            damages = damageTable(object.getAsJsonArray("legacyDamageByBlockMeta"));
            if (damages == null) return null;
        } else {
            return null;
        }

        try {
            return new Rule(Identifier.parse(idValue), metadataIndependent, damages);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static synchronized void installForTests(Identifier id) {
        installForTests(id, Collections.nCopies(16, 0));
    }

    static synchronized void installForTests(Identifier id, List<Integer> legacyDamageByBlockMeta) {
        boolean metadataIndependent = legacyDamageByBlockMeta.stream().allMatch(value -> value == 0);
        RULES.put(id, new Rule(id, metadataIndependent, legacyDamageByBlockMeta));
        LOAD_ATTEMPTED.add(id.getNamespace());
    }

    static synchronized void suppressLoadForTests(String namespace) {
        LOAD_ATTEMPTED.add(namespace);
    }

    static synchronized void clearForTests() {
        RULES.clear();
        LOAD_ATTEMPTED.clear();
    }

    private static List<Integer> damageTable(JsonArray values) {
        if (values == null || values.size() != 16) return null;
        List<Integer> damages = new ArrayList<>(16);
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return null;
            try {
                int damage = value.getAsJsonPrimitive().getAsBigDecimal().intValueExact();
                if (damage < 0 || damage > 15) return null;
                damages.add(damage);
            } catch (ArithmeticException exception) {
                return null;
            }
        }
        return List.copyOf(damages);
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }
}
