# Cross-loader library candidates (for later milestones)

Research note, 2026-07. Libraries verified **Fabric + NeoForge on 1.21.1** that may be
worth adopting as the roadmap advances. Nothing here is a current dependency — the mod
stays deliberately lean (Architectury + Stonecraft, hand-rolled config). Adopt only when
a library erases substantial work AND (ideally) can be an *optional* integration.
Re-verify current versions/loader support before committing to any of these.

## Strong fit — already implied by the roadmap
- **EMI** — item/recipe viewer, zero-dependency, clean modern plugin API; Fabric +
  NeoForge, `1.1.24+1.21.1`. PLAN §4.4 calls for "JEI/EMI/REI category integration from
  day one" for the Arcane Worktable. Plan for it as an **optional** compile-against-API
  integration when the worktable + vis-cost recipe type land.
  https://modrinth.com/mod/emi
- **Accessories** (Wisp Forest) — data-driven, loader-agnostic accessory/equipment slots,
  DataComponents-native, with a compat layer so Curios (NeoForge) and Trinkets (Fabric)
  items interoperate; `1.1.0-beta.x+1.21.1`. The cross-loader answer to Thaumcraft
  baubles: goggles of revealing (Aetherlens as a head slot), amulets, charged robes.
  https://github.com/wisp-forest/accessories · https://modrinth.com/mod/accessories

## Situational — only if the matching feature is committed
- **Fzzy Config** — config engine that does what we hand-rolled: **TOML** serialization,
  auto-generated config GUIs, and **server→client sync** (the thing hand-built for the
  casting config). Fabric + NeoForge, `0.7.x` on 1.21.1. A deliberate adopt-vs-hand-roll
  decision, not a gap: worth it if config grows or in-game config screens are wanted.
  https://github.com/fzzyhmstrs/fconfig · https://modrinth.com/mod/fzzy-config
- **GeckoLib** — keyframed 3D animation (Blockbench export) for entities/blocks/items;
  all loaders, `4.9.2` on 1.21.1. Only pays off with concrete animated content —
  golems/constructs, bubbling alembics, a turning infusion matrix. Big dep; defer.
  https://modrinth.com/mod/geckolib
- **TerraBlender** — cross-loader biome injection (LGPLv3), `4.1.0.8` on 1.21.1. Only if
  taint zones / magical groves become real **biomes** rather than worldgen features
  (silverwood is feature-based today, which is lighter).
  https://modrinth.com/mod/terrablender

## Uncanny overlap — we already built our own
- **Modonomicon** — in-game guidebook lib explicitly "inspired by Thaumcraft's
  Thaumonomicon and Patchouli," fully data-driven with a quest/advancement-style 2D tree
  and unlock conditions; MIT, 1.21.1 both loaders. We have the Codex — this is an
  adopt-vs-maintain call if the Codex ever feels like too much upkeep.
  https://modrinth.com/mod/modonomicon

## Verify before relying on
- **Botarium / Common Storage Lib** — cross-loader item/fluid/energy storage abstraction;
  could help M4 essentia (fluid-like) handling in crucibles/alembics/jars. **1.21.1
  release unconfirmed** in research (latest seen was 1.20.x) — check before counting on
  it. https://modrinth.com/mod/common-storage-lib

## Skip / already covered
- **Architectury** — already in use.
- **owo-lib**, **Cardinal Components** — Fabric-only; fail the dual-loader gate.
- **ParticleAnimationLib** — Fabric/Quilt-only (no NeoForge). MIT, so port the particle
  *shape math* (sphere/vortex/cone/helix) into a small cross-loader helper if casting VFX
  needs it, rather than taking the dep.
- **AuraTip** — Forge 1.20.1 player-facing tooltip mod; wrong loader/version, not a lib.
