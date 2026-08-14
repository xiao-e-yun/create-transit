# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Draw the transit gate's strip curtain -- texture and models together.

The curtain is the one part of the packager family with no Create original
behind it, so repalette.py has nothing to rebuild it from. It is also the part
where the texture and the geometry have to agree exactly: one texel per model
unit is the only scale at which a Minecraft texture keeps its pixel grid, so the
slat's width in units *is* its width in texels. Generating both from the same
constants is what stops them drifting apart.

Three models come out, and the split is the animation's:

  * `flap` and `flap_alt`, one strip each. The strips swing, so each one needs
    its own matrix every frame, and a matrix applies to a whole model -- four
    strips in one model can only ever hold one pose. They are authored at the
    leftmost position and TransitGateCurtain steps the rest sideways, the same
    arrangement Create's tunnel flaps use (one BELT_TUNNEL_FLAP, four instances,
    SEGMENT_STEP between them).
  * `rail`, which does not move, and so stays one static model. It is what the
    gate hands back in place of the packager's iris.

Two numbers come from outside and are not taste:

  * The aperture. Create's packager_frame is transparent over x 2..14, y 3..13,
    with a narrower chamfer row above and below. That is the hole the curtain
    has to fill, and it is measured off the texture rather than guessed.
  * The renderer's offset. PackagerRenderer spins the hatch about the block
    centre and pushes it half a block along the opening, so authored z maps to
    final z as `8 - z`. FINAL_Z below is the position wanted in the world; the
    JSON gets the converted value.

The strip pitch lives here *and* in TransitGateCurtain.PITCH, because one side
lays the art out and the other steps the instances. Nothing enforces the two
agreeing directly -- what catches it is that scripts/scenes.json renders the
authored strip with the matrices dumped out of the Java, so a disagreement shows
up as strips overlapping or gapping in a fixture.

    uv run scripts/draw_curtain.py
    uv run scripts/draw_curtain.py --theme-flaps --out build/curtain-preview
