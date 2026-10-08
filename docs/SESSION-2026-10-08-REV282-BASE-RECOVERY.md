# 2026-10-08 — rev282: recover rev260 complete-main binary provenance and add safe overlay packaging

Target branch: `feature/generic-conversion-iyamato-corpus3`.

## Uploaded, verified baseline

User-provided complete main:
`legacyforgebridge-0.2.0-alpha.27-rev260-handoff-fix.jar`

- Exactly **4,757,585 bytes**, SHA-256 `03a7bff020197b275977227ff0ee4dbbf7c9e3df77ea6c1425470f7fde4b93d9`.
- ZIP CRC and entries check passed; 1,826 file entries including 1,743 JVM class files. All class headers target Java 21 (major version 65).
- 11 Fabric entrypoint references, 6 declared Mixin configs and the included nested dependency/helper are present.
- Exact `fabric.mod.json` target is Fabric client Minecraft 1.21.11, with ViaFabricPlus 4.4.15+ and Java 21+ dependencies. The original artifact's manifest explicitly identifies `rev260` and `javac21-and-audited-bytecode-delta`, **not** full Loom/Gradle.
- Binary contains `convert/pass/Corpus3GenericCompletionPass.class`, `convert/LegacyProjectileLaunchGraph.class`, `desktop/DesktopConversionSession.class` and `rev260/HandoffIO.class`, while corresponding current `src/main` production Java sources for the first two and desktop handoff remain missing or incomplete. The rev260 class signatures and original build metadata were inspected directly.

## New reusable code (source-only, not an installable Minecraft update)

- `tools/release/verify_main_jar_overlay.py`: read-only base archive inventory and fail-closed base-vs-candidate comparison. Checks exact base SHA, ZIP CRC, duplicated/unsafe entries, JVM class headers, entrypoint/mixin/jar references, dependency and mapping metadata, unreviewed/removed binary entries, nested DesktopHelper preservation, output version bump, updated build revision/method manifest and an actual executable class delta. Its status `PASS_STRUCTURAL_ONLY` is deliberately **not** a compatibility certification.
- `tools/release/build_main_from_base.py`: reproducible complete outer JAR packaging from the user-provided exact baseline and a directory containing explicitly allowlisted, *already compiled* overlay classes/resources. Never rewrites the input JAR or silently overwrites a pre-existing output. Stages a temporary candidate, runs the structural verifier, then publishes it with no-overwrite handling. Failed report publication removes an incomplete output.
- `tools/release/test_verify_main_jar_overlay.py` and `tools/release/test_build_main_from_base.py`: isolated regression suites for release-guard and packager behavior.
- `diagnostics/rev282-rev260-binary-baseline/verification.json`: machine-readable baseline and validation summary.

Run from the repository root:

```bash
python tools/release/verify_main_jar_overlay.py \
  --base legacyforgebridge-0.2.0-alpha.27-rev260-handoff-fix.jar \
  --inventory-only --report rev260-inventory.json
```

A new JAR can be *structurally* packaged only after a real compiled overlay is available:

```bash
python tools/release/build_main_from_base.py \
  --base legacyforgebridge-0.2.0-alpha.27-rev260-handoff-fix.jar \
  --overlay-root compiled-overlay \
  --output genuinely-built-new-main.jar \
  --expect-base-sha256 03a7bff020197b275977227ff0ee4dbbf7c9e3df77ea6c1425470f7fde4b93d9 \
  --allow-file fabric.mod.json \
  --allow-file META-INF/MANIFEST.MF \
  --allow-file dev/yinghuang/legacyforgebridge/newfeature/ActualCompiledFeature.class \
  --report new-main-structural-verification.json
```

This example does **not** imply that `ActualCompiledFeature.class` currently exists. Do not compile/place an empty class simply to satisfy the package gate.

## Executed checks

- 11 synthetic archive verifier tests passed, including a stale-manifest build provenance rejection.
- 11 deterministic outer main packaging/rollback tests passed.
- 4 additional validation checks against the **actual uploaded** rev260 archive passed (accepted an explicitly allowlisted compiled synthetic class; rejected removal of the desktop helper, dropped Mixin declarations and metadata-only changes).
- End-to-end packaging smoke on the actual rev260 used one synthetic `javac21` class and a bumped *test-only* metadata version: **all 1,824 unmodified original file contents were preserved** (only the explicitly reviewed Fabric version and build manifest changed), both independent candidate JAR builds had identical SHA-256 and the original baseline JAR SHA-256 was unchanged. Both smoke candidates were destroyed, **not delivered**.
- 33 rev281 synthetic launcher/geometry-preflight methods passed when compiled against the **actual** rev260 archive as Java classpath, with temporary JDK-internal ASM substitutions. This is stronger binary-ABI evidence than earlier test doubles, but still not the actual Fabric ASM / Minecraft runtime.

## Critical handoff gap and nonclaims

Do **not** replace rev260 production converter classes with current root source wholesale. In particular the old compiled `LegacyProjectileLaunchGraph` has a multi-phase, source-backed launcher trace API with PRESS, HELD_TICK, RELEASE and FINISH, while the newly added rev281 `LegacyProjectileLauncherAnalyzer` source has a distinct, narrower proof shape. These implementations must be reconciled rather than assumed interchangeable.

The exact original `twilightforest-1.7.10-2.3.8-tw.jar` is still needed for real corpus validation. The uploaded JAR is the **LFB tool itself**, not Twilight Forest. Only static model and launcher source evidence has been advanced in rev279–281; MoonwormShot and the other Twilight Forest features are not newly executable under this checkpoint.

No new installable complete main JAR, native 1.7.10 comparison, live game test, Gradle/Loom build, original-mod/server mutation, Actions dispatch, PR, release, force push, main or Bamboo branch change is claimed.

## Next code path

1. Preserve rev260 production and embedded DesktopHelper bytecode as the baseline.
2. Recover/audit old production sources or bounded binary deltas: rev256–rev260, especially Corpus3 and desktop code, verifying exact ABI/caller behavior.
3. Compile new generic fixed-model / launcher / lighting code against actual external ASM/Gson/Fabric 1.21.11 API, then validate exact Twilight Forest corpus without mod-name allowlists.
4. Implement and test a modern client-only projectile renderer with source-mapped texture, cuboids, UV, axis-angle and full-bright semantics. Server remains authoritative for impacts, effects and spawning.
5. Only after real Minecraft/VFP and original Forge server acceptance should a new complete main JAR be distributed.

See `docs/SESSION-2026-10-08-TWILIGHTFOREST-PART2F4.md`, `docs/TWILIGHT-FOREST-238-ACCEPTANCE.md` and `docs/SESSION-2026-10-07-REV260-REV262-HANDOFF-GAP.md`.

**Build provenance guard:** the actual rev260 `META-INF/MANIFEST.MF` carries `LFB-Local-Patch-Revision: rev260` and `LFB-Local-Build-Method: javac21-and-audited-bytecode-delta`. A new complete candidate must include a reviewed replacement manifest with an accurate, different patch revision and build method, while retaining the intermediary namespace, Minecraft version and Fabric JAR type. The structural guard checks that these fields changed; it does not itself verify the honesty of the newly declared build method.
