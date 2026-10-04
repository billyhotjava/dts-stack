#!/usr/bin/env bash
# Always override inherited database variables: tests must never target a business database.
set -euo pipefail
if [[ $# -eq 0 ]]; then
  echo 'Usage: with-test-postgres.sh command [arguments...]' >&2
  exit 2
fi
pg_container=''
cleanup() {
  if [[ -n "$pg_container" ]]; then
    docker stop --time 5 "$pg_container" >/dev/null
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

export PG_HOST=127.0.0.1 PG_DB=stack_test PG_USER=stack_test
PG_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_urlsafe(24))')"
export PG_PASSWORD
export POSTGRES_PASSWORD="$PG_PASSWORD"
pg_container="$(docker run --rm --detach --pull=never \
  --label dts.purpose=stack-backend-tests \
  --tmpfs /var/lib/postgresql:rw \
  --publish 127.0.0.1::5432 \
  --env POSTGRES_DB="$PG_DB" --env POSTGRES_USER="$PG_USER" --env POSTGRES_PASSWORD \
  "${STACK_TEST_POSTGRES_IMAGE:-postgres:18.4}")"
unset POSTGRES_PASSWORD
PG_PORT="$(docker port "$pg_container" 5432/tcp)"
PG_PORT="${PG_PORT##*:}"
export PG_PORT
ready=false
for ((attempt = 0; attempt < 60; attempt++)); do
  if docker exec "$pg_container" pg_isready --quiet --username "$PG_USER" --dbname "$PG_DB"; then
    ready=true
    break
  fi
  sleep 1
done
if [[ "$ready" != true ]]; then
  echo 'Disposable test PostgreSQL did not become ready within 60 seconds.' >&2
  exit 1
fi
"$@"
