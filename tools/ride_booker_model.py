#!/usr/bin/env python3
"""
Genera el Ride Booker 3D (diseno original Dex: negro, dorado, rojo y verde).

Salida (en src/main/resources/assets/dexrider):
  textures/item/ride_booker_3d.png         textura 64x64 (paleta + emblema + hoja + empunadura)
  models/item/ride_booker_m0..m5.json      6 cuadros: 0 = espada ... 5 = pistola
  items/ride_booker.json                   range_dispatch sobre dexrider:booker_morph

Ejes del modelo: la empunadura apunta a -Y. Espada: la hoja sale hacia +Y. Pistola: el canon apunta a -Z.
El cuerpo (el "libro" de cartas) gira 90 grados sobre X alrededor de PIVOT para pasar de una forma a la otra.

  python3 tools/ride_booker_model.py            genera los archivos
  python3 tools/ride_booker_model.py --preview  ademas dibuja /tmp/booker_preview.png
"""
import json, math, os, sys
import numpy as np
from PIL import Image
from scipy.spatial.transform import Rotation as Rot

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "dexrider")
TEX = "dexrider:item/ride_booker_3d"
DY = -6.0                    # todo el arma baja 6 px para que la hoja sea mas larga (los elementos van de -16 a 32)
PIVOT = (12.5 + DY, 7.5)     # (y, z) del eje de giro del libro
GRIP_ANGLE = -22.5           # en pistola la empunadura se inclina hacia atras
GRIP_TOP = np.array([8.0, 7.8 + DY, 10.0])
GRIP = np.array([8.0, 4.5 + DY, 10.0])

# ---------------------------------------------------------------- textura
PAL = {
    "black": (20, 20, 26), "dark": (38, 38, 46), "gun": (58, 61, 70),
    "gold": (214, 166, 42), "gold_d": (150, 112, 22), "red": (200, 32, 42), "red_d": (128, 18, 26),
    "green": (47, 191, 74), "glow_g": (140, 255, 160), "glow_r": (255, 96, 80),
    "silver": (200, 206, 216), "white": (240, 244, 255), "window": (150, 200, 220),
}
SW = {name: i for i, name in enumerate(PAL)}


def swatch_uv(name):
    i = SW[name]
    c, r = i % 8, i // 8
    return [c, r, c + 1, r + 1]


