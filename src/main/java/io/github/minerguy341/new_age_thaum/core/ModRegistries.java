package io.github.minerguy341.new_age_thaum.core;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.AetherlensItem;
import io.github.minerguy341.new_age_thaum.content.CodexItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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

    /** Throwaway walking-skeleton item; proves DeferredRegister works on both loaders. */
    public static final RegistrySupplier<Item> PROOF_OF_FORGE = ITEMS.register("proof_of_forge",
            () -> new Item(new Item.Properties()));

    /** The scanning tool: turns blocks and entities into observation points. */
    public static final RegistrySupplier<Item> AETHERLENS = ITEMS.register("aetherlens",
            () -> new AetherlensItem(new Item.Properties().stacksTo(1)));

    /** Opens the Codex (progression journal). */
    public static final RegistrySupplier<Item> CODEX = ITEMS.register("codex",
            () -> new CodexItem(new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<CreativeModeTab> MAIN_TAB = TABS.register("main",
            () -> CreativeTabRegistry.create(builder -> builder
                    .title(Component.translatable("itemGroup.new_age_thaum.main"))
                    .icon(() -> new ItemStack(CODEX.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(CODEX.get());
                        output.accept(AETHERLENS.get());
                        output.accept(PROOF_OF_FORGE.get());
                    })));

    private ModRegistries() {
    }

    public static void init() {
        TABS.register();
        ITEMS.register();
    }
}
