#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../../.." && pwd)"

MAVEN_IMAGE="${MAVEN_IMAGE:-maven:3.9.9-eclipse-temurin-21}"
MVN_SETTINGS="${MVN_SETTINGS:-/root/.m2/settings.xml}"
LOCAL_M2="${LOCAL_M2:-/root/.m2}"

echo "[dts-analytics] repo=${REPO_ROOT}"
echo "[dts-analytics] maven-image=${MAVEN_IMAGE}"

if ! command -v docker >/dev/null 2>&1; then
  echo "[dts-analytics] ERROR: docker not found" >&2
  exit 1
fi

docker run --rm --security-opt seccomp=unconfined -it \
  -v "${REPO_ROOT}/source:/workspace" \
  -v "${LOCAL_M2}:/root/.m2" \
  -w /workspace \
  "${MAVEN_IMAGE}" \
  mvn -B -e -DskipTests -s "${MVN_SETTINGS}" -f pom.xml -pl dts-analytics -am package

JAR_PATH="$(ls -1t "${REPO_ROOT}/source/dts-analytics/target/dts-analytics-"*.jar 2>/dev/null | grep -v '\\.original$' | head -n 1 || true)"
if [[ -z "${JAR_PATH}" ]]; then
  echo "[dts-analytics] ERROR: built jar not found under source/dts-analytics/target" >&2
  exit 1
fi

mkdir -p "${REPO_ROOT}/source/builds"
cp -f "${JAR_PATH}" "${REPO_ROOT}/source/builds/dts-analytics.jar"
echo "[dts-analytics] copied ${JAR_PATH} -> source/builds/dts-analytics.jar"

