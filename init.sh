#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

MODE=""
SECRET=""
BASE_DOMAIN_ARG=""
LEGACY_STACK=false
RESET_PG_DATA=false
RESET_ENV=false
FORCE_PG_ENSURE=true

usage(){ echo "Usage: $0 [legacy] [--reset-pg] [--reset-env] [--no-force-pg-ensure] [app|single] [unified-password] [base-domain]"; }

looks_like_domain(){
  local candidate="${1:-}"
  [[ "$candidate" == *.* && "$candidate" =~ ^[A-Za-z0-9.-]+$ ]]
}

normalize_base_domain(){
  local candidate="${1:-}"
  candidate="${candidate#http://}"
  candidate="${candidate#https://}"
  candidate="${candidate#//}"
  candidate="${candidate%%/*}"
  candidate="${candidate#.}"
  candidate="${candidate%.}"
  candidate="$(printf '%s' "$candidate" | tr '[:upper:]' '[:lower:]')"
  printf '%s' "$candidate"
}

validate_base_domain(){
  local candidate="${1:-}"
  [[ -n "$candidate" ]] || return 1
  [[ "$candidate" =~ ^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$ ]]
}

prompt_base_domain(){
  local default_value="${1:-dts.local}"
  local input=""
  while true; do
    if ! read -rp "[init.sh] Base domain [${default_value}]: " input; then
      input="${default_value}"
    fi
    if [[ -z "$input" ]]; then
      input="${default_value}"
    fi
    input="$(normalize_base_domain "$input")"
    if validate_base_domain "$input"; then
      BASE_DOMAIN="$input"
      return
    fi
    echo "[init.sh] Invalid base domain. Use letters, digits, hyphen, and dots, and include at least one dot." >&2
  done
}

#------------ helpers ------------
pick_mode(){ echo "1) app"; read -rp "Choice: " c; case "$c" in 1|"") MODE=app;;*) exit 1;; esac; }
read_secret(){ while true; do read -rsp "Password: " p1; echo; read -rsp "Confirm: " p2; echo; [[ "$p1" == "$p2" ]] || { echo "Mismatch"; continue; }; [[ ${#p1} -ge 10 && "$p1" =~ [A-Z] && "$p1" =~ [a-z] && "$p1" =~ [0-9] && "$p1" =~ [^A-Za-z0-9] ]] || { echo "Weak"; continue; }; SECRET="$p1"; break; done; }
ensure_env(){ k="$1"; shift; v="$*"; if grep -qE "^${k}=" .env 2>/dev/null; then sed -i -E "s|^${k}=.*|${k}=${v}|g" .env; else echo "${k}=${v}" >> .env; fi; }
load_img_versions(){
  conf="imgversion.conf"
  [[ -f "$conf" ]] || return 0
  # Source into shell env (imgversion.conf overrides stale .env values and shell defaults).
  # This is critical: docker-compose prioritises shell env vars over .env file,
  # so we must update both the shell env AND the .env file.
  set -a; . "./$conf"; set +a
  # Also sync every key into .env so the file stays consistent.
  while IFS='=' read -r k v; do
    [[ -z "${k// }" || "${k#\#}" != "$k" ]] && continue
    v="$(echo "$v" | sed -E 's/^\s+|\s+$//g')"
    ensure_env "$k" "$v"
  done < <(grep -E '^[[:space:]]*[A-Z0-9_]+[[:space:]]*=' "$conf" || true)
  echo "[init.sh] loaded image versions from $conf"
}
urlencode(){
  local input="${1:-}"
  local out="" i ch hex
  for ((i=0; i<${#input}; i++)); do
    ch="${input:i:1}"
    case "${ch}" in
      [a-zA-Z0-9.~_-]) out+="${ch}" ;;
      *) printf -v hex '%02X' "'${ch}"; out+="%${hex}" ;;
    esac
  done
  printf '%s' "${out}"
}

# Try to detect a stable IPv4 address on the host for containers to reach services exposed on the host
detect_host_ipv4(){
  # 1) routing-based detection
  if command -v ip >/dev/null 2>&1; then
    local ip4
    ip4=$(ip -4 route get 1.1.1.1 2>/dev/null | awk '/src/ {for(i=1;i<=NF;i++) if($i=="src"){print $(i+1); exit}}') || true
    if [[ -n "${ip4:-}" && "${ip4}" != 127.* ]]; then
      printf '%s' "$ip4"; return 0
    fi
  fi
  # 2) hostname -I fallback
  if command -v hostname >/dev/null 2>&1; then
    local first
    first=$(hostname -I 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i ~ /^[0-9.]+$/ && $i !~ /^127\./){print $i; exit}}') || true
    if [[ -n "${first:-}" ]]; then
      printf '%s' "$first"; return 0
    fi
  fi
  # 3) ip addr scan fallback
  if command -v ip >/dev/null 2>&1; then
    local any
    any=$(ip -4 addr show scope global 2>/dev/null | awk '/ inet /{print $2}' | sed -E 's#/.*##' | head -n1) || true
    if [[ -n "${any:-}" ]]; then
      printf '%s' "$any"; return 0
    fi
  fi
  # 4) last resort: docker default gateway on many hosts
  printf '%s' "172.17.0.1"
}

# Return possible architecture aliases for docker-compose binary naming.
compose_arch_candidates(){
  local arch
  arch="$(uname -m 2>/dev/null || true)"
  case "${arch}" in
    x86_64) printf '%s\n' "x86_64" "amd64" ;;
    aarch64) printf '%s\n' "aarch64" "arm64" ;;
    arm64) printf '%s\n' "arm64" "aarch64" ;;
    *) printf '%s\n' "${arch}" ;;
  esac
}

detect_runtime_arch(){
  local arch
  arch="$(uname -m 2>/dev/null || true)"
  case "${arch}" in
    x86_64|amd64) printf '%s' "amd64" ;;
    aarch64|arm64) printf '%s' "arm64" ;;
    *) printf '%s' "${arch:-amd64}" ;;
  esac
}

detect_runtime_platform(){
  local arch
  arch="$(detect_runtime_arch)"
  printf 'linux/%s' "${arch}"
}

detect_docker_gid(){
  if [[ -S /var/run/docker.sock ]]; then
    local gid
    gid="$(stat -c '%g' /var/run/docker.sock 2>/dev/null || true)"
    if [[ -n "${gid:-}" ]]; then
      printf '%s' "$gid"
      return 0
    fi
  fi
  printf '%s' "0"
}

find_bundled_docker_compose(){
  local base_dir="$1"
  local -a names
  names=("docker-compose")
  while IFS= read -r arch; do
    [[ -n "${arch}" ]] || continue
    names+=(
      "docker-compose-${arch}"
      "docker-compose-Linux-${arch}"
      "docker-compose-linux-${arch}"
    )
  done < <(compose_arch_candidates)

  local name
  for name in "${names[@]}"; do
    if [[ -x "${base_dir}/${name}" ]]; then
      printf '%s' "${base_dir}/${name}"
      return 0
    fi
  done
  return 1
}

print_compose_info(){
  local cli_display=""
  local version_output=""
  local part

  for part in "${compose_cli[@]}"; do
    if [[ -n "${cli_display}" ]]; then cli_display+=" "; fi
    cli_display+="${part}"
  done

  version_output="$("${compose_cli[@]}" version 2>&1 | head -n 1 || true)"
  if [[ -n "${version_output}" ]]; then
    echo "[init.sh] Compose CLI: ${cli_display} (${version_output})"
  else
    echo "[init.sh] WARNING: Compose CLI: ${cli_display}; unable to read version, continuing." >&2
  fi

  if [[ "${LEGACY_STACK}" == "true" ]]; then
    echo "[init.sh] Legacy compose note: docker-compose.legacy.yml targets Compose file 2.4 and uses health/completion dependencies; version is informational only, continuing."
  fi
}

# Determine which optional services are enabled based on imgversion.conf
determine_enabled_services(){
  local conf="imgversion.conf"
  ENABLE_MINIO="false"
  ENABLE_NESSIE="false"
  if [[ -f "$conf" ]]; then
    if rg -n "^[[:space:]]*IMAGE_MINIO[[:space:]]*=" "$conf" >/dev/null 2>&1 || grep -Eq '^[[:space:]]*IMAGE_MINIO[[:space:]]*=' "$conf"; then
      ENABLE_MINIO="true"
    fi
    if rg -n "^[[:space:]]*IMAGE_NESSIE[[:space:]]*=" "$conf" >/dev/null 2>&1 || grep -Eq '^[[:space:]]*IMAGE_NESSIE[[:space:]]*=' "$conf"; then
      ENABLE_NESSIE="true"
    fi
  fi
  export ENABLE_MINIO ENABLE_NESSIE
}

# Default log root (can be overridden before running init)
set_default_log_root(){
  local default_root="${SCRIPT_DIR}/logs"
  # Ensure source/logs doesn't linger; logs live at repo root.
  if [[ -d "${SCRIPT_DIR}/source/logs" ]]; then
    rm -rf "${SCRIPT_DIR}/source/logs"
  fi
  ensure_env LOG_ROOT "${LOG_ROOT:-${default_root}}"
}

detect_docker_api_version(){
  if grep -qE "^DOCKER_API_VERSION=" .env 2>/dev/null; then
    return
  fi
  if ! command -v docker >/dev/null 2>&1; then
    echo "[init.sh] WARNING: docker not found; unable to detect Docker API version. Set DOCKER_API_VERSION in .env if Traefik reports client version too old." >&2
    return
  fi
  local api_version
  api_version=$(docker version --format '{{.Server.APIVersion}}' 2>/dev/null | head -n1 || true)
  if [[ -n "${api_version:-}" ]]; then
    ensure_env DOCKER_API_VERSION "${api_version}"
    echo "[init.sh] Detected Docker API version ${api_version} (written to .env for Traefik docker provider)."
  else
    echo "[init.sh] WARNING: Could not detect Docker API version. Set DOCKER_API_VERSION manually in .env if you see 'client version ... too old' errors." >&2
  fi
}

fix_pg_permissions(){
  if [[ "${PG_MODE:-}" != "embedded" ]]; then
    return
  fi
  local pg_dir="services/dts-pg/data"
  mkdir -p "${pg_dir}"
  local pg_runtime_uid="${PG_RUNTIME_UID:-999}"
  local pg_runtime_gid="${PG_RUNTIME_GID:-${pg_runtime_uid}}"
  chown -R "${pg_runtime_uid}:${pg_runtime_gid}" "${pg_dir}" 2>/dev/null || true
  # Postgres requires 0700 or 0750. Use 0700 to avoid startup failures.
  chmod -R 700 "${pg_dir}" 2>/dev/null || true
  if command -v stat >/dev/null 2>&1; then
    local perms
    perms="$(stat -c '%a' "${pg_dir}" 2>/dev/null || true)"
    if [[ "${perms}" != "700" && "${perms}" != "750" ]]; then
      echo "[init.sh] WARNING: Postgres data dir permissions are ${perms}; forcing 0700." >&2
      chmod -R 700 "${pg_dir}" 2>/dev/null || true
    fi
    local owner_uid owner_gid
    owner_uid="$(stat -c '%u' "${pg_dir}" 2>/dev/null || true)"
    owner_gid="$(stat -c '%g' "${pg_dir}" 2>/dev/null || true)"
    if [[ -n "${owner_uid}" && "${owner_uid}" != "${pg_runtime_uid}" ]]; then
      echo "[init.sh] WARNING: Postgres data dir owner is ${owner_uid}:${owner_gid}; expected ${pg_runtime_uid}:${pg_runtime_gid}." >&2
      if command -v docker >/dev/null 2>&1; then
        local pg_image="${IMAGE_POSTGRES:-postgres:17.6}"
        if docker image inspect "${pg_image}" >/dev/null 2>&1; then
          echo "[init.sh] Fixing Postgres data dir ownership via Docker (${pg_image})..."
          docker run --rm -v "${SCRIPT_DIR}/${pg_dir}:/data" "${pg_image}" \
            bash -lc "chown -R ${pg_runtime_uid}:${pg_runtime_gid} /data && chmod -R 700 /data" >/dev/null 2>&1 || true
        else
          echo "[init.sh] WARNING: Docker image ${pg_image} not found; unable to auto-fix ownership." >&2
        fi
      else
        echo "[init.sh] WARNING: docker not available to fix Postgres data dir ownership." >&2
      fi
    fi
  fi
}

reset_pg_data_dir(){
  if [[ "${PG_MODE:-}" != "embedded" ]]; then
    return
  fi
  local pg_dir="services/dts-pg/data"
  if command -v docker >/dev/null 2>&1; then
    local cid
    cid="$(docker ps -q --filter "name=dts-pg" 2>/dev/null | head -n 1 || true)"
    if [[ -n "${cid:-}" ]]; then
      echo "[init.sh] Stopping running dts-pg container for reset."
      docker stop "${cid}" >/dev/null 2>&1 || true
    fi
  fi
  rm -rf "${pg_dir}"
  mkdir -p "${pg_dir}"
  if [[ -n "$(ls -A "${pg_dir}" 2>/dev/null)" ]]; then
    echo "[init.sh] ERROR: Postgres data dir not empty after reset: ${pg_dir}" >&2
    echo "[init.sh] ERROR: Remove it manually and re-run init.sh --reset-pg." >&2
    exit 1
  fi
  fix_pg_permissions
}

# Ensure /docker-entrypoint-initdb.d contents are world-readable and shell scripts executable
# This avoids 'permission denied' when the Postgres container (user 'postgres') reads host-mounted init files.
fix_pg_initdir_permissions(){
  local init_dir="services/dts-pg/init"
  if [[ -d "${init_dir}" ]]; then
    chmod -R a+rX "${init_dir}" 2>/dev/null || true
    find "${init_dir}" -type f -name '*.sh' -exec chmod 755 {} + 2>/dev/null || true
  fi
}

