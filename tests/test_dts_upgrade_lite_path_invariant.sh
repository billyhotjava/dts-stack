#!/usr/bin/env bash
set -euo pipefail

# Tests for the target-path contract on dts-upgrade-lite:
#   - default --target = /data/dts-stack (overridable via DTS_UPGRADE_LITE_DEFAULT_TARGET)
#   - target inode/realpath are unchanged across apply/rollback
#   - missing default target produces a friendly error

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

# ------------------------------------------------------------------
# fixture builder (same shape as test_dts_upgrade_lite_robustness.sh)
# ------------------------------------------------------------------
fixture_dir() {
  local name="$1"
  local base="${TMP_DIR}/${name}"
  local fake_bin="${base}/bin"
  local source_root="${base}/dts-stack"
  local target_dir="${base}/data/dts-stack"   # mimics customer path
  local images_dir="${base}/images"
  local extra_dir="${base}/extra"
  mkdir -p "${fake_bin}" \
           "${source_root}/config" "${source_root}/bin" \
           "${target_dir}/config" "${target_dir}/bin" \
           "${target_dir}/services/dts-pg/data/pgdata" \
           "${images_dir}" "${extra_dir}"

  cat > "${fake_bin}/docker-compose" <<'EOF_COMPOSE'
#!/usr/bin/env bash
echo "$*" >> "${FAKE_COMPOSE_LOG}"
if [[ "${1:-}" == "version" ]]; then echo "docker-compose version 1.29.2"; exit 0; fi
exit 0
EOF_COMPOSE
  chmod +x "${fake_bin}/docker-compose"

  cat > "${fake_bin}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
echo "$*" >> "${FAKE_DOCKER_LOG}"
case "${1:-}" in
  load) exit 0 ;;
  version)
    if [[ "${2:-}" == "--format" ]]; then echo "18.09.0"; else echo "Docker version 18.09.0"; fi; exit 0 ;;
  *) exit 0 ;;
esac
EOF_DOCKER
  chmod +x "${fake_bin}/docker"

  echo "17" > "${target_dir}/services/dts-pg/data/pgdata/PG_VERSION"

  cat > "${source_root}/imgversion.conf" <<'EOF_IMG'
IMAGE_POSTGRES=postgres:17.6
IMAGE_DTS_ADMIN=dts-admin:1.0.0
EOF_IMG
  cat > "${source_root}/.env" <<'EOF_SRC'
BASE_DOMAIN=bi.new.local
IMAGE_DTS_ADMIN=dts-admin:1.0.0
EOF_SRC
  cat > "${target_dir}/.env" <<'EOF_TGT'
LEGACY_STACK=true
BASE_DOMAIN=bi.site.local
IMAGE_POSTGRES=postgres:17.6
IMAGE_DTS_ADMIN=dts-admin:1.0.0
EOF_TGT

  cat > "${source_root}/docker-compose.legacy.yml" <<'EOF_SC'
version: "2.4"
services:
  dts-admin:
    image: ${IMAGE_DTS_ADMIN}
EOF_SC
  cp "${source_root}/docker-compose.legacy.yml" "${target_dir}/docker-compose.legacy.yml"

  printf '%s\n' "${fake_bin}|${source_root}|${target_dir}|${images_dir}|${extra_dir}"
}

run_lite() {
  local fake_bin="$1"; shift
  local docker_log="$1"; shift
  local compose_log="$1"; shift
  local default_target="$1"; shift
  PATH="${fake_bin}:${PATH}" \
    FAKE_DOCKER_LOG="${docker_log}" \
    FAKE_COMPOSE_LOG="${compose_log}" \
    DTS_UPGRADE_LITE_DEFAULT_TARGET="${default_target}" \
    "${REPO_ROOT}/bin/dts-upgrade-lite" "$@"
}

# ==================================================================
# Test 1: default target picked when --target omitted
# ==================================================================
echo "[t1] default target picked when --target omitted"
read -r FB SR TD ID ED < <(fixture_dir t1 | tr '|' ' ')
DOCKER_LOG="${TMP_DIR}/t1.dl"; COMPOSE_LOG="${TMP_DIR}/t1.cl"
: > "${DOCKER_LOG}"; : > "${COMPOSE_LOG}"
output="$(run_lite "${FB}" "${DOCKER_LOG}" "${COMPOSE_LOG}" "${TD}" plan --source "${SR}" 2>&1)"
if ! echo "${output}" | grep -Fq -e "no --target provided; using default ${TD}"; then
  echo "FAIL: default target message missing" >&2
  echo "${output}" >&2
  exit 1
