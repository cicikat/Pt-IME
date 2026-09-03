#!/usr/bin/env python3
"""Read-only facts required by the 2026-08-11 pinyin ranking work order.

This script never rewrites the shipped database. It is intentionally a small
stdlib-only audit so it can run before Android/Room is available on a checkout.
"""

from __future__ import annotations

import argparse
import sqlite3
from pathlib import Path


EXPECTED_XUAN = ["选", "玄", "宣", "旋", "轩", "悬"]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "db",
        nargs="?",
        type=Path,
        default=Path("app/src/main/assets/lexicon.db"),
        help="path to the shipped lexicon.db",
    )
    args = parser.parse_args()

    if not args.db.is_file():
        raise SystemExit(f"missing lexicon database: {args.db}")

    with sqlite3.connect(args.db) as connection:
        count = connection.execute("SELECT COUNT(*) FROM lexicon").fetchone()[0]
        xuan_rows = connection.execute(
            "SELECT id, word, base_freq FROM lexicon "
            "WHERE pinyin_key = ? ORDER BY base_freq DESC, id ASC",
            ("xuan",),
        ).fetchall()
        juese_count = connection.execute(
            "SELECT COUNT(*) FROM lexicon WHERE pinyin_key = ?", ("juese",)
        ).fetchone()[0]
        yx_rows = connection.execute(
            "SELECT id, pinyin_key, word, base_freq FROM lexicon "
            "WHERE initials = ? ORDER BY base_freq DESC, id ASC LIMIT 20",
            ("yx",),
        ).fetchall()

    xuan_words = [row[1] for row in xuan_rows[: len(EXPECTED_XUAN)]]
    if xuan_words != EXPECTED_XUAN:
        raise SystemExit(
            "xuan exact ranking drifted: "
            f"expected {EXPECTED_XUAN!r}, got {xuan_words!r}"
        )
    if juese_count != 0:
        raise SystemExit(
            f"juese must be absent from the static lexicon, found {juese_count} row(s)"
        )

    print(f"lexicon rows: {count}")
    print(f"xuan exact top six: {xuan_words}")
    print(f"juese static rows: {juese_count}")
    print(f"yx initialism sample: {yx_rows}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
