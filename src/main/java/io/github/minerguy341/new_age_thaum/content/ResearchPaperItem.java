package io.github.minerguy341.new_age_thaum.content;

import net.minecraft.world.item.Item;

/**
 * A tiered research paper. The puzzle is generated lazily (server-side, from the tier's
 * parameters) the first time the paper sits in an orrery, and stamped on as a component.
 * Tier parameters: endpoint count, gap fraction of cells, how deep into the aspect graph
 * endpoint aspects may reach, and the sphere frequency when tierScaledSpheres is on.
 */
public class ResearchPaperItem extends Item {

    public enum Tier {
        FLEDGLING(2, 0.06, 1, 2),
        APPRENTICE(2, 0.10, 2, 2),
        SCHOLAR(3, 0.14, 3, 3),
        MASTER(4, 0.18, 4, 3),
        GRANDMASTER(5, 0.22, Integer.MAX_VALUE, 4);

        public final int endpointCount;
        public final double gapFraction;
        public final int maxAspectDepth;
        public final int scaledFrequency;

        Tier(int endpointCount, double gapFraction, int maxAspectDepth, int scaledFrequency) {
            this.endpointCount = endpointCount;
            this.gapFraction = gapFraction;
            this.maxAspectDepth = maxAspectDepth;
            this.scaledFrequency = scaledFrequency;
        }
    }

    private final Tier tier;

    public ResearchPaperItem(Properties properties, Tier tier) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public Tier tier() {
        return tier;
    }
}
