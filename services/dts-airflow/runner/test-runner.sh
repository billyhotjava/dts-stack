#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/build"
MAIN_CLASSES="${BUILD_DIR}/classes"
TEST_CLASSES="${BUILD_DIR}/test-classes"
JAVA_RELEASE="${JAVA_RELEASE:-17}"

rm -rf "${BUILD_DIR}"
mkdir -p "${MAIN_CLASSES}" "${TEST_CLASSES}"

mapfile -t main_sources < <(find "${SCRIPT_DIR}/src/main/java" -name '*.java' -print 2>/dev/null | sort)
if [[ "${#main_sources[@]}" -gt 0 ]]; then
  javac --release "${JAVA_RELEASE}" -encoding UTF-8 -d "${MAIN_CLASSES}" "${main_sources[@]}"
fi

mapfile -t test_sources < <(find "${SCRIPT_DIR}/src/test/java" -name '*.java' -print | sort)
javac --release "${JAVA_RELEASE}" -encoding UTF-8 -cp "${MAIN_CLASSES}" -d "${TEST_CLASSES}" "${test_sources[@]}"

java \
  -Drunner.classes="${MAIN_CLASSES}" \
  -cp "${MAIN_CLASSES}:${TEST_CLASSES}" \
  com.yuzhi.dts.addax.AddaxEnvRunnerTest
