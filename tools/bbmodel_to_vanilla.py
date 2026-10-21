"""Convert a Blockbench .bbmodel into our vanilla item-model JSON + extracted wheel anchors.

bbmodel elements are already vanilla-ish cubes; groups here use origin (8,8,8) with no
rotation, so no re-transforming is needed. Handles: embedded texture decode, uv rescale to
vanilla units (px/16 on the source texture resolution), wheel cluster extraction (elements
named wheel/tyre/hub -> 4 corners, removed from the body), 1:1 rotation passthrough.
Usage: python tools/bbmodel_to_vanilla.py <in.bbmodel> <out_dir> [--keep-wheels]
"""

import sys, json, base64, io, math
from pathlib import Path
from collections import defaultdict


def cluster_wheels(elements):
    """Group wheel-ish element centers into 4 corner clusters; return (ids_to_drop, anchors)."""
    w = []
    for e in elements:
        n = (e.get('name') or '').lower()
        if any(k in n for k in ('wheel', 'tyre', 'tire', 'hub', 'rim')) and 'steering' not in n and 'arch' not in n:
            cx = (e['from'][0] + e['to'][0]) / 2
            cy = (e['from'][1] + e['to'][1]) / 2
            cz = (e['from'][2] + e['to'][2]) / 2
            w.append((id(e), cx, cy, cz,
                  math.dist((e['from'][0], e['from'][1], e['from'][2]),
                            (e['to'][0], e['to'][1], e['to'][2]))))
    # cluster by proximity in (x, z)
    parent = list(range(len(w)))
    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i
    for i in range(len(w)):
        for j in range(i + 1, len(w)):
            if abs(w[i][1] - w[j][1]) < 2.5 and abs(w[i][3] - w[j][3]) < 2.5:
                parent[find(i)] = find(j)
    clusters = defaultdict(list)
    for i in range(len(w)):
        clusters[find(i)].append(i)
    anchors = []
    drop = set()
    if len(clusters) == 4:
        for idxs in clusters.values():
            xs_ = [w[i][1] for i in idxs]; ys_ = [w[i][2] for i in idxs]; zs_ = [w[i][3] for i in idxs]
            anchors.append((round(sum(xs_) / len(xs_), 2), round(sum(ys_) / len(ys_), 2),
                            round(sum(zs_) / len(zs_), 2),
                            round(max(w[i][4] for i in idxs) / 2.5, 2)))
            for i in idxs:
                drop.add(w[i][0])
    return drop, anchors


def convert(bb_path, out_dir, keep_wheels=False, flip_y=False):
    m = json.load(open(bb_path))
    els = m['elements']
    drop, anchors = cluster_wheels(els)
    print("wheel clusters:", len(anchors), anchors if anchors else "(none - wheels kept)")

    # texture: decode embedded, save
    tex = m['textures'][0]
    src = tex['source']
    data = base64.b64decode(src.split(',', 1)[1]) if ',' in src else base64.b64decode(src)
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    (out / 'base.png').write_bytes(data)
    tw, th = tex.get('width', 256), tex.get('height', 256)

    body = []
    for e in els:
        if not keep_wheels and id(e) in drop:
            continue
        faces = {}
        for dname, f in (e.get('faces') or {}).items():
            if f is None or 'uv' not in f:
                continue
            u0, v0, u1, v1 = f['uv']
            faces[dname] = {
                'uv': [round(u0 * 16 / tw, 3), round(v0 * 16 / th, 3),
                       round(u1 * 16 / tw, 3), round(v1 * 16 / th, 3)],
                'texture': '#0',
            }
        f, t = [round(v, 2) for v in e['from']], [round(v, 2) for v in e['to']]
        if flip_y:
            f, t = [round(16 - t[0], 2), f[1], round(16 - t[2], 2)], [round(16 - f[0], 2), t[1], round(16 - f[2], 2)]
        el = {
            'from': f,
            'to': t,
        }
        if faces:
            el['faces'] = faces
        r = e.get('rotation')
        if r and any(abs(v) > 0.01 for v in r):
            axis = ('x', 'y', 'z')[max(range(3), key=lambda i: abs(r[i]))]
            angle = r[{'x': 0, 'y': 1, 'z': 2}[axis]]
            o = [round(v, 2) for v in e.get('origin', [8, 8, 8])]
            if flip_y:
                if axis != 'y':
                    angle = -angle
                o = [round(16 - o[0], 2), o[1], round(16 - o[2], 2)]
            el['rotation'] = {'angle': angle, 'axis': axis, 'origin': o}
        body.append(el)

    lo = [min(min(e['from'][i], e['to'][i]) for e in body) for i in range(3)] if body else [0, 0, 0]
    hi = [max(max(e['from'][i], e['to'][i]) for e in body) for i in range(3)] if body else [0, 0, 0]
    print("body bbox:", lo, "->", hi)
    model = {
        'credit': 'ems_police_car.bbmodel by crunkazcanbe (MIT), converted via tools/bbmodel_to_vanilla.py',
        'textures': {'0': 'vehicle:item/police_car_body', 'particle': 'vehicle:item/police_car_body'},
        'elements': body,
        'gui_light': 'front',
        'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.6, 0.6, 0.6]}},
    }
    (out / 'base.json').write_text(json.dumps(model, separators=(',', ':')))
    print("elements:", len(body), "| texture:", len(data), "bytes")
    return anchors


if __name__ == '__main__':
    convert(sys.argv[1], sys.argv[2], '--keep-wheels' in sys.argv, '--flip-y' in sys.argv)
