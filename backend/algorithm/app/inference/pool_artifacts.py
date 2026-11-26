"""Bind reviewed stock evidence to one immutable five-day model checkpoint."""

import argparse
import hashlib
import json
from pathlib import Path
from tempfile import TemporaryDirectory

from .artifact_contract import DIGEST, valid_artifact_id as valid_parent_id
from .artifacts import InferenceError, digest_file
from .expansion_artifacts import (
    EVIDENCE_LIMIT, STOCK_ID, ExpansionArtifactStore, encoded, matches, parse, summarize,
    valid_artifact_id as valid_existing_id,
)
from .rolling_artifacts import VersionedArtifactStore

POOL_ID = r"transformer-s3-pool-[a-f0-9]{16}"
POOL_LIMIT = 16 * 1024 * 1024


def valid_artifact_id(value):
    return valid_existing_id(value) or matches(POOL_ID, value)


def assemble_pool(parent_id, parent_hash, parent, evidence):
    if not 2 <= len(evidence) <= 50 or sum(map(len, evidence)) > POOL_LIMIT:
        raise ValueError("Expected 2 to 50 stock evidence files within the pool size limit")
    entries = []
    for payload in evidence:
        summary = summarize(parse(payload), parent_id, parent_hash, parent)
        entries.append({**summary, "evidenceHash": hashlib.sha256(payload).hexdigest()})
    entries.sort(key=lambda entry: entry["instrumentId"])
    instruments = [entry["instrumentId"] for entry in entries]
    if len(set(instruments)) != len(instruments):
        raise ValueError("Duplicate stock evidence")
    binding = {"parentArtifactId": parent_id, "parentManifestHash": parent_hash, "stocks": entries}
    evidence_hash = hashlib.sha256(encoded(binding)).hexdigest()
    return {**parent, "schemaVersion": 4, "artifactId": f"transformer-s3-pool-{evidence_hash[:16]}",
            "instruments": instruments, "pool": {**binding, "evidenceHash": evidence_hash}}


class PoolArtifactStore(ExpansionArtifactStore):
    def load(self, artifact_id, manifest_hash):
        if not matches(POOL_ID, artifact_id):
            return super().load(artifact_id, manifest_hash)
        if not matches(DIGEST, manifest_hash):
            raise InferenceError("MODEL_REQUEST_INVALID")
        folder = self.root / artifact_id
        if folder.is_symlink():
            raise InferenceError("MODEL_ARTIFACT_INVALID")
        manifest_path = self.bounded(folder, "manifest.json", 131072)
        if digest_file(manifest_path) != manifest_hash:
            raise InferenceError("MODEL_MANIFEST_HASH_MISMATCH")
        try:
            manifest = parse(manifest_path.read_bytes())
            binding = manifest["pool"]
            parent_id, parent_hash = binding["parentArtifactId"], binding["parentManifestHash"]
            ids = manifest["instruments"]
            if (not valid_parent_id(parent_id) or not matches(DIGEST, parent_hash)
                    or not isinstance(ids, list) or not 2 <= len(ids) <= 50
                    or any(not matches(STOCK_ID, stock) for stock in ids)
                    or len(ids) != len(set(ids))):
                raise ValueError("Invalid pool manifest")
            files = [self.bounded(folder, f"{stock}.json", EVIDENCE_LIMIT) for stock in ids]
            if sum(path.stat().st_size for path in files) > POOL_LIMIT:
                raise ValueError("Pool evidence too large")
            parent, model, mean, std = super().load(parent_id, parent_hash)
            expected = assemble_pool(parent_id, parent_hash, parent, [path.read_bytes() for path in files])
            if expected["artifactId"] != artifact_id or encoded(manifest) != encoded(expected):
                raise ValueError("Pool manifest does not match stock evidence")
        except (ValueError, TypeError, KeyError, AttributeError, ArithmeticError, OSError, RecursionError):
            raise InferenceError("MODEL_MANIFEST_INVALID") from None
        return manifest, model, mean, std


def build_pool(root, files):
    if not 2 <= len(files) <= 50:
        raise ValueError("Expected 2 to 50 stock evidence files")
    payloads = []
    size = 0
    for file in files:
        with file.open("rb") as stream:
            payload = stream.read(EVIDENCE_LIMIT + 1)
        size += len(payload)
        if len(payload) > EVIDENCE_LIMIT or size > POOL_LIMIT:
            raise ValueError("Pool evidence too large")
        payloads.append(payload)

    report = parse(payloads[0])
    parent_id, parent_hash = report["artifactId"], report["manifestHash"]
    if not valid_parent_id(parent_id) or not matches(DIGEST, parent_hash):
        raise ValueError("Invalid parent artifact")
    parent, _, _, _ = VersionedArtifactStore(root).load(parent_id, parent_hash)
    manifest = assemble_pool(parent_id, parent_hash, parent, payloads)
    contents = encoded(manifest)
    manifest_hash = hashlib.sha256(contents).hexdigest()
    artifact_id = manifest["artifactId"]
    folder = root / artifact_id
    if not folder.exists():
        with TemporaryDirectory(dir=root) as temporary:
            stage = Path(temporary) / artifact_id
            stage.mkdir()
            (stage / "manifest.json").write_bytes(contents)
            for payload in payloads:
                stock = parse(payload)["instrumentId"]
                (stage / f"{stock}.json").write_bytes(payload)
            stage.rename(folder)
    PoolArtifactStore(root).load(artifact_id, manifest_hash)
    return artifact_id, manifest_hash


if __name__ == "__main__":
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--root", type=Path, required=True)
    cli.add_argument("--evidence", type=Path, action="append", required=True)
    args = cli.parse_args()
    artifact_id, manifest_hash = build_pool(args.root, args.evidence)
    print(json.dumps({"artifactId": artifact_id, "manifestHash": manifest_hash}))
