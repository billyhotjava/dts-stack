#!/usr/bin/env bash
set -euo pipefail

# Prepare offline artifacts (run on an online machine):
# - Pre-populate Maven local repository for Java services
# - Optionally pre-populate node_modules for webapps

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_BASE=".env"

if [[ -f "$ENV_BASE" ]]; then
  set -a; source "$ENV_BASE"; set +a
fi

IMAGE_MAVEN_DEFAULT="maven:3.9.9-eclipse-temurin-21"
MVN_IMAGE="${IMAGE_MAVEN:-$IMAGE_MAVEN_DEFAULT}"

SRC_ROOT="${SRC_ROOT:-"$SCRIPT_DIR/../dts-source"}"
OFFLINE_DIR="$SCRIPT_DIR/offline"
MAVEN_REPO_DIR="$OFFLINE_DIR/maven-repo"

mkdir -p "$MAVEN_REPO_DIR"
mkdir -p "$OFFLINE_DIR/node-modules/dts-admin-webapp" "$OFFLINE_DIR/node-modules/dts-platform-webapp"
mkdir -p "$OFFLINE_DIR/pnpm-store"

echo "[offline-prepare] Using source at: $SRC_ROOT"
echo "[offline-prepare] Maven image: $MVN_IMAGE"

need_src=(
  "$SRC_ROOT/dts-admin/pom.xml"
  "$SRC_ROOT/dts-platform/pom.xml"
  "$SRC_ROOT/dts-public-api/pom.xml"
)
for f in "${need_src[@]}"; do
  [[ -f "$f" ]] || { echo "[offline-prepare] ERROR: missing $f" >&2; exit 1; }
done

echo "[offline-prepare] Resolving Java dependencies (dependency:go-offline) ..."
docker run --rm \
  -v "$SRC_ROOT:/src" \
  -v "$MAVEN_REPO_DIR:/root/.m2/repository" \
  -w /src/dts-admin "$MVN_IMAGE" \
  mvn -B -U -DskipTests dependency:go-offline

docker run --rm \
  -v "$SRC_ROOT:/src" \
  -v "$MAVEN_REPO_DIR:/root/.m2/repository" \
  -w /src/dts-platform "$MVN_IMAGE" \
  mvn -B -U -DskipTests dependency:go-offline

docker run --rm \
  -v "$SRC_ROOT:/src" \
  -v "$MAVEN_REPO_DIR:/root/.m2/repository" \
  -w /src/dts-public-api "$MVN_IMAGE" \
  mvn -B -U -DskipTests dependency:go-offline

echo "[offline-prepare] Maven repo populated at: $MAVEN_REPO_DIR"

echo "[offline-prepare] Preparing PNPM offline store and Node modules ..."
for web in dts-admin-webapp dts-platform-webapp; do
  if [[ -f "$SRC_ROOT/$web/package.json" ]]; then
    echo "  -> $web (pnpm fetch + install)"
    docker run --rm \
      -v "$SRC_ROOT/$web:/app" \
      -v "$OFFLINE_DIR/pnpm-store:/pnpm-store" \
      -v "$OFFLINE_DIR/node-modules/$web:/out" \
      -w /app node:20-alpine \
      sh -lc 'set -e; corepack enable; \
        if [ ! -f pnpm-lock.yaml ] && [ -f package-lock.json ]; then pnpm import; fi; \
        export PNPM_STORE_DIR=/pnpm-store; export npm_config_ignore_scripts=true; \
        pnpm fetch --no-optional || true; \
        pnpm install --prefer-offline --no-optional --ignore-scripts; \
        rm -rf /out/node_modules && cp -a node_modules /out/ || true; \
        true'
  else
    echo "  -> $web skipped (no package.json)"
  fi
done

echo "[offline-prepare] Done. Copy the 'offline/' directory to the target offline environment."
echo "  - Maven: ./offline/maven-repo"
echo "  - Node:  ./offline/node-modules/<webapp>/node_modules"
