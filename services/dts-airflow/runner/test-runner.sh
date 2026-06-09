#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/build"
MAIN_CLASSES="${BUILD_DIR}/classes"
TEST_CLASSES="${BUILD_DIR}/test-classes"
JAVA_RELEASE="${JAVA_RELEASE:-17}"
JAVAC_BIN="${JAVAC:-javac}"

rm -rf "${BUILD_DIR}"
mkdir -p "${MAIN_CLASSES}" "${TEST_CLASSES}"

supports_release_flag() {
  "${JAVAC_BIN}" -version >/dev/null 2>&1
  "${JAVAC_BIN}" --help 2>&1 | grep -q -- "--release"
}

resolve_javac_level() {
  local requested="$1"
  local javac_ver
  local major

  javac_ver="$("${JAVAC_BIN}" -version 2>&1 | awk '{print $2}' | tr -d '\r')"
  if [[ "$javac_ver" =~ ^1\.([0-9]+) ]]; then
    major="${BASH_REMATCH[1]}"
  elif [[ "$javac_ver" =~ ^([0-9]+) ]]; then
    major="${BASH_REMATCH[1]}"
  else
    major="8"
  fi

  if ((major < requested)); then
    echo "[test-runner] WARN: javac ${javac_ver} does not support release ${requested}; using ${major} for compatibility." >&2
    echo "${major}"
  else
    echo "${requested}"
  fi
}

mapfile -t main_sources < <(find "${SCRIPT_DIR}/src/main/java" -name '*.java' -print 2>/dev/null | sort)
if [[ "${#main_sources[@]}" -gt 0 ]]; then
  if supports_release_flag; then
    "${JAVAC_BIN}" --release "${JAVA_RELEASE}" -encoding UTF-8 -d "${MAIN_CLASSES}" "${main_sources[@]}"
  else
    compat_level="$(resolve_javac_level "${JAVA_RELEASE}")"
    "${JAVAC_BIN}" -source "${compat_level}" -target "${compat_level}" -encoding UTF-8 -d "${MAIN_CLASSES}" "${main_sources[@]}"
  fi
fi

mapfile -t test_sources < <(find "${SCRIPT_DIR}/src/test/java" -name '*.java' -print | sort)
if supports_release_flag; then
  "${JAVAC_BIN}" --release "${JAVA_RELEASE}" -encoding UTF-8 -cp "${MAIN_CLASSES}" -d "${TEST_CLASSES}" "${test_sources[@]}"
else
  compat_level="$(resolve_javac_level "${JAVA_RELEASE}")"
  "${JAVAC_BIN}" -source "${compat_level}" -target "${compat_level}" -encoding UTF-8 -cp "${MAIN_CLASSES}" -d "${TEST_CLASSES}" "${test_sources[@]}"
fi

java \
  -Drunner.classes="${MAIN_CLASSES}" \
  -cp "${MAIN_CLASSES}:${TEST_CLASSES}" \
  com.yuzhi.dts.addax.AddaxEnvRunnerTest
