#!/usr/bin/env bash

[[ -n "${_DTS_UPGRADE_COMMON_LOADED:-}" ]] && return 0
_DTS_UPGRADE_COMMON_LOADED=1

upgrade_info() {
  echo "[dts-upgrade] $*"
}

upgrade_warn() {
  printf '\033[0;33m[dts-upgrade] WARNING: %s\033[0m\n' "$*" >&2
}

upgrade_die() {
  printf '\033[0;31m[dts-upgrade] ERROR: %s\033[0m\n' "$*" >&2
  exit 1
}

upgrade_require_dir() {
  local path="$1"
  local label="$2"
  [[ -d "${path}" ]] || upgrade_die "${label} directory not found: ${path}"
}

upgrade_timestamp() {
  date +%Y%m%d-%H%M%S
}

upgrade_require_file() {
  local path="$1"
  local label="$2"
  [[ -f "${path}" ]] || upgrade_die "${label} file not found: ${path}"
}

upgrade_env_value_from_file() {
  local env_file="$1"
  local key="$2"
  local line

  [[ -f "${env_file}" ]] || return 1
  while IFS= read -r line || [[ -n "${line}" ]]; do
    if [[ "${line}" =~ ^[[:space:]]*(export[[:space:]]+)?${key}=(.*)$ ]]; then
      printf '%s\n' "${BASH_REMATCH[2]}"
      return 0
    fi
  done < "${env_file}"
  return 1
}

upgrade_init_logs() {
  local target_dir="$1"
  local timestamp="$2"
  upgrade_init_action_logs "${target_dir}" "${timestamp}" "upgrade"
}

upgrade_init_action_logs() {
  local target_dir="$1"
  local timestamp="$2"
  local prefix="$3"

  mkdir -p "${target_dir}/logs"
  UPGRADE_LOG_FILE="${target_dir}/logs/${prefix}-${timestamp}.log"
  UPGRADE_SUMMARY_FILE="${target_dir}/logs/${prefix}-${timestamp}.summary.md"
  : > "${UPGRADE_LOG_FILE}"
  cat > "${UPGRADE_SUMMARY_FILE}" <<EOF_SUMMARY
# DTS Upgrade Summary

- timestamp: ${timestamp}
- action: ${prefix}
- status: preflight-ready
EOF_SUMMARY
}

upgrade_append_log() {
  local message="$1"
  if [[ -n "${UPGRADE_LOG_FILE:-}" ]]; then
    printf '%s\n' "${message}" >> "${UPGRADE_LOG_FILE}"
  fi
}

upgrade_append_summary() {
  local message="$1"
  if [[ -n "${UPGRADE_SUMMARY_FILE:-}" ]]; then
    printf '%s\n' "${message}" >> "${UPGRADE_SUMMARY_FILE}"
  fi
}

upgrade_append_unique_line() {
  local file="$1"
  local value="$2"
  touch "${file}"
  if ! grep -Fqx "${value}" "${file}" 2>/dev/null; then
    printf '%s\n' "${value}" >> "${file}"
  fi
}

upgrade_manifest_list_field() {
  local manifest_file="$1"
  local field_name="$2"

  python3 - <<'PY' "${manifest_file}" "${field_name}"
import json
import sys

manifest_file, field_name = sys.argv[1:3]
with open(manifest_file, "r", encoding="utf-8") as fh:
    data = json.load(fh)

items = data.get(field_name) or []
if not isinstance(items, list):
    raise SystemExit(f"manifest field must be a list: {field_name}")

for item in items:
    print(item)
PY
}

upgrade_init_backup_state() {
  local target_dir="$1"
  local timestamp="$2"

  UPGRADE_BACKUP_DIR="${target_dir}/backups/upgrade-${timestamp}"
  UPGRADE_ROLLBACK_FILES_LIST="${UPGRADE_BACKUP_DIR}/.rollback-files.list"
  UPGRADE_PROTECTED_DIRS_LIST="${UPGRADE_BACKUP_DIR}/.protected-dirs.list"
  UPGRADE_BACKED_UP_DATA_DIRS_LIST="${UPGRADE_BACKUP_DIR}/.backed-up-data-dirs.list"

  mkdir -p "${UPGRADE_BACKUP_DIR}"
  : > "${UPGRADE_ROLLBACK_FILES_LIST}"
  : > "${UPGRADE_PROTECTED_DIRS_LIST}"
  : > "${UPGRADE_BACKED_UP_DATA_DIRS_LIST}"

  if [[ -d "${target_dir}/services/dts-pg/data" ]]; then
    upgrade_append_unique_line "${UPGRADE_PROTECTED_DIRS_LIST}" "services/dts-pg/data"
  fi
}

upgrade_backup_target_file() {
  local target_dir="$1"
  local target_file="$2"
  local relative_path
  local backup_file

  if [[ ! -f "${target_file}" ]]; then
    return 0
  fi

  relative_path="${target_file#${target_dir}/}"
  backup_file="${UPGRADE_BACKUP_DIR}/${relative_path}"
  if [[ -f "${backup_file}" ]]; then
    return 0
  fi

  mkdir -p "$(dirname "${backup_file}")"
  cp "${target_file}" "${backup_file}"
  upgrade_append_unique_line "${UPGRADE_ROLLBACK_FILES_LIST}" "${relative_path}"
  upgrade_append_log "backup created ${relative_path}"
}

