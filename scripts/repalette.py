# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Rebuild the mod's derived textures from Create's originals in one pass.

Every packager-family texture here is a recolour of a Create texture. Editing
them by layering one colour map over the last is how strays accumulate: a map
only rewrites the exact values it is given, so any shade a previous pass
introduced survives untouched and unnoticed. So the source of truth is Create's
jar, not the file on disk -- each run starts from the original pixels, and a
source colour with no entry in the palette is a hard error rather than a silent
passthrough.

The palette below is not a fresh design. It was recovered by diffing the
committed artwork against Create's originals pixel by pixel, and it came out a
clean function over 42 colours with no conflicts, which means the original
recolour was systematic and is reproducible exactly. Its rule is two lines:

    zinc  -> brass          the frame, rails and hinges
    wood  -> THEME          Create's logistics accent, restyled
    everything else kept    neutrals, navy, and every red

Red staying put is deliberate and predates this script: red means powered
across all of Create, and that vocabulary is worth more than palette purity.
The postbox flag keeps its blue for the same kind of reason -- see SIGNALS.

`--canon` drops the wood rule instead of applying it, which returns the accent
to Create's own logistics wood. That is the whole difference between the mod's
look and a fully canon one -- THEME occupies the slot Create fills with wood.

    uv run scripts/repalette.py            # rebuild
    uv run scripts/repalette.py --check    # report only, touch nothing

A second, smaller group is handled too: HAND_LAID, the sheets drawn for this
mod that carry the accent but recolour nothing. They cannot follow the rule
above because they have no Create original, so their original is the committed
art and the only thing done to them is moving the accent. Without that they
would be hand-edited every time the theme moved, which is the exact thing the
paragraph above says never to do -- and a shade of accent the table has never
seen is a hard error there too, for the same reason.

