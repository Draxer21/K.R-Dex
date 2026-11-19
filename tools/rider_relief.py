#!/usr/bin/env python3
"""
Relieve de los trajes Rider: piezas 3D nuevas (diseno propio) para Nine, Forge, Cipher, Warden, Spore, Mirage y Proto.

  python3 tools/rider_relief.py             # regenera todo (se puede repetir las veces que haga falta)
  python3 tools/rider_relief.py cipher      # solo un traje
  python3 tools/rider_relief.py --clean     # quita las piezas y deja modelos y texturas como estaban

Como funciona:
  - Cada pieza es un cubo con textura propia pintada por codigo (degradado, bisel, remaches, vetas, laminillas...)
    en el estilo de las texturas que ya existen, a 4 px por unidad del modelo.
  - Los rectangulos nuevos se acomodan en los huecos libres de cada textura (y de su _glowmask si brillan) y se
    anotan en tools/rider_relief.json, para poder borrarlos y rehacerlos sin ensuciar la textura.
  - Las piezas van en huesos nuevos "rx_<zona>" colgados de armorHead, armorBody, armorRightArm...
  - Las variantes (formas de Nine y de Forge, Accel y Blaster de Cipher) reciben las mismas piezas, salvo las que
    chocarian con las partes propias de esa variante.

Coordenadas: -x es la derecha del personaje, +y arriba, -z el frente (igual que en los .geo.json).
Los disenos son propios y siguen las descripciones del fanfic; no copian a ningun Rider de la tele.
Psyga, Kaixa y Orga no se tocan. Para ver el resultado sin esperar al CI: tools/geo_preview.py.
"""
import json
import os
import sys
import zlib

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from geo_preview import rot_matrix  # noqa: E402

ROOT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'dexrider')
TEX = os.path.join(ROOT, 'textures', 'item', 'armor')
GEO = os.path.join(ROOT, 'geckolib', 'models', 'item', 'armor')
SIDE = os.path.join(HERE, 'rider_relief.json')
PX = 4
PREFIX = 'rx_'
PARENT = {'head': 'armorHead', 'body': 'armorBody', 'rarm': 'armorRightArm', 'larm': 'armorLeftArm',
          'rleg': 'armorRightLeg', 'lleg': 'armorLeftLeg', 'rboot': 'armorRightBoot', 'lboot': 'armorLeftBoot'}
# huesos que se mueven juntos (para avisar de caras que se pisan)
RIGID = {'head': ['bipedHead', 'armorHead'], 'body': ['bipedBody', 'armorBody'],
         'rarm': ['bipedRightArm', 'armorRightArm'], 'larm': ['bipedLeftArm', 'armorLeftArm'],
         'rleg': ['bipedRightLeg', 'armorRightLeg', 'armorRightBoot'], 'rboot': ['bipedRightLeg', 'armorRightLeg', 'armorRightBoot'],
         'lleg': ['bipedLeftLeg', 'armorLeftLeg', 'armorLeftBoot'], 'lboot': ['bipedLeftLeg', 'armorLeftLeg', 'armorLeftBoot']}
SAME = {'rleg': 'rboot', 'rboot': 'rleg', 'lleg': 'lboot', 'lboot': 'lleg'}


# ------------------------------------------------------------------ piezas

def C(o, s, st, rot=None, piv=None, **fs):
    """Un cubo: origen (esquina minima), tamano, estilo; fs = estilo distinto para una cara (down='gil')."""
    return {'o': list(o), 's': list(s), 'st': st, 'rot': rot, 'piv': piv, 'fs': fs}


def mir(c, swap=None):
    """El mismo cubo al otro lado del cuerpo."""
    swap = swap or {}
    o, s = c['o'], c['s']
    fs = {{'east': 'west', 'west': 'east'}.get(k, k): swap.get(v, v) for k, v in c['fs'].items()}
    return {'o': [round(-(o[0] + s[0]), 3), o[1], o[2]], 's': list(s), 'st': swap.get(c['st'], c['st']),
            'rot': None if c['rot'] is None else [c['rot'][0], -c['rot'][1], -c['rot'][2]],
            'piv': None if c['piv'] is None else [-c['piv'][0], c['piv'][1], c['piv'][2]], 'fs': fs}


def both(cubes, swap=None):
    return list(cubes) + [mir(c, swap) for c in cubes]


def limbs(p, right, left, cubes, swap=None):
    p.setdefault(right, []).extend(cubes)
    p.setdefault(left, []).extend(mir(c, swap) for c in cubes)


# ------------------------------------------------------------------ pintura

