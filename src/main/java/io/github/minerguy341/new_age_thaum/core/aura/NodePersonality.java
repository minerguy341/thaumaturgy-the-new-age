package io.github.minerguy341.new_age_thaum.core.aura;

import net.minecraft.util.RandomSource;

/**
 * A node's temperament (PLAN.md §4.3, the TC4 flavor). Each modulates how the node feeds
 * the ambient field on every pump:
 * <ul>
 *   <li>{@link #BRIGHT} — a rich wellspring: more vis per pump.
 *   <li>{@link #PALE} — a faint node: little vis.
 *   <li>{@link #HUNGRY} — a starveling: the poorest output, so its surroundings stay dim.
 *   <li>{@link #TAINTED} — pollutes: normal vis, but raises {@code flux} in its chunk.
 *   <li>{@link #PURE} — cleanses: a little extra vis, and burns {@code flux} down.
 * </ul>
 * The orb renderer tints by personality so a glance reads the node's nature. Weights make
 * the plain temperaments (pale/hungry) common and the dramatic ones (tainted/pure) rare.
 */
public enum NodePersonality {
    BRIGHT("bright", 2, 1.6f, 0f),
    PALE("pale", 4, 0.5f, 0f),
    HUNGRY("hungry", 3, 0.35f, 0f),
    TAINTED("tainted", 1, 1.0f, 0.4f),
    PURE("pure", 1, 1.1f, -0.6f);

    private final String id;
    private final int weight;
    /** Multiplier on the node's vis output per pump. */
    private final float visMultiplier;
    /** Flux added to the node's chunk per pump (negative burns flux down). */
    private final float fluxPerPump;

    NodePersonality(String id, int weight, float visMultiplier, float fluxPerPump) {
        this.id = id;
        this.weight = weight;
        this.visMultiplier = visMultiplier;
        this.fluxPerPump = fluxPerPump;
    }

    public String id() {
        return id;
    }

    public float visMultiplier() {
        return visMultiplier;
    }

    public float fluxPerPump() {
        return fluxPerPump;
    }

    /** The lang key for a scan/tooltip readout of this personality. */
    public String translationKey() {
        return "node_personality.new_age_thaum." + id;
    }

    /** Parse a saved id back to a personality; {@code PALE} if the id is unknown/corrupt. */
    public static NodePersonality byId(String id) {
        for (NodePersonality personality : values()) {
            if (personality.id.equals(id)) {
                return personality;
            }
        }
        return PALE;
    }

    /** A weighted random personality — plain ones common, tainted/pure rare. */
    public static NodePersonality roll(RandomSource random) {
        int total = 0;
        for (NodePersonality personality : values()) {
            total += personality.weight;
        }
        int pick = random.nextInt(total);
        for (NodePersonality personality : values()) {
            pick -= personality.weight;
            if (pick < 0) {
                return personality;
            }
        }
        return PALE; // unreachable; total is positive
    }
}
