---
name: stonecutter-multiloader
description: >
  The build, branch, and verification workflow for multi-loader Minecraft mods on
  Stonecutter + the Stonecraft plugin + Architectury (Fabric + NeoForge from one common
  codebase). Use this at the START of any session in a Stonecutter mod repo, and whenever
  the task involves building, running gametests, booting a test client, adding
  registrations (items/blocks/components/menus), switching loader nodes, committing, or
  scaffolding a new multi-loader mod. Also consult it when a build fails after a node
  switch, when NeoForge crashes with "Registry Object not present", or when something
  works on Fabric but breaks on NeoForge.
---

# Stonecutter Multi-Loader Workflow

Proven across the Thaumaturgy: The New Age project (Stonecutter 0.9.x, Stonecraft plugin
`gg.meza.stonecraft`, Architectury, Java 21, Mojmap, nodes `1.21.1-fabric` +
`1.21.1-neoforge`). Scaffold new projects from the meza/Stonecraft-template.

## The rules that keep the repo sane

- **One VCS node.** Stonecutter has an "active" node; this project standardizes on the
  NeoForge node as the VCS version. Always return to it before committing — a node
  switch rewrites the preprocessor comments in-place, and committing from the wrong node
  poisons the diff. A node-switch round trip must leave `git status` clean.
- **Definition of green: `chiseledBuild` + `chiseledGameTest`** — both compile AND test
  every node. Never call a change done on the strength of one loader.
- **JAVA_HOME must point at a JDK 21** for every gradle invocation (on this machine:
  `C:\Program Files\Java\jdk-21.0.11`; the default `java` is 22 and fails). Prefix
  commands: `JAVA_HOME="C:\Program Files\Java\jdk-21.0.11" ./gradlew ...`.
- **Dependencies go in `versions/dependencies/<mc>.properties`**, consumed via
  `mod.prop()` in `build.gradle.kts` — not hardcoded in the buildscript.

## Loader-specific code: preprocessor comments

Common code carries both branches; Stonecutter toggles which is live:

```java
//? if neoforge {
@net.neoforged.neoforge.gametest.GameTestHolder(MOD_ID)
//?} else {
/*fabric-only code lives in a comment on the neoforge node
*///?}
```

Keep these blocks small and rare — the platform-bridge pattern (an interface in common,
one impl per loader installed at init) is better than scattering conditionals.

## Architectury registration rules (the crash class that only hits NeoForge)

- All registration through `DeferredRegister`; common code never touches a loader
  registry directly.
- **Never call `RegistrySupplier.get()` during mod construction or client init.** On
  NeoForge, registries populate AFTER the mod constructor, so an eager `.get()` throws
  `Registry Object not present`. Fabric registers synchronously and tolerates it, and
  server gametests never run client code — so **Fabric + gametests both passing proves
  nothing about this bug class.** Known traps:
  - `Item.Properties().component(TYPE.get(), ...)` at registration time — don't.
  - `ColorHandlerRegistry.registerItemColors(handler, item.get())` — use the
    `Supplier` overload and pass the `RegistrySupplier` itself.
  - Menu screen factories: client init is too early, CLIENT_SETUP is too late
    (`RegisterMenuScreensEvent` already fired → screen silently never opens). The one
    safe window: `MENU_SUPPLIER.listen(type -> MenuRegistry.registerScreenFactory(...))`.
- Client-only code is isolated behind `EnvExecutor.runInEnv(Env.CLIENT, () -> Client::init)`
  or `level.isClientSide` guards, plus a thin client-package seam class. Never reference
  `Screen`/`Minecraft` from common classes that load on a dedicated server.

## The verification loop

1. Make the increment small; build it.
2. `chiseledBuild` then `chiseledGameTest` (both nodes, always).
3. **Boot the NeoForge client** in the background and watch the log:
   ready marker = `Sound engine started`; failure markers = `Registry Object not
   present`, `Exception message`, `Failed to create screen`, `GAME CRASHED`,
   `BUILD FAILED`. Grep with `-a` (Minecraft logs can contain binary bytes that make
   grep report "Binary file matches" instead of the line).
