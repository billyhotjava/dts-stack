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
  if command -v sudo >/dev/null 2>&1 && sudo -n true 2>/dev/null; then
    sudo -n chown -R "$(id -u):$(id -g)" "${test_root}" 2>/dev/null || true
  fi
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

if command -v sudo >/dev/null 2>&1 && sudo -n true 2>/dev/null; then
  root_owned_parent="${test_root}/root-owned-case"
  mkdir -m 0755 -- "${root_owned_parent}"
  root_owned_runtime_root="${root_owned_parent}/dts-dbt-runtime"
  sudo -n mkdir -m 0700 -- "${root_owned_runtime_root}"

  sudo -n env \
    DTS_DBT_RUNTIME_PROFILE_ROOT="${root_owned_runtime_root}" \
    DTS_DBT_RUNTIME_PROFILE_EXPECTED_UID="$(id -u)" \
    DTS_DBT_RUNTIME_PROFILE_REPAIR_DOCKER_CREATED_ROOT="true" \
    sh "${HELPER}"

  [[ "$(stat -c '%u' "${root_owned_runtime_root}")" == "$(id -u)" ]]
  [[ "$(stat -c '%a' "${root_owned_runtime_root}")" == "700" ]]

  stale_uid_parent="${test_root}/stale-uid-case"
  mkdir -m 0755 -- "${stale_uid_parent}"
  stale_uid_runtime_root="${stale_uid_parent}/dts-dbt-runtime"
  sudo -n mkdir -m 0700 -- "${stale_uid_runtime_root}"
  sudo -n chown 1000:0 -- "${stale_uid_runtime_root}"

  sudo -n env \
    DTS_DBT_RUNTIME_PROFILE_ROOT="${stale_uid_runtime_root}" \
    DTS_DBT_RUNTIME_PROFILE_EXPECTED_UID="0" \
    DTS_DBT_RUNTIME_PROFILE_REPAIR_DOCKER_CREATED_ROOT="true" \
    sh "${HELPER}"

  [[ "$(stat -c '%u' "${stale_uid_runtime_root}")" == "0" ]]
  [[ "$(stat -c '%a' "${stale_uid_runtime_root}")" == "700" ]]

  unknown_uid_parent="${test_root}/unknown-uid-case"
  mkdir -m 0755 -- "${unknown_uid_parent}"
  unknown_uid_runtime_root="${unknown_uid_parent}/dts-dbt-runtime"
  sudo -n mkdir -m 0700 -- "${unknown_uid_runtime_root}"
  sudo -n chown 2000:0 -- "${unknown_uid_runtime_root}"
  if sudo -n env \
    DTS_DBT_RUNTIME_PROFILE_ROOT="${unknown_uid_runtime_root}" \
    DTS_DBT_RUNTIME_PROFILE_EXPECTED_UID="0" \
    DTS_DBT_RUNTIME_PROFILE_REPAIR_DOCKER_CREATED_ROOT="true" \
    DTS_DBT_RUNTIME_PROFILE_TRUSTED_STALE_UIDS="0,1000" \
    sh "${HELPER}" >/dev/null 2>&1; then
    echo "preflight repaired a runtime root owned by an untrusted UID" >&2
    exit 1
  fi
  [[ "$(stat -c '%u' "${unknown_uid_runtime_root}")" == "2000" ]]

  nonempty_stale_uid_parent="${test_root}/nonempty-stale-uid-case"
  mkdir -m 0755 -- "${nonempty_stale_uid_parent}"
  nonempty_stale_uid_runtime_root="${nonempty_stale_uid_parent}/dts-dbt-runtime"
  sudo -n mkdir -m 0700 -- "${nonempty_stale_uid_runtime_root}"
  sudo -n touch "${nonempty_stale_uid_runtime_root}/active-lease"
  sudo -n chown -R 1000:0 -- "${nonempty_stale_uid_runtime_root}"
  if sudo -n env \
    DTS_DBT_RUNTIME_PROFILE_ROOT="${nonempty_stale_uid_runtime_root}" \
    DTS_DBT_RUNTIME_PROFILE_EXPECTED_UID="0" \
    DTS_DBT_RUNTIME_PROFILE_REPAIR_DOCKER_CREATED_ROOT="true" \
    DTS_DBT_RUNTIME_PROFILE_TRUSTED_STALE_UIDS="0,1000" \
    sh "${HELPER}" >/dev/null 2>&1; then
    echo "preflight repaired a non-empty runtime root owned by a trusted stale UID" >&2
    exit 1
  fi
  [[ "$(stat -c '%u' "${nonempty_stale_uid_runtime_root}")" == "1000" ]]
  sudo -n test -f "${nonempty_stale_uid_runtime_root}/active-lease"

  nonempty_parent="${test_root}/nonempty-root-owned-case"
  mkdir -m 0755 -- "${nonempty_parent}"
  nonempty_runtime_root="${nonempty_parent}/dts-dbt-runtime"
  sudo -n mkdir -m 0700 -- "${nonempty_runtime_root}"
  sudo -n touch "${nonempty_runtime_root}/active-lease"
  if sudo -n env \
    DTS_DBT_RUNTIME_PROFILE_ROOT="${nonempty_runtime_root}" \
    DTS_DBT_RUNTIME_PROFILE_EXPECTED_UID="$(id -u)" \
    DTS_DBT_RUNTIME_PROFILE_REPAIR_DOCKER_CREATED_ROOT="true" \
    sh "${HELPER}" >/dev/null 2>&1; then
    echo "preflight repaired a non-empty root-owned runtime root" >&2
    exit 1
  fi
  [[ "$(stat -c '%u' "${nonempty_runtime_root}")" == "0" ]]
  [[ "$(stat -c '%a' "${nonempty_runtime_root}")" == "700" ]]
  sudo -n test -f "${nonempty_runtime_root}/active-lease"
