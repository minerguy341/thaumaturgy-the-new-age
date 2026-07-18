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
import io.github.minerguy341.new_age_thaum.core.research.PuzzleGenerator;
import io.github.minerguy341.new_age_thaum.core.research.ResearchPuzzle;
import io.github.minerguy341.new_age_thaum.core.research.ResearchSphereData;
import io.github.minerguy341.new_age_thaum.core.aspect.Aspect;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectNames;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRegistry;
import io.github.minerguy341.new_age_thaum.core.research.grid.GoldbergGrid;
import io.github.minerguy341.new_age_thaum.network.NewAgeThaumNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
import java.util.HashMap;
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

    /** Fill shrink derived from the configurable border width (1.0 config = classic 0.86). */
    private static double cellShrink() {
        double width = io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig.cellBorderWidth;
        return Math.max(0.5, Math.min(1.0, 1.0 - 0.14 * width));
    }
    private static final double ROTATE_SPEED = 0.008;
    private static final Quaternionf DEFAULT_ORIENTATION =
            io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity.DEFAULT_ORIENTATION;
    /** Drag rotations stream to the server at most this often; release always sends. */
    private static final long ROTATION_SEND_INTERVAL_MS = 50;

    private GoldbergGrid grid = PuzzleGenerator.gridFor(FREQUENCY);
    private int gridFrequency = FREQUENCY;
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
    private long lastRotationSent;
    // Flick momentum: drag velocity in pixels/ms, decayed by friction after release.
    private double spinVelX;
    private double spinVelY;
    private long lastDragMillis;
    private long lastSpinTick;
    private boolean spinning;
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
        io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig.maybeReload();
        // Continue from the orrery's stored orientation (also keeps the screen and the
        // world hologram agreeing across reopen/resize).
        var orrery = clientOrrery();
        if (orrery != null) {
            orientation.set(orrery.orientation());
        }

        // Discovery: the six primals are always listed; compounds only once the player
        // has ever earned points of them (m2-gameplay-spec §A).
        aspects = new ArrayList<>();
        var progress = ClientPlayerProgress.get();
        for (Aspect aspect : AspectRegistry.all()) {
            if (aspect.isPrimal() || progress.hasDiscovered(aspect.id())) {
                aspects.add(aspect.id());
            }
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

    /** The held paper's generated puzzle (endpoints, gaps, sphere size), or null. */
    private ResearchPuzzle puzzle() {
        net.minecraft.world.item.ItemStack paper = paperStack();
        return paper.isEmpty() ? null : paper.get(ModComponents.RESEARCH_PUZZLE.get());
    }

    /** Player placements plus the puzzle's fixed endpoint aspects — what the rules see. */
    private Map<Integer, ResourceLocation> effectiveMap() {
        ResearchPuzzle puzzle = puzzle();
        Map<Integer, ResourceLocation> placed = placedMap();
        if (puzzle == null || puzzle.endpoints().isEmpty()) {
            return placed;
        }
        Map<Integer, ResourceLocation> combined = new HashMap<>(placed);
        combined.putAll(puzzle.endpoints());
        return combined;
    }

    private boolean isGapCell(int cell) {
        ResearchPuzzle puzzle = puzzle();
        return puzzle != null && puzzle.isGap(cell);
    }

    private boolean isSolved() {
        ResearchPuzzle puzzle = puzzle();
        return puzzle != null && puzzle.solved();
    }

    private boolean isLockedCell(int cell) {
        ResearchPuzzle puzzle = puzzle();
        // A solved paper is sealed shut; endpoints and gaps are always untouchable.
        return puzzle != null && (puzzle.solved() || puzzle.isEndpoint(cell) || puzzle.isGap(cell));
    }

    /** Swaps in the sphere matching the paper's puzzle (tierScaledSpheres config). */
    private void updateGrid() {
        ResearchPuzzle puzzle = puzzle();
        int frequency = puzzle != null ? puzzle.frequency() : FREQUENCY;
        if (frequency != gridFrequency) {
            gridFrequency = frequency;
            grid = PuzzleGenerator.gridFor(frequency);
            lastLinkInput = null;
        }
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
        pushOrientation(true);
    }

    private io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity clientOrrery() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(this.menu.pos())
                instanceof io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity orrery
                ? orrery : null;
    }

    /**
     * Mirrors the screen rotation onto the world hologram: writes through to this
     * client's block entity immediately and streams the quaternion to the server
     * (throttled while dragging; release/recenter force-send the final pose).
     */
    private void pushOrientation(boolean force) {
        var orrery = clientOrrery();
        if (orrery != null) {
            orrery.setOrientation(new Quaternionf(orientation));
        }
        long now = net.minecraft.Util.getMillis();
        if (!force && now - lastRotationSent < ROTATION_SEND_INTERVAL_MS) {
            return;
        }
        lastRotationSent = now;
        NewAgeThaumNetwork.sendOrreryRotation(this.menu.pos(), orientation);
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
        updateGrid();
        tickSpin();
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.flush();
        renderSphere(graphics, mouseX, mouseY);
        if (!hasPaper()) {
            Component hint = Component.translatable("screen.new_age_thaum.insert_paper");
            graphics.drawCenteredString(this.font, hint, (int) sphereCx,
                    (int) (sphereCy + sphereR + 2), 0x9A8CBF);
        } else if (isSolved()) {
            Component done = Component.translatable("screen.new_age_thaum.research_complete");
            graphics.drawCenteredString(this.font, done, (int) sphereCx,
                    (int) (sphereCy + sphereR + 2), 0xE8C86A);
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
        var progress = ClientPlayerProgress.get();
        graphics.enableScissor(listLeft, listTop, listLeft + rowW, listTop + listH);
        int y = listTop - (int) scroll;
        for (ResourceLocation id : aspects) {
            if (y + ROW_HEIGHT >= listTop && y <= listTop + listH) {
                int points = progress.points(id);
                boolean broke = points <= 0;
                boolean hover = mouseX >= listLeft && mouseX < listLeft + rowW && mouseY >= y && mouseY < y + ROW_HEIGHT;
                if (hover) {
                    graphics.fill(listLeft, y, listLeft + rowW, y + ROW_HEIGHT, 0x40FFFFFF);
                }
                int swatch = broke ? blend(colorOf(id), 0x1A1524, 0.6) : colorOf(id);
                graphics.fill(listLeft + 4, y + 3, listLeft + 4 + SWATCH, y + 3 + SWATCH, 0xFF000000 | swatch);
                graphics.renderOutline(listLeft + 4, y + 3, SWATCH, SWATCH, 0xFF000000);
                graphics.drawString(this.font, AspectNames.displayName(id), listLeft + 22, y + 5,
                        broke ? 0x5F5876 : 0xD8CCEE, false);
                String count = String.valueOf(points);
                graphics.drawString(this.font, count, listLeft + rowW - 4 - this.font.width(count), y + 5,
                        broke ? 0x4A4560 : 0x9A8CBF, false);
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

    private void renderSphere(GuiGraphics graphics, int mouseX, int mouseY) {
        ResearchPuzzle puzzle = puzzle();
        Map<Integer, ResourceLocation> placed = effectiveMap();
        java.util.Set<Integer> unlinked = unlinkedFor(placed);
        // -1 while unsolved; 0..1 breathing wave once the circuit has closed.
        double solvedBreath = puzzle != null && puzzle.solved()
                ? 0.5 + 0.5 * Math.sin(net.minecraft.Util.getMillis() / 1000.0 * 2.4) : -1;

        // Drop preview: while dragging, the target cell gets a rim — white when the drop
        // would immediately link to a related neighbor, dim grey when it would sit unlinked.
        // Endpoint cells are locked, so they never preview.
        int previewCell = -1;
        boolean previewLinks = false;
        if (dragging != null && hasPaper() && !isSolved() && inSphere(mouseX, mouseY)) {
            previewCell = pickCell(mouseX, mouseY);
            if (previewCell >= 0 && puzzle != null && puzzle.isEndpoint(previewCell)) {
                previewCell = -1;
            }
            if (previewCell >= 0) {
                previewLinks = io.github.minerguy341.new_age_thaum.core.research.LinkingPuzzle
                        .wouldLink(grid, placed, previewCell, dragging);
            }
        }

        // One rotation per cell per frame, reused by the cull, the depth sort, the fade,
        // and the currents — re-rotating inside the sort comparator alone was O(n log n)
        // fresh Vector3fs per frame.
        Vector3f[] rotated = new Vector3f[grid.size()];
        for (GoldbergGrid.Cell cell : grid.cells()) {
            rotated[cell.index()] = rotate(cell.x(), cell.y(), cell.z());
        }
        List<GoldbergGrid.Cell> front = new ArrayList<>();
        for (GoldbergGrid.Cell cell : grid.cells()) {
            // Gap cells are holes in the sphere: not drawn, not paintable.
            if (puzzle != null && puzzle.isGap(cell.index())) {
                continue;
            }
            if (rotated[cell.index()].z > 0) {
                front.add(cell);
            }
        }
        front.sort(Comparator.comparingDouble(cell -> rotated[cell.index()].z));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // The ribbon quads' winding depends on line direction — never let culling eat them.
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = graphics.pose().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (GoldbergGrid.Cell cell : front) {
            boolean endpoint = puzzle != null && puzzle.isEndpoint(cell.index());
            int rgb = placed.containsKey(cell.index()) ? colorOf(placed.get(cell.index())) : EMPTY_CELL;
            // No valid connection -> greyed out (unrelated neighbors are ignored, not
            // errors). Endpoints keep their true color — the player plans around them.
            if (!endpoint && unlinked.contains(cell.index())) {
                rgb = blend(rgb, 0x45434E, 0.65);
            }

            double[][] full = visiblePolygon(cell);
            if (full == null) {
                continue;
            }
            // Fade the cell out as its center reaches the horizon: together with the
            // clip this swallows cells smoothly at the limb — no pop-out, and no
            // leftover sliver peeking through the neighbors' dividers.
            float fade = Math.min(1.0f, rotated[cell.index()].z / 0.12f);
            double[] pc = polygonCenter(full);
            int n = full.length;
            double shrink = cellShrink();
            double[][] pts = new double[n][];
            for (int i = 0; i < n; i++) {
                pts[i] = new double[]{pc[0] + (full[i][0] - pc[0]) * shrink,
                        pc[1] + (full[i][1] - pc[1]) * shrink};
            }

            // Rims first (full, un-shrunk face), fill on top. Endpoints wear gold;
            // on a solved paper the gold breathes with the completed circuit.
            if (cell.index() == previewCell) {
                addPolygon(buffer, matrix, pc, full, previewLinks ? 0xFFFFFF : 0x8A8794, (int) (200 * fade));
            } else if (endpoint) {
                int rimAlpha = solvedBreath >= 0
                        ? (int) ((190 + 60 * solvedBreath) * fade)
                        : (int) (235 * fade);
                addPolygon(buffer, matrix, pc, full, 0xE8C86A, rimAlpha);
            }
            addPolygon(buffer, matrix, pc, pts, rgb, (int) (255 * fade));
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        renderCurrents(matrix, placed, rotated);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * Flowing energy currents between validly linked adjacent cells: an anchored ribbon
     * distorted by two layered travelling sine waves, colored as a gradient between the
     * two aspects. Drawn on top of the fills; both cells must face the viewer.
     */
    private void renderCurrents(Matrix4f matrix, Map<Integer, ResourceLocation> placed, Vector3f[] rotated) {
        List<int[]> pairs = linkedPairsFor(placed);
        if (pairs.isEmpty()) {
            return;
        }
        double time = net.minecraft.Util.getMillis() / 1000.0;
        boolean solved = isSolved();
        // Closed circuit: once solved, the whole web breathes gold in unison.
        double breath = solved ? 0.30 + 0.20 * Math.sin(time * 2.4) : 0;
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (int[] pair : pairs) {
            // Orient the link downstream: the current flows from lower chain depth to higher.
            int from = pair[0];
            int to = pair[1];
            if (lastFlowDepth.getOrDefault(to, 0) < lastFlowDepth.getOrDefault(from, 0)) {
                from = pair[1];
                to = pair[0];
            }
            Vector3f ra = rotated[from];
            Vector3f rb = rotated[to];
            // Currents fade with their cells at the horizon (same window as the fills).
            float edgeFade = Math.min(1.0f, Math.min(ra.z, rb.z) / 0.12f);
            if (edgeFade <= 0) {
                continue;
            }
            double[] p1 = project(ra);
            double[] p2 = project(rb);
            boolean custom = io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig.customCurrentColors();
            int c1 = custom ? io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig.currentBaseColor
                    : colorOf(placed.get(from));
            int c2 = custom ? io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig.currentBaseColor
                    : colorOf(placed.get(to));
            if (solved) {
                c1 = blend(c1, 0xE8C86A, breath);
                c2 = blend(c2, 0xE8C86A, breath);
            }
            // Chain phase makes crests continue cell-to-cell along the web; the hash
            // jitter keeps parallel links from moving in lockstep.
            int depth = lastFlowDepth.getOrDefault(from, 0);
            double chainPhase = depth * 2.2;
            double jitter = (pair[0] * 31 + pair[1] * 17) % 97 / 97.0 * 0.9;
            float widthScale = (float) io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig.currentWidth;
            ribbon(buffer, matrix, p1, p2, c1, c2, 3.8f * widthScale, (int) ((solved ? 110 : 70) * edgeFade), time, chainPhase + jitter, depth);   // soft glow
            ribbon(buffer, matrix, p1, p2, c1, c2, 1.6f * widthScale, (int) ((solved ? 255 : 235) * edgeFade), time, chainPhase + jitter, depth);  // bright core
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    private void ribbon(BufferBuilder buffer, Matrix4f matrix, double[] p1, double[] p2,
            int rgb1, int rgb2, float width, int alpha, double time, double phase, int chainDepth) {
        double dx = p2[0] - p1[0];
        double dy = p2[1] - p1[1];
        double len = Math.hypot(dx, dy);
        if (len < 1.0e-3) {
            return;
        }
        double nx = -dy / len;
        double ny = dx / len;
        final int segments = 10;

        double[][] left = new double[segments + 1][2];
        double[][] right = new double[segments + 1][2];
        double amp = io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig.currentAmplitude;
        double speed = io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig.currentSpeed;
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            // Envelope anchors the current at both cell centres; both waves travel in
            // the flow direction (phase continues cell-to-cell via the chain phase).
            double envelope = Math.sin(Math.PI * t);
            double disp = envelope * amp * (1.9 * Math.sin(2 * Math.PI * 1.3 * t - time * speed * 2.4 + phase)
                    + 1.1 * Math.sin(2 * Math.PI * 2.7 * t - time * speed * 3.3 + phase * 1.7));
            double cx = p1[0] + dx * t + nx * disp;
            double cy = p1[1] + dy * t + ny * disp;
            double half = width * (0.55 + 0.45 * envelope) / 2.0;
            left[i] = new double[]{cx + nx * half, cy + ny * half};
            right[i] = new double[]{cx - nx * half, cy - ny * half};
        }
        for (int i = 0; i < segments; i++) {
            int colA = glinted(blend(rgb1, rgb2, (double) i / segments), (double) i / segments, time, speed, chainDepth);
            int colB = glinted(blend(rgb1, rgb2, (double) (i + 1) / segments), (double) (i + 1) / segments, time, speed, chainDepth);
            quad(buffer, matrix, left[i], right[i], right[i + 1], left[i + 1], colA, colB, alpha);
        }
    }

    /** Wavelength of the travelling pulse, measured in links of the chain. */
    private static final double GLINT_WAVELENGTH = 2.6;

    /**
     * A bright pulse travelling in the flow direction. The wave lives in global chain
     * coordinates (depth + t), so a pulse leaves one link exactly as it enters the
     * next — a continuous relay along the whole web. No per-link jitter here.
     * In custom color mode the pulse itself grades pulseFrom→pulseTo with intensity.
     */
    private static int glinted(int rgb, double t, double time, double speed, int chainDepth) {
        double s = (chainDepth + t) / GLINT_WAVELENGTH;
        double wave = Math.sin(2 * Math.PI * (s - time * speed * 0.5));
        double strength = Math.pow(Math.max(0, wave), 3);
        if (strength <= 0) {
            return rgb;
        }
        int pulseColor;
        if (io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig.customCurrentColors()) {
            pulseColor = blend(io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig.currentPulseFrom,
                    io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig.currentPulseTo, strength);
        } else {
            pulseColor = 0xFFFFFF;
        }
        return blend(rgb, pulseColor, strength * 0.55);
    }

    private void quad(BufferBuilder buffer, Matrix4f matrix, double[] l0, double[] r0, double[] r1, double[] l1,
            int colorNear, int colorFar, int alpha) {
        int rN = (colorNear >> 16) & 0xFF;
        int gN = (colorNear >> 8) & 0xFF;
        int bN = colorNear & 0xFF;
        int rF = (colorFar >> 16) & 0xFF;
        int gF = (colorFar >> 8) & 0xFF;
        int bF = colorFar & 0xFF;
        buffer.addVertex(matrix, (float) l0[0], (float) l0[1], 0).setColor(rN, gN, bN, alpha);
        buffer.addVertex(matrix, (float) r0[0], (float) r0[1], 0).setColor(rN, gN, bN, alpha);
        buffer.addVertex(matrix, (float) r1[0], (float) r1[1], 0).setColor(rF, gF, bF, alpha);
        buffer.addVertex(matrix, (float) l0[0], (float) l0[1], 0).setColor(rN, gN, bN, alpha);
        buffer.addVertex(matrix, (float) r1[0], (float) r1[1], 0).setColor(rF, gF, bF, alpha);
        buffer.addVertex(matrix, (float) l1[0], (float) l1[1], 0).setColor(rF, gF, bF, alpha);
    }

    private void addPolygon(BufferBuilder buffer, Matrix4f matrix, double[] center, double[][] pts,
            int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int n = pts.length;
        for (int i = 0; i < n; i++) {
            double[] p1 = pts[i];
            double[] p2 = pts[(i + 1) % n];
            buffer.addVertex(matrix, (float) center[0], (float) center[1], 0).setColor(r, g, b, alpha);
            buffer.addVertex(matrix, (float) p1[0], (float) p1[1], 0).setColor(r, g, b, alpha);
            buffer.addVertex(matrix, (float) p2[0], (float) p2[1], 0).setColor(r, g, b, alpha);
        }
    }

    private static int blend(int from, int to, double factor) {
        int r = (int) (((from >> 16) & 0xFF) * (1 - factor) + ((to >> 16) & 0xFF) * factor);
        int g = (int) (((from >> 8) & 0xFF) * (1 - factor) + ((to >> 8) & 0xFF) * factor);
        int b = (int) ((from & 0xFF) * (1 - factor) + (to & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    // Link caches recomputed only when the paper's sphere data actually changes.
    private Map<Integer, ResourceLocation> lastLinkInput;
    private java.util.Set<Integer> lastUnlinked = java.util.Set.of();
    private List<int[]> lastPairs = List.of();
    private Map<Integer, Integer> lastFlowDepth = Map.of();

    private void refreshLinkCaches(Map<Integer, ResourceLocation> placed) {
        if (placed.equals(lastLinkInput)) {
            return;
        }
        lastLinkInput = Map.copyOf(placed);
        // A crafted/corrupt component can carry out-of-range cell indices; every filled
        // key goes through grid.cell() below (and inside LinkingPuzzle), so drop them
        // here or one bad paper crashes the render loop of every viewer.
        Map<Integer, ResourceLocation> sane = new HashMap<>();
        for (Map.Entry<Integer, ResourceLocation> entry : placed.entrySet()) {
            if (entry.getKey() >= 0 && entry.getKey() < grid.size()) {
                sane.put(entry.getKey(), entry.getValue());
            }
        }
        lastUnlinked = io.github.minerguy341.new_age_thaum.core.research.LinkingPuzzle.unlinked(grid, sane);
        List<int[]> pairs = new ArrayList<>();
        Map<Integer, List<Integer>> linkAdjacency = new java.util.HashMap<>();
        for (Map.Entry<Integer, ResourceLocation> entry : sane.entrySet()) {
            for (int neighbor : grid.cell(entry.getKey()).neighbors()) {
                if (neighbor <= entry.getKey()) {
                    continue; // each edge once
                }
                ResourceLocation there = sane.get(neighbor);
                if (there != null && io.github.minerguy341.new_age_thaum.core.aspect.AspectRelations
                        .related(entry.getValue(), there)) {
                    pairs.add(new int[]{entry.getKey(), neighbor});
                    linkAdjacency.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(neighbor);
                    linkAdjacency.computeIfAbsent(neighbor, k -> new ArrayList<>()).add(entry.getKey());
                }
            }
        }
        lastPairs = pairs;

        // Flow depths: the puzzle's endpoints are the springs — multi-source BFS from
        // every endpoint in the web, so the current visibly flows outward from the fixed
        // cells. Components touching no endpoint fall back to their lowest cell index.
        Map<Integer, Integer> depth = new java.util.HashMap<>();
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        ResearchPuzzle puzzle = puzzle();
        if (puzzle != null) {
            for (Integer endpoint : puzzle.endpoints().keySet()) {
                if (linkAdjacency.containsKey(endpoint) && !depth.containsKey(endpoint)) {
                    depth.put(endpoint, 0);
                    queue.add(endpoint);
                }
            }
            drainDepths(queue, depth, linkAdjacency);
        }
        for (Integer start : linkAdjacency.keySet().stream().sorted().toList()) {
            if (!depth.containsKey(start)) {
                depth.put(start, 0);
                queue.add(start);
                drainDepths(queue, depth, linkAdjacency);
            }
        }
        lastFlowDepth = depth;
    }

    private static void drainDepths(java.util.ArrayDeque<Integer> queue, Map<Integer, Integer> depth,
            Map<Integer, List<Integer>> adjacency) {
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int next : adjacency.get(current)) {
                if (!depth.containsKey(next)) {
                    depth.put(next, depth.get(current) + 1);
                    queue.add(next);
                }
            }
        }
    }

    private java.util.Set<Integer> unlinkedFor(Map<Integer, ResourceLocation> placed) {
        refreshLinkCaches(placed);
        return lastUnlinked;
    }

    private List<int[]> linkedPairsFor(Map<Integer, ResourceLocation> placed) {
        refreshLinkCaches(placed);
        return lastPairs;
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
                playUi(SoundEvents.ITEM_PICKUP, 1.3f);
                return true;
            }
        } else if (inSphere(mouseX, mouseY)) {
            int cell = pickCell(mouseX, mouseY);
            if (button == 1 && cell >= 0) {
                clearCell(cell);
                return true;
            }
            if (button == 0) {
                // Grabbing the sphere catches it: any ongoing momentum stops.
                rotating = true;
                spinning = false;
                spinVelX = 0;
                spinVelY = 0;
                lastDragMillis = net.minecraft.Util.getMillis();
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
            // Smoothed velocity estimate feeding the flick momentum on release.
            long now = net.minecraft.Util.getMillis();
            double dt = Math.max(1, now - lastDragMillis);
            lastDragMillis = now;
            spinVelX = spinVelX * 0.75 + (dragX / dt) * 0.25;
            spinVelY = spinVelY * 0.75 + (dragY / dt) * 0.25;
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
        if (rotating) {
            rotating = false;
            long now = net.minecraft.Util.getMillis();
            // A flick (release while still moving) keeps the sphere spinning; a release
            // after holding still (no drag events for 100ms) parks it where it is.
            if (now - lastDragMillis <= 100 && Math.hypot(spinVelX, spinVelY) > 0.04) {
                spinning = true;
                lastSpinTick = now;
            } else {
                spinning = false;
                pushOrientation(true); // final pose always reaches the server
            }
            return true;
        }
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
        if (aspect.equals(placedMap().get(cell))) {
            // Repainting the same aspect changes nothing — don't send a packet the
            // server would (correctly) treat as a paid edit.
            return;
        }
        if (!hasPaper() || isLockedCell(cell) || ClientPlayerProgress.get().points(aspect) < 1) {
            // Audible refusal — a silent no-op here reads as "placement is broken".
            // No paper, a locked endpoint/gap cell, or can't afford the 1-point
            // placement cost (server re-checks all of it).
            playUi(SoundEvents.VILLAGER_NO, 1.0f);
            return;
        }
        net.minecraft.world.item.ItemStack paper = paperStack();
        paper.set(ModComponents.RESEARCH_SPHERE.get(),
                paper.getOrDefault(ModComponents.RESEARCH_SPHERE.get(), ResearchSphereData.EMPTY).with(cell, aspect));
        NewAgeThaumNetwork.sendOrreryEdit(this.menu.pos(), cell, Optional.of(aspect));
        // Each aspect chimes at its own pitch.
        playUi(SoundEvents.AMETHYST_CLUSTER_PLACE, 0.9f + Math.floorMod(aspect.hashCode(), 6) * 0.08f);
    }

    private void clearCell(int cell) {
        if (!hasPaper()) {
            return;
        }
        if (isLockedCell(cell)) {
            playUi(SoundEvents.VILLAGER_NO, 1.0f);
            return;
        }
        if (!placedMap().containsKey(cell)) {
            return; // nothing painted here — no packet for a no-op clear
        }
        net.minecraft.world.item.ItemStack paper = paperStack();
        paper.set(ModComponents.RESEARCH_SPHERE.get(),
                paper.getOrDefault(ModComponents.RESEARCH_SPHERE.get(), ResearchSphereData.EMPTY).without(cell));
        NewAgeThaumNetwork.sendOrreryEdit(this.menu.pos(), cell, Optional.empty());
    }

    // --- helpers ---------------------------------------------------------------

    /** Friction integration for flick momentum; runs once per frame while the screen is open. */
    private void tickSpin() {
        if (!spinning || rotating) {
            return;
        }
        long now = net.minecraft.Util.getMillis();
        double dt = Math.min(100, now - lastSpinTick); // clamp frame hitches
        lastSpinTick = now;
        if (dt <= 0) {
            return;
        }
        applyRotation(spinVelX * dt, spinVelY * dt);
        double decay = Math.exp(-dt / 700.0); // ~3s from a fast flick to standstill
        spinVelX *= decay;
        spinVelY *= decay;
        if (Math.hypot(spinVelX, spinVelY) < 0.004) {
            spinning = false;
            pushOrientation(true); // the rest pose reaches the server exactly
        }
    }

    @Override
    public void removed() {
        if (spinning) {
            pushOrientation(true); // closing mid-spin parks the hologram at the current pose
        }
        super.removed();
    }

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
        pushOrientation(false);
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
        // Truncate exactly like renderList's `listTop - (int) scroll`, or a fractional
        // scrollbar drag makes the top pixel row of each entry hit-test as its neighbor.
        int index = (int) ((y - listTop + (int) scroll) / ROW_HEIGHT);
        return index >= 0 && index < aspects.size() ? aspects.get(index) : null;
    }

    private int pickCell(double mx, double my) {
        int best = -1;
        double bestZ = Double.NEGATIVE_INFINITY;
        for (GoldbergGrid.Cell cell : grid.cells()) {
            if (isGapCell(cell.index())) {
                continue; // holes can't be picked
            }
            Vector3f c = rotate(cell.x(), cell.y(), cell.z());
            if (c.z <= 0) {
                continue; // faded out at the horizon — not clickable
            }
            double[][] poly = visiblePolygon(cell);
            if (poly == null) {
                continue;
            }
            double[] xs = new double[poly.length];
            double[] ys = new double[poly.length];
            for (int i = 0; i < poly.length; i++) {
                xs[i] = poly[i][0];
                ys[i] = poly[i][1];
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

    /**
     * The cell's corner polygon, clipped at the horizon plane (z = 0) BEFORE projection
     * and then projected. Orthographic projection folds behind-the-horizon points back
     * inside the disc, so an unclipped limb cell overdraws its neighbors' dividers and
     * shows through gap holes. Null when nothing of the cell is visible.
     */
    private double[][] visiblePolygon(GoldbergGrid.Cell cell) {
        double[][] corners = cell.corners();
        List<Vector3f> poly = new ArrayList<>(corners.length);
        boolean anyBehind = false;
        for (double[] corner : corners) {
            Vector3f r = rotate(corner[0], corner[1], corner[2]);
            anyBehind |= r.z < 0;
            poly.add(r);
        }
        if (anyBehind) {
            poly = clipAtHorizon(poly);
            if (poly.size() < 3) {
                return null;
            }
        }
        double[][] projected = new double[poly.size()][];
        for (int i = 0; i < poly.size(); i++) {
            projected[i] = project(poly.get(i));
        }
        return projected;
    }

    /** Sutherland–Hodgman against the z >= 0 half-space. */
    private static List<Vector3f> clipAtHorizon(List<Vector3f> poly) {
        List<Vector3f> out = new ArrayList<>(poly.size() + 2);
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            Vector3f prev = poly.get((i + n - 1) % n);
            Vector3f current = poly.get(i);
            boolean prevIn = prev.z >= 0;
            boolean currentIn = current.z >= 0;
            if (prevIn != currentIn) {
                float t = prev.z / (prev.z - current.z);
                out.add(new Vector3f(prev.x + (current.x - prev.x) * t,
                        prev.y + (current.y - prev.y) * t, 0));
            }
            if (currentIn) {
                out.add(current);
            }
        }
        return out;
    }

    /** Vertex average — projection is affine, so this tracks the cell's visible middle. */
    private static double[] polygonCenter(double[][] poly) {
        double x = 0;
        double y = 0;
        for (double[] p : poly) {
            x += p[0];
            y += p[1];
        }
        return new double[]{x / poly.length, y / poly.length};
    }

    private double[] project(Vector3f rotated) {
        return new double[]{sphereCx + rotated.x * sphereR, sphereCy - rotated.y * sphereR};
    }

    private static void playUi(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
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
