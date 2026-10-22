package com.draxer.dexrider.block;

import com.draxer.dexrider.entity.ArchitectEntity;
import com.draxer.dexrider.entity.KaijinEntity;
import com.draxer.dexrider.registry.DexEntities;
import com.draxer.dexrider.rider.FX;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Nucleo de la Senal: el emisor en el fondo de la base del Arquitecto. Clic derecho lo despierta (si no esta ya
 * despierto cerca). Cuando el Arquitecto cae, el Nucleo se apaga para siempre (SPENT). No se puede romper.
 */
public class SignalCoreBlock extends Block {
    public static final BooleanProperty SPENT = BooleanProperty.create("spent");

    public SignalCoreBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(SPENT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SPENT);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (state.getValue(SPENT)) {
            player.sendSystemMessage(Component.translatable("message.dexrider.core_spent").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        if (!server.getEntitiesOfClass(ArchitectEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(48)).isEmpty()) {
            return InteractionResult.SUCCESS;
        }
        awaken(server, pos, player);
        return InteractionResult.SUCCESS;
    }

    /** Despierta al Arquitecto entre el Nucleo y quien lo toco. */
    public static ArchitectEntity awaken(ServerLevel level, BlockPos pos, Player player) {
        KaijinEntity e = DexEntities.KAIJIN.get(KaijinEntity.Kind.ARCHITECT).get().create(level, EntitySpawnReason.TRIGGERED);
        if (!(e instanceof ArchitectEntity boss)) return null;
        Vec3 c = Vec3.atBottomCenterOf(pos);
        Vec3 to = player.position().subtract(c).multiply(1, 0, 1);
        Vec3 at = to.lengthSqr() > 1 ? c.add(to.normalize().scale(Math.min(5.0, to.length() * 0.5))) : c.add(0, 1, 0);
        boss.setPos(at.x, pos.getY() + 1, at.z);
        boss.setCore(pos);
        boss.setTarget(player);
        boss.setPersistenceRequired();
        level.addFreshEntity(boss);
        FX.particle(level, ParticleTypes.LARGE_SMOKE, boss.position().add(0, 1.2, 0), 40, 0.7, 0.03);
        FX.dust(level, 0xD4202C, 2.0F, boss.position().add(0, 1.2, 0), 30, 0.8);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5F, 0.5F);
        level.playSound(null, pos, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.2F, 1.3F);
        return boss;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(SPENT) || random.nextInt(3) != 0) return;
        level.addParticle(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                (random.nextDouble() - 0.5) * 0.6, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.6);
    }
}
