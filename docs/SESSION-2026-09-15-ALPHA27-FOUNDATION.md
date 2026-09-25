# LegacyForgeBridge — Bamboo alpha.27 conversion-foundation work

Date: 2026-09-15
Branch: `feature/generic-conversion-bamboo-corpus2`

## Scope

This work package strengthens conversion reproducibility and dependency evidence before the first BlockEntity / Inventory / Menu implementation slice.

It does **not** mark Bamboo loader-safe, installable, or gameplay-complete.

## Exact corpus identity

The formal Bamboo corpus remains:

```text
Bamboo-2.6.8.5.jar
size = 1,319,593 bytes
SHA-256 = bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

The binary is external test input and is not committed to this repository.

## alpha.27 changes

### Converter/cache identity

Conversion cache identity now includes all of:

```text
public converter version
conversion schema
converter revision
source SHA-256
```

A matching fingerprint is no longer sufficient to reuse a `PARTIAL` or `CONVERTED` result. The cached candidate must still exist and its SHA-256 must equal the previously recorded candidate SHA. Missing, modified, malformed, failed, or unknown cached results rebuild instead of being treated as verified.

`BLOCKED` remains reusable only when it never claimed a candidate artifact.

### Exact-corpus test task

Normal unit tests exclude the external `exact-corpus` tag. A separate fail-closed task is provided:

```text
gradle exactCorpusTest -PlfbExactCorpusJar=/path/to/Bamboo-2.6.8.5.jar
```

or:

```text
LFB_EXACT_CORPUS_JAR=/path/to/Bamboo-2.6.8.5.jar gradle exactCorpusTest
```

If that task is explicitly invoked without a corpus path, it fails instead of silently reporting a skipped corpus test.

The Bamboo exact regression pins the source SHA and checks the existing extraction/materialization baselines, deterministic candidate output, class-dependency sidecar, and continued fail-closed `PARTIAL` state.

### Original-class dependency evidence

`LegacyClassDependencyAnalyzer` and `legacyforgebridge/class-dependency-analysis.json` add a non-executing ASM inventory of the original source classes.

Evidence includes:

- `@Mod`, `@SidedProxy`, `guiFactory`, active `FMLCorePlugin`, and service-provider roots;
- source-to-source symbolic references;
- generated modern bytecode that still references a source class;
- source/candidate byte equality state;
- direct capability families such as BlockEntity, Inventory/Menu, NBT, rendering, entities, projectiles, worldgen/dimensions, events, network and item families;
- reverse dependent sets for prioritizing shared capability work;
- reflection/native/invokedynamic evidence where static reachability is incomplete.

This analysis intentionally never emits an `exclude` authorization. `UNRESOLVED_NOT_PROVEN_UNREACHABLE` means exactly that: static analysis did not prove a path, not that the class may be deleted.

## Safety boundary unchanged

The following remain prohibited shortcuts:

- forcing `loaderSafe=true`;
- forcing `status=CONVERTED`;
- deleting all 341 original classes just to satisfy the audit;
- treating generated wrappers or a dependency report as proof that legacy semantics have been replaced;
- treating a normal CI build as an exact Bamboo corpus pass.

## Next implementation slice

After this foundation passes CI, continue with the highest-value shared runtime blocker rather than a Bamboo-name special case.

The current exact Bamboo investigation identifies `TileEntityJPChest` / its 54-slot inventory as a useful acceptance anchor for a generic BlockEntity + NBT + Inventory + Menu pipeline. Production code must be expressed through shared analyzers/materializers/runtime adapters, with the Bamboo class used only as corpus evidence and acceptance coverage.
