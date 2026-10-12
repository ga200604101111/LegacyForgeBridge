# rev323 — Source Visuals / Generic Contracts checkpoint

This checkpoint records the **rev323 installer actually prepared and verified on Windows**. It is a source-and-install-script checkpoint, not a claim that a Git checkout alone reproduces the complete binary runtime.

## Delivery

- Local installer ZIP: `LFB_rev323_Generic_Contracts_Install.zip` (43 entries, 10,484,220 bytes).
- ZIP SHA-256: `1e22cb65d839973f714324b20d988c73ecc86d568aa87b945b546d8c94de4a86`.
- Runtime (`LegacyForgeBridge-rev321-optimizer-core.jar`) SHA-256: `3c0a656c128574788a2ba76817dfb4f3a5f48e936661d316219f94f491bd12a9`.
- Converter SHA-256: `556ce2f47196bf63546637da95b61dfe4e5d1654222fbf1c280c3d0cdfe11c9a`.
- Bootstrap SHA-256: `14d56189da4b0216e910d6687969f2986e3924d6c1d417a419635a6191bdc54e`.
- Converted Twilight Forest output SHA-256: `a97a5d14244ce0ed8dbbe872ac1ffd8554576fc36fc48c3c5bf9af24948a57b4`.
- Original Twilight Forest 1.7.10 source SHA-256: `1aa2c191170ff707499c42cd58bdcc69f2772b59014cb684ba99a6d1e6540589` (not stored).
- Full ZIP and binary JAR payloads remain on the authorized Windows machine under `minecraft/lfb/releases/`; this checkpoint does **not** commit the binaries or original Forge mods.

## Source and installation layout

- `source/repair322/`: source-driven plant geometry, source visual programs, grid UV and client runtime support.
- `source/repair323/`: additional source-driven shape, mesh, armor, creative tabs, spawn eggs and held-item analysis, native self-tests, runtime hooks.
- `tools/build.py` and `tools/assemble.py`: *historical* build/patch scripts, dependent on externally supplied Java 21, Minecraft/Fabric API jars, previous binary baseline and local paths.
- `installer/`: exported installer and rollback script text plus checksum manifest. The installer expects the binary `LFB_Package/*.jar` payloads that are **not stored here**; therefore the Git directory by itself is NOT an installable package.
- `verification/final-receipt323.json` and `verification/snapshot323.json`: local validation summaries.

The source is held separately from root `src` to avoid incorrectly overwriting the older maintained source tree. Scripts were transported through UTF-8 text interfaces; Git text-file bytes may differ in line endings/BOM from ZIP entries. The verified hashes above apply to the original local ZIP/JAR files, not these transcribed text files.

## Validation and outstanding gaps

- One fresh Twilight Forest conversion and cache reuse succeeded, with `PREPARE_OK`.
- Native read-only checks: `meshes=152`, `shapes=480`, `armor=28`, `eggs=53`, `held=6`. These are not gameplay-equivalence counts.
- Client reached a Minecraft 1.21.11 world in the previous local run. Live placement of an entire giant 4×4×4 structure was **not fully verified**.
- Known remaining issues: special scepter hold/use animation, source creative enchantment stacks, cicada sound namespace/event, invisible Twilight Forest entities (no admitted live entity render rules), and post-placement giant structure rendering.
- No additional rev324 fixes are included in this rev323 checkpoint.
- Server gameplay remains authoritative; original 1.7.10 server and old mod archives were not modified.

This is a non-destructive checkpoint commit on `feature/generic-conversion-iyamato-corpus3`. No CI dispatch, workflow edits, PR, tags, releases, force push or other branch modifications.
