# rev275 Twilight Forest Part 2E-3 — platform/source watcher merge and unpinned-base gate

Date: 2026-10-07

## Exact corpus merge audit

The 77 source-proven Twilight Forest registrations were reconstructed directly from the uploaded
JAR and joined to their first external vanilla base plus the Part 2C source-owned watcher schemas.

Result:

- registrations: 77
- unique registered classes: 77
- transitive platform watcher entry instances (excluding Entity 0/1): 396
- source-owned watcher entry instances across registered schemas: 37
- platform/source duplicate-index conflicts: 0
- total non-0/1 watcher bridge entry instances: 433

Local audit artifact:

`/mnt/data/tf_part2e3_audit.json`

## Runtime-family audit

- Plain generated entities consume the complete DataWatcher schema emitted by
  LegacyEntityDataWatcherAnalyzer.
- LegacyVisibleEntityPresentationAnalyzer also reads the same analyzer schema and converts every
  byte/short/int/float/string entry into its watcherTypes map; rev274 platform entries therefore
  flow into visible carriers without a second table.
- EntityThrowable has no additional 1.7.10 watcher beyond Entity 0/1.
- EntityArrow adds only 16/byte, which is already accepted by the dedicated projectile base-watcher
  path.
- The specialized rotating-assembly carrier is a separate family and is not part of the 77
  Twilight Forest registration census addressed by this platform-base audit.

## Generic safety hardening

An unknown first external Entity base can contain inherited watcher definitions that are invisible
to the source mod JAR. Treating an unknown base as an empty platform schema would therefore be an
unsafe false positive.

LegacyEntityDataWatcherAnalyzer now requires the exact external base to be present in the pinned
LegacyVanillaEntityDataWatcher1710 family set. Unpinned vanilla classes (for example
EntityFireball) and external dependency bases remain fail closed until their exact 1.7.10 watcher
schema is added and verified.

Pinned-table internal index/type/default conflicts now throw instead of degrading to an empty
schema.

## Boundary

This revision proves that the 18 exact Twilight Forest base families can be represented without
platform/source watcher conflicts. It does not yet claim that every one of the 77 entities has a
fully admitted modern gameplay/runtime family; behavior, construction, rendering and additional
spawn data gates remain separate.

The next Part 2E slice should test the FML initial-spawn watcher acceptance contract itself and
persist a machine-readable platform/source provenance marker into the definition/runtime sidecars so
later runtime families do not accidentally drop these inherited entries.
