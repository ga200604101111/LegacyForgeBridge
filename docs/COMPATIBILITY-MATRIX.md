# Compatibility Matrix

This document defines support classes and how LegacyForgeBridge reports compatibility. Percentages are measured against a declared test corpus, not against every Forge 1.7.10 mod ever released.

## Support classes

| Class | Meaning | Expected handling |
|---|---|---|
| A | Direct mapping | class/method/field/resource remap only |
| B | API adapter | Forge/FML API call translated to modern Fabric/vanilla API |
| C | Semantic transform | bytecode/data/lifecycle rewrite required |
| D | Legacy implementation migration | old renderer/ASM/CoreMod intent translated to event/Mixin/modern renderer |
| E | Unsupported | no safe deterministic translation currently exists |

## Initial subsystem targets

| Subsystem | v0.4 | v0.5 | v0.7 | v1.0 target |
|---|---:|---:|---:|---:|
| Item/Block/Recipe | 90% | 95% | 98% | 99%+ corpus coverage |
| Registry/GameRegistry | 85% | 95% | 98% | 99%+ |
| Forge events | 70% | 90% | 95% | 98%+ |
| Inventory/GUI | 20% | 75% | 90% | 95%+ |
| Entity basics | 20% | 70% | 90% | 95%+ |
| Networking | 10% | 65% | 85% | 95%+ |
| World generation | 10% | 65% | 85% | 95%+ |
| Rendering | 5% | 25% | 75% | 90%+ |
| CoreMod/ASM intent | 0% | 10% | 40% | corpus-defined only |

These are engineering targets, not current measured results.

## Per-mod score

Each analyzed mod should eventually report:

```text
startupCompatibility
apiCoverage
semanticTransformCoverage
renderingCoverage
networkCoverage
behaviorFidelity
automaticConversionRate
manualRuleCount
unsupportedRuleCount
```

A mod is not considered supported merely because Minecraft reaches the title screen.

## Result levels

- **FULL** — primary gameplay loop verified; no known critical loss.
- **HIGH** — minor visual/edge-case loss; primary gameplay works.
- **PARTIAL** — starts and some features work, but material features are missing.
- **ANALYSIS_ONLY** — JAR can be analyzed but should not be emitted as a converted gameplay mod.
- **UNSUPPORTED** — conversion must stop before producing a misleading output.

## Compatibility rule IDs

Every transformation or rejection should have a stable rule ID, for example:

```text
LFB-MAP-ITEMSTACK-COUNT
LFB-FORGE-GAMEREGISTRY-ITEM
LFB-FORGE-EVENTBUS-SUBSCRIBE
LFB-RENDER-GL11-MATRIX
LFB-COREMOD-UNKNOWN-TRANSFORMER
LFB-SESSION-MODERN-ITEM-BLOCKED
```

Reports should reference rule IDs so failures are searchable and regression-testable.

## Measurement rules

A release report should record at minimum:

- tested mod count;
- successful analysis count;
- successful conversion count;
- successful game-start count;
- successful primary-gameplay-loop count;
- average automatic-conversion percentage;
- count of manual compatibility rules;
- unsupported reason distribution.

Do not inflate scores by excluding difficult failures after they enter the declared corpus.
