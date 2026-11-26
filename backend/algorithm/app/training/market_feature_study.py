"""Paired six-feature and market-augmented five-day Transformer experiment."""

import argparse
import hashlib
import json
from datetime import date
from functools import partial
from pathlib import Path
from types import SimpleNamespace

import numpy as np
import torch

from app.inference.artifacts import CONFIG
from app.inference.vendor.model import Model
from .dlinear_study import metrics
from .five_day import SPLITS, forecast, load_samples, train

LOOKBACK_VOLATILITY = 20
SEEDS = (42, 43, 44)
EPOCHS = 6


def market_features(days, closes):
    values = np.asarray(closes, dtype=np.float64)
    if (len(days) != len(values) or len(days) <= LOOKBACK_VOLATILITY
            or any(right <= left for left, right in zip(days, days[1:]))
            or not np.isfinite(values).all() or not (values > 0).all()):
        raise ValueError("Invalid market close series")
    returns = values[1:] / values[:-1] - 1
    return {days[i]: (float(returns[i-1]), float(returns[i-LOOKBACK_VOLATILITY:i].std()))
            for i in range(LOOKBACK_VOLATILITY, len(days))}


def window_with_market(features, raw, days):
    try:
        market = np.asarray([features[day] for day in days], dtype=np.float32)
    except KeyError as error:
        raise ValueError(f"Market history unavailable on stock input date: {error.args[0]}") from error
    if market.shape != (len(raw), 2) or not np.isfinite(market).all():
        raise ValueError("Invalid market features")
    return np.concatenate((raw, market), axis=1)


def evaluate_run(rows, seed, config, *, horizon_weights=None):
    checkpoint, _, _ = train(rows, seed, EPOCHS, config=config, horizon_weights=horizon_weights)
    model = Model(SimpleNamespace(**config))
    model.load_state_dict(checkpoint["state_dict"])
    model.eval()
    mean = np.asarray(checkpoint["mean"], dtype=np.float32)
    std = np.asarray(checkpoint["std"], dtype=np.float32)
    periods = {}
    for period in ("heldout2025", "heldout2026"):
        raw, actual = rows[period]
        inputs = torch.from_numpy(np.clip((raw-mean)/std,-10,10))
        with torch.inference_mode():
            predicted = torch.cat([forecast(model, part, config=config) for part in inputs.split(64)]).numpy()
        periods[period] = metrics(predicted, actual)
    return {"epoch": checkpoint["epoch"], "periods": periods}


def run(source, manifest, dataset_hash, index_file, through):
    with index_file.open("rb") as stream:
        payload = stream.read(524289)
    if len(payload) > 524288:
        raise ValueError("Index data file too large")
    index = json.loads(payload)
    if (index.get("provider"), index.get("indexId"), index.get("code")) != ("baostock", "CSI_300", "sh.000300"):
        raise ValueError("Expected a verified CSI 300 data file")
    days = [date.fromisoformat(point["date"]).isoformat() for point in index["points"]]
    features = market_features(days, [point["close"] for point in index["points"]])
    splits = {**SPLITS, "heldout2026": ("2026-01-01", through, "heldout_stock")}
    extended = load_samples(source, manifest, dataset_hash, splits=splits,
                            enrich_window=partial(window_with_market, features))
    stock_only = {name: (values[:, :, :6].copy(), labels) for name, (values, labels) in extended.items()}
    runs = []
    for seed in SEEDS:
        runs.append({"seed": seed,
                     "stockOnly": evaluate_run(stock_only, seed, CONFIG),
                     "withMarket": evaluate_run(extended, seed, {**CONFIG, "enc_in": 8, "dec_in": 8})})
    counts = {name: len(labels) for name, (_, labels) in extended.items()}
    baseline = {period: metrics(np.ones_like(extended[period][1]), extended[period][1])
                for period in ("heldout2025", "heldout2026")}
    return {
        "datasetHash": dataset_hash, "csi300FileHash": hashlib.sha256(payload).hexdigest(),
        "marketFeatureDefinition": "same-session-close-return-and-20-session-return-std",
        "trainingEpochLimit": EPOCHS, "splits": splits, "windows": counts,
        "priceUnchanged": baseline, "runs": runs,
    }


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--source", type=Path, required=True)
    cli.add_argument("--manifest", type=Path, required=True)
    cli.add_argument("--dataset-sha256", required=True)
    cli.add_argument("--index", type=Path, required=True)
    cli.add_argument("--through", type=lambda value:date.fromisoformat(value).isoformat(), required=True)
    args = cli.parse_args()
    print(json.dumps(run(args.source, args.manifest, args.dataset_sha256, args.index, args.through),
                     indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
