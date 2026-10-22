package com.draxer.dexrider.client;

import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.DriverDeck;
import com.draxer.dexrider.network.CompleteFormPayload;
import com.draxer.dexrider.rider.KTouch;
import com.draxer.dexrider.rider.KamenRide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla del K-Touch: nueve pads en anillo y el de Dex en el centro.
 * Pads encendidos = Kamen Ride que tienes; apagados = hueco que falta.
 * Sin uso parcial: hay que pulsar los diez (incluido Dex) para entrar en la Complete Form.
 */
public class KTouchScreen extends Screen {

    private static final int RADIUS = 58, PAD = 22;

    private final List<Button> pads = new ArrayList<>();
    private final boolean[] pressed = new boolean[10];   // 0..8 anillo, 9 = Dex (centro)
    private final boolean[] lit = new boolean[10];
    private int cx, cy, missing;

    public KTouchScreen() {
        super(Component.translatable("screen.dexrider.ktouch"));
    }

    @Override
    protected void init() {
        pads.clear();
        cx = this.width / 2;
        cy = this.height / 2 + 6;
        DriverDeck deck = Minecraft.getInstance().player == null ? DriverDeck.EMPTY
                : DexDriverItem.getDeck(DexDriverItem.findDriver(Minecraft.getInstance().player));
        int count = KTouch.kamenRideCount(deck);
        missing = Math.max(0, KTouch.COMPLETE_AT - count);

        for (int i = 0; i < 10; i++) {
            final int idx = i;
            int x, y;
            KamenRide k;
            if (i < 9) {
                double a = Math.toRadians(-90 + i * 40);
                x = cx + (int) Math.round(Math.cos(a) * RADIUS) - PAD / 2;
                y = cy + (int) Math.round(Math.sin(a) * RADIUS) - PAD / 2;
                k = KamenRide.RING[i];
                lit[i] = k.card != null && deck.has(k.card);
            } else {
                x = cx - PAD / 2;
                y = cy - PAD / 2;
                k = KamenRide.DEX;
                lit[i] = true;
            }
            String letter = k.displayName().getString().substring(0, 1);
            Button b = Button.builder(Component.literal(letter), btn -> press(idx)).bounds(x, y, PAD, PAD)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(k.displayName())).build();
            b.active = lit[i];
            pads.add(addRenderableWidget(b));
        }
    }

    private void press(int idx) {
        pressed[idx] = true;
        pads.get(idx).active = false;
        for (int i = 0; i < 10; i++) if (!pressed[i]) return;
        ClientPacketDistributor.sendToServer(CompleteFormPayload.INSTANCE);
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // carcasa perla con borde dorado y panel oscuro
        int r = RADIUS + PAD;
        g.fill(cx - r - 6, cy - r - 22, cx + r + 6, cy + r + 26, 0xFFC8A030);
        g.fill(cx - r - 4, cy - r - 20, cx + r + 4, cy + r + 24, 0xFFECE8E2);
        g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF16141C);
        for (int i = 0; i < 4; i++) {                                   // aletas rojas
            g.fill(cx - r - 12, cy - 30 + i * 16, cx - r - 4, cy - 22 + i * 16, 0xFFB01020);
            g.fill(cx + r + 4, cy - 30 + i * 16, cx + r + 12, cy - 22 + i * 16, 0xFFB01020);
        }
        g.text(this.font, this.title, cx - this.font.width(this.title) / 2, cy - r - 15, 0xFF8A1020, false);

        // brillo de cada pad: blanco encendido, dorado pulsado, rojo y dorado el de Dex, negro apagado
        for (int i = 0; i < pads.size(); i++) {
            Button b = pads.get(i);
            int col = !lit[i] ? 0xFF050505 : pressed[i] ? 0xFFF2C230 : i == 9 ? 0xFFCD1C28 : 0xFFF4F4FA;
            g.fill(b.getX() - 2, b.getY() - 2, b.getX() + b.getWidth() + 2, b.getY() + b.getHeight() + 2, col);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        Component hint = missing > 0
                ? Component.translatable("screen.dexrider.ktouch.missing", missing)
                : Component.translatable("screen.dexrider.ktouch.hint");
        g.text(this.font, hint, cx - this.font.width(hint) / 2, cy + r + 10, missing > 0 ? 0xFF8A1020 : 0xFF303030, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
