package io.github.minerguy341.new_age_thaum.client;

import io.github.minerguy341.new_age_thaum.content.ArcaneWorktableMenu;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Arcane Worktable screen — "augment don't replace" in the Thaumcraft Arcane-Workbench idiom:
 * a familiar 3×3 grid + wand slot + result, with the vis cost shown as a RING of gauge pips
 * ENCIRCLING the grid (fills teal toward the wand's available vis, red-locks the result when it
 * can't pay). The panel is the mod's dark-purple house style (matches the Orrery), but every
 * slot is stamped from a real vanilla slot cell (referenced from minecraft's crafting_table.png
 * — nothing shipped) so slots still read as Minecraft.
 */
public class ArcaneWorktableScreen extends AbstractContainerScreen<ArcaneWorktableMenu> {
    // Vanilla crafting-table texture (256×256) — only used to stamp authentic 18×18 slot cells.
    private static final ResourceLocation VANILLA =
            ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final int SLOT_CELL_U = 29, SLOT_CELL_V = 16;

    // House palette (minecraft-ui-design skill).
    private static final int PANEL = 0xF0100A18;
    private static final int CHROME = 0xFF241B33;
    private static final int TEXT = 0xE8D9FF;
    private static final int DIM = 0x9A8CBF;
    private static final int TEAL = 0xFF7FE8D8;
    private static final int TEAL_TEXT = 0x7FE8D8;
    private static final int PIP_OFF = 0xFF37304A;
    private static final int WARN = 0xFFE08A8A;
    private static final int WARN_TEXT = 0xE08A8A;

    private static final int RING_PIPS = 16;

    private ItemStack ghostWand = ItemStack.EMPTY;

    public ArcaneWorktableScreen(ArcaneWorktableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 194;
        this.imageHeight = 200;
        this.inventoryLabelY = ArcaneWorktableMenu.INV_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        g.fill(x, y, x + imageWidth, y + 14, CHROME);

        // Authentic vanilla slot cells behind every slot.
        for (Slot slot : menu.slots) {
            g.blit(VANILLA, x + slot.x - 1, y + slot.y - 1, SLOT_CELL_U, SLOT_CELL_V, 18, 18);
        }

        drawVisRing(g, x, y);

        // Ghost-wand hint in the empty wand slot.
        if (!menu.hasWand()) {
            if (ghostWand.isEmpty()) {
                ghostWand = new ItemStack(ModRegistries.WAND.get());
            }
            int wx = x + ArcaneWorktableMenu.WAND_X;
            int wy = y + ArcaneWorktableMenu.WAND_Y;
            g.renderFakeItem(ghostWand, wx, wy);
            g.fill(wx, wy, wx + 16, wy + 16, 0x90100A18); // dim to a ghost
        }
    }

    /** The vis gauge: a ring of pips around the grid, filling teal toward wand-vis / cost. */
    private void drawVisRing(GuiGraphics g, int x, int y) {
        int status = menu.status();
        boolean arcane = status == ArcaneWorktableMenu.STATUS_ARCANE_READY
                || status == ArcaneWorktableMenu.STATUS_INSUFFICIENT
                || status == ArcaneWorktableMenu.STATUS_NEED_WAND;
        if (!arcane) {
            return; // vanilla/empty recipe: no ring, keep it a clean table
        }
        int cost = menu.visCost();
        double fraction;
        if (status == ArcaneWorktableMenu.STATUS_ARCANE_READY) {
            fraction = 1.0;
        } else if (status == ArcaneWorktableMenu.STATUS_INSUFFICIENT && cost > 0) {
            fraction = Math.min(1.0, menu.wandVis() / (double) cost);
        } else {
            fraction = 0.0; // need wand: ring present but unlit (charge me)
        }
        int lit = (int) Math.round(RING_PIPS * fraction);
        // Pips walk a rectangle just OUTSIDE the grid square (so none land inside a cell).
        int rx0 = x + ArcaneWorktableMenu.GRID_X - 5;
        int ry0 = y + ArcaneWorktableMenu.GRID_Y - 5;
        int w = 54 + 10;
        int h = 54 + 10;
        double perim = 2.0 * (w + h);
        double step = perim / RING_PIPS;
        for (int i = 0; i < RING_PIPS; i++) {
            double d = i * step;                     // distance along the perimeter, from top-left
            int px;
            int py;
            if (d < w) {                             // top edge, left→right
                px = rx0 + (int) d;
                py = ry0;
            } else if (d < w + h) {                  // right edge, top→bottom
                px = rx0 + w;
                py = ry0 + (int) (d - w);
            } else if (d < 2 * w + h) {              // bottom edge, right→left
                px = rx0 + w - (int) (d - w - h);
                py = ry0 + h;
            } else {                                 // left edge, bottom→top
                px = rx0;
                py = ry0 + h - (int) (d - 2 * w - h);
            }
            int color = i < lit ? (status == ArcaneWorktableMenu.STATUS_INSUFFICIENT ? WARN : TEAL) : PIP_OFF;
            g.fill(px - 1, py - 1, px + 2, py + 2, color); // 3×3 pip
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY); // title + "Inventory"
        // Numeric cost centred under the grid, coloured by affordability. (The wand slot is
        // self-labelled by its ghost-wand hint, TC4-style, so no separate label.)
        int status = menu.status();
        int costY = ArcaneWorktableMenu.GRID_Y + 54 + 12; // below the ring's bottom edge
        int costX = ArcaneWorktableMenu.GRID_CX;
        if (status == ArcaneWorktableMenu.STATUS_ARCANE_READY) {
            g.drawCenteredString(font, Component.translatable("screen.new_age_thaum.vis_cost", menu.visCost()),
                    costX, costY, TEAL_TEXT);
        } else if (status == ArcaneWorktableMenu.STATUS_INSUFFICIENT) {
            g.drawCenteredString(font, Component.translatable("screen.new_age_thaum.vis_cost", menu.visCost()),
                    costX, costY, WARN_TEXT);
        } else if (status == ArcaneWorktableMenu.STATUS_NEED_WAND) {
            g.drawCenteredString(font, Component.translatable("screen.new_age_thaum.vis_cost", menu.visCost()),
                    costX, costY, DIM);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // hovered-slot item tooltips
        Slot result = menu.getSlot(ArcaneWorktableMenu.RESULT_SLOT);
        if (!result.hasItem() && isHovering(result.x, result.y, 16, 16, mouseX, mouseY)) {
            switch (menu.status()) {
                case ArcaneWorktableMenu.STATUS_NEED_WAND -> g.renderTooltip(font,
                        Component.translatable("screen.new_age_thaum.need_wand"), mouseX, mouseY);
                case ArcaneWorktableMenu.STATUS_INSUFFICIENT -> g.renderTooltip(font,
                        Component.translatable("screen.new_age_thaum.insufficient_vis", menu.visCost(), menu.wandVis()),
                        mouseX, mouseY);
                default -> { /* nothing to explain */ }
            }
        }
    }
}
