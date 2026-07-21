---
name: minecraft-crafting-station
description: >
  Decide and build a crafting-station block (workbench, arcane worktable, altar, infuser —
  any block a player right-clicks to combine items into an output) on Fabric/NeoForge 1.21.x.
  Use this BEFORE writing the block or its screen whenever the task adds a station that crafts,
  combines, infuses, or transforms items, or asks "should this have a custom UI or reuse the
  crafting table". It routes the reuse-vs-overlay-vs-custom decision, then hands off to the
  right implementation skills and to the Pixel-Art-Aide studio for the UI art. Consult it first —
  going custom by reflex forfeits the recipe book + JEI/REI transfer and costs a plugin to rebuild.
---

# Crafting-Station Block: decide the UI before you build it

The most expensive mistake with a crafting station is reaching for a custom `MenuType`/`Screen`
when the vanilla grid would have done — you pay the whole custom bill and hand players a UI that
does *less*. **Make the reuse-vs-custom call FIRST**, from the decision below, then build.

Full rationale, the mod survey it's drawn from, and the JEI/REI parity table live in the
Pixel-Art-Aide studio repo: `gallery/2026-07-20-workbench-ui-study/findings.html`
(+ `decision.md`). The two governing lessons are `knowledge/lessons.md` there, dated 2026-07-20.

## Why "the crafting table never changes its UI" is the baseline

It's a **stability contract**, not laziness. Recipes are matched server-side against the
container; slot indices mean fixed things (`0` = result, `1–9` = grid). Because the shape is
standard, **the recipe book, JEI/REI "+" auto-fill, shift-click/quick-move, and universal recipe
compat all work for free**. Every step off that shape buys expressive power and forfeits that free
infrastructure — which you then owe by hand.

## The decision (make this call before writing code)

**Reuse the vanilla 3×3 grid IF** the feature is orthogonal to the crafting interaction —
persistence, pulling from neighbors, portability, performance, a cosmetic variant — i.e. it's
expressible as *container behavior*, not new slots/widgets. Use vanilla `CraftingMenu`; you inherit
everything. (This is the correct default for anything table-shaped.)

**Add an arcane OVERLAY (grid + readout) IF** crafting is still grid-shaped but a cost/requirement
must be shown and can gate the craft, or a non-consumed tool participates. Keep a real 3×3 + result
where players expect them; decorate the frame with:
- a **tool/battery slot** (wand/focus) with a **ghost-item empty-state hint**;
- a **ring of cost/validity chips** that **appear only when the current recipe needs them**,
  **live-recompute on every slot change**, and **lock the result with a "why" tooltip** when
  unaffordable ("Requires 12 Vis, chunk has 4").

  This is **augment-don't-replace** — Thaumcraft's Arcane Workbench — and the **default for T.N.A.'s
  arcane worktable**. It matches the 3×3-grid top texture the block already wears.

**Go FULLY CUSTOM only IF (need ≥1)** the recipe *shape* isn't a grid (radial, node-graph,
pedestal-orbit, sequential), a resource axis must be interactively *manipulated* (not just shown),
validity/cost is a live feedback surface central to the interaction, you need search/category
browsing of a large set, or preview-then-commit. **AND** you've budgeted the full stack below.

## The bill you owe going off the standard grid

Non-negotiable if you leave the vanilla shape — budget it up front, not as follow-up:
- **JEI *and* REI plugin** (players run either) — recipes are invisible in the viewer otherwise.
- a **recipe category** that draws your cost/aspects in the lookup panel;
- a **recipe transfer handler** for "+"; for ghost/battery slots that means a **validated C2S
  packet** (client-side viewers can't move server items — never trust the client's layout);
- **catalyst registration** so right-clicking the block in JEI shows its recipes;
- hand-written `quickMoveStack`; recipe-book hooks are gone unless you re-add them.

## Build path once you've decided

Chain the existing skills in this repo — don't reinvent what the orrery already solved:
- **`minecraft-container-blocks`** — the BlockEntity + `Container` (spill on break, save/load/sync,
  hopper interaction). Every station is this shape.
- **`minecraft-ui-design`** — the menu+screen triad, the NeoForge screen-registration timing trap,
  optimistic-client/authoritative-server edits, widget patterns, the dark-purple palette. The
  Arcane Orrery (`content/ArcaneOrreryBlockEntity.java` + its screen) is the worked example — an
  overlay worktable reuses that whole stack.
- **`minecraft-c2s-validation`** — the craft/transfer packet: server re-checks recipe, cost,
  ownership, reach; reject → `broadcastFullState()`. A silent refusal reads as a bug — play a
  `VILLAGER_NO`.
- **`minecraft-data-components`** — per-item state the craft reads (wand vis, research unlocks).
- **`minecraft-gametests`** — cover the server-side craft + rejection on both loaders.

## Designing the UI art → hand off to the Pixel-Art-Aide studio

Once a screen (overlay or custom) is warranted, the **look** is authored in the **Pixel-Art-Aide**
studio repo, not hand-coded pixel-by-pixel here:
- invoke its **`pixel-artist`** skill to author the panel chrome, slots, chips, and the tool-slot
  ghost hint as **1.21 nine-slice sprites** (corners fixed, edges/center stretch — scales at every
  GUI scale) in the shared dark-purple UI palette;
- keep vanilla widget conventions (18×18 slot bevel, standard button sprites) so it reads as
  Minecraft; the studio's contact-sheet loop is how you pick a layout before wiring it;
- the **block's own faces** (e.g. the 3×3-grid top) are authored there too — exterior and screen
  should tell the same story. Sync any palette from `docs/art-direction.md` (upstream).

## Smell tests (you picked wrong)

- Custom screen is a 3×3 with a nicer background and nothing else → **reskin sin**; revert to grid
  or overlay. You broke the recipe book + "+" for zero gain.
- Can't name the mechanic the grid couldn't hold in one sentence → don't go custom.
- Recipe JSON grew cost/aspect/tool fields explained in the tooltip/wiki instead of the UI → it
  wants an **overlay**.
- Cost readout only updates on reopen → not recomputing on slot change / not syncing.
- Empty arcane slots look identical to grid slots → add ghost hints/labels.

## Reserved for fully-custom in T.N.A. (genuinely non-grid — not the worktable)

Infusion (central pedestal + orbiting input pedestals, in-world) · the research web (node-and-line
browser + search) · essentia handling (fill columns + tier-trim). These earn their custom UIs; the
crafting worktable does not.
