# rev226 — iY projectile motion recovery + Damascus Steel final face model

Target branch: `feature/generic-conversion-iyamato-corpus3`.

rev226 is an audited binary overlay on rev225/corpus4-local.9. The cumulative projectile runtime classes used by the user's binary are not represented by the root source tree, so projectile repair is recorded here as binary-overlay evidence rather than falsely claiming the root source is the complete cumulative build base.

## Live evidence

The user's rev224 log loads 19 source projectile runtime rules but registers 24 iY remote projectile renderers. The five presentation identities without a source-runtime program are:

- 4804 ExtendedReach (Halberd/Spear invisible carrier)
- 4805 MagicBullet (Kris Knife)
- 4818 Bullet
- 4819 ShotShell
- 4823 ContractDocuments

Current cumulative `SourceProjectileRuntime.tick` returns immediately when `program(rule.id()) == null`. Therefore those five converted entities can spawn/render but never receive the cumulative visual-motion integration.

The same log also proves two distinct FML spawn-envelope shapes:
- FML-IThrowableEntity projectiles such as Javelin and MagicBullet carry thrower id + exact velocity;
- vanilla EntityThrowable-derived iY daggers and ContractDocuments arrive with `throwerId=0 velocity=0,0,0`.

That is the exact reason daggers with an otherwise-valid source runtime fall straight down: the existing ThrowableVisualStep starts from zero horizontal velocity and immediately applies vanilla EntityThrowable gravity.

## Projectile repair

rev226 keeps server damage/impact authoritative and changes only client presentation.

### Initial velocity reconstruction

Only when the FML throwable envelope is absent and current delta movement is still zero:

- 4800-4803 dagger family: reconstruct vector from FML spawn yaw/pitch at speed 1.5, matching vanilla EntityThrowable source launch magnitude.
- 4823 ContractDocuments: same 1.5 launch magnitude.
- 4804 ExtendedReach: reconstruct at source speed 1.0.

Projectiles whose FML envelope already carries velocity are not modified.

### Missing five visual tick profiles

Only the five `program == null` iY presentations receive rev226 compatibility ticks:

- 4804 ExtendedReach: dry drag 1.0, gravity 0; water drag 0.8.
- 4805 MagicBullet: dry drag 0.99, gravity 0; water drag 0.8.
- 4818 Bullet: dry drag 0.99, gravity 0.04; water drag 0.8.
- 4819 ShotShell: dry drag 0.99, gravity 0.04; water drag 0.8.
- 4823 ContractDocuments: dry drag 0.99, gravity 0.03; water drag 0.8.

The existing 19 source-runtime projectiles continue through the original cumulative SourceProjectileRuntime and are not double-ticked.

## Damascus Steel Block

Exact iY source is a normal cube with:
- UP/DOWN: `iymts_mod:damascus_steel_block_top`
- four side faces: `iymts_mod:damascus_steel_block_side`

A generic source fix was committed at `89a80a583b71bd36ce11916bdb96c2ffb30376ef`: a source-proven external enum object now executes its stored `$ordinal` directly instead of requiring source-JAR hierarchy discovery. This closes the ForgeDirection.UP/DOWN selector gap in the root analyzer.

Because the user's cumulative converter differs from the root source history, rev226 also carries an exact-corpus SHA-gated finalizer after block geometry and before texture-atlas finalization. For the iY source SHA only, it finds the converted BlockIYDamascusSteel identity, copies the exact source top/side PNGs into modern block texture paths, and writes the six-face block model + all 16 legacy_meta blockstates. This prevents later generic geometry ownership from leaving the unresolved ceramic placeholder.

## Cache invalidation

Binary converter revision:

`2026-09-30.226-projectile-motion-damascus-face`

This intentionally forces the user's cached iY candidate to rebuild. rev225's `.224` converted candidate must not be reused for the Damascus model repair.

## Output

- file: `legacyforgebridge-0.2.0-alpha.27-rev226-corpus4-local.10.jar`
- bytes: `4,414,745`
- SHA-256: `f7828045f5b7598e822fd91166c74c1c7d849e35583e086f74e6f14a4b37f3be`
- internal version: `0.2.0-alpha.27-corpus4-local.10-rev226-local-test.1`
- cache compatibility constant preserved: `0.2.0-alpha.27-corpus3-local.3-rev220-local-test.1`

## Binary audit vs rev225

- base entries: 1664
- output entries: 1665
- removed entries: 0
- duplicate entries: 0
- added entry: `dev/yinghuang/legacyforgebridge/convert/Rev226Compat.class`
- changed existing entries:
  - `BuildInfo.class`
  - `SourceProjectileRuntime.class`
  - `FmlRuntimeClient.class`
  - `LegacySimpleBlockPresentationPass.class`
  - `fabric.mod.json`
- nested `META-INF/jars/energy-4.2.0.jar`: byte-for-byte unchanged
- ZIP integrity: pass
- ASM BasicVerifier: pass for every method of every changed/new class and ConvertedLegacyRemoteProjectile.

No Windows/Prism/Minecraft live launch was available inside the build environment. No Actions dispatch, PR, tag, release, workflow modification, or main/Bamboo branch modification was performed.
