package com.draxer.dexrider.client;

import com.draxer.dexrider.menu.RideBookerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Pantalla del Ride Booker: cinco mazos (pestanas), 18 cartas por mazo, y el modo INSERTAR
 * (clic en una carta = se activa al instante sin sacarla del Booker).
 */
public class RideBookerScreen extends AbstractContainerScreen<RideBookerMenu> {

    private static final String[] DECKS = {"rescue", "crowd", "duel", "chase", "everything_wrong"};
    private final Button[] deckButtons = new Button[RideBookerMenu.DECKS];
    private Button insertButton;

    public RideBookerScreen(RideBookerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 176, 166);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        int w = 32;
        for (int i = 0; i < RideBookerMenu.DECKS; i++) {
            final int id = i;
            deckButtons[i] = addRenderableWidget(Button.builder(Component.translatable("booker.dexrider.deck." + DECKS[i] + ".short"),
                    b -> press(id)).bounds(leftPos + 8 + i * (w + 1), topPos + 16, w, 14)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("booker.dexrider.deck." + DECKS[i])))
                    .build());
        }
        insertButton = addRenderableWidget(Button.builder(Component.empty(), b -> press(RideBookerMenu.BUTTON_INSERT))
                .bounds(leftPos + imageWidth + 4, topPos + 16, 70, 18)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("booker.dexrider.insert.tip")))
                .build());
        refresh();
    }

    private void press(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        menu.clickMenuButton(mc.player, id);                     // cliente
        mc.gameMode.handleInventoryButtonClick(menu.containerId, id);   // servidor
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < deckButtons.length; i++) deckButtons[i].active = i != menu.deck();
        insertButton.setMessage(Component.translatable(menu.insertMode() ? "booker.dexrider.insert.on" : "booker.dexrider.insert.off"));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        // estuche negro con franjas plateadas y rojas (paleta del Ride Booker)
        g.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, 0xFF0E0E14);
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF24232E);
        g.fill(x, y, x + imageWidth, y + 3, 0xFFCD1C28);
        g.fill(x, y + imageHeight - 3, x + imageWidth, y + imageHeight, 0xFFB8BCC8);
        // ranuras de cartas (dorado si esta en modo insertar)
        int frame = menu.insertMode() ? 0xFFF2C230 : 0xFF8A8C9A;
        for (int i = 0; i < RideBookerMenu.PER_DECK; i++) slotBox(g, x + 8 + (i % 9) * 18, y + 34 + (i / 9) * 18, frame);
        for (int i = 0; i < 27; i++) slotBox(g, x + 8 + (i % 9) * 18, y + 84 + (i / 9) * 18, 0xFF8A8C9A);
        for (int i = 0; i < 9; i++) slotBox(g, x + 8 + i * 18, y + 142, 0xFF8A8C9A);
    }

    private static void slotBox(GuiGraphicsExtractor g, int sx, int sy, int frame) {
        g.fill(sx - 1, sy - 1, sx + 17, sy + 17, frame);
        g.fill(sx, sy, sx + 16, sy + 16, 0xFF16151C);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title.copy().append(" - ").append(Component.translatable("booker.dexrider.deck." + DECKS[menu.deck()])),
                this.titleLabelX, this.titleLabelY, 0xFFECE8E2, false);
        g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFFB8BCC8, false);
    }
}
