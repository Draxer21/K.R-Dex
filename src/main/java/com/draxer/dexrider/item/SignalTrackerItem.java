package com.draxer.dexrider.item;

import com.draxer.dexrider.DexRider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Optional;

/**
 * Rastreador de la Senal: un receptor que los investigadores de Gunma dejaron sintonizado. Clic derecho busca la
 * base del Arquitecto mas cercana; la aguja la senala (como una brujula) y te dice a que distancia esta.
 */
public class SignalTrackerItem extends Item {
    public static final TagKey<Structure> TARGET = TagKey.create(Registries.STRUCTURE, DexRider.id("signal_base"));

    public SignalTrackerItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        player.getCooldowns().addCooldown(stack, 60);
        if (server.dimension() != Level.OVERWORLD) {
            player.sendSystemMessage(Component.translatable("message.dexrider.tracker_static").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }
        BlockPos found = server.findNearestMapStructure(TARGET, player.blockPosition(), 100, false);
        if (found == null) {
            player.sendSystemMessage(Component.translatable("message.dexrider.tracker_static").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(server.dimension(), found)), false));
        int dx = found.getX() - player.getBlockX(), dz = found.getZ() - player.getBlockZ();
        int dist = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
        String dir = Math.abs(dx) > Math.abs(dz) * 2 ? (dx > 0 ? "east" : "west")
                : Math.abs(dz) > Math.abs(dx) * 2 ? (dz > 0 ? "south" : "north")
                : (dz > 0 ? "south" : "north") + (dx > 0 ? "east" : "west");
        player.sendSystemMessage(dist < 40
                ? Component.translatable("message.dexrider.tracker_here").withStyle(ChatFormatting.RED)
                : Component.translatable("message.dexrider.tracker_found", dist, Component.translatable("direction.dexrider." + dir))
                        .withStyle(ChatFormatting.DARK_RED), true);
        server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
        return InteractionResult.SUCCESS;
    }
}
