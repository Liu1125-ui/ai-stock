import hashlib
import json
from pathlib import Path

import pytest

from app.training.execution import TrainingClaim, TrainingError, TrainingExecution


def make_preset(directory: Path, configuration: str, seed: int):
    dataset = "d" * 64
    raw = json.dumps({"configurationId": configuration, "datasetHash": dataset,
                      "seed": seed, "epochs": 1}).encode()
    digest = hashlib.sha256(raw).hexdigest()
    path = directory / (digest + ".json")
    path.write_bytes(raw)
    claim = TrainingClaim(taskId="00000000-0000-0000-0000-000000000001",
                          token="00000000-0000-0000-0000-000000000002",
                          configurationId=configuration, datasetHash=dataset, configurationHash=digest)
    return claim, path


def test_resolves_each_frozen_preset_and_keeps_single_file_compatible(tmp_path):
    source, output, presets = (tmp_path / name for name in ("source", "output", "presets"))
    for directory in (source, output, presets):
        directory.mkdir()
    manifest = source / "manifest.json"
    manifest.touch()
    first, first_file = make_preset(presets, "five-day", 42)
    second, second_file = make_preset(presets, "five-day-seed-43", 43)
    directory_worker = TrainingExecution(output, source, manifest, presets)
    assert directory_worker._preset(first).seed == 42
    assert directory_worker._preset(second).seed == 43
    assert TrainingExecution(output, source, manifest, first_file)._preset(first).seed == 42

    raw = second_file.read_bytes()
    second_file.write_bytes(raw.replace(b"43", b"44"))
    with pytest.raises(TrainingError, match="TRAINING_PRESET_UNAVAILABLE"):
        directory_worker._preset(second)
    second_file.unlink()
    with pytest.raises(TrainingError, match="TRAINING_PRESET_UNAVAILABLE"):
        directory_worker._preset(second)
    external = tmp_path / "outside.json"
    external.write_bytes(raw)
    second_file.symlink_to(external)
    with pytest.raises(TrainingError, match="TRAINING_PRESET_UNAVAILABLE"):
        directory_worker._preset(second)


def test_rolling_preset_checks_snapshot_fingerprint_and_split_binding(tmp_path):
    output,legacy,presets,snapshots=(tmp_path/name for name in ("jobs","legacy","presets","snapshots"))
    for path in (output,legacy,presets,snapshots):
        path.mkdir()
    manifest=legacy/"manifest.json"
    manifest.write_bytes(b"{}")
    snapshot_bytes=b'{"schemaVersion":2}'
    dataset=hashlib.sha256(snapshot_bytes).hexdigest()
    frozen=snapshots/dataset
    frozen.mkdir()
    (frozen/"manifest.json").write_bytes(snapshot_bytes)
    metadata={"datasetHash":dataset,"seed":42,"train":{"from":"2023-01-01","through":"2024-12-31"},
              "validation":{"from":"2025-01-01","through":"2025-06-30"},
              "holdout":{"from":"2025-07-01","through":"2026-09-24"}}

    def preset(training):
        raw=json.dumps({"configurationId":"rolling","schemaVersion":2,"datasetHash":dataset,
                        "seed":42,"epochs":1,"trainingData":training}).encode()
        digest=hashlib.sha256(raw).hexdigest()
        (presets/(digest+".json")).write_bytes(raw)
        return TrainingClaim(taskId="00000000-0000-0000-0000-000000000001",
            token="00000000-0000-0000-0000-000000000002",configurationId="rolling",
            datasetHash=dataset,configurationHash=digest)

    worker=TrainingExecution(output,legacy,manifest,presets,snapshots)
    claim=preset(metadata)
    assert worker._preset(claim).trainingData==metadata
    for changed in ({"seed":43},{"datasetHash":"a"*64},{"train":metadata["holdout"]}):
        with pytest.raises(TrainingError,match="TRAINING_PRESET_UNAVAILABLE"):
            worker._preset(preset({**metadata,**changed}))
    (frozen/"manifest.json").write_bytes(b'{"schemaVersion":1}')
    with pytest.raises(TrainingError,match="TRAINING_PRESET_UNAVAILABLE"):
        worker._preset(claim)
