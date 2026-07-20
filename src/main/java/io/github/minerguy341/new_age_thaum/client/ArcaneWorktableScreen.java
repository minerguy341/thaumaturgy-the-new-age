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
 * Arcane Worktable screen — the "augment don't replace" overlay. It blits the VANILLA
 * crafting-table GUI (referenced by ResourceLocation — the client already ships it, nothing is
 * bundled here) so the 3×3 grid + result read as the familiar crafting table, then adds exactly
 * two arcane augments: a wand slot (blitted from a real vanilla slot cell, with a ghost-wand
 * empty-state hint) and a live vis-cost chip that appears only when an arcane recipe is on the
 * grid and locks the result with a reason when the wand can't pay.
 */
public class ArcaneWorktableScreen extends AbstractContainerScreen<ArcaneWorktableMenu> {
    // Vanilla crafting-table container texture (256×256). Referenced, not shipped.
    private static final ResourceLocation BG =
            ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    // A single 18×18 slot cell within that texture (the top-left grid slot's inset).
    private static final int SLOT_CELL_U = 29;
    private static final int SLOT_CELL_V = 16;

    private static final int TEAL = 0x7FE8D8;
    private static final int WARN = 0xC0392B;
    private static final int DIM = 0x6A6A6A;

    private ItemStack ghostWand = ItemStack.EMPTY;

    public ArcaneWorktableScreen(ArcaneWorktableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 29; // vanilla crafting-table title inset
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.blit(BG, x, y, 0, 0, imageWidth, imageHeight);
        // The wand slot sits over blank background — stamp a real vanilla slot cell behind it.
        g.blit(BG, x + ArcaneWorktableMenu.WAND_X - 1, y + ArcaneWorktableMenu.WAND_Y - 1,
                SLOT_CELL_U, SLOT_CELL_V, 18, 18);

        // Ghost-wand hint in the empty wand slot: says "put a wand here" without a label.
        if (!menu.hasWand()) {
            if (ghostWand.isEmpty()) {
                ghostWand = new ItemStack(ModRegistries.WAND.get());
            }
            int wx = x + ArcaneWorktableMenu.WAND_X;
            int wy = y + ArcaneWorktableMenu.WAND_Y;
            g.renderFakeItem(ghostWand, wx, wy);
            g.fill(wx, wy, wx + 16, wy + 16, 0x90C6C6C6); // dim to a ghost on the light bg
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY); // title + "Inventory", vanilla dark text
        // Vis chip under the wand slot (the payer), so cost sits with the tool that pays it.
        int chipX = ArcaneWorktableMenu.WAND_X - 8;
        int chipY = ArcaneWorktableMenu.WAND_Y + 20;
        switch (menu.status()) {
            case ArcaneWorktableMenu.STATUS_ARCANE_READY -> g.drawString(font,
                    Component.translatable("screen.new_age_thaum.vis_cost", menu.visCost()), chipX, chipY, TEAL, false);
            case ArcaneWorktableMenu.STATUS_INSUFFICIENT -> g.drawString(font,
                    Component.translatable("screen.new_age_thaum.vis_cost", menu.visCost()), chipX, chipY, WARN, false);
            case ArcaneWorktableMenu.STATUS_NEED_WAND -> g.drawString(font,
                    Component.translatable("screen.new_age_thaum.wand_label"), chipX, chipY, DIM, false);
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
