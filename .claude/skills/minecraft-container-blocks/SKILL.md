---
name: minecraft-container-blocks
description: >
  Trap list for blocks whose BlockEntity holds items (Container/inventory blocks) on
  Fabric/NeoForge 1.21.x. Use this whenever the task adds a block with an inventory or
  item slot, touches hopper/automation interaction with a mod block, debugs "items
  vanish when the block is broken/moved", or wires BlockEntity save/load/sync for a
  container. Consult it BEFORE writing the block class — the M4 systems (crucible,
  alembics, jars, infusion pedestals) are all this shape, and every trap below shipped
  as a real bug in the orrery before the m2 review.
---

# Container Block-Entity Traps (1.21.x)

Worked examples: `content/ResearchSphereBlock.java` + `content/ArcaneOrreryBlockEntity.java`
(one paper slot backing a menu), verified by `gametest/OrreryValidationGameTest.java`.

## 1. Breaking the block must spill the contents

`EntityBlock` + a Container BE + no `onRemove` override = the contents are silently
deleted on break, explosion, or piston. The orrery shipped this way — relocating it
destroyed hours of painted research. The fix is three lines and belongs in EVERY
container block:

```java
@Override
protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
    if (!state.is(newState.getBlock())
            && level.getBlockEntity(pos) instanceof ArcaneOrreryBlockEntity orrery) {
        Containers.dropContents(level, pos, orrery);
    }
    super.onRemove(state, level, pos, newState, movedByPiston);
}
```

The `!state.is(newState.getBlock())` guard keeps same-block state changes from
spilling. Gametest: place, fill, `helper.destroyBlock(pos)`,
`helper.assertItemEntityPresent(item, pos, 2.0)`.

## 2. Hoppers ignore your menu Slot rules

`Slot.mayPlace`/`getMaxStackSize` overrides only govern the MENU. Hoppers talk to the
`Container` directly, whose defaults accept anything and give up everything. Every
restriction must exist twice:

```java
@Override
public boolean canPlaceItem(int slot, ItemStack stack) {
    return slot == 0 && paper.isEmpty() && stack.getItem() instanceof ResearchPaperItem;
}

@Override
public boolean canTakeItem(Container target, int slot, ItemStack stack) {
    return false; // precious contents: no silent automation extraction
}
```

Decide extraction policy deliberately — for a workstation holding player work, block
it; for a processing machine, allow outputs only.

## 3. Sync economy: setChanged vs sendBlockUpdated

- `setChanged()` alone = persisted, no packet. Correct when the only consumer is a
  menu/screen — vanilla slot sync already carries the ItemStack (components included)
  to every viewer. The orrery's per-cell edits used to fire a full
  `ClientboundBlockEntityDataPacket` to all tracking clients per click, duplicating
  slot sync with a payload that grew with sphere fill.
- `sendBlockUpdated(...)` (via a `sync()` helper) only where in-WORLD rendering needs
  the data (a future BER): content insert/remove (`setPaper`) and rare state flips
  (`markSolved`), not per-edit.
- `setChanged()` is null-level safe, so BE logic stays testable on a bare
  `new MyBlockEntity(BlockPos.ZERO, state)`.

## 4. Legacy/migrated data: loadAdditional has no level

`loadAdditional` runs before the BE joins a level, so lazy initialization keyed on the
level (the orrery stamps a puzzle onto inserted papers) never runs for data loaded from
disk or structure NBT. Migrate in `setLevel`:

```java
@Override
public void setLevel(Level level) {
    super.setLevel(level);
    if (stampPuzzleIfNeeded()) { // no-op client-side and when already stamped
        setChanged();
    }
}
```

And make the server handler REJECT operations on un-migrated state rather than
"skipping the checks that need it" — see `minecraft-c2s-validation` §4.

## 5. Round-trip completeness

`saveAdditional`/`loadAdditional` + `getUpdateTag`/`getUpdatePacket`
(`ClientboundBlockEntityDataPacket.create(this)`) as in the orrery. Gametest the round
trip AND that removed items carry their components with them
(`gametest/ArcaneOrreryPersistenceGameTest.java`).
