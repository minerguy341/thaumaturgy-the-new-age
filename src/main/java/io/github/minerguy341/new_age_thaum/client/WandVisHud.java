package io.github.minerguy341.new_age_thaum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.event.events.client.ClientGuiEvent;
import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.CastingImplementItem;
import io.github.minerguy341.new_age_thaum.core.ModComponents;
import io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig;
import io.github.minerguy341.new_age_thaum.core.aspect.Aspect;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRegistry;
import io.github.minerguy341.new_age_thaum.core.aspect.Primals;
import io.github.minerguy341.new_age_thaum.core.casting.WandComponent;
import io.github.minerguy341.new_age_thaum.core.casting.WandForm;
import io.github.minerguy341.new_age_thaum.core.casting.WandStats;
import io.github.minerguy341.new_age_thaum.core.casting.WandVis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * The wand/stave vis HUD (PLAN §6, redesigned): a wand- or stave-shaped bar drawn above
 * the vanilla health row while an assembled implement is held. The bar's core shaft comes
 * from the wand's core material, its two end caps from its caps (emitter tip + butt
 * pommel), and its six chambers show per-primal stored vis against the wand's capacity,
 * each with the ambient-recharge floor as a white tick. Dual-wielding stacks a second bar.
 *
 * <p>The chrome is authored art (the {@code textures/gui/wand_hud} sprites, HD 2x); this
 * renderer composites those sprites and draws the dynamic chambers over them. Everything
 * is drawn inside a half-scaled pose so the 2x sprites land at native HUD size but keep
 * their extra pixel density — the whole bar is authored in "2x space" and shrunk to fit.
 * Colors come from the synced aspect registry; stored vis and the floor marks arrive
 * through the WAND_VIS component sync and the casting-config mirror, so no extra packets.
 *
 * <p>The vanilla health/hunger/mount rows are <em>not</em> repositioned (that needs a Gui
 * mixin with no clean cross-loader hook); the bar is parked in the free space just above
 * them instead.
 */
public final class WandVisHud {

    // Authoring resolution: the sprites are 2x, so the bar is laid out in doubled pixels
    // and drawn under a 0.5 pose scale. These match build_wandhud_hd.py exactly.
    private static final int SCALE = 2;
    private static final int CH_H = 14 * SCALE;   // wand bar height (2x space)
    private static final int ST_H = 18 * SCALE;   // stave bar height (2x space)
    private static final int TOTAL_W = 182 * SCALE; // full bar width (2x space) — matches the hotbar
    private static final int SEAT = 3;            // rod underlap into each cap's full-height seat

    // Each bar's on-screen anchor/offset/scale is read from config (HudLayout), editable
    // in-game via /thaum hud. The two bars are independent movable elements.
    public static final double MIN_SCALE = 0.3;
    public static final double MAX_SCALE = 2.0;

    /** Set by the transform editor while it's open, so the live HUD yields to its mock bars. */
    public static boolean editorActive;

    // Static chrome colors (ARGB), matching the art builder.
    private static final int CH = 0xFF0E0910;   // chamber recess
    private static final int CHS = 0xFF060309;  // chamber inner shadow
    private static final int GEM = 0xFF7FE8D8;  // teal gem on the centre rib
    private static final int SHADE = 0xFF120C10; // fill bottom-shade blend target
    private static final int FLOOR_TICK = 0xDCFFFFFF; // ambient-floor mark (alpha 220)

    // 6-step metal ramps (dark..light), ARGB — used to tint the chamber-divider ribs.
    private static final int[] BRASS = {
            0xFF4E3618, 0xFF6E4E26, 0xFF8F6B38, 0xFFB98B48, 0xFFD8B06A, 0xFFF2D493};
    private static final int[] AETHERIUM = {
            0xFF2E2148, 0xFF453266, 0xFF5A4380, 0xFF7A5BA0, 0xFF9E80C4, 0xFFC2A6E4};

    // Fallback primal colors if the aspect registry hasn't synced yet (art builder values).
    private static final int[] PRIMAL_FALLBACK = {
            0xCDE8F5, 0x6BA84F, 0xF0552B, 0x3D9BE0, 0xEDE9DC, 0x4A3459};

    private WandVisHud() {
    }

    public static void register() {
        ClientGuiEvent.RENDER_HUD.register(WandVisHud::render);
    }

