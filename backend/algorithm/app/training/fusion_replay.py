"""Score published-format model artifacts on the same frozen holdout windows."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path

import numpy as np
import torch

from app.inference.artifacts import ArtifactStore
from .five_day import forecast, load_samples

WEIGHT_TOTAL = 10_000
PERIODS = ("heldout2025", "heldout2026")


@dataclass(frozen=True)
class Member:
    artifact_id: str
    manifest_hash: str
    weight_bps: int


def parse_member(value):
    try:
        artifact, manifest, weight = value.split(":")
        if not weight.isascii() or not weight.isdecimal() or len(weight) > 5:
            raise ValueError()
        return Member(artifact, manifest, int(weight))
    except ValueError as error:
        raise argparse.ArgumentTypeError("Expected artifact-id:manifest-sha256:weight-bps") from error


def score(artifact_root, feature_root, manifest, dataset_hash, members):
    if (not 2 <= len(members) <= 3
            or len({item.artifact_id for item in members}) != len(members)
            or any(not 0 <= item.weight_bps <= WEIGHT_TOTAL for item in members)
            or sum(item.weight_bps for item in members) != WEIGHT_TOTAL):
        raise ValueError("Provide two or three distinct members whose weights total 10000 basis points")

    torch.set_num_threads(2)
    store = ArtifactStore(artifact_root)
    loaded = [store.load(item.artifact_id, item.manifest_hash) for item in members]
    if len({metadata["checkpointHash"] for metadata, *_ in loaded}) != len(members):
        raise ValueError("Members must reference different model checkpoints")
    splits = load_samples(feature_root, manifest, dataset_hash)
    periods = {}
    for period in PERIODS:
        raw, labels = splits[period]
        actual = torch.from_numpy(labels).to(torch.float64)
        predictions = []
        for _, model, mean, std in loaded:
            inputs = torch.from_numpy(np.clip((raw-mean)/std, -10, 10))
            with torch.inference_mode():
                result = torch.cat([forecast(model, block) for block in inputs.split(64)])
            if result.shape != actual.shape or not torch.isfinite(result).all() or not (result > 0).all():
                raise ValueError("Model returned invalid or misaligned predictions")
            predictions.append(result.to(torch.float64))

        def mae(values):
            return float((values-actual).abs().mean())

        fusion = sum(values * (item.weight_bps / WEIGHT_TOTAL) for item, values in zip(members, predictions))
        periods[period] = {
            "windows": len(raw),
            "models": {item.artifact_id: mae(values) for item, values in zip(members, predictions)},
            "fusionMae": mae(fusion),
            "baselineMae": mae(torch.ones_like(actual)),
        }

    return {
        "datasetHash": dataset_hash,
        "metric": "adjusted-close-relative-origin-mae",
        "members": [
            {"artifactId": item.artifact_id, "manifestHash": item.manifest_hash,
             "checkpointHash": metadata["checkpointHash"], "weightBps": item.weight_bps}
            for item, (metadata, _, _, _) in zip(members, loaded)
        ],
        "periods": periods,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifacts", type=Path, required=True)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--dataset-sha256", required=True)
    parser.add_argument("--member", action="append", type=parse_member, required=True,
                        help="artifact-id:manifest-sha256:weight-bps")
    args = parser.parse_args()
    try:
        report = score(args.artifacts, args.source, args.manifest, args.dataset_sha256, args.member)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    print(json.dumps(report, indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
