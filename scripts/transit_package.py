# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow", "coloraide"]
# ///
"""Build the transit packages' texture from Create's cardboard.

Same discipline as repalette.py, and for the same reason: the source is the
pixels inside Create's jar rather than the file on disk, so a rebuild always
starts from the original and cannot accumulate strays from the last one. The
jar is found by the helper there too, which pins the exact Create the project
builds against.

    uv run scripts/transit_package.py            # rebuild
    uv run scripts/transit_package.py --check    # report only, touch nothing
    uv run scripts/transit_package.py --verify   # assert the rebuild matches HEAD
    uv run scripts/transit_package.py --preview  # magnified grids for review

What it makes
-------------

One 64x64 atlas, the transit counterpart of `create:item/package/cardboard`.
Create packs all four standard boxes into that single texture, one size per
column, and every `cardboard_WxH` model reads it -- so our four item models
parent Create's own models and override the texture, and no geometry, no UVs
and no rigging art are ours to maintain.

The atlas is recoloured whole, then a waybill is printed on each of the four
lids. Create's own grammar for a different package is "same box, print
something on it": rare_simi.png is cardboard.png's palette exactly, plus the
colours of the design over it, and nine of the ten rare boxes keep the
cardboard entirely. The lid is kept clear in six of them and never carries a
bordered shape. This texture breaks both of those on purpose. A rare box is a
prize and only has to look good; this one has to be identifiable at a glance,
from any angle, at inventory-icon size, because it is how a player sees that
goods are still foreign. The reasoning is in issue #3.

Colour
------

Two spaces, each because it is what produced the approved artwork rather than
out of principle. The body is an HSV hue rotation, which is what the chosen
green was picked from. The label is built in OKLCH, which is what made it stop
looking like a sticker: picked by eye, its paper had landed at L 0.946, and
Create paints packages between L 0.575 and 0.795 with nothing above 0.86. Paper
now sits exactly on that ceiling -- the lightest thing on the box, and no
lighter than anything the game already puts on a package.

The waybill
-----------

A lid is as wide as the box's footprint, so the four sizes give two lids: 12x12
px on the 12-wide boxes and 10x10 on the 10-wide ones. The 12-wide bill is the
approved artwork unchanged, down to its off-centre seat; the 10-wide one is the
same design one size down, dropping the second ruled line that no longer fits.
"""
import argparse
import colorsys
import io
import json
import os
import random
import subprocess
import sys

from coloraide import Color
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from repalette import create_jar                                  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REL = 'src/main/resources/assets/create_transit/textures/item/package'
NAME = 'transit_cardboard'
PREVIEW = 'build/texture-preview'

# Create's shared cardboard atlas, and the models that carve it up.
SOURCE = 'item/package/cardboard'
SIZES = ((12, 12), (10, 12), (10, 8), (12, 10))

BODY_HUE, BODY_SAT, BODY_VAL = 118.0, 1.00, 0.82
GREY_FLOOR = 0.18            # below this a pixel has no hue worth rotating

PAPER_H, PAPER_C, PAPER_L = 86.0, 0.018, 0.860
PAPER_STEPS = (0.0, -0.025, -0.050, -0.080)
INK_L = (0.500, 0.455)
SEAL_L, SEAL_SPREAD, SEAL_C, SEAL_H = 0.520, 0.025, 0.135, 30.0

WEAR_AMOUNT, WEAR_STRENGTH = 0.15, 0.5
SEED = 20260731              # a fixed build is a comparable build

# Label size and its seat on the lid, per lid width.
BILL = {12: (9, 7, (2, 3)), 10: (7, 6, (2, 2))}

MAG, GRID = 18, (0, 0, 0, 90)   # magnified preview: scale, gridline colour


def ok(L, C, hue):
    c = Color('oklch', [L, C, hue]).convert('srgb').fit()
    return tuple(int(round(v * 255)) for v in c[:3]) + (255,)


