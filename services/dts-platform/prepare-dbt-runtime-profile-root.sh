#!/bin/sh
set -eu

runtime_root="${DTS_DBT_RUNTIME_PROFILE_ROOT:-/dev/shm/dts-dbt-runtime}"
expected_uid="${DTS_DBT_RUNTIME_PROFILE_EXPECTED_UID:-$(id -u)}"
repair_docker_created_root="${DTS_DBT_RUNTIME_PROFILE_REPAIR_DOCKER_CREATED_ROOT:-false}"
trusted_stale_uids="${DTS_DBT_RUNTIME_PROFILE_TRUSTED_STALE_UIDS:-0,1000}"

fail() {
  echo "[dbt-runtime-preflight] ERROR: $*" >&2
  exit 1
}

case "${expected_uid}" in
  ''|*[!0-9]*)
    fail "DTS_DBT_RUNTIME_PROFILE_EXPECTED_UID must be numeric"
    ;;
esac

case "${trusted_stale_uids}" in
  ''|,*|*,|*,,*|*[!0-9,]*)
    fail "DTS_DBT_RUNTIME_PROFILE_TRUSTED_STALE_UIDS must be a comma-separated numeric list"
    ;;
esac

is_trusted_stale_uid() {
  candidate_uid="$1"
  previous_ifs="${IFS}"
  IFS=','
  for trusted_uid in ${trusted_stale_uids}; do
    if [ "${trusted_uid}" = "${candidate_uid}" ]; then
      IFS="${previous_ifs}"
      return 0
    fi
  done
  IFS="${previous_ifs}"
  return 1
}

case "${runtime_root}" in
  /*/dts-dbt-runtime)
    ;;
  *)
    fail "runtime root must be an absolute path ending in /dts-dbt-runtime"
    ;;
esac

if [ -L "${runtime_root}" ]; then
  fail "runtime root must not be a symbolic link: ${runtime_root}"
fi
if [ -e "${runtime_root}" ] && [ ! -d "${runtime_root}" ]; then
  fail "runtime root is not a directory: ${runtime_root}"
fi
created_by_preflight="false"
if [ ! -e "${runtime_root}" ]; then
  runtime_parent="$(dirname -- "${runtime_root}")"
  if [ -L "${runtime_parent}" ]; then
    fail "runtime parent must not be a symbolic link: ${runtime_parent}"
  fi
  if [ ! -d "${runtime_parent}" ]; then
    fail "runtime parent does not exist: ${runtime_parent}"
  fi
  parent_filesystem_type="$(stat -f -c '%T' "${runtime_parent}")"
  case "${parent_filesystem_type}" in
    tmpfs|ramfs)
      ;;
    *)
      fail "runtime parent must be tmpfs/ramfs; found ${parent_filesystem_type}"
      ;;
  esac
  mkdir -m 0700 -- "${runtime_root}"
  created_by_preflight="true"
fi
if [ -L "${runtime_root}" ]; then
  fail "runtime root became a symbolic link: ${runtime_root}"
fi

filesystem_type="$(stat -f -c '%T' "${runtime_root}")"
case "${filesystem_type}" in
  tmpfs|ramfs)
    ;;
  *)
    fail "runtime root must be tmpfs/ramfs; found ${filesystem_type}"
    ;;
esac

actual_uid="$(stat -c '%u' "${runtime_root}")"
actual_mode="$(stat -c '%a' "${runtime_root}")"
if [ "${actual_uid}" != "${expected_uid}" ] || [ "${actual_mode}" != "700" ]; then
  repair_allowed="false"
  if [ "${created_by_preflight}" = "true" ]; then
    repair_allowed="true"
  # Docker bind creation commonly leaves 0755. A previous DTS run can also
  # leave an empty 0700 directory owned by a trusted runtime UID after the
  # operator changes between root and the standard uid 1000 account.
  elif [ "${repair_docker_created_root}" = "true" ] &&
    { [ "${actual_mode}" = "700" ] || [ "${actual_mode}" = "755" ]; } &&
    { [ "${actual_uid}" = "${expected_uid}" ] || is_trusted_stale_uid "${actual_uid}"; }; then
    repair_allowed="true"
  fi
  if [ "${repair_allowed}" != "true" ]; then
    fail "runtime root uid/mode=${actual_uid}/${actual_mode}; expected ${expected_uid}/700"
  fi
  if find "${runtime_root}" -mindepth 1 -print -quit | grep -q .; then
    fail "refusing to repair a non-empty Docker-created runtime root"
  fi
  chown "${expected_uid}" -- "${runtime_root}" ||
    fail "cannot set runtime root uid=${expected_uid}: ${runtime_root}"
  chmod 0700 -- "${runtime_root}" ||
    fail "cannot set runtime root mode=0700: ${runtime_root}"
fi

actual_uid="$(stat -c '%u' "${runtime_root}")"
actual_mode="$(stat -c '%a' "${runtime_root}")"
if [ "${actual_uid}" != "${expected_uid}" ]; then
  fail "runtime root uid=${actual_uid}; expected ${expected_uid}"
fi
if [ "${actual_mode}" != "700" ]; then
  fail "runtime root mode=${actual_mode}; expected 700"
fi

echo "[dbt-runtime-preflight] ready: ${runtime_root} (uid=${actual_uid}, mode=${actual_mode}, fs=${filesystem_type})"
