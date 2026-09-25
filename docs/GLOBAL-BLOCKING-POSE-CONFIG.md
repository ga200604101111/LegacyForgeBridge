# Global converted-weapon blocking pose configuration

LegacyForgeBridge does not replace ViaFabricPlus's blocking decision, packets, use animation or
combat behavior. It removes the old LFB-authored blocking model branch and optionally appends one
client-side affine correction after Via/vanilla has selected the active block pose.

The configuration is created on first launch at:

`config/legacyforgebridge-client.json`

It applies to every actively blocking **LegacyForgeBridge-converted item** that carries the modern
`minecraft:blocks_attacks` component. There are no mod IDs, class names or per-item allowlists.
Vanilla shields and unrelated native Fabric items are not modified.

Default configuration (an exact visual no-op):

```json
{
  "schemaVersion": 1,
  "swordBlockingPose": {
    "enabled": true,
    "mirrorLeftHand": true,
    "firstPerson": {
      "translation": [0.0, 0.0, 0.0],
      "rotationDegrees": [0.0, 0.0, 0.0],
      "scale": [1.0, 1.0, 1.0]
    },
    "thirdPerson": {
      "translation": [0.0, 0.0, 0.0],
      "rotationDegrees": [0.0, 0.0, 0.0],
      "scale": [1.0, 1.0, 1.0]
    }
  }
}
```

Array order is X, Y, Z. `rotationDegrees` is applied X then Y then Z. With `mirrorLeftHand: true`,
left-hand X translation and Y/Z rotation are mirrored automatically. The file is checked once per
second; a valid edit is applied without restarting. Invalid edits retain the last valid values and
write a warning to `logs/legacyforgebridge.log`.
