# alpha.14 RPGTool live-test handoff

This session used the externally supplied exact `RPGTool1-1.1-1.7.10.jar` binary rather than a synthetic fixture.

```text
size: 14,556,748 bytes
sha256: b82cd54d2d2db576e82ba02ea4e55b4db92174c5814b45aa3ebbe1b44d98961d
classes: 53
modid: rpgtool1
version: 1.0
```

The binary was inspected with `jar`/`javap`. The alpha.14 branch reconstructs 71 registered items, strips obsolete Forge class files from the converted candidate, supplies modern item resources/translations, adds a 1.21.11 Wavefront OBJ special renderer for the 20 weapon models, and advertises the converted `rpgtool1=1.0` identity in the legacy FML Client ModList.

The test boundary remains intentionally explicit: gem gameplay, skill effects, recipes, wing movement/fall behavior, equipped wing/circle renderers, and any modded numeric registry packet gap exposed by live Via translation remain follow-up work rather than being silently declared complete.

The user-supplied third-party JAR is never committed to GitHub.
