#!/usr/bin/env bash
set -euo pipefail

# Regression tests for dts-upgrade-lite hardening:
#   - --force breaks stale lock
#   - apply failure auto-clears lock via EXIT trap
#   - empty checksums.txt -> die
#   - --remove-orphans is opt-in (off by default)
#   - --force-recreate omitted when no IMAGE changes
#   - parse_args validates flag/action combinations

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

# ------------------------------------------------------------------
# fixture: minimal legacy stack with fake docker / docker-compose
# ------------------------------------------------------------------
fixture_dir() {
  local name="$1"
  local base="${TMP_DIR}/${name}"
  local fake_bin="${base}/bin"
  local source_root="${base}/dts-stack"
  local target_dir="${base}/stack-old"
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
if [[ "${1:-}" == "version" ]]; then
  echo "docker-compose version 1.29.2"
  exit 0
fi
exit 0
EOF_COMPOSE
  chmod +x "${fake_bin}/docker-compose"

  cat > "${fake_bin}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
echo "$*" >> "${FAKE_DOCKER_LOG}"
case "${1:-}" in
  load) exit 0 ;;
  version)
    if [[ "${2:-}" == "--format" ]]; then echo "18.09.0"; else echo "Docker version 18.09.0"; fi
    exit 0 ;;
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
  PATH="${fake_bin}:${PATH}" \
    FAKE_DOCKER_LOG="${docker_log}" \
    FAKE_COMPOSE_LOG="${compose_log}" \
    "${REPO_ROOT}/bin/dts-upgrade-lite" "$@"
}

assert_grep_q() {
  local file="$1" pattern="$2" msg="$3"
  if ! grep -Fq -e "${pattern}" "${file}"; then
    echo "FAIL: ${msg}; expected '${pattern}' in ${file}" >&2
    [[ -f "${file}" ]] && cat "${file}" >&2 || true
    exit 1
  fi
}

assert_no_grep() {
  local file="$1" pattern="$2" msg="$3"
  if grep -Fq -e "${pattern}" "${file}"; then
    echo "FAIL: ${msg}; unexpected '${pattern}' in ${file}" >&2
    cat "${file}" >&2 || true
    exit 1
  fi
}

# ==================================================================
# Test 1: validate_args_for_action — plan rejects --yes
# ==================================================================
echo "[t1] plan rejects --yes"
read -r FB SR TD ID ED < <(fixture_dir t1 | tr '|' ' ')
DOCKER_LOG="${TMP_DIR}/t1.dl"; COMPOSE_LOG="${TMP_DIR}/t1.cl"
: > "${DOCKER_LOG}"; : > "${COMPOSE_LOG}"
if run_lite "${FB}" "${DOCKER_LOG}" "${COMPOSE_LOG}" plan --target "${TD}" --source "${SR}" --yes >/dev/null 2>&1; then
  echo "FAIL: plan --yes should have failed" >&2; exit 1
fi
echo "  ok"

# ==================================================================
# Test 2: validate_args_for_action — apply rejects --restore-db
# ==================================================================
echo "[t2] apply rejects --restore-db"
read -r FB SR TD ID ED < <(fixture_dir t2 | tr '|' ' ')
DOCKER_LOG="${TMP_DIR}/t2.dl"; COMPOSE_LOG="${TMP_DIR}/t2.cl"
: > "${DOCKER_LOG}"; : > "${COMPOSE_LOG}"
if run_lite "${FB}" "${DOCKER_LOG}" "${COMPOSE_LOG}" apply --target "${TD}" --source "${SR}" --yes --restore-db >/dev/null 2>&1; then
  echo "FAIL: apply --restore-db should have failed" >&2; exit 1
fi
echo "  ok"

# ==================================================================
# Test 3: --remove-orphans off by default; on when flag passed
# ==================================================================
echo "[t3] --remove-orphans default off + opt-in"
read -r FB SR TD ID ED < <(fixture_dir t3 | tr '|' ' ')
DOCKER_LOG="${TMP_DIR}/t3.dl"; COMPOSE_LOG="${TMP_DIR}/t3.cl"
: > "${DOCKER_LOG}"; : > "${COMPOSE_LOG}"
run_lite "${FB}" "${DOCKER_LOG}" "${COMPOSE_LOG}" apply --target "${TD}" --source "${SR}" --yes >/dev/null
assert_no_grep "${COMPOSE_LOG}" "--remove-orphans" "default apply must not pass --remove-orphans"

