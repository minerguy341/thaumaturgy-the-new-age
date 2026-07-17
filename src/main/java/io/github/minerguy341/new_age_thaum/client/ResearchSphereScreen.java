package io.github.minerguy341.new_age_thaum.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.minerguy341.new_age_thaum.content.ArcaneOrreryMenu;
import io.github.minerguy341.new_age_thaum.core.ModComponents;
import io.github.minerguy341.new_age_thaum.core.research.ResearchSphereData;
import io.github.minerguy341.new_age_thaum.core.aspect.Aspect;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectNames;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRegistry;
import io.github.minerguy341.new_age_thaum.core.research.grid.GoldbergGrid;
import io.github.minerguy341.new_age_thaum.network.NewAgeThaumNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The research linking-puzzle surface (M2, prototype), now a container screen: the paper
 * slot and player inventory are real menu slots (bottom-left), the scrollable aspect list
 * sits above them, and the rotatable Goldberg sphere fills the right side. Sphere state
 * lives in the {@link ArcaneOrreryBlockEntity} addressed by the menu's BlockPos; paints
 * are optimistic on the client and confirmed by the server, which persists them.
 */
public class ResearchSphereScreen extends AbstractContainerScreen<ArcaneOrreryMenu> {
    private static final int FREQUENCY = 3;
    private static final int ROW_HEIGHT = 18;
    private static final int SWATCH = 12;
    private static final int SCROLLBAR_W = 5;
    private static final int EMPTY_CELL = 0x2A2438;
    private static final double CELL_SHRINK = 0.86;
    private static final double ROTATE_SPEED = 0.008;
    private static final Quaternionf DEFAULT_ORIENTATION = new Quaternionf().rotateY(0.6f).rotateX(-0.35f);

    private final GoldbergGrid grid = GoldbergGrid.generate(FREQUENCY);
    private final Quaternionf orientation = new Quaternionf(DEFAULT_ORIENTATION);
    private List<ResourceLocation> aspects = new ArrayList<>();

    private int listLeft;
    private int listTop;
    private int listW;
    private int listH;
    private int rowW;
    private double sphereCx;
    private double sphereCy;
    private double sphereR;

    private double scroll;
    private boolean freeRotation;
    private float pitchAccum;
    private boolean rotating;
    private boolean draggingScrollbar;
    private ResourceLocation dragging;
    private Button cameraButton;

    public ResearchSphereScreen(ArcaneOrreryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 420;
        this.imageHeight = 240;
    }

    @Override
    protected void init() {
        super.init();

        aspects = new ArrayList<>();
        for (Aspect aspect : AspectRegistry.all()) {
            aspects.add(aspect.id());
        }
        aspects.sort(Comparator.comparing((ResourceLocation id) ->
                AspectRegistry.get(id).map(Aspect::isPrimal).orElse(false) ? 0 : 1)
                .thenComparing(ResourceLocation::getPath));

        listLeft = leftPos + 8;
        listTop = topPos + 26;
        listW = 162;
        listH = 106;
        rowW = listW - SCROLLBAR_W - 3;

        double areaLeft = leftPos + 8 + listW + 12;
        double areaRight = leftPos + imageWidth - 12;
        sphereCx = (areaLeft + areaRight) / 2.0;
        sphereCy = topPos + imageHeight / 2.0 + 4;
        sphereR = Math.min((areaRight - areaLeft) / 2.0, (imageHeight - 44) / 2.0);

        cameraButton = Button.builder(cameraLabel(), b -> toggleCamera())
                .bounds(leftPos + imageWidth - 150, topPos + 2, 74, 16).build();
        addRenderableWidget(cameraButton);
        addRenderableWidget(Button.builder(Component.translatable("screen.new_age_thaum.recenter"), b -> recenter())
                .bounds(leftPos + imageWidth - 72, topPos + 2, 64, 16).build());
    }

    /** The paper in slot 0 — vanilla slot sync keeps this current, components included. */
    private net.minecraft.world.item.ItemStack paperStack() {
        return this.menu.getSlot(0).getItem();
    }

    private boolean hasPaper() {
        return !paperStack().isEmpty();
    }

    private Map<Integer, ResourceLocation> placedMap() {
        net.minecraft.world.item.ItemStack paper = paperStack();
        return paper.isEmpty() ? Map.of()
                : paper.getOrDefault(ModComponents.RESEARCH_SPHERE.get(), ResearchSphereData.EMPTY).cells();
    }

