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
│  ├─ session-scoped visibility masking
│  └─ outbound packet guard
├─ protocol
│  ├─ ViaFabricPlus/ViaVersion integration
│  └─ Forge/FML 1.7.10 handshake/channel logic
├─ scanner
│  ├─ old-mods discovery
│  ├─ metadata detection
│  └─ SHA-256 cache
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
│  ├─ resource/metadata conversion
│  ├─ output validation
│  ├─ conversion manifest
│  └─ converted JAR cache
└─ diagnostics
   ├─ rule IDs
   ├─ per-mod reports
   └─ compatibility corpus metrics
```

## `old-mods` lifecycle

```text
.minecraft/old-mods/*.jar
  -> fingerprint
  -> analyze
  -> dependency/compatibility plan
  -> transform
  -> validate
  -> write converted artifact
  -> .minecraft/mods/
  -> load on next launch
```

A failed validation must not overwrite or emit a JAR that Fabric will try to load as if conversion succeeded.

## Registry timing

Modern Minecraft/Fabric registry initialization occurs during startup. Therefore content that needs genuine modern registry entries generally must be converted and known before joining a legacy server.

The network handshake may supply legacy registry identity and numeric/discriminator information, but it should bind those identities to already prepared modern converted content through a conversion manifest rather than attempting unsafe late global registration.

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
3. Unsupported modern content must not be serialized to a 1.7.10 server.
4. Unsupported legacy conversion must fail explicitly before a misleading JAR is emitted.
5. Every nontrivial conversion rule should be identifiable by a stable diagnostic rule ID.
6. Disconnecting from a legacy server must restore all session-scoped masks and capabilities.

## Version support policy

There are three independent version axes:

- **Minecraft version:** initially exactly 1.21.11 client -> 1.7.10 target server.
- **Fabric Loader version:** should support the widest tested compatible range; compile/test against the declared minimum to avoid accidental use of newer Loader APIs.
- **Fabric API version:** only require modules actually used. Avoid unnecessary hard coupling to one exact Fabric API build.

Multi-Minecraft-version support is a future architectural concern and should not be implied merely because multiple Fabric Loader versions work.
