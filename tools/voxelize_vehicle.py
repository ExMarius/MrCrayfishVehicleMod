"""Voxelize a floor-aligned OBJ mesh into a vanilla Minecraft JSON model + palette PNG.

Used once, offline, to derive our own blocky body from the CC0 Kenney Car Kit
(https://kenney.nl/assets/car-kit). NOT part of the Gradle/CI pack build — the
generated `base.json` + `base.png` are the committed artifacts.

Usage (in a venv with `pip install numpy pillow`):
    python tools/voxelize_vehicle.py <model.obj> <textures/colormap.png> <out_model_dir> [px_per_unit]

Outputs <out_model_dir>/base.json and <out_model_dir>/base.png, prints wheel
anchors, element/face counts and a top-left-text preview is rendered through
tools/voxel_preview.py if requested.
"""

import sys
import json
import math
import subprocess
from collections import deque, defaultdict
from pathlib import Path

from PIL import Image


def parse_obj(path):
    verts, uvs = [], []
    groups = {}  # name -> list of (a, b, c, ta, tb, tc) 1-based tuples
    current = "default"
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        parts = line.strip().split()
        if not parts:
            continue
        head = parts[0]
        if head == "g":
            current = parts[1]
        elif head == "v":
            verts.append(tuple(float(p) for p in parts[1:4]))
        elif head == "vt":
            uvs.append(tuple(float(p) for p in parts[1:3]))
        elif head == "f":
            idx = []
            for token in parts[1:]:
                bits = token.split("/")
                v = int(bits[0]) - 1
                t = int(bits[1]) - 1 if len(bits) > 1 and bits[1] else 0
                idx.append((v, t))
            for k in range(1, len(idx) - 1):  # fan triangulation
                groups.setdefault(current, []).append((idx[0], idx[k], idx[k + 1]))
    return verts, uvs, groups


def quantize(c, step=8):
    return (min(255, round(c[0] / step) * step) & 0xFF,
            min(255, round(c[1] / step) * step) & 0xFF,
            min(255, round(c[2] / step) * step) & 0xFF)


def sample_color(tex, w, h, u, v):
    x = min(w - 1, max(0, int(u * w)))
    y = min(h - 1, max(0, int((1.0 - v) * h)))  # OBJ v=0 at image bottom
    c = tex[x, y]
    if len(c) == 4 and c[3] < 128:
        return None
    return quantize(c)


def voxelize(verts, uvs, tris, s, ox, oy, oz):
    """Rasterize triangles into voxel cells; returns {(ix,iy,iz): rgb}.
    s may be a scalar or an (sx, sy, sz) tuple for anisotropic scaling."""
    voxels = {}
    tex_img = None
    sampler = voxels  # alias for readability below in build()
    sub_samples = []
    for a, b, c in tris:
        pa, pb, pc = verts[a[0]], verts[b[0]], verts[c[0]]
        ta, tb, tc = uvs[a[1]], uvs[b[1]], uvs[c[1]]
        sx, sy, sz = (s, s, s) if isinstance(s, (int, float)) else s
        e1 = max(abs(pa[k] - pb[k]) * (sx, sy, sz)[k] for k in range(3))
        e2 = max(abs(pb[k] - pc[k]) * (sx, sy, sz)[k] for k in range(3))
        e3 = max(abs(pa[k] - pc[k]) * (sx, sy, sz)[k] for k in range(3))
        n = max(1, int(max(e1, e2, e3) * 2.0))
        for i in range(n + 1):
            for j in range(n + 1 - i):
                u1, v1 = i / n, j / (n + 1e-9)
                w0 = 1.0 - u1 - v1
                px = (w0 * pa[0] + u1 * pb[0] + v1 * pc[0]) * sx + ox
                py = (w0 * pa[1] + u1 * pb[1] + v1 * pc[1]) * sy + oy
                pz = (w0 * pa[2] + u1 * pb[2] + v1 * pc[2]) * sz + oz
                tu = w0 * ta[0] + u1 * tb[0] + v1 * tc[0]
                tv = w0 * ta[1] + u1 * tb[1] + v1 * tc[1]
                col = sample_color(TEX, TEXW, TEXH, tu, tv)
                if col is None:
                    continue
                key = (math.floor(px), math.floor(py), math.floor(pz))
                voxels[key] = col
    return voxels


TEX = TEXW = TEXH = None  # module-global texture for voxelize()


