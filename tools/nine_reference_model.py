"""Export Nine's Kuuga-inspired proposal to all eight existing forms.
Run with --preview for actual geometry views. Original resources are immutable.
"""
import copy, io, json, math, sys, zipfile
from pathlib import Path
from PIL import Image
from geo_preview import sheet

ROOT=Path(__file__).resolve().parents[1]
AS=ROOT/'src/main/resources/assets/dexrider'
GEO=AS/'geckolib/models/item/armor'
TEX=AS/'textures/item/armor'
OUT=ROOT/'diseno/nine'
BACKUP=OUT/'recursos-originales.zip'
FACES=('north','south','east','west','up','down')
MATS=('main','dark','gold','silver','stone','eye','gem','light','black')

def cube(o,s,m,rot=None,pivot=None):
    i=MATS.index(m)
    c={'origin':o,'size':s,'uv':{f:{'uv':[i*40,640],'uv_size':[32,32]} for f in FACES}}
    if rot: c.update(rotation=rot,pivot=pivot)
    return c

def bar(a,b,w,d,m):
    mid=[(x+y)/2 for x,y in zip(a,b)]
    length=math.hypot(b[0]-a[0],b[1]-a[1])
    return cube([mid[0]-w/2,mid[1]-length/2,mid[2]-d/2],[w,length+.025,d],m,
                [0,0,math.degrees(math.atan2(b[0]-a[0],b[1]-a[1]))],mid)

def ring(cx,cy,z,r,w,d,m):
    return [bar([cx+r*math.cos(a),cy+r*math.sin(a),z],
                [cx+r*math.cos(a+math.pi/4),cy+r*math.sin(a+math.pi/4),z],w,d,m)
            for a in [i*math.pi/4 for i in range(8)]]

def disk(cx,cy,z,rx,ry,d,m):
    parts=[]
    for j in range(12):
        y=(j+.5)/6-1
        w=2*rx*math.sqrt(1-y*y)
        c=cube([cx-w/2,cy-ry+j*ry/6,z],[w,ry/6+.008,d],m)
        for face in ('north','south'):
            c['uv'][face]={'uv':[MATS.index(m)*40+2,642+(11-j)*28/12], 'uv_size':[28,28/12]}
        parts.append(c)
    return parts

def geometry(form,rising):
    p={n:[] for n in ('armorHead','armorBody','armorRightArm','armorLeftArm','armorRightLeg','armorLeftLeg','armorRightBoot','armorLeftBoot')}
    h=p['armorHead']; b=p['armorBody']
    h.append(cube([-4.15,24,-4.15],[8.3,8.25,8.3],'main'))
    h.append(cube([-3.7,24,-4.38],[7.4,1.6,.35],'black'))
    for side in (-1,1):
        h+=disk(side*2.05,28.3,-4.62,1.87,2.03,.28,'black')
        h+=disk(side*2.05,28.3,-4.85,1.62,1.80,.26,'eye')
        points=[(0,30.55,-4.9),(side*2.5,31.35,-4.9),(side*4.7,32.8,-4.7),(side*4.9,34.7,-4.4),(side*4.2,36.2,-4.2)]
        for i,w in enumerate((1.2,1.35,1.05,.5)):
            h.append(bar(points[i],points[i+1],w,.8,'gold'))
        h.append(bar([0,24.5,-4.8],[side*2.1,25.65,-4.8],1.25,.5,'silver'))
        for j in range(2): h.append(cube([side*(1.0+j*.55)-.13,25.2,-5.1],[.26,.55,.1],'black'))
    h.append(bar([0,30.5,-4.9],[0,33.3,-4.7],.42,.5,'gold'))
    h.append(cube([-3.6,25,4.15],[7.2,5.9,.35],'main'))
    b.append(cube([-4.1,12,-2.1],[8.2,12,4.2],'black'))
    b.append(cube([-3.3,23.15,-2.55],[6.6,.65,5.1],'gold'))
    for side in (-1,1):
        x=-3.9 if side<0 else .15
        b.append(cube([x,19.4,-3.0],[3.75,3.45,1.0],'dark'))
        b+=disk(x+1.875,21.1,-3.35,1.7,1.55,.5,'main')
        b.append(bar([side*.2,22.85,-3.4],[side*3.9,23.25,-3.1],.48,.45,'gold'))
        b.append(cube([x,19,2.1],[3.75,4.1,.65],'main'))
    b.append(cube([-.45,19.25,-3.45],[.9,1.25,.4],'gold'))
    for y in (14.5,16,17.5): b.append(cube([-2.8,y,-2.45],[5.6,1.15,.45],'dark'))
    b.append(cube([-4.35,12,-2.45],[8.7,1.6,4.9],'stone'))
    b.append(cube([-3.85,11.8,-3.15],[7.7,2,.7],'stone'))
    b+=disk(0,12.8,-3.9,1.12,1.12,.35,'gem')
    b+=ring(0,12.8,-3.82,1.42,.35,.38,'gold')
    for side in (-1,1):
        b+=disk(side*2.7,12.8,-3.55,.35,.35,.2,'eye')
        for y in (12,13.5): b.append(cube([side*2.8-.6,y,-3.3],[1.2,.18,.2],'gold'))
    for side,word in ((-1,'Right'),(1,'Left')):
        arm=p['armor'+word+'Arm']; leg=p['armor'+word+'Leg']; boot=p['armor'+word+'Boot']
        x=-8.3 if side<0 else 4.0
        arm.append(cube([x,12,-2.15],[4.3,12,4.3],'black'))
        arm.append(cube([x-.2,20.5,-2.55],[4.7,2.8,5.1],'main'))
        arm.append(cube([x+.25,23.3,-2.05],[3.8,1.0,4.1],'main'))
        arm.append(cube([x-.25,20.15,-2.6],[4.8,.5,5.2],'gold'))
        arm.append(cube([x-.05,13,-2.5],[4.4,3.5,5],'main'))
        for y in (12.65,16.5): arm.append(cube([x-.15,y,-2.55],[4.6,.5,5.1],'gold'))
        arm.append(cube([x+.8,15,-2.65],[2.5,.22,.2],'light'))
        lx=-4.1 if side<0 else .1
        leg.append(cube([lx,4,-2.1],[4,8,4.2],'black'))
        leg.append(cube([lx+.3,5.7,-2.35],[3.4,5.4,.35],'dark'))
        boot.append(cube([lx,0,-2.4],[4,5.5,4.8],'main'))
        for y in (1,5): boot.append(cube([lx-.1,y,-2.5],[4.2,.5,5],'gold'))
        boot.append(cube([lx,0,-2.9],[4,.75,5.3],'silver'))
        if rising:
            arm.append(cube([x+.35,13.4,-2.72],[.45,2.65,.2],'light'))
            boot.append(cube([lx+.35,1.5,-2.6],[.5,3.4,.2],'gold'))
            boot.append(cube([lx+3.1,1.5,-2.6],[.5,3.4,.2],'gold'))
        if form=='titan': arm.append(cube([x-.4,21,-2.9],[5.1,2.5,.5],'silver'))
    if form=='pegasus': h.append(cube([-4.55,27,-1],[.45,3,2],'silver'))
    if form=='dragon': h.append(cube([-.45,29,4.5],[.9,4,.35],'gold'))
    if rising:
        for side in (-1,1): b.append(bar([side*3.6,19.5,-3.6],[side*3.6,22.5,-3.6],.4,.2,'gold'))
    return p

