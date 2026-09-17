# 2026-09-17 — Variant snowball projectile proof IR

## Scope

This slice follows converter revision `2026-09-17.100` and starts the first generic custom projectile family without reclassifying custom projectiles as vanilla snowballs.

Converter revision: `2026-09-17.101`.

## Proven source surface

`LegacyVariantSnowballAnalyzer` admits only a narrow source shape:

- a registered item reaches legacy `ItemSnowball`;
- the item declares the 1.7 right-click callback;
- that callback directly constructs a source `EntitySnowball` subclass and immediately passes it to `World.spawnEntityInWorld`;
- constructor world and thrower arguments are dataflow-proven from the callback's current world/player parameters;
- the projectile selector argument is dataflow-proven from `Map.get(Integer.valueOf(stack.getItemDamage()))` and an exact selector cast;
- the projectile constructor stores that selector argument into its own selector field;
- the selector type is an enum whose constants use one canonical `(String, ordinal, id, damage)` constructor;
- selector id and byte-damage getters are direct field getters, and the constructor writes the corresponding parameters to those fields;
- the custom projectile declares the legacy impact callback.

The analyzer materializes the enum field, legacy metadata id and base damage for every proven selector constant.

## Intentional runtime boundary

This slice is proof IR only. `legacyforgebridge/variant-snowball-proof.json` explicitly records:

- `metadataSelectorLookupProven=true`;
- `projectileSelectorStorageProven=true`;
- `selectorEnumConstantsProven=true`;
- `impactSemanticsComplete=false`;
- `runtimeImplementationWired=false`.

Every variant is initially marked `impactEffect=UNCOMPILED`.

That boundary is deliberate: custom impact behavior may include damage, potion effects, teleportation, particle/sound presentation or other gameplay logic. The converter must prove those branches before it generates a modern projectile runtime. It must not silently substitute `SnowballItem` / vanilla snowball behavior.

## Bamboo exact-corpus target

The checksum-pinned Bamboo corpus has one important family matching this source shape:

- registered item: `snowball`;
- item class: `ruby/bamboo/item/ItemDirtySnowball`;
- projectile: `ruby/bamboo/entity/EntityDirtySnowball`;
- selector: `ruby/bamboo/entity/EnumDirtySnowball`.

The selector table contains ten metadata variants. The exact-corpus regression requires ids `0..9` and base damages:

`1, 2, 3, 8, 6, 1, 0, 0, 0, 0`.

No Bamboo package/class/registry name is used by production analysis; those names appear only in the checksum-pinned exact regression.

## Regression coverage

Normal CI uses an unrelated `foreign/*` synthetic fixture and proves:

- direct metadata selector lookup;
- custom projectile construction/spawn binding;
- projectile selector field storage;
- enum constructor/getter identity;
- constant selector id/damage extraction;
- proof sidecar remains runtime-closed.

The exact Bamboo test is tagged `exact-corpus` and validates the real DirtySnowball table when the pinned corpus JAR is supplied.

## Next gate

The next safe projectile slice is impact-branch classification. It should independently prove:

- thrown damage application;
- bounded potion-effect branches;
- teleport branch semantics;
- termination and client presentation effects;
- item consumption/sound and dispenser spawn entry points.

Only after every admitted metadata selector has a proven impact program should a modern projectile EntityType/item runtime be generated. Whole Bamboo conversion remains `PARTIAL`.
