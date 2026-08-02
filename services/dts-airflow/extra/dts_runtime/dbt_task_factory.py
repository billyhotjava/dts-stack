"""Single canonical Airflow/dbt task factory for DTS model materialization.

The factory deliberately accepts only deployment metadata. Runtime project, selector and target
are resolved by dts-platform from a one-time token. Warehouse credentials remain in a short-lived
host tmpfs profile lease and never enter DagRun conf, XCom or task environment variables.
"""

from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import http.client
import json
import logging
import os
from pathlib import Path
import re
import socket
import subprocess
from typing import Any
from urllib import error as urllib_error
from urllib.parse import quote
from urllib import request as urllib_request
from uuid import UUID


LOG = logging.getLogger(__name__)
_SHA256 = re.compile(r"^[0-9a-f]{64}$")
_DAG_ID = re.compile(r"^[a-z][a-z0-9_]{2,199}$")
_SAFE_NAME = re.compile(r"^[A-Za-z_][A-Za-z0-9_-]{0,127}$")
_SAFE_IMAGE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._/:@-]{0,255}$")
_DIGEST_IMAGE = re.compile(
    r"^[A-Za-z0-9][A-Za-z0-9._/:@-]{0,255}@sha256:[0-9a-f]{64}$"
)
_DOCKER_CONTAINER_ID = re.compile(r"^[0-9a-f]{64}$")
_RELEASE_BUILD_CONF_KEYS = frozenset(
    {
        "pipelineRunGroupId",
        "candidateId",
        "candidateVersion",
        "attempt",
        "runPurpose",
        "runtimeSpecToken",
        "bundleChecksum",
    }
)
_OPERATIONAL_RUN_CONF_KEYS = frozenset(
    {
        "pipelineRunGroupId",
        "bindingId",
        "bindingVersion",
        "runPurpose",
        "triggerType",
        "runtimeSpecToken",
        "bundleChecksum",
    }
)
_BASE_RUNTIME_SPEC_KEYS = frozenset(
    {
        "pipelineRunGroupId",
        "runPurpose",
        "projectBundleChecksum",
        "selector",
        "targetName",
        "profileLeaseId",
        "expiresAt",
        "credentialVersionRef",
    }
)
_RUNTIME_CERTIFICATION_KEYS = frozenset(
    {
        "runtimeProfileId",
        "candidateProfileId",
        "dbtCoreVersion",
        "dbtPostgresVersion",
        "adapter",
        "databaseType",
        "requirementsLockSha256",
        "candidateImageDigest",
        "imageRef",
        "evidenceManifestSha256",
    }
)
_RUNTIME_SPEC_KEYS = _BASE_RUNTIME_SPEC_KEYS | _RUNTIME_CERTIFICATION_KEYS
_EXPECTED_CANDIDATE_PROFILE_ID = (
    "H83-RT01-LINUX-AMD64-DBT11022-PG1100-LOCK-"
    "01d7c02b6bf4fefdfc188cbf9ef8aed4fb243c227c060103f195c4ca45af5f02"
)
_EXPECTED_REQUIREMENTS_LOCK_SHA256 = (
    "01d7c02b6bf4fefdfc188cbf9ef8aed4fb243c227c060103f195c4ca45af5f02"
)
_EXPECTED_CERTIFICATION_PROFILE_ID = (
    "H83-CERT-RT01-LINUX-AMD64-EVIDENCE-"
    "bcd2fc84b05b9508990538c6642be7ac7b35073ec034e6b1980b22f556345a68"
)
_EXPECTED_CANDIDATE_IMAGE_DIGEST = (
    "sha256:fe1d15f1b4215e693dadfc1d99be2ae07c7e50e8df144504005feb41cc7686b7"
)
_EXPECTED_CERTIFIED_IMAGE_DIGEST = (
    "sha256:2f6dddb7237fdb7141f452b6d09da0379ef7569f2f82560473f304b265cbbd85"
)
_EXPECTED_EVIDENCE_MANIFEST_SHA256 = (
    "bcd2fc84b05b9508990538c6642be7ac7b35073ec034e6b1980b22f556345a68"
)
_PREPARE_TASK_ID = "prepare_runtime"
_BUILD_TASK_ID = "dbt_build"
_SYNC_TASK_ID = "sync_manifest_and_probe"
_PROFILE_LEASE_RENEW_MIN_INTERVAL_SECONDS = 5.0
_PROFILE_LEASE_RENEW_MAX_INTERVAL_SECONDS = 60.0
_PROCESS_TERMINATE_TIMEOUT_SECONDS = 10.0
_PROCESS_KILL_TIMEOUT_SECONDS = 10.0
_DOCKER_SOCKET_PATH = "/var/run/docker.sock"
_DOCKER_ENGINE_HOST = f"unix://{_DOCKER_SOCKET_PATH}"
_DOCKER_ENGINE_TIMEOUT_SECONDS = 12.0
_DOCKER_ENGINE_MAX_RESPONSE_BYTES = 64 * 1024
_PROFILE_LEASE_OWNER_LABEL = "com.yuzhi.dts.dbt.profile-lease-id"
_PIPELINE_RUN_GROUP_OWNER_LABEL = (
    "com.yuzhi.dts.dbt.pipeline-run-group-id"
)


