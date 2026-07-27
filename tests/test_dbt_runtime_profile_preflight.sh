#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
HELPER="${REPO_ROOT}/services/dts-platform/prepare-dbt-runtime-profile-root.sh"

if [[ ! -f "${HELPER}" ]]; then
  echo "missing shared dbt runtime profile preflight: ${HELPER}" >&2
  exit 1
fi

test_root="$(mktemp -d /dev/shm/dts-dbt-runtime-preflight.XXXXXX)"
disk_test_root=""
cleanup() {
  rm -rf -- "${test_root}"
  if [[ -n "${disk_test_root}" ]]; then
    rm -rf -- "${disk_test_root}"
  fi
}
trap cleanup EXIT

runtime_root="${test_root}/dts-dbt-runtime"
DTS_DBT_RUNTIME_PROFILE_ROOT="${runtime_root}" \
DTS_DBT_RUNTIME_PROFILE_EXPECTED_UID="$(id -u)" \
  sh "${HELPER}"

[[ -d "${runtime_root}" ]]
[[ "$(stat -c '%u' "${runtime_root}")" == "$(id -u)" ]]
[[ "$(stat -c '%a' "${runtime_root}")" == "700" ]]
[[ "$(stat -f -c '%T' "${runtime_root}")" =~ ^(tmpfs|ramfs)$ ]]

file_parent="${test_root}/file-case"
mkdir -p -- "${file_parent}"
touch "${file_parent}/dts-dbt-runtime"
if DTS_DBT_RUNTIME_PROFILE_ROOT="${file_parent}/dts-dbt-runtime" \
  DTS_DBT_RUNTIME_PROFILE_EXPECTED_UID="$(id -u)" \
  sh "${HELPER}" >/dev/null 2>&1; then
  echo "preflight accepted a non-directory runtime root" >&2
  exit 1
fi

symlink_parent="${test_root}/symlink-case"
mkdir -p -- "${symlink_parent}/target"
ln -s -- "${symlink_parent}/target" "${symlink_parent}/dts-dbt-runtime"
if DTS_DBT_RUNTIME_PROFILE_ROOT="${symlink_parent}/dts-dbt-runtime" \
  DTS_DBT_RUNTIME_PROFILE_EXPECTED_UID="$(id -u)" \
  sh "${HELPER}" >/dev/null 2>&1; then
  echo "preflight accepted a symbolic-link runtime root" >&2
  exit 1
fi

invalid_uid_parent="${test_root}/invalid-uid-case"
mkdir -p -- "${invalid_uid_parent}"
if DTS_DBT_RUNTIME_PROFILE_ROOT="${invalid_uid_parent}/dts-dbt-runtime" \
  DTS_DBT_RUNTIME_PROFILE_EXPECTED_UID="not-a-uid" \
  sh "${HELPER}" >/dev/null 2>&1; then
  echo "preflight accepted a non-numeric expected uid" >&2
  exit 1
fi

disk_test_root="$(mktemp -d /tmp/dts-dbt-runtime-preflight.XXXXXX)"
if [[ ! "$(stat -f -c '%T' "${disk_test_root}")" =~ ^(tmpfs|ramfs)$ ]]; then
  disk_runtime_root="${disk_test_root}/dts-dbt-runtime"
  mkdir -m 0755 -- "${disk_runtime_root}"
  if DTS_DBT_RUNTIME_PROFILE_ROOT="${disk_runtime_root}" \
    DTS_DBT_RUNTIME_PROFILE_EXPECTED_UID="$(id -u)" \
    sh "${HELPER}" >/dev/null 2>&1; then
    echo "preflight accepted a non-tmpfs runtime root" >&2
    exit 1
  fi
  if [[ "$(stat -c '%a' "${disk_runtime_root}")" != "755" ]]; then
    echo "preflight modified a non-tmpfs runtime root before rejecting it" >&2
    exit 1
  fi
