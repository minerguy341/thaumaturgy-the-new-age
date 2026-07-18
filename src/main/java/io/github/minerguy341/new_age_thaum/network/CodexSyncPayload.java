package io.github.minerguy341.new_age_thaum.network;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.codex.CodexEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/** Full Codex-entry sync, sent on player join and after datapack reloads. */
public record CodexSyncPayload(List<CodexEntry> entries) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CodexSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(NewAgeThaum.MOD_ID, "codex_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CodexSyncPayload> STREAM_CODEC =
            StreamCodec.of(CodexSyncPayload::write, CodexSyncPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, CodexSyncPayload payload) {
        buf.writeVarInt(payload.entries.size());
        for (CodexEntry entry : payload.entries) {
            buf.writeResourceLocation(entry.id());
            buf.writeUtf(entry.category());
            buf.writeUtf(entry.titleKey());
            buf.writeResourceLocation(BuiltInRegistries.ITEM.getKey(entry.icon()));
            buf.writeVarInt(entry.x());
            buf.writeVarInt(entry.y());
        }
    }

    private static CodexSyncPayload read(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<CodexEntry> entries = new ArrayList<>(NetworkLimits.safeCapacity(count));
        for (int i = 0; i < count; i++) {
            ResourceLocation id = buf.readResourceLocation();
            String category = buf.readUtf();
            String titleKey = buf.readUtf();
            Item icon = BuiltInRegistries.ITEM.get(buf.readResourceLocation());
            int x = buf.readVarInt();
            int y = buf.readVarInt();
            entries.add(new CodexEntry(id, category, titleKey, icon, x, y));
        }
        return new CodexSyncPayload(entries);
    }

    @Override
    public CustomPacketPayload.Type<CodexSyncPayload> type() {
        return TYPE;
    }
}
