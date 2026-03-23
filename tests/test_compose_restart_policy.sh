#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

python3 - <<'PY' "${REPO_ROOT}"
import pathlib
import sys
import yaml

repo = pathlib.Path(sys.argv[1])

def load_yaml(path: pathlib.Path):
    with path.open("r", encoding="utf-8") as fh:
        return yaml.safe_load(fh)

legacy = load_yaml(repo / "docker-compose.legacy.yml")
app = load_yaml(repo / "docker-compose-app.yml")

expected_unless_stopped = {
    ("legacy", "dts-admin"),
    ("legacy", "dts-platform"),
    ("legacy", "dts-ingestion"),
    ("legacy", "dts-analytics"),
    ("legacy", "dts-analytics-webapp-modern"),
    ("legacy", "dts-admin-webapp"),
    ("legacy", "dts-platform-webapp"),
    ("app", "dts-admin"),
    ("app", "dts-platform"),
    ("app", "dts-ingestion"),
    ("app", "dts-analytics"),
    ("app", "dts-analytics-webapp-modern"),
    ("app", "dts-admin-webapp"),
    ("app", "dts-platform-webapp"),
}

expected_no_restart = {
    ("legacy", "dts-airflow-init"),
    ("legacy", "dts-openmetadata-init"),
    ("legacy", "dts-openmetadata-ingestion"),
}

docs = {"legacy": legacy, "app": app}
errors = []

for scope, service in sorted(expected_unless_stopped):
    restart = (((docs[scope] or {}).get("services") or {}).get(service) or {}).get("restart")
    if restart != "unless-stopped":
        errors.append(f"{scope}:{service} restart expected 'unless-stopped', got {restart!r}")

for scope, service in sorted(expected_no_restart):
    restart = (((docs[scope] or {}).get("services") or {}).get(service) or {}).get("restart")
    if restart != "no":
        errors.append(f"{scope}:{service} restart expected 'no', got {restart!r}")

if errors:
    raise SystemExit("\n".join(errors))
PY