# now opt-in: clean lock first (lite was successful so lock already removed)
read -r FB SR TD ID ED < <(fixture_dir t3b | tr '|' ' ')
DOCKER_LOG="${TMP_DIR}/t3b.dl"; COMPOSE_LOG="${TMP_DIR}/t3b.cl"
: > "${DOCKER_LOG}"; : > "${COMPOSE_LOG}"
run_lite "${FB}" "${DOCKER_LOG}" "${COMPOSE_LOG}" apply --target "${TD}" --source "${SR}" --yes --remove-orphans >/dev/null
assert_grep_q "${COMPOSE_LOG}" "--remove-orphans" "apply --remove-orphans must propagate"
echo "  ok"

# ==================================================================
# Test 4: no IMAGE changes -> no --force-recreate
# ==================================================================
echo "[t4] no image diff -> no --force-recreate"
read -r FB SR TD ID ED < <(fixture_dir t4 | tr '|' ' ')
DOCKER_LOG="${TMP_DIR}/t4.dl"; COMPOSE_LOG="${TMP_DIR}/t4.cl"
: > "${DOCKER_LOG}"; : > "${COMPOSE_LOG}"
run_lite "${FB}" "${DOCKER_LOG}" "${COMPOSE_LOG}" apply --target "${TD}" --source "${SR}" --yes >/dev/null
assert_no_grep "${COMPOSE_LOG}" "--force-recreate" "no IMAGE changes must skip --force-recreate"
echo "  ok"

# ==================================================================
# Test 5: --force-recreate flag forces it on regardless
# ==================================================================
echo "[t5] --force-recreate flag forces it on"
read -r FB SR TD ID ED < <(fixture_dir t5 | tr '|' ' ')
DOCKER_LOG="${TMP_DIR}/t5.dl"; COMPOSE_LOG="${TMP_DIR}/t5.cl"
: > "${DOCKER_LOG}"; : > "${COMPOSE_LOG}"
run_lite "${FB}" "${DOCKER_LOG}" "${COMPOSE_LOG}" apply --target "${TD}" --source "${SR}" --yes --force-recreate >/dev/null
assert_grep_q "${COMPOSE_LOG}" "--force-recreate" "--force-recreate flag must propagate"
echo "  ok"

# ==================================================================
# Test 6: empty checksums.txt -> die
# ==================================================================
echo "[t6] empty checksums.txt -> die"
read -r FB SR TD ID ED < <(fixture_dir t6 | tr '|' ' ')
DOCKER_LOG="${TMP_DIR}/t6.dl"; COMPOSE_LOG="${TMP_DIR}/t6.cl"
: > "${DOCKER_LOG}"; : > "${COMPOSE_LOG}"
# put a tar so IMAGES_DIR is non-empty, plus an empty checksums.txt
printf 'fake' > "${ID}/dummy.tar"
: > "${ED}/checksums.txt"
if run_lite "${FB}" "${DOCKER_LOG}" "${COMPOSE_LOG}" apply --target "${TD}" --source "${SR}" --images-dir "${ID}" --extra-dir "${ED}" --yes >/dev/null 2>&1; then
  echo "FAIL: empty checksums.txt should have failed apply" >&2; exit 1
fi
# lock must be auto-cleared by EXIT trap
if [[ -f "${TD}/.upgrade-lite-lock" ]]; then
  echo "FAIL: lock not auto-cleared after empty-checksums failure" >&2; exit 1
fi
echo "  ok"

# ==================================================================
# Test 7: stale lock blocks subsequent apply; --force breaks it
# ==================================================================
echo "[t7] stale lock blocks; --force breaks"
read -r FB SR TD ID ED < <(fixture_dir t7 | tr '|' ' ')
DOCKER_LOG="${TMP_DIR}/t7.dl"; COMPOSE_LOG="${TMP_DIR}/t7.cl"
: > "${DOCKER_LOG}"; : > "${COMPOSE_LOG}"
echo "stale=1" > "${TD}/.upgrade-lite-lock"
if run_lite "${FB}" "${DOCKER_LOG}" "${COMPOSE_LOG}" apply --target "${TD}" --source "${SR}" --yes >/dev/null 2>&1; then
  echo "FAIL: stale lock should block apply" >&2; exit 1
fi
# --force should succeed
run_lite "${FB}" "${DOCKER_LOG}" "${COMPOSE_LOG}" apply --target "${TD}" --source "${SR}" --yes --force >/dev/null
if [[ -f "${TD}/.upgrade-lite-lock" ]]; then
  echo "FAIL: lock not removed after successful apply" >&2; exit 1
fi
echo "  ok"

echo ""
echo "All robustness tests passed."