def build():
    OUT.mkdir(exist_ok=True)
    if not BACKUP.exists():
        with zipfile.ZipFile(BACKUP,'x',zipfile.ZIP_DEFLATED) as z:
            for p in [*GEO.glob('rider_nine*.json'),*TEX.glob('rider_nine*.png')]: z.write(p,p.relative_to(ROOT))
    with zipfile.ZipFile(BACKUP) as z:
        for path in sorted(GEO.glob('rider_nine*.json')):
            model=path.name.removesuffix('.geo.json'); form=next((f for f in ('dragon','pegasus','titan') if f in model),'base'); rising='rising' in model
            g=json.loads(z.read(path.relative_to(ROOT).as_posix())); geo=g['minecraft:geometry'][0]
            new=geometry(form,rising)
            for b in geo['bones']:
                if b['name'] in new: b['cubes']=new[b['name']]
                elif b['name'].startswith(('rx_','biped')): b['cubes']=[]
            geo['description']['texture_height']=1024
            path.write_text(json.dumps(g,indent=1)+'\n')
            color={'base':(26,150,61),'dragon':(44,104,218),'pegasus':(184,201,215),'titan':(132,51,193)}[form]
            palette=[color,(27,30,35),(230,180,44),(191,202,213),(192,182,157),(224,19,37),color,(162,255,107),(14,16,19)]
            for glow in (False,True):
                p=TEX/(model+('_glowmask' if glow else '')+'.png')
                old=Image.open(io.BytesIO(z.read(p.relative_to(ROOT).as_posix()))).convert('RGBA')
                im=Image.new('RGBA',(512,1024)); im.paste(old,(0,0))
                for i,col in enumerate(palette):
                    if glow and MATS[i] not in ('eye','gem','light'): continue
                    for y in range(32):
                        for x in range(32):
                            v=.82+.18*(1-y/31)
                            if MATS[i] in ('eye','gem'): v=.74+.26*((x//4+y//4)%2)
                            elif min(x,y,31-x,31-y)==0: v*=.7
                            im.putpixel((i*40+x,640+y),tuple(int(c*v) for c in col)+(255,))
                im.save(p)
            baseline=json.loads(z.read(path.relative_to(ROOT).as_posix()))['minecraft:geometry'][0]
            assert [(b['name'],b.get('parent'),b.get('pivot')) for b in geo['bones']]==[(b['name'],b.get('parent'),b.get('pivot')) for b in baseline['bones']]
            assert [b for b in geo['bones'] if b['name']=='weapon']==[b for b in baseline['bones'] if b['name']=='weapon']
            assert len({b['name'] for b in geo['bones']})==len(geo['bones'])
            for b in geo['bones']:
                for c in b.get('cubes',[]):
                    assert all(v>0 for v in c['size'])
                    if isinstance(c.get('uv'),dict):
                        for face in c['uv'].values():
                            assert all(0<=v<=limit and 0<=v+size<=limit for v,size,limit in zip(face['uv'],face['uv_size'],(512,1024)))
            print(model, sum(len(b.get('cubes',[])) for b in geo['bones']), 'cuboids')
    if '--preview' in sys.argv:
        sheet(['rider_nine'],['front','q','side','back'],16).save(OUT/'modelo-implementado.png')
        sheet(['rider_nine','rider_nine_dragon','rider_nine_pegasus','rider_nine_titan'],['q'],10).save(OUT/'formas-implementadas.png')

if __name__=='__main__': build()
