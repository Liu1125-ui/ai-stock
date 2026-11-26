from pathlib import Path
from types import SimpleNamespace

import numpy as np
import pytest

from app.training.model_comparison import compare


def test_same_holdout_and_cutoff(monkeypatch, tmp_path):
    from app.training import model_comparison as service
    digest = "a" * 64
    candidate = {"artifactId": "candidate", "manifestHash": digest}
    reference = {"artifactId": "reference", "manifestHash": "b" * 64}
    meta = {"trainingCutoff": "2026-03-31", "trainingData": {
        "datasetHash": digest, "seed": 42,
        "train": {"from": "2024-07-01", "through": "2026-03-31"},
        "validation": {"from": "2026-04-01", "through": "2026-06-30"},
        "holdout": {"from": "2026-07-01", "through": "2026-11-13"}}}
    reference_meta = {"trainingCutoff": "2023-12-31"}
    monkeypatch.setattr(service, "VersionedArtifactStore", lambda _: SimpleNamespace(
        load=lambda name, _: (meta if name == "candidate" else reference_meta, name, 0, 1)))
    (tmp_path / digest).mkdir()
    def load(source, manifest, expected_hash, *, splits):
        assert source == tmp_path / digest and expected_hash == digest
        assert splits["holdout"] == ("2026-07-01", "2026-11-13", "heldout_stock")
        return {"holdout": (np.ones((2, 60, 6), dtype="float32"), np.ones((2, 5), dtype="float32"))}
    monkeypatch.setattr(service, "load_samples", load)
    monkeypatch.setattr(service, "evaluate", lambda model, data, labels: dict(
        mae=0.01 if model == "reference" else 0.02, rmse=0.03, direction=0.5, samples=len(data)))
    result = compare(tmp_path, tmp_path, candidate, reference)
    assert result["windows"] == 2
    assert result["baseline"]["mae"] == 0
    assert result["candidate"]["mae"] > result["reference"]["mae"]
    reference_meta["trainingCutoff"] = "2026-07-01"
    with pytest.raises(ValueError, match="overlaps"):
        compare(tmp_path, tmp_path, candidate, reference)
