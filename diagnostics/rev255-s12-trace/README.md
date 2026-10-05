# rev255 /lfbtrace S12 timing + FPS

Reviewable production source mirror for the binary overlay on the exact delivered rev254 candidate. The full offline build/test kit is delivered in the conversation as LFB-rev255-source-test-kit-20261005.zip; this directory is not added to the main Gradle source set.

Artifact: legacyforgebridge-0.2.0-alpha.27-rev255-s12-trace-fps-test.jar
SHA-256: 11928e03f5e7c5801ebd311a49f887931ffb0a2bc4195f228f06c2f1bcfbbd28
Base SHA-256: 10f1192470300a87195a2cf50faca12ca3efe767049e96d41b188d80497c2f22

Commands: /lfbtrace [status|fps|start|stop|reset|export|watch on/off|fasts12 on/off]. Fast changes apply immediately for this game process; normal S12 is never disabled. ON/OFF samples are kept separately, mode-transition samples excluded from A/B distributions. Metrics are enabled by default in bounded 1024-entry rings; no continuous frame/packet text logging. Export writes snapshots into logs/lfb-s12/ on one bounded export worker.

Measurements: same packet object enqueue-entry to main-thread handler-entry wait, handler duration, pre/post/requested Y velocity, ground-before, reported Minecraft FPS and previous render-entry interval. Via transformClientbound times are separate invocation samples and MUST NOT be paired to packet rows by index. None is network RTT, server-origin age, proof of saved latency, or proof of fixing jumping. Missing evidence is N/A.

Preserves Bamboo, saplings, assets, source jump/landing and unrelated gameplay. Replaces only rev254 FastS12, batcher and velocity observer classes plus metadata; adds rev255 code and mixins.

Executed: 67 synthetic runtime assertions on produced classes with -Xverify:all; ASM BasicVerifier 19 classes/175 methods; 10 annotation checks and 2 original-call wrapper checks; 1467 unrelated gameplay/resource entries unchanged; same-environment byte-identical rebuild. Compile-only ABI declarations are NOT packaged.

NOT executed: real Minecraft/Fabric startup, actual Sponge transformation, real Brigadier parsing, real Via translation/network or GPU. Keep the candidate label.

Commit uses [skip ci] [skip actions]; no workflows modified.
