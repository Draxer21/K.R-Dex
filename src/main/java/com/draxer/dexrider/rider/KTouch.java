package com.draxer.dexrider.rider;

import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.DriverDeck;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexItems;
import com.draxer.dexrider.registry.DexSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * K-Touch y Complete Form, con las reglas del fanfic:
 *
 *  - Al reunir NUEVE Kamen Ride (Dex + ocho), el Driver forja el K-Touch y la carta de Complete Form.
 *    La carta sale incompleta: ocho blasones, el de Dex al centro y un hueco apagado.
 *  - Con la DECIMA Kamen Ride la carta se completa.
 *  - Sin uso parcial: para entrar hay que pulsar los diez pads, incluido el de Dex.
 *  - El Driver se desacopla a la cadera derecha y el K-Touch ocupa el centro del cinturon (se ve en la armadura).
 *  - Multiplicador: +0,2 por cada Kamen Ride (con diez, x3). Agota rapido (hambre).
 *
 * Pendiente para la siguiente tanda: invocar las proyecciones de cada Rider pulsando su blason.
 */
public final class KTouch {
    private KTouch() {}

    public static final int FORGE_AT = 9;      // Dex + 8
    public static final int COMPLETE_AT = 10;  // Dex + 9

    public static int kamenRideCount(DriverDeck deck) {
        return deck.count(DexCard.Family.KAMEN_RIDE);
    }

