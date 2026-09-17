# 2026-09-17 — GridPot inherited 1.7.10 render identity proof

## Scope

This slice follows converter revision `2026-09-17.97`, where source-proven known-negative BlockItems already execute the exact GridPot fallback while unresolved BlockItems remain fail-closed.

Converter revision: `2026-09-17.98`.

## Problem

`LegacyRegisteredBlockRenderTypeAnalyzer` previously admitted only render identities explicitly represented inside the converted mod JAR:

- direct integer constants returned by `getRenderType()/func_149645_b`;
- direct static integer fields;
- the bounded constructor-field family proven in `.95`.

A source block that declares no render override at all was therefore left unresolved once its superclass chain reached vanilla Minecraft classes outside the source JAR. This was conservative but unnecessarily blocked ordinary subclasses that simply inherit a stable 1.7.10 platform render type.

## Bounded platform proof

`LegacyBlockRenderType1710` now contains a deliberately small 1.7.10 platform fact table:

- `net/minecraft/block/Block` -> `0`;
- `net/minecraft/block/BlockContainer` -> `0`;
- `net/minecraft/block/BlockBush` -> `1`;
- `net/minecraft/block/BlockCactus` -> `13`;
- `net/minecraft/block/BlockDoublePlant` -> `40`.

The analyzer uses this table only after traversing every source-owned class in the registered block lineage and proving that none of them declares `getRenderType()/func_149645_b`.

A source override always wins. If a source class or source superclass declares the method but its body is not otherwise provable, the platform fallback is not used. Unknown external bases remain fail-closed.

This is therefore inheritance proof, not registry-name or class-name guessing.

## GridPot effect

No GridPot runtime code changes are required in this slice. The `.96/.97` pipeline already classifies every proven render identity into:

- positive insertion identities (`1`, `13`, `40`, or exact symbolic positive identity);
- known-negative identities;
- unresolved identities.

A newly proven inherited `0` identity automatically enters the known-negative set and is therefore eligible for the source-proven `.97` negative fallback.

## Exact Bamboo coverage

The checksum-pinned Bamboo corpus must now prove:

- registry name `bambooMoss`;
- source class `ruby/bamboo/block/BlockMoss`;
- no source-owned render override;
- effective legacy render identity `0` inherited from vanilla `Block`.

This moves Bamboo moss from the unknown BlockItem category into the source-proven known-negative GridPot path without hard-coding Bamboo names in production logic.

## Regression boundary

Normal CI covers:

- every currently admitted platform base fact;
- source-owned multi-level inheritance into a known platform base;
- source override precedence;
- unknown external bases remaining closed.

The exact-corpus test pins the Bamboo `BlockMoss` result.

## Remaining boundary

The platform table remains intentionally small and is not a substitute for a complete 1.7.10 vanilla class hierarchy model. Unknown external bases and unproven source overrides remain unresolved.

GridPot inserted-content presentation is still separate work, and `contentInsertionRuntimeComplete` remains false while unresolved BlockItems can still exist. Whole Bamboo conversion remains `PARTIAL`.
