"""Inspect rolling models while retaining the original v1 loader."""
import json
from pathlib import Path
from types import SimpleNamespace
import re

import torch

from .artifact_contract import DIGEST, ROLLING_ID, rolling_artifact_id
from .artifacts import (
    ArtifactStore, COMMIT, CONFIG, FEATURE, FEATURES, INSTRUMENTS, SOURCE, TARGET,
    InferenceError, digest_file, runtime_hashes, scaling,
)

MANIFEST_FIELDS={
    "schemaVersion","artifactId","status","sourceCommit","checkpointHash","checkpointBytes","config",
    "mean","std","trainingCutoff","epoch","instruments","featureDefinition","sourceDefinition",
    "targetDefinition","features","clip","runtimeHashes","trainingData",
}


def rolling_runtime_hashes():
    directory=Path(__file__).parent
    return runtime_hashes() | {name:digest_file(directory/name) for name in
                              ("artifact_contract.py","rolling_artifacts.py")}


def unique_fields(pairs):
    result=dict(pairs)
    if len(result)!=len(pairs):
        raise ValueError("Duplicate manifest fields")
    return result


class VersionedArtifactStore(ArtifactStore):
    def load(self,artifact_id,manifest_hash):
        if not isinstance(artifact_id,str) or not re.fullmatch(ROLLING_ID,artifact_id):
            return super().load(artifact_id,manifest_hash)
        if not isinstance(manifest_hash,str) or not re.fullmatch(DIGEST,manifest_hash):
            raise InferenceError("MODEL_REQUEST_INVALID")
        folder=self.root/artifact_id
        if folder.is_symlink():
            raise InferenceError("MODEL_ARTIFACT_INVALID")
        path=self.bounded(folder,"manifest.json",131072)
        if digest_file(path)!=manifest_hash:
            raise InferenceError("MODEL_MANIFEST_HASH_MISMATCH")
        try:
            data=json.loads(path.read_bytes(),object_pairs_hook=unique_fields)
            if not isinstance(data,dict) or set(data)!=MANIFEST_FIELDS:
                raise ValueError("Invalid rolling manifest fields")
            training=data["trainingData"]
            checkpoint_hash=data["checkpointHash"]
            required={
                "schemaVersion":2,"artifactId":artifact_id,"status":"CANDIDATE","sourceCommit":COMMIT,
                "config":CONFIG,"instruments":INSTRUMENTS,"features":FEATURES,"sourceDefinition":SOURCE,
                "featureDefinition":FEATURE,"targetDefinition":TARGET,"clip":10,"runtimeHashes":rolling_runtime_hashes(),
            }
            if (any(data[key]!=value for key,value in required.items())
                    or type(data["schemaVersion"]) is not int or type(data["epoch"]) is not int
                    or not 1<=data["epoch"]<=100 or type(data["checkpointBytes"]) is not int
                    or rolling_artifact_id(training,checkpoint_hash)!=artifact_id
                    or data["trainingCutoff"]!=training["train"]["through"]):
                raise ValueError("Incompatible rolling manifest")
            mean,std=scaling(data["mean"]),scaling(data["std"],True)
        except (ValueError,TypeError,UnicodeError,OSError,KeyError,RecursionError):
            raise InferenceError("MODEL_MANIFEST_INVALID") from None

        weights=self.bounded(folder,"checkpoint.pt",8388608)
        if data["checkpointBytes"]!=weights.stat().st_size or checkpoint_hash!=digest_file(weights):
            raise InferenceError("MODEL_CHECKPOINT_HASH_MISMATCH")
        try:
            checkpoint=torch.load(weights,map_location="cpu",weights_only=True)
            fields={
                "config":CONFIG,"mean":data["mean"],"std":data["std"],"mode":"absolute",
                "cutoff":data["trainingCutoff"],"epoch":data["epoch"],"datasetHash":training["datasetHash"],
                "seed":training["seed"],
            }
            if (not isinstance(checkpoint,dict) or set(checkpoint)!=(set(fields)|{"state_dict"})
                    or any(checkpoint[key]!=value for key,value in fields.items())):
                raise ValueError("Incompatible rolling checkpoint")
            from .vendor.model import Model
            model=Model(SimpleNamespace(**CONFIG))
            model.load_state_dict(checkpoint["state_dict"],strict=True)
            if sum(p.numel() for p in model.parameters())!=22881 or any(
                    not torch.isfinite(value).all() for value in model.state_dict().values()):
                raise ValueError("Invalid rolling checkpoint parameters")
            model.eval()
        except Exception:
            raise InferenceError("MODEL_CHECKPOINT_INVALID") from None
        return data,model,mean,std
