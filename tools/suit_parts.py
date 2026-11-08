#!/usr/bin/env python3
"""
Piezas extra del editor del traje (etapa 3) y lineas del visor.

  python3 tools/suit_parts.py

1. Pinta "muestras" de color solido en zonas libres de las capas en escala de grises (acento, plata, cristal,
   bufanda, negro) de dex_armor y dex_upgrade. Las piezas nuevas apuntan su UV ahi, asi cada cubo se tine con
   el color de esa zona del diseno (y brilla si esa zona brilla).
2. Agrega (o rehace) los huesos de las piezas en los modelos GeckoLib:
     Upgrade: upgHornsBlade/Twin, upgShouldersCap/Blades, upgChestPlate/Core, upgCape, upgScarf
     Form Rides (dex_armor): hydroFins, frostCrystals, cinderFlames, speedSpoiler, gasDucts, terraPlates
3. Genera las variantes del visor: dex_armor_eyes_<o>_<t>_<g>.png (lente) y dex_armor_lines_<...>.png (lineas),
   o = h|v|d (horizontal, vertical, diagonal), t = 1|2 (grosor), g = 1|3 (separacion). Y dex_armor_lines.png
   para el visor clasico.

Todo es original (formas simples de bloques); no copia disenos de juguetes.
"""
import json
import os
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'dexrider')
TEX = os.path.join(ROOT, 'textures', 'item', 'armor')
GEO = os.path.join(ROOT, 'geckolib', 'models', 'item', 'armor')

# ------------------------------------------------------------------ muestras

SWATCH = {
    'dex_armor': {'accent': (128, 384), 'silver': (160, 384), 'crystal': (192, 384), 'scarf': (224, 384), 'inner': (256, 384)},
    'dex_upgrade': {'accent': (224, 288), 'silver': (256, 288), 'crystal': (288, 288), 'scarf': (320, 288), 'inner': (352, 288)},
}
FINISH_LAYERS = ('accent', 'silver', 'scarf')   # tienen _matte y _metal


