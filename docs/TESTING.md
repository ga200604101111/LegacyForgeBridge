# Testing Strategy

LegacyForgeBridge must be tested beyond compilation. GitHub Actions is the primary regression environment for deterministic code paths, while real Minecraft client/server behavior remains an integration-test tier.

## Test tiers

### Tier A - unit tests (every push / pull request)

Fast deterministic tests that require no third-party mod JARs:

- FML handshake state transitions;
- mapping/rule lookup;
- dependency graph construction and SCC handling;
- cache-key invalidation;
- item-boundary policy;
- conversion scheduler determinism.

### Tier B - synthetic JAR integration tests (every push / pull request)

Tests generate small Forge-1.7.10-shaped JARs at runtime using ASM. This allows GitHub Actions to verify the real JAR analyzer without redistributing third-party mods.

Required coverage includes:

- `mcmod.info` detection;
- Forge/FML references;
- Minecraft references;
- CoreMod/`IClassTransformer` markers;
- legacy OpenGL markers;
- malformed/unreadable class handling;
- dependency metadata parsing.

### Tier C - real mod corpus

Representative real 1.7.10 mods are tracked by cryptographic hash and expected analysis/conversion results.

A corpus record contains at minimum:

```text
file name
SHA-256
size
known modid/version
expected class count
expected dependency metadata
expected feature markers
expected analyzer classification
conversion status
verified gameplay status
```

Third-party JAR binaries should not automatically be committed to the repository. Where redistribution/licensing is unclear, keep only the baseline record and run the binary from an authorized/private test source.

A changed source hash is a different corpus sample and must not silently reuse the old expected result.

### Tier D - conversion integration

Once conversion emits JARs, CI verifies:

```text
legacy JAR
 -> analyze
 -> dependency plan
 -> convert
 -> validate output structure
 -> inspect fabric.mod.json
 -> inspect converted classes/resources
 -> deterministic hash/manifests where applicable
```

Conversion success does not imply gameplay success.

### Tier E - Minecraft server/client integration

Used for protocol/session milestones:

1. launch a 1.7.10 vanilla test server;
2. verify 1.21.11 bridge connection behavior;
3. launch a clean Forge 1.7.10 test server;
4. verify FML handshake and PLAY-state behavior;
5. later add converted client mods and server mod sets.

Server-side tests are straightforward on GitHub-hosted Linux runners. Full graphical client tests may require a virtual display (for example Xvfb) or a purpose-built automated client harness. Passing headless protocol tests does not replace manual/automated rendering and gameplay validation.

## CI acceptance

The default `build` workflow runs `gradle build`; Gradle's `build` task includes the JUnit test suite. A failing regression test therefore blocks a green build.

## Real-corpus privacy/licensing

A private repository does not automatically grant redistribution rights for third-party mods. Prefer hash + baseline metadata unless the test JAR is redistributable or explicitly supplied for private fixture storage.