class DbtBuildProcessError(RuntimeError):
    def __init__(self, returncode: int) -> None:
        self.returncode = returncode
        super().__init__(f"dbt build process exited with code {returncode}")


def _required_text(value: Any, name: str) -> str:
    text = "" if value is None else str(value).strip()
    if not text:
        raise ValueError(f"{name} is required")
    return text


def _uuid_text(value: Any, name: str) -> str:
    text = _required_text(value, name)
    try:
        return str(UUID(text))
    except (TypeError, ValueError) as failure:
        raise ValueError(f"{name} must be UUID") from failure


def _checksum(value: Any, name: str) -> str:
    text = _required_text(value, name)
    if not _SHA256.fullmatch(text):
        raise ValueError(f"{name} must be a lowercase SHA-256 checksum")
    return text


def _safe_name(value: Any, name: str) -> str:
    text = _required_text(value, name)
    if not _SAFE_NAME.fullmatch(text):
        raise ValueError(f"{name} is invalid")
    return text


def _fixed_root(value: str, name: str) -> Path:
    root = Path(_required_text(value, name))
    if not root.is_absolute() or ".." in root.parts:
        raise ValueError(f"{name} must be an absolute fixed root")
    return root


def _validate_release_build_conf(conf: Any) -> dict[str, Any]:
    if not isinstance(conf, dict):
        raise ValueError("DagRun conf must be an object")
    unsupported = sorted(set(conf) - _RELEASE_BUILD_CONF_KEYS)
    if unsupported:
        raise ValueError(
            "DagRun conf contains unsupported keys: " + ", ".join(unsupported)
        )
    required = _RELEASE_BUILD_CONF_KEYS
    missing = sorted(key for key in required if key not in conf)
    if missing:
        raise ValueError(
            "DagRun conf is missing required keys: " + ", ".join(missing)
        )
    normalized = dict(conf)
    normalized["pipelineRunGroupId"] = _uuid_text(
        conf["pipelineRunGroupId"], "pipelineRunGroupId"
    )
    normalized["candidateId"] = _uuid_text(conf["candidateId"], "candidateId")
    normalized["candidateVersion"] = int(conf["candidateVersion"])
    normalized["attempt"] = int(conf["attempt"])
    if normalized["candidateVersion"] < 1 or normalized["attempt"] < 1:
        raise ValueError("candidateVersion and attempt must be positive")
    if _required_text(conf["runPurpose"], "runPurpose") != "RELEASE_BUILD":
        raise ValueError("runPurpose is not supported by the executor DAG")
    normalized["runPurpose"] = "RELEASE_BUILD"
    normalized["runtimeSpecToken"] = _required_text(
        conf["runtimeSpecToken"], "runtimeSpecToken"
    )
    normalized["bundleChecksum"] = _checksum(
        conf["bundleChecksum"], "bundleChecksum"
    )
    return normalized


