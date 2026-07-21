---
name: minecraft-ui-design
description: >
  Design and implement Minecraft mod GUIs: container screens, menus, custom widgets,
  scrollable lists, tooltips, drag-and-drop, custom 2D/3D rendering inside a screen, and
  the loader-specific registration traps that silently break them. Use this whenever the
  user wants a screen, GUI, UI, menu, HUD overlay, or any in-game interface for a mod
  (1.20+, Fabric or NeoForge, Architectury or not) — including "make a block open a UI",
  "add a slot/inventory screen", "render X in the GUI", or fixing a screen that won't
  open or renders wrong. Even a "simple" screen hits these traps; consult this first.
  For the pixel-art assets a UI needs — icons, sprites, widget chrome, block/item
  textures — this skill does layout and wiring only and hands the actual pixels to the
  pixel-artist skill (see "Pixel-art assets" below).
---

# Minecraft UI Design (1.21.x, multi-loader)

Patterns and traps proven in the Thaumaturgy: The New Age project (Arcane Orrery: a
420x240 container screen with a scrollable list, real item slots, drag-and-drop painting
onto a rotatable 3D sphere, and animated energy currents). For custom geometry rendering
(Tesselator, projection, culling, animation) read
[references/gui-3d-rendering.md](references/gui-3d-rendering.md) when the screen needs
more than sprites and text.

## Pixel-art assets → the pixel-artist skill

This skill owns **layout, widget code, registration, and wiring**. It does **not** own
pixels. Any bitmap the UI ships — HUD icons, the aspect/primal glyphs, widget chrome and
frames, button faces, background panels drawn from a texture, and of course block/item
textures — is authored by the **`pixel-artist` skill**, not hand-rolled here and not left
as a raw-`fill` placeholder in shipped work.

- **Where it lives:** the `pixel-artist` skill (`.claude/skills/pixel-artist`) in the
  user's **`minerguy341/Pixel-Art-Aide`** repo, backed by its `aide` Python toolkit. If
  that repo isn't already in the workspace, clone it first, then invoke the skill.