    /** One resolved chrome sprite: its texture plus its native (2x) pixel size. */
    private record Sprite(ResourceLocation tex, int w, int h) {
    }

    private static Sprite sprite(String name, int w, int h) {
        return new Sprite(NewAgeThaum.id("textures/gui/wand_hud/" + name + ".png"), w, h);
    }

    private static void render(GuiGraphics graphics, DeltaTracker tickDelta) {
        if (!NewAgeThaumConfig.wandHudEnabled) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        // The editor draws its own mock bars — suppress the live HUD while it's open.
        if (editorActive) {
            return;
        }
        // Two independent movable elements: main-hand bar (emitter left) and off-hand bar
        // (emitter right), each at its own configured layout, drawn only when that hand
        // holds an assembled implement.
        int guiW = graphics.guiWidth();
        int guiH = graphics.guiHeight();
        ItemStack main = player.getMainHandItem();
        if (assembled(main)) {
            drawAt(graphics, layout(false), guiW, guiH, barDataOf(main), true);
        }
        ItemStack off = player.getOffhandItem();
        if (assembled(off)) {
            drawAt(graphics, layout(true), guiW, guiH, barDataOf(off), false);
        }
    }

    private static boolean assembled(ItemStack stack) {
        if (!(stack.getItem() instanceof CastingImplementItem implement)) {
            return false;
        }
        WandComponent component = CastingImplementItem.componentOf(stack);
        return component != null
                && WandStats.compute(component, implement.form()).capacity() > 0;
    }

    /** Resolve a layout's screen position for a bar's content box, then draw it there. */
    private static void drawAt(GuiGraphics graphics, HudLayout layout, int guiW, int guiH,
                               BarData data, boolean tipLeft) {
        int cw = contentW(layout.scale);
        int ch = contentH(layout.scale, data.stave());
        drawBar(graphics, layout.screenX(guiW, cw), layout.screenY(guiH, ch), layout.scale, data, tipLeft);
    }

    // ---- editor-facing API (same package) --------------------------------------------
    static int contentW(double scale) {
        return (int) Math.round(TOTAL_W * scale / SCALE);
    }

    static int contentH(double scale, boolean stave) {
        return (int) Math.round((stave ? ST_H : CH_H) * scale / SCALE);
    }

    /** Read a bar's layout from config (off-hand when {@code off}). */
    static HudLayout layout(boolean off) {
        return off
                ? new HudLayout(NewAgeThaumConfig.hudOffAnchorX, NewAgeThaumConfig.hudOffAnchorY,
                        NewAgeThaumConfig.hudOffOffX, NewAgeThaumConfig.hudOffOffY, NewAgeThaumConfig.hudOffScale)
                : new HudLayout(NewAgeThaumConfig.hudMainAnchorX, NewAgeThaumConfig.hudMainAnchorY,
                        NewAgeThaumConfig.hudMainOffX, NewAgeThaumConfig.hudMainOffY, NewAgeThaumConfig.hudMainScale);
    }

    /** Write both layouts + the scale-link flag back to config and persist. */
    static void saveLayouts(HudLayout main, HudLayout off, boolean scaleLink) {
        NewAgeThaumConfig.hudMainAnchorX = main.anchorX;
        NewAgeThaumConfig.hudMainAnchorY = main.anchorY;
        NewAgeThaumConfig.hudMainOffX = main.offX;
        NewAgeThaumConfig.hudMainOffY = main.offY;
        NewAgeThaumConfig.hudMainScale = main.scale;
        NewAgeThaumConfig.hudOffAnchorX = off.anchorX;
        NewAgeThaumConfig.hudOffAnchorY = off.anchorY;
        NewAgeThaumConfig.hudOffOffX = off.offX;
        NewAgeThaumConfig.hudOffOffY = off.offY;
        NewAgeThaumConfig.hudOffScale = off.scale;
        NewAgeThaumConfig.hudWandScaleLink = scaleLink;
        NewAgeThaumConfig.save();
    }

    /** A representative bar for the editor (greatwood core, brass tip + aetherium pommel). */
    static BarData mockBar(boolean stave) {
        float[] fills = {0.85f, 0.0f, 0.62f, 0.2f, 1.0f, 0.45f};
        float[] floors = {0.3f, 0.55f, 0.3f, 0.55f, 0.3f, 0.3f};
        int[] colors = new int[6];
        for (int i = 0; i < 6; i++) {
            colors[i] = 0xFF000000 | PRIMAL_FALLBACK[i];
        }
        return new BarData("greatwood", NewAgeThaum.id("brass"), NewAgeThaum.id("aetherium"),
                stave, fills, floors, colors);
    }

