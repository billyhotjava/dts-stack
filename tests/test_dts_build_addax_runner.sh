#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

TEST_REPO="${TMP_DIR}/repo"
FAKE_BIN="${TMP_DIR}/bin"
DOCKER_CAPTURE="${TMP_DIR}/docker-runner-content"

mkdir -p \
  "${TEST_REPO}/builds/dts-addax" \
  "${TEST_REPO}/services/dts-airflow/dags" \
  "${TEST_REPO}/services/dts-airflow/runner" \
  "${FAKE_BIN}"

cp "${REPO_ROOT}/builds/dts-build.sh" "${TEST_REPO}/builds/dts-build.sh"
chmod +x "${TEST_REPO}/builds/dts-build.sh"

cat > "${TEST_REPO}/builds/dts-addax/Dockerfile" <<'EOF_DOCKERFILE'
FROM scratch
COPY addax-env-runner.jar /opt/addax/addax-env-runner.jar
EOF_DOCKERFILE

printf 'stale-runner\n' > "${TEST_REPO}/services/dts-airflow/dags/addax-env-runner.jar"

cat > "${TEST_REPO}/services/dts-airflow/runner/build-runner.sh" <<'EOF_RUNNER'
#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
printf 'fresh-runner\n' > "${SCRIPT_DIR}/../dags/addax-env-runner.jar"
EOF_RUNNER
chmod +x "${TEST_REPO}/services/dts-airflow/runner/build-runner.sh"

cat > "${FAKE_BIN}/docker" <<'EOF_DOCKER'
#!/usr/bin/env bash
set -euo pipefail

case "${1:-}" in
  build)
    if [[ "${2:-}" == "--help" ]]; then
      echo "--progress"
      exit 0
    fi
    context_dir="${*: -1}"
    cp "${context_dir}/addax-env-runner.jar" "__DOCKER_CAPTURE__"
    ;;
  image|builder|save)
    ;;
esac
EOF_DOCKER
sed -i "s|__DOCKER_CAPTURE__|${DOCKER_CAPTURE}|g" "${FAKE_BIN}/docker"
chmod +x "${FAKE_BIN}/docker"

PATH="${FAKE_BIN}:${PATH}" \
  PREBUILD_JARS=0 \
  "${TEST_REPO}/builds/dts-build.sh" --image dts-addax --no-save >/dev/null

if [[ "$(<"${DOCKER_CAPTURE}")" != "fresh-runner" ]]; then
  echo "expected dts-addax build to compile and inject the runner from current source" >&2
  exit 1
fi