def _validate_operational_run_conf(conf: Any) -> dict[str, Any]:
    if not isinstance(conf, dict):
        raise ValueError("DagRun conf must be an object")
    unsupported = sorted(set(conf) - _OPERATIONAL_RUN_CONF_KEYS)
    if unsupported:
        raise ValueError(
            "DagRun conf contains unsupported keys: " + ", ".join(unsupported)
        )
    missing = sorted(key for key in _OPERATIONAL_RUN_CONF_KEYS if key not in conf)
    if missing:
        raise ValueError(
            "DagRun conf is missing required keys: " + ", ".join(missing)
        )
    normalized = dict(conf)
    normalized["pipelineRunGroupId"] = _uuid_text(
        conf["pipelineRunGroupId"], "pipelineRunGroupId"
    )
    normalized["bindingId"] = _uuid_text(conf["bindingId"], "bindingId")
    normalized["bindingVersion"] = int(conf["bindingVersion"])
    if normalized["bindingVersion"] < 1:
        raise ValueError("bindingVersion must be positive")
    if _required_text(conf["runPurpose"], "runPurpose") != "OPERATIONAL_RUN":
        raise ValueError("runPurpose is not supported by the plan DAG")
    normalized["runPurpose"] = "OPERATIONAL_RUN"
    trigger_type = _required_text(conf["triggerType"], "triggerType").upper()
    if trigger_type not in {"MANUAL", "CRON"}:
        raise ValueError("triggerType is invalid")
    normalized["triggerType"] = trigger_type
    normalized["runtimeSpecToken"] = _required_text(
        conf["runtimeSpecToken"], "runtimeSpecToken"
    )
    normalized["bundleChecksum"] = _checksum(
        conf["bundleChecksum"], "bundleChecksum"
    )
    return normalized


def _validate_runtime_spec(runtime_spec: Any) -> dict[str, str]:
    if not isinstance(runtime_spec, dict):
        raise ValueError("runtime spec must be an object")
    unsupported = sorted(set(runtime_spec) - _RUNTIME_SPEC_KEYS)
    if unsupported:
        raise ValueError(
            "runtime spec contains unsupported keys: " + ", ".join(unsupported)
        )
    missing = sorted(
        key for key in _BASE_RUNTIME_SPEC_KEYS if key not in runtime_spec
    )
    if missing:
        raise ValueError(
            "runtime spec is missing required keys: " + ", ".join(missing)
        )
    purpose = _required_text(runtime_spec["runPurpose"], "runPurpose")
    if purpose not in {"RELEASE_BUILD", "OPERATIONAL_RUN"}:
        raise ValueError("runtime purpose is invalid")
    selector = _required_text(runtime_spec["selector"], "selector")
    if "\x00" in selector or "\n" in selector or "\r" in selector:
        raise ValueError("selector is invalid")
    normalized = {
        "pipelineRunGroupId": _uuid_text(
            runtime_spec["pipelineRunGroupId"], "pipelineRunGroupId"
        ),
        "runPurpose": purpose,
        "projectBundleChecksum": _checksum(
            runtime_spec["projectBundleChecksum"], "projectBundleChecksum"
        ),
        "selector": selector,
        "targetName": _safe_name(runtime_spec["targetName"], "targetName"),
        "profileLeaseId": _uuid_text(
            runtime_spec["profileLeaseId"], "profileLeaseId"
        ),
        "expiresAt": _required_text(runtime_spec["expiresAt"], "expiresAt"),
        "credentialVersionRef": _required_text(
            runtime_spec["credentialVersionRef"], "credentialVersionRef"
        ),
    }
    if purpose == "RELEASE_BUILD":
        missing_certification = sorted(
            key
            for key in _RUNTIME_CERTIFICATION_KEYS
            if key not in runtime_spec
        )
        if missing_certification:
            raise ValueError(
                "runtime certification is missing required keys: "
                + ", ".join(missing_certification)
            )
        image_ref = _required_text(runtime_spec["imageRef"], "imageRef")
        if (
            not _DIGEST_IMAGE.fullmatch(image_ref)
            or not image_ref.endswith("@" + _EXPECTED_CERTIFIED_IMAGE_DIGEST)
        ):
            raise ValueError("runtime imageRef is not certified")
        certification = {
            "runtimeProfileId": _safe_name(
                runtime_spec["runtimeProfileId"], "runtimeProfileId"
            ),
            "candidateProfileId": _required_text(
                runtime_spec["candidateProfileId"], "candidateProfileId"
            ),
            "dbtCoreVersion": _required_text(
                runtime_spec["dbtCoreVersion"], "dbtCoreVersion"
            ),
            "dbtPostgresVersion": _required_text(
                runtime_spec["dbtPostgresVersion"], "dbtPostgresVersion"
            ),
            "adapter": _required_text(runtime_spec["adapter"], "adapter"),
            "databaseType": _required_text(
                runtime_spec["databaseType"], "databaseType"
            ),
            "requirementsLockSha256": _checksum(
                runtime_spec["requirementsLockSha256"],
                "requirementsLockSha256",
            ),
            "candidateImageDigest": _required_text(
                runtime_spec["candidateImageDigest"],
                "candidateImageDigest",
            ),
            "imageRef": image_ref,
            "evidenceManifestSha256": _checksum(
                runtime_spec["evidenceManifestSha256"],
                "evidenceManifestSha256",
            ),
        }
        if (
            certification["runtimeProfileId"]
            != _EXPECTED_CERTIFICATION_PROFILE_ID
            or certification["candidateProfileId"]
            != _EXPECTED_CANDIDATE_PROFILE_ID
            or certification["dbtCoreVersion"] != "1.10.22"
            or certification["dbtPostgresVersion"] != "1.10.0"
            or certification["adapter"] != "postgres"
            or certification["databaseType"] != "PostgreSQL"
            or certification["requirementsLockSha256"]
            != _EXPECTED_REQUIREMENTS_LOCK_SHA256
            or certification["candidateImageDigest"]
            != _EXPECTED_CANDIDATE_IMAGE_DIGEST
            or certification["evidenceManifestSha256"]
            != _EXPECTED_EVIDENCE_MANIFEST_SHA256
        ):
            raise ValueError("runtime certification does not match executor")
        normalized.update(certification)
    return normalized


