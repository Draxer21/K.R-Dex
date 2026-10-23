package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.item.AllyBook;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.DriverDeck;
import com.draxer.dexrider.network.CompleteFormPayload;
import com.draxer.dexrider.network.SummonAllyPayload;
import com.draxer.dexrider.registry.DexComponents;
import com.draxer.dexrider.registry.DexItems;
import com.draxer.dexrider.rider.KTouch;
import com.draxer.dexrider.rider.KamenRide;
import com.draxer.dexrider.rider.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla de invocar amigos Rider (tecla B) con forma de K-TOUCH: apaisado como el juguete, pero con el casco
 * de Dex (cuerpo negro con laminas rojas escalonadas = las franjas del casco, cuernos dorados en V arriba,
 * lente verde = el ojo de Dex, muesca de la carta a la izquierda). En la pantalla, los nueve blasones (3x3) y,
 * en el panel lateral derecho, el blason de DEX, que abre la Complete Form (o la cierra si ya estas en ella).
 *
 *  marco plateado = listo · dorado = peleando a tu lado · gris = descansando (con los segundos) · apagado = aun no es tu amigo.
 *  Las tres luces de la cabecera = cuantos amigos pelean contigo (maximo 3).
 *  Atajo: teclado numerico 1-9, en orden de lectura (1 arriba a la izquierda ... 9 abajo a la derecha).
 *
 * Texturas (assets/dexrider/textures/gui): ktouch_body (carcasa), ktouch_pads (marcos), ktouch_emblems (blasones
 * en color / gris / apagado), ktouch_lights.
 */
public class SummonScreen extends Screen {

    private static final Identifier BODY = DexRider.id("textures/gui/ktouch_body.png");
    private static final Identifier PADS = DexRider.id("textures/gui/ktouch_pads.png");
    private static final Identifier EMBLEMS = DexRider.id("textures/gui/ktouch_emblems.png");
    private static final Identifier LIGHTS = DexRider.id("textures/gui/ktouch_lights.png");

    private static final int W = 380, H = 220, PAD = 36, DEXPAD = 48, STEP = 42, EMB = 24, DEXEMB = 36;
    private KamenRide[] ring = KamenRide.RING;
    private static final int PADS_W = 372, PADS_H = 48, EMB_W = 264, EMB_H = 108;
    // marcos en ktouch_pads.png: cinco de 36 (Riders) y cuatro de 48 (Dex) a partir de x = 180
    private static final int P_READY = 0, P_HOVER = 1, P_ACTIVE = 2, P_REST = 3, P_LOCKED = 4;
    private static final int D_READY = 0, D_HOVER = 1, D_ON = 2, D_LOCKED = 3;

    private final List<Button> pads = new ArrayList<>();
    private Button dexButton;
    private final int[] state = new int[9];   // 0 no amigo, 1 listo, 2 activo, 3 descansando
    private final long[] restSeconds = new long[9];
    private int x0, y0, activeCount, krCount;
    private boolean dexReady, inComplete;
    private Identifier cardIn;

    public SummonScreen() {
        super(Component.translatable("screen.dexrider.summon"));
    }

    // pantalla LCD dentro de la carcasa: x 74..306, y 28..186; panel lateral de Dex a la derecha del separador
    private int sx0() { return x0 + 74; }
    private int sx1() { return x0 + W - 74; }
    private int sy0() { return y0 + 28; }
    private int divider() { return sx1() - 82; }
    private int panelCx() { return (divider() + sx1()) / 2; }
    private int padX(int i) { return sx0() + 10 + (i % 3) * STEP; }
    private int padY(int i) { return sy0() + 24 + (i / 3) * STEP; }
    private int dexX() { return panelCx() - DEXPAD / 2; }
    private int dexY() { return sy0() + 40; }

