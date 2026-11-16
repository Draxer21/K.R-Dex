#!/usr/bin/env python3
"""
Cartas raras de los jefes de Dai-Shocker (0.52): parte del marco de una carta Attack Ride existente, pinta un dibujo
propio en el panel, escribe el nombre con una fuente de pixeles 5x7 y le da al borde un brillo tornasol (rara).

  python3 tools/rare_cards.py
"""
import os
import numpy as np
from PIL import Image

TEX = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'dexrider', 'textures', 'item')
FONT = {
    'A': ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    'B': ["11110", "10001", "11110", "10001", "10001", "10001", "11110"],
    'C': ["01111", "10000", "10000", "10000", "10000", "10000", "01111"],
    'D': ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    'E': ["11111", "10000", "11110", "10000", "10000", "10000", "11111"],
    'I': ["111", "010", "010", "010", "010", "010", "111"],
    'K': ["10001", "10010", "11100", "10010", "10001", "10001", "10001"],
    'L': ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    'M': ["10001", "11011", "10101", "10001", "10001", "10001", "10001"],
    'N': ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    'O': ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    'Q': ["01110", "10001", "10001", "10001", "10101", "10010", "01101"],
    'T': ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    'U': ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    ' ': ["00", "00", "00", "00", "00", "00", "00"],
}
BG = (20, 18, 26)


def text(im, s, cy):
    w = sum(len(FONT[c][0]) + 1 for c in s) - 1
    x = 64 - w // 2
    for c in s:
        g = FONT[c]
        for yy, row in enumerate(g):
            for xx, v in enumerate(row):
                if v == '1':
                    im[cy + yy + 1, x + xx + 1] = (0, 0, 0, 255)
        for yy, row in enumerate(g):
            for xx, v in enumerate(row):
                if v == '1':
                    im[cy + yy, x + xx] = (242, 242, 248, 255)
        x += len(g[0]) + 1


def rect(im, x0, y0, x1, y1, c):
    im[y0:y1, x0:x1] = (*c, 255)


def line(im, x0, y0, x1, y1, c, w=1):
    n = max(abs(x1 - x0), abs(y1 - y0)) + 1
    for i in range(n):
        x = round(x0 + (x1 - x0) * i / (n - 1)); y = round(y0 + (y1 - y0) * i / (n - 1))
        im[y:y + w, x:x + w] = (*c, 255)


def holo(im):
    """Marco dorado -> tornasol (de dorado a magenta a cian a lo largo del borde)."""
    h, w = im.shape[:2]
    for y in range(h):
        for x in range(w):
            r, g, b, a = im[y, x]
            if a == 0 or not (r > 140 and g > 90 and b < 90 and r > g):
                continue
            if 18 < x < 110 and 18 < y < 118:
                continue
            t = ((x + y) % 96) / 96.0
            stops = [(246, 198, 52), (255, 90, 214), (90, 220, 255), (246, 198, 52)]
            k = t * 3; i = int(k); f = k - i
            c = [stops[i][j] * (1 - f) + stops[i + 1][j] * f for j in range(3)]
            light = (int(r) + int(g)) / 2 / 220.0
            im[y, x, :3] = np.clip(np.array(c) * light, 0, 255)


def base():
    im = np.array(Image.open(os.path.join(TEX, 'card_attack_ride_mantis_reap.png')).convert('RGBA'))
    # limpiar el panel con su degradado rojo (columna sin dibujo) y la barra del nombre
    col = im[:, 27].copy()
    for x in range(26, 102):
        im[20:88, x] = col[20:88]
    im[93:117, 25:103] = (*BG, 255)
    return im


def queen():
    im = base()
    # estelas del parpadeo
    for k, a in enumerate((60, 110, 170)):
        line(im, 34 + k * 6, 76, 70 + k * 6, 30, (110 + 30 * k, 70 + 10 * k, 160 + 20 * k), 2)
    # hoja de guadania (verde dorado) y corona
    line(im, 46, 78, 78, 34, (122, 190, 80), 3)
    line(im, 78, 34, 92, 42, (180, 230, 120), 3)
    line(im, 92, 42, 88, 52, (180, 230, 120), 2)
    for x in (66, 72, 78):
        rect(im, x, 24, x + 3, 30, (246, 198, 52))
    rect(im, 64, 29, 82, 32, (246, 198, 52))
    im[26, 73] = (255, 60, 80, 255)
    im[46:48, 70:72] = (240, 240, 120, 255); im[46:48, 80:82] = (240, 240, 120, 255)
    text(im, 'QUEEN BLINK', 101)
    holo(im)
    return im


def titan():
    im = base()
    cx, cy = 64, 60
    yy, xx = np.mgrid[0:128, 0:128]
    d = np.sqrt(((xx - cx) / 1.0) ** 2 + ((yy - cy) / 0.55) ** 2)
    for r, c in ((40, (110, 40, 30)), (30, (170, 70, 40)), (20, (230, 120, 50))):
        m = (np.abs(d - r) < 1.6) & (yy > 20) & (yy < 86) & (xx > 26) & (xx < 101)
        im[m] = (*c, 255)
    # grietas
    for x1, y1 in ((40, 80), (90, 82), (98, 50), (30, 52)):
        line(im, 64, 62, x1, y1, (40, 20, 20))
    line(im, 64, 64, 44, 82, (50, 25, 25), 2); line(im, 64, 64, 88, 84, (50, 25, 25), 2)
    # cuerno del titan
    for i in range(14):
        rect(im, 60 + i // 4, 30 + i * 2, 70 - i // 4, 32 + i * 2, (214, 196, 150))
    rect(im, 58, 56, 72, 60, (110, 110, 120))
    text(im, 'TITAN QUAKE', 101)
    holo(im)
    return im


def command():
    im = base()
    # asta y estandarte con tres galones
    rect(im, 40, 24, 43, 84, (200, 170, 80))
    rect(im, 43, 26, 88, 58, (30, 28, 40))
    rect(im, 43, 26, 88, 28, (246, 198, 52)); rect(im, 43, 56, 88, 58, (246, 198, 52))
    for k in range(3):
        y = 34 + k * 7
        line(im, 52, y, 65, y + 6, (255, 70, 90), 2)
        line(im, 65, y + 6, 78, y, (255, 70, 90), 2)
    rect(im, 38, 22, 45, 25, (246, 198, 52))
    # siluetas de aliados
    for x in (56, 70, 84):
        rect(im, x, 66, x + 6, 72, (60, 200, 110)); rect(im, x - 1, 72, x + 7, 84, (60, 200, 110))
    text(im, 'COMMAND', 101)
    holo(im)
    return im


if __name__ == '__main__':
    for name, f in (('queen_blink', queen), ('titan_quake', titan), ('command', command)):
        Image.fromarray(f()).save(os.path.join(TEX, f'card_attack_ride_{name}.png'))
    print('ok')
