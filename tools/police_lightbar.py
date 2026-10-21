"""Build police_car/base.json: off_roader_body clone + lightbar + livery stripes.

The body geometry/tint is copied 1:1 from off_roader_body.json (proven shape and
proven tint pipeline). Livery/lightbar faces use the small stylable palette copied to
base.png (tints chosen by the pack build): white/black stripes, red+blue lightbar,
'sirens' bright, metal grill, gray push-bar, iron bumper.
Textures used in the source body stay identical; only the livery extras need the palette.
"""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/resources/assets/vehicle/models/vehicle/off_roader_body.json"
DST = ROOT / "src/main/resources/assets/vehicle/models/vehicle/police_car"

FACE_DIRS = ('north', 'east', 'south', 'west', 'up', 'down')
FACE_AXIS = {'north': 2, 'south': 2, 'east': 0, 'west': 0, 'up': 1, 'down': 1}


def cube(x0, y0, z0, x1, y1, z1, tex, glow_faces=()):
    """axis-aligned cube with all faces; glow_faces get the 'sirens' bright palette key."""
    fr, to = [x0, y0, z0], [x1, y1, z1]
    faces = {}
    for d in FACE_DIRS:
        a1, a2 = [i for i in range(3) if i != FACE_AXIS[d]]
        faces[d] = {
            'uv': [min(fr[a1], to[a1]), min(fr[a2], to[a2]),
                   max(fr[a1], to[a1]), max(fr[a2], to[a2])],
            'texture': '#sirens' if d in glow_faces else tex,
            'tintindex': 0,
        }
    return {'from': fr, 'to': to, 'faces': faces}


def mirror_x(el):
    e = json.loads(json.dumps(el))
    f, t = e['from'], e['to']
    e['from'] = [16 - t[0]] + f[1:]
    e['to'] = [16 - f[0]] + t[1:]
    faces = {}
    for d, face in e['faces'].items():
        nd = d
        if d == 'east':
            nd = 'west'
        elif d == 'west':
            nd = 'east'
        faces[nd] = json.loads(json.dumps(face))
        if d in ('east', 'west'):
            uv = faces[nd]['uv']
            faces[nd]['uv'] = [uv[2], uv[1], uv[0], uv[3]]
    e['faces'] = faces
    r = e.get('rotation')
    if r:
        r['origin'] = [16 - r['origin'][0]] + r['origin'][1:]
        if r['axis'] != 'x':
            r['angle'] = -r['angle']
    return e


def main():
    model = json.load(open(SRC))
    els = [mirror_x(e) for e in model['elements']]

    # livery stripes (flat planes, uv 0..16 mapped via base.png palette)
    stripe_y = (10.95, 12.55)
    for side, x in (('west', -5.0), ('east', 21.0)):
        els.append({
            'from': [x + (-0.05 if side == 'west' else 0.0), stripe_y[0], -16.0],
            'to': [x + (0.0 if side == 'west' else 0.05), stripe_y[1], 32.0],
            'faces': {side: {'uv': [0, 0, 16, 16], 'texture': '#livery', 'tintindex': 1}},
        })
    # nose stripe on the bonnet/top-front area (up face visible from the front 3/4)
    els.append({
        'from': [2.0, 14.4, 20.5], 'to': [14.0, 14.55, 25.5],
        'faces': {'up': {'uv': [0, 0, 16, 16], 'texture': '#black', 'tintindex': 1}},
    })

    # push bar: two vertical posts + two horizontal bars, in front of the bumper
    els.append(cube(3.6, 6.0, 30.6, 4.8, 10.8, 31.8, '#metal'))
    els.append(cube(11.2, 6.0, 30.6, 12.4, 10.8, 31.8, '#metal'))
    els.append(cube(3.6, 9.6, 30.4, 12.4, 10.8, 32.0, '#metal'))
    els.append(cube(3.6, 6.4, 30.4, 12.4, 7.6, 32.0, '#metal'))

    # lightbar: 2 blocks/cell, sloped roof y 23.1 at z 3.5..10.5 -> front cell slightly higher
    els.append(cube(4.0, 23.1, 4.4, 12.0, 23.6, 7.0, '#metal'))     # base
    els.append(cube(4.8, 23.6, 4.6, 8.0, 24.9, 6.8, '#redbar',
                    ('north', 'east', 'south', 'west', 'up')))
    els.append(cube(8.0, 23.6, 4.6, 11.2, 24.9, 6.8, '#bluebar',
                    ('north', 'east', 'south', 'west', 'up')))

    # textures: body keys must match off_roader mapping in the pack build;
    # palette fills are referenced by the amber '#name' keys used above.
    # uv() face uvs in extra cubes are coordinates into the 64x32 base.png palette:
    #   row 0 swatches x 0..8 livery(blue), 8..16 black, 16..24 redbar, 24..32 bluebar,
    #   32..40 metal, 40..48 sirens(white-bright). keep the swatch interiors offset ~0.5
    # extra cube face uvs (already written in-swatches by cube() using 0..16 windows:
    # remap 0..16 into the right swatch window below per texture key)
    SWATCH = {'#livery': 0, '#black': 8, '#redbar': 16, '#bluebar': 24, '#metal': 32, '#sirens': 40}
    for e in els:
        for f in (e.get('faces') or {}).values():
            if f is None:
                continue
            key = f.get('texture')
            if key in SWATCH:
                # palette 64x32, 8x8 swatches in row 0 -> each swatch spans uv 2.0
                x0 = SWATCH[key] / 4.0  # px offset -> uv offset (16/64 = 0.25 per px)
                f['uv'] = [round(x0 + 0.03 + f['uv'][0] * 1.94 / 16, 3),
                           round(0.03 + f['uv'][1] * 1.94 / 16, 3),
                           round(x0 + 0.03 + f['uv'][2] * 1.94 / 16, 3),
                           round(0.03 + f['uv'][3] * 1.94 / 16, 3)]

    textures = {key: 'vehicle:item/police_car_body' for key in
                list(model.get('textures', {}).keys()) + ['livery', 'black', 'redbar', 'bluebar', 'metal', 'sirens']}
    textures['particle'] = 'vehicle:item/police_car_body'
    model['textures'] = textures
    model['elements'] = els
    model['credit'] = 'Off-roader body (MrCrayfish, original mod) + police livery/lightbar extras'; model.pop('comment', None)

    DST.mkdir(parents=True, exist_ok=True)
    out = json.dumps(model, separators=(',', ':'))
    (DST / 'base.json').write_text(out)
    print('elements:', len(els), '| wrote', DST / 'base.json', len(out), 'bytes')

    # small palette texture: 8x8 per color id, generated here so the pack keeps
    # receiving exactly one art file per body (like every other custom alpha key).
    try:
        from PIL import Image
    except ImportError:
        print('PIL missing - palette png skipped')
        return
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    colors = {
        'redbar': (168, 32, 45, 255), 'bluebar': (31, 63, 200, 255),
        'livery': (31, 63, 200, 255), 'black': (18, 18, 18, 255),
        'metal': (70, 74, 80, 255), 'sirens': (238, 242, 248, 255),
    }
    for i, name in enumerate(colors):
        x0 = (i % 8) * 8
        y0 = (i // 8) * 8
        for dx in range(8):
            for dy in range(8):
                img.putpixel((x0 + dx, y0 + dy), colors[name])
    img.save(DST / 'base.png')
    print('palette wrote', DST / 'base.png')


if __name__ == '__main__':
    main()
