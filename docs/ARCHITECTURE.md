# Architecture

LegacyForgeBridge is a Fabric 1.21.11 compatibility platform for interacting with Forge 1.7.10 servers and progressively converting supported Forge 1.7.10 mods.

## Design principle

**Translate behavior and intent, not obsolete implementation details.**

Examples:

- Forge event -> Fabric/vanilla event when an equivalent exists;
- old ASM transformer -> modern event or shared Mixin hook;
- GL11 rendering intent -> modern renderer;
- legacy field mutation -> getter/setter or semantic adapter;
- GameRegistry operation -> modern registration/conversion manifest.

## Major subsystems

```text
LegacyForgeBridge
├─ session
│  ├─ server/version detection
│  ├─ legacy capability table
│  ├─ item/BlockItem compatibility boundary
│  └─ outbound packet guard
├─ protocol
│  ├─ ViaFabricPlus/ViaVersion integration
│  └─ Forge/FML 1.7.10 handshake/channel logic
├─ scanner
│  ├─ old-mods discovery
│  ├─ metadata detection
│  └─ SHA-256 cache
├─ dependency
│  ├─ logical mod-id/artifact graph
│  ├─ hard/optional/order/API/bootstrap edges
│  ├─ transitive closure and version constraints
│  ├─ SCC detection / condensation
│  └─ dependency-ready scheduling groups
├─ analyzer
│  ├─ ASM bytecode analysis
│  ├─ Forge/Minecraft reference inventory
│  ├─ CoreMod/transformer detection
│  └─ compatibility classification
├─ mapping
│  ├─ 1.7.10 MCP/SRG side
│  ├─ 1.21.11 modern mapping side
│  └─ semantic cross-version rules
├─ transformer
│  ├─ direct remap
│  ├─ API adapters
│  ├─ semantic bytecode transforms
│  └─ known ASM/CoreMod intent migration
├─ conversion
│  ├─ dependency-aware parallel worker pool
│  ├─ resource/metadata conversion
│  ├─ output validation
│  ├─ conversion manifest
│  ├─ converted JAR cache
│  └─ early-window progress reporting
└─ diagnostics
   ├─ rule IDs
   ├─ per-mod reports
   └─ compatibility corpus metrics
```

Detailed dependency semantics are defined in [`DEPENDENCY-MODEL.md`](DEPENDENCY-MODEL.md).

## `old-mods` lifecycle

```text
.minecraft/old-mods/*.jar
  -> fingerprint
  -> metadata + mod-id discovery
  -> analyze
  -> build dependency graph
  -> resolve transitive closure / SCC groups
  -> compatibility plan
  -> transform dependency-ready groups
  -> validate
  -> write converted artifact
  -> .minecraft/mods/
  -> load on next launch
```

A failed validation must not overwrite or emit a JAR that Fabric will try to load as if conversion succeeded.

## Parallel conversion model

Conversion should use multiple CPU cores when multiple legacy mods are ready to process. Parallelism is **per mod / per dependency-ready conversion group**, not uncontrolled concurrent mutation of shared state.

### Parallel-safe stages

The following stages are expected to run concurrently for independent mods:

- fingerprint / SHA-256;
- JAR metadata scan;
- ASM bytecode analysis;
- mapping lookup;
- bytecode/resource transformation;
- per-mod validation;
- report generation.

### Dependency-aware scheduling

Before transformation, the coordinator builds a complete dependency graph from legacy mod metadata, ordering declarations, discovered API/library relationships, and classified bytecode references.

The raw graph is **not assumed to be acyclic**. The resolver first detects strongly connected components (SCCs), classifies cycles, collapses valid SCCs into conversion groups, and then schedules the resulting DAG.

Example after condensation:

```text
Group[A] ──> Group[B] ──> Group[D]
                       \
Group[C] ───────────────> Group[D]

ready set 1: A, C
ready set 2: B
ready set 3: D
```

Mods/groups in the same ready set may be converted concurrently. A dependent conversion group must not consume another group's manifest until the required provider has completed successfully.

Independent branches must continue converting even if an unrelated dependency chain is waiting or blocked.

Hard dependency failure propagates only through its dependent closure. Missing optional dependencies do not block the base mod.

### Worker-count policy

Worker count must be configurable.

Recommended modes:

```text
auto        = choose a conservative count from available processors
fixed N     = explicit worker count
single      = diagnostic / deterministic fallback
```

`auto` should reserve CPU capacity for Minecraft/OS startup instead of blindly using every logical processor. The implementation should expose current worker count in diagnostics and allow a safe upper limit.

### Shared-state rules

- mapping databases and immutable rule tables may be shared read-only;
- mutable per-mod state belongs to that mod's conversion context;
- cache index updates must be synchronized or transactionally merged;
- cross-mod namespace/registry collision resolution occurs through the coordinator;
- final JAR writes use temporary files plus atomic move/commit where supported;
- one mod failing must not corrupt another mod's output.