def median_gray(img):
    vals = sorted(p[0] for p in img.getdata() if p[3] > 0)
    return vals[len(vals) // 2] if vals else 235


def paint_swatches(model):
    size = (512, 1024) if model == 'dex_armor' else (512, 512)
    for layer, (sx, sy) in SWATCH[model].items():
        names = [layer] + ([layer + '_matte', layer + '_metal'] if layer in FINISH_LAYERS else [])
        for n in names:
            path = os.path.join(TEX, f'{model}_{n}.png')
            img = Image.open(path).convert('RGBA') if os.path.exists(path) else Image.new('RGBA', size, (0, 0, 0, 0))
            ref = path
            if not os.path.exists(path):      # capa nueva (bufanda y cristal de la Upgrade): toma el tono de dex_armor
                ref_img = Image.open(os.path.join(TEX, f'dex_armor_{n}.png')).convert('RGBA')
                g = median_gray(ref_img)
            else:
                g = median_gray(img)
            dark = int(g * 0.8)
            for y in range(8):
                for x in range(8):
                    v = g if y < 4 else dark
                    img.putpixel((sx + x, sy + y), (v, v, v, 255))
            img.save(path)


# ------------------------------------------------------------------ cubos

def faces(model, layer):
    sx, sy = SWATCH[model][layer]
    light = {'uv': [sx + 2, sy + 1], 'uv_size': [4, 2]}
    dark = {'uv': [sx + 2, sy + 5], 'uv_size': [4, 2]}
    return {'north': light, 'south': light, 'up': light, 'east': dark, 'west': dark, 'down': dark}


def C(o, s, layer, rot=None, pivot=None):
    return {'o': o, 's': s, 'layer': layer, 'rot': rot, 'pivot': pivot}


def mirror(c):
    o, s = c['o'], c['s']
    rot = None if c['rot'] is None else [c['rot'][0], -c['rot'][1], -c['rot'][2]]
    pivot = None if c['pivot'] is None else [-c['pivot'][0], c['pivot'][1], c['pivot'][2]]
    return C([-(o[0] + s[0]), o[1], o[2]], s, c['layer'], rot, pivot)


def build(model, cubes):
    out = []
    for c in cubes:
        cube = {'origin': c['o'], 'size': c['s'], 'uv': faces(model, c['layer'])}
        if c['rot'] is not None:
            cube['rotation'] = c['rot']
            cube['pivot'] = c['pivot'] or c['o']
        out.append(cube)
    return out


def both(cubes):
    return cubes + [mirror(c) for c in cubes]


# Upgrade ---------------------------------------------------------------
UPGRADE = {
    'upgHornsBlade': ('armorHead', [
        C([-1.0, 29.8, -5.3], [2.0, 1.2, 0.8], 'silver'),
        C([-0.35, 30.6, -5.25], [0.7, 5.2, 0.6], 'accent', [-18, 0, 0], [0, 30.6, -5.0]),
        C([-0.2, 30.8, -5.4], [0.4, 3.6, 0.3], 'inner', [-18, 0, 0], [0, 30.6, -5.0]),
    ]),
    'upgHornsTwin': ('armorHead', both([
        C([1.0, 30.2, -5.1], [0.7, 4.4, 0.7], 'accent', [-15, 0, -28], [1.35, 30.2, -4.75]),
        C([0.7, 29.7, -5.25], [1.3, 1.0, 0.8], 'silver'),
    ])),
    'upgShouldersCap': ('ARMS', [
        C([-10.1, 24.0, -3.4], [6.6, 1.0, 6.8], 'silver'),
        C([-10.4, 21.6, -3.6], [0.6, 2.6, 7.2], 'accent'),
    ]),
    'upgShouldersBlades': ('ARMS', [
        C([-10.6, 21.0, -1.0], [0.6, 1.4, 7.0], 'accent', [-20, 0, 0], [-10.3, 21.7, -1.0]),
        C([-10.4, 22.6, -0.6], [0.5, 1.0, 5.6], 'silver', [-28, 0, 0], [-10.15, 23.1, -0.6]),
    ]),
    'upgChestPlate': ('armorBody', both([
        C([0.2, 19.0, -3.45], [4.4, 0.9, 0.5], 'silver', [0, 0, -32], [0.2, 19.45, -3.2]),
        C([0.3, 16.6, -3.4], [3.6, 0.7, 0.45], 'accent', [0, 0, -24], [0.3, 16.95, -3.2]),
    ])),
    'upgChestCore': ('armorBody', [
        C([-1.7, 18.6, -3.4], [3.4, 3.4, 0.4], 'silver'),
        C([-1.2, 19.1, -3.75], [2.4, 2.4, 0.5], 'crystal'),
        C([-0.5, 19.8, -3.95], [1.0, 1.0, 0.3], 'crystal'),
    ]),
    'upgCape': ('armorBody', [
        C([-4.6, 23.0, 2.0], [9.2, 1.0, 1.4], 'silver'),
        C([-4.4, 6.6, 3.0], [8.8, 16.5, 0.3], 'scarf', [8, 0, 0], [0, 23.1, 3.0]),
        C([-4.5, 6.4, 3.0], [9.0, 0.6, 0.4], 'accent', [8, 0, 0], [0, 23.1, 3.0]),
    ]),
    'upgScarf': ('armorBody', [
        C([-3.9, 22.4, -3.4], [7.8, 1.7, 0.8], 'scarf'),
        C([-3.9, 22.4, 2.6], [7.8, 1.7, 0.8], 'scarf'),
        C([-4.5, 22.4, -3.4], [0.7, 1.7, 6.8], 'scarf'),
        C([3.8, 22.4, -3.4], [0.7, 1.7, 6.8], 'scarf'),
        C([-2.7, 13.6, 3.1], [1.6, 9.0, 0.4], 'scarf', [12, 0, 0], [-1.9, 22.6, 3.1]),
        C([0.9, 14.8, 3.1], [1.5, 7.8, 0.4], 'scarf', [18, 0, 0], [1.6, 22.6, 3.1]),
    ]),
}

# Form Rides (dex_armor) ---------------------------------------------------
ARM_FINS = [C([-9.2, 12.6, -0.4], [0.45, 3.8, 3.4], 'accent', [24, 0, 0], [-9.0, 16.4, 0.4])]
FROST_ARM = [
    C([-7.6, 24.0, -1.3], [1.0, 3.2, 1.0], 'crystal', [0, 0, 18], [-7.1, 24.0, -0.8]),
    C([-6.1, 24.0, 0.3], [0.8, 2.4, 0.8], 'crystal', [-15, 0, 8], [-5.7, 24.0, 0.7]),
    C([-5.3, 24.0, -2.1], [0.7, 1.8, 0.7], 'crystal', [12, 0, -6], [-4.95, 24.0, -1.75]),
    C([-9.0, 13.2, -0.5], [0.6, 2.0, 0.6], 'crystal', [0, 0, 30], [-8.7, 13.2, -0.2]),
]
CINDER_ARM = [
    C([-7.3, 23.4, 0.3], [1.2, 3.8, 1.2], 'crystal', [-35, 0, 10], [-6.7, 23.4, 0.9]),
    C([-5.8, 23.6, 1.0], [0.9, 2.9, 0.9], 'crystal', [-45, 0, 0], [-5.35, 23.6, 1.45]),
    C([-8.4, 22.4, 0.6], [0.8, 2.5, 0.8], 'crystal', [-30, 0, 25], [-8.0, 22.4, 1.0]),
    C([-9.0, 12.6, 0.9], [0.6, 2.7, 0.6], 'crystal', [-40, 0, 15], [-8.7, 12.6, 1.2]),
]
TERRA_ARM = [
    C([-9.7, 18.8, -3.4], [2.3, 5.6, 6.8], 'silver'),
    C([-9.9, 19.4, -2.8], [0.4, 4.4, 5.6], 'inner'),
    C([-9.1, 12.3, -2.9], [1.2, 4.4, 5.8], 'inner'),
    C([-9.3, 13.0, -2.3], [0.4, 3.0, 4.6], 'silver'),
]
FORMS = {
    'hydroFins': [('armorBody', [
        C([-0.3, 13.6, 2.4], [0.6, 9.0, 1.2], 'silver'),
        C([-0.25, 14.0, 3.4], [0.5, 7.6, 2.6], 'accent', [-20, 0, 0], [0, 14.0, 3.4]),
    ]), ('armorRightArm', ARM_FINS), ('armorLeftArm', [mirror(c) for c in ARM_FINS])],
    'frostCrystals': [('armorRightArm', FROST_ARM), ('armorLeftArm', [mirror(c) for c in FROST_ARM]), ('armorBody', [
        C([-1.6, 19.0, 2.4], [1.0, 3.6, 1.0], 'crystal', [-20, 0, 12], [-1.1, 19.0, 2.9]),
        C([0.7, 19.4, 2.4], [0.8, 2.8, 0.8], 'crystal', [-25, 0, -10], [1.1, 19.4, 2.8]),
    ])],
    'cinderFlames': [('armorRightArm', CINDER_ARM), ('armorLeftArm', [mirror(c) for c in CINDER_ARM]), ('armorBody', [
        C([-0.6, 19.6, 2.5], [1.2, 4.2, 1.2], 'crystal', [-40, 0, 0], [0, 19.6, 3.1]),
    ])],
    'speedSpoiler': [('armorBody', both([
        C([2.0, 19.5, 2.4], [0.6, 0.6, 2.2], 'inner'),
        C([0.0, 19.6, 4.4], [4.8, 0.5, 1.8], 'silver', [-12, 0, 0], [0, 19.85, 5.3]),
        C([4.4, 18.8, 4.3], [0.4, 1.6, 2.0], 'accent'),
    ]))],
    'gasDucts': [('armorBody', both([
        C([1.1, 13.5, 2.5], [2.4, 7.5, 2.2], 'silver'),
        C([1.3, 12.8, 2.7], [2.0, 0.7, 1.8], 'inner'),
        C([1.9, 21.0, 3.1], [0.8, 0.8, 0.8], 'accent'),
        C([1.5, 16.5, 4.65], [1.6, 0.5, 0.2], 'inner'),
    ]))],
    'terraPlates': [('armorRightArm', TERRA_ARM), ('armorLeftArm', [mirror(c) for c in TERRA_ARM])],
}


def add_bones(model, parts):
    path = os.path.join(GEO, f'{model}.geo.json')
    d = json.load(open(path))
    g = d['minecraft:geometry'][0]
    names = set()
    for name, spec in parts.items():
        for i, (parent, _) in enumerate(spec):
            names.add(name if i == 0 else f'{name}_{i}')
    bones = [b for b in g['bones'] if b['name'] not in names]
    for name, spec in parts.items():
        for i, (parent, cubes) in enumerate(spec):
            # el primer hueso es el que se oculta; los demas (en brazos) cuelgan de su brazo con el mismo prefijo
            bname = name if i == 0 else f'{name}_{i}'
            pivot = next((b['pivot'] for b in bones if b['name'] == parent), [0, 24, 0])
            bones.append({'name': bname, 'parent': parent, 'pivot': pivot, 'cubes': build(model, cubes)})
    g['bones'] = bones
    json.dump(d, open(path, 'w'), indent=1)


def upgrade_spec():
    spec = {}
    for name, (parent, cubes) in UPGRADE.items():
        if parent == 'ARMS':
            spec[name] = [('armorRightArm', cubes), ('armorLeftArm', [mirror(c) for c in cubes])]
        else:
            spec[name] = [(parent, cubes)]
    return spec


# ------------------------------------------------------------------ visor

def visor():
    eyes = Image.open(os.path.join(TEX, 'dex_armor_eyes.png')).convert('RGBA')
    W = 32
    op = lambda x, y: eyes.getpixel((x, y))[3] > 0
    ext = {}
    for y in range(32):
        xs = [x for x in range(15) if op(x, y)]
        if xs: ext[y] = (min(xs), max(xs))
    rows = sorted(ext)
    y0, y1 = rows[0], rows[-1]
    mask = set()
    for y in range(y0, y1 + 1):
        e = ext.get(y)
        if e is None:      # fila de linea: la extension de sus vecinas
            a, b = ext.get(y - 1), ext.get(y + 1)
            cand = [v for v in (a, b) if v]
            e = (min(v[0] for v in cand), max(v[1] for v in cand))
        for x in range(e[0], e[1] + 1):
            mask.add((x, y)); mask.add((W - 1 - x, y))
    center = {(x, y) for y in range(y0, y1 + 1) for x in (15, 16) if (14, y) in mask}

    lens_gray = median_gray(eyes)

    def write(name, pix, src=None):
        img = Image.new('RGBA', eyes.size, (0, 0, 0, 0))
        if src is not None: img = src.copy()
        for (x, y) in list(mask | center): img.putpixel((x, y), (0, 0, 0, 0))
        v = lens_gray if src is not None else 255
        for (x, y) in pix: img.putpixel((x, y), (v, v, v, 255))
        img.save(os.path.join(TEX, name))

    # clasico: las lineas son los huecos del visor original
    write('dex_armor_lines.png', [(x, y) for (x, y) in mask | center if not op(x, y)])
    for o in 'hvd':
        for t in (1, 2):
            for gap in (1, 3):
                per = t + gap
                lens, lines = [], []
                for (x, y) in mask:
                    dx = x if x < 16 else W - 1 - x            # simetrico
                    k = {'h': y - y0, 'v': 15 - dx, 'd': (15 - dx) + (y - y0)}[o]
                    (lines if k % per < t else lens).append((x, y))
                lines += list(center)
                write(f'dex_armor_eyes_{o}_{t}_{gap}.png', lens, eyes)
                write(f'dex_armor_lines_{o}_{t}_{gap}.png', lines)


if __name__ == '__main__':
    paint_swatches('dex_armor')
    paint_swatches('dex_upgrade')
    add_bones('dex_upgrade', upgrade_spec())
    add_bones('dex_armor', FORMS)
    visor()
    print('ok')
