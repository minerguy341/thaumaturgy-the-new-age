package io.github.minerguy341.new_age_thaum.client;

import io.github.minerguy341.new_age_thaum.core.player.PlayerProgress;

/** Client-side mirror of the local player's progress, updated by the sync payload. */
public final class ClientPlayerProgress {
    private static volatile PlayerProgress current = PlayerProgress.EMPTY;

    private ClientPlayerProgress() {
    }

    public static PlayerProgress get() {
        return current;
    }

    public static void set(PlayerProgress progress) {
        current = progress;
    }
}
