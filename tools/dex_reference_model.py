#!/usr/bin/env python3
"""Build Dex's reference-based suit and all three base Driver representations.

Run from any directory: python tools/dex_reference_model.py [--preview] [--check-only]
Requires Pillow and NumPy. Geometry/UV/materials are authored here; optional suit
accessories are carried forward from the immutable original-resource archive.
The archive is created once, before writing resources. Keep it with this script.
"""
from __future__ import annotations

import argparse
import copy
import io
import json
import math
from pathlib import Path
import zipfile

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/dexrider'
GEO = ASSETS / 'geckolib/models/item/armor/dex_armor.geo.json'
TEX = ASSETS / 'textures/item/armor'
JAVA = ROOT / 'src/main/java/com/draxer/dexrider'
OUT = ROOT / 'diseno/dex'
BACKUP = OUT / 'respaldo-0.56.0.zip'
ATLAS_Y = 608
TILE = 32
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
COLORS = {'main': (205, 28, 40), 'accent': (232, 182, 48),
          'inner': (81, 84, 93), 'silver': (255, 255, 255),
          'eyes': (222, 35, 203), 'scarf': (255, 255, 255),
          'crystal': (222, 35, 203), 'lines': (28, 28, 36)}
# Material, runtime tint layer (None means fixed Driver colors), base swatch.
MATERIALS = {
    'red': ('main', (232, 232, 232)),
    'gold': ('accent', (242, 242, 242)),
    'black': ('inner', (75, 75, 75)),
    'panel': ('inner', (118, 118, 118)),
    'silver': ('silver', (212, 212, 212)),
    'eye': ('eyes', (240, 240, 240)),
    'driver_black': (None, (28, 30, 37)),
    'driver_red': (None, (160, 16, 32)),
    'driver_silver': (None, (200, 208, 220)),
    'driver_lens': (None, (244, 24, 39)),
    'driver_light': (None, (218, 30, 188)),
    'driver_dark': (None, (9, 10, 14)),
}


def relative(path):
    return path.relative_to(ROOT).as_posix()


def backup():
    OUT.mkdir(parents=True, exist_ok=True)
    if BACKUP.exists():
        return
    paths = [GEO, *TEX.glob('dex_armor*.png'),
             ASSETS / 'textures/entity/dex_driver_belt.png',
             ASSETS / 'models/item/dex_driver.json',
             JAVA / 'client/DexDriverBeltModel.java',
             JAVA / 'item/SuitLooks.java']
    with zipfile.ZipFile(BACKUP, 'x', compression=zipfile.ZIP_DEFLATED) as archive:
        for path in paths:
            archive.write(path, relative(path))
    print('Original resources preserved:', relative(BACKUP))


def original(path):
    with zipfile.ZipFile(BACKUP) as archive:
        return archive.read(relative(path))


def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=1, ensure_ascii=False) + '\n', encoding='utf-8')


