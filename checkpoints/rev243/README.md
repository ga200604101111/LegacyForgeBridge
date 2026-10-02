# rev243 — observation-only motion diagnostics

Branch: `feature/generic-conversion-iyamato-corpus3`.
Parent: `2acf7d9481bb6f77d97a230ec9104d313a7360d4`.
Target: Fabric Minecraft 1.21.11 / Java 21 connecting to an unchanged Forge 1.7.10 server.

This is a diagnostic main JAR, NOT a claim that the original jump defect is fixed.
It removes the rev241/rev242 heuristic motion compensation from execution so that
new traces no longer mix server-applied motion with our velocity restoration.
No JVM enable property can reactivate those heuristics in this artifact. Original
source-driven jump events and the independent rev242 landing/particle adapter remain.
Original jerk or premature descent may consequently reappear while capturing.

## Installation and capture

1. Fully quit the client, move the previous main LegacyForgeBridge JAR out of `mods`,
   and install `legacyforgebridge-0.2.0-alpha.27-rev243-corpus4-local.27-diagnostic.jar`.
   Keep only one main JAR. Keep the original legacy mod JARs, server, and conversion cache.
2. Join the legacy server. Capture starts automatically when the existing session
   hook establishes the local player. No additional Java parameters are necessary.
3. Test ordinary jumps, wing jumps, moving jumps and the unwanted visual movement.
   `/lfbtrace mark` marks the time a symptom is noticed.
4. `/lfbtrace stop` stops accepting new capture records and drains queued records
   on the dedicated writer. `/lfbtrace start` starts a NEW capture, never overwrites
   the previous capture. `/lfbtrace status` shows current capture status.
5. Send the timestamped files from `logs/lfb-motion/`, plus `logs/legacyforgebridge.log`.
   Zip the folder after stopping/closing. Do not replace it with a screenshot or
   manually synthesized sample. No new test log is claimed to come from the user's game.

`lfbtrace` controls are intercepted by Fabric's client ALLOW_COMMAND event; no
control is sent to the server. All unrelated command strings return true and their
text is NOT recorded. These controls are not a server permission or admin command.
If the optional command registration fails, the ordinary log reports that failure;
joining and disconnecting still start/stop the capture through the existing hooks.

## New and retained observations

- Existing source jump, native velocity/position, raw legacy wire/Via codec and
  client application stages stay attached through their existing call sites.
- Capture is no longer restricted to an eight-second post-jump window or a lifetime
  30,000-record quota. Local motion outside a jump is also eligible for logging.
- START_CLIENT_TICK and the existing end-client-tick hook capture player position,
  velocity, last Y, eye Y, box, collision flags, fall distance and environment state.
- Jump/sneak key held state and consumed PlayerInput snapshots are read without
  consuming keypresses. State changes and snapshots are logged separately.
- Two new optional mixins observe Entity.move HEAD/RETURN and Camera.update RETURN.
  Move records pair requested and actual displacement; camera records include the
  real camera position, interpolation progress, player/last/eye Y, and view mode.
- Source event wrappers preserve single dispatch, cancellation/result values, and
  exception identity; before/after/throw states are recorded for local source events.
- Source particle enqueue records include type, position and velocity. This is queue
  acceptance, NOT proof that a particle passed culling, shaders or appeared on screen.
- Header metadata includes the loaded class-container SHA-256 when available, actual
  BuildInfo, Java, Minecraft, Fabric Loader, Fabric API and ViaFabricPlus versions.
  Unavailable metadata is explicitly marked rather than guessed.
- Existing rev220/rev222 header literals in the ordinary launch/FML logs are corrected.

Every record has producer-side monotonic ns, UTC, sequence, thread, local entity,
local session and local jump observation label. The local jump label is NOT a
server causal ID. Thread interleaving can produce file order different from record
sequence; retain timestamps and labels when correlating records.

## File I/O and completeness

The high-volume stream is written only by `LFB-motion-log-writer`. Producers enqueue
immutable strings through a bounded 8,192-entry queue and do not wait for free queue
space or perform file writes. The ordinary logger receives sparse status notices,
not every motion line. The writer flushes at approximately 500 ms intervals, at part
boundaries and at normal close. There is no per-record fsync claim.

Parts rotate at approximately 16 MiB. At approximately 128 MiB per capture, further
logging is stopped with TRACE_LIMIT_REACHED; gameplay is not stopped. Existing parts
are not overwritten/deleted. Use `/lfbtrace start` to deliberately start another
capture after the limit. Headers/trailers and the final record can exceed the nominal
size by a bounded amount.

