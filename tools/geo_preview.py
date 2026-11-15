#!/usr/bin/env python3
"""
Vista previa de un modelo GeckoLib (.geo.json con UV por cara) sin esperar al CI.

  python3 tools/geo_preview.py rider_spore [rider_mirage ...] --out /tmp/vista.png [--views front,back,side] [--scale 12]

Dibuja cada modelo con su textura desde varios angulos (frente, 3/4, lado, espalda). Es un rasterizador sencillo con
z-buffer: sirve para revisar formas, colores y UV antes de subir; no reemplaza las fotos del juego.

Convenciones (las mismas que usa GeckoLib con los modelos de Bedrock):
  - El frente del personaje es -z; su brazo derecho esta en -x; y hacia arriba.
  - Rotacion de un cubo [rx, ry, rz] alrededor de su pivote: R = Rz(-rz) * Ry(ry) * Rx(-rx).
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'dexrider')
TEX = os.path.join(ROOT, 'textures', 'item', 'armor')
GEO = os.path.join(ROOT, 'geckolib', 'models', 'item', 'armor')

VIEWS = {'front': 0, 'q': 35, 'side': 90, 'bq': 145, 'back': 180, 'q2': -35}


def rot_matrix(r):
    ax, ay, az = math.radians(-r[0]), math.radians(r[1]), math.radians(-r[2])
    cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def cube_faces(c):
    """Devuelve [(nombre, 4 esquinas TL,TR,BR,BL en espacio del archivo)] de un cubo."""
    inf = c.get('inflate', 0)
    x0, y0, z0 = (c['origin'][i] - inf for i in range(3))
    x1, y1, z1 = (c['origin'][i] + c['size'][i] + inf for i in range(3))
    f = {
        'north': [(x0, y1, z0), (x1, y1, z0), (x1, y0, z0), (x0, y0, z0)],
        'south': [(x1, y1, z1), (x0, y1, z1), (x0, y0, z1), (x1, y0, z1)],
        'east': [(x1, y1, z0), (x1, y1, z1), (x1, y0, z1), (x1, y0, z0)],
        'west': [(x0, y1, z1), (x0, y1, z0), (x0, y0, z0), (x0, y0, z1)],
        'up': [(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)],
        'down': [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)],
    }
    out = []
    rot = c.get('rotation')
    if rot:
        m = rot_matrix(rot)
        piv = np.array(c.get('pivot', c['origin']), dtype=float)
    for name, pts in f.items():
        p = np.array(pts, dtype=float)
        if rot:
            p = (p - piv) @ m.T + piv
        out.append((name, p))
    return out


NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}


def render(model, yaw, scale=12, size=(420, 470), pitch=8, hide=(), glow=False):
    d = json.load(open(os.path.join(GEO, model + '.geo.json')))
    g = d['minecraft:geometry'][0]
    tw, th = g['description']['texture_width'], g['description']['texture_height']
    tex = np.array(Image.open(os.path.join(TEX, model + '.png')).convert('RGBA'))
    gm_path = os.path.join(TEX, model + '_glowmask.png')
    gm = np.array(Image.open(gm_path).convert('RGBA')) if os.path.exists(gm_path) else None
    sxk, syk = tex.shape[1] / tw, tex.shape[0] / th
    W, H = size
    img = np.zeros((H, W, 3), dtype=np.float32)
    img[:] = (0.36, 0.42, 0.50) if not glow else (0.02, 0.02, 0.04)
    zbuf = np.full((H, W), -1e9, dtype=np.float32)
    ya, pa = math.radians(yaw), math.radians(pitch)
    # mundo: X = x, Y = y, Z = -z (el frente mira a +Z). Camara gira alrededor de Y.
    cy_, sy_ = math.cos(ya), math.sin(ya)
    cp, sp = math.cos(pa), math.sin(pa)
    view = np.array([[cy_, 0, -sy_], [0, 1, 0], [sy_, 0, cy_]])
    tilt = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
    cam = tilt @ view
    light = np.array([0.35, 0.8, 0.5]); light /= np.linalg.norm(light)
    for b in g['bones']:
        if b['name'] in hide:
            continue
        for c in b.get('cubes', []):
            uv = c['uv']
            for name, p in cube_faces(c):
                if not isinstance(uv, dict) or name not in uv:
                    continue
                fu = uv[name]
                (u0, v0), (us, vs) = fu['uv'], fu['uv_size']
                if us == 0 or vs == 0:
                    continue
                w = p.copy(); w[:, 2] *= -1
                q = w @ cam.T
                px = W / 2 + q[:, 0] * scale
                py = H - 20 - q[:, 1] * scale
                pz = q[:, 2]
                n = np.cross(q[3] - q[0], q[1] - q[0])   # (el mundo tiene z invertida: se invierte el giro)
                if n[2] <= 1e-9:          # cara de espaldas a la camara
                    continue
                nn = n / (np.linalg.norm(n) + 1e-9)
                shade = 0.55 + 0.45 * max(0.0, float(nn @ light))
                x_lo, x_hi = int(max(0, math.floor(px.min()))), int(min(W - 1, math.ceil(px.max())))
                y_lo, y_hi = int(max(0, math.floor(py.min()))), int(min(H - 1, math.ceil(py.max())))
                if x_lo > x_hi or y_lo > y_hi:
                    continue
                xs, ys = np.meshgrid(np.arange(x_lo, x_hi + 1) + 0.5, np.arange(y_lo, y_hi + 1) + 0.5)
                # coordenadas (s,t) en el paralelogramo: P = P0 + s*(P1-P0) + t*(P3-P0)
                ax_, ay_ = px[1] - px[0], py[1] - py[0]
                bx_, by_ = px[3] - px[0], py[3] - py[0]
                det = ax_ * by_ - ay_ * bx_
                if abs(det) < 1e-9:
                    continue
                dx, dy = xs - px[0], ys - py[0]
                s = (dx * by_ - dy * bx_) / det
                t = (ax_ * dy - ay_ * dx) / det
                inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
                if not inside.any():
                    continue
                z = pz[0] + s * (pz[1] - pz[0]) + t * (pz[3] - pz[0])
                tu = np.clip(((u0 + s * us) * sxk).astype(int), 0, tex.shape[1] - 1)
                tv = np.clip(((v0 + t * vs) * syk).astype(int), 0, tex.shape[0] - 1)
                col = tex[tv, tu]
                sub_z = zbuf[y_lo:y_hi + 1, x_lo:x_hi + 1]
                ok = inside & (col[..., 3] > 20) & (z > sub_z)
                if not ok.any():
                    continue
                sub = img[y_lo:y_hi + 1, x_lo:x_hi + 1]
                rgb = col[..., :3].astype(np.float32) / 255.0
                if glow:
                    lit = np.zeros_like(rgb) + rgb * 0.12
                    if gm is not None:
                        gcol = gm[tv, tu]
                        m = gcol[..., 3] > 0
                        lit[m] = gcol[..., :3][m].astype(np.float32) / 255.0
                    rgb = lit
                else:
                    rgb = rgb * shade
                sub[ok] = rgb[ok]
                sub_z[ok] = z[ok]
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def sheet(models, views, scale=12, hide=(), glow=False):
    size = (int(22 * scale) + 60, int(40 * scale))
    rows = []
    for m in models:
        tiles = [render(m, VIEWS.get(v, 0) if not v.lstrip('-').isdigit() else int(v), scale, size, hide=hide, glow=glow) for v in views]
        row = Image.new('RGB', (size[0] * len(tiles), size[1]))
        for i, t in enumerate(tiles):
            row.paste(t, (i * size[0], 0))
        rows.append(row)
    out = Image.new('RGB', (rows[0].width, sum(r.height for r in rows)))
    y = 0
    for r in rows:
        out.paste(r, (0, y)); y += r.height
    return out


if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    opt = dict(a[2:].split('=', 1) if '=' in a else (a[2:], '1') for a in sys.argv[1:] if a.startswith('--'))
    views = opt.get('views', 'front,q,side,back').split(',')
    hide = tuple(opt.get('hide', 'weapon').split(','))
    img = sheet(args, views, int(opt.get('scale', 12)), hide, glow='glow' in opt)
    out = opt.get('out', '/tmp/geo_preview.png')
    img.save(out)
    print(out, img.size)
