package com.draxer.dexrider.rider;

import com.draxer.dexrider.entity.KaijinEntity;
import com.draxer.dexrider.entity.RiderAllyEntity;
import com.draxer.dexrider.item.AllySkills;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.registry.DexComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Encargos de los amigos de U.A. (0.53): Izuku, Momo, Kinoko, Toru y Mei tienen tres encargos cada uno, en orden.
 * Se piden y se entregan desde el menu del amigo (boton "Encargo"). El progreso vive en el Driver (componente
 * ua_missions: rider -> [encargo, progreso, aceptado, avisado]).
 *
 * Tipos: traer objetos (se gastan al entregar), derrotar enemigos con el amigo cerca (de noche, kaijin concretos o
 * jefes de la arena), llegar a un nivel de confianza, que el amigo trabaje para ti, o ir a un bioma con el.
 */
public final class UaMissions {
    private UaMissions() {}

    public enum Type { ITEMS, KILL, NIGHT_KILL, KAIJIN, BOSS, TRUST, WORK, BIOME }

    public record Mission(String id, Type type, int amount, List<Item> items, @Nullable KaijinEntity.Kind kind, List<String> biomes,
                          Supplier<List<ItemStack>> reward, int levels, int trust) {}

    private static Mission items(String id, int n, List<Item> it, Supplier<List<ItemStack>> r, int lv, int tr) {
        return new Mission(id, Type.ITEMS, n, it, null, List.of(), r, lv, tr);
    }

    private static Mission of(String id, Type t, int n, Supplier<List<ItemStack>> r, int lv, int tr) {
        return new Mission(id, t, n, List.of(), null, List.of(), r, lv, tr);
    }

    private static Mission kaijin(String id, Type t, int n, KaijinEntity.Kind k, Supplier<List<ItemStack>> r, int lv, int tr) {
        return new Mission(id, t, n, List.of(), k, List.of(), r, lv, tr);
    }

    private static Mission biome(String id, List<String> biomes, Supplier<List<ItemStack>> r, int lv, int tr) {
        return new Mission(id, Type.BIOME, 1, List.of(), null, biomes, r, lv, tr);
    }

    private static ItemStack s(Item i, int n) { return new ItemStack(i, n); }

    public static final Map<KamenRide, List<Mission>> CHAINS = Map.of(
            KamenRide.NINE, List.of(
                    of("nine_notes", Type.KILL, 10, () -> List.of(s(Items.WRITABLE_BOOK, 1), s(Items.GOLDEN_CARROT, 8)), 3, 6),
                    of("nine_trust", Type.TRUST, 2, () -> List.of(s(Items.DIAMOND, 2)), 5, 0),
                    of("nine_boss", Type.BOSS, 1, () -> List.of(s(Items.ENCHANTED_GOLDEN_APPLE, 1), s(Items.DIAMOND, 3)), 10, 10)),
            KamenRide.FORGE, List.of(
                    items("forge_iron", 32, List.of(Items.IRON_INGOT), () -> List.of(s(Items.DIAMOND, 2), s(Items.BOOK, 4)), 3, 6),
                    items("forge_chemistry", 24, List.of(Items.REDSTONE, Items.GLOWSTONE_DUST), () -> List.of(s(Items.EXPERIENCE_BOTTLE, 12)), 5, 6),
                    kaijin("forge_rhino", Type.KAIJIN, 3, KaijinEntity.Kind.RHINO, () -> List.of(s(Items.NETHERITE_SCRAP, 2)), 8, 10)),
            KamenRide.SPORE, List.of(
                    items("spore_mushrooms", 16, List.of(Items.RED_MUSHROOM, Items.BROWN_MUSHROOM), () -> List.of(s(Items.MUSHROOM_STEW, 3), s(Items.EMERALD, 6)), 2, 6),
                    of("spore_night", Type.NIGHT_KILL, 15, () -> List.of(s(Items.GLOW_BERRIES, 16), s(Items.DIAMOND, 2)), 5, 6),
                    biome("spore_forest", List.of("minecraft:dark_forest", "minecraft:mushroom_fields", "minecraft:pale_garden"),
                            () -> List.of(s(Items.MYCELIUM, 16), s(Items.GOLDEN_APPLE, 2)), 8, 10)),
            KamenRide.MIRAGE, List.of(
                    items("mirage_shine", 12, List.of(Items.AMETHYST_SHARD), () -> List.of(s(Items.SPYGLASS, 1), s(Items.EMERALD, 8)), 2, 6),
                    of("mirage_friends", Type.TRUST, 1, () -> List.of(s(Items.DIAMOND, 2), s(Items.CAKE, 1)), 4, 0),
                    kaijin("mirage_queen", Type.BOSS, 1, KaijinEntity.Kind.MANTIS_QUEEN, () -> List.of(s(Items.TOTEM_OF_UNDYING, 1)), 10, 10)),
            KamenRide.PROTO, List.of(
                    items("proto_parts", 32, List.of(Items.COPPER_INGOT), () -> List.of(s(Items.REDSTONE_BLOCK, 4), s(Items.PISTON, 4)), 3, 6),
                    of("proto_field_test", Type.WORK, 30, () -> List.of(s(Items.DIAMOND, 3)), 5, 6),
                    items("proto_ultimate", 1, List.of(Items.NETHERITE_INGOT), () -> List.of(s(Items.ENCHANTED_GOLDEN_APPLE, 1), s(Items.EMERALD_BLOCK, 2)), 10, 10)));

