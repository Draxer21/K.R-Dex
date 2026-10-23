package com.draxer.dexrider.rider;

import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.DexWeaponItem;
import com.draxer.dexrider.item.DriverDeck;
import com.draxer.dexrider.registry.DexItems;
import com.draxer.dexrider.vehicle.DexMachineEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.world.phys.Vec3;

/** Eventos del juego (bus de NeoForge). Todos son metodos estaticos. */
public final class DexEvents {
    private DexEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        RiderKick.tick(level, player);
        Guard.tick(player);
        if (player.tickCount % 100 == 0) AllyManager.tickPlayer(player);
        if (player.tickCount % 100 == 50) DaiShocker.tickPlayer(player);
        AllyManager.tickReunion(player);
        Scheduler.tick(player);

        if (Transformation.isTransformed(player)) {
            if (player.tickCount % 20 == 0) {
                Transformation.applyFormEffects(player);
            }
            FormRide.tick(level, player);
            CipherModes.tick(level, player);
            LentDriver.tick(player);
            DexEmotions.tick(level, player);
            if (player.tickCount % 5 == 0) com.draxer.dexrider.network.HudSyncPayload.send(level, player);
            if (player.tickCount % 5 == 2) CombatRead.sync(level, player);
            KamenRide identity = KamenRide.current(player);
            if (identity != KamenRide.DEX) RiderPowers.tick(level, player, identity);
            if (player.tickCount % 200 == 0) KTouch.retroCheck(player);
            if (player.tickCount % 10 == 0) Scanner.observe(level, player);
        } else if (player.tickCount % 5 == 0) {
            FormRide.revokeFlight(player);
            Transformation.removeSuitBody(player);   // sin traje no hay vida extra
            if (player.tickCount % 20 == 0) com.draxer.dexrider.network.HudSyncPayload.clear(player);
            if (player.tickCount % 20 == 0) CombatRead.clear(player);
            // Set incompleto (creativo, comandos...): limpiar y devolver la armadura original.
            if (hasStrayDexArmor(player)) Transformation.detransform(player, false);
            // Las armas solo existen transformado.
            else if (ArmedRide.hasWeapon(player)) ArmedRide.removeWeapons(player);
        }
    }

    /**
     * Antes de aplicar cualquier dano:
     *  1. Escalado del mazo si el atacante es Dex (+15 % por carta de la familia).
     *  2. Escaneo: si un Dex cercano esta mirando, el Driver toma una muestra.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;

        // Fuego amigo: ni el duenio dana a sus amigos Rider ni ellos a el (ni entre ellos)
        if (victim instanceof com.draxer.dexrider.entity.RiderMob npcVictim && event.getSource().getEntity() instanceof LivingEntity src
                && npcVictim.isFriendlyTo(src)) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().getEntity() instanceof com.draxer.dexrider.entity.RiderMob npcAttacker && npcAttacker.isFriendlyTo(victim)) {
            event.setCanceled(true);
            return;
        }

        // Configuracion: dano de los Riders del juego
        if (event.getSource().getEntity() instanceof com.draxer.dexrider.entity.RiderMob) {
            event.setAmount(event.getAmount() * com.draxer.dexrider.config.DexConfig.npcDamage());
        }
        // Heroic Emotion: alguien a quien Dex protege recibe un golpe
        DexEmotions.onProtectedHurt(level, victim, event.getSource());

        // Tonos: Orga y Kaixa esquivan la carta que ya leyeron; el senuelo los deja expuestos
        if (Tones.dodges(victim, event.getSource())) { event.setCanceled(true); return; }
        if (event.getSource().getEntity() instanceof ServerPlayer) event.setAmount(event.getAmount() * Tones.exposed(victim));

        // Un Rider perdido atacado pide ayuda a los que esten cerca
        if (victim instanceof com.draxer.dexrider.entity.RiderEchoEntity lost && event.getSource().getEntity() instanceof LivingEntity src) {
            lost.callForHelp(level, src);
        }

        // Debilidades e inmunidades de la Form Ride activa del que recibe el golpe
        if (victim instanceof ServerPlayer target && Transformation.isTransformed(target)) {
            // Parada (Shift justo a tiempo) o bloqueo (Shift mantenido) contra golpes de frente
            if (Guard.onIncoming(level, target, event)) return;
            FormRide form = FormRide.current(target);
            // El traje del Rider System absorbe parte de cada golpe
            event.setAmount(event.getAmount() * Transformation.SUIT_DAMAGE_TAKEN * DexEmotions.takenScale(target));
            KTouch.onHurt(level, target, target.getHealth() - event.getAmount());
            // Violent Emotion: Dex al borde o recibiendo una paliza de golpe
            DexEmotions.onDexHurt(level, target, event.getAmount());
            // Orga leyendo los tonos: esquiva los golpes cuerpo a cuerpo
            if (KamenRide.current(target) == KamenRide.ORGA && RiderPowers.dodge(target, event.getSource())) {
                event.setCanceled(true);
                return;
            }
            // Cuerpo de humo: nada lo toca (salvo el vacio y /kill)
            if (form == FormRide.GAS && FormAbilities.isSmoke(target)
                    && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                FX.particle(level, ParticleTypes.CLOUD, FX.center(target), 8, 0.4, 0.05);
                event.setCanceled(true);
                return;
            }
            // Las explosiones dispersan el gas: mitad de dano, pero sale volando el doble
            if (form == FormRide.GAS && event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
                event.setAmount(event.getAmount() * 0.5F);
                Vec3 from = event.getSource().getSourcePosition();
                if (from != null) FX.push(target, from, 1.4, 0.7);
            }
            if (form == FormRide.HALF && event.getSource().is(DamageTypes.FREEZE)) {
                event.setCanceled(true);
                return;
            }
            if (form == FormRide.GAS && (event.getSource().is(DamageTypeTags.IS_PROJECTILE)
                    || event.getSource().is(DamageTypeTags.IS_FALL))) {
                FX.particle(level, ParticleTypes.CLOUD, FX.center(target), 10, 0.4, 0.05);
                event.setCanceled(true);   // los proyectiles atraviesan el humo
                return;
            }
            if (form == FormRide.FROST) {
                if (event.getSource().is(DamageTypes.FREEZE)) { event.setCanceled(true); return; }
                if (event.getSource().is(DamageTypeTags.IS_FIRE)) event.setAmount(event.getAmount() * 1.5F);
            }
        }

        if (event.getSource().getEntity() instanceof ServerPlayer attacker && Transformation.isTransformed(attacker)) {
            // En cuerpo de humo no se puede atacar
            if (FormRide.current(attacker) == FormRide.GAS && FormAbilities.isSmoke(attacker)) {
                event.setCanceled(true);
                return;
            }
            ItemStack driver = DexDriverItem.findDriver(attacker);
            DriverDeck deck = DexDriverItem.getDeck(driver);
            DexCard.Family family = ArmedRide.hasWeapon(attacker) ? DexCard.Family.ARMED_RIDE : DexCard.Family.KAMEN_RIDE;
            float mult = deck.multiplier(family) * Transformation.SUIT_DAMAGE_DEALT * CipherModes.damageScale(attacker)
                    * DexEmotions.damageScale(attacker) * com.draxer.dexrider.config.DexConfig.dexDamage()
                    * (UpgradeForm.isActive(attacker) ? 1.2F : 1.0F) * ArmedRide.weaponScale(attacker);
            if (FormRide.current(attacker) != null) mult *= deck.multiplier(DexCard.Family.FORM_RIDE);
            if (KamenRide.current(attacker) == KamenRide.COMPLETE) mult *= RiderPowers.completeMultiplier(attacker);
            mult *= Guard.counterScale(attacker, victim);
            event.setAmount(event.getAmount() * mult);
            return;   // los ataques del propio Dex no se escanean
        }

        Scanner.onDamage(level, victim, event.getSource());
    }

    /** Lectura del Driver: vida antes del golpe... */
    @SubscribeEvent
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!event.getEntity().level().isClientSide()) CombatRead.before(event.getEntity());
    }

    /** ...y despues: cuanto hizo de verdad cada golpe. */
    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (!event.getEntity().level().isClientSide()) CombatRead.after(event.getEntity(), event.getSource().getEntity());
    }

    /** Efectos al golpear cuerpo a cuerpo con algunas armas. */
    @SubscribeEvent
    public static void onDamageDealt(LivingDamageEvent.Post event) {

        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getSource().getDirectEntity() != player) return;   // solo golpes directos
        if (!(player.level() instanceof ServerLevel level)) return;
        LivingEntity target = event.getEntity();

        // Golpes segun la Form Ride activa
        FormRide form = Transformation.isTransformed(player) ? FormRide.current(player) : null;
        if (form != null) {
            int lvl = FormRide.level(player, form);
            switch (form) {
                case FROST -> {
                    target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 60, target.getTicksFrozen() + 40 * lvl));
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, lvl));
                    FX.particle(level, ParticleTypes.SNOWFLAKE, FX.center(target), 15, 0.3, 0.05);
                }
                case CINDER -> {
                    target.igniteForSeconds(2.0F + 2 * lvl);
                    FX.particle(level, ParticleTypes.FLAME, FX.center(target), 15, 0.3, 0.05);
                }
                case TERRA -> FX.push(target, player.position(), 0.2, 0.35 + 0.15 * lvl);
                case HALF -> {
                    target.igniteForSeconds(2.0F + lvl);
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, lvl - 1));
                    FX.particle(level, ParticleTypes.SNOWFLAKE, FX.center(target), 8, 0.3, 0.05);
                    FX.particle(level, ParticleTypes.FLAME, FX.center(target), 8, 0.3, 0.05);
                }
                default -> { }
            }
        }

        if (!(player.getMainHandItem().getItem() instanceof DexWeaponItem weapon)) return;

        switch (weapon.kind()) {
            case SHOCK_BATON -> {
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
                FX.particle(level, ParticleTypes.ELECTRIC_SPARK, FX.center(target), 12, 0.3, 0.2);
            }
            case POWER_KNUCKLE -> {
                FX.push(target, player.position(), 0.9, 0.25);
                FX.dust(level, FX.GOLD, 1.5F, FX.center(target), 8, 0.3);
            }
            default -> RiderWeapons.onHit(level, player, weapon.kind(), target);
        }
    }

    /** Romper un bloque temporal no da nada. */
    @SubscribeEvent
    public static void onBlockBreak(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level && TempBlocks.breakTemp(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** Bloques temporales de las habilidades (muros de hielo, pilares...). */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) TempBlocks.tick(level);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TempBlocks.restoreAll(event.getServer().getAllLevels());
    }

    /** Al morir transformado, se devuelve la armadura original para que caiga normalmente. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        // Los amigos Rider no mueren: quedan agotados y se retiran a descansar
        if (event.getEntity() instanceof com.draxer.dexrider.entity.RiderAllyEntity ally) {
            event.setCanceled(true);
            ally.knockOut();
            return;
        }
        if (event.getEntity() instanceof com.draxer.dexrider.entity.RiderEchoEntity echo) {
            event.setCanceled(true);
            echo.onDefeated(event.getSource());
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            RiderKick.cancel(player);
            Scheduler.cancel(player);
            if (hasStrayDexArmor(player) || Transformation.isTransformed(player)) {
                Transformation.detransform(player, false);
            }
            ArmedRide.removeWeapons(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RiderKick.cancel(player);
            Scheduler.cancel(player);
            FormRide.revokeFlight(player);
            DexMachineEntity.dismissFor(player);
            AllyManager.cancelReunion(player);
            Guard.forget(player.getUUID());
            Combos.forget(player.getUUID());
            CombatRead.forget(player.getUUID());
        }
    }

    /** La armadura y las armas de Dex nunca pueden existir tiradas en el suelo. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity item && DexItems.isRiderBound(item.getItem().getItem())) {
            event.setCanceled(true);
        }
    }

    private static boolean hasStrayDexArmor(ServerPlayer player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (DexItems.isDexArmor(player.getItemBySlot(slot).getItem())) return true;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (DexItems.isDexArmor(stack.getItem())) return true;
        }
        return DexItems.isDexArmor(player.containerMenu.getCarried().getItem());
    }
}
