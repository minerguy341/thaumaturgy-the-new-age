# M0 Task Spec — Walking Skeleton

**Context:** read `docs/PLAN.md` §2 (toolchain) and `CLAUDE.md` before starting. This task proves the entire build/toolchain infrastructure end-to-end with near-zero gameplay code. Success is fully verifiable from the command line, plus a short human smoke test at the end.

## Prerequisite decisions — HUMAN FILLS THESE IN BEFORE THE SESSION

Do not begin until every row has a value. If any is blank, stop and ask.

| # | Decision | Value |
|---|---|---|
| 1 | Mod name (display) | Thaumaturgy: The New Age |
| 2 | Mod id (lowercase, collision-checked on Modrinth/CurseForge) | new_age_thaum (verified free on both, 2026-07-14) |
| 3 | Root package | io.github.minerguy341.new_age_thaum |
| 4 | Toolchain: (A) Stonecraft plugin or (B) hand-rolled split scripts from rotgruengelb template | A — Stonecraft plugin (`gg.meza.stonecraft`), scaffold from meza/Stonecraft-template |
| 5 | License (MIT / LGPL-3.0 / ARR / other) | MIT |
| 6 | GitHub repo URL (for CI) | https://github.com/minerguy341/thaumaturgy-the-new-age (create before session) |

Fixed for M0 (not decisions): Java 21, Mojmap, nodes `1.21.1-fabric` + `1.21.1-neoforge`, VCS version `1.21.1-neoforge`.

## Objective

A repository where one preprocessed codebase builds and boots on both loaders, the platform-abstraction pattern is proven with real code, CI builds the full matrix, and the whole thing is tagged `m0` as the permanent known-good toolchain state.

## In scope

1. **Scaffold** from the template matching decision #4 (Stonecraft template for A, rotgruengelb/stonecutter-mod-template for B). Rename all packages, ids, and metadata to decisions #1–3. Strip template example content.
2. **Node matrix** in `settings.gradle.kts`: the two 1.21.1 nodes via a `mc(version, vararg loaders)` helper so future versions are one-line additions. Set `vcsVersion = "1.21.1-neoforge"`.
3. **Loader constants** registered with `constants.match` from the node name suffix. Prove branching with a guarded init log line that prints a different message per loader.
4. **Architectury API** wired as a dependency on both nodes (correct per-loader artifact in each `versions/{node}/gradle.properties`). Register exactly one item via `DeferredRegister` in common code — id `<modid>:proof_of_forge` or similar throwaway — and one creative tab containing it.
5. **Platform service layer proof:** a `platform/PlatformInfo` interface (`loaderName()`, `isDevelopmentEnvironment()`) with Fabric and NeoForge implementations in separate guarded files, resolved at init, logged once. This is the pattern template every future service follows.
6. **Loader metadata:** `fabric.mod.json` and `META-INF/neoforge.mods.toml` with versions templated from Gradle properties. Verify each built jar contains **only** its own loader's metadata file (`unzip -l` both jars).
7. **CI:** GitHub Actions workflow running `./gradlew chiseledBuild` on JDK 21 with Gradle caching, uploading the jars as artifacts. A matrix mirroring the node list is preferred; a single chiseled job is acceptable for M0.
8. **One GameTest** (or the simplest loader-agnostic automated test achievable) asserting the proof item is registered — establishes the test harness for later milestones.
9. **Docs:** commit `docs/PLAN.md` and `CLAUDE.md`; README stub with build instructions and license.

## Out of scope — do not touch

Aspects, codecs, research, aura, nodes, worldgen, GUIs, particles, mixins, config systems, any datapack-driven anything, any texture beyond a placeholder for the proof item. If a step seems to require one of these, the step is wrong — stop and ask.

## Acceptance criteria

- [ ] `./gradlew chiseledBuild` green locally
- [ ] CI green on the repo's default branch
- [ ] Switching active project `1.21.1-neoforge → 1.21.1-fabric → 1.21.1-neoforge` leaves `git status` clean
- [ ] Each jar ships only its own loader metadata (verified via jar listing, output pasted in the handoff)
- [ ] GameTest/registration test passes on both nodes
- [ ] Init logs show: loader-constant branch line + `PlatformInfo` line, per loader (verified in dev-run logs if available, otherwise listed for human check)
- [ ] `docs/PLAN.md`, `CLAUDE.md`, README, LICENSE committed
- [ ] Human smoke-test list produced (below), **tag `m0` only after Jacob confirms it**

## Human smoke test (agent fills in exact details at handoff)

1. `runClient` on `1.21.1-neoforge`: game reaches title screen, mod appears in mod list, log shows the two proof lines.
2. Same on `1.21.1-fabric`.
3. Creative search finds the proof item on both loaders; it can be placed in the hotbar without errors.
4. Optional: drop the fabric jar into a Modrinth-launcher Fabric 1.21.1 instance alongside Architectury API and confirm it loads outside the dev environment.