    /** Everything drawBar needs, resolved once per frame (real) or faked (editor). */
    record BarData(String core, ResourceLocation capA, ResourceLocation capB, boolean stave,
                   float[] fills, float[] floors, int[] colors) {
    }

    private static BarData barDataOf(ItemStack stack) {
        CastingImplementItem implement = (CastingImplementItem) stack.getItem();
        WandComponent component = CastingImplementItem.componentOf(stack);
        WandStats stats = WandStats.compute(component, implement.form());
        boolean stave = implement.form() == WandForm.STAVE;
        float capacity = (float) stats.capacity();
        Set<ResourceLocation> affinity =
                stats.rechargeAffinity().map(Primals::primalsOf).orElse(Set.of());
        WandVis vis = stack.getOrDefault(ModComponents.WAND_VIS.get(), WandVis.EMPTY);
        float ambientFloor = Mth.clamp(ClientCastingConfig.ambientFloor(), 0f, 1f);
        float affinityFloor = Mth.clamp(ClientCastingConfig.affinityFloor(), 0f, 1f);
        float[] fills = new float[6];
        float[] floors = new float[6];
        int[] colors = new int[6];
        for (int i = 0; i < 6; i++) {
            ResourceLocation primal = Primals.ORDER.get(i);
            fills[i] = capacity <= 0f ? 0f : Mth.clamp(vis.get(primal) / capacity, 0f, 1f);
            floors[i] = affinity.contains(primal) ? affinityFloor : ambientFloor;
            colors[i] = 0xFF000000 | AspectRegistry.get(primal).map(Aspect::color)
                    .orElse(PRIMAL_FALLBACK[i]);
        }
        return new BarData(corePath(component.core()), component.capA(), component.capB(),
                stave, fills, floors, colors);
    }

    /**
     * Draw a bar's chrome at ({@code screenLeft}, {@code screenTop}) in native HUD pixels,
     * scaled by {@code scale}. {@code tipLeft} points the emitter cap left (main hand). The
     * 2x sprites are drawn under a {@code scale/SCALE} pose so detail is preserved.
     */
    static void drawBar(GuiGraphics graphics, int screenLeft, int screenTop, double scale,
                        BarData d, boolean tipLeft) {
        boolean stave = d.stave();
        int h = stave ? ST_H : CH_H;
        String core = d.core();
        int[] tipRamp = capRamp(d.capA());
        int[] pommelRamp = capRamp(d.capB());

        // Emitter (tip) = capA, butt (pommel) = capB. When the emitter faces left we use the
        // re-lit "_faceleft" sprite variants so the shine stays top-left after the mirror.
        Sprite leftSprite;
        Sprite rightSprite;
        int[] leftRamp;
        int[] rightRamp;
        if (tipLeft) {
            leftSprite = capSprite(d.capA(), true, stave, true);
            rightSprite = capSprite(d.capB(), false, stave, true);
            leftRamp = tipRamp;
            rightRamp = pommelRamp;
        } else {
            leftSprite = capSprite(d.capB(), false, stave, false);
            rightSprite = capSprite(d.capA(), true, stave, false);
            leftRamp = pommelRamp;
            rightRamp = tipRamp;
        }
        Sprite shaft = sprite("hshaft_" + core + (stave ? "_stave" : ""), 48, h);

        float[] fills = d.fills();
        float[] floors = d.floors();
        int[] colors = d.colors();

        // Authored in 2x space; the scale/SCALE pose lands it on screen at `scale` of native
        // size while keeping the sprites' extra pixel density.
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(screenLeft, screenTop, 0);
        pose.scale((float) scale / SCALE, (float) scale / SCALE, 1f);

        int sx0 = leftSprite.w() - SEAT;
        int sx1 = TOTAL_W - rightSprite.w() + SEAT;
        for (int xx = sx0; xx < sx1; xx += shaft.w()) {
            int wv = Math.min(shaft.w(), sx1 - xx);
            graphics.blit(shaft.tex(), xx, 0, 0, 0, wv, h, shaft.w(), shaft.h());
        }

        int chanTop = 8;
        int chanH = h - 16;
        double cellsX0 = sx0 + 6;
        double cellsX1 = sx1 - 6;
        double cw = (cellsX1 - cellsX0) / 6.0;
        for (int i = 0; i < 6; i++) {
            int wx0 = (int) Math.round(cellsX0 + i * cw) + 3;
            int wx1 = (int) Math.round(cellsX0 + (i + 1) * cw) - 2;
            int wy0 = chanTop;
            int wy1 = chanTop + chanH - 1;
            fillIncl(graphics, wx0, wy0, wx1, wy1, CH);
            fillIncl(graphics, wx0, wy0, wx1, wy0 + 1, CHS);   // top inner shadow
            fillIncl(graphics, wx0, wy0, wx0 + 1, wy1, CHS);   // left inner shadow
            int col = colors[i];
            int fw = (int) ((wx1 - wx0) * fills[i]);
            if (fw > 0) {
                fillIncl(graphics, wx0 + 2, wy0 + 2, wx0 + 2 + fw, wy1 - 1, col);
                fillIncl(graphics, wx0 + 2, wy0 + 2, wx0 + 2 + fw, wy0 + 3, blend(col, 0xFFFFFFFF, 0.45f));
                fillIncl(graphics, wx0 + 2, wy1 - 1, wx0 + 2 + fw, wy1 - 1, blend(col, SHADE, 0.35f));
            }
            int ftx = wx0 + 2 + (int) ((wx1 - wx0 - 2) * floors[i]);
            fillIncl(graphics, ftx, wy0 - 1, ftx + 1, wy1 + 1, FLOOR_TICK);
        }

        // Chamber-divider ribs: colored by the near cap's metal so the divider tone flows
        // from the left cap to the right across the bar (centre rib carries the teal gem).
        for (int i = 0; i < 7; i++) {
            int dx = (int) Math.round(cellsX0 + i * cw);
            if (i == 3) {
                rib(graphics, dx, h, leftRamp, true);
            } else {
                rib(graphics, dx, h, i < 3 ? leftRamp : rightRamp, false);
            }
        }

        graphics.blit(leftSprite.tex(), 0, 0, 0, 0, leftSprite.w(), h, leftSprite.w(), leftSprite.h());
        graphics.blit(rightSprite.tex(), TOTAL_W - rightSprite.w(), 0, 0, 0,
                rightSprite.w(), h, rightSprite.w(), rightSprite.h());

        pose.popPose();
    }

