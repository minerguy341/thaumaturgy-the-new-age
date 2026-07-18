package io.github.minerguy341.new_age_thaum.client;

import io.github.minerguy341.new_age_thaum.core.codex.CodexEntry;
import io.github.minerguy341.new_age_thaum.core.codex.CodexRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Codex shell (M1): renders one category's entries as a node grid with hover
 * tooltips and a stub detail panel. No research gating and no entry pages yet — those
 * arrive with the research systems in M2.
 */
public class CodexScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 180;
    private static final int CELL = 24;

    private String category = "";
    private CodexEntry selected;
    private int panelLeft;
    private int panelTop;
    // renderFakeItem needs an ItemStack; cache per item so render() doesn't allocate
    // one per entry per frame.
    private final Map<Item, ItemStack> iconStacks = new HashMap<>();

    public CodexScreen() {
        super(Component.translatable("screen.new_age_thaum.codex"));
    }

    // byCategory scans and filters the whole registry — cache the list instead of
    // rebuilding it every frame (and on every click).
    private List<CodexEntry> shownEntries = List.of();

    @Override
    protected void init() {
        List<String> categories = CodexRegistry.categories();
        if (category.isEmpty() && !categories.isEmpty()) {
            category = categories.get(0);
        }
        shownEntries = CodexRegistry.byCategory(category);
        panelLeft = (this.width - PANEL_WIDTH) / 2;
        panelTop = (this.height - PANEL_HEIGHT) / 2;
    }

    private int iconX(CodexEntry entry) {
        return panelLeft + 16 + entry.x() * CELL;
    }

    private int iconY(CodexEntry entry) {
        // Start the node grid well below the category label (drawn at panelTop + 26)
        // so the icons don't sit on top of it.
        return panelTop + 48 + entry.y() * CELL;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xF0100A18);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + 22, 0xFF241B33);
        graphics.drawString(this.font, this.title, panelLeft + 8, panelTop + 7, 0xE8D9FF, false);

        if (!category.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("codex.new_age_thaum.category." + category)
                    .withStyle(ChatFormatting.GRAY), panelLeft + 8, panelTop + 26, 0xB9A7D6, false);
        }

        CodexEntry hovered = null;
        for (CodexEntry entry : shownEntries) {
            int x = iconX(entry);
            int y = iconY(entry);
            boolean isSelected = entry.equals(selected);
            graphics.fill(x - 3, y - 3, x + 19, y + 19, isSelected ? 0xFF6A4FB0 : 0xFF2C2140);
            graphics.renderFakeItem(iconStacks.computeIfAbsent(entry.icon(), ItemStack::new), x, y);
            if (mouseX >= x - 3 && mouseX <= x + 19 && mouseY >= y - 3 && mouseY <= y + 19) {
                hovered = entry;
            }
        }

        if (selected != null) {
            graphics.drawString(this.font, Component.translatable(selected.titleKey()),
                    panelLeft + 8, panelTop + PANEL_HEIGHT - 34, 0xE8D9FF, false);
            graphics.drawString(this.font, Component.translatable("codex.new_age_thaum.stub")
                    .withStyle(ChatFormatting.DARK_GRAY), panelLeft + 8, panelTop + PANEL_HEIGHT - 20, 0x7A6E99, false);
        }

        if (hovered != null) {
            graphics.renderTooltip(this.font, Component.translatable(hovered.titleKey()), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (CodexEntry entry : shownEntries) {
            int x = iconX(entry);
            int y = iconY(entry);
            if (mouseX >= x - 3 && mouseX <= x + 19 && mouseY >= y - 3 && mouseY <= y + 19) {
                selected = entry;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
