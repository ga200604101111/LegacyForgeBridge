# Session 2026-09-16 — Exact Bamboo corpus gate fixes

Branch: `feature/generic-conversion-bamboo-corpus2`

Exact corpus:

```text
Bamboo-2.6.8.5.jar
SHA-256 = bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

## Why this slice exists

The normal synthetic/build CI was green, but running the checksum-pinned whole Bamboo JAR exposed
three false-negative proof gates. None requires a Bamboo-specific production branch; each was an
analyzer provenance problem that can be fixed generically while preserving fail-closed behavior.

## 1. Raw TileEntity GUI handoff

Forge 1.7 `IGuiHandler#getClientGuiElement` is allowed to pass the generic result of
`World.getTileEntity(x,y,z)` directly to a GUI constructor whose formal parameter is `TileEntity`.
The GUI constructor may then narrow that value to its concrete source TileEntity class.

The previous processor presentation analyzer required the redundant `CHECKCAST` to appear in the
IGuiHandler switch case itself. Exact Bamboo MillStone does not emit that redundant cast, so its
otherwise-proven GUI was rejected.

The analyzer now requires instead:

- the requested GUI switch case;
- a direct `World.getTileEntity` / `func_147438_o` source in that case;
- the exact admitted `(InventoryPlayer, TileEntity)` GUI constructor descriptor;
- and, independently, the existing constructor proof that the GUI narrows to the exact source
  TileEntity before using/storing it.

This keeps the proof source-derived without demanding one particular javac bytecode layout.

## 2. Consecutive ClientRegistry TESR registrations

Both processor and oscillating-model presentation analyzers previously scanned a fixed number of
instructions before `ClientRegistry.registerTileEntity(...)` to recover:

- TileEntity class literal;
- client tile id;
- newly constructed renderer.

Exact Bamboo registers several TileEntity renderers consecutively. A fixed backward window can see
constants from the previous registration and falsely create multiple candidates.

`LegacyDirectCallArguments` now first proves the canonical call-local javac sequence immediately
before the target invocation:

```text
LDC TileEntity.class
LDC "client-id"
NEW Renderer
DUP
INVOKESPECIAL Renderer.<init>()V
INVOKESTATIC ClientRegistry.registerTileEntity(...)
```

Labels, line numbers and frame nodes may appear between those executable instructions, but no other
executable instruction may intervene. This local sequence cannot consume constants from a previous
registration. For equivalent direct shapes that are not canonical, the helper retains an ASM
`SourceInterpreter` frame proof as a fallback when complete verifier metadata is available.

The canonical proof is also important for synthetic analyzer fixtures: hand-built ASM test methods
do not necessarily carry production `maxStack`/frame metadata and therefore should not require a
successful verifier analysis when their direct push sequence is already exact and local.

Unknown/dataflow-heavy argument construction remains rejected.

## 3. Scoped Item.setHasSubtypes mutations

The silk-touch analyzer used one global `unscopedSubtypeMutation` gate. In the exact Bamboo JAR all
17 `setHasSubtypes(true)` calls are constructor-local mutations whose receiver is exactly `ALOAD 0`.
Several classes extend external vanilla Item families (`ItemFood`, `ItemBlock`, etc.), so the old
source-only hierarchy check could not prove that `this` was an Item and incorrectly poisoned the
silk stacked-item proof for every registered Block.

The invocation owner/descriptor already requires an Item receiver. Therefore an exact `ALOAD 0`
receiver in a non-static source method is object-scoped even when the first external superclass is
not bundled in the input JAR. A direct `NEW` receiver is likewise object-scoped.

This does **not** make custom ItemBlocks automatically safe. The existing per-registration custom
ItemBlock gate remains intact, so subtype/metadata behavior for a block that actually uses a custom
ItemBlock is still fail-closed unless separately compiled.

## Exact-corpus validation after the fixes

Targeted analyzer execution against the checksum-pinned Bamboo JAR proves:

```text
MillStone processor rules = 1
MillStone presentation = proven
GUI      = ruby/bamboo/gui/GuiMillStone
Renderer = ruby/bamboo/render/tileentity/RenderMillStone
Model    = ruby/bamboo/render/tileentity/ModelMillStone

Oscillating model rules = 1
Maneki presentation = proven
Client id = MManeki
Renderer  = ruby/bamboo/render/tileentity/RenderManeki
Model     = ruby/bamboo/render/tileentity/ModelManeki

Silk proofs = 63 blocks
eligibility complete = 5
stacked-item complete = 7
both complete = 1
fully proven block = bambooMoss, silk eligible, legacy damage 0
```

The change intentionally does not claim that all Bamboo block drops are complete. Blocks with custom
ItemBlocks, external superclass semantics, source overrides, metadata-dependent silk stacks, or any
other unresolved proof remain blocked.

## Regression coverage

- consecutive direct call arguments cannot cross-contaminate;
- canonical call-local proof works even for synthetic ASM methods without production verifier metadata;
- raw `World.getTileEntity` GUI handoff is accepted only when the GUI constructor itself performs
  the concrete narrowing proof;
- an unrelated constructor-local custom ItemBlock subtype mutation no longer invalidates default
  BlockItem silk proof globally;
- exact Bamboo silk proof counts are checksum-pinned in the dedicated `exact-corpus` task.

## Converter identity

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.50
```

The whole Bamboo candidate remains `PARTIAL` and non-installable until the wider runtime semantics,
legacy class dependency closure, loader safety, and client/server smoke gates are completed.
