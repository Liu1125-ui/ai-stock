"""Freeze the local research features used by the five-day training configuration."""

import argparse
import csv
import hashlib
import json
import re
from collections import Counter
from pathlib import Path


def digest(root: Path, relative: str) -> dict:
    path = root / relative
    if path.is_symlink() or not path.resolve().is_relative_to(root) or not path.is_file():
        raise ValueError(f"Invalid training source file: {relative}")
    size = path.stat().st_size
    if not 0 < size <= 64 * 1024 * 1024:
        raise ValueError(f"Unexpected training source size: {relative}")
    with path.open("rb") as stream:
        sha256 = hashlib.file_digest(stream, "sha256").hexdigest()
    return {"path": relative, "bytes": size, "sha256": sha256}


def manifest(root: Path) -> bytes:
    root = root.resolve(strict=True)
    sample_file = "正式样本清单_40只.csv"
    sample_entry = digest(root, sample_file)
    with (root / sample_file).open(encoding="utf-8-sig", newline="") as stream:
        sample_rows = list(csv.DictReader(stream))
    groups = Counter(row.get("role") for row in sample_rows)
    if groups != {"training_stock": 30, "heldout_stock": 10}:
        raise ValueError("Expected 30 training and 10 held-out stocks")
    entries = []
    seen = set()
    for row in sorted(sample_rows, key=lambda value: value["stock"]):
        stock = row["stock"]
        if not re.fullmatch(r"(SH|SZ)[0-9]{6}", stock) or stock in seen:
            raise ValueError("Duplicate or invalid stock in the frozen sample list")
        seen.add(stock)
        entries.append({"stock": stock, "role": row["role"], **digest(root, f"特征数据/{stock}.npz")})
    return (
        json.dumps(
            {
                "schemaVersion": 1,
                "sampleList": sample_entry,
                "featureDefinition": "baostock-six-price-volume-v1",
                "targetDefinition": "adjusted-close-relative-origin-v1",
                "trainingTargetYears": [2023],
                "validationTargetYears": [2024],
                "stocks": entries,
            },
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        ) + "\n"
    ).encode("utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    payload = manifest(args.source)
    if args.verify:
        if args.manifest.read_bytes() != payload:
            raise SystemExit("Frozen training dataset differs from its manifest")
    else:
        destination = args.manifest.resolve()
        if destination.is_relative_to(args.source.resolve()):
            raise SystemExit("Manifest must be stored outside the frozen sources")
        destination.parent.mkdir(parents=True, exist_ok=True)
        with destination.open("xb") as stream:
            stream.write(payload)
    print(hashlib.sha256(payload).hexdigest())


if __name__ == "__main__":
    main()
