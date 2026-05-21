#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_VERSION="2026-05-21-docker18-compose129-r2"

IMAGE_TAG="${OPMANAGER_IMAGE:-dts-opmanager:2.2.3}"
OUTPUT_PATH="${OPMANAGER_PACKAGE_TAR:-}"
NODE_IMAGE="${NODE_IMAGE:-node:20.17.0-alpine3.20}"
PNPM_VERSION="${PNPM_VERSION:-10.28.0}"
MAVEN_IMAGE="${MAVEN_IMAGE:-maven:3.9.9-eclipse-temurin-21}"
OPMANAGER_USE_HOST_MAVEN="${OPMANAGER_USE_HOST_MAVEN:-}"
MAVEN_CONTAINER_JAVA_HOME="${MAVEN_CONTAINER_JAVA_HOME:-/opt/java/openjdk}"
MAVEN_MEMORY_LIMIT="${MAVEN_MEMORY_LIMIT:-4g}"
MAVEN_MIRROR_URL="${MAVEN_MIRROR_URL:-https://maven.aliyun.com/repository/public}"
MAVEN_SETTINGS_FILE="${MAVEN_SETTINGS_FILE:-${HOME:-/root}/.m2/settings.xml}"
MAVEN_SETTINGS_DIR="${MAVEN_SETTINGS_DIR:-$(dirname "${MAVEN_SETTINGS_FILE}")}"
MAVEN_REPO_LOCAL="${MAVEN_REPO_LOCAL:-${MAVEN_SETTINGS_DIR}/repository}"
HOST_ARCH="$(uname -m)"
DOCKER_API_VERSION="$(docker version --format '{{.Server.APIVersion}}' 2>/dev/null || echo "0.0")"
DOCKER_API_MAJOR="${DOCKER_API_VERSION%%.*}"
DOCKER_API_MINOR="${DOCKER_API_VERSION#*.}"
DOCKER_PLATFORM_SUPPORTED=""
if [[ "${DOCKER_API_MAJOR:-0}" -gt 1 ]] || { [[ "${DOCKER_API_MAJOR}" == "1" ]] && [[ "${DOCKER_API_MINOR:-0}" -ge 40 ]]; }; then
  DOCKER_PLATFORM_SUPPORTED="1"
fi

