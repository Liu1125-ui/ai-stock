from datetime import date
import hashlib
import json
import pytest

from app.inference.artifacts import SOURCE
from app.training.increment import merge,publish

SESSIONS=[date(2026,9,day) for day in (17,18,21,22,23,24)]
HEADER="date,open,high,low,close,volume,amount,factor,tradestatus"


def data(sessions,factor="2"):
    return ("\n".join([HEADER,*[f"{day},10,12,9,11,100,1050,{factor},1" for day in sessions]])+"\n").encode()


def test_merges_only_new_sessions_and_keeps_the_archive_overlap():
    old=data(SESSIONS[:4])
    delta=data(SESSIONS[1:],"2.0000000001")
    result=merge(old,delta,SESSIONS,SESSIONS[-1])
    assert result.overlap==3
    assert result.appended==2
    assert result.csv.startswith(old)
    assert b"2026-09-24" in result.csv


@pytest.mark.parametrize("delta,reason",[
    (data(SESSIONS[1:],"2.01"),"overlap"),
    (data(SESSIONS[2:]),"overlap"),
    (data(SESSIONS[1:4]+SESSIONS[-1:]),"calendar"),
    (data(SESSIONS[1:-1]),"calendar"),
])
def test_rejects_unaligned_or_incomplete_increment(delta,reason):
    with pytest.raises(ValueError,match=reason):
        merge(data(SESSIONS[:4]),delta,SESSIONS,SESSIONS[-1])

def test_batch_mismatch_leaves_the_input_and_final_directory_untouched(tmp_path):
    base=tmp_path/"base"
    delta=tmp_path/"delta"
    base.mkdir()
    delta.mkdir()
    stock="SH600030"
    name=stock+"_canonical.csv"
    old=data(SESSIONS[:4])
    recent=data(SESSIONS[1:],"2.01")
    (base/name).write_bytes(old)
    (delta/name).write_bytes(recent)

    def pin(name,body):
        path=tmp_path/name
        raw=json.dumps(body).encode()
        path.write_bytes(raw)
        return path,hashlib.sha256(raw).hexdigest()

    base_path,base_hash=pin("base.json",{"source":"Baostock, fixture","features":[
        {"stock":stock,"raw_sha256":hashlib.sha256(old).hexdigest()}]})
    delta_path,delta_hash=pin("delta.json",{"provider":"baostock","basis":SOURCE,
        "asOf":SESSIONS[-1].isoformat(),"features":[{"stock":stock,"raw_sha256":hashlib.sha256(recent).hexdigest()}]})
    calendar,calendar_hash=pin("calendar.json",{"sessions":[day.isoformat() for day in SESSIONS]})
    output=tmp_path/"output"
    with pytest.raises(ValueError,match="Adjustment overlap changed"):
        publish(base,base_path,base_hash,delta,delta_path,delta_hash,calendar,calendar_hash,SESSIONS[-1],output)
    assert not output.exists()
    assert (base/name).read_bytes()==old
