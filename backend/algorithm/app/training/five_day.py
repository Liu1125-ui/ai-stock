"""Run a bounded five-day Transformer training experiment on frozen local features."""

import argparse
import hashlib
import json
import math
import re
import tempfile
import time
from pathlib import Path
from types import SimpleNamespace

import numpy as np
import torch
from torch.utils.data import DataLoader, TensorDataset

from app.inference.artifacts import CONFIG, FEATURE, TARGET, digest_file
from app.inference.vendor.model import Model
from .snapshot import checked_read, parse_date, verify_archived_source

WINDOW = CONFIG["seq_len"]
HORIZON = CONFIG["pred_len"]
SPLITS = {
    "train": ("2023-01-01", "2023-12-31", "training_stock"),
    "validation": ("2024-01-01", "2024-12-31", "training_stock"),
    "heldout2025": ("2025-01-01", "2025-12-31", "heldout_stock"),
    "heldout2026": ("2026-01-01", "2026-09-24", "heldout_stock"),
}


def target_ratios(base, index, horizon):
    return base[index + 1:index + horizon + 1, 3] / base[index, 3]


def load_samples(root, manifest_path, expected_hash, *, splits=SPLITS, coverage=None, horizon=HORIZON, enrich_window=None):
    if horizon not in (5, 10, 20):
        raise ValueError("Unsupported horizon")
    with manifest_path.open("rb") as stream:
        manifest = stream.read(65537)
    if len(manifest) > 65536 or hashlib.sha256(manifest).hexdigest() != expected_hash:
        raise ValueError("Training dataset manifest does not match the frozen hash")
    info = json.loads(manifest)
    if (
        info.get("schemaVersion") not in (1,2) or info.get("featureDefinition") != FEATURE
        or info.get("targetDefinition") != TARGET or len(info.get("stocks", [])) != 40
    ):
        raise ValueError("Unsupported dataset")
    root = root.resolve(strict=True)
    snapshot_day=None
    if info["schemaVersion"]==1:
        if horizon!=HORIZON or info.get("trainingTargetYears")!=[2023] or info.get("validationTargetYears")!=[2024]:
            raise ValueError("Unsupported legacy dataset")
    else:
        snapshot_day=parse_date(info["asOf"]).isoformat()
        if any(end>snapshot_day for _,end,_ in splits.values()):
            raise ValueError("Training intervals extend beyond the snapshot")
        for name,field in (("cohort","cohortHash"),("index","sourceIndexHash")):
            digest=info.get(field)
            if not isinstance(digest,str) or not re.fullmatch(r"[a-f0-9]{64}",digest):
                raise ValueError("Invalid snapshot provenance digest")
            checked_read(root/"source"/(name+".json"),65536,digest)
    rows = {name: ([], []) for name in splits}
    seen = set()
    counts = {"training_stock": 0, "heldout_stock": 0}
    for entry in info["stocks"]:
        stock, role = entry["stock"], entry["role"]
        if not re.fullmatch(r"(SH|SZ)[0-9]{6}", stock) or stock in seen or role not in counts:
            raise ValueError("Invalid stock binding")
        seen.add(stock)
        counts[role] += 1
        relative = f"特征数据/{stock}.npz"
        path = root / relative
        if (
            entry["path"] != relative or path.is_symlink() or not path.resolve().is_relative_to(root)
            or not 0 < path.stat().st_size == entry["bytes"] <= 64 * 1024 * 1024
            or digest_file(path) != entry["sha256"]
        ):
            raise ValueError(f"Frozen features changed for {stock}")
        if snapshot_day is not None:
            verify_archived_source(root,stock,entry.get("source"))
        with np.load(path, allow_pickle=False) as features:
            dates = features["dates"].astype(str)
            valid = features["valid"]
            base = features["base"]
            targets = features["targets"]
        n = len(dates)
        if (
            dates.shape != (n,) or valid.shape != (n,) or valid.dtype != np.bool_
            or base.shape != (n, 6) or targets.shape != (n, HORIZON)
            or n > 10000 or np.any(dates[1:] <= dates[:-1])
            or snapshot_day is not None and n>0 and dates[-1]>snapshot_day
        ):
            raise ValueError(f"Invalid frozen feature dimensions for {stock}")
        if coverage is not None:
            eligible=np.flatnonzero(valid & np.isfinite(base).all(axis=1) & (base>0).all(axis=1))
            coverage.setdefault(role,{})[stock]=str(dates[eligible[-1]]) if eligible.size else None
        for name, (start, end, required_role) in splits.items():
            if role != required_role:
                continue
            windows, labels = rows[name]
            for index in range(119, n-horizon, 5):
                if not start <= dates[index] <= dates[index+horizon] <= end:
                    continue
                if not valid[index-119:index+horizon+1].all():
                    continue
                window = base[index-WINDOW+1:index+1]
                target = targets[index] if horizon == HORIZON else target_ratios(base,index,horizon)
                if np.isfinite(window).all() and np.isfinite(target).all() and (window > 0).all() and (target > 0).all():
                    if enrich_window is not None:
                        window = enrich_window(window, dates[index-WINDOW+1:index+1])
                    windows.append(window)
                    labels.append(target)
    if counts != {"training_stock": 30, "heldout_stock": 10} or any(not values[0] for values in rows.values()):
        raise ValueError("Unexpected dataset membership or empty split")
    return {name: (np.asarray(windows, dtype=np.float32), np.asarray(labels, dtype=np.float32))
            for name, (windows, labels) in rows.items()}


