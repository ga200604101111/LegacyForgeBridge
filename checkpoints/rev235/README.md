# rev235 — generic converted-stack ARMOR mirror

Target branch: `feature/generic-conversion-iyamato-corpus3`.

rev235 is a cumulative local binary overlay on the exact rev234 artifact.

## RPGTool source conclusion

Exact corpus:
- `RPGTool1-1.1-1.7.10.jar`
- SHA-256 `b82cd54d2d2db576e82ba02ea4e55b4db92174c5814b45aa3ebbe1b44d98961d`

Direct bytecode inspection proves:
- RPGTool creates custom ArmorMaterial `rpgtool1:spec`;
- defense array is `[3, 10, 5, 4]`;
- all `WingBase` armor uses legacy chest slot 1, so base armor = 10;
- all `CircleBase` armor uses legacy feet slot 3, so base armor = 4;
- neither `WingBase` nor `CircleBase` overrides `getItemAttributeModifiers / func_111205_h`;
- neither class provides a source `generic.movementSpeed` modifier.

The converted RPGTool candidate already materializes these values into real modern stack ARMOR modifiers:
- Wing = 10;
- Circle = 4.

## rev234 bug

rev234 required `LegacySourceItemRuntime.rule(itemId)` before reading the equipped stack. RPGTool does not need an iY-style source rule for these generated content items, so the runtime skipped the already-correct modern ARMOR modifiers.

## rev235 generic rule

No RPGTool/iY item-name special cases are used.

Eligibility:
- item identity must be present in the live Forge 1.7.10 `LegacyModItemRegistryMap`.

Primary source:
- read the equipped converted stack's actual 1.21.11 ARMOR modifiers and mirror them as temporary local-player entity ARMOR modifiers when Via targets 1.7.10.

Fallback:
- source-contract `armorPoints` is used only if an explicit stack component replaced the converted base armor carrier.

Safety:
- vanilla/high-version items are not mirrored;
- existing entity modifier IDs are not duplicated;
- temporary LFB runtime modifiers are removed when the converted equipment is removed;
- the unchanged 1.7.10 server remains authoritative.

## Movement speed rule

Unchanged from rev234 and remains mod-agnostic:
- player base 1.21.11 Movement Speed remains a player entity base attribute and is not shown as an equipment modifier;
- only source-proven 1.7.10 equipment movementSpeed modifiers are generated/displayed;
- stack/NBT-added movementSpeed remains visible;
- no per-mod visibility special case exists.

## Output

- file: `legacyforgebridge-0.2.0-alpha.27-rev235-corpus4-local.19.jar`
- bytes: `4,455,730`
- SHA-256: `98cf7a8c0ce39fe018d7ce0d7929d11079b87abb29577e1273da57e0c0d7b3c2`
- internal version: `0.2.0-alpha.27-corpus4-local.19-rev235-local-test.1`
- converter revision: `2026-10-01.235-generic-converted-stack-armor-mirror`
- cache compatibility unchanged: `0.2.0-alpha.27-corpus4-local.17-rev233-cache.1`

## Validation

- RPG Wing policy: 10 armor — pass
- RPG Circle policy: 4 armor — pass
- modern 8 + converted 10 = 18 — pass
- modern 8 + explicit NBT 2 + missing converted base fallback 10 = 20 — pass
- pure modern item not mirrored — pass
- Java 21 `-Xverify:all` class load — pass
- ZIP integrity — pass

Binary audit vs rev234:
- base entries: 1682
- output entries: 1683
- duplicates: 0
- removed: 0
- added: `legacyforgebridge/rev235-build.json`
- changed existing:
  - `BuildInfo.class`
  - `Rev234ArmorAttributeSync.class`
  - `Rev234ArmorAttributeSync$Wanted.class`
  - `fabric.mod.json`
- manifest unchanged
- nested Energy JAR unchanged, SHA-256 `072cd9fad2ec00c3b11b5862f34bb12f0554f727958ad685ac7b81b4fd79eac3`
- rev233 Bamboo liquid helper/mixin classes unchanged

No live Minecraft launch was available in the build environment. No Actions dispatch, PR, tag, release, workflow modification, force push, main-branch modification or Bamboo-branch modification was performed.
