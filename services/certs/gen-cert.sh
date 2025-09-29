#!/usr/bin/env bash
set -euo pipefail
# Compatibility wrapper for users calling gen-cert.sh
# Usage: gen-cert.sh [BASE_DOMAIN] [P12_PASSWORD]

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BASE_DOMAIN_IN="${1:-${BASE_DOMAIN:-}}"
P12_PASSWORD_IN="${2:-${P12_PASSWORD:-}}"

if [[ -n "${BASE_DOMAIN_IN}" ]]; then
  export BASE_DOMAIN="${BASE_DOMAIN_IN}"
fi
if [[ -n "${P12_PASSWORD_IN}" ]]; then
  export P12_PASSWORD="${P12_PASSWORD_IN}"
fi

exec "${SCRIPT_DIR}/gen-certs.sh"