    /** Llamado cada vez que el mazo gana una carta Kamen Ride. */
    public static void onKamenRideAdded(ServerPlayer player, ItemStack driver, DriverDeck deck) {
        int count = kamenRideCount(deck);
        if (count >= FORGE_AT && !Boolean.TRUE.equals(driver.get(DexComponents.KTOUCH_FORGED.get()))) {
            driver.set(DexComponents.KTOUCH_FORGED.get(), true);
            give(player, new ItemStack(DexItems.K_TOUCH.get()));
            give(player, new ItemStack(DexItems.COMPLETE_FORM_CARD.get()));
            DexAdvancements.award(player, "ktouch");
            if (player.level() instanceof ServerLevel level) {
                DexSounds.play(level, player, DexSounds.SCAN_COMPLETE, 1.4F, 0.6F);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 0.8F);
                for (KamenRide k : KamenRide.RING) FX.ring(level, player, k.color, 1.4, 3);
            }
            player.sendSystemMessage(Component.translatable("message.dexrider.ktouch_forged").withStyle(ChatFormatting.GOLD));
            if (count < COMPLETE_AT) {
                player.sendSystemMessage(Component.translatable("message.dexrider.ktouch_incomplete",
                        COMPLETE_AT - count).withStyle(ChatFormatting.GRAY));
            }
        }
        if (count == COMPLETE_AT) {
            player.sendSystemMessage(Component.translatable("message.dexrider.ktouch_complete").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        // la undecima Kamen Ride (Kaixa) forja una segunda carta: otro grupo de nueve
        if (Boolean.TRUE.equals(driver.get(DexComponents.KTOUCH_FORGED.get())) && deck.has(DexCard.KAMEN_RIDE_KAIXA)
                && !Boolean.TRUE.equals(driver.get(DexComponents.KTOUCH_CARD2.get()))) {
            driver.set(DexComponents.KTOUCH_CARD2.get(), true);
            give(player, new ItemStack(DexItems.COMPLETE_FORM_CARD_2.get()));
            player.sendSystemMessage(Component.translatable("message.dexrider.ktouch_card2").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** Tiene la carta de Complete Form del anillo activo. */
    public static boolean hasActiveCard(Player player) {
        return has(player, KamenRide.ring(player) == KamenRide.RING_2 ? DexItems.COMPLETE_FORM_CARD_2.get() : DexItems.COMPLETE_FORM_CARD.get());
    }

    /** Faltan Kamen Ride del anillo activo (o la de Dex). */
    public static int missing(Player player, DriverDeck deck) {
        int n = deck.has(DexCard.KAMEN_RIDE_DEX) ? 0 : 1;
        for (KamenRide k : KamenRide.ring(player)) if (k.card != null && !deck.has(k.card)) n++;
        return n;
    }

    /** Para los mundos donde las cartas ya estaban antes de esta version. */
    public static void retroCheck(ServerPlayer player) {
        ItemStack driver = DexDriverItem.findDriver(player);
        if (driver.isEmpty()) return;
        DriverDeck deck = DexDriverItem.getDeck(driver);
        if ((kamenRideCount(deck) >= FORGE_AT && !Boolean.TRUE.equals(driver.get(DexComponents.KTOUCH_FORGED.get())))
                || (deck.has(DexCard.KAMEN_RIDE_KAIXA) && Boolean.TRUE.equals(driver.get(DexComponents.KTOUCH_FORGED.get()))
                    && !Boolean.TRUE.equals(driver.get(DexComponents.KTOUCH_CARD2.get())))) {
            onKamenRideAdded(player, driver, deck);
        }
    }

    public static boolean has(Player player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) return true;
        }
        return false;
    }

    /** Pulsados los diez pads en la pantalla del K-Touch. */
    public static void enter(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!Transformation.isTransformed(player) || KamenRide.current(player) != KamenRide.DEX) {
            player.sendSystemMessage(Component.translatable("message.dexrider.ktouch_need_dex"), true);
            return;
        }
        if (!has(player, DexItems.K_TOUCH.get()) || !hasActiveCard(player)) return;
        int miss = missing(player, DexDriverItem.getDeck(DexDriverItem.findDriver(player)));
        if (miss > 0) {
            player.sendSystemMessage(Component.translatable("message.dexrider.ktouch_incomplete", miss), true);
            return;
        }
        // Complete Form Nv.2: si entras desde la Upgrade Form, la carta sube de nivel (sustitucion) y ya no baja
        ItemStack drv = DexDriverItem.findDriver(player);
        boolean fromUpgrade = UpgradeForm.isActive(player);
        Integer clv = drv.get(DexComponents.COMPLETE_LEVEL.get());
        KamenRide.set(level, player, KamenRide.COMPLETE);
        DexAdvancements.award(player, "complete_form");
        if (fromUpgrade && (clv == null || clv < 2)) {
            drv.set(DexComponents.COMPLETE_LEVEL.get(), 2);
            clv = 2;
            player.sendSystemMessage(Component.translatable("message.dexrider.complete_lv2").withStyle(ChatFormatting.LIGHT_PURPLE));
            DexAdvancements.award(player, "complete_form_2");
        }
        if (clv != null && clv >= 2) player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).set(DexComponents.COMPLETE_LV2.get(), true);
        // espectaculo: las nueve cartas giran alrededor y se clavan en la armadura
        Vec3 c = FX.center(player);
        KamenRide[] ring = KamenRide.ring(player);
        for (int i = 0; i < ring.length; i++) {
            KamenRide k = ring[i];
            double a = Math.toRadians(i * 40);
            Vec3 from = c.add(Math.cos(a) * 3, 1.2, Math.sin(a) * 3);
            Scheduler.later(player, 1 + i * 2, () -> {
                FX.card(level, from, c.subtract(from).normalize(), k.color);
                FX.line(level, from, c, k.color, 1.0F, 0.4);
            });
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.6F);
        player.sendSystemMessage(Component.translatable("message.dexrider.complete_form",
                String.format("%.1f", RiderPowers.completeMultiplier(player))).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Salir de la Complete Form (K-Touch otra vez, o agotamiento). */
    public static void exit(ServerLevel level, ServerPlayer player, boolean exhausted) {
        if (KamenRide.current(player) != KamenRide.COMPLETE) return;
        releaseAll(level, player);
        KamenRide.set(level, player, KamenRide.DEX);
        if (exhausted) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 2));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
            player.sendSystemMessage(Component.translatable("message.dexrider.complete_exhausted"), true);
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    // ------------------------------------------------------------------ blasones: invocar a los nueve

    /**
     * En Complete Form, pulsar un blason (pad del K-Touch con B o el teclado numerico) invoca a ese Rider.
     * Como en el fanfic: si el amigo esta cerca de verdad, no sale una proyeccion; se transforma el mismo y pelea
     * con todo. Si no, sale su proyeccion, que pelea como el original. Cada uno que sostienes agota mas rapido.
     * Pulsar otra vez el mismo blason lo libera.
     */
    private static final java.util.Map<java.util.UUID, java.util.EnumSet<KamenRide>> HELD = new java.util.HashMap<>();

    public static boolean isHeld(ServerPlayer player, KamenRide k) {
        var set = HELD.get(player.getUUID());
        return set != null && set.contains(k);
    }

    public static int heldCount(ServerPlayer player) {
        var set = HELD.get(player.getUUID());
        return set == null ? 0 : set.size();
    }

    public static void toggleCrest(ServerPlayer player, KamenRide k) {
        if (!(player.level() instanceof ServerLevel level) || KamenRide.current(player) != KamenRide.COMPLETE) return;
        var set = HELD.computeIfAbsent(player.getUUID(), u -> java.util.EnumSet.noneOf(KamenRide.class));
        if (set.contains(k) && inLoop(player) && player.getRandom().nextBoolean()) {
            // en el bucle, tu "no" deja de ser fiable
            player.sendSystemMessage(Component.translatable("message.dexrider.correa_no_fails").withStyle(ChatFormatting.DARK_PURPLE), true);
            return;
        }
        if (set.remove(k)) {
            FX.ring(level, player, k.color, 1.0, 3);
            checkCorreaEnd(player, set);
            player.sendSystemMessage(Component.translatable("message.dexrider.crest_release", k.displayName()), true);
            return;
        }
        DriverDeck deck = DexDriverItem.getDeck(DexDriverItem.findDriver(player));
        if (k.card == null || !deck.has(k.card)) return;
        set.add(k);
        DexSounds.play(level, player, DexSounds.CARD_INSERT, 1.0F, 1.3F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.5F);
        var present = AllyManager.nearbyFriend(player, k, 32);
        if (present != null) {
            FX.line(level, FX.center(player), FX.center(present), k.color, 1.2F, 0.5);
            FX.ring(level, present, k.color, 1.4, 6);
            player.sendSystemMessage(Component.translatable("message.dexrider.crest_present", k.displayName()), true);
        } else {
            AllyManager.spawnProjection(level, player, k);
            player.sendSystemMessage(Component.translatable("message.dexrider.crest_projection", k.displayName()), true);
        }
    }

    /** Cada tick en Complete Form: los amigos presentes se potencian, y cada blason sostenido cuesta. */
    public static void tickHeld(ServerLevel level, ServerPlayer player) {
        var set = HELD.get(player.getUUID());
        if (set == null || set.isEmpty()) return;
        float cost = 0;
        for (KamenRide k : set) {
            var present = AllyManager.nearbyFriend(player, k, 48);
            if (present != null) {
                cost += isFamily(k) ? 0.006F : 0.012F;   // la sangre: un Kagami cuesta la mitad de sostener
                if (player.tickCount % 20 == 0) {
                    present.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, 1, false, false));
                    present.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 1, false, false));
                    present.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, false, false));
                    FX.dust(level, k.color, 1.0F, FX.center(present), 4, 0.5);
                }
            } else cost += 0.025F;
        }
        if (isLevel2(player)) cost *= 0.75F;   // la Nv.2 aguanta mas
        correaTick(level, player, set);
        if (!player.isCreative()) player.causeFoodExhaustion(cost);
    }

    public static boolean isLevel2(Player player) {
        return Boolean.TRUE.equals(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).get(DexComponents.COMPLETE_LV2.get()));
    }

    public static void releaseAll(ServerLevel level, ServerPlayer player) {
        HELD.remove(player.getUUID());
        CORREA.remove(player.getUUID());
    }

    // ------------------------------------------------------------------ la correa y la sangre (dex_biblia: contagio)

    /** Los Kagami: misma arquitectura de Rider System, linea paterna. */
    public static boolean isFamily(KamenRide k) {
        return k == KamenRide.CIPHER || k == KamenRide.WARDEN || k == KamenRide.PSYGA || k == KamenRide.ORGA || k == KamenRide.KAIXA;
    }

    /** Progreso del contagio (ticks). 0-160 eco, 160-500 arrastre, 500-900 bucle; a los 900 la correa te consume. */
    private static final java.util.Map<java.util.UUID, Integer> CORREA = new java.util.HashMap<>();
    public static final int ECHO_END = 160, DRAG_END = 500, LOOP_END = 900;

    public static int correa(Player p) { return CORREA.getOrDefault(p.getUUID(), -1); }
    public static boolean isDragged(Player p) { return correa(p) >= ECHO_END; }
    public static boolean inLoop(Player p) { return correa(p) >= DRAG_END; }

    private static boolean familyPresent(ServerPlayer p, java.util.Set<KamenRide> set) {
        for (KamenRide k : set) if (isFamily(k) && AllyManager.nearbyFriend(p, k, 48) != null) return true;
        return false;
    }

    /** Un golpe que te deja bajo el 30 % con un familiar presente despierta la correa, y viaja por la sangre. */
    public static void onHurt(ServerLevel level, ServerPlayer p, float healthAfter) {
        if (KamenRide.current(p) != KamenRide.COMPLETE || CORREA.containsKey(p.getUUID())) return;
        var set = HELD.get(p.getUUID());
        if (set == null || healthAfter > p.getMaxHealth() * 0.3F || !familyPresent(p, set)) return;
        CORREA.put(p.getUUID(), 0);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 0.8F);
        p.sendSystemMessage(Component.translatable("message.dexrider.correa_echo").withStyle(ChatFormatting.DARK_PURPLE));
    }

    private static void correaTick(ServerLevel level, ServerPlayer p, java.util.Set<KamenRide> set) {
        Integer prog = CORREA.get(p.getUUID());
        if (prog == null) return;
        if (!familyPresent(p, set)) { endCorrea(p, true); return; }
        // los amigos son toma de tierra: con alguno presente, el contagio avanza a la mitad
        boolean ground = false;
        for (KamenRide k : set) if (!isFamily(k) && AllyManager.nearbyFriend(p, k, 48) != null) ground = true;
        int next = prog + (ground ? (p.tickCount % 2) : 1);
        CORREA.put(p.getUUID(), next);
        if (p.tickCount % 4 == 0) {
            FX.dust(level, 0x8A2BE2, 1.0F, FX.center(p), 3, 0.5);
            for (KamenRide k : set) {
                var a = AllyManager.nearbyFriend(p, k, 48);
                if (a != null && isFamily(k)) FX.line(level, FX.center(p), FX.center(a), 0x8A2BE2, 0.6F, 1.2);
            }
        }
        if (prog < ECHO_END && next >= ECHO_END) p.sendSystemMessage(Component.translatable("message.dexrider.correa_drag").withStyle(ChatFormatting.DARK_PURPLE));
        if (prog < DRAG_END && next >= DRAG_END) p.sendSystemMessage(Component.translatable("message.dexrider.correa_loop").withStyle(ChatFormatting.DARK_RED));
        if (next >= ECHO_END && p.tickCount % 20 == 0) p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, 1, false, false, true));
        // palabras de salida (las que el fanfic ya fijo): Takumi "Tres", Nagisa "Sotano"
        if (p.tickCount % 20 == 0) {
            for (KamenRide k : java.util.List.copyOf(set)) {
                if ((k != KamenRide.CIPHER && k != KamenRide.PSYGA) || p.getRandom().nextInt(25) != 0) continue;
                var a = AllyManager.nearbyFriend(p, k, 48);
                if (a == null) continue;
                RiderDialogue.sayRaw(level, a, k, Component.translatable("exitword.dexrider." + k.id()));
                set.remove(k);
                FX.ring(level, a, k.color, 1.0, 4);
                checkCorreaEnd(p, set);
            }
        }
        if (next >= LOOP_END) {
            CORREA.remove(p.getUUID());
            p.sendSystemMessage(Component.translatable("message.dexrider.correa_consumed").withStyle(ChatFormatting.DARK_RED));
            p.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
            exit(level, p, true);
        }
    }

    private static void checkCorreaEnd(ServerPlayer p, java.util.Set<KamenRide> set) {
        if (!CORREA.containsKey(p.getUUID())) return;
        for (KamenRide k : set) if (isFamily(k)) return;
        endCorrea(p, false);
    }

    private static void endCorrea(ServerPlayer p, boolean quiet) {
        if (CORREA.remove(p.getUUID()) != null && !quiet)
            p.sendSystemMessage(Component.translatable("message.dexrider.correa_cut").withStyle(ChatFormatting.AQUA));
    }

    /** Para pruebas: empuja el contagio. */
    public static void forceCorrea(ServerPlayer p, int ticks) { CORREA.put(p.getUUID(), ticks); }
}
