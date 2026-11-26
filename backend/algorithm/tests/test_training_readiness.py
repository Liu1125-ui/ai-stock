from datetime import date
import pytest

from app.training.readiness import decide

SESSION=date(2026,11,11)


def coverage(counts):
    return {role:{str(i):day for i,day in enumerate(days)} for role,days in counts.items()}


def test_freshness_depends_on_both_groups_and_previous_admission():
    old="2026-11-10"
    current=SESSION.isoformat()
    snapshot=coverage({"training_stock":[current]*24+[old]*6,"heldout_stock":[current]*8+[old]*2})
    assert decide(snapshot,SESSION,"a"*64)["state"]=="READY"
    assert decide(snapshot,SESSION,"a"*64,previous=("a"*64,date(2026,11,10)))["state"]=="ALREADY_PROCESSED"
    assert decide(snapshot,SESSION,"a"*64,previous=("b"*64,SESSION))["state"]=="ALREADY_PROCESSED"
    snapshot["heldout_stock"]["0"]=old
    assert decide(snapshot,SESSION,"a"*64)["state"]=="INSUFFICIENT_COVERAGE"


def test_stale_or_future_rows_cannot_trigger_weekly_training():
    snapshot=coverage({"training_stock":["2026-09-24"]*30,"heldout_stock":[None]*10})
    assert decide(snapshot,SESSION,"a"*64)["state"]=="STALE"
    snapshot["training_stock"]["0"]="2026-11-12"
    with pytest.raises(ValueError,match="beyond"):
        decide(snapshot,SESSION,"a"*64)

def test_preset_report_binds_exact_bytes_and_rejects_mismatched_metadata(tmp_path,monkeypatch):
    import hashlib
    import json
    from app.training import readiness

    preset=tmp_path/"rolling.json"
    training={"datasetHash":"a"*64,"seed":42,
              "train":{"from":"2022-01-01","through":"2023-12-31"},
              "validation":{"from":"2024-01-01","through":"2024-12-31"},
              "holdout":{"from":"2025-01-01","through":"2026-09-24"}}
    payload={"configurationId":"rolling","datasetHash":"a"*64,"schemaVersion":2,
             "trainingData":training,"seed":42,"epochs":1}
    preset.write_bytes(json.dumps(payload).encode())
    monkeypatch.setattr(readiness,"inspect",lambda *args:{"state":"READY","datasetHash":args[2]["datasetHash"]})
    result=readiness.inspect_preset(None,None,preset,SESSION)
    assert result["configurationId"]=="rolling"
    assert result["configurationHash"]==hashlib.sha256(preset.read_bytes()).hexdigest()
    payload["datasetHash"]="b"*64
    preset.write_bytes(json.dumps(payload).encode())
    with pytest.raises(ValueError,match="metadata mismatch"):
        readiness.inspect_preset(None,None,preset,SESSION)
