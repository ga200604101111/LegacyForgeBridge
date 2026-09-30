# 2026-09-30 — Bamboo flat BlockItem and iYAMATO live presentation follow-up

Branch: `feature/generic-conversion-iyamato-corpus3`

This checkpoint follows the user's live `0.2.0-alpha.27-corpus4-local.6-rev220-local-test.1`
session and the exact original 1.7.10 corpora supplied again in the conversation.

## Exact corpus

- Bamboo 2.6.8.5 SHA-256:
  `bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`.
- iYAMATO's Mod 1.7.10-1.6.8 SHA-256:
  `35a79ca5a034fd6cda4fe4ee1f658c4ce058f9b7ec026516e76363ebdf89635e`.

## Live evidence

The supplied live log reports:

- 19 source-rendered iYAMATO projectile runtime rules;
- 4 additional converted remote projectile presentation rules;
- 23 registered remote projectile renderers;
- actual remote spawns for IDs 4815 and 4822 rejected after mapping because watcher 16,
  wire type byte, was treated as unmapped;
- Bamboo and iYAMATO were reused from cache because source and converter fingerprints matched.

The exact iYAMATO source confirms that EntityArrow and several direct-Entity copied-arrow
families use DataWatcher index 16 as the legacy arrow flags byte.

The exact source also confirms 24 registered weapon/helper entities. The one entity outside the
23-renderer live set is EntityIYExtendedReach. It is a direct Entity + IProjectile copied-arrow
family, defines watcher 16 byte zero, and is intentionally rendered through
RenderSnowball(invisible_entity_projectile). It is an invisible extended-reach combat carrier,
not a visible missing arrow model.

## Source fixes

### Projectile watcher compatibility

Remote projectile spawn admission now accepts the canonical legacy projectile watcher-16 byte
wire layout after an entity has already passed the proof-gated projectile mapping. This removes
the observed 4815/4822 spawn rejection without executing server projectile gameplay client-side.

### Invisible copied-arrow RenderSnowball family

Projectile presentation analysis can admit a direct-Entity copied-arrow family only when:

- the source implements legacy IProjectile;
- source DataWatcher proof contains exactly watcher 16, byte, default zero;
- one exact RenderSnowball renderer binding selects a registered presentation carrier.

For this RenderSnowball-only presentation path, a unique launcher item is no longer required.
This covers families such as a shared invisible reach carrier spawned by multiple source weapons
without guessing a weapon or numeric ID.

### Bamboo flat BlockItem

A source-proven flat BlockItem sprite is materialized into the modern
`textures/item/lfb_flat/...` atlas path.

The final simple-renderer pass also retakes item-model ownership from earlier provisional native
geometry: only this exact block's `lfb_geometry/<metadata>` ITEM_MODEL entries are repointed to
the flat item definition. Independent source-owned metadata presentations are preserved.

### iYAMATO blocks

Generic block texture recovery now accepts a unique short legacy registry prefix difference such
as `iyvanadium_ore -> vanadium_ore`.

The legacy icon interpreter also recognizes Forge 1.7.10 ForgeDirection ordinals. This is needed
for source selectors such as the Damascus steel block, whose UP/DOWN faces use the top icon and
other faces use the separately registered side icon.

### Cache invalidation

The source BuildInfo converter fingerprint is bumped to
`2026-09-30.223-flat-item-projectile-blocktexture` so stale converted candidates are not accepted
as unchanged after these presentation changes.

## Validation boundary

Source regressions were added for:

- watcher-16 byte projectile compatibility;
- copied-arrow IProjectile + watcher16 + RenderSnowball admission with ambiguous/multiple launchers;
- modern flat sprite materialization;
- final flat BlockItem model ownership over provisional geometry metadata entries;
- unique short-prefix block texture recovery;
- ForgeDirection ordinal interpretation.

The exact original JARs were independently inspected with javap/resource enumeration for the
source facts above.

No new GitHub Actions workflow was triggered. No clean Gradle/Loom build or full replacement JAR
is claimed by this checkpoint.

The user's currently running main is a cumulative local binary overlay
(`corpus4-local.6-rev220-local-test.1`) which is not stored in this repository or in the current
conversation attachments. Per repository policy, a complete replacement must be built/overlaid
from that exact current main (or an equivalent complete cumulative binary/source restore); an
older repository JAR must not be renamed and delivered as the new main.
