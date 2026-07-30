"""The block-entity-renderer transform table, loaded as data.

Keeping these matrices in a JSON file rather than in code is what makes this
tool mod-agnostic: nothing here knows what a packager is. Point it at a
different table and it places a different mod's partial models. The table is
produced by scripts/dump_transforms.py -- read that file for provenance and for
why a Gradle task should eventually replace it.

The one thing this module insists on is noticing when the table no longer
matches the dependency the project pins. A stale matrix is the worst failure
mode available here, because everything downstream -- renders and assertions
alike -- is computed *through* it, so wrong geometry passes every check while
looking entirely reasonable.
"""
import glob
import json
import os
import sys

import numpy as np


class Stale(Exception):
    pass


class TransformTable:
    def __init__(self, data, path):
        self.data = data
        self.path = path
        self._m = {}
        for name, spec in data.get('transforms', {}).items():
            for facing, rows in spec.get('matrices', {}).items():
                self._m[(name, facing)] = np.asarray(rows, float)

    @classmethod
    def load(cls, directory, mod='create', version=None, strict=False):
        """Load `mod`'s table, preferring the one built for `version`.

        A version mismatch is a warning rather than an error by default: it is
        usually harmless, and refusing to render is a worse outcome than saying
        so. `strict` turns it into a failure, which is what a fixture check or a
        validator should use -- there, silently trusting an unverified matrix
        defeats the point of running at all.
        """
        want = os.path.join(directory, '%s-%s.json' % (mod, version)) \
            if version else None
        if want and os.path.exists(want):
            path = want
        else:
            found = sorted(glob.glob(os.path.join(directory, '%s-*.json' % mod)))
            if not found:
                raise Stale('no transform table for %r in %s -- run '
                            'scripts/dump_transforms.py' % (mod, directory))
            path = found[-1]
        with open(path, encoding='utf-8') as fh:
            data = json.load(fh)
        table = cls(data, path)
        if version and data.get('version') != version:
            msg = ('transform table is for %s %s but the project pins %s; '
                   'rerun scripts/dump_transforms.py'
                   % (mod, data.get('version'), version))
            if strict:
                raise Stale(msg)
            print('  warning: %s' % msg, file=sys.stderr)
        if data.get('units') != 'model':
            raise Stale('%s declares units=%r; this tool works in model units'
                        % (path, data.get('units')))
        return table

    def names(self):
        return sorted(self.data.get('transforms', {}))

    def describe(self, name):
        spec = self.data['transforms'][name]
        return spec.get('doc', '')

    def matrix(self, name, facing):
        """A pure lookup -- every `getOpposite()` and angle helper lives in the
        producer, so a wrong facing here can only ever be a missing key."""
        if name == 'none':
            return np.eye(4)
        try:
            return self._m[(name, facing)]
        except KeyError:
            raise Stale('no matrix for %r at facing=%r; table has %s'
                        % (name, facing, ', '.join(self.names()))) from None


def apply(pts, m):
    """Transform an array of points shaped (..., 3)."""
    return pts @ m[:3, :3].T + m[:3, 3]


def bounds(m, lo, hi):
    """The axis-aligned box enclosing a transformed box.

    Rotation turns a box into a box only at right angles, so this takes the
    hull of all eight corners. Assertions about whether geometry stays inside
    the block are built on this.
    """
    corners = np.array([[x, y, z] for x in (lo[0], hi[0])
                        for y in (lo[1], hi[1]) for z in (lo[2], hi[2])], float)
    out = apply(corners, m)
    return out.min(axis=0), out.max(axis=0)
