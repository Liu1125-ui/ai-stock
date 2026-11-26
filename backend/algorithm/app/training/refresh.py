"""Refresh and publish the evidence consumed by the weekly training scanner."""
import argparse
from contextlib import contextmanager
from datetime import datetime, time, timedelta, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import socket
import tempfile
from time import sleep

from app.inference.rolling_artifacts import unique_fields
from .collect_increment import collect
from .increment import publish
from .readiness import inspect_preset
from .seal import canonical
from .snapshot import checked_read, freeze, parse_date

SHANGHAI = timezone(timedelta(hours=8))


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def load(path):
    return json.loads(checked_read(path, 65536), object_pairs_hook=unique_fields)


def atomic_write(path, data):
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as stream:
        temp = Path(stream.name)
        try:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
            stream.close()
            os.replace(temp, path)
            sync_directory(path.parent)
        finally:
            temp.unlink(missing_ok=True)


def sync_directory(path):
    descriptor = os.open(path, os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


@contextmanager
def publisher_lock(root):
    with (root / ".refresh.lock").open("a+b") as stream:
        try:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            yield False
            return
        yield True


def refresh(root, base, index, index_hash, cohort, cohort_hash, template, start, through, *, sdk=None):
    with publisher_lock(root) as locked:
        if not locked:
            return {"state": "BUSY"}
        current = root / "current"
        for path in (current, root / "archives", root / "snapshots", root / "presets"):
            path.mkdir(exist_ok=True)
        report_path = current / "readiness.json"
        prior = load(report_path) if report_path.exists() else {}
        prior_session = parse_date(prior["expectedSession"]) if prior else None
        if prior_session and prior_session > through:
            return {"state": "OLDER_CUTOFF", "asOf": str(prior_session)}
        if "archiveHash" in prior:
            index_hash = prior["archiveHash"]
            if len(index_hash) != 64 or any(char not in "0123456789abcdef" for char in index_hash):
                raise ValueError("Invalid current archive fingerprint")
            base = root / "archives" / index_hash
            index = base / "index.json"
            start = max(start, prior_session - timedelta(days=18))

        with tempfile.TemporaryDirectory(prefix=".refresh-", dir=root) as directory:
            stage = Path(directory)
            delta = stage / "delta"
            result = collect(base, index, index_hash, start, through, delta, sdk=sdk,
                             already_published=prior_session)
            if result.get("unchanged"):
                atomic_write(current / "calendar.json", canonical(result["calendar"]))
                return {"state": "UNCHANGED", "asOf": result["asOf"]}

            as_of = parse_date(result["asOf"])
            calendar = delta / "calendar.json"
            archive = stage / "archive"
            merged = publish(base, index, index_hash, delta, delta / "index.json", result["indexHash"],
                             calendar, result["calendarHash"], as_of, archive)
            snapshot = stage / "snapshot"
            frozen = freeze(archive, cohort, cohort_hash, archive / "index.json", merged["indexHash"],
                            as_of, snapshot)
            dataset = frozen["datasetHash"]
            training = {"datasetHash": dataset, "seed": template["seed"],
                        "train": template["train"], "validation": template["validation"],
                        "holdout": {**template["holdout"], "through": str(as_of)}}
            preset = canonical({"schemaVersion": 2, "configurationId": template["configurationId"],
                                "datasetHash": dataset, "seed": template["seed"],
                                "epochs": template["epochs"], "trainingData": training})
            staged_preset = stage / "preset.json"
            staged_preset.write_bytes(preset)
            report = inspect_preset(snapshot, snapshot / "manifest.json", staged_preset, as_of)
            if report["state"] != "READY":
                return {"state": report["state"], "asOf": str(as_of), "groups": report["groups"]}
            publish_directory(archive, root / "archives", merged["indexHash"], "index.json")
            publish_directory(snapshot, root / "snapshots", dataset, "manifest.json")
            preset_path = root / "presets" / (digest(preset) + ".json")
            if preset_path.exists():
                checked_read(preset_path, 4096, digest(preset))
            else:
                atomic_write(preset_path, preset)
            report["archiveHash"] = merged["indexHash"]
            # A calendar/report mismatch during this short interval causes the scanner to wait.
            atomic_write(current / "calendar.json", checked_read(calendar, 65536, result["calendarHash"]))
            atomic_write(report_path, canonical(report))
            return {"state": "PUBLISHED", "asOf": str(as_of), "datasetHash": dataset,
                    "configurationHash": digest(preset), "updatedStocks": result["stocks"]}


def publish_directory(staged, parent, fingerprint, index_name):
    destination = parent / fingerprint
    if destination.exists():
        checked_read(destination / index_name, 65536, fingerprint)
    else:
        staged.rename(destination)
        sync_directory(parent)


def completed_cutoff():
    now = datetime.now(SHANGHAI)
    return now.date() if now.time() >= time(20) else now.date() - timedelta(days=1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("root", "base", "index", "cohort", "template"):
        parser.add_argument("--" + name, required=True, type=Path)
    for name in ("index-sha256", "cohort-sha256"):
        parser.add_argument("--" + name, required=True)
    parser.add_argument("--start", required=True, type=parse_date)
    parser.add_argument("--poll-seconds", type=int, default=3600)
    parser.add_argument("--once", action="store_true")
    args = parser.parse_args()
    if args.poll_seconds < 60:
        parser.error("Poll interval must be at least 60 seconds")
    socket.setdefaulttimeout(20)
    last_done = None
    while True:
        through = completed_cutoff()
        if through != last_done:
            try:
                result = refresh(args.root, args.base, args.index, args.index_sha256,
                                 args.cohort, args.cohort_sha256, load(args.template), args.start, through)
                if result["state"] in {"PUBLISHED", "UNCHANGED", "OLDER_CUTOFF"}:
                    last_done = through
                print(canonical(result).decode(), flush=True)
                if args.once:
                    return 0 if last_done else 1
            except (ValueError, OSError, KeyError, TypeError) as error:
                print(canonical({"state": "FAILED", "message": str(error)}).decode(), flush=True)
                if args.once:
                    return 1
        sleep(args.poll_seconds)


if __name__ == "__main__":
    raise SystemExit(main())