warn_if_ima_appraise(){
  local ima_policy="/sys/kernel/security/ima/policy"
  if [[ -r "${ima_policy}" ]]; then
    if grep -qi 'appraise' "${ima_policy}"; then
      cat <<'EOF' >&2
[init.sh] WARNING: Detected host IMA appraisal policy. Linux kernels configured
[init.sh] WARNING: with ima_appraise enforce signature checks on every binary
[init.sh] WARNING: (func=BPRM_CHECK). Containers may fail to start Postgres with
[init.sh] WARNING: 'could not execute "/usr/lib/postgresql/17/bin/postgres" -V: Operation not permitted'.
[init.sh] WARNING: Disable ima_appraise (e.g. boot with ima_appraise=off) or move Docker data
[init.sh] WARNING: to a filesystem mounted without appraisal before continuing.
EOF
    fi
  fi
}

prepare_data_dirs(){
  fix_pg_permissions
  fix_pg_initdir_permissions
  local -a data_dirs=(
    "services/certs"
    "services/dts-ranger"
    "services/dts-analytics/data"
    "services/dts-analytics/plugins"
    "services/dts-airflow/plugins"
    "services/dts-openmetadata/ingestion"
    "services/dts-airflow/extra"
    "services/dts-airflow/dags"
    "services/dts-airflow/dags/ods"
    "logs/airflow"
    "logs/airflow/scheduler"
    "logs/addax"
    "logs/dbt"
    "logs/openmetadata"
    "logs/keycloak"
    "logs/elasticsearch"
    "logs/postgresql"
    "logs/traefik"
  )
  if [[ "${DTS_LEGACY_METRICS_ENABLED:-false}" == "true" || "${DTS_LEGACY_METRICS_ENABLED:-false}" == "1" ]]; then
    data_dirs+=("logs/dts-metrics")
  fi
  if [[ "${ENABLE_MINIO:-false}" == "true" ]]; then
    data_dirs+=("services/dts-minio/data")
  fi
  local dir
  for dir in "${data_dirs[@]}"; do
    mkdir -p "${dir}"
  done
  if [[ "${ENABLE_MINIO:-false}" == "true" ]]; then
    chmod -R 777 services/dts-minio/data 2>/dev/null || true
  fi
  if [[ -d "logs/airflow" ]]; then
    chmod -R 777 logs/airflow 2>/dev/null || true
    if command -v getenforce >/dev/null 2>&1; then
      if [[ "$(getenforce 2>/dev/null || true)" != "Disabled" ]]; then
        if command -v chcon >/dev/null 2>&1; then
          chcon -Rt svirt_sandbox_file_t logs/airflow 2>/dev/null || true
        fi
      fi
    fi
  fi
  local -a log_dirs=(
    "logs/addax"
    "logs/dbt"
    "logs/openmetadata"
    "logs/keycloak"
    "logs/elasticsearch"
    "logs/postgresql"
    "logs/traefik"
    "logs/dts-admin"
    "logs/dts-platform"
    "logs/dts-ingestion"
    "logs/dts-analytics"
  )
  if [[ "${DTS_LEGACY_METRICS_ENABLED:-false}" == "true" || "${DTS_LEGACY_METRICS_ENABLED:-false}" == "1" ]]; then
    log_dirs+=("logs/dts-metrics")
  fi
  for dir in "${log_dirs[@]}"; do
    if [[ -d "${dir}" ]]; then
      chmod -R 777 "${dir}" 2>/dev/null || true
      if command -v getenforce >/dev/null 2>&1; then
        if [[ "$(getenforce 2>/dev/null || true)" != "Disabled" ]]; then
          if command -v chcon >/dev/null 2>&1; then
            chcon -Rt svirt_sandbox_file_t "${dir}" 2>/dev/null || true
          fi
        fi
      fi
    fi
  done
  if [[ -d "services/dts-airflow/dags" ]]; then
    chmod -R 777 services/dts-airflow/dags 2>/dev/null || true
    # Keep Airflow focused on Python DAG files; Addax job/data artifacts live in the
    # same directory and can significantly delay DAG discovery if not ignored.
    printf '%s\n' \
      '^.*/__pycache__/.*$' \
      '^.*\.pyc$' \
      '^.*\.json$' \
      '^.*\.csv$' \
      '^.*\.tsv$' \
      '^.*\.xlsx$' \
      '^.*\.xls$' \
      '^.*\.parquet$' \
      '^.*/exchange/.*$' \
      > services/dts-airflow/dags/.airflowignore 2>/dev/null || true
    chmod 644 services/dts-airflow/dags/.airflowignore 2>/dev/null || true
    if command -v getenforce >/dev/null 2>&1; then
      if [[ "$(getenforce 2>/dev/null || true)" != "Disabled" ]]; then
        if command -v chcon >/dev/null 2>&1; then
          chcon -Rt svirt_sandbox_file_t services/dts-airflow/dags 2>/dev/null || true
        fi
      fi
    fi
  fi
  # Ensure Airflow plugins are always readable in legacy/non-legacy containers.
  if [[ -d "services/dts-airflow/plugins" ]]; then
    chmod 755 services/dts-airflow/plugins 2>/dev/null || true
    chmod -R a+rX services/dts-airflow/plugins 2>/dev/null || true
    find services/dts-airflow/plugins -type f -name "*.py" -exec chmod 644 {} + 2>/dev/null || true
    if command -v getenforce >/dev/null 2>&1; then
      if [[ "$(getenforce 2>/dev/null || true)" != "Disabled" ]]; then
        if command -v chcon >/dev/null 2>&1; then
          chcon -Rt svirt_sandbox_file_t services/dts-airflow/plugins 2>/dev/null || true
        fi
      fi
    fi
  fi
  # Ensure JDBC driver jars are readable by non-root container users (e.g. airflow).
  if [[ -d "services/dts-platform/drivers" ]]; then
    chmod 755 services/dts-platform/drivers 2>/dev/null || true
    chmod -R a+rX services/dts-platform/drivers 2>/dev/null || true
    if command -v getenforce >/dev/null 2>&1; then
      if [[ "$(getenforce 2>/dev/null || true)" != "Disabled" ]]; then
        if command -v chcon >/dev/null 2>&1; then
          chcon -Rt svirt_sandbox_file_t services/dts-platform/drivers 2>/dev/null || true
        fi
      fi
    fi
  fi
  # Ensure ingestion scripts are readable inside containers (SELinux-safe when possible).
  if [[ -d "services/dts-openmetadata/ingestion" ]]; then
    chmod -R a+rX services/dts-openmetadata/ingestion || true
    if command -v getenforce >/dev/null 2>&1; then
      if [[ "$(getenforce 2>/dev/null || true)" != "Disabled" ]]; then
        if command -v chcon >/dev/null 2>&1; then
          chcon -Rt svirt_sandbox_file_t services/dts-openmetadata/ingestion 2>/dev/null || true
        fi
      fi
    fi
  fi
}

prepare_dbt_runtime_profile_root(){
  local helper="${SCRIPT_DIR}/services/dts-platform/prepare-dbt-runtime-profile-root.sh"
  if [[ "${LEGACY_STACK}" == "true" ]]; then
    echo "[init.sh] Preparing dbt runtime profile root via privileged Docker preflight (legacy mode)..."
    docker run --rm \
      --network none \
      --read-only \
      --user 0:0 \
      --cap-drop ALL \
      --cap-add CHOWN \
      --cap-add FOWNER \
      --security-opt label=disable \
      --security-opt no-new-privileges \
      -e "DTS_DBT_RUNTIME_PROFILE_EXPECTED_UID=${DTS_DBT_RUNTIME_PROFILE_EXPECTED_UID}" \
      -e "DTS_DBT_RUNTIME_PROFILE_REPAIR_DOCKER_CREATED_ROOT=true" \
      -e "DTS_DBT_RUNTIME_PROFILE_ROOT=/host-dev-shm/dts-dbt-runtime" \
      -v "/dev/shm:/host-dev-shm" \
      -v "${helper}:/opt/dts/bin/prepare-dbt-runtime-profile-root.sh:ro" \
      --entrypoint /bin/sh \
      "${IMAGE_DTS_PLATFORM}" \
      /opt/dts/bin/prepare-dbt-runtime-profile-root.sh
    return
  fi

  echo "[init.sh] Preparing dbt runtime profile root via privileged Compose preflight..."
  "${compose_run[@]}" run --rm --no-deps dts-dbt-runtime-init
}

ensure_airflow_openmetadata_plugin() {
  local plugin_dir="services/dts-airflow/extra/openmetadata_managed_apis"
  local metadata_dir="services/dts-airflow/extra/metadata"
  if [[ -d "${plugin_dir}" && -d "${metadata_dir}" ]]; then
    return
  fi
  if ! command -v docker >/dev/null 2>&1; then
    return
  fi
  local img="${IMAGE_OPENMETADATA_INGESTION:-openmetadata/ingestion:1.11.5}"
  local cid=""
  cid="$(docker create "${img}" 2>/dev/null || true)"
  if [[ -z "${cid}" ]]; then
    echo "[init.sh] WARNING: cannot create container from ${img} to copy OpenMetadata Airflow plugin." >&2
    return
  fi
  mkdir -p "services/dts-airflow/plugins"
  local ok_plugin="false"
  local ok_metadata="false"
  if [[ ! -d "${plugin_dir}" ]]; then
    if docker cp "${cid}:/home/airflow/.local/lib/python3.10/site-packages/openmetadata_managed_apis" \
      "${plugin_dir}" 2>/dev/null; then
      ok_plugin="true"
    fi
  else
    ok_plugin="true"
  fi
  if [[ ! -d "${metadata_dir}" ]]; then
    if docker cp "${cid}:/home/airflow/.local/lib/python3.10/site-packages/metadata" \
      "${metadata_dir}" 2>/dev/null; then
      ok_metadata="true"
    fi
  else
    ok_metadata="true"
  fi
  if [[ -d "services/dts-airflow/plugins/metadata" ]]; then
    rm -rf "services/dts-airflow/plugins/metadata"
  fi
  if [[ -d "services/dts-airflow/plugins/openmetadata_managed_apis" ]]; then
    rm -rf "services/dts-airflow/plugins/openmetadata_managed_apis"
  fi
  if [[ "${ok_plugin}" == "true" && "${ok_metadata}" == "true" ]]; then
    echo "[init.sh] Copied OpenMetadata Airflow plugin dependencies from ${img}"
  else
    echo "[init.sh] WARNING: failed to copy OpenMetadata Airflow plugin dependencies from ${img}" >&2
  fi
  docker rm -f "${cid}" >/dev/null 2>&1 || true
}

# Ensure embedded Postgres has all required roles/databases
ensure_pg_triplets(){
  if [[ "${PG_MODE}" != "embedded" ]]; then
    return
  fi
  echo "[init.sh] Ensuring Postgres roles/databases (idempotent)..."
  local i cid
  local exports=""

  # Inject PG_* triplets explicitly when running ensure scripts.
  # Avoid relying on `docker exec -e` because some Docker/legacy environments don't support it.
  quote_sh() {
    # Single-quote for safe embedding in a shell command: foo'bar -> 'foo'"'"'bar'
    printf "'%s'" "$(printf '%s' "${1:-}" | sed "s/'/'\\\"'\\\"'/g")"
  }

  cid="$("${compose_run[@]}" ps -q dts-pg 2>/dev/null | head -n 1 || true)"
  if [[ -z "${cid:-}" ]] && command -v docker >/dev/null 2>&1; then
    cid="$(docker ps --filter "name=dts-pg" -q 2>/dev/null | head -n 1 || true)"
  fi

  if [[ -f .env ]]; then
    while IFS='=' read -r k v; do
      case "${k}" in
        PG_DB_*|PG_USER_*|PG_PWD_*)
          exports+="export ${k}=$(quote_sh "${v}");"
          ;;
      esac
    done < <(grep -E '^(PG_DB_|PG_USER_|PG_PWD_)' .env || true)
  else
    while IFS='=' read -r k v; do
      case "${k}" in
        PG_DB_*|PG_USER_*|PG_PWD_*)
          exports+="export ${k}=$(quote_sh "${v}");"
          ;;
      esac
    done < <(env)
  fi

  local ensured=0
  for i in {1..5}; do
    if [[ -n "${cid:-}" ]] && command -v docker >/dev/null 2>&1; then
      if docker exec -i "${cid}" bash -lc "${exports} bash /docker-entrypoint-initdb.d/99-ensure-users-runtime.sh" >/dev/null; then
        echo "[init.sh] Postgres roles/databases ensured."
        ensured=1
        break
      fi
    else
      if "${compose_run[@]}" exec -T dts-pg bash -lc "${exports} bash /docker-entrypoint-initdb.d/99-ensure-users-runtime.sh" >/dev/null; then
        echo "[init.sh] Postgres roles/databases ensured."
        ensured=1
        break
      fi
    fi

    echo "[init.sh] Waiting for dts-pg to accept ensure script... (${i}/5)" >&2
    sleep 2
  done
  if [[ "${ensured}" != "1" ]]; then
    echo "[init.sh] WARNING: Failed to run Postgres ensure script; roles/databases may be missing." >&2
    echo "[init.sh] WARNING: Try: docker exec -it ${cid:-<dts-pg-cid>} bash -lc 'bash /docker-entrypoint-initdb.d/99-ensure-users-runtime.sh'" >&2
  fi

  # Optional sanity check: verify analytics DB connectivity (helps catch "db not created" and env injection issues)
  if [[ -n "${cid:-}" ]] && command -v docker >/dev/null 2>&1; then
    if ! docker exec -i "${cid}" bash -lc "${exports} PGPASSWORD=\"\${PG_PWD_ANALYTICS:-}\" psql -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U \"\${PG_USER_ANALYTICS:-}\" -d \"\${PG_DB_ANALYTICS:-}\" -c \"SELECT 1\" >/dev/null" 2>/dev/null; then
      local u db pwd_len
      u="${PG_USER_ANALYTICS:-}"
      db="${PG_DB_ANALYTICS:-}"
      pwd_len="${#PG_PWD_ANALYTICS}"
      if [[ -f .env ]]; then
        [[ -z "${u}" ]] && u="$(grep '^PG_USER_ANALYTICS=' .env | head -n 1 | cut -d= -f2- || true)"
        [[ -z "${db}" ]] && db="$(grep '^PG_DB_ANALYTICS=' .env | head -n 1 | cut -d= -f2- || true)"
        if [[ "${pwd_len}" == "0" ]]; then
          pwd_len="$(grep '^PG_PWD_ANALYTICS=' .env | head -n 1 | cut -d= -f2- | wc -c | tr -d ' ' || true)"
        fi
      fi
      echo "[init.sh] WARNING: Analytics DB connectivity check failed (user='${u}', db='${db}', pwd_len=${pwd_len})." >&2
      echo "[init.sh] WARNING: This usually means PG_DB/PG_USER/PG_PWD_ANALYTICS did not reach the dts-pg container or the DB wasn't created." >&2
      echo "[init.sh] WARNING: You can inspect inside the container with: docker exec -it ${cid} env | egrep 'PG_DB_ANALYTICS|PG_USER_ANALYTICS|PG_PWD_ANALYTICS'" >&2
      echo "[init.sh] WARNING: And re-run ensure with: docker exec -it ${cid} bash -lc 'bash /docker-entrypoint-initdb.d/99-ensure-users-runtime.sh'" >&2
    fi
  fi
}

