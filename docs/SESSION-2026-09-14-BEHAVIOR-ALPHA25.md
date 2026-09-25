# alpha.25: source callbacks, native use lifecycle, tooltips and equipment behavior

> Historical paused-WIP notes. The completed integration and actual original-corpus test results are in [ALPHA25-COMPLETED-BEHAVIOR.md](ALPHA25-COMPLETED-BEHAVIOR.md). The unsupported-callback list below describes the paused snapshot, not the completed implementation.

## Corrected source evidence

The uploaded original RPGTool JAR has SHA-256 `b82cd54d2d2db576e82ba02ea4e55b4db92174c5814b45aa3ebbe1b44d98961d`. Earlier statements that SwordBase did not override use behavior were incorrect. Its bytecode overrides right click, use action, duration and release. No-tag/ordinary swords block; `right_range` and `light_range` use a charged BOW action. Other source conditions are preserved, not replaced with unconditional blocking.

ViaVersion's 1.21.5 item data fix checks the five vanilla numeric sword IDs and attaches BLOCKS_ATTACKS. An LFB item temporarily transported as paper misses that branch. Native tool tags alone cannot repair the use state. alpha.25 retains the reversible paper identity carrier, then restores the native blocking component at the final modern boundary according to the source-compiled action. It uses the same delay/damage-function parameters as Via's vanilla fix. Generated items use an ordinary modern Item subclass: the source callback decides whether to begin, Player.startUsingItem enters the real native state, and the existing Minecraft/Via release flow ends it. There is no synthetic mouse monitor or unconditional release-packet spam.

## Generic source callback compiler

LegacyItemRenderAnalyzer now also recovers bounded source item construction sites, constructor arguments and full-3D/rotation properties. LegacyBehaviorCompiler validates reachable callback methods and remaps them into candidate-owned JVM classes against a narrow, version-neutral adapter API. Source classes are not loaded by analysis. The generated classes are JVM-verified without initialization before writing the candidate. Unsupported dependencies reject an individual callback and produce an explicit behavior-analysis report.

The common pass covers tooltip, item use/action/duration/release, inventory/armor tick, and directly registered normal-priority jump/fall/hurt handlers where API operations are supported. Callback code, literals, NBT keys and arithmetic come from the source JAR, not a mod-name switch or RPGTool effect table. GeneratedContent initializes its own behavior bootstrap before registering native items. The original loader-safe generated namespace rules remain in effect.

This is a bounded compatibility subset, not arbitrary Java/Forge emulation and not a security sandbox. General item/block registration extraction remains limited by the existing content conversion profiles. Reflection, arbitrary world/entity operations, unsupported event priority and unmodelled APIs are diagnosed rather than invented.

## Tooltip and equipment behavior

Source tooltip callbacks read the current stack's original CUSTOM_DATA NBT. Source Chinese text, section-sign formatting, line ordering within the source callback, socket counts, individual gem values, aggregate attack/defense/lifesteal, and skill descriptions are retained. Existing source language aliases are used; no new locale is fabricated. Native tooltip placement follows Fabric's callback, so its position relative to native attribute/advanced lines is not claimed identical to 1.7.10.

Equipment source callbacks are adapted at native jump, fall and pre-armor damage boundaries. Integrated-server inventory ticks run admitted source potion/passive callbacks. The original corpus's wing jump increment, fall cancellation/particle requests, gem attack/defense and lifesteal arithmetic are source-compiled. The client only predicts its own movement/local effects; health, damage, potion and skill results on a remote Forge server remain authoritative there, avoiding duplicate application.

## Hand coordinate conversion

Use a neutral hand-model base with explicit identity display transforms. Cancel the modern ItemInHandLayer arm-local basis, restore the legacy RenderPlayer and Forge helper chain, then apply source-local transforms in order. Separate native using-item/blocks-attacks model branches preserve normal versus blocking placement. Left-hand transforms are reflection-conjugated. The GUI source sprite and the already-fixed OBJ topology/UV paths are retained. Independent affine-chain tests compare transformed positions, not only a list of constants.

Camera/equip/swing poses still come from the modern client/Via. GPU screenshots, exact third-party animation interaction and first-person visual equivalence require live testing; no pixel-identical claim is made.

## Checks performed before CI

The actual uploaded original yielded 71 proven construction sites with no construction diagnostics. Its behavior compiler produced 71 item facets, 3 event handlers and 18 generated classes. A local Java 21 harness loaded and initialized these generated classes using an internal-ASM stand-in (production uses the existing external ASM dependency).

Executed checks included dynamic tooltip sockets/gem values; ordinary BLOCK versus right_range/light_range BOW; 72,000-tick source use duration; wing vertical velocity 0.42 -> 0.57; source fall cancellation and 10 particle requests; source hurt arithmetic 10 + 3 - 2 = 11; source 5% lifesteal health 10 -> 10.55. These are source-program checks, not observed Minecraft/server results.

Independent alchemy/astronomy fixtures compile their own legacy APIs outside the fixture JAR, then verify emitted callback execution, changing NBT tooltip data, disabled/charged/ordinary use paths, release callbacks, equipment removal and unsupported-call diagnostics. Additional tests verify candidate-owned initialization order and hand-space matrices. Full CI is required before distributing the runtime JAR.

## Explicit remaining scope

Complex RPGTool charged-area/lightning attacks, some right-click gem/inventory/chat mutations and arbitrary hitEntity combat logic are not completely ported for standalone modern worlds. They remain listed as unsupported callbacks. On the user's original 1.7.10 server those original server handlers still perform their own actions when the client's ordinary use/release/interaction packets arrive. Live release synchronization and visuals are not certified by unit tests.

Retained: source-basename-lfb.jar output, source-only locales, source equipment animation code, native tool tags, no bundled ASM, and dev.yinghuang distributed classes. PR remains unmerged pending live acceptance.
