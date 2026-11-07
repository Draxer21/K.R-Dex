package com.draxer.dexrider.client;

import com.draxer.dexrider.DexRider;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ajustes de la interfaz del visor (solo de este cliente): config/dexrider-visor.json.
 * Se cambian en el juego desde el Archivo del Driver (boton "Visor").
 */
public final class VisorSettings {

    public enum Mode { FULL, MINIMAL, CLASSIC }
    public enum ColorMode { SUIT, CUSTOM }

    public Mode mode = Mode.FULL;
    public float opacity = 0.85F;          // 0.2 .. 1
    public float scale = 1.0F;             // 0.5 .. 1.5
    public ColorMode colorMode = ColorMode.SUIT;
    public int customColor = 0x28D250;
    public boolean boot = true, frame = true, scanlines = true, reticle = true, alerts = true, target = true;
    /** Filtro de la vision: el mundo se ve a traves del lente, tenido de su color, con los bordes mas oscuros. */
    public boolean filter = true, vignette = true;
    public float filterStrength = 0.12F;   // 0 .. 0.5
    /** Posiciones de las piezas del HUD (id -> ancla, desplazamiento, tamano, visible). Vacio = de fabrica. */
    public java.util.Map<String, HudLayout.Elem> layout = new java.util.HashMap<>();

    private static VisorSettings current;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("dexrider-visor.json");
    }

    public static VisorSettings get() {
        if (current == null) {
            current = new VisorSettings();
            try {
                Path f = file();
                if (Files.exists(f)) try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    VisorSettings s = GSON.fromJson(r, VisorSettings.class);
                    if (s != null) current = s.clamped();
                }
            } catch (Exception e) {
                DexRider.LOGGER.warn("No se pudo leer dexrider-visor.json, uso los valores por defecto", e);
            }
        }
        return current;
    }

    public static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) { GSON.toJson(get(), w); }
        } catch (Exception e) {
            DexRider.LOGGER.warn("No se pudo guardar dexrider-visor.json", e);
        }
    }

    public static void reset() {
        current = new VisorSettings();
        save();
    }

    private VisorSettings clamped() {
        if (mode == null) mode = Mode.FULL;
        if (colorMode == null) colorMode = ColorMode.SUIT;
        opacity = Math.max(0.2F, Math.min(1F, opacity));
        scale = Math.max(0.5F, Math.min(1.5F, scale));
        customColor &= 0xFFFFFF;
        filterStrength = Math.max(0F, Math.min(0.5F, filterStrength));
        if (layout == null) layout = new java.util.HashMap<>();
        layout.values().removeIf(java.util.Objects::isNull);
        layout.values().forEach(e -> e.scale = Math.max(0.5F, Math.min(2F, e.scale)));
        return this;
    }
}
