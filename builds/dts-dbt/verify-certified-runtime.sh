#!/bin/sh
set -eu

METADATA_FILE=/opt/dts-runtime/runtime-metadata
LOCK_FILE=/opt/dts-runtime/requirements.lock

emit_failure() {
  printf '{"status":"FAILED","code":"%s"}\n' "$1" >&2
}

verify_install() {
  if [ ! -r "$METADATA_FILE" ] || [ ! -r "$LOCK_FILE" ]; then
    emit_failure DBT_RUNTIME_METADATA_INVALID
    return 20
  fi
  # shellcheck disable=SC1091
  . "$METADATA_FILE"
  if ! python -m pip check >/dev/null 2>&1; then
    emit_failure DBT_RUNTIME_DEPENDENCY_CHECK_FAILED
    return 21
  fi
  python - "$LOCK_FILE" \
    "${IMAGE_CERTIFICATION_STATUS:-}" \
    "${IMAGE_CERTIFICATION_PROFILE_ID:-}" \
    "${IMAGE_EVIDENCE_MANIFEST_SHA256:-}" \
    "${IMAGE_CANDIDATE_PLATFORM_MANIFEST_DIGEST:-}" \
    "${IMAGE_CANDIDATE_CONFIGURATION_DIGEST:-}" \
    "${IMAGE_RUNTIME_PLATFORM:-}" \
    "${IMAGE_RUNTIME_ARCH:-}" \
    "${IMAGE_REQUIREMENTS_LOCK_SHA256:-}" \
    "${IMAGE_CANDIDATE_PROFILE_ID:-}" \
    "${IMAGE_DBT_CORE_VERSION:-}" \
    "${IMAGE_DBT_POSTGRES_VERSION:-}" <<'PY'
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
(
    status,
    profile_id,
    evidence_sha256,
    candidate_manifest_digest,
    candidate_config_digest,
    runtime_platform,
    runtime_arch,
    lock_sha256,
    candidate_profile_id,
    dbt_core,
    dbt_postgres,
) = sys.argv[2:]

expected_profile_id = (
    "H83-CERT-RT01-LINUX-AMD64-EVIDENCE-"
    "bcd2fc84b05b9508990538c6642be7ac7b35073ec034e6b1980b22f556345a68"
)
expected_candidate_id = (
    "H83-RT01-LINUX-AMD64-DBT11022-PG1100-LOCK-"
    "01d7c02b6bf4fefdfc188cbf9ef8aed4fb243c227c060103f195c4ca45af5f02"
)
if status != "CERTIFIED" or profile_id != expected_profile_id:
    fail("DBT_RUNTIME_NOT_CERTIFIED")
if evidence_sha256 != "bcd2fc84b05b9508990538c6642be7ac7b35073ec034e6b1980b22f556345a68":
    fail("DBT_RUNTIME_EVIDENCE_MISMATCH")
if candidate_manifest_digest != "sha256:fe1d15f1b4215e693dadfc1d99be2ae07c7e50e8df144504005feb41cc7686b7":
    fail("DBT_RUNTIME_CANDIDATE_DIGEST_MISMATCH")
if candidate_config_digest != "sha256:da312997c55425d9a63622221245cee61c0e219ce83234587d2122959e1b640f":
    fail("DBT_RUNTIME_CANDIDATE_DIGEST_MISMATCH")
actual_lock_sha256 = hashlib.sha256(lock_path.read_bytes()).hexdigest()
if lock_sha256 != actual_lock_sha256 or lock_sha256 != "01d7c02b6bf4fefdfc188cbf9ef8aed4fb243c227c060103f195c4ca45af5f02":
    fail("DBT_RUNTIME_LOCK_SHA256_MISMATCH")
if candidate_profile_id != expected_candidate_id:
    fail("DBT_RUNTIME_CANDIDATE_IDENTITY_MISMATCH")
if runtime_platform != "linux/amd64" or runtime_arch != "amd64":
    fail("DBT_RUNTIME_PLATFORM_MISMATCH")
if platform.system().lower() != "linux" or platform.machine().lower() not in {"x86_64", "amd64"}:
    fail("DBT_RUNTIME_PLATFORM_MISMATCH")
if dbt_core != "1.10.22" or dbt_postgres != "1.10.0":
    fail("DBT_RUNTIME_VERSION_MISMATCH")
if Version(dbt_core).is_prerelease or Version(dbt_postgres).is_prerelease:
    fail("DBT_RUNTIME_PRE_RELEASE_REJECTED")

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
if any(installed.get(name) != version for name, version in locked.items()):
    fail("DBT_RUNTIME_DEPENDENCY_DRIFT")
if set(installed) - set(locked) - {"pip", "setuptools", "wheel"}:
    fail("DBT_RUNTIME_UNREVIEWED_DISTRIBUTION")
print(json.dumps({"candidateProfileId": candidate_profile_id, "certificationProfileId": profile_id, "status": "PASS"}, separators=(",", ":"), sort_keys=True))
PY
}

case "${1:---verify-install}" in
  --verify-identity|--verify-install)
    verify_install
    ;;
  --gate-materialization)
    verify_install >/dev/null || exit $?
    printf '{"status":"PASS","candidateStatus":"CERTIFIED"}\n'
    ;;
  *)
    printf 'usage: %s [--verify-identity|--verify-install|--gate-materialization]\n' "$0" >&2
    exit 64
    ;;
esac
