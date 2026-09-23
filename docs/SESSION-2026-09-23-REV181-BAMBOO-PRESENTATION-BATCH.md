# rev181 — complete reported presentation batch before building

Base: `0505ffa979ec9d30db674a09bac41c2610e3eed7` (rev180).
Branch: `feature/generic-conversion-bamboo-corpus2`.

The user requires the whole reported batch, not another per-item JAR. All of the following
source changes and regression tests are prepared together before the full build.

## Reported failures and fixes

* Tray: `itemWatcherRange` started scanning at instruction 3 and missed the source getter's
  leading `ILOAD 1; ICONST_5; IF_ICMPGE` guard at instruction 2. Recognize the guard independently
  of the later three-operand watcher expression. The resulting five consecutive ItemStack
  watcher slots, nine cuboids, source texture and entity registration reach the runtime sidecar.
* Windmill and waterwheel: the common transform proof recognized floating literals but not
  javac's `BIPUSH 90; IMUL; I2F` for `dir * 90`. Admit this equivalent numeric constant while
  retaining all watcher, bounds, radial model, texture and client-roll gates. Both source-shape
  fixtures now emit their complete VARIABLE_Z_RADIAL / FLUID_X_RADIAL rules.
* Bamboo shoot / bamboo crossed and crop models: a geometry rule used for selection/collision
  caused the native geometry wrapper to replace the JSON mesh with the faces of a bounding box.
  Mark source-proven simple renderer geometry as model-owned, preserve the original bounds,
  and bypass only the replacement mesh. Do not replace custom plants with generic solid cubes.
* Bow pulling: finalize the texture atlas after combat, simple-block, liquid and connected
  model writers. Pull stages and metadata item-definition aliases now reference sprites that
  are actually materialized and registered in the final item atlas. Source stage timing is kept.
* Held-only light selection: the old selected AABB was an outline, not the bounds used by
  collisionRayTrace. Extract held/unheld literal `setBlockBounds` from the admitted mask branches.
  Modern picking reads the current context's held item, independent of stale metadata or a
  random display tick. Client outline queries with an empty context are scoped to the active
  client level. Physical collision remains separately source-proven and empty. Unknown bounds
  are not guessed; old sidecars remain readable without claiming this new selection proof.
* Arrows: retain rev180's direct vanilla ArrowRenderer / ArrowRenderState delegation, source
  entity textures, packet-angle interpolation and server-authoritative gameplay. No second
  client-side gameplay arrow is created.

There are no new Bamboo mod-ID, class-name or item-name switches in production code.
The revision is bumped so rev179/rev180 cached candidates are reprocessed through normal policy.

## Regression and evidence boundary

Normal (non-exact-corpus) tests now cover compiled unrelated-namespace source shapes for all
three formerly unadmitted entities, both integer and float yaw constants, absent source textures,
validated candidate sidecars, the head-of-method tray guard, selection proof and malformed bounds,
immediate hand switching, physical-vs-pick shape separation, native mesh ownership, every bow pull
stage and alias, and actual production pass ordering. Existing arrow regressions remain enabled.

A standalone offline Java harness exercised the pure analyzers and model/atlas integration before
submission. This is not a Minecraft runtime or a replacement for the full Gradle test suite.
Full build/test/remap results and the tested source revision are retained in the CI artifacts.

The checksum-pinned complete Bamboo 2.6.8.5 JAR is not present in this workspace. Public source
at `rubnsn/mcmod@843a7c7dc0532fed00a59e04fcab50736a2414ec` was used for semantic cross-checking.
Compiled source-shape fixtures do not constitute an exact-JAR conversion or a live client test.
Do not mark the user's reported failures live-verified merely because CI is green.

## Client acceptance

Replace only LegacyForgeBridge on the modern client, retain the original source mod, and let the
normal revision-aware conversion run. If it reports restart required, restart to load the new
managed candidate; a previously loaded JAR cannot be replaced in-place inside the running JVM.
Then check tray placement/items, bamboo models, wind/water assemblies, held/unheld selection,
all bow pull stages and arrow directions in the same session. No intermediate per-item release.