upgrade_write_rollback_manifests() {
  local extra_dir="$1"
  local backup_manifest="${UPGRADE_BACKUP_DIR}/rollback-manifest.json"
  local extra_manifest="${extra_dir}/rollback-manifest.json"

  python3 - <<'PY' \
    "${UPGRADE_ROLLBACK_FILES_LIST}" \
    "${UPGRADE_PROTECTED_DIRS_LIST}" \
    "${UPGRADE_BACKED_UP_DATA_DIRS_LIST}" \
    "${backup_manifest}" \
    "${extra_manifest}"
import json
import sys

files_list, dirs_list, backed_up_dirs_list, backup_manifest, extra_manifest = sys.argv[1:6]

def read_lines(path):
    with open(path, "r", encoding="utf-8") as fh:
        return [line.strip() for line in fh if line.strip()]

payload = {
    "backedUpFiles": read_lines(files_list),
    "protectedDataDirs": read_lines(dirs_list),
    "backedUpDataDirs": read_lines(backed_up_dirs_list),
}

for output in (backup_manifest, extra_manifest):
    with open(output, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=2, sort_keys=True)
        fh.write("\n")
PY
}

upgrade_set_state() {
  local state="$1"
  upgrade_append_log "state=${state}"
  upgrade_append_summary "- state ${state}"
}

upgrade_detect_mode() {
  local source_root="$1"
  local target_dir="$2"
  local target_env="${target_dir}/.env"
  local legacy_stack
  local deploy_mode

  UPGRADE_MODE="single"
  UPGRADE_PRECHECK_COMPOSE_FILES=()
  UPGRADE_RUNTIME_COMPOSE_FILES=()

  legacy_stack="$(upgrade_env_value_from_file "${target_env}" "LEGACY_STACK" || true)"
  deploy_mode="$(upgrade_env_value_from_file "${target_env}" "DEPLOY_MODE" || true)"

  if [[ "${legacy_stack}" == "true" ]]; then
    UPGRADE_MODE="legacy"
    UPGRADE_PRECHECK_COMPOSE_FILES=("docker-compose.legacy.yml")
    UPGRADE_RUNTIME_COMPOSE_FILES=("docker-compose.legacy.yml")
  else
    case "${deploy_mode:-single}" in
      ""|single)
        UPGRADE_MODE="single"
        UPGRADE_PRECHECK_COMPOSE_FILES=("docker-compose.yml")
        if [[ -f "${target_dir}/docker-compose-app.yml" ]]; then
          UPGRADE_PRECHECK_COMPOSE_FILES+=("docker-compose-app.yml")
        fi
        UPGRADE_RUNTIME_COMPOSE_FILES=("docker-compose.yml")
        if [[ -f "${target_dir}/docker-compose-app.yml" || -f "${source_root}/docker-compose-app.yml" ]]; then
          UPGRADE_RUNTIME_COMPOSE_FILES+=("docker-compose-app.yml")
        fi
        ;;
      *)
        upgrade_die "unsupported DEPLOY_MODE for in-place upgrade: ${deploy_mode}"
        ;;
    esac
  fi

  upgrade_append_log "mode=${UPGRADE_MODE} precheck_compose=${UPGRADE_PRECHECK_COMPOSE_FILES[*]} runtime_compose=${UPGRADE_RUNTIME_COMPOSE_FILES[*]}"
  upgrade_append_summary "- mode: ${UPGRADE_MODE}"
  upgrade_append_summary "- compose files: ${UPGRADE_RUNTIME_COMPOSE_FILES[*]}"
}

upgrade_detect_compose_runner() {
  if [[ -n "${UPGRADE_COMPOSE_RUNNER:-}" ]]; then
    printf '%s\n' "${UPGRADE_COMPOSE_RUNNER}"
    return 0
  fi

  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    UPGRADE_COMPOSE_RUNNER="docker compose"
  elif command -v docker-compose >/dev/null 2>&1 && docker-compose version >/dev/null 2>&1; then
    UPGRADE_COMPOSE_RUNNER="docker-compose"
  else
    upgrade_die "neither 'docker compose' nor 'docker-compose' is available"
  fi

  upgrade_append_log "compose runner=${UPGRADE_COMPOSE_RUNNER}"
  upgrade_append_summary "- compose runner: ${UPGRADE_COMPOSE_RUNNER}"
  printf '%s\n' "${UPGRADE_COMPOSE_RUNNER}"
}

upgrade_compose_cmd() {
  local target_dir="$1"
  shift
  local compose_runner
  compose_runner="$(upgrade_detect_compose_runner)"
  (
    cd "${target_dir}"
    if [[ "${compose_runner}" == "docker compose" ]]; then
      docker compose "$@"
    else
      docker-compose "$@"
    fi
  )
}

