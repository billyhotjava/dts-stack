#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

setup_common_repo() {
  local target_repo="$1"
  mkdir -p "${target_repo}/builds" "${target_repo}/builds/dts-admin" "${target_repo}/source"
  cp "${REPO_ROOT}/builds/dts-build.sh" "${target_repo}/builds/dts-build.sh"
  chmod +x "${target_repo}/builds/dts-build.sh"
  cat > "${target_repo}/builds/dts-admin/Dockerfile" <<'EOF_DOCKERFILE'
FROM scratch
EOF_DOCKERFILE
  cat > "${target_repo}/builds/dts-admin/Dockerfile.offline" <<'EOF_DOCKERFILE'
FROM scratch
EOF_DOCKERFILE
}

SCENARIO1_REPO="${TMP_DIR}/scenario1-repo"
SCENARIO1_BIN="${TMP_DIR}/scenario1-bin"
mkdir -p "${SCENARIO1_BIN}"
setup_common_repo "${SCENARIO1_REPO}"

cat > "${SCENARIO1_BIN}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
case "${1:-}" in
  build)
    if [[ "${2:-}" == "--help" ]]; then
      echo "--progress"
    fi
    exit 0
    ;;
  version)
    echo "1.41"
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
EOF_DOCKER
chmod +x "${SCENARIO1_BIN}/docker"

cat > "${SCENARIO1_BIN}/free" <<'EOF_FREE'
#!/usr/bin/env bash
cat <<'EOF_MEM'
              total        used        free      shared  buff/cache   available
Mem:          65536       16384       32768           0       16384       32768
Swap:             0           0           0
EOF_MEM
EOF_FREE
chmod +x "${SCENARIO1_BIN}/free"

cat > "${SCENARIO1_BIN}/df" <<'EOF_DF'
#!/usr/bin/env bash
cat <<'EOF_DISK'
Filesystem     1G-blocks  Used Available Use% Mounted on
/dev/vda2            295   256        39  87% /
EOF_DISK
EOF_DF
chmod +x "${SCENARIO1_BIN}/df"

SCENARIO1_LOG="${TMP_DIR}/scenario1.log"
if PATH="${SCENARIO1_BIN}:${PATH}" "${SCENARIO1_REPO}/builds/dts-build.sh" -all >"${SCENARIO1_LOG}" 2>&1; then
  echo "expected -all to fail preflight when only 39GB is available" >&2
  exit 1
fi

if ! grep -q 'requires at least 45GB free disk' "${SCENARIO1_LOG}"; then
  echo "expected mode-aware disk guard for -all builds" >&2
  cat "${SCENARIO1_LOG}" >&2
  exit 1
fi

if ! grep -q -- '--no-save' "${SCENARIO1_LOG}"; then
  echo "expected disk guard to recommend --no-save" >&2
  cat "${SCENARIO1_LOG}" >&2
  exit 1
fi

SCENARIO2_REPO="${TMP_DIR}/scenario2-repo"
SCENARIO2_BIN="${TMP_DIR}/scenario2-bin"
SCENARIO2_SAVE_LOG="${TMP_DIR}/scenario2-save.log"
mkdir -p "${SCENARIO2_BIN}"
setup_common_repo "${SCENARIO2_REPO}"

cat > "${SCENARIO2_BIN}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
set -euo pipefail

case "${1:-}" in
  build)
    if [[ "${2:-}" == "--help" ]]; then
      echo "--progress"
    fi
    exit 0
    ;;
  version)
    echo "1.41"
    exit 0
    ;;
  inspect)
    echo "arm64"
    exit 0
    ;;
  image|builder)
    exit 0
    ;;
  save)
    printf 'save-called\n' >> "__SAVE_LOG__"
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
EOF_DOCKER
sed -i "s|__SAVE_LOG__|${SCENARIO2_SAVE_LOG}|g" "${SCENARIO2_BIN}/docker"
chmod +x "${SCENARIO2_BIN}/docker"

cat > "${SCENARIO2_BIN}/free" <<'EOF_FREE'
#!/usr/bin/env bash
cat <<'EOF_MEM'
              total        used        free      shared  buff/cache   available
Mem:          32768        8192       16384           0        8192       16384
Swap:             0           0           0
EOF_MEM
EOF_FREE
chmod +x "${SCENARIO2_BIN}/free"

cat > "${SCENARIO2_BIN}/df" <<'EOF_DF'
#!/usr/bin/env bash
cat <<'EOF_DISK'
Filesystem     1G-blocks  Used Available Use% Mounted on
/dev/vda2            295   283        12  96% /
EOF_DISK
EOF_DF
chmod +x "${SCENARIO2_BIN}/df"

PATH="${SCENARIO2_BIN}:${PATH}" PREBUILD_JARS=0 "${SCENARIO2_REPO}/builds/dts-build.sh" --image dts-admin --no-save >/dev/null

if [[ -f "${SCENARIO2_SAVE_LOG}" ]]; then
  echo "expected --no-save to skip docker save" >&2
  cat "${SCENARIO2_SAVE_LOG}" >&2
  exit 1
fi
