#!/usr/bin/env bash
set -euo pipefail
REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT
source "${REPO_ROOT}/bin/lib/dts-upgrade-common.sh"

dts_managed_runtime_file services/dts-airflow/extra/openmetadata_managed_apis/resources/dag_runner.j2
dts_managed_runtime_file services/dts-dbt/dbt_project.yml
dts_managed_runtime_file services/dts-dbt/dbt_model/models/stg/static.sql
if dts_managed_runtime_file services/dts-dbt/models/site_generated.sql; then exit 1; fi
if dts_managed_runtime_file services/dts-dbt/profiles/profiles.yml; then exit 1; fi

PACKAGE_ROOT="${TMP_DIR}/package"
SOURCE_ROOT="${PACKAGE_ROOT}/dts-stack"
EXTRA_DIR="${PACKAGE_ROOT}/extra"
TARGET_DIR="${TMP_DIR}/installed"
RUNTIME_REL="services/dts-airflow/extra/dts_runtime/dbt_task_factory.py"
NEW_REL="services/dts-airflow/dags/dts_release_build_postgres_primary.py"
SITE_REL="services/dts-airflow/config/site.cfg"
mkdir -p "${PACKAGE_ROOT}/images" "${EXTRA_DIR}" \
  "$(dirname "${SOURCE_ROOT}/${RUNTIME_REL}")" "$(dirname "${TARGET_DIR}/${RUNTIME_REL}")" \
  "$(dirname "${SOURCE_ROOT}/${NEW_REL}")" \
  "$(dirname "${SOURCE_ROOT}/${SITE_REL}")" "$(dirname "${TARGET_DIR}/${SITE_REL}")"
printf 'new factory\n' > "${SOURCE_ROOT}/${RUNTIME_REL}"
printf 'old factory\n' > "${TARGET_DIR}/${RUNTIME_REL}"
printf 'release DAG\n' > "${SOURCE_ROOT}/${NEW_REL}"
printf 'package config\n' > "${SOURCE_ROOT}/${SITE_REL}"
printf 'site config\n' > "${TARGET_DIR}/${SITE_REL}"

# Exercise the real metadata producer with a minimal Docker-save archive.
python3 - "${PACKAGE_ROOT}/images/app.tar" <<'PY'
import io, json, sys, tarfile
with tarfile.open(sys.argv[1], 'w') as archive:
    for name, value in {
        'manifest.json': [{'Config': 'config.json', 'RepoTags': ['dts-platform:test'], 'Layers': []}],
        'config.json': {'architecture': 'amd64', 'os': 'linux', 'config': {'Labels': {}}},
    }.items():
        data = json.dumps(value).encode()
        entry = tarfile.TarInfo(name)
        entry.size = len(data)
        archive.addfile(entry, io.BytesIO(data))
PY
python3 "${REPO_ROOT}/builds/write-release-metadata.py" --root "${PACKAGE_ROOT}" --metadata-dir extra --revision 0123456789abcdef
dts_verify_package_files "${SOURCE_ROOT}" "${EXTRA_DIR}"
dts_verify_image_files "${PACKAGE_ROOT}/images" "${EXTRA_DIR}"
[[ "$(upgrade_manifest_images "${EXTRA_DIR}/release-manifest.json")" == app.tar ]]

printf 'corrupted\n' > "${SOURCE_ROOT}/${RUNTIME_REL}"
if dts_verify_package_files "${SOURCE_ROOT}" "${EXTRA_DIR}"; then exit 1; fi
printf 'new factory\n' > "${SOURCE_ROOT}/${RUNTIME_REL}"
mv "${EXTRA_DIR}/files-checksums.txt" "${TMP_DIR}/saved-checksums.txt"
if dts_verify_package_files "${SOURCE_ROOT}" "${EXTRA_DIR}"; then exit 1; fi
mv "${TMP_DIR}/saved-checksums.txt" "${EXTRA_DIR}/files-checksums.txt"
printf 'unlisted code\n' > "${SOURCE_ROOT}/extra.py"
if dts_verify_package_files "${SOURCE_ROOT}" "${EXTRA_DIR}"; then exit 1; fi
rm "${SOURCE_ROOT}/extra.py"

dts_preflight_runtime_paths "${SOURCE_ROOT}" "${TARGET_DIR}"
if dts_preflight_runtime_paths "${SOURCE_ROOT}" "${SOURCE_ROOT}"; then exit 1; fi
mv "${TARGET_DIR}/${RUNTIME_REL}" "${TMP_DIR}/saved-old.py"
ln -s "${TMP_DIR}/saved-old.py" "${TARGET_DIR}/${RUNTIME_REL}"
if dts_preflight_runtime_paths "${SOURCE_ROOT}" "${TARGET_DIR}"; then exit 1; fi
rm "${TARGET_DIR}/${RUNTIME_REL}"
mv "${TMP_DIR}/saved-old.py" "${TARGET_DIR}/${RUNTIME_REL}"

# Real file synchronization/rollback, with no Docker invocation.
UPGRADE_TIMESTAMP=fixture
upgrade_init_logs "${TARGET_DIR}" "${UPGRADE_TIMESTAMP}"
upgrade_init_backup_state "${TARGET_DIR}" "${UPGRADE_TIMESTAMP}"
upgrade_sync_new_files "${SOURCE_ROOT}" "${TARGET_DIR}"
upgrade_write_rollback_manifests "${EXTRA_DIR}"
cmp "${SOURCE_ROOT}/${RUNTIME_REL}" "${TARGET_DIR}/${RUNTIME_REL}"
grep -Fq 'site config' "${TARGET_DIR}/${SITE_REL}"
test -f "${TARGET_DIR}/${NEW_REL}"
dts_verify_package_files "${SOURCE_ROOT}" "${EXTRA_DIR}"
upgrade_restore_backed_up_files "${TARGET_DIR}" "${UPGRADE_BACKUP_DIR}" "${UPGRADE_BACKUP_DIR}/rollback-manifest.json"
grep -Fq 'old factory' "${TARGET_DIR}/${RUNTIME_REL}"
test ! -e "${TARGET_DIR}/${NEW_REL}"
grep -Fq 'site config' "${TARGET_DIR}/${SITE_REL}"
echo 'runtime package and rollback contract OK'