upgrade_run_target_compose() {
  local target_dir="$1"
  local files_var="$2"
  shift 2
  local -a cmd=()
  local compose_file
  local -n compose_files_ref="${files_var}"

  for compose_file in "${compose_files_ref[@]}"; do
    cmd+=(-f "${compose_file}")
  done
  cmd+=("$@")
  upgrade_compose_cmd "${target_dir}" "${cmd[@]}"
}

upgrade_running_services_output() {
  local target_dir="$1"
  local files_var="$2"
  local compose_runner

  compose_runner="$(upgrade_detect_compose_runner)"
  if [[ "${compose_runner}" == "docker compose" ]]; then
    upgrade_run_target_compose "${target_dir}" "${files_var}" ps --status running --services 2>/dev/null || true
  else
    upgrade_run_target_compose "${target_dir}" "${files_var}" ps --services --filter status=running 2>/dev/null || true
  fi
}

upgrade_postgres_target_major() {
  local source_root="$1"
  local source_env="${source_root}/.env"
  local image_postgres

  image_postgres="$(upgrade_env_value_from_file "${source_env}" "IMAGE_POSTGRES" || true)"
  if [[ -z "${image_postgres}" && -f "${source_root}/imgversion.conf" ]]; then
    image_postgres="$(grep -E '^IMAGE_POSTGRES=' "${source_root}/imgversion.conf" | head -n1 | cut -d= -f2- | tr -d '\r' || true)"
  fi
  [[ -n "${image_postgres}" ]] || return 1
  if [[ "${image_postgres}" =~ :([0-9]+)(\.[0-9]+)?$ ]]; then
    printf '%s\n' "${BASH_REMATCH[1]}"
    return 0
  fi
  return 1
}

upgrade_check_postgres_compatibility() {
  local source_root="$1"
  local target_dir="$2"
  local pg_version_file="${target_dir}/services/dts-pg/data/pgdata/PG_VERSION"
  local current_major
  local target_major

  if [[ ! -f "${pg_version_file}" ]]; then
    upgrade_append_log "postgres compatibility skipped: no PG_VERSION file"
    upgrade_append_summary "- postgres compatibility: skipped (no existing PG_VERSION)"
    return 0
  fi

  current_major="$(tr -d '[:space:]' < "${pg_version_file}")"
  target_major="$(upgrade_postgres_target_major "${source_root}" || true)"
  [[ -n "${target_major}" ]] || upgrade_die "unable to determine target postgres major version"

  if [[ "${current_major}" != "${target_major}" ]]; then
    upgrade_die "postgres major version mismatch: current=${current_major} target=${target_major}"
  fi

  upgrade_append_log "postgres compatibility ok: current=${current_major} target=${target_major}"
  upgrade_append_summary "- postgres compatibility: ok (${current_major} -> ${target_major})"
}

upgrade_start_target_stack() {
  local target_dir="$1"
  upgrade_run_target_compose "${target_dir}" UPGRADE_RUNTIME_COMPOSE_FILES down --remove-orphans >/dev/null || true
  upgrade_append_log "target stack cleaned before start"
  upgrade_run_target_compose "${target_dir}" UPGRADE_RUNTIME_COMPOSE_FILES up -d >/dev/null
  upgrade_append_log "target stack started"
}

upgrade_postcheck() {
  local target_dir="$1"
  local running_services

  running_services="$(upgrade_running_services_output "${target_dir}" UPGRADE_RUNTIME_COMPOSE_FILES)"
  if [[ -z "${running_services//[$'\t\r\n ']/}" ]]; then
    upgrade_die "postcheck found no running services"
  fi

  upgrade_append_log "postcheck running services: ${running_services//$'\n'/, }"
  upgrade_append_summary "- running services: ${running_services//$'\n'/, }"
}

upgrade_write_lock() {
  local target_dir="$1"
  local timestamp="$2"
  local lock_file="${target_dir}/.upgrade-lock"

  if [[ -e "${lock_file}" ]]; then
    upgrade_die "upgrade lock already exists: ${lock_file}"
  fi

  cat > "${lock_file}" <<EOF_LOCK
timestamp=${timestamp}
status=preflight-ready
EOF_LOCK
}

upgrade_remove_lock() {
  local target_dir="$1"
  rm -f "${target_dir}/.upgrade-lock"
}

upgrade_backup_postgres_data_dir() {
  local target_dir="$1"
  local relative_dir="services/dts-pg/data"
  local source_dir="${target_dir}/${relative_dir}"
  local backup_dir="${UPGRADE_BACKUP_DIR}/${relative_dir}"

  if [[ ! -d "${source_dir}" ]]; then
    return 0
  fi
  if [[ -d "${backup_dir}" ]]; then
    return 0
  fi

  mkdir -p "$(dirname "${backup_dir}")"
  cp -a "${source_dir}" "${backup_dir}"
  upgrade_append_unique_line "${UPGRADE_BACKED_UP_DATA_DIRS_LIST}" "${relative_dir}"
  upgrade_append_log "postgres cold backup created ${relative_dir}"
  upgrade_append_summary "- postgres cold backup: ${relative_dir}"
}

