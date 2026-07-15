package io.github.minerguy341.new_age_thaum.content;

import io.github.minerguy341.new_age_thaum.core.aspect.AspectBag;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectNames;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectResolver;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * The Aetherlens: the primary early-game scanning verb (PLAN §4.1). Right-clicking a
 * block or entity grants its aspects as observation points the first time that object
 * kind is scanned, with actionbar feedback and a chime. All logic is server-side; the
 * client learns the result through the synced {@code PlayerProgress}.
 */
public class AetherlensItem extends Item {
    public AetherlensItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockState state = level.getBlockState(context.getClickedPos());
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        AspectBag aspects = AspectResolver.resolve(state.getBlock().asItem(), level);
        return scan(serverPlayer, level, context.getClickedPos(), "block/" + blockId, aspects);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity,
            InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return scan(serverPlayer, entity.level(), entity.blockPosition(), "entity/" + typeId, entityAspects(entity));
    }

    /** Placeholder entity aspects until an entity-assignment format lands in a later milestone. */
    private static AspectBag entityAspects(LivingEntity entity) {
        ResourceLocation vita = aspect("vita");
        ResourceLocation fera = aspect("fera");
        if (entity instanceof Enemy) {
            return new AspectBag(Map.of(vita, 1, aspect("discordia"), 1, aspect("caro"), 1));
        }
        return new AspectBag(Map.of(vita, 2, fera, 1));
    }

    private InteractionResult scan(ServerPlayer player, Level level, BlockPos pos, String key, AspectBag aspects) {
        if (aspects.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.new_age_thaum.scan.none").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        boolean granted = PlayerProgressService.scan(player, key, aspects);
        if (!granted) {
            player.displayClientMessage(
                    Component.translatable("message.new_age_thaum.scan.known").withStyle(ChatFormatting.DARK_GRAY), true);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(
                Component.translatable("message.new_age_thaum.scan.discovered", aspectList(aspects)), true);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.2f);
        return InteractionResult.CONSUME;
    }

    private static MutableComponent aspectList(AspectBag aspects) {
        MutableComponent list = Component.empty();
        boolean first = true;
        for (Map.Entry<ResourceLocation, Integer> entry : aspects.ordered()) {
            if (!first) {
                list.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            list.append(AspectNames.colored(entry.getKey()))
                    .append(Component.literal(" ×" + entry.getValue()).withStyle(ChatFormatting.DARK_GRAY));
            first = false;
        }
        return list;
    }

    private static ResourceLocation aspect(String path) {
        return ResourceLocation.fromNamespaceAndPath("new_age_thaum", path);
    }
}