Truly hand-drawn work with no accent in it at all -- the gate's flaps, the
postboxes -- is not managed here either way.
"""
import argparse
import colorsys
import io
import os
import subprocess
import sys
import zipfile

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REL = 'transit/src/main/resources/assets/create_transit/textures/block'
OUT = os.path.join(REPO, *REL.split('/'))


def rgb(h):
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


# zinc casing -> brass mechanism
ZINC_TO_BRASS = {rgb(a): rgb(b) for a, b in [
    ('BCB6AE', 'FFEB8C'), ('9F9B93', 'F7CB6C'), ('848277', 'F7CB6C'),
    ('828784', 'E4B763'), ('707570', 'E4B763'), ('696962', 'E4B763'),
    ('535751', 'CEA05A'), ('414844', '9E6947'), ('303A38', '724731'),
    # The three the original recolour missed. Every texel of these in the whole
    # palette is in packager_details, and every one of those is on the two rails
    # under the tray -- which came out half brass and half zinc, alternating
    # texel by texel with shades a rung either side that did convert. That reads
    # as an unfinished job rather than as steel parts left steel, so they follow
    # their neighbours: these two rungs are where luminance puts them.
    ('797E7B', 'E4B763'), ('696E69', 'E4B763'), ('5A5D5A', 'CEA05A'),
    # the frame sheet and the iris reach a rung darker than the side atlases
    # ever did, so these two have no counterpart in the recovered mapping
    ('252F2D', '724731'), ('2B2B2E', '724731'),
]}

# Create's logistics wood, brightest rung first.
WOOD = ['886539', '82613A', '7A5A34', '70522E', '614B2E', '5A4424', '553A1F']

# The accent as it stood when it was a steel blue: seven rungs, one per rung of
# wood. Kept because the hand-laid sheets below have no Create original to
# rebuild from -- the committed art is their original, so moving their accent
# needs to know where it is moving from.
PREVIOUS_THEME = ['42709D', '426B94', '3C648C', '355B81', '35526F', '2B4B6B',
                  '234261']

# Every shade the mod draws its accent with, and where each one lands: on
# Railway Casing's own dark ramp.
#
# Trains are the one long-distance carrier the game has, and distance is the
# argument this whole mod exists to make, so the blocks say it in the only
# vocabulary Create has for it. Half of that was already true and nobody had
# noticed -- ZINC_TO_BRASS targets Brass Casing's ramp, and Railway Casing's
# metal is five sixths the same six colours, so the frame has been wearing
# train brass all along. This puts the accent in the same livery.
#
# The casing runs darker than the wood it stands in for, 73 down to 25 in
# luminance against wood's 106 down to 63. That is the cost of the change, not
# an oversight in it: Create shades the casing dark on purpose, and a ramp
# lifted into the wood's band would be a colour that resembles the casing
# instead of being it.
#
# Twenty-three entries rather than seven because the accent is only seven rungs
# in the textures rebuilt from Create. The link sheets were drawn for this mod
# and shaded by hand across twenty-one, of which the seven are a subset -- so a
# seven-entry map recoloured a third of a link and left the rest steel blue.
# The table is generated, not designed: each shade keeps its place in the ramp,
# its brightness rescaled from the accent's range into the casing's.
ACCENT = dict([
    ('42709D', '444B4C'), ('416F9C', '434A4B'), ('426B94', '3A4043'),
    ('416A93', '3A4043'), ('3C648C', '363C3F'), ('3B638B', '353B3E'),
    ('3D6287', '33393C'), ('355B81', '303639'), ('2F5376', '2C3035'),
    ('2D5073', '2B2F34'), ('35526F', '2A2E33'), ('284B6F', '2A2E33'),
    ('2A4B6D', '2A2E33'), ('2B4B6B', '292D32'), ('294969', '282C31'),
    ('294968', '282C31'), ('254463', '262A2F'), ('244463', '262A2F'),
    ('234261', '25292E'), ('203F5D', '24272C'), ('1E3955', '1F2227'),
    ('1A354F', '1C1E22'), ('163049', '17191C'),
])

# What an accent shade looks like, so that one the table has never seen is a
# hard error instead of a patch of the old colour left behind. This is exactly
# how the seven-entry version got caught.
ACCENT_HUE = (195, 225)
ACCENT_SATURATION = 0.45

THEME = [ACCENT[c] for c in PREVIOUS_THEME]
WOOD_TO_THEME = {rgb(a): rgb(b) for a, b in zip(WOOD, THEME)}
RETHEME = {rgb(a): rgb(b) for a, b in ACCENT.items()}

# left exactly as Create drew them: the neutral greys of the tray, arm and
# rails, the navy of the interior floor, and every red
KEEP = [rgb(h) for h in [
    '676161', '595858', '4F4F4F', '444444',
    '44485A', '414150', '3F3E42', '3E3D41', '3D3C48', '35343D', '323236',
    '2B2B31', '2B2B2E', '252F2D',
    '4B5E71', '495669', '1E1827', '120F1B',
    'CD0000', 'AE0000', '970000', '660000', '580101', '500101', '460000',
    '3B0000', '370000',
]]


def palette(canon):
    table = {c: c for c in KEEP}
    table.update(ZINC_TO_BRASS)
    if canon:
        table.update({c: c for c in WOOD_TO_THEME})
    else:
        table.update(WOOD_TO_THEME)
    return table


def as_drawn(canon):
    """Leave Create's colours alone -- for the parts that are already right."""
    table = palette(canon)
    return {c: c for c in table}


# The frame sheet is the one file where the same zinc means two things. Its top
# half is the block's outward faces, its bottom half the cavity you see through
# the opening, and Create shades the cavity dark on purpose. Recolouring that
# half as if it were frame turns the inside of the block brass, which reads as a
# solid brown underside rather than a hole. Splitting on the sheet's own layout
# is not a guess about semantics -- it is the layout.
CAVITY_HALF = (0, 16, 32, 32)

# output name -> (Create source, [(region or None, palette builder)])
OUTPUTS = {
    'packager_horizontal_unpowered': ('packager_horizontal_unpowered', None),
    'packager_horizontal_powered': ('packager_horizontal_powered', None),
    'packager_vertical_unpowered': ('packager_vertical_unpowered', None),
    'packager_vertical_powered': ('packager_vertical_powered', None),
    'packager_details': ('packager_details', None),
    'transit_gate_frame': ('packager_frame', CAVITY_HALF),
    'transit_repackager_frame': ('packager_frame', CAVITY_HALF),
    'transit_iris_closed': ('packager_iris_closed', None),
    'transit_gate_particle': ('packager_particle', None),
    'transit_repackager_particle': ('repackager_particle', None),
}

