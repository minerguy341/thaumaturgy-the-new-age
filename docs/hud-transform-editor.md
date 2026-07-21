# HUD Transform Editor

In-game editor for repositioning, scaling (and later rotating) the mod's HUD elements,
opened with **`/thaum hud`**. Built on branch `claude/hud-transform-editor`.

## Scope

- **In:** the two wand-vis bars — **main-hand** and **off-hand** — as *independent* movable
  elements.
- **Out:** the Aetherlens node readout. It's a temporary element; do not add it to the editor.
- Extensible: the movable-element list can grow later; the machinery is generic.

## Per-element model (`HudLayout`)

Each element stores an on-screen transform, resolution/GUI-scale independent:

- **anchor** — a 9-point reference (anchorX, anchorY ∈ {0, 0.5, 1}) the element is pinned to.
- **offset** — (offX, offY) pixel nudge from the anchor.
- **scale** — per-element (the old global `PROJECTION` becomes this).
- **rotation** — *planned (v2)*, per element.

Anchor + offset keeps elements stuck to their corner/edge across window resize & GUI scale.
The element's own matching handle sits at `(anchorX*guiW + offX, anchorY*guiH + offY)`.
On drop, re-anchor to the nearest of the 9 points, absorbing the difference into the offset.

Rendering and the editor share `HudLayout` so placement math never diverges. `WandVisHud`
reads each bar's layout from config instead of the old hardcoded centering / `BAR_BOTTOM` /
`PROJECTION`. The **facing tell stays tied to the hand** (main = emitter-left, off =
emitter-right) so the two read apart even when spaced across the screen.

Defaults reproduce today's look: off-hand default = main-hand's spot offset up by one bar
height + gap, so out of the box dual-wield still auto-stacks; separation only shows if dragged.

## Editor UX

Opened by `/thaum hud` (client command, via `ClientCommandRegistrationEvent`, same `/thaum`
tree as `/thaum debug`). Renders **mock** bars (sample greatwood+brass wand / silverwood
stave, fake fills) so layout needs no wand in hand.

- **Drag** to move. **Parallel snapping** between the two bars: the dragged bar snaps into
  parallel alignment with the other at **discrete separations** — the present stacked spacing
  is one snap stop, with steps closer/further around it, maintaining parallelism. Enforce a
  **minimum** gap (no overlap); hold a modifier to **break free** for off-axis placement.
  Snapping is *momentary* (no permanent bond) — snap them near, then they're independent.
- **Scale** via scroll-wheel over a bar (or a slider).
- **Scale-link toggle** — links only the two bars' *scale* (not position). Default on.
- **Buttons:** per-element Reset, global Reset, Done (save), Cancel.

## Rotation — v2 (planned, not in v1)

Rotate the two bars (readout is excluded anyway, which sidesteps tilted text). **9 rotation
origins**, chosen in the editor as the pivot for a rotate gesture:

- per wand: **center / tip-end / pommel-end** (×2 wands = 6) — pommel-pivot swings the wand
  from its base, a natural motion.
- the pair: **center / left-end / right-end** (3).

Add **angle snapping** (0/15/45/90°, freehand with a modifier). Persisted state stays
**per-bar** (`rotation` on each `HudLayout`); the 9 origins and "the pair" are *editor
gestures* only — a pair-rotate bakes into each bar's own rotation+offset. No pair grouping in
render code.

## Persistence

Client-side keys in `NewAgeThaumConfig` (hand-rolled TOML), per bar:
`hud{Main,Off}Anchor{X,Y}`, `hud{Main,Off}Off{X,Y}`, `hud{Main,Off}Scale`, plus
`hudWandScaleLink`. (Rotation key added in v2.) Editor writes on Done; live HUD reads them.

## Phasing

- **v1 (this branch, first):** position (parallel snapping) + scale (+ scale-link) + the
  command + config persistence + `WandVisHud` reading layout from config.
- **v2:** rotation with the 9 origins + angle snap.

## Verification

None of the gesture/snap/rotation math is verifiable in the sandbox (no client). Every part
leans on in-game testing; hand off with a smoke list per phase.