"""
import argparse
import io
import json
import math
import os

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, 'transit', 'src', 'main', 'resources', 'assets',
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

# A weighted bottom edge, and the curtain's only brass. There was a matching
# mounting strip across the top, and it was the wrong idea twice over: the rail
# reaches down to the aperture now, so the top of a slat is behind steel rather
# than exposed, and brass at both ends made the strips read as printed cards with
# a border rather than as rubber hanging from a beam.
HEM = (BRASS_DIM, BRASS_DARK, BRASS_DARK)

# The rail is the block's own interior steel rather than brass, so it reads as
# part of the shell the curtain is mounted inside rather than as a fitting bolted
# across the opening. These three tones are the entire interior palette, sampled
# from the interior tile of transit_gate_frame (uv 8,8..16,16, which is what
# Create's frame_interior element draws with).
STEEL_LIT, STEEL, STEEL_DARK = '414844', '303A38', '252F2D'

# Ribs every third column, the same machined look as Create's own casings at the
# one scale where that is a single pixel.
RAIL_UPPER = (STEEL_LIT, STEEL_DARK)
RAIL_LOWER = (STEEL, STEEL_DARK)
RAIL_EDGE = (STEEL, STEEL_DARK)

# The frame's hole is x 2..14 over y 3..13, chamfering in to x 3..13 for one row
# above and below. That is the width the curtain has to fill, and it is measured
# off the texture rather than guessed.
#
# The slats' own span is neither end of that hole. The top runs one unit past it,
# to y 15, so it is inside the rail rather than merely under it: an overlap cannot
# open a gap, and the unit that gets hidden behind opaque frame on the way is a
# unit of texture, not of anything anyone can see.
#
# The floor is not the aperture's either, and not the cavity's. Create's block has
# a cavity element spanning y 0..4 whose front face sits at z 0.9 -- inside the
# half unit the curtain hangs in -- so anything the slats put below y 4 is buried
# in it, the brass hem first. y 4 is therefore the lowest a hem can be drawn at,
# and the curtain stops half a unit above that: a strip curtain that meets the
# floor reads as a wall, and the gap is what says the strips are free to swing. It
# also gives the hem an underside to be seen from below, which a hem resting on
# the cavity does not have.
#
# Know what that gap is: a hole. Nothing in Create's block blocks a level
# sightline at that height -- element 4 is a zero-thickness plane at y 4 with only
# an `up` face, and the cavity starts below it -- so the world behind the gate
# shows through the slit, deliberately, at half a unit rather than a full one.
#
# Half a unit also means the span is 10.5, and one texel has to stay one unit or
# the art loses its pixel grid. So the slats sample v 0.5..11 of an 11-row strip
# rather than 0..10.5: the half texel that gets dropped is at the top, where the
# frame is opaque and nothing was visible anyway, which leaves the hem whole.
APERTURE_X = (2.0, 14.0)
SLAT_Y = (4.5, 15)
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
# which read as a curtain with no rail at all. What was missing was depth: it
# runs deeper than the slats, so it has a shaded underside and a cast edge to be
# seen against, neither of which a beam buried in the recess had.
#
# Depth is what fixes the seam too. The slats hang back from the face, the game's
# projection is perspective and this project's renderer is orthographic, so an
# angled sightline that clears the slat tops and reaches the recess behind them
# shows up in game and cannot show up in a render. A rail spanning from the face
# to behind the slats blocks that line geometrically, which does not depend on
# anyone noticing it.
#
# The rail is jammed into the block's top north corner, flush on both: its top
# is the block's own top face and its front is the block's own north face. Being
# flush is what makes a seam impossible rather than merely small.
#
# It comes down to y 14, the top of the aperture, which is what gives it a
# visible edge. The upper unit is behind opaque frame; the lower one puts its
# underside on the line where the hole begins, so it caps the curtain from any
# sightline angled up through the opening, and it is the surface the slat tops
# disappear behind rather than a beam floating somewhere above them.
#
# Full block width, not just the aperture's. A beam that stops at the hole has
# ends, and ends are a detail to get wrong; one that runs wall to wall is a piece
# of the shell. It costs nothing either, since the two units at each side are
# behind the frame's corner brass along with everything else at this height.
#
# So four of the six faces are not drawn: `up` at y 16, the front at z 0, and
# `east` and `west` at x 0 and x 16 are each in the same plane as a face of the
# block. Two coplanar quads are the one arrangement the depth buffer cannot
# order, so at distance it is the block itself that flickers. Omitting them is
# not an optimisation, it is the fix, and it is how Create's own models handle
# exactly this (packager/block draws one element with nothing but an `up` face,
# another with no north or south at all). Nothing is lost: every one of the four
# sits behind opaque frame at this height. What is left to see is the underside.
RAIL_X = (0.0, 16.0)
RAIL_Y = (14, 16)
RAIL_FINAL_Z = (0.0, 1.7)

# How many strips span the aperture. This has to match TransitGateCurtain.STRIPS,
# which is what actually places them; the swing angles used to live here too, as a
# baked-in pose per strip, and have moved to the Java entirely -- there is no
# second copy of them to drift.
STRIPS = 4


def rgb(h):
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def draw(rows, theme=False):
    """Two slat variants side by side, plus a column for the strip's thickness.

    The variants differ only in where the scuff sits. Alternating them across
    the curtain keeps identical strips from reading as a printed pattern, the
    same way Create's own repeated slats are broken up.
    """
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    rib, rib_worn = (RIB_THEME, RIB_THEME_WORN) if theme else (RIB, RIB_WORN)
    for variant, wear in enumerate(({3, 4}, {6, 7})):
        for row in range(rows):
            if row == rows - 1:
                cols = HEM
            elif row in wear:
                cols = rib_worn
            else:
                cols = rib
            for i, c in enumerate(cols):
                px[variant * SLAT_W + i, row] = rgb(c)
    for row in range(rows):
        px[SLAT_W * 2, row] = rgb(
            # the strip's own thickness, so it has to follow whichever ramp the
            # front is drawn in -- left hardcoded it stayed theme blue after the
            # face went rubber, giving every strip blue sides
            BRASS_DARK if row == rows - 1 else rib[2])

    # The rail, below the slat rows: one row per unit of its height, then one for
    # the underside. No end caps -- it runs the full width of the block, so the
    # faces that would need them are not drawn.
    rail_w, rail_h = int(RAIL_X[1] - RAIL_X[0]), int(RAIL_Y[1] - RAIL_Y[0])
    for x in range(rail_w):
        rib = x % 3 == 0
        for row in range(rail_h):
            face = RAIL_UPPER if row == 0 else RAIL_LOWER
            px[x, rows + row] = rgb(face[rib])
        px[x, rows + rail_h] = rgb(RAIL_EDGE[rib])       # underside
    return im


def strip_width(strips):
    """A strip's own width: the aperture shared out, less the seams between."""
    return (APERTURE_X[1] - APERTURE_X[0] - SEAM * (strips - 1)) / strips