def _build_docker_command(
    runtime_spec: dict[str, Any],
    image: str,
    project_host_root: str,
    profile_host_root: str,
    docker_network: str,
) -> list[str]:
    runtime = _validate_runtime_spec(runtime_spec)
    safe_image = _required_text(image, "dbt image")
    if not _SAFE_IMAGE.fullmatch(safe_image):
        raise ValueError("dbt image is invalid")
    if (
        runtime["runPurpose"] == "RELEASE_BUILD"
        and safe_image != runtime["imageRef"]
    ):
        raise ValueError("release build image does not match runtime spec")
    safe_network = _safe_name(docker_network, "docker network")
    project_root = _fixed_root(project_host_root, "project host root")
    profile_root = _fixed_root(profile_host_root, "profile host root")
    project_directory = (
        project_root
        / ".dts-scoped-runs"
        / f"candidate-{runtime['projectBundleChecksum']}"
    )
    profile_directory = profile_root / runtime["profileLeaseId"]
    container_name = _dbt_container_name(runtime["profileLeaseId"])
    command = [
        "docker", "--host", _DOCKER_ENGINE_HOST, "run", "--rm",
        "--name", container_name,
        "--label",
        f"{_PROFILE_LEASE_OWNER_LABEL}={runtime['profileLeaseId']}",
        "--label",
        (
            f"{_PIPELINE_RUN_GROUP_OWNER_LABEL}="
            f"{runtime['pipelineRunGroupId']}"
        ),
        "--network", safe_network,
        "-v", f"{project_directory}:/opt/dbt",
        "-v", f"{profile_directory}:/root/.dbt:ro",
        safe_image,
        "build",
        "--project-dir", "/opt/dbt",
        "--profiles-dir", "/root/.dbt",
        "--target", runtime["targetName"],
        "--select", runtime["selector"],
    ]
    return command


def _dbt_container_name(lease_id: str) -> str:
    return "dts-dbt-" + _uuid_text(lease_id, "profileLeaseId")


class _DockerSocketConnection(http.client.HTTPConnection):
    def __init__(self) -> None:
        super().__init__("localhost", timeout=_DOCKER_ENGINE_TIMEOUT_SECONDS)

    def connect(self) -> None:
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(self.timeout)
        self.sock.connect(_DOCKER_SOCKET_PATH)


def _docker_engine_request(method: str, path: str) -> tuple[int, bytes]:
    if method not in {"GET", "POST"} or not path.startswith("/containers/"):
        raise ValueError("Docker Engine request is invalid")
    connection = _DockerSocketConnection()
    try:
        connection.request(method, path)
        response = connection.getresponse()
        payload = response.read(_DOCKER_ENGINE_MAX_RESPONSE_BYTES + 1)
        if len(payload) > _DOCKER_ENGINE_MAX_RESPONSE_BYTES:
            raise RuntimeError("Docker Engine response is too large")
        return response.status, payload
    finally:
        connection.close()