Queue overflow, detail truncation (16,384 characters), discarded data and queue
high-water are reported by TRACE_HEALTH/TRACE_END. A missing end, missing hook,
writer failure, truncation, drop or disk-cap stop means the relevant capture may
be incomplete. This design does not claim impossible loss-free logging under all
loads. A bounded JVM shutdown flush cannot guarantee recovery from a process kill.
Extra instrumentation still has CPU/allocation cost; no real-game overhead or
frame-time benchmark has been performed.

## Build and exact artifact

Full cumulative main JAR, incremental local build over the exact supplied rev242.
No Gradle/Loom rebuild, Actions execution or dependency downloads were used.
The repository checkpoint stores readable UTF-8 source, the patch generator,
explicit test doubles, regression harness and build script. The chat delivery also
includes an optional source-and-tests ZIP. From the repository root, run:

```sh
python3 checkpoints/rev243/build_local.py /path/to/rev242.jar /path/to/rev243-diagnostic.jar
```

Baseline SHA-256: `dadc5024b8713781684caa57ca93d55f54506c2abbf205366510a3f932ab8f44`.
Output size: 4,517,037 bytes.
Output SHA-256: `87d4e3cbe4939b21c4eb955b66508bbc2724aafbf6b557c87245cc345121ef63`.
The builder rejects a different baseline and refuses to overwrite it.

All original entries other than eight explicitly audited changes remain byte-identical.
Protected entries include both LegacyBehaviorRuntime classes, the rev242 landing
adapter, rev240 liquid fix, existing Via wire tracer, original mixin configuration,
manifest, Energy nested JAR and desktop helper. No test-double classes are packaged.
The two observer mixins are generated by the checked-in ASM patch script with exact
intermediary descriptors, non-cancellable injection and require=0 in a separate
optional configuration. Their actual game application still needs a client launch.

## Validation actually run

- JDK 21 compilation and guarded patch counts: pass.
- Packaged helper test with `java -Xverify:all`: 1,048 checks.
- 1,000 varied authoritative velocity cases: the new helper hooks leave them unchanged,
  including with the previous enabling JVM property deliberately set.
- Read-only input/camera/move snapshots; requested vs clipped actual move: pass.
- Synthetic source-event wrapper single dispatch, cancellation and exception identity,
  plus ten synthetic source particles: pass. This is NOT the original RPGTool handler
  test used in previous revisions and is not represented as one.
- Real JDK writer with four concurrent producers / 4,000 accepted records: all written.
- Controlled queue overflow, rotation, disk cap, truncation, close and new-capture
  path uniqueness: pass; all tested data losses were disclosed in the stream.
- Bytecode audit of six no-op motion hooks and three observational injectors: pass.
- ZIP validation, Java 21 class parsing, protected-entry comparison: pass.
- Independent second local build: byte-for-byte identical output.

## API checks and limitations

Signatures were checked against primary Fabric documentation, not a downloaded
Minecraft runtime. The build environment could not download Maven dependencies;
none were required for this incremental build. Tests use explicit Minecraft/Fabric
API doubles and the actual packaged Java helpers, not a running client.

Primary API references:
- https://maven.fabricmc.net/docs/yarn-1.21.11+build.4/net/minecraft/client/render/Camera.html
- https://maven.fabricmc.net/docs/yarn-1.21.11+build.4/net/minecraft/entity/Entity.html
- https://maven.fabricmc.net/docs/yarn-1.21.11+build.4/net/minecraft/client/input/Input.html
- https://maven.fabricmc.net/docs/yarn-1.21.11+build.4/net/minecraft/client/option/GameOptions.html
- https://maven.fabricmc.net/docs/fabric-api-0.141.1+1.21.11/net/fabricmc/fabric/api/client/message/v1/ClientSendMessageEvents.html

Not validated: live Minecraft/Fabric/Sodium startup, actual optional mixin application,
real particle visuals, performance in the user's pack, a paired original Forge 1.7.10
client, or server-internal causal sources. The camera sample is before final bobbing
or shader matrices. Existing wire hooks do not cover every packet class or every
possible direct field mutation. Missing hooks are surfaced rather than claimed.

No main/Bamboo branch, workflow, PR, tag/release, original mod or server change is part
of this checkpoint. Continuation commit message must contain `[skip ci] [skip actions]`.
