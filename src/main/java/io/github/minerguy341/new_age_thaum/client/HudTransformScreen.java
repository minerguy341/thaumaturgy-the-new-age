package io.github.minerguy341.new_age_thaum.client;

import io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The {@code /thaum hud} HUD transform editor (v1: position + scale). Renders the two wand
 * bars as draggable mock elements — main-hand (emitter left) and off-hand (emitter right) —
 * each an independent {@link HudLayout}. Drag to move (with parallel snapping between the
 * two), scroll to scale (optionally linked), then Save. Rotation is a planned v2.
 *
 * <p>Nothing here is verifiable without a client; the drag/snap math wants in-game testing.
 */
public final class HudTransformScreen extends Screen {
    private static final int ALIGN_PX = 8;         // horizontal centre-align snap threshold
    private static final int SNAP_PX = 5;          // vertical-gap snap threshold
    private static final int MIN_GAP = 1;          // never let the two bars overlap when snapped
    private static final int[] GAP_SET = {2, 8, 16, 26}; // snappable edge-to-edge gaps (incl. default)
    private static final double SCALE_STEP = 0.05;

    private final HudLayout main = WandVisHud.layout(false);
    private final HudLayout off = WandVisHud.layout(true);
    private boolean scaleLink = NewAgeThaumConfig.hudWandScaleLink;
    private final WandVisHud.BarData mock = WandVisHud.mockBar(false);

    private HudLayout dragged;   // the bar currently being dragged, or null
    private int grabDX;
    private int grabDY;

    /** A bar's on-screen content box in native HUD pixels. */
    private record Box(int x, int y, int w, int h) {
    }

    public HudTransformScreen() {
        super(Component.translatable("screen.new_age_thaum.hud_editor"));
    }

    @Override
    protected void init() {
        WandVisHud.editorActive = true;
        int y = height - 26;
        addRenderableWidget(Button.builder(Component.translatable("screen.new_age_thaum.hud_editor.reset"),
                b -> resetDefaults()).bounds(width / 2 - 154, y, 74, 20).build());
        addRenderableWidget(Button.builder(linkLabel(), b -> {
            scaleLink = !scaleLink;
            b.setMessage(linkLabel());
        }).bounds(width / 2 - 76, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.new_age_thaum.hud_editor.save"),
                b -> {
                    WandVisHud.saveLayouts(main, off, scaleLink);
                    onClose();
                }).bounds(width / 2 + 28, y, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                b -> onClose()).bounds(width / 2 + 92, y, 60, 20).build());
    }

    @Override
    public void removed() {
        WandVisHud.editorActive = false;
    }

    private Component linkLabel() {
        return Component.translatable(scaleLink
                ? "screen.new_age_thaum.hud_editor.link_on"
                : "screen.new_age_thaum.hud_editor.link_off");
    }

    /** The other bar (the one {@code l} isn't). */
    private HudLayout other(HudLayout l) {
        return l == main ? off : main;
    }

    /** The bar's current on-screen content box. */
    private Box box(HudLayout l) {
        int w = WandVisHud.contentW(l.scale);
        int h = WandVisHud.contentH(l.scale, false);
        return new Box(l.screenX(width, w), l.screenY(height, h), w, h);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        // Both mock bars at their edited layouts.
        drawBar(g, main, true, dragged == main || isOver(main, mouseX, mouseY));
        drawBar(g, off, false, dragged == off || isOver(off, mouseX, mouseY));
        g.drawCenteredString(font, this.title, width / 2, 10, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.new_age_thaum.hud_editor.hint"),
                width / 2, 24, 0xFFB9A8D8);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawBar(GuiGraphics g, HudLayout layout, boolean tipLeft, boolean highlight) {
        Box b = box(layout);
        if (highlight) {
            g.fill(b.x - 2, b.y - 2, b.x + b.w + 2, b.y + b.h + 2, 0x3357E8D8);
            g.renderOutline(b.x - 2, b.y - 2, b.w + 4, b.h + 4, 0xFF7FE8D8);
        }
        WandVisHud.drawBar(g, b.x, b.y, layout.scale, mock, tipLeft);
    }

    private boolean isOver(HudLayout layout, double mx, double my) {
        Box b = box(layout);
        return mx >= b.x - 2 && mx <= b.x + b.w + 2 && my >= b.y - 2 && my <= b.y + b.h + 2;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            // Prefer the top (off-hand) bar if they overlap.
            for (HudLayout l : new HudLayout[]{off, main}) {
                if (isOver(l, mx, my)) {
                    dragged = l;
                    Box b = box(l);
                    grabDX = (int) Math.round(mx) - b.x;
                    grabDY = (int) Math.round(my) - b.y;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragged != null) {
            applyDrag(dragged, other(dragged), mx, my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    private void applyDrag(HudLayout d, HudLayout o, double mx, double my) {
        int dw = WandVisHud.contentW(d.scale);
        int dh = WandVisHud.contentH(d.scale, false);
        int rawX = (int) Math.round(mx) - grabDX;
        int rawY = (int) Math.round(my) - grabDY;

        // Parallel snapping to the other bar (unless Shift = freehand).
        if (!hasShiftDown()) {
            Box ob = box(o);
            if (Math.abs((rawX + dw / 2) - (ob.x + ob.w / 2)) <= ALIGN_PX) {
                rawX = ob.x + ob.w / 2 - dw / 2;                 // align centres (parallel)
                boolean above = (rawY + dh / 2) < (ob.y + ob.h / 2);
                int gap = above ? ob.y - (rawY + dh) : rawY - (ob.y + ob.h);
                int snapped = snapGap(gap);
                if (snapped != Integer.MIN_VALUE) {
                    rawY = above ? ob.y - dh - snapped : ob.y + ob.h + snapped;
                }
            }
        }
        // Write back as an offset under the current anchor (re-anchored on release).
        d.offX = rawX - d.anchorPxX(width, dw);
        d.offY = rawY - d.anchorPxY(height, dh);
    }

    /** Nearest snappable gap within threshold (never below MIN_GAP), or MIN_VALUE for none. */
    private static int snapGap(int gap) {
        int best = Integer.MIN_VALUE;
        int bestErr = SNAP_PX + 1;
        for (int g : GAP_SET) {
            int err = Math.abs(g - gap);
            if (err < bestErr) {
                bestErr = err;
                best = g;
            }
        }
        if (bestErr <= SNAP_PX) {
            return Math.max(MIN_GAP, best);
        }
        return gap < MIN_GAP ? MIN_GAP : Integer.MIN_VALUE;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragged != null) {
            dragged.reanchorNearest(width, height,
                    WandVisHud.contentW(dragged.scale), WandVisHud.contentH(dragged.scale, false));
            dragged = null;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        HudLayout target = isOver(off, mx, my) ? off : isOver(main, mx, my) ? main : null;
        if (target != null && scrollY != 0) {
            target.scale += Math.signum(scrollY) * SCALE_STEP;
            target.clampScale(WandVisHud.MIN_SCALE, WandVisHud.MAX_SCALE);
            if (scaleLink) {
                other(target).scale = target.scale;
            }
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private void resetDefaults() {
        main.set(new HudLayout(0.5, 1.0, 0, -41, 0.75));
        off.set(new HudLayout(0.5, 1.0, 0, -56, 0.75));
        scaleLink = true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
