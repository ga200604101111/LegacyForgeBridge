# rev262 Twilight Forest generic Block CreativeTab proof

Status: local handoff checkpoint, not yet promoted into `src/main`.

Base runtime JAR: `legacyforgebridge-0.2.0-alpha.27-rev260-handoff-fix.jar`.
Result runtime version: `0.2.0-alpha.27-corpus4-local.45-rev262-tf-creative.1`.
Converter revision: `2026-10-07.262-block-creative-allocation-proof`.

## Scope

Adds allocation-specific, fail-closed CreativeTab provenance for registered legacy Forge 1.7.10 Blocks.
The proof follows the actual registered block allocation, constructor constants, constructor/super-constructor control flow, and `setCreativeTab` / SRG equivalents. It does not contain Twilight Forest registry-name or class allowlists.

The runtime handoff injects `GenericBlockCreativeMembership.complete(...)` at the end of the existing rev260 `Corpus3GenericCompletionPass.State.scanDirectCreativeMembership(...)` method. This is retained as patch tooling because the rev260 `Corpus3GenericCompletionPass.java` production source is not present on the GitHub branch yet.

## Twilight Forest 2.3.8 corpus result

Registered blocks: 61.

- Proven CreativeTab members: 57
- Proven hidden: 4
- Unknown: 0

The four hidden registrations are the source-expected portal, trophy, huge gloom block, and lit cinder furnace. `BlockTFCinderFurnace(Boolean.FALSE)` resolves to the Twilight Forest tab while the `Boolean.TRUE` allocation remains hidden, proving allocation-specific rather than implementation-class-wide behavior.

## Exact source snapshot

`GenericBlockCreativeMembership.java.gz.b64` is a deterministic gzip (mtime=0) + base64 snapshot of the exact Java source used for the local rev262 build.

Expected restored source SHA-256:

`dfc9c3eefef420f6ec035641ba1cbac9a6916ad79656d4f1f392a2594c5be97a`

Restore with:

```bash
base64 -d GenericBlockCreativeMembership.java.gz.b64 | gzip -d > GenericBlockCreativeMembership.java
sha256sum GenericBlockCreativeMembership.java
```

## Why this is stored under diagnostics

The user-supplied rev260 JAR contains production classes whose Java sources are newer than the current `feature/generic-conversion-iyamato-corpus3` branch. Promoting this patch directly into `src/main` before those rev256-rev260 sources are recovered would create an incomplete source baseline. This checkpoint preserves the exact new source and injection logic without pretending the branch can fully reproduce rev260/rev262 yet.

No GitHub Actions run is claimed. The rev262 JAR was a local Java 21 / audited bytecode overlay, not a full Gradle/Loom build.
