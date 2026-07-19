package io.github.minerguy341.new_age_thaum.network;

import net.minecraft.util.Mth;

/** Decode-side guards shared by the hand-rolled stream codecs. */
public final class NetworkLimits {
    private NetworkLimits() {
    }

    /**
     * Pre-allocation cap: a hostile peer can claim any varint count before sending a
     * single element, and {@code new ArrayList<>(claimed)} allocates up front — so never
     * size a collection from the wire's claim alone. Oversized claims still fail when
     * the element reads exhaust the buffer; this only bounds the allocation.
     */
    public static int safeCapacity(int claimed) {
        return Mth.clamp(claimed, 0, 1024);
    }
}
