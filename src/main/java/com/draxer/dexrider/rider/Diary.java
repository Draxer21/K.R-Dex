package com.draxer.dexrider.rider;

import com.draxer.dexrider.entity.KaijinEntity;
import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.registry.DexComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * El diario de Yusuke (0.55): el Driver anota lo que haces y el diario lo cuenta con su voz. Las entradas viven en el
 * Driver (componente "diary", texto "dia|clave|arg|arg"); un arg que empieza por # es una clave de traduccion.
 * El diario (DiaryItem) las lee en el cliente y las pagina.
 */
public final class Diary {
    private Diary() {}

    public static final int MAX = 300;

    /** Logros del mod que dejan una entrada (diary.dexrider.adv.<nombre>). */
    public static final Set<String> ADV = Set.of("henshin", "ride_booker", "first_scan", "form_ride", "attack_ride", "max_level",
            "final_attack", "ally", "kamen_ride", "accel", "ktouch", "complete_form", "violent_emotion", "heroic_emotion",
            "daishocker", "chimera", "mountain_house", "commission_depot", "scarab", "upgrade_form", "kaixa", "gunma_lab",
            "complete_form_2", "signal_base", "architect", "said_no", "parry", "combo", "daishocker_boss",
            "ua_nine", "ua_forge", "ua_spore", "ua_mirage", "ua_proto");

    public static int day(ServerPlayer p) {
        return (int) (p.level().getGameTime() / 24000L) + 1;
    }

    public static List<String> entries(ItemStack driver) {
        List<String> l = driver.get(DexComponents.DIARY.get());
        return l == null ? List.of() : l;
    }

    /** Clave de una entrada ("12|diary.dexrider.x|..." -> "diary.dexrider.x"). */
    public static String keyOf(String entry) {
        String[] parts = entry.split("\\|", -1);
        return parts.length > 1 ? parts[1] : "";
    }

    /** Firma de una entrada sin el dia ("clave|arg|arg"). */
    static String signature(String key, String... args) {
        StringBuilder sb = new StringBuilder(key);
        for (String a : args) sb.append('|').append(a.replace('|', '/'));
        return sb.toString();
    }

    public static boolean has(ItemStack driver, String signature) {
        for (String e : entries(driver)) {
            int i = e.indexOf('|');
            if (i >= 0 && e.substring(i + 1).equals(signature)) return true;
        }
        return false;
    }

    /** Anota una entrada siempre (si el jugador lleva el Driver). */
    public static boolean write(ServerPlayer p, String key, String... args) {
        ItemStack driver = DexDriverItem.findDriver(p);
        if (driver.isEmpty()) return false;
        List<String> l = new ArrayList<>(entries(driver));
        if (l.isEmpty()) l.add(day(p) + "|diary.dexrider.start");
        l.add(day(p) + "|" + signature(key, args));
        while (l.size() > MAX) l.remove(1);   // la primera entrada (el comienzo) se queda
        driver.set(DexComponents.DIARY.get(), l);
        if (com.draxer.dexrider.config.DexConfig.diaryNotice()) {
            p.sendSystemMessage(Component.translatable("message.dexrider.diary_new").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC), true);
        }
        return true;
    }

    /** Anota una entrada solo la primera vez (misma clave y mismos argumentos). */
    public static boolean once(ServerPlayer p, String key, String... args) {
        ItemStack driver = DexDriverItem.findDriver(p);
        if (driver.isEmpty() || has(driver, signature(key, args))) return false;
        return write(p, key, args);
    }

    // ---------------------------------------------------------------- ganchos

    public static void onAdvancement(ServerPlayer p, String name) {
        if (ADV.contains(name)) once(p, "diary.dexrider.adv." + name);
    }

    public static void onCard(ServerPlayer p, DexCard card, boolean scanned) {
        String name = "#" + card.translationKey();
        if (card.family() == DexCard.Family.KAMEN_RIDE) {
            once(p, "diary.dexrider.kamen_ride." + variant(card.id(), 3), name);
        } else if (scanned) {
            once(p, "diary.dexrider.scan." + variant(card.id(), 4), name);
        }
    }

    public static void onFriend(ServerPlayer p, KamenRide r) {
        once(p, "diary.dexrider.friend." + r.id());
    }

    public static void onTrust(ServerPlayer p, KamenRide r, int level) {
        if (level >= 1) once(p, "diary.dexrider.trust." + Math.min(level, 3), "#diary.dexrider.who." + r.id());
    }

    public static void onKill(ServerPlayer p, KaijinEntity.Kind kind) {
        switch (kind) {
            case SOLDIER, MANTIS, RHINO, MANTIS_QUEEN, RHINO_TITAN, COMMANDER -> once(p, "diary.dexrider.kaijin." + kind.id);
            default -> { }
        }
    }

    public static void onFall(ServerPlayer p) {
        ItemStack driver = DexDriverItem.findDriver(p);
        if (driver.isEmpty()) return;
        String prefix = day(p) + "|diary.dexrider.fell.";   // como mucho una vez al dia
        for (String e : entries(driver)) if (e.startsWith(prefix)) return;
        write(p, "diary.dexrider.fell." + variant(p.getUUID().toString() + day(p), 3));
    }

    static int variant(String seed, int n) {
        return Math.floorMod(seed.hashCode(), n) + 1;
    }
}
