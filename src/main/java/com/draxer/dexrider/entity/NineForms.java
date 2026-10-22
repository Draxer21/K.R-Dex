package com.draxer.dexrider.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * Formas de Izuku como Kamen Rider Nine (del fanfic). EXCLUSIVAS DE IZUKU: solo las usa el Nine del juego
 * (perdido o amigo); el jugador con Kamen Ride: Nine usa la forma base.
 *
 *  BASE    verde   : equilibrado, Smash.
 *  DRAGON  azul    : agil, salta muy alto; Dragon Rod (baston de barrido).
 *  PEGASUS plateado: sentidos agudizados; Pegasus Bow (disparos de precision a distancia). Su final es un disparo.
 *  TITAN   morado  : lento y muy resistente; Titan Blades (tajos pesados).
 *
 *  RISING (One For All, Full Cowl): ~30 s con acentos dorados. Pega x1.5, sus finales explotan mucho mas grande
 *  y, si fallan, ROMPEN BLOQUES (respeta la regla mobGriefing). Despues necesita descansar antes de repetirlo.
 *
 *  Forma = indice + 4 si esta en Rising. Modelo: rider_nine[_dragon|_pegasus|_titan][_rising].
 */
public final class NineForms {
    private NineForms() {}

    public enum Form { BASE, DRAGON, PEGASUS, TITAN }

    public static int encode(Form f, boolean rising) { return f.ordinal() + (rising ? 4 : 0); }
    public static Form form(int v) { return Form.values()[v & 3]; }
    public static boolean rising(int v) { return (v & 4) != 0; }

    public static String model(int v) {
        Form f = form(v);
        return "rider_nine" + (f == Form.BASE ? "" : "_" + f.name().toLowerCase(Locale.ROOT)) + (rising(v) ? "_rising" : "");
    }

    public static Component formName(int v) {
        Component base = Component.translatable("nine.dexrider.form." + form(v).name().toLowerCase(Locale.ROOT));
        return rising(v) ? Component.translatable("nine.dexrider.rising", base) : base;
    }

    public static int color(int v) {
        if (rising(v)) return 0xF2C230;
        return switch (form(v)) {
            case BASE -> 0x2EA046;
            case DRAGON -> 0x3C78E6;
            case PEGASUS -> 0xC8CEDC;
            case TITAN -> 0x8C3CD2;
        };
    }

    /** Multiplicador de dano de esta forma. */
    public static float scale(int v) {
        float s = switch (form(v)) { case TITAN -> 1.2F; case DRAGON -> 0.85F; default -> 1.0F; };
        return rising(v) ? s * 1.5F : s;
    }

    /** Efectos de la forma (se renuevan cada segundo). */
    public static void effects(LivingEntity e, int v) {
        switch (form(v)) {
            case DRAGON -> {
                e.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 1, false, false));
                e.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 40, 2, false, false));
            }
            case TITAN -> {
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, false, false));
                e.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 1, false, false));
            }
            default -> { }
        }
        if (rising(v)) {
            e.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, 1, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, form(v) == Form.DRAGON ? 2 : 0, false, false));
        }
    }

    /** Que forma le conviene contra este objetivo. */
    public static Form choose(RiderMob self, LivingEntity target, Form current) {
        double dist = self.distanceTo(target);
        boolean high = target.getY() - self.getY() > 2.5 || !target.onGround();
        float r = self.getRandom().nextFloat();
        Form pick;
        if (self.getHealth() < self.getMaxHealth() * 0.4F && r < 0.6F) pick = Form.TITAN;
        else if (dist > 11 && r < 0.7F) pick = Form.PEGASUS;
        else if (high && r < 0.7F) pick = Form.DRAGON;
        else pick = Form.values()[self.getRandom().nextInt(4)];
        return pick;
    }
}
