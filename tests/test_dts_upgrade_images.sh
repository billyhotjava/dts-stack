#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

FAKE_BIN="${TMP_DIR}/bin"
SOURCE_ROOT="${TMP_DIR}/source"
STATE_FILE="${TMP_DIR}/services-running"
mkdir -p "${FAKE_BIN}" "${SOURCE_ROOT}"

cat > "${FAKE_BIN}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
if [[ "${1:-}" == "load" && "${2:-}" == "-i" ]]; then
  printf '%s\n' "${3##*/}" >> "${FAKE_DOCKER_LOG}"
  exit 0
fi
if [[ "${1:-}" == "compose" ]]; then
  shift
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -f)
        shift 2
        ;;
      ps)
        if [[ -f "${FAKE_DOCKER_STATE_FILE}" ]]; then
          printf 'dts-platform\n'
        fi
        exit 0
        ;;
      up)
        if [[ "${2:-}" == "-d" ]]; then
          : > "${FAKE_DOCKER_STATE_FILE}"
          exit 0
        fi
        shift
        ;;
      *)
        shift
        ;;
    esac
  done
fi
exit 0
EOF_DOCKER
chmod +x "${FAKE_BIN}/docker"

run_upgrade() {
  local target_dir="$1"
  local images_dir="$2"
  local extra_dir="$3"
  PATH="${FAKE_BIN}:${PATH}" \
    FAKE_DOCKER_LOG="${TMP_DIR}/docker-load.log" \
    FAKE_DOCKER_STATE_FILE="${STATE_FILE}" \
    DTS_UPGRADE_SOURCE_ROOT="${SOURCE_ROOT}" \
    "${REPO_ROOT}/bin/dts-upgrade" \
    --target "${target_dir}" \
    --images-dir "${images_dir}" \
    --extra-dir "${extra_dir}"
}

ensure_no_docker_loads() {
  local log_file="${TMP_DIR}/docker-load.log"
  if [[ -s "${log_file}" ]]; then
    echo "expected no docker load operations, but found:" >&2
    cat "${log_file}" >&2
    exit 1
  fi
}

prepare_target() {
  local target_dir="$1"
  mkdir -p "${target_dir}"
  cat > "${target_dir}/docker-compose-app.yml" <<'EOF_COMPOSE'
services: {}
EOF_COMPOSE
  cat > "${target_dir}/.env" <<'EOF_ENV'
IMAGE_POSTGRES=postgres:17.6
EOF_ENV
}

prepare_extra() {
  local extra_dir="$1"
  mkdir -p "${extra_dir}"
}

prepare_manifest() {
  local extra_dir="$1"
  shift
  local images=("$@")
  {
    printf '{\n  "images": [\n'
    local idx
    for idx in "${!images[@]}"; do
      printf '    "%s"' "${images[$idx]}"
      if [[ "${idx}" -lt "$((${#images[@]} - 1))" ]]; then
        printf ','
      fi
      printf '\n'
    done
    printf '  ]\n}\n'
  } > "${extra_dir}/release-manifest.json"
}

write_checksums() {
  local images_dir="$1"
  local extra_dir="$2"
  shift 2
  (
    cd "${images_dir}"
    sha256sum "$@"
  ) > "${extra_dir}/checksums.txt"
}

expect_failure() {
  local expected="$1"
  shift
  local output_file="${TMP_DIR}/failure.log"
  set +e
  run_upgrade "$@" >"${output_file}" 2>&1
  local status=$?
  set -e
  if [[ ${status} -eq 0 ]]; then
    echo "expected dts-upgrade to fail" >&2
    cat "${output_file}" >&2
    exit 1
  fi
  if ! grep -Fq "${expected}" "${output_file}"; then
    echo "expected failure output to contain: ${expected}" >&2
    cat "${output_file}" >&2
    exit 1
  fi
}

expect_success() {
  local output_file="${TMP_DIR}/success.log"
  : > "${TMP_DIR}/docker-load.log"
  if ! run_upgrade "$@" >"${output_file}" 2>&1; then
    cat "${output_file}" >&2
    exit 1
  fi
}

MISSING_TARGET="${TMP_DIR}/missing-target"
MISSING_IMAGES="${TMP_DIR}/missing-images"
MISSING_EXTRA="${TMP_DIR}/missing-extra"
prepare_target "${MISSING_TARGET}"
mkdir -p "${MISSING_IMAGES}" "${MISSING_EXTRA}"
prepare_manifest "${MISSING_EXTRA}" "one.tar"
cat > "${MISSING_EXTRA}/checksums.txt" <<'EOF_SUM'
deadbeef  one.tar
EOF_SUM
expect_failure "checksums validation failed" "${MISSING_TARGET}" "${MISSING_IMAGES}" "${MISSING_EXTRA}"

