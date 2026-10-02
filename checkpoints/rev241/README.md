# rev241 — source-jump history reconciliation (local test build)

Branch: `feature/generic-conversion-iyamato-corpus3`.
Parent: `99df9f07ed1d86b27c6062a70a45bbecc54ee90d` (rev240).
Runtime target: Fabric Minecraft 1.21.11 / Java 21 -> unchanged Forge 1.7.10 server.

## Report and evidence

The user's rev240 diagnostic contains 14 ground jumps, each with one converted
source jump event. The recorded event raises Y velocity from approximately 0.42
to 0.57. Thirteen positive incoming velocity packets later restore a larger,
earlier ascent velocity; one additional incoming packet is negative and only
slows descent. Two jumps receive two positive packets each. The highest recorded
rise is 5.846647832927157 blocks, versus 2.1658178319081456 for unaffected jumps.

`tests/jump-fixture.json` / `.tsv` contain only sanitized relative timing and
velocity values. The full user log, coordinates, machine paths and entity IDs
are NOT committed. These fixtures are evidence, not production dispatch keys.

## What the old/new comparison actually established

- The inspected MCP 9.08 source mirror identifies both client and server as
  Minecraft 1.7.10 (`CodeMajorGeek/lwjgl3-mcp908/conf/version.cfg`). Its
  `NetHandlerPlayClient.handleEntityVelocity` also assigns all three absolute
  velocities, using short / 8000. There is no vanilla jump-echo acknowledgement
  or built-in historical reconciliation in that handler.
- The inspected 1.7.10 server handler detects an upward ground-to-air C03
  transition and calls the server-side player's `jump()` independently.
- ViaFabricPlus `ver/1.21.11` already has compatibility mixins for the newer
  `Math.max` jump-velocity behavior and old idle movement packets. This was a
  source review, NOT a binary audit of the user's installed ViaFabricPlus 4.4.15.
- The supplied dry-air motion follows the legacy gravity/drag recurrence, and
  the recorded Via output agrees with the packed-vector codec shipped in the
  user's main JAR. The significant discontinuity is the later velocity apply,
  not the tiny codec quantization difference.

This does NOT prove a unique 1.21.11-only engine defect or identify why the server
emitted each packet. No paired 1.7.10/1.21.11 client run or server emission trace
was available. rev241 adds a bounded compatibility reconciliation rule; it is
not presented as an exact restoration of all vanilla 1.7.10 semantics.

### Source review identifiers

- `CodeMajorGeek/lwjgl3-mcp908`, version.cfg blob
  `ae2ffeed186b1871891efc75e7d28923c9728ae4`.
- Old client `src/minecraft/net/minecraft/client/network/NetHandlerPlayClient.java`,
  blob `643800f096a6bcad667c50ac7939e2c9f07006ee`, velocity handler at reviewed
  lines 495-502.
- Old server `src/minecraft_server/net/minecraft/network/NetHandlerPlayServer.java`,
  blob `30f726c523d90a82411aaaebeaac68c815148cb8`.
- ViaFabricPlus movement/jump `MixinLivingEntity.java`, blob
  `df393318e011bd5276e307298bbbdcc30472636c`.
- ViaFabricPlus movement/packet `MixinLocalPlayer.java`, blob
  `0c81b1300ffaed401c2bef6d11778ac56a6a6cfd`.
- Production intermediary member names match the real rev240 lifecycle bytecode;
  API context also reviewed in Fabric Yarn 1.21.11+build.4 documentation.

## Behavior and guardrails

The existing source jump runs once and its result remains unchanged. Only a
finite, positive source-event Y increase from a dry grounded local multiplayer
player arms a window. The window records actual native gravity writes, for at
most 40 ticks. It is tied to the same player, world and network connection.

An incoming positive Y can be reconciled only when it exactly matches an
already observed ascent sample after legacy-short and modern-vector encoding,
and the current local trajectory has remained continuous. On a unique match,
the packet's X/Z are kept exactly and Y is restored to the current predicted
phase before the next physics update. There is no fixed jump-height cap and no
production lookup of the observed 0.57 value, mod name, item name or entity ID.

The existing packet is not cancelled or modified. No outgoing movement,
position, world, collision, original source mod or server code is rewritten.
Nonmatching, negative, zero and oversized velocity packets pass unchanged.
Damage/status, explosion, position correction, respawn, landing, water/lava,
climbing, flying, riding, unexpected Y writes, clipped motion, changed identity,
missing packet tails and expiration invalidate the window. Another handler's
changed velocity is not overwritten. Reflection failures disable reconciliation
and retain the complete server velocity. Netty dispatch is not treated as a
client application. Behavior is independent of trace logging's time/line limits.

