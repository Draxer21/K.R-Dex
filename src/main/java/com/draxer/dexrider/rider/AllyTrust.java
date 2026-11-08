package com.draxer.dexrider.rider;

import com.draxer.dexrider.entity.RiderAllyEntity;
import com.draxer.dexrider.item.AllySkills;
import com.draxer.dexrider.network.AllyTalkPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Confianza con cada amigo Rider (0-100, guardada en el Driver junto a lo que aprendio).
 *  Niveles: 0 Conocido (0-19), 1 Amigo (20-49), 2 Buen amigo (50-79), 3 Confianza total (80-100).
 *  Sube: charlar (+2, una vez por dia de juego), regalar (+6 si es su favorito, +3 comida o flores, +1 lo demas;
 *  una vez por dia), pelear a su lado (+1 por enemigo, hasta 4 por dia) y trabajar para ti (+1 de vez en cuando).
 *  Cada nivel: trabaja un 10 % mas rapido; con Buen amigo se abre un tema de su historia y con Confianza total,
 *  algo personal.
 */
public final class AllyTrust {
    private AllyTrust() {}

    public static final int[] THRESHOLDS = {0, 20, 50, 80};
    public static final int FLAG_TALKED = 1, FLAG_GIFTED = 2;
    public static final int MAX_FIGHTS_PER_DAY = 4;

    public static int level(int trust) {
        int lv = 0;
        for (int i = 0; i < THRESHOLDS.length; i++) if (trust >= THRESHOLDS[i]) lv = i;
        return lv;
    }

    public static Component levelName(int trust) {
        return Component.translatable("trust.dexrider.level" + level(trust));
    }

    /** Multiplicador de velocidad de trabajo por la confianza. */
    public static float workBonus(int trust) {
        return 1.0F + 0.1F * level(trust);
    }

    private static final Map<KamenRide, List<String>> FAVORITES = new EnumMap<>(KamenRide.class);

    static {
        FAVORITES.put(KamenRide.NINE, List.of("minecraft:cooked_porkchop", "minecraft:writable_book", "minecraft:book"));
        FAVORITES.put(KamenRide.CIPHER, List.of("minecraft:iron_ingot", "minecraft:redstone", "minecraft:clock"));
        FAVORITES.put(KamenRide.SPORE, List.of("minecraft:brown_mushroom", "minecraft:red_mushroom", "minecraft:mushroom_stew"));
        FAVORITES.put(KamenRide.FORGE, List.of("minecraft:book", "minecraft:cake", "minecraft:glass"));
        FAVORITES.put(KamenRide.MIRAGE, List.of("minecraft:glass", "minecraft:glow_berries", "minecraft:cake", "minecraft:cookie"));
        FAVORITES.put(KamenRide.PROTO, List.of("minecraft:redstone", "minecraft:copper_ingot", "minecraft:piston", "minecraft:iron_ingot"));
        FAVORITES.put(KamenRide.WARDEN, List.of("minecraft:cooked_beef", "minecraft:bread", "minecraft:shield"));
        FAVORITES.put(KamenRide.PSYGA, List.of("minecraft:cake", "minecraft:cookie", "minecraft:honey_bottle", "minecraft:poppy", "minecraft:dandelion"));
        FAVORITES.put(KamenRide.ORGA, List.of("minecraft:cooked_salmon", "minecraft:cooked_cod", "minecraft:honey_bottle"));
        FAVORITES.put(KamenRide.KAIXA, List.of("minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:comparator"));
    }

    public static boolean isFavorite(KamenRide r, ItemStack s) {
        List<String> f = FAVORITES.get(r);
        return f != null && f.contains(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
    }

    /** Flores (por id de etiqueta: la constante cambia de nombre entre versiones). */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> FLOWERS = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.withDefaultNamespace("small_flowers"));

    private static boolean isNice(ItemStack s) {
        return s.has(net.minecraft.core.component.DataComponents.FOOD) || s.is(FLOWERS);
    }

    /** Dia de juego (cada 24000 ticks). */
    private static long day(ServerLevel level) {
        return level.getGameTime() / 24000L;
    }

    /** Entrada con el contador del dia al dia (si cambio el dia, se borran banderas y peleas). */
    private static AllySkills.Entry today(ServerLevel level, AllySkills.Entry e) {
        long d = day(level);
        return e.trustDay() == d ? e : e.withTrust(e.trust(), d, 0, 0);
    }

