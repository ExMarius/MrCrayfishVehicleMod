"""Stage 2: trimesh geometry + XML material binding -> voxel model + preview.

Frame mapping: source (x=width, y=length, z=height, units ~cm) -> model px frame
(x width, y height, z length) at anisotropic (sx, sy, sz) scale, then Blockbench
+8,+8,+8 offsets like all our vehicles.
"""

import sys, math, json, io, pickle
import numpy as np
import xml.etree.ElementTree as ET
import trimesh
from collections import defaultdict, deque
from PIL import Image, ImageDraw

Q = '{http://www.collada.org/2005/11/COLLADASchema}'
sys.path.insert(0, '/home/user/MrCrayfishVehicleMod/tools')
import voxelize_vehicle as V  # reuse greedy_merge / strip_interior / face_visible


def xml_materials(dae_path):
    root = ET.parse(dae_path).getroot()
    images = {}
    for im in root.findall(f'.//{Q}library_images/{Q}image'):
        init = im.find(f'{Q}init_from')
        if init is not None and init.text:
            images[im.get('id')] = init.text.strip()
    effects = {}
    for eff in root.findall(f'.//{Q}effect'):
        eid = eff.get('id')
        tex = None
        lam = None
        for tag in ('lambert', 'phong', 'blinn'):
            lam = eff.find(f'.//{Q}{tag}/{Q}diffuse')
            if lam is not None:
                break
        lamc = lam
        if lamc is not None and lamc.find(f'{Q}texture') is not None:
            tref = lamc.find(f'{Q}texture').get('texture')
            surface = None
            for s2d in eff.iter(f'{Q}sampler2D'):
                if s2d.get('sid') == tref:
                    src = s2d.find(f'{Q}source')
                    surface = src.text if src is not None else None
            if surface:
                for sf in eff.iter(f'{Q}surface'):
                    if sf.get('sid') == surface:
                        init = sf.find(f'{Q}init_from')
                        if init is not None:
                            tex = images.get(init.text.strip())
        color = None
        if lamc is not None and lamc.find(f'{Q}color') is not None:
            vals = [float(x) for x in lamc.find(f'{Q}color').text.split()]
            color = tuple(int(round(v * 255)) for v in vals[:3])
        effects[eid] = (tex, color)
    materials = {}
    for m in root.findall(f'.//{Q}material'):
        inst = m.find(f'{Q}instance_effect')
        if inst is not None:
            materials[m.get('id')] = inst.get('url', '').lstrip('#')
    geom_effect = {}
    for ig in root.iter(f'{Q}instance_geometry'):
        target = ig.get('url', '').lstrip('#')
        smap = {}
        for imat in ig.findall(f'.//{Q}instance_material'):
            smap[imat.get('symbol')] = imat.get('target', '').lstrip('#')
        if target and smap and target not in geom_effect:
            for sym, mat in smap.items():
                eff_id = materials.get(mat)
                if eff_id in effects:
                    geom_effect[target] = effects[eff_id]
                    break
    return images, effects, geom_effect


