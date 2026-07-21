package io.github.minerguy341.new_age_thaum.core.aura;

import io.github.minerguy341.new_age_thaum.core.NewAgeThaumConfig;
import net.minecraft.server.level.ServerLevel;

/**
 * Drives {@link AuraField#diffuse} per dimension on the configured cadence (PLAN §4.3/§5
 * budget-ticker discipline). Scope is configurable: "loaded" (default) restricts
 * diffusion SOURCES to chunks that are loaded and block-ticking — nodes only pump while
 * loaded, so frozen frontier regions cost nothing — while "all" levels out the entire
 * recorded field, unloaded areas included. Registered on
 * {@code TickEvent.SERVER_LEVEL_POST} in {@code NewAgeThaum.init}.
 */
public final class AuraDiffusionTicker {
    private AuraDiffusionTicker() {
    }

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % Math.max(1, NewAgeThaumConfig.auraDiffusionInterval) != 0) {
            return;
        }
        AuraField field = AuraField.get(level);
        if ("all".equalsIgnoreCase(NewAgeThaumConfig.auraDiffusionScope)) {
            field.diffuse();
        } else {
            // shouldTickBlocksAt(long) is an O(1) ticket-level lookup — the exact gate
            // vanilla block ticks use, and it never loads the chunk.
            field.diffuse(level::shouldTickBlocksAt);
        }
    }
}
