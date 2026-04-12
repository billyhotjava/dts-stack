#!/usr/bin/env bash
# 统一入口：bash run.sh [smoke|admin|platform|all]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

SUITE="${1:-smoke}"
shift || true

case "${SUITE}" in
  smoke)    MARKERS="-m smoke" ;;
  admin)    MARKERS="-m admin" ;;
  platform) MARKERS="-m platform" ;;
  all)      MARKERS="" ;;
  *) echo "usage: $0 [smoke|admin|platform|all]"; exit 2 ;;
esac

# 自动激活 venv（如存在）
if [[ -d .venv && -z "${VIRTUAL_ENV:-}" ]]; then
  # shellcheck disable=SC1091
  source .venv/bin/activate
fi

rm -rf reports/allure-results
mkdir -p reports/allure-results

exec pytest ${MARKERS} "$@"
