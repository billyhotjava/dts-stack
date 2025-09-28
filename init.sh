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

#------------Excute functions----------------
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
  )

  local dir
  for dir in "${data_dirs[@]}"; do
    mkdir -p "${dir}"
  done

  chmod -R 777 services/dts-minio/data || true
}

generate_env_base(){
  if [ -f .env ]; then
    set -a
    . ./.env
    set +a
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

  : "${KC_ADMIN:=admin}"
  : "${KC_ADMIN_PWD:=${SECRET}}"
  : "${KC_HTTP_ENABLED:=true}"
  : "${KC_HOSTNAME:=sso.${BASE_DOMAIN}}"
  : "${KC_HOSTNAME_PORT:=${TLS_PORT}}"
  : "${KC_HOSTNAME_STRICT:=true}"
  : "${KC_HOSTNAME_STRICT_HTTPS:=true}"
  : "${KC_HOSTNAME_URL:=https://${KC_HOSTNAME}}"

  : "${PG_SUPER_USER:=postgres}"
  : "${PG_SUPER_PASSWORD:=${SECRET}}"
  : "${PG_PORT:=5432}"
  : "${PG_DB_KEYCLOAK:=dts_keycloak}"
  : "${PG_USER_KEYCLOAK:=dts_keycloak}"
  : "${PG_PWD_KEYCLOAK:=${SECRET}}"

  : "${MINIO_ROOT_USER:=minio}"
  : "${MINIO_ROOT_PASSWORD:=${SECRET}}"
  : "${S3_BUCKET:=dts-lake}"
  : "${S3_REGION:=cn-local-1}"

  HOST_SSO="sso.${BASE_DOMAIN}"
  HOST_MINIO="minio.${BASE_DOMAIN}"
  HOST_TRINO="trino.${BASE_DOMAIN}"
  HOST_NESSIE="nessie.${BASE_DOMAIN}"

  cat > .env <<EOF
BASE_DOMAIN=${BASE_DOMAIN}
HOST_SSO=${HOST_SSO}
HOST_MINIO=${HOST_MINIO}
HOST_TRINO=${HOST_TRINO}
HOST_NESSIE=${HOST_NESSIE}
TLS_PORT=${TLS_PORT}
TRAEFIK_DASHBOARD=${TRAEFIK_DASHBOARD}
TRAEFIK_DASHBOARD_PORT=${TRAEFIK_DASHBOARD_PORT}
TRAEFIK_METRICS_PORT=${TRAEFIK_METRICS_PORT}
TRAEFIK_ENABLE_PING=${TRAEFIK_ENABLE_PING}
KC_ADMIN=${KC_ADMIN}
KC_ADMIN_PWD=${KC_ADMIN_PWD}
KC_HTTP_ENABLED=${KC_HTTP_ENABLED}
KC_HOSTNAME=${KC_HOSTNAME}
KC_HOSTNAME_PORT=${KC_HOSTNAME_PORT}
KC_HOSTNAME_URL=${KC_HOSTNAME_URL}
KC_HOSTNAME_STRICT=${KC_HOSTNAME_STRICT}
KC_HOSTNAME_STRICT_HTTPS=${KC_HOSTNAME_STRICT_HTTPS}
PG_MODE=${PG_MODE}
PG_HOST=${PG_HOST}
PG_SUPER_USER=${PG_SUPER_USER}
PG_SUPER_PASSWORD=${PG_SUPER_PASSWORD}
PG_PORT=${PG_PORT}
PG_DB_KEYCLOAK=${PG_DB_KEYCLOAK}
PG_USER_KEYCLOAK=${PG_USER_KEYCLOAK}
PG_PWD_KEYCLOAK=${PG_PWD_KEYCLOAK}
MINIO_ROOT_USER=${MINIO_ROOT_USER}
MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD}
S3_BUCKET=${S3_BUCKET}
S3_REGION=${S3_REGION}
EOF
}