PAPER = [ok(PAPER_L + d, PAPER_C, PAPER_H) for d in PAPER_STEPS]
INK = [ok(L, 0.030, PAPER_H) for L in INK_L]
SEAL = [ok(SEAL_L + SEAL_SPREAD, SEAL_C, SEAL_H),
        ok(SEAL_L, SEAL_C, SEAL_H),
        ok(SEAL_L - SEAL_SPREAD, SEAL_C, SEAL_H)]


def faces(jar, model):
    """Pixel rects per face for one of Create's package models."""
    data = json.loads(jar.read('assets/create/models/item/package/%s.json' % model))
    texture = list(data['textures'].values())[0].split(':')[1]
    im = Image.open(io.BytesIO(
        jar.read('assets/create/textures/%s.png' % texture))).convert('RGBA')
    scale = im.width / 16.0
    rects = {}
    for face, v in data['elements'][0]['faces'].items():
        x0, y0, x1, y1 = v['uv']
        rects[face] = tuple(int(round(c * scale)) for c in
                            (min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1)))
    return im, rects


def atlas(jar):
    """Create's cardboard atlas, as its own copy to draw on."""
    im = Image.open(io.BytesIO(
        jar.read('assets/create/textures/%s.png' % SOURCE))).convert('RGBA')
    return im.copy()


def recolour(canvas):
    """Set the hue of everything that has one. Create's printing stays grey."""
    px = canvas.load()
    for y in range(canvas.height):
        for x in range(canvas.width):
            c = px[x, y]
            if not c[3]:
                continue
            _, s, v = colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)
            if s < GREY_FLOOR:
                continue
            r, g, b = colorsys.hsv_to_rgb(BODY_HUE / 360.0, min(1, s * BODY_SAT),
                                          min(1, v * BODY_VAL))
            px[x, y] = (int(round(r * 255)), int(round(g * 255)),
                        int(round(b * 255)), 255)


