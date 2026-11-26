"""Merge pinned Baostock increments into a new canonical training archive."""
import argparse
import csv
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
import hashlib
import io
import json
from pathlib import Path
import re
import tempfile

from app.inference.artifact_contract import DIGEST
from app.inference.artifacts import SOURCE
from app.inference.rolling_artifacts import unique_fields
from .seal import canonical
from .snapshot import FIELDS,RAW_LIMIT,STOCK,archive_rows,checked_read,parse_date

FACTOR_TOLERANCE=Decimal("0.000000001")
MINIMUM_OVERLAP=3


@dataclass(frozen=True)
class MergeResult:
    csv: bytes
    overlap: int
    appended: int


def number(value):
    if not value.strip():
        return None
    try:
        result=Decimal(value)
        if not result.is_finite() or len(value)>48 or abs(result.adjusted())>25:
            raise ValueError("Invalid archive value")
        return result
    except InvalidOperation:
        raise ValueError("Invalid archive value") from None


def validate_overlap(before,after):
    for key in (*FIELDS[1:7],"tradestatus"):
        if number(before[key])!=number(after[key]):
            raise ValueError("RAW overlap changed")
    previous=number(before["factor"])
    current=number(after["factor"])
    if previous is None or current is None:
        if previous!=current or number(before["tradestatus"])==1:
            raise ValueError("Adjustment overlap is missing")
    elif previous<=0 or current<=0 or abs(previous-current)>abs(previous)*FACTOR_TOLERANCE:
        raise ValueError("Adjustment overlap changed")
    return number(before["tradestatus"])==1


def merge(base,delta,calendar,as_of):
    old=archive_rows(base,as_of)
    incoming=archive_rows(delta,as_of)
    calendar=list(calendar)
    if not calendar or calendar!=sorted(set(calendar)) or calendar[-1]!=as_of:
        raise ValueError("Invalid trading calendar")
    anchor=next(iter(incoming))
    old_end=next(reversed(old))
    expected=[day for day in calendar if day>=anchor]
    if list(incoming)!=expected or old_end not in incoming or anchor>=old_end:
        raise ValueError("Increment does not match the calendar and archive boundary")
    if any(day not in old for day in expected if day<=old_end):
        raise ValueError("Trading calendar overlap is incomplete")
    overlap=sum(validate_overlap(old[day],incoming[day]) for day in expected if day<=old_end)
    if overlap<MINIMUM_OVERLAP:
        raise ValueError("Insufficient comparable overlap")
    append=[incoming[day] for day in expected if day>old_end]
    if not append:
        raise ValueError("No new calendar sessions in increment")
    for row in append:
        status=number(row["tradestatus"])
        values=[number(row[key]) for key in FIELDS[1:-1]]
        if status not in (0,1) or status==1 and any(value is None or value<=0 for value in values):
            raise ValueError("Invalid increment trading row")
    # Copy the original bytes, including original factors on the shared sessions.
    output=io.StringIO(newline="")
    writer=csv.DictWriter(output,fieldnames=FIELDS,lineterminator="\n")
    writer.writerows(append)
    combined=base+(b"" if base.endswith(b"\n") else b"\n")+output.getvalue().encode("utf-8")
    if len(combined)>RAW_LIMIT or len(old)+len(append)>10000:
        raise ValueError("Merged archive exceeds size limits")
    return MergeResult(combined,overlap,len(append))


def source_hashes(index):
    entries=index["features"]
    if not isinstance(entries,list) or not 1<=len(entries)<=40:
        raise ValueError("Invalid training archive index size")
    hashes={}
    for entry in entries:
        stock,digest=entry["stock"],entry["raw_sha256"]
        if not isinstance(stock,str) or not re.fullmatch(STOCK,stock) or stock in hashes or not isinstance(digest,str) or not re.fullmatch(DIGEST,digest):
            raise ValueError("Invalid or duplicate indexed stock")
        hashes[stock]=digest
    return hashes


def publish(base,base_index,base_hash,delta,delta_index,delta_hash,calendar,calendar_hash,as_of,output):
    base=base.resolve(strict=True)
    delta=delta.resolve(strict=True)
    output=output.resolve()
    if output.exists() or any(output.is_relative_to(source) for source in (base,delta)):
        raise ValueError("Output must be a new directory outside both inputs")
    previous=json.loads(checked_read(base_index,65536,base_hash),object_pairs_hook=unique_fields)
    update=json.loads(checked_read(delta_index,65536,delta_hash),object_pairs_hook=unique_fields)
    sessions=json.loads(checked_read(calendar,65536,calendar_hash),object_pairs_hook=unique_fields)["sessions"]
    sessions=[parse_date(day) for day in sessions]
    if (not previous["source"].startswith("Baostock,") or update["provider"]!="baostock"
            or update["basis"]!=SOURCE or update["asOf"]!=as_of.isoformat()):
        raise ValueError("Incompatible Baostock update metadata")
    archived=source_hashes(previous)
    additions=source_hashes(update)
    if not additions.keys()<=archived.keys():
        raise ValueError("Increment contains stocks outside the cohort")

    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".training-increment-",dir=output.parent) as temp:
        staged=Path(temp)/"archive"
        staged.mkdir()
        features=[]
        for stock,old_hash in sorted(archived.items()):
            filename=f"{stock}_canonical.csv"
            old=checked_read(base/filename,RAW_LIMIT,old_hash)
            row={"stock":stock,"previousSha256":old_hash,"appended":0}
            if stock in additions:
                new_hash=additions[stock]
                raw=checked_read(delta/filename,RAW_LIMIT,new_hash)
                try:
                    result=merge(old,raw,sessions,as_of)
                except ValueError as error:
                    raise ValueError(f"{stock}: {error}") from None
                data=result.csv
                row.update(deltaSha256=new_hash,overlap=result.overlap,appended=result.appended)
            else:
                data=old
            (staged/filename).write_bytes(data)
            row["raw_sha256"]=hashlib.sha256(data).hexdigest()
            features.append(row)
        index_bytes=canonical({"source":"Baostock, verified rolling archive","asOf":as_of.isoformat(),
                               "previousIndexHash":base_hash,"deltaIndexHash":delta_hash,
                               "calendarHash":calendar_hash,"features":features})
        (staged/"index.json").write_bytes(index_bytes)
        staged.rename(output)
    return {"indexHash":hashlib.sha256(index_bytes).hexdigest(),"asOf":as_of.isoformat(),
            "updatedStocks":len(additions),"retainedStocks":len(archived)-len(additions)}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for key in ("base","base-index","delta","delta-index","calendar","output"):
        parser.add_argument("--"+key,type=Path,required=True)
    for key in ("base-sha256","delta-sha256","calendar-sha256"):
        parser.add_argument("--"+key,required=True)
    parser.add_argument("--as-of",type=parse_date,required=True)
    args=parser.parse_args()
    try:
        result=publish(args.base,args.base_index,args.base_sha256,args.delta,args.delta_index,args.delta_sha256,
                       args.calendar,args.calendar_sha256,args.as_of,args.output)
    except (ValueError,OSError,KeyError,TypeError) as error:
        parser.error(str(error))
    print(canonical(result).decode())


if __name__=="__main__":
    main()
