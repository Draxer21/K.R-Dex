package com.draxer.dexrider.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Cartas guardadas en el Ride Booker (componente del Driver): lista de (ranura, carta). */
public record BookerContents(List<Entry> entries) {

    public record Entry(int slot, ItemStack stack) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("slot").forGetter(Entry::slot),
                ItemStack.CODEC.fieldOf("item").forGetter(Entry::stack)
        ).apply(i, Entry::new));
    }

    public static final BookerContents EMPTY = new BookerContents(List.of());

    public static final Codec<BookerContents> CODEC =
            Entry.CODEC.listOf().xmap(BookerContents::new, BookerContents::entries);

    public BookerContents {
        entries = List.copyOf(entries);
    }

    public static BookerContents of(NonNullList<ItemStack> items) {
        List<Entry> list = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) list.add(new Entry(i, items.get(i).copy()));
        }
        return new BookerContents(list);
    }

    public void copyInto(NonNullList<ItemStack> items) {
        for (Entry e : entries) {
            if (e.slot() >= 0 && e.slot() < items.size()) items.set(e.slot(), e.stack().copy());
        }
    }
}
