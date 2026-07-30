"""Command line for the offline renderer."""
import argparse
import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

from .assets import Assets, find_jars, gradle_properties
from .raster import compare, render
from .transforms import Stale, TransformTable

# yaw, pitch pairs worth having a name. Which faces a given yaw shows is not
# something anyone should be recomputing from a cosine every time they want to
# look at the underside of a block.
VIEWS = {
    'iso':     (225.0, 30.0),   # north + east, the angle an item icon uses
    'iso-back': (45.0, 30.0),   # south + west
    'opening': (180.0, 0.0),    # square on the north face, where a packager's
                                # hatch lands for a facing=south block
    'opening-low': (180.0, -30.0),  # the sightline that reaches in over
                                # anything recessed behind the face, which a
                                # square-on view cannot show
    'south':   (0.0, 0.0),
    'north':   (180.0, 0.0),
    'east':    (90.0, 0.0),     # shows depth along z, which flat-on views hide
    'west':    (270.0, 0.0),
    'top':     (225.0, 89.9),
    'below':   (225.0, -60.0),  # where a frame sheet's cavity half shows
}

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPTS = os.path.dirname(HERE)
REPO = os.path.dirname(SCRIPTS)


def label_font(px):
    try:
        return ImageFont.load_default(size=px)
    except TypeError:                       # Pillow older than 10.1
        return ImageFont.load_default()


