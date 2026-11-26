"""Batch preflight for existing stock expansion candidates and their evidence."""

import argparse
import hashlib
import json
from pathlib import Path

import numpy as np

from app.inference.artifacts import InferenceError
from app.inference.engine import InferenceEngine
from app.inference.expansion_artifacts import (
    EVIDENCE_LIMIT, ExpansionArtifactStore, assemble, encoded, parse,
)
from app.inference.rolling_artifacts import VersionedArtifactStore
from .stock_expansion import build_evidence


def read_json(path, limit):
    with path.open("rb") as stream:
        payload = stream.read(limit + 1)
    if len(payload) > limit:
        raise ValueError(f"{path.name} is too large")
    return payload, parse(payload)


def compare_replay(saved, replayed):
    identity_fields = (
        "schemaVersion", "instrumentId", "artifactId", "manifestHash", "checkpointHash",
        "sourceFileSha256", "sourcePayloadHash", "targetDefinition", "requestedPeriod", "excludedWindows",
    )
    if any(saved[key] != replayed[key] for key in identity_fields):
        raise ValueError("Source or backtest identity differs from the evidence")
    originals, results = saved["samples"], replayed["samples"]
    if len(originals) != len(results):
        raise ValueError("Backtest window count differs from the evidence")
    for before, after in zip(originals, results):
        if (before["origin"] != after["origin"] or before["targetDates"] != after["targetDates"]
                or not np.array_equal(before["actual"], after["actual"])
                or not np.allclose(before["predicted"], after["predicted"], rtol=0, atol=0.000002)):
            raise ValueError(f"Backtest values differ at {after['origin']}")


def performance_flags(scores):
    flags = []
    if scores["windows"] < 60:
        flags.append("FEWER_THAN_60_BACKTEST_WINDOWS")
    gain = scores["baselineMae"] - scores["mae"]
    if gain <= 0:
        flags.append("AGGREGATE_MAE_NOT_BELOW_BASELINE")
    elif gain < 0.0005:
        flags.append("SMALL_BASELINE_GAIN")
    for day, (error, baseline) in enumerate(
        zip(scores["horizonMae"], scores["horizonBaselineMae"], strict=True), 1
    ):
        if error > baseline:
            flags.append(f"HORIZON_{day}_ABOVE_BASELINE")
    return flags


def audit_case(root, folder):
    payload, saved = read_json(folder / "evidence.json", EVIDENCE_LIMIT)
    parent_id, parent_hash = saved["artifactId"], saved["manifestHash"]
    parent, _, _, _ = VersionedArtifactStore(root).load(parent_id, parent_hash)
    manifest = assemble(parent_id, parent_hash, parent, payload)
    candidate_id = manifest["artifactId"]
    candidate_hash = hashlib.sha256(encoded(manifest)).hexdigest()
    scores_period = saved["requestedPeriod"]
    replayed = build_evidence(root, parent_id, parent_hash, folder / "source.json",
                              scores_period["from"], scores_period["through"])
    compare_replay(saved, replayed)

    engine = InferenceEngine(root)
    engine.store = ExpansionArtifactStore(root)
    _, window = read_json(folder / "window.json", 1_048_576)
    if window["instrumentId"] != manifest["instruments"][0] or window["freshness"] != "FRESH":
        raise ValueError("Expected a saved fresh input window for the expansion stock")
    output = engine.validate(candidate_id, candidate_hash, window)
    if len(output["points"]) != 5 or [point["targetDate"] for point in output["points"]] != window["targetDates"]:
        raise ValueError("Candidate failed the five-day output check")

    scores = replayed["scores"]
    flags = performance_flags(scores)
    return {
        "status": "REVIEW_REQUIRED" if flags else "TECHNICAL_PASS",
        "instrumentId": manifest["instruments"][0], "candidateArtifactId": candidate_id,
        "candidateManifestHash": candidate_hash, "evidenceHash": manifest["expansion"]["evidenceHash"],
        "replayPeriod": scores_period, "scores": scores, "flags": flags,
        "candidateCheck": {"origin": window["originDate"], "inputVersionId": window["inputVersionId"],
                           "fivePoints": len(output["points"]), "freshnessAtSnapshot": window["freshness"]},
    }


def audit_batch(root, folders):
    counts = {"TECHNICAL_PASS": 0, "REVIEW_REQUIRED": 0, "BLOCKED": 0}
    cases = []
    for folder in folders:
        try:
            result = audit_case(root, folder)
        except (ValueError, OSError, InferenceError, KeyError, TypeError, ArithmeticError, RuntimeError) as error:
            result = {"status": "BLOCKED", "reason": getattr(error, "code", str(error))}
        counts[result["status"]] += 1
        cases.append({"case": folder.name, **result})
    return {"schemaVersion": 1, "counts": counts, "cases": cases}


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--artifact-root", required=True, type=Path)
    cli.add_argument("--case", required=True, action="append", type=Path,
                     help="folder containing source.json, evidence.json and window.json; repeat for more stocks")
    args = cli.parse_args()
    folders = list(dict.fromkeys(folder.resolve() for folder in args.case))
    if len(folders) > 50:
        cli.error("A batch may contain at most 50 cases")
    result = audit_batch(args.artifact_root, folders)
    print(json.dumps(result, ensure_ascii=True, indent=2, allow_nan=False))
    return 2 if result["counts"]["BLOCKED"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
