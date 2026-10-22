package com.draxer.dexrider.rider;

import com.draxer.dexrider.config.DexConfig;
import com.draxer.dexrider.entity.KaijinEntity;
import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.registry.DexEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Dai-Shocker se interesa por un Rider que escanea y se adapta. De noche, transformado y con al menos tres
 * Kamen Ride en el mazo, a veces te tienden una emboscada: combatientes con un Kaijin, y si ya llevas seis
 * Kamen Ride o mas, a veces la Quimera en persona. Se puede apagar en la configuracion (dex.daiShockerAmbush).
 */
public final class DaiShocker {
    private DaiShocker() {}

    /** Cada 100 ticks por jugador. */
    public static void tickPlayer(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level) || !DexConfig.ambush()) return;
        if (level.dimension() != Level.OVERWORLD || level.isBrightOutside() || !Transformation.isTransformed(p)) return;
        int kr = DexDriverItem.getDeck(DexDriverItem.findDriver(p)).count(DexCard.Family.KAMEN_RIDE);
        if (kr < 3 || p.getRandom().nextInt(300) != 0) return;
        if (!level.getEntitiesOfClass(KaijinEntity.class, p.getBoundingBox().inflate(48)).isEmpty()) return;
        ambush(level, p, kr >= 6 && p.getRandom().nextInt(4) == 0);
    }

    public static void ambush(ServerLevel level, ServerPlayer p, boolean chimera) {
        double a = p.getRandom().nextDouble() * Math.PI * 2;
        int x = (int) (p.getX() + Math.cos(a) * 18), z = (int) (p.getZ() + Math.sin(a) * 18);
        BlockPos base = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        spawn(level, p, chimera ? KaijinEntity.Kind.CHIMERA : (p.getRandom().nextBoolean() ? KaijinEntity.Kind.MANTIS : KaijinEntity.Kind.RHINO), base);
        for (int i = 0; i < 3; i++) spawn(level, p, KaijinEntity.Kind.SOLDIER, base.offset(p.getRandom().nextInt(5) - 2, 0, p.getRandom().nextInt(5) - 2));
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.6F, 1.4F);
        p.sendSystemMessage(Component.translatable(chimera ? "message.dexrider.ambush_chimera" : "message.dexrider.ambush"), true);
    }

    private static void spawn(ServerLevel level, ServerPlayer p, KaijinEntity.Kind kind, BlockPos at) {
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
        KaijinEntity e = DexEntities.KAIJIN.get(kind).get().create(level, EntitySpawnReason.EVENT);
        if (e == null) return;
        e.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        e.setTarget(p);
        level.addFreshEntity(e);
        FX.particle(level, ParticleTypes.LARGE_SMOKE, e.position().add(0, 1, 0), 20, 0.5, 0.02);
    }
}