    @Override
    protected void init() {
        pads.clear();
        x0 = (this.width - W) / 2;
        y0 = (this.height - H) / 2 - 6;
        var player = Minecraft.getInstance().player;
        ItemStack driver = player == null ? ItemStack.EMPTY : DexDriverItem.findDriver(player);
        DriverDeck deck = DexDriverItem.getDeck(driver);
        AllyBook book = driver.isEmpty() || driver.get(DexComponents.ALLIES.get()) == null
                ? AllyBook.EMPTY : driver.get(DexComponents.ALLIES.get());
        long now = player == null ? 0 : player.level().getGameTime();
        activeCount = 0;
        ring = player == null ? KamenRide.RING : KamenRide.ring(player);
        boolean complete = player != null && KamenRide.current(player) == KamenRide.COMPLETE;

        for (int i = 0; i < 9; i++) {
            final int idx = i;
            KamenRide k = ring[i];
            boolean friend = k.implemented && k.card != null && deck.has(k.card);
            if (complete) {   // en Complete Form los pads son blasones: cualquier Kamen Ride del mazo se puede invocar
                state[i] = friend ? 1 : 0;
                Component tip = Component.literal((i + 1) + " · ").append(k.displayName()).append(Component.literal(" — "))
                        .append(Component.translatable("screen.dexrider.summon.crest"));
                Button b = Button.builder(Component.empty(), btn -> {
                    ClientPacketDistributor.sendToServer(new SummonAllyPayload(idx));
                    onClose();
                }).bounds(padX(i), padY(i), PAD, PAD).tooltip(Tooltip.create(tip)).build();
                b.active = friend;
                pads.add(addRenderableWidget(b));
                continue;
            }
            String id = k.id();
            if (!friend) state[i] = 0;
            else if (book.activeUntil(id) > now) { state[i] = 2; activeCount++; }
            else if (book.readyAt(id) > now) { state[i] = 3; restSeconds[i] = (book.readyAt(id) - now) / 20; }
            else state[i] = 1;

            Component tip = Component.literal((i + 1) + " · ").append(k.displayName()).append(Component.literal(" — "))
                    .append(Component.translatable("screen.dexrider.summon.state." + state[i]));
            Button b = Button.builder(Component.empty(), btn -> {
                ClientPacketDistributor.sendToServer(new SummonAllyPayload(idx));
                onClose();
            }).bounds(padX(i), padY(i), PAD, PAD).tooltip(Tooltip.create(tip)).build();
            b.active = state[i] == 1 || state[i] == 2;
            pads.add(addRenderableWidget(b));
        }

        // Blason de Dex: Complete Form
        krCount = KTouch.kamenRideCount(deck);
        inComplete = player != null && KamenRide.current(player) == KamenRide.COMPLETE;
        boolean hasKit = player != null && KTouch.has(player, DexItems.K_TOUCH.get()) && KTouch.hasActiveCard(player);
        int miss = player == null ? 1 : KTouch.missing(player, deck);
        boolean asDex = player != null && Transformation.isTransformed(player) && KamenRide.current(player) == KamenRide.DEX;
        dexReady = inComplete || (hasKit && asDex && miss == 0);
        cardIn = player == null || !KTouch.hasActiveCard(player) ? null
                : DexRider.id(ring == KamenRide.RING_2 ? "textures/item/complete_form_card_2.png" : "textures/item/complete_form_card.png");
        Component dexTip;
        if (inComplete) dexTip = Component.translatable("screen.dexrider.summon.dex.exit");
        else if (miss > 0) dexTip = Component.translatable("screen.dexrider.summon.dex.missing", miss);
        else if (!hasKit) dexTip = Component.translatable("message.dexrider.ktouch_need_card");
        else if (!asDex) dexTip = Component.translatable("message.dexrider.ktouch_need_dex");
        else dexTip = Component.translatable("screen.dexrider.summon.dex.enter");
        dexButton = addRenderableWidget(Button.builder(Component.empty(), btn -> {
            ClientPacketDistributor.sendToServer(CompleteFormPayload.INSTANCE);
            onClose();
        }).bounds(dexX(), dexY(), DEXPAD, DEXPAD).tooltip(Tooltip.create(dexTip)).build());
        dexButton.active = dexReady;
    }

