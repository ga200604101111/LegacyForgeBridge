# LegacyForgeBridge

Experimental Fabric 1.21.11 client compatibility/conversion layer for Forge 1.7.10 mods. The original server owns gameplay state. Keep original JARs in `old-mods`, not modern Fabric `mods`.

## Latest continuation: rev247 — UI, cache identity and post-ground observations

Complete main delivered in chat: `legacyforgebridge-0.2.0-alpha.27-rev247-corpus4-local.30-ui-cache-diagnostic.jar`, 4,553,459 bytes, SHA-256 `db7627f77ddac1d84f2eff300f6397c9304971b56f0578d507d4a573944ae88a`.

**Jump remains observation-only and is not fixed. The user's rev246 conversion exception has not been captured, so its root cause is not confirmed.** The supplied logs are from rev245; do not represent the replay as a new game test.

The support child dialog now wraps to the viewport width, uses vertical scrolling only, reuses the existing dialog and supports Close/Escape. The actual embedded DesktopHelper reproduces the old horizontal overflow; the new layout was tested at four widths and 2x display scaling. The main title remains `LegacyForgeBridge | [目前的狀態] | by YingHunag09`.

The rev245 and rev246 conversion classes are byte-identical, but rev246 changed their cache revision unnecessarily. rev247 separates its new artifact version from the retained rev245 semantic revision, accepting only the exact rev246 UI-only fingerprint alias. Source/schema/version checks, failed-result handling and artifact hash validation are retained. This fixes an independently proven invalidation issue; it does not establish why the user's conversion failed.

A supplemental read-only observer labels repeated positive velocity after an observed ground contact, without suppressing movement or changing server authority. Replaying the existing valid capture parts recovered nine repeated-positive intervals including five post-ground candidates. The original correlation classes, jump handlers, landing effects, liquid adapter and mixins are preserved.

## Validation and reproducibility

See [rev247 instructions and boundaries](checkpoints/rev247/README.md), [validation](checkpoints/rev247/verification.json), and [cache equivalence evidence](checkpoints/rev247/cache-equivalence-proof.json).

Actual packaged DesktopHelper/Swing validation: 145 checks each on outer main, nested helper, and nested helper at 2x scaling. Packaged cache checks: 25. Diagnostic regression with explicit Minecraft/Fabric/API/writer doubles: 20 scenarios / 643 assertions. Ground-evidence tests plus old-capture replay: 11 checks. Two independent offline builds are byte-identical. No full mod-corpus conversion, Minecraft launch, live Via pipeline or server call-site validation was performed.

Root `src` is NOT the cumulative delivered state. Build from the pinned checkpoint and exact rev246 main, not root source alone. No third-party mod/game JARs or font files are stored in this checkpoint. The [previous root README](docs/README-before-rev247-e34f9ed.md) is preserved verbatim, including rev205/206 history and limitations; historical paths there are relative to the repository root. All older checkpoints remain intact.

## Policy and remaining boundaries

Source-driven generic conversion is not a mod-name allow-list. Historical corpus or gameplay validation does not prove complete compatibility for every feature/version. Projectile gameplay, custom renderers, universal GUI conversion, and jump synchronization still have unverified paths. Successful loading, handshake or analyzer counts do not establish full gameplay support.

Work only on `feature/generic-conversion-iyamato-corpus3`, with `[skip ci] [skip actions]`. No Actions dispatch, workflow changes, PR, tag/release, force push, main/Bamboo branch changes, original-mod changes or server changes. See [AGENTS.md](AGENTS.md).
