# LegacyForgeBridge

Experimental Fabric 1.21.11 client compatibility/conversion layer for Forge 1.7.10 mods. The original server owns gameplay state. Keep original JARs in `old-mods`, not modern Fabric `mods`.

## Latest continuation: rev205 — source-held presentation and event-gated hold use

This feature branch now saves the previously conversation-only rev202–204 source kits and the rev205 continuation. The new work corrects the vanilla ItemBow full3D inheritance assumption, preserves original sprite models, and supports ArrowNockEvent control flow in the generic use-start compiler. Cross-mod source audits guard the default uncancelled event outcome; unknown listeners and missing audits are not bypassed.

The supplied iYAMATO corpus has 99 audited registered items, 95 property records, 99 icon outputs, 12 armor assets, 57 full3D grips, six inherited bow sprite models, 29 use-start programs (previously 15), 35 action programs and six creative-NBT programs. These are separate analysis/output counts, NOT a claim of universal weapon gameplay. Renamed-corpus and rejection tests are included. RPGTool's existing 71 icon tables remain unchanged.

A complete local incremental main JAR was produced: `legacyforgebridge-0.2.0-alpha.27-rev205-local-test.1.jar`, 3,990,189 bytes, SHA-256 `3ce19e3a6d9b4788251aae95edd0c179c93bc44f4d5f65fba054be67ce4461de`. Two fresh work directories reproduced identical output; 3,597 assertions passed. rev202–204 were also rebuilt with outputs identical to their previous deliveries.

**Not a clean Gradle/Loom build or a real Minecraft/Fabric/Mixin launch.** Runtime tests use explicit recording API hosts; those declarations are not packaged in the main JAR. No original Bamboo corpus test was possible this turn. Custom renderers, special view transforms, all projectile entities and arbitrary weapon effects remain incomplete. Jump stays OBSERVE_ONLY; repeated-weapon disconnect causality remains unresolved.

See [rev205 details and source extraction](checkpoints/rev205/README.md), [verification](checkpoints/rev205/verification.json) and [branch/no-Actions development policy](checkpoints/rev205/DEVELOPMENT_POLICY.md).

```sh
python checkpoints/rev205/unpack.py --output ../LegacyForgeBridge-rev202-205-kits
```

This expands 819 checksummed source/test/tool files into revision kits. Use each kit's rebuild.py with its pinned base main. **Root src remains the old base plus cumulative checkpoints; do not compile it alone and label it rev205.** This checkpoint does not include proprietary game/mod binaries or test dependency JARs.

## Preserved baselines

Only `feature/generic-conversion-iyamato-corpus3` is updated. Main stays at the merged rev197 baseline; the Bamboo branch, original server/mods and workflow definitions remain unchanged. No workflow dispatch, PR, tag or release is requested. Continuation commits use `[skip ci] [skip actions]`.

[Previous complete README at rev201](docs/README-before-rev205-f3f4cf05.md) retains earlier restoration instructions and unresolved GUI/runtime boundaries. All older checkpoints remain. Successful handshake, source analysis or local tests are not proof of full compatibility.
