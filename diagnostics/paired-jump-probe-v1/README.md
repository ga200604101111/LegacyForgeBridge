# LFB Paired Jump Probe 1.0.0 — 2026-10-05

Independent observation-only Forge 1.7.10 coremod for BOTH the native client and server. NOT a Fabric mod, NOT a replacement for LegacyForgeBridge rev251, and NOT a claimed jump fix.

## Purpose and evidence boundary

The uploaded rev251 already provides modern-client/Via observations. This probe fills the missing native/server side:

- SERVER_C03_HEAD/RETURN captures the same C03 and server player state before/after processing.
- SERVER_C03_JUMP_CALL_BEFORE/AFTER is inserted at the actual server jump() invocation in processPlayer. Its packetKey identifies the exact triggering C03 object, not a nearest-time candidate.
- JUMP_HEAD/RETURN and FORGE_JUMP_EVENT_HEAD/RETURN bracket native jump and the whole Forge source-event invocation.
- S12_CREATE_VALUES/ENTITY_RETURN captures packet components and constructor call stacks. Constructors can delegate: two constructor records sharing packetKey are NOT two sends.
- SERVER_SEND_HEAD and NET_SEND_REQUEST_HEAD capture API/send requests, NOT proof of socket delivery.
- NET_RECEIVE_HEAD and CLIENT_S12_APPLY_HEAD/RETURN separate native arrival from actual velocity application. Network callbacks never snapshot live player state.
- CLIENT_TICK, CLIENT_PLAYER_TICK, CLIENT_WALK_SEND, SERVER_TICK, TRACKER_TICK and NET_DRAIN expose method/tick phases.

C03/C05 without coordinates omit the coordinate fields instead of reporting fake zero positions. Both posY and bounding-box feetY are logged; C03 Y and stance are separate fields. velocityChanged and isAirBorne remain separate flags.

packetKey and nanoTime are local to ONE JVM/capture. Never subtract client and server nanoTime. Matching S12 numeric values or nearby timestamps alone does not prove a cross-network packet identity or that a packet belongs to a specific earlier jump. The exact C03-to-jump call site is a stronger, narrowly scoped causal observation.

## Installation / 安裝

| Environment | Installation |
|---|---|
| Forge 1.7.10 server | Stop cleanly, put JAR in mods/, start a test instance first. |
| Native Forge 1.7.10 client | Put the SAME JAR in that instance's mods/. No LFB required. |
| Fabric 1.21.11 + ViaFabricPlus | DO NOT install this JAR. Keep existing rev251 unchanged. |

Default target is LongYu420. Change config/lfb-jump-probe.properties player= when needed. Missing config uses the same default. player=* records all players and is not recommended on a multiplayer server. No network upload is performed. Output is logs/lfb-jump-probe/forge1710-*.jsonl.

This is a coremod: validate HEADER, TRANSFORM and actual player snapshots, not only the ordinary mod list. TRANSFORM_FAILED, hooks=[] or no player snapshots invalidate conclusions about missing events.

## Paired test / 對照

Keep the same server, account, equipment, location and other mods. Server keeps this probe during both sessions.

A. Native 1.7.10: unobstructed flat ground, 3 separate jumps without wings, then 5 with wings, fully land between jumps. Then 2 reproductions at the problem platform. Disconnect and close client cleanly.

B. 1.21.11 + Via + unchanged rev251: repeat exactly the same sequence. Do not change Via/optimization settings at the same time. Close client and test server cleanly to flush logs.

Return separately labelled native client logs/lfb-jump-probe/, server logs/lfb-jump-probe/, and modern client logs/lfb-motion/ plus latest.log. Latest logs also establish the actually loaded Forge/Via/coremod versions. Native-only capture is useful but cannot prove the server's S12 construction source.

## Interpretation

More server jump call-site records only for the modern connection: inspect C03 ground flags, coordinates, order and server ground state. Same jump calls but different S12 constructor/source/send observations: inspect tracker or other mod/core hooks. Same server observations but different pre-apply native/modern height/velocity: inspect receive scheduling and tick phase. Different source velocity before packets: inspect LFB source-event/local physics. These are discriminating tests, NOT findings already observed in the user's runtime.

Do not delete +0.15, erase velocityChanged or cancel positive S12 packets based solely on this probe. Native 1.7.10 working correctly is still the baseline report.

## Non-mutation / limits

No player/packet field writes, packet filtering, added sends, or replacement game return values. Original game exceptions propagate; diagnostic exceptions are isolated. No cross-exception ThreadLocal packet-causality scope exists. RETURN hooks only execute on normal returns, so missing RETURN is not proof the method was never entered.

Observation costs CPU/time. Bounded asynchronous log queue: 8192 records; capture 600 seconds after first matched player, 200000 records, 64 MiB file. FOOTER includes drops, observerErrors, transformFailures, cap and matched-player status. Missing hooks or unhealthy/incomplete logs must not be interpreted as a complete causal chain. IN_TICK is a method interval, not client/server clock synchronization.

## Executed offline verification

45 MCP fixture checks + 45 SRG fixture checks + 32 generated-trace checks = 122 passed. Actual ASM transformation, JVM -Xverify:all, idempotence, one source invocation, preserved +0.15/flags, one S12 send/application, preserved exception identity, unchanged non-target gameplay, negative velocity and S08 handling, and correct field/packet identity logging were tested.

These are SYNTHETIC Minecraft ABI fixtures, not live Minecraft. Build used explicit compile-only FML/LaunchWrapper/ASM API declarations; none are packaged. Tests remap ONLY test copies to JDK21's actual ASM implementation in API5 mode. Forge's actual ASM 5.0.3/LaunchWrapper startup and the user's server were NOT run. Java classfile version is 52. An intentionally invalid-class test emits one TRANSFORM_FAILED in synthetic test logs; it is not a user-game failure.

The full offline build/fixture/test sources are included in the conversation's source test kit. build_with_forge.py can independently compile these three production sources against caller-provided actual Forge 1.7.10, LaunchWrapper and ASM5 JARs. It does not download dependencies or invoke Actions. Its build metadata differs, so do not claim a rebuilt JAR is byte-identical to the delivered artifact without checking.

analyze_probe.py summarizes individual captures without cross-JVM time subtraction or nearest-packet causal guesses.

## Artifact

lfb-paired-jump-probe-1.7.10-1.0.0.jar — 23303 bytes.
SHA-256: de5260042775e74b7494434066df6b7a4cd966dfe610e38746c13bf59d069281

Existing uploaded rev251 remains unchanged:
01b36aff7d6f5ddc2b0097356c74f568f15173c4fed821f5a6c9788236e65502

## Source references

Native reference (not proof of the user's actual server binary): CodeMajorGeek/lwjgl3-mcp908@2a6d28f2b7541b760ebb8e7a6dc905465f935a64, NetHandlerPlayServer.processPlayer, NetworkManager, S12PacketEntityVelocity and C03PacketPlayer. Official mapping/coremod ABI: MinecraftForge/FML branch 1.7.10, conf/fields.csv and IFMLLoadingPlugin.java.

Only this diagnostic directory is added on feature/generic-conversion-iyamato-corpus3. No LFB gameplay source, original mod, server configuration, workflow, main branch, PR, tag or release is changed. Commit uses [skip ci] [skip actions].
