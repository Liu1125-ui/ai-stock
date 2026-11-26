"""Compare a rolling candidate with the recommended model on one frozen holdout."""
import os
from pathlib import Path
from threading import BoundedSemaphore

from fastapi import Request
import numpy as np
from pydantic import BaseModel, ConfigDict
import torch

from app.inference.artifacts import InferenceError
from app.inference.rolling_artifacts import VersionedArtifactStore
from .execution import Digest, TrainingError
from .five_day import evaluate, load_samples


_SLOT = BoundedSemaphore(1)


class Artifact(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    artifactId: str
    manifestHash: Digest


class Comparison(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    candidate: Artifact
    reference: Artifact


def compare(artifact_root, snapshot_root, candidate, reference):
    torch.set_num_threads(2)
    store = VersionedArtifactStore(artifact_root)
    versions = [store.load(item["artifactId"], item["manifestHash"])
                for item in (candidate, reference)]
    training = versions[0][0]["trainingData"]
    holdout = training["holdout"]
    if any(v[0]["trainingCutoff"] >= holdout["from"] or
           v[0].get("trainingData", {}).get("validation", {}).get("through", "") >= holdout["from"]
           for v in versions):
        raise ValueError("Training or validation overlaps the holdout")
    dataset = training["datasetHash"]
    source = snapshot_root / dataset
    if source.is_symlink() or not source.is_dir():
        raise ValueError("Snapshot unavailable")
    raw, labels = load_samples(source, source / "manifest.json", dataset,
        splits={"holdout": (holdout["from"], holdout["through"], "heldout_stock")})["holdout"]
    actual = torch.from_numpy(labels)
    scores = []
    for identity, (meta, model, mean, std) in zip((candidate, reference), versions):
        result = evaluate(model, torch.from_numpy(np.clip((raw - mean) / std, -10, 10)), actual)
        scores.append({**identity, **{k: result[k] for k in ("mae", "rmse", "direction")}})
    error = actual - 1
    return {"datasetHash": dataset, "holdout": holdout, "windows": len(labels),
            "candidate": scores[0], "reference": scores[1],
            "baseline": {"mae": float(error.abs().mean()), "rmse": float(error.square().mean().sqrt()),
                         "direction": float((torch.round(error, decimals=6) == 0).float().mean())}}


def compare_request(body: Comparison, request: Request):
    from .api import reply, worker
    try:
        execution = worker(request)
        if execution.rolling_root is None:
            return reply("MODEL_COMPARISON_UNAVAILABLE", 503)
        if not _SLOT.acquire(blocking=False):
            return reply("MODEL_COMPARISON_BUSY", 409)
        try:
            result = compare(Path(os.environ["MODEL_ARTIFACT_ROOT"]), execution.rolling_root,
                             body.candidate.model_dump(), body.reference.model_dump())
        finally:
            _SLOT.release()
        return reply("OK", 200, result)
    except TrainingError as error:
        return reply(error.code, error.status)
    except (InferenceError, OSError, ValueError, TypeError, KeyError, RuntimeError):
        return reply("MODEL_COMPARISON_UNAVAILABLE", 503)
