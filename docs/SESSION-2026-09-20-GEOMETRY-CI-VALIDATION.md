# Revision 141 CI artifact validation

Production commit: `db1144a34beda13941b51852e6ecefac9f05427d`.
Full build/test/remap: GitHub Actions run #634 (`35462416828`), success.

The unmodified CI-built main JAR was used for a second local resource-stage run against the
checksum-pinned original Bamboo 2.6.8.5 binary. No local production-class overlays were on the
classpath. All thirteen standalone geometry/source/schema checks passed using that JAR.
The original-source resource stages emitted 19 native geometry identities / 287 metadata
geometry entries, 162 materialized sprites and 823 rewritten models; no missing or ambiguous
owned sprite references remained. All 223 original PNG/animation entries were byte-identical.
This does not execute the complete semantic conversion pipeline or a live Minecraft renderer.

The original `BlockDSquare` half-height family has a fixed lower-half constructor box and no
state-dependent bounds callback. Its four metadata bits select source texture/orientation,
not upper-half placement. `BlockDecorations` and `BlockTwoDirections` have distinct bit-3
upper-half behavior. Production analysis already preserved that distinction. The external
corpus test initially assumed all three shared the upper-half format; this follow-up corrects
that test expectation rather than changing the production geometry to an invented rule.

Fresh resource-only output still contains 31 canonical placeholder models. This is a different
baseline from the prior 58-placeholder full-semantic candidate: do not present these numbers
as a full-renderer completion count. Specialized and unknown renderer limitations remain in
SESSION-2026-09-20-NATIVE-GEOMETRY.md. This follow-up changes tests/documentation only; runtime
revision, protocol mappings and rendering implementation remain .141.
