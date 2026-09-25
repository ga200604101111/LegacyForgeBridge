# rev196 — tray scale, spa steam, villager-head presentation and returned cookbook

Main replacement: `legacyforgebridge-0.2.0-alpha.27-rev196.jar`
SHA-256: `1ab2c04282987fd67b1ddea4d9e66ff3563e1fd227464e06e57bb35264152f9e`

## Implemented changes

* Tray: the source renderer scales camera-relative translation and geometry together by 0.7. Reapplying that scale only to world-local geometry makes the modern display smaller. Remove that extra local scale, retaining the source 0.5128205 icon transform, 1–5-item layout, camera-yaw facing and model-bottom anchoring. The changed geometry/layout is 1/0.7 (about 1.43) times rev195; this is not a same-camera screenshot parity measurement.
* Spa: preserve the registered `BambooMod:spaWater` identity. The original is a water-material BlockLiquid with vanilla water sprites, not the ordinary `minecraft:water` block. Its client random-display callback creates EntityWhiteSmokeFX with a 1/10 gate and an air-above condition; neutral RGB is 1-random*0.3. Restore that branch using a modern smoke particle recoloured through the 1.21.11 SingleQuadParticle API. Do not replace server blocks, simulate healing/boiling/dye gameplay, or claim complete fluid-renderer equivalence.
* VillagerBlock: reconstruct the original head and nose cuboids, 64x64 villager texture, world 1.6 and inventory 1.68 scale, metadata orientation, client spin/hearts and stick acceleration. Use the existing passive block-entity carrier and its initial-chunk hydration. Ordinary interactions still travel to the server. This is a presentation adaptation, NOT completion of its merchant inventory, trade/ritual logic, wearable-head behaviour or local server implementation.
* Cookbook: the original ItemCookBook right-click returns a vanilla written_book with generated recipe NBT. It does not open the campfire FML GUI. A source-admitted main-hand use returns Fabric SUCCESS to send the ordinary use packet, then waits at most 100 client ticks for the real server's replacement in the same slot. Only then invoke the native client open-book handler. Abort on world/player/connection/slot/screen changes or timeout. No invented recipe pages, fake stacks or additional packet are sent. Real-server inventory replacement and UI rendering still need testing.

The three new families are admitted by closed instruction templates plus recovered source registrations and dependency/model checks. Renamed source classes are tested; arbitrary modified implementations are not universally supported. The converter never loads or executes the original mod bytecode.

## Verification

* Local JDK21 targeted compilation and pinned integration into the exact delivered rev195 input.
* 176 prior recording-fixture assertions plus 56 new recording-fixture assertions.
* 17 source-mutation/emitted-contract assertions; changed steam gate, wrong returned item, altered head geometry and missing renderer registration fail closed. Renamed classes remain admitted.
* Old/new released tray method comparison: radius 0.175 to 0.25. rev195 Property and world-clock regressions remain passing.
* 17 related conversion passes ran on the original Bamboo JAR and finished PARTIAL, emitting all three new contracts and the head model. This is NOT the entire conversion engine.
* ASM BasicVerifier: 23 changed/new classes, 180 methods; 892 bridge-internal member references resolved. Targeted Property/clock scan: 1,452 classes, 14 Property calls, 4 current-clock calls, no targeted ABI errors.
* Independent fresh-directory local rebuild produced identical bytes. The 17-file normal-symbol source delta applied cleanly to an isolated restored rev195 source subset and matched intended files. The five previously untouched base source files were checked against the remote Git blob hashes.
* 1,444 prior archive entries remain byte-identical. The Fabric manifest and existing Mixin configuration are preserved. No original-mod, dependency or API-fixture classes are bundled into the release.

All runtime verification uses explicitly labelled API doubles, not actual Minecraft/Fabric/Mixin or a live server. No clean Gradle/Loom build was performed. An attempted full engine run stopped because this container lacks the Mojang DFU `DynamicOps` dependency; the selected pass run completed. Complete water-surface/fluid parity, underwater motes/drips, dyed-water visuals, full VillagerBlock gameplay and the existing optional GridPot insertion-rule conflict remain outside this delivery. No Actions build was started.

## Install

Close Minecraft. Replace all older main LegacyForgeBridge JARs with this one; remove the obsolete separate visual-stack hotfix if present. Keep dependencies, settings and original `old-mods`. Let rev196 regenerate converted output, then restart the client before testing. Its converter fingerprint is `2026-09-25.196-source-scale-steam-head-and-books`.

After regeneration the dedicated log should report three source-bound auxiliary presentation rules for Bamboo. Cookbook use reports either the received server book or a bounded timeout, so absence of a book is not hidden behind an empty custom UI.

## Source restoration

Root `src/` is still the rev188 base plus cumulative checkpoints, not expanded rev196. Run:

```
python checkpoints/rev196/restore.py --output ../LegacyForgeBridge-rev196
```

The checksum-guarded payload includes the 17-file normal-symbol source delta, targeted offline builder, corrected compile-only API declarations and regression source/logs. Restoration first invokes rev195 restoration, validates every old source hash, applies the new files and installs the local validation bundle. It does not start a build or Actions. See `verification.json` for exact scope and limitations.
