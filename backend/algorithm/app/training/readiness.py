"""Inspect a frozen snapshot before admitting a periodic training job."""
import argparse
from datetime import date
import hashlib
import json
from pathlib import Path
import re

from app.inference.artifact_contract import DIGEST, training_spans
from app.inference.rolling_artifacts import unique_fields
from .five_day import load_samples

GROUPS=("training_stock","heldout_stock")


def decide(coverage,expected_session,dataset_hash,previous=None,min_percent=80):
    if not isinstance(dataset_hash,str) or not re.fullmatch(DIGEST,dataset_hash):
        raise ValueError("Invalid snapshot digest")
    if not 1<=min_percent<=100:
        raise ValueError("Invalid coverage threshold")
    if previous is not None and not re.fullmatch(DIGEST,previous[0]):
        raise ValueError("Invalid previous snapshot digest")
    groups={}
    expected=expected_session.isoformat()
    for role in GROUPS:
        stocks=coverage.get(role,{})
        if not stocks:
            raise ValueError("Missing required stock group")
        for value in stocks.values():
            if value is not None and (date.fromisoformat(value).isoformat()!=value or value>expected):
                raise ValueError("Snapshot has invalid dates or rows beyond the expected session")
        current=sum(value==expected for value in stocks.values())
        known=[value for value in stocks.values() if value is not None]
        groups[role]={
            "total":len(stocks),"fresh":current,"latest":max(known,default=None),
            "missingOrStale":[stock for stock,value in stocks.items() if value!=expected],
        }
    if previous is not None and (previous[0]==dataset_hash or previous[1]>=expected_session):
        state="ALREADY_PROCESSED"
    elif not any(group["fresh"] for group in groups.values()):
        state="STALE"
    elif any(group["fresh"]*100<group["total"]*min_percent for group in groups.values()):
        state="INSUFFICIENT_COVERAGE"
    else:
        state="READY"
    return {"state":state,"datasetHash":dataset_hash,"expectedSession":expected,
            "minimumCoveragePercent":min_percent,"groups":groups}


def inspect(source,manifest,training,expected_session,previous=None,min_percent=80):
    spans=training_spans(training)
    if spans["holdout"][1]>expected_session.isoformat():
        raise ValueError("Holdout ends beyond the expected session")
    splits={name:(start,end,"heldout_stock" if name=="holdout" else "training_stock")
            for name,(start,end) in spans.items()}
    coverage={}
    rows=load_samples(source,manifest,training["datasetHash"],splits=splits,coverage=coverage)
    report=decide(coverage,expected_session,training["datasetHash"],previous,min_percent)
    report["windows"]={name:len(samples[0]) for name,samples in rows.items()}
    return report


def read_metadata(path):
    with path.open("rb") as stream:
        raw=stream.read(4097)
    if len(raw)>4096:
        raise ValueError("Training metadata exceeds size limit")
    return raw,json.loads(raw,object_pairs_hook=unique_fields)


def inspect_preset(source,manifest,path,expected_session,previous=None,min_percent=80):
    from .execution import RollingPreset

    raw,metadata=read_metadata(path)
    preset=RollingPreset.model_validate(metadata)
    if type(metadata["schemaVersion"]) is not int or preset.seed not in (42,43,44) or not 1<=preset.epochs<=6:
        raise ValueError("Invalid rolling preset")
    if preset.trainingData["datasetHash"]!=preset.datasetHash or preset.trainingData["seed"]!=preset.seed:
        raise ValueError("Rolling preset metadata mismatch")
    report=inspect(source,manifest,preset.trainingData,expected_session,previous,min_percent)
    return {**report,"configurationId":preset.configurationId,
            "configurationHash":hashlib.sha256(raw).hexdigest()}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source",type=Path,required=True)
    parser.add_argument("--manifest",type=Path,required=True)
    metadata=parser.add_mutually_exclusive_group(required=True)
    metadata.add_argument("--training-data",type=Path)
    metadata.add_argument("--preset",type=Path,help="Frozen v2 preset; bind report to its exact SHA-256")
    parser.add_argument("--expected-session",type=date.fromisoformat,required=True)
    parser.add_argument("--previous-dataset-hash")
    parser.add_argument("--previous-session",type=date.fromisoformat)
    parser.add_argument("--min-coverage-percent",type=int,default=80)
    args=parser.parse_args()
    if bool(args.previous_dataset_hash)!=(args.previous_session is not None):
        parser.error("Provide both previous snapshot fields")
    previous=(args.previous_dataset_hash,args.previous_session) if args.previous_session else None
    try:
        if args.preset:
            report=inspect_preset(args.source,args.manifest,args.preset,args.expected_session,
                                  previous,args.min_coverage_percent)
        else:
            _,metadata=read_metadata(args.training_data)
            report=inspect(args.source,args.manifest,metadata,args.expected_session,previous,args.min_coverage_percent)
    except (OSError,ValueError,KeyError,TypeError) as error:
        parser.error(str(error))
    print(json.dumps(report,ensure_ascii=False,allow_nan=False,indent=2))


if __name__=="__main__":
    main()