while (($#)); do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --password)
      shift
      if (($# == 0)); then
        echo "[init.sh] ERROR: --password requires a value." >&2
        exit 1
      fi
      SECRET="$1"
      ;;
    --base-domain)
      shift
      if (($# == 0)); then
        echo "[init.sh] ERROR: --base-domain requires a value." >&2
        exit 1
      fi
      if [[ -n "$BASE_DOMAIN_ARG" ]]; then
        echo "[init.sh] ERROR: base domain already provided as '${BASE_DOMAIN_ARG}'." >&2
        exit 1
      fi
      BASE_DOMAIN_ARG="$1"
      ;;
    single|ha2|cluster)
      if [[ -n "$MODE" ]]; then
        echo "[init.sh] ERROR: deployment mode already specified as '${MODE}'." >&2
        exit 1
      fi
      MODE="$1"
      ;;
    *)
      if [[ -z "$BASE_DOMAIN_ARG" ]] && looks_like_domain "$1"; then
        BASE_DOMAIN_ARG="$1"
      elif [[ -z "$SECRET" ]]; then
        SECRET="$1"
      else
        echo "[init.sh] ERROR: unexpected argument '$1'." >&2
        usage
        exit 1
      fi
      ;;
  esac
  shift
done

BASE_DOMAIN="${BASE_DOMAIN:-}"
if [[ -n "$BASE_DOMAIN_ARG" ]]; then
  BASE_DOMAIN="$(normalize_base_domain "$BASE_DOMAIN_ARG")"
fi

if [[ -z "$BASE_DOMAIN" && -f .env ]]; then
  existing_base_domain="$(grep -E '^BASE_DOMAIN=' .env | head -n1 | cut -d= -f2- | tr -d '\r')"
  if [[ -n "${existing_base_domain}" ]]; then
    BASE_DOMAIN="$(normalize_base_domain "${existing_base_domain}")"
  fi
fi

DEFAULT_BASE_DOMAIN="${BASE_DOMAIN:-dts.local}"
DEFAULT_BASE_DOMAIN="$(normalize_base_domain "$DEFAULT_BASE_DOMAIN")"

if [[ -z "$BASE_DOMAIN" ]]; then
  if [[ -t 0 ]]; then
    prompt_base_domain "$DEFAULT_BASE_DOMAIN"
  else
    BASE_DOMAIN="$DEFAULT_BASE_DOMAIN"
  fi
fi

BASE_DOMAIN="$(normalize_base_domain "$BASE_DOMAIN")"
if ! validate_base_domain "$BASE_DOMAIN"; then
  echo "[init.sh] ERROR: invalid base domain '${BASE_DOMAIN}'." >&2
  exit 1
fi

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
    if [[ -n "${input_pg_host}" ]]; then
      PG_HOST="${input_pg_host}"
    fi
  fi
  if [[ "${PG_HOST}" == "your-external-pg-host" ]]; then
    echo "[init.sh] WARNING: PG_HOST is still set to 'your-external-pg-host'. Update it in .env before starting services." >&2
  fi
fi
generate_env_base
ensure_env PG_MODE "${PG_MODE}"
ensure_env PG_HOST "${PG_HOST}"
load_img_versions
prepare_data_dirs
if [[ -n "${MODE}" ]]; then
  ensure_env DEPLOY_MODE "${MODE}"
fi
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
  echo "docker compose not found"
  exit 1
fi

"${compose_cmd[@]}" -f "${COMPOSE_FILE}" up -d

sleep 2
fix_pg_permissions

for host_var in HOST_SSO HOST_MINIO HOST_TRINO HOST_NESSIE; do
  host_value="$(grep "^${host_var}=" .env | cut -d= -f2-)"
  if [[ -n "${host_value}" ]]; then
    echo "https://${host_value}"
  fi
done
TRAEFIK_DASHBOARD_ENABLED="$(grep '^TRAEFIK_DASHBOARD=' .env | cut -d= -f2)"
if [[ "${TRAEFIK_DASHBOARD_ENABLED}" == "true" ]]; then
  TRAEFIK_DASHBOARD_PORT_VALUE="$(grep '^TRAEFIK_DASHBOARD_PORT=' .env | cut -d= -f2)"
  echo "http://localhost:${TRAEFIK_DASHBOARD_PORT_VALUE} (local Traefik dashboard via --api.insecure)"
fi