    private void toggleCamera() {
        freeRotation = !freeRotation;
        pitchAccum = 0;
        cameraButton.setMessage(cameraLabel());
    }

    private Component cameraLabel() {
        return Component.translatable(freeRotation
                ? "screen.new_age_thaum.camera_free" : "screen.new_age_thaum.camera_lock");
    }

    private void recenter() {
        orientation.set(DEFAULT_ORIENTATION);
        pitchAccum = 0;
    }

    // --- rendering -------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0100A18);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 20, 0xFF241B33);
        graphics.fill(listLeft - 2, listTop - 2, listLeft + listW + 2, listTop + listH + 2, 0xFF0B0713);

        for (Slot slot : this.menu.slots) {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, 0xFF373045);
            graphics.renderOutline(x, y, 18, 18, 0xFF000000);
        }

        renderList(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, 8, 6, 0xE8D9FF, false);
        graphics.drawString(this.font, Component.translatable("screen.new_age_thaum.paper_slot"),
                ArcaneOrreryMenu.PAPER_SLOT_X + 22, ArcaneOrreryMenu.PAPER_SLOT_Y + 5, 0x9A8CBF, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.flush();
        renderSphere(graphics);
        if (!hasPaper()) {
            Component hint = Component.translatable("screen.new_age_thaum.insert_paper");
            graphics.drawCenteredString(this.font, hint, (int) sphereCx,
                    (int) (sphereCy + sphereR + 2), 0x9A8CBF);
        }
        this.renderTooltip(graphics, mouseX, mouseY);

        if (dragging != null) {
            int color = 0xFF000000 | colorOf(dragging);
            graphics.fill(mouseX - SWATCH / 2, mouseY - SWATCH / 2, mouseX + SWATCH / 2, mouseY + SWATCH / 2, color);
            graphics.renderOutline(mouseX - SWATCH / 2, mouseY - SWATCH / 2, SWATCH, SWATCH, 0xFFFFFFFF);
        } else {
            ResourceLocation hovered = aspectRowAt(mouseX, mouseY);
            if (hovered != null) {
                graphics.renderComponentTooltip(this.font, tooltipFor(hovered), mouseX, mouseY);
            }
        }
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.enableScissor(listLeft, listTop, listLeft + rowW, listTop + listH);
        int y = listTop - (int) scroll;
        for (ResourceLocation id : aspects) {
            if (y + ROW_HEIGHT >= listTop && y <= listTop + listH) {
                boolean hover = mouseX >= listLeft && mouseX < listLeft + rowW && mouseY >= y && mouseY < y + ROW_HEIGHT;
                if (hover) {
                    graphics.fill(listLeft, y, listLeft + rowW, y + ROW_HEIGHT, 0x40FFFFFF);
                }
                graphics.fill(listLeft + 4, y + 3, listLeft + 4 + SWATCH, y + 3 + SWATCH, 0xFF000000 | colorOf(id));
                graphics.renderOutline(listLeft + 4, y + 3, SWATCH, SWATCH, 0xFF000000);
                graphics.drawString(this.font, AspectNames.displayName(id), listLeft + 22, y + 5, 0xD8CCEE, false);
            }
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();

        int contentH = aspects.size() * ROW_HEIGHT;
        if (contentH > listH) {
            int trackX = listLeft + listW - SCROLLBAR_W;
            graphics.fill(trackX, listTop, trackX + SCROLLBAR_W, listTop + listH, 0xFF000000);
            int barH = Math.max(12, (int) ((double) listH * listH / contentH));
            int barY = listTop + (int) (scroll * (listH - barH) / (contentH - listH));
            graphics.fill(trackX, barY, trackX + SCROLLBAR_W, barY + barH, 0xFF6A4FB0);
        }
    }

    private void renderSphere(GuiGraphics graphics) {
        Map<Integer, ResourceLocation> placed = placedMap();
        List<GoldbergGrid.Cell> front = new ArrayList<>();
        for (GoldbergGrid.Cell cell : grid.cells()) {
            if (rotate(cell.x(), cell.y(), cell.z()).z > 0) {
                front.add(cell);
            }
        }
        front.sort(Comparator.comparingDouble(cell -> rotate(cell.x(), cell.y(), cell.z()).z));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = graphics.pose().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (GoldbergGrid.Cell cell : front) {
            int rgb = placed.containsKey(cell.index()) ? colorOf(placed.get(cell.index())) : EMPTY_CELL;
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            double[] pc = project(rotate(cell.x(), cell.y(), cell.z()));
            double[][] corners = cell.corners();
            int n = corners.length;
            double[][] pts = new double[n][];
            for (int i = 0; i < n; i++) {
                double[] p = project(rotate(corners[i][0], corners[i][1], corners[i][2]));
                pts[i] = new double[]{pc[0] + (p[0] - pc[0]) * CELL_SHRINK, pc[1] + (p[1] - pc[1]) * CELL_SHRINK};
            }
            for (int i = 0; i < n; i++) {
                double[] p1 = pts[i];
                double[] p2 = pts[(i + 1) % n];
                buffer.addVertex(matrix, (float) pc[0], (float) pc[1], 0).setColor(r, g, b, 255);
                buffer.addVertex(matrix, (float) p1[0], (float) p1[1], 0).setColor(r, g, b, 255);
                buffer.addVertex(matrix, (float) p2[0], (float) p2[1], 0).setColor(r, g, b, 255);
            }
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.disableBlend();
    }

    private List<Component> tooltipFor(ResourceLocation id) {
        List<Component> lines = new ArrayList<>();
        lines.add(AspectNames.colored(id));
        lines.add(Component.translatable(descKey(id)).withStyle(ChatFormatting.GRAY));
        Aspect aspect = AspectRegistry.get(id).orElse(null);
        if (aspect != null) {
            if (aspect.isPrimal()) {
                lines.add(Component.translatable("aspect.new_age_thaum.desc.primal").withStyle(ChatFormatting.DARK_GRAY));
            } else if (aspect.components().size() == 2) {
                lines.add(Component.translatable("aspect.new_age_thaum.desc.composed",
                        AspectNames.colored(aspect.components().get(0)),
                        AspectNames.colored(aspect.components().get(1))).withStyle(ChatFormatting.DARK_GRAY));
            }
            MutableComponent into = combinesInto(id);
            if (into != null) {
                lines.add(Component.translatable("aspect.new_age_thaum.desc.combines", into).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return lines;
    }

    private MutableComponent combinesInto(ResourceLocation id) {
        List<ResourceLocation> users = new ArrayList<>();
        for (Aspect aspect : AspectRegistry.all()) {
            if (aspect.components().contains(id)) {
                users.add(aspect.id());
            }
        }
        if (users.isEmpty()) {
            return null;
        }
        users.sort(Comparator.comparing(ResourceLocation::getPath));
        MutableComponent out = Component.empty();
        int shown = Math.min(users.size(), 8);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                out.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
            }
            out.append(AspectNames.colored(users.get(i)));
        }
        if (users.size() > shown) {
            out.append(Component.literal(" …").withStyle(ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    private static String descKey(ResourceLocation id) {
        return "aspect." + id.getNamespace() + "." + id.getPath() + ".desc";
    }

    // --- interaction -----------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int contentH = aspects.size() * ROW_HEIGHT;
        boolean onScrollbar = contentH > listH && mouseX >= listLeft + listW - SCROLLBAR_W
                && mouseX <= listLeft + listW && mouseY >= listTop && mouseY <= listTop + listH;
        if (onScrollbar) {
            draggingScrollbar = true;
            setScrollFromMouse(mouseY);
            return true;
        }
        if (mouseX >= listLeft && mouseX < listLeft + rowW && mouseY >= listTop && mouseY < listTop + listH) {
            ResourceLocation id = aspectRowAt(mouseX, mouseY);
            if (id != null && button == 0) {
                dragging = id;
                return true;
            }
        } else if (inSphere(mouseX, mouseY)) {
            int cell = pickCell(mouseX, mouseY);
            if (button == 1 && cell >= 0) {
                clearCell(cell);
                return true;
            }
            if (button == 0) {
                rotating = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            setScrollFromMouse(mouseY);
            return true;
        }
        if (rotating) {
            applyRotation(dragX, dragY);
            return true;
        }
        return dragging != null || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        if (dragging != null) {
            if (inSphere(mouseX, mouseY)) {
                int cell = pickCell(mouseX, mouseY);
                if (cell >= 0) {
                    paintCell(cell, dragging);
                }
            }
            dragging = null;
            return true;
        }
        rotating = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (mouseX >= listLeft && mouseX <= listLeft + listW && mouseY >= listTop && mouseY <= listTop + listH) {
            int contentH = aspects.size() * ROW_HEIGHT;
            scroll = clamp(scroll - dy * ROW_HEIGHT, 0, Math.max(0, contentH - listH));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    // --- edits (optimistic client + C2S; server persists onto the paper) --------

    private void paintCell(int cell, ResourceLocation aspect) {
        if (!hasPaper()) {
            return;
        }
        net.minecraft.world.item.ItemStack paper = paperStack();
        paper.set(ModComponents.RESEARCH_SPHERE.get(),
                paper.getOrDefault(ModComponents.RESEARCH_SPHERE.get(), ResearchSphereData.EMPTY).with(cell, aspect));
        NewAgeThaumNetwork.sendOrreryEdit(this.menu.pos(), cell, Optional.of(aspect));
    }

    private void clearCell(int cell) {
        if (!hasPaper()) {
            return;
        }
        net.minecraft.world.item.ItemStack paper = paperStack();
        paper.set(ModComponents.RESEARCH_SPHERE.get(),
                paper.getOrDefault(ModComponents.RESEARCH_SPHERE.get(), ResearchSphereData.EMPTY).without(cell));
        NewAgeThaumNetwork.sendOrreryEdit(this.menu.pos(), cell, Optional.empty());
    }

    // --- helpers ---------------------------------------------------------------

    private void applyRotation(double dragX, double dragY) {
        if (freeRotation) {
            orientation.premul(new Quaternionf()
                    .rotateY((float) (dragX * ROTATE_SPEED))
                    .rotateX((float) (dragY * ROTATE_SPEED)));
        } else {
            orientation.rotateLocalY((float) (dragX * ROTATE_SPEED));
            float target = clampf(pitchAccum + (float) (dragY * ROTATE_SPEED), -1.4f, 1.4f);
            orientation.premul(new Quaternionf().rotateX(target - pitchAccum));
            pitchAccum = target;
        }
    }

    private void setScrollFromMouse(double mouseY) {
        int contentH = aspects.size() * ROW_HEIGHT;
        double frac = (mouseY - listTop) / (double) listH;
        scroll = clamp(frac * contentH - listH / 2.0, 0, Math.max(0, contentH - listH));
    }

    private boolean inSphere(double x, double y) {
        double dx = x - sphereCx;
        double dy = y - sphereCy;
        return dx * dx + dy * dy <= (sphereR + 6) * (sphereR + 6);
    }

    private ResourceLocation aspectRowAt(double x, double y) {
        if (x < listLeft || x >= listLeft + rowW || y < listTop || y >= listTop + listH) {
            return null;
        }
        int index = (int) ((y - listTop + scroll) / ROW_HEIGHT);
        return index >= 0 && index < aspects.size() ? aspects.get(index) : null;
    }

    private int pickCell(double mx, double my) {
        int best = -1;
        double bestZ = Double.NEGATIVE_INFINITY;
        for (GoldbergGrid.Cell cell : grid.cells()) {
            Vector3f c = rotate(cell.x(), cell.y(), cell.z());
            if (c.z <= 0) {
                continue;
            }
            double[][] corners = cell.corners();
            double[] xs = new double[corners.length];
            double[] ys = new double[corners.length];
            for (int i = 0; i < corners.length; i++) {
                double[] p = project(rotate(corners[i][0], corners[i][1], corners[i][2]));
                xs[i] = p[0];
                ys[i] = p[1];
            }
            if (pointInPolygon(mx, my, xs, ys) && c.z > bestZ) {
                bestZ = c.z;
                best = cell.index();
            }
        }
        return best;
    }

    private static boolean pointInPolygon(double px, double py, double[] xs, double[] ys) {
        boolean inside = false;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            if ((ys[i] > py) != (ys[j] > py)
                    && px < (xs[j] - xs[i]) * (py - ys[i]) / (ys[j] - ys[i]) + xs[i]) {
                inside = !inside;
            }
        }
        return inside;
    }

    private Vector3f rotate(double x, double y, double z) {
        return orientation.transform(new Vector3f((float) x, (float) y, (float) z));
    }

    private double[] project(Vector3f rotated) {
        return new double[]{sphereCx + rotated.x * sphereR, sphereCy - rotated.y * sphereR};
    }

    private static int colorOf(ResourceLocation aspectId) {
        return AspectRegistry.get(aspectId).map(Aspect::color).orElse(0x888888);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clampf(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
