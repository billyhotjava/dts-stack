#!/usr/bin/env bash
set -Eeuo pipefail

POSTGRES_HOST="${POSTGRES_HOST:-127.0.0.1}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_DB="${POSTGRES_DB:-postgres}"
PGDATA_DIR="${PGDATA:-/var/lib/postgresql/data/pgdata}"

# scram | md5（默认 scram）
PG_AUTH_METHOD="${PG_AUTH_METHOD:-scram}"

log(){ echo "[$(date +'%F %T')] [init] $*"; }

# 等待 PG，若未就绪则仅做离线文件调整，在线 SQL 留待后续 ensure 脚本
PG_READY=0
for i in {1..30}; do
  if pg_isready -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1; then
    PG_READY=1
    break
  fi
  log "waiting postgres... (${i}/30)"
  sleep 1
done

psqlb=(psql -v ON_ERROR_STOP=1 -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB")

# pg_hba 认证方式
if [[ -f "${PGDATA_DIR}/pg_hba.conf" ]]; then
  case "$PG_AUTH_METHOD" in
    scram)
      if grep -qE '\bmd5\b' "${PGDATA_DIR}/pg_hba.conf"; then
        log "switch pg_hba -> scram-sha-256"
        sed -i 's/\bmd5\b/scram-sha-256/g' "${PGDATA_DIR}/pg_hba.conf"
        if [[ $PG_READY -eq 1 ]]; then
          "${psqlb[@]}" -c "SELECT pg_reload_conf();" || true
        else
          log "pg not ready; pg_hba change will take effect on server start"
        fi
      fi
      ;;
    md5)
      if grep -qE 'scram-sha-256' "${PGDATA_DIR}/pg_hba.conf"; then
        log "switch pg_hba -> md5"
        sed -i 's/scram-sha-256/md5/g' "${PGDATA_DIR}/pg_hba.conf"
        if [[ $PG_READY -eq 1 ]]; then
          "${psqlb[@]}" -c "SELECT pg_reload_conf();" || true
        else
          log "pg not ready; pg_hba change will take effect on server start"
        fi
      fi
      ;;
  esac
fi

if [[ $PG_READY -eq 1 ]]; then
  # password_encryption
  target_enc=$([[ "$PG_AUTH_METHOD" == "md5" ]] && echo "md5" || echo "scram-sha-256")
  cur_enc="$("${psqlb[@]}" -Atc "SHOW password_encryption;" || echo "")"
  if [[ -n "$cur_enc" && "${cur_enc,,}" != "$target_enc" ]]; then
    log "ALTER SYSTEM password_encryption='${target_enc}'"
    "${psqlb[@]}" <<SQL
ALTER SYSTEM SET password_encryption = '${target_enc}';
SELECT pg_reload_conf();
SQL
  fi
else
  log "pg not ready; skip online password_encryption setup (handled later)"
fi

# 工具函数：若无则建角色（可在事务中执行）
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
  # 刷新哈希到当前策略
  "${psqlb[@]}" -c "ALTER ROLE ${user} WITH PASSWORD '${pass}';" >/dev/null
}

# 工具函数：若无则建库（不能在 DO/事务内！）
ensure_db() {
  local db="$1" owner="$2"
  if ! "${psqlb[@]}" -tAc "SELECT 1 FROM pg_database WHERE datname='${db}'" | grep -q 1; then
    "${psqlb[@]}" -c "CREATE DATABASE ${db} OWNER ${owner};"
  fi
  # 授权（幂等）
  "${psqlb[@]}" -c "REVOKE ALL ON DATABASE ${db} FROM PUBLIC;" >/dev/null || true
  "${psqlb[@]}" -c "GRANT ALL PRIVILEGES ON DATABASE ${db} TO ${owner};" >/dev/null || true

  # schema 与默认权限
  "${psqlb[@]}" -d "${db}" <<SQL
DO \$\$
BEGIN
  BEGIN
    EXECUTE 'GRANT USAGE ON SCHEMA public TO ${owner}';
    EXECUTE 'GRANT CREATE ON SCHEMA public TO ${owner}';
  EXCEPTION WHEN others THEN NULL; END;

  BEGIN
    EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO ${owner}';
    EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO ${owner}';
  EXCEPTION WHEN others THEN NULL; END;
END
\$\$;
SQL
}

# 扫描三元组
while IFS='=' read -r k v; do
  [[ "$k" == PG_DB_* ]] || continue
  suf="${k#PG_DB_}"
  db="${!k:-}"
  uvar="PG_USER_${suf}"
  pvar="PG_PWD_${suf}"
  user="${!uvar:-}"
  pass="${!pvar:-}"
  [[ -n "$db" && -n "$user" && -n "$pass" ]] || continue

  if [[ $PG_READY -eq 1 ]]; then
    log "ensure user/db for suffix=${suf} -> ${user}/${db}"
    ensure_role "$user" "$pass"
    ensure_db "$db" "$user"
  else
    log "pg not ready; defer ensuring ${user}/${db} to runtime ensure script"
  fi
done < <(env)
if [[ $PG_READY -eq 1 ]]; then
  log "all users/databases ensured"
else
  log "init script finished with deferred actions; runtime ensure will finalize when PG is up"
fi
