# Internal Conversion API

LegacyForgeBridge uses an internal Java conversion SPI so Forge 1.7.10 mods can contribute narrow compatibility rules without forking the whole converter. The API remains internal/unstable until several real mods have exercised it.

## Core rules

- one deterministic conversion engine;
- small ordered `ConversionPass` units;
- mod profiles add only the exceptional rules they actually need;
- registry/network/GUI/entity identity is carried by conversion manifests, never inferred from modern raw IDs;
- old Forge bytecode is never called "converted" simply because resources copied successfully;
- CoreMods stay blocked until their transformation intent is explicitly migrated;
- old language keys must not globally overwrite modern Minecraft translations;
- only **loader-safe** candidates are automatically staged into `mods/`;
- staged files are owned by LFB and are never expected to be managed manually by the user.

## Current flow

```text
old-mods/*.jar
  -> SHA-256 update detection
  -> cache check (converter version + source SHA)
  -> ASM analyzer when cache is invalid
  -> mcmod.info metadata
  -> profile selection
  -> copy source into isolated staging tree
  -> .lang -> modern collision-safe JSON
  -> profile semantic passes
       (may remove/replace obsolete Forge classes)
  -> clean obsolete legacy language inputs
  -> final staged-bytecode audit
  -> conversion manifest
  -> fabric.mod.json
  -> deterministic candidate JAR
  -> legacy-cache/converted/
  -> loader-safety validation
  -> managed mods/ staging
  -> next Minecraft launch loads the converted candidate
```

Sidecar manifests are stored under:

```text
legacy-cache/manifests/
```

Persistent conversion progress and cache/staging state are stored in:

```text
legacy-cache/conversion-state.json
```

When converted files changed during a launch, LFB also writes:

```text
legacy-cache/RESTART_REQUIRED.txt
```

The restart marker is removed automatically once the currently loaded managed candidates match the current old-mod sources and converter version.

## Managed staging and update detection

Fabric Loader discovers mods before `LegacyForgeBridge.onInitialize()` runs. A candidate produced from `old-mods` therefore cannot become a new Fabric `ModContainer` in the same launch.

LFB solves this with a managed cross-launch staging layer:

```text
first launch
old-mods/RPGTool.jar
  -> convert
  -> mods/legacyforgebridge-converted-rpgtool1.jar
  -> restart required

next launch
Fabric Loader sees managed JAR
  -> ConvertedContentRuntime registers modern content
  -> LFB verifies source SHA + converter version
  -> unchanged candidate is skipped
```

Managed filenames are stable per generated Fabric mod ID. Users should continue to manage only:

```text
mods/LegacyForgeBridge.jar
old-mods/<legacy Forge jars>
```

The generated `legacyforgebridge-converted-*.jar` files are LFB-owned implementation details.

### Cache identity

The conversion cache identity is:

```text
converter version + source SHA-256
```

This means:

- unchanged old-mod + unchanged converter -> analysis/conversion is skipped;
- same filename but changed JAR contents -> SHA changes and conversion runs again;
- converter update -> conversion runs again even if the old JAR is unchanged;
- removed old-mod -> its managed converted JAR is retired;
- renamed source -> it is treated as a newly discovered source while stale state is reconciled safely.

The source is hashed every launch rather than trusting only timestamp or file size, so replacing a 1.7.10 mod with a new build cannot silently keep an old conversion.

### Updating a converted mod while its previous JAR is loaded

On Windows a loaded Fabric mod JAR may remain open. LFB never mutates a currently loaded managed JAR in place.

Instead it writes the new candidate under `legacy-cache/pending/` and launches a tiny JDK-only helper which waits for the current Minecraft process to exit, then atomically swaps the managed JAR before the next launch. If the helper cannot be started, a JVM-shutdown fallback is registered and the restart marker remains visible until the desired candidate becomes active.

While a generated candidate is known stale during the current launch, `ConvertedModCatalog` suppresses its old FML identity so LFB does not falsely advertise an outdated converted mod to a 1.7.10 server.

## Conversion progress

`latest.log` emits deterministic progress stages such as:

```text
Legacy conversion [1/1] 0% DISCOVERED ...
Legacy conversion [1/1] 10% HASHING ...
Legacy conversion [1/1] 35% ANALYZING ...
Legacy conversion [1/1] 60% CONVERTING ...
Legacy conversion [1/1] 85% STAGING ...
Legacy conversion [1/1] 100% STAGED/LOADED/SKIPPED ...
```

`conversion-state.json` mirrors the same state per source with source SHA, converter version, profile, candidate hash, managed-JAR hash, pending-swap flag, restart requirement and final phase.

`legacyforgebridge.log` remains reserved for Forge/FML custom-payload tracing and is intentionally not used as the conversion progress log.

## API surface

`ConversionPass` owns one deterministic transformation or validation stage:

```java
public interface ConversionPass {
    String id();
    void apply(ConversionContext context) throws Exception;
}
```

