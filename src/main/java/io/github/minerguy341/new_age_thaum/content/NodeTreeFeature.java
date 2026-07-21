package io.github.minerguy341.new_age_thaum.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.ModRegistries;
import io.github.minerguy341.new_age_thaum.core.aura.NodePersonality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Grows an inner tree, then with a small chance nests an aura node inside its trunk — the
 * silverwood grove's rare heartwood node (PLAN §4.3/§4.4: silverwood is the pure,
 * aether-leaning wood). The node is pre-seeded {@link NodePersonality#PURE} and a clean
 * primal so it reads as a wholesome wellspring, not a random roll. Reusable: the config
 * names the tree to grow and the chance, so any wood could host nodes later.
 */
public class NodeTreeFeature extends Feature<NodeTreeFeature.Config> {
    /** Clean, order-leaning primals fitting silverwood's pale purity (aether-adjacent). */
    private static final ResourceLocation[] TRUNK_PRIMALS = {
            NewAgeThaum.id("forma"), NewAgeThaum.id("ventus")};

    public NodeTreeFeature() {
        super(Config.CODEC);
    }

    /** {@code tree} = the configured feature to grow; {@code nodeChance} = trunk-node odds. */
    public record Config(Holder<ConfiguredFeature<?, ?>> tree, float nodeChance) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ConfiguredFeature.CODEC.fieldOf("tree").forGetter(Config::tree),
                Codec.floatRange(0f, 1f).fieldOf("node_chance").forGetter(Config::nodeChance)
        ).apply(instance, Config::new));
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        Config config = context.config();
        boolean grew = config.tree().value().place(level, context.chunkGenerator(), random, origin);
        if (grew && random.nextFloat() < config.nodeChance()) {
            embedNode(level, random, origin);
        }
        return grew;
    }

    /** Replaces a mid-trunk log with a pre-seeded aura node. Trunk is the 1-wide column
     * rising from {@code base} (straight_trunk_placer starts at the origin). */
    private void embedNode(WorldGenLevel level, RandomSource random, BlockPos base) {
        Block log = ModRegistries.SILVERWOOD_LOG.get();
        int trunk = 0;
        BlockPos.MutableBlockPos cursor = base.mutable();
        while (trunk < 32 && level.getBlockState(cursor).is(log)) {
            cursor.move(Direction.UP);
            trunk++;
        }
        if (trunk < 3) {
            return; // trunk too short to nest a node without gutting it
        }
        // A mid-trunk log — never the base or the tip — becomes the node's berth.
        BlockPos nodePos = base.above(1 + random.nextInt(trunk - 2));
        setBlock(level, nodePos, ModRegistries.AURA_NODE.get().defaultBlockState());
        if (level.getBlockEntity(nodePos) instanceof AuraNodeBlockEntity node) {
            ResourceLocation aspect = TRUNK_PRIMALS[random.nextInt(TRUNK_PRIMALS.length)];
            node.seedIdentity(aspect, NodePersonality.PURE, 1.0f + random.nextFloat() * 0.5f);
        }
    }
}
