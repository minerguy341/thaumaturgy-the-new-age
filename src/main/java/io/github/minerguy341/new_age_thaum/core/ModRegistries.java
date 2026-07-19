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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

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
    public static final RegistrySupplier<Item> ARCANE_ORRERY_ITEM = ITEMS.register("arcane_orrery",
            () -> new BlockItem(ARCANE_ORRERY.get(), new Item.Properties()));

    /** Floating aura wellspring (PLAN §4.3): worldgen-scattered, feeds the chunk aura. */
    public static final RegistrySupplier<Block> AURA_NODE = BLOCKS.register("aura_node",
            () -> new io.github.minerguy341.new_age_thaum.content.AuraNodeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIAMOND).strength(0.5f).noOcclusion().lightLevel(s -> 9)
                    .sound(net.minecraft.world.level.block.SoundType.AMETHYST)));
    public static final RegistrySupplier<Item> AURA_NODE_ITEM = ITEMS.register("aura_node",
            () -> new BlockItem(AURA_NODE.get(), new Item.Properties()));

    /** Pedestal that projects a holographic vis map of the surrounding chunks. */
    public static final RegistrySupplier<Block> THAUMIC_DIOPTRA = BLOCKS.register("thaumic_dioptra",
            () -> new io.github.minerguy341.new_age_thaum.content.ThaumicDioptraBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).strength(2.0f).noOcclusion().lightLevel(s -> 3)
                    .sound(net.minecraft.world.level.block.SoundType.DEEPSLATE)));
    public static final RegistrySupplier<Item> THAUMIC_DIOPTRA_ITEM = ITEMS.register("thaumic_dioptra",
            () -> new BlockItem(THAUMIC_DIOPTRA.get(), new Item.Properties()));

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
                        output.accept(AURA_NODE_ITEM.get());
                        output.accept(THAUMIC_DIOPTRA_ITEM.get());
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
                    })));

    private ModRegistries() {
    }

    private static RegistrySupplier<Item> rod(String name, String material) {
        return ITEMS.register(name, () -> new WandPartItem(new Item.Properties(),
                NewAgeThaum.id(material), WandMaterial.Kind.CORE));
    }

    private static RegistrySupplier<Item> cap(String name, String material) {
        return ITEMS.register(name, () -> new WandPartItem(new Item.Properties(),
                NewAgeThaum.id(material), WandMaterial.Kind.CAP));
    }

    public static void init() {
        ModComponents.init();
        BLOCKS.register();
        TABS.register();
        ITEMS.register();
    }
}