upgrade_restore_backed_up_files() {
  local target_dir="$1"
  local backup_dir="$2"
  local manifest_file="$3"
  local relative_path
  local source_file
  local target_file

  while IFS= read -r relative_path; do
    [[ -z "${relative_path}" ]] && continue
    source_file="${backup_dir}/${relative_path}"
    target_file="${target_dir}/${relative_path}"
    [[ -f "${source_file}" ]] || upgrade_die "backup file missing for rollback: ${relative_path}"
    mkdir -p "$(dirname "${target_file}")"
    cp "${source_file}" "${target_file}"
    upgrade_append_log "rollback restored file ${relative_path}"
    upgrade_append_summary "- rollback restored file: ${relative_path}"
  done < <(upgrade_manifest_list_field "${manifest_file}" "backedUpFiles")
}

upgrade_restore_backed_up_data_dirs() {
  local target_dir="$1"
  local backup_dir="$2"
  local manifest_file="$3"
  local relative_dir
  local source_dir
  local target_path

  while IFS= read -r relative_dir; do
    [[ -z "${relative_dir}" ]] && continue
    source_dir="${backup_dir}/${relative_dir}"
    target_path="${target_dir}/${relative_dir}"
    [[ -d "${source_dir}" ]] || upgrade_die "backup data dir missing for rollback: ${relative_dir}"
    rm -rf "${target_path}"
    mkdir -p "$(dirname "${target_path}")"
    cp -a "${source_dir}" "${target_path}"
    upgrade_append_log "rollback restored data dir ${relative_dir}"
    upgrade_append_summary "- rollback restored data dir: ${relative_dir}"
  done < <(upgrade_manifest_list_field "${manifest_file}" "backedUpDataDirs")
}

