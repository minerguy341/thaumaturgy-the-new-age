# Art Direction

Decided 2026-07-14. Closes the last pre-M1 open question from PLAN.md §7.

## Principles

1. **Vanilla + Create compatible.** Block and item textures are **16x16**, in
   vanilla's rendering idiom, with saturation and detail density no higher than
   the Create mod's. Thaumaturgy builds should sit next to vanilla and Create
   blocks without either side looking out of place.
2. **Mid-value palette.** Not too dark, not too light: keep block albedo
   roughly within 25-80% luminance per channel (no near-black like TC6's
   darker sets, no washed-out near-white). Contrast comes from hue and
   texture, not extreme values.
3. **GUI art is the exception to 16x:** aspect icons are HD hexagons
   (512px masters, 256px shipped — see `docs/aspects.md`), and the research
   globe is rendered geometry, not sprites.
4. **Two accent metals, two woods, cross-paired.** Warm wood + warm metal
   (greatwood + brass), cool wood + cool metal (silverwood + aetherium) as
   the default pairings; the swapped combinations exist as decor variants.

## Woods (homage trees)

Names kept as homage: "greatwood" and "silverwood" are generic English
compounds, unlike TC's distinctive Latin coinages — see naming note below.

### Greatwood
Massive, ancient, warm. The early-game magic wood (wand cores, worktable).

| Element | Color |
|---|---|
| Bark | `#4E3D2E` with `#3A2D22` furrows |
| Planks | `#4F3B25` (TC-era dark; revised 2026-07-19, Jacob's call — see note) |
| Plank shadow / highlight | `#3E2C1F` / `#60492C`, seam `#30201C` |
| Leaves | `#3E6B2F` (deep green, vanilla-biome-tint friendly) |

> **Greatwood darkness note (2026-07-19).** The plank ramp was darkened from
> `#5E4530/#7A5B3C/#8F6E4B` to the values above after the first texture pass:
> Jacob picked the TC-era darkness over the mid-value-compliant candidates.
> Greatwood planks (luminance ~14-30%) are a **sanctioned exception** to
> principle 2's 25-80% band; the mid-value rule still applies to everything
> else. Ramp source of record: `styles/thaumaturgy.md` in the Pixel-Art-Aide
> repo mirrors these values.

### Silverwood
Pale, shimmering, node-bearing. The late/pure magic wood.

| Element | Color |
|---|---|
| Bark | `#D9DED9` with `#AAB2AC` streaks |
| Planks | `#CFD3C4` (pale warm gray-green) |
| Plank shadow / highlight | `#A8AC9D` / `#E4E7DA` |
| Leaves | `#A9D8C4` (pale blue-green) |
| Magic accent (sapling glow, node shimmer) | `#7FE8D8` |

## Metals

### Brass
Matches the Create mod's brass hue closely so machinery and magic decor blend.

| Element | Color |
|---|---|
| Base | `#C79A55` |
| Highlight | `#E8C983` |
| Shadow | `#8F6B38` |

### Aetherium (thaumium-equivalent)
The magic-infused metal gating the mid-tier gear. Named for the **Aether**
aspect (aura); purple metal with the silverwood teal glint for cohesion.
(Alternatives considered: Orichalcum, Arcanite — rename is cheap until M3.)

| Element | Color |
|---|---|
| Base | `#8A6BB5` |
| Highlight | `#B99BE0` |
| Shadow | `#5A4380` |
| Glint accent | `#7FE8D8` |

## Gilded planks

Decor block family: each wood in each metal trim.

- **Gilded greatwood** (brass trim) — default warm pairing, Create-adjacent.
- **Gilded silverwood** (aetherium trim) — default cool pairing.
- Cross pairings (greatwood + aetherium, silverwood + brass) ship as variants.
- Trim reads as inlay/banding in the 16x texture, Create-style detailing —
  thin metal lines, not full metal borders.

## Naming note

PLAN.md §1.4's "no reuse of Thaumcraft names" rule is amended (2026-07-14,
Jacob's call): **generic English compounds** (greatwood, silverwood) are
acceptable homage. TC's **distinctive coinages** (Thaumium, Praecantatio,
Thaumonomicon, aspect names, ...) remain off-limits — hence Aetherium.
