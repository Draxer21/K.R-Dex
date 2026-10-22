package com.draxer.dexrider.network;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.item.BookerContents;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.rider.CipherModes;
import com.draxer.dexrider.rider.DexEmotions;
import com.draxer.dexrider.rider.FormAbilities;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Servidor -> cliente: datos del HUD del Rider (cada 5 ticks mientras estas transformado).
 * card = id del objeto de la carta elegida en el Ride Booker ("" si no hay); slot = mazo * 18 + ranura.
 */
public record HudSyncPayload(boolean active, String card, int slot, int abilityLeft, int abilityTotal,
                             int accelLeft, String emotion, int emotionLeft, int emotionCooldown)
        implements CustomPacketPayload {

    public static final HudSyncPayload EMPTY = new HudSyncPayload(false, "", 0, 0, 1, 0, "", 0, 0);

    public static final CustomPacketPayload.Type<HudSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(DexRider.id("hud_sync"));

    public static final StreamCodec<ByteBuf, HudSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.BOOL.encode(buf, p.active);
                ByteBufCodecs.STRING_UTF8.encode(buf, p.card);
                ByteBufCodecs.VAR_INT.encode(buf, p.slot);
                ByteBufCodecs.VAR_INT.encode(buf, p.abilityLeft);
                ByteBufCodecs.VAR_INT.encode(buf, p.abilityTotal);
                ByteBufCodecs.VAR_INT.encode(buf, p.accelLeft);
                ByteBufCodecs.STRING_UTF8.encode(buf, p.emotion);
                ByteBufCodecs.VAR_INT.encode(buf, p.emotionLeft);
                ByteBufCodecs.VAR_INT.encode(buf, p.emotionCooldown);
            },
            buf -> new HudSyncPayload(ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(HudSyncPayload payload, IPayloadContext context) {
        HudState.last = payload;
        HudState.receivedAt = System.currentTimeMillis();
    }

    private static final java.util.Set<java.util.UUID> ACTIVE = new java.util.HashSet<>();

    public static void send(ServerLevel level, ServerPlayer p) {
        ACTIVE.add(p.getUUID());
        long now = level.getGameTime();
        String card = "";
        int sel = 0;
        ItemStack driver = DexDriverItem.findDriver(p);
        if (!driver.isEmpty()) {
            Integer s = driver.get(DexComponents.BOOKER_SEL.get());
            BookerContents saved = driver.get(DexComponents.BOOKER.get());
            sel = s == null ? 0 : Math.max(0, s);
            if (saved != null) {
                NonNullList<ItemStack> items = NonNullList.withSize(com.draxer.dexrider.menu.RideBookerMenu.SIZE, ItemStack.EMPTY);
                saved.copyInto(items);
                sel = Math.floorMod(sel, items.size());
                ItemStack c = items.get(sel);
                if (!c.isEmpty()) card = BuiltInRegistries.ITEM.getKey(c.getItem()).toString();
            }
        }
        long[] cd = FormAbilities.cooldown(p, now);
        String emotion = DexEmotions.current(p);
        PacketDistributor.sendToPlayer(p, new HudSyncPayload(true, card, sel, (int) cd[0], (int) cd[1],
                (int) CipherModes.accelLeft(p, now), emotion == null ? "" : emotion,
                (int) DexEmotions.remaining(p, now), (int) DexEmotions.cooldownLeft(p, now)));
    }

    public static void clear(ServerPlayer p) {
        if (ACTIVE.remove(p.getUUID())) PacketDistributor.sendToPlayer(p, EMPTY);
    }
}
