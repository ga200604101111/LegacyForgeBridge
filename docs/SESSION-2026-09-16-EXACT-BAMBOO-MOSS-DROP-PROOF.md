# Session 2026-09-16 — Exact Bamboo moss drop source proof

Branch: `feature/generic-conversion-bamboo-corpus2`

Exact corpus SHA-256:

```text
bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

The checksum-pinned Bamboo 2.6.8.5 JAR now has one block whose complete static self-drop **source proof chain** closes under the current generic analyzers: `bambooMoss` / `ruby/bamboo/block/BlockMoss`.

The exact-corpus regression requires all of the following simultaneously:

- ordinary drop plan = self BlockItem, quantity 1;
- all sixteen raw legacy metadata states produce item damage 0;
- silk eligibility proof complete and silk is enabled;
- silk stacked item proof complete with damage 0;
- constructor material provenance = 1.7.10 `Material.ground` (`field_151578_c`);
- the pinned 1.7.10 material rule proves `toolNotRequired=true`;
- source material-fast-path harvest customization is absent;
- explosion drop eligibility uses the admitted default path;
- source explosion destruction overrides are absent;
- the actually registered Bamboo Forge/FML event handlers contain no HarvestDrops, HarvestCheck, or ExplosionEvent mutation of this path.

This test deliberately stops at source proof. It does **not** claim a final generated runtime-rule count until the full `LegacyConversionEngine` exact-corpus output is executed and inspected under the modern development/runtime classpath.

No production behavior or converter revision changes in this checkpoint.
