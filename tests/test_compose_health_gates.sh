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

expected_health = {
    ("legacy", "dts-admin"): "management/health",
    ("legacy", "dts-platform"): "management/health",
    ("legacy", "dts-ingestion"): "management/health",
    ("legacy", "dts-analytics"): "/api/health",
    ("app", "dts-admin"): "management/health",
    ("app", "dts-platform"): "management/health",
    ("app", "dts-ingestion"): "management/health",
    ("app", "dts-analytics"): "/api/health",
}

docs = {"legacy": legacy, "app": app}
errors = []

for (scope, service), expected_fragment in sorted(expected_health.items()):
    node = (((docs[scope] or {}).get("services") or {}).get(service) or {})
    health = node.get("healthcheck")
    if not health:
        errors.append(f"{scope}:{service} missing healthcheck")
        continue
    test_cmd = health.get("test")
    if not test_cmd or expected_fragment not in " ".join(str(item) for item in test_cmd):
        errors.append(f"{scope}:{service} healthcheck does not contain {expected_fragment!r}: {test_cmd!r}")

for scope in ("legacy", "app"):
    webapp = (((docs[scope] or {}).get("services") or {}).get("dts-platform-webapp") or {})
    depends_on = webapp.get("depends_on")
    if not isinstance(depends_on, dict):
        errors.append(f"{scope}:dts-platform-webapp missing depends_on")
        continue
    for upstream in ("dts-platform", "dts-admin", "dts-analytics"):
        cond = ((depends_on.get(upstream) or {}).get("condition"))
        if cond != "service_healthy":
            errors.append(f"{scope}:dts-platform-webapp depends_on {upstream} expected service_healthy, got {cond!r}")

if errors:
    raise SystemExit("\n".join(errors))
PY
