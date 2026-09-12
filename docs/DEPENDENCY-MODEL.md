# Legacy Mod Dependency Model

Forge 1.7.10 mods frequently depend on one or more library/API mods, and those dependencies may themselves have further dependencies. LegacyForgeBridge must resolve the complete dependency closure before conversion begins.

## Core rule

Never convert mods in directory or filename order.

Conversion planning is driven by a normalized dependency graph built from every JAR in `old-mods` plus any bridge-provided compatibility modules.

Example:

```text
GameplayMod
  -> MagicAPI
      -> CommonLibrary
          -> UtilityCore
```

`UtilityCore` must be analyzed/planned before `CommonLibrary`, then `MagicAPI`, then `GameplayMod`.

Independent branches may execute in parallel.

## Dependency sources

The planner should collect dependency information from all useful sources rather than trusting one metadata field:

- Forge/FML mod metadata;
- `@Mod` dependency declarations and ordering constraints;
- declared mod IDs and versions;
- manifest/bootstrap information;
- known CoreMod/loading-plugin relationships;
- bytecode references to classes owned by other discovered mods;
- known API packages/providers;
- optional integration checks such as runtime mod-presence tests;
- bridge-maintained compatibility rules when legacy metadata is incomplete.

Bytecode-derived dependencies are evidence, not an automatic hard dependency in every case. They must be classified because optional integrations may reference another mod only behind a presence check.

## Normalized edge types

Every edge should be normalized into one of these classes:

```text
HARD_REQUIRED
SOFT_OPTIONAL
ORDER_AFTER
ORDER_BEFORE
API_PROVIDER
BOOTSTRAP_REQUIRED
IMPLICIT_CLASS_REFERENCE
```

Version constraints are stored separately on the edge.

Examples:

```text
ModA --HARD_REQUIRED--> LibraryB >= 1.2
ModA --SOFT_OPTIONAL--> IntegrationC
ModD --ORDER_AFTER----> ModE
CoreModF --BOOTSTRAP_REQUIRED--> HelperG
```

## Transitive dependency closure

Required dependencies are resolved recursively.

If:

```text
A -> B -> C -> D
```

and `D` is unavailable or unsupported, the planner must report the complete affected chain:

```text
D unavailable
C blocked by D
B blocked by C
A blocked by B
```

Unrelated mods continue converting normally.

The diagnostic must identify the root cause rather than reporting four unrelated conversion failures.

## Version constraints

The resolver must preserve and validate legacy version ranges/constraints where metadata provides them.

A dependency with the correct mod ID but an incompatible version must not silently satisfy a hard edge.

The conversion manifest should record:

```text
providerModId
providerLegacyVersion
acceptedLegacyRange
convertedProviderVersion
resolutionStatus
```

## Multiple mods in one JAR

One physical JAR may expose more than one FML mod ID.

Therefore the graph distinguishes:

```text
ArtifactNode = physical JAR
ModNode      = logical mod ID inside the artifact
```

Conversion scheduling operates primarily on artifacts, while dependency resolution operates on logical mod IDs.

## Library/API mods

Library and API mods are first-class conversion targets, even when they add no visible gameplay content.

A converted dependent mod must never assume its old library classes exist unless one of these is true:

1. the library/API mod was converted and its modern artifact is available;
2. LegacyForgeBridge provides a documented compatibility replacement;
3. the dependency can be proven optional and the integration path is disabled.

## Optional dependencies

Missing optional dependencies must not block the base mod.

Instead, optional integration paths should be classified as:

```text
AVAILABLE_AND_CONVERTED
AVAILABLE_BUT_UNSUPPORTED
ABSENT_OPTIONAL
DISABLED_BY_POLICY
```

Where practical, the transformer should preserve the original behavior of runtime checks such as "is this mod loaded?" so optional integrations remain optional after conversion.

## Cycles and SCC handling

Legacy mod ecosystems may contain cyclic relationships. The raw graph therefore cannot be assumed to be a DAG.

LegacyForgeBridge should:

1. build the complete directed graph;
2. find strongly connected components (SCCs);
3. classify whether cycles are hard, soft, ordering-only, or API-level;
4. collapse valid SCCs into conversion groups;
5. produce a DAG of groups for scheduling.

A group containing mutually hard-coupled mods may need to be analyzed/validated as one conversion unit.

An unsafe or contradictory cycle must produce a clear diagnostic rather than nondeterministic ordering.

## Parallel scheduling

After SCC condensation, independent graph layers may run concurrently.

Example:

```text
Layer 0: CoreA, LibB, LibC      <- parallel
Layer 1: API-D, Addon-E         <- parallel when prerequisites complete
Layer 2: Gameplay-F
```

The scheduler may parallelize:

- hashing;
- metadata extraction;
- ASM analysis;
- mapping;
- per-artifact transformation;
- per-artifact validation.

Cross-artifact dependency resolution and final manifest publication are coordinated centrally.

## Failure propagation

A failed hard prerequisite blocks only its dependent closure.

Example:

```text
A -> B -> C   (C fails)
X -> Y        (independent)
```

Expected result:

```text
C = FAILED
B = BLOCKED_DEPENDENCY
A = BLOCKED_DEPENDENCY
Y = CONVERTED
X = CONVERTED
```

This is required for large modpacks so one broken dependency does not waste all conversion work.

## Cache invalidation

Cache keys cannot depend only on the mod's own SHA-256.

A converted artifact must also be invalidated when a relevant prerequisite changes.

Conceptually:

```text
conversionKey = hash(
  sourceJarHash,
  converterVersion,
  mappingVersion,
  compatibilityRuleVersion,
  resolvedDependencyManifestHashes
)
```

If a library/API dependency changes, every dependent artifact whose generated bytecode or linkage depends on it must be revalidated and, when necessary, reconverted.

## Diagnostics

The dependency planner should produce a readable report such as:

```text
[READY] UtilityCore 1.0
[READY] CommonLibrary 2.4
  -> requires UtilityCore >= 1.0
[READY] MagicAPI 3.1
  -> requires CommonLibrary >= 2.0
[BLOCKED] GameplayMod 5.6
  -> requires MissingAddon >= 1.3 [not found]
[READY] IndependentWeapons 1.2
```

Required diagnostic rule families:

```text
LFB-DEP-MISSING-HARD
LFB-DEP-VERSION-MISMATCH
LFB-DEP-CYCLE-UNSAFE
LFB-DEP-OPTIONAL-ABSENT
LFB-DEP-PROVIDER-UNSUPPORTED
LFB-DEP-BLOCKED-TRANSITIVE
```

## Acceptance criteria

Dependency handling is considered stable only when LegacyForgeBridge can reproducibly resolve and schedule test packs containing:

- no dependencies;
- one-level dependencies;
- 3+ level transitive dependencies;
- shared libraries used by many mods;
- optional dependencies;
- version constraints;
- multiple mod IDs in one JAR;
- dependency failure in one branch while independent branches still convert;
- valid cyclic/SCC groups;
- invalid cycles with deterministic diagnostics.
