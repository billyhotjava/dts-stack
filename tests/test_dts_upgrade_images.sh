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

prepare_target() {
  local target_dir="$1"
  mkdir -p "${target_dir}"
  cat > "${target_dir}/docker-compose.yml" <<'EOF_COMPOSE'
services: {}
EOF_COMPOSE
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
  run_upgrade "$@" >"${output_file}" 2>&1
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
expect_failure "image tar referenced by manifest is missing" "${MISSING_TARGET}" "${MISSING_IMAGES}" "${MISSING_EXTRA}"

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
write_checksums "${GOOD_IMAGES}" "${GOOD_EXTRA}" one.tar two.tar
expect_success "${GOOD_TARGET}" "${GOOD_IMAGES}" "${GOOD_EXTRA}"

if ! diff -u <(printf 'one.tar\ntwo.tar\n') "${TMP_DIR}/docker-load.log"; then
  echo "expected docker load to follow manifest order" >&2
  exit 1
fi
