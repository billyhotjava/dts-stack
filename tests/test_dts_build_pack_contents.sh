#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

TEST_REPO="${TMP_DIR}/repo"
FAKE_BIN="${TMP_DIR}/bin"
PACKAGE_PATH="${TMP_DIR}/dts-deploy.tar.gz"
mkdir -p \
  "${TEST_REPO}/builds" \
  "${TEST_REPO}/bin/lib" \
  "${TEST_REPO}/services/dts-dbt/models" \
  "${TEST_REPO}/services/dts-dbt/macros" \
  "${TEST_REPO}/services/dts-dbt/profiles" \
  "${TEST_REPO}/services/dts-airflow/config" \
  "${TEST_REPO}/builds/airflow" \
  "${TEST_REPO}/config" \
  "${TEST_REPO}/docs/release/v2.2.3" \
  "${TEST_REPO}/tools" \
  "${FAKE_BIN}"

cp "${REPO_ROOT}/builds/dts-build.sh" "${TEST_REPO}/builds/dts-build.sh"
chmod +x "${TEST_REPO}/builds/dts-build.sh"

cat > "${TEST_REPO}/init.sh" <<'EOF_FILE'
#!/usr/bin/env bash
EOF_FILE
chmod +x "${TEST_REPO}/init.sh"

cat > "${TEST_REPO}/start.sh" <<'EOF_FILE'
#!/usr/bin/env bash
EOF_FILE
chmod +x "${TEST_REPO}/start.sh"

cat > "${TEST_REPO}/stop.sh" <<'EOF_FILE'
#!/usr/bin/env bash
EOF_FILE
chmod +x "${TEST_REPO}/stop.sh"

cat > "${TEST_REPO}/encry.sh" <<'EOF_FILE'
#!/usr/bin/env bash
EOF_FILE
chmod +x "${TEST_REPO}/encry.sh"

cat > "${TEST_REPO}/docker-compose-app.yml" <<'EOF_FILE'
services: {}
EOF_FILE

cat > "${TEST_REPO}/docker-compose.dev.yml" <<'EOF_FILE'
services: {}
EOF_FILE

cat > "${TEST_REPO}/docker-compose.legacy.yml" <<'EOF_FILE'
services: {}
EOF_FILE

cat > "${TEST_REPO}/imgversion.conf" <<'EOF_FILE'
IMAGE_DTS_ADMIN=dts-admin:test
EOF_FILE

cat > "${TEST_REPO}/imgversion.dts-source.conf" <<'EOF_FILE'
IMAGE_DTS_PLATFORM=dts-platform:test
EOF_FILE

cat > "${TEST_REPO}/.dockerignore" <<'EOF_FILE'
target
EOF_FILE

cat > "${TEST_REPO}/services/dts-dbt/dbt_project.yml" <<'EOF_FILE'
name: dts
version: 1.0.0
EOF_FILE

cat > "${TEST_REPO}/bin/test-helper.sh" <<'EOF_FILE'
#!/usr/bin/env bash
echo helper
EOF_FILE
chmod +x "${TEST_REPO}/bin/test-helper.sh"

cat > "${TEST_REPO}/bin/lib/shared.sh" <<'EOF_FILE'
#!/usr/bin/env bash
echo shared
EOF_FILE
chmod +x "${TEST_REPO}/bin/lib/shared.sh"

cat > "${TEST_REPO}/bin/dts-upgrade" <<'EOF_FILE'
#!/usr/bin/env bash
echo upgrade
EOF_FILE
chmod +x "${TEST_REPO}/bin/dts-upgrade"

cat > "${TEST_REPO}/bin/dts-upgrade-lite" <<'EOF_FILE'
#!/usr/bin/env bash
echo upgrade-lite
EOF_FILE
chmod +x "${TEST_REPO}/bin/dts-upgrade-lite"

cat > "${TEST_REPO}/bin/dts-upgrade-rollback" <<'EOF_FILE'
#!/usr/bin/env bash
echo rollback
EOF_FILE
chmod +x "${TEST_REPO}/bin/dts-upgrade-rollback"

cat > "${TEST_REPO}/bin/lib/dts-upgrade-common.sh" <<'EOF_FILE'
#!/usr/bin/env bash
echo common
EOF_FILE
chmod +x "${TEST_REPO}/bin/lib/dts-upgrade-common.sh"