def _inspect_dbt_container(
    container_reference: str,
    lease_id: str,
    pipeline_run_group_id: str,
    *,
    expected_container_id: str | None = None,
) -> tuple[str, bool] | None:
    expected_lease_id = _uuid_text(lease_id, "profileLeaseId")
    expected_run_group_id = _uuid_text(
        pipeline_run_group_id,
        "pipelineRunGroupId",
    )
    if (
        container_reference != _dbt_container_name(expected_lease_id)
        and not _DOCKER_CONTAINER_ID.fullmatch(container_reference)
    ):
        raise ValueError("dbt container reference is invalid")
    if (
        expected_container_id is not None
        and not _DOCKER_CONTAINER_ID.fullmatch(expected_container_id)
    ):
        raise ValueError("expected dbt container id is invalid")
    encoded_reference = quote(container_reference, safe="")
    status, payload = _docker_engine_request(
        "GET",
        f"/containers/{encoded_reference}/json",
    )
    if status == 404:
        return None
    if status != 200:
        raise RuntimeError("Docker Engine container inspection failed")
    try:
        decoded = json.loads(payload.decode("utf-8"))
        container_id = decoded["Id"]
        labels = decoded["Config"]["Labels"]
        running = decoded["State"]["Running"]
        if (
            not isinstance(container_id, str)
            or not _DOCKER_CONTAINER_ID.fullmatch(container_id)
            or not isinstance(labels, dict)
            or not isinstance(running, bool)
        ):
            raise TypeError
    except (AttributeError, UnicodeDecodeError, json.JSONDecodeError) as failure:
        raise RuntimeError(
            "Docker Engine container inspection response is invalid"
        ) from failure
    except (KeyError, TypeError) as failure:
        raise RuntimeError(
            "Docker Engine container inspection response is invalid"
        ) from failure
    if (
        expected_container_id is not None
        and container_id != expected_container_id
    ):
        raise RuntimeError(
            "dbt container identity does not match inspected container"
        )
    if (
        labels.get(_PROFILE_LEASE_OWNER_LABEL) != expected_lease_id
        or labels.get(_PIPELINE_RUN_GROUP_OWNER_LABEL)
        != expected_run_group_id
    ):
        raise RuntimeError("dbt container ownership does not match runtime")
    return container_id, running


def _stop_dbt_container(
    container_name: str,
    lease_id: str,
    pipeline_run_group_id: str,
) -> None:
    expected_lease_id = _uuid_text(lease_id, "profileLeaseId")
    if container_name != _dbt_container_name(expected_lease_id):
        raise ValueError("dbt container name does not match profile lease")
    inspected = _inspect_dbt_container(
        container_name,
        expected_lease_id,
        pipeline_run_group_id,
    )
    if inspected is None:
        return
    container_id, running = inspected
    if running is not True:
        return
    encoded_container_id = quote(container_id, safe="")
    try:
        _docker_engine_request(
            "POST",
            f"/containers/{encoded_container_id}/stop?t="
            f"{int(_PROCESS_TERMINATE_TIMEOUT_SECONDS)}",
        )
    except (OSError, TimeoutError, http.client.HTTPException):
        pass
    inspected = _inspect_dbt_container(
        container_id,
        expected_lease_id,
        pipeline_run_group_id,
        expected_container_id=container_id,
    )
    if inspected is None:
        return
    _, running = inspected
    if running is not True:
        return
    try:
        _docker_engine_request(
            "POST",
            f"/containers/{encoded_container_id}/kill",
        )
    except (OSError, TimeoutError, http.client.HTTPException):
        pass
    try:
        inspected = _inspect_dbt_container(
            container_id,
            expected_lease_id,
            pipeline_run_group_id,
            expected_container_id=container_id,
        )
        if inspected is None:
            return
        _, running = inspected
        if running is not True:
            return
    except (OSError, TimeoutError, http.client.HTTPException) as failure:
        raise RuntimeError(
            "dbt container stop could not be confirmed"
        ) from failure
    raise RuntimeError("dbt container stop could not be confirmed stopped")


def _platform_request(
    path: str,
    *,
    method: str = "POST",
    payload: dict[str, Any] | None = None,
    runtime_spec_token: str | None = None,
) -> dict[str, Any]:
    base_url = _required_text(
        os.getenv("DTS_PLATFORM_INTERNAL_BASE_URL"),
        "platform internal base URL",
    ).rstrip("/")
    if not (
        base_url.startswith("http://") or base_url.startswith("https://")
    ):
        raise ValueError("platform internal base URL is invalid")
    if not path.startswith("/") or ".." in path:
        raise ValueError("platform internal path is invalid")
    service_token = _required_text(
        os.getenv("DTS_AIRFLOW_TO_PLATFORM_TOKEN"),
        "Airflow-to-platform service token",
    )
    body = (
        None
        if payload is None
        else json.dumps(payload, separators=(",", ":")).encode("utf-8")
    )
    headers = {
        "Accept": "application/json",
        "Content-Type": "application/json",
        "X-DTS-Service": "dts-airflow",
        "X-DTS-Service-Token": service_token,
    }
    if runtime_spec_token is not None:
        headers["X-DTS-Runtime-Spec-Token"] = _required_text(
            runtime_spec_token, "runtime spec token"
        )
    request = urllib_request.Request(
        base_url + path,
        data=body,
        headers=headers,
        method=method,
    )
    try:
        with urllib_request.urlopen(request, timeout=30) as response:
            content = response.read()
    except urllib_error.HTTPError as failure:
        raise RuntimeError(
            f"platform internal API rejected {method}: HTTP {failure.code}"
        ) from failure
    except urllib_error.URLError as failure:
        raise RuntimeError(
            f"platform internal API unavailable for {method}"
        ) from failure
    if not content:
        return {}
    decoded = json.loads(content.decode("utf-8"))
    if not isinstance(decoded, dict):
        raise RuntimeError("platform internal API returned a non-object")
    return decoded


