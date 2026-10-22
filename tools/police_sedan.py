"""Build the Romanian police sedan (police v5) from sports_car:

- body: sports_car/base.json components transferred as-is (proper car silhouette,
  native 2-block-wide chassis with sloped hood/cabin), renamed with police texture.
- livery: body-black paint regions in the 128x128 texture are recolored to police
  white, the waistline band (native y 8..14) is painted blue, and POLITIA pixel
  text goes on the two door flank faces via a dedicated high-res strip appended
  to the texture canvas (256x128).
- lightbar: slim full-width blue LED bar on the roof plate, gray push bar retained
  on the nose. Palette keys #livery/#bluebar/#metal/#sirens reuse the existing
  64x32 palette at models/vehicle/police_car/base.png.
Anchors stay in the spec file (wheels/seats = sports_car SPORTS_CAR values).
"""

import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SRC_MODEL = ROOT / "src/main/resources/assets/vehicle/models/vehicle/sports_car/base.json"
SRC_TEX = ROOT / "src/main/resources/assets/vehicle/textures/vehicle/sports_car/base.png"
DST_DIR = ROOT / "src/main/resources/assets/vehicle/models/vehicle/police_car"

WHITE = (240, 242, 245, 255)
BAND = (21, 57, 190, 255)
BODY_BLACK = (50, 49, 46)

# POLITIA 5x8 glyphs (same font as tools/police_lightbar.py)
GLYPHS = {
    'P': ['11110', '10001', '10001', '11110', '10000', '10000', '10000', '10000'],
    'O': ['01110', '10001', '10001', '10001', '10001', '10001', '10001', '01110'],
    'L': ['10000', '10000', '10000', '10000', '10000', '10000', '10000', '11111'],
    'I': ['01110', '00100', '00100', '00100', '00100', '00100', '00100', '01110'],
    '\u021a': ['11111', '00100', '00100', '00100', '00100', '00100', '00110', '00100'],
    'A': ['01110', '10001', '10001', '10001', '11111', '10001', '10001', '10001'],
}
WORD = 'POLI\u021aIA'

BAND_Y = (5.5, 10.5)   # native model units: waistline over the door panels
FACE_AXIS = {'north': 2, 'south': 2, 'east': 0, 'west': 0, 'up': 1, 'down': 1}


def median_color(img, rect):
    x0, y0, x1, y1 = [max(0, int(round(v))) for v in rect]
    x1, y1 = min(x1, img.width), min(y1, img.height)
    if x1 <= x0 or y1 <= y0:
        return (0, 0, 0, 0)
    cols = {}
    for x in range(x0, x1):
        for y in range(y0, y1):
            p = img.getpixel((x, y))
            if p[3] > 0:
                cols[p] = cols.get(p, 0) + 1
    return max(cols.items(), key=lambda kv: kv[1])[0] if cols else (0, 0, 0, 0)


def near(c, t, tol=14):
    return all(abs(c[i] - t[i]) <= tol for i in range(3)) and c[3] == 255


SWATCH_U = {'#livery': 0.0, '#black': 2.0, '#redbar': 4.0, '#bluebar': 6.0,
            '#metal': 8.0, '#sirens': 10.0}


def comp_box(x0, y0, z0, x1, y1, z1, key, tint=0):
    fr, to = [x0, y0, z0], [x1, y1, z1]
    faces = {}
    ux = SWATCH_U[key]
    for d in FACE_AXIS:
        faces[d] = {'uv': [ux + 0.03, 0.03, ux + 1.97, 1.97],
                    'texture': key, 'tintindex': tint}
    return {'from': fr, 'to': to,
            'rotation': {'angle': 0, 'axis': 'y', 'origin': [sum(fr) / 3 + 8, 0, 0]},
            'faces': faces}


