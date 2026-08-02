#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "${SCRIPT_DIR}/../../.." && pwd)
BUILD_DIR="${REPO_ROOT}/builds/dts-dbt"
FIXTURE_DIR="${REPO_ROOT}/services/dts-dbt/rt01"
EVIDENCE_DIR="${REPO_ROOT}/worklog/v2.2.3/sprint-83-202608-dbt-visual-roundtrip-modeling/it/rt-01"
RUNTIME_PLATFORM=linux/amd64
RUNTIME_PLATFORM_PATH="${EVIDENCE_DIR}/linux/amd64"
LOCK_SHA256=01d7c02b6bf4fefdfc188cbf9ef8aed4fb243c227c060103f195c4ca45af5f02
CANDIDATE_PROFILE_ID="H83-RT01-LINUX-AMD64-DBT11022-PG1100-LOCK-${LOCK_SHA256}"
CANDIDATE_EVIDENCE_DIR="${RUNTIME_PLATFORM_PATH}/${CANDIDATE_PROFILE_ID}"
BASE_PLATFORM_MANIFEST_DIGEST=sha256:00af38ae2ed311628970782e8a2d7f014d8909dbc63cb97bc0a158187f4db045

fail() {
  printf 'runtime-contract: FAIL: %s\n' "$1" >&2
  exit 1
}

require_file() {
  [ -f "$1" ] || fail "missing file: ${1#"${REPO_ROOT}/"}"
}

require_file "${BUILD_DIR}/requirements.lock"
require_file "${BUILD_DIR}/lock-tool-requirements.lock"
require_file "${BUILD_DIR}/LOCKING.md"
require_file "${BUILD_DIR}/verify-runtime.sh"
require_file "${BUILD_DIR}/entrypoint.sh"
require_file "${BUILD_DIR}/tests/entrypoint-command-matrix-test.sh"
require_file "${FIXTURE_DIR}/dbt_project.yml"
require_file "${FIXTURE_DIR}/profiles.yml.example"
require_file "${FIXTURE_DIR}/seeds/rt01_orders.csv"
require_file "${FIXTURE_DIR}/models/staging/stg_rt01_orders.sql"
require_file "${FIXTURE_DIR}/models/marts/fct_rt01_orders.sql"
require_file "${FIXTURE_DIR}/models/marts/inc_rt01_order_totals.sql"
require_file "${FIXTURE_DIR}/models/schema.yml"
require_file "${EVIDENCE_DIR}/README.md"
require_file "${CANDIDATE_EVIDENCE_DIR}/candidate-profile.template.json"
require_file "${CANDIDATE_EVIDENCE_DIR}/evidence-manifest.template.json"
require_file "${CANDIDATE_EVIDENCE_DIR}/rollback.template.md"

grep -Eq '^FROM python:3\.11-slim@sha256:[0-9a-f]{64}$' "${BUILD_DIR}/Dockerfile" \
  || fail 'base image must be pinned by immutable sha256 digest'
grep -Fq 'COPY requirements.lock /opt/dts-runtime/requirements.lock' "${BUILD_DIR}/Dockerfile" \
  || fail 'Dockerfile must copy the reviewed lock file'
grep -Fq -- '--require-hashes' "${BUILD_DIR}/Dockerfile" \
  || fail 'Dockerfile must install with --require-hashes'
grep -Fq 'COPY verify-runtime.sh /usr/local/bin/verify-dbt-runtime' "${BUILD_DIR}/Dockerfile" \
  || fail 'Dockerfile must install the runtime verifier'
grep -Fq 'COPY entrypoint.sh /usr/local/bin/dts-dbt-entrypoint' "${BUILD_DIR}/Dockerfile" \
  || fail 'Dockerfile must install the fail-closed entrypoint'
grep -Fq 'ENTRYPOINT ["/usr/local/bin/dts-dbt-entrypoint"]' "${BUILD_DIR}/Dockerfile" \
  || fail 'candidate image must use the fail-closed entrypoint'
grep -Fq "ARG DBT_RUNTIME_PLATFORM=${RUNTIME_PLATFORM}" "${BUILD_DIR}/Dockerfile" \
  || fail 'candidate build platform must be explicit'
grep -Fq "ARG DBT_RUNTIME_LOCK_SHA256=${LOCK_SHA256}" "${BUILD_DIR}/Dockerfile" \
  || fail 'candidate build must bind the reviewed lock sha256'
grep -Fq "ARG DBT_RUNTIME_CANDIDATE_PROFILE_ID=${CANDIDATE_PROFILE_ID}" "${BUILD_DIR}/Dockerfile" \
  || fail 'candidate identity must include platform and full lock sha256'
grep -Fq "ARG DBT_RUNTIME_BASE_PLATFORM_MANIFEST_DIGEST=${BASE_PLATFORM_MANIFEST_DIGEST}" "${BUILD_DIR}/Dockerfile" \
  || fail 'candidate must record its platform-specific base manifest digest'
grep -Fq "[ \"\${DBT_RUNTIME_BASE_PLATFORM_MANIFEST_DIGEST}\" = \"${BASE_PLATFORM_MANIFEST_DIGEST}\" ]" "${BUILD_DIR}/Dockerfile" \
  || fail 'candidate build must reject a substituted platform manifest digest'
grep -Fq 'pip-tools==7.5.2' "${BUILD_DIR}/LOCKING.md" \
  || fail 'lock generation tool version must be recorded'
grep -Fq 'python:3.11-slim@sha256:db3ff2e1800a8581e2c48a27c3995339d47bdf046da21c7627accd3d51053a93' "${BUILD_DIR}/LOCKING.md" \
  || fail 'lock generation image digest must be recorded'
