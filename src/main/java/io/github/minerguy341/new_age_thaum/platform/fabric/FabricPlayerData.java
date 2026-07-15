//? if fabric {
/*package io.github.minerguy341.new_age_thaum.platform.fabric;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgress;
import io.github.minerguy341.new_age_thaum.platform.PlayerDataBridge;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public final class FabricPlayerData implements PlayerDataBridge {
    private static final AttachmentType<PlayerProgress> PROGRESS = AttachmentRegistry
            .<PlayerProgress>builder()
            .initializer(() -> PlayerProgress.EMPTY)
            .persistent(PlayerProgress.CODEC)
            .copyOnDeath()
            .buildAndRegister(ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, "player_progress"));

    @Override
    public PlayerProgress get(Player player) {
        return ((AttachmentTarget) player).getAttachedOrCreate(PROGRESS);
    }

    @Override
    public void set(Player player, PlayerProgress progress) {
        ((AttachmentTarget) player).setAttached(PROGRESS, progress);
    }
}
*///?}
