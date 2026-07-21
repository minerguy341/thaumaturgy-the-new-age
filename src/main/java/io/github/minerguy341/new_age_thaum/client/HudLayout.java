package io.github.minerguy341.new_age_thaum.client;

import net.minecraft.util.Mth;

/**
 * One movable HUD element's on-screen transform: a 9-point anchor + pixel offset + scale.
 * Anchor keeps the element pinned to a screen reference (a corner/edge/centre) so it stays
 * put across window resizes and GUI-scale changes; the offset nudges it from there. Shared
 * by the live HUD renderer and the {@code /thaum hud} transform editor so both agree on the
 * exact placement math. (Rotation is a planned follow-up — not stored here yet.)
 *
 * <p>anchorX / anchorY are each one of 0, 0.5, 1 (left/centre/right, top/middle/bottom).
 * The element's own matching anchor handle is placed at
 * {@code (anchorX*guiW + offX, anchorY*guiH + offY)}, so the top-left works out to
 * {@code anchorX*(guiW - contentW) + offX}.
 */
public final class HudLayout {
    public double anchorX;
    public double anchorY;
    public int offX;
    public int offY;
    public double scale;

    public HudLayout(double anchorX, double anchorY, int offX, int offY, double scale) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.offX = offX;
        this.offY = offY;
        this.scale = scale;
    }

    public void set(HudLayout other) {
        this.anchorX = other.anchorX;
        this.anchorY = other.anchorY;
        this.offX = other.offX;
        this.offY = other.offY;
        this.scale = other.scale;
    }

    /** The anchor's pixel X (offset excluded) for a {@code contentW}-wide box. */
    public int anchorPxX(int guiW, int contentW) {
        return (int) Math.round(anchorX * (guiW - contentW));
    }

    /** The anchor's pixel Y (offset excluded) for a {@code contentH}-tall box. */
    public int anchorPxY(int guiH, int contentH) {
        return (int) Math.round(anchorY * (guiH - contentH));
    }

    /** Top-left X for a content box {@code contentW} wide on a {@code guiW}-wide screen. */
    public int screenX(int guiW, int contentW) {
        return anchorPxX(guiW, contentW) + offX;
    }

    /** Top-left Y for a content box {@code contentH} tall on a {@code guiH}-tall screen. */
    public int screenY(int guiH, int contentH) {
        return anchorPxY(guiH, contentH) + offY;
    }

    /**
     * Re-anchor to the nearest of the nine reference points to where the box currently sits,
     * keeping its on-screen position identical (the offset absorbs the difference). Called on
     * drop so an element dragged into a corner stays stuck to that corner on resize.
     */
    public void reanchorNearest(int guiW, int guiH, int contentW, int contentH) {
        int absX = screenX(guiW, contentW);
        int absY = screenY(guiH, contentH);
        anchorX = nearestThird((absX + contentW / 2.0) / Math.max(1, guiW));
        anchorY = nearestThird((absY + contentH / 2.0) / Math.max(1, guiH));
        offX = absX - anchorPxX(guiW, contentW);
        offY = absY - anchorPxY(guiH, contentH);
    }

    private static double nearestThird(double f) {
        if (f < 0.3333) {
            return 0.0;
        }
        return f < 0.6667 ? 0.5 : 1.0;
    }

    public void clampScale(double min, double max) {
        scale = Mth.clamp(scale, min, max);
    }
}
