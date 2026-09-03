#!/bin/sh
set -eu

PROJECT_ROOT=/opt/dbt
PROFILE_ROOT=/run/dts-dbt-runtime
STATE_ROOT=/run/dts-dbt-model-runs
RUNTIME_VERIFIER=/usr/local/bin/verify-dbt-runtime

fail() {
  code=$1
  message=$2
  printf '{"status":"FAILED","code":"%s","message":"%s"}\n' \
    "$code" "$message" >&2
  exit 64
}

require_uuid() {
  value=$1
  printf '%s\n' "$value" \
    | grep -Eq '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' \
    || fail DBT_MANAGED_RUN_ID_INVALID 'Managed run id is invalid'
}

require_checksum() {
  value=$1
  printf '%s\n' "$value" | grep -Eq '^[0-9a-f]{64}$' \
    || fail DBT_MANAGED_BUNDLE_INVALID 'Project bundle checksum is invalid'
}

require_target() {
  value=$1
  printf '%s\n' "$value" | grep -Eq '^[A-Za-z_][A-Za-z0-9_-]{0,127}$' \
    || fail DBT_MANAGED_TARGET_INVALID 'dbt target is invalid'
}

cleanup_state() {
  if [ -d "$state_dir" ] && [ ! -L "$state_dir" ]; then
    rm -f -- "$state_dir/pid"
    rmdir -- "$state_dir" 2>/dev/null || true
  fi
}

cleanup_execution() {
  if [ -n "${child_pid:-}" ] && kill -0 "$child_pid" 2>/dev/null; then
    kill -TERM -- "-$child_pid" 2>/dev/null || true
  fi
  cleanup_state
}

process_matches_run() {
  expected_pid=$1
  expected_run_id=$2
  [ -r "/proc/$expected_pid/cmdline" ] || return 1
  command_line=$(tr '\000' ' ' < "/proc/$expected_pid/cmdline")
  case "$command_line" in
    *"dbt build"*"$PROFILE_ROOT/$expected_run_id"*) return 0 ;;
    *) return 1 ;;
  esac
}

require_mount_mode() {
  mount_path=$1
  required_mode=$2
  mount_options=$(awk -v path="$mount_path" '$2 == path { print $4 }' /proc/mounts | tail -n 1)
  case ",$mount_options," in
    *",$required_mode,"*) return 0 ;;
    *) fail DBT_MANAGED_MOUNT_INVALID 'Managed dbt mount mode is invalid' ;;
  esac
}

stop_run() {
  [ "$#" -eq 1 ] \
    || fail DBT_MANAGED_ARGUMENTS_INVALID 'stop requires one managed run id'
  run_id=$1
  require_uuid "$run_id"
  state_dir="$STATE_ROOT/$run_id"
  if [ ! -e "$state_dir" ] && [ ! -L "$state_dir" ]; then
    return 0
  fi
  [ -d "$state_dir" ] && [ ! -L "$state_dir" ] \
    || fail DBT_MANAGED_STATE_INVALID 'Managed run state is invalid'
  [ -f "$state_dir/pid" ] && [ ! -L "$state_dir/pid" ] \
    || fail DBT_MANAGED_STATE_INVALID 'Managed run pid is unavailable'
  pid=$(sed -n '1p' "$state_dir/pid")
  printf '%s\n' "$pid" | grep -Eq '^[1-9][0-9]*$' \
    || fail DBT_MANAGED_STATE_INVALID 'Managed run pid is invalid'
  if ! kill -0 "$pid" 2>/dev/null; then
    cleanup_state
    return 0
  fi
  process_matches_run "$pid" "$run_id" \
    || fail DBT_MANAGED_PROCESS_MISMATCH 'Managed run process identity does not match'

  kill -TERM -- "-$pid" 2>/dev/null || true
  count=0
  while kill -0 "$pid" 2>/dev/null && [ "$count" -lt 100 ]; do
    sleep 0.1
    count=$((count + 1))
  done
  if kill -0 "$pid" 2>/dev/null; then
    process_matches_run "$pid" "$run_id" \
      || fail DBT_MANAGED_PROCESS_MISMATCH 'Managed run process identity changed'
    kill -KILL -- "-$pid" 2>/dev/null || true
  fi
  count=0
  while kill -0 "$pid" 2>/dev/null && [ "$count" -lt 100 ]; do
    sleep 0.1
    count=$((count + 1))
  done
  kill -0 "$pid" 2>/dev/null \
    && fail DBT_MANAGED_STOP_UNCONFIRMED 'Managed run did not stop'
  cleanup_state
}