    private static boolean over(int mx, int my, int x, int y, int size) {
        return mx >= x && mx < x + size && my >= y && my < y + size;
    }

    private static void sprite(GuiGraphicsExtractor g, Identifier tex, int x, int y, int u, int v, int w, int h, int tw, int th) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, tw, th);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);   // botones (quedan debajo del dibujo del aparato)
        // la carta de Complete Form, en vertical, entrando por la muesca lateral (el aparato tapa la parte metida)
        if (cardIn != null) sprite(g, cardIn, x0 - 30, y0 + H / 2 - 24, 0, 0, 48, 48, 48, 48);
        sprite(g, BODY, x0, y0, 0, 0, W, H, W, H);
        int sy0 = sy0(), sx1 = sx1();

        // cabecera: titulo sobre la cuadricula y las tres luces de "en pelea"
        int titleCx = (sx0() + divider()) / 2;
        g.text(this.font, this.title, titleCx - this.font.width(this.title) / 2, sy0 + 5, 0xFFECE8E2, false);
        for (int k = 0; k < 3; k++) {
            sprite(g, LIGHTS, sx1 - 6 - (3 - k) * 12, sy0 + 6, k < activeCount ? 10 : 0, 0, 10, 6, 20, 6);
        }

        // los nueve blasones
        for (int i = 0; i < 9; i++) {
            int px = padX(i), py = padY(i);
            boolean hover = pads.get(i).active && over(mouseX, mouseY, px, py, PAD);
            int frame = switch (state[i]) {
                case 1 -> hover ? P_HOVER : P_READY;
                case 2 -> P_ACTIVE;
                case 3 -> P_REST;
                default -> P_LOCKED;
            };
            sprite(g, PADS, px, py, frame * PAD, 0, PAD, PAD, PADS_W, PADS_H);
            int row = state[i] == 3 ? 1 : state[i] == 0 ? 2 : 0;
            sprite(g, EMBLEMS, px + 6, py + 6 - (hover ? 1 : 0), (ring[i] == KamenRide.KAIXA ? 10 : i) * EMB, row * EMB, EMB, EMB, EMB_W, EMB_H);
            if (state[i] == 3) {
                Component s = Component.literal(restSeconds[i] + "s");
                g.text(this.font, s, px + PAD - 3 - this.font.width(s), py + PAD - 11, 0xFFE8E8EE, true);
            }
        }

        // panel lateral: blason de Dex (Complete Form)
        int dx = dexX(), dy = dexY();
        boolean dexHover = dexReady && over(mouseX, mouseY, dx, dy, DEXPAD);
        int dexFrame = inComplete ? D_ON : dexReady ? (dexHover ? D_HOVER : D_READY) : D_LOCKED;
        sprite(g, PADS, dx, dy, 180 + dexFrame * DEXPAD, 0, DEXPAD, DEXPAD, PADS_W, PADS_H);
        sprite(g, EMBLEMS, dx + 6, dy + 6 - (dexHover ? 1 : 0), dexReady ? 0 : DEXEMB, 72, DEXEMB, DEXEMB, EMB_W, EMB_H);
        int on = dexReady ? 0xFFF2C230 : 0xFF6A6A76;
        Component label = Component.translatable("screen.dexrider.summon.complete");
        g.text(this.font, label, panelCx() - this.font.width(label) / 2, dy + DEXPAD + 8, on, false);
        Component kr = Component.literal(Math.min(krCount, KTouch.COMPLETE_AT) + "/" + KTouch.COMPLETE_AT);
        g.text(this.font, kr, panelCx() - this.font.width(kr) / 2, dy + DEXPAD + 20,
                krCount >= KTouch.COMPLETE_AT ? 0xFFF2C230 : 0xFF6A6A76, false);

        Component hint = Component.translatable("screen.dexrider.summon.hint");
        g.text(this.font, hint, x0 + W / 2 - this.font.width(hint) / 2, y0 + H + 6, 0xFFECE8E2, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
