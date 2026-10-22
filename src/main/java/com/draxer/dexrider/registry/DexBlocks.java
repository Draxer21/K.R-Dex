package com.draxer.dexrider.registry;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.block.SignalCoreBlock;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Bloques del mod. */
public final class DexBlocks {
    private DexBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DexRider.MODID);

    /** Nucleo de la Senal (fondo de la base del Arquitecto). Irrompible. */
    public static final DeferredBlock<SignalCoreBlock> SIGNAL_CORE = BLOCKS.registerBlock("signal_core",
            props -> new SignalCoreBlock(props.strength(-1.0F, 3600000.0F).sound(SoundType.METAL).noLootTable()
                    .lightLevel(s -> s.getValue(SignalCoreBlock.SPENT) ? 0 : 9)));
}
