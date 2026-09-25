# 2026-09-19 — Processor source cohort retirement

Converter revision: `2026-09-19.132`.

## Scope

This slice wires the final, proof-gated source-class retirement transaction for a
runtime-complete single-input processor. It does not broaden processor discovery or accept a
new source shape. It consumes the existing retirement-readiness sidecar after all modern
runtime, registration, construction, allocation and GUI branch gates have closed.

The retirement cohort is reconstructed rather than trusted as an opaque list. It contains:

- the source Block and TileEntity classes;
- the source Container and GUI classes proven by the retired handler branches;
- every current nested companion class whose internal name begins with any cohort base class
  plus `$`.

The shared GUI handler is intentionally excluded. It may continue serving unrelated GUI IDs.

## Transactional retirement

`LegacySingleInputProcessorRetirementPass` requires a valid readiness sidecar for the current
source hash and independently rechecks every readiness gate. It then verifies that the declared
presentation identities still match the proven Container/GUI identities and rediscovers the
nested companion set from the live staging tree. Any drift blocks retirement before mutation.

For an admitted cohort the pass performs a fresh candidate reference scan. External incoming
class references, resource references, unreadable classes or an incomplete bounded resource scan
all block deletion. Internal references among cohort members are allowed because the cohort is
retired atomically.

All class bytes are saved before mutation. The pass deletes the complete cohort, runs a second
fresh reference scan against the staged candidate, and restores every original byte when deletion
or post-delete proof fails. Partial retirement is therefore never an accepted state.

The resulting evidence is written to
`legacyforgebridge/single-input-processor-retirement.json`, including the final cohort, fresh
pre-delete evidence, post-delete diagnostics, deletion counts, restoration state and blockers.

## Pipeline integration

The processor retirement pass now runs immediately after
`LegacySingleInputProcessorRetirementReadiness` inside the final class-dependency stage. This
keeps deletion behind generated semantic code, registration stripping, constructor/allocation
replacement, GUI branch stripping and the final candidate dependency inventory.

## Regression coverage

`LegacySingleInputProcessorRetirementPassTest` adds normal generic regressions for:

1. atomic deletion of Block, TileEntity, Container, GUI and nested companion classes while an
   unrelated bootstrap class remains;
2. complete preservation when a new nested companion appears after readiness materialization;
3. complete preservation when a fresh scan finds an external incoming reference to the Block.

`BambooExactProcessorRetirementTest` is an `exact-corpus` guard. For the pinned Bamboo source it
requires retirement completion to equal readiness: an admitted cohort must be entirely absent
from the output JAR, while a blocked cohort must remain entirely present. The shared GUI handler
must remain in both cases. Standard CI compiles this guard but does not execute it without the
external exact Bamboo JAR.

## Boundaries

This commit does not add static-field Block allocation support, relax the inline allocation
proof, broaden runtime admission, or claim live-game acceptance. It only authorizes deletion when
all previously independent processor gates and both fresh reference-closure checks succeed.
