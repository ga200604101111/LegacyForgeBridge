# Development constraints

- Work only on `feature/generic-conversion-iyamato-corpus3`. Do not force-push or modify main or the Bamboo branch.
- Do not trigger GitHub Actions. Do not dispatch workflows, open a PR, create tags/releases, or modify workflow definitions to obtain a build. Include `[skip ci] [skip actions]` in continuation commit messages and verify the resulting branch ref.
- The target is a Fabric 1.21.11 client connecting to an unchanged Forge 1.7.10 server. Keep original RPGTool, Bamboo and iYAMATO JARs and server state unchanged.
- Implement source-driven common conversion. Mod/weapon names and numeric IDs observed in logs are evidence, not production dispatch keys. Do not equate inheritance with rendering, behavior or lifecycle compatibility.
- Preserve source invisible rendering and server authority. Do not execute legacy server damage/explosion code on the client or broadly suppress legitimate movement updates to hide synchronization bugs.
- The user requires complete main JARs for actual installable updates, not auxiliary patches. Source-only progress must be labelled as such; never rename an old main or claim unexecuted tests or builds.
- The repository stores cumulative source checkpoints. Root src alone is not the latest delivered source. Verify source/base hashes and preserve newer changes before applying any delta.
- Record artifact SHA-256, exact build method, dependency provenance, real API/game validation versus test doubles, uncovered paths and regressions. A source analysis count is not a gameplay support count.