generate_fernet(){
  # We generate a Fernet-compatible secret (URL-safe base64 for 32 random bytes).
  # Do NOT depend on python 'cryptography' module, because many legacy hosts don't have it.
  local key=""

  if command -v python >/dev/null 2>&1; then
    key="$(python - <<'PY' 2>/dev/null || true
import base64, os
print(base64.urlsafe_b64encode(os.urandom(32)).decode("ascii"))
PY
)"
  fi

  if [[ -z "${key}" ]] && command -v openssl >/dev/null 2>&1; then
    key="$(openssl rand -base64 32 2>/dev/null | tr -d '\n' | tr '+/' '-_' || true)"
  fi

  if [[ -z "${key}" ]]; then
    key="$(head -c 32 /dev/urandom | base64 | tr -d '\n' | tr '+/' '-_' || true)"
  fi

  printf '%s' "${key}"
}

ensure_distinct_service_tokens(){
  # Each authenticated producer gets its own credential. If an older .env reused the
  # installation password (or another service token), rotate only the duplicate entry.
  local -a token_names=(
    DTS_PLATFORM_TO_ADMIN_TOKEN
    DTS_PLATFORM_TO_INGESTION_TOKEN
    DTS_AIRFLOW_TO_INGESTION_TOKEN
    DTS_ANALYTICS_TO_ADMIN_TOKEN
    DTS_INGESTION_TO_ADMIN_TOKEN
    DTS_INBOUND_FROM_INGESTION
    DTS_INBOUND_FROM_ANALYTICS
    DTS_INBOUND_FROM_AIRFLOW
  )
  local -a seen_names=()
  local -a seen_values=()
  local name value replacement duplicate index legacy_shared
  if [[ "${DTS_SERVICE_TOKEN_VERSION:-1}" != "2" ]]; then
    for name in "${token_names[@]}"; do
      replacement="$(generate_fernet)"
      [[ -n "${replacement}" ]] || { echo "[init.sh] ERROR: unable to rotate ${name}." >&2; return 1; }
      printf -v "${name}" '%s' "${replacement}"
    done
    DTS_SERVICE_TOKEN_VERSION=2
    echo "[init.sh] Rotated all service credentials to pairwise credential version 2." >&2
  fi
  for name in "${token_names[@]}"; do
    value="${!name:-}"
    if [[ -z "${value}" ]]; then
      replacement="$(generate_fernet)"
      [[ -n "${replacement}" ]] || { echo "[init.sh] ERROR: unable to generate ${name}." >&2; return 1; }
      printf -v "${name}" '%s' "${replacement}"
      value="${replacement}"
    fi
    for legacy_shared in "${SECRET:-}" "${DTS_ADMIN_SERVICE_TOKEN:-}"; do
      if [[ -n "${legacy_shared}" && "${value}" == "${legacy_shared}" ]]; then
        replacement="$(generate_fernet)"
        [[ -n "${replacement}" ]] || { echo "[init.sh] ERROR: unable to rotate legacy-derived ${name}." >&2; return 1; }
        printf -v "${name}" '%s' "${replacement}"
        value="${replacement}"
        echo "[init.sh] Rotated legacy-derived service credential ${name}." >&2
        break
      fi
    done
    duplicate=""
    for ((index=0; index<${#seen_values[@]}; index++)); do
      if [[ "${value}" == "${seen_values[index]}" ]]; then
        duplicate="${seen_names[index]}"
        break
      fi
    done
    if [[ -n "${duplicate}" ]]; then
      replacement="$(generate_fernet)"
      while [[ -z "${replacement}" || " ${seen_values[*]} " == *" ${replacement} "* ]]; do
        replacement="$(generate_fernet)"
      done
      printf -v "${name}" '%s' "${replacement}"
      value="${replacement}"
      echo "[init.sh] Rotated duplicate service credential ${name}; it must not share identity with ${duplicate}." >&2
    fi
    seen_names+=("${name}")
    seen_values+=("${value}")
  done
}

generate_aes_key(){
  # Java-side InfraSettingsCryptoService expects STANDARD Base64 (with + and /), not URL-safe variants.
  local key=""

  if command -v openssl >/dev/null 2>&1; then
    key="$(openssl rand -base64 32 2>/dev/null | tr -d '\n\r' || true)"
  fi

  if [[ -z "${key}" ]] && command -v python3 >/dev/null 2>&1; then
    key="$(python3 - <<'PY' 2>/dev/null || true
import base64, os
print(base64.b64encode(os.urandom(32)).decode("ascii"), end="")
PY
)"
  fi

  if [[ -z "${key}" ]] && command -v python >/dev/null 2>&1; then
    key="$(python - <<'PY' 2>/dev/null || true
import base64, os
print(base64.b64encode(os.urandom(32)).decode("ascii"), end="")
PY
)"
  fi

  if [[ -z "${key}" ]]; then
    key="$(head -c 32 /dev/urandom | base64 | tr -d '\n\r' || true)"
  fi

  if [[ -z "${key}" ]]; then
    echo "[init.sh] ERROR: Failed to generate a standard base64 AES key." >&2
    exit 1
  fi

  printf '%s' "${key}"
}

# URL-encode a single component for safe embedding in URIs
urlencode_component(){
  local s="${1:-}"
  local i c out="" hex
  # Treat bytes, not locale-specific multibyte; acceptable for ASCII passwords
  LC_ALL=C
  for ((i=0; i<${#s}; i++)); do
    c="${s:i:1}"
    case "$c" in
      [a-zA-Z0-9._~-]) out+="$c" ;;
      *) printf -v hex '%02X' "'$c"; out+="%${hex}" ;;
    esac
  done
  printf '%s' "$out"
}

generate_env_base(){
  local env_tmp=""
  if [ -f .env ]; then
    chmod 600 .env || { echo "[init.sh] ERROR: cannot secure existing .env permissions." >&2; return 1; }
    # Source .env safely: export line-by-line to handle values with spaces
    # (plain `. ./.env` would treat "VAR=a b" as "set VAR=a then run b")
    while IFS= read -r _env_line || [[ -n "$_env_line" ]]; do
      # skip comments and blank lines
      [[ -z "$_env_line" || "$_env_line" == \#* ]] && continue
      _env_key="${_env_line%%=*}"
      _env_val="${_env_line#*=}"
      # Strip surrounding double/single quotes (Docker Compose style)
      if [[ "$_env_val" =~ ^\"(.*)\"$ ]] || [[ "$_env_val" =~ ^\'(.*)\'$ ]]; then
        _env_val="${BASH_REMATCH[1]}"
      fi
      export "${_env_key}=${_env_val}"
    done < .env
  fi

  : "${BASE_DOMAIN:=dts.local}"
  : "${TLS_PORT:=443}"

  if [[ "${MODE}" == "app" ]]; then
    : "${TRAEFIK_DASHBOARD:=true}"
  else
    : "${TRAEFIK_DASHBOARD:=false}"
  fi
  : "${TRAEFIK_DASHBOARD_PORT:=8080}"
  : "${TRAEFIK_METRICS_PORT:=9100}"
  : "${TRAEFIK_ENABLE_PING:=true}"
  : "${KAFKA_UI_BIND_HOST:=0.0.0.0}"
  : "${TRUSTSTORE_PASSWORD:=changeit}"
  : "${IMAGE_MAVEN:=maven:3.9.9-eclipse-temurin-21}"
  # Optional: image to run keytool in cert generation (offline/air-gapped)
  # Default to IMAGE_MAVEN so no new image is required offline.
  : "${KEYTOOL_IMAGE:=${IMAGE_MAVEN}}"
  : "${KEYTOOL_IMAGE_STRICT:=true}"

  # ---------- Keycloak ----------
  : "${KC_ADMIN:=admin}"
  : "${KC_ADMIN_PWD:=${SECRET}}"
  : "${KC_HTTP_ENABLED:=true}"
  : "${KC_HOSTNAME:=sso.${BASE_DOMAIN}}"
  : "${KC_HOSTNAME_PORT:=${TLS_PORT}}"
  : "${KC_HOSTNAME_STRICT:=true}"
  : "${KC_HOSTNAME_STRICT_HTTPS:=true}"
  : "${KC_HOSTNAME_URL:=https://${KC_HOSTNAME}}"
  : "${KC_HOSTNAME_ADMIN_URL:=${KC_HOSTNAME_URL}}"
  : "${KC_DB_URL_PROPERTIES:=sslmode=disable}"
  : "${KC_REALM:=S10}"

  # ---------- 域名（Traefik 路由） ----------
  HOST_SSO="sso.${BASE_DOMAIN}"
  HOST_MINIO="minio.${BASE_DOMAIN}"
  HOST_TRINO="trino.${BASE_DOMAIN}"
  HOST_NESSIE="nessie.${BASE_DOMAIN}"
  HOST_API="api.${BASE_DOMAIN}"
  HOST_RANGER="ranger.${BASE_DOMAIN}"
  HOST_ADMIN_UI="biadmin.${BASE_DOMAIN}"
  HOST_PLATFORM_UI="bi.${BASE_DOMAIN}"
  HOST_ANALYTICS="analytics.${BASE_DOMAIN}"
  HOST_META="meta.${BASE_DOMAIN}"
  HOST_FLOW="flow.${BASE_DOMAIN}"

  # ---------- Host reachability for in-container calls to host services ----------
  # Allow operators to pin this via environment; otherwise auto-detect.
  : "${HOST_GATEWAY_IP:=$(detect_host_ipv4)}"
  # Docker containers may need a stable way to reach host-side services like an HTTP proxy.
  # Used by compose as the IP behind 'host.docker.internal' (works even on older Docker versions).
  : "${DOCKER_HOST_GATEWAY_IP:=${HOST_GATEWAY_IP}}"
  # ---------- Hetu upstream (DEPRECATED, Sprint-78/F4) ----------
  # Hetu proxy routes were removed from compose and the traefik file provider.
  # This variable is no longer consumed; existing .env values are left untouched.
  : "${HETU_UPSTREAM_IP:=${HOST_GATEWAY_IP}}"

  # ---------- MinIO/S3 (placed before Airflow uses it) ----------
  if [[ "${ENABLE_MINIO:-false}" == "true" ]]; then
    : "${MINIO_ROOT_USER:=minio}"
    : "${MINIO_ROOT_PASSWORD:=${SECRET}}"
    : "${S3_BUCKET:=dts-lake}"
    : "${S3_REGION:=cn-local-1}"
    # Derived MinIO URLs for reverse-proxy deployments
    MINIO_SERVER_URL="https://${HOST_MINIO}"
    MINIO_BROWSER_REDIRECT_URL="https://${HOST_MINIO}"
  fi

  # ---------- Postgres & 多服务三元组 ----------
  : "${PG_AUTH_METHOD:=scram}"     # scram | md5
  : "${PG_SUPER_USER:=postgres}"
  : "${PG_SUPER_PASSWORD:=${SECRET}}"
  : "${PG_PORT:=5432}"

  # Keycloak
  : "${PG_DB_KEYCLOAK:=dts_keycloak}"
  : "${PG_USER_KEYCLOAK:=dts_keycloak}"
  : "${PG_PWD_KEYCLOAK:=${SECRET}}"

  # dts-admin（与 compose 变量名对齐）
  : "${DTADMIN_DB_NAME:=dts_admin}"
  : "${DTADMIN_DB_USER:=dts_admin}"
  : "${DTADMIN_DB_PASSWORD:=${SECRET}}"
  : "${DTADMIN_API_PORT:=18081}"
  # 为 dts-pg 初始化脚本提供 dev 可选三元组（避免 compose 变量警告）
  : "${PG_DB_DTADMIN:=${DTADMIN_DB_NAME}}"
  : "${PG_USER_DTADMIN:=${DTADMIN_DB_USER}}"
  : "${PG_PWD_DTADMIN:=${DTADMIN_DB_PASSWORD}}"

  # dts-platform
  : "${PG_DB_DTPS:=dts_platform}"
  : "${PG_USER_DTPS:=dts_platform}"
  : "${PG_PWD_DTPS:=${SECRET}}"

  # dts-common
  : "${PG_DB_DTCOMMON:=dts_common}"
  : "${PG_USER_DTCOMMON:=dts_common}"
  : "${PG_PWD_DTCOMMON:=${SECRET}}"

  # Analytics metadata
  : "${PG_DB_ANALYTICS:=dts_analytics}"
  : "${PG_USER_ANALYTICS:=dts_analytics}"
  : "${PG_PWD_ANALYTICS:=${SECRET}}"

  # dts-metrics retired from the default app stack. Legacy stack can still opt in.
  : "${DTS_LEGACY_METRICS_ENABLED:=${LEGACY_STACK}}"
  if [[ "${DTS_LEGACY_METRICS_ENABLED}" == "true" || "${DTS_LEGACY_METRICS_ENABLED}" == "1" ]]; then
    : "${PG_DB_METRICS:=dts_metrics}"
    : "${PG_USER_METRICS:=dts_metrics}"
    : "${PG_PWD_METRICS:=${SECRET}}"
    : "${DTS_INBOUND_FROM_METRICS:=${SECRET}}"
    : "${DTS_METRICS_TO_PLATFORM:=${DTS_INBOUND_FROM_METRICS}}"
    : "${DTS_METRICS_SERVICE_NAME:=dts-metrics}"
    : "${DTS_METRICS_API_BASE_PATH:=/api/metrics}"
    : "${IMAGE_DTS_METRICS:=dts-metrics:1.0.0}"
  fi

  # OpenMetadata
  : "${PG_DB_OPENMETADATA:=openmetadata_db}"
  : "${PG_USER_OPENMETADATA:=openmetadata}"
  : "${PG_PWD_OPENMETADATA:=${SECRET}}"

  : "${DTS_PLATFORM_OPENMETADATA_ENABLED:=true}"
  : "${DTS_PLATFORM_OPENMETADATA_BASE_URL:=http://dts-openmetadata:8585}"
  : "${DTS_PLATFORM_OPENMETADATA_UI_BASE_URL:=https://${HOST_META}}"
  : "${DTS_PLATFORM_OPENMETADATA_AUTH_TOKEN:=}"
  : "${DTS_PLATFORM_OPENMETADATA_SERVICE_NAME:=hive}"
  : "${DTS_PLATFORM_OPENMETADATA_DEFAULT_DATABASE:=}"
  : "${DTS_PLATFORM_OPENMETADATA_DEFAULT_SCHEMA:=}"
  if [[ -z "${DTS_PLATFORM_OPENMETADATA_TABLE_FQN_PATTERN:-}" ]]; then
    DTS_PLATFORM_OPENMETADATA_TABLE_FQN_PATTERN='{service}.{database}.{table}'
  elif [[ "${DTS_PLATFORM_OPENMETADATA_TABLE_FQN_PATTERN}" == "{service" ]]; then
    echo "[init.sh] WARNING: fixed invalid DTS_PLATFORM_OPENMETADATA_TABLE_FQN_PATTERN='{service}' from previous init.sh versions." >&2
    DTS_PLATFORM_OPENMETADATA_TABLE_FQN_PATTERN='{service}.{database}.{table}'
  fi
  : "${DTS_PLATFORM_OPENMETADATA_TABLE_FIELDS:=columns,owner,tags,domain,usageSummary,profile}"
  : "${DTS_OPENMETADATA_ENABLED:=${DTS_PLATFORM_OPENMETADATA_ENABLED}}"
  : "${DTS_OPENMETADATA_BASE_URL:=${DTS_PLATFORM_OPENMETADATA_BASE_URL}}"
  : "${DTS_OPENMETADATA_AUTH_TOKEN:=${DTS_PLATFORM_OPENMETADATA_AUTH_TOKEN}}"
  : "${DTS_OPENMETADATA_TABLE_FIELDS:=${DTS_PLATFORM_OPENMETADATA_TABLE_FIELDS}}"
  : "${DTS_OPENMETADATA_ALLOW_NO_AUTH:=false}"
  : "${DTS_OPENMETADATA_SOURCE_SERVICE_NAME:=}"
  : "${DTS_OPENMETADATA_SOURCE_SERVICE_TYPE:=}"
  : "${DTS_OPENMETADATA_DESTINATION_SERVICE_NAME:=${DTS_PLATFORM_OPENMETADATA_SERVICE_NAME}}"
  : "${DTS_OPENMETADATA_DESTINATION_SERVICE_TYPE:=Postgres}"
  : "${DTS_OPENMETADATA_DESTINATION_DATABASE:=${DTS_PLATFORM_OPENMETADATA_DEFAULT_DATABASE}}"
  : "${DTS_OPENMETADATA_DESTINATION_SCHEMA:=${DTS_PLATFORM_OPENMETADATA_DEFAULT_SCHEMA}}"
  : "${DTS_OPENMETADATA_SOURCE_DATABASE:=}"
  : "${DTS_OPENMETADATA_SOURCE_SCHEMA:=}"
  : "${DTS_OPENMETADATA_INGESTION_ENABLED:=true}"
  : "${DTS_OPENMETADATA_INGESTION_PREFIX:=dts_ingest}"
  : "${DTS_OPENMETADATA_INGESTION_SCHEDULE:=0 * * * *}"
  : "${DTS_OPENMETADATA_INGEST_DATABASE:=${PG_DB_BIADMIN:-biadmin}}"
  : "${DTS_OPENMETADATA_FORBIDDEN_DATABASES:=dts_platform,dts-platform,dts_admin,dts-admin,dts_common,dts-common,dts_analytics,dts-analytics,dts_keycloak,dts-keycloak,openmetadata_db,openmetadata,airflow,dts_ranger,dts-ranger}"
  : "${STACK_ROOT:=${SCRIPT_DIR}}"
  : "${DBT_PROJECT_DIR:=${STACK_ROOT}/services/dts-dbt}"
  : "${DBT_PROFILES_DIR:=${STACK_ROOT}/services/dts-dbt/profiles}"
  : "${OPENMETADATA_INGEST_CONFIG_DIR:=${STACK_ROOT}/services/dts-openmetadata/ingestion}"
  : "${DTS_DBT_PROJECT_DIR:=/opt/dts/dbt}"
  : "${DTS_DBT_PROFILES_DIR:=/opt/dts/dbt-profiles}"
  : "${DTS_DBT_CONFIG_PATH:=/opt/dts/upload/dbt-config.json}"
  : "${DTS_DBT_RUNTIME_PROFILE_EXPECTED_UID:=$(id -u)}"
  # Host-side mapping for DTS_DBT_PROJECT_DIR — reuses DBT_PROJECT_DIR (stack-root relative).
  # The platform backend returns this path to Airflow as the HOST side of docker -v mounts.
  : "${DTS_DBT_HOST_PROJECT_DIR:=${DBT_PROJECT_DIR}}"
  : "${AIRFLOW_ADMIN_USERNAME:=airflow}"
  : "${AIRFLOW_ADMIN_PASSWORD:=${SECRET}}"
  : "${AIRFLOW_ADMIN_EMAIL:=airflow@example.com}"
  : "${AIRFLOW_ADMIN_FIRSTNAME:=Airflow}"
  : "${AIRFLOW_ADMIN_LASTNAME:=Admin}"
  : "${DTS_AIRFLOW_USERNAME:=${AIRFLOW_ADMIN_USERNAME}}"
  : "${DTS_AIRFLOW_PASSWORD:=${AIRFLOW_ADMIN_PASSWORD}}"
  : "${DOCKER_GID:=$(detect_docker_gid)}"
  : "${DTS_RUNTIME_ARCH:=$(detect_runtime_arch)}"
  : "${DTS_RUNTIME_PLATFORM:=$(detect_runtime_platform)}"

  # ---------- Addax ----------
  : "${IMAGE_ADDAX:=dts-addax:6.0.8}"
  : "${ADDAX_DOCKER_NETWORK:=dts-core}"

  # Airflow
  : "${PG_DB_AIRFLOW:=airflow}"
  : "${PG_USER_AIRFLOW:=airflow}"
  : "${PG_PWD_AIRFLOW:=${SECRET}}"
  : "${PG_PWD_AIRFLOW_URLENCODED:=$(urlencode "${PG_PWD_AIRFLOW}")}"

  # BI Admin (数仓专用)
  : "${PG_DB_BIADMIN:=biadmin}"
  : "${PG_USER_BIADMIN:=biadmin}"
  : "${PG_PWD_BIADMIN:=${SECRET}}"

  # ---------- Ranger（Admin） ----------
  : "${PG_DB_RANGER:=dts_ranger}"
  : "${PG_USER_RANGER:=dts_ranger}"
  : "${PG_PWD_RANGER:=${SECRET}}"
  : "${RANGER_ADMIN_PASSWORD:=${SECRET}}"
  : "${RANGER_TAGSYNC_PASSWORD:=${SECRET}}"
  : "${RANGER_USERSYNC_PASSWORD:=${SECRET}}"

  # --- DTS 服务数据库 ---
  : "${IAM_DB_NAME:=iam}"
  : "${IAM_DB_USER:=iam}"
  : "${IAM_DB_PASSWORD:=${SECRET}}"
  : "${GOVERNANCE_DB_NAME:=governance}"
  : "${GOVERNANCE_DB_USER:=governance}"
  : "${GOVERNANCE_DB_PASSWORD:=${SECRET}}"
  : "${EXPLORE_DB_NAME:=explore}"
  : "${EXPLORE_DB_USER:=explore}"
  : "${EXPLORE_DB_PASSWORD:=${SECRET}}"

  # ---------- OIDC 客户端（admin 与 platform 各自一个） ----------
  : "${OAUTH2_ADMIN_CLIENT_ID:=dts-system}"
  : "${OAUTH2_ADMIN_CLIENT_SECRET:=${SECRET}}"
  : "${OAUTH2_PLATFORM_CLIENT_ID:=dts-system}"
  : "${OAUTH2_PLATFORM_CLIENT_SECRET:=${SECRET}}"
  OIDC_ISSUER_URI="https://${HOST_SSO}/realms/${KC_REALM}"

  # ---------- Service-to-service auth ----------
  # 按调用方向生成 pairwise credential；不同生产者不得共享服务身份。
  : "${DTS_PLATFORM_TO_ADMIN_TOKEN:=$(generate_fernet)}"
  : "${DTS_PLATFORM_TO_INGESTION_TOKEN:=$(generate_fernet)}"
  : "${DTS_AIRFLOW_TO_INGESTION_TOKEN:=$(generate_fernet)}"
  : "${DTS_ANALYTICS_TO_ADMIN_TOKEN:=$(generate_fernet)}"
  : "${DTS_INGESTION_TO_ADMIN_TOKEN:=$(generate_fernet)}"
  : "${DTS_INBOUND_FROM_INGESTION:=$(generate_fernet)}"
  : "${DTS_INBOUND_FROM_ANALYTICS:=$(generate_fernet)}"
  : "${DTS_INBOUND_FROM_AIRFLOW:=$(generate_fernet)}"
  : "${DTS_SERVICE_TOKEN_VERSION:=1}"
  ensure_distinct_service_tokens
  DTS_INGESTION_TO_PLATFORM="${DTS_INBOUND_FROM_INGESTION}"
  DTS_ANALYTICS_TO_PLATFORM="${DTS_INBOUND_FROM_ANALYTICS}"
  DTS_AIRFLOW_TO_PLATFORM="${DTS_INBOUND_FROM_AIRFLOW}"
  : "${DTS_MODEL_RUNTIME_SPEC_SIGNING_KEY:=$(generate_fernet)}"

  # ---------- Edition / optional service capabilities ----------
  : "${DTS_EDITION:=foundation}"

  # ---------- Analytics ----------
  # Prefer your self-built image (offline/air-gapped friendly). Default aligns with other DTS app images.
  : "${DTS_ANALYTICS_BASE_URL:=http://dts-analytics:3000}"
  : "${IMAGE_DTS_ADMIN:=dts-admin:1.0.0}"
  : "${IMAGE_DTS_PLATFORM:=dts-platform:1.0.0}"
  : "${IMAGE_DTS_INGESTION:=dts-ingestion:1.0.0}"
  : "${IMAGE_DTS_ADMIN_WEBAPP:=dts-admin-webapp:1.0.0}"
  : "${IMAGE_DTS_PLATFORM_WEBAPP:=dts-platform-webapp:1.0.0}"
  : "${IMAGE_DTS_ANALYTICS:=dts-analytics:1.0.0}"
  # IMAGE_DTS_ANALYTICS_WEBAPP_MODERN removed — analytics UI embedded in platform-webapp.
  : "${ANALYTICS_ENCRYPTION_SECRET:=$(generate_fernet)}"
  # Prefer same-domain mount under platform UI to keep user-facing URLs consistent and avoid extra DNS/ports.
  : "${ANALYTICS_SITE_URL:=https://${HOST_PLATFORM_UI}/analytics}"
  : "${ANALYTICS_JAVA_TOOL_OPTIONS:=-Xms512m -Xmx1024m}"
  : "${ANALYTICS_OIDC_CLIENT_ID:=analytics}"
  : "${ANALYTICS_OIDC_CLIENT_SECRET:=${SECRET}}"
  # When mounted under /analytics behind Traefik, the redirect URI must include the prefix.
  : "${ANALYTICS_OIDC_REDIRECT_URI:=https://${HOST_PLATFORM_UI}/analytics/auth/oidc/callback}"
  : "${ANALYTICS_OIDC_METADATA_URL:=https://${HOST_SSO}/realms/${KC_REALM}/.well-known/openid-configuration}"

  # ---------- OpenMetadata ----------
  : "${OPENMETADATA_HTTP_PORT:=18585}"
  : "${OPENMETADATA_JWT_SECRET:=$(generate_fernet)}"

  # ---------- Airflow ----------
  : "${AIRFLOW_WEBSERVER_PORT:=18090}"
  : "${DTS_INFRA_ENCRYPTION_KEY:=$(generate_aes_key)}"
  : "${DTS_INFRA_KEY_VERSION:=v1}"
  : "${AIRFLOW_FERNET_KEY:=$(generate_fernet)}"
  : "${AIRFLOW_ADMIN_USERNAME:=airflow}"
  : "${AIRFLOW_ADMIN_PASSWORD:=${SECRET}}"
  : "${AIRFLOW_ADMIN_EMAIL:=airflow@example.com}"
  : "${AIRFLOW_ADMIN_FIRSTNAME:=Airflow}"
  : "${AIRFLOW_ADMIN_LASTNAME:=Admin}"
  : "${DTS_AIRFLOW_USERNAME:=${AIRFLOW_ADMIN_USERNAME}}"
  : "${DTS_AIRFLOW_PASSWORD:=${AIRFLOW_ADMIN_PASSWORD}}"

  # ---------- MDM Gateway ----------
  : "${DTS_MDM_GATEWAY_ENABLED:=true}"
  if [[ "${LEGACY_STACK}" == "true" ]]; then
    : "${DTS_MDM_GATEWAY_STORAGE_PATH:=/data/mdm}"
  else
    : "${DTS_MDM_GATEWAY_STORAGE_PATH:=data/mdm}"
  fi
  : "${DTS_MDM_GATEWAY_LOG_PATH:=/logs/dts-admin/mdm-gateway.log}"
  : "${DTS_MDM_GATEWAY_UPSTREAM_BASE_URL:=http://localhost:28080}"
  : "${DTS_MDM_GATEWAY_UPSTREAM_PULL_PATH:=/api/mdm/pull}"
  : "${DTS_MDM_GATEWAY_UPSTREAM_AUTH_TOKEN:=}"
  : "${DTS_MDM_GATEWAY_UPSTREAM_CONNECT_TIMEOUT:=5s}"
  : "${DTS_MDM_GATEWAY_UPSTREAM_READ_TIMEOUT:=30s}"
  : "${DTS_MDM_GATEWAY_UPSTREAM_USE_MULTIPART:=true}"
  : "${DTS_MDM_GATEWAY_UPSTREAM_FILE_PART_NAME:=file}"
  : "${DTS_MDM_GATEWAY_UPSTREAM_FILE_PREFIX:=orgItDemand}"
  : "${DTS_MDM_GATEWAY_UPSTREAM_FILE_SUFFIX:=.txt}"
  : "${DTS_MDM_GATEWAY_CALLBACK_URL:=http://localhost:38012/api/mdm/receive}"
  : "${DTS_MDM_GATEWAY_CALLBACK_AUTH_TOKEN:=}"
  : "${DTS_MDM_GATEWAY_CALLBACK_SIGNATURE_HEADER:=X-Signature}"
  : "${DTS_MDM_GATEWAY_CALLBACK_ALLOWED_IPS:=}"
  : "${DTS_MDM_GATEWAY_REGISTRY_SYSTEM_CODE:=10XT}"
  : "${DTS_MDM_GATEWAY_REGISTRY_DATA_RANGE:=9010}"
  : "${DTS_MDM_GATEWAY_REGISTRY_AREA_SECURITY:=9001}"
  : "${DTS_MDM_GATEWAY_REGISTRY_AREA_BUSINESS:=B}"
  : "${DTS_MDM_GATEWAY_REGISTRY_DATA_TYPE:=sync-demand}"
  : "${DTS_MDM_GATEWAY_REQUIRED_FIELDS:=orgCode,deptCode,status}"
  : "${DTS_MDM_GATEWAY_REQUIRED_USERS:=userCode,userName,deptCode}"
  : "${DTS_MDM_GATEWAY_REQUIRED_DEPTS:=deptCode,deptName}"
  : "${DTS_MDM_GATEWAY_ROOT_CODE:=90}"
  : "${DTS_MDM_GATEWAY_AUTO_PROVISION_USERS:=true}"
  : "${DTS_MDM_GATEWAY_AUTO_PROVISION_ROLES:=EMPLOYEE}"
  : "${DTS_MDM_GATEWAY_AUTO_PROVISION_ENABLE_LOGIN:=true}"

  # ---------- PKI（admin 服务验签配置） ----------
  : "${DTS_PKI_ENABLED:=true}"
  : "${DTS_PKI_MODE:=gateway}"
  : "${DTS_PKI_ALLOW_MOCK:=false}"
  : "${DTS_PKI_ACCEPT_FORWARDED_CERT:=false}"
  : "${DTS_PKI_CLIENT_CERT_HEADER_NAME:=X-Forwarded-Tls-Client-Cert}"
  : "${DTS_PKI_ISSUER_CN:=}"
  : "${DTS_PKI_API_BASE:=}"
  : "${DTS_PKI_API_TOKEN:=}"
  : "${DTS_PKI_API_TIMEOUT:=3000}"
  : "${DTS_PKI_GATEWAY_HOST:=}"
  : "${DTS_PKI_GATEWAY_PORT:=0}"
  : "${DTS_PKI_GATEWAY_ALT_PORT:=10009}"
  : "${DTS_PKI_GATEWAY_ENDPOINT:=/wglogin}"

  # ---------- Admin password-login IP allowlist (triad only; PKI unaffected) ----------
  : "${DTS_SECURITY_IP_ALLOWLIST_ENABLED:=false}"
  : "${DTS_SECURITY_IP_ALLOWLIST_TRIAD_USERNAMES:=sysadmin,authadmin,auditadmin}"

  # ---------- Test / API-debug switches (KEEP DEFAULTS on prod) ----------
  # Swagger UI + /v3/api-docs 匿名访问开关；生产务必 false，dev/test 可 true
  : "${APP_API_DOCS_PUBLIC:=false}"
  # /test/** 辅助接口（TestApiResource）总开关；true 时由 X-Test-Token 单独鉴权
  : "${APP_TEST_API_ENABLED:=false}"
  # /test/** 使用的 token，建议: openssl rand -hex 32；留空表示所有访问都被拒
  : "${APP_TEST_API_TOKEN:=}"
  : "${DTS_PKI_DIGEST:=SHA1}"
  : "${DTS_PKI_VENDOR_JAR:=/opt/dts/vendor}"
  : "${DTS_ADMIN_JAVA_TOOL_OPTIONS_EXTRA:=--add-exports=java.base/sun.security.x509=ALL-UNNAMED --add-exports=java.base/sun.security.util=ALL-UNNAMED --add-opens=java.base/sun.security.x509=ALL-UNNAMED --add-opens=java.base/sun.security.util=ALL-UNNAMED}"

  # ---------- 前端 PKI 互操作 ----------
  : "${VITE_ADMIN_API_BASE_URL:=/admin/api}"
  : "${VITE_ADMIN_PROXY_TARGET:=}"
  : "${VITE_KOAL_PKI_ENDPOINTS:=https://127.0.0.1:16080,http://127.0.0.1:18080}"
  : "${VITE_ENABLE_SQL_WORKBENCH:=true}"
  # Unified runtime injection for both webapps (optional). If unset, frontends fall back to defaults.
  : "${KOAL_PKI_ENDPOINTS:=${VITE_KOAL_PKI_ENDPOINTS}}"
  # Optional: Explicit base URL for Koal SDK assets used by webapps.
  # Default to same-origin '/vendor/koal' so offline/air‑gapped deployments work out-of-the-box.
  # Can be overridden by environment if needed (e.g., a full https URL).
  : "${KOAL_VENDOR_BASE:=/vendor/koal}"
  # Optional dev-only alias (read by Vite when serving /runtime-config.js in dev)
  : "${VITE_KOAL_VENDOR_BASE:=${KOAL_VENDOR_BASE}}"
  # —— 分别控制 admin 与 platform 前端密码登录显示（运行时注入，无需重建镜像）——
  # 默认均为通过 PKI 登录（隐藏密码登录表单）
  : "${ADMIN_WEBAPP_PASSWORD_LOGIN_ENABLED:=true}"
  : "${ADMIN_VITE_HIDE_PASSWORD_LOGIN:=false}"
  : "${PLATFORM_WEBAPP_PASSWORD_LOGIN_ENABLED:=true}"
  : "${PLATFORM_VITE_HIDE_PASSWORD_LOGIN:=false}"
  : "${WEBAPP_SHOW_CLASSIFIED_LOGIN_BADGE:=true}"

  # ---------- 管理端来源 IP 白名单（按单/多 IP，/32 形式由脚本生成） ----------
  # 输入：纯 IP，逗号分隔；应急后门 IP 同样逗号分隔。留空时默认放开 0.0.0.0/0（便于离线/内网环境调试）。
  : "${ADMIN_ALLOWED_IPS:=}"
  : "${ADMIN_BACKUP_IPS:=}"

  # 生成 /32 CIDR 并集，去重
  ADMIN_WHITELIST_CIDRS="0.0.0.0/0"
  {
    printf '%s' "${ADMIN_ALLOWED_IPS}"
    printf ','
    printf '%s' "${ADMIN_BACKUP_IPS}"
  } | sed -E 's/[[:space:]]+//g; s/,+/,/g; s/^,|,$//g' | awk -F',' 'NF{for(i=1;i<=NF;i++) if(length($i)) print $i}' | \
  while IFS= read -r ip; do
    if [[ "$ip" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
      # 粗略过滤 0-255 之外的情况留给运维自行校验；此处仅做形态检查
      echo "${ip}/32"
    fi
  done | awk '!x[$0]++' | paste -sd, - | sed 's/^$/0.0.0.0\/0/' | { read -r line || true; ADMIN_WHITELIST_CIDRS="${line:-0.0.0.0/0}"; }

  # 注意：若需要覆盖默认行为，可在 .env 中修改上述四个变量

  DTS_ADMIN_JAVA_TOOL_OPTIONS_EXTRA_ENV=${DTS_ADMIN_JAVA_TOOL_OPTIONS_EXTRA//$'\\'/\\\\}
  DTS_ADMIN_JAVA_TOOL_OPTIONS_EXTRA_ENV=${DTS_ADMIN_JAVA_TOOL_OPTIONS_EXTRA_ENV//\"/\\\"}
  DTS_ADMIN_JAVA_TOOL_OPTIONS_EXTRA_ENV=${DTS_ADMIN_JAVA_TOOL_OPTIONS_EXTRA_ENV//$'\n'/ }

  env_tmp="$(mktemp "${PWD}/.env.tmp.XXXXXX")" || return 1
  chmod 600 "${env_tmp}" || { rm -f -- "${env_tmp}"; return 1; }
  cat > "${env_tmp}" <<EOF
# ====== Base & Traefik ======
BASE_DOMAIN=${BASE_DOMAIN}
TLS_PORT=${TLS_PORT}
TRAEFIK_DASHBOARD=${TRAEFIK_DASHBOARD}
TRAEFIK_DASHBOARD_PORT=${TRAEFIK_DASHBOARD_PORT}
TRAEFIK_METRICS_PORT=${TRAEFIK_METRICS_PORT}
TRAEFIK_ENABLE_PING=${TRAEFIK_ENABLE_PING}
KAFKA_UI_BIND_HOST=${KAFKA_UI_BIND_HOST}

# ====== Build Helpers ======
IMAGE_MAVEN=${IMAGE_MAVEN}
KEYTOOL_IMAGE=${KEYTOOL_IMAGE}
KEYTOOL_IMAGE_STRICT=${KEYTOOL_IMAGE_STRICT}
DTS_RUNTIME_ARCH=${DTS_RUNTIME_ARCH}
DTS_RUNTIME_PLATFORM=${DTS_RUNTIME_PLATFORM}

# ====== Hosts ======
HOST_SSO=${HOST_SSO}
HOST_TRINO=${HOST_TRINO}
HOST_API=${HOST_API}
HOST_RANGER=${HOST_RANGER}
HOST_ADMIN_UI=${HOST_ADMIN_UI}
HOST_PLATFORM_UI=${HOST_PLATFORM_UI}
HOST_ANALYTICS=${HOST_ANALYTICS}
HOST_META=${HOST_META}
HOST_FLOW=${HOST_FLOW}
HOST_GATEWAY_IP=${HOST_GATEWAY_IP}
DOCKER_HOST_GATEWAY_IP=${DOCKER_HOST_GATEWAY_IP}
# HETU_UPSTREAM_IP 已退役（Sprint-78/F4）：新部署不再写入；既有 .env 中的值保持不变。

# ====== Keycloak ======
KC_ADMIN=${KC_ADMIN}
KC_ADMIN_PWD=${KC_ADMIN_PWD}
KC_HTTP_ENABLED=${KC_HTTP_ENABLED}
KC_HOSTNAME=${KC_HOSTNAME}
KC_HOSTNAME_PORT=${KC_HOSTNAME_PORT}
KC_HOSTNAME_URL=${KC_HOSTNAME_URL}
KC_HOSTNAME_ADMIN_URL=${KC_HOSTNAME_ADMIN_URL}
KC_HOSTNAME_STRICT=${KC_HOSTNAME_STRICT}
KC_HOSTNAME_STRICT_HTTPS=${KC_HOSTNAME_STRICT_HTTPS}
KC_DB_URL_PROPERTIES=${KC_DB_URL_PROPERTIES}
KC_REALM=${KC_REALM}
OIDC_ISSUER_URI=${OIDC_ISSUER_URI}
TRUSTSTORE_PASSWORD=${TRUSTSTORE_PASSWORD}

# ====== Postgres (mode/host filled later) ======
PG_AUTH_METHOD=${PG_AUTH_METHOD}
PG_MODE=${PG_MODE}
PG_HOST=${PG_HOST}
PG_SUPER_USER=${PG_SUPER_USER}
PG_SUPER_PASSWORD=${PG_SUPER_PASSWORD}
PG_PORT=${PG_PORT}

# --- Keycloak DB triplet ---
PG_DB_KEYCLOAK=${PG_DB_KEYCLOAK}
PG_USER_KEYCLOAK=${PG_USER_KEYCLOAK}
PG_PWD_KEYCLOAK=${PG_PWD_KEYCLOAK}

# --- dts-admin (compose 直连用这些名) ---
DTADMIN_DB_NAME=${DTADMIN_DB_NAME}
DTADMIN_DB_USER=${DTADMIN_DB_USER}
DTADMIN_DB_PASSWORD=${DTADMIN_DB_PASSWORD}
DTADMIN_API_PORT=${DTADMIN_API_PORT}

# --- dts-admin PG triplet (for dts-pg init) ---
PG_DB_DTADMIN=${PG_DB_DTADMIN}
PG_USER_DTADMIN=${PG_USER_DTADMIN}
PG_PWD_DTADMIN=${PG_PWD_DTADMIN}

# --- dts-platform triplet ---
PG_DB_DTPS=${PG_DB_DTPS}
PG_USER_DTPS=${PG_USER_DTPS}
PG_PWD_DTPS=${PG_PWD_DTPS}

# --- dts-common triplet ---
PG_DB_DTCOMMON=${PG_DB_DTCOMMON}
PG_USER_DTCOMMON=${PG_USER_DTCOMMON}
PG_PWD_DTCOMMON=${PG_PWD_DTCOMMON}

# --- Analytics metadata ---
PG_DB_ANALYTICS=${PG_DB_ANALYTICS}
PG_USER_ANALYTICS=${PG_USER_ANALYTICS}
PG_PWD_ANALYTICS=${PG_PWD_ANALYTICS}

# --- OpenMetadata triplet ---
PG_DB_OPENMETADATA=${PG_DB_OPENMETADATA}
PG_USER_OPENMETADATA=${PG_USER_OPENMETADATA}
PG_PWD_OPENMETADATA=${PG_PWD_OPENMETADATA}

# --- Airflow triplet ---
PG_DB_AIRFLOW=${PG_DB_AIRFLOW}
PG_USER_AIRFLOW=${PG_USER_AIRFLOW}
PG_PWD_AIRFLOW=${PG_PWD_AIRFLOW}
PG_PWD_AIRFLOW_URLENCODED=${PG_PWD_AIRFLOW_URLENCODED}

# --- BI Admin triplet (数仓) ---
PG_DB_BIADMIN=${PG_DB_BIADMIN}
PG_USER_BIADMIN=${PG_USER_BIADMIN}
PG_PWD_BIADMIN=${PG_PWD_BIADMIN}

# ====== OIDC Clients ======
OAUTH2_ADMIN_CLIENT_ID=${OAUTH2_ADMIN_CLIENT_ID}
OAUTH2_ADMIN_CLIENT_SECRET=${OAUTH2_ADMIN_CLIENT_SECRET}
OAUTH2_PLATFORM_CLIENT_ID=${OAUTH2_PLATFORM_CLIENT_ID}
OAUTH2_PLATFORM_CLIENT_SECRET=${OAUTH2_PLATFORM_CLIENT_SECRET}

# ====== Admin PKI ======
DTS_PKI_ENABLED=${DTS_PKI_ENABLED}
DTS_PKI_MODE=${DTS_PKI_MODE}
DTS_PKI_ALLOW_MOCK=${DTS_PKI_ALLOW_MOCK}
DTS_PKI_ACCEPT_FORWARDED_CERT=${DTS_PKI_ACCEPT_FORWARDED_CERT}
DTS_PKI_CLIENT_CERT_HEADER_NAME=${DTS_PKI_CLIENT_CERT_HEADER_NAME}
DTS_PKI_ISSUER_CN=${DTS_PKI_ISSUER_CN}
DTS_PKI_API_BASE=${DTS_PKI_API_BASE}
DTS_PKI_API_TOKEN=${DTS_PKI_API_TOKEN}
DTS_PKI_API_TIMEOUT=${DTS_PKI_API_TIMEOUT}
DTS_PKI_GATEWAY_HOST=${DTS_PKI_GATEWAY_HOST}
DTS_PKI_GATEWAY_PORT=${DTS_PKI_GATEWAY_PORT}
DTS_PKI_GATEWAY_ALT_PORT=${DTS_PKI_GATEWAY_ALT_PORT}
DTS_PKI_GATEWAY_ENDPOINT=${DTS_PKI_GATEWAY_ENDPOINT}
DTS_PKI_DIGEST=${DTS_PKI_DIGEST}
DTS_PKI_VENDOR_JAR=${DTS_PKI_VENDOR_JAR}
DTS_ADMIN_JAVA_TOOL_OPTIONS_EXTRA="${DTS_ADMIN_JAVA_TOOL_OPTIONS_EXTRA_ENV}"

# ====== Service-to-service auth ======
# 按调用方向命名；不再生成或回退到全局 DTS_ADMIN_SERVICE_TOKEN。
DTS_SERVICE_TOKEN_VERSION=${DTS_SERVICE_TOKEN_VERSION}
DTS_PLATFORM_TO_ADMIN_TOKEN=${DTS_PLATFORM_TO_ADMIN_TOKEN}
DTS_PLATFORM_TO_INGESTION_TOKEN=${DTS_PLATFORM_TO_INGESTION_TOKEN}
DTS_AIRFLOW_TO_INGESTION_TOKEN=${DTS_AIRFLOW_TO_INGESTION_TOKEN}
DTS_INBOUND_FROM_INGESTION=${DTS_INBOUND_FROM_INGESTION}
DTS_INBOUND_FROM_ANALYTICS=${DTS_INBOUND_FROM_ANALYTICS}
DTS_INBOUND_FROM_AIRFLOW=${DTS_INBOUND_FROM_AIRFLOW}
DTS_INGESTION_TO_PLATFORM=${DTS_INGESTION_TO_PLATFORM}
DTS_ANALYTICS_TO_PLATFORM=${DTS_ANALYTICS_TO_PLATFORM}
DTS_AIRFLOW_TO_PLATFORM=${DTS_AIRFLOW_TO_PLATFORM}
DTS_MODEL_RUNTIME_SPEC_SIGNING_KEY=${DTS_MODEL_RUNTIME_SPEC_SIGNING_KEY}
DTS_ANALYTICS_TO_ADMIN_TOKEN=${DTS_ANALYTICS_TO_ADMIN_TOKEN}
DTS_INGESTION_TO_ADMIN_TOKEN=${DTS_INGESTION_TO_ADMIN_TOKEN}

# ====== Admin password-login IP allowlist (triad only; PKI unaffected) ======
DTS_SECURITY_IP_ALLOWLIST_ENABLED=${DTS_SECURITY_IP_ALLOWLIST_ENABLED}
DTS_SECURITY_IP_ALLOWLIST_TRIAD_USERNAMES=${DTS_SECURITY_IP_ALLOWLIST_TRIAD_USERNAMES}

# ====== Test / API-debug switches (生产务必保持 false) ======
# Swagger UI + /v3/api-docs 是否匿名可访问（dev/test=true 方便排查，prod=false）
APP_API_DOCS_PUBLIC=${APP_API_DOCS_PUBLIC}
# /test/** 辅助接口总开关；true 时由 X-Test-Token 鉴权（非 OIDC）
APP_TEST_API_ENABLED=${APP_TEST_API_ENABLED}
APP_TEST_API_TOKEN=${APP_TEST_API_TOKEN}

# ====== Frontend PKI defaults ======
VITE_ADMIN_API_BASE_URL=${VITE_ADMIN_API_BASE_URL}
VITE_ADMIN_PROXY_TARGET=${VITE_ADMIN_PROXY_TARGET}
VITE_KOAL_PKI_ENDPOINTS=${VITE_KOAL_PKI_ENDPOINTS}
VITE_ENABLE_SQL_WORKBENCH=${VITE_ENABLE_SQL_WORKBENCH}
KOAL_VENDOR_BASE=${KOAL_VENDOR_BASE}
VITE_KOAL_VENDOR_BASE=${VITE_KOAL_VENDOR_BASE}
KOAL_PKI_ENDPOINTS=${KOAL_PKI_ENDPOINTS}
ADMIN_WEBAPP_PASSWORD_LOGIN_ENABLED=${ADMIN_WEBAPP_PASSWORD_LOGIN_ENABLED}
ADMIN_VITE_HIDE_PASSWORD_LOGIN=${ADMIN_VITE_HIDE_PASSWORD_LOGIN}
PLATFORM_WEBAPP_PASSWORD_LOGIN_ENABLED=${PLATFORM_WEBAPP_PASSWORD_LOGIN_ENABLED}
PLATFORM_VITE_HIDE_PASSWORD_LOGIN=${PLATFORM_VITE_HIDE_PASSWORD_LOGIN}
WEBAPP_SHOW_CLASSIFIED_LOGIN_BADGE=${WEBAPP_SHOW_CLASSIFIED_LOGIN_BADGE}

# ====== Analytics ======
DTS_ANALYTICS_BASE_URL=${DTS_ANALYTICS_BASE_URL}
ANALYTICS_ENCRYPTION_SECRET=${ANALYTICS_ENCRYPTION_SECRET}
ANALYTICS_SITE_URL=${ANALYTICS_SITE_URL}
ANALYTICS_JAVA_TOOL_OPTIONS="${ANALYTICS_JAVA_TOOL_OPTIONS}"
ANALYTICS_OIDC_CLIENT_ID=${ANALYTICS_OIDC_CLIENT_ID}
ANALYTICS_OIDC_CLIENT_SECRET=${ANALYTICS_OIDC_CLIENT_SECRET}
ANALYTICS_OIDC_REDIRECT_URI=${ANALYTICS_OIDC_REDIRECT_URI}
ANALYTICS_OIDC_METADATA_URL=${ANALYTICS_OIDC_METADATA_URL}

# ====== OpenMetadata ======
OPENMETADATA_HTTP_PORT=${OPENMETADATA_HTTP_PORT}
OPENMETADATA_JWT_SECRET=${OPENMETADATA_JWT_SECRET}
DTS_PLATFORM_OPENMETADATA_ENABLED=${DTS_PLATFORM_OPENMETADATA_ENABLED}
DTS_PLATFORM_OPENMETADATA_BASE_URL=${DTS_PLATFORM_OPENMETADATA_BASE_URL}
DTS_PLATFORM_OPENMETADATA_UI_BASE_URL=${DTS_PLATFORM_OPENMETADATA_UI_BASE_URL}
DTS_PLATFORM_OPENMETADATA_AUTH_TOKEN=${DTS_PLATFORM_OPENMETADATA_AUTH_TOKEN}
DTS_PLATFORM_OPENMETADATA_SERVICE_NAME=${DTS_PLATFORM_OPENMETADATA_SERVICE_NAME}
DTS_PLATFORM_OPENMETADATA_DEFAULT_DATABASE=${DTS_PLATFORM_OPENMETADATA_DEFAULT_DATABASE}
DTS_PLATFORM_OPENMETADATA_DEFAULT_SCHEMA=${DTS_PLATFORM_OPENMETADATA_DEFAULT_SCHEMA}
DTS_PLATFORM_OPENMETADATA_TABLE_FQN_PATTERN=${DTS_PLATFORM_OPENMETADATA_TABLE_FQN_PATTERN}
DTS_PLATFORM_OPENMETADATA_TABLE_FIELDS=${DTS_PLATFORM_OPENMETADATA_TABLE_FIELDS}
DTS_OPENMETADATA_ENABLED=${DTS_OPENMETADATA_ENABLED}
DTS_OPENMETADATA_BASE_URL=${DTS_OPENMETADATA_BASE_URL}
DTS_OPENMETADATA_AUTH_TOKEN=${DTS_OPENMETADATA_AUTH_TOKEN}
DTS_OPENMETADATA_TABLE_FIELDS=${DTS_OPENMETADATA_TABLE_FIELDS}
DTS_OPENMETADATA_ALLOW_NO_AUTH=${DTS_OPENMETADATA_ALLOW_NO_AUTH}
DTS_OPENMETADATA_SOURCE_SERVICE_NAME=${DTS_OPENMETADATA_SOURCE_SERVICE_NAME}
DTS_OPENMETADATA_SOURCE_SERVICE_TYPE=${DTS_OPENMETADATA_SOURCE_SERVICE_TYPE}
DTS_OPENMETADATA_DESTINATION_SERVICE_NAME=${DTS_OPENMETADATA_DESTINATION_SERVICE_NAME}
DTS_OPENMETADATA_DESTINATION_SERVICE_TYPE=${DTS_OPENMETADATA_DESTINATION_SERVICE_TYPE}
DTS_OPENMETADATA_DESTINATION_DATABASE=${DTS_OPENMETADATA_DESTINATION_DATABASE}
DTS_OPENMETADATA_DESTINATION_SCHEMA=${DTS_OPENMETADATA_DESTINATION_SCHEMA}
DTS_OPENMETADATA_SOURCE_DATABASE=${DTS_OPENMETADATA_SOURCE_DATABASE}
DTS_OPENMETADATA_SOURCE_SCHEMA=${DTS_OPENMETADATA_SOURCE_SCHEMA}
DTS_OPENMETADATA_INGESTION_ENABLED=${DTS_OPENMETADATA_INGESTION_ENABLED}
DTS_OPENMETADATA_INGESTION_PREFIX=${DTS_OPENMETADATA_INGESTION_PREFIX}
DTS_OPENMETADATA_INGESTION_SCHEDULE="${DTS_OPENMETADATA_INGESTION_SCHEDULE}"
DTS_OPENMETADATA_INGEST_DATABASE=${DTS_OPENMETADATA_INGEST_DATABASE}
DTS_OPENMETADATA_FORBIDDEN_DATABASES=${DTS_OPENMETADATA_FORBIDDEN_DATABASES}
STACK_ROOT=${STACK_ROOT}
DBT_PROJECT_DIR=${DBT_PROJECT_DIR}
DBT_PROFILES_DIR=${DBT_PROFILES_DIR}
OPENMETADATA_INGEST_CONFIG_DIR=${OPENMETADATA_INGEST_CONFIG_DIR}
DTS_DBT_PROJECT_DIR=${DTS_DBT_PROJECT_DIR}
DTS_DBT_PROFILES_DIR=${DTS_DBT_PROFILES_DIR}
DTS_DBT_CONFIG_PATH=${DTS_DBT_CONFIG_PATH}
DTS_DBT_HOST_PROJECT_DIR=${DTS_DBT_HOST_PROJECT_DIR}
DTS_DBT_RUNTIME_PROFILE_EXPECTED_UID=${DTS_DBT_RUNTIME_PROFILE_EXPECTED_UID}
DOCKER_GID=${DOCKER_GID}
IMAGE_ADDAX=${IMAGE_ADDAX}
ADDAX_DOCKER_NETWORK=${ADDAX_DOCKER_NETWORK}

# ====== Airflow ======
AIRFLOW_WEBSERVER_PORT=${AIRFLOW_WEBSERVER_PORT}
DTS_INFRA_ENCRYPTION_KEY=${DTS_INFRA_ENCRYPTION_KEY}
DTS_INFRA_KEY_VERSION=${DTS_INFRA_KEY_VERSION}
AIRFLOW_FERNET_KEY=${AIRFLOW_FERNET_KEY}
AIRFLOW_ADMIN_USERNAME=${AIRFLOW_ADMIN_USERNAME}
AIRFLOW_ADMIN_PASSWORD=${AIRFLOW_ADMIN_PASSWORD}
AIRFLOW_ADMIN_EMAIL=${AIRFLOW_ADMIN_EMAIL}
AIRFLOW_ADMIN_FIRSTNAME=${AIRFLOW_ADMIN_FIRSTNAME}
AIRFLOW_ADMIN_LASTNAME=${AIRFLOW_ADMIN_LASTNAME}
DTS_AIRFLOW_USERNAME=${DTS_AIRFLOW_USERNAME}
DTS_AIRFLOW_PASSWORD=${DTS_AIRFLOW_PASSWORD}

# ====== MDM Gateway (new keys) ======
DTS_MDM_GATEWAY_ENABLED=${DTS_MDM_GATEWAY_ENABLED}
DTS_MDM_GATEWAY_STORAGE_PATH=${DTS_MDM_GATEWAY_STORAGE_PATH}
DTS_MDM_GATEWAY_LOG_PATH=${DTS_MDM_GATEWAY_LOG_PATH}
DTS_MDM_GATEWAY_UPSTREAM_BASE_URL=${DTS_MDM_GATEWAY_UPSTREAM_BASE_URL}
DTS_MDM_GATEWAY_UPSTREAM_PULL_PATH=${DTS_MDM_GATEWAY_UPSTREAM_PULL_PATH}
DTS_MDM_GATEWAY_UPSTREAM_AUTH_TOKEN=${DTS_MDM_GATEWAY_UPSTREAM_AUTH_TOKEN}
DTS_MDM_GATEWAY_UPSTREAM_CONNECT_TIMEOUT=${DTS_MDM_GATEWAY_UPSTREAM_CONNECT_TIMEOUT}
DTS_MDM_GATEWAY_UPSTREAM_READ_TIMEOUT=${DTS_MDM_GATEWAY_UPSTREAM_READ_TIMEOUT}
DTS_MDM_GATEWAY_UPSTREAM_USE_MULTIPART=${DTS_MDM_GATEWAY_UPSTREAM_USE_MULTIPART}
DTS_MDM_GATEWAY_UPSTREAM_FILE_PART_NAME=${DTS_MDM_GATEWAY_UPSTREAM_FILE_PART_NAME}
DTS_MDM_GATEWAY_UPSTREAM_FILE_PREFIX=${DTS_MDM_GATEWAY_UPSTREAM_FILE_PREFIX}
DTS_MDM_GATEWAY_UPSTREAM_FILE_SUFFIX=${DTS_MDM_GATEWAY_UPSTREAM_FILE_SUFFIX}
DTS_MDM_GATEWAY_CALLBACK_URL=${DTS_MDM_GATEWAY_CALLBACK_URL}
DTS_MDM_GATEWAY_CALLBACK_AUTH_TOKEN=${DTS_MDM_GATEWAY_CALLBACK_AUTH_TOKEN}
DTS_MDM_GATEWAY_CALLBACK_SIGNATURE_HEADER=${DTS_MDM_GATEWAY_CALLBACK_SIGNATURE_HEADER}
DTS_MDM_GATEWAY_CALLBACK_ALLOWED_IPS=${DTS_MDM_GATEWAY_CALLBACK_ALLOWED_IPS}
DTS_MDM_GATEWAY_REGISTRY_SYSTEM_CODE=${DTS_MDM_GATEWAY_REGISTRY_SYSTEM_CODE}
DTS_MDM_GATEWAY_REGISTRY_DATA_RANGE=${DTS_MDM_GATEWAY_REGISTRY_DATA_RANGE}
DTS_MDM_GATEWAY_REGISTRY_AREA_SECURITY=${DTS_MDM_GATEWAY_REGISTRY_AREA_SECURITY}
DTS_MDM_GATEWAY_REGISTRY_AREA_BUSINESS=${DTS_MDM_GATEWAY_REGISTRY_AREA_BUSINESS}
DTS_MDM_GATEWAY_REGISTRY_DATA_TYPE=${DTS_MDM_GATEWAY_REGISTRY_DATA_TYPE}
DTS_MDM_GATEWAY_REQUIRED_FIELDS=${DTS_MDM_GATEWAY_REQUIRED_FIELDS}
DTS_MDM_GATEWAY_REQUIRED_USERS=${DTS_MDM_GATEWAY_REQUIRED_USERS}
DTS_MDM_GATEWAY_REQUIRED_DEPTS=${DTS_MDM_GATEWAY_REQUIRED_DEPTS}
DTS_MDM_GATEWAY_ROOT_CODE=${DTS_MDM_GATEWAY_ROOT_CODE}
DTS_MDM_GATEWAY_AUTO_PROVISION_USERS=${DTS_MDM_GATEWAY_AUTO_PROVISION_USERS}
DTS_MDM_GATEWAY_AUTO_PROVISION_ROLES=${DTS_MDM_GATEWAY_AUTO_PROVISION_ROLES}
DTS_MDM_GATEWAY_AUTO_PROVISION_ENABLE_LOGIN=${DTS_MDM_GATEWAY_AUTO_PROVISION_ENABLE_LOGIN}

# ====== Admin IP 白名单（由 init.sh 生成） ======
ADMIN_ALLOWED_IPS=${ADMIN_ALLOWED_IPS}
ADMIN_BACKUP_IPS=${ADMIN_BACKUP_IPS}
ADMIN_WHITELIST_CIDRS=${ADMIN_WHITELIST_CIDRS}

# ====== Ranger (Admin) ======
PG_DB_RANGER=${PG_DB_RANGER}
PG_USER_RANGER=${PG_USER_RANGER}
PG_PWD_RANGER=${PG_PWD_RANGER}
RANGER_ADMIN_PASSWORD=${RANGER_ADMIN_PASSWORD}
RANGER_TAGSYNC_PASSWORD=${RANGER_TAGSYNC_PASSWORD}
RANGER_USERSYNC_PASSWORD=${RANGER_USERSYNC_PASSWORD}

# ====== YTS 服务数据库 ======
IAM_DB_NAME=${IAM_DB_NAME}
IAM_DB_USER=${IAM_DB_USER}
IAM_DB_PASSWORD=${IAM_DB_PASSWORD}
GOVERNANCE_DB_NAME=${GOVERNANCE_DB_NAME}
GOVERNANCE_DB_USER=${GOVERNANCE_DB_USER}
GOVERNANCE_DB_PASSWORD=${GOVERNANCE_DB_PASSWORD}
EXPLORE_DB_NAME=${EXPLORE_DB_NAME}
EXPLORE_DB_USER=${EXPLORE_DB_USER}
EXPLORE_DB_PASSWORD=${EXPLORE_DB_PASSWORD}

# ====== 应用镜像 ======
IMAGE_DTS_ADMIN=${IMAGE_DTS_ADMIN}
IMAGE_DTS_PLATFORM=${IMAGE_DTS_PLATFORM}
IMAGE_DTS_INGESTION=${IMAGE_DTS_INGESTION}
IMAGE_DTS_ADMIN_WEBAPP=${IMAGE_DTS_ADMIN_WEBAPP}
IMAGE_DTS_PLATFORM_WEBAPP=${IMAGE_DTS_PLATFORM_WEBAPP}
IMAGE_DTS_ANALYTICS=${IMAGE_DTS_ANALYTICS}

# ====== 可选能力 ======
DTS_EDITION=${DTS_EDITION}
DTS_LEGACY_METRICS_ENABLED=${DTS_LEGACY_METRICS_ENABLED}
EOF

  if [[ "${DTS_LEGACY_METRICS_ENABLED}" == "true" || "${DTS_LEGACY_METRICS_ENABLED}" == "1" ]]; then
    cat >> "${env_tmp}" <<EOF

# ====== Legacy dts-metrics (disabled in default app stack) ======
PG_DB_METRICS=${PG_DB_METRICS}
PG_USER_METRICS=${PG_USER_METRICS}
PG_PWD_METRICS=${PG_PWD_METRICS}
DTS_INBOUND_FROM_METRICS=${DTS_INBOUND_FROM_METRICS}
DTS_METRICS_TO_PLATFORM=${DTS_METRICS_TO_PLATFORM}
DTS_METRICS_SERVICE_NAME=${DTS_METRICS_SERVICE_NAME}
DTS_METRICS_API_BASE_PATH=${DTS_METRICS_API_BASE_PATH}
IMAGE_DTS_METRICS=${IMAGE_DTS_METRICS}
EOF
  fi

  # Append optional hosts/env blocks conditionally to .env
  if [[ "${ENABLE_MINIO:-false}" == "true" ]]; then
    {
      echo "HOST_MINIO=${HOST_MINIO}"
      echo "MINIO_ROOT_USER=${MINIO_ROOT_USER}"
      echo "MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD}"
      echo "S3_BUCKET=${S3_BUCKET}"
      echo "S3_REGION=${S3_REGION}"
      echo "MINIO_REGION_NAME=${S3_REGION}"
      echo "MINIO_SERVER_URL=${MINIO_SERVER_URL}"
      echo "MINIO_BROWSER_REDIRECT_URL=${MINIO_BROWSER_REDIRECT_URL}"
    } >> "${env_tmp}"
  fi
  if [[ "${ENABLE_NESSIE:-false}" == "true" ]]; then
    echo "HOST_NESSIE=${HOST_NESSIE}" >> "${env_tmp}"
  fi
  mv -f -- "${env_tmp}" .env
  chmod 600 .env || { echo "[init.sh] ERROR: cannot secure generated .env permissions." >&2; return 1; }
}

# ================= argument parsing (kept) =================
while (($#)); do
  case "$1" in
    -h|--help) usage; exit 0;;
    --password) shift; [[ $# -gt 0 ]] || { echo "[init.sh] ERROR: --password requires a value." >&2; exit 1; }; SECRET="$1";;
    --base-domain) shift; [[ $# -gt 0 ]] || { echo "[init.sh] ERROR: --base-domain requires a value." >&2; exit 1; }; [[ -z "$BASE_DOMAIN_ARG" ]] || { echo "[init.sh] ERROR: base domain already provided as '${BASE_DOMAIN_ARG}'." >&2; exit 1; }; BASE_DOMAIN_ARG="$1";;
    legacy|--legacy)
      if [[ "${LEGACY_STACK}" == "true" ]]; then
        echo "[init.sh] ERROR: legacy flag specified multiple times." >&2
        exit 1
      fi
      LEGACY_STACK=true
      ;;
    --reset-pg)
      RESET_PG_DATA=true
      ;;
    --reset-env)
      RESET_ENV=true
      ;;
    --no-force-pg-ensure)
      FORCE_PG_ENSURE=false
      ;;
    app|single) [[ -z "$MODE" ]] || { echo "[init.sh] ERROR: deployment mode already specified as '${MODE}'." >&2; exit 1; }; MODE=app;;
    *)
      if [[ -z "$BASE_DOMAIN_ARG" ]] && looks_like_domain "$1"; then BASE_DOMAIN_ARG="$1"
      elif [[ -z "$SECRET" ]]; then SECRET="$1"
      else echo "[init.sh] ERROR: unexpected argument '$1'." >&2; usage; exit 1
      fi
      ;;
  esac; shift
done

BASE_DOMAIN="${BASE_DOMAIN:-}"
if [[ -n "$BASE_DOMAIN_ARG" ]]; then BASE_DOMAIN="$(normalize_base_domain "$BASE_DOMAIN_ARG")"; fi

if [[ -z "$BASE_DOMAIN" && -f .env ]]; then
  existing_base_domain="$(grep -E '^BASE_DOMAIN=' .env | head -n1 | cut -d= -f2- | tr -d '\r')"
  if [[ -n "${existing_base_domain}" ]]; then BASE_DOMAIN="$(normalize_base_domain "${existing_base_domain}")"; fi
fi

DEFAULT_BASE_DOMAIN="${BASE_DOMAIN:-dts.local}"
DEFAULT_BASE_DOMAIN="$(normalize_base_domain "$DEFAULT_BASE_DOMAIN")"

if [[ -z "$BASE_DOMAIN" ]]; then
  if [[ -t 0 ]]; then prompt_base_domain "$DEFAULT_BASE_DOMAIN"; else BASE_DOMAIN="$DEFAULT_BASE_DOMAIN"; fi
fi

BASE_DOMAIN="$(normalize_base_domain "$BASE_DOMAIN")"
if ! validate_base_domain "$BASE_DOMAIN"; then echo "[init.sh] ERROR: invalid base domain '${BASE_DOMAIN}'." >&2; exit 1; fi

if [[ "${LEGACY_STACK}" == "true" && -z "${MODE}" ]]; then
  MODE="app"
fi

if [[ -z "${MODE}" ]]; then pick_mode; else case "$MODE" in app) ;; *) usage; exit 1;; esac; fi

if [[ "${LEGACY_STACK}" == "true" && "${MODE}" != "app" ]]; then
  echo "[init.sh] ERROR: legacy stack currently supports only app mode." >&2
  exit 1
fi
if [[ -z "${SECRET}" ]]; then read_secret; else [[ ${#SECRET} -ge 10 && "$SECRET" =~ [A-Z] && "$SECRET" =~ [a-z] && "$SECRET" =~ [0-9] && "$SECRET" =~ [^A-Za-z0-9] ]] || { echo "Weak password"; exit 1; } fi

PG_MODE="${PG_MODE:-}"
PG_HOST="${PG_HOST:-}"
COMPOSE_FILE="docker-compose-app.yml"

case "$MODE" in
  app)
    COMPOSE_FILE="docker-compose-app.yml"
    PG_MODE="${PG_MODE:-embedded}"
    PG_HOST="${PG_HOST:-dts-pg}"
    ;;
esac

if [[ "${LEGACY_STACK}" == "true" ]]; then
  COMPOSE_FILE="docker-compose.legacy.yml"
  PG_MODE="embedded"
  PG_HOST="dts-pg"
fi

if [[ "${PG_MODE}" == "external" && "${PG_HOST}" == "your-external-pg-host" ]]; then
  if [[ -t 0 ]]; then
    read -rp "[init.sh] Enter the hostname or IP for the external PostgreSQL instance: " input_pg_host
    if [[ -n "${input_pg_host}" ]]; then PG_HOST="${input_pg_host}"; fi
  fi
  if [[ "${PG_HOST}" == "your-external-pg-host" ]]; then
    echo "[init.sh] WARNING: PG_HOST is still set to 'your-external-pg-host'. Update it in .env before starting services." >&2
  fi
fi

# 生成 .env（在生成前判定可选服务开关）
determine_enabled_services
set_default_log_root
generate_env_base
detect_docker_api_version
ensure_env PG_MODE "${PG_MODE}"
ensure_env PG_HOST "${PG_HOST}"
ensure_env LEGACY_STACK "${LEGACY_STACK}"

# 加载镜像版本 & 目录
load_img_versions
if [[ "${LEGACY_STACK}" == "true" ]]; then
  if [[ -z "${IMAGE_DBT:-}" || "${IMAGE_DBT}" == "ghcr.io/dbt-labs/dbt-core:1.11.2" || "${IMAGE_DBT}" == "dbt-core:1.11.2" ]]; then
    ensure_env IMAGE_DBT "dts-dbt:1.11.2"
    echo "[init.sh] Using custom dbt image for legacy stack: dts-dbt:1.11.2"
  fi
else
  if [[ -z "${IMAGE_DBT:-}" || "${IMAGE_DBT}" == "ghcr.io/dbt-labs/dbt-core:1.11.2" || "${IMAGE_DBT}" == "dbt-core:1.11.2" || "${IMAGE_DBT}" == "dts-dbt:1.11.2" ]]; then
    ensure_env IMAGE_DBT "dts-dbt:1.10.0"
    echo "[init.sh] Using custom dbt image for non-legacy stack: dts-dbt:1.10.0"
  fi
fi

if [[ "${RESET_ENV}" == "true" ]]; then
  echo "[init.sh] Resetting .env (requested)."
  rm -f .env
  generate_env_base
  load_img_versions
  if [[ "${LEGACY_STACK}" == "true" ]]; then
    if [[ -z "${IMAGE_DBT:-}" || "${IMAGE_DBT}" == "ghcr.io/dbt-labs/dbt-core:1.11.2" || "${IMAGE_DBT}" == "dbt-core:1.11.2" ]]; then
      ensure_env IMAGE_DBT "dts-dbt:1.11.2"
      echo "[init.sh] Using custom dbt image for legacy stack: dts-dbt:1.11.2"
    fi
  else
    if [[ -z "${IMAGE_DBT:-}" || "${IMAGE_DBT}" == "ghcr.io/dbt-labs/dbt-core:1.11.2" || "${IMAGE_DBT}" == "dbt-core:1.11.2" || "${IMAGE_DBT}" == "dts-dbt:1.11.2" ]]; then
      ensure_env IMAGE_DBT "dts-dbt:1.10.0"
      echo "[init.sh] Using custom dbt image for non-legacy stack: dts-dbt:1.10.0"
    fi
  fi
fi

if [[ "${RESET_PG_DATA}" == "true" ]]; then
  echo "[init.sh] Resetting Postgres data directory (requested)."
  reset_pg_data_dir
fi
prepare_data_dirs

ensure_airflow_openmetadata_plugin

warn_if_ima_appraise

# 记录部署模式
if [[ -n "${MODE}" ]]; then ensure_env DEPLOY_MODE "${MODE}"; fi

# 证书
if [[ "${MODE}" == "app" ]]; then
  if ! BASE_DOMAIN="${BASE_DOMAIN}" TRUSTSTORE_PASSWORD="${TRUSTSTORE_PASSWORD:-changeit}" bash services/certs/gen-certs.sh; then
    echo "[init.sh] ERROR: Failed to generate TLS certificates/truststores." >&2
    exit 1
  fi
else
  if [[ ! -f services/certs/server.crt || ! -f services/certs/server.key ]]; then
    echo "[init.sh] ERROR: Production mode requires a CA-issued TLS certificate at services/certs/server.crt and services/certs/server.key." >&2
    exit 1
  fi
fi

if [[ "${LEGACY_STACK}" == "true" ]]; then
  echo "[init.sh] Legacy stack enabled; using docker-compose.legacy.yml."
fi

echo "[init.sh] Starting with ${COMPOSE_FILE} ..."
compose_cli=()
if [[ "${LEGACY_STACK}" == "true" ]]; then
  if command -v docker-compose >/dev/null 2>&1; then
    compose_cli=(docker-compose)
  elif docker compose version >/dev/null 2>&1; then
    compose_cli=(docker compose)
  # Legacy/offline helper package (see tools/compose/*/upgrade.txt)
  elif [[ -x /opt/dcenv/bin/docker-compose ]]; then
    compose_cli=(/opt/dcenv/bin/docker-compose)
  else
    bundled=""
    bundled="$(find_bundled_docker_compose "${SCRIPT_DIR}/tools/docker-compose" 2>/dev/null || true)"
    if [[ -n "${bundled}" ]]; then
      compose_cli=("${bundled}")
    else
      bundled="$(find_bundled_docker_compose "${SCRIPT_DIR}/builds/docker-compose" 2>/dev/null || true)"
      if [[ -n "${bundled}" ]]; then
        compose_cli=("${bundled}")
      elif [[ -x /usr/local/bin/docker-compose ]]; then
        compose_cli=(/usr/local/bin/docker-compose)
      elif [[ -x /usr/bin/docker-compose ]]; then
        compose_cli=(/usr/bin/docker-compose)
      elif [[ -x /usr/libexec/docker/cli-plugins/docker-compose ]]; then
        compose_cli=(/usr/libexec/docker/cli-plugins/docker-compose)
      elif [[ -x /usr/lib/docker/cli-plugins/docker-compose ]]; then
        compose_cli=(/usr/lib/docker/cli-plugins/docker-compose)
      elif [[ -x "${HOME}/.docker/cli-plugins/docker-compose" ]]; then
        compose_cli=("${HOME}/.docker/cli-plugins/docker-compose")
      else
        cat <<'EOF' >&2
[init.sh] ERROR: Compose is not available.
[init.sh] Legacy mode requires Docker Compose (v1 docker-compose or v2 docker compose plugin).
[init.sh] Install one of:
[init.sh]   - docker-compose (recommended for legacy hosts; v1.22+)
[init.sh]   - docker compose plugin (v2)
[init.sh] Offline option:
[init.sh]   - put a docker-compose binary at:
[init.sh]       ./tools/docker-compose/docker-compose
[init.sh]       ./tools/docker-compose/docker-compose-Linux-aarch64  (or -Linux-arm64)
[init.sh]       ./tools/docker-compose/docker-compose-Linux-x86_64   (or -Linux-amd64)
[init.sh]     (also supports ./builds/docker-compose/ with same names)
[init.sh]   - or use the bundled venv binary if you have it:
[init.sh]       /opt/dcenv/bin/docker-compose
[init.sh] Then re-run: ./init.sh legacy ...
EOF
        exit 1
      fi
    fi
  fi
else
  if docker compose version >/dev/null 2>&1; then
    compose_cli=(docker compose)
  elif command -v docker-compose >/dev/null 2>&1; then
    compose_cli=(docker-compose)
  elif [[ -x /opt/dcenv/bin/docker-compose ]]; then
    compose_cli=(/opt/dcenv/bin/docker-compose)
  else
    bundled=""
    bundled="$(find_bundled_docker_compose "${SCRIPT_DIR}/tools/docker-compose" 2>/dev/null || true)"
    if [[ -n "${bundled}" ]]; then
      compose_cli=("${bundled}")
    else
      bundled="$(find_bundled_docker_compose "${SCRIPT_DIR}/builds/docker-compose" 2>/dev/null || true)"
      if [[ -n "${bundled}" ]]; then
        compose_cli=("${bundled}")
      else
        echo "[init.sh] ERROR: docker compose not found." >&2
        exit 1
      fi
    fi
  fi
fi

print_compose_info

compose_run=("${compose_cli[@]}")
if [[ -f .env ]]; then
  # Compose v2 supports --env-file; docker-compose v1 does not.
  if "${compose_cli[@]}" --help 2>/dev/null | grep -q -- '--env-file'; then
    compose_run+=(--env-file .env)
  fi
fi
if [[ -n "${COMPOSE_FILE}" ]]; then
  compose_run+=(-f "${COMPOSE_FILE}")
fi

prepare_dbt_runtime_profile_root

wait_for_service_healthy() {
  local svc="$1"
  local max_wait="${2:-60}"
  local waited=0
  local cid=""
  cid="$("${compose_run[@]}" ps -q "${svc}" 2>/dev/null | head -n 1 || true)"
  if [[ -z "${cid}" ]]; then
    return 1
  fi
  while (( waited < max_wait )); do
    local status
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "${cid}" 2>/dev/null || echo none)"
    if [[ "${status}" == "healthy" ]]; then
      return 0
    fi
    if [[ "${status}" == "none" ]]; then
      # No healthcheck defined; treat as ready.
      return 0
    fi
    sleep 2
    waited=$(( waited + 2 ))
  done
  return 1
}

if [[ "${PG_MODE}" == "embedded" ]]; then
  # 先启动 Postgres，确保用户/库就绪，再启动其余服务，避免依赖服务初始化竞态
  echo "[init.sh] Bringing up Postgres first to prepare roles/databases ..."
  "${compose_run[@]}" up -d dts-pg
  # 等待容器起来后放宽一点时间
  sleep 2
  fix_pg_permissions
  # 等待 ready
  for i in {1..5}; do
    if "${compose_run[@]}" exec -T dts-pg bash -lc "pg_isready -h 127.0.0.1 -p ${PG_PORT} -U ${PG_SUPER_USER} -d postgres" >/dev/null 2>&1; then
      break
    fi
    echo "[init.sh] Waiting for Postgres to be ready ... (${i}/5)" >&2
    sleep 2
  done
  # 收敛角色/数据库（幂等）
  ensure_pg_triplets
  # Bring up Airflow first, then OpenMetadata, then the rest to avoid API detection races.
  echo "[init.sh] Bringing up Airflow services ..."
  "${compose_run[@]}" up -d dts-airflow-init dts-airflow-webserver dts-airflow-scheduler dts-airflow-triggerer >/dev/null 2>&1 || true
  if ! wait_for_service_healthy dts-airflow-webserver 90; then
    echo "[init.sh] WARNING: dts-airflow-webserver not healthy yet; continuing." >&2
  fi
  # Run OpenMetadata migrations before starting OpenMetadata server (idempotent).
  echo "[init.sh] Running OpenMetadata migrations (dts-openmetadata-init) ..."
  "${compose_run[@]}" up -d dts-openmetadata-init >/dev/null 2>&1 || \
    echo "[init.sh] WARNING: dts-openmetadata-init did not start; continuing." >&2
  echo "[init.sh] Bringing up OpenMetadata ..."
  "${compose_run[@]}" up -d dts-openmetadata >/dev/null 2>&1 || true
  echo "[init.sh] Bringing up the remaining services ..."
  "${compose_run[@]}" up -d
  if [[ "${FORCE_PG_ENSURE}" == "true" ]]; then
    echo "[init.sh] Re-running Postgres ensure script after stack up (forced)."
    ensure_pg_triplets
  fi
else
  # 外部 PG：直接启动全部服务
  echo "[init.sh] Bringing up Airflow services ..."
  "${compose_run[@]}" up -d dts-airflow-init dts-airflow-webserver dts-airflow-scheduler dts-airflow-triggerer >/dev/null 2>&1 || true
  if ! wait_for_service_healthy dts-airflow-webserver 90; then
    echo "[init.sh] WARNING: dts-airflow-webserver not healthy yet; continuing." >&2
  fi
  echo "[init.sh] Running OpenMetadata migrations (dts-openmetadata-init) ..."
  "${compose_run[@]}" up -d dts-openmetadata-init >/dev/null 2>&1 || \
    echo "[init.sh] WARNING: dts-openmetadata-init did not start; continuing." >&2
  "${compose_run[@]}" up -d dts-openmetadata >/dev/null 2>&1 || true
  "${compose_run[@]}" up -d
fi

# 输出可访问地址
host_vars=(HOST_SSO HOST_TRINO HOST_RANGER HOST_API HOST_ADMIN_UI HOST_PLATFORM_UI HOST_ANALYTICS HOST_META HOST_FLOW)
if [[ "${ENABLE_MINIO:-false}" == "true" ]]; then host_vars+=(HOST_MINIO); fi
if [[ "${ENABLE_NESSIE:-false}" == "true" ]]; then host_vars+=(HOST_NESSIE); fi
for host_var in "${host_vars[@]}"; do
  host_value="$(grep "^${host_var}=" .env | cut -d= -f2-)"
  if [[ -n "${host_value}" ]]; then echo "https://${host_value}"; fi
done
TRAEFIK_DASHBOARD_ENABLED="$(grep '^TRAEFIK_DASHBOARD=' .env | cut -d= -f2)"
if [[ "${TRAEFIK_DASHBOARD_ENABLED}" == "true" ]]; then
  TRAEFIK_DASHBOARD_PORT_VALUE="$(grep '^TRAEFIK_DASHBOARD_PORT=' .env | cut -d= -f2)"
  echo "http://localhost:${TRAEFIK_DASHBOARD_PORT_VALUE} (local Traefik dashboard via --api.insecure)"
fi

echo "[init.sh] OIDC settings:"
echo "  SPRING_DATASOURCE_URL=jdbc:postgresql://dts-pg:\${PG_PORT}/\${DTADMIN_DB_NAME}"
echo "  SPRING_DATASOURCE_USERNAME=\${DTADMIN_DB_USER}  SPRING_DATASOURCE_PASSWORD=\${DTADMIN_DB_PASSWORD}"
echo "  Issuer: https://\${HOST_SSO}/realms/\${KC_REALM}"
echo "  Admin client: \${OAUTH2_ADMIN_CLIENT_ID}  Secret: (use Keycloak value)"
echo "  Platform client: \${OAUTH2_PLATFORM_CLIENT_ID}  Secret: (use Keycloak value)"
