#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/build"
CLASSES_DIR="${BUILD_DIR}/classes"
OUTPUT_JAR="${SCRIPT_DIR}/../dags/addax-env-runner.jar"
MAIN_CLASS="com.yuzhi.dts.addax.AddaxEnvRunner"
JAVA_RELEASE="${JAVA_RELEASE:-17}"

rm -rf "${CLASSES_DIR}"
mkdir -p "${CLASSES_DIR}" "$(dirname -- "${OUTPUT_JAR}")"

mapfile -t main_sources < <(find "${SCRIPT_DIR}/src/main/java" -name '*.java' -print | sort)
javac --release "${JAVA_RELEASE}" -encoding UTF-8 -d "${CLASSES_DIR}" "${main_sources[@]}"
jar --create --file "${OUTPUT_JAR}" --main-class "${MAIN_CLASS}" -C "${CLASSES_DIR}" .

echo "Built ${OUTPUT_JAR}"
