#!/usr/bin/env python3
"""
La casa de la montania de los Kagami (0.54): genera data/dexrider/structure/mountain_house.nbt.

  python3 tools/mountain_house.py

Plantas (la estructura se coloca 4 bloques bajo la superficie: la planta baja queda a ras de suelo):
  y 0-3   Sotano: el viejo taller de Takumi (yunque, alto horno, mesa de herreria, afiladora, antena), el estante
          del equipo Blaster (cofre del taller) y una zona de entrenamiento con dos combatientes de guardia.
  y 4-8   Planta baja: engawa (porche) con la puerta, sala con la mesa del te y cojines, chimenea, rincon de
          recuerdos con flores, cocina con despensa (cofre) y el garaje de Tetsuya con el banco de la moto.
  y 9-12  Planta alta: cuartos de Nagisa (cama roja), Genzo y Tetsuya, y el estudio con estanterias.
  y 13+   Tejado a dos aguas de roble oscuro con chimenea.
Todo con bloques vanilla; los cofres usan las mismas tablas de botin de antes.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import nbtio  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'data', 'dexrider', 'structure', 'mountain_house.nbt')
X, Y, Z = 21, 22, 18
DATA_VERSION = 4189

blocks = {}       # (x,y,z) -> (name, props, nbt)
entities = []


def S(x, y, z, name, nbt=None, **props):
    if 0 <= x < X and 0 <= y < Y and 0 <= z < Z:
        blocks[(x, y, z)] = ('minecraft:' + name if ':' not in name else name, props, nbt)


def F(x0, y0, z0, x1, y1, z1, name, **props):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                S(x, y, z, name, **props)


def air(x0, y0, z0, x1, y1, z1):
    F(x0, y0, z0, x1, y1, z1, 'air')


def stairs(x, y, z, facing, name='dark_oak_stairs', half='bottom'):
    S(x, y, z, name, facing=facing, half=half, shape='straight', waterlogged='false')


def door(x, y, z, facing, hinge='left', name='spruce_door'):
    S(x, y, z, name, facing=facing, half='lower', hinge=hinge, open='false', powered='false')
    S(x, y + 1, z, name, facing=facing, half='upper', hinge=hinge, open='false', powered='false')


def bed(x, y, z, facing, color):
    """facing = hacia donde mira la cabecera; la cabecera queda en (x,z)."""
    dx, dz = {'north': (0, 1), 'south': (0, -1), 'east': (-1, 0), 'west': (1, 0)}[facing]
    S(x, y, z, color + '_bed', facing=facing, occupied='false', part='head')
    S(x + dx, y, z + dz, color + '_bed', facing=facing, occupied='false', part='foot')


def chest(x, y, z, facing, loot):
    S(x, y, z, 'chest', nbt={'id': ('str', 'minecraft:chest'), 'LootTable': ('str', loot)}, facing=facing, type='single', waterlogged='false')


def lantern(x, y, z, hanging=True):
    S(x, y, z, 'lantern', hanging='true' if hanging else 'false', waterlogged='false')


def pane_window(x0, y0, z0, x1, y1, z1):
    F(x0, y0, z0, x1, y1, z1, 'glass_pane', east='false', west='false', north='false', south='false', waterlogged='false')


def table(x, y, z):
    S(x, y, z, 'spruce_fence', east='false', west='false', north='false', south='false', waterlogged='false')
    S(x, y + 1, z, 'spruce_pressure_plate', powered='false')


# ------------------------------------------------------------------ cuerpo de la casa
X0, X1, Z0, Z1 = 1, 19, 3, 16          # muros exteriores

# sotano (y 0..4): piedra
F(X0, 0, Z0, X1, 4, Z1, 'stone_bricks')
air(X0 + 1, 1, Z0 + 1, X1 - 1, 3, Z1 - 1)
F(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, 'polished_andesite')

# planta baja (y 4 suelo, 5..8 interior) y planta alta (9 suelo, 10..12 interior)
F(X0, 4, Z0, X1, 4, Z1, 'cobblestone')                    # zocalo
F(X0 + 1, 4, Z0 + 1, X1 - 1, 4, Z1 - 1, 'spruce_planks')  # suelo
for y in range(5, 13):
    for x in range(X0, X1 + 1):
        for z in (Z0, Z1):
            S(x, y, z, 'spruce_planks' if y in (5, 9) else 'white_terracotta')
    for z in range(Z0, Z1 + 1):
        for x in (X0, X1):
            S(x, y, z, 'spruce_planks' if y in (5, 9) else 'white_terracotta')
F(X0 + 1, 9, Z0 + 1, X1 - 1, 9, Z1 - 1, 'spruce_planks')  # suelo planta alta
air(X0 + 1, 5, Z0 + 1, X1 - 1, 8, Z1 - 1)                  # interiores limpios (la plantilla no toca lo que no se escribe)
air(X0 + 1, 10, Z0 + 1, X1 - 1, 12, Z1 - 1)
air(X0, 5, 0, X1, 8, Z0 - 1)
air(X0 - 1, 13, Z0 - 1, X1 + 1, 21, Z1 + 1)
for (x, z) in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1), (7, Z0), (13, Z0), (7, Z1), (13, Z1), (X0, 9), (X1, 9)):
    F(x, 5, z, x, 12, z, 'stripped_spruce_log', axis='y')
for x in range(X0, X1 + 1):                                 # vigas horizontales
    for z in (Z0, Z1):
        S(x, 9, z, 'stripped_spruce_log', axis='x')
for z in range(Z0, Z1 + 1):
    for x in (X0, X1):
        S(x, 9, z, 'stripped_spruce_log', axis='z')

# ventanas
for y0 in (6, 10):
    for x0 in (3, 9, 15):
        pane_window(x0, y0, Z0, x0 + 2, y0 + 1, Z0)
        pane_window(x0, y0, Z1, x0 + 2, y0 + 1, Z1)
    pane_window(X0, y0, 5, X0, y0 + 1, 7)
    pane_window(X1, y0, 5, X1, y0 + 1, 7)
    pane_window(X0, y0, 12, X0, y0 + 1, 14)

# engawa (porche) delante, con escalon y faroles
F(X0, 4, 0, X1, 4, Z0 - 1, 'spruce_slab', type='top', waterlogged='false')
for x in range(X0, X1 + 1):
    stairs(x, 3, 0, 'south', 'spruce_stairs')
for x in (X0, X1, 7, 13):
    F(x, 5, 0, x, 8, 0, 'spruce_fence', east='false', west='false', north='false', south='false', waterlogged='false')
F(X0, 9, 0, X1, 9, Z0 - 1, 'spruce_slab', type='bottom', waterlogged='false')   # alero del porche
lantern(4, 8, 0); lantern(16, 8, 0)
air(10, 5, Z0, 10, 6, Z0)
door(10, 5, Z0, 'south', 'left')

# ------------------------------------------------------------------ tejado a dos aguas (cumbrera a lo largo de x)
roof_y = 13
for i in range(0, 9):
    y = roof_y + i
    z_n, z_s = Z0 - 1 + i, Z1 + 1 - i
    if z_n > z_s:
        break
    for x in range(X0 - 1, X1 + 2):
        if z_n == z_s:
            S(x, y, z_n, 'dark_oak_slab', type='bottom', waterlogged='false')
        else:
            stairs(x, y, z_n, 'south')
            stairs(x, y, z_s, 'north')
    # hastiales
    for z in range(z_n + 1, z_s):
        S(X0, y, z, 'spruce_planks'); S(X1, y, z, 'spruce_planks')
        if y == roof_y:
            for x in range(X0 + 1, X1):
                S(x, y, z, 'spruce_planks')        # techo de la planta alta
# chimenea
F(3, 5, 9, 4, 21, 10, 'bricks')
air(3, 5, 9, 4, 6, 9)
S(3, 5, 9, 'campfire', facing='south', lit='true', signal_fire='false', waterlogged='false')
S(4, 5, 9, 'campfire', facing='south', lit='true', signal_fire='false', waterlogged='false')
S(3, 4, 9, 'stone_bricks'); S(4, 4, 9, 'stone_bricks')
# la chimenea pasa por la planta alta: hueco tapado (sin humo dentro)

# ------------------------------------------------------------------ planta baja
# tabique entre sala (oeste) y cocina/garaje (este) con paso
F(12, 5, Z0 + 1, 12, 8, Z1 - 1, 'spruce_planks')
air(12, 5, 5, 12, 6, 6)
# tabique cocina / garaje
F(13, 5, 10, X1 - 1, 8, 10, 'spruce_planks')
air(15, 5, 10, 15, 6, 10)
door(15, 5, 10, 'south', 'left')

# sala: mesa del te baja con cojines, rincon de recuerdos, estanteria
table(7, 5, 6); table(8, 5, 6)
for (x, z) in ((6, 6), (9, 6), (7, 5), (8, 5), (7, 7), (8, 7)):
    S(x, 5, z, 'red_carpet')
S(2, 5, 4, 'bookshelf'); S(2, 6, 4, 'bookshelf'); S(2, 5, 5, 'bookshelf')
S(11, 5, 4, 'spruce_slab', type='bottom', waterlogged='false')       # rincon de recuerdos
S(11, 6, 4, 'potted_lily_of_the_valley')
S(10, 5, 4, 'spruce_slab', type='bottom', waterlogged='false')
S(10, 6, 4, 'candle', candles='3', lit='false', waterlogged='false')
lantern(6, 8, 6); lantern(9, 8, 12)
S(2, 5, 14, 'potted_azalea_bush')
S(11, 5, 15, 'barrel', facing='up', open='false')

# escalera a la planta alta (x 10..11, sube hacia el sur desde z 9 a z 13)
for i in range(4):
    stairs(10, 5 + i, 9 + i, 'south', 'spruce_stairs')
    stairs(11, 5 + i, 9 + i, 'south', 'spruce_stairs')
    air(10, 6 + i, 9 + i, 11, 8, 9 + i)
air(10, 9, 10, 11, 9, 12)
# escotilla y escalera de mano al sotano
air(2, 4, 15, 2, 4, 15)
S(2, 4, 15, 'spruce_trapdoor', facing='north', half='top', open='false', powered='false', waterlogged='false')
for y in range(1, 4):
    S(2, y, 15, 'ladder', facing='north', waterlogged='false')
S(2, 0, 15, 'polished_andesite')

# cocina (noreste)
S(18, 5, 4, 'smoker', facing='west', lit='false')
S(18, 5, 5, 'furnace', facing='west', lit='false')
S(18, 5, 6, 'cauldron')
S(18, 5, 7, 'barrel', facing='west', open='false')
chest(18, 5, 8, 'west', 'dexrider:chests/mountain_house')
F(14, 5, 4, 16, 5, 4, 'spruce_slab', type='top', waterlogged='false')
S(15, 6, 4, 'potted_red_tulip')
table(15, 5, 7)
lantern(15, 8, 6)

# garaje de Tetsuya (sureste): suelo de piedra, porton al este, banco de la moto
F(13, 4, 11, X1 - 1, 4, Z1 - 1, 'smooth_stone')
air(X1, 5, 12, X1, 7, 14)
S(14, 5, 15, 'anvil', facing='east')
S(13, 5, 14, 'grindstone', face='floor', facing='east')
S(13, 5, 15, 'smithing_table')
F(16, 5, 15, 18, 5, 15, 'barrel', facing='north', open='false')
S(16, 4, 12, 'yellow_concrete'); S(16, 4, 13, 'yellow_concrete')   # marca de aparcar la moto
lantern(16, 8, 13)
S(17, 6, 15, 'chain', axis='y'); S(17, 7, 15, 'chain', axis='y')

# ------------------------------------------------------------------ planta alta (y 10..12)
# pasillo central este-oeste en z 9..10 (al lado de la escalera)
F(X0 + 1, 10, 8, X1 - 1, 12, 8, 'spruce_planks')
F(X0 + 1, 10, 11, X1 - 1, 12, 11, 'spruce_planks')
air(5, 10, 8, 5, 11, 8); door(5, 10, 8, 'south')         # cuarto de Nagisa (noroeste)
air(9, 10, 8, 9, 11, 8); door(9, 10, 8, 'south')         # cuarto de Genzo (centro norte)
air(16, 10, 8, 16, 11, 8); door(16, 10, 8, 'south')      # estudio (noreste)
air(6, 10, 11, 6, 11, 11); door(6, 10, 11, 'north')
air(12, 10, 11, 12, 11, 11)                                  # paso del rellano al pasillo      # cuarto de Tetsuya (suroeste)
F(7, 10, Z0 + 1, 7, 12, 7, 'spruce_planks')
F(13, 10, Z0 + 1, 13, 12, 7, 'spruce_planks')
F(9, 10, 12, 9, 12, Z1 - 1, 'spruce_planks')
# Nagisa
bed(2, 10, 4, 'north', 'red')
S(5, 10, 4, 'potted_poppy'); S(6, 10, 4, 'potted_cornflower')
S(2, 10, 7, 'barrel', facing='east', open='false')
S(4, 10, 7, 'white_carpet'); S(5, 10, 6, 'white_carpet')
lantern(4, 12, 5)
# Genzo
bed(8, 10, 4, 'north', 'brown')
S(12, 10, 4, 'lectern', facing='west', has_book='false', powered='false')
S(12, 10, 7, 'bookshelf'); S(12, 11, 7, 'bookshelf')
S(10, 10, 4, 'potted_spruce_sapling')
lantern(10, 12, 5)
# estudio
F(14, 10, 4, 18, 11, 4, 'bookshelf')
S(18, 10, 6, 'bookshelf'); S(18, 11, 6, 'bookshelf')
table(16, 10, 6)
S(15, 10, 6, 'spruce_stairs', facing='east', half='bottom', shape='straight', waterlogged='false')
lantern(16, 12, 6)
# Tetsuya
bed(2, 10, 15, 'south', 'blue')
S(6, 10, 15, 'barrel', facing='north', open='false')
S(7, 10, 15, 'chest', nbt={'id': ('str', 'minecraft:chest')}, facing='north', type='single', waterlogged='false')
S(5, 10, 13, 'gray_carpet'); S(5, 10, 14, 'gray_carpet')
lantern(5, 12, 14)
# altillo: dormitorio de invitados (este del pasillo sur)
bed(18, 10, 15, 'south', 'white'); bed(16, 10, 15, 'south', 'white')
lantern(14, 12, 14)

# ------------------------------------------------------------------ sotano: el taller de Takumi
S(X0 + 1, 1, Z0 + 1, 'blast_furnace', facing='south', lit='false')
S(X0 + 2, 1, Z0 + 1, 'smithing_table')
S(X0 + 3, 1, Z0 + 1, 'anvil', facing='east')
S(X0 + 4, 1, Z0 + 1, 'crafting_table')
S(X0 + 5, 1, Z0 + 1, 'grindstone', face='floor', facing='south')
chest(8, 1, Z0 + 1, 'south', 'dexrider:chests/mountain_house_workshop')       # equipo Blaster
F(9, 1, Z0 + 1, 11, 2, Z0 + 1, 'iron_bars', east='true', west='true', north='false', south='false', waterlogged='false')
S(10, 3, Z0 + 1, 'redstone_lamp', lit='false')
S(12, 1, Z0 + 1, 'lightning_rod', facing='up', powered='false', waterlogged='false')
S(13, 1, Z0 + 1, 'copper_bulb', lit='true', powered='false')
# mesa de trabajo larga
F(4, 1, 7, 8, 1, 7, 'polished_andesite_slab', type='top', waterlogged='false')
S(5, 2, 7, 'redstone_torch', lit='true'); S(7, 2, 7, 'potted_cactus')
# zona de entrenamiento (sur del sotano)
F(11, 0, 10, 18, 0, 15, 'smooth_stone')
F(11, 1, 10, 18, 1, 10, 'polished_andesite_wall', east='low', west='low', north='none', south='none', up='true', waterlogged='false')
air(14, 1, 10, 15, 1, 10)
S(17, 1, 14, 'target', power='0'); S(17, 2, 14, 'carved_pumpkin', facing='north')
S(12, 1, 14, 'hay_block', axis='y'); S(12, 2, 14, 'carved_pumpkin', facing='north')
for (x, z) in ((5, 5), (12, 6), (6, 12), (15, 13)):
    lantern(x, 3, z)
entities.append(((6.5, 1.0, 10.5), (6, 1, 10), 'dexrider:kaijin_soldier'))
entities.append(((15.5, 1.0, 12.5), (15, 1, 12), 'dexrider:kaijin_soldier'))


# ------------------------------------------------------------------ escribir
def tagify(v):
    if isinstance(v, tuple):
        return v
    raise ValueError(v)


def main():
    palette, index, blist = [], {}, []
    for (x, y, z), (name, props, nbt) in sorted(blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            entry = {'Name': ('str', name)}
            if props:
                entry['Properties'] = ('c', {k: ('str', str(v)) for k, v in sorted(props.items())})
            palette.append(('c', entry))
        b = {'pos': ('list', (3, [('i', x), ('i', y), ('i', z)])), 'state': ('i', index[key])}
        if nbt:
            b['nbt'] = ('c', {k: tagify(v) for k, v in nbt.items()})
        blist.append(('c', b))
    ents = []
    for pos, bpos, eid in entities:
        ents.append(('c', {'pos': ('list', (6, [('d', p) for p in pos])), 'blockPos': ('list', (3, [('i', p) for p in bpos])),
                           'nbt': ('c', {'id': ('str', eid), 'PersistenceRequired': ('b', 1)})}))
    root = ('c', {
        'size': ('list', (3, [('i', X), ('i', Y), ('i', Z)])),
        'entities': ('list', (10, ents)),
        'blocks': ('list', (10, blist)),
        'palette': ('list', (10, palette)),
        'DataVersion': ('i', DATA_VERSION),
    })
    nbtio.save(OUT, root)
    print('ok', len(blist), 'bloques,', len(palette), 'estados')


if __name__ == '__main__':
    main()