def _scheduled_operational_conf(
    binding_id: str,
    deployment_checksum: str,
    dag_run: Any,
) -> dict[str, Any]:
    run_type = str(getattr(dag_run, "run_type", "")).lower()
    if not run_type.endswith("scheduled"):
        raise ValueError("Manual OPERATIONAL_RUN requires durable DagRun conf")
    logical_date = getattr(dag_run, "logical_date", None)
    logical_date_text = (
        logical_date.isoformat()
        if hasattr(logical_date, "isoformat")
        else _required_text(logical_date, "logical date")
    )
    opened = _platform_request(
        f"/api/internal/modeling/execution-bindings/{binding_id}"
        "/scheduled-runs/open",
        payload={
            "dagRunId": _required_text(
                getattr(dag_run, "dag_run_id", None), "DagRun id"
            ),
            "logicalDate": logical_date_text,
            "deploymentChecksum": _checksum(
                deployment_checksum, "deployment checksum"
            ),
        },
    )
    return _validate_operational_run_conf(opened)


def _prepare_runtime_task(
    *,
    purpose: str,
    binding_id: str | None,
    deployment_checksum: str | None = None,
    **context: Any,
) -> dict[str, str]:
    dag_run = context.get("dag_run")
    if purpose == "RELEASE_BUILD":
        if binding_id is not None:
            raise ValueError("RELEASE_BUILD must not declare a binding")
        conf = _validate_release_build_conf(getattr(dag_run, "conf", None))
    elif purpose == "OPERATIONAL_RUN":
        binding = _uuid_text(binding_id, "binding id")
        supplied = getattr(dag_run, "conf", None)
        conf = (
            _validate_operational_run_conf(supplied)
            if supplied
            else _scheduled_operational_conf(
                binding,
                _checksum(
                    deployment_checksum,
                    "deployment checksum",
                ),
                dag_run,
            )
        )
        if conf["bindingId"] != binding:
            raise RuntimeError("DagRun binding does not match the plan DAG")
    else:
        raise ValueError("purpose is invalid")
    runtime = _validate_runtime_spec(
        _platform_request(
            (
                "/api/internal/modeling/materialization/runtime-specs/consume"
                if purpose == "RELEASE_BUILD"
                else
                "/api/internal/modeling/execution-bindings/runtime-specs/consume"
            ),
            runtime_spec_token=conf["runtimeSpecToken"],
        )
    )
    if (
        runtime["pipelineRunGroupId"] != conf["pipelineRunGroupId"]
        or runtime["runPurpose"] != conf["runPurpose"]
        or runtime["projectBundleChecksum"] != conf["bundleChecksum"]
    ):
        raise RuntimeError("runtime spec does not match the durable DagRun")
    return runtime


def _runtime_from_xcom(task_instance: Any) -> dict[str, str]:
    runtime = task_instance.xcom_pull(task_ids=_PREPARE_TASK_ID)
    return _validate_runtime_spec(runtime)


def _profile_lease_renew_interval(expires_at: Any, now: datetime) -> float:
    try:
        expires_at_text = _required_text(
            expires_at,
            "profile lease expiresAt",
        )
        expires = datetime.fromisoformat(
            expires_at_text.replace("Z", "+00:00")
        )
    except (TypeError, ValueError) as failure:
        raise RuntimeError("profile lease expiresAt is invalid") from failure
    if expires.tzinfo is None or now.tzinfo is None:
        raise RuntimeError("profile lease expiresAt is invalid")
    remaining_seconds = (
        expires.astimezone(timezone.utc) - now.astimezone(timezone.utc)
    ).total_seconds()
    if remaining_seconds <= 2 * _PROFILE_LEASE_RENEW_MIN_INTERVAL_SECONDS:
        raise RuntimeError("profile lease expires too soon to execute dbt safely")
    return min(
        _PROFILE_LEASE_RENEW_MAX_INTERVAL_SECONDS,
        max(
            _PROFILE_LEASE_RENEW_MIN_INTERVAL_SECONDS,
            remaining_seconds / 3,
        ),
    )


