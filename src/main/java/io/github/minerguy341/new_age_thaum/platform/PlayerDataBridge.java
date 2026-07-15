package io.github.minerguy341.new_age_thaum.platform;

import io.github.minerguy341.new_age_thaum.core.player.PlayerProgress;
import net.minecraft.world.entity.player.Player;

/**
 * Platform gap #2 (PLAN.md §3 rule 2, §5): Architectury does not abstract data
 * attachments, so player-attached {@link PlayerProgress} routes through this
 * bridge. NeoForge implements it with {@code AttachmentType}, Fabric with the
 * data-attachment API, each in its own guarded file. Both configure copy-on-death
 * so progress survives respawn.
 */
public interface PlayerDataBridge {
    PlayerProgress get(Player player);

    void set(Player player, PlayerProgress progress);
}
