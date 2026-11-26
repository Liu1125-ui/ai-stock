import json
from pathlib import Path
from unittest import TestCase
from unittest.mock import patch

from app.inference.artifacts import InferenceError, digest_file
from app.inference.expansion_artifacts import build_expansion, ExpansionArtifactStore
from app.inference.rolling_artifacts import VersionedArtifactStore
from tempfile import TemporaryDirectory


class ExpansionArtifactTest(TestCase):
    def test_expansion_binds_evidence_and_keeps_parent_weights(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            parent_id = "transformer-s3-2023-seed43"
            parent = {
                "checkpointHash": "b" * 64, "config": {}, "instruments": ["SH600030"],
                "trainingCutoff": "2023-12-31", "targetDefinition": "target",
            }
            evidence = root / "source-evidence.json"
            evidence.write_text(json.dumps({
                "schemaVersion": 1, "instrumentId": "SH600519", "artifactId": parent_id,
                "manifestHash": "a" * 64, "checkpointHash": "b" * 64,
                "targetDefinition": "target", "sourceFileSha256": "c" * 64,
                "sourcePayloadHash": "d" * 64,
                "requestedPeriod": {"from": "2025-01-02", "through": "2025-01-09"},
                "scores": {"windows": 1, "mae": 0.01, "baselineMae": 0.02, "directionAccuracy": 1.0},
                "samples": [{"origin": "2025-01-02", "targetDates": [
                    "2025-01-03", "2025-01-06", "2025-01-07", "2025-01-08", "2025-01-09"],
                    "actual": [1.02] * 5, "predicted": [1.01] * 5}],
            }), encoding="utf-8")
            checkpoint = object()
            with patch.object(VersionedArtifactStore, "load", return_value=(parent, checkpoint, None, None)):
                artifact_id, manifest_hash = build_expansion(root, evidence)
                meta, model, _, _ = ExpansionArtifactStore(root).load(artifact_id, manifest_hash)
                self.assertEqual(meta["instruments"], ["SH600519"])
                self.assertIs(model, checkpoint)
                self.assertEqual(meta["expansion"]["evidenceHash"], digest_file(evidence))
                (root / artifact_id / "evidence.json").write_text("{}", encoding="utf-8")
                with self.assertRaises(InferenceError):
                    ExpansionArtifactStore(root).load(artifact_id, manifest_hash)
