package com.draxer.dexrider.registry;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.item.AllyBook;
import com.draxer.dexrider.item.DriverDeck;
import com.draxer.dexrider.item.SuitConfig;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Datos que se guardan dentro de los ItemStack del mod. */
public final class DexComponents {
    private DexComponents() {}

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, DexRider.MODID);

    /** Carta Kamen Ride cargada en el Driver (por ahora solo "kamen_ride_dex"). */
    public static final Supplier<DataComponentType<String>> LOADED_CARD =
            COMPONENTS.registerComponentType("loaded_card", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** Mazo del Driver: cartas generadas + muestras de escaneo a medias. */
    /** Cartas guardadas en el Ride Booker (vive en el Driver). */
    public static final Supplier<DataComponentType<com.draxer.dexrider.item.BookerContents>> BOOKER =
            COMPONENTS.registerComponentType("ride_booker", builder -> builder
                    .persistent(com.draxer.dexrider.item.BookerContents.CODEC));

    /** Carta elegida en el Ride Booker para usarla rapido (mazo * 18 + ranura). */
    public static final Supplier<DataComponentType<Integer>> BOOKER_SEL =
            COMPONENTS.registerComponentType("ride_booker_sel", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Emotion activa de Dex: "violent" o "heroic" (en el peto, para que todos vean el traje). */
    public static final Supplier<DataComponentType<String>> EMOTION =
            COMPONENTS.registerComponentType("emotion", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** Modo de Cipher activo: "accel" o "blaster" (en el peto, para que todos vean el modelo). */
    public static final Supplier<DataComponentType<String>> CIPHER_MODE =
            COMPONENTS.registerComponentType("cipher_mode", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    public static final Supplier<DataComponentType<DriverDeck>> DECK =
            COMPONENTS.registerComponentType("deck", builder -> builder
                    .persistent(DriverDeck.CODEC)
                    .networkSynchronized(DriverDeck.STREAM_CODEC));

    /** Form Ride activa, guardada en el peto de Dex ("speed", "gas"...). Sin componente = Dex Form base. */
    public static final Supplier<DataComponentType<String>> FORM =
            COMPONENTS.registerComponentType("form", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** Personalizacion del traje (en el Driver, y copiada al peto mientras estas transformado). */
    public static final Supplier<DataComponentType<SuitConfig>> SUIT =
            COMPONENTS.registerComponentType("suit", builder -> builder
                    .persistent(SuitConfig.CODEC)
                    .networkSynchronized(SuitConfig.STREAM_CODEC));

    /** Identidad de Rider activa, guardada en el peto ("nine", "cipher", "complete"...). Sin componente = Dex. */
    public static final Supplier<DataComponentType<String>> RIDER =
            COMPONENTS.registerComponentType("rider", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** El Driver ya forjo el K-Touch (para no darlo dos veces). */
    public static final Supplier<DataComponentType<Boolean>> KTOUCH_FORGED =
            COMPONENTS.registerComponentType("ktouch_forged", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    /** Amigos Rider: version, descanso, invocacion activa y casa de cada uno (en el Driver). */
    public static final Supplier<DataComponentType<AllyBook>> ALLIES =
            COMPONENTS.registerComponentType("allies", builder -> builder
                    .persistent(AllyBook.CODEC)
                    .networkSynchronized(AllyBook.STREAM_CODEC));

    /** Modulo cargado en el Module Driver (0 taladro, 1 gancho, 2 soplete). */
    public static final Supplier<DataComponentType<Integer>> MODULE =
            COMPONENTS.registerComponentType("module", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Modo del Ride Booker: false = espada, true = pistola. */
    public static final Supplier<DataComponentType<Boolean>> GUN_MODE =
            COMPONENTS.registerComponentType("gun_mode", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));
}