def bill(w, h):
    """The waybill as a grid: ruled lines and a 3x2 seal on shaded paper.

    Built rather than typed out as rows of characters, because a row one
    character longer than its neighbours is a misprint nothing would catch. The
    paper is shaded along the diagonal, lightest at the corner the light comes
    from, so the sheet varies the way the cardboard around it does.
    """
    grid = [[PAPER[min(3, (x + y) * 4 // (w + h - 2))] for x in range(w)]
            for y in range(h)]
    seal_x, seal_y = w - 4, h - 3
    if h >= 7:
        for x in range(1, 5):
            grid[1][x] = INK[0]
        for x in range(1, 4):
            grid[3][x] = INK[1]
    else:
        for x in range(1, 4):
            grid[1][x] = INK[0]
    for dx, rung in enumerate((0, 0, 1)):
        grid[seal_y][seal_x + dx] = SEAL[rung]
    for dx, rung in enumerate((1, 2, 2)):
        grid[seal_y + 1][seal_x + dx] = SEAL[rung]
    return grid


def wear(grid):
    """Speckle the paper, and only the paper.

    Edges take twice the chance of the interior, because that is where a label
    rubs. Chroma barely moves: pushing a pale warm grey far in chroma makes a
    stain rather than a scuff, which is what an earlier pass at 2.2x looked
    like.
    """
    rng = random.Random(SEED)
    tones = set(PAPER)
    h, w = len(grid), len(grid[0])
    for y in range(h):
        for x in range(w):
            if grid[y][x] not in tones:
                continue
            edge = x in (0, w - 1) or y in (0, h - 1)
            if rng.random() > WEAR_AMOUNT * (2 if edge else 1):
                continue
            c = Color('srgb', [v / 255 for v in grid[y][x][:3]]).convert('oklch')
            dL = rng.choice([-0.030, -0.016, 0.010, 0.018]) * WEAR_STRENGTH
            dC = 1 + (rng.choice([1.0, 1.15, 1.35]) - 1) * WEAR_STRENGTH
            grid[y][x] = ok(c['lightness'] + dL, c['chroma'] * dC, PAPER_H)
    return grid


def build(jar=None):
    jar = jar or create_jar()
    canvas = atlas(jar)
    recolour(canvas)
    px = canvas.load()
    for w, h in SIZES:
        x0, y0, x1, _ = faces(jar, 'cardboard_%dx%d' % (w, h))[1]['up']
        bw, bh, at = BILL[x1 - x0]
        for dy, row in enumerate(wear(bill(bw, bh))):
            for dx, colour in enumerate(row):
                px[x0 + at[0] + dx, y0 + at[1] + dy] = colour
    return canvas


def columns(jar):
    """The atlas rect each size occupies, so a preview can show one box."""
    out = {}
    for w, h in SIZES:
        rects = faces(jar, 'cardboard_%dx%d' % (w, h))[1].values()
        out[(w, h)] = (min(r[0] for r in rects), min(r[1] for r in rects),
                       max(r[2] for r in rects), max(r[3] for r in rects))
    return out


def magnify(im):
    """Nearest-neighbour blow-up with a gridline on every texel boundary."""
    big = im.resize((im.width * MAG, im.height * MAG), Image.NEAREST)
    draw = ImageDraw.Draw(big)
    for x in range(0, im.width + 1):
        draw.line([(x * MAG, 0), (x * MAG, big.height)], fill=GRID)
    for y in range(0, im.height + 1):
        draw.line([(0, y * MAG), (big.width, y * MAG)], fill=GRID)
    return big


def head_version():
    raw = subprocess.run(['git', 'show', 'HEAD:%s/%s.png' % (REL, NAME)],
                         capture_output=True, cwd=REPO).stdout
    return Image.open(io.BytesIO(raw)).convert('RGBA') if raw else None


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument('--check', action='store_true',
                    help='report what would be built, write nothing')
    ap.add_argument('--verify', action='store_true',
                    help='fail if the rebuild differs from the committed texture')
    ap.add_argument('--preview', action='store_true',
                    help='write magnified grids of the atlas and each size')
    args = ap.parse_args()

    jar = create_jar()
    im = build(jar)

    if args.verify:
        head = head_version()
        if head is None:
            print('%s.png has no committed version to compare' % NAME)
            return 0
        same = head.tobytes() == im.tobytes()
        print('%s.png %s HEAD' % (NAME, 'matches' if same else 'DIFFERS from'))
        return 0 if same else 1

    if args.preview:
        out = os.path.join(REPO, *PREVIEW.split('/'))
        os.makedirs(out, exist_ok=True)
        magnify(im).save(os.path.join(out, '%s_atlas.png' % NAME))
        print(os.path.join(PREVIEW, '%s_atlas.png' % NAME))
        for (w, h), rect in columns(jar).items():
            path = os.path.join(PREVIEW, '%s_%dx%d.png' % (NAME, w, h))
            magnify(im.crop(rect)).save(os.path.join(REPO, *path.split('/')))
            print(path)
        return 0

    if args.check:
        opaque = [c for _, c in im.getcolors(maxcolors=1 << 16) if c[3]]
        print('%s.png %dx%d, %d colours'
              % (NAME, im.width, im.height, len(set(opaque))))
        for (w, h), rect in columns(jar).items():
            print('  %dx%d at %s, lid %d wide' % (w, h, rect,
                  faces(jar, 'cardboard_%dx%d' % (w, h))[1]['up'][2]
                  - faces(jar, 'cardboard_%dx%d' % (w, h))[1]['up'][0]))
        return 0

    out = os.path.join(REPO, *REL.split('/'))
    os.makedirs(out, exist_ok=True)
    im.save(os.path.join(out, NAME + '.png'))
    print('wrote', NAME + '.png')
    return 0


if __name__ == '__main__':
    sys.exit(main())
