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

`drift` -- an en_us entry that disagrees with the English our source passes
for that key. Only Ponder keys can drift, because only they carry their
English twice: once in the storyboard, once in the lang file.

Registry keys (`block.`, `item.`, `itemGroup.`) are looked for by their
identifier as well as by the whole key, since the game builds those from the
registry rather than from a literal in our source. Schedule keys join them:
Create names an instruction's dropdown entry by pasting its ResourceLocation
together, so the only thing our source ever spells is the path.

Ponder keys get neither treatment, because neither would catch what actually
goes wrong with them. Ponder numbers a scene's captions by the order the
`.text(...)` calls run -- `text_1`, `text_2`, and so on -- and nothing in the
storyboard ever spells a key. Insert one caption in the middle of a finished
scene and every key after it shifts by one: the scene still plays, every
caption after the insertion is a sentence from the following beat, and both
the game and a prefix-matching audit stay silent about it. So this reads the
storyboards the way Ponder does, counts the calls, and rebuilds the key list
from scratch. That is also where the English comes from, which is what makes
`drift` possible.

Exits non-zero when anything is reported, so it can gate a commit.
"""

from __future__ import annotations

import json
import os
import re
import sys

# This repo is two mods sharing one build: each has its own module directory,
# its own lang directory, and its own key prefix.
MODULES = {
    "create_transit": os.path.join("transit", "src", "main", "resources", "assets", "create_transit", "lang"),
    "create_routes": os.path.join("route", "src", "main", "resources", "assets", "create_routes", "lang"),
}
SRC_ROOTS = {
    os.path.join("route", "src"): "create_routes",
    os.path.join("transit", "src"): "create_transit",
}
BASE = "en_us"

REGISTRY_PREFIXES = ("block.", "item.", "itemGroup.")

# A translation key in a string literal: a dotted path naming one of this
# repo's mods. Resource locations do not match -- those carry a colon and slashes.
KEY_LITERAL = re.compile(r'"((?:[a-z_]+\.)*(?:%s)\.[A-Za-z0-9_.]+)"' % "|".join(MODULES))

# %s and its indexed form, which Minecraft accepts interchangeably.
FORMAT_ARG = re.compile(r"%(?:\d+\$)?s")

# Files that mention keys for reasons other than using them.
NOT_A_KEY = (".json", ".png", ".mixins", ".refmap")

# A Java string literal, escapes and all, for the Ponder patterns below.
LITERAL = r'"((?:[^"\\]|\\.)*)"'

# `scene.title("stock_link", "Logistics Networks and the Stock Link")` opens a
# storyboard and names it; every `.text(...)` until the next title belongs to
# it. `.sharedText(...)` is not one of them -- Ponder skips it when numbering,
# and it does not match here either, since its own dot is followed by `shared`.
PONDER_TITLE = re.compile(r"\.title\(\s*%s\s*,\s*%s\s*\)" % (LITERAL, LITERAL))
PONDER_TEXT = re.compile(r"\.text\(\s*%s" % LITERAL)
# A tag is a builder chain that has to end in `.register()`.
PONDER_TAG = re.compile(r"\.registerTag\(\s*%s\s*\)(.*?)\.register\(\)" % LITERAL, re.S)
PONDER_TAG_TITLE = re.compile(r"\.title\(\s*%s\s*\)" % LITERAL)
PONDER_TAG_DESC = re.compile(r"\.description\(\s*%s\s*\)" % LITERAL)


def load_langs(lang_dir: str) -> dict[str, dict[str, str]]:
    langs = {}
    for name in sorted(os.listdir(lang_dir)):
        if not name.endswith(".json"):
            continue
        with open(os.path.join(lang_dir, name), encoding="utf-8") as f:
            langs[name[:-5]] = json.load(f)
    return langs


def unescape(literal: str) -> str:
    """A Java string literal's own escapes, which the lang JSON does not repeat."""
    return literal.replace('\\"', '"').replace("\\n", "\n").replace("\\\\", "\\")


def derive_ponder(text: str, mod_id: str) -> dict[str, str]:
    """The keys Ponder builds from a storyboard, and the English it was given.

    `PonderLocalization` names a scene's title `header` and its captions
    `text_1` upwards, counting `.text(...)` calls in the order they run. Read
    the same way, so a caption inserted mid-scene shows up here as every later
    key changing its English rather than as nothing at all.
    """
    derived = {}
    titles = list(PONDER_TITLE.finditer(text))
    for i, title in enumerate(titles):
        scene = title.group(1)
        until = titles[i + 1].start() if i + 1 < len(titles) else len(text)
        derived[f"{mod_id}.ponder.{scene}.header"] = unescape(title.group(2))
        for n, caption in enumerate(PONDER_TEXT.finditer(text, title.end(), until), 1):
            derived[f"{mod_id}.ponder.{scene}.text_{n}"] = unescape(caption.group(1))
    for tag in PONDER_TAG.finditer(text):
        name, chain = tag.group(1), tag.group(2)
        for suffix, pattern in (("", PONDER_TAG_TITLE), (".description", PONDER_TAG_DESC)):
            found = pattern.search(chain)
            if found:
                derived[f"{mod_id}.ponder.tag.{name}{suffix}"] = unescape(found.group(1))
    return derived


