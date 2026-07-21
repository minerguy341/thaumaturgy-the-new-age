# Arcane Wand Forge — web customizer

An interactive, hot-swap customizer for wands and staves, à la the lightsaber
workbench in *Jedi: Fallen Order / Survivor*. Live 3D preview + part swapping +
derived stats, in a single self-contained `index.html` (no build step, no
dependencies, no network — open it in any browser or host it statically).

```
open docs/wand-editor/index.html      # or double-click it
```

## What it models

Mirrors the shipped wand system (`docs/wands.md`, `core/casting/*`,
`data/new_age_thaum/wand_materials/*.json`):

| Implement | Parts | Capacity |
|---|---|---|
| **Wand** | Emitter (top cap) · Core (rod) · Pommel (bottom cap) | core × 1.0 |
| **Stave** | Emitter **×2** (both ends) · Grip (central connector) · Core (twin rods) | core × 1.6 |

- **Emitter & pommel use the cap model** (`wand_cap_base` → `brass_cap` /
  `aetherium_cap`), tinted by material — the same `tintindex` scheme as
  `client/WandColors.java` (0 = core, 1 = cap A, 2 = cap B).
- Stats mirror `WandStats.compute`: capacity = core × form multiplier;
  discount = capA + capB (capped 50%); potency = capA + capB; recharge
  affinity = the core's aspect.
- Names compose exactly like `CastingImplementItem.getName`
  ("Aetherium Capped Silverwood Stave").

## The double-bladed stave (design proposal)

The shipped stave is just a longer wand (top cap + core + bottom cap). This
tool reframes it as a **double-bladed saber**: an emitter on *both* ends joined
by a **central grip/connector** over two core-halves. Emitters are mirror-linked
by default (🔗) with a 🔓 toggle to customize each end independently. The grip is
a **bare-bones placeholder model** and is cosmetic for now (feeds no stat).

This lives in the editor only — the mod's game assets/Java are untouched. If we
adopt the double-bladed form in-game, the next step is a real grip model +
extending assembly to a middle part.

## Tech notes

- **Renderer:** a small hand-rolled WebGL renderer for Minecraft-style box
  geometry (0–16 space, Y up), flat directional + ambient shading, orbit camera
  (drag-rotate, wheel/pinch-zoom). No Three.js — the page must run under a
  strict CSP with zero external resources.
- **Geometry** is authored in `buildParts()` from the cap/rod proportions; parts
  overlap at their seams so the implement reads as one object. Swap in exported
  Blockbench JSON later by feeding element `from`/`to` boxes to the same builder.
- **Themes:** light/dark via CSS tokens; palette from `docs/art-direction.md`
  (aetherium `#8A6BB5`, brass `#C79A55`, silverwood teal `#7FE8D8`).
- Responsive: sidebar on desktop, stacked sheet on mobile.