def _validated_profile_lease_renew_interval(
    response: dict[str, Any],
    lease_id: str,
    action: str,
) -> float:
    if str(response.get("profileLeaseId", "")) != lease_id:
        raise RuntimeError(
            f"profile lease {action} response does not match runtime"
        )
    return _profile_lease_renew_interval(
        response.get("expiresAt"),
        datetime.now(timezone.utc),
    )


def _terminate_and_wait(process: Any) -> None:
    if process.poll() is not None:
        return
    try:
        process.terminate()
    except ProcessLookupError:
        pass
    try:
        process.wait(timeout=_PROCESS_TERMINATE_TIMEOUT_SECONDS)
        return
    except subprocess.TimeoutExpired:
        LOG.warning(
            "event=dbt_materialization_terminate_timeout "
            "timeout_seconds=%s",
            _PROCESS_TERMINATE_TIMEOUT_SECONDS,
        )
    except OSError as failure:
        LOG.warning(
            "event=dbt_materialization_terminate_wait_failed "
            "error_type=%s",
            type(failure).__name__,
        )
    if process.poll() is not None:
        return
    try:
        process.kill()
    except ProcessLookupError:
        pass
    process.wait(timeout=_PROCESS_KILL_TIMEOUT_SECONDS)


def _dbt_build_task(**context: Any) -> None:
    runtime = _runtime_from_xcom(context["ti"])
    lease_id = runtime["profileLeaseId"]
    container_name = _dbt_container_name(lease_id)
    consumed = _platform_request(
        "/api/internal/modeling/materialization/profile-leases/"
        f"{lease_id}/consume",
    )
    renew_interval = _validated_profile_lease_renew_interval(
        consumed,
        lease_id,
        "consume",
    )
    image = (
        runtime["imageRef"]
        if runtime["runPurpose"] == "RELEASE_BUILD"
        else os.getenv("DBT_IMAGE", "dts-dbt:1.10.0")
    )
    command = _build_docker_command(
        runtime,
        image=image,
        project_host_root=os.getenv("DTS_DBT_PROJECT_HOST_ROOT", ""),
        profile_host_root=os.getenv(
            "DTS_DBT_RUNTIME_PROFILE_HOST_ROOT", ""
        ),
        docker_network=os.getenv("DBT_DOCKER_NETWORK", "dts-core"),
    )
    selector_checksum = hashlib.sha256(
        runtime["selector"].encode("utf-8")
    ).hexdigest()
    LOG.info(
        "event=dbt_materialization_start purpose=%s run_group=%s "
        "image=%s selector_checksum=%s",
        runtime["runPurpose"],
        runtime["pipelineRunGroupId"],
        command[command.index("build") - 1],
        selector_checksum,
    )
    process = None
    try:
        process = subprocess.Popen(command)
        while True:
            try:
                return_code = process.wait(timeout=renew_interval)
            except subprocess.TimeoutExpired:
                renewed = _platform_request(
                    "/api/internal/modeling/materialization/profile-leases/"
                    f"{lease_id}/renew",
                )
                renew_interval = _validated_profile_lease_renew_interval(
                    renewed,
                    lease_id,
                    "renew",
                )
                continue
            if return_code != 0:
                raise DbtBuildProcessError(return_code)
            break
    except BaseException as failure:
        cleanup_failures: list[str] = []
        try:
            _stop_dbt_container(
                container_name,
                lease_id,
                runtime["pipelineRunGroupId"],
            )
        except BaseException as cleanup_failure:
            cleanup_failures.append(
                "container:"
                + type(cleanup_failure).__name__
            )
        if process is not None:
            try:
                _terminate_and_wait(process)
            except BaseException as cleanup_failure:
                cleanup_failures.append(
                    "client:"
                    + type(cleanup_failure).__name__
                )
        if cleanup_failures:
            LOG.error(
                "event=dbt_materialization_cleanup_unconfirmed failures=%s",
                ",".join(cleanup_failures),
            )
            add_note = getattr(failure, "add_note", None)
            if callable(add_note):
                add_note(
                    "dbt container cleanup could not be fully confirmed"
                )
        raise


