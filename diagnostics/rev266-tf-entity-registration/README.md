# rev266 Twilight Forest Part 2A — lifecycle-reachable static entity ids

Date: 2026-10-07

## Corpus correction

A direct bytecode audit of Twilight Forest 2.3.8 corrects an earlier working assumption:

- the 58 creature `idMob*` values are not read from Forge Configuration;
- `loadConfiguration(Configuration)` assigns them literal integer values (177..237 with intentional gaps);
- the 19 `idVehicleSpawn*` fields are assigned literal values in `<clinit>`;
- `creatureCompatibility` is configuration-controlled, but it only gates the extra
  `registerGlobalEntityID` call in `TFCreatures`; `registerModEntity` remains unconditional.

Therefore all 77 mod-entity numeric ids are source-provable without guessing server configuration.

## Generic fix

`LegacyLifecycleAnalyzer` now resolves a source `GETSTATIC` through reachable `PUTSTATIC`
assignments only when:

1. the assignment method is reachable from an FML lifecycle root or `<clinit>`;
2. every reachable assignment to that exact static field is analyzable;
3. every analyzable assignment resolves to the same concrete value.

If any reachable assignment is dynamic (for example
`Configuration.get(...).getInt()`), parameter-derived, unresolved, or conflicts with another
assignment, the analyzer preserves the original `FieldValue` and downstream entity admission
continues to fail closed.

This behavior is namespace/class/field-name independent.

## Expected Twilight Forest effect

The existing lifecycle helper propagation already follows:

`TwilightForestMod.load -> registerCreatures -> TFCreatures.registerTFCreature -> EntityRegistry.registerModEntity`.

With this static-field proof the source-proven entity registration tuple can now retain:

- entity class;
- entity name;
- exact numeric mod entity id;
- owner object provenance;
- tracking range;
- update frequency;
- velocity-update flag.

Spawn-egg colors and optional `registerGlobalEntityID` behavior are not claimed by this slice.

Part 2A remains registration identity only; DataWatcher, AI, rendering, spawn rules, TileEntity,
dimension and world generation are separate checkpoints.
