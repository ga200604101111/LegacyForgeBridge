# rev274 Twilight Forest Part 2E-2 — transitive vanilla 1.7.10 watcher schemas

Date: 2026-10-07

## Scope

This revision expands the previously narrow vanilla watcher table into a transitive schema for the
18 first-external-base families observed across the 77 registered Twilight Forest entities.

Legacy Entity base watcher indices 0/1 are intentionally not duplicated into generated
SynchedEntityData accessors because the existing FML spawn bridge already accepts those platform
base fields separately.

## Source basis

The pinned 1.7.10 vanilla source definitions were checked against the Bukkit mc-dev 1.7.10 NMS
source. The MCP family mapping used by LegacyForgeBridge yields the following exact owners:

- EntityLivingBase: 6 float=1.0, 7 int=0, 8 byte=0, 9 byte=0
- EntityLiving: 10 string="", 11 byte=0
- EntityAgeable: 12 int=0
- EntityTameable: 16 byte=0, 17 string=""
- EntityWolf: 18 float=1.0, 19 byte=0, 20 byte=14
- EntityPig: 16 byte=0
- EntitySheep: 16 byte=0
- EntitySpider: 16 byte=0
- EntitySlime: 16 byte=1
- EntityGhast: 16 byte=0
- EntityZombie: 12 byte=0, 13 byte=0, 14 byte=0
- EntityArrow: 16 byte=0

EntityWolf index 18 is 1.0 because its constructor-time watcher initialization calls getHealth(),
which reads EntityLivingBase watcher 6 whose initial value is 1.0. Wolf collar index 20 is 14
because the 1.7.10 BlockCloth inverse-color helper maps 1 to 14.

The following intermediary families add no watcher entry of their own and inherit transitively:
EntityCreature, EntityMob, EntityFlying, EntityAmbientCreature, EntityAnimal, EntityCow and
EntityThrowable.

## Implementation

`LegacyVanillaEntityDataWatcher1710` now stores:

- one exact parent relation per pinned vanilla class;
- only the watcher entries declared by each exact class;
- a transitive merge for `inheritedForExternalBase`.

The merge rejects internal index/type/default conflicts instead of choosing one side.

`matchesPlatformAccess` now checks the same transitive schema, so direct vanilla getters owned by a
subclass may safely reference inherited platform watcher state.

## Exact 18-family schema sizes

- Entity: 0 bridge entries (0/1 remain handled by FML base logic)
- EntityThrowable: 0
- EntityArrow: 1
- EntityLiving / EntityCreature / EntityMob / EntityFlying / EntityAmbientCreature: 6
- EntityAnimal / EntityCow: 7
- EntitySpider / EntitySlime / EntityGhast: 7
- EntityPig / EntitySheep: 8
- EntityTameable: 9
- EntityZombie: 9
- EntityWolf: 12

No index/type conflicts exist in the 18 observed transitive families.

## Next

Part 2E-3 should rerun the exact Twilight Forest definition/access plan with these transitive
platform entries and then audit the actual FML initial watcher envelope acceptance.

The key acceptance criterion is not merely 77/77 source access proof. For every registered entity,
every non-Entity-base watcher that can appear in the initial 1.7.10 FML spawn envelope must have a
matching generated bridge entry with the correct legacy wire type.
