# rev242 — bounded previous-descent handling and source landing particles

Branch: `feature/generic-conversion-iyamato-corpus3`
Parent: `038581a307f9eccdb93d24ca5a30b0117c228cb1` (rev241)
Runtime: Fabric Minecraft 1.21.11 / Java 21 -> unchanged Forge 1.7.10 server.

## Delivered artifact

- `legacyforgebridge-0.2.0-alpha.27-rev242-corpus4-local.26.jar`
- 4,497,254 bytes
- SHA-256: `dadc5024b8713781684caa57ca93d55f54506c2abbf205366510a3f932ab8f44`
- Version: `0.2.0-alpha.27-corpus4-local.26-rev242-local-test.1`
- Converter revision: `2026-10-02.242-stationary-descent-and-source-landing`
- Conversion-cache compatibility remains `0.2.0-alpha.27-corpus4-local.17-rev233-cache.1`.

The complete main JAR is delivered in the chat. This checkpoint contains its local
build source, guarded patcher, source-backed regression harness and evidence.

## Evidence and scope

The uploaded rev241 log records 30 landings. Twenty-nine reach approximately
2.165818 blocks above takeoff. Jump 12 instead stops at 1.442396 blocks when the
client's upward Y velocity 0.305952 is overwritten by a negative packet -0.251236.
The log proves that overwrite, but not why the server emitted the packet.

The original, SHA-pinned RPGTool JAR contains a jump handler adding 0.15 to motionY
when the chest item inherits WingBase. Its fall handler cancels the fall event and
queues ten `flame` particles with Gaussian velocity components multiplied by 0.04.
There is no continuous wing trail or extra height multiplier in those methods.
The production patch does not dispatch by RPGTool/wing name, entity ID or numeric
registry ID. The original names appear only in the read-only source test fixture.

### Motion

The existing exact-codec ascent matching is retained. rev242 additionally retains
one fully observed, stationary jump until its verified ground contact. During an
immediate subsequent stationary ascent, one negative packet may retain the current
predicted Y only when it exactly matches an observed descending phase of that
previous jump. The next jump must begin within two ticks; the packet must arrive
within eight ticks of landing and within the first six phases of the new ascent.
Incoming X/Z must be zero for this added guard, and all incoming X/Z values remain
untouched in every case. The prior source launch speed must also match.

Damage/teleport/explosion barriers, changed trajectories, moving jumps, fluids,
flight, expired windows and unmatched packets retain server authority. A prior
landing can account for at most one cross-jump negative packet. The implementation
never extrapolates unobserved future velocities. There is no fixed jump-height cap
and no alteration to the original jump event's 0.15 boost or native gravity.

**This remains a bounded heuristic, not a protocol acknowledgement.** Legacy S12
packets contain no jump ID/server timestamp. An unrelated identical encoded impulse
without a visible barrier can still be misclassified. Moving cross-jump cases are
intentionally not guessed. Paired native 1.7.10 gameplay has not been measured.
This does not restore the older abnormal 3–5-block double-pull heights.

### Landing effects and coordinates

`LegacyBehaviorRuntime.fall` keeps its public signature and delegates through a
local-player wrapper. The original body is preserved as `lfb$fallBeforeRev242`.
The normal native hook remains, with a client-end-tick ground-transition fallback
for clients/paths that bypass that damage hook. Both paths share player/tick
suppression to avoid duplicate particle emission. The fallback measures actual
downward displacement, excludes fluids/climbing/flying, resets on session and
position barriers, and never invokes native server damage simulation.

While a local legacy FALL event runs, `Snapshot.fill` exposes old local-player
`posY` as modern feet Y plus `(double)1.62F`. This is a scope-local source-coordinate
translation, not a live player move and not a global particle offset. Other source
events and other entities keep their existing coordinates. The original source
program still owns particle count, name, random spread and cancellation. No
particle loop is invented in production. `Snapshot.commit` is bytecode-preserved,
including its client/server effect gates and existing native flame renderer call.

Diagnostics:
- `REV242_ARM`, `REV242_RECONCILED`, `REV242_PASS`
- `REV242_FALL_DISPATCH` (event dispatch, not visual proof)
- `REV242_FALL_PARTICLES` (queued source particles, not visual proof)

## Build and validation actually performed

JDK 21 `javac --release 21` compiles the new helpers. JDK-internal ASM performs
exact guarded edits to the cumulative rev241 JAR. This is not a full Gradle/Loom
rebuild. No build dependencies were downloaded and no GitHub Actions build was used.

```sh
python3 build_local.py /path/to/rev241-main.jar /path/to/RPGTool1-1.1-1.7.10.jar /path/to/rev242-main.jar
```

The script checks input hashes, compiles, extracts only the two original source
method bodies for tests, creates separate Minecraft/logger/snapshot test doubles,
runs regression tests and audits the output. No test doubles or original old-mod
classes are packaged into the main JAR. The original JAR is never modified.

- Recorded 30-jump stationary-Y replay: baseline reproduces 29 full / 1 shortened;
  fixed gives 30 full / 0 shortened. This isolates Y packet behavior and is not a
  full XYZ/world replay of the user's game.
- 1,000 consecutive simulated jumps with delayed previous descent plus ascent
  packets: all retain the unchanged source-native peak.
- Real source event bytecode, remapped only for test API types: ten flames,
  unchanged boost, correct source Y basis, native/fallback deduplication, no-wing
  behavior, teleport/water exclusions and exception-scope cleanup pass.
- Guard tests cover unmatched downward impulses, one-use archive, horizontal
  impulses, moving jumps, expiry, hurt, explosion, water, flight, arbitrary motion,
  world changes, remote entities, Netty dispatch and competing packet handlers.
- Fixed suite: 6,224 assertions; baseline: 114; reconciliation-off: 45.
- Actual packaged-helper suite repeated successfully with `java -Xverify:all`.
- Core bytecode audit: 76 existing method bodies preserved after accounting for
  the explicit narrow hooks/log literal; 10 structural class checks pass.
- ZIP integrity, duplicate entries, Java 21 class format and `javap -v`: pass.
- 1,701 -> 1,704 entries: 10 changed, 4 added, 1 obsolete private Api class removed.
- All other entry contents unchanged, including water fixes, block carrier,
  manifest, nested Energy JAR and desktop helper.
- Second clean local build: byte-identical main JAR.

**Not tested:** launching Minecraft/Fabric/Sodium, applying the mixins in the actual
modpack, connecting to the live old server, visual particles/shaders, remote-player
landing effects, and native-client height parity. API test doubles are not the game.

Signature cross-check: Fabric Yarn 1.21.11+build.4 documents `handleFallDamage`
(`method_5747`) and `ParticleTypes.FLAME` (`class_2398.field_11240`). Existing runtime
API names were additionally checked directly from the cumulative installed binary.

## Installation and repository boundary

Fully exit the client, replace the rev241 main JAR in `mods` with rev242, keep only
one main LegacyForgeBridge JAR, and restart. Keep original old-mod JARs and server
unchanged. No conversion-cache wipe or new launch argument is needed. Existing
`-Dlegacyforgebridge.jumpReconcile=off` disables motion matching but not landing
presentation. Do not add that argument for the normal test.

Only this checkpoint is added on the requested branch, with `[skip ci] [skip actions]`.
No workflow edits/dispatch, PR, tags/releases, force push, main or Bamboo changes.
