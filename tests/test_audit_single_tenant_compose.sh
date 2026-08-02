#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TEST_ROOT="$(mktemp -d /tmp/dts-audit-single-tenant.XXXXXX)"
trap 'rm -rf -- "${TEST_ROOT}"' EXIT

FILTERED_ENV="${TEST_ROOT}/without-audit-tenancy.env"
awk '!/^(AUDIT_TENANCY_MODE|AUDIT_TENANT_ID)=/' "${REPO_ROOT}/.env" > "${FILTERED_ENV}"

for compose_file in \
  docker-compose-app.yml \
  docker-compose.dev.yml \
  docker-compose.legacy.yml
do
  rendered="${TEST_ROOT}/${compose_file}.rendered.yml"
  env -u AUDIT_TENANCY_MODE -u AUDIT_TENANT_ID \
    docker compose \
      --env-file "${FILTERED_ENV}" \
      -f "${REPO_ROOT}/${compose_file}" \
      config > "${rendered}"

  python3 - "${compose_file}" "${rendered}" <<'PY'
import json
import pathlib
import sys
import yaml

compose_file = sys.argv[1]
rendered = yaml.safe_load(pathlib.Path(sys.argv[2]).read_text(encoding="utf-8")) or {}
services = rendered.get("services") or {}
errors = []

for service_name in ("dts-platform", "dts-ingestion"):
    environment = (services.get(service_name) or {}).get("environment") or {}
    for retired_name in ("AUDIT_TENANCY_MODE", "AUDIT_TENANT_ID"):
        if retired_name in environment:
            errors.append(
                f"{compose_file}:{service_name} still exposes retired {retired_name}"
            )

platform_environment = (services.get("dts-platform") or {}).get("environment") or {}
application_json = platform_environment.get("SPRING_APPLICATION_JSON")
try:
    application_config = json.loads(application_json or "")
except json.JSONDecodeError:
    errors.append(f"{compose_file}:dts-platform has no valid internal Spring configuration")
else:
    audit_config = application_config.get("auditing") or {}
    if audit_config != {"tenancy-mode": "SINGLE_TENANT", "tenant-id": "default"}:
        errors.append(
            f"{compose_file}:dts-platform internal audit owner is not SINGLE_TENANT/default"
        )

if errors:
    raise SystemExit("\n".join(errors))
PY
done