def flap(rows, variant, strips=STRIPS):
    """One strip, authored at the leftmost position in the aperture.

    Carries no `rotation`: the swing is a matrix the renderer builds per frame,
    and the pivot it rotates about is the top edge of this box, which
    TransitGateCurtain has to agree with. `rows` is how many texel rows the art
    occupies -- the strip is 10.5 units tall and samples the lower 10.5 of them,
    see SLAT_Y.
    """
    each = strip_width(strips)
    lo, hi = round(8 - FINAL_Z[1], 4), round(8 - FINAL_Z[0], 4)
    bottom, top = SLAT_Y
    v0 = round(rows - (top - bottom), 4)
    u = variant * SLAT_W
    return {
        'name': 'flap',
        'from': [APERTURE_X[0], bottom, lo],
        'to': [round(APERTURE_X[0] + each, 4), top, hi],
        'faces': {
            'south': {'uv': [u, v0, u + SLAT_W, rows], 'texture': '#3'},
            'north': {'uv': [u + SLAT_W, v0, u, rows], 'texture': '#3'},
            'east': {'uv': [SLAT_W * 2, v0, SLAT_W * 2 + 1, rows],
                     'texture': '#3'},
            'west': {'uv': [SLAT_W * 2, v0, SLAT_W * 2 + 1, rows],
                     'texture': '#3'},
            'down': {'uv': [SLAT_W * 2, rows - 1, SLAT_W * 2 + 1, rows],
                     'texture': '#3'},
        },
    }


def rail(rows):
    """The beam the curtain hangs from, jammed into the block's top north corner.

    Living in the hatch model rather than the block model is not a detail: the
    two carry different transforms, and putting the rail in the block model is
    exactly how it once ended up eight units away from the slats it holds.

    Only the underside and the deep face are drawn -- see RAIL_Y above. `south` is
    the one facing the world's north, not the back: the renderer flips the hatch
    180 degrees about Y, so the authored +z face ends up at the front.
    """
    lo, hi = round(8 - RAIL_FINAL_Z[1], 4), round(8 - RAIL_FINAL_Z[0], 4)
    w, h = int(RAIL_X[1] - RAIL_X[0]), int(RAIL_Y[1] - RAIL_Y[0])
    top = rows + h
    return {
        'name': 'flap_rail',
        'from': [RAIL_X[0], RAIL_Y[0], lo],
        'to': [RAIL_X[1], RAIL_Y[1], hi],
        'faces': {
            'north': {'uv': [w, rows, 0, top], 'texture': '#3'},
            'down': {'uv': [0, top, w, top + 1], 'texture': '#3'},
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


def write(model_dir, name, element):
    model = {
        'textures': {'3': 'create_transit:block/transit_gate_flaps',
                     'particle': 'create_transit:block/transit_gate_particle'},
        'elements': [element],
    }
    buf = io.StringIO()
    dump(model, 0, buf)
    with open(os.path.join(model_dir, name + '.json'), 'w',
              encoding='utf-8') as fh:
        fh.write(buf.getvalue() + '\n')
    print('  ' + name + '.json')


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--strips', type=int, default=STRIPS, metavar='N',
                    help='how many strips span the aperture (default: '
                         '%(default)s). Only the strip width changes here -- '
                         'TransitGateCurtain.STRIPS places them, so a value it '
                         'disagrees with shows up as a gap in the fixtures')
    ap.add_argument('--theme-flaps', action='store_true',
                    help='colour the strips theme blue instead of rubber, '
                         'trading fit with Create for a louder accent')
    ap.add_argument('--out', help='write texture and models under here instead '
                                  'of into the mod, to preview a variant')
    args = ap.parse_args()

    # A slat is 10.5 units tall and one texel is one unit, so the art needs 11
    # rows and the geometry samples the lower 10.5 of them.
    span = SLAT_Y[1] - SLAT_Y[0]
    rows = int(math.ceil(span))
    tex_dir = os.path.join(args.out, 'textures') if args.out else TEX_DIR
    model_dir = os.path.join(args.out, 'models') if args.out else MODEL_DIR
    os.makedirs(tex_dir, exist_ok=True)
    os.makedirs(model_dir, exist_ok=True)

    draw(rows, args.theme_flaps).save(os.path.join(tex_dir, 'transit_gate_flaps.png'))
    print('transit_gate_flaps.png  %d strips of %g x %g units, pitch %g'
          % (args.strips, strip_width(args.strips), span,
             strip_width(args.strips) + SEAM))

    write(model_dir, 'flap', flap(rows, 0, args.strips))
    write(model_dir, 'flap_alt', flap(rows, 1, args.strips))
    write(model_dir, 'rail', rail(rows))


if __name__ == '__main__':
    main()
