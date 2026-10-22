package com.draxer.dexrider.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Archivo de configuracion (config/dexrider-common.toml). Se puede editar con el juego cerrado
 * o desde la lista de mods.
 */
public final class DexConfig {
    private DexConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue ECHO_SPAWN_CHANCE;
    public static final ModConfigSpec.DoubleValue NPC_DAMAGE;
    public static final ModConfigSpec.DoubleValue DEX_DAMAGE;
    public static final ModConfigSpec.BooleanValue BLOCK_BREAKING;
    public static final ModConfigSpec.BooleanValue EMOTIONS;
    public static final ModConfigSpec.BooleanValue HUD;
    public static final ModConfigSpec.BooleanValue AMBUSH;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("riders");
        ECHO_SPAWN_CHANCE = b.comment("Probabilidad (0-1) de que un intento de aparicion de un Rider perdido siga adelante. 1 = normal, 0 = nunca aparecen solos.")
                .defineInRange("echoSpawnChance", 1.0, 0.0, 1.0);
        NPC_DAMAGE = b.comment("Multiplicador del dano de los Riders del juego (perdidos y amigos).")
                .defineInRange("npcDamage", 1.0, 0.1, 5.0);
        BLOCK_BREAKING = b.comment("Si los finales fallados (Nine en Rising) rompen bloques. Tambien respeta la regla mobGriefing.")
                .define("blockBreaking", true);
        AMBUSH = b.comment("Emboscadas nocturnas de Dai-Shocker cuando llevas tres Kamen Ride o mas (y la Quimera con seis o mas).")
                .define("daiShockerAmbush", true);
        b.pop();
        b.push("dex");
        DEX_DAMAGE = b.comment("Multiplicador del dano del jugador transformado.")
                .defineInRange("dexDamage", 1.0, 0.1, 5.0);
        EMOTIONS = b.comment("Activa Violent Emotion y Heroic Emotion.")
                .define("emotions", true);
        HUD = b.comment("Muestra el HUD del Rider (carta elegida, recargas, Accel, Emotion).")
                .define("hud", true);
        b.pop();
        SPEC = b.build();
    }

    private static double d(ModConfigSpec.DoubleValue v, double fallback) {
        try { return v.get(); } catch (RuntimeException e) { return fallback; }
    }

    private static boolean bool(ModConfigSpec.BooleanValue v, boolean fallback) {
        try { return v.get(); } catch (RuntimeException e) { return fallback; }
    }

    public static double echoSpawnChance() { return d(ECHO_SPAWN_CHANCE, 1.0); }
    public static float npcDamage() { return (float) d(NPC_DAMAGE, 1.0); }
    public static float dexDamage() { return (float) d(DEX_DAMAGE, 1.0); }
    public static boolean blockBreaking() { return bool(BLOCK_BREAKING, true); }
    public static boolean emotions() { return bool(EMOTIONS, true); }
    public static boolean hud() { return bool(HUD, true); }
    public static boolean ambush() { return bool(AMBUSH, true); }
}
