#!/usr/bin/env bash
set -euo pipefail

if [[ "${RUN_LIVE:-0}" != "1" ]]; then
  echo "Set RUN_LIVE=1 to create a live API data source and verify credential isolation."
  exit 0
fi

PLATFORM_CONTAINER="${DTS_PLATFORM_CONTAINER:-v223-dts-platform-1}"
PG_CONTAINER="${DTS_PG_CONTAINER:-v223-dts-pg-1}"
PG_DB="${DTS_PLATFORM_DB:-dts_platform}"
PG_USER="${DTS_PG_USER:-postgres}"
DAGS_DIR="${DTS_AIRFLOW_DAGS_DIR:-services/dts-airflow/dags}"
AIRFLOW_CONTAINER="${DTS_AIRFLOW_CONTAINER:-dts-airflow-scheduler}"
ENV_SCAN_CONTAINERS="${DTS_ENV_SCAN_CONTAINERS:-$PLATFORM_CONTAINER v223-dts-ingestion-1 $AIRFLOW_CONTAINER dts-airflow-webserver}"

for bin in docker jq grep; do
  command -v "$bin" >/dev/null 2>&1 || {
    echo "missing required command: $bin" >&2
    exit 2
  }
done

psql_scalar() {
  docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -Atc "$1"
}

platform_env_present() {
  docker exec "$PLATFORM_CONTAINER" sh -lc "[ -n \"\${$1:-}\" ] && echo true || echo false"
}

portal_token="${DTS_PORTAL_SESSION_TOKEN:-}"
if [[ -z "$portal_token" ]]; then
  portal_token="$(
    psql_scalar "
      select access_token
      from portal_sessions
      where revoked_at is null
        and expires_at > now()
        and roles::text ~ 'ROLE_(ADMIN|OP_ADMIN|INST_DATA_OWNER|DEPT_DATA_OWNER|INST_LEADER|DEPT_LEADER)'
      order by created_at desc
      limit 1;
    "
  )"
fi

if [[ -z "$portal_token" ]]; then
  echo "no_active_infra_maintainer_session=true"
  exit 3
fi

ingestion_token="$(docker exec "$PLATFORM_CONTAINER" sh -lc 'printf %s "$DTS_INBOUND_FROM_INGESTION"')"
analytics_token="$(docker exec "$PLATFORM_CONTAINER" sh -lc 'printf %s "$DTS_INBOUND_FROM_ANALYTICS"')"
if [[ -z "$ingestion_token" || -z "$analytics_token" ]]; then
  echo "missing_service_token=true"
  exit 4
fi

ts="$(date +%s)"
ds_name="s38-it02-api-secret-${ts}"
sentinel="s38-it02-token-${ts}"

payload="$(
  jq -n --arg name "$ds_name" --arg secret "$sentinel" '{
    name: $name,
    type: "api",
    connectorKey: "http-api",
    description: "Sprint-38 IT-02 credential encryption live check",
    props: {
      baseUrl: "http://s38-api-it02.local/orders",
      authProvider: "bearerToken",
      resourceKey: "orders",
      request: { method: "GET", path: "/orders" }
    },
    secrets: { token: $secret }
  }'
)"

create_response="$(
  docker exec -i -e PORTAL_TOKEN="$portal_token" "$PLATFORM_CONTAINER" sh -lc \
    'curl -sS -H "Content-Type: application/json" -H "Cookie: portal_session=$PORTAL_TOKEN" -X POST --data-binary @- http://127.0.0.1:8081/api/infra/data-sources' \
    <<<"$payload"
)"
create_status="$(jq -r '.status // empty' <<<"$create_response")"
ds_id="$(jq -r '.data.id // empty' <<<"$create_response")"
has_secrets="$(jq -r '.data.hasSecrets // empty' <<<"$create_response")"

if [[ "$create_status" != "200" || -z "$ds_id" ]]; then
  jq '{status,message,code}' <<<"$create_response"
  exit 5
fi