def _sync_manifest_and_probe_task(**context: Any) -> None:
    runtime = _runtime_from_xcom(context["ti"])
    group_id = runtime["pipelineRunGroupId"]
    owner = (
        "materialization"
        if runtime["runPurpose"] == "RELEASE_BUILD"
        else "execution-bindings"
    )
    _platform_request(
        f"/api/internal/modeling/{owner}/run-groups/"
        f"{group_id}/sync-probe",
        payload={
            "runPurpose": runtime["runPurpose"],
            "projectBundleChecksum": runtime["projectBundleChecksum"],
        },
    )


def _finalize_task(**context: Any) -> None:
    task_instance = context["ti"]
    runtime = None
    succeeded = False
    try:
        runtime = _runtime_from_xcom(task_instance)
        dag_run = context["dag_run"]
        states = {
            instance.task_id: str(instance.state).lower()
            for instance in dag_run.get_task_instances()
        }
        succeeded = all(
            states.get(task_id) == "success"
            for task_id in (
                _PREPARE_TASK_ID,
                _BUILD_TASK_ID,
                _SYNC_TASK_ID,
            )
        )
        _platform_request(
            (
                "/api/internal/modeling/materialization/run-groups/"
                if runtime["runPurpose"] == "RELEASE_BUILD"
                else
                "/api/internal/modeling/execution-bindings/run-groups/"
            )
            + f"{runtime['pipelineRunGroupId']}/finalize",
            payload={"outcome": "SUCCEEDED" if succeeded else "FAILED"},
        )
    finally:
        if runtime is not None:
            _stop_dbt_container(
                _dbt_container_name(runtime["profileLeaseId"]),
                runtime["profileLeaseId"],
                runtime["pipelineRunGroupId"],
            )
            _platform_request(
                "/api/internal/modeling/materialization/profile-leases/"
                f"{runtime['profileLeaseId']}",
                method="DELETE",
            )
    if not succeeded:
        raise RuntimeError("materialization upstream task failed")


def build_dbt_dag(
    *,
    dag_id: str,
    purpose: str,
    schedule: str | None,
    timezone: str,
    template_version: str,
    deployment_checksum: str,
    binding_id: str | None = None,
    tags: list[str] | None = None,
):
    """Build the one supported coarse-grained materialization DAG."""
    from airflow import DAG
    from airflow.operators.python import PythonOperator
    from airflow.utils.trigger_rule import TriggerRule
    import pendulum

    if not _DAG_ID.fullmatch(_required_text(dag_id, "dag id")):
        raise ValueError("dag id is invalid")
    if purpose not in {"RELEASE_BUILD", "OPERATIONAL_RUN"}:
        raise ValueError("purpose is invalid")
    if purpose == "RELEASE_BUILD" and schedule is not None:
        raise ValueError("RELEASE_BUILD executor must not have a schedule")
    if purpose == "RELEASE_BUILD" and binding_id is not None:
        raise ValueError("RELEASE_BUILD executor must not have a binding")
    if purpose == "OPERATIONAL_RUN" and binding_id is None:
        raise ValueError("OPERATIONAL_RUN executor requires a binding")
    _checksum(deployment_checksum, "deployment checksum")
    _required_text(template_version, "template version")
    if binding_id is not None:
        _uuid_text(binding_id, "binding id")

    dag = DAG(
        dag_id=dag_id,
        schedule=schedule,
        start_date=pendulum.datetime(2024, 1, 1, tz=timezone),
        catchup=False,
        max_active_runs=1,
        tags=list(tags or []) + [
            f"purpose:{purpose}",
            f"template:{template_version}",
            f"deployment:{deployment_checksum}",
        ],
        render_template_as_native_obj=True,
    )
    with dag:
        prepare = PythonOperator(
            task_id=_PREPARE_TASK_ID,
            python_callable=_prepare_runtime_task,
            op_kwargs={
                "purpose": purpose,
                "binding_id": binding_id,
                "deployment_checksum": deployment_checksum,
            },
        )
        build = PythonOperator(
            task_id=_BUILD_TASK_ID,
            python_callable=_dbt_build_task,
        )
        sync = PythonOperator(
            task_id=_SYNC_TASK_ID,
            python_callable=_sync_manifest_and_probe_task,
        )
        finalize = PythonOperator(
            task_id="finalize_run",
            python_callable=_finalize_task,
            trigger_rule=TriggerRule.ALL_DONE,
        )
        prepare >> build >> sync >> finalize
    return dag
