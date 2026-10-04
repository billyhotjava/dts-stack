#!/usr/bin/env bash
# Pre-pull all third-party images referenced in imgversion.conf
# Usage: bash builds/pull-images.sh
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
CONF="${SCRIPT_DIR}/../imgversion.conf"

if [[ ! -f "$CONF" ]]; then
  echo "[pull-images] ERROR: imgversion.conf not found at ${CONF}" >&2
  exit 1
fi

# Collect third-party images (skip commented lines, skip local-build images without registry prefix)
images=()
while IFS='=' read -r key value; do
  # Trim whitespace
  key="${key// /}"
  value="$(echo "$value" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
  [[ -z "$key" || -z "$value" ]] && continue

  # Skip local-build images (dts-* without registry prefix are built locally)
  case "$value" in
    dts-*) continue ;;
  esac

  images+=("$value")
done < <(grep -E '^[[:space:]]*IMAGE_[A-Z_]+=.+' "$CONF" | grep -v '^[[:space:]]*#')

if [[ ${#images[@]} -eq 0 ]]; then
  echo "[pull-images] No third-party images found in imgversion.conf"
  exit 0
fi

echo "[pull-images] Will pull ${#images[@]} images:"
for img in "${images[@]}"; do
  echo "  - $img"
done
echo ""

failed=()
for img in "${images[@]}"; do
  echo "[pull-images] Pulling $img ..."
  if docker pull "$img"; then
    echo "[pull-images] OK: $img"
  else
    echo "[pull-images] FAILED: $img" >&2
    failed+=("$img")
  fi
  echo ""
done

echo "========================================"
echo "[pull-images] Done. Success: $(( ${#images[@]} - ${#failed[@]} ))/${#images[@]}"
if [[ ${#failed[@]} -gt 0 ]]; then
  echo "[pull-images] Failed:"
  for img in "${failed[@]}"; do
    echo "  - $img"
  done
  exit 1
fi