usage() {
  cat <<USAGE
Usage:
  ${0##*/} [image-tag]
  ${0##*/} --tag <image-tag> [--output <package-path-or-dir>]

Builds the dts-opmanager runtime image without running Maven inside Dockerfile
RUN steps. This matches the Kunpeng/Kylin workaround used by builds/dts-build.sh.

Options:
  --tag <image-tag>             Image tag to build. Default: ${IMAGE_TAG}
  --output <package-path-or-dir>
                                Runtime package tar.gz output. If a directory is given,
                                the default package name is created under that directory.
  --pull                        Pull base images during the final runtime docker build.
  --host-maven                  Use host mvn instead of a Maven container.
  -h, --help                    Show this help.

Environment:
  OPMANAGER_IMAGE       Default image tag when [image-tag] is omitted.
  OPMANAGER_PACKAGE_TAR Default runtime package output path.
  NODE_IMAGE            Node image for frontend build. Default: ${NODE_IMAGE}
  MAVEN_IMAGE           Maven image for backend jar build. Default: ${MAVEN_IMAGE}
  MAVEN_UNRESTRICTED    Set to 1 to force seccomp/nproc relaxation.
  OPMANAGER_USE_HOST_MAVEN
                       Set to 1 to use host mvn instead of a Maven container.
  MAVEN_MIRROR_URL      Maven mirror used when settings.xml is generated.
  NPM_REGISTRY          Optional npm registry mirror.

Example:
  ${0##*/} --tag dts-opmanager:2.2.3 --output dts-opmanager-runtime-2.2.3-linux-arm64.tar.gz
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag)
      IMAGE_TAG="${2:-}"
      if [[ -z "${IMAGE_TAG}" ]]; then
        echo "[opmanager-build] ERROR: --tag requires a value" >&2
        exit 1
      fi
      shift 2
      ;;
    --output|-o)
      OUTPUT_PATH="${2:-}"
      if [[ -z "${OUTPUT_PATH}" ]]; then
        echo "[opmanager-build] ERROR: --output requires a value" >&2
        exit 1
      fi
      shift 2
      ;;
    --pull)
      DOCKER_BUILD_PULL="1"
      shift
      ;;
    --host-maven)
      OPMANAGER_USE_HOST_MAVEN="1"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    -*)
      echo "[opmanager-build] ERROR: Unknown argument: $1" >&2
      usage
      exit 1
      ;;
    *)
      IMAGE_TAG="$1"
      shift
      if [[ $# -gt 0 ]]; then
        echo "[opmanager-build] ERROR: unexpected extra arguments: $*" >&2
        usage
        exit 1
      fi
      ;;
  esac
done

if [[ -z "${IMAGE_TAG}" ]]; then
  echo "[opmanager-build] ERROR: image tag is empty" >&2
  exit 1
fi

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[opmanager-build] ERROR: '$1' not found in PATH" >&2
    exit 1
  fi
}

ensure_maven_settings() {
  if [[ -f "${MAVEN_SETTINGS_FILE}" || -z "${MAVEN_MIRROR_URL}" ]]; then
    return 0
  fi
  mkdir -p "${MAVEN_SETTINGS_DIR}"
  cat > "${MAVEN_SETTINGS_FILE}" <<SETTINGS_EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>*</mirrorOf>
      <url>${MAVEN_MIRROR_URL}</url>
    </mirror>
  </mirrors>
</settings>
SETTINGS_EOF
  echo "[opmanager-build] Generated ${MAVEN_SETTINGS_FILE} (mirror: ${MAVEN_MIRROR_URL})"
}

build_webapp() {
  echo "[opmanager-build] Building React webapp via ${NODE_IMAGE}"
  local npm_env=()
  if [[ -n "${NPM_REGISTRY:-}" ]]; then
    npm_env+=(-e "NPM_REGISTRY=${NPM_REGISTRY}")
  fi
  if [[ -n "${NPM_HTTP_PROXY:-${HTTP_PROXY:-}}" ]]; then
    npm_env+=(-e "NPM_HTTP_PROXY=${NPM_HTTP_PROXY:-${HTTP_PROXY:-}}")
  fi
  if [[ -n "${NPM_HTTPS_PROXY:-${HTTPS_PROXY:-}}" ]]; then
    npm_env+=(-e "NPM_HTTPS_PROXY=${NPM_HTTPS_PROXY:-${HTTPS_PROXY:-}}")
  fi

  docker run --rm \
    "${npm_env[@]}" \
    -e "PNPM_VERSION=${PNPM_VERSION}" \
    -v "${SCRIPT_DIR}:/workspace" \
    -w /workspace/src/main/webapp \
    "${NODE_IMAGE}" \
    sh -lc 'set -eux; \
      trap "chmod -R a+rwX /workspace/src/main/webapp /workspace/src/main/resources/static 2>/dev/null || true" EXIT; \
      if [ -n "${NPM_REGISTRY:-}" ]; then export npm_config_registry="${NPM_REGISTRY}"; fi; \
      if [ -n "${NPM_HTTP_PROXY:-}${NPM_HTTPS_PROXY:-}" ]; then \
        export http_proxy="${NPM_HTTP_PROXY:-${NPM_HTTPS_PROXY:-}}"; \
        export https_proxy="${NPM_HTTPS_PROXY:-${NPM_HTTP_PROXY:-}}"; \
        export npm_config_proxy="${http_proxy}"; \
        export npm_config_https_proxy="${https_proxy}"; \
      fi; \
      npm install -g "pnpm@${PNPM_VERSION}"; \
      pnpm install --frozen-lockfile --ignore-scripts; \
      pnpm build; \
      rm -rf node_modules .vite-cache'
}

build_backend_jar_with_host_maven() {
  ensure_maven_settings
  echo "[opmanager-build] Building backend jar via host Maven"
  local args=(-B -e -DskipTests -Dskip.webapp=true -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" -f "${SCRIPT_DIR}/pom.xml")
  if [[ -f "${MAVEN_SETTINGS_FILE}" ]]; then
    args+=(-s "${MAVEN_SETTINGS_FILE}")
  fi
  mvn "${args[@]}" package
}

build_backend_jar_with_container_maven() {
  ensure_maven_settings
  echo "[opmanager-build] Building backend jar via ${MAVEN_IMAGE}"

  local security_opts=()
  if [[ -n "${MAVEN_SECURITY_OPT:-}" ]]; then
    security_opts+=(--security-opt "${MAVEN_SECURITY_OPT}")
  fi

  local unrestricted="${MAVEN_UNRESTRICTED:-}"
  if [[ -z "${unrestricted}" && ("${HOST_ARCH}" == "aarch64" || "${HOST_ARCH}" == "arm64") ]]; then
    unrestricted="1"
  fi
  if [[ "${unrestricted}" == "1" ]]; then
    security_opts+=(--security-opt "seccomp=unconfined" --ulimit "nproc=65535:65535")
  fi

  local maven_args=(-B -e -DskipTests -Dskip.webapp=true -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" -f pom.xml)
  if [[ -f "${MAVEN_SETTINGS_FILE}" ]]; then
    maven_args+=(-s "${MAVEN_SETTINGS_FILE}")
  fi

  local container_path="${MAVEN_CONTAINER_JAVA_HOME}/bin:/usr/share/maven/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
  local use_bash_runner=""
  if [[ -z "${DOCKER_PLATFORM_SUPPORTED}" && ("${HOST_ARCH}" == "aarch64" || "${HOST_ARCH}" == "arm64") ]]; then
    use_bash_runner="1"
  fi

  if [[ -n "${use_bash_runner}" ]]; then
    local quoted_args=()
    local arg
    for arg in "${maven_args[@]}" package; do
      quoted_args+=("$(printf '%q' "${arg}")")
    done
    docker run --rm --memory="${MAVEN_MEMORY_LIMIT}" \
      ${security_opts[@]+"${security_opts[@]}"} \
      --entrypoint /bin/bash \
      -v "${SCRIPT_DIR}:/workspace" \
      -v "${MAVEN_SETTINGS_DIR}:${MAVEN_SETTINGS_DIR}" \
      -w /workspace \
      "${MAVEN_IMAGE}" \
      -lc "set -e; trap 'chmod -R a+rwX /workspace/target /workspace/src/main/resources/static 2>/dev/null || true' EXIT; export JAVA_HOME=${MAVEN_CONTAINER_JAVA_HOME}; export PATH=${container_path}; /usr/bin/mvn ${quoted_args[*]}"
  else
    docker run --rm --memory="${MAVEN_MEMORY_LIMIT}" \
      ${security_opts[@]+"${security_opts[@]}"} \
      -e "JAVA_HOME=${MAVEN_CONTAINER_JAVA_HOME}" \
      -e "PATH=${container_path}" \
      -v "${SCRIPT_DIR}:/workspace" \
      -v "${MAVEN_SETTINGS_DIR}:${MAVEN_SETTINGS_DIR}" \
      -w /workspace \
      "${MAVEN_IMAGE}" \
      mvn "${maven_args[@]}" package
    docker run --rm \
      --entrypoint /bin/sh \
      -v "${SCRIPT_DIR}:/workspace" \
      "${MAVEN_IMAGE}" \
      -lc 'chmod -R a+rwX /workspace/target /workspace/src/main/resources/static 2>/dev/null || true' >/dev/null 2>&1 || true
  fi
}

build_backend_jar() {
  rm -f "${SCRIPT_DIR}"/target/dts-opmanager-*.jar "${SCRIPT_DIR}"/target/dts-opmanager-*.jar.original 2>/dev/null || true
  if [[ "${OPMANAGER_USE_HOST_MAVEN:-}" == "1" ]]; then
    if command -v mvn >/dev/null 2>&1; then
      build_backend_jar_with_host_maven
    else
      echo "[opmanager-build] WARN: OPMANAGER_USE_HOST_MAVEN=1 but 'mvn' is not in PATH; falling back to Maven container." >&2
      build_backend_jar_with_container_maven
    fi
  else
    build_backend_jar_with_container_maven
  fi

  local jar_path
  jar_path="$(ls -1t "${SCRIPT_DIR}"/target/dts-opmanager-*.jar 2>/dev/null | head -n 1 || true)"
  if [[ -z "${jar_path}" ]]; then
    echo "[opmanager-build] ERROR: built jar not found under ${SCRIPT_DIR}/target" >&2
    exit 1
  fi
  echo "[opmanager-build] Built ${jar_path}"
}

build_runtime_image() {
  echo "[opmanager-build] Building runtime image ${IMAGE_TAG}"
  local pull_args=()
  if [[ "${DOCKER_BUILD_PULL:-}" == "1" || "${DOCKER_BUILD_PULL:-}" == "true" ]]; then
    pull_args+=(--pull)
  fi
  docker build "${pull_args[@]}" -t "${IMAGE_TAG}" -f "${SCRIPT_DIR}/Dockerfile" "${SCRIPT_DIR}"
  docker image inspect "${IMAGE_TAG}" --format '[opmanager-build] Image architecture: {{.Architecture}}' || true
}

image_arch() {
  local arch
  arch="$(docker image inspect "${IMAGE_TAG}" --format '{{.Architecture}}' 2>/dev/null || true)"
  case "${arch}" in
    aarch64)
      echo "arm64"
      ;;
    x86_64)
      echo "amd64"
      ;;
    "")
      uname -m | sed 's/aarch64/arm64/;s/x86_64/amd64/'
      ;;
    *)
      echo "${arch}"
      ;;
  esac
}

