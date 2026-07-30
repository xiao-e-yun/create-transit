"""Project and shade model elements into an image.

Deliberately not a faithful Minecraft renderer. `cullface` is ignored, so is
`tintindex`, and element rotation is applied without vanilla's `rescale`. That
is a considered position rather than a backlog: once renders are compared
against committed reference images, what matters is that the renderer is
*deterministic and sensitive* -- that wrong geometry produces a different
picture -- not that it is pixel-accurate to the game. Fidelity is an open-ended
project; sensitivity is a finite one.

The projection is orthographic and the game's is perspective, and that one is a
real blind spot rather than a tolerable approximation. A perspective camera
close to a block reaches sightlines an orthographic one never takes, so a gap
that opens up over recessed geometry can be invisible here and obvious in game
-- which is exactly how a slot above the gate's curtain got shipped. Angled
views reach similar sightlines and are the mitigation, so anything set back
behind a block face deserves a fixture that looks at it from off-axis; the
`opening-low` view exists for that. Treat a square-on render as evidence about
alignment, never as evidence that nothing shows through.

Everything in a scene goes through one shared depth buffer. Compositing
separately rendered layers afterwards cannot work: a packager's hatch is behind
the frame in places and in front of the interior in others, and only a depth
test gets that right.
"""
import math
from fnmatch import fnmatch
import sys

import numpy as np
from PIL import Image

from .assets import deref
from .transforms import apply as apply_matrix

# vanilla's directional shading, so a render reads the way the game looks
SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8,
         'east': 0.6, 'west': 0.6}

# How much nearer a surface must be, in model units, to win a pixel. Two orders
# of magnitude below anything anyone authors, so it never hides real geometry,
# and far above the float noise that would otherwise decide coplanar cases.
DEPTH_EPS = 1e-3

# corners in tl, tr, br, bl order as seen from outside the cube
CORNERS = {
    'north': ((1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)),
    'south': ((0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)),
    'west':  ((0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)),
    'east':  ((1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)),
    'up':    ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)),
    'down':  ((0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)),
}


def element_rotation(rot):
    """MC's per-element rotation: one axis, one angle, through an origin.

    Same right-handed convention as the renderer transforms in the table, so
    element rotation and renderer placement compose through one code path.
    """
    angle = math.radians(rot.get('angle', 0))
    c, s = math.cos(angle), math.sin(angle)
    m = np.eye(4)
    axis = rot.get('axis', 'y')
    if axis == 'x':
        m[1, 1], m[1, 2], m[2, 1], m[2, 2] = c, -s, s, c
    elif axis == 'y':
        m[0, 0], m[0, 2], m[2, 0], m[2, 2] = c, s, -s, c
    else:
        m[0, 0], m[0, 1], m[1, 0], m[1, 1] = c, -s, s, c
    o = np.asarray(rot.get('origin', [8, 8, 8]), float)
    t, tb = np.eye(4), np.eye(4)
    t[:3, 3], tb[:3, 3] = o, -o
    return t @ m @ tb


def element_name(model_res, index, el):
    """A stable handle for an element, so it can be selected from the CLI.

    Most elements in Create's models are unnamed, and those are exactly the ones
    you end up needing to point at, so an index stands in. Qualifying by model
    matters because names repeat across the models composed into one scene.
    """
    return '%s#%s' % (model_res.split(':')[-1].split('/')[-1],
                      el.get('name') or index)