# Textures that carry the accent but are not recolours of anything.
#
# The link sheets and the ticker were laid out for this mod -- the link's four
# models share one 32x32 sheet where Create's stock link has four 16x16 ones --
# so no Create texture reproduces them and none can be their source. That left
# 1074 accent texels outside this script's reach, which is fine right up until
# the theme changes and five files have to be hand-edited: the one thing the
# docstring above says never to do.
#
# So their original is the committed art, and the only thing done to them is
# RETHEME -- a total, exact map over the seven accent rungs, which touches no
# other colour and cannot invent one. Anything left holding a PREVIOUS_THEME
# colour afterwards is a hard error, the same way an unmapped source colour is.
HAND_LAID = [
    'link_base_powered',
    'link_base_unpowered',
    'link_details',
    'transit_frogport_base',
    'transit_ticker',
]

# Accent-coloured art that keeps its blue on purpose, and is therefore not a
# straggler.
#
# The postbox flag is a signal, not decoration: four texels wide, raised only
# when the box has mail, and read at whatever distance the player happens to
# be standing. On the casing ramp it came out the same shade as the pole it
# hangs from -- a flag that no longer says anything, which costs more than the
# livery gains. Same trade as the red above.
SIGNALS = ['transit_postbox_flag']


def pinned_create_version():
    path = os.path.join(REPO, 'gradle.properties')
    with open(path, encoding='utf-8') as fh:
        for line in fh:
            if line.strip().startswith('create_version='):
                return line.split('=', 1)[1].strip()
    return None


def create_jar():
    """Open the Create jar the project actually builds against.

    The Gradle cache keeps every build ever resolved, and the newest is not the
    one on the classpath -- this project pins 6.0.8-289 while 6.0.8-291 also sits
    in the cache. Taking whichever turned up first meant the source pixels could
    come from a different Create than the game runs, which is a difference nobody
    would think to look for when a colour came out wrong.
    """
    version = pinned_create_version()
    cache = os.path.expanduser('~/.gradle/caches/modules-2')
    exact, others = [], []
    for base, _, files in os.walk(cache):
        for f in files:
            if f.startswith('create-1.20.1') and f.endswith('.jar'):
                path = os.path.join(base, f)
                (exact if version and version in f else others).append(path)
    for path in sorted(exact) + sorted(others):
        if not exact:
            print('warning: no Create %s in the Gradle cache, falling back to '
                  '%s' % (version, os.path.basename(path)), file=sys.stderr)
        return zipfile.ZipFile(path)
    raise SystemExit('no Create jar in the Gradle cache; run ./gradlew build once')


def load(jar, name):
    im = Image.open(io.BytesIO(
        jar.read('assets/create/textures/block/%s.png' % name))).convert('RGBA')
    if im.height > im.width:              # animated strip: first frame only
        im = im.crop((0, 0, im.width, im.width))
    return im


def head_version(name):
    raw = subprocess.run(
        ['git', 'show', 'HEAD:%s/%s.png' % (REL, name)],
        capture_output=True, cwd=REPO).stdout
    return Image.open(io.BytesIO(raw)).convert('RGBA') if raw else None


def looks_like_accent(c):
    """Whether a colour is in the accent's corner of the wheel."""
    h, s, _ = colorsys.rgb_to_hsv(*[k / 255 for k in c[:3]])
    lo, hi = ACCENT_HUE
    return lo <= h * 360 <= hi and s >= ACCENT_SATURATION


def colours(im):
    """Every distinct colour in an image. 24 bits is more than any of these."""
    return {c for _, c in im.getcolors(1 << 24)}


