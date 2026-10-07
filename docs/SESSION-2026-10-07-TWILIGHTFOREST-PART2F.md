# 2026-10-07 — Twilight Forest 2.3.8 compatibility, Part 2F-1 blocker census

Branch: `feature/generic-conversion-iyamato-corpus3`

Corpus:
`twilightforest-1.7.10-2.3.8-tw.jar`

Scope: census only. No AI, renderer, movement, combat, or entity gameplay adapter is added by this checkpoint.

## Why this census exists

Parts 2C–2E closed the DataWatcher side of the registered entity surface:

- 77/77 source watcher schemas proven;
- 77/77 registered entity watcher access surfaces proven;
- 94/94 source runtime DataWatcher calls accounted;
- 0 unresolved watcher methods;
- 396 inherited vanilla watcher entry instances plus 37 source-owned instances;
- 0 platform/source watcher index conflicts.

The next blocker is therefore not watcher discovery. The current behavior/construction/presentation
admission gates were audited against all 77 registrations before implementing any new entity family.

## Current plain-entity admission: 0 / 77

The current `PLAIN_ENTITY_SYNCHED_DATA_ONLY` family admits **none** of the 77 Twilight Forest
registrations.

### Behavior blockers

Every registered entity has at least one source instance method which the current
`LegacyEntityBehaviorSurfaceAnalyzer` cannot classify into an admitted callback/runtime mapping:

**77 / 77 have `unclassified-source-instance-methods`.**

Also:

**74 / 77** have a first external superclass other than exact vanilla
`net/minecraft/entity/Entity`, so the current plain family adds
`unsupported-external-entity-base`.

No entity is therefore blocked *solely* by the external-base gate; all 77 already have at least one
source-method blocker.

Counts of registered entities whose effective source callback remains unsupported by the current
plain admission rules:

- TICK: 34
- READ_NBT: 22
- WRITE_NBT: 22
- HURT: 21
- CAN_TRIGGER_WALKING: 9
- INTERACT: 8
- GET_PARTS: 5
- MOUNTED_Y_OFFSET: 5
- CAN_COLLIDE: 2
- COLLISION_BOX: 1
- READ_SPAWN_DATA: 1
- WRITE_SPAWN_DATA: 1

The existing constant-override adapter was respected during the census: exact constant
CAN_PUSH/CAN_COLLIDE/CAN_ATTACK_WITH_ITEM/RENDER_DISTANCE/COLLISION_BORDER_SIZE bodies are not
counted as unsupported.

### The three exact Entity-base registrations

Only three registrations have first external base exactly `Entity`:

- `tfcharmeffect` / `EntityTFCharmEffect`
- `tffallingice` / `EntityTFFallingIce`
- `tfslideblock` / `EntityTFSlideBlock`

All three still have source behavior blockers.

Their construction is also not admission-clean:

- CharmEffect: constant size 0.25 × 0.25, but constructor has one unmapped method call
  (`setItemID`).
- FallingIce: constant size 2.98 × 2.98, but constructor has two source field writes
  (`hurtAmount`, `hurtMax`).
- SlideBlock: constant size 0.98 × 0.98, but constructor has three source field writes
  (`canDropItem` plus two inherited Entity fields).

So even the direct-Entity slice remains 0/3 for the current plain runtime.

## Construction census

Across all 77:

- proven source `(World)V` constructor present: 77
- source-owned `setSize(float,float)` override present: 0
- size proof blocked by current external-base restriction: 74
- direct-Entity constant size proof complete: 3
- direct-Entity registrations with unmapped constructor effects: 3
- fully clean for current plain construction admission: 0

The current construction analyzer deliberately refuses to pretend that constructors inherited from
Living/Mob/Animal/Ghast/etc. have the same semantics as direct Entity construction.

## Renderer registration census

`TFClientProxy` contains 82 entity-renderer registrations total, including helper/unregistered
entity types.

Among the 77 registered mod entities, 74 have a direct renderer binding in the source proxy.

