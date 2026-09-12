# Version Virtualization / Legacy Session Profile

A Minecraft 1.21.11 client contains content and mechanics that do not exist in Minecraft 1.7.10. LegacyForgeBridge must not globally downgrade or unregister modern registries. Instead, it activates a reversible **Legacy Session Profile** while connected to a 1.7.10 server.

## Core rule

Keep the modern client experience intact. Do **not** turn the whole client into a 1.7.10-style UI.

The default user-visible restriction is limited to **items**, including `BlockItem` instances for modern-only blocks. Other modern interfaces may remain visible unless a specific protocol incompatibility proves that a targeted guard is required.

Disconnecting must restore normal 1.21.11 behavior without restarting the client.

## Session lifecycle

```text
NORMAL_1_21_11
  -> LEGACY_NEGOTIATING
  -> LEGACY_ACTIVE
  -> RESTORING
  -> NORMAL_1_21_11
```

No legacy state may survive `RESTORING`.

## Item compatibility boundary

A modern-only item that has no safe 1.7.10 representation must not enter a server-bound legacy item stack.

This includes modern-only block items. The underlying modern `Block` and `Item` registry entries stay registered locally; LegacyForgeBridge does not unregister them from Minecraft's global registries.

Required behavior during a legacy session:

1. Keep all modern registry entries intact.
2. Maintain a session-local set/map of items that have a valid 1.7.10 representation.
3. Prevent unsupported modern items or block items from being encoded into legacy inventory/action packets.
4. Translate supported items through a dedicated legacy ID/metadata mapping rather than using modern raw registry IDs.
5. Restore unrestricted modern item behavior after disconnecting.

Conceptually:

```text
1.21.11 ItemStack
      |
      v
Legacy Item Compatibility Map
      |
      +-- supported -> legacy item ID / metadata / NBT -> 1.7.10 server
      |
      +-- unsupported -> cancel server-bound action + diagnostic
```

This is **session-scoped item compatibility**, not registry deletion.

## UI policy

Modern UI should remain modern by default.

LegacyForgeBridge should **not** globally disable or replace:

- HUD;
- recipe book;
- advancements UI;
- modern inventory screens;
- modern settings/screens;
- tooltips;
- rendering improvements;
- other client-only quality-of-life interfaces.

If an interface can cause an unsupported item to be sent to the server, guard the resulting **item action**, not the entire interface.

Examples:

- A modern recipe-book screen may remain visible; an unsupported resulting item must not be sent as a legacy stack.
- A modern creative screen may remain structurally unchanged; unsupported item acquisition/use must be blocked or filtered at the item boundary where necessary.
- Command UI/autocomplete does not need blanket replacement. Server-side command behavior remains authoritative; bridge-specific filtering is added only if a concrete incompatibility is observed.

## `/give` and commands

Do not reimplement all command interfaces just to look like 1.7.10.

For a remote 1.7.10 server, the server remains authoritative for `/give` and other server commands. LegacyForgeBridge only needs additional command filtering when the modern client itself would otherwise create or transmit a modern-only item identity that cannot be represented by the legacy protocol.

## Legacy item identity and numeric IDs

Never equate a modern raw registry ID with a 1.7.10 numeric item ID.

Example of what must **not** happen:

```text
1.21.11 raw item id 236
        !=
1.7.10 Forge item id 236
```

Instead use a session-local translation table:

```text
modern Identifier / converted mod identity
        <-> semantic legacy identity
        <-> Forge 1.7.10 numeric ID + metadata
```

The 1.7.10 side of this table must be populated from known vanilla mappings plus FML/Forge registry synchronization for modded content.

## Blocks

A modern-only block does not need to be globally hidden or unregistered merely because 1.7.10 lacks it.

The important boundary is normally its **item representation** (`BlockItem`) and any server-bound block interaction. A 1.7.10 server cannot legitimately send a modern-only block state to the client through the legacy protocol, so modern-only blocks that exist only in the local 1.21.11 registry may remain untouched.

If a future edge case exposes a modern-only block through a server-bound action, add a targeted translation/guard for that action rather than removing the block globally.

## Other modern mechanics

Modern mechanics are handled only where protocol or gameplay semantics require translation. They are not automatically hidden from the user.

Available internal policies may include:

```text
ALLOW_LOCAL_ONLY
TRANSLATE
EMULATE
BLOCK_OUTBOUND
REPLACE_WITH_LEGACY_EQUIVALENT
UNSUPPORTED
```

Use the least invasive policy that preserves correct 1.7.10 server behavior.

## Modded 1.7.10 items

Preferred path:

```text
legacy server registry entry
  -> FML mod/registry identity
  -> LegacyForgeBridge conversion manifest
  -> converted 1.21.11 Item
  -> session-local legacy ID / metadata mapping
```

If a required modded item is unknown or unconverted, fail that item mapping explicitly and record a compatibility diagnostic. Do not guess a numeric ID or silently substitute an unrelated modern item.

## Packet guard

The final safety boundary for items is:

```text
modern item action
  -> ItemStack compatibility check
  -> semantic mapping
  -> legacy ID / metadata / NBT encoding
  -> 1.7.10-compatible packet
```

If the item has no safe legacy representation, cancel only that server-bound item action and emit a diagnostic rule ID.

## Non-goals

LegacyForgeBridge does not attempt to visually recreate the complete Minecraft 1.7.10 client. Modern rendering, screens, HUD behavior and client-side quality-of-life features should remain available unless a specific compatibility bug requires a narrowly targeted adaptation.
