package io.github.minerguy341.new_age_thaum.client;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.event.events.client.ClientTooltipEvent;
import dev.architectury.registry.menu.MenuRegistry;
import io.github.minerguy341.new_age_thaum.content.ArcaneOrreryBlockEntity;
import io.github.minerguy341.new_age_thaum.core.ModBlockEntities;
import io.github.minerguy341.new_age_thaum.core.ModMenus;
import io.github.minerguy341.new_age_thaum.core.casting.WandMaterialRegistry;
import io.github.minerguy341.new_age_thaum.core.codex.CodexRegistry;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectAssignments;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectBag;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectNames;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectRegistry;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectResolver;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgress;
import io.github.minerguy341.new_age_thaum.network.NewAgeThaumNetwork;
import io.github.minerguy341.new_age_thaum.network.OrreryOrientationPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Client-only setup. Loaded exclusively through {@code EnvExecutor.runInEnv(Env.CLIENT, ...)}
 * so its client-class references never resolve on a dedicated server.
 */
public final class NewAgeThaumClient {
    private NewAgeThaumClient() {
    }

    /** S2C orientation mirror: another player rotated an orrery's sphere. Main thread. */
    public static void applyOrreryOrientation(OrreryOrientationPayload payload) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getBlockEntity(payload.pos())
                instanceof ArcaneOrreryBlockEntity orrery) {
            // Reuses the server-path validation and rest-pose/coast math, so this client
            // lands on the identical settled orientation.
            NewAgeThaumNetwork.applyOrreryRotation(orrery, payload.frame());
        }
    }

    public static void init() {
        WandColors.register();
        OrreryDebugCommand.register();
        // listen() fires the moment the menu type is actually registered: late enough to
        // avoid the NeoForge early-.get() crash, early enough for RegisterMenuScreensEvent
        // (CLIENT_SETUP would be too late — the screen factory would never register).
        ModMenus.ARCANE_ORRERY.listen(type ->
                MenuRegistry.registerScreenFactory(type, ResearchSphereScreen::new));
        ModMenus.ARCANE_WORKTABLE.listen(type ->
                MenuRegistry.registerScreenFactory(type, ArcaneWorktableScreen::new));
        // Same listen() timing rule as the screen factory: no eager .get() in client init.
        ModBlockEntities.ARCANE_ORRERY.listen(type ->
                BlockEntityRendererRegistry
                        .register(type, OrreryHologramRenderer::new));
        ModBlockEntities.AURA_NODE.listen(type ->
                BlockEntityRendererRegistry.register(type, AuraNodeRenderer::new));
        // Synced state is per-server. Without this reset, a vanilla server joined next
        // (which never syncs) would render the previous server's aspects in tooltips and
        // report its point balances. Singleplayer repopulates on world load (reload
        // listeners) and multiplayer on the join sync, so clearing is always safe.
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            if (player == null) {
                // NeoForge can deliver spurious logging-out fires with no player
                // (startup, respawn edges). Wiping live registries on one of those
                // empties the aspect list and codex mid-session — only reset when an
                // actual player is leaving a world. Missing an occasional reset is
                // harmless (the next join sync overwrites everything anyway).
                return;
            }
            ClientPlayerProgress.set(PlayerProgress.EMPTY);
            AspectRegistry.reload(Map.of());
            AspectAssignments.accept(Map.of(), Map.of());
            CodexRegistry.reload(Map.of());
            WandMaterialRegistry.reload(Map.of());
            AspectResolver.invalidate();
        });
        ClientTooltipEvent.ITEM.register((stack, lines, context, flag) -> {
            var level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            AspectBag bag = AspectResolver.resolve(stack.getItem(), level);
            if (bag.isEmpty()) {
                return;
            }
            lines.add(Component.translatable("tooltip.new_age_thaum.aspects").withStyle(ChatFormatting.GRAY));
            for (Map.Entry<ResourceLocation, Integer> entry : bag.ordered()) {
                lines.add(Component.literal("  ")
                        .append(AspectNames.colored(entry.getKey()))
                        .append(Component.literal(" ×" + entry.getValue()).withStyle(ChatFormatting.DARK_GRAY)));
            }
        });
    }
}
