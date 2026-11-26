"""Reproduce stock expansion evidence using an existing immutable model."""

import argparse
from datetime import date
from decimal import Decimal, ROUND_HALF_UP
import hashlib
import json
from pathlib import Path
import re

import numpy as np
import torch

from app.inference.artifacts import InferenceError
from app.inference.contracts import prepare_window
from app.inference.rolling_artifacts import VersionedArtifactStore
from .five_day import HORIZON, WINDOW, forecast


def select_windows(days, start, end):
    return [
        i for i in range(len(days) - 1 - HORIZON, WINDOW - 2, -HORIZON)
        if start <= days[i] and days[i + HORIZON] <= end
    ]


def score_predictions(predicted, actual):
    if (predicted.shape != actual.shape or predicted.ndim != 2
            or predicted.shape[1] != HORIZON or not predicted.size
            or any(not np.isfinite(values).all() or not (values > 0).all()
                   for values in (predicted, actual))):
        raise ValueError("Invalid five-day results")
    error = np.abs(predicted - actual)
    baseline = np.abs(1 - actual)
    direction = np.sign(np.round(predicted - 1, 6)) == np.sign(np.round(actual - 1, 6))
    return {
        "windows": len(actual),
        "mae": float(error.mean()),
        "baselineMae": float(baseline.mean()),
        "directionAccuracy": float(direction.mean()),
        "horizonMae": error.mean(axis=0).tolist(),
        "horizonBaselineMae": baseline.mean(axis=0).tolist(),
    }


def build_evidence(root, artifact_id, manifest_hash, source, start, end):
    date.fromisoformat(start)
    date.fromisoformat(end)
    meta, model, mean, std = VersionedArtifactStore(root).load(artifact_id, manifest_hash)
    validation_end = meta.get("trainingData", {}).get("validation", {}).get("through", "2024-12-31")
    if not max(meta["trainingCutoff"], validation_end) < start <= end:
        raise ValueError("Evaluation must follow training and validation")
    with source.open("rb") as stream:
        payload = stream.read(2_097_153)
    if len(payload) > 2_097_152:
        raise ValueError("Source snapshot too large")
    response = json.loads(payload)
    if response["state"] != "AVAILABLE":
        raise ValueError("Source snapshot is unavailable")
    candidate = response["candidate"]
    stock = candidate["instrumentId"]
    if (not re.fullmatch(r"(SH|SZ)[0-9]{6}", stock) or stock in meta["instruments"]
            or candidate["provider"] != "baostock" or candidate["definition"] != meta["sourceDefinition"]):
        raise ValueError("Expected compatible expansion stock")
    calendar = candidate["calendar"]
    all_days = [date.fromisoformat(item["date"]) for item in calendar]
    if (not all_days or not all_days[0].isoformat() <= start <= end <= all_days[-1].isoformat()
            or any((after-before).days != 1 for before, after in zip(all_days, all_days[1:]))
            or any(type(item["open"]) is not bool for item in calendar)):
        raise ValueError("Incomplete source calendar")
    days = [item["date"] for item in calendar if item["open"]]
    bars = {}
    for item in candidate["bars"]:
        raw = item["raw"].copy()
        day = raw["date"]
        if day in bars or day not in days:
            raise ValueError("Duplicate or non-trading source date")
        bars[day] = raw, Decimal(item["backwardClose"])

    source_hash = hashlib.sha256(payload).hexdigest()
    source_version = candidate["rawVersionId"]
    window_identity = {
        "instrumentId": stock, "provider": "baostock", "priceBasis": "RAW",
        "definition": meta["sourceDefinition"], "freshness": "UNKNOWN",
        "rawVersionId": source_version, "inputVersionId": source_version,
        "rawContentHash": source_hash, "inputHash": source_hash, "calendarHash": source_hash,
    }
    inputs, targets, samples = [], [], []
    excluded = {"missingOrSuspended": 0, "invalidInput": 0}
    evaluation_days = [day for day in days if day <= end]
    for index in select_windows(evaluation_days, start, end):
        past = days[index-WINDOW+1:index+1]
        future = days[index+1:index+HORIZON+1]
        if any(day not in bars or bars[day][0]["tradeStatus"] != 1 for day in past + future):
            excluded["missingOrSuspended"] += 1
            continue
        rows = []
        for day in past:
            raw, adjusted = bars[day]
            factor = adjusted / Decimal(raw["close"])
            ratio = factor.quantize(Decimal(".000000000001"), rounding=ROUND_HALF_UP)
            rows.append(dict(raw, sourceAdjustmentRatio=format(ratio, "f")))
        try:
            features, _ = prepare_window(
                dict(window_identity, bars=rows, targetDates=future, originDate=past[-1],
                     originRawClose=rows[-1]["close"]), [stock])
        except InferenceError:
            excluded["invalidInput"] += 1
            continue
        inputs.append(features)
        targets.append([float(bars[day][1] / bars[past[-1]][1]) for day in future])
        samples.append({"origin": past[-1], "targetDates": future})
    if not inputs:
        raise ValueError("No usable evaluation windows")

    torch.set_num_threads(2)
    x = torch.from_numpy(np.clip((np.stack(inputs) - mean) / std, -10, 10).astype(np.float32))
    actual = np.asarray(targets)
    with torch.inference_mode():
        predicted = torch.cat([forecast(model, block) for block in x.split(64)]).numpy()
    scores = score_predictions(predicted, actual)
    for index, sample in enumerate(samples):
        sample.update(actual=actual[index].tolist(), predicted=predicted[index].tolist())
    return {
        "schemaVersion": 1, "instrumentId": stock, "artifactId": artifact_id,
        "manifestHash": manifest_hash, "checkpointHash": meta["checkpointHash"],
        "sourceFileSha256": source_hash, "sourcePayloadHash": candidate["sourcePayloadHash"],
        "targetDefinition": meta["targetDefinition"], "requestedPeriod": {"from": start, "through": end},
        "excludedWindows": excluded, "scores": scores, "samples": samples,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact-root", type=Path, required=True)
    parser.add_argument("--artifact-id", required=True)
    parser.add_argument("--manifest-hash", required=True)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--from", dest="start", required=True)
    parser.add_argument("--through", required=True)
    args = parser.parse_args()
    evidence = build_evidence(args.artifact_root, args.artifact_id, args.manifest_hash,
                              args.source, args.start, args.through)
    print(json.dumps(evidence, sort_keys=True, separators=(",", ":"), allow_nan=False))


if __name__ == "__main__":
    main()
