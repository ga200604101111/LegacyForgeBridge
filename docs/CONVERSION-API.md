# Internal Conversion API

LegacyForgeBridge uses an internal Java conversion SPI so individual Forge 1.7.10 mods can contribute narrow compatibility rules without forking the entire converter.

This API is intentionally **internal and unstable** until several real mods have exercised it. The first corpus-backed profile is `RPGTool1-1.1-1.7.10.jar`.

## Goals

- one common deterministic conversion engine;
- small ordered `ConversionPass` units;
- per-mod profiles that only add special rules when necessary;
- a machine-readable conversion manifest consumed by later registry/network/GUI/entity bridges;
- explicit `AUTO / ADAPTED / RUNTIME_BRIDGE / MANUAL_REQUIRED / UNSUPPORTED` diagnostics;
- never report untouched Forge bytecode as a completed Fabric conversion;
- never write a partial/blocked candidate into `.minecraft/mods` automatically;
- never let old `.lang` keys globally overwrite modern Minecraft/Fabric translations.

## Current flow

```text
old-mods/*.jar
  -> SHA-256 + ASM analyzer
  -> mcmod.info metadata extraction
  -> profile selection
  -> ConversionPlan
  -> copy legacy JAR into isolated staging tree
  -> .lang -> collision-free modern JSON aliases
  -> bytecode safety audit
  -> profile-specific guards
  -> conversion manifest
  -> fabric.mod.json wrapper
  -> deterministic candidate JAR
  -> legacy-cache/converted/
```

The sidecar manifest is written to:

```text
legacy-cache/manifests/<source>.manifest.json
```

A `PARTIAL` candidate is useful for inspection and later passes but is not installed automatically.

## API surface

### `ConversionPass`

A pass owns one deterministic transformation or validation stage:

```java
public interface ConversionPass {
    String id();
    void apply(ConversionContext context) throws Exception;
}
```

A pass may:

- transform staged resources/classes;
- add registry/translation identity mappings;
- emit stable diagnostics;
- reject unsupported behavior before an installable artifact is produced.

### `ConversionContext`

Per-mod mutable state lives in `ConversionContext`. Shared global registries are not mutated by conversion passes.

The context carries:

- source JAR path / SHA-256 / size;
- parsed logical mod metadata;
- analyzer result;
- staging directory;
- candidate output path;
- selected profile ID;
- applied pass IDs;
- diagnostics;
- conversion-manifest identity maps.

### `LegacyModProfile`

Profiles add only mod-specific behavior:

```java
public interface LegacyModProfile {
    String id();
    boolean matches(LegacyModMetadata metadata, String sourceHash);
    default void inspect(ConversionContext context) {}
    default void configure(ConversionPlan.Builder plan) {}
}
```

A profile must **not** copy the common conversion engine.

## Language conversion safety

Minecraft's client language loader ultimately merges translation keys across resource namespaces. Therefore simply changing:

```text
assets/<legacy-mod>/lang/en_US.lang
```

to:

```text
assets/<legacy-mod>/lang/en_us.json
```

while keeping old keys can still let a legacy mod override a modern key with the same string.

The first conversion slice avoids that by generating collision-free aliases:

```text
old key:
commands.example.usage

candidate key:
lfb.converted.examplemod.commands.example.usage
```

The manifest records:

```text
registries.translations[oldKey] -> candidateKey
```

A candidate containing legacy `.lang` entries stays `PARTIAL` until later content/bytecode passes retarget its translation references to these aliases. This is intentionally conservative.

## RPGTool1 first profile

The recorded corpus sample is:

```text
file:   RPGTool1-1.1-1.7.10.jar
sha256: b82cd54d2d2db576e82ba02ea4e55b4db92174c5814b45aa3ebbe1b44d98961d
modid:  rpgtool1
name:   RPGTool1
version from metadata: 1.0
```

The filename says `1.1`; conversion identity deliberately trusts parsed metadata (`1.0`) while preserving the filename separately.

`RpgTool1Profile` does not implement a second converter. It contributes a corpus guard which verifies the exact known SHA still produces the recorded analyzer baseline before later RPGTool-specific conversion rules are added.

The sample is expected to remain `PARTIAL` in the first conversion slice because it contains Forge/FML references, old language references, and direct GL11 rendering. Resource/metadata conversion succeeding does **not** mean gameplay conversion is finished.

## Manifest contract

Each candidate embeds:

```text
legacyforgebridge/conversion-manifest.json
```

The manifest contains at least:

- converter version;
- source filename / SHA-256 / size;
- all logical mod IDs from metadata;
- generated Fabric ID;
- profile ID;
- analyzer counts;
- ordered applied passes;
- item/block/entity/GUI/translation identity maps;
- stable diagnostics;
- final conversion status;
- whether the artifact is installable.

Runtime Forge/FML bridges must eventually resolve converted registry/GUI/entity identities through this manifest instead of assuming modern raw registry IDs equal Forge 1.7.10 numeric IDs.

## Status rules

```text
CONVERTED = all current required rules satisfied; candidate may be installable
PARTIAL   = useful conversion output exists, but runtime/manual/semantic work remains
BLOCKED   = known unsupported/unsafe behavior; no candidate JAR emitted
FAILED    = conversion engine/pass exception; retry on next launch
```

The current bytecode audit intentionally marks any ordinary legacy class-bearing Forge mod `PARTIAL` until actual Forge API/bytecode conversion passes exist.

CoreMod/IClassTransformer markers are `BLOCKED` rather than copied into a misleading Fabric artifact.

## Determinism and cache identity

Equivalent input must generate the same candidate bytes regardless of temporary directories. Candidate JAR entries are sorted and written with deterministic timestamps.

The source cache includes both:

```text
converter version + source SHA-256
```

so a converter update forces re-evaluation even when the old JAR itself did not change.

Dependency-manifest hashes will be added when the dependency graph becomes active in the conversion engine.

## Testing policy

CI contains an end-to-end synthetic legacy JAR fixture that verifies:

- mcmod.info extraction;
- Fabric metadata generation;
- resource preservation;
- `.lang -> .json` conversion, placeholder normalization, and collision-free aliases;
- translation identity entries in the conversion manifest;
- deterministic candidate output;
- CoreMod blocking;
- safe resource-only success;
- language-only candidates remaining partial until references are retargeted.

RPGTool1 additionally has a corpus-profile regression using the recorded exact SHA/analyzer baseline. The third-party RPGTool binary is deliberately not committed to the repository; when the external sample is available, its exact SHA activates the same strict corpus guard automatically.
