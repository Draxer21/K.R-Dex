package com.draxer.dexrider.network;

/** Lo que el servidor le cuenta al cliente para el HUD (solo datos, sin clases de cliente). */
public final class HudState {
    private HudState() {}

    public static volatile HudSyncPayload last = HudSyncPayload.EMPTY;
    public static volatile long receivedAt = 0;
}