db_line="$(
  psql_scalar "
    select
      secure_props is not null,
      secure_iv is not null,
      coalesce(secure_key_version, ''),
      coalesce(octet_length(secure_props), 0),
      props like '%__apiSecretMeta%',
      props like '%${sentinel}%',
      encode(secure_props, 'escape') like '%${sentinel}%'
    from infra_data_source
    where id = '${ds_id}';
  "
)"
IFS='|' read -r db_secure_props db_secure_iv db_key_version db_secure_bytes db_has_meta db_props_plain db_blob_plain <<<"$db_line"

user_body="$(
  docker exec -e PORTAL_TOKEN="$portal_token" -e DS_ID="$ds_id" "$PLATFORM_CONTAINER" sh -lc \
    'curl -sS -H "Cookie: portal_session=$PORTAL_TOKEN" "http://127.0.0.1:8081/api/infra/data-sources/$DS_ID"'
)"
user_http="$(jq -r '.status // empty' <<<"$user_body")"
user_plain=false
[[ "$user_body" == *"$sentinel"* ]] && user_plain=true
user_has_meta="$(jq -r '(.data.props.__apiSecretMeta // null) != null' <<<"$user_body")"

analytics_body="$(
  docker exec -e TOKEN="$analytics_token" -e DS_ID="$ds_id" "$PLATFORM_CONTAINER" sh -lc \
    'curl -sS -H "X-DTS-Service: dts-analytics" -H "X-DTS-Service-Token: $TOKEN" "http://127.0.0.1:8081/api/infra/data-sources/$DS_ID/detail"'
)"
analytics_http="$(jq -r '.status // empty' <<<"$analytics_body")"
analytics_plain=false
[[ "$analytics_body" == *"$sentinel"* ]] && analytics_plain=true
analytics_secret_empty="$(jq -r '(.data.secrets // {}) == {}' <<<"$analytics_body")"
analytics_summary_present="$(jq -r '((.data.secretSummaries // []) | length) > 0' <<<"$analytics_body")"

runtime_missing_http="$(
  docker exec -e DS_ID="$ds_id" "$PLATFORM_CONTAINER" sh -lc \
    'curl -sS -o /tmp/s38-it02-runtime-missing.json -w "%{http_code}" -H "X-DTS-Service: dts-ingestion" "http://127.0.0.1:8081/api/infra/data-sources/$DS_ID/runtime-detail"'
)"
runtime_wrong_http="$(
  docker exec -e DS_ID="$ds_id" "$PLATFORM_CONTAINER" sh -lc \
    'curl -sS -o /tmp/s38-it02-runtime-wrong.json -w "%{http_code}" -H "X-DTS-Service: dts-ingestion" -H "X-DTS-Service-Token: wrong-token" "http://127.0.0.1:8081/api/infra/data-sources/$DS_ID/runtime-detail"'
)"
runtime_valid_body="$(
  docker exec -e TOKEN="$ingestion_token" -e DS_ID="$ds_id" "$PLATFORM_CONTAINER" sh -lc \
    'curl -sS -H "X-DTS-Service: dts-ingestion" -H "X-DTS-Service-Token: $TOKEN" "http://127.0.0.1:8081/api/infra/data-sources/$DS_ID/runtime-detail"'
)"
runtime_valid_http="$(jq -r '.status // empty' <<<"$runtime_valid_body")"
runtime_valid_contains_secret=false
[[ "$runtime_valid_body" == *"$sentinel"* ]] && runtime_valid_contains_secret=true

platform_logs_have_plaintext=false
docker logs "$PLATFORM_CONTAINER" 2>&1 | grep -Fq "$sentinel" && platform_logs_have_plaintext=true
airflow_dags_have_plaintext=false
grep -R -Fq "$sentinel" "$DAGS_DIR" 2>/dev/null && airflow_dags_have_plaintext=true
container_envs_have_plaintext=false
for container in $ENV_SCAN_CONTAINERS; do
  if docker inspect "$container" >/dev/null 2>&1; then
    docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$container" | grep -Fq "$sentinel" && container_envs_have_plaintext=true
  fi
