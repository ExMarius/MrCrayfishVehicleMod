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

    # ------------------------------------------------------------------
    # Romanian police livery (Politia Romana / Dacia-Duster style):
    #   - big blue horizontal band, y 9..14, full length, both sides
    #   - POLITIA white pixel text on the band (side doors + bonnet + tailgate)
    #   - slim full-width blue lightbar on the roof
    #   - gray metal push bar in front
    # ------------------------------------------------------------------
    BAND_Y = (9.0, 14.0)
    # side skin per 4px z-bin (outer x), band sticks 0.07 out of the skin
    side_left = [(-16, -5.0), (-12, -5.0), (-8, -5.0), (-4, -5.0), (0, -4.0),
                 (4, -4.0), (8, -4.0), (12, -4.0), (16, -5.0), (20, -5.0),
                 (24, -5.0), (28, -4.0)]
    for lo, x in side_left:
        for sx in (x - 0.07, (16 - x) + 0.07):
            x0, x1 = (sx, sx + 0.07) if sx < 8 else (sx, sx + 0.07)
            els.append({
                'from': [round(x0, 2), BAND_Y[0], float(lo)],
                'to': [round(x1, 2), BAND_Y[1], float(lo + 4)],
                'faces': {('west' if sx < 8 else 'east'):
                          {'uv': [0.03, 0.03, 1.97, 1.97], 'texture': '#livery', 'tintindex': 1}},
            })

    # POLITIA text planes (drawn white-on-transparent in the palette, swatch
    # at texture px x 4..50, y 17..26 -> uv window below); left/right doors,
    # bonnet top and tailgate.
    TW, TH = 46 * 0.25, 9 * 0.5  # 11.5 x 4.5 uv
    TEXT_UV = [round(4 * 0.25 + 0.03, 2), round(17 * 0.5 + 0.03, 2),
               round(4 * 0.25 + 0.03 + TW, 2), round(17 * 0.5 + 0.03 + TH, 2)]

    def text_face(d, flip_h=False, flip_v=False):
        u = TEXT_UV if not flip_h else [TEXT_UV[2], TEXT_UV[1], TEXT_UV[0], TEXT_UV[3]]
        if flip_v:
            u = [u[0], u[3], u[2], u[1]]
        return {'uv': u, 'texture': '#politia_text', 'tintindex': 1}

    # doors: cabin tall zone spans z -4..20; band height 5 -> text 4.5 tall centred
    ty0, ty1 = 9.4, 13.1
    els.append({'from': [-4.16, ty0, -2.0], 'to': [-4.14, ty1, 12.0],
                'faces': {'west': text_face('west')}})
    els.append({'from': [20.14, ty0, -2.0], 'to': [20.16, ty1, 12.0],
                'faces': {'east': text_face('east', flip_h=True)}})
    # bonnet (nose is +z): hood top y=13, x 2..14, z 20..30; reads from the nose
    els.append({'from': [3.0, 13.06, 21.0], 'to': [13.0, 13.095, 26.0],
                'faces': {'up': text_face('up', flip_v=True)}})
    # tailgate: rear face is north (-z); trunk top 13
    els.append({'from': [3.0, 9.6, -16.12], 'to': [13.0, 13.4, -16.10],
                'faces': {'north': text_face('north', flip_h=True)}})

    # push bar: two vertical posts + two horizontal bars, in front of the bumper
    els.append(cube(3.6, 6.0, 30.6, 4.8, 10.8, 31.8, '#metal'))
    els.append(cube(11.2, 6.0, 30.6, 12.4, 10.8, 31.8, '#metal'))
    els.append(cube(3.6, 9.6, 30.4, 12.4, 10.8, 32.0, '#metal'))
    els.append(cube(3.6, 6.4, 30.4, 12.4, 7.6, 32.0, '#metal'))

    # slim full-width blue lightbar (LED look), centred on the roof
    els.append(cube(3.9, 23.1, 4.4, 12.1, 23.55, 7.0, '#metal'))          # base rail
    els.append(cube(4.3, 23.55, 4.6, 11.7, 24.35, 6.8, '#bluebar',
                    ('north', 'east', 'south', 'west', 'up')))

    # textures: body keys must match off_roader mapping in the pack build;
    # palette fills are referenced by the amber '#name' keys used above.
    # uv() face uvs in extra cubes are coordinates into the 64x32 base.png palette:
    #   row 0 swatches x 0..8 livery(blue), 8..16 metal tint, 16..24 redbar, 24..32 bluebar,
    #   32..40 gray metal, 40..48 sirens(white-bright); '#politia_text' faces carry their
    #   own explicit uv into the text strip (texture px x 4..50, y 17..26) - do not remap.
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
                list(model.get('textures', {}).keys()) + ['livery', 'black', 'redbar', 'bluebar', 'metal', 'sirens', 'politia_text']}
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
        'redbar': (168, 32, 45, 255), 'bluebar': (21, 57, 190, 255),
        'livery': (21, 57, 190, 255), 'black': (18, 18, 18, 255),
        'metal': (70, 74, 80, 255), 'sirens': (238, 242, 248, 255),
    }
    for i, name in enumerate(colors):
        x0 = (i % 8) * 8
        y0 = (i // 8) * 8
        for dx in range(8):
            for dy in range(8):
                img.putpixel((x0 + dx, y0 + dy), colors[name])

    # POLITIA pixel text (5x8 glyphs, white on transparent) into the text strip
    GLYPHS = {
        'P': ['11110', '10001', '10001', '11110', '10000', '10000', '10000', '10000'],
        'O': ['01110', '10001', '10001', '10001', '10001', '10001', '10001', '01110'],
        'L': ['10000', '10000', '10000', '10000', '10000', '10000', '10000', '11111'],
        'I': ['01110', '00100', '00100', '00100', '00100', '00100', '00100', '01110'],
        # T with the comma accent baked low (close enough at 5x8 for POLITIA)
        '\u021a': ['11111', '00100', '00100', '00100', '00100', '00100', '00110', '00100'],
        'A': ['01110', '10001', '10001', '10001', '11111', '10001', '10001', '10001'],
    }
    word = 'POLI\u021aIA'
    xcur = 4
    white = (244, 246, 250, 255)
    for ch in word:
        g = GLYPHS[ch]
        for gy, row in enumerate(g[:9]):
            for gx, bit in enumerate(row):
                if bit == '1':
                    img.putpixel((xcur + gx, 17 + gy), white)
        xcur += 6
    print('text strip ends at x', xcur, '(window to 50)')

    img.save(DST / 'base.png')
    print('palette wrote', DST / 'base.png')


if __name__ == '__main__':
    main()
