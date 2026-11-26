"""Identifiers and immutable split metadata for model artifacts."""
from datetime import date
import hashlib
import json
import re

LEGACY_ID = r"transformer-s3-2023-seed(?:42|43|44)(?:-[a-f0-9]{16})?"
ROLLING_ID = r"transformer-s3-rolling-[0-9]{8}-seed(?:42|43|44)-[a-f0-9]{16}"
DIGEST = r"[a-f0-9]{64}"


def valid_artifact_id(value):
    return isinstance(value,str) and re.fullmatch(f"(?:{LEGACY_ID}|{ROLLING_ID})",value) is not None


def training_spans(training):
    if (not isinstance(training,dict) or set(training)!={"datasetHash","seed","train","validation","holdout"}
            or not isinstance(training["datasetHash"],str) or not re.fullmatch(DIGEST,training["datasetHash"])
            or type(training["seed"]) is not int or training["seed"] not in (42,43,44)):
        raise ValueError("Invalid rolling training metadata")
    spans={}
    previous_end=None
    for name in ("train","validation","holdout"):
        span=training[name]
        if not isinstance(span,dict) or set(span)!={"from","through"}:
            raise ValueError("Invalid rolling training interval")
        raw_start,raw_end=span["from"],span["through"]
        if any(not isinstance(value,str) or not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}",value)
               for value in (raw_start,raw_end)):
            raise ValueError("Invalid rolling training date")
        start,end=date.fromisoformat(raw_start),date.fromisoformat(raw_end)
        if start>end or previous_end is not None and start<=previous_end:
            raise ValueError("Overlapping rolling training intervals")
        previous_end=end
        spans[name]=(raw_start,raw_end)
    return spans


def rolling_artifact_id(training,checkpoint_hash):
    cutoff=training_spans(training)["train"][1].replace("-","")
    if not isinstance(checkpoint_hash,str) or not re.fullmatch(DIGEST,checkpoint_hash):
        raise ValueError("Invalid checkpoint hash")
    content=json.dumps([training,checkpoint_hash],sort_keys=True,separators=(",",":")).encode()
    suffix=hashlib.sha256(content).hexdigest()[:16]
    return f"transformer-s3-rolling-{cutoff}-seed{training['seed']}-{suffix}"
