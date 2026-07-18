package io.github.minerguy341.new_age_thaum.core;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.AetherlensItem;
import io.github.minerguy341.new_age_thaum.content.CastingImplementItem;
import io.github.minerguy341.new_age_thaum.content.CodexItem;
import io.github.minerguy341.new_age_thaum.content.ResearchSphereBlock;
import io.github.minerguy341.new_age_thaum.content.WandPartItem;
import io.github.minerguy341.new_age_thaum.core.casting.WandForm;
import io.github.minerguy341.new_age_thaum.core.casting.WandMaterial;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * All game-object registration goes through Architectury's {@link DeferredRegister}
 * so common code never touches a loader registry directly (PLAN.md §3 rule 1).
 * The creative tab uses the vanilla displayItems callback instead of Architectury's
 * arch$tab interface injection, which does not resolve reliably at compile time.
 */
public final class ModRegistries {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(NewAgeThaum.MOD_ID, Registries.CREATIVE_MODE_TAB);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(NewAgeThaum.MOD_ID, Registries.ITEM);
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(NewAgeThaum.MOD_ID, Registries.BLOCK);

    /** Opens the research sphere (linking-puzzle) UI on right-click. */
    public static final RegistrySupplier<Block> ARCANE_ORRERY = BLOCKS.register("arcane_orrery",
            () -> new ResearchSphereBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(2.0f).requiresCorrectToolForDrops().lightLevel(s -> 6)));
    public static final RegistrySupplier<Item> ARCANE_ORRERY_ITEM = blockItem(ARCANE_ORRERY);

    /** Throwaway walking-skeleton item; proves DeferredRegister works on both loaders. */
    public static final RegistrySupplier<Item> PROOF_OF_FORGE = ITEMS.register("proof_of_forge",
            () -> new Item(new Item.Properties()));

    // --- Homage trees (art-direction.md): greatwood warm and massive, silverwood pale
    // and pure. Tree shapes live in datapack JSON (worldgen/configured_feature); the
    // saplings grow them by key, and TREE_PLACEMENTS below is the single source of
    // truth for world placement.
    public static final ResourceKey<ConfiguredFeature<?, ?>> GREATWOOD_TREE = treeFeature("greatwood_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SILVERWOOD_TREE = treeFeature("silverwood_tree");

    public static final RegistrySupplier<Block> GREATWOOD_LOG =
            log("greatwood_log", Blocks.OAK_LOG, MapColor.PODZOL);
    public static final RegistrySupplier<Block> GREATWOOD_LEAVES =
            leaves("greatwood_leaves", Blocks.OAK_LEAVES);
    public static final RegistrySupplier<Block> GREATWOOD_SAPLING =
            sapling("greatwood_sapling", GREATWOOD_TREE, Blocks.OAK_SAPLING, p -> p);
    public static final RegistrySupplier<Block> SILVERWOOD_LOG =
            log("silverwood_log", Blocks.BIRCH_LOG, MapColor.QUARTZ);
    public static final RegistrySupplier<Block> SILVERWOOD_LEAVES =
            leaves("silverwood_leaves", Blocks.BIRCH_LEAVES);
    public static final RegistrySupplier<Block> SILVERWOOD_SAPLING =
            sapling("silverwood_sapling", SILVERWOOD_TREE, Blocks.BIRCH_SAPLING,
                    p -> p.lightLevel(s -> 4)); // faint magic glow (art-direction.md)

    public static final RegistrySupplier<Item> GREATWOOD_LOG_ITEM = blockItem(GREATWOOD_LOG);
    public static final RegistrySupplier<Item> GREATWOOD_LEAVES_ITEM = blockItem(GREATWOOD_LEAVES);
    public static final RegistrySupplier<Item> GREATWOOD_SAPLING_ITEM = blockItem(GREATWOOD_SAPLING);
    public static final RegistrySupplier<Item> SILVERWOOD_LOG_ITEM = blockItem(SILVERWOOD_LOG);
    public static final RegistrySupplier<Item> SILVERWOOD_LEAVES_ITEM = blockItem(SILVERWOOD_LEAVES);
    public static final RegistrySupplier<Item> SILVERWOOD_SAPLING_ITEM = blockItem(SILVERWOOD_SAPLING);

    /** One tree's world placement: biome tag -> placed feature at VEGETAL_DECORATION. */
    public record TreePlacement(TagKey<Biome> biomes, ResourceKey<PlacedFeature> feature) {
    }

    /**
     * The single source of truth for tree placement wiring. The Fabric entrypoint
     * iterates this list (BiomeModifications); NeoForge mirrors each pair as
     * datapack-native data/neoforge/biome_modifier JSON so pack devs can override it;
     * TreeGenGameTest asserts on BOTH loaders that every pair actually reached the
     * tagged biomes' generation settings — a rename breaks the tests, never silently
     * one loader.
     */
    public static final List<TreePlacement> TREE_PLACEMENTS = List.of(
            new TreePlacement(biomeTag("has_greatwood"), placedFeature("greatwood_trees")),
            new TreePlacement(biomeTag("has_silverwood"), placedFeature("silverwood_trees")));

    private static ResourceKey<ConfiguredFeature<?, ?>> treeFeature(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, name));
    }

    private static ResourceKey<PlacedFeature> placedFeature(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, name));
    }

    private static TagKey<Biome> biomeTag(String name) {
        return TagKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, name));
    }

    private static RegistrySupplier<Block> log(String name, Block base, MapColor color) {
        return BLOCKS.register(name, () -> new RotatedPillarBlock(
                BlockBehaviour.Properties.ofFullCopy(base).mapColor(color)));
    }

    private static RegistrySupplier<Block> leaves(String name, Block base) {
        return BLOCKS.register(name, () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(base)));
    }

    private static RegistrySupplier<Block> sapling(String name, ResourceKey<ConfiguredFeature<?, ?>> tree,
            Block base, UnaryOperator<BlockBehaviour.Properties> tweak) {
        return BLOCKS.register(name, () -> new SaplingBlock(
                new TreeGrower(tree.location().toString(), Optional.empty(), Optional.of(tree), Optional.empty()),
                tweak.apply(BlockBehaviour.Properties.ofFullCopy(base))));
    }

    private static RegistrySupplier<Item> blockItem(RegistrySupplier<Block> block) {
        return ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
    }

    /** The scanning tool: turns blocks and entities into observation points. */
    public static final RegistrySupplier<Item> AETHERLENS = ITEMS.register("aetherlens",
            () -> new AetherlensItem(new Item.Properties().stacksTo(1)));

    /** Opens the Codex (progression journal). */
    public static final RegistrySupplier<Item> CODEX = ITEMS.register("codex",
            () -> new CodexItem(new Item.Properties().stacksTo(1)));

    // Tiered research papers (the plain paper is gone): each generates a random puzzle
    // of its tier on first insertion into an orrery. One puzzle per paper, no stacking.
    public static final RegistrySupplier<Item> PAPER_FLEDGLING = paper("research_paper_fledgling",
            io.github.minerguy341.new_age_thaum.content.ResearchPaperItem.Tier.FLEDGLING);
    public static final RegistrySupplier<Item> PAPER_APPRENTICE = paper("research_paper_apprentice",
            io.github.minerguy341.new_age_thaum.content.ResearchPaperItem.Tier.APPRENTICE);
    public static final RegistrySupplier<Item> PAPER_SCHOLAR = paper("research_paper_scholar",
            io.github.minerguy341.new_age_thaum.content.ResearchPaperItem.Tier.SCHOLAR);
    public static final RegistrySupplier<Item> PAPER_MASTER = paper("research_paper_master",
            io.github.minerguy341.new_age_thaum.content.ResearchPaperItem.Tier.MASTER);
    public static final RegistrySupplier<Item> PAPER_GRANDMASTER = paper("research_paper_grandmaster",
            io.github.minerguy341.new_age_thaum.content.ResearchPaperItem.Tier.GRANDMASTER);

    private static RegistrySupplier<Item> paper(String name,
            io.github.minerguy341.new_age_thaum.content.ResearchPaperItem.Tier tier) {
        return ITEMS.register(name, () -> new io.github.minerguy341.new_age_thaum.content.ResearchPaperItem(
                new Item.Properties(), tier));
    }

    // Wand parts: rods (cores) and caps. Each is bound to a wand-material id.
    public static final RegistrySupplier<Item> GREATWOOD_ROD = rod("greatwood_rod", "greatwood");
    public static final RegistrySupplier<Item> SILVERWOOD_ROD = rod("silverwood_rod", "silverwood");
    public static final RegistrySupplier<Item> BRASS_CAP = cap("brass_cap", "brass");
    public static final RegistrySupplier<Item> AETHERIUM_CAP = cap("aetherium_cap", "aetherium");

    /** Assembled implements. Their materials live in the WAND data component. */
    public static final RegistrySupplier<Item> WAND = ITEMS.register("wand",
            () -> new CastingImplementItem(new Item.Properties(), WandForm.WAND));
    public static final RegistrySupplier<Item> STAVE = ITEMS.register("stave",
            () -> new CastingImplementItem(new Item.Properties(), WandForm.STAVE));

    public static final RegistrySupplier<CreativeModeTab> MAIN_TAB = TABS.register("main",
            () -> CreativeTabRegistry.create(builder -> builder
                    .title(Component.translatable("itemGroup.new_age_thaum.main"))
                    .icon(() -> new ItemStack(WAND.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ARCANE_ORRERY_ITEM.get());
                        output.accept(GREATWOOD_LOG_ITEM.get());
                        output.accept(GREATWOOD_LEAVES_ITEM.get());
                        output.accept(GREATWOOD_SAPLING_ITEM.get());
                        output.accept(SILVERWOOD_LOG_ITEM.get());
                        output.accept(SILVERWOOD_LEAVES_ITEM.get());
                        output.accept(SILVERWOOD_SAPLING_ITEM.get());
                        output.accept(PAPER_FLEDGLING.get());
                        output.accept(PAPER_APPRENTICE.get());
                        output.accept(PAPER_SCHOLAR.get());
                        output.accept(PAPER_MASTER.get());
                        output.accept(PAPER_GRANDMASTER.get());
                        output.accept(WAND.get());
                        output.accept(STAVE.get());
                        output.accept(GREATWOOD_ROD.get());
                        output.accept(SILVERWOOD_ROD.get());
                        output.accept(BRASS_CAP.get());
                        output.accept(AETHERIUM_CAP.get());
                        output.accept(CODEX.get());
                        output.accept(AETHERLENS.get());
                        output.accept(PROOF_OF_FORGE.get());
                    })));

    private ModRegistries() {
    }

    private static RegistrySupplier<Item> rod(String name, String material) {
        return ITEMS.register(name, () -> new WandPartItem(new Item.Properties(),
                ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, material), WandMaterial.Kind.CORE));
    }

    private static RegistrySupplier<Item> cap(String name, String material) {
        return ITEMS.register(name, () -> new WandPartItem(new Item.Properties(),
                ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, material), WandMaterial.Kind.CAP));
    }

    public static void init() {
        ModComponents.init();
        BLOCKS.register();
        TABS.register();
        ITEMS.register();
    }
}