    private static void rib(GuiGraphics graphics, int dx, int h, int[] ramp, boolean center) {
        int y0 = 3;
        int y1 = h - 4;
        if (center) {
            fillIncl(graphics, dx - 2, y0, dx - 2, y1, ramp[0]);
            fillIncl(graphics, dx - 1, y0, dx, y1, ramp[ramp.length - 1]);
            fillIncl(graphics, dx + 1, y0, dx + 2, y1, ramp[2]);
            fillIncl(graphics, dx + 3, y0, dx + 3, y1, ramp[0]);
            fillIncl(graphics, dx, h / 2 - 1, dx + 1, h / 2, GEM);
        } else {
            fillIncl(graphics, dx, y0, dx + 1, y1, ramp[ramp.length - 1]);
            fillIncl(graphics, dx + 2, y0, dx + 2, y1, ramp[1]);
        }
    }

    /** Fill with inclusive max corner (the art was authored with Pillow's inclusive rects). */
    private static void fillIncl(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1 + 1, y1 + 1, color);
    }

    private static String corePath(ResourceLocation core) {
        return "silverwood".equals(core.getPath()) ? "silverwood" : "greatwood";
    }

    private static String capName(ResourceLocation cap) {
        return "aetherium".equals(cap.getPath()) ? "aetherium" : "brass";
    }

    private static int[] capRamp(ResourceLocation cap) {
        return "aetherium".equals(cap.getPath()) ? AETHERIUM : BRASS;
    }

    private static Sprite capSprite(ResourceLocation cap, boolean tip, boolean stave, boolean faceleft) {
        String mat = capName(cap);
        String role = tip ? "tip" : "pommel";
        String name = "hcap_" + mat + "_" + role + (stave ? "_stave" : "") + (faceleft ? "_faceleft" : "");
        int h = stave ? ST_H : CH_H;
        int w;
        if (tip) {
            w = stave ? 44 : ("brass".equals(mat) ? 48 : 32);
        } else {
            w = stave ? 36 : 22;
        }
        return sprite(name, w, h);
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
