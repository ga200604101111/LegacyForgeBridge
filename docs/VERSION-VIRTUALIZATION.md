# Version Virtualization / Legacy Session Profile

A Minecraft 1.21.11 client contains registries, mechanics, UI surfaces, and protocol concepts that do not exist in Minecraft 1.7.10. LegacyForgeBridge must not globally downgrade or unregister modern content. Instead, it activates a reversible **Legacy Session Profile** while connected to a 1.7.10 server.

## Core rule

Modern-only content remains registered locally but is treated as unavailable to the legacy server.

During a legacy session it may be:

- hidden from legacy-facing UI;
- filtered from suggestions/search surfaces controlled by the bridge;
- rejected by outbound packet guards;
- translated to a legacy equivalent where a safe mapping exists;
- marked unsupported when no safe translation exists.

Disconnecting must restore normal 1.21.11 behavior without restarting the client.

## Session lifecycle

```text
NORMAL_1_21_11
  -> LEGACY_NEGOTIATING
  -> LEGACY_ACTIVE
  -> RESTORING
  -> NORMAL_1_21_11
```

No legacy state may survive RESTORING.

## Capability table

All bridge subsystems must use one session-scoped capability model, conceptually containing:

```text
targetMinecraftVersion
forgeVersion
protocolVersion
fmlProtocolVersion
serverMods
serverRegistrySnapshot
supportedBlocks
supportedItems
supportedEntities
supportedEffects
supportedEnchantments
supportedChannels
supportedInteractions
```

Do not maintain separate hard-coded support lists in UI, packet, and conversion code.

## Modern-only blocks/items

A 1.21.11 block or item that has no 1.7.10 meaning must not be sent to the server.

Required behavior:

1. Keep the modern registry entry intact locally.
2. Hide it from bridge-controlled legacy selection/search surfaces.
3. Prevent pick-block or equivalent actions from creating a server-bound unsupported entry.
4. Reject outbound inventory/block interaction that references unsupported content.
5. Restore full visibility after leaving the legacy session.

This is **session-scoped masking**, not registry deletion.

## Modern mechanics

Each feature absent from 1.7.10 receives an explicit policy:

```text
ALLOW_LOCAL_ONLY
TRANSLATE
EMULATE
HIDE
BLOCK_OUTBOUND
REPLACE_WITH_LEGACY_EQUIVALENT
UNSUPPORTED
```

Examples requiring policy decisions include offhand behavior, modern combat timing, shields, swimming/crawling-era poses, recipe-book UI assumptions, modern inventory/data-component semantics, modern attributes/effects/enchantments, and newer interaction packets.

## Vanilla mapping

For content existing in both versions:

```text
1.7.10 legacy identity / metadata
  -> semantic vanilla identity
  -> 1.21.11 registry identity / state
```

Mapping must be semantic rather than numeric-ID matching.

## Modded 1.7.10 content

Preferred path:

```text
legacy server registry entry
  -> FML mod/registry identity
  -> LegacyForgeBridge conversion manifest
  -> pre-registered converted 1.21.11 entry
```

If required server-side client content is unknown or unconverted, default policy is strict failure with a compatibility report. Placeholder rendering may be added later as a diagnostic mode, but must not be presented as full gameplay compatibility.

## Visibility surfaces

The bridge must audit more than creative inventory.

| Surface | Legacy policy |
|---|---|
| Creative / bridge selection UI | hide unsupported entries |
| Pick block / item selection | reject unsupported result |
| Recipe/search UI | hide unsupported bridge-controlled entries |
| Command suggestions | filter unsupported bridge-provided identifiers |
| Outbound inventory interaction | validate before encode |
| Outbound block interaction | validate before encode |
| Entity interaction/spawn request | validate capability |
| Tooltips | may remain local; must not imply server support |
| Assets/resource packs | may remain loaded |

Third-party UI mods may not be safely rewriteable. LegacyForgeBridge should therefore expose a visibility/capability API for integrations.

## Packet guard

Final safety path:

```text
modern client action
  -> semantic action
  -> LegacyCapabilityTable check
  -> mapping / translation
  -> 1.7.10-compatible packet
```

If validation fails, cancel the outbound action locally and emit a diagnostic rule ID. Never rely on the legacy server to reject modern-only identifiers.

## Non-goal

LegacyForgeBridge does not attempt to make the entire 1.21.11 feature set usable on a 1.7.10 server. It preserves modern client stability while exposing only capabilities that can be represented safely to the target legacy session.
