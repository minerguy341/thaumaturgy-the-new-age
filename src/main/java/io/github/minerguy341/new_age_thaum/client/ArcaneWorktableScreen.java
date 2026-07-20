package io.github.minerguy341.new_age_thaum.client;

import io.github.minerguy341.new_age_thaum.content.ArcaneWorktableMenu;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Arcane Worktable screen — the "augment don't replace" overlay: a familiar 3×3 grid + result
 * where players expect them, plus a wand slot (with a ghost-wand empty-state hint) and a live
 * vis-cost chip that appears only when an arcane recipe is on the grid and locks the result
 * with a reason when the wand can't pay. Drawn procedurally against the shared dark-purple UI
 * palette; nine-slice sprite chrome is a studio follow-up.
 */
public class ArcaneWorktableScreen extends AbstractContainerScreen<ArcaneWorktableMenu> {
    // Shared UI palette (minecraft-ui-design skill).
    private static final int PANEL = 0xF0100A18;
    private static final int CHROME = 0xFF241B33;
    private static final int WELL_EDGE = 0xFF0B0912;
    private static final int WELL_FILL = 0xFF1A1526;
    private static final int TEXT = 0xE8D9FF;
    private static final int DIM = 0x9A8CBF;
    private static final int TEAL = 0x7FE8D8;
    private static final int WARN = 0xE08A8A;

    private ItemStack ghostWand = ItemStack.EMPTY;

    public ArcaneWorktableScreen(ArcaneWorktableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 190;
        this.titleLabelY = 5;
        this.inventoryLabelY = ArcaneWorktableMenu.INV_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        g.fill(x, y, x + imageWidth, y + 14, CHROME);

        for (Slot slot : menu.slots) {
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, WELL_EDGE);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, WELL_FILL);
        }

        // Ghost-wand hint in the empty wand slot: says "put a wand here" without a label.
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

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY); // title + "Inventory"
        int status = menu.status();
        int chipX = ArcaneWorktableMenu.RESULT_X - 22;
        int chipY = ArcaneWorktableMenu.RESULT_Y + 20;
        switch (status) {
            case ArcaneWorktableMenu.STATUS_ARCANE_READY -> g.drawString(font,
                    Component.translatable("screen.new_age_thaum.vis_cost", menu.visCost()), chipX, chipY, TEAL, false);
            case ArcaneWorktableMenu.STATUS_INSUFFICIENT -> g.drawString(font,
                    Component.translatable("screen.new_age_thaum.vis_cost", menu.visCost()), chipX, chipY, WARN, false);
            case ArcaneWorktableMenu.STATUS_NEED_WAND -> g.drawString(font,
                    Component.translatable("screen.new_age_thaum.need_wand"), chipX - 12, chipY, DIM, false);
            default -> { /* no chip */ }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // draws hovered-slot item tooltips already
        // A "why is this locked" tooltip on the empty result slot — the missing-reason case
        // the UI study flagged as the #1 way custom crafting UIs feel broken.
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
