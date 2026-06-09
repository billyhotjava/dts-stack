#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/build"
CLASSES_DIR="${BUILD_DIR}/classes"
OUTPUT_JAR="${SCRIPT_DIR}/../dags/addax-env-runner.jar"
MAIN_CLASS="com.yuzhi.dts.addax.AddaxEnvRunner"
JAVA_RELEASE="${JAVA_RELEASE:-17}"
JAVAC_BIN="${JAVAC:-javac}"

rm -rf "${CLASSES_DIR}"
mkdir -p "${CLASSES_DIR}" "$(dirname -- "${OUTPUT_JAR}")"

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
    echo "[build-runner] WARN: javac ${javac_ver} does not support release ${requested}; using ${major} for compatibility." >&2
    echo "${major}"
  else
    echo "${requested}"
  fi
}

mapfile -t main_sources < <(find "${SCRIPT_DIR}/src/main/java" -name '*.java' -print | sort)
if supports_release_flag; then
  "${JAVAC_BIN}" --release "${JAVA_RELEASE}" -encoding UTF-8 -d "${CLASSES_DIR}" "${main_sources[@]}"
else
  compat_level="$(resolve_javac_level "${JAVA_RELEASE}")"
  "${JAVAC_BIN}" -source "${compat_level}" -target "${compat_level}" -encoding UTF-8 -d "${CLASSES_DIR}" "${main_sources[@]}"
fi
jar --create --file "${OUTPUT_JAR}" --main-class "${MAIN_CLASS}" -C "${CLASSES_DIR}" .

echo "Built ${OUTPUT_JAR}"
