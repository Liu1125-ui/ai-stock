"""Freeze a source-verified Baostock archive for rolling five-day training."""
import argparse
from collections import Counter
import csv
from datetime import date
import hashlib
import io
import json
from pathlib import Path
import re
import tempfile

import numpy as np

from app.inference.artifact_contract import DIGEST
from app.inference.artifacts import FEATURE,TARGET
from app.inference.rolling_artifacts import unique_fields
from .seal import canonical

FIELDS=("date","open","high","low","close","volume","amount","factor","tradestatus")
STOCK=r"(?:SH|SZ)[0-9]{6}"
GROUPS={"training_stock":30,"heldout_stock":10}
SOURCE="BAOSTOCK_RESEARCH_ARCHIVE"
RAW_LIMIT=4_194_304


def checked_read(path,limit,digest=None):
    if path.is_symlink() or not path.is_file():
        raise ValueError(f"Not a regular source file: {path.name}")
    with path.open("rb") as stream:
        raw=stream.read(limit+1)
    if not 0<len(raw)<=limit or digest is not None and hashlib.sha256(raw).hexdigest()!=digest:
        raise ValueError(f"Source size or digest mismatch: {path.name}")
    return raw


def parse_date(value):
    parsed=date.fromisoformat(value)
    if parsed.isoformat()!=value:
        raise ValueError("Noncanonical trading date")
    return parsed


def archive_rows(raw,as_of):
    reader=csv.DictReader(io.StringIO(raw.decode("utf-8-sig"),newline=""))
    if tuple(reader.fieldnames or ())!=FIELDS:
        raise ValueError("Unexpected Baostock archive columns")
    rows={}
    previous=None
    for row in reader:
        if len(rows)>=10000 or None in row or any(value is None for value in row.values()):
            raise ValueError("Malformed or oversized source rows")
        day=parse_date(row["date"])
        if day>as_of or previous is not None and day<=previous:
            raise ValueError("Source dates must be unique, ascending and at or before the cutoff")
        rows[day]=row
        previous=day
    if not rows:
        raise ValueError("Empty stock archive")
    return rows


def transform(raw,as_of):
    rows=archive_rows(raw,as_of)
    dates=[day.isoformat() for day in rows]
    values=[[float(row[key]) if row[key] else float("nan") for key in FIELDS[1:]]
            for row in rows.values()]
    data=np.asarray(values,dtype=np.float64)
    open_,high,low,close,volume,amount,factor,status=data.T
    with np.errstate(divide="ignore",invalid="ignore",over="ignore"):
        vwap=amount/volume
        valid=(np.isfinite(data).all(axis=1)&(data>0).all(axis=1)&(status==1)
               &(high>=np.maximum.reduce([open_,low,close]))&(low<=np.minimum.reduce([open_,high,close]))
               &(vwap>=low-.01)&(vwap<=high+.01))
        base=np.column_stack((open_*factor,high*factor,low*factor,close*factor,vwap*factor,volume/factor)).astype(np.float32)
        valid&=np.isfinite(base).all(axis=1)&(base>0).all(axis=1)
        base[~valid]=np.nan
        adjusted=np.where(valid,close*factor,np.nan)
        targets=np.full((len(dates),5),np.nan,dtype=np.float32)
        for step in range(1,6):
            if len(dates)>step:
                targets[:-step,step-1]=adjusted[step:]/adjusted[:-step]
    return {"dates":np.asarray(dates,dtype="U10"),"base":base,"targets":targets,"valid":valid}


def archive_path(stock):
    return f"source/{stock}.csv"


