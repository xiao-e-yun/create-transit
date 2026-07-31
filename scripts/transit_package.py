# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow", "coloraide"]
# ///
"""Build the transit package's texture from Create's cardboard.

Same discipline as repalette.py, and for the same reason: the source is the
pixels inside Create's jar rather than the file on disk, so a rebuild always
starts from the original and cannot accumulate strays from the last one. The
jar is found by the helper there too, which pins the exact Create the project
builds against.

    uv run scripts/transit_package.py            # rebuild
    uv run scripts/transit_package.py --check    # report only, touch nothing
    uv run scripts/transit_package.py --verify   # assert the rebuild matches HEAD

What it makes
-------------

A transit package is Create's 12x10 cardboard box, moved into the layout
`create:item/package/custom_12x10` expects -- the parent model the ten rare
boxes share -- then recoloured, with a waybill printed on the lid.

Create's own grammar for a different package is "same box, print something on
it": rare_simi.png is cardboard.png's palette exactly, plus the colours of the
design over it, and nine of the ten rare boxes keep the cardboard entirely. The
lid is kept clear in six of them and never carries a bordered shape. This
texture breaks both of those on purpose. A rare box is a prize and only has to
look good; this one has to be identifiable at a glance, from any angle, at
inventory-icon size, because it is how a player sees that goods are still
foreign. The reasoning is in issue #3.

Colour
------

Two spaces, each because it is what produced the approved artwork rather than
out of principle. The body is an HSV hue rotation, which is what the chosen
green was picked from. The label is built in OKLCH, which is what made it stop
looking like a sticker: picked by eye, its paper had landed at L 0.946, and
Create paints packages between L 0.575 and 0.795 with nothing above 0.86. Paper
now sits exactly on that ceiling -- the lightest thing on the box, and no
lighter than anything the game already puts on a package.
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
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from repalette import create_jar                                  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REL = 'src/main/resources/assets/create_transit/textures/item/package'
NAME = 'transit'

# Create's model this is drawn for, and the one its pixels come from.
LAYOUT, SOURCE = 'custom_12x10', 'cardboard_12x10'

BODY_HUE, BODY_SAT, BODY_VAL = 118.0, 1.00, 0.82
GREY_FLOOR = 0.18            # below this a pixel has no hue worth rotating

PAPER_H, PAPER_C, PAPER_L = 86.0, 0.018, 0.860
PAPER_STEPS = (0.0, -0.025, -0.050, -0.080)
INK_L = (0.500, 0.455)
SEAL_L, SEAL_SPREAD, SEAL_C, SEAL_H = 0.520, 0.025, 0.135, 30.0

WEAR_AMOUNT, WEAR_STRENGTH = 0.15, 0.5
SEED = 20260731              # a fixed build is a comparable build

W, H, AT = 9, 7, (2, 3)      # the label, and where it sits on the lid


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


def base(jar):
    """Create's 12x10 cardboard, rearranged into the layout model's UVs.

    North and south share one rect in both models, and both mirror it the same
    way, so copying the rect once serves both faces.
    """
    src, src_faces = faces(jar, SOURCE)
    dst_im, dst_faces = faces(jar, LAYOUT)
    canvas = Image.new('RGBA', dst_im.size, (0, 0, 0, 0))
    for face in ('east', 'north', 'west', 'up', 'down'):
        canvas.paste(src.crop(src_faces[face]), dst_faces[face][:2])
    return canvas, dst_faces


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


def bill():
    """The waybill as a grid: two ruled lines and a 3x2 seal on shaded paper.

    Built rather than typed out as rows of characters, because a row one
    character longer than its neighbours is a misprint nothing would catch. The
    paper is shaded along the diagonal, lightest at the corner the light comes
    from, so the sheet varies the way the cardboard around it does.
    """
    grid = [[PAPER[min(3, (x + y) * 4 // (W + H - 2))] for x in range(W)]
            for y in range(H)]
    for x in range(1, 5):
        grid[1][x] = INK[0]
    for x in range(1, 4):
        grid[3][x] = INK[1]
    for dx, rung in enumerate((0, 0, 1)):
        grid[4][5 + dx] = SEAL[rung]
    for dx, rung in enumerate((1, 2, 2)):
        grid[5][5 + dx] = SEAL[rung]
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
    for y in range(H):
        for x in range(W):
            if grid[y][x] not in tones:
                continue
            edge = x in (0, W - 1) or y in (0, H - 1)
            if rng.random() > WEAR_AMOUNT * (2 if edge else 1):
                continue
            c = Color('srgb', [v / 255 for v in grid[y][x][:3]]).convert('oklch')
            dL = rng.choice([-0.030, -0.016, 0.010, 0.018]) * WEAR_STRENGTH
            dC = 1 + (rng.choice([1.0, 1.15, 1.35]) - 1) * WEAR_STRENGTH
            grid[y][x] = ok(c['lightness'] + dL, c['chroma'] * dC, PAPER_H)
    return grid


def build():
    jar = create_jar()
    canvas, dst = base(jar)
    recolour(canvas)
    px = canvas.load()
    x0, y0 = dst['up'][:2]
    for dy, row in enumerate(wear(bill())):
        for dx, colour in enumerate(row):
            px[x0 + AT[0] + dx, y0 + AT[1] + dy] = colour
    return canvas


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
    args = ap.parse_args()

    im = build()

    if args.verify:
        head = head_version()
        if head is None:
            print('%s.png has no committed version to compare' % NAME)
            return 0
        same = head.tobytes() == im.tobytes()
        print('%s.png %s HEAD' % (NAME, 'matches' if same else 'DIFFERS from'))
        return 0 if same else 1

    if args.check:
        opaque = [c for _, c in im.getcolors(maxcolors=1 << 16) if c[3]]
        print('%s.png %dx%d, %d colours'
              % (NAME, im.width, im.height, len(set(opaque))))
        return 0

    out = os.path.join(REPO, *REL.split('/'))
    os.makedirs(out, exist_ok=True)
    im.save(os.path.join(out, NAME + '.png'))
    print('wrote', NAME + '.png')
    return 0


if __name__ == '__main__':
    sys.exit(main())
