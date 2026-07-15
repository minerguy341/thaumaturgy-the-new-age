//? if neoforge {
package io.github.minerguy341.new_age_thaum.platform.neoforge;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgress;
import io.github.minerguy341.new_age_thaum.platform.PlayerDataBridge;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** NeoForge implementation of the player-data gap: an {@link AttachmentType} with copy-on-death. */
public final class NeoForgePlayerData implements PlayerDataBridge {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, NewAgeThaum.MOD_ID);

    private static final Supplier<AttachmentType<PlayerProgress>> PROGRESS =
            ATTACHMENT_TYPES.register("player_progress", () -> AttachmentType
                    .builder(() -> PlayerProgress.EMPTY)
                    .serialize(PlayerProgress.CODEC)
                    .copyOnDeath()
                    .build());

    public void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    @Override
    public PlayerProgress get(Player player) {
        return player.getData(PROGRESS.get());
    }

    @Override
    public void set(Player player, PlayerProgress progress) {
        player.setData(PROGRESS.get(), progress);
    }
}
//?}