def forecast(model, inputs, *, config=CONFIG):
    decoder = torch.cat((inputs[:, -config["label_len"]:, :], inputs.new_zeros((len(inputs), config["pred_len"], config["dec_in"]))), dim=1)
    return model(inputs, None, decoder, None)[:, :, 0]


def evaluate(model, inputs, labels, *, config=CONFIG):
    model.eval()
    with torch.inference_mode():
        predictions = torch.cat([forecast(model, block, config=config) for block in inputs.split(64)])
    error = predictions-labels
    if not torch.isfinite(error).all() or not (predictions > 0).all():
        raise ValueError("Invalid evaluation predictions")
    return {
        "mae": float(error.abs().mean()),
        "rmse": float(error.square().mean().sqrt()),
        "baselineMae": float((labels-1).abs().mean()),
        "direction": float((torch.round(predictions-1, decimals=6).sign() == torch.round(labels-1, decimals=6).sign()).float().mean()),
        "samples": len(inputs),
    }


def loss_weights(values, horizon):
    weights = torch.as_tensor(values, dtype=torch.float64)
    if weights.shape != (horizon,) or not torch.isfinite(weights).all() or not (weights > 0).all():
        raise ValueError("One positive finite loss weight is required per forecast day")
    scaled = weights / weights.max()
    return (scaled / scaled.mean()).float()


def forecast_loss(predicted, actual, weights):
    if weights is None:
        return torch.nn.functional.mse_loss(predicted, actual)
    return ((predicted - actual).square() * weights).mean()


def train(rows, seed, epochs, *, cutoff="2023-12-31", config=CONFIG, horizon_weights=None):
    weights = None if horizon_weights is None else loss_weights(horizon_weights, config["pred_len"])
    torch.set_num_threads(2)
    torch.manual_seed(seed)
    raw = rows["train"][0]
    mean = raw.mean(axis=(0, 1), dtype=np.float64).astype(np.float32)
    std = np.maximum(raw.std(axis=(0, 1), dtype=np.float64), 1e-6).astype(np.float32)
    tensors = {name: (torch.from_numpy(np.clip((x-mean)/std, -10, 10)), torch.from_numpy(y))
               for name, (x, y) in rows.items()}
    model = Model(SimpleNamespace(**config))
    torch.nn.init.normal_(model.decoder.projection.weight, mean=0, std=.001)
    torch.nn.init.constant_(model.decoder.projection.bias, 1)
    optimizer = torch.optim.Adam(model.parameters(), lr=.0003)
    batches = DataLoader(TensorDataset(*tensors["train"]), batch_size=64, shuffle=True, num_workers=0)
    history, best, checkpoint = [], float("inf"), None
    for epoch in range(1, epochs+1):
        model.train()
        total = 0.0
        for x, y in batches:
            optimizer.zero_grad()
            loss = forecast_loss(forecast(model, x, config=config), y, weights)
            if not math.isfinite(float(loss.detach())):
                raise ValueError("Nonfinite training loss")
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0, error_if_nonfinite=True)
            optimizer.step()
            total += float(loss.detach())*len(x)
        validation = evaluate(model, *tensors["validation"], config=config)
        history.append({"epoch": epoch, "trainingMse": total/len(raw), "validation": validation})
        if validation["rmse"] < best:
            best = validation["rmse"]
            checkpoint = {"state_dict": {k: v.detach().clone() for k, v in model.state_dict().items()},
                          "config": config, "mean": mean.tolist(), "std": std.tolist(),
                          "mode": "absolute", "cutoff": cutoff, "epoch": epoch}
    model.load_state_dict(checkpoint["state_dict"])
    return checkpoint, history, {key: evaluate(model, *tensors[key], config=config) for key in tensors if key not in ("train", "validation")}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--dataset-sha256", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--seed", type=int, choices=(42, 43, 44), default=42)
    parser.add_argument("--epochs", type=int, choices=range(1, 7), default=6)
    args = parser.parse_args()
    output = args.output.resolve()
    if output.is_relative_to(args.source.resolve()) or output.exists():
        raise SystemExit("Output must be a new directory outside the frozen data")
    start = time.monotonic()
    rows = load_samples(args.source, args.manifest, args.dataset_sha256)
    checkpoint, history, heldout = train(rows, args.seed, args.epochs)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".training-", dir=output.parent) as staging:
        prepared = Path(staging) / "candidate"
        prepared.mkdir()
        weights = prepared / "checkpoint.pt"
        torch.save(checkpoint, weights)
        result = {
            "status": "EXPERIMENT_COMPLETED",
            "datasetHash": args.dataset_sha256,
            "checkpointHash": digest_file(weights),
            "seed": args.seed,
            "selectedEpoch": checkpoint["epoch"],
            "elapsedSeconds": time.monotonic()-start,
            "trainingSamples": len(rows["train"][0]),
            "epochHistory": history,
            **heldout,
        }
        (prepared / "evaluation.json").write_text(json.dumps(result, indent=2, allow_nan=False)+"\n", encoding="utf8")
        prepared.rename(output)
    print(json.dumps(result, allow_nan=False))


if __name__ == "__main__":
    main()
