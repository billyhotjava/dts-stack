#!/usr/bin/env bash
# 统一入口：bash run.sh [smoke|admin|platform|all]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

# 自动加载 .env（简单解析）
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

SUITE="${1:-smoke}"
shift || true

case "${SUITE}" in
  smoke)    GROUPS="-Dgroups=smoke" ;;
  admin)    GROUPS="-Dgroups=admin" ;;
  platform) GROUPS="-Dgroups=platform" ;;
  all)      GROUPS="" ;;
  *) echo "usage: $0 [smoke|admin|platform|all]"; exit 2 ;;
esac

# JUnit5 tag filter via surefire groups system property
MVN="${MVN:-./mvnw}"
[[ -x "${MVN}" ]] || MVN="mvn"

rm -rf target/allure-results
exec "${MVN}" -q test ${GROUPS} "$@"
