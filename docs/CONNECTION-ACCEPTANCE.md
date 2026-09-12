# 1.7.10 Connection Acceptance Criteria

This document defines what LegacyForgeBridge means by "can connect to Minecraft 1.7.10". Reaching the PLAY state or seeing the world for a few seconds is not enough.

## Required connection tiers

### Tier 0 — Vanilla 1.7.10 baseline

A Minecraft 1.21.11 Fabric client using LegacyForgeBridge must be able to join a clean Minecraft 1.7.10 vanilla server through the selected protocol translation layer.

This tier proves that the base Minecraft protocol path is functional independently of Forge/FML.

Required checks:

- server-list ping/status works;
- login succeeds;
- join reaches stable PLAY state;
- player movement and rotation are synchronized;
- chat send/receive works;
- block break/place works for representable 1.7.10 content;
- item pickup/drop works;
- inventory slot synchronization works;
- chest/container interaction works;
- entity spawn/despawn and basic interaction work;
- health, damage, hunger and death work;
- respawn works;
- dimension/world change works;
- disconnect returns the client to a clean normal 1.21.11 state.

### Tier 1 — Clean Forge 1.7.10 baseline

A Minecraft 1.21.11 Fabric client must be able to join a Forge 1.7.10 server with no third-party gameplay mods installed.

In addition to every Tier 0 requirement, the bridge must correctly handle:

- Forge/FML server detection;
- FML connection marker behavior;
- legacy channel registration;
- `FML|HS` handshake traffic;
- ServerHello / ClientHello;
- ModList exchange;
- registry/ID synchronization required by the clean Forge session;
- HandshakeAck state progression;
- transition to stable PLAY state;
- clean disconnect and complete restoration of modern session state.

The server should treat the client as a valid Forge/FML-compatible client where Forge requires one, rather than relying only on vanilla-client fallback behavior.

### Tier 2 — One simple legacy mod

After Tier 1 is stable, add one deliberately simple Forge 1.7.10 mod to the server and matching converted/client compatibility content to LegacyForgeBridge.

Required checks:

- server-required mod list is recognized;
- converted mod identity matches the legacy server expectation;
- simple registered item/block IDs are mapped safely;
- inventory synchronization does not collide with modern registry raw IDs;
- the mod's primary simple gameplay action works.

Tier 2 is the first point where old-mod conversion is allowed to affect connection compatibility.

## Stability requirement

A connection tier is not considered complete after a single successful join.

Minimum regression target for Tier 0 and Tier 1:

- 100 consecutive connect -> play -> disconnect cycles;
- no client crash;
- no server crash caused by the bridge;
- no stale Legacy Session state after disconnect;
- a normal Minecraft 1.21.11 server can still be joined afterward without restarting;
- no unsupported modern item is emitted to the 1.7.10 server.

## Gameplay smoke-test sequence

Each Tier 0/Tier 1 regression cycle should include at least a representative subset of:

```text
join
move / sprint / jump
chat
break block
place block
pickup item
move item between inventory slots
open chest
move item through chest inventory
attack / receive damage
consume food
use a basic item
change dimension or world when available
die
respawn
disconnect
join a normal 1.21.11 server/world
```

Longer soak tests should additionally verify chunk loading, entity tracking, inventory consistency and repeated dimension changes.

## Item compatibility boundary

LegacyForgeBridge does not downgrade the entire modern UI or registry.

The primary safety boundary is server-bound `ItemStack` / `BlockItem` compatibility:

```text
modern client item action
    -> legacy item mapping check
    -> legacy numeric/metadata or registry representation
    -> encode 1.7.10-compatible network state
```

If a modern-only item has no safe legacy representation, the outbound item action is cancelled. Modern HUD, menus and local rendering do not need to be globally downgraded just because the target server is 1.7.10.

## Failure classification

Connection failures should be assigned stable diagnostic IDs, for example:

```text
LFB-CONN-PROTOCOL-TARGET
LFB-CONN-LOGIN
LFB-FML-SERVER-HELLO
LFB-FML-MODLIST
LFB-FML-REGISTRY-SYNC
LFB-FML-HANDSHAKE-ACK
LFB-PLAY-INVENTORY-SYNC
LFB-PLAY-DIMENSION-CHANGE
LFB-SESSION-RESTORE
LFB-SESSION-MODERN-ITEM-BLOCKED
```

A tier must not be marked complete while a critical connection failure is merely being ignored or suppressed.