def main():
    m = json.load(open(SRC_MODEL))
    comp = m['components']
    img = Image.open(SRC_TEX).convert('RGBA')
    canvas = Image.new('RGBA', (256, 128), (0, 0, 0, 0))
    canvas.paste(img, (0, 0))

    text_strips = []   # (comp_index, face_name) that should receive the POLITIA artwork

    # repaint rules on exterior body-black faces (paint variants 50,49,46 / 60,59,56):
    #   vertical faces only, elements starting at y >= 2 (never the floor plate, interior
    #   or undercarriage); band rows land only where the face's world-y overlaps 5.5..10.5
    # Bonnets/boot decks (big flat 'up' faces at y >= 8) become white, no band.
    for idx, e in enumerate(comp):
        x0, y0, z0 = e['from']; x1, y1, z1 = e['to']
        side_area = max((y1 - y0) * (z1 - z0), (x1 - x0) * (z1 - z0))
        for face_name in ('west', 'east', 'north', 'south'):
            f = e.get('faces', {}).get(face_name)
            f0check = f
            if not f0check or 'uv' not in f0check or y0 < 2.0:
                continue
            u0, v0, u1, v1 = f['uv']
            rect = (u0 * 8, v0 * 8, u1 * 8, v1 * 8)
            c = median_color(canvas, rect)
            if not near(c, BODY_BLACK):
                continue
            for py in range(int(rect[1]), int(rect[3])):
                frac = (py - rect[1]) / max(1.0, rect[3] - rect[1])
                wy = y0 + frac * (y1 - y0)
                col = BAND if BAND_Y[0] <= wy <= BAND_Y[1] else WHITE
                for px in range(int(rect[0]), int(rect[2])):
                    if canvas.getpixel((px, py))[3] > 0:
                        canvas.putpixel((px, py), col)
        f = e.get('faces', {}).get('up')
        top_area = (x1 - x0) * (z1 - z0)
        if f and 'uv' in f and y0 >= 8.0 and top_area >= 60:
            u0, v0, u1, v1 = f['uv']
            rect = (u0 * 8, v0 * 8, u1 * 8, v1 * 8)
            c = median_color(canvas, rect)
            if near(c, BODY_BLACK):
                for py in range(int(rect[1]), int(rect[3])):
                    for px in range(int(rect[0]), int(rect[2])):
                        if canvas.getpixel((px, py))[3] > 0:
                            canvas.putpixel((px, py), WHITE)
    # POLITIA goes on the merged flank doors: comp37 west (visible, normal reading),
    # comp37 east (mirrored reading), comp40 north (rear tailgate, mirrored)
    strips = []
    def door_strip(idx, face_name, flip_h):
        e = comp[idx]
        # paint blue bg + white POLITIA into a fresh artwork region per strip
        strips.append((idx, face_name, flip_h))
        return len(strips) - 1

    ART_X = 130
    ART_Y = 30   # each strip is 46x9 px at 8px/unit -> uv window (130..176)/8, (30..39)/8

    def paint_strip_art(slot):
        ay = ART_Y + slot * 10
        for dx in range(46):
            for dy in range(9):
                canvas.putpixel((ART_X + dx, ay + dy), BAND)
        xcur = ART_X + 2
        for ch in WORD:
            g = GLYPHS[ch]
            for gy, row in enumerate(g):
                for gx, bit in enumerate(row):
                    if bit == '1':
                        canvas.putpixel((xcur + gx, ay + gy), WHITE)
            xcur += 6
        return (ART_X / 8.0, ay / 8.0, (ART_X + 46) / 8.0, (ay + 9) / 8.0)

    for idx, face_name, flip in ((37, 'west', False), (37, 'east', True), (40, 'north', True)):
        slot = door_strip(idx, face_name, flip)
        u0, v0, u1, v1 = paint_strip_art(slot)
        if flip:
            comp[idx]['faces'][face_name]['uv'] = [u1, v0, u0, v1]
        else:
            comp[idx]['faces'][face_name]['uv'] = [u0, v0, u1, v1]

    # lightbar on the greenhouse roof plate: sports_car roof cosmetic caries the cabin
    # shell (top plate y 16..17, native frame, same scale/wo as the body), so the bar
    # mounts at y 17.0 over the cockpit centre line z 5..9, x centred on the plate.
    ry = 20.76
    cx0, cx1 = 3.6, 12.4
    cz0, cz1 = 18.42, 20.30
    new = list(comp)
    new.append(comp_box(cx0, ry, cz0, cx1, ry + 0.42, cz1, '#metal'))
    mid = (cx0 + cx1) / 2.0
    new.append(comp_box(cx0 + 0.35, ry + 0.42, cz0 + 0.18, mid - 0.1, ry + 1.1, cz1 - 0.18,
                        '#bluebar', 0))
    new.append(comp_box(mid + 0.1, ry + 0.42, cz0 + 0.18, cx1 - 0.35, ry + 1.1, cz1 - 0.18,
                        '#bluebar', 0))

    # slim gray push bar under the nose (+z friendly, kept inside x edges)
    for i, zoff in enumerate((0.0,)):
        new.append(comp_box(2.5, 4.0, 38.8 + zoff, 3.7, 8.2, 40.0 + zoff, '#metal'))
        new.append(comp_box(20.3, 4.0, 38.8 + zoff, 21.5, 8.2, 40.0 + zoff, '#metal'))
        new.append(comp_box(2.5, 7.0, 38.6 + zoff, 21.5, 8.2, 40.2 + zoff, '#metal'))
        new.append(comp_box(2.5, 4.2, 38.6 + zoff, 21.5, 5.4, 40.2 + zoff, '#metal'))

    textures = dict(m.get('textures', {}))
    textures['8'] = 'vehicle:vehicle/police_car/base'
    for k in ('livery', 'bluebar', 'redbar', 'metal', 'sirens', 'black'):
        textures[k] = 'vehicle:item/police_car_body'
    m['textures'] = textures
    m['components'] = new
    m['credit'] = ('Police sedan for the paper plugin: sports-car body (MrCrayfish, '
                   'Blockbench / Framework open_model) repainted with Politia Romana '
                   'livery by tools/police_sedan.py')

    DST_DIR.mkdir(parents=True, exist_ok=True)
    (DST_DIR / 'base.json').write_text(json.dumps(m, separators=(',', ':')))
    canvas.save(DST_DIR / 'sedan_base.png')
    print('components:', len(new), '| texture 256x128 | strips on', len(text_strips), 'door slots:',
          [i for i, _ in text_strips])


if __name__ == '__main__':
    main()
