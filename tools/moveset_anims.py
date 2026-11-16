#!/usr/bin/env python3
"""
Animaciones del moveset (0.56) para Player Animation Library: las agrega a
assets/dexrider/player_animations/dex_poses.json (formato Bedrock, grados, segundos).
Son poses propias y genericas (corte, estocada, patada...); cada movimiento del moveset usa una.

  python3 tools/moveset_anims.py
"""
import json, os

PATH = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'dexrider', 'player_animations', 'dex_poses.json')
Z = [0, 0, 0]


def anim(length, **bones):
    out = {}
    for bone, keys in bones.items():
        rot = {}
        for t, v in keys:
            rot[f'{t:.2f}'.rstrip('0').rstrip('.') if t else '0.0'] = v
        if '0.0' not in rot:
            rot['0.0'] = Z
        rot[f'{length:.2f}'.rstrip('0').rstrip('.')] = Z
        out[bone] = {'rotation': dict(sorted(rot.items(), key=lambda kv: float(kv[0])))}
    return {'animation_length': length, 'loop': False, 'bones': out}


A = {
    'm_jab': anim(0.3, right_arm=[(0.08, [-95, 0, 0]), (0.16, [-90, 0, 0])], body=[(0.08, [0, -15, 0])]),
    'm_cross': anim(0.35, left_arm=[(0.1, [-95, 0, 0]), (0.18, [-90, 0, 0])], body=[(0.1, [0, 22, 0])], right_arm=[(0.1, [-30, 0, 10])]),
    'm_kick_high': anim(0.45, right_leg=[(0.12, [-60, 0, 0]), (0.2, [-105, 0, 15]), (0.3, [-95, 0, 10])], body=[(0.2, [-8, 15, 0])],
                        left_arm=[(0.2, [-40, 0, -30])], right_arm=[(0.2, [20, 0, 30])]),
    'm_kick_spin': anim(0.5, body=[(0.15, [0, 120, 0]), (0.3, [0, 260, 0]), (0.45, [0, 360, 0])],
                        right_leg=[(0.15, [-30, 0, 40]), (0.3, [-85, 0, 60]), (0.42, [-20, 0, 10])], left_arm=[(0.2, [0, 0, -80])], right_arm=[(0.2, [0, 0, 80])]),
    'm_slash_r': anim(0.35, right_arm=[(0.06, [-100, 50, 30]), (0.16, [-80, -60, -10]), (0.25, [-60, -50, 0])], body=[(0.06, [0, 25, 0]), (0.16, [0, -25, 0])]),
    'm_slash_l': anim(0.35, right_arm=[(0.06, [-80, -60, -10]), (0.16, [-100, 50, 30]), (0.25, [-70, 40, 20])], body=[(0.06, [0, -25, 0]), (0.16, [0, 25, 0])]),
    'm_slash_down': anim(0.4, right_arm=[(0.12, [-175, 0, 10]), (0.22, [-35, 0, 0]), (0.3, [-40, 0, 0])], left_arm=[(0.12, [-150, 0, -10]), (0.22, [-40, 0, 0])],
                         body=[(0.12, [-8, 0, 0]), (0.22, [15, 0, 0])]),
    'm_slash_up': anim(0.4, right_arm=[(0.1, [-15, 0, 15]), (0.22, [-170, 0, -10])], body=[(0.1, [12, 0, 0]), (0.22, [-12, 0, 0])],
                       right_leg=[(0.22, [-25, 0, 0])]),
    'm_thrust': anim(0.35, right_arm=[(0.08, [-70, 0, 0]), (0.16, [-92, 0, 0])], body=[(0.08, [0, 15, 0]), (0.16, [6, -20, 0])],
                     right_leg=[(0.16, [-35, 0, 0])], left_leg=[(0.16, [20, 0, 0])]),
    'm_spin': anim(0.55, body=[(0.15, [0, 120, 0]), (0.3, [0, 260, 0]), (0.45, [0, 360, 0])], right_arm=[(0.1, [-90, 0, 80]), (0.4, [-90, 0, 70])],
                   left_arm=[(0.1, [0, 0, -40])]),
    'm_heavy': anim(0.6, right_arm=[(0.2, [-40, 30, 40]), (0.32, [-95, -10, 0]), (0.45, [-90, 0, 0])], body=[(0.2, [-5, 35, 0]), (0.32, [10, -30, 0])],
                    left_arm=[(0.2, [-60, 0, -20]), (0.32, [20, 0, -10])], right_leg=[(0.32, [-25, 0, 0])]),
    'm_dash': anim(0.45, body=[(0.1, [25, 0, 0]), (0.3, [20, 0, 0])], right_arm=[(0.1, [-95, 0, 0])], left_arm=[(0.1, [40, 0, -10])],
                   right_leg=[(0.1, [-45, 0, 0])], left_leg=[(0.1, [35, 0, 0])]),
    'm_air': anim(0.5, body=[(0.12, [-10, 0, 0]), (0.25, [30, 0, 0])], right_arm=[(0.12, [-165, 0, 10]), (0.25, [-40, 0, 0])],
                  left_arm=[(0.12, [-165, 0, -10]), (0.25, [-40, 0, 0])], right_leg=[(0.25, [-40, 0, 0])], left_leg=[(0.25, [-30, 0, 0])]),
    'm_evade': anim(0.45, body=[(0.1, [20, -90, 0]), (0.25, [20, -220, 0]), (0.4, [5, -360, 0])], right_leg=[(0.1, [-40, 0, 50]), (0.3, [-30, 0, 40])],
                    left_leg=[(0.1, [30, 0, 0])]),
    'm_counter': anim(0.4, right_arm=[(0.06, [-60, 0, 20]), (0.14, [-100, 0, 0])], left_arm=[(0.06, [-60, 0, -30]), (0.14, [-20, 0, -20])],
                      body=[(0.06, [0, 20, 0]), (0.14, [5, -25, 0])]),
    'm_shot': anim(0.3, right_arm=[(0.04, [-90, 0, 0]), (0.09, [-112, 0, 0]), (0.18, [-92, 0, 0])], body=[(0.04, [0, -10, 0])]),
    'm_whip': anim(0.45, right_arm=[(0.12, [-170, 0, -30]), (0.25, [-55, 0, 40])], body=[(0.12, [-6, 15, 0]), (0.25, [8, -15, 0])]),
    'dodge_f': anim(0.35, body=[(0.1, [30, 0, 0])], right_arm=[(0.1, [30, 0, 20])], left_arm=[(0.1, [30, 0, -20])], right_leg=[(0.1, [-40, 0, 0])], left_leg=[(0.1, [30, 0, 0])]),
    'dodge_b': anim(0.35, body=[(0.1, [-18, 0, 0])], right_arm=[(0.1, [-50, 0, 20])], left_arm=[(0.1, [-50, 0, -20])], right_leg=[(0.1, [25, 0, 0])], left_leg=[(0.1, [-30, 0, 0])]),
    'dodge_l': anim(0.35, body=[(0.1, [0, 0, -22])], right_arm=[(0.1, [0, 0, 50])], left_arm=[(0.1, [-30, 0, -20])], right_leg=[(0.1, [0, 0, 25])], left_leg=[(0.1, [0, 0, -10])]),
    'dodge_r': anim(0.35, body=[(0.1, [0, 0, 22])], left_arm=[(0.1, [0, 0, -50])], right_arm=[(0.1, [-30, 0, 20])], left_leg=[(0.1, [0, 0, -25])], right_leg=[(0.1, [0, 0, 10])]),
}

d = json.load(open(PATH))
d['animations'].update(A)
json.dump(d, open(PATH, 'w'), indent=2)
print('ok', len(d['animations']), 'animaciones')
