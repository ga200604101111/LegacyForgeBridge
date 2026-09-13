# Development & CI Workflow

This document is an operational rule for LegacyForgeBridge development. Its purpose is to keep GitHub Actions useful and prevent one implementation task from producing a large queue of redundant builds.

## CI budget rule

For a normal feature or fix, the expected validation count is:

1. one pull-request build after the branch is ready for review;
2. one `main` build after merge.

Additional builds should happen only when the previous validation found a real defect that requires another code change.

## Required development sequence

1. Create a branch from the current `main`.
2. Inspect all relevant source, runtime logs, upstream implementations and compatibility constraints first.
3. Decide the complete file set that must change before writing to GitHub.
4. Batch related code, tests, resources and documentation together.
5. Prefer Git data operations (`create_blob` + one `create_tree` + one `create_commit`) for multi-file changes so one logical implementation becomes one branch commit.
6. Do **not** open the pull request while the implementation is still being assembled.
7. Open the pull request only after the branch head is ready for CI. Creating the PR should trigger the first real validation build.
8. Wait for that build result before pushing more changes.
9. If CI fails, diagnose the full failure first and batch the complete fix into one follow-up change instead of repeatedly pushing speculative edits.
10. Merge only after CI is green and any required live/runtime smoke test has passed.

## Important GitHub Actions behavior

The repository workflow runs on pushes to `main` and on pull-request activity. A development branch by itself does not need to trigger the normal build workflow.

Therefore the safest workflow is:

```text
branch
  -> assemble complete change
  -> tests/resources/docs complete
  -> open PR once
  -> PR CI once
  -> runtime validation if required
  -> merge
  -> main CI once
```

Avoid this pattern:

```text
open PR early
  -> push small file
  -> push another small file
  -> push generated resources in pieces
  -> push test
  -> push docs
  -> every synchronization creates/cancels another workflow run
```

`cancel-in-progress: true` reduces wasted runner time but does not make repeated PR synchronizations free: GitHub still creates workflow runs and they can accumulate in the queue.

## Large generated/resource updates

When adding many generated files or split resources:

- prepare the complete set first;
- create all blobs without moving the branch ref;
- create one tree containing every path;
- create one commit from that tree;
- update the branch ref once;
- open the PR only afterward.

Do not use many sequential `create_file` / `update_file` commits on an already-open pull request unless the task genuinely requires independent reviewable commits.

## Runtime-library Mixin rule

Compile success is not sufficient when a Mixin targets implementation classes inside an external runtime mod.

Before declaring a Mixin change complete:

1. verify the target method descriptor against the **exact runtime version** used by the client;
2. account for package relocation/shading performed by the runtime artifact;
3. do not put a compile-time library type in a callback descriptor when the runtime may relocate that type;
4. use `@Coerce Object` plus a narrow compatibility adapter when the target argument type is shaded/private but the object API is stable;
5. add a regression test that checks the compiled callback descriptor does not reintroduce the wrong package;
6. require at least one real client startup smoke test for new Mixins into ViaFabricPlus/ViaVersion internals.

### alpha.10 incident

`0.2.0-alpha.10` compiled successfully against the ViaFabricPlus API, but the live ViaFabricPlus 4.4.15 runtime relocates Gson from:

```text
com.google.gson.*
```

to:

```text
com.viaversion.viaversion.libs.gson.*
```

A Mixin callback that declared `com.google.gson.JsonObject` therefore had a different bytecode descriptor from the live target method and crashed during Mixin application.

The permanent rule is: **never bind the Via component-preservation callback descriptor to either Gson package.** Keep the callback argument as `@Coerce Object` and access only the small stable JSON surface through the LFB adapter.

## Merge policy for runtime-sensitive changes

For ordinary pure-Java logic, green CI can be sufficient to merge.

For changes involving any of the following, green CI is necessary but not sufficient:

- Mixin targets in ViaFabricPlus/ViaVersion;
- packet pipeline hooks;
- class-loader behavior;
- shaded/relocated runtime dependencies;
- Minecraft client bootstrap;
- resource reload integration.

These require a live startup or connection test against the exact supported runtime before merge whenever practical.