def swatch(name, finish='normal'):
    """Procedural pixel material authored for the native model, not concept art."""
    color = MATERIALS[name][1]
    im = Image.new('RGBA', (TILE, TILE))
    for y in range(TILE):
        for x in range(TILE):
            edge = min(x, y, TILE - 1 - x, TILE - 1 - y)
            v = 0.90 + 0.10 * (1 - y / (TILE - 1))
            if finish == 'matte':
                v = 0.91 + 0.025 * (1 - y / (TILE - 1))
            elif finish == 'metal':
                v = 0.72 + 0.26 * max(0, 1 - abs(x - 0.65 * y - 6) / 9)
            if edge == 0:
                v *= 0.72
            elif edge == 1 and (x < 3 or y < 3):
                v = min(1.08, v + 0.08)
            if name == 'gold':
                v *= 1.0 if (x + y * 3) % 17 else 0.97
            if name == 'eye':
                v = (1.0, 0.78, 0.89, 0.95)[((x // 2) + 2 * (y // 2)) % 4]
            if name == 'driver_lens':
                # Continuous glass on the rows forming the circular silhouette.
                v = 0.78 + 0.22 * max(0, 1 - abs(x - 14) / 18)
            im.putpixel((x, y), (*[min(255, round(c * v)) for c in color], 255))
    return im


def atlas_origin(material):
    i = list(MATERIALS).index(material)
    return (i % 8) * 40, ATLAS_Y + (i // 8) * 40


def uv(material):
    x, y = atlas_origin(material)
    return {f: {'uv': [x, y], 'uv_size': [TILE, TILE]} for f in FACES}


def cube(origin, size, material, rotation=None, pivot=None):
    c = {'origin': [round(v, 4) for v in origin],
         'size': [round(v, 4) for v in size], 'uv': uv(material)}
    if rotation and any(rotation):
        c['rotation'] = [round(v, 4) for v in rotation]
        c['pivot'] = [round(v, 4) for v in (pivot or origin)]
    return c


def bar(a, b, width, depth, material):
    """A cuboid following an XY segment; geo_preview uses Rz(-rotation.z)."""
    dx, dy = b[0] - a[0], b[1] - a[1]
    length = math.hypot(dx, dy)
    center = [(a[i] + b[i]) / 2 for i in range(3)]
    return cube([center[0] - width / 2, center[1] - length / 2, center[2] - depth / 2],
                [width, length + 0.035, depth], material,
                [0, 0, math.degrees(math.atan2(dx, dy))], center)


def horn(points, widths, depth):
    return [bar(points[i], points[i + 1], widths[i], depth[i], 'gold')
            for i in range(len(points) - 1)]


def driver_parts():
    """Single device specification in suit-space units (front is -Z)."""
    parts = []
    def add(o, s, mat, r=0, p=None):
        parts.append((o, s, mat, r, p))
    add([-4.35, -1.12, -0.22], [8.7, 2.24, 0.62], 'driver_black')
    for side in (-1, 1):
        add([side * 2.62 - 1.08, -0.86, -0.49], [2.16, 1.72, 0.36], 'driver_red')
        add([side * 2.83 - 1.25, -0.35, -0.68], [2.5, 0.70, 0.30], 'driver_black')
        add([side * 4.08 - 0.16, -1.02, -0.51], [0.32, 2.04, 0.30], 'driver_silver')
        for n in range(3):
            x = side * (2.05 + n * 0.53)
            add([x - 0.19, -0.19, -0.84], [0.38, 0.38, 0.18], 'driver_silver')
            add([x - 0.12, -0.12, -0.90], [0.24, 0.24, 0.10], 'driver_light')
    # Nested octagonal rings around a filled red lens.
    for radius, thickness, z, depth, mat in (
            (1.61, 0.38, -0.54, 0.54, 'driver_red'),
            (1.22, 0.27, -0.90, 0.28, 'driver_silver'),
            (0.96, 0.18, -1.06, 0.18, 'driver_dark')):
        for i in range(8):
            a = (i + 0.5) * math.pi / 4
            center = [math.cos(a) * radius, math.sin(a) * radius, z]
            # A vertical bar tangent to the octagon. Rotation uses geo's convention.
            length = 2 * radius * math.tan(math.pi / 8) + 0.045
            add([center[0] - thickness / 2, center[1] - length / 2, z - depth / 2],
                [thickness, length, depth], mat, -math.degrees(a), center)
    for row in range(10):
        y = -0.88 + row * 0.176
        half = math.sqrt(max(0.0, 0.91 ** 2 - (y + 0.088) ** 2))
        add([-half, y, -1.15], [2 * half, 0.18, 0.30], 'driver_lens')
    add([-0.82, 1.59, -0.27], [1.64, 0.23, 0.64], 'driver_silver')
    add([-0.63, 1.70, -0.32], [1.26, 0.07, 0.35], 'driver_dark')
    for i in range(5):
        add([-0.66 + i * 0.28, 1.28, -0.91], [0.17, 0.14, 0.10], 'driver_silver')
    return parts


def driver_cubes(center=(0, 12.55, -2.90)):
    out = []
    for o, s, material, r, p in driver_parts():
        out.append(cube([o[i] + center[i] for i in range(3)], s, material,
                        [0, 0, r], None if p is None else [p[i] + center[i] for i in range(3)]))
    return out


def build_geometry():
    data = json.loads(original(GEO))
    g = data['minecraft:geometry'][0]
    bones = {b['name']: b for b in g['bones']}
    head = [cube([-4.1, 24.3, -4.10], [8.2, 8.0, 8.2], 'red')]
    # Thin vertical face/crown ridges leave the center red, as in the reference.
    for x in (-0.77, 0.42):
        head.append(cube([x, 24.75, -4.43], [0.35, 7.58, 0.34], 'black'))
        head.append(cube([x, 32.30, -4.10], [0.35, 0.22, 8.2], 'black'))
        head.append(cube([x, 25.0, 4.09], [0.35, 7.3, 0.24], 'black'))
    # Lens profile, built in slim rows with a dark perimeter and faceted surface.
    rows = [(26.0, 1.25, 2.75), (26.7, 0.78, 3.35),
            (27.4, 0.72, 3.65), (28.1, 0.72, 3.65),
            (28.8, 1.00, 3.65), (29.5, 1.60, 3.60), (30.2, 2.30, 3.50)]
    for side in (-1, 1):
        for y, inner, outer in rows:
            left = inner if side > 0 else -outer
            w = outer - inner
            head.append(cube([left - 0.16, y - 0.10, -4.52], [w + 0.32, 0.92, 0.43], 'black'))
            head.append(cube([left, y, -4.73], [w, 0.72, 0.28], 'eye'))
        head.append(cube([side * 3.58 - 0.27, 24.55, -3.9], [0.54, 1.05, 1.6], 'silver'))
    head.append(cube([-2.95, 24.18, -4.40], [5.9, 0.53, 0.50], 'silver'))
    bones['armorHead']['cubes'] = head

    outer, inner, straight, short = [], [], [], []
    for side in (-1, 1):
        points = [[side*x,y,z] for x,y,z in [(3.95,29.5,-1.6),(5.65,30.5,-1.6),
                   (7.0,32.0,-1.5),(7.65,33.8,-1.3),(8.65,34.65,-1.2)]]
        outer += horn(points, [1.25,1.05,0.72,0.36], [1.5,1.25,0.9,0.48])
        points = [[side*x,y,z] for x,y,z in [(1.65,30.1,-4.2),(2.55,32.15,-4.1),
                   (2.75,34.2,-3.85),(2.35,35.9,-3.65),(1.85,36.85,-3.45)]]
        inner += horn(points, [1.0,0.85,0.57,0.27], [1.2,1.05,0.72,0.4])
        straight += horn([[side*4.1,29.5,-1.5],[side*6.2,32,-1.4],[side*8.5,34.6,-1.3]],
                         [1.15,0.70],[1.4,0.9])
        short += horn([[side*4.1,29.5,-1.5],[side*5.4,31.2,-1.4],[side*5.9,32.0,-1.3]],
                      [1.0,0.5],[1.3,0.75])
    for name, cubes in [('hornsCurved',outer),('hornsInner',inner),
                        ('hornsStraight',straight),('hornsShort',short)]:
        bones[name]['cubes'] = cubes

    body = [cube([-4,12,-2], [8,12,4], 'black'),
            cube([-4.15,18.15,-2.48], [8.3,5.65,0.48], 'panel'),
            cube([-4.15,18.15,2.0], [8.3,5.65,0.42], 'panel')]
    for side in (-1,1):
        body.append(cube([side*3.77-0.45,13.55,-2.3], [0.9,10.1,4.6], 'red'))
        body.append(cube([side*3.2-0.16,13.7,-2.61], [0.32,5.2,0.32], 'gold'))
        for row in range(3):
            body.append(cube([side*1.35-1.08,14.0+row*1.36,-2.36], [2.16,1.16,0.44], 'panel'))
    # The gold chest motif follows the diagonal, off-center design in the image.
    for z in (-2.90,2.67):
        for y in (18.7,17.65):
            body.append(bar([-4.10,y,z],[4.10,y+3.85,z],0.64,0.30,'gold'))
        body.append(cube([1.77,19.3,z-0.16], [0.62,4.60,0.34], 'gold'))
    body += [cube([-2.6,23.75,-2.75],[5.2,0.65,0.45],'silver'),
             cube([-2.6,23.75,2.25],[5.2,0.65,0.45],'silver'),
             cube([-2.6,23.75,-2.3],[0.45,0.65,4.6],'silver'),
             cube([2.15,23.75,-2.3],[0.45,0.65,4.6],'silver'),
             cube([-4.45,11.55,-2.5],[8.9,1.8,5.0],'driver_black')]
    body += driver_cubes()
    bones['armorBody']['cubes'] = body

    for side, name in [(-1,'Right'),(1,'Left')]:
        x = side * 6.0
        arm = [cube([x-2,12,-2],[4,12,4],'black'),
               cube([x-2.12,19.25,-2.12],[4.24,4.25,4.24],'red'),
               cube([x-2.25,13.05,-2.27],[4.5,0.65,4.54],'gold'),
               cube([x-2.30,17.15,-2.30],[4.6,0.70,4.6],'gold'),
               cube([x+side*1.60-0.45,13.7,-2.19],[0.9,3.45,4.38],'gold'),
               cube([x-1.50,12.05,-2.32],[3.0,0.85,0.38],'gold')]
        bones['armor'+name+'Arm']['cubes'] = arm
        sx = x - 2.55
        shoulders = [cube([sx,21,-2.65],[5.1,3.45,5.3],'red')]
        for y in (21.25,22.48):
            shoulders += [cube([sx-0.12,y,-2.89],[5.34,0.48,0.34],'gold'),
                          cube([sx-0.12,y,2.55],[5.34,0.48,0.34],'gold'),
                          cube([x+side*2.6-0.20,y,-2.55],[0.40,0.48,5.1],'gold')]
        bones['pauldron'+('R' if side<0 else 'L')]['cubes'] = shoulders
        bones['pauldronFlat'+('R' if side<0 else 'L')]['cubes'] = [
            cube([x-2.25,22,-2.25],[4.5,2.1,4.5],'red'),
            cube([x-2.3,22.2,-2.45],[4.6,0.48,0.3],'gold')]
        lx = side * 1.95
        leg = [cube([lx-1.95,5.25,-2],[3.9,6.75,4],'black'),
               cube([lx+side*1.28-0.57,6.5,-2.17],[1.14,5.5,4.34],'red'),
               cube([lx+side*0.60-0.16,6.45,-2.39],[0.32,5.6,0.30],'gold'),
               cube([lx-1.60,4.85,-2.62],[3.2,1.70,0.72],'gold')]
        bones['armor'+name+'Leg']['cubes'] = leg
        boot = [cube([lx-2.04,0,-2.25],[4.08,5.12,4.5],'black'),
                cube([lx-2.08,0,-2.95],[4.16,0.85,5.2],'black'),
                cube([lx+side*1.55-0.45,1.3,-2.35],[0.9,3.9,4.7],'red')]
        for ox in (-0.96,0.96):
            boot.append(cube([lx+ox-0.48,1.1,-2.69],[0.96,3.95,0.48],'gold'))
        boot.append(cube([lx-1.75,0.9,-3.0],[3.5,0.52,1.25],'gold'))
        bones['armor'+name+'Boot']['cubes'] = boot
    # Include the new silhouette in culling bounds. Rig names and optional bones survive.
    g['description']['visible_bounds_width'] = 4
    g['description']['visible_bounds_height'] = 4
    g['description']['visible_bounds_offset'] = [0,1.5,0]
    return data


def eye_lines(suffix):
    if suffix == 'eyes_plain':
        return None
    if suffix in ('eyes','lines'):
        return ('h',1,3)
    words = suffix.split('_')
    if len(words) == 4 and words[0] in ('eyes','lines'):
        return (words[1],int(words[2]),int(words[3]))
    return None


def eye_tile(suffix):
    tile=swatch('eye')
    spec=eye_lines(suffix)
    for y in range(TILE):
        for x in range(TILE):
            line=False
            if spec:
                orientation,thick,gap=spec
                pos={'h':y,'v':x,'d':x+y,'w':y+round(2*math.sin(x*math.pi/12))}[orientation]
                line=(pos%(thick+gap))<thick
            keep=line if suffix.startswith('lines') else not line
            if not keep: tile.putpixel((x,y),(0,0,0,0))
            elif suffix.startswith('lines'): tile.putpixel((x,y),(235,235,235,255))
    return tile


def refresh_visor_tiles():
    """Called by suit_parts.py after its legacy visor textures are regenerated."""
    if not BACKUP.exists():
        return
    paths=list(TEX.glob('dex_armor_eyes*.png'))+list(TEX.glob('dex_armor_lines*.png'))
    for path in paths:
        image=Image.open(path).convert('RGBA')
        image.paste(eye_tile(path.stem[len('dex_armor_'):]),atlas_origin('eye'))
        image.save(path)


def build_textures():
    with zipfile.ZipFile(BACKUP) as archive:
        for key in archive.namelist():
            if not key.startswith(relative(TEX)+'/dex_armor') or not key.endswith('.png'):
                continue
            image = Image.open(io.BytesIO(archive.read(key))).convert('RGBA')
            assert image.size == (512,1024)
            suffix = Path(key).stem[len('dex_armor'):].lstrip('_')
            image.paste((0,0,0,0),(0,ATLAS_Y,512,ATLAS_Y+80))
            finish = 'metal' if suffix.endswith('_metal') else 'matte' if suffix.endswith('_matte') else 'normal'
            base_layer = suffix.replace('_metal','').replace('_matte','')
            for material,(layer,_) in MATERIALS.items():
                tile = swatch(material,finish)
                selected = (layer is None and suffix=='') or layer==base_layer
                if layer=='main' and base_layer in ('main_right','main_left'):
                    # Split custom-tinted halves are populated per-face below after UV remapping.
                    selected = False
                if layer=='eyes' and (suffix.startswith('eyes') or suffix.startswith('lines')):
                    selected=True
                    tile=eye_tile(suffix)
                if suffix=='glowmask' and material in ('eye','driver_lens','driver_light'):
                    selected=True
                    if material=='eye':
                        a=np.array(tile); a[:,:,:3]=(a[:,:,:3].astype(float)*np.array(COLORS['eyes'])/255).astype(np.uint8); tile=Image.fromarray(a)
                if selected:
                    image.paste(tile,atlas_origin(material))
            image.save(ROOT/key)
    driver_glow=Image.new('RGBA',(512,1024))
    for material in ('driver_lens','driver_light'):
        driver_glow.paste(swatch(material),atlas_origin(material))
    driver_glow.save(TEX/'dex_armor_driver_glow.png')
    # Main-color cubes on either side need distinct UVs for the Half form layers.
    # Reserve an exact copy of the red tile for each side; geometry is remapped below.
    ox,oy=atlas_origin('red')
    for suffix in ('main','main_matte','main_metal','main_right','main_right_matte','main_right_metal',
                   'main_left','main_left_matte','main_left_metal'):
        path=TEX/f'dex_armor_{suffix}.png'
        im=Image.open(path).convert('RGBA')
        finish='metal' if suffix.endswith('_metal') else 'matte' if suffix.endswith('_matte') else 'normal'
        im.paste((0,0,0,0),(0,ATLAS_Y+80,128,ATLAS_Y+120))
        for side,tx in [('right',0),('left',40)]:
            if 'right' not in suffix and 'left' not in suffix or side in suffix:
                im.paste(swatch('red',finish),(tx,ATLAS_Y+80))
        im.save(path)


def split_main_uv(data):
    """Keep all main layers recolorable, including Half-Cold Half-Hot's two sides."""
    old=atlas_origin('red')
    for b in data['minecraft:geometry'][0]['bones']:
        updated=[]
        for c in b.get('cubes',[]):
            if c['uv']['north']['uv']!=list(old):
                updated.append(c); continue
            # Only axis-aligned main plates are used. Split straddling cubes at X=0.
            lo=c['origin'][0]; hi=lo+c['size'][0]
            parts=[c]
            if lo<0<hi and not c.get('rotation'):
                left=copy.deepcopy(c); right=copy.deepcopy(c)
                left['size'][0]=-lo; right['origin'][0]=0; right['size'][0]=hi
                # Internal cut faces are fully transparent, rather than dark seams.
                left['uv']['east']['uv_size']=[0,0]; right['uv']['west']['uv_size']=[0,0]
                parts=[left,right]
            for piece in parts:
                center=piece['origin'][0]+piece['size'][0]/2
                for face in piece['uv'].values(): face['uv']=[0 if center<0 else 40,ATLAS_Y+80]
                updated.append(piece)
        b['cubes']=updated


def driver_palette():
    im=Image.new('RGBA',(256,256))
    for i,name in enumerate(MATERIALS):
        if not name.startswith('driver_'): continue
        idx=list(k for k in MATERIALS if k.startswith('driver_')).index(name)
        tile=swatch(name).resize((64,64),Image.Resampling.NEAREST)
        im.paste(tile,((idx%4)*64,(idx//4)*64))
    im.save(ASSETS/'textures/entity/dex_driver_belt.png')
    im.save(ASSETS/'textures/item/dex_driver_3d.png')


def palette_xy(material):
    idx=list(k for k in MATERIALS if k.startswith('driver_')).index(material)
    return (idx%4)*64,(idx//4)*64


def build_driver_item():
    elements=[]
    for origin,size,mat,angle,pivot in driver_parts():
        center=[origin[i]+size[i]/2 for i in range(3)]
        s=list(size)
        # Vanilla item rotations allow +/-45 and +/-22.5 degrees. Fold equivalent
        # 90-degree turns into the cuboid dimensions for the octagonal bezel.
        a=angle
        while a>45: a-=90; s[0],s[1]=s[1],s[0]
        while a<-45: a+=90; s[0],s[1]=s[1],s[0]
        # Ring segments have half-step angles (22.5 degrees), so these are exact.
        a=round(a,4)
        pos=[8+center[0]*1.45,8+center[1]*1.45,8+center[2]*1.45]
        half=[v*1.45/2 for v in s]
        tx,ty=palette_xy(mat)
        rect=[(tx+4)/16,(ty+4)/16,(tx+60)/16,(ty+60)/16]
        e={'from':[round(pos[i]-half[i],4) for i in range(3)],
           'to':[round(pos[i]+half[i],4) for i in range(3)],
           'faces':{f:{'uv':rect,'texture':'#driver'} for f in FACES}}
        if a: e['rotation']={'origin':pos,'axis':'z','angle':-a,'rescale':False}
        elements.append(e)
    model={'ambientocclusion':False,'textures':{'driver':'dexrider:item/dex_driver_3d','particle':'dexrider:item/dex_driver_3d'},
           'gui_light':'front','elements':elements,
           'display':{'gui':{'rotation':[12,160,0],'translation':[0,0,0],'scale':[1.25,1.25,1.25]},
             'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[0.6,0.6,0.6]},
             'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[1,1,1]},
             'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[0.65,0.65,0.65]},
             'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[0.65,0.65,0.65]},
             'firstperson_righthand':{'rotation':[0,-25,0],'translation':[1,1,0],'scale':[0.9,0.9,0.9]},
             'firstperson_lefthand':{'rotation':[0,25,0],'translation':[1,1,0],'scale':[0.9,0.9,0.9]}}}
    dump(ASSETS/'models/item/dex_driver.json',model)


def build_curios_java():
    source=original(JAVA/'client/DexDriverBeltModel.java').decode('utf-8')
    start=source.index('        // Torso:')
    end=source.index('\n    }',start)
    lines=['        // Generated by tools/dex_reference_model.py from the same device as the armor/item.',
           '        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()',
           '                .texOffs(2, 2).addBox(-4.45F, 10.65F, -2.5F, 8.9F, 1.8F, 5.0F), PartPose.ZERO);']
    def f(n): return f'{n:.5f}F'
    for i,(o,s,mat,r,p) in enumerate(driver_parts()):
        x=o[0]+s[0]/2; y=24-(12.55+o[1]+s[1]/2); z=-2.9+o[2]+s[2]/2
        tx,ty=palette_xy(mat)
        lines += [f'        body.addOrReplaceChild("driver_{i}", CubeListBuilder.create().texOffs({tx+4}, {ty+4})',
            f'                .addBox({f(-s[0]/2)}, {f(-s[1]/2)}, {f(-s[2]/2)}, {f(s[0])}, {f(s[1])}, {f(s[2])}),',
            f'                PartPose.offsetAndRotation({f(x)}, {f(y)}, {f(z)}, 0F, 0F, {f(math.radians(r))}));']
    lines += ['        return LayerDefinition.create(mesh, 256, 256);']
    source=source[:start]+'\n'.join(lines)+source[end:]
    source=source.replace('(256x128, 4 px por unidad).','(256x256, atlas de materiales).')
    (JAVA/'client/DexDriverBeltModel.java').write_text(source,encoding='utf-8')


def validate():
    data=json.loads(GEO.read_text(encoding='utf-8'))
    g=data['minecraft:geometry'][0]
    names=[b['name'] for b in g['bones']]
    assert len(names)==len(set(names)), 'Duplicate bones'
    baseline=json.loads(original(GEO))['minecraft:geometry'][0]['bones']
    assert {b['name'] for b in baseline}==set(names), 'Rig or optional bones lost'
    parents={b['name']:b.get('parent') for b in baseline}
    for b in g['bones']:
        assert b.get('parent')==parents[b['name']], 'Parent changed'
        for c in b.get('cubes',[]):
            assert all(v>0 for v in c['size']), 'Degenerate cuboid'
            for face in c['uv'].values():
                for i,limit in enumerate((512,1024)):
                    a=face['uv'][i]; z=a+face['uv_size'][i]
                    assert 0<=min(a,z)<=max(a,z)<=limit, 'UV outside atlas'
    for path in TEX.glob('dex_armor*.png'):
        assert Image.open(path).size==(512,1024), str(path)
    x,y=atlas_origin('eye')
    for path in TEX.glob('dex_armor_eyes*.png'):
        if path.stem.endswith('_plain'): continue
        line=TEX/path.name.replace('_eyes','_lines')
        a=np.array(Image.open(path))[y:y+TILE,x:x+TILE,3]>0
        b=np.array(Image.open(line))[y:y+TILE,x:x+TILE,3]>0
        assert np.all(a|b) and not np.any(a&b), 'Lens/line UV masks do not complement each other'
    right=np.array(Image.open(TEX/'dex_armor_main_right.png'))
    left=np.array(Image.open(TEX/'dex_armor_main_left.png'))
    for b in g['bones']:
        for c in b.get('cubes',[]):
            u,v=c['uv']['north']['uv']
            if v!=ATLAS_Y+80: continue
            cx=c['origin'][0]+c['size'][0]/2
            assert (right[int(v)+16,int(u)+16,3]>0)==(cx<0), 'Wrong right-half tint'
            assert (left[int(v)+16,int(u)+16,3]>0)==(cx>=0), 'Wrong left-half tint'
    model=json.loads((ASSETS/'models/item/dex_driver.json').read_text())
    assert len(model['elements'])==len(driver_parts())
    for element in model['elements']:
        assert element.get('rotation',{}).get('angle',0) in (-45,-22.5,0,22.5,45)
    print('Checked:',len(names),'bones;',sum(len(b.get('cubes',[])) for b in g['bones']),
          'cuboids;',len(model['elements']),'Driver parts; valid UVs and item rotations.')


def preview():
    import geo_preview
    stage=OUT/'vistas'
    stage.mkdir(exist_ok=True)
    composite=Image.open(TEX/'dex_armor.png').convert('RGBA')
    glow=Image.open(TEX/'dex_armor_glowmask.png').convert('RGBA')
    for layer in ('main','accent','silver','inner','scarf','crystal','eyes'):
        suffix='eyes_plain' if layer=='eyes' else layer
        a=np.array(Image.open(TEX/f'dex_armor_{suffix}.png').convert('RGBA'))
        a[:,:,:3]=np.round(a[:,:,:3].astype(float)*np.array(COLORS[layer])/255).astype(np.uint8)
        composite.alpha_composite(Image.fromarray(a))
    composite.save(stage/'dex_armor.png')
    glow.save(stage/'dex_armor_glowmask.png')
    data=json.loads(GEO.read_text(encoding='utf-8'))
    dump(stage/'dex_armor.geo.json',data)
    hidden={'hornsStraight','hornsShort','pauldronFlatR','pauldronFlatL','scarf','scarfLong','crest','emotionCrystal',
            'spikesHead','spikesBody','spikesRArm','spikesLArm','skyWings','skyWingR','skyWingL'}
    for b in data['minecraft:geometry'][0]['bones']:
        if b['name'].startswith(('hydroFins','frostCrystals','cinderFlames','speedSpoiler','gasDucts','terraPlates')):
            hidden.add(b['name'])
    geo_preview.GEO=str(stage); geo_preview.TEX=str(stage)
    sheet=geo_preview.sheet(['dex_armor'],['front','q','side','back'],scale=16,hide=hidden)
    frame=Image.new('RGB',(sheet.width,sheet.height+70),(31,36,44))
    frame.paste(sheet,(0,44))
    draw=ImageDraw.Draw(frame)
    font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',20)
    draw.text((20,12),'DEX | MODELO GECKOLIB | geometria y texturas exportadas',font=font,fill=(236,237,240))
    for i,label in enumerate(('FRENTE','TRES CUARTOS','PERFIL','ESPALDA')):
        draw.text((20+i*sheet.width//4,frame.height-24),label,font=font,fill=(236,237,240))
    frame.save(OUT/'modelo-implementado.png')
    # A separate close view uses precisely the same cuboids and fixed material atlas.
    driver=copy.deepcopy(data)
    driver['minecraft:geometry'][0]['bones']=[{'name':'driver','pivot':[0,0,0],'cubes':driver_cubes((0,2.3,0))}]
    dump(stage/'driver.geo.json',driver)
    composite.save(stage/'driver.png')
    geo_preview.render('driver',-18,scale=65,size=(760,400),pitch=12).save(OUT/'driver-implementado.png')
    print('Model views:',relative(OUT/'modelo-implementado.png'))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preview',action='store_true')
    parser.add_argument('--check-only',action='store_true')
    args=parser.parse_args()
    if not args.check_only:
        backup()
        data=build_geometry()
        split_main_uv(data)
        build_textures()
        dump(GEO,data)
        driver_palette()
        build_driver_item()
        build_curios_java()
    validate()
    if args.preview: preview()


if __name__=='__main__':
    main()
