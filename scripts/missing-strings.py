#!/usr/bin/env python3
"""Find the sentences the app looks up that a string table lacks.

Every literal key passed to `AppLocalization.string`, `format` or
`formatCount` in the app's Kotlin must be in each of the three languages in
`app/src/main/assets/strings/`, either as a translation or, in English, as
itself when the sentence differs from its key. A language is its shared
`RuntimeStrings` table and the app's own `AndroidStrings` table read as one.
A key in no table reads as itself, which is English on a Chinese screen. Keys
built at run time cannot be read here: core's sentences and wording passed
through a variable.

The app's own tables are checked too: a key there that the shared table also
has shadows it, and a key one language has and another lacks falls back to
English.

Prints each finding, and exits non-zero when it found any.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCES = ROOT / "app/src/main/kotlin"
TABLES = ROOT / "app/src/main/assets/strings"
LOCALES = ["en", "zh-Hans", "zh-Hant"]

CALL = re.compile(r"AppLocalization\.(?:string|format|formatCount)\(")
LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')


def first_argument(text: str, start: int) -> str:
    """The first argument of the call whose parenthesis opens at `start`: the
    key, or an `if` or `when` that picks one."""
    depth = 0
    index = start
    in_string = False
    while index < len(text):
        char = text[index]
        if in_string:
            if char == "\\":
                index += 1
            elif char == '"':
                in_string = False
        elif char == '"':
            in_string = True
        elif char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
            if depth == 0:
                return text[start + 1:index]
        elif char == "," and depth == 1:
            return text[start + 1:index]
        index += 1
    return text[start + 1:]


def kotlin_literal(text: str) -> str:
    return text.encode().decode("unicode_escape").encode("latin-1").decode("utf-8").replace("\\$", "$")


def main() -> int:
    shared = {loc: json.loads((TABLES / f"RuntimeStrings.{loc}.json").read_text()) for loc in LOCALES}
    own = {loc: json.loads((TABLES / f"AndroidStrings.{loc}.json").read_text()) for loc in LOCALES}
    tables = {loc: {**shared[loc], **own[loc]} for loc in LOCALES}
    conflicts = 0
    for loc in LOCALES:
        for key in sorted(own[loc].keys() & shared[loc].keys()):
            conflicts += 1
            print(f"AndroidStrings.{loc}.json: {key!r} is also in RuntimeStrings.{loc}.json")
    own_keys = set().union(*(own[loc].keys() for loc in LOCALES))
    for loc in LOCALES:
        for key in sorted(own_keys - own[loc].keys()):
            conflicts += 1
            print(f"AndroidStrings.{loc}.json: {key!r} is missing, though another language has it")
    used: dict[str, str] = {}
    for path in sorted(SOURCES.rglob("*.kt")):
        text = path.read_text()
        for match in CALL.finditer(text):
            argument = first_argument(text, match.end() - 1)
            for literal in LITERAL.finditer(argument):
                # An empty fallback and a template built at run time are not keys.
                if not literal.group(1) or re.search(r"\$[{A-Za-z_]", literal.group(1)):
                    continue
                used.setdefault(kotlin_literal(literal.group(1)), str(path.relative_to(ROOT)))
    missing = 0
    for key, where in sorted(used.items()):
        # English reads a key as itself; only a dotted name needs its words.
        lacking = [loc for loc in LOCALES if key not in tables[loc] and not (loc == "en" and "." not in key.split(" ")[0])]
        if lacking:
            missing += 1
            print(f"{where}: {key!r} missing from {', '.join(lacking)}")
    print(f"{missing} missing of {len(used)} keys, {conflicts} inconsistent in AndroidStrings")
    return 1 if missing or conflicts else 0


if __name__ == "__main__":
    sys.exit(main())
