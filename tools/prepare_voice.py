"""Fetch pinned offline ASR assets; verify SHA-256 before making them build inputs.

Run once before Gradle on a new checkout: python tools/prepare_voice.py
Use --direct if the configured proxy cannot reach the release CDN.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import urllib.request


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--direct", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    if args.direct:
        urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
    for entry in json.loads((root / "voice_assets.json").read_text(encoding="utf-8")):
        target = root / ".cache/voice/runtime" / entry["path"]
        target.parent.mkdir(parents=True, exist_ok=True)
        def valid(path):
            if not path.exists():
                return False
            with path.open("rb") as source:
                return hashlib.file_digest(source, "sha256").hexdigest() == entry["sha256"]
        if valid(target):
            print("Verified", entry["path"])
            continue
        temporary = target.with_suffix(target.suffix + ".part")
        for attempt in range(3):
            print("Downloading", entry["path"], "attempt", attempt + 1, flush=True)
            with urllib.request.urlopen(entry["url"], timeout=60) as response, temporary.open("wb") as out:
                shutil.copyfileobj(response, out)
            if valid(temporary):
                temporary.replace(target)
                break
        else:
            raise RuntimeError("Checksum mismatch: " + entry["path"])


if __name__ == "__main__":
    main()
