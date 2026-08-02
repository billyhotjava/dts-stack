#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)
dockerfile="$repo_root/builds/dts-dbt/Dockerfile.certified"
verifier="$repo_root/builds/dts-dbt/verify-certified-runtime.sh"
platform_certification="$repo_root/source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/DbtRuntimeCertificationService.java"
airflow_factory="$repo_root/services/dts-airflow/extra/dts_runtime/dbt_task_factory.py"
certified_profile="$repo_root/worklog/v2.2.3/sprint-83-202608-dbt-visual-roundtrip-modeling/it/rt-01/certified-profile.json"
certified_env="$repo_root/worklog/v2.2.3/sprint-83-202608-dbt-visual-roundtrip-modeling/it/rt-01/certified-runtime.env.example"
certified_digest=sha256:2f6dddb7237fdb7141f452b6d09da0379ef7569f2f82560473f304b265cbbd85
revoked_digest=sha256:423926d8ce77a9bdce23db23501910843e9c7b17476b1c025320a3098e2d33f8

fail() {
  printf 'certified-runtime-contract: FAIL: %s\n' "$1" >&2
  exit 1
}

sh -n "$verifier"
grep -Fq 'FROM ${DBT_RUNTIME_CANDIDATE_IMAGE}' "$dockerfile" \
  || fail 'certified derivative must require an immutable candidate image argument'
if [ "$(grep -Fc 'ARG DBT_RUNTIME_CANDIDATE_IMAGE' "$dockerfile")" -ne 2 ]; then
  fail 'candidate image argument must be redeclared after FROM for runtime validation'
fi
grep -Fq '*@sha256:fe1d15f1b4215e693dadfc1d99be2ae07c7e50e8df144504005feb41cc7686b7)' "$dockerfile" \
  || fail 'actual FROM reference suffix is not verified before certification'
grep -Fq 'DBT_RUNTIME_CANDIDATE_REF_MISMATCH' "$dockerfile" \
  || fail 'candidate reference mismatch does not fail closed'
ref_check_line=$(grep -n 'DBT_RUNTIME_CANDIDATE_REF_MISMATCH' "$dockerfile" | cut -d: -f1)
candidate_verify_line=$(grep -n '/usr/local/bin/verify-dbt-runtime --verify-install' "$dockerfile" | head -1 | cut -d: -f1)
if [ "$ref_check_line" -ge "$candidate_verify_line" ]; then
  fail 'candidate reference must be checked before invoking candidate-controlled verifier'
fi
grep -Fq 'sha256:fe1d15f1b4215e693dadfc1d99be2ae07c7e50e8df144504005feb41cc7686b7' "$dockerfile" \
  || fail 'candidate evidence digest is not pinned'
grep -Fq 'bcd2fc84b05b9508990538c6642be7ac7b35073ec034e6b1980b22f556345a68' "$dockerfile" \
  || fail 'evidence manifest is not pinned'
grep -Fq 'IMAGE_CERTIFICATION_STATUS=CERTIFIED' "$dockerfile" \
  || fail 'certified derivative metadata is absent'
grep -Fq 'DBT_RUNTIME_NOT_CERTIFIED' "$verifier" \
  || fail 'certified verifier does not fail closed'
if grep -Fq 'localhost:' "$dockerfile" "$verifier"; then
  fail 'repository-local registry addresses must not be embedded'
fi

for runtime_consumer in "$platform_certification" "$airflow_factory"; do
  grep -Fq "$certified_digest" "$runtime_consumer" \
    || fail "runtime consumer does not pin the r2 certified derivative: $runtime_consumer"
  if grep -Fq "$revoked_digest" "$runtime_consumer"; then
    fail "runtime consumer still accepts the revoked r1 derivative: $runtime_consumer"
  fi
done
grep -Fq "\"certifiedDerivativePlatformManifestDigest\": \"$certified_digest\"" "$certified_profile" \
  || fail 'certification profile and runtime consumers disagree on the r2 derivative'
grep -Fq "@$certified_digest" "$certified_env" \
  || fail 'deployment example and runtime consumers disagree on the r2 derivative'

printf 'certified-runtime-contract: PASS\n'
