# Local source checkpoint: rev191

This checkpoint persists the complete ordered source deltas for **rev189, rev190 and rev191**, including previously unpushed work. It is not a claim that the repository's main `src/` working tree has already been integrated or that CI has passed. The main source tree outside this checkpoint remains based on `471db9187a8b17c4cd02a7749c8a09395104e03a` (rev188).

The four `source-delta.json.xz.*` files are ordered binary pieces of one XZ-compressed UTF-8 JSON payload. They contain only source patch text and the base commit, not original Bamboo/RPGTool binaries, Minecraft classes, build dependencies or credentials.

## Restore before continuing development

Run `python checkpoints/rev191/extract.py --output /path/to/new/patches`. The script verifies the complete archive SHA-256 before extracting three patches. Review and apply them in revision order against an isolated worktree based on `471db9187a8b17c4cd02a7749c8a09395104e03a`. Keep the existing working tree intact if patch application reports conflicts. Do not infer that reading the old root BuildInfo describes the latest delivered local binary.

Archive SHA-256: `e11c923af2812601508d656596662e711176b0bca71d276676e6379bb7180552`.

## rev191 changes

- Prove absent creative-tab membership through constructor inheritance and explicit null setters; unknown branches/helpers remain unknown instead of being hidden by item name.
- Preserve all source registry identities while removing source-hidden world-only members from creative membership. Exact Bamboo output keeps 63 blocks and 37 standalone items; ten hidden identities are excluded from the synthetic fallback tab.
- Recover unconditional null collision boxes independently of renderer admission. Keep selection and rendering geometry separate from collision. Do not infer collision from opacity or use Bamboo-specific names in the implementation.
- Guard only the proven ViaFabricPlus merged `canPlace1_12_2` material predicate: replace `material.equals("decoration")` with null-safe `Objects.equals(material, "decoration")`. Preserve all non-null placement logic and do not grant placement unconditionally.
- Advance runtime converter fingerprint to `2026-09-24.191-creative-admission-and-placement-safety`.

## Verification boundaries

Delivered bridge: `legacyforgebridge-0.2.0-alpha.27-rev191.jar`.
SHA-256: `78df1a62026a248ea7612afa390f3018d5bf378f66976f9e7b16a052dee68057`.
Base bridge rev190 SHA-256: `465eff2caf8448535d0719cb45d9f86856499545dd80ab3b63c56855dccc2b85`.

Local incremental build only, using compile-only API declarations and guarded bytecode changes against that exact base. Compile-only declarations and third-party libraries are not packaged in the bridge. No GitHub Actions were triggered. No full Gradle clean build or Minecraft gameplay validation was performed.

Verified: 44 targeted assertions against the delivered JAR, 32 changed class files / 270 methods checked with ASM BasicVerifier, selected 15-pass Bamboo conversion pipeline, identical JAR bytes on two local builds, and 111 renderer class entries unchanged from rev190. The selected conversion pipeline still reports PARTIAL; it is not a full compatibility certification.

The user still converts their original mods locally via `old-mods`; do not deliver a preconverted Bamboo mod as the fix.