    /** Suma confianza y avisa si sube de nivel. */
    public static AllySkills.Entry add(ServerPlayer owner, RiderAllyEntity ally, AllySkills.Entry e, int amount) {
        int before = e.trust(), after = Math.min(100, before + amount);
        e = e.withTrust(after, e.trustDay(), e.trustFlags(), e.fights());
        if (level(after) > level(before)) {
            owner.sendSystemMessage(Component.translatable("message.dexrider.trust_up", ally.rider().displayName(), levelName(after))
                    .withStyle(ChatFormatting.GOLD));
        }
        return e;
    }

    /** Charlar: +2 la primera vez del dia. */
    public static void onTalk(ServerLevel level, ServerPlayer owner, RiderAllyEntity ally) {
        AllySkills.Entry e = today(level, AllyWork.entry(owner, ally.rider()));
        if ((e.trustFlags() & FLAG_TALKED) == 0) {
            e = e.withTrust(e.trust(), e.trustDay(), e.trustFlags() | FLAG_TALKED, e.fights());
            e = add(owner, ally, e, 2);
        }
        AllyWork.save(owner, ally.rider(), e);
    }

    /** Regalar lo que tiene en la mano principal (gasta uno). Devuelve la clave de la reaccion (null si no lleva nada). */
    public static String onGift(ServerLevel level, ServerPlayer owner, RiderAllyEntity ally) {
        ItemStack hand = owner.getMainHandItem();
        if (hand.isEmpty()) return null;
        boolean fav = isFavorite(ally.rider(), hand);
        int points = fav ? 6 : isNice(hand) ? 3 : 1;
        if (!owner.isCreative()) hand.shrink(1);
        AllySkills.Entry e = today(level, AllyWork.entry(owner, ally.rider()));
        if ((e.trustFlags() & FLAG_GIFTED) == 0) {
            e = e.withTrust(e.trust(), e.trustDay(), e.trustFlags() | FLAG_GIFTED, e.fights());
            e = add(owner, ally, e, points);
        }
        AllyWork.save(owner, ally.rider(), e);
        return "dialog.dexrider." + ally.rider().id() + (fav ? ".gift_love." : ".gift_ok.") + (1 + level.getRandom().nextInt(3));
    }

    /** Derribo a un enemigo a tu lado: +1, hasta 4 por dia. */
    public static void onFight(ServerLevel level, ServerPlayer owner, RiderAllyEntity ally) {
        AllySkills.Entry e = today(level, AllyWork.entry(owner, ally.rider()));
        if (e.fights() >= MAX_FIGHTS_PER_DAY) return;
        e = e.withTrust(e.trust(), e.trustDay(), e.trustFlags(), e.fights() + 1);
        e = add(owner, ally, e, 1);
        AllyWork.save(owner, ally.rider(), e);
    }

    /** Trabajar para ti tambien une (llamado de vez en cuando desde el trabajo). */
    public static void onWork(ServerPlayer owner, RiderAllyEntity ally) {
        AllySkills.Entry e = AllyWork.entry(owner, ally.rider());
        if (e.trust() >= THRESHOLDS[2]) return;    // el trabajo solo llega hasta Buen amigo
        AllyWork.save(owner, ally.rider(), add(owner, ally, e, 1));
    }

    // ------------------------------------------------------------------ conversacion

    public static final int TOPIC_HOW = 0, TOPIC_ABOUT = 1, TOPIC_ADVICE = 2, TOPIC_DEEP = 3, TOPIC_SECRET = 4;
    private static final String[] TOPIC_KEYS = {"how", "about", "advice", "deep", "secret"};
    private static final int[] TOPIC_VARIANTS = {3, 2, 3, 1, 1};

    /** Que temas puede tocar (bit por tema). */
    public static int topics(int trust) {
        int m = (1 << TOPIC_HOW) | (1 << TOPIC_ABOUT) | (1 << TOPIC_ADVICE);
        if (level(trust) >= 2) m |= 1 << TOPIC_DEEP;
        if (level(trust) >= 3) m |= 1 << TOPIC_SECRET;
        return m;
    }

    /** Clave de la respuesta a un tema (o el saludo si topic < 0). */
    public static String answerKey(ServerLevel level, KamenRide rider, int topic) {
        if (topic < 0) return "dialog.dexrider." + rider.id() + ".hello." + (1 + level.getRandom().nextInt(2));
        return "dialog.dexrider." + rider.id() + ".topic_" + TOPIC_KEYS[topic] + "." + (1 + level.getRandom().nextInt(TOPIC_VARIANTS[topic]));
    }

    /** Envia (o refresca) la ventana de conversacion con una linea. */
    public static void sendTalk(ServerPlayer owner, RiderAllyEntity ally, String lineKey) {
        int trust = AllyWork.entry(owner, ally.rider()).trust();
        AllyTalkPayload.send(owner, new AllyTalkPayload(ally.getId(), ally.rider().id(), trust, topics(trust), lineKey));
    }
}
