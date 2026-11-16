import gzip,struct,io
TAGS={}
def rd_payload(f,t):
    if t==1: return ('b',struct.unpack('>b',f.read(1))[0])
    if t==2: return ('s',struct.unpack('>h',f.read(2))[0])
    if t==3: return ('i',struct.unpack('>i',f.read(4))[0])
    if t==4: return ('l',struct.unpack('>q',f.read(8))[0])
    if t==5: return ('f',struct.unpack('>f',f.read(4))[0])
    if t==6: return ('d',struct.unpack('>d',f.read(8))[0])
    if t==7: n=struct.unpack('>i',f.read(4))[0]; return ('ba',f.read(n))
    if t==8: n=struct.unpack('>H',f.read(2))[0]; return ('str',f.read(n).decode())
    if t==9:
        et=f.read(1)[0]; n=struct.unpack('>i',f.read(4))[0]; return ('list',(et,[rd_payload(f,et) for _ in range(n)]))
    if t==10:
        d={}
        while True:
            tt=f.read(1)[0]
            if tt==0: return ('c',d)
            n=struct.unpack('>H',f.read(2))[0]; name=f.read(n).decode(); d[name]=rd_payload(f,tt)
    if t==11: n=struct.unpack('>i',f.read(4))[0]; return ('ia',list(struct.unpack('>%di'%n,f.read(4*n))))
    if t==12: n=struct.unpack('>i',f.read(4))[0]; return ('la',list(struct.unpack('>%dq'%n,f.read(8*n))))
TID={'b':1,'s':2,'i':3,'l':4,'f':5,'d':6,'ba':7,'str':8,'list':9,'c':10,'ia':11,'la':12}
def load(path):
    f=io.BytesIO(gzip.open(path).read()); t=f.read(1)[0]; n=struct.unpack('>H',f.read(2))[0]; f.read(n)
    return rd_payload(f,t)
def wr_payload(o,tag):
    k,v=tag
    if k=='b': o.write(struct.pack('>b',v))
    elif k=='s': o.write(struct.pack('>h',v))
    elif k=='i': o.write(struct.pack('>i',v))
    elif k=='l': o.write(struct.pack('>q',v))
    elif k=='f': o.write(struct.pack('>f',v))
    elif k=='d': o.write(struct.pack('>d',v))
    elif k=='ba': o.write(struct.pack('>i',len(v))); o.write(v)
    elif k=='str': b=v.encode(); o.write(struct.pack('>H',len(b))); o.write(b)
    elif k=='list':
        et,items=v
        if not items: et=0 if et is None else et
        o.write(bytes([et])); o.write(struct.pack('>i',len(items)))
        for it in items: wr_payload(o,it)
    elif k=='c':
        for name,t in v.items():
            o.write(bytes([TID[t[0]]])); b=name.encode(); o.write(struct.pack('>H',len(b))); o.write(b); wr_payload(o,t)
        o.write(b'\x00')
    elif k=='ia': o.write(struct.pack('>i',len(v))); o.write(struct.pack('>%di'%len(v),*v))
    elif k=='la': o.write(struct.pack('>i',len(v))); o.write(struct.pack('>%dq'%len(v),*v))
def save(path,tag):
    o=io.BytesIO(); o.write(bytes([10])); o.write(struct.pack('>H',0)); wr_payload(o,tag)
    gzip.open(path,'wb').write(o.getvalue())
def plain(t):
    k,v=t
    if k=='c': return {a:plain(b) for a,b in v.items()}
    if k=='list': return [plain(x) for x in v[1]]
    return v
