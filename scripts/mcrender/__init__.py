"""Offline rendering of Minecraft block models, including the parts a block
entity renderer draws.

The problem this exists for: a model file's coordinates are meaningless until
you know who draws it. A block model is baked into the chunk mesh and means what
it says; a partial model is transformed by a renderer first. Minecraft's format
cannot express which, so geometry that looks correct in Blockbench can land
eight units away in game -- and a render that ignores the transform shows it in
the wrong place while looking entirely plausible.

The pieces, in dependency order:

  transforms.py  the renderer matrices, loaded from JSON as data, so nothing
                 here knows what a packager is
  assets.py      model and texture lookup across the working tree and the
                 dependency jars in the Gradle cache
  raster.py      projection and shading, plus reference-image comparison
  cli.py         the command line, scenes, and the fixture check

Renderer version. Bump it when a change alters output pixels, since every
committed reference image is invalidated by that and regenerating them should be
a deliberate, reviewable act rather than a surprise.
"""
__version__ = '2.4.0'
