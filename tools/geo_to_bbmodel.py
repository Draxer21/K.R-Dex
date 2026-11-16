#!/usr/bin/env python3
"""
Pasa los modelos GeckoLib (.geo.json) del mod a proyectos de Blockbench (.bbmodel), con la textura incrustada y, si el
modelo tiene animaciones, tambien estas.

  python3 tools/geo_to_bbmodel.py [--out carpeta] [nombre ...]

Sin nombres convierte todos (armaduras, kaijin, civiles y vehiculos). El resultado se abre con Blockbench (formato
"Bedrock Model", que viene de serie); desde ahi se exporta de vuelta a .geo.json con File > Export > Bedrock Geometry, o
se pasa al formato de GeckoLib con File > Convert Project si tienes el plugin.

Conversion (la misma que hace Blockbench al importar un .geo.json de Bedrock):
  - x se invierte: from = [-(origin.x + size.x), y, z], to = [-origin.x, y + size.y, z + size.z]; pivotes [-x, y, z].
  - rotaciones de cubos y huesos: [-rx, -ry, rz]; en las animaciones, rotacion [-x, -y, z] y posicion [-x, y, z].
  - UV por cara: cada cara con su nombre; up/down van con la UV invertida (u2, v2, u1, v1).
"""
import base64
import json
import os
import sys
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'dexrider')
MODELS = os.path.join(ASSETS, 'geckolib', 'models')
ANIMS = os.path.join(ASSETS, 'geckolib', 'animations')

# modelo -> archivo de animaciones (relativo a geckolib/animations)
ANIM_FOR = {'entity': 'entity/rider_bike.animation.json', 'item/armor/dex_armor': 'item/armor/dex_armor.animation.json'}


def uid(*parts):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'dexrider/' + '/'.join(parts)))


def texture_for(rel):
    """item/armor/rider_spore -> textures/item/armor/rider_spore.png; entity/x -> textures/entity/x.png"""
    p = os.path.join(ASSETS, 'textures', rel + '.png')
    return p if os.path.exists(p) else None


def inv(v):
    """Como Blockbench al importar animaciones de Bedrock: x (y en rotacion, tambien y) cambian de signo."""
    if isinstance(v, (int, float)):
        return -v
    v = str(v).strip()
    return v if v in ('', '0') else '-(' + v + ')'


def neg(v):
    return [-v[0], v[1], v[2]]


def face_uv(key, f, w, h):
    u, v = f['uv']
    su, sv = f.get('uv_size', [0, 0])
    if key in ('up', 'down'):
        return [u + su, v + sv, u, v]
    return [u, v, u + su, v + sv]


def convert_cube(c, bone, idx, model, texw, texh):
    o, s = c['origin'], c['size']
    inf = c.get('inflate', 0)
    rot = c.get('rotation', [0, 0, 0])
    pivot = c.get('pivot', bone.get('pivot', [0, 0, 0]))
    el = {
        'name': bone['name'],
        'box_uv': not isinstance(c.get('uv'), dict),
        'rescale': False,
        'locked': False,
        'render_order': 'default',
        'allow_mirror_modeling': True,
        'from': [-(o[0] + s[0]), o[1], o[2]],
        'to': [-o[0], o[1] + s[1], o[2] + s[2]],
        'autouv': 0,
        'color': idx % 8,
        'origin': neg(pivot),
        'faces': {},
        'type': 'cube',
        'uuid': uid(model, bone['name'], str(idx)),
    }
    if inf:
        el['inflate'] = inf
    if any(rot):
        el['rotation'] = [-rot[0], -rot[1], rot[2]]
    if c.get('mirror') or bone.get('mirror'):
        el['mirror_uv'] = True
    uv = c.get('uv')
    if isinstance(uv, dict):
        for key in ('north', 'east', 'south', 'west', 'up', 'down'):
            f = uv.get(key)
            if f is None:
                el['faces'][key] = {'uv': [0, 0, 0, 0], 'texture': None}
            else:
                el['faces'][key] = {'uv': face_uv(key, f, texw, texh), 'texture': 0}
    else:
        # UV de caja (box UV): Blockbench la calcula a partir del desplazamiento
        el['uv_offset'] = list(uv or [0, 0])
        for key in ('north', 'east', 'south', 'west', 'up', 'down'):
            el['faces'][key] = {'uv': [0, 0, 0, 0], 'texture': 0}
    return el


