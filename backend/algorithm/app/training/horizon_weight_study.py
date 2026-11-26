"""Compare uniform loss with a doubled training weight on the fifth forecast day."""

import argparse
import json
from datetime import date
from pathlib import Path

import numpy as np

from app.inference.artifacts import CONFIG
from .dlinear_study import metrics
from .five_day import SPLITS, load_samples
from .market_feature_study import EPOCHS, SEEDS, evaluate_run

WEIGHTED = (1, 1, 1, 1, 2)
PERIODS = ("heldout2025", "heldout2026")


def run(source, manifest, digest, through):
    splits = {**SPLITS, "heldout2026": ("2026-01-01", through, "heldout_stock")}
    rows = load_samples(source, manifest, digest, splits=splits)
    baseline = {name: metrics(np.ones_like(rows[name][1]), rows[name][1]) for name in PERIODS}
    runs = [
        {"seed": seed, "uniform": evaluate_run(rows, seed, CONFIG),
         "weightedDay5": evaluate_run(rows, seed, CONFIG, horizon_weights=WEIGHTED)}
        for seed in SEEDS
    ]
    summary = {}
    for period in PERIODS:
        summary[period] = {}
        for method in ("uniform", "weightedDay5"):
            scored = [item[method]["periods"][period] for item in runs]
            by_day = np.mean([score["maeByDay"] for score in scored], axis=0)
            summary[period][method] = {
                "mae": float(np.mean([score["mae"] for score in scored])),
                "maeDays1To4": float(by_day[:4].mean()),
                "maeByDay": by_day.tolist(),
            }
    return {"datasetHash": digest, "trainingEpochLimit": EPOCHS, "weights": WEIGHTED,
            "splits": splits, "windows": {name: len(labels) for name, (_, labels) in rows.items()},
            "priceUnchanged": baseline, "runs": runs, "summary": summary}


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--source", type=Path, required=True)
    cli.add_argument("--manifest", type=Path, required=True)
    cli.add_argument("--dataset-sha256", required=True)
    cli.add_argument("--through", type=lambda value: date.fromisoformat(value).isoformat(), required=True)
    args = cli.parse_args()
    print(json.dumps(run(args.source, args.manifest, args.dataset_sha256, args.through),
                     indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
