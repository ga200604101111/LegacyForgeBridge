# alpha.25 — completed source behavior integration (2026-09-14)

This continues `feature/runtime-content-conversion-rpgtool` at paused commit
`dea93bf3a903aecf486e4ce8d3e20a0eab207f16`. It does not incorporate the retired alpha.14 PR.
The runtime version is `0.2.0-alpha.25`; distributed classes use `dev.yinghuang`.

## Completed for the supplied original corpus

Original: `RPGTool1-1.1-1.7.10.jar` (14,556,748 bytes).
SHA-256: `b82cd54d2d2db576e82ba02ea4e55b4db92174c5814b45aa3ebbe1b44d98961d`.
The original binary is external and is not committed.

The common source compiler now admits all selected callbacks for the 71 recovered item
construction sites and 3 source event handlers; the actual original produces an empty
`behavior-analysis.json` unsupported list. This is bounded callback coverage, not a claim
that every possible Forge API, recipe or third-party mod is supported.

- Tooltips execute original source text and arithmetic against the current stack NBT.
  Chinese wording, section-sign styles, sockets, individual gems, aggregate bonuses and
  skill descriptions come from the source. Only its existing zh_cn locale is emitted.
- Right-click drilling (including the three-socket limit), attack/defense/lifesteal gems,
  skill gems, consumption, original slot-zero targeting, messages and cleaning/drop
  return now pass through generic Inventory, Chat and EntityItem adapters.
- Ordinary sword blocking and NBT-selected BOW charging retain source use/duration/release
  logic and native Minecraft use/release networking. There is no synthetic mouse monitor.
- The source charged-area and lightning release callbacks, burn-conditioned area hit,
  area strike, chance health reduction, poison/wither hit and directional knockback now
  compile. Damage amounts, conditions, random chance, area, charge threshold and wear
  remain in generated source methods; the runtime contains no RPGTool skill-name table.
- Wing jump/fall, armor defense, attack/lifesteal event arithmetic and selected-sword
  passive effects retain source branches. Native server callbacks dispatch real damage,
  potion effects, durability, drops, lightning and sound operations, not logging placeholders.
- Source hit callbacks include inherited sword wear. The native Weapon component has
  zero additional hit wear for these callbacks, preventing a second modern wear charge.
- Source registries initialize transactionally, abort on constructor failure and publish
  no half-built facets. NBT primitives retain their numeric types and unrelated nested
  fields rather than passing through lossy JSON. Serialized hook order is stable across JVMs.

## Logical-side boundary

A remote Forge 1.7.10 server already executes original inventory/skill/damage handlers.
The modern client retains original use/release/attack packet flow and does not repeat
server-authoritative effects. Snapshot inventory/NBT writes, world spawns, damage, potion
and durability operations are applied only on the logical server in an integrated world.
Client callbacks may provide local messages/particles and predict the local player's
movement, but never another player's health or motion. Inventory callbacks visit the
36 main slots; armor callbacks are separate, and offhand is not treated as legacy armor.

## Verification performed

Using the exact external ASM/Fabric/Minecraft dependencies exported by the successful
baseline CI, the complete locally compiled suite passes **98 tests, 0 failures, 0 skipped**.
This includes **11 opt-in real-corpus tests** executing the newly generated programs from
the original JAR, plus independent alchemy/astronomy fixtures (not RPGTool-name special cases).

The corpus cases cover all 71 item descriptions, live NBT changes, socket limit and gem
consumption/return, armor/skill eligibility, BLOCK/BOW distinctions and 72,000 ticks,
19-versus-20-tick release threshold, area/lightning requests, hit skill branches and wear,
conditional wing removal/fall, damage `10 + 3 - 2 = 11`, lifesteal `10 -> 10.55`, and
passive selection/refresh thresholds. These are executed source-program tests, not a
claim of observed gameplay. Native NBT/style and bytecode dispatch tests also pass.

Two separate JVM conversions of the same source produce identical candidate bytes.
The final Gradle build, annotation processing, remapped JAR and no-bundled-ASM/no-old-
namespace guards must also pass before delivery; their actual run is recorded with the
verification branch and downloaded build artifacts.

Run the optional corpus test with environment `LFB_CORPUS_JAR=/path/to/original.jar`.
CI intentionally does not store the original third-party JAR, so the external corpus
container is skipped when that environment variable is absent, not reported as a real
sample pass. Ordinary generated-fixture tests remain active in CI.

## Remaining acceptance (not silently passed)

No GPU/client startup, real Forge-server connection, first-/third-person screenshot,
release-packet capture or multiplayer combat smoke test has been performed here.
Existing alpha.25 hand-space/OBJ/UV/equipment animation work is retained and covered by
its arithmetic/structure regressions, but visual equivalence still needs real-client
acceptance. Recipe migration and universal standalone Forge gameplay coverage are not
established by callback coverage. Keep the runtime feature off main until live acceptance.

The converted JAR remains a loader-safe test artifact; existing conservative overall
`PARTIAL`/installability metadata is not overridden merely because callbacks pass.
Use the test output `RPGTool1-1.1-1.7.10-lfb.jar` alongside the matching alpha.25 bridge,
not alongside an older converted copy of the same mod. Original Forge JARs belong in
`old-mods`, never directly in a Fabric `mods` directory.
