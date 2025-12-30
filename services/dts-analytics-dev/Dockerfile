FROM node:22-bullseye

RUN set -eux; \
  apt-get update; \
  apt-get install -y --no-install-recommends wget apt-transport-https gpg curl git ca-certificates bash; \
  rm -rf /var/lib/apt/lists/*

# Install Temurin JDK 21
RUN set -eux; \
  wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor > /etc/apt/trusted.gpg.d/adoptium.gpg; \
  echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" > /etc/apt/sources.list.d/adoptium.list; \
  apt-get update; \
  apt-get install -y --no-install-recommends temurin-21-jdk; \
  rm -rf /var/lib/apt/lists/*

# Install Clojure CLI
RUN set -eux; \
  curl -fsSL -o /tmp/linux-install-clojure.sh https://download.clojure.org/install/linux-install-1.12.0.1488.sh; \
  chmod +x /tmp/linux-install-clojure.sh; \
  /tmp/linux-install-clojure.sh; \
  rm -f /tmp/linux-install-clojure.sh

# Ensure Yarn v1 (Metabase requires yarn@1.x)
RUN set -eux; \
  corepack enable; \
  corepack prepare yarn@1.22.22 --activate

WORKDIR /workspace

