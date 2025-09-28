#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

MODE=""
SECRET=""
BASE_DOMAIN_ARG=""

usage(){ echo "Usage: $0 [single|ha2|cluster] [unified-password] [base-domain]"; }

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
pick_mode(){ echo "1) single  2) ha2  3) cluster"; read -rp "Choice: " c; case "$c" in 1) MODE=single;;2) MODE=ha2;;3) MODE=cluster;;*) exit 1;; esac; }
read_secret(){ while true; do read -rsp "Password: " p1; echo; read -rsp "Confirm: " p2; echo; [[ "$p1" == "$p2" ]] || { echo "Mismatch"; continue; }; [[ ${#p1} -ge 10 && "$p1" =~ [A-Z] && "$p1" =~ [a-z] && "$p1" =~ [0-9] && "$p1" =~ [^A-Za-z0-9] ]] || { echo "Weak"; continue; }; SECRET="$p1"; break; done; }
ensure_env(){ k="$1"; shift; v="$*"; if grep -qE "^${k}=" .env 2>/dev/null; then sed -i -E "s|^${k}=.*|${k}=${v}|g" .env; else echo "${k}=${v}" >> .env; fi; }
load_img_versions(){ conf="imgversion.conf"; [[ -f "$conf" ]] || return 0; while IFS='=' read -r k v; do [[ -z "${k// }" || "${k#\#}" != "$k" ]] && continue; v="$(echo "$v"|sed -E 's/^\s+|\s+$//g')"; ensure_env "$k" "$v"; done < <(grep -E '^[[:space:]]*([A-Z0-9_]+)[[:space:]]*=' "$conf" || true); echo "[init.sh] loaded image versions"; }

fix_pg_permissions(){
  if [[ "${PG_MODE:-}" != "embedded" ]]; then
    return
  fi
  local pg_dir="services/dts-pg/data"
  mkdir -p "${pg_dir}"
  local pg_runtime_uid="${PG_RUNTIME_UID:-999}"
  local pg_runtime_gid="${PG_RUNTIME_GID:-${pg_runtime_uid}}"
  if command -v setfacl >/dev/null 2>&1; then
    setfacl -R -m u:"${pg_runtime_uid}":rwx "${pg_dir}" 2>/dev/null || true
    setfacl -R -d -m u:"${pg_runtime_uid}":rwx "${pg_dir}" 2>/dev/null || true
  else
    echo "[init.sh] WARNING: setfacl not found, falling back to chmod 777 on Postgres data directory." >&2
    chmod -R 777 "${pg_dir}" 2>/dev/null || true
  fi
  chown -R "${pg_runtime_uid}:${pg_runtime_gid}" "${pg_dir}" 2>/dev/null || true
}

prepare_data_dirs(){
  fix_pg_permissions
  local -a data_dirs=(
    "services/certs"
    "services/dts-minio/data"
    "services/dts-airflow/dags"
    "services/dts-airflow/logs"
    "services/dts-airflow/plugins"
    "services/dts-ranger"
  )
  local dir
  for dir in "${data_dirs[@]}"; do
    mkdir -p "${dir}"
  done
  chmod -R 777 services/dts-minio/data || true
  chmod -R 777 services/dts-airflow/logs || true
  chmod -R 777 services/dts-airflow/dags || true
  chmod -R 777 services/dts-airflow/plugins || true
}

# Ensure embedded Postgres has all required roles/databases
ensure_pg_triplets(){
  if [[ "${PG_MODE}" != "embedded" ]]; then
    return
  fi
  echo "[init.sh] Ensuring Postgres roles/databases (idempotent)..."
  local i
  for i in {1..30}; do
    if "${compose_cmd[@]}" exec -T dts-pg bash -lc \
      "/docker-entrypoint-initdb.d/99-ensure-users-runtime.sh"; then
      echo "[init.sh] Postgres roles/databases ensured."
      return
    fi
    echo "[init.sh] Waiting for dts-pg to accept ensure script... (${i}/30)" >&2
    sleep 2
  done
  echo "[init.sh] WARNING: Could not ensure PG roles/DBs automatically. You can run: docker compose exec dts-pg bash -lc '/docker-entrypoint-initdb.d/99-ensure-users-runtime.sh'" >&2
}

generate_fernet(){
  if command -v python >/dev/null 2>&1; then
    python - <<'PY' || true
from cryptography.fernet import Fernet
print(Fernet.generate_key().decode())
PY
  elif command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 32 || true
  else
    # 最后兜底：弱一些，但可用
    head -c 32 /dev/urandom | base64 || true
  fi
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
  if [ -f .env ]; then
    set -a; . ./.env; set +a
  fi

  : "${BASE_DOMAIN:=dts.local}"
  : "${TLS_PORT:=443}"

  if [[ "${MODE}" == "single" ]]; then
    : "${TRAEFIK_DASHBOARD:=true}"
  else
    : "${TRAEFIK_DASHBOARD:=false}"
  fi
  : "${TRAEFIK_DASHBOARD_PORT:=8080}"
  : "${TRAEFIK_METRICS_PORT:=9100}"
  : "${TRAEFIK_ENABLE_PING:=true}"

  # ---------- Keycloak ----------
  : "${KC_ADMIN:=admin}"
  : "${KC_ADMIN_PWD:=${SECRET}}"
  : "${KC_HTTP_ENABLED:=true}"
  : "${KC_HOSTNAME:=sso.${BASE_DOMAIN}}"
  : "${KC_HOSTNAME_PORT:=${TLS_PORT}}"
  : "${KC_HOSTNAME_STRICT:=true}"
  : "${KC_HOSTNAME_STRICT_HTTPS:=true}"
  : "${KC_HOSTNAME_URL:=https://${KC_HOSTNAME}}"
  : "${KC_DB_URL_PROPERTIES:=sslmode=disable}"
  : "${KC_REALM:=dts-platform}"

  # ---------- 域名（Traefik 路由） ----------
  HOST_SSO="sso.${BASE_DOMAIN}"
  HOST_MINIO="minio.${BASE_DOMAIN}"
  HOST_TRINO="trino.${BASE_DOMAIN}"
  HOST_NESSIE="nessie.${BASE_DOMAIN}"
  HOST_AIRFLOW="airflow.${BASE_DOMAIN}"
  HOST_PORTAL="portal.${BASE_DOMAIN}"
  HOST_RANGER="ranger.${BASE_DOMAIN}"

  # ---------- MinIO/S3 (placed before Airflow uses it) ----------
  : "${MINIO_ROOT_USER:=minio}"
  : "${MINIO_ROOT_PASSWORD:=${SECRET}}"
  : "${S3_BUCKET:=dts-lake}"
  : "${S3_REGION:=cn-local-1}"

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


  # Airflow
  : "${PG_DB_AIRFLOW:=airflow}"
  : "${PG_USER_AIRFLOW:=airflow}"
  : "${PG_PWD_AIRFLOW:=${SECRET}}"
  : "${AIRFLOW_ADMIN_USER:=admin}"
  : "${AIRFLOW_ADMIN_PASSWORD:=${SECRET}}"
  : "${AIRFLOW_ADMIN_EMAIL:=admin@example.com}"
  : "${AIRFLOW_SECRET_KEY:=${SECRET}}"
  : "${AIRFLOW_FERNET_KEY:=${AIRFLOW_FERNET_KEY:-}}"
  : "${AIRFLOW_UID:=50000}"
  : "${AIRFLOW_GID:=0}"
  : "${AIRFLOW__CORE__EXECUTOR:=LocalExecutor}"
  : "${AIRFLOW__CORE__LOAD_EXAMPLES:=false}"
  : "${AIRFLOW__CORE__DAGS_ARE_PAUSED_AT_CREATION:=false}"
  : "${AIRFLOW__LOGGING__REMOTE_LOGGING:=true}"
  : "${AIRFLOW__LOGGING__REMOTE_LOG_CONN_ID:=s3_minio}"
  if [[ -z "${AIRFLOW_FERNET_KEY}" ]]; then
    AIRFLOW_FERNET_KEY="$(generate_fernet || true)"
  fi
  local AF_USER_ENC AF_PWD_ENC
  AF_USER_ENC="$(urlencode_component "${PG_USER_AIRFLOW}")"
  AF_PWD_ENC="$(urlencode_component "${PG_PWD_AIRFLOW}")"
  AIRFLOW__DATABASE__SQL_ALCHEMY_CONN="postgresql+psycopg2://${AF_USER_ENC}:${AF_PWD_ENC}@${PG_HOST}:${PG_PORT}/${PG_DB_AIRFLOW}"
  AIRFLOW__LOGGING__REMOTE_BASE_LOG_FOLDER="s3://${S3_BUCKET}/airflow/logs"
  AIRFLOW__CONNECTIONS__S3_MINIO__CONN_TYPE="s3"
  AIRFLOW__CONNECTIONS__S3_MINIO__EXTRA="{\"host\":\"https://${HOST_MINIO}\",\"aws_access_key_id\":\"${MINIO_ROOT_USER}\",\"aws_secret_access_key\":\"${MINIO_ROOT_PASSWORD}\",\"region_name\":\"${S3_REGION}\",\"verify\":false,\"client_kwargs\":{\"endpoint_url\":\"https://${HOST_MINIO}\",\"aws_access_key_id\":\"${MINIO_ROOT_USER}\",\"aws_secret_access_key\":\"${MINIO_ROOT_PASSWORD}\",\"region_name\":\"${S3_REGION}\"}}"

  # ---------- Ranger（Admin） ----------
  : "${PG_DB_RANGER:=dts_ranger}"
  : "${PG_USER_RANGER:=dts_ranger}"
  : "${PG_PWD_RANGER:=${SECRET}}"
  : "${RANGER_ADMIN_PASSWORD:=${SECRET}}"
  : "${RANGER_TAGSYNC_PASSWORD:=${SECRET}}"
  : "${RANGER_USERSYNC_PASSWORD:=${SECRET}}"

  # --- YTS 服务数据库 ---
  : "${IAM_DB_NAME:=iam}"
  : "${IAM_DB_USER:=iam}"
  : "${IAM_DB_PASSWORD:=${SECRET}}"
  : "${GOVERNANCE_DB_NAME:=governance}"
  : "${GOVERNANCE_DB_USER:=governance}"
  : "${GOVERNANCE_DB_PASSWORD:=${SECRET}}"
  : "${EXPLORE_DB_NAME:=explore}"
  : "${EXPLORE_DB_USER:=explore}"
  : "${EXPLORE_DB_PASSWORD:=${SECRET}}"

  # ---------- OIDC 客户端（供 dts-admin 使用） ----------
  : "${OAUTH2_CLIENT_ID:=dts-admin}"
  : "${OAUTH2_CLIENT_SECRET:=${SECRET}}"

  cat > .env <<EOF
# ====== Base & Traefik ======
BASE_DOMAIN=${BASE_DOMAIN}
TLS_PORT=${TLS_PORT}
TRAEFIK_DASHBOARD=${TRAEFIK_DASHBOARD}
TRAEFIK_DASHBOARD_PORT=${TRAEFIK_DASHBOARD_PORT}
TRAEFIK_METRICS_PORT=${TRAEFIK_METRICS_PORT}
TRAEFIK_ENABLE_PING=${TRAEFIK_ENABLE_PING}

# ====== Hosts ======
HOST_SSO=${HOST_SSO}
HOST_MINIO=${HOST_MINIO}
HOST_TRINO=${HOST_TRINO}
HOST_NESSIE=${HOST_NESSIE}
HOST_AIRFLOW=${HOST_AIRFLOW}
HOST_PORTAL=${HOST_PORTAL}
HOST_RANGER=${HOST_RANGER}

# ====== Keycloak ======
KC_ADMIN=${KC_ADMIN}
KC_ADMIN_PWD=${KC_ADMIN_PWD}
KC_HTTP_ENABLED=${KC_HTTP_ENABLED}
KC_HOSTNAME=${KC_HOSTNAME}
KC_HOSTNAME_PORT=${KC_HOSTNAME_PORT}
KC_HOSTNAME_URL=${KC_HOSTNAME_URL}
KC_HOSTNAME_STRICT=${KC_HOSTNAME_STRICT}
KC_HOSTNAME_STRICT_HTTPS=${KC_HOSTNAME_STRICT_HTTPS}
KC_DB_URL_PROPERTIES=${KC_DB_URL_PROPERTIES}
KC_REALM=${KC_REALM}

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

# --- Airflow triplet + admin/secrets ---
PG_DB_AIRFLOW=${PG_DB_AIRFLOW}
PG_USER_AIRFLOW=${PG_USER_AIRFLOW}
PG_PWD_AIRFLOW=${PG_PWD_AIRFLOW}
AIRFLOW_ADMIN_USER=${AIRFLOW_ADMIN_USER}
AIRFLOW_ADMIN_PASSWORD=${AIRFLOW_ADMIN_PASSWORD}
AIRFLOW_ADMIN_EMAIL=${AIRFLOW_ADMIN_EMAIL}
AIRFLOW_SECRET_KEY=${AIRFLOW_SECRET_KEY}
AIRFLOW_FERNET_KEY=${AIRFLOW_FERNET_KEY}
AIRFLOW_UID=${AIRFLOW_UID}
AIRFLOW_GID=${AIRFLOW_GID}
AIRFLOW__CORE__EXECUTOR=${AIRFLOW__CORE__EXECUTOR}
AIRFLOW__CORE__LOAD_EXAMPLES=${AIRFLOW__CORE__LOAD_EXAMPLES}
AIRFLOW__CORE__DAGS_ARE_PAUSED_AT_CREATION=${AIRFLOW__CORE__DAGS_ARE_PAUSED_AT_CREATION}
AIRFLOW__LOGGING__REMOTE_LOGGING=${AIRFLOW__LOGGING__REMOTE_LOGGING}
AIRFLOW__DATABASE__SQL_ALCHEMY_CONN=${AIRFLOW__DATABASE__SQL_ALCHEMY_CONN}
AIRFLOW__LOGGING__REMOTE_BASE_LOG_FOLDER=${AIRFLOW__LOGGING__REMOTE_BASE_LOG_FOLDER}
AIRFLOW__LOGGING__REMOTE_LOG_CONN_ID=${AIRFLOW__LOGGING__REMOTE_LOG_CONN_ID}
AIRFLOW__CONNECTIONS__S3_MINIO__CONN_TYPE=${AIRFLOW__CONNECTIONS__S3_MINIO__CONN_TYPE}
AIRFLOW__CONNECTIONS__S3_MINIO__EXTRA=${AIRFLOW__CONNECTIONS__S3_MINIO__EXTRA}

# --- Optional services (avoid compose warnings) ---
PG_DB_AIRBYTE=${PG_DB_AIRBYTE}
PG_USER_AIRBYTE=${PG_USER_AIRBYTE}
PG_PWD_AIRBYTE=${PG_PWD_AIRBYTE}
PG_DB_OM=${PG_DB_OM}
PG_USER_OM=${PG_USER_OM}
PG_PWD_OM=${PG_PWD_OM}
PG_DB_TEMPORAL=${PG_DB_TEMPORAL}
PG_USER_TEMPORAL=${PG_USER_TEMPORAL}
PG_PWD_TEMPORAL=${PG_PWD_TEMPORAL}

# ====== MinIO / S3 ======
MINIO_ROOT_USER=${MINIO_ROOT_USER}
MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD}
S3_BUCKET=${S3_BUCKET}
S3_REGION=${S3_REGION}

# ====== OIDC Client for dts-admin ======
OAUTH2_CLIENT_ID=${OAUTH2_CLIENT_ID}
OAUTH2_CLIENT_SECRET=${OAUTH2_CLIENT_SECRET}

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
EOF
}

# ================= argument parsing (kept) =================
while (($#)); do
  case "$1" in
    -h|--help) usage; exit 0;;
    --password) shift; [[ $# -gt 0 ]] || { echo "[init.sh] ERROR: --password requires a value." >&2; exit 1; }; SECRET="$1";;
    --base-domain) shift; [[ $# -gt 0 ]] || { echo "[init.sh] ERROR: --base-domain requires a value." >&2; exit 1; }; [[ -z "$BASE_DOMAIN_ARG" ]] || { echo "[init.sh] ERROR: base domain already provided as '${BASE_DOMAIN_ARG}'." >&2; exit 1; }; BASE_DOMAIN_ARG="$1";;
    single|ha2|cluster) [[ -z "$MODE" ]] || { echo "[init.sh] ERROR: deployment mode already specified as '${MODE}'." >&2; exit 1; }; MODE="$1";;
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

if [[ -z "${MODE}" ]]; then pick_mode; else case "$MODE" in single|ha2|cluster) ;; *) usage; exit 1;; esac; fi
if [[ -z "${SECRET}" ]]; then read_secret; else [[ ${#SECRET} -ge 10 && "$SECRET" =~ [A-Z] && "$SECRET" =~ [a-z] && "$SECRET" =~ [0-9] && "$SECRET" =~ [^A-Za-z0-9] ]] || { echo "Weak password"; exit 1; } fi

PG_MODE="${PG_MODE:-}"
PG_HOST="${PG_HOST:-}"
COMPOSE_FILE="docker-compose.yml"

case "$MODE" in
  single)
    COMPOSE_FILE="docker-compose.yml"
    PG_MODE="${PG_MODE:-embedded}"
    PG_HOST="${PG_HOST:-dts-pg}"
    ;;
  ha2)
    COMPOSE_FILE="docker-compose.ha2.yml"
    PG_MODE="${PG_MODE:-external}"
    PG_HOST="${PG_HOST:-your-external-pg-host}"
    ;;
  cluster)
    COMPOSE_FILE="docker-compose.cluster.yml"
    PG_MODE="${PG_MODE:-external}"
    PG_HOST="${PG_HOST:-your-external-pg-host}"
    ;;
esac

if [[ "${PG_MODE}" == "external" && "${PG_HOST}" == "your-external-pg-host" ]]; then
  if [[ -t 0 ]]; then
    read -rp "[init.sh] Enter the hostname or IP for the external PostgreSQL instance: " input_pg_host
    if [[ -n "${input_pg_host}" ]]; then PG_HOST="${input_pg_host}"; fi
  fi
  if [[ "${PG_HOST}" == "your-external-pg-host" ]]; then
    echo "[init.sh] WARNING: PG_HOST is still set to 'your-external-pg-host'. Update it in .env before starting services." >&2
  fi
fi

# 生成 .env
generate_env_base
ensure_env PG_MODE "${PG_MODE}"
ensure_env PG_HOST "${PG_HOST}"

# 加载镜像版本 & 目录
load_img_versions
prepare_data_dirs

# 记录部署模式
if [[ -n "${MODE}" ]]; then ensure_env DEPLOY_MODE "${MODE}"; fi

# 证书
if [[ "${MODE}" == "single" ]]; then
  BASE_DOMAIN="${BASE_DOMAIN}" bash services/certs/gen-certs.sh || true
else
  if [[ ! -f services/certs/server.crt || ! -f services/certs/server.key ]]; then
    echo "[init.sh] ERROR: Production mode requires a CA-issued TLS certificate at services/certs/server.crt and services/certs/server.key." >&2
    exit 1
  fi
fi

echo "[init.sh] Starting with ${COMPOSE_FILE} ..."
compose_cmd=()
if docker compose version >/dev/null 2>&1; then
  compose_cmd=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  compose_cmd=(docker-compose)
else
  echo "docker compose not found"; exit 1
fi

if [[ "${PG_MODE}" == "embedded" ]]; then
  # 先启动 Postgres，确保用户/库就绪，再启动其余服务，避免依赖服务初始化竞态
  echo "[init.sh] Bringing up Postgres first to prepare roles/databases ..."
  "${compose_cmd[@]}" -f "${COMPOSE_FILE}" up -d dts-pg
  # 等待容器起来后放宽一点时间
  sleep 2
  fix_pg_permissions
  # 等待 ready
  for i in {1..60}; do
    if "${compose_cmd[@]}" exec -T dts-pg bash -lc "pg_isready -h 127.0.0.1 -p ${PG_PORT} -U ${PG_SUPER_USER} -d postgres" >/dev/null 2>&1; then
      break
    fi
    echo "[init.sh] Waiting for Postgres to be ready ... (${i}/60)" >&2
    sleep 2
  done
  # 收敛角色/数据库（幂等）
  ensure_pg_triplets
  echo "[init.sh] Bringing up the remaining services ..."
  "${compose_cmd[@]}" -f "${COMPOSE_FILE}" up -d
else
  # 外部 PG：直接启动全部服务
  "${compose_cmd[@]}" -f "${COMPOSE_FILE}" up -d
fi

# 输出可访问地址
for host_var in HOST_SSO HOST_MINIO HOST_TRINO HOST_NESSIE HOST_AIRFLOW HOST_RANGER; do
  host_value="$(grep "^${host_var}=" .env | cut -d= -f2-)"
  if [[ -n "${host_value}" ]]; then echo "https://${host_value}"; fi
done
TRAEFIK_DASHBOARD_ENABLED="$(grep '^TRAEFIK_DASHBOARD=' .env | cut -d= -f2)"
if [[ "${TRAEFIK_DASHBOARD_ENABLED}" == "true" ]]; then
  TRAEFIK_DASHBOARD_PORT_VALUE="$(grep '^TRAEFIK_DASHBOARD_PORT=' .env | cut -d= -f2)"
  echo "http://localhost:${TRAEFIK_DASHBOARD_PORT_VALUE} (local Traefik dashboard via --api.insecure)"
fi

# 额外提示：dts-admin OIDC 回调与变量对齐
echo "[init.sh] dts-admin will use:"
echo "  SPRING_DATASOURCE_URL=jdbc:postgresql://dts-pg:\${PG_PORT}/\${DTADMIN_DB_NAME}"
echo "  SPRING_DATASOURCE_USERNAME=\${DTADMIN_DB_USER}  SPRING_DATASOURCE_PASSWORD=\${DTADMIN_DB_PASSWORD}"
echo "  OIDC issuer: https://\${HOST_SSO}/realms/\${KC_REALM}"
echo "  Client: \${OAUTH2_CLIENT_ID}  Secret: \${OAUTH2_CLIENT_SECRET}"