done
airflow_variables_have_plaintext=false
if docker inspect "$AIRFLOW_CONTAINER" >/dev/null 2>&1; then
  set +e
  docker exec -e SENTINEL="$sentinel" "$AIRFLOW_CONTAINER" sh -lc '
    tmp="$(mktemp /tmp/s38-airflow-vars.XXXXXX.json)"
    if ! airflow variables export "$tmp" >/dev/null 2>&1; then
      rm -f "$tmp"
      exit 2
    fi
    if grep -Fq "$SENTINEL" "$tmp"; then
      rm -f "$tmp"
      exit 0
    fi
    rm -f "$tmp"
    exit 1
  '
  airflow_var_rc=$?
  set -e
  if [[ "$airflow_var_rc" == "0" ]]; then
    airflow_variables_have_plaintext=true
  elif [[ "$airflow_var_rc" != "1" ]]; then
    echo "airflow_variables_export_failed=true"
    exit 6
  fi
fi

platform_env_has_dts_infra_key="$(platform_env_present DTS_INFRA_ENCRYPTION_KEY)"
platform_env_has_dts_infra_version="$(platform_env_present DTS_INFRA_KEY_VERSION)"

echo "ds_id=${ds_id}"
echo "ds_name=${ds_name}"
echo "create_has_secrets=${has_secrets}"
echo "platform_env_has_dts_infra_key=${platform_env_has_dts_infra_key}"
echo "platform_env_has_dts_infra_version=${platform_env_has_dts_infra_version}"
echo "db_secure_props_present=${db_secure_props}"
echo "db_secure_iv_present=${db_secure_iv}"
echo "db_secure_key_version=${db_key_version}"
echo "db_secure_props_bytes=${db_secure_bytes}"
echo "db_props_has_secret_meta=${db_has_meta}"
echo "db_props_has_plaintext=${db_props_plain}"
echo "db_secure_blob_has_plaintext=${db_blob_plain}"
echo "user_detail_http=${user_http}"
echo "user_detail_has_plaintext=${user_plain}"
echo "user_detail_has_secret_meta=${user_has_meta}"
echo "analytics_detail_http=${analytics_http}"
echo "analytics_detail_has_plaintext=${analytics_plain}"
echo "analytics_detail_secrets_empty=${analytics_secret_empty}"
echo "analytics_detail_has_secret_summary=${analytics_summary_present}"
echo "runtime_missing_token_http=${runtime_missing_http}"
echo "runtime_wrong_token_http=${runtime_wrong_http}"
echo "runtime_valid_http=${runtime_valid_http}"
echo "runtime_valid_contains_secret=${runtime_valid_contains_secret}"
echo "platform_logs_have_plaintext=${platform_logs_have_plaintext}"
echo "airflow_dags_have_plaintext=${airflow_dags_have_plaintext}"
echo "container_envs_have_plaintext=${container_envs_have_plaintext}"
echo "airflow_variables_have_plaintext=${airflow_variables_have_plaintext}"

[[ "$platform_env_has_dts_infra_key" == "true" ]]
[[ "$platform_env_has_dts_infra_version" == "true" ]]
[[ "$db_secure_props" == "t" ]]
[[ "$db_secure_iv" == "t" ]]
[[ -n "$db_key_version" && "$db_key_version" != "PLAINTEXT" ]]
[[ "$db_has_meta" == "t" ]]
[[ "$db_props_plain" == "f" ]]
[[ "$db_blob_plain" == "f" ]]
[[ "$user_http" == "200" ]]
[[ "$user_plain" == "false" ]]
[[ "$user_has_meta" == "true" ]]
[[ "$analytics_http" == "200" ]]
[[ "$analytics_plain" == "false" ]]
[[ "$analytics_secret_empty" == "true" ]]
[[ "$analytics_summary_present" == "true" ]]
[[ "$runtime_missing_http" != "200" ]]
[[ "$runtime_wrong_http" != "200" ]]
[[ "$runtime_valid_http" == "200" ]]
[[ "$runtime_valid_contains_secret" == "true" ]]
[[ "$platform_logs_have_plaintext" == "false" ]]
[[ "$airflow_dags_have_plaintext" == "false" ]]
[[ "$container_envs_have_plaintext" == "false" ]]
[[ "$airflow_variables_have_plaintext" == "false" ]]
