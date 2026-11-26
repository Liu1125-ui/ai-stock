"""Seal a trained checkpoint for the existing candidate inspection contract."""
import argparse
import hashlib
import json
from decimal import Decimal
from pathlib import Path
import shutil
import tempfile

import torch

from app.inference.artifacts import (
    ArtifactStore, COMMIT, CONFIG, FEATURE, FEATURES, INSTRUMENTS, SOURCE, TARGET, digest_file, runtime_hashes,
)


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def metric(value, upper):
    number = Decimal(str(value))
    if not number.is_finite() or not 0 <= number <= upper:
        raise ValueError("Invalid training evaluation")
    return format(number, ".12f")


def seal(job, artifact_root, expected_checkpoint_hash=None):
    report_path = job / "evaluation.json"
    with report_path.open("rb") as stream:
        report_bytes = stream.read(65537)
    if len(report_bytes) > 65536:
        raise ValueError("Training report exceeds limit")
    report = json.loads(report_bytes)
    weights = job / "checkpoint.pt"
    size = weights.stat().st_size
    if weights.is_symlink() or not 0 < size <= 8388608:
        raise ValueError("Invalid training checkpoint size")
    checkpoint_hash = digest_file(weights)
    seed = report["seed"]
    if (report["status"] != "EXPERIMENT_COMPLETED" or seed not in (42, 43, 44)
            or report["checkpointHash"] != checkpoint_hash
            or expected_checkpoint_hash is not None and checkpoint_hash != expected_checkpoint_hash):
        raise ValueError("Checkpoint and training report do not match")
    checkpoint = torch.load(weights, map_location="cpu", weights_only=True)
    epoch = checkpoint["epoch"]
    if checkpoint["config"] != CONFIG or epoch != report["selectedEpoch"]:
        raise ValueError("Incompatible candidate configuration")
    selected = next(row["validation"] for row in report["epochHistory"] if row["epoch"] == epoch)
    evaluation = {
        "sourceHash": hashlib.sha256(report_bytes).hexdigest(),
        "scope": "RETROSPECTIVE_DEVELOPMENT",
        "seed": seed,
        "validationYear": 2024,
        "validationMae": metric(selected["mae"], 10),
        "validationBaselineMae": metric(selected["baselineMae"], 10),
        "mae2025": metric(report["heldout2025"]["mae"], 10),
        "mae2026": metric(report["heldout2026"]["mae"], 10),
        "direction2026": metric(report["heldout2026"]["direction"], 1),
    }
    if Decimal(evaluation["validationBaselineMae"]) == 0:
        raise ValueError("Validation baseline is zero")
    hashes = runtime_hashes()
    suffix = hashlib.sha256(canonical({"checkpoint": checkpoint_hash, "report": evaluation["sourceHash"], "runtime": hashes})).hexdigest()[:16]
    artifact_id = f"transformer-s3-2023-seed{seed}-{suffix}"
    data = {
        "schemaVersion": 1, "artifactId": artifact_id, "status": "CANDIDATE", "sourceCommit": COMMIT,
        "checkpointHash": checkpoint_hash, "checkpointBytes": size, "config": checkpoint["config"],
        "mean": checkpoint["mean"], "std": checkpoint["std"], "trainingCutoff": checkpoint["cutoff"],
        "epoch": epoch, "instruments": INSTRUMENTS, "featureDefinition": FEATURE, "sourceDefinition": SOURCE,
        "targetDefinition": TARGET, "features": FEATURES, "clip": 10, "runtimeHashes": hashes,
    }
    payload = canonical(data)
    manifest_hash = hashlib.sha256(payload).hexdigest()
    artifact_root.mkdir(parents=True, exist_ok=True)
    target = artifact_root / artifact_id
    if not target.exists():
        with tempfile.TemporaryDirectory(prefix=".candidate-", dir=artifact_root) as directory:
            staged = Path(directory) / artifact_id
            staged.mkdir()
            shutil.copyfile(weights, staged / "checkpoint.pt")
            (staged / "manifest.json").write_bytes(payload)
            ArtifactStore(directory).load(artifact_id, manifest_hash)
            try:
                staged.rename(target)
            except OSError:
                if not target.exists():
                    raise
    ArtifactStore(artifact_root).load(artifact_id, manifest_hash)
    return {"artifactId": artifact_id, "manifestHash": manifest_hash, "evaluation": evaluation}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--job", type=Path, required=True)
    parser.add_argument("--artifacts", type=Path, required=True)
    args = parser.parse_args()
    print(canonical(seal(args.job, args.artifacts)).decode())


if __name__ == "__main__":
    main()
