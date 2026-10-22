package com.draxer.dexrider.registry;

import com.draxer.dexrider.DexRider;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sonidos del mod. Los archivos estan en assets/dexrider/sounds/*.ogg y se declaran en sounds.json.
 *
 * Efectos: originales, sintetizados para el mod.
 * Voces (voice_*.ogg): vienen en silencio; para darles voz basta con reemplazar el .ogg
 * por una grabacion (mono, mismo nombre de archivo).
 */
public final class DexSounds {
    private DexSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, DexRider.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> CARD_INSERT = sound("card_insert");
    public static final DeferredHolder<SoundEvent, SoundEvent> HENSHIN = sound("henshin");
    public static final DeferredHolder<SoundEvent, SoundEvent> DETRANSFORM = sound("detransform");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMED_RIDE = sound("armed_ride");
    public static final DeferredHolder<SoundEvent, SoundEvent> FAR_CHARGE = sound("far_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> FAR_IMPACT = sound("far_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCAN_SAMPLE = sound("scan_sample");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCAN_COMPLETE = sound("scan_complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOT = sound("shot");
    public static final DeferredHolder<SoundEvent, SoundEvent> MODE_SWITCH = sound("mode_switch");

    public static final DeferredHolder<SoundEvent, SoundEvent> VOICE_KAMEN_RIDE = sound("voice_kamen_ride");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOICE_ARMED_RIDE = sound("voice_armed_ride");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOICE_ATTACK_RIDE = sound("voice_attack_ride");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOICE_FINAL_ATTACK_RIDE = sound("voice_final_attack_ride");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(DexRider.id(name)));
    }

    /** Reproduce un sonido del mod en la posicion de una entidad (lo oyen todos los cercanos). */
    public static void play(ServerLevel level, Entity at, DeferredHolder<SoundEvent, SoundEvent> sound, float volume, float pitch) {
        level.playSound(null, at.getX(), at.getY(), at.getZ(), sound.get(), SoundSource.PLAYERS, volume, pitch);
    }
}
