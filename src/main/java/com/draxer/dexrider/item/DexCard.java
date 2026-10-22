package com.draxer.dexrider.item;

import java.util.Locale;

/**
 * Todas las cartas del mod en un solo lugar.
 *
 * samplesToScan: cuantas veces tiene que ver el Driver ese poder "en uso real" para generar la carta.
 *   0 = no se escanea (viene con el Driver o se consigue de otra forma).
 *   Regla del fanfic: cuanto mas complejo el poder, mas muestras necesita.
 */
public enum DexCard {
    KAMEN_RIDE_DEX(Family.KAMEN_RIDE, 0),
    // Kamen Ride de otros Riders: se consiguen venciendo a su eco en un combate real (no se escanean)
    KAMEN_RIDE_NINE(Family.KAMEN_RIDE, 0),
    KAMEN_RIDE_FORGE(Family.KAMEN_RIDE, 0),
    KAMEN_RIDE_PSYGA(Family.KAMEN_RIDE, 0),
    KAMEN_RIDE_CIPHER(Family.KAMEN_RIDE, 0),
    KAMEN_RIDE_WARDEN(Family.KAMEN_RIDE, 0),
    KAMEN_RIDE_ORGA(Family.KAMEN_RIDE, 0),
    KAMEN_RIDE_SPORE(Family.KAMEN_RIDE, 0),
    KAMEN_RIDE_MIRAGE(Family.KAMEN_RIDE, 0),
    KAMEN_RIDE_PROTO(Family.KAMEN_RIDE, 0),
    ATTACK_RIDE_SLASH(Family.ATTACK_RIDE, 0),
    // Attack Ride del fanfic: suben de nivel re-escaneando
    ATTACK_RIDE_OVERCLOCK(Family.ATTACK_RIDE, 3),     // ver a un enderman pelear, o a Cipher en Accel
    ATTACK_RIDE_GALE(Family.ATTACK_RIDE, 2),          // ver atacar a un breeze (o una carga de viento)
    ATTACK_RIDE_RAZOR_FEATHER(Family.ATTACK_RIDE, 2), // ver atacar a un vex
    ATTACK_RIDE_NITRO_BURST(Family.ATTACK_RIDE, 1),   // ver una explosion (creeper, TNT)
    ATTACK_RIDE_GROUND_WAVE(Family.ATTACK_RIDE, 2),   // ver un golpe de maza o el rugido sonico del warden
    FINAL_ATTACK_RIDE_DEX(Family.FINAL_ATTACK_RIDE, 0),

    ARMED_RIDE_RIDE_BOOKER(Family.ARMED_RIDE, 0),
    ARMED_RIDE_SWORD(Family.ARMED_RIDE, 1),        // ver a alguien atacar con espada
    ARMED_RIDE_BLAST_SHOT(Family.ARMED_RIDE, 2),   // ver disparar un arco o ballesta
    ARMED_RIDE_SHOCK_BATON(Family.ARMED_RIDE, 2),  // ver un tridente en combate o un rayo
    ARMED_RIDE_VOLT_CHAIN(Family.ARMED_RIDE, 3),   // ver el laser de un guardian
    ARMED_RIDE_POWER_KNUCKLE(Family.ARMED_RIDE, 3),// ver golpear a un golem, ravager o warden
    // armas de los Riders: se escanean viendolas en uso real (el duenio peleando con ella)
    ARMED_RIDE_CIPHER_EDGE(Family.ARMED_RIDE, 2),
    ARMED_RIDE_PRISM_EDGE(Family.ARMED_RIDE, 2),
    ARMED_RIDE_MODULE_DRIVER(Family.ARMED_RIDE, 2),
    ARMED_RIDE_FORGE_LANCE(Family.ARMED_RIDE, 2),
    ARMED_RIDE_WARDEN_BATON(Family.ARMED_RIDE, 2),
    ARMED_RIDE_ORGA_HALBERD(Family.ARMED_RIDE, 3),
    ARMED_RIDE_SPORE_ROD(Family.ARMED_RIDE, 2),

    // Form Ride: una sola forma activa; suben de nivel (1-3) re-escaneando
    FORM_RIDE_SPEED(Family.FORM_RIDE, 2),          // un caballo (o similar) galopando con jinete
    FORM_RIDE_GAS(Family.FORM_RIDE, 2),            // un ghast atacando
    FORM_RIDE_TERRA(Family.FORM_RIDE, 2),          // algo excavando o emergiendo de la tierra (warden, sniffer)
    FORM_RIDE_SKY(Family.FORM_RIDE, 2),            // un phantom en picada
    FORM_RIDE_FROST(Family.FORM_RIDE, 2),          // un stray o un golem de nieve atacando
    FORM_RIDE_CINDER(Family.FORM_RIDE, 2),         // un blaze atacando
    FORM_RIDE_HALF(Family.FORM_RIDE, 2),           // con Frost y Cinder: ver fuego y hielo atacando casi a la vez

    // Vehicle Ride
    VEHICLE_RIDE_DEX_MACHINE(Family.VEHICLE_RIDE, 2), // alguien montado en un vehiculo en marcha
    // motos de los demas Riders: te las dejan al volver a su mundo por el portal
    VEHICLE_RIDE_NINE_STRIKER(Family.VEHICLE_RIDE, 0),
    VEHICLE_RIDE_CIPHER_RUNNER(Family.VEHICLE_RIDE, 0),
    VEHICLE_RIDE_MACHINE_FORGER(Family.VEHICLE_RIDE, 0),
    VEHICLE_RIDE_MYCO_HURRICANE(Family.VEHICLE_RIDE, 0),
    VEHICLE_RIDE_MIRROR_SHOOTER(Family.VEHICLE_RIDE, 0),
    VEHICLE_RIDE_PROTO_RACER(Family.VEHICLE_RIDE, 0),
    VEHICLE_RIDE_WARDEN_INTERCEPTOR(Family.VEHICLE_RIDE, 0),
    VEHICLE_RIDE_PSYGA_GLIDER(Family.VEHICLE_RIDE, 0),
    VEHICLE_RIDE_ORGA_MONARCH(Family.VEHICLE_RIDE, 0);

    public enum Family { KAMEN_RIDE, FORM_RIDE, ATTACK_RIDE, ARMED_RIDE, VEHICLE_RIDE, FINAL_ATTACK_RIDE }

    private final Family family;
    private final int samplesToScan;

    DexCard(Family family, int samplesToScan) {
        this.family = family;
        this.samplesToScan = samplesToScan;
    }

    public Family family() { return family; }
    public int samplesToScan() { return samplesToScan; }

    /** "armed_ride_sword", etc. Se usa en ids de item y en el mazo guardado. */
    public String id() { return name().toLowerCase(Locale.ROOT); }

    public String itemName() { return "card_" + id(); }

    public String translationKey() { return "item.dexrider." + itemName(); }

    public static DexCard byId(String id) {
        for (DexCard c : values()) if (c.id().equals(id)) return c;
        return null;
    }
}
