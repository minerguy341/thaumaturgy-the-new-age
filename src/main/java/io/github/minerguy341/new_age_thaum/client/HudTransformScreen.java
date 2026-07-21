package io.github.minerguy341.new_age_thaum.client;

import io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

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

    private int dragging = -1;   // -1 none, 0 main, 1 off
    private int grabDX;
    private int grabDY;

    public HudTransformScreen() {
        super(Component.translatable("screen.new_age_thaum.hud_editor"));
    }

    @Override
    protected void init() {
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

    private Component linkLabel() {
        return Component.translatable(scaleLink
                ? "screen.new_age_thaum.hud_editor.link_on"
                : "screen.new_age_thaum.hud_editor.link_off");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        // Both mock bars at their edited layouts.
        drawBar(g, main, true, dragging == 0 || isOver(main, mouseX, mouseY));
        drawBar(g, off, false, dragging == 1 || isOver(off, mouseX, mouseY));
        g.drawCenteredString(font, this.title, width / 2, 10, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.new_age_thaum.hud_editor.hint"),
                width / 2, 24, 0xFFB9A8D8);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawBar(GuiGraphics g, HudLayout layout, boolean tipLeft, boolean highlight) {
        int w = WandVisHud.contentW(layout.scale);
        int h = WandVisHud.contentH(layout.scale, false);
        int x = layout.screenX(width, w);
        int y = layout.screenY(height, h);
        if (highlight) {
            g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x3357E8D8);
            g.renderOutline(x - 2, y - 2, w + 4, h + 4, 0xFF7FE8D8);
        }
        WandVisHud.drawBar(g, x, y, layout.scale, mock, tipLeft);
    }

    private boolean isOver(HudLayout layout, double mx, double my) {
        int w = WandVisHud.contentW(layout.scale);
        int h = WandVisHud.contentH(layout.scale, false);
        int x = layout.screenX(width, w);
        int y = layout.screenY(height, h);
        return mx >= x - 2 && mx <= x + w + 2 && my >= y - 2 && my <= y + h + 2;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            // Prefer the top (off-hand) bar if they overlap.
            for (int i : new int[]{1, 0}) {
                HudLayout l = i == 0 ? main : off;
                if (isOver(l, mx, my)) {
                    dragging = i;
                    int w = WandVisHud.contentW(l.scale);
                    int h = WandVisHud.contentH(l.scale, false);
                    grabDX = (int) Math.round(mx) - l.screenX(width, w);
                    grabDY = (int) Math.round(my) - l.screenY(height, h);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging >= 0) {
            HudLayout d = dragging == 0 ? main : off;
            HudLayout o = dragging == 0 ? off : main;
            applyDrag(d, o, mx, my);
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
            int ow = WandVisHud.contentW(o.scale);
            int oh = WandVisHud.contentH(o.scale, false);
            int ox = o.screenX(width, ow);
            int oy = o.screenY(height, oh);
            if (Math.abs((rawX + dw / 2) - (ox + ow / 2)) <= ALIGN_PX) {
                rawX = ox + ow / 2 - dw / 2;                      // align centres (parallel)
                boolean above = (rawY + dh / 2) < (oy + oh / 2);
                int gap = above ? oy - (rawY + dh) : rawY - (oy + oh);
                int snapped = snapGap(gap);
                if (snapped != Integer.MIN_VALUE) {
                    rawY = above ? oy - dh - snapped : oy + oh + snapped;
                }
            }
        }
        // Write back as an offset under the current anchor (re-anchored on release).
        d.offX = rawX - (int) Math.round(d.anchorX * (width - dw));
        d.offY = rawY - (int) Math.round(d.anchorY * (height - dh));
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
        if (dragging >= 0) {
            HudLayout d = dragging == 0 ? main : off;
            d.reanchorNearest(width, height, WandVisHud.contentW(d.scale), WandVisHud.contentH(d.scale, false));
            dragging = -1;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        HudLayout target = isOver(off, mx, my) ? off : isOver(main, mx, my) ? main : null;
        if (target != null && scrollY != 0) {
            double delta = Math.signum(scrollY) * SCALE_STEP;
            target.scale = Mth.clamp(target.scale + delta, WandVisHud.MIN_SCALE, WandVisHud.MAX_SCALE);
            if (scaleLink) {
                (target == main ? off : main).scale = target.scale;
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
