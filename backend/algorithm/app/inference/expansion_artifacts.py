"""An expansion candidate references one immutable parent checkpoint and stock evidence."""

import argparse
from datetime import date
import hashlib
import json
import math
from pathlib import Path
import re
from tempfile import TemporaryDirectory

import numpy as np

from .artifact_contract import DIGEST, valid_artifact_id as valid_parent_id
from .artifacts import InferenceError, digest_file
from .rolling_artifacts import VersionedArtifactStore, unique_fields

EXPANSION_ID = r"transformer-s3-expansion-(?:sh|sz)[0-9]{6}-[a-f0-9]{16}"
STOCK_ID = r"(?:SH|SZ)[0-9]{6}"
EVIDENCE_LIMIT = 2_097_152


def matches(pattern, value):
    return isinstance(value, str) and re.fullmatch(pattern, value) is not None


def valid_artifact_id(value):
    return valid_parent_id(value) or matches(EXPANSION_ID, value)


def encoded(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def parse(payload):
    return json.loads(payload, object_pairs_hook=unique_fields)


def summarize(evidence, parent_id, parent_hash, parent):
    stock = evidence["instrumentId"]
    if (type(evidence["schemaVersion"]) is not int or evidence["schemaVersion"] != 1
            or not matches(STOCK_ID, stock) or stock in parent["instruments"]
            or evidence["artifactId"] != parent_id or evidence["manifestHash"] != parent_hash
            or evidence["checkpointHash"] != parent["checkpointHash"]
            or evidence["targetDefinition"] != parent["targetDefinition"]
            or any(not matches(DIGEST, evidence[key]) for key in ("sourceFileSha256", "sourcePayloadHash"))):
        raise ValueError("Evidence does not match the parent model")
    period = evidence["requestedPeriod"]
    start, end = (date.fromisoformat(period[key]) for key in ("from", "through"))
    latest_fit = parent.get("trainingData", {}).get("validation", {}).get("through", "2024-12-31")
    if not date.fromisoformat(max(parent["trainingCutoff"], latest_fit)) < start <= end:
        raise ValueError("Evidence overlaps the model fit period")
    samples = evidence["samples"]
    if not isinstance(samples, list) or not 1 <= len(samples) <= 2000:
        raise ValueError("Invalid evidence sample count")
    previous = start
    for sample in sorted(samples, key=lambda item: item["origin"]):
        origin = date.fromisoformat(sample["origin"])
        targets = [date.fromisoformat(day) for day in sample["targetDates"]]
        if (len(targets) != 5 or origin < previous or not origin < targets[0]
                or any(after <= before for before, after in zip(targets, targets[1:]))
                or targets[-1] > end):
            raise ValueError("Overlapping or incomplete five-day evidence")
        previous = targets[-1]
    values = [np.asarray([item[key] for item in samples]) for key in ("actual", "predicted")]
    if any(value.shape != (len(samples), 5) or value.dtype.kind not in ("f", "i", "u")
           or not np.isfinite(value).all() or not (value > 0).all() for value in values):
        raise ValueError("Invalid evidence ratios")
    actual, predicted = values
    scores = evidence["scores"]
    metrics = {
        "mae": float(np.abs(predicted - actual).mean()),
        "baselineMae": float(np.abs(1 - actual).mean()),
        "directionAccuracy": float((np.sign(np.round(predicted - 1, 6))
                                    == np.sign(np.round(actual - 1, 6))).mean()),
    }
    if type(scores["windows"]) is not int or scores["windows"] != len(samples) or any(
        type(scores[key]) not in (float, int) or not math.isclose(value, scores[key], abs_tol=2e-8)
        for key, value in metrics.items()
    ):
        raise ValueError("Evidence metrics do not match the samples")
    return {"instrumentId": stock, "from": start.isoformat(), "through": end.isoformat(),
            "windows": len(samples), **{key: format(value, ".12f") for key, value in metrics.items()}}


def assemble(parent_id, parent_hash, parent, payload):
    summary = summarize(parse(payload), parent_id, parent_hash, parent)
    expansion = {"parentArtifactId": parent_id, "parentManifestHash": parent_hash,
                 "evidenceHash": hashlib.sha256(payload).hexdigest(), **summary}
    suffix = hashlib.sha256(encoded(expansion)).hexdigest()[:16]
    artifact_id = f"transformer-s3-expansion-{summary['instrumentId'].lower()}-{suffix}"
    return {**parent, "schemaVersion": 3, "artifactId": artifact_id,
            "instruments": [summary["instrumentId"]], "expansion": expansion}


class ExpansionArtifactStore(VersionedArtifactStore):
    def load(self, artifact_id, manifest_hash):
        if not matches(EXPANSION_ID, artifact_id):
            return super().load(artifact_id, manifest_hash)
        if not matches(DIGEST, manifest_hash):
            raise InferenceError("MODEL_REQUEST_INVALID")
        folder = self.root / artifact_id
        if folder.is_symlink():
            raise InferenceError("MODEL_ARTIFACT_INVALID")
        manifest_path = self.bounded(folder, "manifest.json", 131072)
        if digest_file(manifest_path) != manifest_hash:
            raise InferenceError("MODEL_MANIFEST_HASH_MISMATCH")
        try:
            manifest = parse(manifest_path.read_bytes())
            binding = manifest["expansion"]
            parent_id, parent_hash = binding["parentArtifactId"], binding["parentManifestHash"]
            if not valid_parent_id(parent_id) or not matches(DIGEST, parent_hash):
                raise ValueError("Invalid parent artifact")
            parent, model, mean, std = super().load(parent_id, parent_hash)
            evidence = self.bounded(folder, "evidence.json", EVIDENCE_LIMIT).read_bytes()
            expected = assemble(parent_id, parent_hash, parent, evidence)
            if expected["artifactId"] != artifact_id or encoded(manifest) != encoded(expected):
                raise ValueError("Expansion manifest does not match its evidence")
        except (ValueError, TypeError, KeyError, AttributeError, ArithmeticError, OSError, RecursionError):
            raise InferenceError("MODEL_MANIFEST_INVALID") from None
        return manifest, model, mean, std


def build_expansion(root, evidence):
    with evidence.open("rb") as stream:
        payload = stream.read(EVIDENCE_LIMIT + 1)
    if len(payload) > EVIDENCE_LIMIT:
        raise ValueError("Expansion evidence too large")
    report = parse(payload)
    parent_id, parent_hash = report["artifactId"], report["manifestHash"]
    if not valid_parent_id(parent_id) or not matches(DIGEST, parent_hash):
        raise ValueError("Invalid parent artifact")
    store = ExpansionArtifactStore(root)
    parent, _, _, _ = VersionedArtifactStore(root).load(parent_id, parent_hash)
    manifest = assemble(parent_id, parent_hash, parent, payload)
    contents = encoded(manifest)
    manifest_hash = hashlib.sha256(contents).hexdigest()
    artifact_id = manifest["artifactId"]
    folder = root / artifact_id
    if not folder.exists():
        with TemporaryDirectory(dir=root) as temporary:
            stage = Path(temporary) / artifact_id
            stage.mkdir()
            (stage / "manifest.json").write_bytes(contents)
            (stage / "evidence.json").write_bytes(payload)
            stage.rename(folder)
    store.load(artifact_id, manifest_hash)
    return artifact_id, manifest_hash


if __name__ == "__main__":
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--root", type=Path, required=True)
    cli.add_argument("--evidence", type=Path, required=True)
    args = cli.parse_args()
    artifact_id, manifest_hash = build_expansion(args.root, args.evidence)
    print(json.dumps({"artifactId": artifact_id, "manifestHash": manifest_hash}))
