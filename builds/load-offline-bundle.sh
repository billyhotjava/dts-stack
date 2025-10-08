#!/usr/bin/env bash
set -euo pipefail

# DTS Stack — Offline bundle loader (on the air-gapped host)
#
# Usage:
#   1) Extract the bundle tgz to some directory, e.g. /opt/dts-offline/
#   2) Run this script from inside the extracted bundle dir:
#        builds/load-offline-bundle.sh
#   3) Then cd repo/ && adjust .env and start compose as usual

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_DIR="${SCRIPT_DIR%/builds}"

IMG_DIR="${BASE_DIR}/images"
REPO_DIR="${BASE_DIR}/repo"

if [[ ! -d "${IMG_DIR}" ]]; then
  echo "images/ directory not found. Are you running from inside the extracted bundle?" >&2
  exit 1
fi

echo "Loading Docker images from: ${IMG_DIR}"
shopt -s nullglob
for tarfile in "${IMG_DIR}"/*.tar; do
  echo "- docker load < ${tarfile}"
  docker load -i "${tarfile}"
done
shopt -u nullglob

echo "All images loaded. Next steps:"
echo "1) cd repo/"
echo "2) Edit .env for the target environment (BASE_DOMAIN, passwords)"
echo "3) ./init.sh single '<Admin@Pass>' '<your.domain>'"
echo "4) docker compose -f docker-compose.yml -f docker-compose-app.yml up -d"