build_run() {
  [ "$#" -eq 5 ] \
    || fail DBT_MANAGED_ARGUMENTS_INVALID 'build requires run, bundle, target, selector and profile root'
  run_id=$1
  bundle_checksum=$2
  target_name=$3
  selector=$4
  supplied_profile_root=$5

  require_uuid "$run_id"
  require_checksum "$bundle_checksum"
  require_target "$target_name"
  [ "$supplied_profile_root" = "$PROFILE_ROOT" ] \
    || fail DBT_MANAGED_PROFILE_ROOT_INVALID 'Runtime profile root is invalid'
  [ -n "$selector" ] \
    || fail DBT_MANAGED_SELECTOR_INVALID 'dbt selector is invalid'
  selector_newlines=$(printf '%s' "$selector" | wc -l | tr -d '[:space:]')
  [ "$selector_newlines" = 0 ] \
    || fail DBT_MANAGED_SELECTOR_INVALID 'dbt selector is invalid'
  if printf '%s' "$selector" | LC_ALL=C grep -q '[[:cntrl:]]'; then
    fail DBT_MANAGED_SELECTOR_INVALID 'dbt selector is invalid'
  fi

  project_dir="$PROJECT_ROOT/.dts-scoped-runs/candidate-$bundle_checksum"
  profile_dir="$PROFILE_ROOT/$run_id"
  [ -d "$project_dir" ] && [ ! -L "$project_dir" ] \
    || fail DBT_MANAGED_PROJECT_UNAVAILABLE 'Scoped dbt project is unavailable'
  [ -f "$project_dir/dbt_project.yml" ] && [ ! -L "$project_dir/dbt_project.yml" ] \
    || fail DBT_MANAGED_PROJECT_INVALID 'Scoped dbt project is invalid'
  [ -d "$profile_dir" ] && [ ! -L "$profile_dir" ] \
    || fail DBT_MANAGED_PROFILE_UNAVAILABLE 'Runtime profile lease is unavailable'
  [ -f "$profile_dir/profiles.yml" ] && [ ! -L "$profile_dir/profiles.yml" ] \
    || fail DBT_MANAGED_PROFILE_INVALID 'Runtime profile lease is invalid'
  require_mount_mode "$PROJECT_ROOT" rw
  require_mount_mode "$PROFILE_ROOT" ro
  [ -x "$RUNTIME_VERIFIER" ] \
    || fail DBT_RUNTIME_VERIFIER_UNAVAILABLE 'dbt runtime verifier is unavailable'
  command -v flock >/dev/null 2>&1 \
    || fail DBT_MANAGED_LOCK_UNAVAILABLE 'Managed dbt execution lock is unavailable'
  "$RUNTIME_VERIFIER" --verify-install >/dev/null

  umask 077
  if [ -e "$STATE_ROOT" ] || [ -L "$STATE_ROOT" ]; then
    [ -d "$STATE_ROOT" ] && [ ! -L "$STATE_ROOT" ] \
      || fail DBT_MANAGED_STATE_INVALID 'Managed run state root is invalid'
  else
    mkdir -m 0700 "$STATE_ROOT"
  fi
  state_dir="$STATE_ROOT/$run_id"
  if [ -e "$state_dir" ] || [ -L "$state_dir" ]; then
    [ -d "$state_dir" ] && [ ! -L "$state_dir" ] \
      || fail DBT_MANAGED_STATE_INVALID 'Managed run state is invalid'
    if [ -f "$state_dir/pid" ] && [ ! -L "$state_dir/pid" ]; then
      existing_pid=$(sed -n '1p' "$state_dir/pid")
      if printf '%s\n' "$existing_pid" | grep -Eq '^[1-9][0-9]*$' \
        && kill -0 "$existing_pid" 2>/dev/null; then
        fail DBT_MANAGED_RUN_ACTIVE 'Managed run is already active'
      fi
    fi
    cleanup_state
  fi
  mkdir -m 0700 "$state_dir"
  child_pid=''
  trap 'cleanup_execution' EXIT
  trap 'if [ -n "$child_pid" ]; then kill -TERM -- "-$child_pid" 2>/dev/null || true; fi' HUP INT TERM

  DBT_LOG_PATH="$project_dir/logs" \
    setsid flock -x "$STATE_ROOT/executor.lock" dbt build \
      --project-dir "$project_dir" \
      --profiles-dir "$profile_dir" \
      --target "$target_name" \
      --select "$selector" &
  child_pid=$!
  printf '%s\n' "$child_pid" > "$state_dir/pid"
  set +e
  wait "$child_pid"
  status=$?
  set -e
  exit "$status"
}

action=${1:-}
if [ "$#" -gt 0 ]; then
  shift
fi
case "$action" in
  build) build_run "$@" ;;
  stop) stop_run "$@" ;;
  *) fail DBT_MANAGED_ACTION_INVALID 'Only build and stop are supported' ;;
esac