def read_sources() -> tuple[str, dict[str, set[str]], dict[str, tuple[str, str]]]:
    """Everything in either module outside its lang directory, plus where each key literal was seen."""
    blob = []
    referenced: dict[str, set[str]] = {}
    derived: dict[str, tuple[str, str]] = {}
    for src_root, mod_id in SRC_ROOTS.items():
        for root, _, names in os.walk(src_root):
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
                for key, english in derive_ponder(text, mod_id).items():
                    derived[key] = (english, path.replace(os.sep, "/"))
                    referenced.setdefault(key, set()).add(path.replace(os.sep, "/"))
    return "\n".join(blob), referenced, derived


def is_mentioned(key: str, blob: str, mod_id: str, derived: dict[str, tuple[str, str]]) -> bool:
    # A Ponder key is spelled nowhere, so the storyboards were read instead.
    if key in derived:
        return True
    # Quoted, because a bare substring makes any key that is a prefix of another
    # invisible: `...window.stop` was carried for free by `...window.stops` long
    # after the only thing that used it was deleted.
    if '"%s"' % key in blob:
        return True
    # The game derives a registry key from the registry, so the source may only
    # ever name the block or item itself. Schedule keys join them: Create names
    # an instruction's dropdown entry by pasting its ResourceLocation together,
    # so the only thing our source ever spells is the path.
    if key.startswith(REGISTRY_PREFIXES) or key.startswith(f"{mod_id}.schedule."):
        return key.rsplit(".", 1)[-1] in blob
    return False


def audit(
    mod_id: str,
    lang_dir: str,
    blob: str,
    referenced: dict[str, set[str]],
    derived: dict[str, tuple[str, str]],
) -> int:
    langs = load_langs(lang_dir)
    if BASE not in langs:
        print(f"no {BASE}.json in {lang_dir}")
        return 1
    base_keys = langs[BASE]
    # Only this module's own keys -- the other module's are somebody else's audit.
    mod_referenced = {k: v for k, v in referenced.items() if k.startswith(f"{mod_id}.")}
    findings = 0

    print(f"{mod_id}: " + ", ".join(f"{name} ({len(keys)})" for name, keys in langs.items()))

    for key in base_keys:
        if not is_mentioned(key, blob, mod_id, derived):
            print(f"  dead     {key}")
            findings += 1

    for key, (english, path) in sorted(derived.items()):
        if not key.startswith(f"{mod_id}.") or key not in base_keys:
            continue
        if base_keys[key] != english:
            print(f"  drift    {key}")
            print(f"           {path} says {english!r}")
            print(f"           {BASE}.json says {base_keys[key]!r}")
            findings += 1

    for key, where in sorted(mod_referenced.items()):
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

    return findings


SELF_CHECK_SOURCE = '''
    scene.title("first", "A Title");
    scene.overlay().showText(80).text("one").attachKeyFrame();
    scene.overlay().showText(80).sharedText("rpm8");
    scene.overlay().showText(80).text("two \\"quoted\\"");
    scene.title("second", "Another");
    scene.overlay().showText(80).text("one again");
    helper.registerTag("transit").addToIndex().title("Transit").description("Crossing.").register();
'''

SELF_CHECK_EXPECTED = {
    "m.ponder.first.header": "A Title",
    "m.ponder.first.text_1": "one",
    "m.ponder.first.text_2": 'two "quoted"',
    "m.ponder.second.header": "Another",
    "m.ponder.second.text_1": "one again",
    "m.ponder.tag.transit": "Transit",
    "m.ponder.tag.transit.description": "Crossing.",
}


def main() -> int:
    # The Ponder half is a parser, and a parser that quietly stops matching
    # reports a clean run rather than a broken one. `sharedText` not taking a
    # number, and the count restarting at the next title, are the two things
    # it has to keep getting right.
    assert derive_ponder(SELF_CHECK_SOURCE, "m") == SELF_CHECK_EXPECTED

    blob, referenced, derived = read_sources()
    findings = 0
    for mod_id, lang_dir in MODULES.items():
        findings += audit(mod_id, lang_dir, blob, referenced, derived)

    print(f"{findings} finding(s)" if findings else "clean")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
