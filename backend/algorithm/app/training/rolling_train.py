"""Train and seal an isolated five-day rolling Transformer experiment."""
import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import time

import torch

from app.inference.artifact_contract import rolling_artifact_id, training_spans
from app.inference.artifacts import COMMIT, CONFIG, FEATURE, FEATURES, INSTRUMENTS, SOURCE, TARGET, digest_file
from app.inference.rolling_artifacts import VersionedArtifactStore, rolling_runtime_hashes, unique_fields
from .five_day import load_samples, train
from .seal import canonical, metric


def run(source, manifest, training, output, epochs):
    started=time.monotonic()
    spans=training_spans(training)
    source=source.resolve(strict=True)
    output=output.resolve()
    if output.is_relative_to(source) or output.exists():
        raise ValueError("Output must be a new directory outside the frozen data")
    splits={name:(start,end,"heldout_stock" if name=="holdout" else "training_stock")
            for name,(start,end) in spans.items()}
    rows=load_samples(source,manifest,training["datasetHash"],splits=splits)
    cutoff=spans["train"][1]
    checkpoint,history,heldout=train(rows,training["seed"],epochs,cutoff=cutoff)
    checkpoint.update(datasetHash=training["datasetHash"],seed=training["seed"])
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".rolling-",dir=output.parent) as directory:
        prepared=Path(directory)/"candidate"
        prepared.mkdir()
        weights=prepared/"checkpoint.pt"
        torch.save(checkpoint,weights)
        checkpoint_hash=digest_file(weights)
        artifact_id=rolling_artifact_id(training,checkpoint_hash)
        folder=prepared/artifact_id
        folder.mkdir()
        weights=weights.rename(folder/weights.name)
        manifest_bytes=canonical({
            "schemaVersion":2,"artifactId":artifact_id,"status":"CANDIDATE","sourceCommit":COMMIT,
            "checkpointHash":checkpoint_hash,"checkpointBytes":weights.stat().st_size,
            "config":CONFIG,"mean":checkpoint["mean"],"std":checkpoint["std"],"trainingCutoff":cutoff,
            "epoch":checkpoint["epoch"],"instruments":INSTRUMENTS,"features":FEATURES,
            "featureDefinition":FEATURE,"sourceDefinition":SOURCE,"targetDefinition":TARGET,
            "clip":10,"runtimeHashes":rolling_runtime_hashes(),"trainingData":training,
        })
        manifest_file=folder/"manifest.json"
        manifest_file.write_bytes(manifest_bytes)
        manifest_hash=digest_file(manifest_file)
        VersionedArtifactStore(prepared).load(artifact_id,manifest_hash)
        report={
            "status":"EXPERIMENT_COMPLETED","artifactId":artifact_id,"manifestHash":manifest_hash,
            "checkpointHash":checkpoint_hash,"trainingData":training,"datasetHash":training["datasetHash"],
            "seed":training["seed"],"elapsedSeconds":time.monotonic()-started,
            "trainingSamples":len(rows["train"][0]),"selectedEpoch":checkpoint["epoch"],
            "epochHistory":history,"holdout":heldout["holdout"],
        }
        report_bytes=canonical(report)+b"\n"
        (prepared/"evaluation.json").write_bytes(report_bytes)
        (prepared/"registration.json").write_bytes(canonical(registration_payload(report,report_bytes))+b"\n")
        prepared.rename(output)
    return report


def export_candidate(folder,root,report,raw):
    store=VersionedArtifactStore(root)
    artifact_id=report["artifactId"]
    manifest_hash=report["manifestHash"]
    target=root/artifact_id
    if not target.exists():
        with tempfile.TemporaryDirectory(prefix=".rolling-candidate-",dir=root) as temp:
            staged=Path(temp)/artifact_id
            staged.mkdir()
            from .snapshot import checked_read
            (staged/"manifest.json").write_bytes(checked_read(folder/"manifest.json",131072,manifest_hash))
            (staged/"checkpoint.pt").write_bytes(checked_read(folder/"checkpoint.pt",8388608,report["checkpointHash"]))
            manifest,*_=VersionedArtifactStore(temp).load(artifact_id,manifest_hash)
            if manifest["trainingData"]!=report["trainingData"]:
                raise ValueError("Rolling report does not match candidate")
            try:
                staged.rename(target)
            except OSError:
                if not target.is_dir():
                    raise
    manifest,*_=store.load(artifact_id,manifest_hash)
    if manifest["checkpointHash"]!=report["checkpointHash"] or manifest["trainingData"]!=report["trainingData"]:
        raise ValueError("Rolling report does not match candidate")
    return registration_payload(report,raw)


def registration_payload(report,report_bytes):
    if "elapsedSeconds" in report:
        # Runtime varies on replay, while candidate identity and evaluation remain stable.
        report_bytes=canonical({key:value for key,value in report.items() if key!="elapsedSeconds"})+b"\n"
    selected=next(item["validation"] for item in report["epochHistory"] if item["epoch"]==report["selectedEpoch"])
    holdout=report["holdout"]
    training=report["trainingData"]
    return {
        "artifactId":report["artifactId"],"manifestHash":report["manifestHash"],
        "evaluation":{
            "sourceHash":hashlib.sha256(report_bytes).hexdigest(),"scope":"ROLLING_OUT_OF_SAMPLE",
            "seed":training["seed"],"validationMae":metric(selected["mae"],10),
            "validationBaselineMae":metric(selected["baselineMae"],10),
            "rolling":{"datasetHash":training["datasetHash"],"holdoutMae":metric(holdout["mae"],10),
                       "holdoutBaselineMae":metric(holdout["baselineMae"],10),
                       "holdoutDirection":metric(holdout["direction"],1),"holdoutWindows":holdout["samples"]},
        },
    }


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source",type=Path,required=True)
    parser.add_argument("--manifest",type=Path,required=True)
    parser.add_argument("--training-data",type=Path,required=True)
    parser.add_argument("--output",type=Path,required=True)
    parser.add_argument("--epochs",type=int,choices=range(1,7),default=6)
    args=parser.parse_args()
    try:
        with args.training_data.open("rb") as stream:
            data=stream.read(4097)
        if len(data)>4096:
            raise ValueError("Training metadata exceeds size limit")
        training=json.loads(data,object_pairs_hook=unique_fields)
        report=run(args.source,args.manifest,training,args.output,args.epochs)
    except (OSError,ValueError) as error:
        parser.error(str(error))
    print(canonical(report).decode())


if __name__=="__main__":
    main()
