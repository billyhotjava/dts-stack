#!/usr/bin/env bash
set -euo pipefail
module_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
action="${1:-verify}"
if [[ $# -gt 0 ]]; then shift; fi
case "$action" in
  verify|package) exec "$module_dir/scripts/with-test-postgres.sh" "${MVN:-mvn}" -B -ntp -f "$module_dir/analytics/pom.xml" clean "$action" "$@" ;;
  --help|-h|help) echo 'Usage: ./build.sh [verify|package] [Maven arguments...]' ;;
  *) echo "Unsupported build action: $action" >&2; exit 2 ;;
esac