def strip_interior(voxels):
    xs = [k[0] for k in voxels]; ys = [k[1] for k in voxels]; zs = [k[2] for k in voxels]
    x0, x1 = min(xs) - 1, max(xs) + 1
    y0, y1 = min(ys) - 1, max(ys) + 1
    z0, z1 = min(zs) - 1, max(zs) + 1
    air = set()
    q = deque()
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in (z0, z1):
                q.append((x, y, z))
    for x in (x0, x1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                q.append((x, y, z))
    for x in range(x0, x1 + 1):
        for y in (y0, y1):
            for z in range(z0, z1 + 1):
                q.append((x, y, z))
    while q:
        c = q.popleft()
        if c in air or c in voxels:
            continue
        if not (x0 <= c[0] <= x1 and y0 <= c[1] <= y1 and z0 <= c[2] <= z1):
            continue
        air.add(c)
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            q.append((c[0] + d[0], c[1] + d[1], c[2] + d[2]))
    kept = {k: v for k, v in voxels.items()
            if any((k[0] + d[0], k[1] + d[1], k[2] + d[2]) in air
                   for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))}
    return kept, air


def greedy_merge(voxels):
    """3-pass merge along X, then Z, then Y (fixed color)."""
    runs = []
    by_yz = defaultdict(list)
    for (ix, iy, iz), col in voxels.items():
        by_yz[(iy, iz)].append((ix, col))
    for (iy, iz), cells in by_yz.items():
        cells.sort()
        x0, x1, col = cells[0][0], cells[0][0], cells[0][1]
        for x, c in cells[1:] + [(None, None)]:
            if x == x1 + 1 and c == col:
                x1 = x
            else:
                runs.append((x0, x1, iy, iy, iz, iz, col))
                x0, x1, col = x, x, c
    merged = []
    by_xy_col = defaultdict(list)
    for x0, x1, y0, y1, z0, z1, col in runs:
        by_xy_col[(x0, x1, y0, y1, col)].append((z0, z1))
    for (x0, x1, y0, y1, col), zs in by_xy_col.items():
        zs.sort()
        z0, z1 = zs[0]
        for cz0, cz1 in zs[1:] + [(None, None)]:
            if cz0 == z1 + 1:
                z1 = cz1
            else:
                merged.append((x0, x1, y0, y1, z0, z1, col))
                z0, z1 = cz0, cz1
    final = []
    by_xz_col = defaultdict(list)
    for x0, x1, y0, y1, z0, z1, col in merged:
        by_xz_col[(x0, x1, z0, z1, col)].append((y0, y1))
    for (x0, x1, z0, z1, col), ys in by_xz_col.items():
        ys.sort()
        y0, y1 = ys[0]
        for cy0, cy1 in ys[1:] + [(None, None)]:
            if cy0 == y1 + 1:
                y1 = cy1
            else:
                final.append((x0, x1, y0, y1, z0, z1, col))
                y0, y1 = cy0, cy1
    return final


FACE_DEFS = (
    ("up", (0, 1, 0)), ("down", (0, -1, 0)), ("north", (0, 0, -1)),
    ("south", (0, 0, 1)), ("east", (1, 0, 0)), ("west", (-1, 0, 0)),
)


def face_visible(voxels_set, box, name, d):
    """Systematically per face plane: any voxel in the box-adjacent plane
    covered? If entire plane is covered by voxels -> hide the face."""
    x0, x1, y0, y1, z0, z1, col = box
    axe = {"up": 1, "down": 1, "north": 2, "south": 2, "east": 0, "west": 0}[name]
    plane_voxels = []
    if axe == 1:
        y = y1 + 1 if name == "up" else y0 - 1
        plane_voxels = [(x, y0 if name == "up" else y1, z)
                        for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)]
        plane_voxels = [(x, y, z) for (x, _, z) in plane_voxels]
        src = [(x, y0 if name == "down" else y1, z)
               for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)]
    elif axe == 0:
        x = x1 + 1 if name == "east" else x0 - 1
        src = [(x0 if name == "west" else x1, y, z)
               for y in range(y0, y1 + 1) for z in range(z0, z1 + 1)]
        plane_voxels = [(x, y, z) for (_, y, z) in src]
    else:
        z = z1 + 1 if name == "south" else z0 - 1
        src = [(x, y, z0 if name == "north" else z1)
               for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)]
        plane_voxels = [(x, y, z) for (x, y, _) in src]
    return any(p in voxels_set for p in plane_voxels) is False


