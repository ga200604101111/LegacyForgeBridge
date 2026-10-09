# LegacyForgeBridge rev307 — converted legacy mods' own configuration screens

## Status and authoritative source

Experimental local main-JAR overlay built from the **exact user-supplied rev306** artifact.
The `feature/generic-conversion-iyamato-corpus3` repository branch stores older cumulative
checkpoints; root `src/` is not the complete rev306 source. This delivery replaces only
`BuildInfo.class`, `LegacyForgeBridgeModMenu.class`, and `fabric.mod.json`, while adding
new self-contained source-backed configuration classes and three SHA-pinned resource profiles.
It does not change rev306 gameplay conversion or protocol movement semantics.

- Base: `legacyforgebridge-0.2.0-alpha.27-rev306-source-bow-presentation.jar`
- Base SHA-256: `3144da4956c06765b9798de699bdcb8f62f89ed07d9f219cdc452ff3ddf37df8`
- Main: `legacyforgebridge-0.2.0-alpha.27-rev307-legacy-mod-config-menu.jar`
- Main SHA-256: `84059ca567dba87d36a0b25f8c2b09c5955c8bfc67b1bd033b5718e27dc7e13e`
- Main bytes: `5,194,527`
- BuildInfo.CONVERTER_REVISION retains `2026-10-09.306-source-bow-presentation-proof-alpha`.
- BuildInfo.CACHE_COMPATIBILITY_VERSION retains `0.2.0-alpha.27-corpus4-local.17-rev233-cache.1`.

## Intended user flow

Keep the original Forge 1.7.10 JARs unchanged in `old-mods/` and the original server unchanged.
Replace the rev306 LFB main in **Fabric 1.21.11** `mods/` with the rev307 main; retain current
Fabric API, ViaFabricPlus 4.4.15, Cloth Config 21.11.153, and add Mod Menu 17.0.0 if absent.
Never install both LFB main JAR versions simultaneously.

Open Mod Menu, select a **successfully converted Fabric wrapper** for a Forge 1.7.10 mod,
and select its configuration gear. The LFB Mod Menu gear still edits the existing LFB
settings. Converted-mod gears are provided via ModMenuApi.getProvidedConfigScreenFactories;
no synthetic Fabric mods are registered, and unrelated native Fabric mods are untouched.
If an old JAR has not yielded an installed wrapper, there is no corresponding Mod Menu entry.

Source-pinned extracted configuration options:

| Source | Original Forge config | Literal option count | Local editor |
|---|---|---:|---:|
| Bamboo 2.6.8.5 | `config/BambooConfig.cfg` | 12 | 12 |
| Bamboo 2.6.8.5 | **world save** `/BambooDimConfig.cfg` | 1 | 0, excluded |
| iYAMATO 1.6.8 | `config/iymts_mod.cfg` | 41 | 41 |
| Twilight Forest 2.3.8 | `config/TwilightForest.cfg` | 30 | 30 |

Total 84 source-observed scalar settings, 83 safely editable *local* config settings.
The Bamboo dimension ID derives from `DimensionManager.getCurrentSaveRootDirectory` and
must **not** be redirected to the Fabric client's config folder. This is deliberately excluded.

## Design and compatibility boundaries

- Entry identity uses the converted Fabric wrapper's ID and presence of its conversion manifest.
- The original source JAR is resolved by `LegacyModMetadata.read(...).fabricId()` in `old-mods/`.
- SHA-256 must match the exact source profile; another JAR version cannot inherit its defaults.
- Every screen is generated from a shared option schema (`B`, `I`, `D`, `S`), not per-mod GUI code.
- If a profile is unavailable, a standard existing `<modid>.cfg` can be inspected and edited.
  A missing or ambiguous config shows an explanation instead of fake options.
- Existing config lines, unsupported list values and comments are retained. Writes are atomic
  where supported, guarded against stale edits, and backed up to `.lfb-backup`.
- Malformed, duplicate, non-UTF-8 or symlink config targets fail closed. Not all Forge
  Configuration syntaxes are supported by the new editor; unsupported options are not invented.
- Custom original 1.7.10 GUI classes (e.g. Bamboo's old GuiFactory) are **not executed** in 1.21.11.
- Item availability, config application in generated wrapper code, and server gameplay behavior
  are separate conversion problems. Editing client-side `.cfg` **does not change server state**,
  and does not demonstrate that any related gameplay behavior is implemented in LFB.
- No automatic text translation is synthesized. Mod identity/display name comes from the
  original metadata; Forge property labels/comments remain faithful to the original source.

## Build method and executed checks

Tools `extract_legacy_forge_config.py` extracts simple literal `Configuration.get(...)`
bytecode arguments using `javap`, never loading mod classes. Non-literal options are skipped;
property-count claims are NOT exhaustive source/gameplay-support claims.

`compile_offline_overlay.py` compiles a remapped/intermediary overlay using **temporary
signature-compatible test stubs** and the exact rev306 main JAR. Its stub types are NOT included
in the artifact. This is NOT a full Gradle/Fabric/Minecraft compilation. The bundled
`package_rev307.py` preserves existing ZIP entry payload bytes, replacing only the two small
entry classes and metadata and appending seven new classes plus the three source profiles.

Executed locally:

- LegacyForgeCfgFileSelfTest: 19 assertions, PASS (including an existing CRLF file, unknown
  properties, list preservation, backups, stale-write refusal, unsafe names, invalid values).
- ModMenuProviderSelfTest: PASS against explicit Fabric/ModMenu test doubles; three converted
  entries receive separate gear factories, unrelated mod excluded, original LFB gear retained.
- Source JAR hash/profile integrity checks: 3/3, 84 unique scalar descriptors.
- Offline remapped overlay compilation: PASS with test stubs.
- Final main-JAR ZIP integrity: PASS, duplicate entries: NONE, bundled ASM: NONE.
- 1,973 rev306 non-target existing ZIP entry payloads: BYTE-IDENTICAL.
- No live Minecraft 1.21.11 client, real Fabric Loader, real Mod Menu/Cloth integration,
  original Forge client, or 1.7.10 server execution was performed in this environment.

### Known gaps and test priority

Runtime UI appearance and save need first real in-game validation. The JAR should be treated
as an experimental alpha candidate, not confirmed compatible. First test the three converted
mod entries, each category, toggles and numeric entries, save/reopen, and client logs. Check
that the client config files are written only in expected paths, and that no world-save
`BambooDimConfig.cfg` is created in the client config directory. Retain the working rev306
base for immediate rollback.

No GitHub Actions, PR, tag or release should be triggered in continuation. Commit messages
must include `[skip ci] [skip actions]` and target only the specified feature branch.
