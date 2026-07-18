---
name: minecraft-reload-lifecycle
description: >
  Sided statics and datapack-reload timing for mods with reloadable data registries
  (Fabric/NeoForge via Architectury). Use this whenever the task adds a
  datapack-reloadable registry or static cache, syncs reloadable data to clients, or
  debugs "wrong/stale data after /reload", "wrong data after switching servers",
  "tooltips show another world's values", or singleplayer-only weirdness that
  multiplayer doesn't reproduce. Consult it BEFORE adding any static mutable store —
  all four trap classes below shipped as real bugs in this repo's aspect system.
---

# Reload Lifecycle & Sided Statics

This mod's reloadable stores are volatile statics swapped whole
(`AspectRegistry`, `AspectAssignments`, `CodexRegistry`, `WandMaterialRegistry`) and
synced to clients via payloads. That architecture is sound, but it carries four traps.

## 1. Client mirrors must reset on disconnect

Synced statics are per-server state. Without a reset, a vanilla server joined next
(which never syncs) renders the PREVIOUS server's aspects in tooltips and reports its
point balances. One hook clears everything (`client/NewAgeThaumClient.java`):

```java
ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
    ClientPlayerProgress.set(PlayerProgress.EMPTY);
    AspectRegistry.reload(Map.of());
    AspectAssignments.accept(Map.of(), Map.of());
    CodexRegistry.reload(Map.of());
    WandMaterialRegistry.reload(Map.of());
    AspectResolver.invalidate();
});
```

Clearing is always safe: singleplayer repopulates via reload listeners at world load,
multiplayer via the join sync. Every NEW synced store must be added to this hook.

## 2. Tags and recipes rebind AFTER your reload listeners

On `/reload`, custom reload listeners run first; vanilla binds new tags to holders and
swaps recipes after the whole reload completes (client: after the tag packet arrives).
Any cache whose values depend on tags or recipes and which only invalidates inside a
reload listener can be repopulated in that window with NEW assignments + OLD
tags/recipes — and stick for the session. Fix: also invalidate on the tags-updated
event, which fires on both logical sides on both loaders:

- NeoForge: `NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent e) -> ...)`
  (`platform/neoforge/NewAgeThaumNeoForge.java`)
- Fabric: `CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> ...)`
  (`platform/fabric/NewAgeThaumFabric.java`)

Architectury does not abstract this event — it goes through the platform entrypoints.

## 3. Singleplayer shares your statics between logical sides

The integrated server thread and the client render thread hit the same static caches.
Consequences:

- Swap stores atomically (volatile field, immutable `Map.copyOf` values) — already the
  house pattern; keep it for new stores.
- NEVER use a shared-cache sentinel for re-entrancy. `AspectResolver` used to put
  `EMPTY` in the cache as a recipe-cycle guard; the other side could observe it
  mid-inference as a final answer (an Aetherlens scan in that window recorded the scan
  and granted zero points, permanently). The fix is a per-thread in-progress set with
  try/finally (`core/aspect/AspectResolver.java`) so exceptions can't poison the cache
  either.
- "Works in multiplayer, breaks in singleplayer" (or only under tooltip-while-scanning
  timing) is the smell of this class.

## 4. Reload input order is not deterministic

`SimpleJsonResourceReloadListener.apply` hands you a plain `HashMap` — iteration is
hash order, different per JVM. Anything order-sensitive must sort:

- Iterate `files.entrySet().stream().sorted(Map.Entry.comparingByKey())`
  (`core/codex/CodexReloadListener.java`, `core/aspect/AspectAssignments.java`).
- Log duplicate matches so pack authors see which entry won (`AspectAssignments`).
- Preserve order THROUGH sync: the payload carries an ordered list — collect it into a
  `LinkedHashMap` on the receiver, not a `HashMap` (`NewAgeThaumNetwork` codex
  receiver). A HashMap on either end re-scrambles everything.

## Adding a new reloadable registry: the checklist

1. Volatile static store, whole-map swap, immutable values.
2. Reload listener sorts file iteration; logs conflicts; syncs to all players in
   `apply` (guard `GameInstance.getServer() != null`).
3. Join sync in the `PLAYER_JOIN` hook (`NewAgeThaum.init`), through the
   `sendIfPossible` guard (mock players have no channel — see `minecraft-gametests`).
4. Receiver preserves payload order; decode caps per `minecraft-c2s-validation`.
5. Client reset hook entry (§1).
6. If any cache derives from it + tags/recipes: invalidate on tags-updated too (§2).
