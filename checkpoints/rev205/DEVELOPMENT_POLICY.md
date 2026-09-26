# Development policy for this continuation

User instruction, 2026-09-27:

- Push completed source changes, tests, local build reports and known limitations to `feature/generic-conversion-iyamato-corpus3` in `ga200604101111/LegacyForgeBridge`.
- Do not dispatch GitHub Actions or intentionally trigger CI builds. Inspect workflow triggers before updating refs. Use `[skip ci] [skip actions]` in continuation commits; do not create PRs, tags, releases or push another build branch as a workaround.
- Use non-force updates and re-read branch HEAD before publishing. Preserve main, the Bamboo branch and concurrent work.
- Deliver one complete main LegacyForgeBridge JAR for local testing, not a renamed old build or a separate hotfix addon. State the actual build and test scope.
- Keep the Forge server, original source-mod JARs and old-mods unchanged.
- Derive item models, held presentation, use duration, start/release behavior, descriptions and properties from the original 1.7.10 implementation. No production mod-name, item-name, texture-name or NBT-key exceptions for iYAMATO.
- Genericity requires renamed-corpus tests and rejection cases, including custom renderers, event cancellation, external subscribers, missing evidence and unsupported effects. A successful icon or use-start adapter does not establish full gameplay compatibility.
- Preserve source checkpoints and publish unfinished boundaries honestly. Do not replace a source rendering path with an invented 3D gun model.
