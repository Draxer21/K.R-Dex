package com.draxer.dexrider.item;

import com.draxer.dexrider.entity.KaijinBossEntity;
import com.draxer.dexrider.entity.KaijinEntity;
import com.draxer.dexrider.registry.DexEntities;
import com.draxer.dexrider.rider.FX;
import com.draxer.dexrider.rider.TempBlocks;
import com.draxer.dexrider.rider.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Sello de desafio de Dai-Shocker: clic derecho en el suelo (transformado) levanta una arena circular de 12 bloques de
 * radio con muros temporales y llama a un jefe. Primero viene el jefe cuya carta rara aun no tienes
 * (Mantis Reina, Rinoceronte Titan, Comandante); con las tres, uno al azar. La arena desaparece cuando cae el jefe.
 */
public class DaiShockerSealItem extends Item {
    public static final int RADIUS = 12, HEIGHT = 4, ARENA_TICKS = 20 * 60 * 20;
    private static final KaijinEntity.Kind[] ORDER = {KaijinEntity.Kind.MANTIS_QUEEN, KaijinEntity.Kind.RHINO_TITAN, KaijinEntity.Kind.COMMANDER};

    public DaiShockerSealItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
        if (!Transformation.isTransformed(p)) {
            p.sendSystemMessage(Component.translatable("message.dexrider.seal_transform"), true);
            return InteractionResult.FAIL;
        }
        if (!level.getEntitiesOfClass(KaijinBossEntity.class, p.getBoundingBox().inflate(48)).isEmpty()) {
            p.sendSystemMessage(Component.translatable("message.dexrider.seal_busy"), true);
            return InteractionResult.FAIL;
        }
        KaijinBossEntity boss = challenge(level, p, ctx.getClickedPos().above(), next(p));
        if (boss == null) return InteractionResult.FAIL;
        if (!p.isCreative()) ctx.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }

    /** El jefe que toca: el primero cuya carta rara falta en tu mazo. */
    public static KaijinEntity.Kind next(ServerPlayer p) {
        ItemStack driver = DexDriverItem.findDriver(p);
        if (driver.isEmpty()) return ORDER[0];
        DriverDeck deck = DexDriverItem.getDeck(driver);
        for (KaijinEntity.Kind k : ORDER) {
            if (!deck.has(KaijinBossEntity.rareCard(k))) return k;
        }
        return ORDER[p.getRandom().nextInt(ORDER.length)];
    }

    /** Levanta la arena alrededor de {@code center} y llama al jefe. */
    @Nullable
    public static KaijinBossEntity challenge(ServerLevel level, ServerPlayer p, BlockPos center, KaijinEntity.Kind kind) {
        KaijinEntity e = DexEntities.KAIJIN.get(kind).get().create(level, EntitySpawnReason.EVENT);
        if (!(e instanceof KaijinBossEntity boss)) return null;
        BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        // en 26.2 cambian los nombres de los bloques de color: se busca por id y, si no esta, vidrio normal
        BlockState glass = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getOptional(net.minecraft.resources.Identifier.withDefaultNamespace("red_stained_glass"))
                .map(net.minecraft.world.level.block.Block::defaultBlockState).orElse(Blocks.GLASS.defaultBlockState());
        for (int a = 0; a < 360; a += 3) {
            double rad = Math.toRadians(a);
            int x = (int) Math.round(Math.cos(rad) * RADIUS), z = (int) Math.round(Math.sin(rad) * RADIUS);
            for (int y = 0; y < HEIGHT; y++) {
                BlockPos bp = center.offset(x, y, z);
                if (boss.arena.contains(bp)) continue;
                if (TempBlocks.place(level, bp, y == HEIGHT - 1 ? glass : wall, ARENA_TICKS)) boss.arena.add(bp.immutable());
            }
        }
        // el jefe aparece al otro lado de la arena, frente al jugador
        Vec3 dir = Vec3.atBottomCenterOf(center).subtract(p.position()).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1e-3 ? p.getLookAngle().multiply(1, 0, 1).normalize() : dir.normalize();
        Vec3 spawn = Vec3.atBottomCenterOf(center).add(dir.scale(RADIUS * 0.5));
        boss.setPos(spawn.x, center.getY(), spawn.z);
        boss.setTarget(p);
        boss.setPersistenceRequired();
        level.addFreshEntity(boss);
        FX.particle(level, ParticleTypes.LARGE_SMOKE, spawn.add(0, 1, 0), 40, 1.0, 0.05);
        FX.particle(level, ParticleTypes.EXPLOSION, spawn.add(0, 1, 0), 3, 0.8, 0);
        level.playSound(null, spawn.x, spawn.y, spawn.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 1.3F);
        Component name = Component.translatable("entity.dexrider." + kind.id);
        for (ServerPlayer q : level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(40))) {
            q.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
            q.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("boss.dexrider.arena_title").withStyle(ChatFormatting.RED)));
            q.connection.send(new ClientboundSetSubtitleTextPacket(name));
            q.sendSystemMessage(Component.translatable("boss.dexrider." + kind.id + ".intro").withStyle(ChatFormatting.DARK_RED), false);
        }
        return boss;
    }
}
