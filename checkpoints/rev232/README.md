# rev232 — recomputed StackMap frames for LegacySourceItemRuntime

Target branch: `feature/generic-conversion-iyamato-corpus3`.

rev232 is a cumulative local binary overlay on the exact rev231 artifact. It retains rev230's legacy-NBT armor merge/strip behavior and every earlier Via/Bamboo/iY projectile, particle, armor and Cloth Config repair.

## Live rev231 failure

The user-side Minecraft 1.21.11 launch with rev231 no longer reached the previous JVM `VerifyError`, but Mixin failed while parsing `LegacySourceItemRuntime.class`:

`java.lang.ArrayIndexOutOfBoundsException: Index 11 out of bounds for length 11`

The failure path was ASM `ClassReader.readVerificationTypeInfo -> readStackMapFrame -> readCode` during Mixin transformation. The manually inserted rev231 full frame therefore produced StackMap metadata that the live ASM 9.10.1 parser rejected.

## rev232 repair

Do not patch rev231's malformed frame in place.

rev232 takes the executable `LegacySourceItemRuntime` bytecode from rev230, discards all existing StackMap frames with ASM `SKIP_FRAMES`, and rewrites the complete class using `COMPUTE_FRAMES | COMPUTE_MAXS`.

The `configure()` executable-instruction fingerprint is identical before and after the rewrite:

`51271d5c0be42349113cfb6fe939bd9f2ccbb8cefc324a21134899076826d00b`

Only class verification metadata is regenerated. rev230/rev231 armor semantics are unchanged.

The new `configure()` StackMapTable contains 19 entries and includes an automatically generated frame at the former offset-210 join point.

## Preserved armor/NBT behavior

- legacy base armor remains one hidden modern `ARMOR` HUD carrier;
- explicit legacy NBT AttributeModifiers remain visible;
- when explicit legacy NBT attributes replace the modern item-default component, the hidden base-armor carrier is merged back client-side;
- only the LFB-owned compatibility carrier is stripped before Via downgrades the stack to the unchanged Forge 1.7.10 server;
- iY knockback resistance remains visible;
- no synthetic visible base-armor tooltip line is added.

## Output

- file: `legacyforgebridge-0.2.0-alpha.27-rev232-corpus4-local.16.jar`
- bytes: `4,435,350`
- SHA-256: `d07ebbacf1556c1e0945f29e9ecde7d245c1bcc1c27acc5343b6249e7b346528`
- internal version: `0.2.0-alpha.27-corpus4-local.16-rev232-local-test.1`
- converter revision: `2026-10-01.232-recomputed-stackmap-frames`
- cache compatibility: unchanged: `0.2.0-alpha.27-corpus4-local.11-rev227-cache.1`

## Binary audit vs exact rev231 base

- base entries: 1674
- output entries: 1675
- duplicate entries: 0
- removed entries: 0
- added: `legacyforgebridge/rev232-build.json`
- changed existing:
  - `dev/yinghuang/legacyforgebridge/BuildInfo.class`
  - `dev/yinghuang/legacyforgebridge/compat/LegacySourceItemRuntime.class`
  - `fabric.mod.json`
- `META-INF/MANIFEST.MF`: byte-for-byte unchanged
- nested `META-INF/jars/energy-4.2.0.jar`: byte-for-byte unchanged
- nested Energy SHA-256: `072cd9fad2ec00c3b11b5862f34bb12f0554f727958ad685ac7b81b4fd79eac3`

## Validation

- ZIP integrity: pass
- JDK ASM tree parse: pass
- ASM BasicVerifier over every method in the regenerated class: pass
- `javap -v` StackMapTable parse: pass
- Java 21 `-Xverify:all` class load from the finished JAR: pass
- exact external ASM 9.10.1 / full Mixin live parser was not available in the build container
- live Minecraft launch was not available in the build environment; final end-to-end validation remains user-side

No Actions dispatch, PR, tag, release, workflow modification, force push, main-branch modification or Bamboo-branch modification was performed.
