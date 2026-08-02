#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "${SCRIPT_DIR}/../../.." && pwd)
ENTRYPOINT_FILE="${REPO_ROOT}/builds/dts-dbt/entrypoint.sh"
FIXTURE_DIR="${REPO_ROOT}/services/dts-dbt/rt01"
PROFILE_FILE="${FIXTURE_DIR}/profiles.yml.example"
CANDIDATE_IMAGE=${H83_TEST_IMAGE:-dts-dbt:h83-rt01-linux-amd64-candidate-r1}

run_candidate() {
  docker run --rm \
    --platform linux/amd64 \
    --volume "${ENTRYPOINT_FILE}:/tmp/dts-dbt-entrypoint:ro" \
    --volume "${FIXTURE_DIR}:/workspace:ro" \
    --volume "${PROFILE_FILE}:/tmp/rt01-profile/profiles.yml:ro" \
    --env RT01_PG_HOST=rt01.invalid \
    --env RT01_PG_USER=rt01 \
    --env RT01_PG_PASSWORD=not-a-real-secret \
    --env RT01_PG_DATABASE=rt01 \
    --entrypoint /bin/sh \
    "$CANDIDATE_IMAGE" /tmp/dts-dbt-entrypoint "$@"
}

assert_blocked() {
  label=$1
  shift
  set +e
  output=$(run_candidate "$@" 2>&1)
  status=$?
  set -e
  if [ "$status" -ne 42 ]; then
    printf 'entrypoint-matrix: FAIL: %s returned %s, expected 42\n%s\n' "$label" "$status" "$output" >&2
    exit 1
  fi
  printf '%s' "$output" | grep -Fq DBT_RUNTIME_NOT_CERTIFIED || {
    printf 'entrypoint-matrix: FAIL: %s omitted DBT_RUNTIME_NOT_CERTIFIED\n' "$label" >&2
    exit 1
  }
  printf 'entrypoint-matrix: blocked=%s exit=42\n' "$label"
}

assert_allowed() {
  label=$1
  shift
  set +e
  output=$(run_candidate "$@" 2>&1)
  status=$?
  set -e
  if [ "$status" -ne 0 ]; then
    printf 'entrypoint-matrix: FAIL: %s was not allowed, exit=%s\n%s\n' "$label" "$status" "$output" >&2
    exit 1
  fi
  printf 'entrypoint-matrix: allowed=%s\n' "$label"
}

assert_blocked compile compile
assert_blocked docs-generate docs generate
assert_blocked debug debug
assert_blocked profiles-before-compile --profiles-dir /tmp/rt01-profile compile
assert_blocked quiet-run --quiet run
assert_blocked json-log-build --log-format json build
assert_blocked run-operation run-operation unsafe_macro
assert_blocked show show
assert_blocked retry retry
assert_blocked python-module-run python -m dbt run

assert_allowed help --help
assert_allowed version --version
assert_allowed parse parse --project-dir /workspace --profiles-dir /tmp/rt01-profile --target-path /tmp/target --log-path /tmp/logs --no-use-colors
assert_allowed list list --project-dir /workspace --profiles-dir /tmp/rt01-profile --target-path /tmp/target --log-path /tmp/logs --output name --no-use-colors
assert_allowed ls ls --project-dir /workspace --profiles-dir /tmp/rt01-profile --target-path /tmp/target --log-path /tmp/logs --output name --no-use-colors

printf 'entrypoint-matrix: PASS\n'
