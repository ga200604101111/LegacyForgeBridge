# Legacy runtime bridge — alpha.14

This slice continues the remaining work recorded on 2026-09-13. It adds complete packet framing and an installed-converted-mod dispatch boundary. It is **not** universal Forge gameplay conversion.

## Protocol coverage

| Channel / message | Implemented behavior | Deliberate boundary |
| --- | --- | --- |
| `FORGE` / 1 | Signed dimension ID + provider ID, connection-local mapping | Does not inject providers into modern global registries |
| `FORGE` / 2 | Fluid name/ID pairs; both old packets without defaults and newer packets with one default string per entry | Retains server identities, does not equate them to modern raw IDs |
| `FML` / 1 | OpenGui window/mod/GUI/position, manifest-authorized adapter, validated native menu and screen | A missing adapter is reported and the server window is closed; no blank GUI substitute |
| `FML` / 2 | Header, all seven 1.7.10 DataWatcher types, nullable legacy item/NBT framing, thrower ID, three **int** velocities divided by 8000, additional spawn bytes | Initial watcher/owner/mod data semantics belong to an explicit converted entity adapter |
| `FML` / 3 | Packet-position baseline update | Still never a direct teleport |

FML and FORGE discriminators are distinct from FML|HS. Dimensions and fluids are cleared on disconnect/new connection. Generation checks prevent delayed callbacks from a previous session applying to a new world. World-bound packets arriving during CONFIGURATION are validated and bounded to 256 packets / 1 MiB until PLAY. Individual payloads are capped at 1 MiB; fluid counts and dimension counts are bounded. Failed/truncated messages do not partially update the identity maps.

NBT inside a legacy ItemStack is preserved as bounded, length-delimited compressed bytes. This decoder deliberately does not inflate it or interpret its item ID as a modern registry index. An item adapter must use bounded NBT decoding and an explicit semantic item mapping.

## Installed converter runtime SPI

A fully converted and actually installed Fabric mod may declare an entrypoint:

```json
{"entrypoints":{"legacyforgebridge-runtime":["example.ConvertedRuntimeProvider"]}}
```

The class implements `dev.yinghuang.legacyforgebridge.runtime.LegacyRuntimeProvider`. Its embedded `legacyforgebridge/conversion-manifest.json` must have schema 1, `status: converted`, `installable: true`, and `mod.fabricId` equal to the installed provider's Fabric ID.

The manifest maps a case-sensitive `legacyModId:integer` key to an adapter identity:

```json
{
  "schemaVersion": 1,
  "status": "converted",
  "installable": true,
  "mod": {"fabricId":"example_port","logicalMods":[{"modid":"ExampleMod"}]},
  "registries": {
    "entities": {"ExampleMod:7":"example_port:beast"},
    "guis": {"ExampleMod:3":"example_port:bag"}
  }
}
```

Each target must have an explicitly registered entity or GUI adapter. Both categories and logical-mod ownership are validated before any binding is published. Missing factories, undeclared factories, cross-mod mapping claims and ownership collisions reject that provider atomically. Multiple entrypoints belonging to the same physical Fabric mod are gathered into one registration. Packets never name Java classes to instantiate. Files in `old-mods` or `legacy-cache/converted` are **not** loaded by this SPI.

An entity adapter constructs an entity in the supplied world, without inserting it. The bridge assigns network ID, position, rotation, head yaw, packet-position baseline and optional initial velocity. The adapter then translates legacy watcher indices, owner semantics and additional spawn bytes. Only after successful validation does the bridge insert the entity. A GUI adapter returns a native `AbstractContainerScreen` whose menu carries exactly the server's window ID.

The SPI does not synthesize adapters for arbitrary Forge entity classes. Later metadata updates, Via entity-tracker integration where necessary, container/item synchronization, custom dimensions and complete mod behavior still need their corresponding conversion rules and real connection tests.

## Direct translation-reference conversion

`legacy-translation-direct-references` runs after language alias generation. It rewrites only a string literal immediately consumed by:

```text
INVOKESTATIC net/minecraft/util/StatCollector.translateToLocal(String):String
INVOKESTATIC net/minecraft/util/StatCollector.func_74838_a(String):String
```

The literal must exist in the generated translation identity map. Arbitrary strings, registry names, other owners/methods, dynamic/formatted keys and control-flow-interrupted uses remain unchanged. This is a provable call-site rewrite, not a global string replacement. The pass preserves deterministic output and leaves all existing Forge/CoreMod/rendering/installability gates in place.

## Validation and current limits

Automated tests cover wire fixtures, every watcher type, int velocity framing, optional fluid defaults, malformed/truncated input, duplicate IDs, immutable snapshots, reconnect generations, manifest ownership, atomic provider registration, GUI window invariants, bytecode wiring and deterministic end-to-end translation-call rewriting.

A passing Gradle build is not an actual Minecraft connection test. Before merging runtime-sensitive changes, use Minecraft 1.21.11, Fabric Loader 0.19.3, ViaFabricPlus 4.4.15 and this alpha.14 build to check clean-server join, reconnect, disconnect and alpha.13 translation behavior. Actual converted entity/GUI behavior additionally requires a real installed adapter and matching legacy server mod.

RPGTool1 remains **PARTIAL / non-installable**. Its exact external JAR is not in this repository. No real-binary or gameplay success is inferred from synthetic fixtures. The new direct-call pass may cover matching sites when that binary is supplied; it does not migrate its Forge event/item APIs, dynamic translation references or GL11 renderer.

## Upstream protocol references

- [Forge 1.7.10 runtime discriminators](https://github.com/MinecraftForge/MinecraftForge/blob/1.7.10/src/main/java/net/minecraftforge/common/network/ForgeRuntimeCodec.java)
- [Forge dimension/fluid wire messages](https://github.com/MinecraftForge/MinecraftForge/blob/1.7.10/src/main/java/net/minecraftforge/common/network/ForgeMessage.java)
- [FML EntitySpawnMessage and OpenGui](https://github.com/MinecraftForge/FML/blob/1.7.10/src/main/java/cpw/mods/fml/common/network/internal/FMLMessage.java)
- [ViaLegacy 1.7.10 watcher types](https://github.com/ViaVersion/ViaLegacy/blob/main/src/main/java/net/raphimc/vialegacy/protocol/release/r1_7_6_10tor1_8/types/EntityDataTypes1_7_6.java)
