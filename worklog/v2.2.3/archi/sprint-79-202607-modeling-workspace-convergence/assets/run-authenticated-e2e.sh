#!/usr/bin/env bash

set -euo pipefail

sprint79_script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
sprint79_repo_root="$(git -C "${sprint79_script_dir}" rev-parse --show-toplevel)"
source "${sprint79_repo_root}/.env"

sprint79_user="sprint79-e2e-$(date +%s)"
sprint79_password="$(openssl rand -hex 16)"
sprint79_kcadm="/opt/keycloak/bin/kcadm.sh"
sprint79_kcadm_config="/tmp/sprint79-kcadm.config"
sprint79_auth_file="${sprint79_repo_root}/source/dts-platform-webapp/e2e/.auth/user.json"
sprint79_realm_role="ROLE_OP_ADMIN"
sprint79_business_role="ROLE_INST_DATA_OWNER"
sprint79_kc_id=""

cleanup_sprint79_identity() {
	local sprint79_cleanup_failed=0
	if [[ -n "${sprint79_kc_id}" ]]; then
		if ! docker exec -e PGPASSWORD="${PG_PWD_DTADMIN}" v223-dts-pg-1 \
			psql -q -U "${PG_USER_DTADMIN}" -d "${PG_DB_DTADMIN}" \
			-c "DELETE FROM admin_role_member
				WHERE username = '${sprint79_user}'
				  AND role = '${sprint79_business_role}';
				DELETE FROM admin_keycloak_user
				WHERE username = '${sprint79_user}';" >/dev/null; then
			sprint79_cleanup_failed=1
		fi
		if ! docker exec dts-keycloak "${sprint79_kcadm}" \
			delete "users/${sprint79_kc_id}" -r "${KC_REALM}" \
			--config "${sprint79_kcadm_config}" >/dev/null; then
			sprint79_cleanup_failed=1
		fi
		if [[ "${sprint79_cleanup_failed}" -eq 0 ]]; then
			sprint79_kc_id=""
		fi
	fi
	if [[ -e "${sprint79_auth_file}" ]]; then
		if ! unlink "${sprint79_auth_file}"; then
			sprint79_cleanup_failed=1
		fi
	fi
	return "${sprint79_cleanup_failed}"
}

trap 'cleanup_sprint79_identity || echo "ERROR: sprint79 identity cleanup requires manual verification" >&2' EXIT

if [[ "$#" -lt 1 ]]; then
	echo "ERROR: exact Sprint-79 spec path is required; refusing to run the full E2E directory" >&2
	exit 64
fi
sprint79_requested_spec="$1"
shift
case "${sprint79_requested_spec}" in
	e2e/sprint79-model-detail.spec.ts|source/dts-platform-webapp/e2e/sprint79-model-detail.spec.ts)
		sprint79_spec="e2e/sprint79-model-detail.spec.ts"
		;;
	e2e/sprint79-modeling-workspace.spec.ts|source/dts-platform-webapp/e2e/sprint79-modeling-workspace.spec.ts)
		sprint79_spec="e2e/sprint79-modeling-workspace.spec.ts"
		;;
	*)
		echo "ERROR: only approved Sprint-79 read-only specs are allowed by this privileged wrapper" >&2
		exit 64
		;;
esac
if [[ "${SPRINT79_CONFIRM_PRODUCTION_READ_ONLY:-}" != "1" ]]; then
	echo "ERROR: set SPRINT79_CONFIRM_PRODUCTION_READ_ONLY=1 after reviewing the request-time write barrier" >&2
	exit 64
fi
sprint79_playwright_args=()
while [[ "$#" -gt 0 ]]; do
	case "$1" in
		--grep)
			if [[ "$#" -lt 2 ]]; then
				echo "ERROR: --grep requires one pattern" >&2
				exit 64
			fi
			sprint79_playwright_args+=("$1" "$2")
			shift 2
			;;
		--grep=*)
			sprint79_playwright_args+=("$1")
			shift
			;;
		*)
			echo "ERROR: privileged wrapper accepts only --grep after the fixed Sprint-79 spec" >&2
			exit 64
			;;
	esac
done

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
	--rolename "${sprint79_realm_role}" \
	--config "${sprint79_kcadm_config}" >/dev/null

docker exec -e PGPASSWORD="${PG_PWD_DTADMIN}" v223-dts-pg-1 \
	psql -q -U "${PG_USER_DTADMIN}" -d "${PG_DB_DTADMIN}" \
	-c "INSERT INTO admin_keycloak_user (
		kc_id, username, full_name, email, person_security_level,
		realm_roles, group_paths, enabled, last_sync_at,
		created_by, created_date, last_modified_by, last_modified_date
	) VALUES (
		'${sprint79_kc_id}', '${sprint79_user}', 'Sprint 79 E2E', '${sprint79_user}@example.invalid', 'IMPORTANT',
		'[\"${sprint79_realm_role}\"]'::jsonb, '[]'::jsonb, true, now(),
		'sprint79-e2e', now(), 'sprint79-e2e', now()
	);
	INSERT INTO admin_role_member (
		role, username, display_name,
		created_by, created_date, last_modified_by, last_modified_date
	) VALUES (
		'${sprint79_business_role}', '${sprint79_user}', 'Sprint 79 E2E',
		'sprint79-e2e', now(), 'sprint79-e2e', now()
	);" >/dev/null

set +e
PLAYWRIGHT_EXECUTABLE_PATH=/usr/bin/google-chrome \
	E2E_BASE_URL=https://bi.yuzhicloud.com \
	E2E_USERNAME="${sprint79_user}" \
	E2E_PASSWORD="${sprint79_password}" \
	pnpm --dir "${sprint79_repo_root}/source/dts-platform-webapp" exec playwright test \
		"${sprint79_spec}" "${sprint79_playwright_args[@]}" --project=chromium
sprint79_test_status=$?
set -e

if ! cleanup_sprint79_identity; then
	echo "ERROR: sprint79 identity cleanup failed; final cleanup will retry on EXIT" >&2
	sprint79_test_status=1
fi

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
sprint79_role_member_count="$(
	docker exec -e PGPASSWORD="${PG_PWD_DTADMIN}" v223-dts-pg-1 \
		psql -Atq -U "${PG_USER_DTADMIN}" -d "${PG_DB_DTADMIN}" \
		-c "SELECT count(*)
			FROM admin_role_member
			WHERE username = '${sprint79_user}'
			  AND role = '${sprint79_business_role}';"
)"
sprint79_auth_state="absent"
if [[ -e "${sprint79_auth_file}" ]]; then
	sprint79_auth_state="present"
fi

echo "CLEANUP keycloak=${sprint79_keycloak_count} snapshot=${sprint79_snapshot_count} role_member=${sprint79_role_member_count} auth_state=${sprint79_auth_state}"
[[ "${sprint79_keycloak_count}" == "0" ]]
[[ "${sprint79_snapshot_count}" == "0" ]]
[[ "${sprint79_role_member_count}" == "0" ]]
[[ "${sprint79_auth_state}" == "absent" ]]
exit "${sprint79_test_status}"
