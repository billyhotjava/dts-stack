#!/bin/sh
set -eu

METADATA_FILE=/opt/dts-runtime/runtime-metadata
LOCK_FILE=/opt/dts-runtime/requirements.lock

emit_failure() {
  code=$1
  printf '{"status":"FAILED","code":"%s"}\n' "$code" >&2
}

load_metadata() {
  if [ ! -r "$METADATA_FILE" ] || [ ! -r "$LOCK_FILE" ]; then
    emit_failure DBT_RUNTIME_METADATA_INVALID
    return 20
  fi

  # The file is created during the image build, marked read-only and covered by
  # the image digest. Runtime environment variables are never sourced here.
  # shellcheck disable=SC1091
  . "$METADATA_FILE"
  if [ -z "${IMAGE_CERTIFICATION_STATUS:-}" ] \
    || [ -z "${IMAGE_RUNTIME_PLATFORM:-}" ] \
    || [ -z "${IMAGE_RUNTIME_ARCH:-}" ] \
    || [ -z "${IMAGE_BASE_PLATFORM_MANIFEST_DIGEST:-}" ] \
    || [ -z "${IMAGE_REQUIREMENTS_LOCK_SHA256:-}" ] \
    || [ -z "${IMAGE_CANDIDATE_PROFILE_ID:-}" ] \
    || [ -z "${IMAGE_DBT_CORE_VERSION:-}" ] \
    || [ -z "${IMAGE_DBT_POSTGRES_VERSION:-}" ]; then
    emit_failure DBT_RUNTIME_METADATA_INVALID
    return 20
  fi
}

verify_identity() {
  load_metadata || return $?

  python - \
    "$LOCK_FILE" \
    "$IMAGE_RUNTIME_PLATFORM" \
    "$IMAGE_RUNTIME_ARCH" \
    "$IMAGE_REQUIREMENTS_LOCK_SHA256" \
    "$IMAGE_CANDIDATE_PROFILE_ID" \
    "$IMAGE_DBT_CORE_VERSION" \
    "$IMAGE_DBT_POSTGRES_VERSION" \
    "$IMAGE_CERTIFICATION_STATUS" <<'PY'
from __future__ import annotations

import hashlib
import json
import platform
import sys
from pathlib import Path


def fail(code: str) -> None:
    print(json.dumps({"status": "FAILED", "code": code}, separators=(",", ":")), file=sys.stderr)
    raise SystemExit(1)


lock_path = Path(sys.argv[1])
runtime_platform = sys.argv[2]
runtime_arch = sys.argv[3]
expected_lock_sha256 = sys.argv[4]
candidate_profile_id = sys.argv[5]
expected_core = sys.argv[6]
expected_postgres = sys.argv[7]
candidate_status = sys.argv[8]

actual_lock_sha256 = hashlib.sha256(lock_path.read_bytes()).hexdigest()
if actual_lock_sha256 != expected_lock_sha256:
    fail("DBT_RUNTIME_LOCK_SHA256_MISMATCH")

expected_candidate_id = (
    "H83-RT01-LINUX-AMD64-DBT11022-PG1100-LOCK-" + actual_lock_sha256
)
if candidate_profile_id != expected_candidate_id:
    fail("DBT_RUNTIME_CANDIDATE_IDENTITY_MISMATCH")

if runtime_platform != "linux/amd64" or runtime_arch != "amd64":
    fail("DBT_RUNTIME_PLATFORM_MISMATCH")
if platform.system().lower() != "linux" or platform.machine().lower() not in {"x86_64", "amd64"}:
    fail("DBT_RUNTIME_PLATFORM_MISMATCH")
if candidate_status != "NOT_CERTIFIED":
    fail("DBT_RUNTIME_STATUS_TAMPERED")

print(
    json.dumps(
        {
            "candidateProfileId": candidate_profile_id,
            "candidateStatus": candidate_status,
            "dbtCoreVersion": expected_core,
            "dbtPostgresVersion": expected_postgres,
            "platform": runtime_platform,
            "requirementsLockSha256": actual_lock_sha256,
            "status": "PASS",
        },
        separators=(",", ":"),
        sort_keys=True,
    )
)
PY
}

