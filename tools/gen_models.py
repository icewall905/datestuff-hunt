#!/usr/bin/env python3
"""Generates app/src/main/assets/models/{chest,feather}.glb (minimal glTF 2.0 binary, no deps)."""
import json, math, os, struct

def box(cx, cy, cz, sx, sy, sz):
    hx, hy, hz = sx / 2, sy / 2, sz / 2
    faces = [((1,0,0),[(1,-1,-1),(1,1,-1),(1,1,1),(1,-1,1)]), ((-1,0,0),[(-1,-1,1),(-1,1,1),(-1,1,-1),(-1,-1,-1)]),
             ((0,1,0),[(-1,1,-1),(-1,1,1),(1,1,1),(1,1,-1)]), ((0,-1,0),[(-1,-1,1),(-1,-1,-1),(1,-1,-1),(1,-1,1)]),
             ((0,0,1),[(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]), ((0,0,-1),[(1,-1,-1),(-1,-1,-1),(-1,1,-1),(1,1,-1)])]
    pos, nor, idx = [], [], []
    for n, vs in faces:
        b = len(pos)
        for v in vs:
            pos.append((cx + v[0]*hx, cy + v[1]*hy, cz + v[2]*hz)); nor.append(n)
        idx += [b, b+1, b+2, b, b+2, b+3]
    return pos, nor, idx

def half_cyl(cx, cy, cz, r, length, seg=14):
    """Barrel lid: half cylinder along X, flat side down."""
    pos, nor, idx = [], [], []
    for i in range(seg):
        a0, a1 = math.pi * i / seg, math.pi * (i + 1) / seg
        for (x0, x1) in [(-length/2, length/2)]:
            p = [(x0, math.sin(a0)*r, math.cos(a0)*r), (x1, math.sin(a0)*r, math.cos(a0)*r),
                 (x1, math.sin(a1)*r, math.cos(a1)*r), (x0, math.sin(a1)*r, math.cos(a1)*r)]
            n0 = (0, math.sin(a0), math.cos(a0)); n1 = (0, math.sin(a1), math.cos(a1))
            b = len(pos)
            for v, n in zip(p, [n0, n0, n1, n1]):
                pos.append((cx + v[0], cy + v[1], cz + v[2])); nor.append(n)
            idx += [b, b+2, b+1, b, b+3, b+2]
    for sx_ in (-1, 1):  # end caps
        b = len(pos)
        pos.append((cx + sx_*length/2, cy, cz)); nor.append((sx_, 0, 0))
        for i in range(seg + 1):
            a = math.pi * i / seg
            pos.append((cx + sx_*length/2, cy + math.sin(a)*r, cz + math.cos(a)*r)); nor.append((sx_, 0, 0))
        for i in range(seg):
            if sx_ > 0: idx += [b, b+1+i, b+2+i]
            else: idx += [b, b+2+i, b+1+i]
    idx = [k for t in range(0, len(idx), 3) for k in (idx[t], idx[t+2], idx[t+1])]
    return pos, nor, idx

def octa(cx, cy, cz, sx, sy, sz):
    P = [(1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)]
    tris = [(0,2,4),(2,1,4),(1,3,4),(3,0,4),(2,0,5),(1,2,5),(3,1,5),(0,3,5)]
    pos, nor, idx = [], [], []
    for t in tris:
        v = [(cx+P[i][0]*sx, cy+P[i][1]*sy, cz+P[i][2]*sz) for i in t]
        a = [v[1][k]-v[0][k] for k in range(3)]; b = [v[2][k]-v[0][k] for k in range(3)]
        n = (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
        l = math.sqrt(sum(c*c for c in n)) or 1; n = tuple(c/l for c in n)
        base = len(pos); pos += v; nor += [n]*3; idx += [base, base+1, base+2]
    return pos, nor, idx

def merge(*parts):
    pos, nor, idx = [], [], []
    for p, n, i in parts:
        b = len(pos); pos += p; nor += n; idx += [k + b for k in i]
    return pos, nor, idx

class Glb:
    def __init__(self): self.bin = bytearray(); self.views = []; self.acc = []; self.meshes = []; self.mats = []; self.nodes = []; self.anims = []
    def _view(self, data, target=None):
        while len(self.bin) % 4: self.bin.append(0)
        off = len(self.bin); self.bin += data
        v = {"buffer": 0, "byteOffset": off, "byteLength": len(data)}
        if target: v["target"] = target
        self.views.append(v); return len(self.views) - 1
    def accessor(self, data, comp, typ, count, target=None, minmax=None):
        a = {"bufferView": self._view(data, target), "componentType": comp, "count": count, "type": typ}
        if minmax: a["min"], a["max"] = minmax
        self.acc.append(a); return len(self.acc) - 1
    def mesh(self, geo, color, emissive=None, metallic=0.0, rough=0.7):
        pos, nor, idx = geo
        m = {"pbrMetallicRoughness": {"baseColorFactor": list(color) + [1.0], "metallicFactor": metallic, "roughnessFactor": rough}}
        if emissive: m["emissiveFactor"] = list(emissive)
        self.mats.append(m)
        pa = self.accessor(b"".join(struct.pack("<3f", *p) for p in pos), 5126, "VEC3", len(pos), 34962,
                           ([min(p[k] for p in pos) for k in range(3)], [max(p[k] for p in pos) for k in range(3)]))
        na = self.accessor(b"".join(struct.pack("<3f", *n) for n in nor), 5126, "VEC3", len(nor), 34962)
        ia = self.accessor(b"".join(struct.pack("<H", i) for i in idx), 5123, "SCALAR", len(idx), 34963)
        self.meshes.append({"primitives": [{"attributes": {"POSITION": pa, "NORMAL": na}, "indices": ia, "material": len(self.mats) - 1}]})
        return len(self.meshes) - 1
    def write(self, path, root_children):
        while len(self.bin) % 4: self.bin.append(0)
        j = {"asset": {"version": "2.0", "generator": "gen_models.py"}, "scene": 0, "scenes": [{"nodes": root_children}],
             "nodes": self.nodes, "meshes": self.meshes, "materials": self.mats, "accessors": self.acc, "bufferViews": self.views,
             "buffers": [{"byteLength": len(self.bin)}]}
        if self.anims: j["animations"] = self.anims
        js = json.dumps(j, separators=(",", ":")).encode()
        while len(js) % 4: js += b" "
        total = 12 + 8 + len(js) + 8 + len(self.bin)
        with open(path, "wb") as f:
            f.write(struct.pack("<4sII", b"glTF", 2, total))
            f.write(struct.pack("<I4s", len(js), b"JSON") + js)
            f.write(struct.pack("<I4s", len(self.bin), b"BIN\0") + bytes(self.bin))

def chest(path):
    g = Glb()
    wood, dark, gold = (0.42, 0.24, 0.10), (0.30, 0.16, 0.07), (1.0, 0.75, 0.25)
    body = g.mesh(box(0, 0.19, 0, 0.8, 0.38, 0.5), wood, rough=0.85)
    bands = g.mesh(merge(box(-0.28, 0.19, 0, 0.07, 0.40, 0.52), box(0.28, 0.19, 0, 0.07, 0.40, 0.52), box(0, 0.37, 0, 0.82, 0.04, 0.52)), gold, metallic=0.9, rough=0.35)
    lid = g.mesh(half_cyl(0, 0, 0, 0.25, 0.8), dark, rough=0.85)
    lidb = g.mesh(merge(half_cyl(-0.28, 0, 0, 0.262, 0.07), half_cyl(0.28, 0, 0, 0.262, 0.07)), gold, metallic=0.9, rough=0.35)
    lock = g.mesh(box(0, 0, 0.27, 0.1, 0.12, 0.04), gold, emissive=(0.35, 0.22, 0.02), metallic=0.9, rough=0.3)
    glow = g.mesh(box(0, 0.385, 0, 0.7, 0.01, 0.42), (0.2, 1.0, 0.5), emissive=(0.2, 1.0, 0.5))
    # nodes: 0 root, 1 body, 2 bands, 3 lock, 4 lid pivot (hinge at back top edge), 5 lid, 6 lidbands, 7 glow
    g.nodes = [
        {"name": "chest", "children": [1, 2, 3, 4, 7]},
        {"name": "body", "mesh": body}, {"name": "bands", "mesh": bands},
        {"name": "lock", "mesh": lock, "translation": [0, 0.28, 0]},
        {"name": "lidPivot", "translation": [0, 0.38, -0.25], "children": [5, 6]},
        {"name": "lid", "mesh": lid, "translation": [0, 0, 0.25]},
        {"name": "lidBands", "mesh": lidb, "translation": [0, 0, 0.25]},
        {"name": "glow", "mesh": glow},
    ]
    # open animation: pivot rotates about X by -110deg over 1s (quaternion)
    times = struct.pack("<2f", 0.0, 1.0)
    def q(deg): a = math.radians(deg) / 2; return (math.sin(a), 0.0, 0.0, math.cos(a))
    vals = b"".join(struct.pack("<4f", *q(d)) for d in (0, -110))
    ta = g.accessor(times, 5126, "SCALAR", 2, None, ([0.0], [1.0])); va = g.accessor(vals, 5126, "VEC4", 2)
    g.anims = [{"name": "open", "samplers": [{"input": ta, "output": va, "interpolation": "LINEAR"}], "channels": [{"sampler": 0, "target": {"node": 4, "path": "rotation"}}]}]
    g.write(path, [0])

def feather(path):
    g = Glb()
    vane = g.mesh(octa(0, 0.18, 0, 0.07, 0.18, 0.012), (0.22, 1.0, 0.48), emissive=(0.12, 0.6, 0.28), rough=0.4)
    vane2 = g.mesh(octa(0.0, 0.14, 0.0, 0.045, 0.14, 0.02), (1.0, 0.75, 0.29), emissive=(0.5, 0.33, 0.05), rough=0.4)
    spine = g.mesh(box(0, 0.1, 0, 0.008, 0.26, 0.008), (0.95, 0.95, 0.9), rough=0.5)
    g.nodes = [{"name": "feather", "children": [1, 2, 3], "rotation": [0, 0, 0.0, 1]},
               {"mesh": vane}, {"mesh": vane2, "translation": [0, 0.0, 0.004]}, {"mesh": spine}]
    g.write(path, [0])

if __name__ == "__main__":
    out = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "models")
    os.makedirs(out, exist_ok=True)
    chest(os.path.join(out, "chest.glb")); feather(os.path.join(out, "feather.glb"))
    print("ok")