4. Hand off to the user for the in-game check — rendering and UX bugs produce zero log
   output.
5. Commit only after the user confirms; push the feature branch; merge to main on
   approval. Tag milestones only after in-game smoke tests **on both loaders**.

Windows dev-client cleanup: `taskkill //F //IM java.exe` (double slashes in Git Bash).

## Remote/cloud sessions cannot build this project

Claude Code web/remote environments (as of 2026-07) have an egress policy that blocks
`services.gradle.org` and every modding maven (`maven.fabricmc.net`,
`maven.neoforged.net`, `maven.architectury.dev`, `libraries.minecraft.net`,
`piston-meta.mojang.com`) — only Maven Central is reachable. `./gradlew` cannot even
download the wrapper, so `chiseledBuild`/`chiseledGameTest` CANNOT run there. Check
early (`curl -s -o /dev/null -w '%{http_code}' https://maven.fabricmc.net/`) instead of
discovering it after writing code. In such a session:

1. Stick to APIs already proven in this repo (or stable vanilla/Architectury surface);
   do not guess at unfamiliar signatures you cannot compile against.
2. Syntax-check edited files with plain `javac -proc:none` (missing-symbol errors are
   expected noise; you are looking for parse errors).
3. Hand off explicitly: state that the build was NOT run and list the exact commands —
   never claim green. The definition of done in CLAUDE.md still requires a real
   `chiseledBuild` + `chiseledGameTest` somewhere.

### CI is the real build gate when the sandbox can't compile
When the sandbox can't run Gradle, GitHub Actions (`.github/workflows/build.yml` runs
`chiseledBuild` + `chiseledGameTest` on every push and PR) is the ONLY place your code
actually compiles and the gametests actually run. Treat it as the build gate, not a
formality: after pushing, poll the workflow run (`actions_list` → newest run →
`actions_get`/`get_job_logs`) and read the result before telling the user it's done. A
green run is the remote-session equivalent of a local `chiseledBuild`; a red one is your
compile error — read the log, fix, push again, re-poll. Do NOT declare a task done on
"it should compile" — wait for the actual CI verdict. (This is how the reconciled Arcane
Worktable merge was verified: two CI compile errors — a wrong `SimpleContainerData`
import, a missing `Primals` constant — surfaced only in the Actions log, not locally.)

### Diff against `main` before landing a long-lived branch
A feature branch that has lived a while can silently collide with work that landed on
`main` in parallel — not a textual merge conflict, but two independent implementations of
the SAME system. Before merging, actually `git fetch origin main` and read what changed in
the areas you touched. (Here `main` had independently grown a per-primal `WandVis` float
record + `WandRecharge`; our branch carried an `AspectBag`-based vis system for the same
wands. Nothing textually conflicted, but both couldn't coexist.) When you find a competing
system, adopt the one already on `main` as the base and rewrite your feature onto its API,
rather than forcing your parallel version through — main is shared ground, your branch is
not. Catching this at merge time (by diffing) is far cheaper than after a green-looking
merge quietly ships two rival APIs. For the full pre-flight + pre-land procedure (ranking
branches by recent activity, adopt-vs-rebuild triage), see the `parallel-session-survey` skill.

## Ground-truth unfamiliar APIs before writing code

Mapped-name guesses against Architectury/loader APIs waste build cycles. Inspect the
actual cached jar first:

```bash
"/c/Program Files/Java/jdk-21.0.11/bin/javap" -cp "$(find ~/.gradle/caches -name 'architectury-13.0.8.jar' | grep -v sources | head -1)" dev.architectury.event.events.common.CommandRegistrationEvent
```

(Architectury common jars print intermediary names like `class_2168` = CommandSourceStack
— that's expected; the shape of the signature is what you're verifying.)

## Related skills

- Gametest authoring and its loader-specific traps: `minecraft-gametests`.
- Data components, codecs, and network payloads: `minecraft-data-components`.
- Screens/menus/GUI rendering: `minecraft-ui-design`.
- Mod config files: `minecraft-mod-config`.