else
  echo "SKIP: root-owned runtime repair test requires passwordless sudo" >&2
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
import re
import subprocess
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
    depends_on = platform.get("depends_on") or {}
    dependency = depends_on.get("dts-dbt-runtime-init")

    if filename == "docker-compose.legacy.yml":
        if preflight is not None:
            errors.append(
                f"{filename}: legacy startup must not add a Compose-version-sensitive dbt init service"
            )
        if dependency is not None:
            errors.append(
                f"{filename}: legacy dts-platform must not add a dbt init completion dependency"
            )
    else:
        if not isinstance(preflight, dict):
            errors.append(f"{filename}: missing dts-dbt-runtime-init service")
        else:
            if preflight.get("restart") != "no":
                errors.append(f"{filename}: preflight restart must be 'no'")
            preflight_environment = preflight.get("environment") or {}
            if (
                preflight_environment.get(
                    "DTS_DBT_RUNTIME_PROFILE_REPAIR_DOCKER_CREATED_ROOT"
                )
                != "true"
            ):
                errors.append(
                    f"{filename}: preflight must enable guarded Docker-root repair"
                )

            volumes = preflight.get("volumes") or []
            rendered_volumes = "\n".join(str(volume) for volume in volumes)
            if (
                "/dev/shm" not in rendered_volumes
                or "/host-dev-shm" not in rendered_volumes
            ):
                errors.append(
                    f"{filename}: preflight must bind the host /dev/shm parent"
                )
            if "prepare-dbt-runtime-profile-root.sh" not in rendered_volumes:
                errors.append(
                    f"{filename}: preflight must mount the shared helper read-only"
                )

        if not isinstance(dependency, dict) or (
            dependency.get("condition") != "service_completed_successfully"
        ):
            errors.append(
                f"{filename}: dts-platform must wait for successful runtime preflight"
            )

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
        if filename == "docker-compose.legacy.yml":
            for script_name in (
                "prepare-dbt-runtime-profile-root.sh",
                "dts-platform-entrypoint.sh",
            ):
                matching_mounts = [
                    str(volume)
                    for volume in platform_volumes
                    if script_name in str(volume)
                ]
                if not matching_mounts or not matching_mounts[0].endswith(":ro,z"):
                    errors.append(
                        f"{filename}: {script_name} mount must be SELinux-readable (:ro,z)"
                    )

init_source = (repo / "init.sh").read_text(encoding="utf-8")
prepare_match = re.search(
    r"^prepare_dbt_runtime_profile_root\(\)\{(?P<body>.*?)^\}",
    init_source,
    flags=re.MULTILINE | re.DOTALL,
)
if not prepare_match:
    errors.append("init.sh: missing prepare_dbt_runtime_profile_root")
else:
    prepare_body = prepare_match.group("body")
    for expected_fragment in (
        'LEGACY_STACK',
        'docker run --rm',
        'DTS_DBT_RUNTIME_PROFILE_ROOT=/host-dev-shm/dts-dbt-runtime',
        'dts-dbt-runtime-init',
    ):
        if expected_fragment not in prepare_body:
            errors.append(
                f"init.sh: dbt runtime preflight missing {expected_fragment!r}"
            )

if errors:
    raise SystemExit("\n".join(errors))

tracked_profile = subprocess.run(
    [
        "git",
        "-C",
        str(repo),
        "ls-files",
        "--error-unmatch",
        "services/dts-dbt/profiles/profiles.yml",
    ],
    check=False,
    stdout=subprocess.DEVNULL,
    stderr=subprocess.DEVNULL,
)
if (
    tracked_profile.returncode == 0
    and (repo / "services/dts-dbt/profiles/profiles.yml").exists()
):
    raise SystemExit(
        "services/dts-dbt/profiles/profiles.yml must remain untracked"
    )

dockerignore = (repo / ".dockerignore").read_text(encoding="utf-8").splitlines()
if "services/dts-dbt/profiles/profiles.yml" not in dockerignore:
    raise SystemExit("local profiles.yml must be excluded from Docker contexts")

example = (
    repo / "services/dts-dbt/profiles/profiles.example.yml"
).read_text(encoding="utf-8")
if "DTS_DBT_DEV_PASSWORD" not in example or "env_var(" not in example:
    raise SystemExit("tracked dbt profile example must use environment placeholders")
PY
