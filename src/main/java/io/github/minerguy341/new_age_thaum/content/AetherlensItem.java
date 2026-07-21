package io.github.minerguy341.new_age_thaum.content;

import io.github.minerguy341.new_age_thaum.NewAgeThaum;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectBag;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectNames;
import io.github.minerguy341.new_age_thaum.core.aspect.AspectResolver;
import io.github.minerguy341.new_age_thaum.core.aura.AuraField;
import io.github.minerguy341.new_age_thaum.core.player.PlayerProgressService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
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

    // Raise-to-look-through: right-clicking AIR holds the lens up to the eye using the
    // vanilla spyglass use-pose (which centres the model on the camera), so you literally
    // peer through the loupe. Right-clicking a block/entity still routes to useOn/
    // interactLivingEntity below, so scanning and the raise never collide.
    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.UseAnim.SPYGLASS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 1200; // effectively "held until released"
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player,
            InteractionHand hand) {
        player.startUsingItem(hand);
        return net.minecraft.world.InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // An aura node reports its stats instead of a generic scan — the Aetherlens is
        // how you read a node's aspect, temperament, and the aura it sits in.
        if (level.getBlockEntity(context.getClickedPos()) instanceof AuraNodeBlockEntity node) {
            return reportNode(serverPlayer, context.getClickedPos(), node);
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

    /** Reads an aura node's identity and the aura of the chunk it feeds, to the player. */
    private InteractionResult reportNode(ServerPlayer player, BlockPos pos, AuraNodeBlockEntity node) {
        if (node.aspect() == null || node.personality() == null) {
            player.displayClientMessage(
                    Component.translatable("message.new_age_thaum.node.dormant").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        ServerLevel level = player.serverLevel();
        AuraField aura = AuraField.get(level);
        long chunk = new ChunkPos(pos).toLong();
        player.displayClientMessage(Component.translatable("message.new_age_thaum.node.header")
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(" "))
                .append(AspectNames.colored(node.aspect()))
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable(node.personality().translationKey()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable("message.new_age_thaum.node.strength",
                        String.format("%.1f", node.size())).withStyle(ChatFormatting.GRAY)), false);
        player.displayClientMessage(Component.translatable("message.new_age_thaum.node.field",
                Math.round(aura.vis(chunk)), (int) AuraField.CHUNK_CAP, Math.round(aura.flux(chunk)))
                .withStyle(ChatFormatting.DARK_AQUA), false);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.4f);
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
        return NewAgeThaum.id(path);
    }
}