def build(dae_path, out_dir, scale_xyz):
    images, effects, geom_effect = xml_materials(dae_path)
    scene = trimesh.load(dae_path)
    sx, sy, sz = scale_xyz
    base = dae_path.rsplit('/', 1)[0] if '/' in dae_path else '.'

    tex_cache = {}

    def tex_pixels(path):
        if path not in tex_cache:
            p = f'{base}/{path}' if '/' in path or path.endswith('.png') or path.endswith('.jpg') else None
            img = Image.open(p).convert('RGBA')
            tex_cache[path] = (img.load(), img.width, img.height)
        return tex_cache[path]

    from collections import defaultdict
    DIAG = defaultdict(dict)
    R = np.array([[1, 0, 0], [0, 0, 1], [0, -1, 0]], dtype=float)  # (x,y,z)->(x,z,-y) flips length so -y end -> +z
    meshes = []
    allmin = np.full(3, 1e18)
    allmax = np.full(3, -1e18)
    for node in scene.graph.nodes_geometry:
        T, gname = scene.graph[node]
        g = scene.geometry.get(gname)
        if g is None or getattr(g, 'faces', None) is None or len(g.faces) == 0:
            continue
        m = g.copy()
        m.apply_transform(T)
        verts = np.asarray(m.vertices)
        faces = np.asarray(m.faces)
        m2 = verts @ R.T  # rotate
        uv = getattr(getattr(m, 'visual', None), 'uv', None)
        base_name = gname.split('_', 1)[0] if '_' in gname else gname
        eff = geom_effect.get(gname) or geom_effect.get(base_name)
        tpath, dcol = (eff[0], eff[1]) if eff else (None, (128, 128, 128))
        for f in faces:
            a = m2[f[0]]; b = m2[f[1]]; c = m2[f[2]]
            uvs = None
            if tpath is not None and uv is not None:
                uvs = (uv[f[0]], uv[f[1]], uv[f[2]])
            mn = np.minimum(a, np.minimum(b, c))
            mx = np.maximum(a, np.maximum(b, c))
            allmin = np.minimum(allmin, mn)
            allmax = np.maximum(allmax, mx)
        meshes.append((gname, m2, faces, uv, tpath, dcol))
        DIAG['with_tex' if tpath else 'with_color' if dcol else 'none'][gname.split('_')[0]] = None
    span = allmax - allmin
    print("src world span (reoriented):", span.round(1), "| binding:", {k: len(v) for k, v in DIAG.items()})

    vs = []

    # provisional wheels detection in reoriented coords
    bounds = []
    for gname, m2, faces, uv, tpath, dcol in meshes:
        mn = m2.min(0); mx = m2.max(0)
        bounds.append((gname, mn, mx, len(faces)))
    W, H, L = span
    cands = []
    for gname, mn, mx, nf in bounds:
        sp = mx - mn
        cen = (mn + mx) / 2
        if sp[1] > 12 and abs(sp[1] - sp[2]) < 0.18 * max(sp[1], sp[2]) and sp[0] < 0.6 * sp[1] and cen[1] < mn2y if False else True:
            pass
    # simpler: collect candidate computations with explicit filters
    gminy = min(b[1][1] for b in bounds)
    for gname, mn, mx, nf in bounds:
        sp = mx - mn
        cen = (mn + mx) / 2
        ratio_ok = (sp[1] > 12 and abs(sp[1] - sp[2]) <= 0.2 * sp[1] and sp[0] <= 0.55 * sp[1])
        low_ok = (cen[1] - gminy) < 0.45 * span[1]
        side_ok = abs(cen[0] - (allmin[0] + allmax[0]) / 2) > 0.25 * span[0]
        frontback_ok = abs(cen[2] - (allmin[2] + allmax[2]) / 2) > 0.3 * span[2]
        if ratio_ok and low_ok and side_ok and frontback_ok:
            cands.append((gname, tuple(sp.round(1)), tuple(cen.round(1)), nf))
    print("wheel candidates:", len(cands))
    for c in sorted(cands, key=lambda c: (c[2][2], c[2][0]))[:10]:
        print("   ", c)

    # union-find clustering by (x,z) proximity of candidate centers
    cands = [c for c in cands if c[1][1] >= 19 and c[1][1] <= 32]
    parent = list(range(len(cands)))
    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i
    for i in range(len(cands)):
        for j in range(i + 1, len(cands)):
            ai, aj = cands[i][2], cands[j][2]
            if abs(ai[0] - aj[0]) < 4 and abs(ai[2] - aj[2]) < 4:
                parent[find(i)] = find(j)
    clusters = {}
    for i in range(len(cands)):
        clusters.setdefault(find(i), []).append(i)
    print("wheel clusters:", len(clusters))
    wheel_ids = set()
    wheel_anchors = []
    if len(clusters) == 4:
        for root, idxs in clusters.items():
            xs_ = [cands[i][2][0] for i in idxs]
            ys_ = [cands[i][2][1] for i in idxs]
            zs_ = [cands[i][2][2] for i in idxs]
            dia = max(cands[i][1][1] for i in idxs)
            cx_, cy_, cz_ = sum(xs_)/len(xs_), sum(ys_)/len(ys_), sum(zs_)/len(zs_)
            for i in idxs:
                wheel_ids.add(cands[i][0])
            wheel_anchors.append((cx_, cy_, cz_, dia / 2))
            print(f"wheel cluster: center=({cx_:.1f},{cy_:.1f},{cz_:.1f}) radius={dia/2:.1f} parts={len(idxs)}")
    else:
        print("!! wheel detection failed - no exclusions")

    voxels = {}
    for gname, m2, faces, uv, tpath, dcol in meshes:
        if gname in wheel_ids:
            continue
        tex = tpath if tpath else None
        tpix = th = tw = None
        if tex:
            try:
                tpix, tw, th = tex_pixels(tex)
            except Exception as e:
                tex = None
        uv_present = uv is not None and len(uv) == len(m2)
        for fi, f in enumerate(faces):
            pa, pb, pc = m2[f[0]], m2[f[1]], m2[f[2]]
            if tex and uv_present:
                ua, ub, uc = uv[f[0]], uv[f[1]], uv[f[2]]
            else:
                ua = ub = uc = None
            e1 = max(abs(pa[k] - pb[k]) for k in range(3)) * 1.0
            e2 = max(abs(pb[k] - pc[k]) for k in range(3)) * 1.0
            e3 = max(abs(pa[k] - pc[k]) for k in range(3)) * 1.0
            n = max(1, int(max(e1 * sx, e2 * sy, e3 * sz) * 1.6))
            for i in range(n + 1):
                for j in range(n + 1 - i):
                    w0 = i / n if n > 0 else 0
                    w1 = j / n if n > 0 else 0
                    w2 = 1.0 - w0 - w1
                    if w2 < -1e-9:
                        continue
                    px = (w0 * pa[0] + w1 * pb[0] + w2 * pc[0]) * sx
                    py = (w0 * pa[1] + w1 * pb[1] + w2 * pc[1]) * sy
                    pz = (w0 * pa[2] + w1 * pb[2] + w2 * pc[2]) * sz
                    col = None
                    if tex and ua is not None:
                        tu = w0 * ua[0] + w1 * ub[0] + w2 * uc[0]
                        tv = w0 * ua[1] + w1 * ub[1] + w2 * uc[1]
                        xpix = min(tw - 1, max(0, int(tu * tw)))
                        ypix = min(th - 1, max(0, int((1.0 - tv) * th)))
                        c = tpix[xpix, ypix]
                        if c[3] < 128:
                            col = None
                        else:
                            col = (c[0], c[1], c[2])
                    if col is None and dcol is not None:
                        col = dcol
                    if col is None:
                        continue
                    key = (math.floor(px), math.floor(py - min(allmin[1] * sy, 0) * 0.0), math.floor(pz))
                    voxels[key] = (min(255, round(col[0] / 16) * 16), min(255, round(col[1] / 16) * 16), min(255, round(col[2] / 16) * 16))
    print("voxels raw:", len(voxels))
    # normalize to ground y=0 and center x,z
    xs_ = [k[0] for k in voxels]; ys_ = [k[1] for k in voxels]; zs_ = [k[2] for k in voxels]
    x0, x1 = min(xs_), max(xs_)
    y0 = min(ys_)
    z0, z1 = min(zs_), max(zs_)
    xc = (x0 + x1) // 2
    zc = (z0 + z1) // 2
    voxels = {(kx - xc, ky - y0, kz - zc): v for (kx, ky, kz), v in voxels.items()}
    kept, air = V.strip_interior(voxels)
    print("after interior strip:", len(kept))
    boxes = V.greedy_merge(kept)
    print("merged boxes:", len(boxes))

    palette = sorted(set(b[6] for b in boxes))
    pal_idx = {c: i for i, c in enumerate(palette)}
    cols = 16
    cell = 16
    rows = math.ceil(len(palette) / cols)
    pal_img = Image.new('RGBA', (cols * cell, rows * cell), (0, 0, 0, 0))
    dr = ImageDraw.Draw(pal_img)
    for c, i in pal_idx.items():
        px = (i % cols) * cell
        py = (i // cols) * cell
        dr.rectangle([px, py, px + cell - 1, py + cell - 1], fill=(c[0], c[1], c[2], 255))
    out = __import__('pathlib').Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    kept_set = set(kept.keys())
    elements = []
    bounds2 = {'x0': 1e9, 'x1': -1e9, 'y1': -1e9, 'z0': 1e9, 'z1': -1e9}
    for b in boxes:
        x0b, x1b, y0b, y1b, z0b, z1b, col = b
        p = pal_idx[col]
        uvx, uvy = (p % cols) * cell + 6, (p // cols) * cell + 6
        u0 = uvx * 16 / pal_img.width
        v0 = uvy * 16 / pal_img.height
        u1 = (uvx + 4) * 16 / pal_img.width
        v1 = (uvy + 4) * 16 / pal_img.height
        faces = {}
        for name, ddd in V.FACE_DEFS:
            if V.face_visible(kept_set, b, name, ddd):
                faces[name] = {'uv': [round(u0, 3), round(v0, 3), round(u1, 3), round(v1, 3)], 'texture': '#0'}
        fx0, fy0, fz0 = x0b + 8, y0b + 8, z0b + 8
        fx1, fy1, fz1 = x1b + 9, y1b + 9, z1b + 9
        elements.append({'from': [round(fx0, 2), round(fy0, 2), round(fz0, 2)], 'to': [round(fx1, 2), round(fy1, 2), round(fz1, 2)], 'faces': faces})
        bounds2['x0'] = min(bounds2['x0'], fx0); bounds2['x1'] = max(bounds2['x1'], fx1)
        bounds2['y1'] = max(bounds2['y1'], fy1)
        bounds2['z0'] = min(bounds2['z0'], fz0); bounds2['z1'] = max(bounds2['z1'], fz1)
    buf = io.BytesIO()
    pal_img.save(buf, 'PNG')
    (out / 'base.png').write_bytes(buf.getvalue())
    model = {
        'credit': 'Sketchfab police car voxelized via dae pipeline; bounds checked [-16,32]',
        'textures': {'0': 'vehicle:item/police_car_body', 'particle': 'vehicle:item/police_car_body'},
        'elements': elements,
        'gui_light': 'front',
        'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.6, 0.6, 0.6]}},
    }
    (out / 'base.json').write_text(json.dumps(model, separators=(',', ':')))
    print("elements:", len(elements), "palette:", len(palette), "bounds:", bounds2)
    xs_ = [k[0] for k in voxels] if voxels else [0]
    for (cx_, cy_, cz_, r_) in wheel_anchors:
        Y0 = min((k[1] for k in ((math.floor((p[1]) * sy)) for p in ()) ), default=0)
        pass
    print("wheel anchors (world units in reoriented space):", wheel_anchors)


if __name__ == '__main__':
    build(sys.argv[1], sys.argv[2], tuple(float(x) for x in sys.argv[3].split(',')))
