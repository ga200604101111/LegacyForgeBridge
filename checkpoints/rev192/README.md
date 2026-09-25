# Local source checkpoint: rev192

This checkpoint adds the complete rev192 source delta to the cumulative rev189-rev191 checkpoint. It does not integrate the root src tree, run Actions, or claim a full Gradle/game validation. Restore checkpoints/rev191 first against its documented base 471db9187a8b17c4cd02a7749c8a09395104e03a, then extract this delta with `python checkpoints/rev192/extract.py --output /path/to/new/patches` and review `git apply --check` before applying it. Stop on conflicts; preserve the current working tree.

The two ordered binary pieces contain only an XZ-compressed JSON source patch. Their combined SHA-256 is `feda0413cf2b51e8ae270c3e49baea690b1f2e9d6e892b789e0181fa199aa845`.

## Implemented source changes

- Mark source-proven current-hand-dependent blocks dynamic before the geometry-family early return. Keep ray selection independent from collision and do not modify authoritative server metadata.
- Fix vanilla piston icon orientation queries. The source spa unit uses normal/inner piston texture on top and the piston bottom texture on its other five faces.
- Project the standard flower-pot carrier from 6/16 width onto its source cell dimensions: 1/3 width and 3/8 height in the tested corpus. Preserve cell positions, stored-content transforms and inventory presentation.
- Recover bounded, closed constructor-populated integer subtype maps without executing the mod or using partially evaluated constructor state. Require exact key iteration, unit ItemStack identity, constant entry colours and literal icon registration. The tested source yields seven magatama metadata variants with its original names and colours.
- Preserve source unconditional two-argument hasEffect=true as intrinsic item foil without adding enchantment NBT or fabricating gameplay. Unknown/conditional implementations and existing true foil remain unchanged.
- Runtime converter revision: `2026-09-25.192-selection-subtypes-and-shape-parity`.

## Not implemented

Campfire GUI dispatch remains unimplemented. FmlRuntimeClient.handleOpenGui still only decodes/logs the Forge message; source GUI/container/window/slot/progress/click/close integration is not supplied by this revision. Magatama presentation parity is not certification of its spell/holding gameplay.

## Local verification boundaries

Delivered bridge: `legacyforgebridge-0.2.0-alpha.27-rev192.jar`.
SHA-256: `e0a7964daf2d71fd70fd2b1e7f4fe1e7d49d25fecf10078af548eb3ef9ccce96`.
Exact rev191 base SHA-256: `78df1a62026a248ea7612afa390f3018d5bf378f66976f9e7b16a052dee68057`.

Local incremental build with JDK 21.0.11; compile-only declarations and locally recovered ASM/Gson dependencies are not packaged. 206 core assertions plus 99 isolated delivered-method-body assertions passed. The latter use explicitly declared recording test doubles, not a Minecraft/Mixin integration launch. ASM BasicVerifier checked 28 changed class files / 251 methods. Two independent builds produced identical JAR bytes. A selected 16-pass Bamboo pipeline remains PARTIAL. 331 RPGTool presentation asset entries were identical between selected old/new pipelines; 110 of 111 renderer classes were unchanged, with only GridPotRenderer modified. The three rev191 placement guard classes were unchanged.

No full Gradle clean build, Minecraft gameplay verification or GitHub Actions run was performed. The user continues converting original mods locally through old-mods; do not substitute a preconverted Bamboo mod or resource pack.
