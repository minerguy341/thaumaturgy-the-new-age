package io.github.minerguy341.new_age_thaum.client;

import net.minecraft.client.Minecraft;

/** Tiny client-only seam so the common {@code CodexItem} never hard-references the Screen class. */
public final class CodexScreenOpener {
    private CodexScreenOpener() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new CodexScreen());
    }
}
