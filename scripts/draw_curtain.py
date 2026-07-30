# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Draw the transit gate's strip curtain -- texture and hatch models together.

The curtain is the one part of the packager family with no Create original
behind it, so repalette.py has nothing to rebuild it from. It is also the part
where the texture and the geometry have to agree exactly: one texel per model
unit is the only scale at which a Minecraft texture keeps its pixel grid, so the
slat's width in units *is* its width in texels. Generating both from the same
constants is what stops them drifting apart.

Two numbers come from outside and are not taste:

  * The aperture. Create's packager_frame is transparent over x 2..14, y 3..13,
    with a narrower chamfer row above and below. That is the hole the curtain
    has to fill, and it is measured off the texture rather than guessed.
  * The renderer's offset. PackagerRenderer spins the hatch about the block
    centre and pushes it half a block along the opening, so authored z maps to
    final z as `8 - z`. FINAL_Z below is the position wanted in the world; the
    JSON gets the converted value.

    uv run scripts/draw_curtain.py
    uv run scripts/draw_curtain.py --slats 5 --out build/curtain-preview

Check the result with the offline renderer, which applies the same transform:

    uv run scripts/render_model.py --view opening \
        --with block/transit_gate/hatch_closed@packager_hatch \
        block/transit_gate/block
"""
import argparse
import io
import json
import os

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, 'src', 'main', 'resources', 'assets',
                      'create_transit')
TEX_DIR = os.path.join(ASSETS, 'textures', 'block')
MODEL_DIR = os.path.join(ASSETS, 'models', 'block', 'transit_gate')

# the recovered palette repalette.py documents: theme blue for the strips,
# brass for the rail and the fixings
BLUE_LIT, BLUE_MID = '42709D', '355B81'
BLUE_WORN, BLUE_FLOOR = '2B4B6B', '234261'
BRASS_LIT, BRASS, BRASS_DIM, BRASS_DARK = 'F7CB6C', 'E4B763', '9E6947', '724731'

# The shadow column is the ramp's darkest blue rather than the near-black it was
# first drawn with. Measured against Create's own iris -- the part the curtain
# replaces, and so the only honest calibration -- that near-black gave the
# curtain 33% of its area below the threshold where a colour stops reading as a
# colour, against the iris's 13%. In a render downscaled to a few hundred pixels
# it blurred into an edge and looked like shading; at the size a block actually
# occupies on screen it was a black stripe down every slat, and the curtain read
# as a hole rather than as something hanging in one.

# A slat is three units wide and lit from the left, so its cross section reads
# as a rounded rib rather than a flat card. That is also what lets the slats
# butt together and still look separate: the shadow column is the seam, so the
# curtain can cover the aperture edge to edge instead of showing daylight
# through gap geometry.
# Create's own flap art, and the reason the strips are not the theme colour:
# the flap region of brass_funnel and andesite_funnel is byte-identical. The
# casing states the material, the strip is always neutral rubber. Putting the
# accent here would be colouring the one part Create never colours -- and the
# blue is not lost by leaving it out, because it already carries the side
# atlases, which is where Create puts an accent too.
RIB = ('484848', '3B3B3B', '303030')
RIB_WORN = ('4B4B4B', '393939', '303030')

RIB_THEME = (BLUE_LIT, BLUE_MID, BLUE_FLOOR)
RIB_THEME_WORN = (BLUE_MID, BLUE_WORN, BLUE_FLOOR)

CAP = (BRASS_LIT, BRASS, BRASS_DIM)        # mounting, mostly hidden by the rail
HEM = (BRASS_DIM, BRASS_DARK, BRASS_DARK)  # a weighted bottom edge

# Ribs every third column, the same machined look as Create's brass casings at
# the one scale where that is a single pixel.
RAIL_UPPER = (BRASS_LIT, BRASS_DARK)
RAIL_LOWER = (BRASS, BRASS_DARK)
RAIL_EDGE = (BRASS_DIM, BRASS_DARK)

# The frame's hole is x 2..14 over y 3..13, chamfering in to x 3..13 for one row
# above and below.
#
# The curtain stops at y 4 rather than running to the bottom of the hole, and
# that floor is not the aperture's. Create's block has a cavity element spanning
# y 0..4 whose front face sits at z 0.9 -- inside the half unit the curtain hangs
# in -- so anything the slats put below y 4 is buried in it. The brass hem is the
# first thing to disappear that way, and a hem you cannot see is worse than no
# hem, because the texture still pays for it.
APERTURE_X = (2.0, 14.0)
APERTURE_Y = (4, 14)
SEAM = 0.1                                 # keeps butted slats off coplanar
SLAT_W = 3                                 # texels, and units
# Create's own tunnel flap is 3.05 x 1.0 units (belt_tunnel/flap.json). The
# width was already within 4% by coincidence; the depth was not, and depth is
# what makes a strip read as hanging rather than as printed on. Matching it is
# free -- the rail already spans 0.15..1.7, so a thicker curtain still sits
# inside it.
FINAL_Z = (0.4, 1.4)

# The rail is a real element again. It was one the first time, vanished into the
# frame brass directly above it, and got folded into the slat tops instead --
# which read as a curtain with no rail at all. What was missing was depth: at
# 0.15 it clears the block face and runs deeper than the slats, so it has a lit
# top, a shaded underside and a cast edge to be seen against, none of which a
# beam buried in the recess had. Clearing the face rather than sitting flush on
# it also keeps the two out of the same plane, which the game cannot order.
#
# Depth is what fixes the seam too. The slats hang back from the face, the game's
# projection is perspective and this project's renderer is orthographic, so an
# angled sightline that clears the slat tops and reaches the recess behind them
# shows up in game and cannot show up in a render. A rail spanning from near the
# face to behind the slats blocks that line geometrically, which does not depend
# on anyone noticing it.
#
# Full width, and the faces that end up inside the block are simply not drawn.
# A box cannot narrow partway up, so a rail fitted to the widest part of the hole
# pushes its ends behind the opaque chamfer across the top row -- but hidden
# geometry only costs anything if it is rendered, and omitting a face is how
# Create's own models handle exactly this (packager/block draws one element with
# nothing but an `up` face, another with no north or south at all). Shrinking the
# rail to dodge the chamfer was solving the wrong half of the problem: it cost
# the rail two units of width to avoid quads nobody can see.
#
# `up` is the one that goes. At y 14 it is the top of the aperture, with opaque
# frame above it and no sightline from outside that reaches it.
RAIL_X = APERTURE_X
RAIL_Y = (13, 14)
RAIL_FINAL_Z = (0.15, 1.7)

SWING = {4: [22.5, 45, 45, 22.5], 5: [0, 22.5, 45, 22.5, 0]}


def rgb(h):
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def draw(height, theme=False):
    """Two slat variants side by side, plus a column for the strip's thickness.

    The variants differ only in where the scuff sits. Alternating them across
    the curtain keeps identical strips from reading as a printed pattern, the
    same way Create's own repeated slats are broken up.
    """
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    rib, rib_worn = (RIB_THEME, RIB_THEME_WORN) if theme else (RIB, RIB_WORN)
    for variant, wear in enumerate(({3, 4}, {6, 7})):
        for row in range(height):
            if row == 0:
                cols = CAP
            elif row == height - 1:
                cols = HEM
            elif row in wear:
                cols = rib_worn
            else:
                cols = rib
            for i, c in enumerate(cols):
                px[variant * SLAT_W + i, row] = rgb(c)
    for row in range(height):
        px[SLAT_W * 2, row] = rgb(
            BRASS if row == 0 else BRASS_DARK if row == height - 1 else BLUE_FLOOR)

    # The rail, below the slat rows: two rows of face, one of top and underside,
    # and a column for the end caps.
    rail_w, rail_h = int(RAIL_X[1] - RAIL_X[0]), int(RAIL_Y[1] - RAIL_Y[0])
    for x in range(rail_w):
        rib = x % 3 == 0
        for row in range(rail_h):
            face = RAIL_UPPER if row == 0 else RAIL_LOWER
            px[x, height + row] = rgb(face[rib])
        px[x, height + rail_h] = rgb(RAIL_EDGE[rib])     # top and underside
    for row in range(rail_h):
        px[rail_w, height + row] = rgb(RAIL_EDGE[row > 0])
    return im


def elements(slats, height):
    """One element per slat, butted across the aperture with a hairline seam."""
    x0, x1 = APERTURE_X
    span = (x1 - x0 - SEAM * (slats - 1)) / slats
    lo, hi = round(8 - FINAL_Z[1], 4), round(8 - FINAL_Z[0], 4)
    top, bottom = APERTURE_Y[1], APERTURE_Y[1] - height
    pivot = round((lo + hi) / 2, 4)
    out = []
    for i in range(slats):
        left = round(x0 + i * (span + SEAM), 4)
        right = round(left + span, 4)
        u = (i % 2) * SLAT_W
        el = {'name': 'flap_%d' % i,
              'from': [left, bottom, lo], 'to': [right, top, hi]}
        angle = SWING[slats][i]
        if angle:
            el['rotation'] = {'angle': angle, 'axis': 'x',
                              'origin': [round((left + right) / 2, 4), top, pivot]}
        el['faces'] = {
            'south': {'uv': [u, 0, u + SLAT_W, height], 'texture': '#3'},
            'north': {'uv': [u + SLAT_W, 0, u, height], 'texture': '#3'},
            'east': {'uv': [SLAT_W * 2, 0, SLAT_W * 2 + 1, height],
                     'texture': '#3'},
            'west': {'uv': [SLAT_W * 2, 0, SLAT_W * 2 + 1, height],
                     'texture': '#3'},
            'down': {'uv': [SLAT_W * 2, height - 1, SLAT_W * 2 + 1, height],
                     'texture': '#3'},
        }
        out.append(el)
    out.append(rail(height))
    return out


def rail(height):
    """The beam the curtain hangs from, flush with the face and deeper than it.

    Living in the hatch model rather than the block model is not a detail: the
    two carry different transforms, and putting the rail in the block model is
    exactly how it once ended up eight units away from the slats it holds.
    """
    lo, hi = round(8 - RAIL_FINAL_Z[1], 4), round(8 - RAIL_FINAL_Z[0], 4)
    w, h = int(RAIL_X[1] - RAIL_X[0]), int(RAIL_Y[1] - RAIL_Y[0])
    top = height + h
    return {
        'name': 'flap_rail',
        'from': [RAIL_X[0], RAIL_Y[0], lo],
        'to': [RAIL_X[1], RAIL_Y[1], hi],
        'faces': {
            'south': {'uv': [0, height, w, top], 'texture': '#3'},
            'north': {'uv': [w, height, 0, top], 'texture': '#3'},
            'down': {'uv': [0, top, w, top + 1], 'texture': '#3'},
            'east': {'uv': [w, height, w + 1, top], 'texture': '#3'},
            'west': {'uv': [w + 1, height, w, top], 'texture': '#3'},
        },
    }


def dump(node, depth, out):
    """json.dump with coordinate arrays inline and tabs, as Blockbench writes."""
    pad, inner = '\t' * depth, '\t' * (depth + 1)
    if isinstance(node, dict):
        out.write('{\n')
        for i, (k, v) in enumerate(node.items()):
            out.write('%s%s: ' % (inner, json.dumps(k)))
            dump(v, depth + 1, out)
            out.write(',\n' if i < len(node) - 1 else '\n')
        out.write('%s}' % pad)
    elif isinstance(node, list):
        if all(isinstance(v, (int, float)) for v in node):
            out.write('[%s]' % ', '.join(
                repr(int(v)) if float(v).is_integer() else repr(v) for v in node))
            return
        out.write('[\n')
        for i, v in enumerate(node):
            out.write(inner)
            dump(v, depth + 1, out)
            out.write(',\n' if i < len(node) - 1 else '\n')
        out.write('%s]' % pad)
    else:
        out.write(json.dumps(node))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--slats', type=int, default=4, choices=sorted(SWING),
                    help='how many strips span the aperture (default: '
                         '%(default)s, which puts each one at three units)')
    ap.add_argument('--theme-flaps', action='store_true',
                    help='colour the strips theme blue instead of rubber, '
                         'trading fit with Create for a louder accent')
    ap.add_argument('--out', help='write texture and models under here instead '
                                  'of into the mod, to preview a variant')
    args = ap.parse_args()

    height = APERTURE_Y[1] - APERTURE_Y[0]
    tex_dir = os.path.join(args.out, 'textures') if args.out else TEX_DIR
    model_dir = os.path.join(args.out, 'models') if args.out else MODEL_DIR
    os.makedirs(tex_dir, exist_ok=True)
    os.makedirs(model_dir, exist_ok=True)

    draw(height, args.theme_flaps).save(os.path.join(tex_dir, 'transit_gate_flaps.png'))
    print('transit_gate_flaps.png  %d slats of %g x %d units'
          % (args.slats,
             (APERTURE_X[1] - APERTURE_X[0] - SEAM * (args.slats - 1))
             / args.slats, height))

    els = elements(args.slats, height)
    for name, open_ in (('hatch_closed', False), ('hatch_open', True)):
        model = {
            'textures': {'3': 'create_transit:block/transit_gate_flaps',
                         'particle': 'create_transit:block/transit_gate_particle'},
            'elements': [e if open_ else {k: v for k, v in e.items()
                                          if k != 'rotation'} for e in els],
        }
        buf = io.StringIO()
        dump(model, 0, buf)
        with open(os.path.join(model_dir, name + '.json'), 'w',
                  encoding='utf-8') as fh:
            fh.write(buf.getvalue() + '\n')
        print(name + '.json')


if __name__ == '__main__':
    main()
