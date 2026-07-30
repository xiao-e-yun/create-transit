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
    wood  -> theme blue     Create's logistics accent, restyled
    everything else kept    neutrals, navy, and every red

Red staying put is deliberate and predates this script: red means powered
across all of Create, and that vocabulary is worth more than palette purity.

`--canon` drops the wood rule instead of applying it, which returns the accent
to Create's own logistics wood. That is the whole difference between the mod's
look and a fully canon one -- the blue occupies the slot Create fills with wood.

    uv run scripts/repalette.py            # rebuild
    uv run scripts/repalette.py --check    # report only, touch nothing
    uv run scripts/repalette.py --verify   # assert the rebuild matches git HEAD

Hand-drawn textures -- the gate's flaps, the postboxes -- have no Create source
and are not managed here.
"""
import argparse
import io
import os
import subprocess
import sys
import zipfile

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REL = 'src/main/resources/assets/create_transit/textures/block'
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

# Create's logistics wood -> the mod's theme blue, rung for rung
WOOD_TO_BLUE = {rgb(a): rgb(b) for a, b in [
    ('886539', '42709D'), ('82613A', '426B94'), ('7A5A34', '3C648C'),
    ('70522E', '355B81'), ('614B2E', '35526F'), ('5A4424', '2B4B6B'),
    ('553A1F', '234261'),
]}

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
        table.update({c: c for c in WOOD_TO_BLUE})
    else:
        table.update(WOOD_TO_BLUE)
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


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--check', action='store_true',
                    help='report unmapped colours and write nothing')
    ap.add_argument('--verify', action='store_true',
                    help='rebuild in memory and diff against git HEAD')
    ap.add_argument('--canon', action='store_true',
                    help="keep Create's logistics wood instead of recolouring "
                         'it to the theme blue')
    ap.add_argument('--out', default=OUT,
                    help='write elsewhere, to preview a variant without '
                         'touching the working tree')
    args = ap.parse_args()

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

    if gaps:
        print('unmapped source colours -- nothing written:', file=sys.stderr)
        for c, srcs in sorted(gaps.items()):
            print('  %s   from %s' % (c, ', '.join(sorted(srcs))), file=sys.stderr)
        return 1

    if args.verify:
        bad = 0
        for out, im in built:
            head = head_version(out)
            if head is None:
                print('  %-32s no committed version to compare' % out)
            elif head.tobytes() == im.tobytes():
                print('  %-32s matches HEAD' % out)
            else:
                print('  %-32s DIFFERS from HEAD' % out)
                bad += 1
        return 1 if bad else 0

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
