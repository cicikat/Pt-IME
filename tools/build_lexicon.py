#!/usr/bin/env python3
"""Build lexicon.db for JadeBoard's TrieLexiconEngine from rime-ice dictionaries.

Downloads (and caches) two rime-ice yaml dicts:
  - cn_dicts/8105.dict.yaml  -- the 8105-character table, always kept in full
  - cn_dicts/base.dict.yaml  -- the base word/phrase list, filtered by --min-word-freq

and writes a SQLite db with a single table:

    lexicon(pinyin_key TEXT, word TEXT, base_freq INTEGER)

where pinyin_key is the toneless full-pinyin string with no separators
(e.g. "nihao" for 你好) and initials is the first letter of each syllable
(e.g. "nh" for 你好), matching DESIGN.md 3.3.

Usage:
    python tools/build_lexicon.py
    python tools/build_lexicon.py --min-word-freq 1000 --refresh
"""
from __future__ import annotations

import argparse
import re
import sqlite3
import urllib.request
from pathlib import Path

RIME_ICE_RAW = "https://raw.githubusercontent.com/iDvel/rime-ice/main"

# (path within the rime-ice repo, is_char_table)
# Char table entries are always kept regardless of frequency since single
# characters are the fallback building blocks for any pinyin string.
SOURCES = [
    ("cn_dicts/8105.dict.yaml", True),
    ("cn_dicts/base.dict.yaml", False),
]

PINYIN_RE = re.compile(r"^[a-z]+( [a-z]+)*$")


def fetch(rel_path: str, cache_dir: Path, refresh: bool) -> Path:
    cache_dir.mkdir(parents=True, exist_ok=True)
    dest = cache_dir / Path(rel_path).name
    if dest.exists() and not refresh:
        return dest
    url = f"{RIME_ICE_RAW}/{rel_path}"
    print(f"downloading {url} -> {dest}")
    with urllib.request.urlopen(url, timeout=60) as resp:
        dest.write_bytes(resp.read())
    return dest


def parse_dict(path: Path):
    """Yield (word, pinyin_key, initials, freq) tuples from a rime dict.yaml file.

    rime dict.yaml files start with a YAML header terminated by a lone
    "..." line, followed by tab-separated "word\\tpinyin\\tfreq" rows, where
    pinyin is space-separated syllables (e.g. "ni hao").
    """
    started = False
    with path.open(encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not started:
                if line.strip() == "...":
                    started = True
                continue
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) != 3:
                continue
            word, pinyin, freq = parts
            if not word or not PINYIN_RE.match(pinyin):
                continue
            try:
                freq_val = int(freq)
            except ValueError:
                continue
            syllables = pinyin.split(" ")
            initials = "".join(s[0] for s in syllables)
            yield word, pinyin.replace(" ", ""), initials, freq_val


def collect(cache_dir: Path, refresh: bool, min_word_freq: int) -> dict[tuple[str, str], tuple[str, int]]:
    """Returns {(pinyin_key, word): (initials, base_freq)}, deduped by max frequency."""
    merged: dict[tuple[str, str], tuple[str, int]] = {}
    for rel_path, is_char_table in SOURCES:
        src = fetch(rel_path, cache_dir, refresh)
        kept = 0
        for word, pinyin_key, initials, freq in parse_dict(src):
            if not is_char_table and freq < min_word_freq:
                continue
            key = (pinyin_key, word)
            if freq > merged.get(key, ("", -1))[1]:
                merged[key] = (initials, freq)
            kept += 1
        print(f"{rel_path}: kept {kept} entries")
    return merged


def build_db(entries: dict[tuple[str, str], tuple[str, int]], out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    if out_path.exists():
        out_path.unlink()
    conn = sqlite3.connect(out_path)
    conn.execute(
        "CREATE TABLE lexicon ("
        # NOT NULL is required even on an INTEGER PRIMARY KEY: SQLite doesn't
        # imply it, and Room's prepackaged-db schema validator checks nullability
        # exactly against the (always non-null) @PrimaryKey field -- a bare
        # "PRIMARY KEY AUTOINCREMENT" here fails that check at runtime.
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
        "pinyin_key TEXT NOT NULL, "
        "initials TEXT NOT NULL, "
        "word TEXT NOT NULL, "
        "base_freq INTEGER NOT NULL)"
    )
    conn.executemany(
        "INSERT INTO lexicon (pinyin_key, initials, word, base_freq) VALUES (?, ?, ?, ?)",
        (
            (pinyin_key, initials, word, freq)
            for (pinyin_key, word), (initials, freq) in entries.items()
        ),
    )
    conn.execute("CREATE INDEX idx_lexicon_pinyin_key ON lexicon(pinyin_key)")
    conn.execute("CREATE INDEX idx_lexicon_initials ON lexicon(initials)")
    conn.commit()
    conn.close()


def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument(
        "--min-word-freq",
        type=int,
        default=2000,
        help="drop multi-character words below this base_freq; single "
        "characters are always kept. Default 2000 lands the merged "
        "lexicon near rime-ice's ~10-20万 word target (default: 2000)",
    )
    parser.add_argument(
        "--cache-dir",
        type=Path,
        default=Path(__file__).parent / ".cache",
        help="where downloaded rime-ice yaml files are cached",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=Path(__file__).parent.parent / "app/src/main/assets/lexicon.db",
        help="output sqlite db path",
    )
    parser.add_argument(
        "--refresh", action="store_true", help="re-download cached yaml files"
    )
    args = parser.parse_args()

    entries = collect(args.cache_dir, args.refresh, args.min_word_freq)
    print(f"total entries: {len(entries)}")
    build_db(entries, args.out)
    print(f"wrote {args.out} ({args.out.stat().st_size / 1024:.0f} KB)")


if __name__ == "__main__":
    main()
