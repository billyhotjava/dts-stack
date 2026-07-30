#!/usr/bin/env bash

set -euo pipefail

sprint79_repo_root="$(git rev-parse --show-toplevel)"
set -a
source "${sprint79_repo_root}/.env"
set +a

sprint79_user="sprint79-e2e-$(date +%s)"
sprint79_password="$(openssl rand -hex 16)"
sprint79_kcadm="/opt/keycloak/bin/kcadm.sh"
sprint79_kcadm_config="/tmp/sprint79-kcadm.config"
sprint79_auth_file="${sprint79_repo_root}/source/dts-platform-webapp/e2e/.auth/user.json"
sprint79_kc_id=""

cleanup_sprint79_identity() {
	if [[ -n "${sprint79_kc_id}" ]]; then
		docker exec -e PGPASSWORD="${PG_PWD_DTADMIN}" v223-dts-pg-1 \
			psql -q -U "${PG_USER_DTADMIN}" -d "${PG_DB_DTADMIN}" \
			-c "DELETE FROM admin_keycloak_user WHERE username = '${sprint79_user}';" >/dev/null || true
		docker exec dts-keycloak "${sprint79_kcadm}" \
			delete "users/${sprint79_kc_id}" -r "${KC_REALM}" \
			--config "${sprint79_kcadm_config}" >/dev/null || true
		sprint79_kc_id=""
	fi
	if [[ -e "${sprint79_auth_file}" ]]; then
		unlink "${sprint79_auth_file}"
	fi
}

trap cleanup_sprint79_identity EXIT

docker exec dts-keycloak "${sprint79_kcadm}" config credentials \
	--config "${sprint79_kcadm_config}" \
	--server http://localhost:8080 \
	--realm master \
	--user "${KC_ADMIN}" \
	--password "${KC_ADMIN_PWD}" >/dev/null

sprint79_kc_id="$(
	docker exec dts-keycloak "${sprint79_kcadm}" create users \
		-r "${KC_REALM}" \
		-s "username=${sprint79_user}" \
		-s enabled=true \
		-s firstName=Sprint79 \
		-s "email=${sprint79_user}@example.invalid" \
		-s 'attributes={"person_security_level":["IMPORTANT"]}' \
		-i \
		--config "${sprint79_kcadm_config}"
)"
[[ "${sprint79_user}" =~ ^sprint79-e2e-[0-9]+$ ]]
[[ "${sprint79_kc_id}" =~ ^[0-9a-f-]{36}$ ]]

docker exec dts-keycloak "${sprint79_kcadm}" set-password \
	-r "${KC_REALM}" \
	--username "${sprint79_user}" \
	--new-password "${sprint79_password}" \
	--config "${sprint79_kcadm_config}" >/dev/null

docker exec dts-keycloak "${sprint79_kcadm}" add-roles \
	-r "${KC_REALM}" \
	--uusername "${sprint79_user}" \
	--rolename ROLE_MODEL_MAINTAINER \
	--config "${sprint79_kcadm_config}" >/dev/null

docker exec -e PGPASSWORD="${PG_PWD_DTADMIN}" v223-dts-pg-1 \
	psql -q -U "${PG_USER_DTADMIN}" -d "${PG_DB_DTADMIN}" \
	-c "INSERT INTO admin_keycloak_user (
		kc_id, username, full_name, email, person_security_level,
		realm_roles, group_paths, enabled, last_sync_at,
		created_by, created_date, last_modified_by, last_modified_date
	) VALUES (
		'${sprint79_kc_id}', '${sprint79_user}', 'Sprint 79 E2E', '${sprint79_user}@example.invalid', 'IMPORTANT',
		'[\"ROLE_MODEL_MAINTAINER\"]'::jsonb, '[]'::jsonb, true, now(),
		'sprint79-e2e', now(), 'sprint79-e2e', now()
	);" >/dev/null

set +e
PLAYWRIGHT_EXECUTABLE_PATH=/usr/bin/google-chrome \
	E2E_BASE_URL=https://bi.yuzhicloud.com \
	E2E_USERNAME="${sprint79_user}" \
	E2E_PASSWORD="${sprint79_password}" \
	pnpm --dir "${sprint79_repo_root}/source/dts-platform-webapp" exec playwright test "$@" --project=chromium
sprint79_test_status=$?
set -e

cleanup_sprint79_identity

sprint79_keycloak_count="$(
	docker exec dts-keycloak "${sprint79_kcadm}" get users \
		-r "${KC_REALM}" \
		-q exact=true \
		-q "username=${sprint79_user}" \
		--fields id \
		--config "${sprint79_kcadm_config}" | jq 'length'
)"
sprint79_snapshot_count="$(
	docker exec -e PGPASSWORD="${PG_PWD_DTADMIN}" v223-dts-pg-1 \
		psql -Atq -U "${PG_USER_DTADMIN}" -d "${PG_DB_DTADMIN}" \
		-c "SELECT count(*) FROM admin_keycloak_user WHERE username = '${sprint79_user}';"
)"
sprint79_auth_state="absent"
if [[ -e "${sprint79_auth_file}" ]]; then
	sprint79_auth_state="present"
fi

echo "CLEANUP keycloak=${sprint79_keycloak_count} snapshot=${sprint79_snapshot_count} auth_state=${sprint79_auth_state}"
[[ "${sprint79_keycloak_count}" == "0" ]]
[[ "${sprint79_snapshot_count}" == "0" ]]
[[ "${sprint79_auth_state}" == "absent" ]]
exit "${sprint79_test_status}"
