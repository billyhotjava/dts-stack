#!/usr/bin/env bash

# Shared by both offline upgraders; no Python or network access required.
dts_managed_runtime_file() {
  case "$1" in
    */data/*|*/config/*|*/profiles/*|*/tests/*|*/__pycache__/*|*.pyc) return 1 ;;
    bin/*|init.sh|start.sh|stop.sh|imgversion*.conf) return 0 ;;
    services/dts-airflow/extra/*.py|services/dts-airflow/extra/*.sh|services/dts-airflow/extra/*.j2|services/dts-airflow/extra/*.html|services/dts-airflow/plugins/*.py) return 0 ;;
    services/dts-airflow/dags/dts_release_build_*.py) return 0 ;;
    services/dts-dbt/*.sh|services/dts-dbt/macros/*.sql) return 0 ;;
    services/dts-dbt/dbt_project.yml|services/dts-dbt/dbt_model/models/*) return 0 ;;
    *) return 1 ;;
  esac
}

dts_safe_relative_path() {
  [[ -n "$1" && "$1" != /* && "$1" != *$'\n'* && "$1" != *$'\r'* && "$1" != *\\* ]] || return 1
  case "/$1/" in */../*|*/./*|*//*) return 1 ;; esac
}

dts_no_symlink_path() {
  local rel="$2" part current="$1"
  local -a parts=()
  dts_safe_relative_path "${rel}" || return 1
  IFS=/ read -r -a parts <<< "${rel}"
  for part in "${parts[@]}"; do
    current="${current}/${part}"
    [[ ! -L "${current}" ]] || return 1
  done
}

dts_package_requires_files() {
  [[ -f "$1/release-manifest.json" ]] &&
    grep -Eq '"runtimeFilesRequired"[[:space:]]*:[[:space:]]*true' "$1/release-manifest.json"
}

dts_verify_package_files() {
  local source_root="${1%/}" extra_dir="${2%/}" list="${2%/}/files-checksums.txt"
  local line digest rel file actual count=0
  local -A verified=()
  if [[ ! -f "${list}" ]]; then
    if dts_package_requires_files "${extra_dir}"; then
      echo "Missing required files-checksums.txt" >&2
      return 1
    fi
    echo "[dts-upgrade] legacy package: runtime file checksums unavailable" >&2
    return 0
  fi
  [[ -s "${list}" ]] || return 1
  while IFS= read -r line || [[ -n "${line}" ]]; do
    [[ "${line}" =~ ^([[:xdigit:]]{64})\ \ (.+)$ ]] || return 1
    digest="${BASH_REMATCH[1]}"; rel="${BASH_REMATCH[2]}"
    [[ -z "${verified[${rel}]:-}" ]] || return 1
    dts_safe_relative_path "${rel}" || return 1
    case "${rel}" in
      dts-stack/*)
        dts_no_symlink_path "${source_root}" "${rel#dts-stack/}" || return 1
        file="${source_root}/${rel#dts-stack/}" ;;
      extra/*|misc/*)
        dts_no_symlink_path "${extra_dir}" "${rel#*/}" || return 1
        file="${extra_dir}/${rel#*/}" ;;
      *) return 1 ;;
    esac
    [[ -f "${file}" ]] || { echo "Package file missing: ${rel}" >&2; return 1; }
    actual="$(sha256sum -- "${file}")" || return 1
    [[ "${actual%% *}" == "${digest}" ]] || { echo "Package file checksum mismatch: ${rel}" >&2; return 1; }
    verified["${rel}"]=1
    count=$((count + 1))
  done < "${list}"
  # Reject extra runtime files as well as missing/changed files in a verified package.
  while IFS= read -r -d '' file; do
    rel="dts-stack/${file#${source_root}/}"
    [[ -n "${verified[${rel}]:-}" ]] || { echo "Unlisted package file: ${rel}" >&2; return 1; }
  done < <(find "${source_root}" -type f -print0)
  [[ "${count}" -gt 0 ]]
}

dts_image_archives() {
  local line name
  [[ -s "$1" ]] || return 1
  while IFS= read -r line || [[ -n "${line}" ]]; do
    [[ "${line}" =~ ^[[:xdigit:]]{64}\ \ (.+)$ ]] || return 1
    name="${BASH_REMATCH[1]}"
    [[ "${name}" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.:+-]*\.tar$ ]] || return 1
    printf '%s\n' "${name}"
  done < "$1"
}

dts_verify_image_files() {
  local images_dir="$1" extra_dir="$2" names name
  if [[ ! -e "${extra_dir}/checksums.txt" ]]; then
    if dts_package_requires_files "${extra_dir}" &&
      grep -Eq '"imagesIncluded"[[:space:]]*:[[:space:]]*true' "${extra_dir}/release-manifest.json"; then
      echo "Missing required image checksums.txt" >&2
      return 1
    fi
    return 0
  fi
  if [[ ! -s "${extra_dir}/checksums.txt" ]] && ! dts_package_requires_files "${extra_dir}"; then
    return 0
  fi
  names="$(dts_image_archives "${extra_dir}/checksums.txt")" || return 1
  while IFS= read -r name; do
    [[ -f "${images_dir}/${name}" && ! -L "${images_dir}/${name}" ]] || return 1
  done <<< "${names}"
  local checksums_file
  checksums_file="$(cd "${extra_dir}" && pwd -P)/checksums.txt" || return 1
  (cd "${images_dir}" && sha256sum -c "${checksums_file}" >/dev/null)
}

dts_preflight_runtime_paths() {
  local source_root="${1%/}" target_dir="${2%/}" file rel
  local source_real target_real
  source_real="$(cd "${source_root}" && pwd -P)" || return 1
  target_real="$(cd "${target_dir}" && pwd -P)" || return 1
  case "${target_real}/" in "${source_real}/"*) echo "Upgrade source must be separate from target" >&2; return 1 ;; esac
  while IFS= read -r -d '' file; do
    rel="${file#${source_root}/}"
    dts_managed_runtime_file "${rel}" || continue
    dts_no_symlink_path "${target_dir}" "${rel}" || { echo "Unsafe runtime target: ${rel}" >&2; return 1; }
    [[ ! -d "${target_dir}/${rel}" ]] || return 1
  done < <(find "${source_root}" -type f -print0)
}
