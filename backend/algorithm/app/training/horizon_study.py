"""Offline 10- and 20-session Transformer research on a frozen rolling snapshot."""

import argparse
import json
import math
from pathlib import Path
from types import SimpleNamespace

import numpy as np
import torch

from app.inference.artifacts import CONFIG
from app.inference.vendor.model import Model
from .five_day import forecast, load_samples, train
from .snapshot import checked_read

SPLITS = {
    "train": ("2024-07-01", "2026-03-31", "training_stock"),
    "validation": ("2026-04-01", "2026-06-30", "training_stock"),
    "calibration": ("2026-07-01", "2026-09-30", "training_stock"),
    "holdout": ("2026-10-01", "2026-11-13", "heldout_stock"),
}
LONG_HOLDOUT_SPLITS = {
    "train": ("2024-07-01", "2025-12-31", "training_stock"),
    "validation": ("2026-01-01", "2026-03-31", "training_stock"),
    "calibration": ("2026-04-01", "2026-06-30", "training_stock"),
    "holdout": ("2026-07-01", "2026-11-13", "heldout_stock"),
}
HISTORICAL_2025_SPLITS = {
    "train": ("2023-01-01", "2023-12-31", "training_stock"),
    "validation": ("2024-01-01", "2024-06-30", "training_stock"),
    "calibration": ("2024-07-01", "2024-12-31", "training_stock"),
    "holdout": ("2025-01-01", "2025-12-31", "heldout_stock"),
}
COVERAGE = 0.8


def calibrate(errors, coverage):
    residuals = np.asarray(errors)
    if (residuals.ndim != 2 or not np.isfinite(residuals).all()
            or np.any(residuals < 0) or not 0 < coverage < 1):
        raise ValueError("Invalid calibration residuals or requested coverage")
    rank = math.ceil((len(residuals) + 1) * coverage)
    if rank > len(residuals):
        raise ValueError("Too few observations for requested empirical quantile")
    return np.partition(residuals, rank - 1, axis=0)[rank - 1]


def predict(checkpoint, features):
    config = checkpoint["config"]
    mean = np.asarray(checkpoint["mean"], dtype=np.float32)
    scale = np.asarray(checkpoint["std"], dtype=np.float32)
    normalized = torch.from_numpy(np.clip((features - mean) / scale, -10, 10))
    model = Model(SimpleNamespace(**config))
    model.load_state_dict(checkpoint["state_dict"], strict=True)
    model.eval()
    with torch.inference_mode():
        predictions = torch.cat([forecast(model, block, config=config) for block in normalized.split(64)]).numpy()
    if not np.isfinite(predictions).all() or np.any(predictions <= 0):
        raise ValueError("Invalid prediction ratios")
    return predictions


def metrics(actual, predicted):
    errors = predicted - actual
    directional = np.sign(np.round(predicted - 1, 6)) == np.sign(np.round(actual - 1, 6))
    return {"mae": float(np.abs(errors).mean()),
            "rmse": float(np.sqrt(np.mean(errors ** 2))),
            "direction": float(directional.mean())}


def interval_metrics(calibrated_errors, actual, predicted):
    radii = calibrate(calibrated_errors, COVERAGE)
    low = np.maximum(predicted - radii, 0)
    high = predicted + radii
    included = (actual >= low) & (actual <= high)
    return {"targetMarginalCoverage": COVERAGE,
            "coverageByDay": included.mean(axis=0).tolist(),
            "pooledCoverage": float(included.mean()),
            "entirePathCoverage": float(included.all(axis=1).mean()),
            "meanWidthByDay": (high - low).mean(axis=0).tolist(),
            "radiusByDay": radii.tolist()}


def assess(checkpoint, rows):
    calibration_x, calibration_y = rows["calibration"]
    inputs, actual = rows["holdout"]
    predicted = predict(checkpoint, inputs)
    return {"path": metrics(actual, predicted),
            "finalDay": metrics(actual[:, -1], predicted[:, -1]),
            "baselinePath": metrics(actual, np.ones_like(actual)),
            "baselineFinalDay": metrics(actual[:, -1], np.ones(len(actual), dtype=actual.dtype)),
            "interval": interval_metrics(
                np.abs(predict(checkpoint, calibration_x) - calibration_y), actual, predicted)}


def run(root, manifest_hash, seeds, *, splits=SPLITS):
    manifest = root / "manifest.json"
    metadata = json.loads(checked_read(manifest, 65536, manifest_hash))
    if metadata.get("schemaVersion") != 2:
        raise ValueError("The experiment requires a verified rolling snapshot")
    shared = load_samples(root, manifest, manifest_hash, splits=splits, horizon=20)
    experiments = []
    naive_intervals = {}
    for horizon in (10, 20):
        config = CONFIG | {"pred_len": horizon}
        rows = {name: (inputs, labels[:, :horizon].copy())
                for name, (inputs, labels) in shared.items()}
        actual = rows["holdout"][1]
        naive_intervals[horizon] = interval_metrics(
            np.abs(rows["calibration"][1] - 1), actual, np.ones_like(actual))
        for seed in seeds:
            checkpoint, history, _ = train(
                {name: rows[name] for name in ("train", "validation")}, seed, 3,
                cutoff=splits["train"][1], config=config)
            experiments.append({"horizon": horizon, "seed": seed,
                                "selectedEpoch": checkpoint["epoch"], "validationHistory": history,
                                **assess(checkpoint, rows)})
    return {"datasetHash": manifest_hash, "asOf": metadata["asOf"], "splits": splits,
            "windows": {name: len(inputs) for name, (inputs, _) in shared.items()},
            "experiments": experiments, "naiveIntervals": naive_intervals}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--dataset-sha256", required=True)
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--long-holdout", action="store_true")
    group.add_argument("--historical-2025", action="store_true")
    args = parser.parse_args()
    splits = SPLITS
    if args.long_holdout:
        splits = LONG_HOLDOUT_SPLITS
    elif args.historical_2025:
        splits = HISTORICAL_2025_SPLITS
    result = run(args.source, args.dataset_sha256, (42, 43), splits=splits)
    print(json.dumps(result, allow_nan=False, indent=2))


if __name__ == "__main__":
    main()