- **What you hand it:** the subject, target size (16/32/64/128), whether it's a tiling
  block or a cutout item/icon, and the palette/style (for Thaumaturgy, style card
  `styles/thaumaturgy.md`, which mirrors `docs/art-direction.md` — the upstream source of
  truth; don't invent palette values).
- **What it hands back:** a rendered PNG plus its editable `.pxg` source, after its own
  render → analyze → self-critique → contact-sheet → user-approval loop. Drop the PNG into
  the resource pack (`assets/<modid>/textures/…`) and wire it up here — sprite/atlas entry,
  `blit`/`GuiGraphics`, model json.
- **The boundary line:** raw `graphics.fill` rectangles are fine for programmatic chrome,
  quick prototypes, and geometry-driven bars (e.g. the vis HUD's fill bars). The moment the
  UI wants an actual *drawn* asset — an icon, a glyph, a textured frame — that is a
  pixel-artist job. Don't blur the two: keep the pixels in `.pxg` sources over there, keep
  the wiring here.
- **Honesty carries over:** pixel-artist verifies previews and metrics, never in-game
  appearance; this skill verifies compile + client-boot, never rendering. Neither claims a
  human-eyeball check it didn't do — hand off with what each actually verified.

## Architecture: menu + screen + server authority

For any screen attached to a block or item inventory, use the vanilla container triad —
it buys slot sync, shift-click, and persistence for free:

- **`AbstractContainerMenu`** with real `Slot`s backed by the block entity's `Container`.
  Item contents then sync via vanilla slot logic — no custom packets for the inventory.
  Implement `quickMoveStack` (players will shift-click immediately).
- **`AbstractContainerScreen`** for rendering. In 1.21.1 `Screen.renderables` is private:
  draw panel chrome in `renderBg`, text in `renderLabels` (its pose is already translated
  to leftPos/topPos — draw at slot-relative coordinates). Buttons go through
  `addRenderableWidget` in `init()`.
- **Extra opening data** (e.g. the BlockPos) via Architectury `MenuRegistry.ofExtended` +
  `openExtendedMenu`, read back in the client menu constructor from the `FriendlyByteBuf`.
- **Edits are optimistic client-side, authoritative server-side.** The client mutates its
  view immediately (snappy) and sends a small C2S payload; the server validates
  (reach, permissions, costs) and on rejection calls
  `player.containerMenu.broadcastFullState()` — vanilla resync rolls the client back
  without custom rollback code.

## The registration-timing trap (NeoForge)

Menu screen factories have exactly one safe registration window on NeoForge, and missing
it fails in two different silent ways:

- Client init is **too early**: `RegistrySupplier.get()` throws "Registry Object not
  present" (registries not populated yet during construction).
- CLIENT_SETUP is **too late**: `RegisterMenuScreensEvent` already fired, so the screen
  factory is never seen — right-click does nothing, log says "Failed to create screen
  for menu type".

The fix: register from the supplier's own callback, which fires exactly when the menu
type registers: `MENU_SUPPLIER.listen(type -> MenuRegistry.registerScreenFactory(type, Screen::new))`.
Related: never call `RegistrySupplier.get()` during mod construction anywhere (item
`Properties.component(...)`, color handler args — use the `Supplier` overloads). Fabric
tolerates all of this, and gametests never run client code, so **the only verification
that counts is booting the NeoForge client** (ready marker in the log: "Sound engine
started").

## Feedback rules (the difference between "broken" and "polished")

- **A silent no-op reads as a bug.** Every rejected interaction needs an audible or
  visible response — a `VILLAGER_NO` via `SimpleSoundInstance.forUI` tells the player
  "understood, refused" instead of "the mod is broken". This came directly from a
  playtest report ("I can't place aspects" — the action was correctly refused, silently).
- **Pitch-vary repeated sounds per subject** so interactions feel alive:
  `0.9f + Math.floorMod(thing.hashCode(), 6) * 0.08f` gives each item a stable chime.
- **Preview consequences before commit**: while dragging, show on the hover target what
  the drop would do (e.g. white rim = valid, grey rim = allowed but inert). Compute it
  with the same rule code the server uses, never a reimplementation.
- **Disabled ≠ hidden.** Grey out inert entries (blend toward the panel grey) but keep
  them listed; show zero-counts dimmed rather than removing rows, so the layout is stable
  and the player learns what exists.
- **State the empty state.** If the screen needs an item inserted to function, draw a
  hint line saying so; never present a dead UI.

## Widget patterns

- **Scrollable list**: `enableScissor`/`disableScissor` around the rows; wheel scroll AND
  a draggable scrollbar. Give the scrollbar its own gutter column — rows overlapping the
  bar was a real playtest complaint (couldn't grab it). Thumb size
  `max(12, listH * listH / contentH)`.
- **Tooltips**: `renderComponentTooltip` on hover with a `List<Component>`; build lines
  with `Component.translatable` + `ChatFormatting` styles. Everything user-visible goes
  through lang keys from day one.
- **Drag-and-drop**: track the payload in a field; `mouseClicked` picks up (return true to
  claim the click), `mouseDragged` just returns true while holding, `mouseReleased` drops
  and clears. Render the payload as a swatch at the cursor in `render()` after
  `super.render`.
- **Toggle buttons** re-label via `setMessage` on click (`Camera: Free` ⇄ `Camera: Lock`)
  rather than spawning separate buttons.

## Layout conventions that held up

- Fixed-size panel (e.g. 420x240) centered by `AbstractContainerScreen`; compute all
  regions in `init()` from `leftPos`/`topPos`, never hardcode absolutes.
- Title bar strip at top, functional buttons top-right, hint text bottom-center under the
  main viewport.
- Dark-purple UI palette pattern: panel fill `0xF0100A18`, chrome `0xFF241B33`, inset
  wells near-black, primary text `0xE8D9FF`, secondary `0x9A8CBF`, dimmed `0x5F5876`.

## HUD overlays (drawn over the world, not a container screen)

A `ClientGuiEvent.RENDER_HUD` overlay (Architectury) is a different beast from a screen:
no `leftPos`/`topPos`, you own the whole framebuffer, and it draws **every frame** whether
or not the player is using the feature. Proven on the wand/stave vis bar — a wand-shaped
HUD assembled from authored sprites plus dynamically-drawn chambers.

- **Gate hard and gate first.** Bail on `minecraft.options.hideGui`, a null player, and
  your feature toggle before touching anything. RENDER_HUD fires constantly; an unguarded
  overlay is a per-frame tax on every player who isn't using it.
- **Ship the pixel art, don't reimplement it.** Author chrome in the pixel-art toolkit,
  export PNGs to `assets/<ns>/textures/gui/...`, and blit them; draw only the *dynamic*
  parts (bars, fills, ticks) in code. Re-porting shading into `fill()` calls never matches
  the source and wastes the whole reason you authored art.
- **HD without the blit-signature fight.** To land 2x-detail sprites at native HUD size,
  wrap the whole element in `pose().pushPose(); translate(x, y, 0); scale(0.5f, 0.5f, 1f)`
  and draw everything in "2x space" at each sprite's native pixel size. The matrix does the
  downscale, so one basic `blit(rl, x, y, 0, 0, w, h, w, h)` covers every sprite — no
  scaling-blit overload, no per-call source-rect math. At GUI scale ≥2 (almost always) the
  extra texels land on real pixels and read crisp.
- **Porting a Pillow/PIL builder 1:1?** `ImageDraw.rectangle` is **inclusive** of both
  corners; GuiGraphics `fill(x0, y0, x1, y1, c)` is **exclusive** of `x1, y1`. Convert with
  `fill(x0, y0, x1 + 1, y1 + 1, c)` or every rect is a pixel short — this alone kept the
  in-game chambers pixel-identical to the approved mockup.
- **Mirrored/facing variants: re-light, don't blind-flip.** An element that points left in
  one hand and right in the other can't just be `FLIP_LEFT_RIGHT`-ed — that mirrors the
  lighting too (a top-left key light becomes top-right). Author a variant rendered with the
  light-x reversed, *then* flip it, so the shape mirrors but the shine stays top-left. Only
  x-asymmetric shading (domes, bevels) needs this; flat top-lit pieces flip fine.

### Moving the vanilla rows (health / hunger / mount) — loader-split, no library

There is **no cross-loader library** that repositions the vanilla status bars; the HUD
"libraries" are end-user mods or add-only layer registries. The standard approach is a
per-loader hook behind a `platform/` service:

- **NeoForge — no mixin.** `RenderGuiLayerEvent.Pre` for `VanillaGuiLayers.PLAYER_HEALTH` /
  `FOOD_LEVEL` / `ARMOR_LEVEL` / `AIR_LEVEL` / `VEHICLE_HEALTH`: pose-translate to move a
  layer, `setCanceled(true)` to hide it. (NeoForge splits vanilla `Gui` into per-element
  layers; a cancel-and-re-render wrapper doesn't stack across mods, but a plain offset does.)
- **Fabric — a small `Gui` mixin.** Fabric API's HUD layer system only adds/reorders/hides;
  it can't re-anchor the vanilla bars. Inject `@At("HEAD")`/`@At("RETURN")` on
  `Gui#renderPlayerHealth` (draws health+armor+hunger+air as one on unpatched vanilla) and
  `Gui#renderVehicleHealth`, and push/translate/pop the pose. The method split differs from
  NeoForge — don't try to share one mixin.
- Gate the offset in common code on your held-item condition; only the render hook is
  loader-specific. Skipping this entirely — parking your overlay in the free space *above*
  the health row — is a legitimate v1 that needs no mixin at all.

## Verification loop

After any UI change: `chiseledBuild` + gametests for the logic, then boot the NeoForge
client in the background, watch the log for "Sound engine started" vs crash markers, and
hand off to the user for the visual check — rendering bugs (invisible geometry, wrong
alpha, culled faces) produce zero log output and only a human screenshot catches them.
