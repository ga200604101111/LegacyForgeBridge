# RPGTool alpha.14 live test

Use the exact converted RPGTool candidate together with `LegacyForgeBridge 0.2.0-alpha.14` and ViaFabricPlus 4.4.15.

Do not put the original Forge 1.7.10 RPGTool JAR in the modern `mods` directory. The original may remain in `old-mods` as conversion input; the converted candidate belongs in `mods`.

Initial validation order:

1. client reaches title screen without Fabric/Mixin/resource errors;
2. converted `rpgtool1` appears loaded;
3. 71 converted item registry entries initialize;
4. item names/icons display under zh_tw;
5. a converted weapon renders through the OBJ special renderer in hand;
6. Forge 1.7.10 connection advertises `rpgtool1=1.0` and reaches FML COMPLETE;
7. inspect `legacyforgebridge.log` for server registry IDs and any Via modded-item translation gap.

Expected first-slice limitations are tracked in the corpus document and should not be confused with loader/registry failures.
