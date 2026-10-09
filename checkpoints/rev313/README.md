# LegacyForgeBridge rev313 — complete client-side Twilight Forest critter visual follow-up

## Delivery and exact base

- **Complete main**: `legacyforgebridge-0.2.0-alpha.27-rev313-critter-animation-completion.jar`, SHA-256 `fe5c654109888720bb9fd51325c6f295d62120854e361b45e35a63eda1f851dc`, 5,277,146 bytes.
- Exact full base: `legacyforgebridge-0.2.0-alpha.27-rev312-shared-source-analysis.jar`, SHA-256 `ad433e14755d73b57da88d8c6f764a210a61cfffbdcecd52893dc1c1629ff99b`.
- **Full buildable checkpoint**: [`source_bundle.zip`](source_bundle.zip), SHA-256 `5e94cba1513d6726fe79cd027d879dc00bf60540b5721b3303646cecf13f324d`, 18,277 bytes, 14 files under `checkpoints/rev313/`. Extract at repository root to obtain `source/`, `tests/`, `tools/`, original README and verification manifest.
- Version: `0.2.0-alpha.27-corpus4-local.67-rev313-critter-visual-completion.1`. Original conversion cache semantic revision remains rev310; preserve the existing converted mod cache when valid.
- The original Forge Twilight Forest JAR, Fabric mod wrappers, server, config, and game logic have not been modified.

## Source-proven client visualization changes

1. **Six-part cicada geometry:** preserve the source ModelRenderer cuboids, pivots and 64×32 UV. The original thin wing has visible front and back via entity Cutout No Cull instead of rev311's one-sided Cutout.
2. **Cicada yaw:** retain independently timed block-entity yaw state. Add position-seeded source-style singing intervals (100-tick active song) and occasional note particles only during singing. Do not duplicate cicada audio; legacy server remains responsible for its sound events.
3. **Firefly geometry:** preserve the original three-body-parts plus a separate glow plane. The very thin glow plane now has a 1/256-pixel finite thickness instead of potentially degenerate 0 thickness.
4. **Firefly glow:** retain an independently pulsing alpha visual with about 0.05 amplitude increments per game tick. Client-only ambient small-firefly particles approximate the original tiny-firefly visual; **the old EntityTFTinyFirefly server/entity AI is not replicated**.
5. **Inventory:** the source 16×16 item icon still renders flat, preserved from rev310. No change to content models or material atlas.
6. **Diagnostics:** if the source-bound Twilight rule catalogue is empty, provide a warning instead of silently claiming the model is registered.

## Packaging and checks

- 6 replaced existing entries and 3 new Java class files; 2,026 original non-target rev312 ZIP entry payloads are byte-identical.
- ZIP integrity, uniqueness, rev312 cached-analysis code, no bundled ASM, consistent version metadata.
- Source Java compiled with Java 21 against the exact rev312 complete JAR using **temporary external Minecraft/Fabric API signature stubs** (not shipped); tests ran with the genuine ASM BasicVerifier from the host JDK for 11 touched/new classes and 54 methods.
- Pure ambience source-level test: 3,000,205 assertions; independent rebuilding yields a byte-identical JAR.
- **No real Fabric/Minecraft 1.21.11 client, original Forge 1.7.10 server, rendered model, projectile network, audio/particle or converted Twilight candidate was executed/inspected.** These are offline tests, not live-game acceptance.
- The source archive contains reproducible `tools/build_rev313.py`, `tools/package_rev313.py` and `tools/create_stubs.py`. Do not confuse stub-backed compile signatures with proof that Fabric Loader actually links the classes.
- Compatibility constraints: only `feature/generic-conversion-iyamato-corpus3`; no PR, Actions, tag, force-push, main/Bamboo changes or original-mod mutation.

## Install

Use the rev313 complete JAR as the **only** LegacyForgeBridge main in the Fabric 1.21.11 client `mods/`; keep the original Twilight Forest 1.7.10 source JAR in `old-mods/`. Retain a known-working rev312 rollback. For remaining missing geometry, particle issues or missing animations, collect the generated Twilight Forest `-lfb.jar` and `latest.log` for runtime inspection.
