#!/usr/bin/env python3
"""
Rediseno de Kamen Rider Cipher (diseno propio segun el fanfic: azul marino y plata, visor blanco, Driver modular con
telefono y codigos). Etapa 1: solo el casco de Cipher Base.

  python3 cipher_design.py --out carpeta

Lee el modelo y las texturas actuales del repo y escribe COPIAS nuevas en --out (no toca los originales):
  rider_cipher.geo.json, rider_cipher.png, rider_cipher_glowmask.png

Que cambia en el casco (segunda version, segun la imagen definitiva de Cipher del fanfic):
  - armorHead conserva su primer cubo (la carcasa, mismos UV), repintada en gris metalico; azul oscuro solo en la
    parte baja de los lados y la nuca; circuitos azules a los lados y atras.
  - Ojos: dos lentes blancas casi circulares hechas con franjas escalonadas (abombadas al centro) sobre un aro azul
    oscuro. Brillan enteras.
  - Mandibula metalica lisa con una sola linea luminosa; mejillas en angulo; dos antenas finas desde lo alto de la
    frente, juntas y abriendose un poco; orejas grises bajas; protector de nuca gris.
  - rx_head queda vacio (el hueso se conserva). tools/rider_relief.py lleva 'skip': {'rider_cipher': ['head']} para no
    volver a poner el casco viejo; Accel y Blaster siguen igual hasta su etapa.
  - Las piezas nuevas se pintan en la franja libre de la textura (y >= 448), a 4 px por unidad del modelo.
Coordenadas: -x es la derecha del personaje, +y arriba, -z el frente (como en los .geo.json).
"""
import copy
import json
import math
import os
import sys

import numpy as np
from PIL import Image

