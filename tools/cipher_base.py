#!/usr/bin/env python3
"""
Kamen Rider Cipher Base, traje completo (diseno propio segun la imagen definitiva del fanfic): gris / plata de base,
azul oscuro secundario, cian para la energia. Casco aprobado (segunda version, de cipher_design.py) + torso, Driver,
brazos, piernas y botas.

  python3 cipher_base.py --out carpeta

Parte de los archivos del repo (rider_cipher.geo.json / .png / _glowmask.png de la 0.56.0) y escribe copias nuevas:
  - Conserva el identificador, los 23 huesos con su jerarquia y pivotes, el hueso weapon (intacto) y los cubos base de
    cada hueso (biped* y la primera pieza de cada armor*: misma forma y mismos UV, repintados).
  - Las piezas extra viejas de armorX se reemplazan por las nuevas. Los huesos rx_* quedan vacios: rider_relief.py
    lleva 'skip' para todas las zonas de rider_cipher y ya no los rellena (Accel y Blaster siguen recibiendo relieve).
  - La textura se limpia: queda solo lo que usan los cubos conservados; las piezas nuevas se acomodan en el espacio
    libre, a 4 px por unidad del modelo. Tambien escribe rider_relief.json con rider_cipher sin rectangulos.
"""
import copy
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cipher_design as cd  # noqa: E402
from cipher_design import C, NAVY, NAVY_D, NAVY_L, GUN, GUN_L, GUN_D, SIL, SIL_L, SIL_D, CYAN, CYAN_L, WHITE  # noqa: E402

REPO = cd.REPO
GEO, TEX = cd.GEO, cd.TEX
RELIEF_JSON = os.path.join(REPO, 'tools', 'rider_relief.json')
PX = 4
DARK = (46, 50, 60)       # gris muy oscuro (guantes, suela, cinturon)
DARK_L = (72, 78, 92)


