# M1 Task Spec — Aspects & Scanning

**Context:** PLAN.md §4.1 (aspects & scanning), §6 milestone 1, `docs/aspects.md`
(canonical aspect set), `docs/art-direction.md`. Builds on the `m0` toolchain tag.

## Objective

The aspect foundation everything else consumes: a data-driven aspect taxonomy,
aspect assignments on items (explicit via JSON, implicit via recipe inference),
the scanning verb that turns the world into observation points, and the first
visible shell of the Codex. All names/colors from `docs/aspects.md`.

## In scope

1. **Aspect core.** `Aspect` (id, color, optional pair of component aspect ids)
   with a codec; loaded from `data/{ns}/aspects/*.json` by a reload listener
   registered through Architectury. All 41 canonical aspects shipped as JSON.
   Compound graph validated on load (components must exist, exactly 2, no
   cycles); primal = no components. Synced to clients on join and reload.
2. **Aspect assignments.** `data/{ns}/aspect_assignments/*.json`: entries match
   an item id or `#tag` and carry an aspect bag. Starter coverage of common
   vanilla materials (~20 entries, tags preferred). Synced with the aspects.
3. **Recipe inference.** Items without an explicit assignment derive aspects
   from crafting recipes: lazily computed and memoized per item after reload
   (avoids reload-listener ordering); TC4-style dampening (75% of summed
   ingredient aspects per craft step, rounded down, minimum 1 while any source
   aspect survives); per-aspect cap; cycle-safe. Datapack `"aspects": {}` (empty)
   explicitly opts an item out of both assignment and inference.
4. **PlayerProgress service.** Scanned-object set + per-aspect observation
   points behind a `platform/` bridge (PLAN §5): NeoForge `AttachmentType` and
   Fabric data-attachment implementations in guarded files. Codec-serialized,
   survives death (keepInventory off) and dimension change, synced to the
   owning client on login and on change.
5. **Aetherlens.** Item (`aetherlens`, placeholder texture): right-click scans
   the targeted block or entity; first scan of a given object kind grants its
   aspect bag as observation points, actionbar feedback lists the gains, sound
   cue; repeat scans report already-known. Creative tab entry.
6. **Codex shell.** Item (`codex`) opening a client screen with **one**
   category tab whose entries come from `data/{ns}/codex_entries/*.json`
   (codec: id, category, title, icon item, position). Renders the entry nodes;
   no research gating, no entry pages beyond a stub panel (M2 scope).
7. **Tooltips.** Items with aspects (assigned or inferred) list them on the
   tooltip with colored names and amounts.
8. **Tests.** GameTests on both loaders: 41 aspects load + graph valid;
   explicit assignment resolves; inference derives dampened aspects through a
   recipe chain; scan grants observation points exactly once.
9. **Docs.** README feature blurb; JSON format documentation for pack authors
   in `docs/aspects.md` or a new `docs/datapack-formats.md`.

## Out of scope — do not touch

Research theorycrafting/linking minigames (M2), aura/nodes/flux (M3), wands and
casting (M3), essentia (M4), warp (M6), the spherical puzzle renderer, config
systems, JEI/EMI/REI plugins, real textures (placeholders only), advancement
triggers. If a step seems to require one, stop and ask.

## Acceptance criteria

- [ ] `chiseledBuild` + `chiseledGameTest` green locally on both nodes
- [ ] CI green on main
- [ ] Node-switch round trip leaves `git status` clean
- [ ] All 41 aspects load from datapack JSON; invalid data fails soft with a log
- [ ] Inference demonstrably derives aspects for an unassigned item (e.g.
      planks from logs) and is overridable by a datapack
- [ ] Observation points persist across relog and death (verified in gametest
      where possible, otherwise smoke test)
- [ ] Human smoke-test list produced; **tag `m1` only after Jacob confirms**

## Human smoke test (agent fills in exact details at handoff)

1. Both loaders: scan a stone block with the Aetherlens → actionbar shows
   Tellus gain; scanning again reports already-known; relog → still known.
2. Tooltips show aspects on assigned items and on an inferred item (planks).
3. Codex opens, one category renders its entries.
4. Death with keepInventory off does not wipe observation points.
