# Wands & Staves (early M3)

Built ahead of M2 at Jacob's request. The **assembly + item + stats** are complete
and gametested; casting/vis behaviour waits on the M3 aura system (the stats are inert
data until then). Datapack-driven, same patterns as aspects/materials.

## Assembly (in a crafting grid)

- **Wand** = 1 rod + 2 caps (any caps, mix and match).
- **Stave** = 2 rods of the **same** core + 2 caps (bigger vis capacity).

The `wand_assembly` special recipe reads whichever `WandPartItem`s you place and stamps
the result's `WandComponent` (core + the two cap materials). One recipe covers every
core × cap × cap combination — no per-combination recipes.

## Materials — `data/<ns>/wand_materials/<name>.json`

```json
{ "kind": "core", "color": "#7A5B3C", "capacity": 50, "recharge_affinity": "new_age_thaum:silva" }
{ "kind": "cap",  "color": "#C79A55", "discount": 0.10, "potency": 0.0 }
```

- `kind` — `core` (rods) or `cap` (ends).
- `color` — hex; tints the item layer (see appearance below).
- Cores: `capacity` (base vis) + optional `recharge_affinity` aspect.
- Caps: `discount` (cost reduction, both caps add, capped at 50%) + `potency`.

Seeded set (tunable, extend by adding items + JSON): greatwood/silverwood cores,
brass/aetherium caps — colors and roles from `docs/art-direction.md`.

## Derived stats (`WandStats.compute`)

- Vis capacity = core capacity × form multiplier (wand ×1.0, stave ×1.6).
- Discount = capA + capB, capped at 50%.
- Recharge affinity = the core's aspect.

Shown on the wand/stave tooltip.

## Appearance

Composited by **tinting**, not custom models: the wand/stave textures are grayscale
layers (rod, cap tip, cap base) and a client color handler tints each layer by its
material's color. Any combination renders distinctly. The grayscale art is placeholder;
swap the PNGs under `textures/item/` for real art later.

## Deferred (needs the aura system / Jacob's calls)

Actual casting, vis storage/recharge, foci sockets, the gauntlet tier — all M3-aura.
More cores/caps and stat tuning are datapack/JSON edits.
