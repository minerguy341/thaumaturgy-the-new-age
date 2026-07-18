---
name: minecraft-c2s-validation
description: >
  Server-authority checklist for any client-to-server packet that edits world or player
  state (Fabric/NeoForge via Architectury NetworkManager). Use this whenever the task
  adds or touches a C2S payload, builds a screen that edits a block ("menu-driven
  minigame", "click sends an edit to the server"), or investigates a dupe, griefing,
  free-action, or packet-spam concern in a handler. Consult it BEFORE writing the
  handler — every check below was a real, shipped hole in this repo's orrery editor
  before the m2 review hardened it.
---

# C2S Handler Validation (server authority)

Worked example: `network/NewAgeThaumNetwork.java` — `handleOrreryEdit` +
`applyOrreryEdit`, the research-sphere edit path. Every rule below exists because its
absence was an exploitable bug found in review, not a hypothetical.

## The ordered checklist

Run the checks in this order; each one assumes the previous passed.

1. **Identity**: `context.getPlayer() instanceof ServerPlayer player` — and hop to the
   game thread first (`context.queue(...)`); never touch the level from the netty thread.
2. **Reach**: `player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0` → drop.
3. **Authorization — the check everyone forgets**: reach is NOT authorization. Require
   the sender to actually be using the thing:
   ```java
   if (!(player.containerMenu instanceof ArcaneOrreryMenu menu)
           || !menu.pos().equals(payload.pos())) {
       return;
   }
   ```
   Without this, any player within 8 blocks could wipe another player's in-progress
   sphere — clears cost nothing, so it was free griefing.
4. **State exists**: if the editable state is missing (no paper, no stamped puzzle),
   REJECT — do not "skip the checks that need it". The original code nested the bounds
   check inside `if (puzzle != null)`, so puzzle-less papers accepted `cell =
   Integer.MAX_VALUE` straight into persistent item NBT.
5. **Bounds**: validate indices against the authoritative container size — never
   trusting a size the client sent, never nested under optional state (see 4).
6. **No-op detection**: an edit that changes nothing must succeed WITHOUT charging or
   rebroadcasting. Repainting a cell with the aspect it already held used to silently
   burn an observation point per click.
7. **Spend last**: only after every rejection path — `trySpend` then mutate; a rejected
   edit must leave zero state change (gametest this: assert points unchanged after each
   rejected branch).
8. **Minimal sync**: if vanilla menu-slot sync already carries the mutated data to every
   viewer, `setChanged()` is enough; a full `sendBlockUpdated` per click is duplicate
   bandwidth that scales with state size.

## Decode side (both directions)

- A peer can claim any varint count before sending a single element:
  `new ArrayList<>(claimedCount)` is an instant OOM on a hostile 2^31 claim. Cap every
  pre-allocation — `NetworkLimits.safeCapacity(count)` (`network/NetworkLimits.java`);
  oversized claims still fail later when element reads exhaust the buffer.
- Bound indices at decode too (`ResearchPuzzle.read`, `ResearchSphereData.read` clamp
  frequency to the codec's range and drop out-of-range cells) — decode-side junk
  otherwise persists into components and crashes `grid.cell()`-style lookups later.
- Threat directions: S2C decode hardening protects clients from hostile servers; C2S
  handler validation protects the server from hacked clients. Both are needed.

## Client side of the same contract

- Mirror the cheap checks client-side (locked cell, affordability, no-op) so the client
  doesn't spam packets the server will reject — but treat them as UX only; the server
  re-checks everything.
- Optimistic edits + rollback: client paints immediately, server rejection triggers
  `player.containerMenu.broadcastFullState()` which resyncs slot 0 and snaps the
  client back. No bespoke rollback packet needed.
- Never send a packet for a no-op edit (`ResearchSphereScreen.paintCell`/`clearCell`
  early-return when nothing would change).

## Gametest it through the real path

Make the authoritative handler `public static` and drive accept AND reject branches
from gametests (`gametest/OrreryValidationGameTest.java`,
`gametest/AspectEconomyGameTest.java`): rejected edits changed nothing, no-ops are
free, unstamped/absent state rejects. See `minecraft-gametests` for the loader wiring.