def convert(rel, out_dir):
    path = os.path.join(MODELS, rel + '.geo.json')
    geo = json.load(open(path, encoding='utf-8'))
    g = geo['minecraft:geometry'][0]
    desc = g['description']
    texw, texh = desc.get('texture_width', 64), desc.get('texture_height', 64)
    name = os.path.basename(rel)

    elements, groups, bone_uuid = [], {}, {}
    for b in g.get('bones', []):
        bu = uid(rel, 'bone', b['name'])
        bone_uuid[b['name']] = bu
        rot = b.get('rotation', [0, 0, 0])
        grp = {
            'name': b['name'],
            'origin': neg(b.get('pivot', [0, 0, 0])),
            'rotation': [-rot[0], -rot[1], rot[2]],
            'color': 0,
            'uuid': bu,
            'export': True,
            'mirror_uv': bool(b.get('mirror')),
            'isOpen': False,
            'locked': False,
            'visibility': True,
            'autouv': 0,
            'children': [],
            '_parent': b.get('parent'),
        }
        for i, c in enumerate(b.get('cubes', [])):
            el = convert_cube(c, b, i, rel, texw, texh)
            elements.append(el)
            grp['children'].append(el['uuid'])
        groups[b['name']] = grp

    outliner = []
    for gname, grp in groups.items():
        parent = grp.pop('_parent')
        if parent and parent in groups:
            groups[parent]['children'].append(grp)
        else:
            outliner.append(grp)

    textures = []
    tex = texture_for(rel)
    if tex:
        data = base64.b64encode(open(tex, 'rb').read()).decode('ascii')
        textures.append({
            'path': '', 'name': os.path.basename(tex), 'folder': '', 'namespace': 'dexrider', 'id': '0',
            'width': texw, 'height': texh, 'uv_width': texw, 'uv_height': texh,
            'particle': False, 'render_mode': 'default', 'render_sides': 'auto', 'frame_time': 1,
            'frame_order_type': 'loop', 'frame_order': '', 'frame_interpolate': False, 'visible': True,
            'internal': True, 'saved': True, 'uuid': uid(rel, 'texture'), 'relative_path': os.path.relpath(tex, out_dir).replace(os.sep, '/'),
            'mode': 'bitmap', 'source': 'data:image/png;base64,' + data,
        })

    animations = []
    afile = ANIM_FOR.get(rel) or ANIM_FOR.get(rel.split('/')[0])
    if afile and os.path.exists(os.path.join(ANIMS, afile)):
        adata = json.load(open(os.path.join(ANIMS, afile), encoding='utf-8'))
        for aname, a in adata.get('animations', {}).items():
            animators = {}
            for bname, channels in a.get('bones', {}).items():
                if bname not in bone_uuid:
                    continue
                kfs = []
                for ch in ('rotation', 'position', 'scale'):
                    val = channels.get(ch)
                    if val is None:
                        continue
                    frames = val if isinstance(val, dict) and not any(k in val for k in ('pre', 'post', 'vector')) else {'0.0': val}
                    for t, v in frames.items():
                        if isinstance(v, dict):
                            v = v.get('post', v.get('vector', v.get('pre')))
                        if not isinstance(v, list):
                            v = [v, v, v]
                        x, y, z = v
                        if ch in ('rotation', 'position'):
                            x = inv(x)
                        if ch == 'rotation':
                            y = inv(y)
                        kfs.append({'channel': ch, 'data_points': [{'x': x, 'y': y, 'z': z}],
                                    'uuid': uid(rel, aname, bname, ch, str(t)), 'time': float(t),
                                    'color': -1, 'interpolation': 'linear'})
                animators[bone_uuid[bname]] = {'name': bname, 'type': 'bone', 'keyframes': kfs}
            animations.append({'uuid': uid(rel, 'anim', aname), 'name': aname,
                               'loop': 'loop' if a.get('loop') else 'once', 'override': False,
                               'length': a.get('animation_length', 0), 'snapping': 20, 'selected': False,
                               'anim_time_update': '', 'blend_weight': '', 'start_delay': '', 'loop_delay': '',
                               'animators': animators})

    bb = {
        'meta': {'format_version': '4.5', 'model_format': 'bedrock', 'box_uv': False},
        'name': name,
        'model_identifier': desc.get('identifier', 'geometry.' + name).replace('geometry.', ''),
        'visible_box': [desc.get('visible_bounds_width', 1), desc.get('visible_bounds_height', 1),
                        desc.get('visible_bounds_offset', [0, 0, 0])[1]],
        'variable_placeholders': '',
        'variable_placeholder_buttons': [],
        'timeline_setups': [],
        'unhandled_root_fields': {},
        'resolution': {'width': texw, 'height': texh},
        'elements': elements,
        'outliner': outliner,
        'textures': textures,
        'animations': animations,
    }
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, name + '.bbmodel')
    json.dump(bb, open(out, 'w', encoding='utf-8'), separators=(',', ':'))
    return out, len(elements), len(groups), bool(textures), len(animations)


def all_models():
    out = []
    for root, _, files in os.walk(MODELS):
        for f in files:
            if f.endswith('.geo.json'):
                out.append(os.path.relpath(os.path.join(root, f), MODELS)[:-len('.geo.json')].replace(os.sep, '/'))
    return sorted(out)


def main(argv):
    out_root = os.path.join(HERE, '..', 'build', 'bbmodel')
    names = []
    i = 0
    while i < len(argv):
        if argv[i] == '--out':
            out_root = argv[i + 1]; i += 2
        else:
            names.append(argv[i]); i += 1
    models = all_models()
    if names:
        models = [m for m in models if os.path.basename(m) in names]
    for rel in models:
        sub = 'vehiculos' if rel.startswith('entity/') else 'trajes'
        out, ne, ng, tex, na = convert(rel, os.path.join(out_root, sub))
        print(f'{os.path.relpath(out, out_root)}: {ng} huesos, {ne} cubos' + ('' if tex else ', SIN textura') + (f', {na} animaciones' if na else ''))


if __name__ == '__main__':
    main(sys.argv[1:])
