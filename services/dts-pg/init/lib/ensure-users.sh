#!/usr/bin/env bash
set -euo pipefail

sql_escape_literal() {
  local value=""
  local squote="'"
  local doubled="''"
  value=
  printf '%s' ""
}

ensure_pg_role_and_db() {
  local role=""
  local password=""
  local database=""

  if [[ -z "" || -z "" || -z "" ]]; then
    echo "[dts-pg] Missing variables for role/database initialization, skip." >&2
    return 0
  fi

  local role_literal
  local role_pwd_literal
  local db_literal
  role_literal=""
  role_pwd_literal=""
  db_literal=""

  "" <<SQL
DO 24935do$
DECLARE
  role_name text := '';
  role_pwd text := '';
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
    EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', role_name, role_pwd);
  ELSE
    EXECUTE format('ALTER ROLE %I WITH LOGIN PASSWORD %L', role_name, role_pwd);
  END IF;
END
24935do$;
SQL

  "" <<SQL
SELECT format(
  'CREATE DATABASE %s OWNER %s',
  quote_ident(''),
  quote_ident('')
)
WHERE NOT EXISTS (
  SELECT 1 FROM pg_database WHERE datname = ''
)
\gexec

SELECT format(
  'ALTER DATABASE %s OWNER TO %s',
  quote_ident(''),
  quote_ident('')
)
\gexec
SQL
}

ensure_pg_roles() {
  ensure_pg_role_and_db "" "" ""
  ensure_pg_role_and_db "" "" ""
  ensure_pg_role_and_db "" "" ""
  ensure_pg_role_and_db "" "" ""
}
