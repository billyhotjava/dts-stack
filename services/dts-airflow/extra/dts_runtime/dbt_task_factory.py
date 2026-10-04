"""Single canonical Airflow/dbt task factory for DTS model materialization.

The factory deliberately accepts only deployment metadata. Runtime project, selector and target
are resolved by dts-platform from a one-time token. Warehouse credentials remain in a short-lived
host tmpfs profile lease and never enter DagRun conf, XCom or task environment variables. Manual
models and imported dbt packages both execute through the already-running managed dts-dbt service.
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
# Kept as accepted wire fields while older platform instances roll forward.
# They are deliberately ignored: the running dts-dbt service is the sole
# execution base and verifies its installed dependency lock at execution time.
_LEGACY_RUNTIME_CERTIFICATION_KEYS = frozenset(
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
_RUNTIME_SPEC_KEYS = _BASE_RUNTIME_SPEC_KEYS | _LEGACY_RUNTIME_CERTIFICATION_KEYS
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
_DBT_COMPOSE_SERVICE_LABEL = "dts-dbt"
_DBT_RUNTIME_CONTAINER_DEFAULT = "dts-dbt"
_DBT_PROJECT_CONTAINER_ROOT_DEFAULT = "/opt/dbt"
_DBT_PROFILE_CONTAINER_ROOT_DEFAULT = "/run/dts-dbt-runtime"
_DBT_RUNTIME_WRAPPER = "run-model-build.sh"


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
    return normalized


def _dbt_runtime_container_name() -> str:
    container_name = _safe_name(
        os.getenv(
            "DTS_DBT_RUNTIME_CONTAINER_NAME",
            _DBT_RUNTIME_CONTAINER_DEFAULT,
        ),
        "dbt runtime container name",
    )
    if container_name != _DBT_RUNTIME_CONTAINER_DEFAULT:
        raise ValueError("dbt runtime container is not the managed service")
    return container_name


def _build_docker_command(
    runtime_spec: dict[str, Any],
    container_name: str,
    project_container_root: str,
    profile_container_root: str,
) -> list[str]:
    runtime = _validate_runtime_spec(runtime_spec)
    safe_container = _safe_name(container_name, "dbt runtime container name")
    if safe_container != _DBT_RUNTIME_CONTAINER_DEFAULT:
        raise ValueError("dbt runtime container is not the managed service")
    project_root = _fixed_root(
        project_container_root,
        "project container root",
    )
    profile_root = _fixed_root(
        profile_container_root,
        "profile container root",
    )
    if project_root != Path(_DBT_PROJECT_CONTAINER_ROOT_DEFAULT):
        raise ValueError("project container root is not supported")
    if profile_root != Path(_DBT_PROFILE_CONTAINER_ROOT_DEFAULT):
        raise ValueError("profile container root is not supported")
    project_directory = (
        project_root
        / ".dts-scoped-runs"
        / f"candidate-{runtime['projectBundleChecksum']}"
    )
    wrapper = project_root / _DBT_RUNTIME_WRAPPER
    command = [
        "docker", "--host", _DOCKER_ENGINE_HOST, "exec",
        "--workdir", str(project_directory),
        safe_container,
        "/bin/sh", str(wrapper),
        "build",
        runtime["profileLeaseId"],
        runtime["projectBundleChecksum"],
        runtime["targetName"],
        runtime["selector"],
        str(profile_root),
    ]
    return command


def _build_runtime_stop_command(
    container_name: str,
    lease_id: str,
    project_container_root: str,
) -> list[str]:
    safe_container = _safe_name(container_name, "dbt runtime container name")
    if safe_container != _DBT_RUNTIME_CONTAINER_DEFAULT:
        raise ValueError("dbt runtime container is not the managed service")
    project_root = _fixed_root(
        project_container_root,
        "project container root",
    )
    if project_root != Path(_DBT_PROJECT_CONTAINER_ROOT_DEFAULT):
        raise ValueError("project container root is not supported")
    return [
        "docker", "--host", _DOCKER_ENGINE_HOST, "exec",
        safe_container,
        "/bin/sh", str(project_root / _DBT_RUNTIME_WRAPPER),
        "stop", _uuid_text(lease_id, "profileLeaseId"),
    ]


class _DockerSocketConnection(http.client.HTTPConnection):
    def __init__(self) -> None:
        super().__init__("localhost", timeout=_DOCKER_ENGINE_TIMEOUT_SECONDS)

    def connect(self) -> None:
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(self.timeout)
        self.sock.connect(_DOCKER_SOCKET_PATH)


def _docker_engine_request(method: str, path: str) -> tuple[int, bytes]:
    if method != "GET" or not path.startswith("/containers/"):
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


def _inspect_dbt_runtime_container(
    container_reference: str,
) -> tuple[str, bool] | None:
    safe_container = _safe_name(
        container_reference,
        "dbt runtime container name",
    )
    encoded_reference = quote(safe_container, safe="")
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
        mounts = decoded["Mounts"]
        if (
            not isinstance(container_id, str)
            or not _DOCKER_CONTAINER_ID.fullmatch(container_id)
            or not isinstance(labels, dict)
            or not isinstance(running, bool)
            or not isinstance(mounts, list)
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
    if labels.get("com.docker.compose.service") != _DBT_COMPOSE_SERVICE_LABEL:
        raise RuntimeError("dbt runtime container is not the managed service")
    mount_modes = {
        mount.get("Destination"): mount.get("RW")
        for mount in mounts
        if isinstance(mount, dict)
    }
    if mount_modes.get(_DBT_PROJECT_CONTAINER_ROOT_DEFAULT) is not True:
        raise RuntimeError("dbt runtime project mount is unavailable")
    if mount_modes.get(_DBT_PROFILE_CONTAINER_ROOT_DEFAULT) is not False:
        raise RuntimeError("dbt runtime profile mount is not read-only")
    return container_id, running


def _require_dbt_runtime_container(container_name: str) -> str:
    inspected = _inspect_dbt_runtime_container(container_name)
    if inspected is None:
        raise RuntimeError("dbt runtime container is unavailable")
    container_id, running = inspected
    if running is not True:
        raise RuntimeError("dbt runtime container is not running")
    return container_id


def _stop_dbt_execution(
    container_name: str,
    lease_id: str,
    project_container_root: str,
) -> None:
    inspected = _inspect_dbt_runtime_container(container_name)
    if inspected is None:
        return
    _, running = inspected
    if running is not True:
        return
    subprocess.run(
        _build_runtime_stop_command(
            container_name,
            lease_id,
            project_container_root,
        ),
        check=True,
        timeout=(
            _PROCESS_TERMINATE_TIMEOUT_SECONDS
            + _PROCESS_KILL_TIMEOUT_SECONDS
            + 5
        ),
    )


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
        # Keep diagnostic identities, never echo response messages, URLs or credentials.
        details = []
        try:
            error_body = json.loads(failure.read(8192).decode("utf-8"))
            if isinstance(error_body, dict):
                code = error_body.get("code")
                if isinstance(code, str) and re.fullmatch(r"[A-Z][A-Z0-9_]{0,127}", code):
                    details.append(f"code={code}")
                correlation_id = error_body.get("correlationId")
                if isinstance(correlation_id, str):
                    details.append(f"correlationId={UUID(correlation_id)}")
        except (ValueError, OSError, AttributeError):
            pass
        diagnostic = f" ({', '.join(details)})" if details else ""
        raise RuntimeError(
            f"platform internal API rejected {method}: HTTP {failure.code}{diagnostic}"
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
    container_name = _dbt_runtime_container_name()
    project_container_root = os.getenv(
        "DTS_DBT_PROJECT_CONTAINER_ROOT",
        _DBT_PROJECT_CONTAINER_ROOT_DEFAULT,
    )
    profile_container_root = os.getenv(
        "DTS_DBT_RUNTIME_PROFILE_CONTAINER_ROOT",
        _DBT_PROFILE_CONTAINER_ROOT_DEFAULT,
    )
    container_id = _require_dbt_runtime_container(container_name)
    consumed = _platform_request(
        "/api/internal/modeling/materialization/profile-leases/"
        f"{lease_id}/consume",
    )
    renew_interval = _validated_profile_lease_renew_interval(
        consumed,
        lease_id,
        "consume",
    )
    command = _build_docker_command(
        runtime,
        container_name=container_name,
        project_container_root=project_container_root,
        profile_container_root=profile_container_root,
    )
    selector_checksum = hashlib.sha256(
        runtime["selector"].encode("utf-8")
    ).hexdigest()
    LOG.info(
        "event=dbt_materialization_start purpose=%s run_group=%s "
        "container=%s container_id=%s selector_checksum=%s",
        runtime["runPurpose"],
        runtime["pipelineRunGroupId"],
        container_name,
        container_id[:12],
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
            _stop_dbt_execution(
                container_name,
                lease_id,
                project_container_root,
            )
        except BaseException as cleanup_failure:
            cleanup_failures.append(
                "managed-execution:"
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
                    "managed dbt execution cleanup could not be fully confirmed"
                )
        raise


def _sync_manifest_and_probe_task(**context: Any) -> None:
    # A failed dbt process can still produce authoritative per-model results.
    # If preparation failed, there is no runtime identity or artifact to collect.
    if context["ti"].xcom_pull(task_ids=_PREPARE_TASK_ID) is None:
        from airflow.exceptions import AirflowSkipException
        raise AirflowSkipException("Runtime preparation did not produce artifacts")
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
    dag_run = context["dag_run"]
    runtime = None
    succeeded = False
    try:
        raw_runtime = task_instance.xcom_pull(task_ids=_PREPARE_TASK_ID)
        if raw_runtime is not None:
            runtime = _validate_runtime_spec(raw_runtime)
            identity = runtime
        else:
            supplied = getattr(dag_run, "conf", None)
            purpose = (
                supplied.get("runPurpose")
                if isinstance(supplied, dict)
                else None
            )
            if purpose == "RELEASE_BUILD":
                identity = _validate_release_build_conf(supplied)
            elif purpose == "OPERATIONAL_RUN":
                identity = _validate_operational_run_conf(supplied)
            else:
                raise ValueError(
                    "failed materialization run identity is unavailable"
                )
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
                if identity["runPurpose"] == "RELEASE_BUILD"
                else
                "/api/internal/modeling/execution-bindings/run-groups/"
            )
            + f"{identity['pipelineRunGroupId']}/finalize",
            payload={"outcome": "SUCCEEDED" if succeeded else "FAILED"},
        )
    finally:
        if runtime is not None:
            _stop_dbt_execution(
                _dbt_runtime_container_name(),
                runtime["profileLeaseId"],
                os.getenv(
                    "DTS_DBT_PROJECT_CONTAINER_ROOT",
                    _DBT_PROJECT_CONTAINER_ROOT_DEFAULT,
                ),
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
        is_paused_upon_creation=False,
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
            trigger_rule=TriggerRule.ALL_DONE,
        )
        finalize = PythonOperator(
            task_id="finalize_run",
            python_callable=_finalize_task,
            trigger_rule=TriggerRule.ALL_DONE,
        )
        prepare >> build >> sync >> finalize
    return dag
