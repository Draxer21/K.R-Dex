package com.draxer.dexrider.rider;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Formas de prueba de Forge (idea de Build: un traje de dos mitades que se cambian por separado).
 * EXCLUSIVAS DE MOMO: solo las usa la Forge del juego (perdida o amiga); el jugador usa la Forge base.
 *
 *  Mitad DERECHA "herramienta": GEAR engranaje (base) · CANNON canon (disparo pesado) · SHIELD escudo (absorcion, rechazo)
 *  Mitad IZQUIERDA "materia"  : CARBON carbono (base) · MAGNESIUM magnesio (bengala) · FIBER fibra (telaranas)
 *
 *  SINTONIA (mitades que se potencian): Engranaje+Carbono (Forge base), Canon+Magnesio (FLARE), Escudo+Fibra (BASTION).
 *  En sintonia sus ataques pegan mas y usa las dos mitades a la vez.
 *  Forma = derecha * 3 + izquierda (0 = base). Modelo: rider_forge o rider_forge_<derecha>_<izquierda>.
 */
public final class ForgeForms {
    private ForgeForms() {}

    public enum Right { GEAR, CANNON, SHIELD }
    public enum Left { CARBON, MAGNESIUM, FIBER }

    public static int encode(Right r, Left l) { return r.ordinal() * 3 + l.ordinal(); }
    public static Right right(int form) { return Right.values()[Math.floorMod(form, 9) / 3]; }
    public static Left left(int form) { return Left.values()[Math.floorMod(form, 9) % 3]; }
    public static boolean sync(int form) { return right(form).ordinal() == left(form).ordinal(); }

    public static String model(int form) {
        if (Math.floorMod(form, 9) == 0) return "rider_forge";
        return "rider_forge_" + right(form).name().toLowerCase(Locale.ROOT) + "_" + left(form).name().toLowerCase(Locale.ROOT);
    }

    public static Component formName(int form) {
        if (sync(form)) return Component.translatable("forge.dexrider.sync." + right(form).name().toLowerCase(Locale.ROOT));
        return Component.translatable("forge.dexrider.trial",
                Component.translatable("forge.dexrider.half." + right(form).name().toLowerCase(Locale.ROOT)),
                Component.translatable("forge.dexrider.half." + left(form).name().toLowerCase(Locale.ROOT)));
    }

    public static int rightColor(Right r) {
        return switch (r) { case GEAR -> 0xA0222F; case CANNON -> 0xDE8430; case SHIELD -> 0x5480C2; };
    }

    public static int leftColor(Left l) {
        return switch (l) { case CARBON -> 0x96D2FF; case MAGNESIUM -> 0xFFF8C8; case FIBER -> 0x6EC8A0; };
    }
}
