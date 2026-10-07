# rev264 Twilight Forest recipe checkpoint — generic cloning IRecipe proof

Date: 2026-10-07

## Scope

This checkpoint adds `LegacyCloningRecipeAnalyzer`, a non-executing source-bytecode proof for the
legacy recipe family:

`one filled item + one-or-more blank items -> blankCount + 1 filled copies`

The family preserves the filled stack's legacy metadata and may copy its custom display name.

Admission does not use `TFMapCloningRecipe`, Twilight Forest package names, registry names, or map
item names. Constructor field names are also irrelevant. The renamed synthetic regression proves
that a structurally equivalent foreign implementation is admitted while a changed `blankCount+2`
output is rejected.

## Twilight Forest 2.3.8 target

The source corpus has three registrations with this behavior:

- magic map + empty magic map
- maze map + empty maze map
- ore map + empty ore map

They are registered through `GameRegistry.addRecipe(IRecipe)`, which the ordinary recipe analyzer
intentionally did not treat as shaped/shapeless JSON.

## Status

This commit is Part 1C-2a only: semantic admission/provenance.

The runtime 1.21.11 custom recipe serializer and candidate recipe JSON materialization are Part
1C-2b. Until that is added, these rules are proven but not claimed runnable in the converted
candidate.

The older rev260 local-overlay source gap remains separate and is documented in
`docs/SESSION-2026-10-07-REV260-REV262-HANDOFF-GAP.md`.