    // ------------------------------------------------------------------ estado

    private static Map<String, List<Integer>> all(ServerPlayer p) {
        ItemStack d = DexDriverItem.findDriver(p);
        Map<String, List<Integer>> m = d.isEmpty() ? null : d.get(DexComponents.UA_MISSIONS.get());
        return m == null ? Map.of() : m;
    }

    /** [encargo, progreso, aceptado, avisado] */
    public static int[] state(ServerPlayer p, KamenRide r) {
        List<Integer> l = all(p).get(r.id());
        int[] out = new int[4];
        if (l != null) for (int i = 0; i < Math.min(4, l.size()); i++) out[i] = l.get(i);
        return out;
    }

    private static void save(ServerPlayer p, KamenRide r, int[] st) {
        ItemStack d = DexDriverItem.findDriver(p);
        if (d.isEmpty()) return;
        Map<String, List<Integer>> m = new HashMap<>(all(p));
        m.put(r.id(), List.of(st[0], st[1], st[2], st[3]));
        d.set(DexComponents.UA_MISSIONS.get(), m);
    }

    @Nullable
    public static Mission current(ServerPlayer p, KamenRide r) {
        List<Mission> chain = CHAINS.get(r);
        if (chain == null) return null;
        int i = state(p, r)[0];
        return i < chain.size() ? chain.get(i) : null;
    }