def make_texture():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    rng = np.random.default_rng(7)
    for name, col in PAL.items():
        u0, v0, _, _ = swatch_uv(name)
        for dx in range(4):
            for dy in range(4):
                k = 1.0 + (0.06 if (dx + dy) == 0 else 0) - 0.03 * (dy == 3) + rng.uniform(-0.025, 0.025)
                if name.startswith("glow") or name in ("white",):
                    k = 1.0
                px[u0 * 4 + dx, v0 * 4 + dy] = tuple(int(max(0, min(255, c * k))) for c in col) + (255,)

    # emblema 16x32 en (32,0): marco dorado, ranura verde y nucleo rojo en rombo, "codigo" dorado
    B, G, GD, R, GR, GL = PAL["black"], PAL["gold"], PAL["gold_d"], PAL["red"], PAL["green"], PAL["glow_g"]
    for x in range(16):
        for y in range(32):
            c = B
            if x in (0, 15) or y in (0, 31):
                c = GD
            elif x in (1, 14) or y in (1, 30):
                c = G
            elif 7 <= x <= 8 and 4 <= y <= 27:
                c = GL if 6 <= y <= 25 else GR
            elif abs(x - 7.5) + abs(y - 15.5) <= 5.5 and not (7 <= x <= 8):
                c = R if abs(x - 7.5) + abs(y - 15.5) <= 4.0 else PAL["red_d"]
            elif (y in (4, 6, 25, 27)) and 3 <= x <= 12:
                c = G
            elif 3 <= x <= 4 and y % 3 == 0 and 9 <= y <= 22:
                c = GD
            elif 11 <= x <= 12 and y % 3 == 0 and 9 <= y <= 22:
                c = GD
            px[32 + x, y] = c + (255,)

    # hoja 12x48 en (52,0): plata con filo brillante a ambos lados y linea roja central
    for x in range(12):
        for y in range(48):
            if x in (0, 11):
                c = PAL["white"]
            elif x in (1, 10):
                c = (225, 230, 240)
            elif 5 <= x <= 6:
                c = PAL["glow_r"] if y % 8 not in (0,) else PAL["red"]
            elif x in (4, 7):
                c = PAL["black"]
            else:
                c = tuple(int(v * (0.92 + 0.04 * ((y // 4) % 2))) for v in PAL["silver"])
            px[52 + x, y] = c + (255,)

    # empunadura 8x32 en (32,32): negro con vendas rojas
    for x in range(8):
        for y in range(32):
            band = (y % 6) in (0, 1)
            c = PAL["red"] if band else (PAL["dark"] if x in (0, 7) else PAL["black"])
            if band and (y % 6) == 1:
                c = PAL["red_d"]
            px[32 + x, 32 + y] = c + (255,)

    # cantos de las paginas 8x32 en (40,32): hojas claras con lineas finas y una carta roja/verde asomando
    for x in range(8):
        for y in range(32):
            c = (226, 222, 206) if y % 2 == 0 else (176, 172, 160)
            if y in (9, 10):
                c = PAL["red"]
            elif y in (21, 22):
                c = PAL["green"]
            if x in (0, 7):
                c = tuple(int(v * 0.8) for v in c)
            px[40 + x, 32 + y] = c + (255,)
    return img


EMBLEM_UV = [8, 0, 12, 8]
BLADE_UV = [13, 0, 16, 12]
GRIP_UV = [8, 8, 10, 16]
PAGES_UV = [10, 8, 12, 16]

# ---------------------------------------------------------------- geometria


def faces_all(uv, **override):
    f = {d: {"uv": list(uv), "texture": "#t"} for d in ("north", "south", "east", "west", "up", "down")}
    for d, v in override.items():
        f[d] = v
    return f


def el(frm, to, faces, rot=None, shift=True):
    if shift:
        frm, to = [frm[0], frm[1] + DY, frm[2]], [to[0], to[1] + DY, to[2]]
        if rot and "origin" in rot:
            rot = dict(rot, origin=[rot["origin"][0], rot["origin"][1] + DY, rot["origin"][2]])
    e = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces}
    if rot:
        e["rotation"] = rot
    return e


def to_gun(frm, to):
    """Pasa una caja del marco espada al marco pistola (giro de -90 sobre X alrededor de PIVOT)."""
    py, pz = PIVOT[0] - DY, PIVOT[1]
    ys = [py + (z - pz) for z in (frm[2], to[2])]
    zs = [pz - (y - py) for y in (frm[1], to[1])]
    return [frm[0], min(ys), min(zs)], [to[0], max(ys), max(zs)]


def body_parts(gun_frame, face_rot):
    """El libro de cartas, definido en el marco espada (de pie: lomo delante, cantos de las paginas detras)."""
    parts = []
    sw = lambda n: faces_all(swatch_uv(n))
    emb = lambda: {"uv": list(EMBLEM_UV), "texture": "#t", "rotation": face_rot}
    pages = lambda: {"uv": list(PAGES_UV), "texture": "#t", "rotation": face_rot}
    page_rot = {"uv": list(PAGES_UV), "texture": "#t", "rotation": (face_rot + 90) % 360}
    # bloque de paginas: los cantos (arriba, abajo y detras) se ven como hojas
    parts.append(([7.0, 8.3, 3.4], [9.0, 19.7, 10.7], faces_all(swatch_uv("black"), up=page_rot, down=page_rot, south=pages())))
    # tapas: negras con el emblema Dex
    parts.append(([8.95, 8.0, 3.0], [9.45, 20.0, 11.0], faces_all(swatch_uv("black"), east=emb())))
    parts.append(([6.55, 8.0, 3.0], [7.05, 20.0, 11.0], faces_all(swatch_uv("black"), west=emb())))
    # lomo rojo con remates dorados y gema verde
    parts.append(([6.6, 8.2, 2.5], [9.4, 19.8, 3.2], sw("red")))
    parts.append(([6.45, 7.8, 2.3], [9.55, 9.0, 3.4], sw("gold")))
    parts.append(([6.45, 19.0, 2.3], [9.55, 20.2, 3.4], sw("gold")))
    parts.append(([7.4, 13.0, 2.15], [8.6, 15.0, 2.5], sw("glow_g")))
    # esquinas doradas detras: por arriba sale la hoja, por abajo se engancha la empunadura
    parts.append(([6.4, 18.6, 8.6], [9.6, 20.4, 11.4], sw("gold")))
    parts.append(([6.4, 7.6, 8.6], [9.6, 9.4, 11.4], sw("gold_d")))
    if gun_frame:
        parts = [(*to_gun(f, t), fc) for f, t, fc in parts]
    return parts


def gun_extras(muzzle_len):
    """En el marco pistola: la boca sale por la esquina delantera de abajo; miras arriba."""
    sw = lambda n: faces_all(swatch_uv(n))
    out = []
    if muzzle_len > 0:
        z0 = -muzzle_len
        out.append(([7.2, 8.8, z0], [8.8, 11.2, 0.2], faces_all(swatch_uv("gun"), north={"uv": swatch_uv("glow_g"), "texture": "#t"})))
        out.append(([6.9, 8.5, z0 - 0.01], [9.1, 11.5, z0 + 0.7], faces_all(swatch_uv("gold"), north={"uv": swatch_uv("glow_g"), "texture": "#t"})))
    out.append(([7.5, 16.3, 9.6], [8.5, 17.4, 11.2], sw("red")))                        # mira trasera
    out.append(([7.6, 16.3, 0.6], [8.4, 17.0, 1.4], sw("gold")))                        # mira delantera
    return out


def blade(top):
    """Hoja que sale por la esquina de arriba-atras del libro: nucleo de 20.4 a top y punta en rombo."""
    out = []
    zc = 10.0
    side = {"uv": list(BLADE_UV), "texture": "#t"}
    edge = {"uv": swatch_uv("white"), "texture": "#t"}
    out.append(el([7.6, 20.4, zc - 2.4], [8.4, top, zc + 2.4],
                  {"east": side, "west": side, "north": edge, "south": edge, "up": edge, "down": edge}))
    out.append(el([7.5, 21.0, zc - 0.5], [8.5, top - 0.5, zc + 0.5], faces_all(swatch_uv("glow_r"))))
    s = 1.7
    out.append(el([7.62, top - s, zc - s], [8.38, top + s, zc + s], faces_all(swatch_uv("silver"),
                  east={"uv": swatch_uv("white"), "texture": "#t"}, west={"uv": swatch_uv("white"), "texture": "#t"}),
                  {"origin": [8, top, zc], "axis": "x", "angle": 45}))
    return out


def static_parts(trigger, angled):
    """Empunadura (sale de la esquina de abajo-atras del libro), gatillo y guardamonte."""
    out = []
    gr = {"origin": [8, 7.8, 10], "axis": "x", "angle": GRIP_ANGLE} if angled else None
    grip = {"uv": list(GRIP_UV), "texture": "#t"}
    out.append(el([7.0, 1.2, 9.0], [9.0, 7.8, 11.0], faces_all(swatch_uv("black"), east=grip, west=grip, north=grip, south=grip), gr))
    out.append(el([6.6, 0.0, 8.6], [9.4, 1.4, 11.4], faces_all(swatch_uv("gold"), down={"uv": swatch_uv("green"), "texture": "#t"}), gr))
    out.append(el([6.8, 7.4, 8.8], [9.2, 8.0, 11.2], faces_all(swatch_uv("gold_d"))))
    if trigger:
        out.append(el([7.6, 5.9, 7.4], [8.4, 7.8, 8.2], faces_all(swatch_uv("red"))))      # gatillo
        out.append(el([7.5, 5.0, 5.8], [8.5, 5.5, 8.8], faces_all(swatch_uv("gold"))))     # guardamonte
        out.append(el([7.5, 5.0, 5.8], [8.5, 7.8, 6.3], faces_all(swatch_uv("gold"))))
    return out


FRAMES = [
    # (angulo del cuerpo en grados, tope de la hoja o None, largo del canon)
    (0.0, 35.6, 0), (0.0, 28.0, 0), (-22.5, None, 0), (-45.0, None, 0), (-67.5, None, 1.2), (-90.0, None, 2.6),
]


def frame_elements(k):
    angle, blade_top, muzzle = FRAMES[k]
    els = static_parts(trigger=k >= 4, angled=k >= 4)
    gun_frame = angle <= -67.5
    rot = None
    if gun_frame and angle != -90.0:
        rot = {"origin": [8, PIVOT[0] - DY, PIVOT[1]], "axis": "x", "angle": angle + 90.0}
    elif not gun_frame and angle != 0.0:
        rot = {"origin": [8, PIVOT[0] - DY, PIVOT[1]], "axis": "x", "angle": angle}
    for f, t, fc in body_parts(gun_frame, 90 if gun_frame else 0):
        els.append(el(f, t, fc, rot))
    if gun_frame:
        for f, t, fc in gun_extras(muzzle):
            els.append(el(f, t, fc, rot))
    if blade_top is not None:
        els.extend(blade(blade_top))
    return els


# ---------------------------------------------------------------- display


def R(*steps):
    """Compone giros en orden de aplicacion: R(('y',-90),('z',-45)) = primero Y y luego Z (ejes fijos)."""
    m = np.eye(3)
    for ax, deg in steps:
        m = Rot.from_euler(ax, deg, degrees=True).as_matrix() @ m
    return m


def euler_xyz(m):
    import warnings
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        a = Rot.from_matrix(m).as_euler("XYZ", degrees=True)
    back = Rot.from_euler("XYZ", a, degrees=True).as_matrix()
    assert np.allclose(back, m, atol=1e-6)
    return [round(float(v), 2) for v in a]


def slerp(a, b, t):
    ra, rb = Rot.from_matrix(a), Rot.from_matrix(b)
    rel = (ra.inv() * rb).as_rotvec()
    return (ra * Rot.from_rotvec(rel * t)).as_matrix()


def element_corners(e):
    f, t = np.array(e["from"]), np.array(e["to"])
    pts = np.array([[x, y, z] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])])
    r = e.get("rotation")
    if r:
        o = np.array(r["origin"])
        m = Rot.from_euler(r["axis"], r["angle"], degrees=True).as_matrix()
        pts = (pts - o) @ m.T + o
    return pts


def bbox(els):
    pts = np.vstack([element_corners(e) for e in els])
    return pts.min(0), pts.max(0)


HAND = {
    # contexto: (giro espada, giro pistola, escala, punto donde va la empunadura)
    # Tercera persona (ItemInHandLayer), brazo colgando: +X = derecha, +Y = adelante, +Z = arriba;
    # con el brazo levantado para apuntar: +Y = arriba, -Z = adelante.
    # Primera persona: el espacio de la camara (+X derecha, +Y arriba, -Z hacia el fondo).
    "thirdperson_righthand": (R(("x", 8)), R(), 0.85, np.array([0.0, -2.5, 1.6])),
    "firstperson_righthand": (R(("y", -80), ("x", -15), ("z", 12)), R(("y", 6)), 0.5, np.array([2.0, -2.2, -4.0])),
}
CENTERED = {
    "gui": (R(("y", -90), ("z", -45)), R(("y", -70), ("x", 18)), 15.0),
    "fixed": (R(("y", -90), ("z", -45)), R(("y", -90)), 14.0),
    "ground": (R(), R(), 8.0),
    "head": (R(("y", -90)), R(("y", -90)), 14.0),
}


def grip_point(k):
    if k < 4:
        return GRIP
    m = Rot.from_euler("x", GRIP_ANGLE, degrees=True).as_matrix()
    return GRIP_TOP + m @ (GRIP - GRIP_TOP)


def display(k, els):
    t = k / (len(FRAMES) - 1)
    out = {}
    for ctx, (rs, rg, s, anchor) in HAND.items():
        m = slerp(rs, rg, t)
        tr = anchor - m @ (s * (grip_point(k) - 8.0))
        out[ctx] = {"rotation": euler_xyz(m), "translation": [round(float(v), 3) for v in tr], "scale": [s, s, s]}
    pts = np.vstack([element_corners(e) for e in els])
    for ctx, (rs, rg, fit) in CENTERED.items():
        m = slerp(rs, rg, t)
        v = (pts - 8.0) @ m.T
        lo, hi = v.min(0), v.max(0)
        s = min(1.0, fit / max(hi[0] - lo[0], hi[1] - lo[1]))
        c = (lo + hi) / 2
        tr = -s * c
        tr = np.clip(tr, -80, 80)
        out[ctx] = {"rotation": euler_xyz(m), "translation": [round(float(v), 3) for v in tr], "scale": [round(s, 3)] * 3}
    return out


def build():
    os.makedirs(os.path.join(ROOT, "models", "item"), exist_ok=True)
    make_texture().save(os.path.join(ROOT, "textures", "item", "ride_booker_3d.png"))
    models = []
    for k in range(len(FRAMES)):
        els = frame_elements(k)
        for e in els:
            for v in e["from"] + e["to"]:
                assert -16 <= v <= 32, (k, e)
        model = {
            "textures": {"t": TEX, "particle": TEX},
            "gui_light": "front",
            "display": display(k, els),
            "elements": els,
        }
        with open(os.path.join(ROOT, "models", "item", f"ride_booker_m{k}.json"), "w") as fh:
            json.dump(model, fh, indent=1)
        models.append(els)
    thresholds = [0.12, 0.3, 0.5, 0.7, 0.88]
    item = {"model": {
        "type": "minecraft:range_dispatch",
        "property": "dexrider:booker_morph",
        "fallback": {"type": "minecraft:model", "model": "dexrider:item/ride_booker_m0"},
        "entries": [{"threshold": th, "model": {"type": "minecraft:model", "model": f"dexrider:item/ride_booker_m{i + 1}"}}
                    for i, th in enumerate(thresholds)],
    }}
    with open(os.path.join(ROOT, "items", "ride_booker.json"), "w") as fh:
        json.dump(item, fh, indent=2)
    return models


def preview(models):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from matplotlib.patches import Polygon
    tex = make_texture().convert("RGB")
    tpx = np.array(tex)

    def face_color(e, face):
        f = e["faces"].get(face)
        if not f:
            return None
        u0, v0, u1, v1 = f["uv"]
        reg = tpx[int(v0 * 4):max(int(v0 * 4) + 1, int(v1 * 4)), int(u0 * 4):max(int(u0 * 4) + 1, int(u1 * 4))]
        return reg.reshape(-1, 3).mean(0) / 255

    quads = {"east": [1, 3, 7, 5], "west": [0, 2, 6, 4], "up": [2, 3, 7, 6], "down": [0, 1, 5, 4],
             "north": [0, 1, 3, 2], "south": [4, 5, 7, 6]}
    # corners order from element_corners: x outer, y, z inner -> index = xi*4 + yi*2 + zi
    quads = {"west": [0, 1, 3, 2], "east": [4, 5, 7, 6], "down": [0, 1, 5, 4], "up": [2, 3, 7, 6],
             "north": [0, 2, 6, 4], "south": [1, 3, 7, 5]}
    normals = {"west": (-1, 0, 0), "east": (1, 0, 0), "down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1)}

    def draw(ax, els, m, title):
        polys = []
        light = np.array([0.4, 0.8, 0.45]); light /= np.linalg.norm(light)
        for e in els:
            pts = element_corners(e)
            rm = np.eye(3)
            if e.get("rotation"):
                rm = Rot.from_euler(e["rotation"]["axis"], e["rotation"]["angle"], degrees=True).as_matrix()
            vp = (pts - 8) @ m.T
            for face, idx in quads.items():
                col = face_color(e, face)
                if col is None:
                    continue
                n = m @ rm @ np.array(normals[face])
                if n[2] <= 0:   # mira desde +Z hacia -Z
                    continue
                shade = 0.55 + 0.45 * max(0, float(n @ light))
                q = vp[idx]
                polys.append((q[:, 2].mean(), q[:, :2], np.clip(col * shade, 0, 1)))
        polys.sort(key=lambda p: p[0])
        for _, q, c in polys:
            ax.add_patch(Polygon(q, closed=True, fc=c, ec=(0, 0, 0, 0.25), lw=0.3))
        ax.set_xlim(-20, 20); ax.set_ylim(-20, 26); ax.set_aspect("equal"); ax.axis("off"); ax.set_title(title, fontsize=7)

    fig, axes = plt.subplots(3, len(models), figsize=(2.2 * len(models), 7.5))
    for k, els in enumerate(models):
        draw(axes[0, k], els, R(("y", -90)), f"m{k} lado (+X)")
        draw(axes[1, k], els, R(("y", -35), ("x", 20)), f"m{k} 3/4")
        disp = display(k, els)["gui"]
        m = Rot.from_euler("XYZ", disp["rotation"], degrees=True).as_matrix()
        draw(axes[2, k], [dict(e) for e in els], m, f"m{k} gui")
    fig.tight_layout()
    fig.savefig("/tmp/booker_preview.png", dpi=110)
    tex.resize((256, 256), Image.NEAREST).save("/tmp/booker_tex.png")


if __name__ == "__main__":
    ms = build()
    if "--preview" in sys.argv:
        preview(ms)
    print("ok:", len(ms), "cuadros")
