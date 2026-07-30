# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow", "numpy"]
# ///
"""Render block and item models to PNG without launching the game.

Entry point only -- the implementation is the `mcrender` package next to this
file. The PEP 723 header above is what keeps the tool zero-install: `uv run`
fetches Pillow and numpy into a throwaway environment, so there is nothing to
set up and nothing to build between editing a texture and seeing it.

    uv run scripts/render_model.py --list
    uv run scripts/render_model.py --scene transit_gate
    uv run scripts/render_model.py --check-fixtures
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from mcrender.cli import main  # noqa: E402

if __name__ == '__main__':
    sys.exit(main())
