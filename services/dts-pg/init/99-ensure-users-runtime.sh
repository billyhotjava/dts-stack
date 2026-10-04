#!/usr/bin/env bash
set -Eeuo pipefail

POSTGRES_HOST="${POSTGRES_HOST:-127.0.0.1}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_DB="${POSTGRES_DB:-postgres}"

if [[ -n "${POSTGRES_PASSWORD:-}" ]]; then
  export PGPASSWORD="${POSTGRES_PASSWORD}"
fi

psqlb=(psql -v ON_ERROR_STOP=1 -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB")

# ready & 只读探测（失败则 healthcheck 失败）
pg_isready -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1
"${psqlb[@]}" -c "SELECT 1;" >/dev/null

# superuser password sync (runtime)
if [[ -n "${POSTGRES_PASSWORD:-}" ]]; then
  "${psqlb[@]}" -c "ALTER ROLE ${POSTGRES_USER} WITH LOGIN PASSWORD '${POSTGRES_PASSWORD}';" >/dev/null
fi

ensure_role() {
  local user="$1" pass="$2"
  "${psqlb[@]}" <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='${user}') THEN
     EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${user}', '${pass}');
  END IF;
END
\$\$;
SQL
  "${psqlb[@]}" -c "ALTER ROLE ${user} WITH PASSWORD '${pass}';" >/dev/null
}

ensure_db() {
  local db="$1" owner="$2"
  if ! "${psqlb[@]}" -tAc "SELECT 1 FROM pg_database WHERE datname='${db}'" | grep -q 1; then
    "${psqlb[@]}" -c "CREATE DATABASE ${db} OWNER ${owner};"
  fi
}

# Grant enhanced privileges for data warehouse roles (biadmin)
grant_dw_privileges() {
  local user="$1" db="$2"
  # Grant CREATEDB for creating additional schemas/databases
  "${psqlb[@]}" -c "ALTER ROLE ${user} WITH CREATEDB;" >/dev/null 2>&1 || true
  # Grant full privileges on database
  "${psqlb[@]}" -c "GRANT ALL PRIVILEGES ON DATABASE ${db} TO ${user};" >/dev/null 2>&1 || true
  # Connect to the specific database and grant schema privileges
  psql -v ON_ERROR_STOP=0 -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$db" <<SQL || true
-- Grant usage and create on public schema
GRANT ALL ON SCHEMA public TO ${user};
-- Grant default privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO ${user};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO ${user};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO ${user};
-- Allow creating schemas
GRANT CREATE ON DATABASE ${db} TO ${user};
SQL
}

# 遍历三元组，补齐用户/库并刷新口令哈希
while IFS='=' read -r k v; do
  [[ "$k" == PG_DB_* ]] || continue
  suf="${k#PG_DB_}"
  db="${!k:-}"
  uvar="PG_USER_${suf}"
  pvar="PG_PWD_${suf}"
  user="${!uvar:-}"
  pass="${!pvar:-}"
  [[ -n "$db" && -n "$user" && -n "$pass" ]] || continue

  ensure_role "$user" "$pass"
  ensure_db "$db" "$user"

  # 为数仓用户(biadmin)授予额外权限
  if [[ "$suf" == "BIADMIN" ]]; then
    grant_dw_privileges "$user" "$db"
  fi
done < <(env)

echo "ok"
