# LegacyForgeBridge Documentation

This directory is the project contract for LegacyForgeBridge. Code changes should follow these documents unless the documents are deliberately revised first.

## Documents

- [ROADMAP.md](ROADMAP.md) — version milestones, completion percentages, and acceptance criteria.
- [VERSION-VIRTUALIZATION.md](VERSION-VIRTUALIZATION.md) — how a 1.21.11 client presents itself while connected to a 1.7.10 Forge server, including hiding modern-only content.
- [COMPATIBILITY-MATRIX.md](COMPATIBILITY-MATRIX.md) — support classes and how compatibility percentage is measured.
- [ARCHITECTURE.md](ARCHITECTURE.md) — project boundaries, major subsystems, and design rules.

## Core rule

LegacyForgeBridge converts **gameplay intent and observable behavior**, not historical implementation details.

Examples:

- old Forge event hook -> Fabric/vanilla event where possible;
- old ASM/CoreMod hook -> modern event or shared Mixin where necessary;
- direct legacy OpenGL rendering -> modern rendering API;
- old field mutation -> accessor/adapter semantics;
- old registry operation -> modern pre-registration/conversion path.

The bridge must prefer explicit diagnostics over silently producing a broken converted mod.
