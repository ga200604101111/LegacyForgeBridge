# LegacyForgeBridge

Experimental Fabric 1.21.11 client compatibility and conversion layer for legacy Forge 1.7.10 mods. The original server owns gameplay and inventory state. Original mods stay in `old-mods`; do not install a Forge JAR directly in modern Fabric `mods`.

## Latest source checkpoint: rev201 — source-driven menu topology

The iYAMATO feature branch now includes a bounded, non-executing slot/property extractor, an immutable topology contract, schema-2 manifest output and connected client menu consumers. Slot wire order, inventory identity/index, x/y and simple placement restrictions come from source construction; sparse/reordered property IDs map to source fields instead of array positions. The primary analyzer replaces its container-constructor and property-setter fingerprints with semantic extraction.

**This is not a universal GUI translator.** GUI construction, drawing and Shift-click fingerprints remain, and runtime admission still requires the 47-slot/two-property family. Existing five-fingerprint admission is retained only as an explicitly labelled `LEGACY_TEMPLATE` fallback; it is never reported as `SOURCE_TOPOLOGY`. The original Bamboo corpus was not available for a new run this turn.

**Source only: no installable rev201 JAR, full Gradle/Loom build, real Minecraft/Fabric/Mixin API build or live-server verification.** 44 synthetic cases / 1,185 core assertions, 635 recording-host integration assertions and 13 source-application tests passed. Three encoder JSON artifacts were independently read back. Test hosts are explicitly declared doubles, not a Minecraft launch. Local ASM/Gson dependency provenance and exclusions are recorded.

See [rev201 implementation, restoration and boundaries](checkpoints/rev201/README.md), [verification](checkpoints/rev201/verification.json) and [source index](checkpoints/rev201/source-index.json).

## Client jump work is preserved

rev200 jump observation and opt-in echo reconciliation remain unchanged. Default `observe` does not change motion. Equal vectors do not prove packet causality; reconciliation remains an opt-in experiment and the reported intermittent wing lift is not confirmed fixed. This turn reran 862 core assertions, 144 recording-adapter assertions and 10 rev200 application tests. See [rev200](checkpoints/rev200/README.md).

## Baseline and source restoration

`main` remains the merged rev197 baseline; this work changes only `feature/generic-conversion-iyamato-corpus3`. The Forge server, original RPGTool/Bamboo, `old-mods` and the Bamboo branch are unchanged. No Actions build was requested.

**Root `src` remains the old rev188 base plus cumulative source checkpoints. Do not compile it alone and label it rev201.** From the complete checkout, restore into a new directory outside it:

```sh
python checkpoints/rev201/restore.py --output ../LegacyForgeBridge-rev201-source
```

The complete cumulative restore chain was not run this turn. The rev201 payload round-trip and reviewed-file application tests were run. Restoration verifies the pinned payload and reviewed base hashes and refuses unknown source changes. Applied converter fingerprint: `2026-09-26.201-source-menu-topology`.

## Previous progress and limitations

The full previous README is retained [here](docs/README-before-rev201-1b266638.md), including rev197 artifact checksums, iYAMATO trial status and project goals. All older checkpoints are preserved. [rev199](checkpoints/rev199/README.md) texture evidence is not model/runtime admission; [rev198](checkpoints/rev198/README.md) iYAMATO remains incomplete and not playable. Existing GridPot insertion, custom rendering, networking and gameplay limitations are not resolved merely by a successful handshake or a passing static test.

Unsupported operations must produce explicit diagnostics rather than silently emit broken converted mods or substitute a fixed GUI for an unproven layout.
