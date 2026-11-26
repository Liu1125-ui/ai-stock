import json
import hashlib

import pytest

from app.training.refresh import refresh
from test_training_collect_increment import DAYS, SESSIONS, START, STOCK, Source, archive


def fixture(tmp_path):
    base, index, digest = archive(tmp_path)
    root = tmp_path / "feed"
    root.mkdir()
    cohort = tmp_path / "cohort.json"
    cohort.write_text("{}")
    template = {"configurationId": "five-day-rolling", "seed": 42, "epochs": 1,
                "train": {"from": "2023-01-01", "through": "2024-12-31"},
                "validation": {"from": "2025-01-01", "through": "2025-06-30"},
                "holdout": {"from": "2025-07-01"}}
    return (root, base, index, digest, cohort, "a" * 64, template)


@pytest.fixture
def local_pipeline(monkeypatch):
    def freeze(_, cohort, cohort_hash, index, index_hash, as_of, output):
        output.mkdir()
        manifest = output / "manifest.json"
        manifest.write_text("{}")
        return {"datasetHash": hashlib.sha256(manifest.read_bytes()).hexdigest()}

    def readiness(_, manifest, preset, as_of):
        return {"state": readiness.state, "expectedSession": str(as_of), "datasetHash": hashlib.sha256(manifest.read_bytes()).hexdigest(),
                "configurationHash": hashlib.sha256(preset.read_bytes()).hexdigest(), "groups": {}}
    readiness.state = "READY"
    monkeypatch.setattr("app.training.refresh.freeze", freeze)
    monkeypatch.setattr("app.training.refresh.inspect_preset", readiness)
    return readiness


def test_publication_and_repeat_skip_stock_queries(tmp_path, local_pipeline):
    inputs = fixture(tmp_path)
    first = refresh(*inputs, START, DAYS[-1], sdk=Source())
    assert first["state"] == "PUBLISHED"
    root = inputs[0]
    published = (root / "current/readiness.json").read_bytes()
    report = json.loads(published)
    assert list((root / "snapshots").iterdir()) == [root / "snapshots" / report["datasetHash"]]

    class CalendarOnly(Source):
        def query_history_k_data_plus(self, *args, **kwargs):
            pytest.fail("Already published session made a stock query")

    assert refresh(*inputs, START, DAYS[-1], sdk=CalendarOnly())["state"] == "UNCHANGED"
    assert (root / "current/readiness.json").read_bytes() == published


def test_bad_coverage_preserves_existing_publication(tmp_path, local_pipeline):
    inputs = fixture(tmp_path)
    current = inputs[0] / "current"
    current.mkdir()
    report = current / "readiness.json"
    report.write_text('{"expectedSession":"2026-09-18","datasetHash":"' + "a" * 64 + '"}')
    previous = report.read_bytes()
    local_pipeline.state = "INSUFFICIENT_COVERAGE"
    result = refresh(*inputs, START, DAYS[-1], sdk=Source())
    assert result["state"] == "INSUFFICIENT_COVERAGE"
    assert report.read_bytes() == previous
    assert not (current / "calendar.json").exists()


def test_source_failure_and_parallel_refresh_keep_previous_report(tmp_path, local_pipeline):
    import fcntl
    inputs = fixture(tmp_path)
    root = inputs[0]
    first = refresh(*inputs, START, DAYS[-1], sdk=Source())
    assert first["state"] == "PUBLISHED"
    report = root / "current/readiness.json"
    published = report.read_bytes()

    with (root / ".refresh.lock").open("a+b") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        assert refresh(*inputs, START, DAYS[-1], sdk=Source()) == {"state": "BUSY"}

    with pytest.raises(ValueError, match="SOURCE_"):
        refresh(*inputs, START, DAYS[-1], sdk=Source("login"))
    assert report.read_bytes() == published
