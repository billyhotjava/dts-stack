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

upgrade_init_logs() {
  local target_dir="$1"
  local timestamp="$2"

  mkdir -p "${target_dir}/logs"
  UPGRADE_LOG_FILE="${target_dir}/logs/upgrade-${timestamp}.log"
  UPGRADE_SUMMARY_FILE="${target_dir}/logs/upgrade-${timestamp}.summary.md"
  : > "${UPGRADE_LOG_FILE}"
  cat > "${UPGRADE_SUMMARY_FILE}" <<EOF_SUMMARY
# DTS Upgrade Summary

- timestamp: ${timestamp}
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

upgrade_init_backup_state() {
  local target_dir="$1"
  local timestamp="$2"

  UPGRADE_BACKUP_DIR="${target_dir}/backups/upgrade-${timestamp}"
  UPGRADE_ROLLBACK_FILES_LIST="${UPGRADE_BACKUP_DIR}/.rollback-files.list"
  UPGRADE_PROTECTED_DIRS_LIST="${UPGRADE_BACKUP_DIR}/.protected-dirs.list"

  mkdir -p "${UPGRADE_BACKUP_DIR}"
  : > "${UPGRADE_ROLLBACK_FILES_LIST}"
  : > "${UPGRADE_PROTECTED_DIRS_LIST}"

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
    "${backup_manifest}" \
    "${extra_manifest}"
import json
import sys

files_list, dirs_list, backup_manifest, extra_manifest = sys.argv[1:5]

def read_lines(path):
    with open(path, "r", encoding="utf-8") as fh:
        return [line.strip() for line in fh if line.strip()]

payload = {
    "backedUpFiles": read_lines(files_list),
    "protectedDataDirs": read_lines(dirs_list),
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

upgrade_start_target_stack() {
  local target_dir="$1"
  (
    cd "${target_dir}" &&
      docker compose up -d >/dev/null
  )
  upgrade_append_log "target stack started"
}

upgrade_postcheck() {
  local target_dir="$1"
  local running_services

  running_services="$(
    cd "${target_dir}" &&
      docker compose ps --status running --services 2>/dev/null || true
  )"
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

upgrade_check_containers_stopped() {
  local target_dir="$1"
  local output

  if [[ ! -f "${target_dir}/docker-compose.yml" ]]; then
    return 0
  fi

  output="$(
    cd "${target_dir}" &&
      docker compose ps --status running --services 2>/dev/null || true
  )"
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

upgrade_verify_image_package() {
  local images_dir="$1"
  local extra_dir="$2"
  local manifest_file="${extra_dir}/release-manifest.json"
  local checksums_file="${extra_dir}/checksums.txt"
  local image_name

  upgrade_require_file "${manifest_file}" "release manifest"
  upgrade_require_file "${checksums_file}" "checksums"

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
}

upgrade_compose_files_from_source() {
  local source_root="$1"
  find "${source_root}" -maxdepth 1 -type f \( -name 'docker-compose*.yml' -o -name 'docker-compose*.yaml' \) | sort
}

upgrade_compose_config_json() {
  local compose_file="$1"
  docker compose -f "${compose_file}" config --format json
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
