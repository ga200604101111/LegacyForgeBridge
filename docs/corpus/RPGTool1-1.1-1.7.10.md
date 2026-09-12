# RPGTool1-1.1-1.7.10 corpus baseline

This is a real-mod analyzer baseline recorded from an externally supplied test JAR. The third-party binary is intentionally not committed here by default.

## Identity

```text
file: RPGTool1-1.1-1.7.10.jar
size: 14,556,748 bytes
SHA-256: b82cd54d2d2db576e82ba02ea4e55b4db92174c5814b45aa3ebbe1b44d98961d
modid: rpgtool1
@Mod name: RPGTool1
@Mod version: 1.0
mcmod.info mcversion: 1.7.10
mcmod.info dependencies: []
```

Note: the filename says `1.1`, while the embedded `@Mod` / `mcmod.info` metadata reports version `1.0`. Compatibility logic must trust parsed metadata and preserve filename information separately rather than assuming they are identical.

## v0.1 LegacyJarAnalyzer baseline

Expected result for the exact SHA-256 above:

```text
classCount: 53
unreadableClasses: 0
hasMcmodInfo: true
hasManifest: true
likelyForgeMod: true
forgeReferenceCount: 29
minecraftReferenceCount: 104
coremodReferenceCount: 0
openglReferenceCount: 1
requiresManualCoremodReview: false
```

`openglReferenceCount` currently counts unique detected OpenGL marker identities, not the number of classes or call sites. The detected marker is:

```text
org/lwjgl/opengl/GL11
```

## Important detected Forge/API families

The sample contains references including:

- `cpw.mods.fml.common.Mod` / FML lifecycle events;
- `cpw.mods.fml.common.SidedProxy`;
- `cpw.mods.fml.common.registry.GameRegistry`;
- `MinecraftForge.EVENT_BUS`;
- Forge living events;
- `MinecraftForgeClient`;
- `IItemRenderer`;
- `AdvancedModelLoader` / `IModelCustom`;
- `EnumHelper`;
- direct `org.lwjgl.opengl.GL11` rendering.

No `IFMLLoadingPlugin` or `IClassTransformer` marker was detected by the v0.1 analyzer.

## Why this is useful

This sample is intentionally more difficult than a trivial item-only mod. It combines ordinary content registration/event behavior with a substantial legacy custom-rendering surface. It is therefore useful for validating that LegacyForgeBridge distinguishes:

```text
basic Forge/content behavior
from
legacy rendering behavior that needs semantic migration
```

It must not be reported as fully convertible merely because analysis succeeds.

## Current status

```text
analysis: PASS
conversion: NOT IMPLEMENTED / NOT YET VERIFIED
gameplay on 1.21.11: NOT VERIFIED
```

Any future analyzer change that alters these values must either explain the intentional classification improvement or be treated as a regression candidate.
