# rev193 — native LegacyForgeBridge display-stack integration

This is a **main-mod replacement**, not the previously supplied `lfb_visual_stack_hotfix` add-on. The locally built artifact is `legacyforgebridge-0.2.0-alpha.27-rev193.jar`.

SHA-256: `840fb56d7cd8013b3c813fd3d0f4781acd4e571abd105d4be512c99046ec8cc8`

## Native source changes

- `ConvertedLegacyVisualEntityRenderer.modernStack` directly calls the internal vanilla display-stack resolver before retaining its original modded-item path.
- `LegacyGridPotNetworkBridge.stack` directly calls the same resolver before modern registry lookup. Legacy flower names and metadata are passed to the existing vanilla DFU path rather than looked up as modern identifiers.
- `LegacyVanillaDisplayStacks` uses the existing FML registry identities, `LegacyVanillaStackDataFix`, the real Minecraft `ItemStack.CODEC`, and the active client registry provider. It has no reflective add-on backend or per-mod/per-flower table.
- `LegacyDisplayStackCache` bounds immutable display templates to 2,048 identity/metadata entries, copies before setting counts, invalidates across client worlds, re-reads numeric registry bindings and does not cache failed migrations.
- Invalid or unmapped input retains the original path. No network packet, server inventory or gameplay rule is changed.
- The main manifest rejects `lfb_visual_stack_hotfix` to prevent duplicate injection. Remove that obsolete add-on when installing rev193.
- Runtime fingerprint: `2026-09-25.193-native-vanilla-display-stacks`.

## Preserved history and source restoration

The branch already stores rev189–192 as source checkpoints instead of expanded root source. **This commit continues that storage format; it does not falsely label the root `src/` tree as integrated rev193.** `source.patch` is the native rev192-to-rev193 delta. The preceding checkpoints contain the earlier source changes; no previously delivered change should be discarded by rebuilding only the old root tree.

From a checkout of this branch, run:

```sh
python checkpoints/rev193/restore.py --output ../LegacyForgeBridge-rev193
```

The output directory must not already exist and must be outside the checkout. The script verifies all three checkpoint checksums, creates an isolated tracked-source snapshot, checks and applies the five revisions in order, and validates the final fingerprint. It leaves the source checkout untouched, stops on conflict, and never invokes Actions. Review the restored sources before running the project's normal JDK 21 / Gradle build there.

## Local build and verification boundaries

The delivered binary was locally incrementally compiled with JDK 21.0.11 against checksum-pinned upstream artifact commit `dc6e166f5b49355d40c2d5a6cb6ab7628fb44a27`, with the rev188 production changes and complete rev189–192 source deltas restored. Runtime changes use guarded transplantation of selected method bodies; unrelated methods are retained. Compile-only API declarations and ASM/Gson dependencies are **not packaged**. The matching source and local build material are also supplied as a conversation download.

Verified on the delivered binary:

- 28 isolated display-cache assertions with an explicit fake backend, not a game or DFU launch.
- 14 generic inventory ownership/negative/idempotence cases, 16 pane connection masks and the six-face hanging-mesh/cache contract.
- Executed conversion-cache fingerprint and launch-header consistency.
- ASM BasicVerifier over 110 changed class files and 966 methods.
- A selected 16-pass original Bamboo 2.6.8.5 conversion; result remains **PARTIAL**.
- Main-mod identity, both native call sites, declared mixin/nested-dependency presence, ZIP CRC, absence of add-on or compile-only classes, and two byte-identical local assembly runs.

**No full clean Gradle/Loom build, Minecraft/Mixin integration launch or server gameplay test was performed. No new GitHub Actions build was requested.**

## Still unresolved

Wind-chime entity mapping/rendering, campfire GUI/container/slot synchronization, and the optional GridPot positive/negative insertion-predicate conflict are not fixed by this revision. Integrating a native replacement is not evidence that these separate features now work.

## Installation

Close Minecraft. Remove the previous LegacyForgeBridge JAR and `lfb-visual-stack-hotfix-1.0.0.jar` from the Fabric 1.21.11 client's `mods` directory. Install only the new rev193 main bridge alongside the existing required dependencies. Keep the original legacy mod JARs in `old-mods`; do not replace them with a preconverted Bamboo mod. The changed converter fingerprint handles reconversion; follow any restart prompt.
