# rev269 Twilight Forest Part 2C — constructor-boxed DataWatcher defaults

Date: 2026-10-07

## Exact corpus finding

Among 77 registered Twilight Forest entities:

- 25 source classes override entityInit / func_70088_a;
- 21 of those add custom DataWatcher entries directly;
- 4 override without adding a watcher entry;
- no registered source entity uses DataWatcher.addObjectByDataType.

Most custom defaults are Byte.valueOf(0). A smaller set uses Integer/String values.

Two source defaults use the old allocation form `new Integer(int)` rather than
`Integer.valueOf(int)`:

- Hydra index 18: `new Integer(MAX_HEALTH)`, where MAX_HEALTH is source-proven as 360 in <clinit>;
- Lich index 19: `new Integer(100)`.

The existing generic analyzer supported static valueOf wrappers but not constructor-boxed primitive
wrappers, causing these otherwise closed schemas to fail.

## Generic fix

`LegacyEntityDataWatcherAnalyzer` now admits constructor-boxed Byte, Short, Integer and Float
defaults only when:

- the allocation type is exactly one of those four legacy DataWatcher-supported wrapper types;
- a unique matching one-primitive constructor call is proven to consume that exact NEW allocation;
- the constructor scalar argument is source-proven by the existing bounded scalar evaluator.

No arbitrary object constructor is admitted.

The same source-super traversal remains in force. In the Twilight Forest corpus:

- EntityTFBird, EntityTFTowerGhast and EntityTFHostileWolf do not add source watcher definitions;
- EntityTFHydraPart adds source index 17 as an empty string;
- EntityTFHydraHead therefore inherits that source index 17 and adds its own 18/19 entries.

A synthetic regression reproduces the Hydra-style new Integer(GETSTATIC) shape with a source
<clinit> value of 360.
