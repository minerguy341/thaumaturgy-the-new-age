# Project Plan: Thaumaturgy: The New Age — A Thaumcraft-Inspired Magic Mod
### Multi-loader (NeoForge + Fabric), multi-version via Stonecutter + Architectury API

> **Note on the name:** Decided 2026-07-14 — display name **Thaumaturgy: The New Age**, mod id **`new_age_thaum`**, root package `io.github.minerguy341.new_age_thaum`. Slugs `new_age_thaum`/`new-age-thaum` and `thaumaturgy-the-new-age` verified free on Modrinth and CurseForge; the bare `thaumaturgy` slug is free on Modrinth but taken on CurseForge.

---

## 1. Vision & Design Pillars

A spiritual successor to Thaumcraft 4 and 6: research-driven progression, an aspect-based magic taxonomy, and a world that pushes back (flux, warp, taint) when you take shortcuts. The design decisions locked in so far:

1. **Hybrid research** — TC6's theorycrafting for broad progress, TC4's aspect-linking minigame for breakthrough moments.
2. **Hybrid energy model** — TC4-style nodes exist in the world and act as sources/anchors that feed a TC6-style ambient chunk aura.
3. **Full system suite** — wands/foci and vis casting, essentia + infusion crafting, golem automation, and warp/taint/eldritch endgame.
4. **Original identity** — mechanics inspired by Thaumcraft, but all names, art, lore, and text are original. No reuse of Thaumcraft assets, aspect names, or entry text; that keeps the project legally clean and gives it its own flavor.

**Ship target:** NeoForge + Fabric on **1.21.1** first, structured from day one so additional Minecraft versions are added by editing the settings script and per-version properties — not by restructuring the repo.

---

## 2. Toolchain: Stonecutter + Architectury

### 2.1 Approach

Stonecutter (currently **0.9.x**) handles multi-version *and* multi-loader through its branch/node system: each node is a `{version}-{loader}` subproject sharing one preprocessed codebase. Architectury API supplies the runtime cross-loader abstractions (registries, events, networking, render-type registration — the same `RenderTypeRegistry`/`ColorHandlerRegistry` surface you already know from the terrain-slabs work).

Two viable wiring strategies:

