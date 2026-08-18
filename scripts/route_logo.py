# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Build Create: Routes' mod-list logo -- Create's Schedule, stamped with a fork.

Routes registers no block and no item, so it owns no art to photograph the way
transit/logo.png photographs its own package. What it does own is a meaning:
one run of stops that other schedules branch off. So the logo is Create's own
Schedule, which is the thing a route lives inside, with that branch drawn over
it in the colours the route editor's map already uses -- rose for a trail, blue
and white for a station marker.

Create's Schedule is read out of the pinned jar rather than copied into this
repo, for the same reason repalette.py and transit_package.py do it: a rebuild
starts from the original every time and cannot accumulate strays from the last
one. It also keeps Transit's logo apart from this one for free, since Transit's
green timetable is a recolour of this same sprite.

    uv run scripts/route_logo.py            # rebuild
    uv run scripts/route_logo.py --check    # report only, touch nothing

Three numbers are not taste:

  * The canvas is 16, the sprite's own size, and the zoom is a whole 32. A
    fractional zoom turns pixel art into mush, so the output size follows from
    the canvas rather than the other way round -- it does not have to match
    Transit's 400, and Forge scales a mod-list logo anyway.
  * The fork is drawn at 1:1 with the sprite and centred on the marks it inks,
    not on the empty frame around them.
  * The shadow is 180, not 255. Black lands on a sprite that is already dark
    slate, and at full opacity it stops reading as shade and starts reading as
    a hole punched in the page. It is masked to the sprite too -- a shadow
    falling on nothing is just a smudge.
"""
import io
import os
import sys

from PIL import Image, ImageDraw

SCRIPTS = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(SCRIPTS)
sys.path.insert(0, SCRIPTS)

from repalette import create_jar                                  # noqa: E402

SOURCE = 'assets/create/textures/item/schedule.png'
TARGET = os.path.join(REPO, 'route', 'src', 'main', 'resources', 'logo.png')

G, Z = 16, 32                       # canvas, zoom -- 512x512 out
SHADE, DROP = 180, 1                # shadow opacity and offset

ROSE = (209, 86, 123, 255)          # the map's track trails
BLUE = (76, 140, 255, 255)          # its station markers
WHITE = (252, 252, 252, 255)

BOX = 18                            # the glyph's own frame
TRUNK_X, TOP, BOTTOM = 6, 4, 13
BRANCH_X, BRANCH_Y, JOIN = 12, 6, 10
NODES = [(TRUNK_X, TOP), (TRUNK_X, BOTTOM - 1), (BRANCH_X, BRANCH_Y)]
INK = (TRUNK_X - 1, TOP - 1, BRANCH_X + 2, BOTTOM + 1)


def schedule():
    """Create's Schedule item texture, out of the Create the project pins."""
    with create_jar() as jar:
        return Image.open(io.BytesIO(jar.read(SOURCE))).convert('RGBA')


def fork():
    """One route branching off another, as the editor's map draws it."""
    im = Image.new('RGBA', (BOX, BOX), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)

    d.rectangle([TRUNK_X, TOP, TRUNK_X + 1, BOTTOM], fill=ROSE)
    d.rectangle([BRANCH_X, BRANCH_Y, BRANCH_X + 1, JOIN + 1], fill=ROSE)
    d.rectangle([TRUNK_X, JOIN, BRANCH_X + 1, JOIN + 1], fill=ROSE)

    # Nodes last, so a stroke never crosses one.
    for x, y in NODES:
        d.rectangle([x - 1, y - 1, x + 2, y + 2], fill=BLUE)
        d.rectangle([x, y, x + 1, y + 1], fill=WHITE)
    return im


def build():
    sprite = schedule().resize((G, G), Image.NEAREST)
    logo = Image.new('RGBA', (G, G), (0, 0, 0, 0))
    logo.alpha_composite(sprite)

    at = ((G - (INK[2] - INK[0] + 1)) // 2 - INK[0],
          (G - (INK[3] - INK[1] + 1)) // 2 - INK[1])
    glyph = fork()

    cast = Image.new('RGBA', (G, G), (0, 0, 0, 0))
    cast.alpha_composite(glyph, (at[0] + DROP, at[1] + DROP))
    alpha = cast.getchannel('A').point(lambda v: v * SHADE // 255)
    alpha = Image.composite(alpha, Image.new('L', (G, G)), sprite.getchannel('A'))
    shadow = Image.new('RGBA', (G, G), (0, 0, 0, 255))
    shadow.putalpha(alpha)
    logo.alpha_composite(shadow)

    logo.alpha_composite(glyph, at)
    return logo.resize((G * Z, G * Z), Image.NEAREST)


def main(argv):
    check = '--check' in argv
    logo = build()

    buffer = io.BytesIO()
    logo.save(buffer, format='PNG')
    fresh = buffer.getvalue()

    current = None
    if os.path.exists(TARGET):
        with open(TARGET, 'rb') as fh:
            current = fh.read()

    rel = os.path.relpath(TARGET, REPO).replace(os.sep, '/')
    if current == fresh:
        print('up to date:', rel, logo.size)
        return 0
    if check:
        print('stale:', rel, '-- rerun without --check')
        return 1

    with open(TARGET, 'wb') as fh:
        fh.write(fresh)
    print('wrote:', rel, logo.size)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
