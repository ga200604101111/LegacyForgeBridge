# LegacyForgeBridge — Bamboo alpha.27 MillStone source presentation completion

Date: 2026-09-15
Branch: `feature/generic-conversion-bamboo-corpus2`

This slice completes the source-proven MillStone presentation family by adding the final grinding-particle path after GUI, world renderer and inventory 3D rendering were already green.

The generic particle analyzer requires source bytecode proof of the client/metadata gate, block digging particles with source `0.2` velocity multiplier and `0.6` scale, the ordinary-item icon-crack branch, exactly one item particle, and the source random trajectory including the `0.15` vertical velocity subtraction. No Bamboo class name is an admission condition.

The client runtime maps those branches to native 1.21.11 `BLOCK` and `ITEM` particle options. The converted BlockEntity now publishes its active recipe identity through normal BlockEntity update packets so the client never guesses which consumed input is being ground.

For the exact Bamboo 2.6.8.5 corpus (SHA-256 `bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`) this permits `guiPresentationRuntimeComplete`, `worldPresentationRuntimeComplete`, `inventoryPresentationRuntimeComplete`, `particlePresentationRuntimeComplete`, and `sourcePresentationComplete` to become true.

`energyIngressRuntimeComplete` and overall `runtimeComplete` deliberately remain false. The remaining MillStone gameplay gap is the external legacy CoFH energy-ingress contract, not source presentation.