**Inherent limitation:** an old S12 packet has no jump ID or server timestamp.
An unrelated upward impulse with the exact same encoded historical Y and no
invalidation signal can still be mistaken for a historical return. Conversely,
unrelated entity-status traffic may conservatively invalidate a legitimate
window. Exact-codec matching is a heuristic, NOT proof that a packet is an echo.
Combat and special movement still require real gameplay regression testing.

Default is enabled. To disable only this new rule, use JVM argument:
`-Dlegacyforgebridge.jumpReconcile=off`.
The previous `legacyforgebridge.jumpEcho` property remains diagnostic/ignored.

## Build and artifact

This is a COMPLETE cumulative main JAR locally built over the exact supplied
rev240 main JAR. New helpers are compiled by JDK 21 (`javac --release 21`). JDK
internal ASM adds narrow, straight-line hooks to existing lifecycle methods and
updates version/readiness metadata. Existing StackMap frames are retained; max
stack/local sizes are recomputed. No new mixin injection target is introduced.
This is NOT a full Gradle/Loom build. No dependency downloads or Actions builds.

```sh
python3 build_local.py /path/to/legacyforgebridge-0.2.0-alpha.27-rev240-corpus4-local.24.jar \
  /path/to/legacyforgebridge-0.2.0-alpha.27-rev241-corpus4-local.25.jar
```

Baseline SHA-256:
`61afe2b2a21ff4ae2f2fae2afe3f95bf2013f19e2f3d461cd2577ed5c1fa8a45`.
Other baselines are rejected. Test doubles remain outside the packaged main JAR.

- Artifact: `legacyforgebridge-0.2.0-alpha.27-rev241-corpus4-local.25.jar`
- Bytes: 4,489,462
- SHA-256: `7ebcea2bd669fb6baed5b99e5929d68d9a356660035d9b2cd67e6ef3864b9835`
- Internal version: `0.2.0-alpha.27-corpus4-local.25-rev241-local-test.1`
- Converter revision: `2026-10-02.241-source-jump-history-reconciliation`
- Cache compatibility: `0.2.0-alpha.27-corpus4-local.17-rev233-cache.1` (unchanged)

Only three existing entry contents change: BuildInfo, LegacyClientJumpMotion and
fabric.mod.json. Seven entries are added; none removed (1694 -> 1701 entries).
All other contents are byte-identical, including the rev240 water fix, converted
block identities, packet mixins, armor/projectile/UI fixes, manifest and nested
JARs. The artifact is delivered in chat; source/build/test evidence is in GitHub.

## Validation actually run

- Original rev240 lifecycle class, numerical replay of all 14 recorded jumps:
  225 assertions. The recorded peak heights are reproduced within 1e-10.
- Patched lifecycle + real new helpers, 14-jump replay and safety cases:
  included in 5,352 assertions. All 13 positive historical matches reconcile;
  the negative packet passes. Replayed corrected peaks are 2.1658178319081456.
- 1,000 consecutive same-session synthetic jumps with two delayed historical
  packets per jump: one source-height arc each, including after trace line cap.
- OFF mode: 225 assertions; reproduces baseline behavior and original peaks.
- Actual main-JAR lifecycle methods and original packed-vector codec execute
  under `java -Xverify:all`; Minecraft/Fabric classes and the source event are
  explicitly TEST DOUBLES, not a running game or the original RPGTool runtime.
- The same suites pass against the packaged output JAR.
- Changed/new class `javap -v`, SHA-pinned patch guards, ZIP integrity, duplicate
  and content-preservation audits: pass.
- Independent second local build produces a byte-for-byte identical main JAR.

**Not run:** Minecraft/Fabric/Sodium launch, actual mixin application with the
user's installed modpack, live Forge server connection, paired old/new gameplay,
combat or fluid/ladder/vehicle gameplay, in-game water visual regression.
Numerical replay cannot prove the exact server emission reason or every future
special-movement interaction.

## Installation and test markers

Fully quit the client. Replace the rev240 main JAR with rev241 in `mods`, keeping
only one LegacyForgeBridge main JAR. Keep the server, old-mods and conversion
cache unchanged. Restart fully; an F3+T resource reload is not sufficient.

The startup log contains `source motion compatibility READY rev241`.
The existing movement diagnostic gains `REV241_ARM`, `REV241_RECONCILED` and
`REV241_PASS` markers. Reconciliation logs expose incoming Y, retained Y and
matched/current phase; no extra user setup is required. The old trace line cap
still applies to logging, but does not disable the new behavior.

No Actions dispatch, workflow edits, PR, tag/release, force-push, main-branch or
Bamboo-branch edits are part of this checkpoint. Commit message includes
`[skip ci] [skip actions]`.
