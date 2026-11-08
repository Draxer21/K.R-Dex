package com.draxer.dexrider.rider;

import com.draxer.dexrider.registry.DexSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** La voz del Driver: frases encadenadas ("Kamen Ride... Dex"). */
public final class Voices {
    private Voices() {}

    /** Ticks que dura "Kamen Ride" antes de decir el nombre. */
    private static final int AFTER_KAMEN_RIDE = 24;

    public static void say(ServerLevel level, ServerPlayer p, DeferredHolder<SoundEvent, SoundEvent> voice) {
        float vol = com.draxer.dexrider.config.DexConfig.voiceVolume();
        if (vol <= 0.0F) return;
        DexSounds.play(level, p, voice, vol, 1.0F);
    }

    public static void sayLater(ServerPlayer p, int ticks, DeferredHolder<SoundEvent, SoundEvent> voice) {
        Scheduler.soft(p, ticks, () -> { if (p.level() instanceof ServerLevel l) say(l, p, voice); });
    }

    /** "Kamen Ride" y el nombre del Rider (Complete Form: "Complete"). */
    public static void kamenRide(ServerLevel level, ServerPlayer p, KamenRide rider, int delay) {
        DeferredHolder<SoundEvent, SoundEvent> name = rider == KamenRide.COMPLETE ? DexSounds.VOICE_COMPLETE
                : DexSounds.VOICE_RIDERS.get(rider.id());
        if (delay <= 0) say(level, p, DexSounds.VOICE_KAMEN_RIDE);
        else sayLater(p, delay, DexSounds.VOICE_KAMEN_RIDE);
        if (name != null) sayLater(p, delay + AFTER_KAMEN_RIDE, name);
    }
}
