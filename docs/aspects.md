# Aspect Taxonomy

Canonical aspect set for M1 (decided 2026-07-14). Same conceptual skeleton as
Thaumcraft 4 — six primals, compounds built from pairs — but every name is an
original Latin coinage (PLAN.md §1.4: no reuse of Thaumcraft's aspect names).
Colors are the only art for now; icons come later with the art-direction pass.

Compounds always combine exactly two components (primal or compound), TC4-style,
so the research minigame's "adjacent aspects must be related" rule works by
walking this graph. This document is the canonical source until the datapack
JSON format lands in M1.

## Primals

| Aspect | Concept | Color |
|---|---|---|
| **Ventus** | Air, wind | `#CDE8F5` |
| **Tellus** | Earth, soil | `#6BA84F` |
| **Flamma** | Fire, heat | `#F0552B` |
| **Unda** | Water, flow | `#3D9BE0` |
| **Forma** | Order, structure | `#EDE9DC` |
| **Discordia** | Chaos, entropy | `#4A3459` |

## Compounds — Tier 1 (primal + primal)

| Aspect | Concept | Components | Color |
|---|---|---|---|
| **Lumen** | Light | Flamma + Ventus | `#FFE066` |
| **Impetus** | Motion | Ventus + Forma | `#9FD8DC` |
| **Inane** | Void, emptiness | Ventus + Discordia | `#6E6480` |
| **Procella** | Storm, weather | Ventus + Unda | `#7CA6C9` |
| **Glacies** | Ice, cold | Unda + Forma | `#A9E7F5` |
| **Toxicum** | Poison | Unda + Discordia | `#7CA82E` |
| **Vita** | Life | Unda + Tellus | `#C43C55` |
| **Gemma** | Crystal | Tellus + Forma | `#9FE6C9` |
| **Vigor** | Energy, power | Flamma + Forma | `#F2C230` |
| **Mutatio** | Exchange, change | Forma + Discordia | `#B097D6` |

## Compounds — Tier 2

| Aspect | Concept | Components | Color |
|---|---|---|---|
| **Letum** | Death | Vita + Discordia | `#45403E` |
| **Anima** | Soul, spirit | Vita + Letum | `#BFE3F7` |
| **Umbra** | Darkness | Inane + Lumen | `#2E2A3A` |
| **Arcanum** | Magic | Inane + Vigor | `#DD4FD0` |
| **Aviditas** | Hunger, craving | Vita + Inane | `#99885C` |
| **Remedium** | Healing | Vita + Forma | `#F5BFD0` |
| **Flora** | Plants, growth | Vita + Tellus | `#6EC24A` |
| **Fera** | Beasts | Impetus + Vita | `#A66A3E` |
| **Aes** | Metal, ore | Tellus + Gemma | `#ADAFBC` |
| **Via** | Travel, paths | Impetus + Tellus | `#C2B280` |
| **Ala** | Flight | Ventus + Impetus | `#DDF0F7` |

## Compounds — Tier 3

| Aspect | Concept | Components | Color |
|---|---|---|---|
| **Mens** | Mind, thought | Anima + Forma | `#DCC3F0` |
| **Acies** | Perception, senses | Ventus + Anima | `#F5D9A6` |
| **Aether** | Aura, ambient magic | Arcanum + Ventus | `#B37FE8` |
| **Macula** | Taint, corruption | Arcanum + Discordia | `#7A2E8F` |
| **Silva** | Wood, forest | Flora + Tellus | `#7A5A34` |
| **Caro** | Flesh | Fera + Letum | `#DE8878` |
| **Larva** | Undeath | Impetus + Letum | `#9DB86B` |
| **Persona** | Humanity | Fera + Mens | `#E7C39A` |

## Compounds — Tier 4

| Aspect | Concept | Components | Color |
|---|---|---|---|
| **Artificium** | Craft, tools | Persona + Forma | `#C9A85C` |
| **Automata** | Mechanism | Impetus + Artificium | `#8C9EB5` |
| **Ensis** | Weaponry | Artificium + Flamma | `#C24A4A` |
| **Praesidium** | Protection, armor | Artificium + Tellus | `#6E8CA8` |
| **Opes** | Wealth, greed | Persona + Aviditas | `#F5D142` |
| **Barathrum** | The eldritch, the abyss | Inane + Macula | `#33203D` |

**Totals:** 6 primals + 35 compounds = 41 aspects. Extensible by datapack once
the M1 JSON format exists; keep new compounds to exactly two components.

## Icon direction (decided 2026-07-14)

- Aspect icons are **hexagonal**, in the spirit of TC's research grid. One icon
  per aspect, HD resolution: author masters at 512x512, ship at **256x256**
  (icons render at 16-64 px in GUIs; 41 aspects x 512^2 is ~43 MB of texture
  vs ~11 MB at 256^2). Until real art exists, a flat hexagon filled with the
  aspect's palette color is the placeholder.
- The research linking minigame plays on a **rotatable globe**: a spherical
  hex grid (Goldberg polyhedron — hexagons plus the 12 pentagons any such
  tiling requires). Puzzle size scales with research difficulty via the
  subdivision level: GP(1,1) = 20 hex + 12 pent, GP(2,0) = 30 + 12,
  GP(3,0) = 80 + 12. The 12 pentagons are reserved for special cells
  (wildcards, sealed cells, bonus nodes) rather than ordinary aspect slots.

## Naming notes

- Names deliberately avoid Thaumcraft's exact coinages (Ignis, Aqua, Ordo,
  Perditio, Praecantatio, Vitium, ...) while staying classical Latin. Where a
  concept's obvious Latin word was TC's pick, a synonym or poetic variant is
  used instead (Flamma for fire, Unda for water, Letum for death, Inane —
  Lucretius's word for the void).
- **Arcanum** (magic) recycles the project's discarded working title.
- **Aether** (aura) intentionally matches the *Aetherlens* scanning tool from
  PLAN.md §4.1.
