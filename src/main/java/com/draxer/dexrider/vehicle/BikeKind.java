package com.draxer.dexrider.vehicle;

import com.draxer.dexrider.item.DexCard;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Motos del Vehicle Ride. Cada Rider trae la suya (en el fanfic, los portadores del Driver tambien dan Vehicle Ride);
 * aqui te la dejan como carta cuando vuelven a su mundo por el portal.
 *
 *  speed   : velocidad de conduccion
 *  R mientras conduces: habilidad de la moto (BikeAbilities)
 */
public enum BikeKind {
    DEX_MACHINE(DexCard.VEHICLE_RIDE_DEX_MACHINE, 0.55F, 0xCD1C28),
    NINE_STRIKER(DexCard.VEHICLE_RIDE_NINE_STRIKER, 0.60F, 0x2EA046),
    CIPHER_RUNNER(DexCard.VEHICLE_RIDE_CIPHER_RUNNER, 0.62F, 0x1E2C5C),
    MACHINE_FORGER(DexCard.VEHICLE_RIDE_MACHINE_FORGER, 0.52F, 0x7A1220),
    MYCO_HURRICANE(DexCard.VEHICLE_RIDE_MYCO_HURRICANE, 0.55F, 0xBCA8D6),
    MIRROR_SHOOTER(DexCard.VEHICLE_RIDE_MIRROR_SHOOTER, 0.64F, 0xDCE4F0),
    PROTO_RACER(DexCard.VEHICLE_RIDE_PROTO_RACER, 0.58F, 0xFF8418),
    WARDEN_INTERCEPTOR(DexCard.VEHICLE_RIDE_WARDEN_INTERCEPTOR, 0.66F, 0x2E3038),
    PSYGA_GLIDER(DexCard.VEHICLE_RIDE_PSYGA_GLIDER, 0.58F, 0x5AAAF0),
    ORGA_MONARCH(DexCard.VEHICLE_RIDE_ORGA_MONARCH, 0.54F, 0xF2C230);

    public final DexCard card;
    public final float speed;
    public final int color;

    BikeKind(DexCard card, float speed, int color) {
        this.card = card;
        this.speed = speed;
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("entity.dexrider." + id());
    }

    /** La moto que cada Rider te deja al volver a su mundo. */
    public static BikeKind forRider(com.draxer.dexrider.rider.KamenRide rider) {
        return switch (rider) {
            case NINE -> NINE_STRIKER;
            case CIPHER -> CIPHER_RUNNER;
            case FORGE -> MACHINE_FORGER;
            case SPORE -> MYCO_HURRICANE;
            case MIRAGE -> MIRROR_SHOOTER;
            case PROTO -> PROTO_RACER;
            case WARDEN -> WARDEN_INTERCEPTOR;
            case PSYGA -> PSYGA_GLIDER;
            case ORGA -> ORGA_MONARCH;
            default -> null;
        };
    }

    public static BikeKind byCard(DexCard card) {
        for (BikeKind k : values()) if (k.card == card) return k;
        return null;
    }
}