def rgb(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


def hexs(v):
    return '#%02x%02x%02x' % tuple(int(max(0, min(255, round(float(x))))) for x in v)


def mul(h, f):
    return hexs(rgb(h) * f)


def mix(h, h2, t):
    return hexs(rgb(h) * (1 - t) + rgb(h2) * t)


def mode_color(px, sat=True):
    """El color mas repetido de un trozo de textura (si sat, entre los pixeles con color cuando son bastantes)."""
    px = px[px[:, 3] > 0][:, :3].astype(int)
    if sat:
        s = px[(px.max(1) - px.min(1)) > 50]
        if len(s) >= 0.4 * len(px):
            px = s
    q = px // 16
    keys, counts = np.unique(q, axis=0, return_counts=True)
    return hexs(px[(q == keys[counts.argmax()]).all(1)].mean(0))


def face_px(sheet, cube, face, u0=0.0, u1=1.0):
    (u, v), (w, h) = cube['uv'][face]['uv'], cube['uv'][face]['uv_size']
    return sheet.tex[int(v):int(v + h), int(u + w * u0):int(u + w * u1)].reshape(-1, 4)


def inflated(bones, name):
    return next(c for c in bones[name]['cubes'] if c.get('inflate'))


def chest_cube(bones):
    """La placa del pecho: el cubo mas adelantado que cubre el centro del torso."""
    cs = [c for c in bones['armorBody']['cubes'] if not c.get('inflate') and not c.get('rotation')
          and c['origin'][1] <= 20.5 <= c['origin'][1] + c['size'][1] and c['origin'][0] <= -1 <= 1 <= c['origin'][0] + c['size'][0]
          and c['origin'][2] < 0]
    return min(cs, key=lambda c: c['origin'][2])


FACE_K = {'north': 1.0, 'south': 1.0, 'east': 0.93, 'west': 0.93, 'up': 1.05, 'down': 0.80}


def paint(st, w, h, face, seed):
    """Pinta una cara de w x h px. Devuelve (RGBA, mascara de brillo)."""
    rng = np.random.default_rng(seed)
    kind = st.get('kind', 'plate')
    col = rgb(st['col'])
    yy, xx = np.mgrid[0:h, 0:w]
    t = yy / (h - 1) if h > 1 else np.full((h, w), 0.5)
    a = np.empty((h, w, 3), np.float32)
    a[:] = col
    glow = np.zeros((h, w), bool)
    k = FACE_K[face]
    if kind == 'glow':
        a *= (1.05 - 0.12 * t)[..., None]
        glow[:] = True
    elif kind == 'prism':
        stops = [rgb('#4c8cf8'), rgb('#a468f0'), rgb('#f8c848')]
        u = xx / (w - 1) if w > 1 else np.full((h, w), 0.5)
        lo = np.clip(u * 2, 0, 1)[..., None]
        hi = np.clip(u * 2 - 1, 0, 1)[..., None]
        a = stops[0] * (1 - lo) + stops[1] * lo
        a = a * (1 - hi) + stops[2] * hi
        glow[:] = True
    elif kind == 'dark':
        a *= (0.75 + 0.45 * t)[..., None] * k
    elif kind == 'lamella':             # laminillas de Spore: filas crema, costura violeta
        rows = yy % 4
        for r, c in enumerate(('#d8c8a0', '#ccbc96', '#bcac8a', '#7c745c')):
            a[rows == r] = rgb(c)
        a[(xx % 4 == 1) & (rows <= 1)] = rgb('#6c54a4')
        if h < 3:
            a[:] = rgb('#ccbc96')
        a *= k
    elif kind == 'gills':               # laminillas de debajo del sombrero
        a[xx % 2 == 1] *= 0.74
        a *= k
    else:                               # placa
        a *= (1 + st.get('grad', 0.14) * (0.5 - t))[..., None] * k
        if st.get('grid') and w >= 5 and h >= 5:
            p = st.get('gridp', 3)
            a[(xx % p == p - 1) | (yy % p == p - 1)] = rgb(st['grid']) * k
        if st.get('ribs') and w >= 8 and h >= 6:
            for x in range(2, w - 2, 3):
                a[1:h - 1, x] = rgb(st['ribs']) * k
        if st.get('slots') and w >= 6 and h >= 5:
            sc = col * 0.38 if st['slots'] is True else rgb(st['slots'])
            for y in range(2, h - 2, 3):
                a[y, 2:w - 2] = sc * k
        if st.get('veins') and h >= 6 and w >= 5:
            for x0 in ([w // 2] if w < 14 else [w // 3, (2 * w) // 3]):
                for y in range(1, h - 1):
                    a[y, x0 + (1 if (y // 2) % 2 else 0)] = rgb(st['veins']) * k
        if st.get('streaks') and w >= 4 and h >= 4:
            m = (xx + yy) % 6 == 0
            a[m] = a[m] * 0.3 + 255 * 0.7
        if st.get('spots') and w >= 7 and h >= 7:
            for _ in range(max(1, (w * h) // 110)):
                cx, cy, r = rng.integers(2, w - 2), rng.integers(2, h - 2), int(rng.choice([1, 1, 2]))
                a[(xx - cx) ** 2 + (yy - cy) ** 2 <= r * r] = rgb(st['spots']) * k
        if st.get('trace') and w >= 9 and h >= 6:       # eslabones de circuito
            tc = rgb(st['trace']) * k
            base = a.copy()
            for row, y0 in enumerate(range(2, h - 4, 6)):
                x0 = 1 + (3 if row % 2 else 0)
                while x0 + 8 <= w - 1:
                    for ox in (x0, x0 + 5):
                        a[y0:y0 + 3, ox:ox + 3] = tc
                        a[y0 + 1, ox + 1] = base[y0 + 1, ox + 1]
                    a[y0 + 1, x0 + 3:x0 + 5] = tc
                    x0 += 11
        if st.get('bevel', True):
            if h >= 3:
                a[0, :] *= 1.14
                a[-1, :] *= 0.74
            elif h == 2:
                a[1, :] *= 0.84
            if w >= 3:
                a[:, 0] *= 1.07
                a[:, -1] *= 0.86
        if st.get('band') and h > st['band'][1] + 2:
            a[h - st['band'][1]:, :] = rgb(st['band'][0]) * k
        if st.get('edge') and h >= 3:
            a[h - 1, :] = rgb(st['edge'])
        if st.get('rivets') and w >= 7 and h >= 7:
            rc = col * 0.6 if st['rivets'] is True else rgb(st['rivets'])
            for (y, x) in ((1, 1), (1, w - 2), (h - 2, 1), (h - 2, w - 2)):
                a[y, x] = rc * k
    a += rng.normal(0, st.get('noise', 1.6), (h, w, 1))
    out = np.zeros((h, w, 4), np.uint8)
    out[..., :3] = np.clip(a, 0, 255).astype(np.uint8)
    out[..., 3] = 255
    return out, glow


# ------------------------------------------------------------------ hoja de textura

class Sheet:
    def __init__(self, model, old):
        self.tp = os.path.join(TEX, model + '.png')
        self.gp = os.path.join(TEX, model + '_glowmask.png')
        self.tex = np.array(Image.open(self.tp).convert('RGBA'))
        self.glow = np.array(Image.open(self.gp).convert('RGBA')) if os.path.exists(self.gp) else np.zeros_like(self.tex)
        for x, y, w, h in old.get('rects', []):
            self.tex[y:y + h, x:x + w] = 0
            self.glow[y:y + h, x:x + w] = 0
        self.orig_h = old.get('orig_h', self.tex.shape[0])
        if self.tex.shape[0] > self.orig_h and not self.tex[self.orig_h:, :, 3].any():     # volver al alto original
            self.tex, self.glow = self.tex[:self.orig_h].copy(), self.glow[:self.orig_h].copy()
        self.rects, self.cache = [], {}
        self.occ = (self.tex[..., 3] > 0) | (self.glow[..., 3] > 0)
        rows = np.where(self.occ.any(axis=1))[0]
        self.pref = int(rows.max()) + 2 if len(rows) else 0

    def alloc(self, w, h):
        H, W = self.occ.shape
        if w > W or h > H:
            return None
        o = self.occ
        d = o.copy()
        d[1:] |= o[:-1]; d[:-1] |= o[1:]; d[:, 1:] |= o[:, :-1]; d[:, :-1] |= o[:, 1:]
        integ = np.zeros((H + 1, W + 1), np.int32)
        integ[1:, 1:] = d.cumsum(0).cumsum(1)
        free = (integ[h:, w:] - integ[:-h, w:] - integ[h:, :-w] + integ[:-h, :-w]) == 0
        for start in (min(self.pref, H - h), 0):
            idx = np.argwhere(free[start:])
            if len(idx):
                return int(idx[0][1]), int(idx[0][0] + start)
        return None

    def put(self, key, st, w, h, face):
        if key in self.cache:
            return
        pos = self.alloc(w, h)
        if pos is None:                 # no cabe: duplicar el alto (los UV siguen valiendo)
            pad = np.zeros_like(self.tex)
            self.tex, self.glow = np.concatenate([self.tex, pad]), np.concatenate([self.glow, pad.copy()])
            self.occ = np.concatenate([self.occ, np.zeros_like(self.occ)])
            pos = self.alloc(w, h)
        x, y = pos
        img, glow = paint(st, w, h, face, zlib.crc32(repr(key).encode()))
        self.tex[y:y + h, x:x + w] = img
        if glow.any():
            g = np.zeros_like(img)
            g[glow] = img[glow]
            self.glow[y:y + h, x:x + w] = g
        self.occ[y:y + h, x:x + w] = True
        self.rects.append([x, y, w, h])
        self.cache[key] = (x, y)

    def save(self):
        Image.fromarray(self.tex).save(self.tp)
        if os.path.exists(self.gp) or self.glow[..., 3].any():
            Image.fromarray(self.glow).save(self.gp)


# ------------------------------------------------------------------ geometria

CLASS = {'north': 'ns', 'south': 'ns', 'east': 'ew', 'west': 'ew', 'up': 'up', 'down': 'dn'}
CLASS_FACE = {'ns': 'north', 'ew': 'east', 'up': 'up', 'dn': 'down'}


def face_dims(s):
    px = lambda v: max(1, int(round(v * PX)))
    return {'north': (px(s[0]), px(s[1])), 'south': (px(s[0]), px(s[1])), 'east': (px(s[2]), px(s[1])),
            'west': (px(s[2]), px(s[1])), 'up': (px(s[0]), px(s[2])), 'down': (px(s[0]), px(s[2]))}


def keys_of(c):
    return {face: (c['fs'].get(face, c['st']), w, h, CLASS[face]) for face, (w, h) in face_dims(c['s']).items()}


def to_geo(c, sheet):
    uv = {}
    for face, key in keys_of(c).items():
        x, y = sheet.cache[key]
        uv[face] = {'uv': [x, y], 'uv_size': [key[1], key[2]]}
    out = {'origin': [round(v, 3) for v in c['o']], 'size': [round(v, 3) for v in c['s']], 'uv': uv}
    if c['rot']:
        out['rotation'] = c['rot']
        out['pivot'] = [round(v, 3) for v in (c['piv'] or c['o'])]
    return out


def aabb(origin, size, inflate=0, rot=None, piv=None):
    lo = np.array(origin, float) - inflate
    hi = lo + np.array(size, float) + 2 * inflate
    if not rot:
        return lo, hi
    pts = np.array([[x, y, z] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
    p = np.array(piv if piv else origin, float)
    pts = (pts - p) @ rot_matrix(rot).T + p
    return pts.min(0), pts.max(0)


def geo_box(c):
    return aabb(c['origin'], c['size'], c.get('inflate', 0), c.get('rotation'), c.get('pivot'))


def spec_box(c):
    return aabb(c['o'], c['s'], 0, c['rot'], c['piv'])


def overlap(a, b):
    return float(np.prod(np.clip(np.minimum(a[1], b[1]) - np.maximum(a[0], b[0]), 0, None)))


def coplanar(a, b, eps=0.012):
    """Caras que miran al mismo lado, en el mismo plano y que se pisan (parpadearian en el juego)."""
    for ax in range(3):
        o = [i for i in range(3) if i != ax]
        if all(min(a[1][i], b[1][i]) - max(a[0][i], b[0][i]) > 0.05 for i in o):
            if abs(a[1][ax] - b[1][ax]) < eps or abs(a[0][ax] - b[0][ax]) < eps:
                return 'xyz'[ax]
    return None


def sig(c):
    return tuple(round(v, 2) for v in c['origin']) + tuple(round(v, 2) for v in c['size'])


def load(model):
    return json.load(open(os.path.join(GEO, model + '.geo.json')))


def process(model, spec, parts, base_bones, side, clean, report):
    d = load(model)
    g = d['minecraft:geometry'][0]
    old = side.get(model, {})
    sheet = Sheet(model, old)
    g['bones'] = [b for b in g['bones'] if not b['name'].startswith(PREFIX)]
    g['description']['texture_height'] = sheet.tex.shape[0]
    bones = {b['name']: b for b in g['bones']}
    if clean:
        sheet.save()
        json.dump(d, open(os.path.join(GEO, model + '.geo.json'), 'w'), indent=1)
        side.pop(model, None)
        return
    # las caras que ya existen reservan su sitio en la hoja aunque esten vacias (hay caras transparentes a proposito)
    for bone in g['bones']:
        for c in bone.get('cubes', []):
            if isinstance(c.get('uv'), dict):
                for f in c['uv'].values():
                    (u, v), (w, h) = f['uv'], f['uv_size']
                    x0, x1 = sorted((int(np.floor(u)), int(np.ceil(u + w))))
                    y0, y1 = sorted((int(np.floor(v)), int(np.ceil(v + h))))
                    sheet.occ[max(0, y0):y1, max(0, x0):x1] = True
    use = {}
    for grp, cubes in parts.items():
        parent = PARENT[grp]
        if parent not in bones:
            continue
        # partes propias de la variante: no se les pone nada encima
        own = [geo_box(c) for n in RIGID[grp] if n in bones for c in bones[n].get('cubes', [])
               if sig(c) not in base_bones.get(n, set())]
        keep = [c for c in cubes if not any(overlap(spec_box(c), o) > 0.02 for o in own)]
        if len(keep) != len(cubes):
            report.append(f'{model}: {grp} sin {len(cubes) - len(keep)} pieza(s) que chocan con la variante')
        use[grp] = keep
    # aviso de caras coplanares (solo cubos sin rotar)
    for grp, cubes in use.items():
        others = [(n, geo_box(c)) for n in RIGID[grp] if n in bones for c in bones[n].get('cubes', []) if not c.get('rotation')]
        mine = [(grp, spec_box(c)) for c in cubes if not c['rot']]
        mine += [(SAME[grp], spec_box(c)) for c in use.get(SAME.get(grp), []) if not c['rot']] if grp in SAME else []
        for i, c in enumerate(cubes):
            if c['rot']:
                continue
            box = spec_box(c)
            for name, o in others + [m for m in mine if m[1] is not box and not (np.allclose(m[1][0], box[0]) and np.allclose(m[1][1], box[1]))]:
                ax = coplanar(box, o)
                if ax:
                    report.append(f'{model}: {grp}[{i}] {c["o"]} {c["s"]} coplanar en {ax} con {name}')
                    break
    reqs = {}
    for cubes in use.values():
        for c in cubes:
            for key in keys_of(c).values():
                reqs[key] = True
    # las formas cambian de color: los estilos pueden salir de colores tomados de la textura de cada modelo
    styles = spec['styles'](spec['tones'](bones, sheet)) if 'tones' in spec else spec['styles']
    for key in sorted(reqs, key=lambda k: (-k[2], -k[1], k[0], k[3])):
        sheet.put(key, styles[key[0]], key[1], key[2], CLASS_FACE[key[3]])
    for grp, cubes in use.items():
        if cubes:
            g['bones'].append({'name': PREFIX + grp, 'parent': PARENT[grp], 'pivot': bones[PARENT[grp]].get('pivot', [0, 0, 0]),
                               'cubes': [to_geo(c, sheet) for c in cubes]})
    g['description']['texture_height'] = sheet.tex.shape[0]
    sheet.save()
    json.dump(d, open(os.path.join(GEO, model + '.geo.json'), 'w'), indent=1)
    side[model] = {'rects': sheet.rects, 'orig_h': sheet.orig_h}
    report.append(f'{model}: {sum(len(c) for c in use.values())} piezas, {len(sheet.rects)} rectangulos, textura {sheet.tex.shape[1]}x{sheet.tex.shape[0]}')


# ------------------------------------------------------------------ disenos

def cipher():
    """Azul marino y plata; ingeniero con Driver modular: rieles, anclajes y cartuchos."""
    p = {}
    p['head'] = [
        C([-4.5, 29.5, -5.25], [9.0, 0.8, 0.85], 'dnavy'),                                  # ceja sobre el visor
        *both([C([-4.4, 23.9, -5.15], [1.8, 3.4, 0.55], 'sil')]),                           # mejillas
        C([-5.5, 27.2, -1.8], [0.9, 2.6, 3.4], 'sil'), C([-5.75, 28.0, -0.6], [0.25, 1.0, 1.0], 'lit'),   # modulo de radio
        C([-3.4, 26.2, 4.6], [6.8, 4.6, 0.45], 'navys'), C([-1.2, 30.8, 4.6], [2.4, 1.2, 0.4], 'sil'),    # placa trasera
    ]
    p['body'] = [
        *both([C([-4.6, 17.0, -3.45], [1.4, 6.4, 0.5], 'navy')]), C([-3.2, 16.3, -3.45], [6.4, 0.8, 0.5], 'navy'),   # marco del peto
        *[C([0.9 + i * 0.8, 17.3, -3.6], [0.55, 1.3, 0.6], 'gray') for i in range(3)],      # cartuchos
        *[C([0.9 + i * 0.8, 18.6, -3.6], [0.55, 0.4, 0.6], 'lit') for i in range(3)],
        C([-0.8, 17.0, 3.0], [1.6, 6.4, 0.6], 'sils'), C([-0.8, 14.2, 2.5], [1.6, 2.8, 0.6], 'sils'),     # riel de la espalda
        *both([C([-3.7, 17.7, 3.0], [2.2, 5.0, 1.1], 'navy'), C([-3.5, 22.7, 3.0], [1.8, 0.6, 1.3], 'sil'),
               C([-3.5, 17.2, 3.0], [1.8, 0.5, 1.3], 'sil'), C([-3.0, 19.3, 4.1], [0.8, 1.8, 0.15], 'lit')]),   # celdas
        C([-2.2, 11.4, 2.7], [4.4, 2.6, 0.9], 'navy'), C([-0.5, 12.2, 3.6], [1.0, 1.0, 0.2], 'sil'),
    ]
    limbs(p, 'rarm', 'larm', [
        C([-9.6, 24.2, -3.2], [4.8, 0.6, 6.4], 'sil'), C([-8.7, 24.8, -1.4], [2.8, 0.6, 2.8], 'navy'),    # anclaje del hombro
        C([-7.7, 25.4, -0.4], [0.8, 0.3, 0.8], 'sil'), C([-9.85, 20.6, -2.2], [0.55, 3.0, 4.4], 'navyr')])
    p['rarm'].append(C([-9.0, 13.2, -1.2], [0.5, 3.8, 2.4], 'sils'))                        # riel del antebrazo
    p['rleg'] = [C([-5.1, 7.4, -1.6], [0.8, 3.4, 3.2], 'navy'),                             # porta-cartuchos
                 *[C([-5.45, 8.0, -1.3 + i * 1.0], [0.4, 2.0, 0.6], 'gray') for i in range(3)],
                 *[C([-5.45, 10.0, -1.3 + i * 1.0], [0.4, 0.4, 0.6], 'lit') for i in range(3)]]
    p['lleg'] = [C([4.3, 7.6, -1.4], [0.8, 2.8, 2.8], 'navy'), C([4.25, 9.8, -1.5], [0.95, 0.7, 3.0], 'sil')]
    limbs(p, 'rleg', 'lleg', [C([-3.6, 4.4, -3.3], [3.4, 2.6, 0.5], 'silr')])               # rodilleras
    limbs(p, 'rboot', 'lboot', [C([-2.3, 1.6, -2.85], [0.8, 2.9, 0.6], 'navy'), C([-3.2, 0.6, 2.25], [2.6, 2.2, 0.5], 'navy')])
    return p


def warden():
    """Negro, plata y naranja; agente de la Comision: gorra con visera, placa, charreteras, cinturon de servicio."""
    p = {}
    p['head'] = [
        C([-4.65, 29.6, -5.2], [9.3, 0.8, 0.8], 'dk'), C([-3.6, 30.4, -6.3], [7.2, 0.4, 1.9], 'dk'),       # ceja y visera
        C([-4.8, 30.8, -4.8], [9.6, 0.8, 0.4], 'gun'), C([-4.8, 30.8, 4.4], [9.6, 0.8, 0.4], 'gun'),      # cinta de la gorra
        *both([C([-4.8, 30.8, -4.4], [0.4, 0.8, 8.8], 'gun')]),
        C([-0.8, 30.5, -5.15], [1.6, 1.5, 0.35], 'sil'), C([-0.3, 30.9, -5.27], [0.6, 0.7, 0.14], 'or'),  # insignia
        C([-2.9, 23.5, -5.2], [5.8, 0.8, 0.6], 'gun'),                                      # menton
        C([-3.2, 26.4, 4.6], [6.4, 4.2, 0.45], 'guns'),                                     # rejilla trasera
    ]
    p['body'] = [
        C([-1.3, 20.3, -3.35], [2.6, 1.3, 0.35], 'sil'), C([-1.0, 19.4, -3.35], [2.0, 0.9, 0.35], 'sil'),  # placa en relieve
        C([-0.5, 18.7, -3.35], [1.0, 0.7, 0.35], 'sil'), C([-0.25, 19.5, -3.5], [0.5, 1.5, 0.16], 'or'),
        *both([C([-4.5, 22.15, -3.3], [4.2, 0.4, 0.3], 'sil', rot=[0, 0, 26.6], piv=[-2.4, 22.35, -3.15])]),   # galon
        *both([C([-3.9, 10.7, -3.5], [1.2, 2.0, 0.8], 'ch'), C([-3.95, 12.4, -3.55], [1.3, 0.4, 0.9], 'sil')]),
        *both([C([-3.5, 10.6, 2.7], [2.6, 2.4, 1.0], 'ch'), C([-3.55, 12.5, 2.72], [2.7, 0.4, 1.1], 'sil')]),
        C([-0.5, 11.5, 2.7], [1.0, 1.4, 0.5], 'gun'),
        C([-0.7, 16.4, 3.0], [1.4, 7.2, 0.5], 'gun'), *both([C([-3.8, 18.4, 3.0], [2.6, 4.6, 0.45], 'chb')]),
        C([-0.3, 22.0, 3.5], [0.6, 0.6, 0.15], 'or'),
    ]
    limbs(p, 'rarm', 'larm', [
        C([-9.6, 24.2, -2.5], [4.8, 0.5, 5.0], 'gun'),                                      # charretera con dos barras
        C([-9.1, 24.7, -1.9], [0.7, 0.25, 3.8], 'sil'), C([-7.9, 24.7, -1.9], [0.7, 0.25, 3.8], 'sil'),
        C([-9.85, 20.5, -2.3], [0.55, 3.2, 4.6], 'chb'), C([-9.0, 13.2, -1.6], [0.5, 3.8, 3.2], 'guns')])
    p['rleg'] = [C([-5.25, 6.0, -0.5], [0.9, 4.6, 1.0], 'gun'), C([-5.2, 10.6, -0.4], [0.8, 1.2, 0.8], 'dk'),   # porra
                 C([-5.2, 5.5, -0.4], [0.8, 0.5, 0.8], 'or')]
    limbs(p, 'rleg', 'lleg', [C([-3.6, 4.4, -3.3], [3.4, 2.6, 0.5], 'gunb')])
    limbs(p, 'rboot', 'lboot', [C([-3.5, 1.7, -2.75], [3.2, 3.4, 0.5], 'chb')])
    return p


def nine():
    """Bodysuit negro y placas verdes: mas capas (quijadas, espina, hombreras, guardas)."""
    p = {}
    p['head'] = [
        *both([C([-4.5, 23.8, -5.15], [1.9, 2.9, 0.55], 'gr')]),                            # quijadas
        C([-3.4, 25.6, 4.6], [6.8, 5.0, 0.45], 'gr'), C([-1.0, 30.6, 4.6], [2.0, 1.6, 0.4], 'dg'),        # placa trasera
        C([-3.6, 23.5, 4.2], [7.2, 1.7, 0.7], 'dg', rot=[18, 0, 0], piv=[0, 25.2, 4.6]),    # cubrenuca
    ]
    p['body'] = [
        *both([C([-4.2, 21.0, -3.4], [3.6, 2.5, 0.9], 'gr')]),                             # pectorales
        C([-2.7, 15.9, -3.0], [5.4, 1.5, 0.5], 'dg'), C([-2.3, 14.3, -3.0], [4.6, 1.4, 0.5], 'dg'),       # abdomen
        C([-1.1, 21.3, 3.0], [2.2, 1.9, 0.6], 'grp'), C([-1.0, 19.2, 3.0], [2.0, 1.9, 0.55], 'grp'),      # espina
        C([-0.9, 17.1, 3.0], [1.8, 1.9, 0.5], 'grp'), C([-0.8, 14.9, 2.5], [1.6, 1.9, 0.5], 'dg'),
        *both([C([-4.0, 19.3, 3.0], [2.5, 3.8, 0.45], 'gr')]),                              # omoplatos
    ]
    limbs(p, 'rarm', 'larm', [
        C([-9.4, 24.2, -2.6], [4.6, 0.6, 5.2], 'grp'), C([-8.8, 24.8, -0.5], [3.2, 0.6, 1.0], 'dg'),
        C([-9.7, 19.9, -2.4], [0.5, 2.4, 4.8], 'dg'),
        C([-9.0, 13.0, -1.3], [0.5, 3.2, 2.6], 'grp'), C([-9.35, 13.6, -0.7], [0.35, 2.0, 1.4], 'sil')])
    limbs(p, 'rleg', 'lleg', [C([-4.85, 7.2, -1.7], [0.5, 3.8, 3.4], 'grp'), C([-2.7, 5.0, -3.2], [1.6, 1.6, 0.4], 'grp')])
    limbs(p, 'rboot', 'lboot', [C([-4.6, 1.0, -0.9], [0.45, 2.6, 1.8], 'dg'), C([-3.3, 1.9, -2.7], [2.8, 3.2, 0.45], 'gr')])
    return p


def forge():
    """Mitad rojo oscuro, mitad grafito, placas blancas con circuitos; el brazo derecho lleva placas desmontables."""
    p = {}
    p['head'] = [
        C([-4.5, 30.0, -5.2], [3.9, 0.8, 0.7], 'red'), C([0.6, 30.0, -5.2], [3.9, 0.8, 0.7], 'gra'),      # cejas
        *both([C([-4.5, 23.8, -5.15], [1.9, 3.0, 0.55], 'whc')]),                           # quijadas
        C([-3.4, 25.8, 4.6], [3.4, 4.8, 0.45], 'red'), C([0.0, 25.8, 4.6], [3.4, 4.8, 0.45], 'gra'),
        C([-0.7, 26.8, 4.9], [1.4, 2.8, 0.35], 'whc'),
    ]
    p['body'] = [
        C([-4.3, 22.6, -3.8], [4.3, 0.7, 0.45], 'red'), C([0.0, 22.6, -3.8], [4.3, 0.7, 0.45], 'gra'),    # peto reforzado
        C([-4.35, 18.4, -3.8], [0.6, 4.2, 0.45], 'red'), C([3.75, 18.4, -3.8], [0.6, 4.2, 0.45], 'gra'),
        C([-1.1, 19.5, -3.8], [2.2, 2.2, 0.45], 'wh'), C([-0.6, 20.0, -3.95], [1.2, 1.2, 0.2], 'amb'),    # nucleo
        C([-2.4, 16.8, 3.0], [4.8, 5.6, 0.8], 'whc'),                                       # nucleo de la espalda
        C([-2.8, 22.2, 2.96], [2.8, 0.6, 1.04], 'red'), C([0.0, 22.2, 2.96], [2.8, 0.6, 1.04], 'gra'),
        C([-1.7, 17.6, 3.8], [0.6, 3.8, 0.15], 'amb'), C([1.1, 17.6, 3.8], [0.6, 3.8, 0.15], 'amb'),
        C([-0.4, 19.1, 3.8], [0.8, 0.8, 0.2], 'blu'),
    ]
    p['rarm'] = [
        C([-9.9, 20.2, -2.6], [1.0, 3.6, 5.2], 'whc'),                                      # placa desmontable del hombro
        C([-10.15, 22.7, -1.9], [0.25, 0.7, 0.7], 'amb'), C([-10.15, 22.7, 1.2], [0.25, 0.7, 0.7], 'amb'),
        C([-9.2, 23.97, -3.0], [4.4, 0.6, 6.0], 'whg'), C([-8.4, 24.57, -0.4], [2.8, 0.5, 0.8], 'gra'),   # placa superior con asa
        C([-10.2, 12.8, -2.4], [1.4, 4.9, 4.8], 'whc'),                                     # placa del antebrazo
        C([-10.45, 16.5, -1.7], [0.25, 0.7, 0.7], 'amb'), C([-10.45, 16.5, 1.0], [0.25, 0.7, 0.7], 'amb'),
    ]
    p['larm'] = [C([7.9, 20.9, -1.4], [0.45, 2.6, 2.8], 'gra'), C([8.35, 21.4, -0.8], [0.2, 1.6, 1.6], 'blu'),
                 C([8.5, 13.4, -1.6], [0.5, 3.4, 3.2], 'whc')]
    p['rleg'] = [C([-3.4, 4.9, -3.25], [3.0, 1.9, 0.45], 'whg')]
    p['lleg'] = [C([0.4, 4.9, -3.25], [3.0, 1.9, 0.45], 'whc')]
    return p


def spore():
    """Crema y violeta palido: sombrero de seta con ala y laminillas, hombreras de cupula, setas de repisa."""
    p = {}
    p['head'] = [
        C([-6.2, 30.5, -6.0], [12.4, 0.8, 12.2], 'vi', down='gil'),                         # ala del sombrero
        C([-5.5, 31.3, -5.3], [11.0, 0.5, 10.9], 'vi'),
        C([-6.2, 29.9, -6.0], [12.4, 0.6, 0.6], 'vip'), C([-6.2, 29.9, 5.6], [12.4, 0.6, 0.6], 'vip'),    # borde caido
        *both([C([-6.2, 29.9, -5.4], [0.6, 0.6, 11.0], 'vip')]),
        C([-1.9, 34.2, -1.7], [1.2, 0.25, 1.2], 'spot'), C([0.7, 34.2, 0.5], [0.9, 0.25, 0.9], 'spot'),
    ]
    p['body'] = [
        C([-3.6, 22.7, -3.3], [7.2, 0.7, 6.6], 'cr'),                                       # anillo del tallo
        C([-3.4, 20.8, 3.0], [3.0, 0.5, 1.5], 'vi', down='gil'), C([-3.0, 21.3, 3.0], [2.0, 0.35, 1.0], 'vip'),   # setas de repisa
        C([0.6, 18.8, 3.0], [3.2, 0.5, 1.7], 'vi', down='gil'), C([1.0, 19.3, 3.0], [2.2, 0.35, 1.1], 'vip'),
        C([-2.6, 16.6, 3.0], [2.4, 0.5, 1.3], 'vi', down='gil'), C([-2.3, 17.1, 3.0], [1.6, 0.3, 0.8], 'vip'),
    ]
    limbs(p, 'rarm', 'larm', [
        C([-9.4, 24.0, -3.0], [4.7, 0.8, 6.0], 'vi'), C([-8.9, 24.8, -2.3], [3.8, 0.6, 4.6], 'vi'),       # hombrera de cupula
        C([-8.2, 25.4, -1.5], [2.4, 0.5, 3.0], 'vip'), C([-9.9, 23.0, -3.0], [0.5, 1.4, 6.0], 'vip'),
        C([-8.8, 17.4, -2.8], [5.6, 0.7, 5.6], 'vip')])
    limbs(p, 'rleg', 'lleg', [C([-3.7, 4.7, -3.3], [3.6, 0.85, 1.1], 'vi'), C([-3.3, 5.6, -3.3], [2.8, 0.7, 0.9], 'vip')])
    return p


def mirage():
    """Blanco y plata espejados: puntas de cristal en casco, hombros, antebrazos y espalda; prisma en el pecho."""
    p = {}
    p['head'] = [
        C([-0.5, 33.8, -4.7], [1.0, 1.5, 1.4], 'ice'), C([-0.3, 35.3, -4.4], [0.6, 0.9, 0.8], 'tip'),     # punta central
        *both([C([-2.9, 32.3, -4.9], [1.2, 2.4, 0.9], 'ice', rot=[0, 0, -22], piv=[-2.3, 32.3, -4.45]),
               C([-4.35, 31.9, -4.6], [1.0, 1.7, 0.8], 'ice', rot=[0, 0, -40], piv=[-3.85, 31.9, -4.2])]),
        C([-4.5, 29.8, -5.1], [9.0, 0.7, 0.7], 'mir'),                                      # ceja
        C([-0.4, 27.0, 4.55], [0.8, 5.2, 1.3], 'ice'), C([-0.3, 30.0, 5.85], [0.6, 1.8, 0.6], 'tip'),     # aleta trasera
    ]
    p['body'] = [
        C([-1.5, 19.3, -3.75], [3.0, 0.5, 0.4], 'prism'), C([-1.1, 19.8, -3.75], [2.2, 0.5, 0.4], 'prism'),   # prisma
        C([-0.7, 20.3, -3.75], [1.4, 0.5, 0.4], 'prism'), C([-0.3, 20.8, -3.75], [0.6, 0.5, 0.4], 'prism'),
        C([-0.6, 20.4, 3.0], [1.2, 3.4, 1.2], 'ice', rot=[-28, 0, 0], piv=[0, 20.4, 3.0]),  # cristales de la espalda
        C([-0.4, 23.8, 3.2], [0.8, 1.2, 0.8], 'tip', rot=[-28, 0, 0], piv=[0, 20.4, 3.0]),
        C([-0.5, 17.6, 3.0], [1.0, 2.8, 1.0], 'ice', rot=[-34, 0, 0], piv=[0, 17.6, 3.0]),
        C([-0.4, 15.2, 2.6], [0.8, 2.2, 0.8], 'ice', rot=[-40, 0, 0], piv=[0, 15.2, 2.6]),
        *both([C([-3.9, 18.4, 3.0], [2.4, 4.4, 0.4], 'mir')]),
    ]
    limbs(p, 'rarm', 'larm', [
        C([-8.7, 23.6, -0.4], [1.6, 3.0, 0.8], 'ice', rot=[0, 0, -14], piv=[-7.9, 23.6, 0]),      # cristales del hombro
        C([-8.4, 26.6, -0.3], [1.0, 1.4, 0.6], 'tip', rot=[0, 0, -14], piv=[-7.9, 23.6, 0]),
        C([-10.0, 22.4, -1.6], [1.4, 2.6, 0.7], 'ice', rot=[0, 0, -36], piv=[-9.3, 22.4, -1.25]),
        C([-9.75, 25.0, -1.55], [0.9, 1.1, 0.6], 'tip', rot=[0, 0, -36], piv=[-9.3, 22.4, -1.25]),
        C([-9.7, 22.4, 0.9], [1.3, 2.0, 0.7], 'ice', rot=[0, 0, -30], piv=[-9.05, 22.4, 1.25]),
        C([-9.45, 24.4, 0.95], [0.8, 0.9, 0.6], 'tip', rot=[0, 0, -30], piv=[-9.05, 22.4, 1.25]),
        C([-9.72, 12.3, -0.9], [0.45, 5.3, 1.8], 'mir'), C([-9.67, 17.6, -0.6], [0.35, 0.9, 1.2], 'tip')])   # hoja del antebrazo
    limbs(p, 'rleg', 'lleg', [C([-2.7, 4.8, -3.35], [1.6, 2.4, 0.55], 'ice'), C([-2.4, 7.2, -3.3], [1.0, 1.0, 0.9], 'tip')])
    limbs(p, 'rboot', 'lboot', [C([-4.6, 1.4, -0.8], [0.45, 3.2, 2.2], 'ice')])
    return p


def proto():
    """Blanco y naranja, armado por piezas: casco redondo con aro en el visor, puertos en relieve, mochila de modulos."""
    p = {}
    p['head'] = [
        C([-2.6, 33.18, -2.6], [5.2, 0.52, 5.2], 'lg'), C([-3.2, 25.4, 4.6], [6.4, 5.2, 0.6], 'lg'),        # cupula y nuca
        C([-1.5, 30.9, -5.15], [3.0, 0.5, 0.55], 'wh'), C([-1.5, 25.85, -5.2], [3.0, 0.45, 0.6], 'wh'),   # aro del visor
        *both([C([-2.85, 27.1, -5.15], [0.5, 3.0, 0.55], 'wh'), C([-2.35, 30.1, -5.15], [0.85, 0.8, 0.55], 'wh'),
               C([-2.35, 26.3, -5.15], [0.85, 0.8, 0.55], 'wh')]),
        *both([C([-5.45, 27.3, -1.2], [0.45, 2.4, 2.4], 'or'), C([-5.7, 28.1, -0.4], [0.25, 0.8, 0.8], 'org')]),   # diales
    ]
    p['body'] = [
        C([-0.9, 19.4, -3.75], [1.8, 0.4, 0.7], 'wh'), C([-0.9, 17.4, -3.75], [1.8, 0.4, 0.7], 'wh'),     # puerto del pecho
        *both([C([-1.3, 17.8, -3.75], [0.4, 1.6, 0.7], 'wh')]), C([-0.5, 18.1, -3.6], [1.0, 1.0, 0.3], 'org'),
        *both([C([-3.75, 22.1, -3.5], [0.5, 0.5, 0.2], 'lg'), C([-3.75, 19.1, -3.5], [0.5, 0.5, 0.2], 'lg')]),   # pernos
        *both([C([-3.1, 15.8, 4.0], [2.0, 6.4, 1.0], 'wh'), C([-3.1, 22.2, 4.0], [2.0, 0.6, 1.0], 'or'),
               C([-3.1, 15.2, 4.0], [2.0, 0.6, 1.0], 'or')]),                                # tubos de modulos
        C([-0.6, 15.6, 4.0], [1.2, 6.8, 0.45], 'lgs'), C([-0.3, 21.5, 4.45], [0.6, 0.6, 0.15], 'org'),
        C([-1.6, 23.0, 3.0], [3.2, 0.5, 0.7], 'dk'), C([-2.4, 13.8, 2.6], [4.8, 1.2, 1.3], 'dks'),        # asa y escape
    ]
    limbs(p, 'rarm', 'larm', [
        C([-9.5, 16.5, -1.6], [0.5, 0.45, 3.2], 'wh'), C([-9.5, 13.45, -1.6], [0.5, 0.45, 3.2], 'wh'),    # marco del puerto
        C([-9.5, 13.9, -1.75], [0.5, 2.6, 0.45], 'wh'), C([-9.5, 13.9, 1.3], [0.5, 2.6, 0.45], 'wh'),
        C([-9.2, 14.7, -0.5], [0.25, 1.0, 1.0], 'org'),
        C([-8.6, 20.1, -2.8], [4.8, 0.5, 5.6], 'wh'), C([-7.9, 24.3, -2.0], [3.2, 0.5, 4.0], 'wh'),
        C([-6.7, 24.8, -0.4], [0.8, 0.3, 0.8], 'lg')])
    limbs(p, 'rleg', 'lleg', [C([-3.4, 4.8, -3.25], [3.0, 1.8, 0.45], 'whr')])
    limbs(p, 'rboot', 'lboot', [
        C([-3.5, 4.5, -3.2], [3.2, 0.45, 0.5], 'wh'), C([-3.5, 1.5, -3.2], [3.2, 0.4, 0.5], 'wh'),        # marco del puerto
        C([-3.65, 1.9, -3.2], [0.45, 2.6, 0.5], 'wh'), C([-0.6, 1.9, -3.2], [0.45, 2.6, 0.5], 'wh'),
        C([-2.4, 2.7, -2.95], [1.0, 1.0, 0.25], 'org')])
    return p


SUITS = {
    'cipher': {'model': 'rider_cipher', 'variants': ['rider_cipher_accel', 'rider_cipher_blaster'], 'parts': cipher, 'styles': {
        'sil': {'col': '#c4ccdc'}, 'silr': {'col': '#c4ccdc', 'rivets': '#6c7484'}, 'sils': {'col': '#c4ccdc', 'slots': '#6c7484'},
        'navy': {'col': '#1c2c5c'}, 'navyr': {'col': '#1c2c5c', 'rivets': '#c4ccdc'}, 'navys': {'col': '#1c2c5c', 'slots': '#0c142c'},
        'dnavy': {'col': '#121a36'}, 'gray': {'col': '#6c7484'}, 'lit': {'kind': 'glow', 'col': '#e8f6ff'}}},
    'warden': {'model': 'rider_warden', 'parts': warden, 'styles': {
        'ch': {'col': '#2c2c34', 'noise': 2.2}, 'chb': {'col': '#2c2c34', 'noise': 2.2, 'band': ('#ececec', 2)},
        'gun': {'col': '#4c4c5c'}, 'guns': {'col': '#4c4c5c', 'slots': '#1c1c24'}, 'gunb': {'col': '#4c4c5c', 'band': ('#ececec', 1)},
        'sil': {'col': '#e4e4e8'}, 'dk': {'col': '#1a1a20'}, 'or': {'kind': 'glow', 'col': '#f89828'}}},
    'nine': {'model': 'rider_nine', 'parts': nine,
             'variants': ['rider_nine_rising', 'rider_nine_dragon', 'rider_nine_dragon_rising', 'rider_nine_pegasus',
                          'rider_nine_pegasus_rising', 'rider_nine_titan', 'rider_nine_titan_rising'],
             # el color principal sale del peto de cada forma (verde, azul en Dragon, morado en Titan, plata en Pegasus)
             'tones': lambda bones, sheet: {'main': mode_color(face_px(sheet, chest_cube(bones), 'north'))},
             'styles': lambda t: {'gr': {'col': t['main'], 'veins': mix(t['main'], '#f0ffe0', 0.55), 'band': (mul(t['main'], 0.5), 1)},
                                  'grp': {'col': t['main']}, 'dg': {'col': mul(t['main'], 0.7)}, 'sil': {'col': '#c8c8d8'}}},
    'forge': {'model': 'rider_forge', 'parts': forge,
              'variants': ['rider_forge_' + a + '_' + b for a, b in (('cannon', 'carbon'), ('cannon', 'fiber'), ('cannon', 'magnesium'),
                           ('gear', 'fiber'), ('gear', 'magnesium'), ('shield', 'carbon'), ('shield', 'fiber'), ('shield', 'magnesium'))],
              # los dos colores del traje salen del casco: mitad derecha (herramienta) y mitad izquierda (material)
              'tones': lambda bones, sheet: {'r': mode_color(face_px(sheet, inflated(bones, 'armorHead'), 'up', 0, 0.45), False),
                                             'l': mode_color(face_px(sheet, inflated(bones, 'armorHead'), 'up', 0.55, 1), False)},
              'styles': lambda t: {'wh': {'col': '#f0f0f4', 'grad': 0.10}, 'whc': {'col': '#f0f0f4', 'grad': 0.10, 'trace': '#7cbcfc'},
                                   'whg': {'col': '#f4f4f8', 'grad': 0.08, 'grid': '#b8b8c0'},
                                   'red': {'col': t['r'], 'grid': mul(t['r'], 0.74), 'gridp': 4}, 'gra': {'col': t['l']},
                                   'amb': {'kind': 'glow', 'col': '#f8a818'}, 'blu': {'kind': 'glow', 'col': '#94d4fc'}}},
    'spore': {'model': 'rider_spore', 'parts': spore, 'styles': {
        'vi': {'col': '#c0aad8', 'spots': '#fcf4e4'}, 'vip': {'col': '#b8a2d2'}, 'cr': {'col': '#e0d2ac'},
        'gil': {'kind': 'gills', 'col': '#e4d8b8'}, 'spot': {'col': '#fcf4e4', 'grad': 0.05}}},
    'mirage': {'model': 'rider_mirage', 'parts': mirage, 'styles': {
        'mir': {'col': '#f0f4fc', 'grad': 0.16, 'streaks': True, 'edge': '#5898f8'}, 'ice': {'col': '#dce8fc', 'grad': 0.22, 'streaks': True},
        'tip': {'col': '#f8fbff', 'grad': 0.06, 'edge': '#8cb8f8'}, 'prism': {'kind': 'prism', 'col': '#a468f0'}}},
    'proto': {'model': 'rider_proto', 'parts': proto, 'styles': {
        'wh': {'col': '#f0f0f4', 'grad': 0.10}, 'whr': {'col': '#f0f0f4', 'grad': 0.10, 'rivets': '#a8a8b0'},
        'lg': {'col': '#c4c4cc', 'rivets': '#8c8c94'}, 'lgs': {'col': '#c4c4cc', 'slots': '#6c6c78'},
        'or': {'col': '#fc8c1c', 'rivets': '#c05c0c'}, 'org': {'kind': 'glow', 'col': '#ffa030'},
        'dk': {'col': '#24242c'}, 'dks': {'col': '#24242c', 'slots': '#0c0c10'}}},
}


def main():
    only = [a for a in sys.argv[1:] if not a.startswith('--')]
    clean = '--clean' in sys.argv
    side = json.load(open(SIDE)) if os.path.exists(SIDE) else {}
    report = []
    for name, spec in SUITS.items():
        if only and name not in only:
            continue
        if name == 'forge':
            continue  # Generated by forge_reference_model.py
        if name == 'nine' and os.path.exists(os.path.join(HERE, '..', 'diseno', 'nine', 'recursos-originales.zip')):
            report.append('Nine: generado por tools/nine_reference_model.py; se conserva sin cambios.')
            continue
        parts = spec['parts']()
        # las piezas se disenan sobre el modelo base SIN relieve: sus cubos sirven para saber que es propio de cada variante
        base = load(spec['model'])['minecraft:geometry'][0]
        base_bones = {b['name']: {sig(c) for c in b.get('cubes', [])} for b in base['bones'] if not b['name'].startswith(PREFIX)}
        for model in [spec['model']] + spec.get('variants', []):
            process(model, spec, parts, base_bones, side, clean, report)
    json.dump(side, open(SIDE, 'w'), indent=1, sort_keys=True)
    print('\n'.join(report))


if __name__ == '__main__':
    main()