def verify_archived_source(root,stock,metadata):
    if not isinstance(metadata,dict) or set(metadata)!={"provider","version","path","sha256"}:
        raise ValueError("Invalid training source metadata")
    digest=metadata["sha256"]
    if (metadata["provider"]!=SOURCE or metadata["path"]!=archive_path(stock)
            or not isinstance(digest,str) or not re.fullmatch(DIGEST,digest)
            or metadata["version"]!="archive-sha256:"+digest):
        raise ValueError("Training archive version mismatch")
    path=root/metadata["path"]
    if not path.resolve().is_relative_to(root):
        raise ValueError("Training archive escapes snapshot root")
    checked_read(path,RAW_LIMIT,digest)


def freeze(source,cohort,cohort_hash,index,index_hash,as_of,output):
    source=source.resolve(strict=True)
    output=output.resolve()
    if output.is_relative_to(source) or output.exists():
        raise ValueError("Output must be a new directory outside the archive")
    cohort_bytes=checked_read(cohort,65536,cohort_hash)
    index_bytes=checked_read(index,65536,index_hash)
    members=json.loads(cohort_bytes,object_pairs_hook=unique_fields)["stocks"]
    archive=json.loads(index_bytes,object_pairs_hook=unique_fields)
    if not archive["source"].startswith("Baostock,") or len(members)!=40 or len(archive["features"])!=40:
        raise ValueError("Unexpected archive or cohort")
    hashes={row["stock"]:row["raw_sha256"] for row in archive["features"]}
    stocks={row["stock"] for row in members}
    if len(stocks)!=40 or stocks!=set(hashes) or Counter(row["role"] for row in members)!=GROUPS:
        raise ValueError("Source index and training cohort disagree")
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".training-snapshot-",dir=output.parent) as temp:
        root=Path(temp)/"snapshot"
        (root/"source").mkdir(parents=True)
        (root/"特征数据").mkdir()
        (root/"source"/"cohort.json").write_bytes(cohort_bytes)
        (root/"source"/"index.json").write_bytes(index_bytes)
        rows=[]
        for member in sorted(members,key=lambda row:row["stock"]):
            stock=member["stock"]
            digest=hashes[stock]
            if not re.fullmatch(STOCK,stock) or not re.fullmatch(DIGEST,digest):
                raise ValueError("Invalid source identity")
            raw=checked_read(source/f"{stock}_canonical.csv",RAW_LIMIT,digest)
            features=transform(raw,as_of)
            relative=f"特征数据/{stock}.npz"
            feature_path=root/relative
            np.savez_compressed(feature_path,**features)
            (root/archive_path(stock)).write_bytes(raw)
            rows.append({"stock":stock,"role":member["role"],"path":relative,"bytes":feature_path.stat().st_size,
                         "sha256":hashlib.sha256(feature_path.read_bytes()).hexdigest(),
                         "source":{"provider":SOURCE,"path":archive_path(stock),"sha256":digest,
                                   "version":"archive-sha256:"+digest}})
        manifest=canonical({"schemaVersion":2,"asOf":as_of.isoformat(),"featureDefinition":FEATURE,
                            "targetDefinition":TARGET,"cohortHash":cohort_hash,"sourceIndexHash":index_hash,"stocks":rows})
        (root/"manifest.json").write_bytes(manifest)
        root.rename(output)
    return {"datasetHash":hashlib.sha256(manifest).hexdigest(),"asOf":as_of.isoformat(),"stocks":len(rows)}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source",type=Path,required=True)
    parser.add_argument("--cohort",type=Path,required=True)
    parser.add_argument("--cohort-sha256",required=True)
    parser.add_argument("--source-index",type=Path,required=True)
    parser.add_argument("--source-index-sha256",required=True)
    parser.add_argument("--as-of",type=parse_date,required=True)
    parser.add_argument("--output",type=Path,required=True)
    args=parser.parse_args()
    try:
        result=freeze(args.source,args.cohort,args.cohort_sha256,args.source_index,args.source_index_sha256,args.as_of,args.output)
    except (ValueError,OSError,KeyError,TypeError) as error:
        parser.error(str(error))
    print(canonical(result).decode())


if __name__=="__main__":
    main()
