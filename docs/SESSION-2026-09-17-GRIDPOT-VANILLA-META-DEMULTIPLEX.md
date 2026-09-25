# 2026-09-17 — GridPot vanilla metadata demultiplexing

## Scope

This slice follows converter revision `2026-09-17.93`, where GridPot/MultiPot gained a first bounded vanilla 1.7.10 insertion set containing only metadata-independent identities.

Converter revision: `2026-09-17.94`.

## Platform mapping

The positive legacy insertion predicate is still restricted to render types `1`, `13`, `40` plus previously proven symbolic mod render identities. This checkpoint expands only vanilla identities whose 1.7 item metadata can be explicitly demultiplexed into modern block identities.

The platform table now retains exact legacy metadata provenance for:

- `sapling` meta `0..5` -> oak/spruce/birch/jungle/acacia/dark-oak saplings;
- `tallgrass` meta `0..2` -> dead bush / short grass / fern;
- `red_flower` meta `0..8` -> poppy, blue orchid, allium, azure bluet, four tulips, oxeye daisy;
- `double_plant` meta `0..5` -> sunflower, lilac, tall grass, large fern, rose bush, peony.

The one-to-one table also contains brown/red mushroom, dandelion, dead bush, and cactus.

For the proven render-type set `{1,13,40}`, this produces 28 unique modern vanilla block identities. The runtime still receives only an explicit modern-id set; legacy metadata is preserved in the conversion table for auditability and regression.

## Safety boundary

This is not a generic "all plants are eligible" rule. A modern block id is admitted only when it appears as a value in the fixed Minecraft 1.7.10 platform mapping above. Blocks outside the table remain fail-closed even if they are visually plant-like.

No GridPot mutation logic changes in this slice. The `.91` positive replacement/drop/store/consume runtime remains authoritative.

## Regression

Normal CI pins:

- exact sapling metadata 0..5 mapping;
- `red_flower` meta 3 -> `minecraft:azure_bluet`;
- `double_plant` meta 2/3 -> `minecraft:tall_grass` / `minecraft:large_fern`;
- tallgrass meta 0/1/2 -> dead bush / short grass / fern;
- render type 1 expands to exactly 21 unique ids;
- render type 13 expands only to cactus;
- render type 40 expands to exactly six double-plant ids;
- combined `{1,13,40}` expands to exactly 28 unique ids and does not admit unrelated blocks such as `minecraft:oak_log`.

## Remaining GridPot work

The negative insertion branch and inserted-content presentation remain intentionally incomplete. Whole Bamboo conversion remains `PARTIAL`.
