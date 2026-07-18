# M2 Gameplay Spec — Research Economy, Currents, Tiered Puzzles

Decisions locked with Jacob 2026-07-16. Builds on the merged sphere workstation +
link rules (link = derivation step; identical/unrelated adjacency ignored; unlinked
cells grey out).

## A. Aspect economy

- Placing an aspect costs **1 observation point of that aspect**, enforced
  **server-side** in the orrery edit handler; the client pre-checks and refuses
  audibly. On rejection the server forces a menu resync so optimistic paints roll back.
- **No refund on clear.** Future design hook: a research unlock granting a *small
  chance* to refund — the spend/clear path exposes a single `refundChance(player)`
  seam (0.0 for now).
- Repainting an occupied cell costs the new aspect; the old one is lost.
- Spending keeps the aspect's key in the points map at 0 (spending never un-discovers).
- List UI: per-aspect point counts shown in each row; 0-point rows dimmed.
- **Discovery**: the six primals are always listed; compounds appear once the player
  has ever earned points of them, permanently.

## B. Energy currents

- Adjacent cells whose aspects validly link are joined by a **flowing energy line**:
  a ribbon between cell centers, distorted by layered animated sine waves, colored
  as a gradient between the two aspects' colors. Front-facing pairs only.

## C. Tiered puzzle papers

- The plain research paper is **removed**. Five paper items replace it:
  **Fledgling, Apprentice, Scholar, Master, Grandmaster**.
- The puzzle definition lives on the paper (extends the sphere data component):
  sphere frequency, **endpoints** (cell → fixed aspect, locked: cannot be painted
  over or cleared), and **gaps** (void cells that hold nothing, rendered missing).
- Generation is **lazy and server-side** (world random) the first time the paper
  sits in an orrery. Tier scaling: more endpoints, more gaps, deeper endpoint
  aspects (aspect-graph depth as the tier proxy).
- Sphere size: config `tierScaledSpheres` — `true` (default): Fledgling/Apprentice
  on GP(2,0)=42, Scholar/Master on GP(3,0)=92, Grandmaster on GP(4,0)=162;
  `false`: everything on GP(3,0). Config is the first use of the hand-rolled
  cross-loader JSON config (`config/new_age_thaum.json`, loaded at init).
- **Solvability by construction**: the generator first builds a hidden solution —
  carves vertex-disjoint endpoint-to-endpoint paths on the sphere, fills each with
  a valid aspect chain (exact-length walk in the aspect graph via DP), then places
  gaps only on non-solution cells, then discards the solution. Every generated
  puzzle is provably solvable. Retry loop with attempt caps; generation failure
  falls back to fewer gaps/endpoints rather than an unsolvable paper.
- Gametests: bulk-generate puzzles per tier; assert endpoints intact, gaps never on
  endpoints, all endpoints connected through non-gap cells, and the constructed
  solution replays cleanly through the link rules.

## D. Solved detection

- Solved = all endpoints belong to one connected component of valid links.
- For now: detection + a visual acknowledgement in the screen. Rewards, Codex
  unlocks, and the refund-chance research are later design conversations.

## Out of scope this arc

Pentagon special cells, theorycraft loop, Codex gating/rewards, refund research
unlock, puzzle rerolls, paper crafting recipes balance (papers obtainable via
creative for now).