`ConversionContext` contains the source identity, analyzer result, staging/output paths, selected profile, diagnostics, applied passes, and identity maps.

`LegacyModProfile` selects a corpus/mod family and contributes additional passes:

```java
public interface LegacyModProfile {
    String id();
    boolean matches(LegacyModMetadata metadata, String sourceHash);
    default void inspect(ConversionContext context) {}
    default void configure(ConversionPlan.Builder plan) {}
}
```

Profiles must not duplicate the common engine.

## Status / support model

```text
ConversionStatus:
CONVERTED
PARTIAL
BLOCKED
FAILED

SupportLevel:
AUTO
ADAPTED
RUNTIME_BRIDGE
MANUAL_REQUIRED
UNSUPPORTED
```

A semantic pass may convert an old class-bearing mod into a loader-safe resource/content candidate by removing obsolete classes after extracting their required meaning. The final bytecode audit therefore runs **after** profile passes and inspects the staged output rather than treating analyzer facts from the original JAR as unresolved code automatically.

`PARTIAL` and loader safety are separate concepts. RPGTool is currently `PARTIAL` because not every gameplay semantic is ported, but its generated candidate is loader-safe because obsolete Forge classes are removed and LFB owns the modern runtime-backed content.

## Language safety

Minecraft merges translation keys across resource namespaces. Generic legacy `.lang` conversion therefore uses collision-safe aliases such as:

```text
lfb.converted.<fabric-id>.<legacy-key>
```

and records:

```text
registries.translations[legacyKey] -> convertedKey
```

A corpus-specific semantic pass may later promote known content keys to their proper modern identity, for example:

```text
item.dark_sword.name
-> item.rpgtool1.dark_sword
```

without touching modern Minecraft's own item keys.

## Runtime-backed converted content

A loader-safe converted candidate can embed:

```text
legacyforgebridge/converted-content.json
```

`ConvertedContentRuntime` discovers those manifests through Fabric Loader during LFB initialization and registers the modern items represented by the candidate. This keeps the candidate itself free of obsolete Forge classes.

The manifest also carries the original FML identity. `ConvertedModCatalog` exposes those identities to the Forge handshake so a loaded converted candidate can advertise its old mod ID/version to a 1.7.10 server.

Example:

```text
Fabric candidate id: rpgtool1
legacy FML identity: rpgtool1=1.0
```

## OBJ bridge

1.7.10 mods commonly use Forge `AdvancedModelLoader` / `IModelCustom` plus direct GL11 rendering. LFB has a modern custom-geometry path for corpus-backed conversions:

```text
Wavefront OBJ resource
-> legacyforgebridge:obj special item model
-> SpecialModelRenderer
-> SubmitNodeCollector.submitCustomGeometry
-> VertexConsumer
```

The OBJ bridge supports `v`, `vt`, `vn`, polygon triangulation, negative indices and computed face normals when an OBJ omits normals. Legacy resources whose path contains uppercase characters must be normalized because 1.21.11 resource identifiers require lowercase paths.

## RPGTool1 first semantic slice

The exact test corpus is:

```text
RPGTool1-1.1-1.7.10.jar
SHA-256 b82cd54d2d2db576e82ba02ea4e55b4db92174c5814b45aa3ebbe1b44d98961d
embedded modid rpgtool1
embedded version 1.0
53 classes
```

Real-bytecode inspection found 71 registered items, including 20 OBJ weapons, 8 wings and 15 circle/aura items.

For the exact SHA, the RPGTool profile now:

- verifies the analyzer corpus baseline;
- removes all obsolete Forge classes from the candidate;
- reconstructs the 71 item registry identities;
- reconstructs weapon durability/base attack values from the real bytecode;
- emits modern item models and icons;
- converts the 20 weapon OBJ models to LFB's 1.21.11 special renderer;
- normalizes `textures/items3D` to lowercase `textures/items3d`;
- promotes known item-name translations to `item.rpgtool1.*` and supplies them to both `zh_cn` and `zh_tw` for the test candidate;
- exposes `rpgtool1=1.0` to the legacy FML Client ModList once the managed candidate is loaded;
- automatically stages the loader-safe candidate for the next launch.

This is deliberately still `PARTIAL` while gameplay semantics such as gem socketing, skill effects, recipes, wing movement/fall handling and equipped wing/circle OBJ rendering remain to be ported. A live modded-server test may also reveal additional legacy numeric registry packet work required around Via's item translation boundary.

See `docs/corpus/RPGTool1-1.1-1.7.10.md` for the exact corpus contract.

## Testing policy

CI keeps generated legacy fixtures for the common engine and adds corpus-specific tests for semantic passes. Managed staging tests verify first-stage copy, unchanged skip, replacement of an inactive candidate, pending replacement of a currently loaded candidate, loader-safety rejection of class-bearing JARs, and persistent conversion-state round trips.

The third-party RPGTool JAR is not committed. When the external exact JAR is supplied, its SHA activates the strict corpus path and is used for manual/live validation.
