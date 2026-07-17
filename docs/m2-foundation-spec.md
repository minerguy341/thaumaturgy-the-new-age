# M2 Foundation Spec — Research Logic (surface-agnostic)

**Status:** partial M2 work done autonomously while the M1 smoke test was pending.
This spec covers **only** the headlessly-verifiable logic layer of the research
system. The visible research systems (Scriptorium theorycraft UI, the globe
renderer, Codex entry pages, gating) are **out of scope here** and wait for Jacob.

**Context:** PLAN.md §4.2 (research hybrid), `docs/aspects.md` (spherical grid
decision), builds on the `m1` aspect + player-progress layers.

## Why this slice

PLAN.md §4.2 requires the linking-puzzle logic to be written surface-agnostic so
a flat-grid MVP or the full globe can render the same rules. The puzzle rules and
the sphere topology are pure math — objectively testable without a client — so
they are the safest, highest-leverage thing to build ahead of the UI. Nothing
here commits to a game-design decision that is Jacob's to make.

## In scope

1. **Goldberg grid geometry.** `GoldbergGrid.generate(frequency)` builds a class-I
   Goldberg polyhedron GP(f,0) by geodesic subdivision of an icosahedron projected
   to the unit sphere: cells (exactly 12 pentagons + hexagons), unit-vector
   positions (for the future renderer), and a symmetric neighbor adjacency.
   Cell count = 10·f² + 2 (f=2 → 42, f=3 → 92, f=4 → 162).
2. **Linking-puzzle logic.** `LinkingPuzzle` over a grid: fixed endpoint aspects
   plus player placements, with a relatedness rule derived from the aspect
   component graph (two aspects are related iff equal, or one is a direct
   component of the other). Queries: `isValidPlacement` (no adjacent conflicts)
   and `isComplete` (every cell filled and valid). Pure logic, no I/O.
3. **Tests.** GameTests on both loaders: grid invariants (Euler characteristic,
   exactly 12 pentagons, pentagon degree 5 / hexagon degree 6, adjacency
   symmetry, cell counts per frequency) and puzzle validation over the real
   aspect set.

## Explicitly deferred (Jacob's calls / needs in-game verification)

- The globe **renderer** and interaction (rotate, place, the flat-grid MVP).
- Puzzle **generation**: which endpoints, difficulty curve, how the 12 pentagons
  become special cells (wildcard / sealed / bonus).
- **Theorycraft** loop: the Scriptorium, the card-draw minigame, and the set of
  research **disciplines** (naming/among these is a design decision).
- Codex **entry pages**, research **gating**, forbidden-research channel.
- Wiring observation points → spending them in research.

## Acceptance criteria (this slice)

- [ ] `chiseledBuild` + `chiseledGameTest` green on both nodes
- [ ] Grid invariants hold for frequencies 1–4 (exactly 12 pentagons always)
- [ ] Puzzle validation correctly accepts related-adjacent and rejects
      unrelated-adjacent placements
- [ ] Work isolated on the `m2-research-foundation` branch; `main` untouched so
      `m1` can still be tagged at its candidate commit
