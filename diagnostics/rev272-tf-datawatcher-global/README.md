# rev272 Twilight Forest Part 2D — platform-owned DataWatcher getter closure

Date: 2026-10-07

## Exact corpus state before this revision

After rev271:

- registered entity access surfaces: 77/77 proven;
- source runtime DataWatcher calls: 94;
- calls accounted by admitted entity access proofs: 92;
- one unresolved source method remained:
  `TFClientEvents.renderLivingPost(RenderLivingEvent.Post)`.

That method performs exactly two reads:

- vanilla EntityLivingBase watcher 7 as int;
- vanilla EntityLivingBase watcher 8 as byte.

These are not Twilight Forest-owned watcher definitions.

## Pinned vanilla 1.7.10 schema

The platform watcher table now also pins the vanilla EntityLivingBase definitions:

- 6 -> float, default 1.0
- 7 -> int, default 0
- 8 -> byte, default 0
- 9 -> byte, default 0

The existing Ghast 16 -> byte 0 entry remains.

## Global-closure proof

`LegacyEntityDataWatcherGlobalClosureAnalyzer` may account a direct platform getter outside a
registered source entity lineage only when all of the following are proven from bytecode:

1. the DataWatcher operation is one of the already-supported typed primitive/string getters;
2. the watcher index is one direct integer constant;
3. the DataWatcher receiver is produced by a direct non-static
   `getDataWatcher / func_70096_w` invocation;
4. that invocation owner is a vanilla `net/minecraft/entity/...` class;
5. the pinned 1.7.10 platform table contains the exact owner/index/value-kind tuple.

Dynamic indices, mismatched getter kinds, arbitrary GETFIELD watcher receivers and mod-owned
receiver classes do not pass this platform shortcut.

A synthetic regression proves EntityLivingBase 7/int and 8/byte close successfully and that a
7/byte mismatch remains unresolved.

## Expected Twilight Forest result

The exact corpus should now reach:

- 77/77 entity DataWatcher access surfaces proven;
- 94/94 source runtime DataWatcher calls accounted;
- source-wide DataWatcher call closure complete.

This closes Part 2D's source access/closure proof. It does not by itself prove all vanilla
base-class watcher metadata required by FML spawn for every living entity family. That broader
remote-spawn platform metadata inventory is the next distinct compatibility slice.
