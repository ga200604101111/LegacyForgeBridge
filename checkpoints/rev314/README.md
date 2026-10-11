# LegacyForgeBridge rev314 — source ModelBox UV, layout migration and packaging checkpoint

## Delivery and exact inputs

- **Delivery build:** `rev314-uv-layout.1`. The **installable** delivery was provided separately as `LFB_rev314_Visual_Layout_Install.zip`, 9,942,923 bytes, SHA-256 `3803e4126dc372da46f83dc04affc4ba48c75b7897d3267af4925cf90d556fda`. The Git checkpoint intentionally stores source and verification, not the three multi-megabyte executable JARs or original mod binaries.
- **Source/verification archive:** [`source_bundle.tar.xz`](source_bundle.tar.xz), SHA-256 `72c3d25a70037f84eee588bde160307465c9c1d41ca5f0f9bbb99382159cad73` (40,332 bytes), 56 text/source/support entries. It includes complete rev314 delta source, tooling, tests, raw verification reports, installer scripts, and documentation. Extract with `tar -xJf source_bundle.tar.xz` to get `source/`, `verification/`, `docs/`, and `installer/`.
- **Exact previous install package** SHA-256: `d6f0df8e96f3ca43657a4c4509d57769cf837081373c5aa3946e32a051119d16`. This is the *Linkage_Audit* installer artifact, **not** the git root `src/`, and must be supplied separately for a rebuild.
- **Exact original Twilight Forest Forge 1.7.10 JAR** SHA-256: `1aa2c191170ff707499c42cd58bdcc69f2772b59014cb684ba99a6d1e6540589`. Not committed or modified.
- **Runtime JAR in the installer:** `LegacyForgeBridge-rev313-bootstrap1.jar` (rev314-patched runtime despite retained original filename), SHA-256 `4853f94fc421363ea984cf038ccdb1c9e07aa7eb654cdae2156bd99962d30b61`.
- **Converter JAR:** `LFB-Converter.jar`, SHA-256 `ce362e2b924fa87c54ebb360ad90bd23e1e8bad1ad8ccac82e0f546a3ffceb08`.
- **Bootstrap JAR:** `LFB-Bootstrap.jar`, SHA-256 `aa2b9eaee232e47eca37e3c45f2aec373b5ba5a6ef2952b32e434e9a48831585`.

## Implemented changes

1. Recovered the six original Twilight Forest cicada and four firefly ModelBox declarations by **static ASM inspection** of the supplied original source JAR, without loading/executing Forge 1.7.10 classes. Corrected per-face UVs and orientation for the 14 pre-atlas cicada/firefly block placement templates (floor, wall, ceiling variants). Original PNGs, flat inventory icons and all original dimensions are retained; the old cicada top-side wings/legs had sampled transparent texture pixels.
2. `LegacyBoxMesh` and `LegacyBoxModelPart` make explicit source-style per-face UVs in the modern client-side ModelPart path, retaining the existing animation and emissive/translucent firefly pulse. The historical thin glow geometry keeps the prior 1/256-pixel thickness approach without claiming pixel-perfect legacy additive GL blending.
3. External bootstrap migrates LegacyForgeBridge-owned cache to `minecraft/lfb/cache` and LFB logs to `minecraft/lfb/logs`; preserves previous state, conflict backups, and historical logs. It does not move unrelated Minecraft logs, original Forge JARs, mod outputs, or `.fabric`. Existing converted candidates are still validated/deployed into `mods`; `fabric.addMods` cache loading is **not** implemented.
4. Preserves rev313-and-earlier conversion passes, original Swing UI/icon and no-auto-restart behavior, linkage/audit, Mixin, VFP/protocol behavior, source-SHA model patch scoping, and Forge server authority. No expansion of generic rendering/gameplay compatibility is claimed.

## Reported offline validation (from supplied rev314 installation package)

- 48 original-model/texture/pre-atlas checks; 529 UV/geometry factory assertions; 26 layout migration tests; warm-layout integration report.
- 59 synthetic full-converter regression checks; the original Swing window checked at 1x and 2x DPI (44 cases each).
- 50 touched/new classes and 675 methods reported as passing ASM BasicVerifier; independent rebuilt payload files reported byte-identical (see `verification/summary.json`, `verification/artifact-audit.json`, `verification/rebuild-equivalence.json` inside the archive).
- **These results are preserved from the delivered package, not new live-game acceptance.** No Windows PowerShell install test, actual Minecraft 1.21.11 client rendering, ViaFabricPlus / Forge 1.7.10 server session, or full four-mod conversion acceptance was performed. The original Twilight full conversion was blocked at the test environment's missing DataFixerUpper `DynamicOps` dependency.

## Rebuild (requires external exact inputs)

`source/tools/build_from_exact_package.py` refuses a mismatched base package, mismatched source JAR, and nonempty output directory. Requirements: JDK 21, Python 3, Pillow, and a separate ASM core/tree/analysis classpath. API signatures are explicit test doubles only, **not shipped in executable outputs**.

```sh
python source/tools/build_from_exact_package.py \
  --base-package /path/to/previous-Linkage_Audit-package.zip \
  --source-jar /path/to/twilightforest-1.7.10-2.3.8-tw.jar \
  --asm-classpath /path/to/asm.jar:/path/to/asm-tree.jar:/path/to/asm-analysis.jar \
  --out /path/to/empty-rebuild
```

See `docs/變更及驗證範圍.md` and `docs/使用說明.txt` in the source archive for the full change and installation boundaries. **Do not compile from repository root `src/` as though it contains the cumulative rev314 main**; retain the prior complete installer as the exact base.

## Repository and publishing boundaries

Work only on `feature/generic-conversion-iyamato-corpus3`. This checkpoint is additive on the rev313 repository commit; no main/Bamboo edits, PR, tag/release, workflow change, GitHub Actions dispatch, force push, original Forge mod mutation, or server changes. `[skip ci] [skip actions]` is required in the commit title.