# ------------------------------------------------------------------ pintores nuevos (los del casco vienen de cd.paint)
def paint(style, w, h, face):
    img = np.zeros((h, w, 4), dtype=np.uint8)
    glow = np.zeros((h, w, 4), dtype=np.uint8)
    rng = np.random.default_rng(w * 131 + h * 17 + sum(map(ord, style + face)))

    def grad(c, k0=1.16, k1=0.86, noise=1.6):
        for y in range(h):
            t = y / max(1, h - 1)
            img[y, :, :3] = np.clip(np.array(c, dtype=np.float32) * (k0 + (k1 - k0) * t) + rng.normal(0, noise, (w, 3)), 0, 255).astype(np.uint8)
        img[..., 3] = 255

    def bevel(hi, lo):
        if w >= 3 and h >= 3:
            img[0, :, :3] = hi; img[:, 0, :3] = hi
            img[-1, :, :3] = lo; img[:, -1, :3] = lo

    def lit(y0, y1, x0, x1):
        y0, y1, x0, x1 = max(0, y0), min(h, y1), max(0, x0), min(w, x1)
        if y0 < y1 and x0 < x1:
            img[y0:y1, x0:x1, :3] = CYAN; img[y0:y1, x0:x1, 3] = 255
            glow[y0:y1, x0:x1, :3] = CYAN_L; glow[y0:y1, x0:x1, 3] = 255

    front = face == 'north'
    if style == 'plate':                     # placa plateada sobria
        grad(SIL, 1.08, 0.86); bevel(SIL_L, SIL_D)
    elif style in ('pecR', 'pecL'):         # pectoral: plata con canto inferior en diagonal azul (forma la V)
        grad(SIL, 1.06, 0.84); bevel(SIL_L, SIL_D)
        if front and w >= 6 and h >= 6:
            for x in range(w):
                t = x / max(1, w - 1)
                if style == 'pecR':
                    t = 1 - t
                y = int(round((h - 3) - t * (h * 0.45)))
                lit(y, y + 1, x, x + 1)
    elif style == 'grey':
        grad(GUN, 1.12, 0.9); bevel(GUN_L, GUN_D)
    elif style == 'navy':
        grad(NAVY, 1.1, 0.88, 2.2); bevel(NAVY_L, NAVY_D)
    elif style == 'abs':                     # abdomen: gris azulado oscuro con ranuras horizontales
        grad((70, 80, 104), 1.05, 0.9, 1.6)
        for y in range(3, h - 1, 4):
            img[y, 1:-1, :3] = (44, 52, 72)
        bevel((96, 106, 128), (40, 46, 62))
    elif style == 'dark':
        grad(DARK, 1.1, 0.9, 1.4); bevel(DARK_L, (30, 32, 40))
    elif style == 'band':                    # cinturon: gris muy oscuro con costura
        grad(DARK, 1.08, 0.92, 1.2)
        if h >= 4:
            img[1, :, :3] = DARK_L; img[-2, :, :3] = DARK_L
    elif style == 'buckle':                  # hebilla del Driver: plata con marco
        grad(SIL, 1.1, 0.84); bevel(SIL_L, SIL_D)
        if front and w >= 8 and h >= 8:
            img[1:-1, 1, :3] = SIL_D; img[1:-1, -2, :3] = SIL_D
    elif style == 'phone':                   # el telefono del Driver: pantalla azul arriba y teclado de codigo 3 x 3 abajo
        grad(DARK, 1.0, 0.9, 1.0); bevel(SIL, SIL_D)                      # bisel plateado
        if front and w >= 7 and h >= 9:
            img[1:-1, 1:-1, :3] = (30, 34, 44)
            lit(2, 2 + max(2, h // 3), 2, w - 2)                            # pantalla
            img[2, 2:w - 2, :3] = (226, 252, 255)
            top = 3 + max(2, h // 3)
            for j in range(3):
                for i in range(3):
                    x, y = 2 + i * 2, top + j * 2
                    if x < w - 1 and y < h - 1:
                        lit(y, y + 1, x, x + 1)
    elif style == 'core':                    # nucleo azul a los lados del Driver
        grad(SIL, 1.05, 0.85); bevel(SIL_L, SIL_D)
        if face in ('east', 'west') and w >= 4 and h >= 4:
            lit(h // 2 - 1, h // 2 + 1, 1, w - 1)
    elif style == 'shin':                    # espinillera: plata con una linea azul vertical
        grad(SIL, 1.08, 0.86); bevel(SIL_L, SIL_D)
        if front and w >= 6:
            lit(1, h - 1, w // 2 - 1, w // 2)
    elif style == 'shoulder':                # hombrera: plata con canto inferior azul
        grad(SIL, 1.1, 0.84); bevel(SIL_L, SIL_D)
        if face in ('north', 'south', 'east', 'west') and h >= 5:
            lit(h - 2, h - 1, 1, w - 1)
    elif style == 'bracer':                  # brazal: plata con una linea azul que lo rodea
        grad(SIL, 1.08, 0.86); bevel(SIL_L, SIL_D)
        pass
    elif style == 'collar':
        grad(GUN, 1.12, 0.9); bevel(GUN_L, GUN_D)
        if front and w >= 6:
            lit(0, h, w // 2 - 1, w // 2 + 1)
    elif style == 'knee':
        grad(SIL, 1.12, 0.88); bevel(SIL_L, SIL_D)
        if front and w >= 6 and h >= 4:
            lit(h - 2, h - 1, 2, w - 2)
    elif style == 'sole':
        grad(DARK, 0.95, 0.8, 1.0)
    elif style == 'toe':
        grad(SIL, 1.06, 0.86); bevel(SIL_L, SIL_D)
    else:
        return cd.paint(style, w, h, face)
    return img, glow


# ------------------------------------------------------------------ geometria
def mirror(c, swap=None):
    m = copy.deepcopy(c)
    m['o'][0] = -(c['o'][0] + c['s'][0])
    if c['piv']:
        m['piv'][0] = -c['piv'][0]
    if c['rot']:
        m['rot'] = [c['rot'][0], -c['rot'][1], -c['rot'][2]]
    if swap and c['st'] in swap:
        m['st'] = swap[c['st']]
    return m


def body():
    zf = -2.5          # frente del peto (cubo de 4 de fondo inflado 0.5)
    zb = 2.5
    out = [
        # pectorales: dos placas plateadas en dos niveles, giradas un poco hacia afuera y con la parte alta adelantada;
        # el canto inferior azul de la placa dibuja la V
        C([-4.0, 18.6, zf - 0.45], [3.7, 4.6, 0.45], 'pecR', rot=[6, 9, 0], piv=[-0.3, 18.6, zf]),
        C([0.3, 18.6, zf - 0.45], [3.7, 4.6, 0.45], 'pecL', rot=[6, -9, 0], piv=[0.3, 18.6, zf]),
        C([-3.7, 21.2, zf - 0.7], [2.9, 1.9, 0.3], 'plate', rot=[6, 9, 0], piv=[-0.3, 18.6, zf]),
        C([0.8, 21.2, zf - 0.7], [2.9, 1.9, 0.3], 'plate', rot=[6, -9, 0], piv=[0.3, 18.6, zf]),
        # cuello: anillo bajo gris
        C([-2.6, 23.7, -2.65], [5.2, 0.6, 5.3], 'collar'),
        # abdomen: panel azul oscuro con ranuras, entre los pectorales y el Driver
        C([-1.9, 14.6, zf - 0.25], [3.8, 3.8, 0.25], 'abs'),
        # espalda: placa plateada con la linea azul de la columna (pintada en la carcasa) y dos aletas bajas
        C([-3.4, 18.4, zb], [2.6, 4.8, 0.4], 'plate'), C([0.8, 18.4, zb], [2.6, 4.8, 0.4], 'plate'),
        C([-2.2, 14.6, zb], [4.4, 2.2, 0.3], 'navy'),
        # ---- Driver: cinturon, hebilla plateada, telefono con pantalla y teclado de codigo, modulos laterales
        C([-4.65, 11.5, -2.75], [9.3, 1.7, 5.5], 'band'),
        C([-2.4, 10.8, -3.5], [4.8, 3.2, 0.8], 'buckle'),
        C([-1.25, 10.95, -3.75], [2.5, 2.9, 0.25], 'dark'),               # hueco del telefono
        C([-1.1, 11.05, -4.15], [2.2, 2.7, 0.45], 'phone'),
        C([-4.95, 11.1, -1.5], [0.6, 2.4, 3.0], 'core'), C([4.35, 11.1, -1.5], [0.6, 2.4, 3.0], 'core'),
        C([2.3, 11.6, -3.25], [1.9, 1.2, 0.5], 'grey'), C([-4.2, 11.6, -3.25], [1.9, 1.2, 0.5], 'grey'),
    ]
    return out


def arm(right=True):
    """Brazo derecho (x -8..-4); el izquierdo es su espejo."""
    parts = [
        # hombrera compacta plateada con canto azul y una lamina gris debajo
        C([-8.85, 21.9, -2.6], [4.6, 2.2, 5.2], 'shoulder'),
        C([-8.55, 24.1, -2.2], [4.0, 0.55, 4.4], 'plate'),
        C([-9.05, 22.2, -2.2], [0.2, 1.8, 4.4], 'plate'),
        C([-8.65, 21.1, -2.4], [3.0, 0.8, 4.8], 'grey'),
        # brazal plateado con linea azul y guante oscuro
        C([-8.6, 13.1, -2.6], [4.7, 3.5, 5.2], 'bracer'),
        C([-8.5, 11.9, -2.5], [4.5, 1.25, 5.0], 'dark'),
    ]
    return parts if right else [mirror(c) for c in parts]


def leg(right=True):
    """Pierna derecha (x -3.9..0.1): rodillera y muslo en armorRightLeg; espinillera, puntera y suela en la bota."""
    leg_parts = [C([-3.55, 5.0, -2.85], [3.3, 2.4, 0.45], 'plate'),
                 C([-3.15, 5.4, -3.15], [2.5, 1.7, 0.35], 'knee')]
    boot_parts = [
        C([-3.6, 1.5, -2.8], [3.4, 3.4, 0.45], 'shin'),
        C([-3.3, 4.4, -3.0], [2.8, 0.7, 0.25], 'plate'),                   # borde alto de la espinillera
        C([-4.2, 0.0, -3.15], [4.6, 0.9, 1.0], 'toe'),                      # puntera
        C([-3.95, 0.85, -3.0], [4.1, 0.65, 0.75], 'toe', rot=[-18, 0, 0], piv=[-1.9, 0.85, -2.25]),   # empeine inclinado
        C([-4.25, -0.05, -2.3], [4.7, 0.35, 4.6], 'sole'),
    ]
    if not right:
        leg_parts = [mirror(c) for c in leg_parts]
        boot_parts = [mirror(c) for c in boot_parts]
        for c in leg_parts + boot_parts:
            pass
    return leg_parts, boot_parts


# ------------------------------------------------------------------ carcasas repintadas
def shell_paint(tex, glow, cube, kind):
    """Pinta los cubos base (mismos UV): traje gris con lineas de energia. kind: body, rarm, larm, rleg, lleg,
    rboot, lboot, suit (cubos biped bajo la armadura)."""
    for face, f in cube['uv'].items():
        (u, v), (us, vs) = f['uv'], f['uv_size']
        u, v, us, vs = int(round(u)), int(round(v)), int(round(us)), int(round(vs))
        if us <= 0 or vs <= 0:
            continue
        rng = np.random.default_rng(u * 7 + v * 3)
        base = (96, 104, 120) if kind != 'suit' else (84, 90, 104)   # traje gris oscuro: las placas plateadas resaltan
        for y in range(vs):
            t = y / max(1, vs - 1)
            k = 1.14 - 0.24 * t
            tex[v + y, u:u + us, :3] = np.clip(np.array(base, dtype=np.float32) * k + rng.normal(0, 1.6, (us, 3)), 0, 255).astype(np.uint8)
        tex[v:v + vs, u:u + us, 3] = 255
        glow[v:v + vs, u:u + us] = 0
        tex[v, u:u + us, :3] = (128, 136, 152)
        tex[v + vs - 1, u:u + us, :3] = (62, 68, 82)

        def area(y0, y1, x0, x1, c):
            y0, y1, x0, x1 = max(0, y0), min(vs, y1), max(0, x0), min(us, x1)
            if y0 < y1 and x0 < x1:
                tex[v + y0:v + y1, u + x0:u + x1, :3] = np.clip(np.array(c, dtype=np.float32) + rng.normal(0, 1.8, (y1 - y0, x1 - x0, 3)), 0, 255).astype(np.uint8)

        def line(y0, y1, x0, x1):
            y0, y1, x0, x1 = max(0, y0), min(vs, y1), max(0, x0), min(us, x1)
            if y0 < y1 and x0 < x1:
                tex[v + y0:v + y1, u + x0:u + x1, :3] = CYAN
                glow[v + y0:v + y1, u + x0:u + x1, :3] = CYAN_L
                glow[v + y0:v + y1, u + x0:u + x1, 3] = 255

        if kind == 'suit':
            continue
        if kind == 'body':
            if face == 'north':
                # V desde los hombros hasta el centro (la tapan en parte los pectorales) y linea central hasta el Driver
                for x in range(us):
                    t = abs(x - (us - 1) / 2) / ((us - 1) / 2)
                    y = int(round(4 + (1 - t) * 18))
                    line(y, y + 1, x, x + 1)
                line(22, vs, us // 2 - 1, us // 2 + 1)
            elif face == 'south':
                area(20, vs - 6, 4, us - 4, NAVY)
                line(2, vs - 6, us // 2 - 1, us // 2 + 1)     # columna
            elif face in ('east', 'west'):
                area(2, vs - 6, 3, us - 3, NAVY)               # costados azul oscuro
                line(4, vs - 6, us // 2, us // 2 + 1)
        elif kind in ('rarm', 'larm'):
            outer = 'west' if kind == 'rarm' else 'east'
            if face == outer:
                line(6, vs - 6, us // 2 - 1, us // 2 + 1)      # linea por fuera del brazo
            elif face == 'north':
                line(18, 32, us // 2, us // 2 + 1)
            elif face in ('east', 'west'):
                area(14, vs - 8, 3, us - 3, NAVY)              # cara interior azul oscuro
        elif kind in ('rleg', 'lleg'):
            outer = 'west' if kind == 'rleg' else 'east'
            if face == outer:
                line(1, vs - 2, us // 2 - 1, us // 2 + 1)
            elif face == 'north':
                # sigue la linea central del torso: baja desde la entrepierna en diagonal y luego por el centro del muslo
                inner = us - 2 if kind == 'rleg' else 1
                mid = us // 2 - 1 if kind == 'rleg' else us // 2
                steps = abs(inner - mid)
                for k in range(steps + 1):
                    x = inner + (mid - inner) * k // max(1, steps)
                    line(1 + k, 2 + k, x, x + 1)
                line(1 + steps, vs - 8, mid, mid + 1)
            elif face in ('east', 'west'):
                area(2, vs - 2, 3, us - 3, NAVY)
        elif kind in ('rboot', 'lboot'):
            area(vs // 2, vs - 1, 0, us, NAVY if face != 'up' else GUN)   # bota: mitad baja azul oscuro
            outer = 'west' if kind == 'rboot' else 'east'
            if face == outer:
                line(2, vs // 2 + 2, us // 2, us // 2 + 1)


# ------------------------------------------------------------------ espacio libre en la textura
class FreeSpace:
    def __init__(self, occ):
        self.occ = occ.copy()

    def take(self, w, h):
        H, W = self.occ.shape
        # tabla de sumas para buscar el primer hueco w x h libre (con 1 px de margen)
        ww, hh = w + 1, h + 1
        s = np.pad(self.occ.astype(np.int32), ((1, 0), (1, 0))).cumsum(0).cumsum(1)
        for y in range(0, H - hh + 1):
            row = s[y + hh, ww:] - s[y, ww:] - s[y + hh, :W - ww + 1] + s[y, :W - ww + 1]
            xs = np.nonzero(row == 0)[0]
            if len(xs):
                x = int(xs[0])
                self.occ[y:y + hh, x:x + ww] = True
                return x, y
        raise RuntimeError('sin espacio en la textura para %dx%d' % (w, h))


def face_size(s, face):
    return cd.face_size(s, face)


def uv_occ(cubes, shape):
    occ = np.zeros(shape, dtype=bool)
    for c in cubes:
        uv = c.get('uv')
        if isinstance(uv, dict):
            for f in uv.values():
                (u, v), (w, h) = f['uv'], f['uv_size']
                x0, x1 = sorted((int(math.floor(u)), int(math.ceil(u + w))))
                y0, y1 = sorted((int(math.floor(v)), int(math.ceil(v + h))))
                occ[max(0, y0):y1, max(0, x0):x1] = True
    return occ


def build(out_dir):
    geo = json.load(open(os.path.join(GEO, 'rider_cipher.geo.json')))
    tex = np.array(Image.open(os.path.join(TEX, 'rider_cipher.png')).convert('RGBA'))
    glow = np.array(Image.open(os.path.join(TEX, 'rider_cipher_glowmask.png')).convert('RGBA'))
    g = geo['minecraft:geometry'][0]
    bones = {b['name']: b for b in g['bones']}

    # 1) se quedan los cubos base de cada hueso y el arma; todo lo demas se rehace
    for b in g['bones']:
        n = b['name']
        if n.startswith('rx_'):
            b['cubes'] = []
        elif n.startswith('armor'):
            b['cubes'] = b['cubes'][:1]
    kept = [c for b in g['bones'] for c in b.get('cubes', [])]
    occ = uv_occ(kept, tex.shape[:2])
    tex[~occ] = 0
    glow[~occ] = 0
    space = FreeSpace(occ)

    def add(bone, parts):
        for p in parts:
            uv = {}
            for face in ('north', 'south', 'east', 'west', 'up', 'down'):
                w, h = face_size(p['s'], face)
                u, v = space.take(w, h)
                img, gl = paint(p['fs'].get(face, p['st']), w, h, face)
                tex[v:v + h, u:u + w] = img
                glow[v:v + h, u:u + w] = gl if gl[..., 3].any() else 0
                uv[face] = {'uv': [u, v], 'uv_size': [w, h]}
            c = {'origin': [round(a, 4) for a in p['o']], 'size': [round(a, 4) for a in p['s']], 'uv': uv}
            if p['rot']:
                c['rotation'] = p['rot']
                c['pivot'] = p['piv']
            bones[bone]['cubes'].append(c)

    # 2) casco aprobado (segunda version)
    cd.repaint_shell(tex, glow, bones['armorHead']['cubes'][0])
    add('armorHead', cd.helmet())
    # 3) torso y Driver
    shell_paint(tex, glow, bones['armorBody']['cubes'][0], 'body')
    add('armorBody', body())
    # 4) brazos
    shell_paint(tex, glow, bones['armorRightArm']['cubes'][0], 'rarm')
    shell_paint(tex, glow, bones['armorLeftArm']['cubes'][0], 'larm')
    add('armorRightArm', arm(True))
    add('armorLeftArm', arm(False))
    # 5) piernas y botas
    for side, r in (('Right', True), ('Left', False)):
        k = 'r' if r else 'l'
        shell_paint(tex, glow, bones['armor%sLeg' % side]['cubes'][0], k + 'leg')
        shell_paint(tex, glow, bones['armor%sBoot' % side]['cubes'][0], k + 'boot')
        lp, bp = leg(r)
        if not r:
            # la pierna izquierda esta en +x: el espejo ya la pone ahi (las piernas se separan 1.9 del centro)
            pass
        add('armor%sLeg' % side, lp)
        add('armor%sBoot' % side, bp)
    # 6) el traje bajo la armadura (cubos biped), por si se asoma en algun hueco
    for n in ('bipedBody', 'bipedRightArm', 'bipedLeftArm', 'bipedRightLeg', 'bipedLeftLeg'):
        shell_paint(tex, glow, bones[n]['cubes'][0], 'suit')

    os.makedirs(out_dir, exist_ok=True)
    json.dump(geo, open(os.path.join(out_dir, 'rider_cipher.geo.json'), 'w'), indent=1)
    Image.fromarray(tex).save(os.path.join(out_dir, 'rider_cipher.png'))
    Image.fromarray(glow).save(os.path.join(out_dir, 'rider_cipher_glowmask.png'))
    side = json.load(open(RELIEF_JSON))
    side['rider_cipher'] = {'rects': [], 'orig_h': tex.shape[0]}
    json.dump(side, open(os.path.join(out_dir, 'rider_relief.json'), 'w'), indent=1, sort_keys=True)
    used = int(np.nonzero(space.occ.any(1))[0].max()) + 1
    print('ok:', sum(len(b.get('cubes', [])) for b in g['bones']), 'cubos; textura ocupada hasta y =', used)


if __name__ == '__main__':
    build(sys.argv[sys.argv.index('--out') + 1] if '--out' in sys.argv else 'out_base')