fi

python3 - <<'PY' "${REPO_ROOT}"
import pathlib
import sys
import yaml

repo = pathlib.Path(sys.argv[1])
compose_files = (
    "docker-compose-app.yml",
    "docker-compose.dev.yml",
    "docker-compose.legacy.yml",
)
errors = []

for filename in compose_files:
    with (repo / filename).open("r", encoding="utf-8") as handle:
        document = yaml.safe_load(handle) or {}
    services = document.get("services") or {}
    preflight = services.get("dts-dbt-runtime-init")
    platform = services.get("dts-platform") or {}

    if not isinstance(preflight, dict):
        errors.append(f"{filename}: missing dts-dbt-runtime-init service")
        continue
    if preflight.get("restart") != "no":
        errors.append(f"{filename}: preflight restart must be 'no'")
    preflight_environment = preflight.get("environment") or {}
    if (
        preflight_environment.get(
            "DTS_DBT_RUNTIME_PROFILE_REPAIR_DOCKER_CREATED_ROOT"
        )
        != "true"
    ):
        errors.append(f"{filename}: preflight must enable guarded Docker-root repair")

    depends_on = platform.get("depends_on") or {}
    dependency = depends_on.get("dts-dbt-runtime-init") or {}
    if dependency.get("condition") != "service_completed_successfully":
        errors.append(
            f"{filename}: dts-platform must wait for successful runtime preflight"
        )

    volumes = preflight.get("volumes") or []
    rendered_volumes = "\n".join(str(volume) for volume in volumes)
    if "/dev/shm" not in rendered_volumes or "/host-dev-shm" not in rendered_volumes:
        errors.append(f"{filename}: preflight must bind the host /dev/shm parent")
    if "prepare-dbt-runtime-profile-root.sh" not in rendered_volumes:
        errors.append(f"{filename}: preflight must mount the shared helper read-only")

    platform_volumes = platform.get("volumes") or []
    platform_environment = platform.get("environment") or {}
    if (
        platform_environment.get(
            "DTS_DBT_RUNTIME_PROFILE_REPAIR_DOCKER_CREATED_ROOT"
        )
        != "true"
    ):
        errors.append(f"{filename}: dts-platform startup must enable guarded repair")
    platform_volume_text = "\n".join(str(volume) for volume in platform_volumes)
    if "prepare-dbt-runtime-profile-root.sh" not in platform_volume_text:
        errors.append(f"{filename}: dts-platform must mount the shared helper")

    runtime_mounts = [
        volume
        for volume in platform_volumes
        if (
            isinstance(volume, dict)
            and volume.get("target") == "/run/dts-dbt-runtime"
        )
        or (
            isinstance(volume, str)
            and ":/run/dts-dbt-runtime" in volume
        )
    ]
    if len(runtime_mounts) != 1:
        errors.append(f"{filename}: expected one dts-platform runtime mount")
    elif isinstance(runtime_mounts[0], dict):
        create_host_path = (runtime_mounts[0].get("bind") or {}).get(
            "create_host_path"
        )
        if create_host_path is not True:
            errors.append(
                f"{filename}: runtime mount must allow Docker to create the source"
            )

    if filename == "docker-compose.dev.yml":
        if "prepare-dbt-runtime-profile-root.sh" not in str(platform.get("command")):
            errors.append(f"{filename}: dev startup must run the shared preflight")
    else:
        if platform.get("entrypoint") != "/bin/sh":
            errors.append(f"{filename}: dts-platform must use the guarded entrypoint")
        if "dts-platform-entrypoint.sh" not in str(platform.get("command")):
            errors.append(f"{filename}: guarded entrypoint command is missing")
        if "dts-platform-entrypoint.sh" not in platform_volume_text:
            errors.append(f"{filename}: guarded entrypoint must be mounted read-only")

if errors:
    raise SystemExit("\n".join(errors))
PY
