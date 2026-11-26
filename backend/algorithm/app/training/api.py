import json
import os
from pathlib import Path
import secrets

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from .execution import Id, TrainingClaim, TrainingError, TrainingExecution

from .model_comparison import compare_request

router = APIRouter(prefix="/internal/v1/training-executions")
router.add_api_route("/compare-models", compare_request, methods=["POST"])


def worker(request):
    configured = os.environ.get("SERVICE_TOKEN", "")
    tokens = request.headers.getlist("x-service-token")
    if not configured or len(tokens) != 1 or not secrets.compare_digest(tokens[0].encode(), configured.encode()):
        raise TrainingError("INTERNAL_AUTH_REQUIRED", 401)
    if os.environ.get("TRAINING_EXECUTION_ENABLED", "false") != "true":
        raise TrainingError("TRAINING_EXECUTION_UNAVAILABLE", 503)
    try:
        preset_file = os.environ.get("TRAINING_PRESET_FILE")
        preset_directory = os.environ.get("TRAINING_PRESET_DIRECTORY")
        if bool(preset_file) == bool(preset_directory):
            raise ValueError("Configure exactly one training preset location")
        preset_path = Path(preset_directory or preset_file).resolve(strict=True)
        if (preset_directory and not preset_path.is_dir()) or (preset_file and not preset_path.is_file()):
            raise ValueError("Invalid training preset location")
        root, source, manifest = (Path(os.environ[name]) for name in (
            "TRAINING_EXECUTION_ROOT", "TRAINING_DATA_ROOT", "TRAINING_DATA_MANIFEST",
        ))
        rolling = os.environ.get("TRAINING_ROLLING_SNAPSHOT_ROOT")
        return TrainingExecution(root, source, manifest, preset_path, Path(rolling) if rolling else None)
    except (KeyError, ValueError, OSError):
        raise TrainingError("TRAINING_EXECUTION_UNAVAILABLE", 503) from None


def reply(code, status, data=None):
    return JSONResponse({"code": code, "message": code, "data": data, "traceId": None},
                        status_code=status, headers={"Cache-Control": "no-store"})


def unique_fields(pairs):
    data = dict(pairs)
    if len(data) != len(pairs):
        raise ValueError("Duplicate JSON field")
    return data


@router.post("")
async def submit(request: Request):
    try:
        execution = worker(request)
        body = bytearray()
        async for block in request.stream():
            if len(body) + len(block) > 2048:
                return reply("TRAINING_EXECUTION_INVALID", 400)
            body.extend(block)
        claim = TrainingClaim.model_validate(json.loads(body, object_pairs_hook=unique_fields))
        from starlette.concurrency import run_in_threadpool
        return reply("OK", 200, await run_in_threadpool(execution.submit, claim))
    except (ValidationError, ValueError, UnicodeError):
        return reply("TRAINING_EXECUTION_INVALID", 400)
    except TrainingError as error:
        return reply(error.code, error.status)


@router.get("/{task_id}")
def lookup(task_id: Id, request: Request):
    try:
        return reply("OK", 200, worker(request).lookup(task_id))
    except TrainingError as error:
        return reply(error.code, error.status)

@router.post("/{task_id}/candidate")
def candidate(task_id: Id, request: Request):
    try:
        execution = worker(request)
        receipt = execution.lookup(task_id)
        if receipt["state"] != "SUCCEEDED":
            return reply("TRAINING_EXECUTION_NOT_SUCCEEDED", 409)
        candidate_root = os.environ.get("TRAINING_CANDIDATE_ROOT")
        if not candidate_root:
            return reply("TRAINING_CANDIDATE_UNAVAILABLE", 503)
        try:
            root = Path(candidate_root).resolve(strict=True)
            if (not root.is_dir() or root.is_relative_to(execution.source)
                    or execution.rolling_root and root.is_relative_to(execution.rolling_root)):
                raise ValueError("Invalid candidate directory")
            report,raw,folder=execution.report(task_id,receipt)
            if "artifactId" in report:
                from .rolling_train import export_candidate
                registration=export_candidate(folder,root,report,raw)
            else:
                from .seal import seal
                registration=seal(folder,root,receipt["checkpointHash"])
        except (OSError, ValueError, TypeError, KeyError, RuntimeError):
            return reply("TRAINING_CANDIDATE_UNAVAILABLE", 503)
        return reply("OK", 200, {"taskId": task_id, "checkpointHash": receipt["checkpointHash"],
                                  "registration": registration})
    except TrainingError as error:
        return reply(error.code, error.status)

@router.get("/{task_id}/metrics")
def metrics(task_id: Id, request: Request):
    try:
        execution = worker(request)
        receipt = execution.lookup(task_id)
        if receipt["state"] != "SUCCEEDED":
            return reply("TRAINING_EXECUTION_NOT_SUCCEEDED", 409)
        try:
            report,_,_=execution.report(task_id,receipt)
        except (OSError, ValueError, TypeError, KeyError):
            return reply("TRAINING_METRICS_UNAVAILABLE", 503)
        return reply("OK", 200, report)
    except TrainingError as error:
        return reply(error.code, error.status)
