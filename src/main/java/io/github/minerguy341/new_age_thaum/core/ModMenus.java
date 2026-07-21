package io.github.minerguy341.new_age_thaum.core;

import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.content.ArcaneOrreryMenu;
import io.github.minerguy341.new_age_thaum.content.ArcaneWorktableMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

/** Menu types. Each carries its BlockPos as extended data. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(NewAgeThaum.MOD_ID, Registries.MENU);

    public static final RegistrySupplier<MenuType<ArcaneOrreryMenu>> ARCANE_ORRERY =
            MENUS.register("arcane_orrery", () -> MenuRegistry.ofExtended(ArcaneOrreryMenu::new));

    public static final RegistrySupplier<MenuType<ArcaneWorktableMenu>> ARCANE_WORKTABLE =
            MENUS.register("arcane_worktable", () -> MenuRegistry.ofExtended(ArcaneWorktableMenu::new));

    private ModMenus() {
    }

    public static void init() {
        MENUS.register();
    }
}
