# 2026-10-07 — Twilight Forest 2.3.8 compatibility, Part 2F-2

Branch: `feature/generic-conversion-iyamato-corpus3`

Scope: generic fixed-vanilla-item `RenderSnowball` projectile presentation.

## Change

The projectile analyzer previously admitted `RenderSnowball` only when its constructor item could
be joined to a mod-owned `LegacyRegistryAnalyzer.FieldBinding`.

It now also accepts an exact 1.7.10 vanilla `net.minecraft.init.Items` static field when
`LegacyVanillaRegistry1710` proves that SRG field is an Item and supplies its historical registry
identity.

No Twilight Forest selector is present.

The projectile materialization pass now has two distinct item sources:

- `CONVERTED_MOD_ITEM`: previous behavior, resolve through converted candidate item content.
- `VANILLA_1710_DFU`: upgrade the pinned vanilla item stack with
  `LegacyVanillaStackDataFix`.

The vanilla path is fixed-metadata only and rejects DFU results that require arbitrary component
state the current projectile carrier cannot store. After DFU absorbs the fixed legacy metadata,
runtime metadata is reset to zero to prevent double application.

Audit fields are emitted:

- `presentationItemSource`
- `legacyPresentationMetadata`

## Source baseline repair

During post-commit inspection, the branch version of
`LegacyProjectilePresentationAnalyzer` was found to reference `previousReal(...)` without
defining it.

The missing helper was restored with the semantics required by the existing callsites: return the
supplied instruction when executable; otherwise walk backward over label/frame/line nodes.

This is a source-baseline repair, not a Twilight Forest special case.

Commit:
`0c987508a6eed347480712ef13d00da0ccfbef39`

## Regression

rev277 commit:

`2ed946e0ee5aa4357fc25e1d120b2e070562062a`

adds:

- a renamed synthetic EntityThrowable using
  `RenderSnowball(Items.field_151126_ay)`;
- analyzer proof that the presentation item becomes
  `net/minecraft/init/Items + snowball`;
- materialization proof that DFU produces `minecraft:snowball` with runtime metadata 0;
- a regression that candidate-owned mod item resolution remains unchanged.

## Exact Twilight Forest static validation

The uploaded TF 2.3.8 JAR was checked for every target candidate.

All 7 satisfy the newly supported source family:

- exact first external base: EntityThrowable;
- one RenderSnowball binding;
- no IEntityAdditionalSpawnData;
- no source setSize ambiguity (Throwable fallback dimensions remain applicable);
- fixed metadata = 0;
- renderer item SRG field exists in the pinned 1.7.10 vanilla Items table.

Targets:

| Entity registration | 1.7.10 presentation item |
| --- | --- |
| tfnaturebolt | wheat_seeds |
| tflichbolt | ender_pearl |
| tftwilightwandbolt | ender_pearl |
| tftomebolt | paper |
| tflichbomb | magma_cream |
| tfslimeblob | slime_ball |
| tficesnowball | snowball |

Exact local audit:
`/mnt/data/tf_rev277_exact.json`

These six distinct vanilla item identities are stable zero-metadata items in this source use, so the
bounded fixed-stack DFU path is the intended materialization boundary.

## Result / remaining projectile census

Part 2F-1 projectile candidates: 15.

After rev277, the expected semantic split is:

- newly supported fixed vanilla RenderSnowball family: **7**
- custom-renderer item-launch family still unsupported: **4**
- no unique supported direct registered-item launcher: **3**
- missing/ambiguous renderer binding: **1**

A full Gradle/Loom execution of the exact-corpus analyzer is still not claimed because the branch
contains older rev260 local-overlay source gaps documented separately. The 7/7 figure above is an
exact source-shape validation against the uploaded TF JAR plus committed regression coverage.

## Next candidate slice

The next highest-value projectile group is the four item-launched custom renderers:

- tfmoonwormshot
- tfthrownice
- tfchainBlock
- tfcubeannihilation

Do not relax the existing `getIcon(Entity)` rule generically. First classify each renderer's actual
source presentation semantics (model, block/item rendering, texture source and orientation) and
group only structurally equivalent families.
