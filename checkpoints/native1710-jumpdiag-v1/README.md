# Native Forge 1.7.10 jump diagnostic v1.0.0

This is a separate **client-only observation mod**, not part of the modern Fabric main JAR.

Delivered artifact: `lfb-native1710-jumpdiag-1.0.0.jar`  
SHA-256: `0ebb1f4f1371e888fd5481cbbde8262477ace2874794a3cc94cc2efbdb9ba52e`

Target: Minecraft 1.7.10 / Forge 10.13.4.1614 / Java 8.

It starts a timestamped capture automatically on client connection and closes it on disconnect/JVM shutdown. No command or user marker is required. Output is written under `logs/lfb-native1710/`.

Observed surfaces:
- Forge LivingJumpEvent at HIGHEST and LOWEST priority.
- Inbound S12PacketEntityVelocity before the vanilla packet_handler.
- Outbound C03PacketPlayer.
- Client tick START/END state.
- Ground transitions and movement input.
- Position, motion, collisions, velocityChanged, fallDistance and bounding box.

The diagnostic never cancels events or modifies velocity/movement packets. It does not need to be installed on the server.

Build validation: Java 8 classfile major 52, ZIP integrity, no test-double classes packaged. An explicit synthetic Forge/Minecraft/Netty runtime executed connect -> jump HIGHEST/LOWEST -> S12 inbound -> C03 outbound -> tick -> disconnect; 18 assertions passed. No real Minecraft launch was available in the build environment, so the first user launch remains runtime/API validation.

Source/test ZIP delivered in chat: `LFB-Native1710-JumpDiag-1.0.0-source-tests.zip`, SHA-256 `f21be7430d3f93254bc5eda2b7012919ebb620107a1d31901cc945f7de7c556f`.
