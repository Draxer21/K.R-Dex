"""Build the RabbitTank suit using the original Forge rig and weapon textures."""
import json
from pathlib import Path
from PIL import Image
from geo_preview import sheet

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/dexrider"
MODELS = ASSETS / "geckolib/models/item/armor"
TEXTURES = ASSETS / "textures/item/armor"
MATERIALS = ["rubber", "steel", "rabbit", "tank", "amber", "red_lens", "blue_lens"]

def suit(tool, matter):
    bones = {}
    def plate(bone, material, x, y, z, width, height, depth, tilt=0):
        tile = MATERIALS.index(material) * 40
        part = dict(origin=[x,y,z], size=[width,height,depth], uv={
            face: dict(uv=[tile,1152], uv_size=[32,32])
            for face in ("north","south","east","west","up","down")})
        if tilt:
            part.update(rotation=[0,0,tilt], pivot=[x+width/2,y+height/2,z+depth/2])
        bones.setdefault("armor"+bone, []).append(part)

    plate("Head","rubber",-4.15,24,-4.15,8.3,8.1,8.3)
    plate("Head","steel",-2.65,24.45,-4.38,5.3,1.7,.4)
    for sign, material, lens in [(-1,"rabbit","red_lens"),(1,"tank","blue_lens")]:
        plate("Head",lens,sign*2-1.65,26.6,-4.62,3.3,3.6,.55,sign*22)
        plate("Head",material,sign*2.9-.55,29.3,-4.35,1.1,5.6,.95,sign*26)
    plate("Head","rabbit",-4.7,32.4,-4.1,.85,2.8,.8,-22)
    plate("Head","steel",3.7,33.7,-4.48,1.55,.68,1.25,26)
    plate("Head","steel",-.23,28.8,-4.36,.46,3.35,.38)
    plate("Body","rubber",-4,12,-2.15,8,12,4.3)
    for row in range(6):
        split = 1.2+row*.98
        for z in (-2.65,2.1):
            plate("Body","rabbit",-4,18+row,z,split,1.01,.56)
            plate("Body","tank",-4+split,18+row,z,8-split,1.01,.56)
    for y in (15.4,16.6):
        plate("Body","rubber",-2.9,y,-2.35,5.8,.83,.45)
    plate("Body","steel",-4.12,13,-2.26,8.24,1.55,4.52)
    plate("Body","rubber",-3.3,12.65,-3.12,6.6,2.45,.95)
    for angle in (0,45,90,135):
        plate("Body","amber",-2.06,12.85,-3.5,.8,2.12,.45,angle)
    plate("Body","steel",-2.05,13.49,-3.64,.78,.78,.3)
    for x, color in ((.15,"red_lens"),(1.43,"blue_lens")):
        plate("Body",color,x,13.28,-3.55,1,1.95,.76)
        plate("Body","steel",x+.12,15.24,-3.49,.76,.47,.65)
    plate("Body","steel",-3.65,13.35,-3.55,1.75,.35,.45,-18)
    plate("Body","rabbit",-3.88,13.45,-3.95,.55,.65,.85)
    for side, x, legx, armcol, legcol in [
        ("Right",-8,-4,"rabbit","tank"),("Left",4,0,"tank","rabbit")]:
        arm,leg,boot = side+"Arm",side+"Leg",side+"Boot"
        plate(arm,"rubber",x,12,-2,4,12,4)
        plate(arm,armcol,x-.13,20.5,-2.3,4.26,3.45,4.6)
        plate(arm,armcol,x+.15,13.2,-2.17,3.7,4.2,4.34)
        plate(leg,"rubber",legx,2,-2,4,10,4)
        plate(leg,legcol,legx+.25,7,-2.27,3.5,4.25,4.54)
        plate(boot,legcol,legx+.05,.15,-2.42,3.9,6.2,4.75)
        for y in (1,2.3,3.6,4.9):
            plate(boot,"steel" if legcol=="rabbit" else "rubber",legx+.4,y,-2.57,3.2,.38,.38,12 if legcol=="rabbit" else 0)
    if tool=="cannon":
        plate("RightArm","steel",-8.28,12,-2.74,1.35,6,1.5)
    if tool=="shield":
        plate("RightArm","tank",-8.72,13,-2.9,1.2,7,5.8)
    if matter!="carbon":
        for x in (4.5,6.75):
            plate("LeftArm","blue_lens",x,14,-2.48,.65,3.2,.5,12 if matter=="fiber" else 0)
    return bones

for path in sorted(MODELS.glob("rider_forge*.geo.json")):
    name = path.name.removesuffix(".geo.json")
    tool, matter = name.split("_")[2:] or ["gear","carbon"]
    colors = [(25,28,37),(172,187,197),
              {"gear":(200,28,48),"cannon":(219,112,30),"shield":(78,117,170)}[tool],
              {"carbon":(26,90,200),"magnesium":(230,215,159),"fiber":(68,166,126)}[matter],
              (246,149,28),(255,60,73),(60,166,255)]
    doc = json.loads(path.read_text())
    geo = doc["minecraft:geometry"][0]
    geo["description"]["texture_height"] = 2048
    parts = suit(tool,matter)
    assert parts.keys() <= {bone["name"] for bone in geo["bones"]}
    for bone in geo["bones"]:
        if bone["name"] in parts:
            bone["cubes"] = parts[bone["name"]]
        elif bone["name"].startswith(("biped","rx_")):
            bone["cubes"] = []
    path.write_text(json.dumps(doc,separators=(",",":"))+"\n")
    for glow in (False,True):
        target = TEXTURES/(name+("_glowmask" if glow else "")+".png")
        with Image.open(target) as old:
            atlas = Image.new("RGBA",(512,2048))
            atlas.paste(old.crop((0,0,512,1024)),(0,0))
        for i,color in enumerate(colors):
            if glow and i<4:
                continue
            for y in range(32):
                for x in range(32):
                    shade = .65+.35*(1-abs(x-11)/32)*(1-y/64)
                    if i>=5 and (x//3+y//3)%2:
                        shade *= .79
                    atlas.putpixel((i*40+x,1152+y),tuple(int(v*shade) for v in color)+(255,))
        atlas.save(target)
sheet(["rider_forge"],["front","q","side","back"],14).save(ROOT/"diseno/forge/modelo-implementado.png")