verify_install() {
  load_metadata || return $?

  if [ "${DBT_RUNTIME_CERTIFICATION_STATUS:-}" != "$IMAGE_CERTIFICATION_STATUS" ]; then
    emit_failure DBT_RUNTIME_STATUS_TAMPERED
    return 21
  fi
  if [ "${DBT_RUNTIME_PLATFORM:-}" != "$IMAGE_RUNTIME_PLATFORM" ] \
    || [ "${DBT_RUNTIME_LOCK_SHA256:-}" != "$IMAGE_REQUIREMENTS_LOCK_SHA256" ] \
    || [ "${DBT_RUNTIME_CANDIDATE_PROFILE_ID:-}" != "$IMAGE_CANDIDATE_PROFILE_ID" ]; then
    emit_failure DBT_RUNTIME_ENV_METADATA_MISMATCH
    return 21
  fi
  if ! python -m pip check >/dev/null 2>&1; then
    emit_failure DBT_RUNTIME_DEPENDENCY_CHECK_FAILED
    return 21
  fi

  python - \
    "$LOCK_FILE" \
    "$IMAGE_REQUIREMENTS_LOCK_SHA256" \
    "$IMAGE_CANDIDATE_PROFILE_ID" \
    "$IMAGE_DBT_CORE_VERSION" \
    "$IMAGE_DBT_POSTGRES_VERSION" \
    "$IMAGE_RUNTIME_PLATFORM" \
    "$IMAGE_CERTIFICATION_STATUS" <<'PY'
from __future__ import annotations

import hashlib
import importlib.metadata as metadata
import json
import platform
import sys
from pathlib import Path

from packaging.utils import canonicalize_name
from packaging.version import Version


def fail(code: str) -> None:
    print(json.dumps({"status": "FAILED", "code": code}, separators=(",", ":")), file=sys.stderr)
    raise SystemExit(1)


lock_path = Path(sys.argv[1])
expected_lock_sha256 = sys.argv[2]
candidate_profile_id = sys.argv[3]
expected_core = sys.argv[4]
expected_postgres = sys.argv[5]
runtime_platform = sys.argv[6]
candidate_status = sys.argv[7]

actual_lock_sha256 = hashlib.sha256(lock_path.read_bytes()).hexdigest()
if actual_lock_sha256 != expected_lock_sha256:
    fail("DBT_RUNTIME_LOCK_SHA256_MISMATCH")
if candidate_profile_id != "H83-RT01-LINUX-AMD64-DBT11022-PG1100-LOCK-" + actual_lock_sha256:
    fail("DBT_RUNTIME_CANDIDATE_IDENTITY_MISMATCH")
if runtime_platform != "linux/amd64" or platform.machine().lower() not in {"x86_64", "amd64"}:
    fail("DBT_RUNTIME_PLATFORM_MISMATCH")
if candidate_status != "NOT_CERTIFIED":
    fail("DBT_RUNTIME_STATUS_TAMPERED")

locked: dict[str, str] = {}
for raw_line in lock_path.read_text(encoding="utf-8").splitlines():
    if not raw_line or raw_line[0].isspace() or raw_line.startswith(("#", "--")):
        continue
    if "==" not in raw_line:
        fail("DBT_RUNTIME_LOCK_NOT_EXACT")
    raw_name, raw_version = raw_line.split("==", maxsplit=1)
    locked[canonicalize_name(raw_name.strip())] = raw_version.split()[0]

installed = {
    canonicalize_name(distribution.metadata["Name"]): distribution.version
    for distribution in metadata.distributions()
    if distribution.metadata.get("Name")
}
for package_name, locked_version in locked.items():
    if installed.get(package_name) != locked_version:
        fail("DBT_RUNTIME_DEPENDENCY_DRIFT")

allowed_bootstrap = {"pip", "setuptools", "wheel"}
if set(installed) - set(locked) - allowed_bootstrap:
    fail("DBT_RUNTIME_UNREVIEWED_DISTRIBUTION")
if installed.get("dbt-core") != expected_core or installed.get("dbt-postgres") != expected_postgres:
    fail("DBT_RUNTIME_VERSION_MISMATCH")
if Version(expected_core).is_prerelease or Version(expected_postgres).is_prerelease:
    fail("DBT_RUNTIME_PRE_RELEASE_REJECTED")

print(
    json.dumps(
        {
            "candidateProfileId": candidate_profile_id,
            "candidateStatus": candidate_status,
            "dbtAdaptersVersion": installed["dbt-adapters"],
            "dbtCommonVersion": installed["dbt-common"],
            "dbtCoreVersion": installed["dbt-core"],
            "dbtPostgresVersion": installed["dbt-postgres"],
            "lockedDistributionCount": len(locked),
            "platform": runtime_platform,
            "requirementsLockSha256": actual_lock_sha256,
            "status": "PASS",
        },
        separators=(",", ":"),
        sort_keys=True,
    )
)
PY
}

case "${1:---verify-install}" in
  --verify-identity)
    verify_identity
    ;;
  --verify-install)
    verify_install
    ;;
  --gate-materialization)
    load_metadata || exit $?
    if [ "$IMAGE_CERTIFICATION_STATUS" != "CERTIFIED" ]; then
      emit_failure DBT_RUNTIME_NOT_CERTIFIED
      exit 42
    fi
    printf '{"status":"PASS","candidateStatus":"CERTIFIED"}\n'
    ;;
  *)
    printf 'usage: %s [--verify-identity|--verify-install|--gate-materialization]\n' "$0" >&2
    exit 64
    ;;
esac
