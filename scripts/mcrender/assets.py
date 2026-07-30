"""Model and texture lookup across a mod's resources and its dependency jars.

Resolution has to span two worlds: files in the working tree, which is what you
are iterating on, and files inside dependency jars, which is where the models
being reskinned live. Reading the working tree rather than a built jar is the
whole point -- a tool that needs `./gradlew build` before it can show you a
texture change has given back the time it was supposed to save.
"""
import io
import json
import os
import sys
import zipfile

import numpy as np
from PIL import Image

# the notorious missing-texture checkerboard, so a broken reference is loud
MISSING = np.array([[[248, 0, 248, 255], [0, 0, 0, 255]],
                    [[0, 0, 0, 255], [248, 0, 248, 255]]], np.uint8)


def gradle_properties(repo):
    props = {}
    with open(os.path.join(repo, 'gradle.properties'), encoding='utf-8') as fh:
        for line in fh:
            line = line.strip()
            if line and not line.startswith('#') and '=' in line:
                k, v = line.split('=', 1)
                props[k.strip()] = v.strip()
    return props


def find_jars(artifact, version):
    """Locate a dependency jar in the Gradle cache, preferring the pinned build.

    The cache keeps every build the project has ever resolved, and the newest is
    not the one on the classpath. Taking whatever turns up first is how a tool
    ends up reading textures from one Create build while the game runs another,
    so the exact version wins and the rest are only a fallback.
    """
    cache = os.path.expanduser('~/.gradle/caches/modules-2')
    exact, others = [], []
    for base, _, files in os.walk(cache):
        for f in files:
            if not (f.startswith(artifact + '-') and f.endswith('.jar')):
                continue
            (exact if version and version in f else others).append(
                os.path.join(base, f))
    return sorted(exact) + sorted(others)


class Assets:
    def __init__(self, mod_id, roots, jars):
        """`roots` are asset directories checked before any jar, nearest first."""
        self.mod_id = mod_id
        self.roots = list(roots)
        self.jars = []
        for j in jars:
            try:
                self.jars.append(zipfile.ZipFile(j))
            except (OSError, zipfile.BadZipFile):
                continue
        self._tex = {}

    def _read(self, ns, kind, path):
        if ns == self.mod_id:
            for root in self.roots:
                local = os.path.join(root, kind, *path.split('/'))
                if os.path.exists(local):
                    with open(local, 'rb') as fh:
                        return fh.read()
            return None
        name = 'assets/%s/%s/%s' % (ns, kind, path)
        for jar in self.jars:
            try:
                return jar.read(name)
            except KeyError:
                continue
        return None

    def split(self, res):
        return res.split(':', 1) if ':' in res else (self.mod_id, res)

    def model(self, res):
        ns, path = self.split(res)
        raw = self._read(ns, 'models', path + '.json')
        return json.loads(raw) if raw else {}

    def texture(self, res):
        if res not in self._tex:
            ns, path = self.split(res)
            raw = self._read(ns, 'textures', path + '.png')
            if raw is None:
                print('  missing texture: %s' % res, file=sys.stderr)
                self._tex[res] = MISSING
            else:
                im = Image.open(io.BytesIO(raw)).convert('RGBA')
                if im.height > im.width:   # animated strip: first frame only
                    im = im.crop((0, 0, im.width, im.width))
                self._tex[res] = np.asarray(im)
        return self._tex[res]

    def _flatten(self, res, inherited=None):
        """Walk a parent chain, collecting the nearest elements and children.

        Nearer models win every texture slot, which is what lets a model reskin
        its parent by naming the same keys -- and a child that declares its own
        `elements` replaces the parent's outright rather than adding to them.
        """
        textures = dict(inherited or {})
        elements = children = None
        seen = set()
        while res and res not in seen:
            seen.add(res)
            d = self.model(res)
            for k, v in d.get('textures', {}).items():
                textures.setdefault(k, v)
            if elements is None and 'elements' in d:
                elements = d['elements']
            if children is None and 'children' in d:
                children = d['children']
            res = d.get('parent')
        return elements, children, textures

    def resolve(self, res):
        """Flatten a model into a list of (elements, textures) parts.

        A forge:composite model carries its geometry in named children rather
        than in elements of its own, and each child layers its own texture map
        over the parent's.
        """
        elements, children, textures = self._flatten(res)
        if not children:
            return [(elements or [], textures)]
        parts = []
        for child in children.values():
            ctex = dict(textures)
            ctex.update(child.get('textures', {}))
            celem, _, ctex = self._flatten(child.get('parent'), ctex)
            parts.append((child.get('elements') or celem or [], ctex))
        return parts

    def list_models(self):
        out = []
        for root in self.roots:
            base = os.path.join(root, 'models')
            for dirpath, _, files in os.walk(base):
                for f in sorted(files):
                    if not f.endswith('.json'):
                        continue
                    rel = os.path.relpath(os.path.join(dirpath, f), base)
                    out.append(rel[:-5].replace(os.sep, '/'))
        return sorted(set(out))


def deref(textures, key):
    """Follow `#slot` indirection to a real texture id, or None if it dangles."""
    for _ in range(8):
        if not key.startswith('#'):
            return key
        key = textures.get(key[1:], '')
    return None
