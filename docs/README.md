# LegacyForgeBridge Documentation

This directory is the project contract for LegacyForgeBridge. Code changes should follow these documents unless the documents are deliberately revised first.

## Documents

- [ROADMAP.md](ROADMAP.md) — version milestones, completion percentages, and acceptance criteria.
- [CONNECTION-ACCEPTANCE.md](CONNECTION-ACCEPTANCE.md) — what counts as a real, playable 1.7.10 connection.
- [VERSION-VIRTUALIZATION.md](VERSION-VIRTUALIZATION.md) — how modern item state is bounded safely while connected to a 1.7.10 server.
- [DEPENDENCY-MODEL.md](DEPENDENCY-MODEL.md) — transitive prerequisites, optional dependencies, version constraints, cycles/SCC groups, cache invalidation, and dependency-aware parallel conversion.
- [COMPATIBILITY-MATRIX.md](COMPATIBILITY-MATRIX.md) — support classes and how compatibility percentage is measured.
- [ARCHITECTURE.md](ARCHITECTURE.md) — project boundaries, major subsystems, and design rules.
- [CONVERSION-API.md](CONVERSION-API.md) — internal conversion SPI, candidate/manifest safety model, and the first RPGTool1 corpus profile.
- [TESTING.md](TESTING.md) — GitHub Actions regression tiers, synthetic fixtures, real-mod corpus policy, and client/server integration testing.
- [DEVELOPMENT-WORKFLOW.md](DEVELOPMENT-WORKFLOW.md) — required branch/PR/CI batching workflow, runtime-Mixin validation rules, and the alpha.10 shaded-Gson incident guardrail.
- [`corpus/`](corpus/) — expected analyzer/conversion baselines for representative real 1.7.10 mods.

## Core rule

LegacyForgeBridge converts **gameplay intent and observable behavior**, not historical implementation details.

Examples:

- old Forge event hook -> Fabric/vanilla event where possible;
- old ASM/CoreMod hook -> modern event or shared Mixin where necessary;
- direct legacy OpenGL rendering -> modern rendering API;
- old field mutation -> accessor/adapter semantics;
- old registry operation -> modern pre-registration/conversion path.

The bridge must prefer explicit diagnostics over silently producing a broken converted mod.
