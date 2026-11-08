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
    public static final ModConfigSpec.BooleanValue TARGET_READ;
    public static final ModConfigSpec.DoubleValue VOICE_VOLUME;
    public static final ModConfigSpec.DoubleValue ALLY_CHATTER;
    public static final ModConfigSpec.BooleanValue ALLY_REACTIONS;
    public static final ModConfigSpec.BooleanValue SHOW_ZONES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("riders");
        ECHO_SPAWN_CHANCE = b.comment("Probabilidad (0-1) de que un intento de aparicion de un Rider perdido siga adelante. 1 = normal, 0 = nunca aparecen solos.")
                .translation("dexrider.configuration.echoSpawnChance")
                .defineInRange("echoSpawnChance", 1.0, 0.0, 1.0);
        NPC_DAMAGE = b.comment("Multiplicador del dano de los Riders del juego (perdidos y amigos).")
                .translation("dexrider.configuration.npcDamage")
                .defineInRange("npcDamage", 1.0, 0.1, 5.0);
        BLOCK_BREAKING = b.comment("Si los finales fallados (Nine en Rising) rompen bloques. Tambien respeta la regla mobGriefing.")
                .translation("dexrider.configuration.blockBreaking")
                .define("blockBreaking", true);
        AMBUSH = b.comment("Emboscadas nocturnas de Dai-Shocker cuando llevas tres Kamen Ride o mas (y la Quimera con seis o mas).")
                .translation("dexrider.configuration.daiShockerAmbush")
                .define("daiShockerAmbush", true);
        b.pop();
        b.push("dex");
        DEX_DAMAGE = b.comment("Multiplicador del dano del jugador transformado.")
                .translation("dexrider.configuration.dexDamage")
                .defineInRange("dexDamage", 1.0, 0.1, 5.0);
        EMOTIONS = b.comment("Activa Violent Emotion y Heroic Emotion.")
                .translation("dexrider.configuration.emotions")
                .define("emotions", true);
        HUD = b.comment("Muestra el HUD del Rider (carta elegida, recargas, Accel, Emotion).")
                .translation("dexrider.configuration.hud")
                .define("hud", true);
        TARGET_READ = b.comment("Lectura del Driver: tras intercambiar golpes, muestra la vida del objetivo, tu golpe, su golpe y los golpes que faltan.")
                .translation("dexrider.configuration.targetRead")
                .define("targetRead", true);
        VOICE_VOLUME = b.comment("Volumen de las voces del Driver (Kamen Ride, Attack Ride...). 0 = sin voces.")
                .translation("dexrider.configuration.voiceVolume")
                .defineInRange("voiceVolume", 1.0, 0.0, 1.0);
        b.pop();
        b.push("allies");
        ALLY_CHATTER = b.comment("Cuanto hablan los amigos y los Riders por su cuenta (charlas entre ellos, comentarios, reacciones). 0 = nada, 1 = normal.")
                .translation("dexrider.configuration.allyChatter")
                .defineInRange("allyChatter", 1.0, 0.0, 1.0);
        ALLY_REACTIONS = b.comment("Si los amigos comentan lo que haces (picar, talar, construir...).")
                .translation("dexrider.configuration.allyReactions")
                .define("allyReactions", true);
        SHOW_ZONES = b.comment("Con un pico en la mano, mostrar el borde de las zonas de trabajo de tus amigos.")
                .translation("dexrider.configuration.showZones")
                .define("showZones", true);
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
    public static boolean targetRead() { return bool(TARGET_READ, true); }
    public static float voiceVolume() { return (float) d(VOICE_VOLUME, 1.0); }
    public static double allyChatter() { return d(ALLY_CHATTER, 1.0); }
    public static boolean allyReactions() { return bool(ALLY_REACTIONS, true); }
    public static boolean showZones() { return bool(SHOW_ZONES, true); }
}
