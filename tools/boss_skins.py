#!/usr/bin/env python3
"""
Jefes de Dai-Shocker (0.52): copian el modelo de su kaijin de origen y recolorean la textura.
  Mantis Reina (de kaijin_mantis): violeta y oro.   Rinoceronte Titan (de kaijin_rhino): acero oscuro y rojo.
  Comandante (de kaijin_soldier): negro, rojo y oro.
  python3 tools/boss_skins.py
"""
import colorsys
import os
import shutil
import numpy as np
from PIL import Image

A = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'dexrider')
TEX = os.path.join(A, 'textures', 'item', 'armor')
GEO = os.path.join(A, 'geckolib', 'models', 'item', 'armor')


def recolor(src, dst, fn):
    im = np.array(Image.open(os.path.join(TEX, src)).convert('RGBA')).astype(float) / 255
    out = im.copy()
    for y, x in zip(*np.nonzero(im[..., 3])):
        r, g, b = im[y, x, :3]
        h, s, v = colorsys.rgb_to_hsv(r, g, b)
        out[y, x, :3] = fn(h, s, v)
    Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8)).save(os.path.join(TEX, dst))


def queen(h, s, v):
    if s > 0.25:                       # verdes -> violeta; amarillos -> oro
        nh = 0.13 if 0.1 < h < 0.2 else (h + 0.47) % 1.0
        return colorsys.hsv_to_rgb(nh, min(1, s * 1.1), v)
    return colorsys.hsv_to_rgb(0.78, 0.25, v * 0.85)


def titan(h, s, v):
    if s > 0.3 and (h < 0.08 or h > 0.9):
        return colorsys.hsv_to_rgb(0.0, 0.9, min(1, v * 1.2))       # ojos/acentos: rojo intenso
    if s > 0.15 and 0.04 < h < 0.2:
        return colorsys.hsv_to_rgb(0.98, 0.85, min(1, v * 1.1))       # cuerno al rojo vivo
    return colorsys.hsv_to_rgb(0.6, 0.22, min(1, v * 0.8))            # placas: acero azulado


def commander(h, s, v):
    if s > 0.3:
        return colorsys.hsv_to_rgb(0.99 if v < 0.7 else 0.12, 0.85, v)  # acentos rojos y oro
    if v > 0.7:
        return colorsys.hsv_to_rgb(0.11, 0.55, v * 0.9)                # mascara: oro
    return colorsys.hsv_to_rgb(0.0, 0.05, v * 0.45)


for src, dst, fn in (('kaijin_mantis', 'kaijin_mantis_queen', queen), ('kaijin_rhino', 'kaijin_rhino_titan', titan),
                     ('kaijin_soldier', 'kaijin_commander', commander)):
    shutil.copy(os.path.join(GEO, src + '.geo.json'), os.path.join(GEO, dst + '.geo.json'))
    recolor(src + '.png', dst + '.png', fn)
    recolor(src + '_glowmask.png', dst + '_glowmask.png', fn)
print('ok')
