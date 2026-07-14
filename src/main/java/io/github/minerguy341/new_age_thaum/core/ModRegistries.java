package io.github.minerguy341.new_age_thaum.core;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * All game-object registration goes through Architectury's {@link DeferredRegister}
 * so common code never touches a loader registry directly (PLAN.md §3 rule 1).
 */
public final class ModRegistries {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(NewAgeThaum.MOD_ID, Registries.CREATIVE_MODE_TAB);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(NewAgeThaum.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<CreativeModeTab> MAIN_TAB = TABS.register("main",
            () -> CreativeTabRegistry.create(
                    Component.translatable("itemGroup.new_age_thaum.main"),
                    () -> new ItemStack(ModRegistries.PROOF_OF_FORGE.get())));

    /** Throwaway walking-skeleton item; proves DeferredRegister works on both loaders. */
    public static final RegistrySupplier<Item> PROOF_OF_FORGE = ITEMS.register("proof_of_forge",
            () -> new Item(new Item.Properties().arch$tab(MAIN_TAB)));

    private ModRegistries() {
    }

    public static void init() {
        TABS.register();
        ITEMS.register();
    }
}