    private static int countItems(ServerPlayer p, List<Item> items) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (items.contains(s.getItem())) n += s.getCount();
        }
        return n;
    }

    private static void takeItems(ServerPlayer p, List<Item> items, int n) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (n <= 0) return;
            if (!items.contains(s.getItem())) continue;
            int k = Math.min(n, s.getCount());
            s.shrink(k);
            n -= k;
        }
    }

    @Nullable
    private static RiderAllyEntity near(ServerPlayer p, KamenRide r, double range) {
        for (RiderAllyEntity a : p.level().getEntitiesOfClass(RiderAllyEntity.class, p.getBoundingBox().inflate(range),
                a -> a.isAlive() && a.rider() == r && p.getUUID().equals(a.owner()))) return a;
        return null;
    }

    /** Progreso actual (para los tipos que se miran en el momento, se calcula ahora). */
    public static int progress(ServerPlayer p, KamenRide r, Mission m) {
        return switch (m.type()) {
            case ITEMS -> countItems(p, m.items());
            case TRUST -> AllyTrust.level(AllyWork.entry(p, r).trust());
            case BIOME -> {
                RiderAllyEntity a = near(p, r, 16);
                yield a != null && m.biomes().contains(p.level().getBiome(p.blockPosition()).unwrapKey()
                        .map(k -> k.identifier().toString()).orElse("")) ? 1 : state(p, r)[1];
            }
            default -> state(p, r)[1];
        };
    }

    public static boolean ready(ServerPlayer p, KamenRide r, Mission m) {
        return progress(p, r, m) >= m.amount();
    }

    // ------------------------------------------------------------------ eventos

    /** Un enemigo cayo por ti o por uno de tus amigos. */
    public static void onKill(ServerPlayer p, LivingEntity victim) {
        for (var e : CHAINS.keySet()) {
            Mission m = current(p, e);
            int[] st = state(p, e);
            if (m == null || st[2] == 0 || st[1] >= m.amount()) continue;
            if (near(p, e, 24) == null) continue;
            boolean counts = switch (m.type()) {
                case KILL -> victim instanceof Enemy;
                case NIGHT_KILL -> victim instanceof Enemy && !p.level().isBrightOutside();
                case KAIJIN -> victim instanceof KaijinEntity k && k.kind() == m.kind();
                case BOSS -> victim instanceof KaijinEntity k && (m.kind() == null ? k.kind().boss() : k.kind() == m.kind());
                default -> false;
            };
            if (!counts) continue;
            st[1]++;
            save(p, e, st);
            if (st[1] >= m.amount()) notifyReady(p, e, st);
            else p.sendSystemMessage(Component.translatable("message.dexrider.ua_progress", e.displayName(), st[1], m.amount()), true);
        }
    }

    /** El amigo trabajo para ti (una accion). */
    public static void onWork(ServerPlayer p, KamenRide r) {
        Mission m = current(p, r);
        int[] st = state(p, r);
        if (m == null || m.type() != Type.WORK || st[2] == 0 || st[1] >= m.amount()) return;
        st[1]++;
        save(p, r, st);
        if (st[1] >= m.amount()) notifyReady(p, r, st);
    }

    /** Cada 2 s: avisa una vez cuando un encargo que se mira en el momento ya se puede entregar. */
    public static void tick(ServerPlayer p) {
        for (var e : CHAINS.keySet()) {
            Mission m = current(p, e);
            int[] st = state(p, e);
            if (m == null || st[2] == 0 || st[3] != 0) continue;
            if (m.type() == Type.BIOME && progress(p, e, m) >= 1 && st[1] == 0) { st[1] = 1; save(p, e, st); }
            if (ready(p, e, m)) notifyReady(p, e, state(p, e));
        }
    }

    private static void notifyReady(ServerPlayer p, KamenRide r, int[] st) {
        if (st[3] != 0) return;
        st[3] = 1;
        save(p, r, st);
        p.sendSystemMessage(Component.translatable("message.dexrider.ua_ready", r.displayName()).withStyle(ChatFormatting.GOLD));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8F, 1.3F);
    }

    // ------------------------------------------------------------------ boton "Encargo" del menu

    public static void talk(ServerLevel level, ServerPlayer p, RiderAllyEntity ally) {
        KamenRide r = ally.rider();
        List<Mission> chain = CHAINS.get(r);
        if (chain == null) {
            p.sendSystemMessage(Component.translatable("message.dexrider.ua_none", r.displayName()), true);
            return;
        }
        int[] st = state(p, r);
        if (st[0] >= chain.size()) {
            say(p, r, Component.translatable("ua.dexrider." + r.id() + ".all_done"));
            return;
        }
        Mission m = chain.get(st[0]);
        String k = "ua.dexrider." + m.id();
        if (st[2] == 0) {                       // pedir el encargo
            st[2] = 1; st[1] = 0; st[3] = 0;
            save(p, r, st);
            p.sendSystemMessage(Component.translatable("message.dexrider.ua_new", r.displayName(),
                    Component.translatable(k + ".title"), st[0] + 1, chain.size()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            say(p, r, Component.translatable(k + ".ask"));
            p.sendSystemMessage(Component.translatable("message.dexrider.mission_goal", Component.translatable(k + ".goal", m.amount())).withStyle(ChatFormatting.YELLOW));
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.1F);
            return;
        }
        if (!ready(p, r, m)) {
            p.sendSystemMessage(Component.translatable("message.dexrider.ua_progress", r.displayName(), Math.min(progress(p, r, m), m.amount()), m.amount()), false);
            p.sendSystemMessage(Component.translatable("message.dexrider.mission_goal", Component.translatable(k + ".goal", m.amount())).withStyle(ChatFormatting.YELLOW));
            return;
        }
        // entregar
        if (m.type() == Type.ITEMS) takeItems(p, m.items(), m.amount());
        say(p, r, Component.translatable(k + ".thanks"));
        List<ItemStack> reward = new ArrayList<>(m.reward().get());
        for (ItemStack it : reward) if (!p.getInventory().add(it.copy())) p.drop(it.copy(), false);
        if (m.levels() > 0) p.giveExperienceLevels(m.levels());
        if (m.trust() > 0) AllyWork.save(p, r, AllyTrust.add(p, ally, AllyWork.entry(p, r), m.trust()));
        p.sendSystemMessage(Component.translatable("message.dexrider.ua_done", Component.translatable(k + ".title")).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.2F);
        FX.particle(level, net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, FX.center(ally), 12, 0.5, 0.0);
        save(p, r, new int[]{st[0] + 1, 0, 0, 0});
        if (st[0] + 1 >= chain.size()) {
            DexAdvancements.award(p, "ua_" + r.id());
            say(p, r, Component.translatable("ua.dexrider." + r.id() + ".all_done"));
        }
    }

    private static void say(ServerPlayer p, KamenRide r, Component line) {
        p.sendSystemMessage(Component.literal("<").append(r.displayName()).append("> ").append(line).withStyle(ChatFormatting.WHITE));
    }
}
