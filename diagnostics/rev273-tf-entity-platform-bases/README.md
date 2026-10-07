# rev273 Twilight Forest Part 2E-1 — external vanilla entity base census

Date: 2026-10-07

## Scope

This checkpoint inventories the first class outside the Twilight Forest source JAR for all 77
source-proven mod-entity registrations.

It does not yet add the transitive vanilla DataWatcher schema or change FML spawn admission.

## Exact corpus result

Registered entities: 77.

First external vanilla base distribution:

- net/minecraft/entity/monster/EntityMob: 32
- net/minecraft/entity/projectile/EntityThrowable: 14
- net/minecraft/entity/monster/EntitySpider: 4
- net/minecraft/entity/passive/EntityAnimal: 4
- net/minecraft/entity/passive/EntityWolf: 3
- net/minecraft/entity/monster/EntityGhast: 3
- net/minecraft/entity/Entity: 3
- net/minecraft/entity/EntityCreature: 2
- net/minecraft/entity/EntityFlying: 2
- net/minecraft/entity/EntityLiving: 2
- net/minecraft/entity/passive/EntityPig: 1
- net/minecraft/entity/passive/EntitySheep: 1
- net/minecraft/entity/passive/EntityCow: 1
- net/minecraft/entity/monster/EntitySlime: 1
- net/minecraft/entity/monster/EntityZombie: 1
- net/minecraft/entity/passive/EntityAmbientCreature: 1
- net/minecraft/entity/passive/EntityTameable: 1
- net/minecraft/entity/projectile/EntityArrow: 1

No other first external vanilla base appears in this corpus.

## Why first-external-base is not sufficient by itself

FML spawn metadata contains inherited DataWatcher state. A source class whose first external base is
EntityWolf may also inherit watcher definitions from EntityTameable, EntityAnimal, EntityAgeable,
EntityLivingBase and Entity.

Therefore Part 2E-2 must build a pinned **transitive** vanilla Minecraft 1.7.10 watcher schema for
these 18 families. It must not merely attach the watcher declarations owned by the first external
class.

## Existing runtime boundary

The current plain FML entity spawn path:

- specially accepts only legacy Entity base watcher 0/1 when the generated bridge does not consume
  them;
- requires every other incoming watcher to be consumed by the generated
  LegacyPlainEntityWatcherBridge;
- captures raw legacy watcher updates before ViaLegacy removes unknown indices above the base range.

Therefore Part 2D's complete source-owned DataWatcher access proof is not equivalent to complete
initial FML spawn metadata support for living/mob/animal/projectile subclasses.

## Local audit

The exact ancestry listing is retained locally as:

`/mnt/data/tf_external_bases.txt`

## Next

Part 2E-2:

1. verify the exact vanilla Minecraft 1.7.10 watcher definitions for the 18 observed base families
   and their ancestors;
2. derive a transitive platform schema per family;
3. detect duplicate index/type conflicts before touching production runtime;
4. only then merge platform entries into generated watcher schemas and re-evaluate initial FML spawn
   envelopes.