**Option A — Stonecraft plugin (recommended to start).** [`gg.meza.stonecraft`](https://github.com/meza/Stonecraft) is a configuration plugin that exists precisely to wire Stonecutter + Architectury together, collapsing ~500 lines of buildscript boilerplate. Fastest path to a working `{version}-{loader}` matrix, and you can eject to hand-rolled scripts later if it gets in the way.

**Option B — Hand-rolled, Elytra Trims style.** KikuGie's own Elytra Trims supports multiple versions plus Fabric and NeoForge using split build scripts (`fabric.gradle.kts` / `neoforge.gradle.kts` selected via `mapBuilds`). More control, more boilerplate. The [rotgruengelb/stonecutter-mod-template](https://github.com/rotgruengelb/stonecutter-mod-template) is a Java-only batteries-included starting point in this style.

**Decided 2026-07-14: Option A.** The repo layout below is identical either way, so switching later is a build-script change, not a source change.

### 2.2 Repository layout

```
thaumaturgy-the-new-age/
├── settings.gradle.kts          # Stonecutter node matrix lives here
├── stonecutter.gradle.kts       # controller — active version, global params
├── build.gradle.kts             # shared buildscript (per-node via Stonecraft)
├── gradle.properties            # global props + [VERSIONED] placeholders
├── versions/
│   ├── 1.21.1-fabric/gradle.properties      # loader/version-specific deps
│   └── 1.21.1-neoforge/gradle.properties
└── src/main/
    ├── java/io/github/minerguy341/new_age_thaum/
    │   ├── NewAgeThaum.java              # common init
    │   ├── platform/                     # loader entrypoints + shims (guarded)
    │   ├── core/        (aspects, registries, config, codecs)
    │   ├── research/    (journal, theorycraft, minigame)
    │   ├── aura/        (chunk aura, nodes, flux)
    │   ├── casting/     (wands, gauntlets, foci)
    │   ├── essentia/    (crucible, tubes, jars, infusion)
    │   ├── golem/       (assembly, AI tasks)
    │   ├── forbidden/   (warp, taint, eldritch)
    │   └── client/      (screens, particles, BER/node rendering)
    └── resources/
        ├── fabric.mod.json               # only shipped in fabric jars
        ├── META-INF/neoforge.mods.toml   # only shipped in neoforge jars
        └── assets/ + data/
```

### 2.3 Settings script (node matrix)

```kotlin
// settings.gradle.kts
stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"
    create(rootProject) {
        fun mc(version: String, vararg loaders: String) {
            for (loader in loaders) vers("$version-$loader", version)
        }
        mc("1.21.1", "fabric", "neoforge")
        // Adding a version later is one line:
        // mc("1.21.4", "fabric", "neoforge")
        vcsVersion = "1.21.1-neoforge"   // your primary dev target
    }
}
```

### 2.4 Loader constants and comment syntax

Register loader constants once so comments can branch on platform. Stonecutter's `constants.match` is the idiomatic way — it takes the current loader and defines each name as true/false:

```kotlin
// in the per-node configuration (build.gradle.kts / stonecutter params)
stonecutter {
    val loader = project.name.substringAfterLast('-')
    constants.match(loader, "fabric", "neoforge")
}
```

Then in source:

```java
//? if neoforge {
import net.neoforged.neoforge.attachment.AttachmentType;
//?}

public void init() {
    //? if fabric {
    /*FabricThing.register();*/
    //?} else {
    NeoThing.register();
    //?}

    //? if >=1.21.4 {
    /*newComponentApi();*/
    //?} else {
    oldApi();
    //?}
}
```

Additional Stonecutter features to lean on as the matrix grows: `dependencies["architectury"]` predicates for libraries that version independently of Minecraft, `swaps` for one-liner divergences that repeat across many files (e.g., a registry helper call whose signature changed), and string `replacements` for package moves. Install the **Stonecutter IntelliJ plugin** for comment highlighting, folding, and the version-switch button.

### 2.5 Build & release workflow

Day-to-day, work against the active node (`stonecutter active "1.21.1-neoforge"`) and run that node's `runClient`. Before pushing, switch to the VCS version so diffs stay clean. CI runs the **chiseled build** task, which builds every node in the matrix, and publishes via `mod-publish-plugin` to Modrinth/CurseForge with per-node game-version/loader metadata pulled from each node's `gradle.properties`. GitHub Actions matrix mirrors the Stonecutter matrix so a red build pinpoints the exact `{version}-{loader}` combination.

---

## 3. Cross-Loader Architecture Rules

These rules keep the preprocessor comments from metastasizing as the version matrix grows:

**Rule 1 — Architectury API first.** Registries via `DeferredRegister`, events via Architectury's event system, packets via `NetworkManager`, render types and color handlers via the client registries. Anything Architectury abstracts, use it.

**Rule 2 — Own service layer for the gaps.** Architectury does *not* abstract everything this mod needs — notably data attachments (NeoForge `AttachmentType` vs Fabric's attachment API), some capability-style item handlers, and creative-tab quirks. Define small interfaces in `platform/` (e.g., `PlayerDataBridge`, `ItemVisStorage`) with loader implementations behind `//? if fabric/neoforge` guards, each in its own small file. This also hedges the biggest external risk: if Architectury API lags on a future Minecraft version, the swap surface is your service layer, not the whole codebase.

**Rule 3 — Version-specific code lives in shims.** When 1.21.4+ changes an API, don't scatter `//? if >=1.21.4` through gameplay code. Route it through a `VersionShims` class (or a shim per subsystem) so gameplay logic stays clean and the preprocessed surface stays auditable.

**Rule 4 — Mixins are a last resort, and stay per-loader-aware.** You've spent enough hours reading `InjectionError` stack traces from other people's mixins to know why. Prefer events and API hooks; when a mixin is unavoidable (taint world-gen intrusion, aura hooks into chunk load), keep it tiny, targeted, and covered by a comment explaining exactly what breaks without it.

---

## 4. Game Systems Design

### 4.1 Aspects & Scanning (foundation for everything)

Every item, block, and entity resolves to a bag of **aspects** (~40–50: primal aspects plus compounds built from primal pairs, TC4-style). Aspect assignments are **data-driven JSON** loaded as a datapack resource, with a **recipe-inference pass** at resource-reload: any item without an explicit assignment inherits combined aspects from its crafting ingredients (the TC4 propagation algorithm, capped and dampened to avoid absurd stacks). This single decision makes the entire mod modpack-friendly — pack authors extend aspects to modded items with JSON, no code.

A **Thaumometer-equivalent** (the *Aetherlens*, say) scans blocks/items/entities in-world: first scan of a thing grants **observation points** per aspect present, stored on the player. Scanning is the primary early-game verb and the input currency for research.

Client work: scan-target highlight shader-free overlay (Iris-safe), floating aspect orbs on scan complete, a paper-doll aspect readout in tooltips once researched.

### 4.2 Research — the TC4/TC6 hybrid

Progression runs through a **Codex** (Thaumonomicon-equivalent) with category tabs and a node-graph of entries, all defined in **datapack JSON with codecs** so entries are addable by packs and addons.

Two interlocking loops:

**Loop 1 — Theorycrafting (TC6, breadth).** A **Scriptorium** table consumes paper + ink + observation points and runs the card-draw minigame: each round offers 2–3 actions (varying by table surroundings — nearby bookshelves, artifacts, and aura strength unlock better cards), building **Theory** in one or more disciplines. Theories unlock the *ordinary* entries in each category.

**Loop 2 — Aspect-linking breakthroughs (TC4, depth).** Milestone entries — the ones that gate whole systems (first wand, infusion, golem animation, eldritch) — instead demand a **linking puzzle**: a hex grid seeded with fixed aspect endpoints that the player must connect by placing aspects from their pool, where adjacent aspects must be related (compound↔component). Consumes aspect points; failed layouts refund partially. This preserves TC4's "I *solved* magic" feeling at the moments that matter, without making every single unlock a puzzle (the fatigue that TC6 was reacting to).

Warp-flavored **forbidden research** arrives via a third channel: certain scans/events grant sealed entries that require both theory *and* a linking puzzle *and* a warp cost.

### 4.3 Aura & Nodes — the hybrid energy model

The world model merges both games:

**Ambient aura (TC6):** every chunk has `vis` and `flux` floats with a per-chunk cap influenced by biome. Vis is drawn by casting and some machines; flux is pollution created by sloppy magic (over-boiling crucibles, failed infusions, wand misfires) and causes local misfortune — flux events, taint seeds at high sustained levels.

**Nodes (TC4):** generated structures/block-entities scattered in the world (caves, treetops, ruins) with an aspect composition, a personality (*bright*, *pale*, *hungry*, *tainted*, *pure*), and a recharge rate. **Nodes are the regeneration source for ambient aura**: a chunk with no node in range recovers vis very slowly; a healthy node nearby recovers it quickly and can push its cap higher. Hungry nodes eat entities and terrain; tainted nodes leak flux. Nodes can be drained hard (stunting them), jarred and relocated late-game (with penalties), or nurtured/purified.

This gives the TC6 "manage your local environment" gameplay while restoring the TC4 sense of *place* — good node real estate is worth building around.

**Storage & sync:** per-chunk aura lives in a dimension-scoped `SavedData` map keyed by `ChunkPos` (simplest fully cross-loader answer; avoids attachment-API divergence), ticked on a budget with neighbor diffusion, synced to clients only for chunks near players holding an aura-reading item.

### 4.4 Casting: Wands, Gauntlets, Foci

Early/mid-game: **wands** (cap + core material matrix determining capacity, discount, and recharge affinity) that store vis and recharge from nodes, recharge pedestals, or slowly from high-aura chunks. Wands perform *tool verbs* (activating constructs, crafting on the Arcane Worktable) and *cast* through a socketed **focus**.

Foci are itemized spell packages — starter set: bolt (damage), excavation, exchange, ward, illumination — each with **modifier slots** unlocked by research (potency, frugality, scatter, chaining). Late-game the **casting gauntlet** replaces stored vis with direct ambient draw (TC6-style), trading burst capacity for sustain and making chunk-aura management matter for combat casters. Misfires and flux generation scale with drawing beyond what the local aura can supply.

Arcane Worktable crafting (recipe type: shaped + vis cost + optional research gate) is registered as a custom `RecipeType` with JEI/EMI/REI category integration from day one.

### 4.5 Essentia & Infusion

The industrial layer. **Crucible** dissolves items into raw essentia (their aspect bag) atop fire; overfilling or mismatched dumps vent flux. **Alembics + the Essentia Furnace** distill items into typed essentia routed through **tubes** using TC4's suction model (numeric suction propagation, jars with labels/void jars, buffers, valves) — this is a genuinely fun logistics puzzle and worth reimplementing faithfully, with a comparator-readable jar block for redstone nerds.

**Infusion Altar:** center pedestal + runic matrix + surrounding item pedestals; recipes consume a center item, pedestal items, and typed essentia over a multi-second ceremony with **instability** — mitigated by symmetry (TC4's beloved/hated rule, kept, but with clear in-Codex feedback showing measured symmetry), candles/skulls-equivalents, and stabilizer blocks. Failures flick items, spill flux, or roll warp on catastrophic results. Infusion recipes are a datapack recipe type.

### 4.6 Golems & Automation

TC6's modular assembly, TC4's charm. A **Golem Press/Assembly Bench** combines *material* (clay/wood/brass/thaumium-equivalent → stats), *head/core* (task intelligence), *arms/legs* (interaction and mobility), and up to two **seals/addons**. Task assignment via placed **seal blocks** (TC6 model): porter seals on inventories, harvest seals on farm zones, guard seals on areas, lumber, fill, empty. Golems are small entities with a shared brain framework (task queue + navigation + interaction capability), original silhouettes/animations, and a personality tick (idle behaviors) because that's 80% of why people loved them.

Golem AI is deliberately milestone-late: it's the highest-effort, lowest-coupling system, so it can slip without blocking anything else.

### 4.7 Warp, Taint & the Eldritch

**Warp** (TC4): a per-player triple counter — *permanent* (forbidden research, eldritch acts), *normal* (decays very slowly, from risky magic), *temporary* (decays fast, from consumables/events). Threshold-crossing rolls **warp events** from a weighted, data-driven table: whispers, phantom mobs only you can see, inventory hallucinations, buffs-with-a-price. Purification via rituals and rare consumables reduces normal warp only — permanent warp is the price of knowledge and gates the eldritch tab.

**Taint**: a spreading corruption — fibrous blocks, tainted soil, goo — seeded by sustained high flux or tainted nodes, converting terrain and mobs via a budgeted spread ticker (biome conversion is expensive; use a block-conversion frontier set per chunk rather than random-tick everything). Counterable with purifying totems, silverwood-equivalent groves, and node purification. Taint should be *containable but scary* — a reason to care about flux hygiene near your base.

**Eldritch endgame**: high permanent warp + a final linking puzzle opens rifts to a pocket **Eldritch dimension** (maze structure, guardian minibosses, a final crystal-titan-style boss) yielding primordial materials for the top gear/foci tier. This is the last milestone and the most content-heavy; everything before it must be shippable without it.

---

## 5. Technical Notes & Compat Targets

**Player data (research, warp, scans):** a `PlayerProgress` object serialized through the `platform/` service layer (NeoForge attachment / Fabric attachment or persistent-NBT), fully synced on login, delta-synced on change. Never trust the client for research state.

**Custom recipe types:** arcane worktable, crucible, infusion, golem assembly — all codec-based, all with JEI/EMI/REI plugins, all reload-safe.

**Rendering:** you're an Iris/Sodium user, so hold the mod to that stack. Node rendering via block-entity renderer with camera-facing quads (no core-shader dependence), custom particles registered per-loader through Architectury's client registries, and everything on standard render types (`cutout_mipped`/`translucent`) so Sodium's chunk builder and Iris shaderpacks don't need special-casing. Test with DH loaded early — taint terrain conversion and DH's LOD capture can interact.

**Performance guardrails:** aura diffusion and taint spread on tick budgets with per-dimension caps; golem pathfinding throttled per-entity; no per-block-random-tick systems. Profile with Spark on both loaders before every milestone tag — you already know exactly what a haunted `Caused by` chain looks like at 3am; the budget-ticker discipline is how this mod avoids being the one that causes them.

**Config:** one common config (aura rates, warp weights, taint aggressiveness, golem caps) via a cross-loader config lib or hand-rolled JSON — avoid loader-specific config APIs in common code.

---

## 6. Milestones

1. **M0 — Scaffold (1–2 wks).** Stonecraft/Stonecutter matrix builds `1.21.1-fabric` + `1.21.1-neoforge`; empty mod loads on both; CI chiseled build green; one registered item + one guarded platform call proving the preprocessor + service-layer patterns.
2. **M1 — Aspects & Scanning.** Aspect registry + JSON assignment + recipe inference; Aetherlens scanning with observation points; Codex shell with one category; tooltips.
3. **M2 — Research Hybrid.** Scriptorium theorycrafting minigame; linking-puzzle screen; JSON research entries with gates; forbidden-entry channel stubbed.
4. **M3 — Aura, Nodes & Casting.** Chunk aura SavedData + diffusion + HUD readout item; node worldgen + types + recharge coupling; wands, three starter foci, arcane worktable + recipe type + EMI/JEI. *This is the "it's a real mod now" milestone — cut a public alpha here.*
5. **M4 — Essentia & Infusion.** Crucible, distillation, tube/suction network, jars; infusion altar with instability + symmetry; flux consequences live.
6. **M5 — Golems.** Assembly bench, three body materials, porter/harvest/guard seals, brain framework.
7. **M6 — Warp, Taint & Eldritch.** Warp counters + event table; taint spread + purification; eldritch dimension + boss; gauntlet casting tier.
8. **M7 — Polish & Ecosystem.** Sounds, advancement tree, guidebook pass, Jade/WTHIT plugins, config surface, pack-dev docs for the JSON formats.
9. **M8 — Version expansion.** Add the current-latest 1.21.x node pair (one `mc(...)` line + per-node properties + shim work); evaluate a 1.20.1 backport by demand — backports are where Stonecutter earns its keep, but only if the audience exists.

---

## 7. Risks & Open Questions

**Scope is the boss fight.** This is a 12–24 month solo project at hobby pace. The milestone order is chosen so M3 is a shippable, fun alpha and every later system is additive. Resist starting golems early; they're charisma, not skeleton.

**Architectury API longevity.** If it lags a future MC version, the mitigation is Rule 2's service layer plus Stonecutter's `dependencies[]` predicates — worst case, newer nodes swap to direct loader APIs behind the same interfaces while older nodes keep Architectury.

**Attachment/data divergence** between loaders is the most likely source of subtle bugs (sync timing, death persistence). Write a small cross-loader test checklist: login sync, dimension change, death with keepInventory off, LAN client join.

**Name/IP.** Original name, original assets, original aspect names and lore text. "Inspired by Thaumcraft" in the description is fine; anything closer isn't. Name collisions checked on Modrinth/CurseForge 2026-07-14 (see note at top); nothing registered yet.

**Open questions to settle before M1** (name/id settled 2026-07-14; language locked to Java 21 in CLAUDE.md): art direction (TC's engraved-brass look vs something new — affects every texture); whether aspect *names* use invented Latin-alikes or plain English (Latin-alikes read as homage without copying TC's exact set).

---

## 8. First Session Checklist

1. ~~Pick the name~~ — done 2026-07-14: `new_age_thaum`, slugs verified free; register nothing yet.
2. Clone the Stonecraft template, rename packages to `io.github.minerguy341.new_age_thaum`, set the 2-node matrix.
3. Install the Stonecutter IntelliJ plugin.
4. Prove the loop: one item registered via Architectury `DeferredRegister`, one `//? if fabric/neoforge` guarded log line, `runClient` on both nodes, chiseled build in CI.
5. Tag `m0` and start the aspect codec.