NO_PACKAGE_TARGET="${TMP_DIR}/no-package-target"
NO_PACKAGE_IMAGES="${TMP_DIR}/no-package-images"
NO_PACKAGE_EXTRA="${TMP_DIR}/no-package-extra"
prepare_target "${NO_PACKAGE_TARGET}"
expect_success "${NO_PACKAGE_TARGET}" "${NO_PACKAGE_IMAGES}" "${NO_PACKAGE_EXTRA}"
ensure_no_docker_loads
if [[ ! -f "${NO_PACKAGE_EXTRA}/rollback-manifest.json" ]]; then
  echo "expected rollback manifest to be written even when extra dir was initially missing" >&2
  exit 1
fi
rm -f "${STATE_FILE}"

EMPTY_PACKAGE_TARGET="${TMP_DIR}/empty-package-target"
EMPTY_PACKAGE_IMAGES="${TMP_DIR}/empty-package-images"
EMPTY_PACKAGE_EXTRA="${TMP_DIR}/empty-package-extra"
prepare_target "${EMPTY_PACKAGE_TARGET}"
mkdir -p "${EMPTY_PACKAGE_IMAGES}" "${EMPTY_PACKAGE_EXTRA}"
expect_success "${EMPTY_PACKAGE_TARGET}" "${EMPTY_PACKAGE_IMAGES}" "${EMPTY_PACKAGE_EXTRA}"
ensure_no_docker_loads
if [[ ! -f "${EMPTY_PACKAGE_EXTRA}/rollback-manifest.json" ]]; then
  echo "expected rollback manifest to be written when extra dir is empty" >&2
  exit 1
fi
rm -f "${STATE_FILE}"

BAD_TARGET="${TMP_DIR}/bad-target"
BAD_IMAGES="${TMP_DIR}/bad-images"
BAD_EXTRA="${TMP_DIR}/bad-extra"
prepare_target "${BAD_TARGET}"
mkdir -p "${BAD_IMAGES}" "${BAD_EXTRA}"
printf 'one' > "${BAD_IMAGES}/one.tar"
prepare_manifest "${BAD_EXTRA}" "one.tar"
cat > "${BAD_EXTRA}/checksums.txt" <<'EOF_BAD'
deadbeef  one.tar
EOF_BAD
expect_failure "checksums validation failed" "${BAD_TARGET}" "${BAD_IMAGES}" "${BAD_EXTRA}"

GOOD_TARGET="${TMP_DIR}/good-target"
GOOD_IMAGES="${TMP_DIR}/good-images"
GOOD_EXTRA="${TMP_DIR}/good-extra"
prepare_target "${GOOD_TARGET}"
mkdir -p "${GOOD_IMAGES}" "${GOOD_EXTRA}"
printf 'one' > "${GOOD_IMAGES}/one.tar"
printf 'two' > "${GOOD_IMAGES}/two.tar"
prepare_manifest "${GOOD_EXTRA}" "one.tar" "two.tar"
cat > "${GOOD_EXTRA}/release-manifest.json" <<'EOF_OBJECT_MANIFEST'
{"formatVersion":1,"images":[{"archive":"one.tar","images":[]},{"archive":"two.tar","images":[]}]}
EOF_OBJECT_MANIFEST
write_checksums "${GOOD_IMAGES}" "${GOOD_EXTRA}" one.tar two.tar
expect_success "${GOOD_TARGET}" "${GOOD_IMAGES}" "${GOOD_EXTRA}"

if ! diff -u <(printf 'one.tar\ntwo.tar\n') "${TMP_DIR}/docker-load.log"; then
  echo "expected docker load to follow manifest order" >&2
  exit 1
fi

rm -f "${STATE_FILE}"
prepare_manifest "${GOOD_EXTRA}" "one.tar" "two.tar"
expect_success "${GOOD_TARGET}" "${GOOD_IMAGES}" "${GOOD_EXTRA}"
diff -u <(printf 'one.tar\ntwo.tar\n') "${TMP_DIR}/docker-load.log"

rm -f "${STATE_FILE}"
printf '{"images":[{"archive":"../outside.tar"}]}' > "${GOOD_EXTRA}/release-manifest.json"
expect_failure "invalid image manifest" "${GOOD_TARGET}" "${GOOD_IMAGES}" "${GOOD_EXTRA}"
