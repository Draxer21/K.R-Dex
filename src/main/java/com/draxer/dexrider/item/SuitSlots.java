package com.draxer.dexrider.item;

import com.draxer.dexrider.registry.DexComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Las ranuras de disenos del Driver (5) y cual esta puesto. */
public record SuitSlots(List<SuitProfile> slots, int active) {

    public static final int COUNT = 5;

    public static final SuitSlots DEFAULT = new SuitSlots(Collections.nCopies(COUNT, SuitProfile.DEFAULT), 0);

    public static final Codec<SuitSlots> CODEC = RecordCodecBuilder.create(i -> i.group(
            SuitProfile.CODEC.listOf().optionalFieldOf("slots", List.of()).forGetter(SuitSlots::slots),
            Codec.INT.optionalFieldOf("active", 0).forGetter(SuitSlots::active)
    ).apply(i, SuitSlots::new));

    public static final StreamCodec<ByteBuf, SuitSlots> STREAM_CODEC = StreamCodec.composite(
            SuitProfile.STREAM_CODEC.apply(ByteBufCodecs.list(COUNT)), SuitSlots::slots,
            ByteBufCodecs.VAR_INT, SuitSlots::active,
            SuitSlots::new);

    /** Siempre COUNT ranuras, la activa dentro de rango y todo saneado. */
    public SuitSlots normalized() {
        List<SuitProfile> s = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) s.add(i < slots.size() && slots.get(i) != null ? slots.get(i).sanitized() : SuitProfile.DEFAULT);
        return new SuitSlots(List.copyOf(s), Math.floorMod(active, COUNT));
    }

    public SuitProfile current() {
        return normalized().slots.get(Math.floorMod(active, COUNT));
    }

    public SuitSlots with(int index, SuitProfile p) {
        List<SuitProfile> s = new ArrayList<>(normalized().slots);
        s.set(Math.floorMod(index, COUNT), p);
        return new SuitSlots(List.copyOf(s), active);
    }

    public SuitSlots withActive(int index) {
        return new SuitSlots(normalized().slots, Math.floorMod(index, COUNT));
    }

    /** Las ranuras de un Driver (si es de antes de 0.33, su traje pasa a la primera ranura). */
    public static SuitSlots of(ItemStack driver) {
        if (driver == null || driver.isEmpty()) return DEFAULT;
        SuitSlots s = driver.get(DexComponents.SUIT_SLOTS.get());
        if (s != null) return s.normalized();
        SuitConfig legacy = driver.get(DexComponents.SUIT.get());
        return legacy == null ? DEFAULT : DEFAULT.with(0, SuitProfile.fromLegacy(legacy));
    }
}
