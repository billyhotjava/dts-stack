#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLATFORM_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RAW_DIR="${PLATFORM_DIR}/raw"

EXPECT_ARCH="aarch64"
REQUIRE_KYLIN=1
CHECK_CONTAINERS=1

usage() {
  cat <<USAGE
Usage: $(basename "$0") [options]

Options:
  --expect-arch <name>   Expected host arch (default: aarch64)
  --allow-non-kylin      Do not fail on non-kylin distro
  --skip-containers      Do not enforce required container checks
  -h, --help             Show help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --expect-arch)
      EXPECT_ARCH="${2:-}"
      shift 2
      ;;
    --allow-non-kylin)
      REQUIRE_KYLIN=0
      shift
      ;;
    --skip-containers)
      CHECK_CONTAINERS=0
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

mkdir -p "${RAW_DIR}"
RUN_AT="$(date -u +"%Y%m%dT%H%M%SZ")-$$"
REPORT_FILE="${RAW_DIR}/arm-kylin-preflight-${RUN_AT}.txt"

HOST_ARCH="$(uname -m 2>/dev/null || echo unknown)"
HOST_KERNEL="$(uname -sr 2>/dev/null || echo unknown)"
OS_ID="unknown"
OS_NAME="unknown"
OS_LIKE=""
if [[ -f /etc/os-release ]]; then
  # shellcheck disable=SC1091
  source /etc/os-release
  OS_ID="${ID:-unknown}"
  OS_NAME="${NAME:-unknown}"
  OS_LIKE="${ID_LIKE:-}"
fi

is_kylin=0
case "$(printf '%s %s %s' "${OS_ID}" "${OS_NAME}" "${OS_LIKE}" | tr '[:upper:]' '[:lower:]')" in
  *kylin*)
    is_kylin=1
    ;;
esac

arch_ok=0
if [[ "${HOST_ARCH}" == "${EXPECT_ARCH}" ]]; then
  arch_ok=1
fi

docker_ok=1
docker_err=""
if ! docker ps --format '{{.Names}}' >/tmp/arm_kylin_preflight_ps.$$ 2>/tmp/arm_kylin_preflight_ps_err.$$; then
  docker_ok=0
  docker_err="$(cat /tmp/arm_kylin_preflight_ps_err.$$ 2>/dev/null || true)"
fi

required_containers=("dts-stack-dts-pg-1" "dts-stack-dts-ingestion-1")
missing_containers=()
if [[ "${CHECK_CONTAINERS}" -eq 1 && "${docker_ok}" -eq 1 ]]; then
  for c in "${required_containers[@]}"; do
    if ! grep -qx "${c}" /tmp/arm_kylin_preflight_ps.$$; then
      missing_containers+=("${c}")
    fi
  done
fi

status="PASS"
if [[ "${arch_ok}" -ne 1 ]]; then
  status="FAIL"
fi
if [[ "${REQUIRE_KYLIN}" -eq 1 && "${is_kylin}" -ne 1 ]]; then
  status="FAIL"
fi
if [[ "${docker_ok}" -ne 1 ]]; then
  status="FAIL"
fi
if [[ "${CHECK_CONTAINERS}" -eq 1 && "${#missing_containers[@]}" -gt 0 ]]; then
  status="FAIL"
fi

{
  echo "run_at_utc=${RUN_AT}"
  echo "status=${status}"
  echo "host_arch=${HOST_ARCH}"
  echo "expect_arch=${EXPECT_ARCH}"
  echo "host_kernel=${HOST_KERNEL}"
  echo "os_id=${OS_ID}"
  echo "os_name=${OS_NAME}"
  echo "os_like=${OS_LIKE}"
  echo "is_kylin=${is_kylin}"
  echo "require_kylin=${REQUIRE_KYLIN}"
  echo "docker_ok=${docker_ok}"
  if [[ "${docker_ok}" -ne 1 ]]; then
    echo "docker_error=${docker_err}"
  fi
  echo "check_containers=${CHECK_CONTAINERS}"
  if [[ "${CHECK_CONTAINERS}" -eq 1 ]]; then
    echo "required_containers=${required_containers[*]}"
    if [[ "${#missing_containers[@]}" -eq 0 ]]; then
      echo "missing_containers="
    else
      echo "missing_containers=${missing_containers[*]}"
    fi
  fi
} > "${REPORT_FILE}"

rm -f /tmp/arm_kylin_preflight_ps.$$ /tmp/arm_kylin_preflight_ps_err.$$

echo "preflight report: ${REPORT_FILE}"
echo "status: ${status}"
if [[ "${status}" != "PASS" ]]; then
  exit 2
fi
