# alpha.16 RPGTool live item/OBJ repair

alpha.16 is based on the first alpha.15 live run against the real Forge 1.7.10 RPGTool server.

## Live evidence

The server's FML `ModIdData` successfully exposed semantic mod item identities before PLAY, including examples such as:

```text
4169 -> rpgtool1:dark_sword
4172 -> rpgtool1:water_sword
4205 -> rpgtool1:ocean_sword
4236 -> rpgtool1:hot_evil_sword
```

The converted candidate also registered all 71 modern items locally. The remaining stone symptom is therefore a protocol-boundary identity loss, not a missing Fabric item registration.

ViaVersion's 1.12.2 -> 1.13 item rewriter intentionally falls back to stone when an old numeric item ID is absent from vanilla mappings. Arbitrary Forge numeric IDs fall into that path.

## Session-local mod item identity bridge

alpha.16 installs a session-local table from FML registry synchronization:

```text
Forge numeric ID
<-> semantic registry identity
<-> converted 1.21.11 Item
```

Clientbound modded stacks are protected before the lossy Via boundary by storing the semantic identity in an `LFB|legacy_item` marker and temporarily using vanilla paper as a protocol carrier. After the final 1.21.11 structured-item rewrite, LFB restores the real converted item raw ID and legacy damage value.

Serverbound stacks perform the inverse transformation. The marker is removed before the stack reaches the 1.7.10 server, which receives its original Forge numeric ID and metadata.

Vanilla item mappings stay Via-owned. The bridge is cleared on handshake/session reset and never mutates the global modern registry.

## Item texture repair

The alpha.15 live log showed generated models resolving texture IDs such as:

```text
rpgtool1:items/attack4
rpgtool1:items/purple_sword
```

Modern item resources use `textures/item`, while the original 1.7.10 JAR stores icons in `textures/items`. alpha.16 adds a profile pass which:

```text
assets/rpgtool1/textures/items/**
-> assets/rpgtool1/textures/item/**

rpgtool1:items/<id>
-> rpgtool1:item/<id>
```

The pass runs after RPGTool model generation so both copied resources and generated JSON are normalized deterministically.

## OBJ fidelity repair

The OBJ renderer now keeps original polygon faces until submission and mirrors the relevant Forge 1.7.10 Wavefront behavior:

- flip OBJ V texture coordinates;
- use one calculated normal for the whole face rather than per-vertex `vn` smoothing;
- nudge UV coordinates by `0.0005` toward the face-average UV to reduce texture bleeding/seams;
- triangulate only after face normal and UV center are established;
- retain negative OBJ index support.

Per-display-context `IItemRenderer` GL11 translation/rotation data is not guessed in this change because the exact values are not retained in the repository. If orientation/hand placement is still wrong after the texture and face-fidelity fixes, the next live slice should extract those transforms from the supplied RPGTool binary rather than tune them visually.

## alpha.16 acceptance checks

1. Start with the managed converted RPGTool candidate regenerated under alpha.16.
2. Connect to the same Forge 1.7.10 RPGTool server.
3. Confirm `legacyforgebridge.log` records a non-zero `Legacy mod item identity bridge installed mappings=` value.
4. Obtain at least `dark_sword` and `water_sword` from the server; they must resolve to converted RPGTool items rather than stone.
5. Move/click/drop the items and verify the server still recognizes the original RPGTool items.
6. Confirm the former `Missing textures ... rpgtool1:items/...` warnings are gone.
7. Inspect OBJ weapons for seam/bleeding and lighting regressions.
