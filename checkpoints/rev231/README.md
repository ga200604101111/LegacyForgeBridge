# rev231 — JVM StackMap verifier repair

Target branch: `feature/generic-conversion-iyamato-corpus3`.

rev231 is a cumulative local binary overlay on the exact rev230 artifact. It retains rev230's legacy-NBT armor merge/strip behavior and every earlier Via/Bamboo/iY projectile, particle, armor and Cloth Config repair.

## Live failure

The user-side Minecraft 1.21.11 launch with rev230 failed during converted-mod initialization with:

`java.lang.VerifyError: Expecting a stackmap frame at branch target 210`

The rejected method was:

`dev/yinghuang/legacyforgebridge/compat/LegacySourceItemRuntime.configure(Ljava/lang/String;Lnet/minecraft/class_1792$class_1793;)V`

The rev228/rev229 armor-attribute bytecode overlay introduced branches to the movement-speed join point at bytecode offset 210 but did not materialize the required StackMap frame. ASM BasicVerifier did not catch this classfile-attribute defect; the Java 21 verifier did.

## Repair

rev231 inserts one full StackMap frame at the existing movement-speed join target. It does **not** change the executable instructions or the rev230 armor/NBT semantics.

Frame locals at the join:
- String item id
- Item.Properties
- LegacySourceItemContract.Rule
- EquipmentSlot
- ItemAttributeModifiers.Builder
- int modifier index
- Iterator
- LegacySourceItemContract.Modifier

The rev230 behavior is therefore retained:
- base legacy armor remains a hidden modern HUD carrier;
- explicit legacy NBT AttributeModifiers remain visible and are merged with the hidden armor carrier on the client;
- only the LFB-owned carrier is stripped before the stack is downgraded back to the unchanged 1.7.10 server;
- no synthetic base-armor tooltip line is reintroduced.

## Output

- file: `legacyforgebridge-0.2.0-alpha.27-rev231-corpus4-local.15.jar`
- bytes: `4,436,658`
- SHA-256: `7c4011556aaf0376fdb3b99c0694adfac1592a831e13c420f189e3eda2704189`
- internal version: `0.2.0-alpha.27-corpus4-local.15-rev231-local-test.1`
- converter revision: `2026-10-01.231-stackmap-verifier-fix`
- cache compatibility: unchanged: `0.2.0-alpha.27-corpus4-local.11-rev227-cache.1`

## Binary audit vs exact rev230 base

- base entries: 1673
- output entries: 1674
- duplicate entries: 0
- removed entries: 0
- added: `legacyforgebridge/rev231-build.json`
- changed existing:
  - `dev/yinghuang/legacyforgebridge/BuildInfo.class`
  - `dev/yinghuang/legacyforgebridge/compat/LegacySourceItemRuntime.class`
  - `fabric.mod.json`
- `META-INF/MANIFEST.MF`: byte-for-byte unchanged
- nested `META-INF/jars/energy-4.2.0.jar`: byte-for-byte unchanged, SHA-256 `072cd9fad2ec00c3b11b5862f34bb12f0554f727958ad685ac7b81b4fd79eac3`
- ZIP integrity: pass

## Verification

- `javap -v`: confirms a full StackMap frame at the formerly missing movement-speed join target
- Java 21 `-Xverify:all`: **pass**, directly loading `LegacySourceItemRuntime` from the finished rev231 JAR
- no semantic instruction changes in `configure()`; frame metadata only
- live Minecraft launch: not available in the build environment, so the final full-client check remains user-side

No Actions dispatch, PR, tag, release, workflow modification, force push, main-branch modification or Bamboo-branch modification was performed.
