# LegacyForgeBridge rev312 — shared source bytes and registry analysis (phase 1)

## Complete main and preserved baseline

- Exact base: `legacyforgebridge-0.2.0-alpha.27-rev311-critter-tesr-animation.jar`, SHA-256 `a21281ef417669490546e4176e38f8670bdc3dd3e28c973ae828b90ce59c4913`.
- Complete main: `legacyforgebridge-0.2.0-alpha.27-rev312-shared-source-analysis.jar`, **5,272,990 bytes**, SHA-256 `ad433e14755d73b57da88d8c6f764a210a61cfffbdcecd52893dc1c1629ff99b`.
- Version: `0.2.0-alpha.27-corpus4-local.66-rev312-shared-source-registry.1`.
- This is an additive checkpoint over the delivered rev311 main, NOT a build from older root `src/`. The rev311 source checkpoint was also supplied in the conversation. Its animated critter runtime is retained; do not rebuild this against rev310.
- 1,884 non-target ZIP entry payloads are identical to rev311, including **634 protected runtime/resource entries**. No changes to models, particles, tooltip order, weapon interaction, Mod Menu/Cloth config, the nested desktop helper, original Forge mods, server state or protocol gameplay.

## What is implemented

`ScopedConversionRunner` enters a bounded source scope around the actual `LegacyConversionEngine.convertAnalyzed` entry point and always closes it. Original pass order and the full original method remain present as `lfb$rev312ConvertUncached`. A SHA/stamp check is inserted before the existing `DesktopPackHook.pack` call, inside the converter's failure handling.

`SharedSourceSession` indexes the current archive and caches private immutable entry bytes lazily. 148 original `JarFile.getInputStream` call sites across converter code delegate through this cache. Each consumer gets an independent `ByteArrayInputStream`; no caller receives the cached byte array. Other JARs, staging paths, unscoped calls, oversized entries or exhausted budgets use the original IO. Duplicate archive names disable the optimization. Entries larger than 8 MiB are not retained. Default retained-byte limit is min(64 MiB, JVM max heap / 16); there is no unbounded process-global source cache.

`SharedRegistryAnalysis` coalesces registry analysis requests in the scope. It shares the original immutable `Analysis` AND captures the two existing stateful public query surfaces: item classifications and hidden creative-registration keys. It never shares an ASM ClassNode/Frame/MethodNode, and releases the private analysis graphs after capturing those queries. The original algorithm and public generic signatures remain available. A failed computation is not permanently cached; interrupted waiters do not cancel the owner. Nested source scopes and reused analyzer instances cannot consume another source's snapshot.

Explicit scope propagation (`Scope.bind`) supports independent readers requesting the same result. **This does not introduce a new parallel pass scheduler or run competing output writers at once.** Existing lifecycle caching, source prefetch and timing mechanisms are not replaced.

## Limits of this phase

This is not yet a complete immutable parsed-class index: some analyzers still enumerate archives and construct their own ASM trees. It saves repeated entry decompression/reading and repeats of the registry analysis. It does not claim every analyzer now executes only once.

The unified content IR, dependency DAG, automatic multi-mod conversion concurrency, and generalization of the SHA-gated critter/TESR patches are NOT implemented here. Model and inventory contexts remain separate. Successful source analysis does not establish gameplay compatibility.

## Measured comparison

The harness directly executes 12 actual source analyzers from the original/patched main against the same original Bamboo, iYAMATO and Twilight Forest JARs. No Forge mod classes are defined or executed. One warm-up round is discarded; values below are medians of three measured rounds on the build host with Java 21 and `-Xmx2g -Xverify:all`.

| Source | rev311 analysis suite | rev312 analysis suite | Elapsed reduction |
|---|---:|---:|---:|
| Bamboo 2.6.8.5 | 1059.291 ms | 396.203 ms | 62.60% |
| iYAMATO original supplied JAR | 1136.701 ms | 364.097 ms | 67.97% |
| Twilight Forest 2.3.8-tw | 4380.170 ms | 1083.658 ms | 75.26% |

