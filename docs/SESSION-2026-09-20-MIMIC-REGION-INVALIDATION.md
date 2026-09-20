# 2026-09-20 — Bounded mimic reads and cross-section refresh

Converter revision: `2026-09-20.142`. Parent: `fd75cadf77bd25ab7f46ff6fb887412921737f18`.

## Correctness gap

Revision .141 bounded a directional material walk to 256 inspected positions but did not bound
its world reads to the renderer's snapshot. A traversal budget is not a spatial array-index
guard. The supported vanilla 1.21.11 RenderChunkRegion is a three-by-three-by-three section
snapshot; its X, Y and Z bounds all matter. Normal immediate block rerenders also do not cover
every owner section that can read a changed terminal or intermediate block.

## Implementation

* LegacyMimicResolver retains its existing public overload and adds a read predicate. The
  predicate runs before every direction/world callback, including the first and terminal
  positions. Null, unresolved, cyclic, out-of-window and over-budget paths retain the existing
  fallback behavior. No exception-based retry reads the live world.
* LegacyMimicReadWindow grants the wider section halo only to vanilla RenderChunkRegion.
  Other BlockAndTintGetter implementations get a conservative immediate-neighbor allowance,
  not an assumption that their backing arrays match vanilla. Arithmetic uses long bounds and
  floor-aligned sections, including negative coordinates. This is a renderer safety limit,
  not a reconstruction of the original mod's configurable recursion distance.
* LegacyMimicRefreshQueue uses the inverse of that read window: a changed source section can
  invalidate the 27 neighboring owner sections. This includes first-time builds and paths
  currently falling back because their source is air or unresolved. A worker-populated reverse
  dependency cache was deliberately not used: first-build/snapshot timing can otherwise miss
  the first update before the dependency is registered.
* Client dirty-state and listener-notification tails enqueue refreshes without cancelling any
  vanilla behavior. Chunk load, replacement notifications and unload events enqueue the whole
  source column's section halo. End-of-client-tick draining deduplicates requests and calls the
  native LevelRenderer on the client thread. Old-world callbacks are rejected; changing worlds
  or disconnecting clears queued positions. No block states or sprites are stored in the queue.
* The queue has an 8192-section limit. Overflow requests one full renderer refresh for the batch
  rather than silently dropping an affected section or growing an unbounded cache. Normal
  updates do not use a full refresh. No mimic rules means no additional refresh requests.

This is intentionally conservative: with mimic rules installed, nearby sections without mimic
blocks may also be rebuilt. Tick coalescing limits duplicate work, but frame-time performance
under heavy block updates still needs live profiling. No precise per-block dependency cache or
claim of zero rendering overhead is made.

## Preserved boundaries

No registry IDs, raw metadata codecs, Via packet hooks, source geometry admission, candidate
models, original textures, collision shapes, or neighbor interaction semantics were changed.
No Bamboo identity/class-name special case was introduced. Converter revision changes ensure
normal cache invalidation; loader-safe policy is unchanged. This is not a standalone texture mod.

Chains that leave the permitted snapshot still fall back to the mimic's own material. This
revision does NOT implement unrestricted long-distance material copying, arbitrary custom
renderer view equivalence, complex multi-layer neighbor models, block-entity-dependent tint,
pressure plates, or forwarded block interactions. Resource reload continues to use the existing
model-cache clearing and vanilla rebuild path; pending coordinate-only refreshes are harmless.

## Validation and evidence boundary

Local JDK 21: all 22 standalone production-logic checks passed. They also have ordinary JUnit
wrappers, rather than being confined to a manual script. Coverage includes six directions,
null/unresolved/cyclic chains, exact 256-node budget, guarded terminals, all spatial axes,
negative/extreme coordinates, unknown-view limits, cross-section terminals, inverse-window
coverage, request coalescing, chunk columns, bounded overflow, reset/drain and concurrent producers.

Four additional normal JUnit/ASM wiring checks pin exact Minecraft target descriptors, public
renderer refresh methods, non-cancelling tail callbacks, the guarded render call and mixin config.
These require the normal Gradle/CI Minecraft classpath; they were not run in the local environment,
which has no Gradle distribution or Minecraft runtime. The branch's existing push workflow runs
full build/tests/remap for this atomic change. Its result must be checked separately; this document
does not pre-declare CI success.

No exact Bamboo input JAR or live Minecraft client was available in this workspace. There is no
new exact-corpus conversion count and no live visual/performance certification. The .141 CI
artifact was used to inspect the baseline; this is not a fresh .142 build artifact.

Live acceptance: inside the supported snapshot, change a copied terminal to another textured
block and then air; change an intermediate mimic direction; repeat across both horizontal and
vertical section boundaries and negative coordinates. Check chunk unload/reload, dimension
changes, resource reload and a deliberately out-of-window chain. Confirm no stale material,
no crash, preserved fallback, unchanged placement identity and acceptable frame-time behavior.

Primary API references inspected for the exact Minecraft version:

- Fabric Yarn 1.21.11+build.4 ChunkRendererRegion and constant-values documentation
  (`SIDE_LENGTH_CHUNKS = 3`, padding constant = 1, three section-coordinate origins).
- Fabric Yarn 1.21.11+build.4 ClientWorld / WorldRenderer method descriptors.
- Fabric client lifecycle event APIs; exact dependency signatures are compiled and checked in CI.
