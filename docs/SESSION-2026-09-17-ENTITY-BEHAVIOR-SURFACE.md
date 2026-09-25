# 2026-09-17 — Entity source-owned behavior surface inventory

## Scope

This slice follows converter revision `2026-09-17.65`. DataWatcher registration, access, source-wide closure and modern synchronized-data mapping now have explicit proof IR, but that is not enough to generate a playable entity. This step inventories the non-watcher source-owned entity behavior surface without executing legacy classes.

Converter revision: `2026-09-17.66`.

## Analyzer

`LegacyEntityBehaviorSurfaceAnalyzer` starts from entity registrations already admitted by the DataWatcher-definition proof and walks the registered source entity plus every source-owned superclass until the lineage reaches a class outside the source JAR.

For each entity it records:

- registered source class;
- ordered source-owned lineage;
- first external base class;
- every declared non-static, non-synthetic, non-bridge source instance method except constructors/class initializers;
- the effective source-owned implementation for a bounded set of common 1.7.x Entity callbacks.

Keeping every source instance method is intentional. A method that does not match the known callback table remains visible as unclassified source behavior instead of disappearing from the conversion evidence.

## Classified callback families

The initial inventory recognizes MCP/SRG shapes for:

- `entityInit`;
- `onUpdate` / `onEntityUpdate`;
- entity NBT read/write;
- `attackEntityFrom`;
- player interaction;
- collision/push callbacks;
- collide-with-player / apply-entity-collision;
- `setDead`;
- mounted/Y offsets;
- collision/bounding boxes;
- walking trigger;
- render-distance predicate;
- multipart entity parts;
- FML additional spawn-data read/write;
- Forge rider-sit hook;
- collision border size.

This table is an inventory aid, not a claim that these callbacks already have modern semantic equivalents. Unclassified methods remain first-class evidence for later family-specific proof.

## Output

`LegacyEntityBehaviorSurfacePass` writes:

- `legacyforgebridge/entity-behavior-surface.json`
- schema version 1

Each rule includes:

- modern entity id inherited from the proven definition sidecar;
- legacy registry/source identity;
- `externalBaseClass`;
- source lineage;
- classified callback list with exact owner/name/descriptor;
- complete retained source instance-method list with access flags;
- callback and unclassified method counts;
- `sourceOwnedBehaviorInventoryComplete=true`;
- `runtimeBehaviorReady=false`;
- `behaviorRuntimeWired=false`.

The generic profile runs this inventory after synchronized-data planning. No EntityType registration or source entity execution is introduced.

## Regression coverage

`LegacyEntityBehaviorSurfaceAnalyzerTest` uses an unrelated namespace with a registered entity inheriting a source-owned base class. It proves:

- ordered source lineage recovery;
- external vanilla Entity base recovery;
- inherited `entityInit` and tick callback classification;
- subclass NBT and interaction callback classification;
- retention of an unclassified helper method;
- constructor and static helper exclusion.

`LegacyEntityBehaviorSurfacePassTest` additionally proves sidecar materialization, modern id reuse, callback/source-method counts, unclassified method preservation, and that behavior runtime readiness remains false.

## Next gate

This inventory makes the next decision evidence-driven: entity families can now be grouped by external base class plus their actual source-owned callback/method surface. The next safe runtime work should admit only a narrow family whose callbacks and unclassified methods can all be translated or proven inert. AI, projectile motion, complex NBT, extra spawn data, network/event interactions and rendering remain separate gates.

The Bamboo candidate remains `PARTIAL` until family-specific behavior semantics, runtime materialization, loader isolation and exact-corpus regression are all complete.