fi
echo "  ok"

# ==================================================================
# Test 2: missing default target -> friendly error
# ==================================================================
echo "[t2] missing default target -> friendly error"
MISSING="${TMP_DIR}/does-not-exist"
if run_lite "${TMP_DIR}/t1/bin" "${TMP_DIR}/t2.dl" "${TMP_DIR}/t2.cl" "${MISSING}" plan --source "${SR}" >/dev/null 2>"${TMP_DIR}/t2.err"; then
  echo "FAIL: missing default target should fail" >&2
  exit 1
fi
if ! grep -Fq -e "default ${MISSING} does not exist" "${TMP_DIR}/t2.err"; then
  echo "FAIL: friendly error message missing" >&2
  cat "${TMP_DIR}/t2.err" >&2
  exit 1
fi
echo "  ok"

# ==================================================================
# Test 3: target inode unchanged after apply
# ==================================================================
echo "[t3] target inode unchanged after apply"
read -r FB SR TD ID ED < <(fixture_dir t3 | tr '|' ' ')
DOCKER_LOG="${TMP_DIR}/t3.dl"; COMPOSE_LOG="${TMP_DIR}/t3.cl"
: > "${DOCKER_LOG}"; : > "${COMPOSE_LOG}"
INODE_BEFORE="$(stat -c %i "${TD}")"
REAL_BEFORE="$(readlink -f "${TD}")"
run_lite "${FB}" "${DOCKER_LOG}" "${COMPOSE_LOG}" "${TD}" apply --target "${TD}" --source "${SR}" --yes >/dev/null
INODE_AFTER="$(stat -c %i "${TD}")"
REAL_AFTER="$(readlink -f "${TD}")"
if [[ "${INODE_BEFORE}" != "${INODE_AFTER}" ]]; then
  echo "FAIL: inode changed ${INODE_BEFORE} -> ${INODE_AFTER}" >&2; exit 1
fi
if [[ "${REAL_BEFORE}" != "${REAL_AFTER}" ]]; then
  echo "FAIL: realpath changed ${REAL_BEFORE} -> ${REAL_AFTER}" >&2; exit 1
fi
echo "  ok (inode=${INODE_BEFORE}, realpath=${REAL_BEFORE})"

# ==================================================================
# Test 4: banner printed during apply
# ==================================================================
echo "[t4] banner printed during apply"
read -r FB SR TD ID ED < <(fixture_dir t4 | tr '|' ' ')
DOCKER_LOG="${TMP_DIR}/t4.dl"; COMPOSE_LOG="${TMP_DIR}/t4.cl"
: > "${DOCKER_LOG}"; : > "${COMPOSE_LOG}"
output="$(run_lite "${FB}" "${DOCKER_LOG}" "${COMPOSE_LOG}" "${TD}" apply --target "${TD}" --source "${SR}" --yes 2>&1)"
if ! echo "${output}" | grep -Fq -e "apply  target: ${TD}"; then
  echo "FAIL: apply banner missing target line" >&2
  echo "${output}" >&2
  exit 1
fi
if ! echo "${output}" | grep -Fq -e "contract: target path will not be renamed/moved"; then
  echo "FAIL: apply banner missing contract line" >&2
  echo "${output}" >&2
  exit 1
fi
echo "  ok"

# ==================================================================
# Test 5: rollback preserves inode
# ==================================================================
echo "[t5] rollback preserves inode"
APPLY_REPORT="$(find "${TD}/logs" -maxdepth 1 -type d -name 'upgrade-lite-*' | sort | tail -n 1)"
BACKUP_DIR="${APPLY_REPORT}/backup"
INODE_BEFORE_RB="$(stat -c %i "${TD}")"
run_lite "${FB}" "${DOCKER_LOG}" "${COMPOSE_LOG}" "${TD}" rollback --target "${TD}" --backup-dir "${BACKUP_DIR}" >/dev/null
INODE_AFTER_RB="$(stat -c %i "${TD}")"
if [[ "${INODE_BEFORE_RB}" != "${INODE_AFTER_RB}" ]]; then
  echo "FAIL: rollback changed inode ${INODE_BEFORE_RB} -> ${INODE_AFTER_RB}" >&2; exit 1
fi
echo "  ok"

echo ""
echo "All path-invariant tests passed."