image_name() {
  local image_name="${IMAGE_TAG%%:*}"
  image_name="${image_name##*/}"
  echo "${image_name}"
}

image_version() {
  local image_version="latest"
  if [[ "${IMAGE_TAG}" == *:* ]]; then
    image_version="${IMAGE_TAG##*:}"
  fi
  echo "${image_version}"
}

runtime_package_name() {
  echo "$(image_name)-runtime-$(image_version)-linux-$(image_arch).tar.gz"
}

image_tar_name() {
  echo "$(image_name)-$(image_version)-linux-$(image_arch).tar"
}

package_output_path() {
  local output="${OUTPUT_PATH:-${SCRIPT_DIR}/$(runtime_package_name)}"
  if [[ "${output}" != /* ]]; then
    output="$(pwd)/${output}"
  fi
  case "${output}" in
    *.tar.gz|*.tgz)
      mkdir -p "$(dirname "${output}")"
      echo "${output}"
      ;;
    *)
      mkdir -p "${output}"
      echo "${output}/$(runtime_package_name)"
      ;;
  esac
}

write_package_env_example() {
  local target="$1"
  sed \
    -e "s|^OPMANAGER_IMAGE=.*|OPMANAGER_IMAGE=${IMAGE_TAG}|" \
    "${SCRIPT_DIR}/deploy/env.example" > "${target}"
}

write_start_script() {
  local target="$1"
  cat > "${target}" <<'START_SCRIPT'
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
OPMANAGER_HOME="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

cd "${SCRIPT_DIR}"

if [[ ! -f .env ]]; then
  cp env.example .env
fi

escaped_home="$(printf '%s' "${OPMANAGER_HOME}" | sed 's/[\/&]/\\&/g')"
if grep -q '^OPMANAGER_HOME=' .env; then
  sed -i "s|^OPMANAGER_HOME=.*|OPMANAGER_HOME=${escaped_home}|" .env
else
  printf '\nOPMANAGER_HOME=%s\n' "${OPMANAGER_HOME}" >> .env
fi

image_tar="$(find "${SCRIPT_DIR}" -maxdepth 1 -type f -name 'dts-opmanager-*.tar' | sort | tail -n 1)"
if [[ -z "${image_tar}" ]]; then
  echo "[opmanager-start] ERROR: dts-opmanager image tar not found under ${SCRIPT_DIR}" >&2
  exit 1
fi

image_count="$(find "${SCRIPT_DIR}" -maxdepth 1 -type f -name 'dts-opmanager-*.tar' | wc -l | tr -d ' ')"
if [[ "${image_count}" != "1" ]]; then
  echo "[opmanager-start] ERROR: expected exactly one dts-opmanager image tar under ${SCRIPT_DIR}, found ${image_count}." >&2
  echo "[opmanager-start] Remove old image tar files and keep only the runtime package tar for this deployment." >&2
  find "${SCRIPT_DIR}" -maxdepth 1 -type f -name 'dts-opmanager-*.tar' -print >&2
  exit 1
fi

image_tag="$(grep -E '^OPMANAGER_IMAGE=' .env | tail -n 1 | cut -d= -f2-)"
image_tag="${image_tag:-dts-opmanager:2.2.3}"

echo "[opmanager-start] Docker:"
docker version --format 'client={{.Client.Version}} server={{.Server.Version}} api={{.Server.APIVersion}} arch={{.Server.Arch}}' 2>/dev/null || docker version
echo "[opmanager-start] Compose:"
if command -v docker-compose >/dev/null 2>&1; then
  docker-compose version
elif docker compose version >/dev/null 2>&1; then
  docker compose version
else
  echo "[opmanager-start] ERROR: docker-compose or docker compose is required." >&2
  exit 1
fi

docker load -i "${image_tar}"

image_arch="$(docker image inspect "${image_tag}" --format '{{.Architecture}}' 2>/dev/null || true)"
image_entrypoint="$(docker image inspect "${image_tag}" --format '{{json .Config.Entrypoint}}' 2>/dev/null || true)"
echo "[opmanager-start] Loaded image: ${image_tag}, arch=${image_arch:-unknown}, entrypoint=${image_entrypoint:-unknown}"

if [[ "${image_arch}" != "arm64" && "${image_arch}" != "aarch64" ]]; then
  echo "[opmanager-start] WARN: expected an arm64 image for Kunpeng/Kylin, got '${image_arch:-unknown}'." >&2
fi

if docker run --rm --entrypoint /bin/sh "${image_tag}" -c 'test -x /opt/java/openjdk/bin/java && test -f /app/dts-opmanager/dts-opmanager.jar' >/dev/null 2>&1; then
  echo "[opmanager-start] Image layout check passed."
else
  echo "[opmanager-start] WARN: shell-based image layout check failed; continuing because Docker image metadata and compose entrypoint are explicit." >&2
  echo "[opmanager-start] WARN: If startup still fails, run: docker image inspect ${image_tag} --format '{{.Architecture}} {{json .Config.Entrypoint}}'" >&2
fi

docker rm -f dts-opmanager >/dev/null 2>&1 || true

if command -v docker-compose >/dev/null 2>&1; then
  docker-compose -f docker-compose.yml --env-file .env up -d --force-recreate --remove-orphans
else
  docker compose -f docker-compose.yml --env-file .env up -d --force-recreate --remove-orphans
fi

echo "[opmanager-start] DTS OpManager started."
echo "[opmanager-start] Put DTS upgrade packages under ${OPMANAGER_HOME}/packages and extract them there."
START_SCRIPT
  chmod +x "${target}"
}

write_package_readme() {
  local target="$1"
  cat > "${target}" <<'README'
# DTS OpManager Runtime Package

## Install

```bash
mkdir -p /opt/dts-opmanager
tar -xzf dts-opmanager-runtime-*.tar.gz -C /opt/dts-opmanager
cd /opt/dts-opmanager/deploy
./start.sh
```

## Add DTS Upgrade Package

```bash
cp dts-opmanager-upgrade-*.tar.gz /opt/dts-opmanager/packages/
tar -xzf /opt/dts-opmanager/packages/dts-opmanager-upgrade-*.tar.gz -C /opt/dts-opmanager/packages
```

Open `http://<server-ip>:18095/`, set the existing DTS stack directory, then scan the default package directory.
README
}

create_runtime_package() {
  local output
  output="$(package_output_path)"
  local tmp_dir
  tmp_dir="$(mktemp -d)"
  local package_root="${tmp_dir}/runtime"
  local image_tar="${package_root}/deploy/$(image_tar_name)"

  mkdir -p "${package_root}/deploy" "${package_root}/data" "${package_root}/packages"
  cp "${SCRIPT_DIR}/deploy/docker-compose.yml" "${package_root}/deploy/docker-compose.yml"
  write_package_env_example "${package_root}/deploy/env.example"
  write_start_script "${package_root}/deploy/start.sh"
  write_package_readme "${package_root}/README.md"

  docker save "${IMAGE_TAG}" -o "${image_tar}"
  tar -czf "${output}" -C "${package_root}" "deploy" "data" "packages" "README.md"
  rm -rf "${tmp_dir}"
  echo "[opmanager-build] Runtime package ready: ${output}"
}

require_cmd docker
echo "[opmanager-build] Script version: ${SCRIPT_VERSION}"
build_webapp
build_backend_jar
build_runtime_image
create_runtime_package
