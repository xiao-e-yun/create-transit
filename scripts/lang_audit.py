# /// script
# requires-python = ">=3.9"
# dependencies = []
# ///
"""Check the language files against the code that uses them.

Nothing else does. All 184 resource JSONs here are hand-authored -- `runData`
is a dead task in this project, there is no GatherDataEvent, and Minecraft
itself never complains about a translation file: a key with no entry renders as
the raw key, and an entry with no key sits there costing nothing and saying
nothing. Both failures are silent, and both are the kind that survive a review
because the file they are in is a wall of near-identical lines.

So this reads the keys out of the lang files, reads the keys out of the Java,
and reports every way the two disagree.

    uv run scripts/lang_audit.py

Four checks, each for a mistake that has actually been made here:

`dead` -- a key no source file mentions. Left behind when a tooltip line is
dropped; the count on the ticker's goggles outlived its own removal by two
commits.

`missing` -- a key the Java asks for that en_us has no entry for. This is the
one that reaches a player, as a line of raw key text in the middle of a
tooltip.

`parity` -- a key in one language and not another. Minecraft falls back to
en_us per key rather than per file, so a translation with a hole in it looks
finished and reads half-English.

`format` -- the same key taking a different number of arguments in different
languages. `Component.translatable` is handed a fixed argument list, so a
translation that expects more than it is given renders the surplus as literal
`%s`, and one that expects fewer silently drops what it was told.

Registry keys (`block.`, `item.`, `itemGroup.`) are looked for by their
identifier as well as by the whole key, since the game builds those from the
registry rather than from a literal in our source.

Exits non-zero when anything is reported, so it can gate a commit.
"""

from __future__ import annotations

import json
import os
import re
import sys

LANG_DIR = os.path.join("src", "main", "resources", "assets", "create_transit", "lang")
BASE = "en_us"

REGISTRY_PREFIXES = ("block.", "item.", "itemGroup.")

# A translation key in a string literal: a dotted path naming the mod. Resource
# locations do not match -- those carry a colon and slashes.
KEY_LITERAL = re.compile(r'"((?:[a-z_]+\.)*create_transit\.[A-Za-z0-9_.]+)"')

# %s and its indexed form, which Minecraft accepts interchangeably.
FORMAT_ARG = re.compile(r"%(?:\d+\$)?s")

# Files that mention keys for reasons other than using them.
NOT_A_KEY = (".json", ".png", ".mixins", ".refmap")


def load_langs() -> dict[str, dict[str, str]]:
    langs = {}
    for name in sorted(os.listdir(LANG_DIR)):
        if not name.endswith(".json"):
            continue
        with open(os.path.join(LANG_DIR, name), encoding="utf-8") as f:
            langs[name[:-5]] = json.load(f)
    return langs


def read_sources() -> tuple[str, dict[str, set[str]]]:
    """Everything outside the lang directory, plus where each key literal was seen."""
    blob = []
    referenced: dict[str, set[str]] = {}
    for root, _, names in os.walk("src"):
        if "lang" in root.replace(os.sep, "/").split("/"):
            continue
        for name in names:
            if not name.endswith((".java", ".json", ".mcmeta")):
                continue
            path = os.path.join(root, name)
            with open(path, encoding="utf-8", errors="ignore") as f:
                text = f.read()
            blob.append(text)
            if not name.endswith(".java"):
                continue
            for match in KEY_LITERAL.finditer(text):
                key = match.group(1)
                if key.endswith(NOT_A_KEY):
                    continue
                referenced.setdefault(key, set()).add(path.replace(os.sep, "/"))
    return "\n".join(blob), referenced


def is_mentioned(key: str, blob: str) -> bool:
    if key in blob:
        return True
    # The game derives a registry key from the registry, so the source may only
    # ever name the block or item itself.
    if key.startswith(REGISTRY_PREFIXES):
        return key.rsplit(".", 1)[-1] in blob
    return False


def main() -> int:
    langs = load_langs()
    if BASE not in langs:
        print(f"no {BASE}.json in {LANG_DIR}")
        return 1
    blob, referenced = read_sources()
    base_keys = langs[BASE]
    findings = 0

    print("languages: " + ", ".join(f"{name} ({len(keys)})" for name, keys in langs.items()))

    for key in base_keys:
        if not is_mentioned(key, blob):
            print(f"  dead     {key}")
            findings += 1

    for key, where in sorted(referenced.items()):
        if key in base_keys:
            continue
        print(f"  missing  {key}")
        for path in sorted(where):
            print(f"           used by {path}")
        findings += 1

    for name, keys in langs.items():
        if name == BASE:
            continue
        for key in sorted(set(base_keys) - set(keys)):
            print(f"  parity   {name} has no {key}")
            findings += 1
        for key in sorted(set(keys) - set(base_keys)):
            print(f"  parity   {name} has a stray {key}")
            findings += 1

    for key in base_keys:
        counts = {
            name: len(FORMAT_ARG.findall(keys[key])) for name, keys in langs.items() if key in keys
        }
        if len(set(counts.values())) > 1:
            spread = ", ".join(f"{name}={n}" for name, n in sorted(counts.items()))
            print(f"  format   {key} takes a different count per language: {spread}")
            findings += 1

    print(f"{findings} finding(s)" if findings else "clean")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
