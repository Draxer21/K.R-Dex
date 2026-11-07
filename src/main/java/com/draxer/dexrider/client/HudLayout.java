package com.draxer.dexrider.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Posiciones de las piezas del HUD del Dex (las cambia el jugador en Visor > Editar posiciones).
 *
 * Cada pieza tiene un ancla (esquina, borde o centro de la pantalla), un desplazamiento desde ella, un tamano propio y
 * si se ve. La pieza se alinea con su ancla: anclada abajo a la derecha, su esquina de abajo a la derecha queda en la
 * esquina de la pantalla (mas el desplazamiento). Asi no se descoloca al cambiar la ventana o la escala de la interfaz.
 */
public final class HudLayout {
    private HudLayout() {}

    public enum Anchor {
        TOP_LEFT(0, 0), TOP_CENTER(1, 0), TOP_RIGHT(2, 0),
        MIDDLE_LEFT(0, 1), CENTER(1, 1), MIDDLE_RIGHT(2, 1),
        BOTTOM_LEFT(0, 2), BOTTOM_CENTER(1, 2), BOTTOM_RIGHT(2, 2);
        final int col, row;
        Anchor(int col, int row) { this.col = col; this.row = row; }
        static Anchor of(int col, int row) {
            for (Anchor a : values()) if (a.col == col && a.row == row) return a;
            return TOP_LEFT;
        }
    }

    /** Lo que se guarda de cada pieza (en config/dexrider-visor.json). */
    public static class Elem {
        public String anchor = Anchor.TOP_LEFT.name();
        public int dx, dy;
        public float scale = 1F;
        public boolean visible = true;
        /** Lado de la pieza que se alinea con el ancla en horizontal (0 izq., 1 centro, 2 der.); -1 = el del ancla. */
        public int pivot = -1;

        Elem() {}
        Elem(Anchor a, int dx, int dy) { this.anchor = a.name(); this.dx = dx; this.dy = dy; }

        Anchor anchor() {
            try { return Anchor.valueOf(anchor); } catch (RuntimeException e) { return Anchor.TOP_LEFT; }
        }

        Elem copy() { Elem e = new Elem(anchor(), dx, dy); e.scale = scale; e.visible = visible; e.pivot = pivot; return e; }

        Elem pivot(int p) { this.pivot = p; return this; }

        int pivotCol() { return pivot >= 0 && pivot <= 2 ? pivot : anchor().col; }
    }

    /** Piezas movibles: id -> posicion de fabrica (la de antes de 0.40). */
    public static final Map<String, Elem> DEFAULTS = new LinkedHashMap<>();
    /** Tamano de referencia de cada pieza (para el editor cuando la pieza no esta en pantalla). */
    static final Map<String, int[]> SIZE = new HashMap<>();
    static {
        def("status", new Elem(Anchor.TOP_LEFT, 12, 14), 118, 54);
        // la carta y la tira, pegadas a la izquierda de la barra de objetos (su borde derecho a 97 del centro)
        def("card", new Elem(Anchor.BOTTOM_CENTER, -97, -3).pivot(2), 110, 13);
        def("strip", new Elem(Anchor.BOTTOM_CENTER, -97, -20).pivot(2), 72, 24);
        def("target", new Elem(Anchor.TOP_RIGHT, -12, 14), 128, 47);
        def("alerts", new Elem(Anchor.TOP_CENTER, 0, 60), 150, 13);
        def("toast", new Elem(Anchor.TOP_CENTER, 0, 22), 150, 30);
    }

    private static void def(String id, Elem e, int w, int h) {
        DEFAULTS.put(id, e);
        SIZE.put(id, new int[]{w, h});
    }

    /** Ultimo rectangulo dibujado de cada pieza, en coordenadas de la pieza (para el editor). */
    static final Map<String, int[]> LAST = new HashMap<>();

    public static Elem get(String id) {
        Map<String, Elem> m = VisorSettings.get().layout;
        Elem e = m.get(id);
        if (e == null) { e = DEFAULTS.get(id).copy(); m.put(id, e); }
        return e;
    }

    public static void reset(String id) {
        VisorSettings.get().layout.put(id, DEFAULTS.get(id).copy());
    }

    public static void resetAll() {
        VisorSettings.get().layout.clear();
    }

    /** Esquina superior izquierda de una pieza de w x h (ya escalada) en una pantalla de sw x sh. */
    static int[] pos(Elem e, int w, int h, int sw, int sh) {
        Anchor a = e.anchor();
        int x = a.col * sw / 2 - e.pivotCol() * w / 2 + e.dx;
        int y = a.row * sh / 2 - a.row * h / 2 + e.dy;
        x = Math.max(0, Math.min(sw - w, x));
        y = Math.max(0, Math.min(sh - h, y));
        return new int[]{x, y};
    }

    /**
     * Coloca la pieza: si se ve, mueve y escala el dibujo para que la pieza se dibuje desde (0, 0) y devuelve true
     * (hay que llamar a {@link #end} despues). w y h son su tamano sin escalar.
     */
    static boolean begin(GuiGraphicsExtractor g, String id, int w, int h, int sw, int sh) {
        Elem e = get(id);
        if (!e.visible) return false;
        int sw2 = Math.round(w * e.scale), sh2 = Math.round(h * e.scale);
        int[] p = pos(e, sw2, sh2, sw, sh);
        LAST.put(id, new int[]{p[0], p[1], sw2, sh2});
        g.pose().pushMatrix();
        g.pose().translate(p[0], p[1]);
        g.pose().scale(e.scale, e.scale);
        return true;
    }

    static void end(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    /** Rectangulo de la pieza en coordenadas de la pieza (usa el ultimo tamano dibujado o el de referencia). */
    static int[] rect(String id, int sw, int sh) {
        Elem e = get(id);
        int[] last = LAST.get(id);
        int w = last != null ? last[2] : Math.round(SIZE.get(id)[0] * e.scale);
        int h = last != null ? last[3] : Math.round(SIZE.get(id)[1] * e.scale);
        int[] p = pos(e, w, h, sw, sh);
        return new int[]{p[0], p[1], w, h};
    }

    /** Mueve la pieza a (x, y) (esquina, ya escalada) y elige el ancla mas cercana por tercios de pantalla. */
    static void moveTo(String id, int x, int y, int w, int h, int sw, int sh) {
        Elem e = get(id);
        int cx = x + w / 2, cy = y + h / 2;
        int col = cx < sw / 3 ? 0 : cx > sw * 2 / 3 ? 2 : 1;
        int row = cy < sh / 3 ? 0 : cy > sh * 2 / 3 ? 2 : 1;
        e.anchor = Anchor.of(col, row).name();
        e.pivot = -1;
        e.dx = x - (col * sw / 2 - col * w / 2);
        e.dy = y - (row * sh / 2 - row * h / 2);
    }
}
