# 2026-09-18 — Processor GUI handler branch proof

## Scope

This slice follows converter revision 2026-09-18.127. Runtime-complete processors already have registration retirement, source-retirement readiness and a bounded TileEntity constructor replacement proof.

Converter revision: 2026-09-18.128.

This revision proves the exact legacy IGuiHandler server/client branches for a processor GUI id. It does not mutate the shared handler yet.

## Exact handler pair

LegacySingleInputProcessorGuiHandlerAnalyzer accepts a handler only when the same source class implements the legacy IGuiHandler contract and owns both:

- getServerGuiElement;
- getClientGuiElement.

For each method the processor guiId must be dispatched by a TABLESWITCH or LOOKUPSWITCH whose operand is the handler id parameter. The target label must be unique to that guiId.

The admitted branch is straight-line and must end in ARETURN. Branches containing nested jumps or switches remain outside this proof.

## Operand-level TileEntity handoff

Both server and client branches reuse LegacyGuiTileHandoff.fromWorld.

The actual TileEntity constructor operand must therefore trace to the handler's World and x/y/z parameters through World#getTileEntity, rather than merely having a matching lookup somewhere in the case.

The server branch must construct exactly one source Container subclass.

The client branch must construct exactly the sourceGuiClass that was independently admitted by the processor presentation proof.

The proof records:

- handlerClass;
- sourceContainerClass;
- sourceGuiClass;
- server/client method identities;
- TABLE/LOOKUP switch kind;
- unique-case-label proof;
- simple case-flow proof;
- TileEntity handoff proof;
- other-case counts.

A unique server/client pair in the same handler is required.

## Sidecar and readiness

LegacySingleInputProcessorGuiHandlerProofPass writes:

legacyforgebridge/single-input-processor-gui-handler-proof.json

The pass consumes only runtimeComplete=true processors with sourcePresentationComplete=true and an already-proven presentation.sourceGuiClass.

No source byte is mutated. The root therefore states sourceBranchMutationWired=false and sourceClassDeletionWired=false.

LegacySingleInputProcessorRetirementReadiness now distinguishes two states:

- proof incomplete: processor-gui-handler-branch-proof-incomplete;
- proof complete but mutation absent: processor-gui-handler-branch-strip-not-wired.

The readiness rule also carries handlerClass, sourceGuiClass and sourceContainerClass when proven.

## Why the whole handler is not retired

Forge 1.7 mods frequently share one IGuiHandler across multiple GUI ids. Removing its registration or class would risk unrelated screens.

The next mutation slice will target only the proven processor guiId branches and preserve every other switch case. The handler itself remains outside the processor retirement cohort unless later evidence proves it dedicated.

## Regression

Normal CI includes a foreign-namespace handler with matching server/client guiId cases. Both branches must use the same handler, unique switch labels and operand-level World(x,y,z) TileEntity handoff.

A second fixture maps two ids to the same target label and must fail closed.

The exact Bamboo MillStone regression reads the same sidecar by sourceBlockClass. When the real branch pair is proven, readiness must replace the proof blocker with branch-strip-not-wired. If proof fails, explicit proof blockers remain.

## Next boundary

Revision .128 authorizes no deletion.

The next safe slice is a processor GUI-case mutation pass that:

1. freshly re-proves handler owner, guiId and server/client method identities;
2. requires the target label to remain unique;
3. replaces only the target branch body with a null return while preserving other ids;
4. reparses the rewritten class and proves the processor branch no longer references the source TileEntity/Container/Gui;
5. restores original bytes on any post-rewrite failure.

Only after that mutation should readiness clear the GUI gate and expand the processor presentation retirement cohort.
