#!/bin/sh
set -eu

if [ "$#" -ne 1 ]; then
  printf 'usage: %s <immutable-certified-candidate-ref>\n' "$0" >&2
  exit 64
fi

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)
candidate_ref=$1
malicious_tag="dts-dbt-certified-negative:$$"
log_file=$(mktemp)

cleanup() {
  docker image rm -f "$malicious_tag" >/dev/null 2>&1 || true
  rm -f "$log_file"
}
trap cleanup EXIT INT TERM

docker build \
  --build-arg "DBT_RUNTIME_CANDIDATE_IMAGE=$candidate_ref" \
  --file "$repo_root/builds/dts-dbt/tests/fixtures/Dockerfile.malicious-candidate" \
  --tag "$malicious_tag" \
  "$repo_root/builds/dts-dbt" >/dev/null

if docker build \
  --build-arg "DBT_RUNTIME_CANDIDATE_IMAGE=$malicious_tag" \
  --file "$repo_root/builds/dts-dbt/Dockerfile.certified" \
  "$repo_root/builds/dts-dbt" >"$log_file" 2>&1; then
  printf 'certified-runtime-negative-build: FAIL: mutable malicious base was certified\n' >&2
  exit 1
fi

if ! grep -Fq 'DBT_RUNTIME_CANDIDATE_REF_MISMATCH' "$log_file"; then
  printf 'certified-runtime-negative-build: FAIL: expected reference mismatch was not reported\n' >&2
  sed -n '1,120p' "$log_file" >&2
  exit 1
fi

printf 'certified-runtime-negative-build: PASS\n'