cat > "${TEST_REPO}/docs/release/v2.2.3/upgrade-lite-operations-kylin-kunpeng.md" <<'EOF_FILE'
# ops
EOF_FILE

cat > "${TEST_REPO}/docs/release/v2.2.3/offline-upgrade-checklist-kylin-kunpeng.md" <<'EOF_FILE'
# checklist
EOF_FILE

cat > "${TEST_REPO}/docs/release/v2.2.3/offline-upgrade-guide-kylin-kunpeng.md" <<'EOF_FILE'
# guide
EOF_FILE

cat > "${FAKE_BIN}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
case "${1:-}" in
  build)
    if [[ "${2:-}" == "--help" ]]; then
      echo "--progress"
    fi
    exit 0
    ;;
  version)
    echo "1.41"
    exit 0
    ;;
  image|builder)
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
EOF_DOCKER
chmod +x "${FAKE_BIN}/docker"

cat > "${FAKE_BIN}/free" <<'EOF_FREE'
#!/usr/bin/env bash
cat <<'EOF_MEM'
              total        used        free      shared  buff/cache   available
Mem:          65536       16384       32768           0       16384       32768
Swap:             0           0           0
EOF_MEM
EOF_FREE
chmod +x "${FAKE_BIN}/free"

cat > "${FAKE_BIN}/df" <<'EOF_DF'
#!/usr/bin/env bash
cat <<'EOF_DISK'
Filesystem     1G-blocks  Used Available Use% Mounted on
/dev/vda2            295   200        95  68% /
EOF_DISK
EOF_DF
chmod +x "${FAKE_BIN}/df"

PATH="${FAKE_BIN}:${PATH}" "${TEST_REPO}/builds/dts-build.sh" --pack --no-images --output "${PACKAGE_PATH}" >/dev/null
ARCHIVE_CONTENTS="$(tar -tzf "${PACKAGE_PATH}")"

if ! grep -qx 'dts-stack/bin/test-helper.sh' <<<"${ARCHIVE_CONTENTS}"; then
  echo "expected packaged archive to include bin/test-helper.sh" >&2
  exit 1
fi

if ! grep -qx 'dts-stack/bin/lib/shared.sh' <<<"${ARCHIVE_CONTENTS}"; then
  echo "expected packaged archive to include nested bin/lib/shared.sh" >&2
  exit 1
fi

if ! grep -qx 'dts-stack/bin/dts-upgrade' <<<"${ARCHIVE_CONTENTS}"; then
  echo "expected packaged archive to include bin/dts-upgrade" >&2
  exit 1
fi

if ! grep -qx 'dts-stack/bin/dts-upgrade-lite' <<<"${ARCHIVE_CONTENTS}"; then
  echo "expected packaged archive to include bin/dts-upgrade-lite" >&2
  exit 1
fi

if ! grep -qx 'dts-stack/bin/dts-upgrade-rollback' <<<"${ARCHIVE_CONTENTS}"; then
  echo "expected packaged archive to include bin/dts-upgrade-rollback" >&2
  exit 1
fi

if ! grep -qx 'dts-stack/bin/lib/dts-upgrade-common.sh' <<<"${ARCHIVE_CONTENTS}"; then
  echo "expected packaged archive to include bin/lib/dts-upgrade-common.sh" >&2
  exit 1
fi

if ! grep -qx 'dts-stack/docs/release/v2.2.3/upgrade-lite-operations-kylin-kunpeng.md' <<<"${ARCHIVE_CONTENTS}"; then
  echo "expected packaged archive to include upgrade lite operation guide" >&2
  exit 1
fi

if ! grep -qx 'images/' <<<"${ARCHIVE_CONTENTS}"; then
  echo "expected packaged archive to include top-level images/ directory" >&2
  exit 1
fi

if ! grep -qx 'extra/' <<<"${ARCHIVE_CONTENTS}"; then
  echo "expected packaged archive to include top-level extra/ directory" >&2
  exit 1
fi

for metadata_file in \
  extra/release-manifest.json \
  extra/merge-rules.yml \
  extra/checksums.txt \
  extra/rollback-manifest.json
do
  if ! grep -qx "${metadata_file}" <<<"${ARCHIVE_CONTENTS}"; then
    echo "expected packaged archive to include ${metadata_file}" >&2
    exit 1
  fi
done
