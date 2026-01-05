#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../../.." && pwd)"

MAVEN_IMAGE="${MAVEN_IMAGE:-maven:3.9.9-eclipse-temurin-21}"
MVN_SETTINGS="${MVN_SETTINGS:-/root/.m2/settings.xml}"
LOCAL_M2="${LOCAL_M2:-${HOME:-/root}/.m2}"

echo "[dts-analytics] repo=${REPO_ROOT}"
echo "[dts-analytics] maven-image=${MAVEN_IMAGE}"

if ! command -v docker >/dev/null 2>&1; then
  echo "[dts-analytics] ERROR: docker not found" >&2
  exit 1
fi

if [[ ! -f "${REPO_ROOT}/source/pom.xml" ]]; then
  echo "[dts-analytics] ERROR: ${REPO_ROOT}/source/pom.xml not found (wrong repo root?)" >&2
  exit 1
fi

if [[ ! -f "${REPO_ROOT}/source/dts-analytics/pom.xml" ]]; then
  echo "[dts-analytics] ERROR: ${REPO_ROOT}/source/dts-analytics/pom.xml not found (dts-analytics module missing?)" >&2
  exit 1
fi

for required in \
  "${REPO_ROOT}/source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/domain/AnalyticsUser.java" \
  "${REPO_ROOT}/source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/repository/AnalyticsUserRepository.java" \
  "${REPO_ROOT}/source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/filter/DtsRequestContextFilter.java" \
  "${REPO_ROOT}/source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/support/MetabaseCookies.java"
do
  if [[ ! -f "${required}" ]]; then
    echo "[dts-analytics] ERROR: required source file missing: ${required}" >&2
    echo "[dts-analytics] HINT: if you see many 'cannot find symbol' errors in Docker, the host mount is incomplete or points to a different checkout." >&2
    exit 1
  fi
done

docker run --rm --security-opt seccomp=unconfined -it \
  -v "${REPO_ROOT}/source:/workspace" \
  -v "${LOCAL_M2}:/root/.m2" \
  -w /workspace \
  "${MAVEN_IMAGE}" \
  bash -lc "set -euo pipefail; \
    echo '[dts-analytics] container preflight...'; \
    test -f /workspace/pom.xml; \
    test -f /workspace/dts-analytics/pom.xml; \
    test -f /workspace/dts-analytics/src/main/java/com/yuzhi/dts/analytics/domain/AnalyticsUser.java; \
    test -f /workspace/dts-analytics/src/main/java/com/yuzhi/dts/analytics/repository/AnalyticsUserRepository.java; \
    test -f /workspace/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/filter/DtsRequestContextFilter.java; \
    test -f /workspace/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/support/MetabaseCookies.java; \
    echo '[dts-analytics] container source files:' \"\$(find /workspace/dts-analytics/src/main/java -name '*.java' | wc -l)\"; \
    mvn -B -e -DskipTests -s \"${MVN_SETTINGS}\" -f /workspace/pom.xml -pl dts-analytics -am package"

JAR_PATH="$(ls -1t "${REPO_ROOT}/source/dts-analytics/target/dts-analytics-"*.jar 2>/dev/null | grep -v '\\.original$' | head -n 1 || true)"
if [[ -z "${JAR_PATH}" ]]; then
  echo "[dts-analytics] ERROR: built jar not found under source/dts-analytics/target" >&2
  exit 1
fi

mkdir -p "${REPO_ROOT}/source/builds"
cp -f "${JAR_PATH}" "${REPO_ROOT}/source/builds/dts-analytics.jar"
echo "[dts-analytics] copied ${JAR_PATH} -> source/builds/dts-analytics.jar"