**These are source-analysis timings, NOT full mod conversion, candidate compilation/packaging, game startup or the user's hardware timings.** All 36 analyzer results match after canonical serialization, in all four rounds and again using the final packaged main. Each suite makes eight registry requests; rev312 computes once and reuses seven times. Source class bytes retained in these suites are approximately 1.10, 1.18 and 3.29 MB respectively. Existing lifecycle caches are cleared equally for each round.

## Validation actually executed

- Original-JAR registry/classification/visibility tests: 8,608 assertions; 17 additional scope/interruption/source-switch edge checks.
- Eight simultaneous reader requests per source: one calculation, identical result, independent analyzer instances.
- Independent stream cursors, zero-byte cache budget, disable switch, nested scopes, scope cleanup on error, same-size/same-mtime mutation rejection by SHA, closed-scope rejection and generic renamed source.
- 147 touched/new classes, 2,475 methods checked with ASM BasicVerifier. For 2,414 original method bodies, reversing ONLY the added stream/scope hooks produces identical instruction traces.
- ZIP CRC, entry uniqueness and no bundled ASM; version metadata agrees; independent rebuild is byte-identical.
- New production Java code compiles against the JDK and the actual supplied main API, **without Minecraft/Fabric/Gson signature stubs**.
- Offline tooling/tests use the **real ASM implementation embedded in host OpenJDK 21.0.12.1**, relocated to `org.objectweb.asm` for a separate test/build classpath. This is not the exact Fabric Loader ASM 9.10.1 binary. No ASM dependency is shipped.
- **No end-to-end full candidate conversion, live Minecraft/Fabric, real loader ASM 9.10.1, ViaFabricPlus pipeline or original server test was run.** The artifact remains an experimental installable main candidate, not a claim of complete runtime acceptance.

## Install and diagnostics

Replace the single LFB main in the Fabric 1.21.11 client's `mods/` with rev312; retain the original 1.7.10 JARs in `old-mods/`. Do not install two LFB versions. The converter semantic revision stays `2026-10-09.310-pre-atlas-critter-source-flat-icon`, and its cache-compatibility constant stays unchanged. Existing valid converted candidates need not be thrown away just for this optimization; new/necessary conversions use the shared scope.

Every actual scoped conversion writes:

`legacy-cache/reports/source-analysis-rev312-<full-source-sha256>.json`

Check `registryRequests`, `registryComputations`, `registryHits`, `sourceEntryLoads`, `sourceEntryHits`, and `peakSourceBytes`. `conversionMethodReturned` means the method returned a result, not that the conversion result was successful; the existing conversion status report remains authoritative.

Rollback switch in launcher Java arguments: `-Dlegacyforgebridge.sharedAnalysis=false`. This disables the new caches while retaining source-integrity checks and original conversion algorithms. Optional memory control: `-Dlegacyforgebridge.sharedAnalysis.maxMiB=32`. No new Cloth configuration controls are claimed.

## Rebuild

Java 21 and Python 3.11+ are required. Keep the exact base JAR outside the source tree.

```sh
python tools/build_rev312.py --base /path/to/rev311.jar --out /path/to/rev312.jar
```

By default the script extracts the host JDK's embedded ASM for offline tooling only. To use external genuine ASM artifacts instead, pass `--asm-classpath` with ASM core/tree/analysis/util on the platform classpath. No original mod JAR, dependency JAR or compiled stub is in this checkpoint.

`tests/AnalysisSuite.java` accepts: enabled(true/false), rounds, output-directory, then source JAR paths. For baseline, put the original rev311 JAR before the new helper classes on the test classpath; do not activate a shared scope. For rev312, run directly against the new complete main. `SharedAnalysisTest` accepts the three original JAR paths and optional `-Dlfb.testOutput=/path/to/results`; `ScopeEdgeCases` accepts Bamboo and iYAMATO paths. Full raw execution logs/results are in the conversation's verification ZIP.

Work only on `feature/generic-conversion-iyamato-corpus3`, additive checkpoints, no force push, Actions dispatch, PR/tag/release, original-mod or server changes.
