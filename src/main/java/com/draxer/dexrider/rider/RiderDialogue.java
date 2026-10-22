package com.draxer.dexrider.rider;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Los Riders hablan por el chat (para ellos es hablar en voz alta): se oye a 40 bloques.
 *
 * Historia del mod: los Riders del mundo de Yusuke llegaron aqui por accidente. Todos siguen la senal del
 * Driver de Dex, pero al principio no saben si el Dex de este mundo es el de verdad, asi que pelean para comprobarlo.
 * Sus trajes les avisan que no aguantarian la atmosfera de este mundo, asi que no pueden destransformarse;
 * los OC de la familia Kagami si (su Rider System se lo permite). Si los reunes a todos, abren un portal a casa.
 *
 * Frases: lang "dialog.dexrider.<rider>.<situacion>.<n>", n = 1..VARIANTS.
 */
public final class RiderDialogue {
    private RiderDialogue() {}

    public static final int VARIANTS = 2;
    private static final double HEAR_RANGE = 40;

    public enum Line {
        LOST, SPOT_DEX, SPOT_PLAYER, HURT, PANIC, CONVINCED, RETREAT, SUIT,
        SUMMONED, HOME, DOWN, CHAT, REPLY, REUNION, PORTAL, FAREWELL, RETURN, HENSHIN, RELAX, FORM_CHANGE, RISING, ACCEL, ACCEL_END, BLASTER, GIFT;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final Map<UUID, Long> LAST_LINE = new HashMap<>();

    /** Dice una frase si el que habla no ha hablado hace poco (minGap ticks). Devuelve true si hablo. */
    public static boolean say(ServerLevel level, Entity speaker, KamenRide rider, Line line, int minGap, Object... args) {
        long now = level.getGameTime();
        Long last = LAST_LINE.get(speaker.getUUID());
        if (last != null && now - last < minGap) return false;
        LAST_LINE.put(speaker.getUUID(), now);
        int n = 1 + level.getRandom().nextInt(VARIANTS);
        Component text = Component.translatable("dialog.dexrider." + rider.id() + "." + line.key() + "." + n, args);
        broadcast(level, speaker, rider, text);
        return true;
    }

    /** Siempre habla (momentos de historia). */
    public static void force(ServerLevel level, Entity speaker, KamenRide rider, Line line, Object... args) {
        LAST_LINE.remove(speaker.getUUID());
        say(level, speaker, rider, line, 0, args);
    }

    private static void broadcast(ServerLevel level, Entity speaker, KamenRide rider, Component text) {
        MutableComponent msg = Component.literal("<")
                .append(rider.displayName().copy().withStyle(Style.EMPTY.withColor(nameColor(rider))))
                .append(Component.literal("> "))
                .append(text);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(speaker) <= HEAR_RANGE * HEAR_RANGE) p.sendSystemMessage(msg);
        }
    }

    /** Colores legibles en el chat (el azul de Cipher y el blanco de Mirage se aclaran/oscurecen). */
    private static int nameColor(KamenRide rider) {
        return switch (rider) {
            case CIPHER -> 0x6C8CE8;
            case MIRAGE -> 0xB4C8FF;
            case SPORE -> 0xC8A8F0;
            case FORGE -> 0xE04858;
            case KAIXA -> 0x7A9CFF;
            default -> rider.color;
        };
    }

    // ------------------------------------------------------------------ nombres y conversaciones entre conocidos

    /** Como llama <speaker> a <target>: "Yaoyorozu-san", "Midoriya-kun", "Takumi-nii", "Padre"... */
    public static Component address(KamenRide speaker, KamenRide target) {
        if (speaker == target) return target.displayName();
        return Component.translatable("addr.dexrider." + speaker.id() + "." + target.id());
    }

    /** Parejas que se conocen bien y tienen conversaciones propias ("a.b" = a empieza, b responde) y cuantas. */
    private static final Map<String, Integer> PAIRS = Map.ofEntries(
            Map.entry("nine.forge", 2), Map.entry("forge.mirage", 2), Map.entry("mirage.nine", 2),
            Map.entry("proto.forge", 2), Map.entry("proto.nine", 2), Map.entry("spore.mirage", 2),
            Map.entry("spore.nine", 2), Map.entry("forge.cipher", 2), Map.entry("proto.cipher", 2),
            Map.entry("proto.warden", 1), Map.entry("nine.warden", 1), Map.entry("mirage.psyga", 1),
            Map.entry("psyga.spore", 1), Map.entry("cipher.warden", 2), Map.entry("warden.psyga", 2),
            Map.entry("psyga.cipher", 2), Map.entry("orga.cipher", 2), Map.entry("orga.warden", 2),
            Map.entry("orga.psyga", 2));

    /** Conversacion entre dos que se conocen: clave base (sin ".r") o null si no tienen una propia. */
    @org.jetbrains.annotations.Nullable
    public static String pairLine(net.minecraft.util.RandomSource random, KamenRide a, KamenRide b) {
        Integer n = PAIRS.get(a.id() + "." + b.id());
        if (n == null) return null;
        return "dialog.dexrider.pair." + a.id() + "." + b.id() + "." + (1 + random.nextInt(n));
    }

    /** El que empieza la conversacion entre estos dos (si tienen una), o null. */
    @org.jetbrains.annotations.Nullable
    public static KamenRide pairStarter(KamenRide a, KamenRide b) {
        if (PAIRS.containsKey(a.id() + "." + b.id())) return a;
        if (PAIRS.containsKey(b.id() + "." + a.id())) return b;
        return null;
    }

    /** Dice una frase por clave (conversaciones de pareja). */
    public static boolean sayKey(ServerLevel level, Entity speaker, KamenRide rider, String key, int minGap) {
        long now = level.getGameTime();
        Long last = LAST_LINE.get(speaker.getUUID());
        if (last != null && now - last < minGap) return false;
        LAST_LINE.put(speaker.getUUID(), now);
        broadcast(level, speaker, rider, Component.translatable(key));
        return true;
    }

    public static void forget(Entity speaker) {
        LAST_LINE.remove(speaker.getUUID());
    }
}