def stragglers(jar):
    """Accent-coloured art that nothing above is managing.

    Both lists are opt-in, so a sheet drawn later carries the accent and then
    quietly keeps the old colour the next time the theme moves. That is exactly
    how the postbox flag was missed, and it is not the sort of thing anyone
    spots by looking -- the flag is four texels wide and only up when the box
    has mail.

    Subtracting what Create draws is what makes the question answerable without
    a list of exceptions to keep: a blue postbox is blue because the player
    dyed it, and every one of those shades is already in Create's texture of
    the same name. Whatever is left in the accent's corner is ours.
    """
    managed = set(OUTPUTS) | set(HAND_LAID) | set(SIGNALS)
    ramp = set(RETHEME.values())
    found = {}
    for base, dirs, files in sorted(os.walk(OUT)):
        dirs[:] = [d for d in dirs if not d.startswith('.')]
        for f in sorted(files):
            if not f.endswith('.png') or f[:-4] in managed:
                continue
            path = os.path.join(base, f)
            rel = os.path.relpath(path, OUT)[:-4].replace(os.sep, '/')
            ours = Image.open(path).convert('RGBA')
            try:
                theirs = Image.open(io.BytesIO(jar.read(
                    'assets/create/textures/block/%s.png' % rel))).convert('RGBA')
                creates = colours(theirs)
            except KeyError:
                creates = set()
            for c in colours(ours):
                if c[3] and c not in ramp and c not in creates \
                        and looks_like_accent(c):
                    found.setdefault('#%02X%02X%02X' % c[:3], set()).add(rel)
    return found


def retheme(im, canon):
    """Move a hand-laid texture's accent onto the casing ramp.

    Returns the shades that read as accent and had no entry, which is fatal:
    one would survive the pass still wearing the old colour, and that is how a
    sheet ends up half rethemed -- the failure this pass exists to prevent, and
    the one its first version shipped.

    A shade already on the casing ramp is passed over rather than flagged. The
    pass has to be a no-op on art that has already been through it, since the
    committed art is its own source, so "this colour is already the arriving
    one" is the steady state and not a finding.
    """
    table = {c: c for c in RETHEME} if canon else RETHEME
    arriving = set(table.values())
    px = im.load()
    missing = set()
    for y in range(im.height):
        for x in range(im.width):
            c = px[x, y]
            if c[3] == 0:
                continue
            if c in table:
                px[x, y] = table[c]
            elif c not in arriving and looks_like_accent(c):
                missing.add('#%02X%02X%02X' % c[:3])
    return missing


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--check', action='store_true',
                    help='report unmapped colours and write nothing')
    ap.add_argument('--canon', action='store_true',
                    help="keep Create's logistics wood instead of recolouring "
                         'it to THEME')
    ap.add_argument('--out', default=OUT,
                    help='write elsewhere, to preview a variant without '
                         'touching the working tree')
    args = ap.parse_args()

    if set(THEME) & set(PREVIOUS_THEME):
        print('THEME and PREVIOUS_THEME share a colour, so rethemeing the '
              'hand-laid textures would stop being a no-op the second time it '
              'ran', file=sys.stderr)
        return 1

    jar = create_jar()
    table = palette(args.canon)
    gaps = {}
    built = []

    untouched = as_drawn(args.canon)

    for out, (src, keep_box) in sorted(OUTPUTS.items()):
        im = load(jar, src)
        px = im.load()
        for y in range(im.height):
            for x in range(im.width):
                c = px[x, y]
                if c[3] == 0:
                    continue
                here = table
                if keep_box:
                    x0, y0, x1, y1 = keep_box
                    if x0 <= x < x1 and y0 <= y < y1:
                        here = untouched
                if c not in here:
                    gaps.setdefault('#%02X%02X%02X' % c[:3], set()).add(src)
                    continue
                px[x, y] = here[c]
        built.append((out, im))

    for out in sorted(HAND_LAID):
        im = head_version(out)
        if im is None:
            gaps.setdefault('(no committed art)', set()).add(out)
            continue
        for c in retheme(im, args.canon):
            gaps.setdefault(c, set()).add(out)
        built.append((out, im))

    for c, where in sorted(stragglers(jar).items()):
        gaps.setdefault(c, set()).update(where)

    if gaps:
        print('unmapped source colours -- nothing written:', file=sys.stderr)
        for c, srcs in sorted(gaps.items()):
            print('  %s   from %s' % (c, ', '.join(sorted(srcs))), file=sys.stderr)
        return 1

    if args.check:
        print('%d outputs, every source colour mapped' % len(built))
        return 0

    os.makedirs(args.out, exist_ok=True)
    for out, im in built:
        im.save(os.path.join(args.out, out + '.png'))
        print('wrote', out + '.png')
    return 0


if __name__ == '__main__':
    sys.exit(main())
