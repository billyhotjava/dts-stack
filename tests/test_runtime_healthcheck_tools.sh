#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

python3 - <<'PY' "${REPO_ROOT}"
import pathlib
import sys

repo = pathlib.Path(sys.argv[1])

expected = {
    "builds/dts-admin/Dockerfile": "curl",
    "builds/dts-platform/Dockerfile": "curl",
    "builds/dts-ingestion/Dockerfile": "curl",
    "builds/dts-analytics/Dockerfile": "curl",
}

errors = []

for rel_path, fragment in expected.items():
    content = (repo / rel_path).read_text(encoding="utf-8")
    if fragment not in content:
        errors.append(f"{rel_path} missing runtime healthcheck tool fragment {fragment!r}")

if errors:
    raise SystemExit("\n".join(errors))
PY
