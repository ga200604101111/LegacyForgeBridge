# 2026-09-19 — Raw FML handshake and metadata-preserving presentation

Converter revision: `2026-09-19.136`.

## Failure reproduced from live trace

A modern client received the Forge 1.7.10 `ServerHello`, scheduled `ClientHello` and a `ModList`
containing the exact converted mod identities, then disconnected while still waiting for the server
mod list. The server UI reported the converted mods as missing. This proves that constructing the
logical ModList was not sufficient: the synthetic modern custom payload could be scheduled through
ViaVersion without being accepted by the legacy server as its expected C17 `FML|HS` packet.

## Native legacy transport

`ViaLegacyFmlTransport` now prefers an exact Minecraft 1.7.10 serverbound CustomPayload packet:

- packet id `0x17`;
- legacy channel encoded as a 1.7 string;
- signed-short payload length;
- unchanged payload bytes.

Channel registration is now sent as `REGISTER` with the literal payload
`FML|HS\0FML\0FORGE`. Handshake traffic is sent on literal `FML|HS`. These packets bypass the
modern plugin-channel alias rewrite and are written directly to the active legacy server
connection. The previous mapped 1.13 path remains only as a fail-safe when raw packet construction
is unavailable.

The advertised client list now mirrors normal Forge 1.7.10 built-ins (`mcp`, `FML`, `Forge`) before
adding exact converted mod IDs and versions. Converted metadata cannot replace a built-in identity.

## Metadata-preserving block bridge

The FML numeric block map previously allocated one carrier token per block and discarded the lower
four metadata bits. It now allocates sixteen tokens per mapped block. The full legacy
`blockId << 4 | metadata` value therefore survives every ViaVersion mapping boundary. At the native
client boundary the metadata is restored to `ConvertedLegacyBlock.LEGACY_META` when the generated
block supports that property.

## Presentation baseline

The generic client resource baseline no longer presents unresolved content as stone or paper.
It now:

- scans source texture namespaces generically;
- accepts only unique, high-confidence texture matches;
- preserves every model emitted by a semantic presentation pass;
- uses explicit magenta/barrier unresolved markers for ambiguous content;
- creates or expands blockstate files to all sixteen `legacy_meta` variants;
- records texture matches and unresolved identities in its evidence sidecar.

No Bamboo name or class-specific rule is embedded in these mechanisms. Bamboo remains a corpus used
to measure coverage.

## Verification

New regressions cover:

1. exact Forge 1.7.10 registration names and packet id;
2. built-in FML/Forge identities plus converted mod identity advertisement;
3. sixteen disjoint metadata tokens per legacy block;
4. texture-backed generic item/block models;
5. creation and expansion of all sixteen metadata blockstate variants.

A local exact Bamboo staging simulation found 63 blocks and 42 independent items, generated one
105-entry inspection tab, and created or expanded metadata-complete blockstates for 62 of 63 blocks.
Existing specialized presentation files remained authoritative.