The three without a direct binding are:

- `EntityTFHostileWolf`
- `EntityTFArmoredGiant`
- `EntitySeekerArrow`

A renderer binding by itself is not an admission proof; the presentation adapter must still match a
supported semantic family.

## Current projectile presentation family: 0 / 15

There are 15 registered projectile-base candidates:

- 14 inherit EntityThrowable;
- 1 inherits EntityArrow.

No candidate implements a non-empty source IEntityAdditionalSpawnData boundary in this projectile
slice.

Under the current `LegacyProjectilePresentationAnalyzer` semantic rules, the census yields
**0 / 15 admitted**.

Reason distribution:

### 7 — vanilla RenderSnowball item identity gap

These have one RenderSnowball binding, but the renderer constructor uses a fixed
`net.minecraft.init.Items.*` field. The current analyzer only resolves presentation items through
source/mod `LegacyRegistryAnalyzer.FieldBinding` entries:

- tfnaturebolt
- tflichbolt
- tftwilightwandbolt
- tftomebolt
- tflichbomb
- tfslimeblob
- tficesnowball

This is a small generic gap: `LegacyVanillaRegistry1710` already contains the exact 1.7.10 SRG
Items-field identity table.

### 3 — no unique direct registered-item launcher

The current custom-renderer path requires one registered Item whose supported use/release callback
directly constructs and spawns the projectile.

The source shape does not satisfy that rule for:

- tfhydramortar
- tfthrownaxe
- tfthrownpick

These are server/Boss-driven projectile behaviors rather than the existing item-launcher family.

### 4 — custom renderer outside current item-icon selector family

These have a player-item launch path, but their custom renderers do not expose the analyzer's strict
`getIcon(Entity): IIcon` + same registered launcher-item selector shape:

- tfmoonwormshot
- tfthrownice
- tfchainBlock
- tfcubeannihilation

They need new generic renderer semantic families, not a relaxed guess.

### 1 — renderer binding missing/ambiguous

- tfSeekerArrow

Its source item uses a helper to obtain/create the projectile and no direct renderer registration is
present in TFClientProxy, so the current analyzer correctly remains closed.

## Current visible-entity adapters: 0 matches

The current visible carrier supports only four narrow semantic families:

- SLIDE_PANEL
- TINTED_CUSHION
- TRAY_ITEMS
- HANGING_ATLAS

No registered Twilight Forest entity matches those exact structures.

In particular, the registered TF source watcher corpus has no ItemStack watcher schema/getter
surface, so the TRAY_ITEMS family is structurally impossible here.

This is not a statement that Twilight Forest entities cannot be rendered; it only states that none
fit the four currently implemented presentation adapters.

## Machine-readable census

Local exact census:
`/mnt/data/tf_part2f_census.json`

The machine-readable GitHub summary stored beside this document records the same aggregate gates and
projectile reason distribution.

The census was produced from the uploaded exact JAR using javap verifier/class metadata plus the
current branch admission rules. A full Gradle/Loom run is not claimed.

## Recommended next slice: Part 2F-2

The highest-value low-risk next change is the seven RenderSnowball projectiles.

Do this generically:

1. `LegacyProjectilePresentationAnalyzer.vanillaSnowballBinding` should resolve exact
   `net.minecraft.init.Items` SRG fields through `LegacyVanillaRegistry1710`.
2. The rule must preserve that the presentation item is platform/vanilla rather than candidate-owned.
3. `LegacyProjectilePresentationPass` must materialize platform items through the existing
   1.7.10 vanilla-item datafix instead of looking only in the candidate content item table.
4. Unknown vanilla fields, blocks masquerading as items, ambiguous constructor shapes, or failed
   modern upgrades remain fail closed.

Expected immediate target from this exact corpus: **7 projectile presentation rules** without
adding any Twilight Forest class/name allowlist.

Do not start EntityMob AI conversion before this seven-projectile slice is verified.