def render(assets, specs, size=384, yaw=225.0, pitch=30.0, ss=2, quiet=False,
           only=None, paint=None):
    """Draw every (model id, 4x4 placement matrix) pair into one image.

    `only` keeps just the elements whose handle matches one of its glob
    patterns, and `paint` flat-fills matching elements with a colour. Both are
    for answering "which thing am I actually looking at" -- a question that comes
    up constantly once several models are composed into one picture, and that is
    otherwise answered by commenting geometry out and putting it back.
    """
    W = size * ss
    colour = np.zeros((W, W, 4), np.float32)
    depth = np.full((W, W), -1e9, np.float32)

    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    scale = W / 25.0
    tinted = set()

    def project(p):
        x, y, z = p[..., 0] - 8, p[..., 1] - 8, p[..., 2] - 8
        x, z = x * cy + z * sy, -x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return W / 2 + x * scale, W / 2 - y * scale, z

    def draw_face(el, dirn, face, textures, place, flat=None):
        tex = deref(textures, face['texture'])
        if not tex:
            return
        if 'tintindex' in face:
            tinted.add(tex)
        t = assets.texture(tex)
        th, tw = t.shape[:2]
        u0, v0, u1, v1 = face.get('uv', [0, 0, 16, 16])

        lo = np.minimum(el['from'], el['to']).astype(float)
        hi = np.maximum(el['from'], el['to']).astype(float)
        pts = lo + (hi - lo) * np.array(CORNERS[dirn], float)
        if el.get('rotation'):
            pts = apply_matrix(pts, element_rotation(el['rotation']))
        pts = apply_matrix(pts, place)
        sxs, sys_, szs = project(pts)

        # Drop faces that are edge-on to the camera. Their projected quad has
        # essentially no area, so every sample lands on the same pixel column
        # with a depth that depends on rounding rather than on geometry -- and
        # whichever nearly-coplanar neighbour wins that tie then flips on the
        # slightest change anywhere. That turned a 0.00016-unit correction to a
        # translation into thousands of changed pixels along every slat seam,
        # which is the difference between a reference image that catches
        # regressions and one that cries wolf until it gets ignored. The game
        # does not draw these either.
        area = 0.5 * abs((sxs[2] - sxs[0]) * (sys_[3] - sys_[1])
                         - (sxs[3] - sxs[1]) * (sys_[2] - sys_[0]))
        if area < 0.5:
            return

        # sample the quad densely enough that its screen footprint has no gaps
        span = max(sxs.max() - sxs.min(), sys_.max() - sys_.min())
        n = max(4, int(span * 1.5))
        a, b = np.meshgrid(np.linspace(0, 1, n), np.linspace(0, 1, n))

        def lerp(v):
            # bilinear across the quad: a runs tl->tr, b runs tl->bl
            return ((1 - a) * ((1 - b) * v[0] + b * v[3])
                    + a * ((1 - b) * v[1] + b * v[2]))

        px, py, pz = lerp(sxs), lerp(sys_), lerp(szs)

        uu, vv = a, b
        for _ in range(face.get('rotation', 0) // 90):
            uu, vv = vv, 1 - uu
        su = np.clip(((u0 + (u1 - u0) * uu) / 16.0 * tw).astype(int), 0, tw - 1)
        sv = np.clip(((v0 + (v1 - v0) * vv) / 16.0 * th).astype(int), 0, th - 1)
        texel = t[sv, su]
        if flat is not None:
            # Keep the texture's alpha so the silhouette stays honest -- a
            # painted element should answer "where is this", not become a
            # rectangle the real thing never was.
            texel = np.concatenate(
                [np.broadcast_to(flat, texel.shape[:-1] + (3,)),
                 texel[..., 3:]], axis=-1).astype(texel.dtype)

        ix, iy = px.astype(int), py.astype(int)
        ok = (ix >= 0) & (ix < W) & (iy >= 0) & (iy < W) & (texel[..., 3] > 0)
        ix, iy, pz, texel = ix[ok], iy[ok], pz[ok], texel[ok]

        # A face is oversampled, so several samples -- carrying different texels
        # -- land on the same pixel, and the writes below let the last one win.
        # Which one that is has to be decided by something meaningful. Plain
        # argsort is quicksort, and a face square to the camera gives every
        # sample identical depth, so it was resolving those ties on float bit
        # patterns: a 1e-6 change to a translation repainted as many pixels as a
        # 0.5 change, at deltas big enough to be a different surface. lexsort is
        # stable, and breaking ties on distance to the pixel centre picks the
        # sample that actually covers the pixel.
        #
        # Measured after this change: identical input is bit-identical, and the
        # worst delta from a 1e-6 perturbation falls from ~160 to ~18 -- an
        # adjacent shade rather than the wrong face. A few hundred pixels do
        # still move at that scale, so the check reports magnitudes rather than
        # asserting a threshold, and a diff worth acting on looks obviously
        # different from this floor. Genuinely fixing the tail means area
        # sampling instead of point sampling, which is a bigger change than the
        # problem currently justifies.
        centre = (px[ok] - (ix + 0.5)) ** 2 + (py[ok] - (iy + 0.5)) ** 2
        order = np.lexsort((-centre, pz))
        ix, iy, pz, texel = ix[order], iy[order], pz[order], texel[order]
        # A surface has to be meaningfully nearer to take a pixel, not merely
        # nearer by float noise. Depth is in model units and geometry is authored
        # to hundredths at best, so a thousandth of a unit is not a real
        # difference -- but without this bias it decides which of two
        # near-coplanar faces wins, and then a 0.00016 correction to one
        # translation repaints thousands of pixels along every seam. Reference
        # images that move when nothing visible moved get ignored, and an
        # ignored check is worse than no check.
        nearer = pz > depth[iy, ix] + DEPTH_EPS
        ix, iy, pz, texel = ix[nearer], iy[nearer], pz[nearer], texel[nearer]

        depth[iy, ix] = pz
        sh = SHADE[dirn]
        for ch in range(3):
            colour[iy, ix, ch] = texel[:, ch] * sh
        colour[iy, ix, 3] = texel[:, 3]

    seen = []
    for model_res, place in specs:
        parts = assets.resolve(model_res)
        if not any(elements for elements, _ in parts) and not quiet:
            print('  no elements: %s' % model_res, file=sys.stderr)
        index = 0
        for elements, textures in parts:
            for el in elements:
                handle = element_name(model_res, index, el)
                index += 1
                seen.append(handle)
                if only and not any(fnmatch(handle, p) for p in only):
                    continue
                flat = None
                for pattern, rgb in (paint or {}).items():
                    if fnmatch(handle, pattern):
                        flat = np.asarray(rgb, np.int16)
                        break
                for dirn, face in el.get('faces', {}).items():
                    draw_face(el, dirn, face, textures, place, flat)

    if not quiet and (only or paint):
        print('  elements: %s' % ', '.join(seen), file=sys.stderr)
    if tinted and not quiet:
        print('  tintindex ignored, colours are the untinted ones: %s'
              % ', '.join(sorted(tinted)), file=sys.stderr)

    im = Image.fromarray(colour.astype(np.uint8), 'RGBA')
    return im.resize((size, size), Image.LANCZOS)


def compare(a, b):
    """Pixel difference between two same-sized images.

    Comparing arrays rather than encoded bytes on purpose -- PNG output can
    differ between Pillow versions for identical pixels, and a fixture check
    that fails on a library upgrade teaches people to ignore it.
    """
    if a.size != b.size:
        return {'same': False, 'reason': 'size %s vs %s' % (a.size, b.size)}
    x = np.asarray(a.convert('RGBA'), np.int16)
    y = np.asarray(b.convert('RGBA'), np.int16)
    d = np.abs(x - y)
    differing = int((d.max(axis=2) > 0).sum())
    return {'same': differing == 0, 'differing_pixels': differing,
            'max_channel_delta': int(d.max()),
            'total_pixels': x.shape[0] * x.shape[1]}
