package com.draxer.dexrider.rider;

import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.network.MissionSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Misiones de la familia Kagami: una cadena de cartas de Genzo, Nagisa y Tetsuya.
 * Van en orden; cada una se cumple sola cuando haces lo que pide (casi todas leen un logro), da su recompensa y trae
 * la carta de la siguiente. Cumplida = logro oculto dexrider:mission/<id> (sobrevive a la muerte, sin aviso).
 */
public final class Missions {
    private Missions() {}

    public record Mission(String id, String giver, Predicate<ServerPlayer> done, Supplier<List<ItemStack>> items, int levels) {}

    private static Predicate<ServerPlayer> adv(String name) {
        return p -> DexAdvancements.has(p, name);
    }

    private static ItemStack s(net.minecraft.world.item.Item item, int n) {
        return new ItemStack(item, n);
    }

    public static final List<Mission> ALL = List.of(
            new Mission("mountain_house", "genzo", adv("mountain_house"), () -> List.of(s(Items.GOLDEN_APPLE, 2), s(Items.TORCH, 16)), 0),
            new Mission("henshin", "genzo", p -> DexAdvancements.has(p, "henshin") || Transformation.isTransformed(p), List::of, 5),
            new Mission("first_scan", "nagisa", adv("first_scan"), () -> List.of(s(Items.CAKE, 1), s(Items.COOKIE, 8)), 0),
            new Mission("form_ride", "tetsuya", adv("form_ride"), () -> List.of(s(Items.DIAMOND, 3)), 0),
            new Mission("hydro", "nagisa", p -> DexDriverItem.getDeck(DexDriverItem.findDriver(p)).has(DexCard.FORM_RIDE_HYDRO),
                    () -> List.of(s(Items.TRIDENT, 1)), 0),
            new Mission("parry", "genzo", adv("parry"), () -> List.of(s(Items.DIAMOND, 2)), 5),
            new Mission("ally", "tetsuya", adv("ally"), () -> List.of(s(Items.DIAMOND, 4)), 0),
            new Mission("violent_emotion", "nagisa", adv("violent_emotion"), () -> List.of(s(Items.GOLDEN_APPLE, 3)), 0),
            new Mission("heroic_emotion", "nagisa", adv("heroic_emotion"), () -> List.of(s(Items.ENCHANTED_GOLDEN_APPLE, 1)), 0),
            new Mission("gunma_lab", "genzo", adv("gunma_lab"), () -> List.of(s(Items.NETHERITE_SCRAP, 2)), 0),
            new Mission("kaixa", "tetsuya", adv("kaixa"), () -> List.of(s(Items.NETHERITE_INGOT, 1)), 0),
            new Mission("commission_depot", "nagisa", adv("commission_depot"), () -> List.of(s(Items.EMERALD, 10), s(Items.DIAMOND, 3)), 0),
            new Mission("architect", "genzo", adv("architect"),
                    () -> List.of(s(Items.TOTEM_OF_UNDYING, 1), s(Items.ENCHANTED_GOLDEN_APPLE, 1)), 10));

    public static boolean isDone(ServerPlayer p, Mission m) {
        return DexAdvancements.has(p, "mission/" + m.id());
    }

    /** Cuantas lleva cumplidas (en orden). */
    public static int doneCount(ServerPlayer p) {
        int n = 0;
        for (Mission m : ALL) { if (!isDone(p, m)) break; n++; }
        return n;
    }

    /** Cada 2 s por jugador: si cumplio la mision actual, recompensa y carta de la siguiente. */
    public static void tick(ServerPlayer p) {
        int n = doneCount(p);
        if (n >= ALL.size()) return;
        Mission m = ALL.get(n);
        if (!m.done().test(p)) return;
        complete(p, m);
        MissionSyncPayload.send(p, n + 1);
        if (n + 1 < ALL.size()) letter(p, ALL.get(n + 1));
        else p.sendSystemMessage(Component.translatable("message.dexrider.missions_all").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    private static void complete(ServerPlayer p, Mission m) {
        DexAdvancements.award(p, "mission/" + m.id());
        p.sendSystemMessage(Component.translatable("message.dexrider.mission_done",
                Component.translatable("mission.dexrider." + m.id() + ".title")).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        p.sendSystemMessage(Component.translatable("message.dexrider.mission_reward", rewardText(m)).withStyle(ChatFormatting.AQUA));
        for (ItemStack it : m.items().get()) if (!p.getInventory().add(it)) p.drop(it, false);
        if (m.levels() > 0) p.giveExperienceLevels(m.levels());
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.1F);
    }

    /** La carta de una mision (en el chat). */
    public static void letter(ServerPlayer p, Mission m) {
        String k = "mission.dexrider." + m.id();
        p.sendSystemMessage(Component.translatable("message.dexrider.mission_letter",
                Component.translatable("mission.dexrider.giver." + m.giver())).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        p.sendSystemMessage(Component.translatable(k + ".letter1").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        p.sendSystemMessage(Component.translatable(k + ".letter2").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        p.sendSystemMessage(Component.translatable("message.dexrider.mission_goal", Component.translatable(k + ".goal")).withStyle(ChatFormatting.YELLOW));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.9F);
    }

    public static MutableComponent rewardText(Mission m) {
        MutableComponent c = Component.empty();
        boolean first = true;
        for (ItemStack it : m.items().get()) {
            if (!first) c.append(", ");
            c.append(it.getCount() + " × ").append(it.getHoverName());
            first = false;
        }
        if (m.levels() > 0) {
            if (!first) c.append(", ");
            c.append(Component.translatable("screen.dexrider.missions.levels", m.levels()));
        }
        return c;
    }

    public static void onLogin(ServerPlayer p) {
        MissionSyncPayload.send(p, doneCount(p));
    }
}
