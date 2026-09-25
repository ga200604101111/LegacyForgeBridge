# 2026-09-18 — Processor TileEntity registration retirement

## Scope

This slice follows converter revision 2026-09-18.123. The variant-snowball source cohort now supports complete runtime replacement and proof-gated retirement including compiler-generated nested companions.

Converter revision: 2026-09-18.124.

The next generic family is the three-slot single-input processor represented by the Bamboo MillStone corpus. Gameplay, inventory/NBT, menu, sided transfer, source presentation, particles and legacy energy ingress are already independently proof-gated and can reach runtimeComplete=true.

This slice begins processor source retirement conservatively by neutralizing only the legacy TileEntity registration.

## Exact registerTileEntity stripper

LegacyTileEntityRegistrationStripper accepts only the standard Forge 1.7.x shape:

- concrete TileEntity Class literal;
- concrete legacy tile id String;
- GameRegistry.registerTileEntity(Class, String).

The two argument producers must form the proven contiguous input to the call. Helper/computed IDs or other producer shapes are left untouched.

The stripper removes the proven producer/call interval and reparses the rewritten class. A residual matching TileEntity class literal or registerTileEntity call in the proven source method fails closed and returns the original bytes.

## Runtime-complete processor gate

LegacySingleInputProcessorTileRegistrationStripPass runs only after LegacySingleInputProcessorPresentationPass.

A processor registration is eligible only when its sidecar already states:

- baseRuntimeComplete=true;
- sourcePresentationComplete=true;
- runtimeComplete=true.

The pass rejoins each machine to LegacyLifecycleAnalyzer using the source-proven:

- sourceTileClass;
- legacyTileId.

Exactly one matching TILE_ENTITY registration is required. Its direct source owner/method/descriptor is then passed to the exact stripper.

The output sidecar is:

legacyforgebridge/single-input-processor-tile-registration-strip.json

It records tileRegistrationStripWired=true, per-machine source provenance, stripped site counts and blockers. Source class deletion remains false.

## Pipeline ordering

LegacyConversionEngine runs the pass immediately after LegacySingleInputProcessorPresentationPass. This prevents a gameplay-only or presentation-incomplete processor from retiring its legacy registration early.

## Regression

Normal CI includes:

- direct stripper success for a pure Class/String registration;
- computed/static-field tile id rejection with byte-for-byte preservation;
- pass-level runtimeComplete gating and lifecycle provenance join;
- candidate reference proof that the registration owner no longer references the source TileEntity after a successful strip.

The exact Bamboo processor test now also reads the generated strip sidecar and resolves the rule by the sourceTileClass emitted by the processor runtime. No Bamboo TileEntity class name is hard-coded into production or the exact lookup.

## Next boundary

After this slice is green, processor retirement still requires independent gates for:

1. source GameRegistry.registerBlock / block allocation retirement;
2. source GUI handler registration retirement or proof that the shared handler remains required;
3. source Block + TileEntity constructor/replacement evidence;
4. candidate reference closure for the minimal processor-owned source cohort;
5. fresh pre-delete/post-delete scans with atomic byte restoration.

No processor source class is deleted by revision .124.
