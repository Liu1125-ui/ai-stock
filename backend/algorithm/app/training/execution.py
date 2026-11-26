"""Durable execution receipts on the algorithm worker's persistent volume."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
from contextlib import contextmanager
from typing import Annotated, Literal
import uuid

from pydantic import BaseModel, ConfigDict, StringConstraints, ValidationError

Id = Annotated[str, StringConstraints(pattern=r"^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$")]
Digest = Annotated[str, StringConstraints(pattern=r"^[a-f0-9]{64}$")]
ConfigurationId = Annotated[str, StringConstraints(pattern=r"^[a-z0-9][a-z0-9-]{0,63}$")]


class TrainingClaim(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    taskId: Id
    token: Id
    configurationId: ConfigurationId
    configurationHash: Digest
    datasetHash: Digest


class Preset(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    configurationId: ConfigurationId
    datasetHash: Digest
    seed: int
    epochs: int


class RollingPreset(Preset):
    schemaVersion: Literal[2]
    trainingData: dict


class TrainingError(Exception):
    def __init__(self, code, status):
        super().__init__(code)
        self.code = code
        self.status = status


class TrainingExecution:
    def __init__(self, root: Path, source: Path, manifest: Path, preset_file: Path, rolling_root: Path | None = None):
        self.root = root.resolve(strict=True)
        self.source = source.resolve(strict=True)
        self.manifest = manifest.resolve(strict=True)
        self.rolling_root = rolling_root.resolve(strict=True) if rolling_root else None
        if self.rolling_root and (not self.rolling_root.is_dir() or self.root.is_relative_to(self.rolling_root)):
            raise ValueError("Invalid rolling snapshot directory")
        self.preset_path = preset_file.resolve(strict=True)
        self.preset_directory = self.preset_path.is_dir()
        if not self.preset_directory and not self.preset_path.is_file():
            raise ValueError("Invalid training preset path")
        if not self.root.is_dir() or self.root.is_relative_to(self.source):
            raise ValueError("Invalid training output directory")

    def _load(self, task_id):
        try:
            with (self.root / (task_id + ".json")).open("rb") as stream:
                return json.loads(stream.read(8193))
        except FileNotFoundError:
            return None

    def _save(self, receipt):
        with tempfile.NamedTemporaryFile(dir=self.root, delete=False) as stream:
            temporary = Path(stream.name)
            try:
                stream.write(json.dumps(receipt, separators=(",", ":")).encode())
                stream.flush()
                os.fsync(stream.fileno())
                stream.close()
                os.replace(temporary, self.root / (receipt["taskId"] + ".json"))
                fd = os.open(self.root, os.O_DIRECTORY)
                try:
                    os.fsync(fd)
                finally:
                    os.close(fd)
            finally:
                temporary.unlink(missing_ok=True)

    @contextmanager
    def _slot(self):
        import fcntl
        with (self.root / ".training.lock").open("ab") as stream:
            try:
                fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                yield None
            else:
                # Closing the descriptor releases the lock when the child also exits.
                yield stream

    def _preset(self, claim):
        try:
            path = self.preset_path
            if self.preset_directory:
                path = (path / (claim.configurationHash + ".json")).resolve(strict=True)
                if path.parent != self.preset_path or not path.is_file():
                    raise ValueError("Invalid training preset file")
            with path.open("rb") as stream:
                raw = stream.read(4097)
        except (OSError, ValueError, RuntimeError):
            raise TrainingError("TRAINING_PRESET_UNAVAILABLE", 409) from None
        if len(raw) > 4096 or hashlib.sha256(raw).hexdigest() != claim.configurationHash:
            raise TrainingError("TRAINING_PRESET_UNAVAILABLE", 409)
        try:
            from app.inference.artifact_contract import training_spans
            from app.inference.rolling_artifacts import unique_fields
            data = json.loads(raw,object_pairs_hook=unique_fields)
            preset = (RollingPreset if isinstance(data,dict) and "schemaVersion" in data else Preset).model_validate(data)
            if isinstance(preset,RollingPreset):
                if type(data["schemaVersion"]) is not int:
                    raise ValueError("Invalid rolling preset schema")
                training_spans(preset.trainingData)
                if preset.trainingData["datasetHash"] != preset.datasetHash or preset.trainingData["seed"] != preset.seed:
                    raise ValueError("Rolling preset metadata mismatch")
                self._rolling_source(preset.datasetHash)
        except (ValidationError,ValueError,OSError,TypeError,KeyError,RuntimeError):
            raise TrainingError("TRAINING_PRESET_UNAVAILABLE", 409) from None
        if (preset.configurationId != claim.configurationId or preset.datasetHash != claim.datasetHash
                or preset.seed not in (42, 43, 44) or not 1 <= preset.epochs <= 6):
            raise TrainingError("TRAINING_PRESET_UNAVAILABLE", 409)
        return preset

    def _rolling_source(self, digest):
        if self.rolling_root is None:
            raise ValueError("Rolling snapshot directory is not configured")
        from .snapshot import checked_read
        source=self.rolling_root/digest
        if source.is_symlink() or source.resolve(strict=True).parent!=self.rolling_root or not source.is_dir():
            raise ValueError("Invalid rolling snapshot path")
        manifest=source/"manifest.json"
        metadata=json.loads(checked_read(manifest,65536,digest))
        if metadata.get("schemaVersion")!=2:
            raise ValueError("Rolling training requires a source-verified snapshot")
        return source,manifest

    def report(self, task_id, receipt):
        from app.inference.artifact_contract import ROLLING_ID
        from app.inference.rolling_artifacts import unique_fields
        from .snapshot import checked_read
        import re
        job=self.root/task_id
        if job.is_symlink():
            raise ValueError("Invalid training output")
        raw=checked_read(job/"evaluation.json",8192)
        report=json.loads(raw,object_pairs_hook=unique_fields)
        if (report["status"]!="EXPERIMENT_COMPLETED" or report["datasetHash"]!=receipt["datasetHash"]
                or receipt["checkpointHash"] is not None and report["checkpointHash"]!=receipt["checkpointHash"]):
            raise ValueError("Report does not match receipt")
        if "artifactId" in report:
            if not re.fullmatch(ROLLING_ID,report["artifactId"]):
                raise ValueError("Invalid rolling artifact ID")
            folder=job/report["artifactId"]
            if folder.is_symlink() or folder.resolve().parent!=job:
                raise ValueError("Invalid rolling artifact location")
        else:
            folder=job
        return report,raw,folder

    def submit(self, claim):
        existing = self._load(claim.taskId)
        if existing is not None:
            if any(existing.get(k) != v for k, v in claim.model_dump().items()):
                raise TrainingError("TRAINING_EXECUTION_CONFLICT", 409)
            return self.lookup(claim.taskId)
        with self._slot() as slot:
            if slot is None:
                raise TrainingError("TRAINING_WORKER_BUSY", 503)
            existing = self._load(claim.taskId)
            if existing is not None:
                if any(existing.get(k) != v for k, v in claim.model_dump().items()):
                    raise TrainingError("TRAINING_EXECUTION_CONFLICT", 409)
                return self._read_terminal(existing)
            receipt = {**claim.model_dump(), "state": "RUNNING", "checkpointHash": None}
            try:
                preset = self._preset(claim)
            except TrainingError:
                receipt["state"] = "FAILED"
                self._save(receipt)
                return receipt
            self._save(receipt)
            inherited = os.dup(slot.fileno())
            thread = threading.Thread(target=self._run, args=(receipt, preset, inherited), daemon=True)
            try:
                thread.start()
            except BaseException:
                os.close(inherited)
                self._read_terminal(receipt)
                raise
            return receipt

    def lookup(self, task_id):
        if str(uuid.UUID(task_id)) != task_id:
            raise TrainingError("TRAINING_EXECUTION_NOT_FOUND", 404)
        receipt = self._load(task_id)
        if receipt is None:
            raise TrainingError("TRAINING_EXECUTION_NOT_FOUND", 404)
        if receipt["state"] == "RUNNING":
            with self._slot() as slot:
                if slot is not None:
                    return self._read_terminal(self._load(task_id))
        return receipt

    def _read_terminal(self, receipt):
        if receipt["state"] != "RUNNING":
            return receipt
        result = dict(receipt, state="FAILED")
        try:
            from .snapshot import checked_read
            evaluation,_,folder=self.report(receipt["taskId"],receipt)
            weights=checked_read(folder/"checkpoint.pt",8388608,evaluation["checkpointHash"])
            result.update(state="SUCCEEDED",checkpointHash=hashlib.sha256(weights).hexdigest())
        except (OSError,ValueError,KeyError,TypeError):
            pass
        self._save(result)
        return result

    def _run(self, receipt, preset, slot_fd):
        try:
            try:
                output=str(self.root/receipt["taskId"])
                if isinstance(preset,RollingPreset):
                    source,manifest=self._rolling_source(preset.datasetHash)
                    with tempfile.NamedTemporaryFile(dir=self.root) as metadata:
                        metadata.write(json.dumps(preset.trainingData).encode())
                        metadata.flush()
                        self._run_command(["app.training.rolling_train","--source",str(source),"--manifest",str(manifest),
                                           "--training-data",metadata.name,"--epochs",str(preset.epochs),"--output",output],slot_fd)
                else:
                    self._run_command(["app.training.five_day","--source",str(self.source),"--manifest",str(self.manifest),
                                       "--dataset-sha256",receipt["datasetHash"],"--seed",str(preset.seed),
                                       "--epochs",str(preset.epochs),"--output",output],slot_fd)
            except (OSError,ValueError,subprocess.TimeoutExpired):
                pass
            self._read_terminal(receipt)
        finally:
            os.close(slot_fd)

    @staticmethod
    def _run_command(arguments,slot_fd):
        subprocess.run([sys.executable,"-m",*arguments],pass_fds=(slot_fd,),check=False,timeout=900,
                       stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