upgrade_stop_target_stack() {
  local target_dir="$1"
  if [[ ${#UPGRADE_PRECHECK_COMPOSE_FILES[@]} -eq 0 ]]; then
    return 0
  fi
  upgrade_run_target_compose "${target_dir}" UPGRADE_PRECHECK_COMPOSE_FILES down --remove-orphans >/dev/null || true
  upgrade_append_log "target stack stopped"
}

upgrade_check_containers_stopped() {
  local target_dir="$1"
  local output

  if [[ ${#UPGRADE_PRECHECK_COMPOSE_FILES[@]} -eq 0 ]]; then
    return 0
  fi

  output="$(upgrade_running_services_output "${target_dir}" UPGRADE_PRECHECK_COMPOSE_FILES)"
  if [[ -n "${output//[$'\t\r\n ']/}" ]]; then
    upgrade_die "target deployment still has running containers"
  fi
}

upgrade_manifest_images() {
  local manifest_file="$1"
  python3 -c '
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fh:
    data = json.load(fh)

images = data.get("images") or []
if not isinstance(images, list):
    raise SystemExit("manifest images must be a list")

for image in images:
    print(image)
' "${manifest_file}" 2>/dev/null || upgrade_die "failed to parse release manifest: ${manifest_file}"
}

upgrade_has_image_package() {
  local images_dir="$1"
  local extra_dir="$2"
  local manifest_file="${extra_dir}/release-manifest.json"
  local checksums_file="${extra_dir}/checksums.txt"

  if [[ -n "${UPGRADE_IMAGE_PACKAGE_PRESENT:-}" ]]; then
    [[ "${UPGRADE_IMAGE_PACKAGE_PRESENT}" == "true" ]]
    return
  fi

  if [[ ! -f "${manifest_file}" || ! -f "${checksums_file}" ]]; then
    UPGRADE_IMAGE_PACKAGE_PRESENT="false"
    upgrade_append_log "image package skipped: no release-manifest.json/checksums.txt, assuming images are preloaded"
    upgrade_append_summary "- image package: skipped (images assumed preloaded)"
    return 1
  fi

  if [[ ! -s "${manifest_file}" ]]; then
    UPGRADE_IMAGE_PACKAGE_PRESENT="false"
    upgrade_append_log "image package skipped: release-manifest.json is empty, assuming images are preloaded"
    upgrade_append_summary "- image package: skipped (empty manifest)"
    return 1
  fi

  if [[ ! -s "${checksums_file}" ]]; then
    UPGRADE_IMAGE_PACKAGE_PRESENT="false"
    upgrade_append_log "image package skipped: checksums.txt is empty, assuming images are preloaded"
    upgrade_append_summary "- image package: skipped (empty checksums)"
    return 1
  fi

  UPGRADE_IMAGE_PACKAGE_PRESENT="true"
  return 0
}

upgrade_verify_image_package() {
  local images_dir="$1"
  local extra_dir="$2"
  local manifest_file="${extra_dir}/release-manifest.json"
  local checksums_file="${extra_dir}/checksums.txt"
  local image_name

  if ! upgrade_has_image_package "${images_dir}" "${extra_dir}"; then
    return 0
  fi

  while IFS= read -r image_name; do
    [[ -z "${image_name}" ]] && continue
    if [[ ! -f "${images_dir}/${image_name}" ]]; then
      upgrade_die "image tar referenced by manifest is missing: ${image_name}"
    fi
  done < <(upgrade_manifest_images "${manifest_file}")

  if ! (cd "${images_dir}" && sha256sum -c "${checksums_file}" >/dev/null); then
    upgrade_die "checksums validation failed"
  fi
}

upgrade_load_images() {
  local images_dir="$1"
  local extra_dir="$2"
  local manifest_file="${extra_dir}/release-manifest.json"
  local image_name

  if ! upgrade_has_image_package "${images_dir}" "${extra_dir}"; then
    return 0
  fi

  while IFS= read -r image_name; do
    [[ -z "${image_name}" ]] && continue
    upgrade_append_log "docker load -i ${images_dir}/${image_name}"
    docker load -i "${images_dir}/${image_name}" >/dev/null
  done < <(upgrade_manifest_images "${manifest_file}")
}

upgrade_env_key_from_line() {
  local line="$1"
  if [[ "${line}" =~ ^[[:space:]]*(export[[:space:]]+)?([A-Za-z_][A-Za-z0-9_]*)= ]]; then
    printf '%s\n' "${BASH_REMATCH[2]}"
    return 0
  fi
  return 1
}

upgrade_env_value_from_line() {
  local line="$1"
  if [[ "${line}" =~ ^[[:space:]]*(export[[:space:]]+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
    printf '%s\n' "${BASH_REMATCH[3]}"
    return 0
  fi
  return 1
}

upgrade_join_csv() {
  local first=true
  local item
  for item in "$@"; do
    [[ -z "${item}" ]] && continue
    if [[ "${first}" == "true" ]]; then
      printf '%s' "${item}"
      first=false
    else
      printf ', %s' "${item}"
    fi
  done
}

upgrade_merge_env_file() {
  local source_root="$1"
  local target_dir="$2"
  local source_env="${source_root}/.env"
  local target_env="${target_dir}/.env"
  local tmp_env="${target_dir}/.env.upgrade-tmp"
  local line key value
  local -a appended_keys=()
  local -a preserved_keys=()
  local -a conflicting_keys=()
  local preserved_csv appended_csv conflicts_csv
  declare -A target_keys=()
  declare -A target_values=()

  if [[ ! -f "${source_env}" ]]; then
    return 0
  fi

  if [[ ! -f "${target_env}" ]]; then
    cp "${source_env}" "${target_env}"
    upgrade_append_log "env merge: initialized from source package"
    upgrade_append_summary "- env initialized from source package"
    return 0
  fi

  upgrade_backup_target_file "${target_dir}" "${target_env}"
  cp "${target_env}" "${tmp_env}"

  while IFS= read -r line || [[ -n "${line}" ]]; do
    key="$(upgrade_env_key_from_line "${line}" || true)"
    [[ -z "${key}" ]] && continue
    value="$(upgrade_env_value_from_line "${line}" || true)"
    target_keys["${key}"]=1
    target_values["${key}"]="${value}"
  done < "${target_env}"

  while IFS= read -r line || [[ -n "${line}" ]]; do
    key="$(upgrade_env_key_from_line "${line}" || true)"
    [[ -z "${key}" ]] && continue
    value="$(upgrade_env_value_from_line "${line}" || true)"
    if [[ -n "${target_keys[${key}]:-}" ]]; then
      preserved_keys+=("${key}")
      if [[ "${target_values[${key}]}" != "${value}" ]]; then
        conflicting_keys+=("${key}")
      fi
      continue
    fi
    printf '%s\n' "${line}" >> "${tmp_env}"
    target_keys["${key}"]=1
    appended_keys+=("${key}")
  done < "${source_env}"

  mv "${tmp_env}" "${target_env}"

  appended_csv="$(upgrade_join_csv "${appended_keys[@]}")"
  preserved_csv="$(upgrade_join_csv "${preserved_keys[@]}")"
  conflicts_csv="$(upgrade_join_csv "${conflicting_keys[@]}")"

  upgrade_append_log "env merge preserved=[${preserved_csv}] appended=[${appended_csv}] conflicts=[${conflicts_csv}]"
  [[ -n "${appended_csv}" ]] && upgrade_append_summary "- env appended: ${appended_csv}"
  [[ -n "${preserved_csv}" ]] && upgrade_append_summary "- env preserved: ${preserved_csv}"
  [[ -n "${conflicts_csv}" ]] && upgrade_append_summary "- env conflicts kept old values: ${conflicts_csv}"
  return 0
}

upgrade_compose_files_from_source() {
  local source_root="$1"
  find "${source_root}" -maxdepth 1 -type f \( -name 'docker-compose*.yml' -o -name 'docker-compose*.yaml' \) | sort
}

upgrade_should_merge_compose_file() {
  local file_name="$1"

  case "${UPGRADE_MODE:-single}" in
    legacy)
      [[ "${file_name}" == "docker-compose.legacy.yml" ]]
      ;;
    single)
      [[ "${file_name}" == "docker-compose.yml" || "${file_name}" == "docker-compose-app.yml" ]]
      ;;
    *)
      return 1
      ;;
  esac
}

upgrade_prepare_compose_env_file() {
  local target_env="${TARGET_DIR:-}/.env"
  local source_env="${SOURCE_ROOT:-}/.env"

  if [[ -n "${UPGRADE_COMPOSE_ENV_FILE:-}" && -f "${UPGRADE_COMPOSE_ENV_FILE}" ]]; then
    printf '%s\n' "${UPGRADE_COMPOSE_ENV_FILE}"
    return 0
  fi

  UPGRADE_COMPOSE_ENV_FILE="$(mktemp)"

  python3 - <<'PY' "${target_env}" "${source_env}" "${UPGRADE_COMPOSE_ENV_FILE}"
import collections
import os
import sys

target_env, source_env, out_path = sys.argv[1:4]

def read_env(path):
    items = []
    if not path or not os.path.isfile(path):
        return items
    with open(path, "r", encoding="utf-8") as fh:
        for raw in fh:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("export "):
                line = line[len("export "):].lstrip()
            if "=" not in line:
                continue
            key, value = line.split("=", 1)
            key = key.strip()
            if not key:
                continue
            items.append((key, value))
    return items

merged = collections.OrderedDict()
for key, value in read_env(target_env):
    merged[key] = value
for key, value in read_env(source_env):
    merged.setdefault(key, value)

with open(out_path, "w", encoding="utf-8") as fh:
    for key, value in merged.items():
        fh.write(f"{key}={value}\n")
PY

  printf '%s\n' "${UPGRADE_COMPOSE_ENV_FILE}"
}

upgrade_json_file_valid() {
  local json_file="$1"
  python3 - <<'PY' "${json_file}" >/dev/null 2>&1
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fh:
    json.load(fh)
PY
}

upgrade_compose_config_json() {
  local compose_file="$1"
  local compose_runner
  local compose_env_file
  local stdout_file
  local stderr_file

  compose_runner="$(upgrade_detect_compose_runner)"
  compose_env_file="$(upgrade_prepare_compose_env_file)"
  stdout_file="$(mktemp)"
  stderr_file="$(mktemp)"

  if [[ "${compose_runner}" == "docker compose" ]]; then
    if docker compose --env-file "${compose_env_file}" -f "${compose_file}" config --format json >"${stdout_file}" 2>"${stderr_file}"; then
      if [[ -s "${stdout_file}" ]] && upgrade_json_file_valid "${stdout_file}"; then
        cat "${stdout_file}"
        rm -f "${stdout_file}" "${stderr_file}"
        return 0
      fi
    fi
    if docker compose --env-file "${compose_env_file}" -f "${compose_file}" config >"${stdout_file}" 2>"${stderr_file}"; then
      python3 - <<'PY' "${stdout_file}"
import json
import sys
import yaml

with open(sys.argv[1], "r", encoding="utf-8") as fh:
    data = yaml.safe_load(fh) or {}

json.dump(data, sys.stdout, ensure_ascii=False, indent=2, sort_keys=True)
sys.stdout.write("\n")
PY
      rm -f "${stdout_file}" "${stderr_file}"
      return 0
    fi
  else
    if docker-compose --env-file "${compose_env_file}" -f "${compose_file}" config --format json >"${stdout_file}" 2>"${stderr_file}"; then
      if [[ -s "${stdout_file}" ]] && upgrade_json_file_valid "${stdout_file}"; then
        cat "${stdout_file}"
        rm -f "${stdout_file}" "${stderr_file}"
        return 0
      fi
    fi
    if docker-compose --env-file "${compose_env_file}" -f "${compose_file}" config >"${stdout_file}" 2>"${stderr_file}"; then
      python3 - <<'PY' "${stdout_file}"
import json
import sys
import yaml

with open(sys.argv[1], "r", encoding="utf-8") as fh:
    data = yaml.safe_load(fh) or {}

json.dump(data, sys.stdout, ensure_ascii=False, indent=2, sort_keys=True)
sys.stdout.write("\n")
PY
      rm -f "${stdout_file}" "${stderr_file}"
      return 0
    fi
  fi

  upgrade_die "failed to render compose config for ${compose_file}: $(tr '\n' ' ' < "${stderr_file}")"
}

upgrade_merge_compose_file() {
  local source_file="$1"
  local target_file="$2"
  local source_json_file
  local target_json_file
  local merged_json_file

  if [[ ! -f "${target_file}" ]]; then
    cp "${source_file}" "${target_file}"
    upgrade_append_log "compose merge initialized ${target_file} from source package"
    upgrade_append_summary "- compose initialized: $(basename "${target_file}")"
    return 0
  fi

  upgrade_backup_target_file "$(dirname "${target_file}")/.." "${target_file}"
  source_json_file="$(mktemp)"
  target_json_file="$(mktemp)"
  merged_json_file="$(mktemp)"

  upgrade_compose_config_json "${source_file}" > "${source_json_file}"
  upgrade_compose_config_json "${target_file}" > "${target_json_file}"

  python3 - <<'PY' "${source_json_file}" "${target_json_file}" "${merged_json_file}"
import json
import sys

new_path, old_path, out_path = sys.argv[1:4]
with open(new_path, "r", encoding="utf-8") as fh:
    new = json.load(fh)
with open(old_path, "r", encoding="utf-8") as fh:
    old = json.load(fh)

merged = dict(new)

services = dict(new.get("services", {}))
for service_name, old_service in old.get("services", {}).items():
    if service_name not in services:
        services[service_name] = old_service
        continue
    merged_service = dict(services[service_name])
    for key in (
        "environment",
        "ports",
        "volumes",
        "extra_hosts",
        "hostname",
        "container_name",
        "labels",
        "networks",
    ):
        if key in old_service:
            merged_service[key] = old_service[key]
    services[service_name] = merged_service
if services:
    merged["services"] = services

for top_level_key in ("volumes", "networks", "configs", "secrets"):
    combined = dict(new.get(top_level_key, {}))
    for item_name, item_value in old.get(top_level_key, {}).items():
        if item_name not in combined:
            combined[item_name] = item_value
    if combined:
        merged[top_level_key] = combined

with open(out_path, "w", encoding="utf-8") as fh:
    json.dump(merged, fh, ensure_ascii=False, indent=2, sort_keys=True)
    fh.write("\n")
PY

  mv "${merged_json_file}" "${target_file}"
  rm -f "${source_json_file}" "${target_json_file}"

  upgrade_append_log "compose merge updated ${target_file}"
  upgrade_append_summary "- compose merged: $(basename "${target_file}")"
}

upgrade_merge_compose_files() {
  local source_root="$1"
  local target_dir="$2"
  local source_file
  local file_name

  while IFS= read -r source_file; do
    [[ -z "${source_file}" ]] && continue
    file_name="$(basename "${source_file}")"
    if ! upgrade_should_merge_compose_file "${file_name}"; then
      continue
    fi
    upgrade_merge_compose_file "${source_file}" "${target_dir}/${file_name}"
  done < <(upgrade_compose_files_from_source "${source_root}")
}

upgrade_backup_conflict_file() {
  local target_dir="$1"
  local timestamp="$2"
  local relative_path="$3"
  local source_file="$4"
  local backup_root="${target_dir}/backups/upgrade-${timestamp}/config-conflicts"
  local backup_file="${backup_root}/${relative_path}.new"

  mkdir -p "$(dirname "${backup_file}")"
  cp "${source_file}" "${backup_file}"
  upgrade_append_log "config backup saved ${backup_file}"
  upgrade_append_summary "- config conflict backup: ${relative_path}.new"
}

upgrade_merge_properties_file() {
  local source_file="$1"
  local target_file="$2"
  local tmp_file="${target_file}.upgrade-tmp"
  local line key
  declare -A target_keys=()

  cp "${target_file}" "${tmp_file}"

  while IFS= read -r line || [[ -n "${line}" ]]; do
    if [[ "${line}" =~ ^[[:space:]]*([A-Za-z0-9_.-]+)[[:space:]]*[:=] ]]; then
      target_keys["${BASH_REMATCH[1]}"]=1
    fi
  done < "${target_file}"

  while IFS= read -r line || [[ -n "${line}" ]]; do
    if [[ "${line}" =~ ^[[:space:]]*([A-Za-z0-9_.-]+)[[:space:]]*[:=] ]]; then
      key="${BASH_REMATCH[1]}"
      if [[ -z "${target_keys[${key}]:-}" ]]; then
        printf '%s\n' "${line}" >> "${tmp_file}"
        target_keys["${key}"]=1
      fi
    fi
  done < "${source_file}"

  mv "${tmp_file}" "${target_file}"
}

upgrade_merge_json_file() {
  local source_file="$1"
  local target_file="$2"

  python3 - <<'PY' "${source_file}" "${target_file}"
import json
import sys

source_path, target_path = sys.argv[1:3]

with open(source_path, "r", encoding="utf-8") as fh:
    source = json.load(fh)
with open(target_path, "r", encoding="utf-8") as fh:
    target = json.load(fh)

def merge(old, new):
    if isinstance(old, dict) and isinstance(new, dict):
        merged = {}
        for key, value in old.items():
            if key in new:
                merged[key] = merge(value, new[key])
            else:
                merged[key] = value
        for key, value in new.items():
            if key not in merged:
                merged[key] = value
        return merged
    return old

with open(target_path, "w", encoding="utf-8") as fh:
    json.dump(merge(target, source), fh, ensure_ascii=False, indent=2, sort_keys=True)
    fh.write("\n")
PY
}

upgrade_merge_simple_yaml_file() {
  local source_file="$1"
  local target_file="$2"

  python3 - <<'PY' "${source_file}" "${target_file}"
import sys

source_path, target_path = sys.argv[1:3]

def parse_simple_yaml(path):
    root = {}
    stack = [(-1, root)]
    with open(path, "r", encoding="utf-8") as fh:
        for raw_line in fh:
            if not raw_line.strip() or raw_line.lstrip().startswith("#"):
                continue
            indent = len(raw_line) - len(raw_line.lstrip(" "))
            stripped = raw_line.strip()
            if ":" not in stripped:
                raise ValueError(f"unsupported yaml line: {raw_line.rstrip()}")
            key, value = stripped.split(":", 1)
            key = key.strip()
            value = value.strip()
            while indent <= stack[-1][0]:
                stack.pop()
            parent = stack[-1][1]
            if value == "":
                node = {}
                parent[key] = node
                stack.append((indent, node))
            else:
                parent[key] = value
    return root

def merge(old, new):
    if isinstance(old, dict) and isinstance(new, dict):
      merged = {}
      for key, value in old.items():
          if key in new:
              merged[key] = merge(value, new[key])
          else:
              merged[key] = value
      for key, value in new.items():
          if key not in merged:
              merged[key] = value
      return merged
    return old

def dump_yaml(node, indent=0):
    lines = []
    for key, value in node.items():
        prefix = " " * indent
        if isinstance(value, dict):
            lines.append(f"{prefix}{key}:")
            lines.extend(dump_yaml(value, indent + 2))
        else:
            lines.append(f"{prefix}{key}: {value}")
    return lines

source = parse_simple_yaml(source_path)
target = parse_simple_yaml(target_path)
merged = merge(target, source)

with open(target_path, "w", encoding="utf-8") as fh:
    fh.write("\n".join(dump_yaml(merged)))
    fh.write("\n")
PY
}

upgrade_merge_config_tree() {
  local source_root="$1"
  local target_dir="$2"
  local timestamp="$3"
  local source_config_dir="${source_root}/config"
  local source_file
  local relative_path
  local target_file
  local lower_relative

  if [[ ! -d "${source_config_dir}" ]]; then
    return 0
  fi

  while IFS= read -r source_file; do
    [[ -z "${source_file}" ]] && continue
    relative_path="${source_file#${source_config_dir}/}"
    target_file="${target_dir}/config/${relative_path}"
    lower_relative="${relative_path,,}"
    mkdir -p "$(dirname "${target_file}")"

    if [[ ! -f "${target_file}" ]]; then
      cp "${source_file}" "${target_file}"
      upgrade_append_log "config copied ${relative_path}"
      upgrade_append_summary "- config added: ${relative_path}"
      continue
    fi

    case "${lower_relative}" in
      *.properties)
        upgrade_backup_target_file "${target_dir}" "${target_file}"
        upgrade_merge_properties_file "${source_file}" "${target_file}"
        upgrade_append_summary "- config merged: ${relative_path}"
        ;;
      *.json)
        upgrade_backup_target_file "${target_dir}" "${target_file}"
        upgrade_merge_json_file "${source_file}" "${target_file}"
        upgrade_append_summary "- config merged: ${relative_path}"
        ;;
      *.yml|*.yaml)
        upgrade_backup_target_file "${target_dir}" "${target_file}"
        upgrade_merge_simple_yaml_file "${source_file}" "${target_file}"
        upgrade_append_summary "- config merged: ${relative_path}"
        ;;
      *)
        upgrade_backup_conflict_file "${target_dir}" "${timestamp}" "${relative_path}" "${source_file}"
        ;;
    esac
  done < <(find "${source_config_dir}" -type f | sort)
}

upgrade_should_sync_relative_path() {
  local relative_path="$1"

  case "${relative_path}" in
    .env|.upgrade-lock)
      return 1
      ;;
    docker-compose*.yml|docker-compose*.yaml)
      return 1
      ;;
    config/*)
      return 1
      ;;
    services/dts-pg/data|services/dts-pg/data/*)
      return 1
      ;;
    logs/*|backups/*)
      return 1
      ;;
    .git/*|.gitignore)
      return 1
      ;;
    *)
      return 0
      ;;
  esac
}

upgrade_sync_new_files() {
  local source_root="$1"
  local target_dir="$2"
  local source_file
  local relative_path
  local target_file
  local synced_count=0
  local updated_count=0
  local added_count=0

  while IFS= read -r source_file; do
    [[ -z "${source_file}" ]] && continue
    relative_path="${source_file#${source_root}/}"
    [[ "${relative_path}" != "${source_file}" ]] || continue

    if ! upgrade_should_sync_relative_path "${relative_path}"; then
      continue
    fi

    target_file="${target_dir}/${relative_path}"
    if [[ -d "${target_file}" ]]; then
      upgrade_die "cannot sync package file over existing directory: ${relative_path}"
    fi

    mkdir -p "$(dirname "${target_file}")"
    if [[ -f "${target_file}" ]]; then
      upgrade_backup_target_file "${target_dir}" "${target_file}"
      updated_count=$((updated_count + 1))
    else
      added_count=$((added_count + 1))
    fi
    cp -a "${source_file}" "${target_file}"
    synced_count=$((synced_count + 1))
    upgrade_append_log "synced runtime file ${relative_path}"
  done < <(find "${source_root}" -type f | sort)

  upgrade_append_summary "- runtime files synced: total=${synced_count}, updated=${updated_count}, added=${added_count}"
}
