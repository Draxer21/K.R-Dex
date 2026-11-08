#!/usr/bin/env python3
"""
Busto al estilo MCA para los modelos de mujeres ADULTAS (Nagisa civil y Psyga): la placa plana del pecho pasa a ser
un volumen con fondo, inclinado hacia abajo desde su borde superior. Solo personajes adultos.

  python3 tools/bust.py         aplica los valores de BUSTS (idempotente: busca la pieza por su region de textura)
"""
import json, os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "dexrider", "geckolib", "models", "item", "armor")
ANGLE = 20.0   # inclinacion hacia abajo, en grados

# modelo -> (hueso, uv de la cara norte que identifica la pieza, origin, size)
BUSTS = {
    "civil_nagisa": ("bipedBody", [424, 49], [-3.5, 18.1, -3.8], [7.0, 3.6, 1.8]),
    "rider_psyga": ("armorBody", [0, 262], [-3.9, 18.0, -4.1], [7.8, 4.4, 1.85]),
}

for name, (bone, uv, origin, size) in BUSTS.items():
    path = os.path.join(ROOT, name + ".geo.json")
    d = json.load(open(path))
    done = False
    for b in d["minecraft:geometry"][0]["bones"]:
        if b["name"] != bone:
            continue
        for c in b.get("cubes", []):
            if c.get("uv", {}).get("north", {}).get("uv") == uv:
                c["origin"] = origin
                c["size"] = size
                # gira desde el borde superior trasero: la parte de abajo sobresale mas
                c["pivot"] = [0.0, round(origin[1] + size[1], 3), round(origin[2] + size[2], 3)]
                c["rotation"] = [ANGLE, 0.0, 0.0]
                done = True
    assert done, name
    json.dump(d, open(path, "w"), indent=1)
    print("busto:", name)