### Determinism

Parallel execution must not change conversion results. The same source JARs, resolved dependency graph, rule database and configuration must produce the same manifests and output regardless of worker scheduling.

A single-worker mode is mandatory for debugging concurrency or reproducibility problems.

## Dependency-aware cache model

A source JAR's own SHA-256 is necessary but not sufficient for cache validity.

If a converted mod links against a converted library/API provider, changes to that provider may invalidate the dependent output even when the dependent source JAR itself is unchanged.

Cache identity should therefore include at least:

```text
source JAR hash
converter version
mapping/rule versions
resolved dependency manifest hashes
```

This prevents stale dependents from surviving an API/library reconversion.

## Early-window conversion progress

Legacy conversion can be CPU-heavy on the first launch and must not look like a frozen Minecraft process.

The conversion subsystem therefore provides a lightweight progress model as soon as the Minecraft client window is available.

Minimum visible states:

```text
Scanning legacy mods
Analyzing 3 / 12
Planning dependencies
Converting 5 / 12
Validating 8 / 12
Writing cache
Complete - restart required
```

The progress model should expose:

- current phase;
- total discovered mods;
- completed / active / queued counts;
- blocked-by-dependency count;
- active mod names;
- worker count;
- cache-hit count;
- failed/unsupported count;
- overall bounded progress when calculable.

### UI safety rule

The conversion workers must not block the render/event thread for long CPU work. The main client thread remains able to repaint/pump events while worker threads perform analysis and conversion.

The early progress UI is intentionally lightweight. It is not a full custom launcher. If the normal Minecraft window is not yet available, progress must at minimum continue through structured logging and may update a safe launcher/window status surface when one exists.

Because converted JARs are loaded on the **next launch**, conversion does not require unsafe late registry registration during the current session. This gives the implementation freedom to perform heavy conversion work asynchronously once a usable client window/event loop exists.

Cancellation or a conversion failure must leave the previous valid cache/output untouched.

## Registry timing

Modern Minecraft/Fabric registry initialization occurs during startup. Therefore content that needs genuine modern registry entries generally must be converted and known before the launch in which it is loaded.

The network handshake may supply legacy registry identity and numeric/discriminator information, but it should bind those identities to already prepared modern converted content through a conversion manifest rather than attempting unsafe late global registration.

A JAR converted during the current launch is staged for the next launch.

## Protocol boundary

LegacyForgeBridge should not reimplement the full Minecraft 1.7.10 network protocol if ViaFabricPlus/ViaVersion already handles it correctly.

The project owns the missing Forge-specific behavior:

- Forge/FML server detection;
- FML handshake;
- Forge channels;
- mod list negotiation;
- legacy registry/mod identity integration;
- compatibility validation against converted client content.

## ASM/CoreMod policy

Do not preserve the historical transformer runtime by default.

For each detected transformer:

1. identify the target and intended behavior;
2. check for a modern vanilla/Fabric API hook;
3. otherwise check for a shared LegacyForgeBridge Mixin hook;
4. generate/use a dedicated Mixin only when deterministic and safe;
5. reject unknown transformations explicitly.

## Rendering policy

Direct legacy OpenGL is not a compatibility target by itself. Rendering operations should be classified by intent (HUD, world overlay, entity/block render, matrix transform, quad/model drawing, blend/depth behavior) and migrated to modern rendering APIs.

## Safety invariants

1. A normal 1.21.11 session must behave normally when LegacyForgeBridge is inactive.
2. A legacy session must never mutate global registries destructively to hide modern content.
3. Unsupported modern item/BlockItem state must not be serialized to a 1.7.10 server.
4. Unsupported legacy conversion must fail explicitly before a misleading JAR is emitted.
5. Every nontrivial conversion rule should be identifiable by a stable diagnostic rule ID.
6. Disconnecting from a legacy server must restore all session-scoped state.
7. Parallel conversion must be deterministic and isolate per-mod failures.
8. Heavy conversion work must not intentionally freeze the Minecraft render/event thread.
9. A hard prerequisite must be resolved and validated before a dependent artifact is committed.
10. Missing optional prerequisites must not be promoted into hard failures.

## Version support policy

There are three independent version axes:

- **Minecraft version:** initially exactly 1.21.11 client -> 1.7.10 target server.
- **Fabric Loader version:** should support the widest tested compatible range; compile/test against the declared minimum to avoid accidental use of newer Loader APIs.
- **Fabric API version:** only require modules actually used. Avoid unnecessary hard coupling to one exact Fabric API build.

Multi-Minecraft-version support is a future architectural concern and should not be implied merely because multiple Fabric Loader versions work.
