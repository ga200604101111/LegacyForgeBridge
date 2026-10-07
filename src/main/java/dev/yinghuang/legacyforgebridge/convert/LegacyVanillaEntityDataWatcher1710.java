package dev.yinghuang.legacyforgebridge.convert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pinned vanilla Minecraft 1.7.10 DataWatcher definitions needed when a source mod class inherits
 * platform-owned watcher state that is not declared in the mod JAR.
 *
 * <p>The table stores only watcher entries owned by each exact vanilla class plus a pinned 1.7.10
 * parent relation. {@link #inheritedForExternalBase(String)} expands the transitive platform
 * schema. Legacy Entity base indices 0/1 are intentionally excluded because the FML spawn bridge
 * already handles those two platform fields directly.</p>
 */
public final class LegacyVanillaEntityDataWatcher1710 {
    public record Entry(int index,String valueKind,Object defaultValue,String declaredBy) { }

    private static final String ENTITY = "net/minecraft/entity/Entity";
    private static final String LIVING_BASE = "net/minecraft/entity/EntityLivingBase";
    private static final String LIVING = "net/minecraft/entity/EntityLiving";
    private static final String CREATURE = "net/minecraft/entity/EntityCreature";
    private static final String AGEABLE = "net/minecraft/entity/EntityAgeable";
    private static final String FLYING = "net/minecraft/entity/EntityFlying";

    private static final String MOB = "net/minecraft/entity/monster/EntityMob";
    private static final String SPIDER = "net/minecraft/entity/monster/EntitySpider";
    private static final String SLIME = "net/minecraft/entity/monster/EntitySlime";
    private static final String GHAST = "net/minecraft/entity/monster/EntityGhast";
    private static final String ZOMBIE = "net/minecraft/entity/monster/EntityZombie";

    private static final String ANIMAL = "net/minecraft/entity/passive/EntityAnimal";
    private static final String AMBIENT = "net/minecraft/entity/passive/EntityAmbientCreature";
    private static final String COW = "net/minecraft/entity/passive/EntityCow";
    private static final String PIG = "net/minecraft/entity/passive/EntityPig";
    private static final String SHEEP = "net/minecraft/entity/passive/EntitySheep";
    private static final String TAMEABLE = "net/minecraft/entity/passive/EntityTameable";
    private static final String WOLF = "net/minecraft/entity/passive/EntityWolf";

    private static final String THROWABLE = "net/minecraft/entity/projectile/EntityThrowable";
    private static final String ARROW = "net/minecraft/entity/projectile/EntityArrow";

    private static final Map<String,String> PARENT = Map.ofEntries(
            Map.entry(LIVING_BASE, ENTITY),
            Map.entry(LIVING, LIVING_BASE),
            Map.entry(CREATURE, LIVING),
            Map.entry(AGEABLE, CREATURE),
            Map.entry(FLYING, LIVING),

            Map.entry(MOB, CREATURE),
            Map.entry(SPIDER, MOB),
            Map.entry(SLIME, LIVING),
            Map.entry(GHAST, FLYING),
            Map.entry(ZOMBIE, MOB),

            Map.entry(ANIMAL, AGEABLE),
            Map.entry(AMBIENT, LIVING),
            Map.entry(COW, ANIMAL),
            Map.entry(PIG, ANIMAL),
            Map.entry(SHEEP, ANIMAL),
            Map.entry(TAMEABLE, ANIMAL),
            Map.entry(WOLF, TAMEABLE),

            Map.entry(THROWABLE, ENTITY),
            Map.entry(ARROW, ENTITY)
    );

    /** Entries declared by the exact vanilla class; parent entries are not duplicated here. */
    private static final Map<String,List<Entry>> OWNED = Map.ofEntries(
            Map.entry(LIVING_BASE, List.of(
                    new Entry(6, "float", 1.0F, LIVING_BASE),
                    new Entry(7, "int", 0, LIVING_BASE),
                    new Entry(8, "byte", (byte)0, LIVING_BASE),
                    new Entry(9, "byte", (byte)0, LIVING_BASE)
            )),
            Map.entry(LIVING, List.of(
                    new Entry(10, "string", "", LIVING),
                    new Entry(11, "byte", (byte)0, LIVING)
            )),
            Map.entry(AGEABLE, List.of(
                    new Entry(12, "int", 0, AGEABLE)
            )),
            Map.entry(TAMEABLE, List.of(
                    new Entry(16, "byte", (byte)0, TAMEABLE),
                    new Entry(17, "string", "", TAMEABLE)
            )),
            Map.entry(WOLF, List.of(
                    new Entry(18, "float", 1.0F, WOLF),
                    new Entry(19, "byte", (byte)0, WOLF),
                    new Entry(20, "byte", (byte)14, WOLF)
            )),
            Map.entry(PIG, List.of(
                    new Entry(16, "byte", (byte)0, PIG)
            )),
            Map.entry(SHEEP, List.of(
                    new Entry(16, "byte", (byte)0, SHEEP)
            )),
            Map.entry(SPIDER, List.of(
                    new Entry(16, "byte", (byte)0, SPIDER)
            )),
            Map.entry(SLIME, List.of(
                    new Entry(16, "byte", (byte)1, SLIME)
            )),
            Map.entry(GHAST, List.of(
                    new Entry(16, "byte", (byte)0, GHAST)
            )),
            Map.entry(ZOMBIE, List.of(
                    new Entry(12, "byte", (byte)0, ZOMBIE),
                    new Entry(13, "byte", (byte)0, ZOMBIE),
                    new Entry(14, "byte", (byte)0, ZOMBIE)
            )),
            Map.entry(ARROW, List.of(
                    new Entry(16, "byte", (byte)0, ARROW)
            ))
    );

    private LegacyVanillaEntityDataWatcher1710() { }

    /**
     * Returns the complete platform-owned watcher schema for one exact first external vanilla base.
     * Entity base 0/1 are omitted because the existing FML bridge handles them separately.
     */
    public static List<Entry> inheritedForExternalBase(String externalBase) {
        if (externalBase == null || externalBase.isBlank()) return List.of();

        ArrayList<Entry> ordered = new ArrayList<>();
        LinkedHashSet<String> seenClasses = new LinkedHashSet<>();
        LinkedHashMap<Integer,Entry> byIndex = new LinkedHashMap<>();

        String current = externalBase;
        while (current != null && seenClasses.add(current)) {
            for (Entry entry : OWNED.getOrDefault(current, List.of())) {
                Entry prior = byIndex.putIfAbsent(entry.index(), entry);
                if (prior != null && (!prior.valueKind().equals(entry.valueKind())
                        || !java.util.Objects.equals(prior.defaultValue(), entry.defaultValue()))) {
                    throw new IllegalStateException("Conflicting pinned vanilla 1.7.10 watcher index "
                            + entry.index() + " while expanding " + externalBase);
                }
            }
            current = PARENT.get(current);
        }

        ordered.addAll(byIndex.values());
        ordered.sort(java.util.Comparator.comparingInt(Entry::index));
        return List.copyOf(ordered);
    }

    /** True only when the exact vanilla owner has this watcher in its transitive 1.7.10 schema. */
    public static boolean matchesPlatformAccess(String owner,int index,String valueKind) {
        if (owner == null || valueKind == null) return false;
        for (Entry entry : inheritedForExternalBase(owner)) {
            if (entry.index() == index && entry.valueKind().equals(valueKind)) return true;
        }
        return false;
    }

    public static boolean supportsExternalBase(String externalBase) {
        return externalBase != null && supportedExternalBases().contains(externalBase);
    }

    /** Exact vanilla classes for which a transitive platform schema is pinned. */
    public static Set<String> supportedExternalBases() {
        LinkedHashSet<String> output = new LinkedHashSet<>(PARENT.keySet());
        output.add(ENTITY);
        return Collections.unmodifiableSet(output);
    }
}
