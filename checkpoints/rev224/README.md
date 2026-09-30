# rev224 — corpus4 ABI repair + Bamboo 狐火 block-state carrier repair

Target branch: `feature/generic-conversion-iyamato-corpus3`.

This checkpoint supersedes the installable rev223 corpus4-local.7 overlay.

## User live failure

The rev223-local.7 launch failed before normal game initialization with:

`NoSuchFieldError: BuildInfo.CACHE_COMPATIBILITY_VERSION`

at `ConversionCacheIdentity.current`.

The exact cumulative corpus4-local.6 base contains four BuildInfo fields, while the generic root source BuildInfo currently contains only VERSION / CONVERSION_SCHEMA / CONVERTER_REVISION. Replacing the whole cumulative binary BuildInfo with the generic source-shaped class therefore broke the cumulative binary ABI.

rev224 restores the exact base compatibility constant while retaining the new version and converter revision.

## Bamboo 狐火

Exact Bamboo source evidence identifies `BambooMod:kitunebi` as a client held-item visibility block. The user's legacy registry assigns it numeric block id 176.

The cumulative corpus4-local.6 binary `ViaLegacyModBlockBoundaryMixin` called:

`Protocol1_12_2To1_13.MAPPINGS.getBlockStateMappings()`

For ViaVersion's `MappingData1_13`, that accessor is null by design: the special 1.12 -> 1.13 mapping implementation exposes the old block-state-id mapping through `getBlockMappings()`.

As a result, the LegacyForgeBridge carrier entry hook returned early, allowing mod block state 176 to continue through Via's ordinary vanilla mapping path. That matches the observed foxfire -> white standing-banner transition after a server block update.

rev224 patches the cumulative binary mixin to call:

`Protocol1_12_2To1_13.MAPPINGS.getBlockMappings()`

before `LegacyModBlockStateBridge.enterLegacyState(...)`, matching the current branch source.

## Output

- file: `legacyforgebridge-0.2.0-alpha.27-rev224-corpus4-local.8.jar`
- bytes: `4,436,528`
- SHA-256: `36a9bdd89103f83d4f02e61d1d627ab51254c62a1b423dc20fa98a5868c74a51`
- internal version: `0.2.0-alpha.27-corpus4-local.8-rev224-local-test.1`
- converter revision: `2026-09-30.224-flat-item-projectile-blocktexture-blockcarry`
- cache compatibility version: `0.2.0-alpha.27-corpus3-local.3-rev220-local-test.1`

## Binary audit

Compared with exact corpus4-local.6 base:
- base entries: 1663
- output entries: 1664
- removed entries: 0
- added entries: one (`Rev223Compat.class`)
- changed existing entries: eight expected entries
- duplicate entries: 0
- nested `META-INF/jars/energy-4.2.0.jar`: byte-for-byte unchanged
- ZIP integrity: pass

Compared with rev223-local.7, rev224 changes only:
- `BuildInfo.class`
- `ViaLegacyModBlockBoundaryMixin.class`
- `fabric.mod.json`

ASM BasicVerifier passed every method in all rev223 changed classes plus the newly patched Via boundary mixin.

No clean cumulative Loom rebuild was claimed; this remains a binary overlay on the exact user-supplied corpus4-local.6 main JAR. No Windows/Prism/Minecraft live launch was available in the build environment.

No Actions dispatch, PR, tag, release, workflow modification, or main/Bamboo branch modification was performed.
