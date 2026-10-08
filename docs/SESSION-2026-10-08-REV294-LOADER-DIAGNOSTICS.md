# 2026-10-08 — rev294: generic loader outcome diagnostics (complete main preview)

Branch: `feature/generic-conversion-iyamato-corpus3`. Safety constraints from `AGENTS.md` remain in force.

## User symptom and exact scope

The user reported:
- `conversionFinished=true`, elapsed ~132 s; `total=4`, `index=4`, `percent=100`
- `state=ERROR`, `未能載入 4`, `待重啟 0`, `管理器回報已載入 0`, `PARTIAL 0`
- `warningCount=0`
- `uiArtifactVersion=0.2.0-alpha.27-corpus4-local.34-rev251-via-lifecycle.1`

The **actual** rev293 main JAR still included that hardcoded rev251 string inside the in-game and embedded DesktopHelper `Rev247SupportWindow.observe`. It was not a reliable indication of the installed main build. The desktop summary previously combined `FAILED`, `BLOCKED`, `NOT_LOADER_SAFE`, `NOT_STAGED`, `CONFLICT`, and `LOAD_NOT_CONFIRMED` into one `unusable` tally. The parent `warningCount` increments primarily for `PARTIAL/BLOCKED`, not all `FAILED` outcomes, so zero warnings never implied zero unavailable mods. These facts are grounded in rev293 shipped bytecode.

**Do not infer which four source JARs failed or claim they were all conversion errors**: the user's actual `legacy-cache/conversion-state.json`, `legacy-cache/reports/conversion-diagnostics-rev213.zip` and `logs/latest.log` were *not* provided with the summary.

## rev294 shipped preview, complete main JAR

File: `legacyforgebridge-0.2.0-alpha.27-rev294-generic-load-diagnostics-preview.jar`

SHA-256: `c12ab8b56d60c73b90e2eb817546aef839f62c31d4d7f30e571f24966eab8601`

Base: existing complete `legacyforgebridge-0.2.0-alpha.27-rev293-localization-armor-sword-preview.jar`, pinned SHA-256 `20cbb158a1dace616521a487aa4e072eaeef685bd41b9baefb3d2339a463c570`.

Compiled **one** Java 21 production helper `desktop.LegacyConversionStatusDetails` (Gson-facing public API); it produces bounded, sanitized Chinese per-source diagnostic lines, split by exact loader outcome and active conversion diagnostics. It skips successfully loaded / waiting-restart candidates and gives action-specific guidance for known outcomes; it never mutates a conversion result or authorizes an unsafe JAR.

Narrow audited ASM patches over the **actual rev293 shipped classes**:
- `DesktopConversionSession.publish` writes actual `BuildInfo.VERSION` into the parent status `converterVersion` property on every publication.
- `DesktopConversionSession.saveReport` puts the per-source outcome detail into status `message` and publishes `errorCount` as the existing count of unusable modules, rather than leaving the diagnostic field blank; individual outcome codes distinguish hard failures from load-not-confirmed.
- `Rev247SupportWindow.observe` uses `status.converterVersion` (or explicit `unreported-main-version` fallback), **not** the stale hardcoded rev251 string; this class is updated in both the main and the nested DesktopHelper, with all other nested helper JAR entries preserved.
- `BuildInfo.VERSION` is updated to `0.2.0-alpha.27-corpus4-local.47-rev294-load-diagnostics-preview.1` and Fabric metadata/manifest accurately describe `rev294`.

**Critical cache invariant:** `BuildInfo.CONVERTER_REVISION` remains `2026-10-08.293-item-localization-equipment-source`. The cache fingerprint was checked in separate JVM launches and was byte-for-byte identical between rev293 and rev294. Do not force all four source JARs to reconvert for a diagnostics-only change.

## Tests actually performed

- Java 21 with compact Gson stand-ins: `LegacyConversionStatusDetailsSmoke` **11/11 PASS** on mixed `FAILED`, `NOT_LOADER_SAFE`, `LOAD_NOT_CONFIRMED`, `CONFLICT`, `LOADED_REPORTED` and `PENDING_RESTART` synthetic JSON, plus missing data, bounded length and sanitation.
- Same 11/11 smoke executed using **the class packaged in the rev294 JAR** (new JAR first in classpath).
- JDK ASM BasicVerifier on actual packaged `DesktopConversionSession` (**49 methods**), `Rev247SupportWindow` (**15**), `BuildInfo` (**4**) and `LegacyConversionStatusDetails` (**9**).
- ZIP CRC: PASS. 1,929 existing outer entries preserved, 2 entries added (one Java21 class, one provenance JSON), 6 reviewed replacements, no deletions; **all other entries preserved byte-for-byte**.
- Nested DesktopHelper only changed the **one** `Rev247SupportWindow.class` file (18 entries preserved); the corrected UI class matches the copy in the main JAR.
- The prior exact translated Twilight Forest `-lfb.jar` passes the existing `ManagedCandidateInstaller.isLoaderSafeCandidate` structural method, whereas the original Forge 1.7.10 source JAR does not. This does not confirm actual Fabric Loader start or the user's four other mods.

The local tests **did not** launch Minecraft 1.21.11, Fabric Loader, ViaFabricPlus, Prism on Windows or the original Forge 1.7.10 server. The source tree still lacks full rev256–rev260 source overlays; this is an audited complete-binary overlay, **not** an original full Gradle/Loom build. No missing custom-mod behavior, equipment, weapon, entity or animation runtime adaptation has been added.

## User follow-up needed to fix the four actual failures

Request the **full** `legacy-cache/reports/conversion-diagnostics-rev213.zip` first. If not available, request `legacy-cache/conversion-state.json`, the latest `legacy-cache/desktop/sessions/<session-id>/conversion-diagnostics.txt` and `logs/latest.log`. Those identify each source JAR's `status`, `phase`, `outcome`, `loaderSafe`, `managedJarPath` and relevant diagnostics.

Do **not** bypass `loaderSafe=false` or force `FAILED/BLOCKED` converted wrappers into the Fabric `mods` folder. Converted output may be a source-identity wrapper without playable support. Also do not treat the stale rev251 UI label as evidence of an installed obsolete main.

## Preservation

Only `feature/generic-conversion-iyamato-corpus3`, no CI/Actions dispatch, no PR/tag/release, no force push or main/Bamboo changes. Old/source JARs and the user Forge server unchanged. Every new continuation commit includes `[skip ci] [skip actions]`.