def contact_sheet(items, bg=(26, 26, 30, 255)):
    """Tile labelled renders into one image.

    Nearly every question worth asking is a comparison -- this palette against
    that one, closed against open, before against after -- and a directory of
    equally sized PNGs is not one.
    """
    cols = max(1, int(math.ceil(math.sqrt(len(items)))))
    rows = int(math.ceil(len(items) / cols))
    w = items[0][1].width
    bar = max(14, w // 16)
    font = label_font(max(9, bar - 6))
    sheet = Image.new('RGBA', (cols * w, rows * (w + bar)), bg)
    draw = ImageDraw.Draw(sheet)
    thumb = max(24, w // 8)
    for i, (name, im) in enumerate(items):
        x, y = (i % cols) * w, (i // cols) * (w + bar)
        sheet.alpha_composite(im, (x, y))

        # A render is around twenty times the size a block occupies on screen,
        # which flatters fine texture detail: a near-black column that reads as
        # an edge at this size is a solid stripe in game. Pairing every tile with
        # a thumbnail near the real thing costs nothing and makes texture
        # decisions get made at the size they will actually be judged at, rather
        # than depending on someone remembering to check.
        small = im.resize((thumb, thumb), Image.BOX)
        tx, ty = x + w - thumb - 4, y + w - thumb - 4
        draw.rectangle([tx - 1, ty - 1, tx + thumb, ty + thumb],
                       outline=(90, 90, 96, 255))
        sheet.alpha_composite(small, (tx, ty))
        draw.text((x + 4, y + w + 2), name, font=font, fill=(224, 224, 224, 255))
    return sheet


def load_scenes(path):
    with open(path, encoding='utf-8') as fh:
        return json.load(fh).get('scenes', {})


def build_assets(props, extra_jars):
    mod_id = props.get('mod_id', 'create_transit')
    roots = [os.path.join(REPO, 'src', 'main', 'resources', 'assets', mod_id),
             os.path.join(REPO, 'src', 'generated', 'resources', 'assets', mod_id)]
    jars = list(extra_jars)
    jars += find_jars('create', props.get('create_version'))
    jars += find_jars('flywheel-forge', props.get('flywheel_version'))
    jars += find_jars('Ponder-Forge', props.get('ponder_version'))
    if not jars:
        raise Stale('no dependency jars in the Gradle cache; run '
                    './gradlew build once to populate it')
    return Assets(mod_id, [r for r in roots if os.path.isdir(r)], jars)


def specs_for(scene, table, quiet=False):
    """Turn a scene declaration into (model, matrix) pairs."""
    facing = scene.get('facing', 'south')
    out = []
    for entry in scene['models']:
        name = entry.get('transform', 'none')
        out.append((entry['model'], table.matrix(name, facing)))
    return out


def cmd_fixtures(args, assets, table, scenes):
    """Compare every scene view against its committed reference image.

    This is the layer that guards the transform table. Everything else -- every
    render, and every assertion a validator makes -- is computed through those
    matrices, so nothing computed from them can catch them being wrong. Only a
    comparison against an image of a block whose correct appearance is known
    independently can do that, which is what `packager_reference` is for.
    """
    fixtures = os.path.join(SCRIPTS, 'fixtures')
    os.makedirs(fixtures, exist_ok=True)
    failed = missing = 0
    for name in sorted(scenes):
        scene = scenes[name]
        for view in scene.get('views', ['iso']):
            yaw, pitch = VIEWS[view]
            im = render(assets, specs_for(scene, table), size=args.size,
                        yaw=yaw, pitch=pitch, ss=args.ss, quiet=True)
            path = os.path.join(fixtures, '%s.%s.png' % (name, view))
            label = '%s @ %s' % (name, view)
            if args.write:
                im.save(path)
                print('  wrote    %s' % label)
                continue
            if not os.path.exists(path):
                print('  MISSING  %s   (--write to create it)' % label)
                missing += 1
                continue
            result = compare(im, Image.open(path))
            if result['same']:
                print('  ok       %s' % label)
            else:
                failed += 1
                if 'reason' in result:
                    print('  CHANGED  %s   %s' % (label, result['reason']))
                else:
                    print('  CHANGED  %s   %d/%d px differ, max delta %d'
                          % (label, result['differing_pixels'],
                             result['total_pixels'],
                             result['max_channel_delta']))
                bad = os.path.join(fixtures, '%s.%s.actual.png' % (name, view))
                im.save(bad)
    if args.write:
        return 0
    if failed or missing:
        print('\n%d changed, %d missing. If the change is intended, rerun with '
              '--write and review the images in the diff.' % (failed, missing),
              file=sys.stderr)
        return 1
    print('\nall fixtures match')
    return 0


def main(argv=None):
    ap = argparse.ArgumentParser(
        prog='render_model',
        description='Render block and item models to PNG without launching the '
                    'game.',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
examples
  render_model.py --scene transit_gate
  render_model.py --scene-all --sheet
  render_model.py --check-fixtures
  render_model.py --view below block/transit_gate/block
  render_model.py --with 'block/transit_gate/hatch_closed@packager_hatch' \\
                  block/transit_gate/block

Model ids may be bare (resolved against this mod) or namespaced
(`create:block/packager/block`), in which case they are read from the matching
jar in the Gradle cache.

Parts a block entity renderer draws are placed by a matrix from
scripts/transforms/. Prefer --scene, which names the whole composition once, so
that renders, fixture checks and validators all read the same declaration.
""")
    ap.add_argument('models', nargs='*',
                    help='model ids, e.g. block/transit_gate/block')
    ap.add_argument('--scene', action='append', default=[], metavar='NAME',
                    help='render a named scene from scripts/scenes.json')
    ap.add_argument('--scene-all', action='store_true',
                    help='render every scene, at its first listed view')
    ap.add_argument('--list', action='store_true',
                    help='list scenes and transforms, then exit')
    ap.add_argument('--check-fixtures', action='store_true',
                    help='render every scene view and compare against the '
                         'committed reference image; exits non-zero on any '
                         'difference')
    ap.add_argument('--write', action='store_true',
                    help='with --check-fixtures, overwrite the references '
                         'instead of comparing')
    ap.add_argument('--all', action='store_true',
                    help="render every model under this mod's models/ (slow: "
                         'the postboxes alone are over a hundred)')
    ap.add_argument('--with', dest='extra', action='append', default=[],
                    metavar='MODEL[@TRANSFORM]',
                    help='draw this into the same scene as every model given, '
                         'depth-tested against it')
    ap.add_argument('--only', action='append', default=[], metavar='GLOB',
                    help='draw only elements whose handle matches, e.g. '
                         "'hatch_closed#flap_rail' or 'block#*'. Handles are "
                         'printed whenever --only or --paint is used.')
    ap.add_argument('--paint', action='append', default=[],
                    metavar='GLOB=#RRGGBB',
                    help='flat-fill matching elements, to see which geometry is '
                         'which without commenting any of it out')
    ap.add_argument('--facing', default='south',
                    help='the blockstate facing the transforms are looked up '
                         'with (default: %(default)s, the orientation base '
                         'block models are authored in)')
    ap.add_argument('--view', choices=sorted(VIEWS),
                    help='named camera angle instead of --yaw/--pitch')
    ap.add_argument('--yaw', type=float, default=225.0)
    ap.add_argument('--pitch', type=float, default=30.0,
                    help='negative looks up at the block from below')
    ap.add_argument('--sheet', action='store_true',
                    help='also write sheet.png, every render tiled and labelled')
    ap.add_argument('-o', '--out', default='build/model-preview')
    ap.add_argument('--size', type=int, default=384)
    ap.add_argument('--ss', type=int, default=2,
                    help='supersampling factor, traded against time')
    ap.add_argument('--transforms', default=os.path.join(SCRIPTS, 'transforms'))
    ap.add_argument('--scenes', default=os.path.join(SCRIPTS, 'scenes.json'))
    ap.add_argument('--jar', action='append', default=[],
                    help='extra jar to resolve foreign namespaces from')
    args = ap.parse_args(argv)

    props = gradle_properties(REPO)
    try:
        # A fixture check must not proceed on an unverified table: silently
        # trusting a stale matrix is exactly the failure it exists to catch.
        table = TransformTable.load(args.transforms, 'create',
                                    props.get('create_version'),
                                    strict=args.check_fixtures)
        assets = build_assets(props, args.jar)
    except Stale as e:
        print('error: %s' % e, file=sys.stderr)
        return 2
    scenes = load_scenes(args.scenes) if os.path.exists(args.scenes) else {}

    if args.list:
        print('transforms in %s' % os.path.relpath(table.path, REPO))
        for n in table.names():
            print('  %-16s %s' % (n, table.describe(n)))
        print('\nscenes in %s' % os.path.relpath(args.scenes, REPO))
        for n in sorted(scenes):
            print('  %-24s views: %s' % (n, ', '.join(scenes[n].get('views', []))))
        return 0

    if args.check_fixtures:
        return cmd_fixtures(args, assets, table, scenes)

    paint = {}
    for spec in args.paint:
        pattern, _, colour = spec.partition('=')
        colour = colour.lstrip('#')
        if len(colour) != 6:
            ap.error('--paint wants GLOB=#RRGGBB, got %r' % spec)
        paint[pattern] = [int(colour[i:i + 2], 16) for i in (0, 2, 4)]
    debug = dict(only=args.only or None, paint=paint or None)

    yaw, pitch = (VIEWS[args.view] if args.view else (args.yaw, args.pitch))
    outdir = args.out if os.path.isabs(args.out) else os.path.join(REPO, args.out)
    os.makedirs(outdir, exist_ok=True)
    rendered = []

    wanted = list(args.scene) + (sorted(scenes) if args.scene_all else [])
    for name in wanted:
        if name not in scenes:
            print('error: no scene %r; --list to see them' % name, file=sys.stderr)
            return 2
        scene = scenes[name]
        view = args.view or scene.get('views', ['iso'])[0]
        y, p = VIEWS[view]
        print('%s @ %s' % (name, view), flush=True)
        im = render(assets, specs_for(scene, table), size=args.size,
                    yaw=y, pitch=p, ss=args.ss, **debug)
        label = '%s.%s' % (name, view)
        im.save(os.path.join(outdir, label + '.png'))
        rendered.append((label, im))

    loose = list(args.models) + (assets.list_models() if args.all else [])
    if loose:
        extra = []
        for spec in args.extra:
            model, _, tname = spec.partition('@')
            extra.append((model, table.matrix(tname or 'none', args.facing)))
        import numpy as np
        for res in loose:
            print(res, flush=True)
            im = render(assets, [(res, np.eye(4))] + extra, size=args.size,
                        yaw=yaw, pitch=pitch, ss=args.ss, **debug)
            label = res.split(':')[-1].replace('/', '_')
            im.save(os.path.join(outdir, label + '.png'))
            rendered.append((label, im))

    if not rendered:
        ap.error('give a model id, --scene, --scene-all, --all, '
                 '--check-fixtures or --list')
    if args.sheet:
        contact_sheet(rendered).save(os.path.join(outdir, 'sheet.png'))
        print('sheet.png')
    print('-> %s' % outdir)
    return 0