REPO = os.environ.get('DEX_REPO', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
ASSETS = os.path.join(REPO, 'src', 'main', 'resources', 'assets', 'dexrider')
GEO = os.path.join(ASSETS, 'geckolib', 'models', 'item', 'armor')
TEX = os.path.join(ASSETS, 'textures', 'item', 'armor')
PX = 4
AREA = (0, 448, 512, 512)   # franja libre para las piezas nuevas (x0, y0, x1, y1)

# ------------------------------------------------------------------ paleta
NAVY = (26, 40, 86)
NAVY_D = (14, 22, 50)
NAVY_L = (44, 62, 118)
GUN = (128, 136, 150)
GUN_L = (176, 184, 198)
GUN_D = (84, 92, 108)
SIL = (198, 206, 220)
SIL_L = (238, 242, 248)
SIL_D = (124, 134, 152)
WHITE = (242, 248, 255)
GRID = (196, 216, 238)
CYAN = (56, 216, 255)
CYAN_L = (190, 244, 255)


def hexa(c, a=255):
    return np.array([c[0], c[1], c[2], a], dtype=np.uint8)


# ------------------------------------------------------------------ pintores de cara (w x h px)
def paint(style, w, h, face):
    img = np.zeros((h, w, 4), dtype=np.uint8)
    glow = np.zeros((h, w, 4), dtype=np.uint8)
    rng = np.random.default_rng(w * 131 + h * 17 + len(style) * 7 + hash(face) % 97)

    def fill(c, noise=0.0):
        base = np.array(c, dtype=np.float32)
        n = rng.normal(0, noise, (h, w, 1)) if noise else 0
        img[..., :3] = np.clip(base + n, 0, 255).astype(np.uint8)
        img[..., 3] = 255

    def bevel(hi, lo):
        if w >= 3 and h >= 3:
            img[0, :, :3] = hi; img[:, 0, :3] = hi
            img[-1, :, :3] = lo; img[:, -1, :3] = lo

    def lit(y0, y1, x0, x1, c=CYAN, gc=CYAN_L):
        img[y0:y1, x0:x1, :3] = c; img[y0:y1, x0:x1, 3] = 255
        glow[y0:y1, x0:x1, :3] = gc; glow[y0:y1, x0:x1, 3] = 255

    if style in ('sil', 'sild'):
        mid = np.array(SIL if style == 'sil' else SIL_D, dtype=np.float32)
        for y in range(h):
            t = y / max(1, h - 1)
            img[y, :, :3] = np.clip(mid * (1.08 - 0.2 * t), 0, 255).astype(np.uint8)
        img[..., 3] = 255
        bevel(SIL_L if style == 'sil' else SIL, SIL_D if style == 'sil' else (84, 92, 110))
    elif style == 'metal':
        mid = np.array(GUN, dtype=np.float32)
        for y in range(h):
            t = y / max(1, h - 1)
            img[y, :, :3] = np.clip(mid * (1.18 - 0.28 * t) + rng.normal(0, 1.5, (w, 3)), 0, 255).astype(np.uint8)
        img[..., 3] = 255
        bevel(GUN_L, GUN_D)
    elif style == 'lens':
        # lente blanca lisa, con el canto apenas azulado; brilla entera
        fill(WHITE)
        if w >= 3:
            img[:, 0, :3] = (214, 228, 246); img[:, -1, :3] = (214, 228, 246)
        if face != 'north':
            img[..., :3] = (206, 222, 242)
        glow[..., :3] = np.clip(img[..., :3].astype(int) + np.array([0, 4, 10]), 0, 255).astype(np.uint8)
        glow[..., 3] = 255
    elif style == 'ring':
        fill(NAVY_D, 1.5)
    elif style == 'jaw':
        img[...] = paint('metal', w, h, face)[0]
        if face == 'north' and h >= 4 and w >= 8:
            y = h // 2
            lit(y, y + 1, w // 2 - w // 5, w // 2 + w // 5)
    elif style == 'navy':
        fill(NAVY, 3.0); bevel(NAVY_L, NAVY_D)
    elif style == 'dark':
        fill(NAVY_D, 2.0)
    elif style == 'eye':
        # visor blanco con rejilla fina (lineas cada 2 px) y brillo arriba; brilla entero
        fill(WHITE)
        for y in range(2, h - 1, 3):
            img[y, 1:-1, :3] = GRID
        for x in range(3, w - 1, 4):
            img[1:-1, x, :3] = (222, 234, 248)
        img[0, :, :3] = (255, 255, 255)
        if h >= 4:
            img[-1, :, :3] = (170, 200, 230)
        glow[...] = img
        glow[..., :3] = np.clip(img[..., :3].astype(int) * 0.9 + np.array([0, 10, 20]), 0, 255).astype(np.uint8)
        glow[..., 3] = 255
    elif style == 'cyan':
        fill(CYAN); glow[..., :3] = CYAN_L; glow[..., 3] = 255
    elif style == 'port':
        # puerto de codigo: teclado de 3 x 2 puntos que brillan
        fill(NAVY_D)
        bevel(SIL, SIL_D)
        for j in range(2):
            for i in range(3):
                x = 1 + i * max(1, (w - 2) // 3); y = 1 + j * max(1, (h - 2) // 2)
                if x < w - 1 and y < h - 1:
                    lit(y, y + 1, x, x + 1)
    elif style == 'vent':
        # mandibula: plata con tres ranuras azules
        paint_sil = paint('sil', w, h, face)[0]
        img[...] = paint_sil
        if face in ('north', 'south') and h >= 5:
            for k in range(3):
                y = 1 + k * 2
                if y < h - 1:
                    lit(y, y + 1, 2, w - 2)
    elif style == 'dial':
        img[...] = paint('sil', w, h, face)[0]
        if face in ('east', 'west') and w >= 6 and h >= 6:
            cx, cy = w // 2, h // 2
            img[cy - 3:cy + 3, cx - 3:cx + 3, :3] = NAVY_D
            for dy in (-2, 0, 2):
                for dx in (-2, 0, 2):
                    if (dx + dy) % 4 == 0:
                        lit(cy + dy - 1 + 1 if dy < 0 else cy + dy - 1, cy + dy if dy < 0 else cy + dy, cx + dx - 1, cx + dx)
    else:
        raise ValueError(style)
    return img, glow


# ------------------------------------------------------------------ piezas del casco
def C(o, s, st, rot=None, piv=None, **fs):
    return {'o': list(o), 's': list(s), 'st': st, 'rot': rot, 'piv': piv, 'fs': fs}


def both(*cs):
    out = []
    for c in cs:
        out.append(c)
        m = copy.deepcopy(c)
        m['o'][0] = -(c['o'][0] + c['s'][0])
        if c['piv']:
            m['piv'][0] = -c['piv'][0]
        if c['rot']:
            m['rot'] = [c['rot'][0], -c['rot'][1], -c['rot'][2]]
        out.append(m)
    return out


def lens(cx, cy, z, r, depth, st, name_extra=()):
    """Un circulo escalonado (franjas horizontales) centrado en (cx, cy), al frente en z (hacia -z)."""
    out = []
    rows = [(0.0, 0.42, 1.0), (0.42, 0.72, 0.9), (0.72, 0.9, 0.72), (0.9, 1.0, 0.44)]   # (desde, hasta, ancho relativo)
    for a0, a1, wr in rows:
        hw = r * wr
        dz = depth * (1.0 if a0 == 0 else 0.8 if a0 < 0.7 else 0.6)
        for sgn in ((1,) if a0 == 0 else (1, -1)):
            if a0 == 0:
                y0, hh = cy - r * a1, 2 * r * a1
            elif sgn > 0:
                y0, hh = cy + r * a0, r * (a1 - a0)
            else:
                y0, hh = cy - r * a1, r * (a1 - a0)
            out.append(C([cx - hw, y0, z - dz], [2 * hw, hh, dz], st))
    return out


def helmet():
    z0 = -4.6   # frente de la carcasa (cubo de 8 inflado 0.6)
    ex, ey, r = 2.05, 27.75, 1.78   # centro y radio de cada ojo
    parts = []
    for cx in (ex, -ex):
        parts += lens(cx, ey, z0, r + 0.22, 0.18, 'ring')     # aro azul oscuro detras de la lente
        parts += lens(cx, ey, z0 - 0.18, r, 0.4, 'lens')      # lente blanca, abombada al centro
    parts += [
        # frente entre los ojos y bajo ellos: placa gris lisa
        C([-0.45, 26.4, z0 - 0.25], [0.9, 2.8, 0.25], 'metal'),
        # mandibula metalica limpia con una sola linea luminosa, y mejillas en angulo
        C([-2.2, 23.8, z0 - 0.4], [4.4, 1.9, 0.4], 'jaw'),
        *both(C([2.0, 23.8, z0 - 0.35], [2.3, 2.1, 0.35], 'metal', rot=[0, -22, 0], piv=[2.0, 24.9, z0 - 0.2])),
        # antenas: dos finas desde lo alto de la frente, juntas, abriendose apenas y un poco hacia atras
        *both(C([0.35, 32.55, -3.4], [0.3, 2.7, 0.3], 'sil', rot=[-14, 0, 11], piv=[0.5, 32.6, -3.25])),
        # oreja: placa gris baja, sin indicadores
        *both(C([4.55, 26.6, -0.7], [0.25, 2.0, 1.4], 'metal'), C([4.55, 26.9, -1.0], [0.25, 1.4, 2.0], 'metal')),
        # nuca: protector gris
        C([-3.0, 23.8, 4.55], [6.0, 1.3, 0.4], 'metal'),
    ]
    return parts


# ------------------------------------------------------------------ carcasa repintada
def repaint_shell(tex, glow, cube):
    """Carcasa gris metalico (mas clara arriba), azul oscuro solo en la parte baja de los lados y la nuca, y los
    circuitos azules de los lados y la espalda."""
    for face, f in cube['uv'].items():
        (u, v), (us, vs) = f['uv'], f['uv_size']
        u, v, us, vs = int(u), int(v), int(us), int(vs)
        rng = np.random.default_rng(u * 7 + v)
        tex[v:v + vs, u:u + us, 3] = 255
        glow[v:v + vs, u:u + us] = 0
        for y in range(vs):
            t = y / max(1, vs - 1)
            col = np.array(GUN, dtype=np.float32) * (1.22 - 0.3 * t) if face != 'up' else np.array(GUN_L, dtype=np.float32) * 0.96
            tex[v + y, u:u + us, :3] = np.clip(col + rng.normal(0, 1.8, (us, 3)), 0, 255).astype(np.uint8)
        tex[v, u:u + us, :3] = GUN_L
        tex[v + vs - 1, u:u + us, :3] = GUN_D

        def area(y0, y1, x0, x1, c):
            tex[v + y0:v + y1, u + x0:u + x1, :3] = np.clip(np.array(c, dtype=np.float32) + rng.normal(0, 2.0, (y1 - y0, x1 - x0, 3)), 0, 255).astype(np.uint8)

        def line(y0, y1, x0, x1):
            tex[v + y0:v + y1, u + x0:u + x1, :3] = CYAN
            glow[v + y0:v + y1, u + x0:u + x1, :3] = CYAN_L
            glow[v + y0:v + y1, u + x0:u + x1, 3] = 255

        if face in ('east', 'west'):
            area(24, vs - 1, 3, us - 1, NAVY)                 # parte baja hacia la nuca: azul oscuro
            tex[v + 23, u + 3:u + us - 1, :3] = GUN_D
            line(26, 27, 0, 10)                               # circuito: desde la mandibula
            line(17, 27, 9, 10)
            line(17, 18, 9, us)                               # y hacia la nuca
        elif face == 'south':
            area(23, vs - 1, 1, us - 1, NAVY)
            tex[v + 22, u + 1:u + us - 1, :3] = GUN_D
            line(17, 18, 0, 6); line(17, 18, us - 6, us)
            line(17, 26, 5, 6); line(17, 26, us - 6, us - 5)
        elif face == 'up':
            tex[v + 1:v + vs - 1, u + 15:u + 17, :3] = GUN_L
        elif face == 'north':
            area(vs - 3, vs - 1, 1, us - 1, GUN_D)


# ------------------------------------------------------------------ montaje
class Packer:
    def __init__(self, area):
        self.x0, self.y0, self.x1, self.y1 = area
        self.x, self.y, self.row = self.x0, self.y0, 0

    def take(self, w, h):
        if self.x + w > self.x1:
            self.x, self.y, self.row = self.x0, self.y + self.row + 1, 0
        if self.y + h > self.y1:
            raise RuntimeError('sin espacio en la textura')
        at = (self.x, self.y)
        self.x += w + 1
        self.row = max(self.row, h)
        return at


def face_size(s, face):
    sx, sy, sz = s
    d = {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy), 'up': (sx, sz), 'down': (sx, sz)}[face]
    return max(1, int(math.ceil(d[0] * PX - 1e-6))), max(1, int(math.ceil(d[1] * PX - 1e-6)))


def build(out_dir):
    geo = json.load(open(os.path.join(GEO, 'rider_cipher.geo.json')))
    tex = np.array(Image.open(os.path.join(TEX, 'rider_cipher.png')).convert('RGBA'))
    glow = np.array(Image.open(os.path.join(TEX, 'rider_cipher_glowmask.png')).convert('RGBA'))
    x0, y0, x1, y1 = AREA
    if tex[y0:y1, x0:x1, 3].any() or glow[y0:y1, x0:x1, 3].any():
        raise RuntimeError('la franja de la textura ya esta ocupada')
    g = geo['minecraft:geometry'][0]
    bones = {b['name']: b for b in g['bones']}
    head = bones['armorHead']
    shell = head['cubes'][0]
    repaint_shell(tex, glow, shell)
    pk = Packer(AREA)
    cubes = [shell]
    for p in helmet():
        uv = {}
        for face in ('north', 'south', 'east', 'west', 'up', 'down'):
            w, h = face_size(p['s'], face)
            u, v = pk.take(w, h)
            img, gl = paint(p['fs'].get(face, p['st']), w, h, face)
            tex[v:v + h, u:u + w] = img
            if gl[..., 3].any():
                glow[v:v + h, u:u + w] = gl
            uv[face] = {'uv': [u, v], 'uv_size': [w, h]}
        c = {'origin': [round(a, 4) for a in p['o']], 'size': [round(a, 4) for a in p['s']], 'uv': uv}
        if p['rot']:
            c['rotation'] = p['rot']
            c['pivot'] = p['piv']
        cubes.append(c)
    head['cubes'] = cubes
    if 'rx_head' in bones:
        bones['rx_head']['cubes'] = []
    os.makedirs(out_dir, exist_ok=True)
    json.dump(geo, open(os.path.join(out_dir, 'rider_cipher.geo.json'), 'w'), indent='\t')
    Image.fromarray(tex).save(os.path.join(out_dir, 'rider_cipher.png'))
    Image.fromarray(glow).save(os.path.join(out_dir, 'rider_cipher_glowmask.png'))
    print('ok:', len(cubes), 'cubos en armorHead; textura usada hasta y =', pk.y + pk.row)


if __name__ == '__main__':
    out = sys.argv[sys.argv.index('--out') + 1] if '--out' in sys.argv else 'out'
    build(out)
