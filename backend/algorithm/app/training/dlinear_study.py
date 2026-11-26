"""Compare the released Transformer with a six-feature DLinear-style ridge baseline."""

import argparse
import json
from pathlib import Path

import numpy as np
import torch

from app.inference.artifacts import ArtifactStore
from .five_day import SPLITS, forecast, load_samples

PENALTIES = (0.01, 0.1, 1.0, 10.0, 100.0)
KERNEL = 25


def design(raw, mean, std):
    normalized = np.clip((raw - mean) / std, -10, 10)
    pad = KERNEL // 2
    padded = np.pad(normalized, ((0, 0), (pad, pad), (0, 0)), mode="edge")
    trend = np.lib.stride_tricks.sliding_window_view(padded, KERNEL, axis=1).mean(axis=-1)
    n = len(raw)
    return np.concatenate(((normalized - trend).reshape(n, -1), trend.reshape(n, -1)), axis=1)


def fit(inputs, labels, penalty):
    center = inputs.mean(axis=0)
    intercept = labels.astype(np.float64).mean(axis=0)
    x = inputs - center
    gram = x.T @ x
    gram.flat[::len(gram) + 1] += penalty * len(inputs)
    coefficients = np.linalg.solve(gram, x.T @ (labels - intercept))
    return center, intercept, coefficients


def predict(inputs, weights):
    center, intercept, coefficients = weights
    return (inputs - center) @ coefficients + intercept


def metrics(predictions, labels):
    if (predictions.shape != labels.shape or labels.ndim != 2 or labels.shape[1] != 5
            or not labels.size or any(not np.isfinite(x).all() or not (x > 0).all()
                                     for x in (predictions, labels))):
        raise ValueError("Invalid predictions or realized prices")
    residual = predictions.astype(np.float64) - labels
    absolute = np.abs(residual)
    direction = np.sign(np.round(predictions - 1, 6)) == np.sign(np.round(labels - 1, 6))
    return {
        "mae": float(absolute.mean()),
        "rmse": float(np.sqrt((residual ** 2).mean())),
        "directionAccuracy": float(direction.mean()),
        "maeByDay": absolute.mean(axis=0).tolist(),
        "directionAccuracyByDay": direction.mean(axis=0).tolist(),
    }


def run(args):
    torch.set_num_threads(2)
    dataset = json.loads(args.manifest.read_bytes())
    splits = dict(SPLITS)
    if dataset["schemaVersion"] == 2:
        splits["heldout2026"] = ("2026-01-01", dataset["asOf"], "heldout_stock")
    rows = load_samples(args.source, args.manifest, args.dataset_sha256, splits=splits)
    meta, transformer, model_mean, model_std = ArtifactStore(args.artifacts).load(args.artifact_id, args.manifest_hash)
    raw, labels = rows["train"]
    center = raw.mean(axis=(0, 1), dtype=np.float64)
    std = np.maximum(raw.std(axis=(0, 1), dtype=np.float64), 1e-6)
    train_x = design(raw, center, std)
    validation, validation_y = rows["validation"]
    validation_x = design(validation, center, std)
    scores = {}
    best = None
    for penalty in PENALTIES:
        weights = fit(train_x, labels, penalty)
        score = metrics(predict(validation_x, weights), validation_y)["mae"]
        scores[str(penalty)] = score
        if best is None or score < best[0]:
            best = score, penalty, weights

    _, selected, weights = best
    periods = {}
    for name in ("heldout2025", "heldout2026"):
        raw, observed = rows[name]
        normalized = torch.from_numpy(np.clip((raw - model_mean) / model_std, -10, 10))
        with torch.inference_mode():
            inferred = torch.cat([forecast(transformer, block) for block in normalized.split(64)]).numpy()
        periods[name] = {
            "period": splits[name][:2],
            "windows": len(observed),
            "priceUnchanged": metrics(np.ones_like(observed), observed),
            "dlinearRidge": metrics(predict(design(raw, center, std), weights), observed),
            "transformer": metrics(inferred, observed),
        }
    return {
        "schemaVersion": 1, "datasetHash": args.dataset_sha256,
        "artifactId": args.artifact_id, "manifestHash": args.manifest_hash,
        "checkpointHash": meta["checkpointHash"], "trainingWindows": len(labels),
        "validationWindows": len(validation_y), "movingAverageKernel": KERNEL,
        "validationMaeByPenalty": scores, "selectedPenalty": selected,
        "periods": periods,
    }


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--source", type=Path, required=True)
    cli.add_argument("--manifest", type=Path, required=True)
    cli.add_argument("--dataset-sha256", required=True)
    cli.add_argument("--artifacts", type=Path, required=True)
    cli.add_argument("--artifact-id", required=True)
    cli.add_argument("--manifest-hash", required=True)
    print(json.dumps(run(cli.parse_args()), indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
