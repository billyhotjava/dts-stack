#!/usr/bin/env bash
# Deduplicate JAR files across Addax plugins by symlinking to a shared/ directory.
# Adapted from https://github.com/wgzhao/Addax/blob/master/shrink_package.sh
set -euo pipefail

ADDAX_HOME="${1:-.}"

if [ ! -d "$ADDAX_HOME/plugin" ]; then
  echo "ERROR: $ADDAX_HOME/plugin not found" >&2
  exit 1
fi

SHARED_DIR="$ADDAX_HOME/shared"
mkdir -p "$SHARED_DIR"

declare -A JAR_MAP

# Collect all JARs from plugin subdirectories
while IFS= read -r -d '' jar; do
  basename_jar="$(basename "$jar")"
  dir_jar="$(dirname "$jar")"
  if [ -L "$jar" ]; then
    continue
  fi
  md5="$(md5sum "$jar" | awk '{print $1}')"
  key="${basename_jar}__${md5}"
  if [ -n "${JAR_MAP[$key]:-}" ]; then
    # Duplicate found - replace with symlink to shared copy
    rm -f "$jar"
    ln -s "${JAR_MAP[$key]}" "$jar"
  else
    # First occurrence - move to shared/ and symlink back
    shared_path="$SHARED_DIR/$basename_jar"
    if [ -f "$shared_path" ]; then
      # Same filename but different content - use md5 prefix
      shared_path="$SHARED_DIR/${md5:0:8}_${basename_jar}"
    fi
    cp "$jar" "$shared_path"
    rm -f "$jar"
    ln -s "$shared_path" "$jar"
    JAR_MAP[$key]="$shared_path"
  fi
done < <(find "$ADDAX_HOME/plugin" -name "*.jar" -print0)

echo "Deduplication complete. Shared JARs in $SHARED_DIR: $(ls -1 "$SHARED_DIR" | wc -l)"