def build(obj_path, colormap_path, out_dir, s=24.0):
    global TEX, TEXW, TEXH
    img = Image.open(colormap_path).convert("RGBA")
    TEXW, TEXH = img.size
    TEX = img.load()
    verts, uvs, groups = parse_obj(Path(obj_path))

    body_tris = [t for name, ts in groups.items() if not name.startswith("wheel-") for t in ts]
    wheel_groups = {n: ts for n, ts in groups.items() if n.startswith("wheel-")}

    sx, sy, sz = (s, s, s) if isinstance(s, (int, float)) else s
    xs = [v[0] for v in verts]
    ox = ((max(xs) + min(xs)) / 2) * sx  # center x at origin
    # ground y at 0; keep z centered on origin too (bbox symmetric)
    voxels = voxelize(verts, uvs, body_tris, s, -ox, 0.0, 0.0)
    print(f"surface voxels (raw): {len(voxels)}")
    kept, air = strip_interior(voxels)
    print(f"after interior strip: {len(kept)}")
    boxes = greedy_merge(kept)
    print(f"merged boxes: {len(boxes)}")

    palette = sorted(set(b[6] for b in boxes))
    pal_idx = {c: i for i, c in enumerate(palette)}
    cols = 16
    cell = 16  # px per palette slot in the generated texture
    rows = math.ceil(len(palette) / cols)
    pal_img = Image.new("RGBA", (cols * cell, rows * cell), (0, 0, 0, 0))
    from PIL import ImageDraw
    dr = ImageDraw.Draw(pal_img)
    for c, i in pal_idx.items():
        px = (i % cols) * cell
        py = (i // cols) * cell
        dr.rectangle([px, py, px + cell - 1, py + cell - 1], fill=(c[0], c[1], c[2], 255))

    kept_set = set(kept.keys())
    bounds = {"x0": 1e9, "x1": -1e9, "y1": -1e9, "z0": 1e9, "z1": -1e9}
    elements = []
    face_count = 0
    for b in boxes:
        x0, x1, y0, y1, z0, z1, col = b
        p = pal_idx[col]
        uvx, uvy = (p % cols) * cell + cell / 2 - 2, (p // cols) * cell + cell / 2 - 2
        # vanilla uv units: texels relative to a 16x16 canvas => src_px * 16 / texture_px
        u0 = uvx * 16 / pal_img.width
        v0 = uvy * 16 / pal_img.height
        u1 = (uvx + 4) * 16 / pal_img.width
        v1 = (uvy + 4) * 16 / pal_img.height
        faces = {}
        for name, d in FACE_DEFS:
            if face_visible(kept_set, b, name, d):
                faces[name] = {"uv": [u0, v0, u1, v1], "texture": "#palette"}
                face_count += 1
        fx0, fy0, fz0 = x0 + 8, y0 + 8, z0 + 8
        fx1, fy1, fz1 = x1 + 9, y1 + 9, z1 + 9
        elements.append({"from": [fx0, fy0, fz0], "to": [fx1, fy1, fz1], "faces": faces})
        bounds["x0"] = min(bounds["x0"], fx0); bounds["x1"] = max(bounds["x1"], fx1)
        bounds["y1"] = max(bounds["y1"], fy1)
        bounds["z0"] = min(bounds["z0"], fz0); bounds["z1"] = max(bounds["z1"], fz1)

    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    (out / "base.png").write_bytes(_png_bytes(pal_img))
    model = {
        "credit": "Kenney Car Kit (CC0, kenney.nl) voxelized via tools/voxelize_vehicle.py",
        "textures": {"palette": "vehicle:vehicle/police_car/base", "particle": "vehicle:vehicle/police_car/base"},
        "elements": elements,
        "gui_light": "front",
        "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]}},
    }
    (out / "base.json").write_text(json.dumps(model, indent=2))
    print(f"elements: {len(elements)}  faces: {face_count}  palette colors: {len(palette)}")
    print(f"bounds (file-frame px): x {bounds['x0']}..{bounds['x1']}  z {bounds['z0']}..{bounds['z1']}  top y {bounds['y1']}")

    for name, ts in wheel_groups.items():
        wv = [verts[i[0]] for t in ts for i in t]
        wxs = [v[0] for v in wv]; wys = [v[1] for v in wv]; wzs = [v[2] for v in wv]
        cx = (max(wxs) + min(wxs)) / 2 * sx
        cy = (max(wys) + min(wys)) / 2 * sy
        cz = (max(wzs) + min(wzs)) / 2 * sz
        r = (max(wys) - min(wys)) / 2 * sy
        w = (max(wxs) - min(wxs)) * sx
        print(f"wheel {name}: center=({cx:+.1f}, {cy:.1f}, {cz:+.1f}) radius={r:.1f} width={w:.1f}")


def _png_bytes(img):
    import io
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return buf.getvalue()


if __name__ == "__main__":
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(1)
    scale = (float(v) for v in sys.argv[4].split(",")) if len(sys.argv) > 4 else (24.0,)
    build(sys.argv[1], sys.argv[2], sys.argv[3], tuple(scale) if len(sys.argv) > 4 and "," in sys.argv[4] else float(sys.argv[4]) if len(sys.argv) > 4 else 24.0)
