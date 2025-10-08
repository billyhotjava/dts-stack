#!/usr/bin/env bash
set -euo pipefail

# DTS Stack — Offline bundle maker
#
# Usage (on an online build host with Docker):
#   builds/make-offline-bundle.sh [--images-only] [--name <bundle-name>]
#
# Output:
#   builds/offline/<bundle-name>/
#     - repo/     (source tree snapshot with excludes)
#     - images/   (*.tar saved images + manifest)
#     - checksums.txt

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TS="$(date +%Y%m%d-%H%M%S)"
BUNDLE_NAME="dts-stack-offline-${TS}"
IMAGES_ONLY=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --name)
      BUNDLE_NAME="$2"; shift 2 ;;
    --images-only)
      IMAGES_ONLY=true; shift ;;
    *) echo "Unknown arg: $1"; exit 2 ;;
  esac
done

OUT_DIR="${ROOT_DIR}/builds/offline/${BUNDLE_NAME}"
IMG_DIR="${OUT_DIR}/images"
REPO_DIR="${OUT_DIR}/repo"
mkdir -p "${IMG_DIR}"

log() { printf "[%s] %s\n" "$(date +%H:%M:%S)" "$*"; }

# 1) Collect images from .env, imgversion.conf, and compose files
collect_images() {
  local tmp imgs f
  tmp="$(mktemp)"

  # Export .env to allow ${VAR} resolution if we need it later
  set +u
  if [[ -f "${ROOT_DIR}/.env" ]]; then
    set -a; . "${ROOT_DIR}/.env"; set +a
  fi
  set -u

  # From .env
  if [[ -f "${ROOT_DIR}/.env" ]]; then
    sed -n 's/^\s*IMAGE_[A-Z0-9_]*=\s*\(.*\)\s*$/\1/p' "${ROOT_DIR}/.env" >>"${tmp}" || true
  fi
  # From imgversion.conf (if present)
  if [[ -f "${ROOT_DIR}/imgversion.conf" ]]; then
    sed -n 's/^\s*IMAGE_[A-Z0-9_]*=\s*\(.*\)\s*$/\1/p' "${ROOT_DIR}/imgversion.conf" >>"${tmp}" || true
  fi
  # From compose files (raw lines)
  for f in docker-compose.yml docker-compose-app.yml docker-compose.dev.yml; do
    if [[ -f "${ROOT_DIR}/${f}" ]]; then
      sed -n 's/^\s*image:\s*\(.*\)$/\1/p' "${ROOT_DIR}/${f}" >>"${tmp}" || true
    fi
  done

  # Normalize, expand environment variables, drop unresolved, uniq
  imgs=$(sed 's/"//g;s/'\''//g' "${tmp}" \
    | sed 's/[[:space:]]//g' \
    | grep -vE '^$' \
    | while read -r line; do printf '%s\n' "$(echo "${line}" | envsubst)"; done \
    | grep -v '\${' \
    | sort -u)

  rm -f "${tmp}"
  echo "${imgs}"
}

IMAGES=( $(collect_images) )
if [[ ${#IMAGES[@]} -eq 0 ]]; then
  log "No images discovered. Check .env/imgversion.conf/compose files."
  exit 1
fi

log "Discovered ${#IMAGES[@]} images to bundle."

# 2) Pull, inspect and save images
MANIFEST="${IMG_DIR}/image-manifest.txt"
printf "# image manifest\n# generated at %s\n" "${TS}" > "${MANIFEST}"

sanitize() { echo "$1" | tr '/:@' '___'; }

for image in "${IMAGES[@]}"; do
  file="$(sanitize "${image}").tar"
  out="${IMG_DIR}/${file}"

  # Skip pulling/saving if tar already exists; this allows resume on flaky networks
  if [[ -f "${out}" ]]; then
    log "Skip existing: ${image} (images/${file})"
    # Try best-effort inspect (may fail if image not loaded locally)
    if id="$(docker inspect -f '{{.Id}}' "${image}" 2>/dev/null || true)"; then :; fi
    if digest="$(docker inspect -f '{{join .RepoDigests ","}}' "${image}" 2>/dev/null || true)"; then :; fi
    size="$(stat -c %s "${out}" 2>/dev/null || stat -f %z "${out}")"
    printf "image=%s file=%s id=%s digest=%s size=%s\n" "${image}" "${file}" "${id:-}" "${digest:-}" "${size}" >>"${MANIFEST}"
    continue
  fi

  pull_with_retry() {
    local img="$1"; local tries="${2:-5}"; local delay="${3:-10}"
    local n=1
    while (( n <= tries )); do
      if docker pull "$img" >/dev/null; then return 0; fi
      log "Pull failed ($n/${tries}): $img; retry in ${delay}s"
      sleep "$delay"
      n=$((n+1))
    done
    return 1
  }

  log "Pulling: ${image}"
  if ! pull_with_retry "${image}" 5 15; then
    log "ERROR: Unable to pull ${image}. Recording and continue."
    echo "${image}" >> "${OUT_DIR}/missing-images.txt"
    continue
  fi

  id="$(docker inspect -f '{{.Id}}' "${image}")"
  digest="$(docker inspect -f '{{join .RepoDigests ","}}' "${image}" || true)"
  log "Saving: ${image} -> images/${file}"
  docker save -o "${out}" "${image}"
  size="$(stat -c %s "${out}" 2>/dev/null || stat -f %z "${out}")"
  printf "image=%s file=%s id=%s digest=%s size=%s\n" "${image}" "${file}" "${id}" "${digest}" "${size}" >>"${MANIFEST}"
done

# 3) Checksums
(cd "${OUT_DIR}" && find images -type f -name '*.tar' -print0 | xargs -0 sha256sum > checksums.txt)

if [[ "${IMAGES_ONLY}" == true ]]; then
  log "Images saved. Bundle dir: ${OUT_DIR}"
  exit 0
fi

# 4) Snapshot repo (exclude bulky/dev caches)
log "Snapshotting repository..."
mkdir -p "${REPO_DIR}"

# Create a tarball then extract into repo/ to avoid copying VCS metadata
TMP_TAR="${OUT_DIR}/repo.tar"
tar --exclude-vcs \
    --exclude='*/target' \
    --exclude='*/node_modules' \
    --exclude='.pnpm-store' \
    --exclude='services/*/data' \
    --exclude='builds/offline' \
    --exclude='.git' \
    -C "${ROOT_DIR}" -czf "${TMP_TAR}" .
(cd "${REPO_DIR}" && tar -xzf "${TMP_TAR}")
rm -f "${TMP_TAR}"

log "Done. Offline bundle ready: ${OUT_DIR}"
log "To transfer: compress with 'tar -C builds/offline -czf ${BUNDLE_NAME}.tgz ${BUNDLE_NAME}'"