grep -Fq -- '--require-hashes --requirement /src/lock-tool-requirements.lock' "${BUILD_DIR}/LOCKING.md" \
  || fail 'lock generator bootstrap must be hash locked'

python3 - "${BUILD_DIR}/requirements.lock" "${BUILD_DIR}/lock-tool-requirements.lock" <<'PY'
from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path

def validate_lock(lock_path: Path) -> set[str]:
    lines = lock_path.read_text(encoding="utf-8").splitlines()
    requirements: list[tuple[str, str]] = []
    current: list[str] = []

    for line in lines:
        if not line or line.lstrip().startswith("#") or line.startswith(("--", " ", "\t")):
            if current:
                current.append(line)
            continue
        if current:
            requirements.append((current[0], "\n".join(current)))
        current = [line]
    if current:
        requirements.append((current[0], "\n".join(current)))

    if not requirements:
        raise SystemExit(f"{lock_path.name} contains no distributions")

    names: set[str] = set()
    for declaration, block in requirements:
        if "==" not in declaration:
            raise SystemExit(f"non-exact requirement in {lock_path.name}: {declaration}")
        name = re.split(r"==", declaration, maxsplit=1)[0].strip().lower().replace("_", "-")
        names.add(name)
        if not re.search(r"--hash=sha256:[0-9a-f]{64}", block):
            raise SystemExit(f"requirement has no sha256 artifact hash: {declaration}")
    return names


runtime_lock = Path(sys.argv[1])
runtime_names = validate_lock(runtime_lock)
tool_names = validate_lock(Path(sys.argv[2]))
missing = {"dbt-core", "dbt-postgres", "dbt-adapters", "dbt-common"} - runtime_names
if missing:
    raise SystemExit(f"required runtime distributions are absent: {sorted(missing)}")
if "pip-tools" not in tool_names:
    raise SystemExit("hash-locked generator bootstrap must include pip-tools")
if hashlib.sha256(runtime_lock.read_bytes()).hexdigest() != "01d7c02b6bf4fefdfc188cbf9ef8aed4fb243c227c060103f195c4ca45af5f02":
    raise SystemExit("requirements.lock changed without assigning a new candidate identity")
PY

python3 - "${CANDIDATE_EVIDENCE_DIR}/candidate-profile.template.json" "${CANDIDATE_EVIDENCE_DIR}/evidence-manifest.template.json" "${BUILD_DIR}/requirements.lock" "${BUILD_DIR}/lock-tool-requirements.lock" <<'PY'
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

candidate = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
evidence = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
lock_sha256 = hashlib.sha256(Path(sys.argv[3]).read_bytes()).hexdigest()
bootstrap_lock_sha256 = hashlib.sha256(Path(sys.argv[4]).read_bytes()).hexdigest()
expected_candidate_id = f"H83-RT01-LINUX-AMD64-DBT11022-PG1100-LOCK-{lock_sha256}"

if candidate.get("status") != "NOT_CERTIFIED":
    raise SystemExit("candidate status must remain NOT_CERTIFIED")
if candidate.get("candidateProfileId") != expected_candidate_id:
    raise SystemExit("candidate identity is not bound to platform and lock sha256")
if candidate.get("certificationProfileId") is not None:
    raise SystemExit("H83 candidate must not assign certificationProfileId")
if candidate.get("materializationFailureCode") != "DBT_RUNTIME_NOT_CERTIFIED":
    raise SystemExit("candidate must expose the fail-closed materialization code")
if candidate.get("platform") != "linux/amd64" or evidence.get("platform") != "linux/amd64":
    raise SystemExit("candidate and evidence must bind linux/amd64")
if candidate.get("baseImage", {}).get("platformManifestDigest") != "sha256:00af38ae2ed311628970782e8a2d7f014d8909dbc63cb97bc0a158187f4db045":
    raise SystemExit("platform-specific base manifest digest is missing")
if candidate.get("runtime", {}).get("requirementsLockSha256") != lock_sha256:
    raise SystemExit("candidate runtime lock sha256 does not match repository lock")
if candidate.get("lockGenerator", {}).get("bootstrapLockSha256") != bootstrap_lock_sha256:
    raise SystemExit("candidate lock generator hash does not match bootstrap lock")
if evidence.get("candidateProfileId") != expected_candidate_id or evidence.get("requirementsLockSha256") != lock_sha256:
    raise SystemExit("evidence identity does not match candidate lock identity")
if evidence.get("evidenceStatus") != "INCOMPLETE":
    raise SystemExit("template evidence must start INCOMPLETE")
if evidence.get("containsSecrets") is not False:
    raise SystemExit("evidence template must explicitly prohibit secrets")
if evidence.get("lockGenerator", {}).get("bootstrapLockSha256") != bootstrap_lock_sha256:
    raise SystemExit("evidence lock generator hash does not match bootstrap lock")
PY

grep -Fq 'DBT_RUNTIME_NOT_CERTIFIED' "${BUILD_DIR}/verify-runtime.sh" \
  || fail 'runtime verifier must expose the fail-closed materialization code'
grep -Fq '/opt/dts-runtime/runtime-metadata' "${BUILD_DIR}/verify-runtime.sh" \
  || fail 'runtime verifier must consume immutable image metadata'
grep -Fq 'DBT_RUNTIME_NOT_CERTIFIED' "${BUILD_DIR}/entrypoint.sh" \
  || fail 'entrypoint must fail closed before materialization'
grep -Fq 'env_var(' "${FIXTURE_DIR}/profiles.yml.example" \
  || fail 'RT-01 profile must obtain credentials from environment variables'
if grep -Eq 'packages\.yml|dependencies\.yml' "${FIXTURE_DIR}/dbt_project.yml"; then
  fail 'RT-01 fixture must not require online dbt packages'
fi

printf 'runtime-contract: PASS\n'
