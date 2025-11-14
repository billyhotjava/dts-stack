ARG RUNTIME_BASE_IMAGE=eclipse-temurin:21-jre
# ---------- build stage ----------
# Build against Maven 3.9.9 with Temurin JDK 21
# Use official Maven with Temurin JDK 21 for cross-platform builds
# syntax=docker/dockerfile:1.6
FROM maven:3.9.9-eclipse-temurin-21 AS build

# Embed Ubuntu archive signing key so apt works on older ARM mirrors;
# use a dedicated keyring path and repoint the Ubuntu sources to it.
COPY apt-keys/ubuntu-archive-2021.gpg /tmp/ubuntu-archive-2021.gpg
ARG UBUNTU_PORTS_MIRROR=https://mirrors.aliyun.com/ubuntu-ports
ARG UBUNTU_ARCHIVE_MIRROR=https://mirrors.aliyun.com/ubuntu
RUN set -eux; \
    install -d /usr/share/keyrings /etc/apt/trusted.gpg.d; \
    gpg --dearmor --batch --yes --output /usr/share/keyrings/ubuntu-archive-2021.gpg /tmp/ubuntu-archive-2021.gpg; \
    install -m 0644 /usr/share/keyrings/ubuntu-archive-2021.gpg /etc/apt/trusted.gpg.d/ubuntu-archive-2021.gpg; \
    ARCH=$(dpkg --print-architecture); \
    case "$ARCH" in \
      arm*|aarch64) MIRROR="${UBUNTU_PORTS_MIRROR}";; \
      *) MIRROR="${UBUNTU_ARCHIVE_MIRROR}";; \
    esac; \
    MIRROR="${MIRROR%/}"; \
    printf '%s\n' \
      "Types: deb" \
      "URIs: ${MIRROR}/" \
      "Suites: noble noble-updates noble-backports" \
      "Components: main universe restricted multiverse" \
      "Trusted: yes" \
      "" \
      "Types: deb" \
      "URIs: ${MIRROR}/" \
      "Suites: noble-security" \
      "Components: main universe restricted multiverse" \
      "Trusted: yes" \
      > /etc/apt/sources.list.d/ubuntu.sources; \
    sha256sum /usr/share/keyrings/ubuntu-archive-2021.gpg; \
    gpg --show-keys /usr/share/keyrings/ubuntu-archive-2021.gpg; \
    cat /etc/apt/sources.list.d/ubuntu.sources
RUN printf '%s\n' \
      'APT::Get::AllowUnauthenticated "true";' \
      'Acquire::AllowInsecureRepositories "true";' \
      'Acquire::AllowDowngradeToInsecureRepositories "true";' \
      'Acquire::https::Verify-Peer "false";' \
      'Acquire::https::Verify-Host "false";' \
      > /etc/apt/apt.conf.d/99allow-insecure
ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
ARG DEBIAN_FRONTEND=noninteractive
ARG ENABLE_MAVEN_BUILD=false

RUN apt-get -o Acquire::AllowInsecureRepositories=true -o Acquire::AllowDowngradeToInsecureRepositories=true update || true && \
    apt-get install -y --no-install-recommends --allow-unauthenticated ca-certificates && \
    rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY . .
RUN set -eu; \
    if [ -f pom.xml ] && [ -d dts-admin ] && [ -d dts-common ]; then \
        echo "pom.xml" > /workspace/.build-pom; \
    elif [ -f source/pom.xml ] && [ -d source/dts-admin ] && [ -d source/dts-common ]; then \
        for module in dts-common dts-platform dts-admin; do \
            if [ -d "source/${module}" ] && [ ! -e "${module}" ]; then \
                ln -s "source/${module}" "${module}"; \
            fi; \
        done; \
        echo "source/pom.xml" > /workspace/.build-pom; \
    else \
        echo >&2 "Missing source modules. Run build with repository root or 'source' as context, e.g.:"; \
        echo >&2 "  docker build -f source/dts-admin/Dockerfile source"; \
        exit 1; \
    fi
RUN mkdir -p /root/.m2 && \
    printf '%s\n' \
      '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">' \
      '  <mirrors>' \
      '    <mirror>' \
      '      <id>mirror-default</id>' \
      '      <mirrorOf>*</mirrorOf>' \
      '      <url>__MIRROR_URL__</url>' \
      '    </mirror>' \
      '  </mirrors>' \
      '</settings>' \
      > /root/.m2/settings.xml
RUN set -eux; \
    BUILD_POM=$(cat /workspace/.build-pom); \
    PREBUILT_JAR="/workspace/builds/dts-admin.jar"; \
    USE_MAVEN="${ENABLE_MAVEN_BUILD}"; \
    if [ ! -f "${PREBUILT_JAR}" ]; then \
      echo "[build] Prebuilt jar missing at ${PREBUILT_JAR}; forcing Maven build"; \
      USE_MAVEN="true"; \
    fi; \
    if [ "${USE_MAVEN}" = "true" ]; then \
      echo "[build] Building dts-admin via Maven"; \
      if [ -x "${JAVA_HOME:-/opt/java/openjdk}/bin/java" ]; then \
        export JAVA_HOME="${JAVA_HOME:-/opt/java/openjdk}"; \
        echo "[build] Reusing preset JAVA_HOME=${JAVA_HOME}"; \
      elif command -v javac >/dev/null 2>&1; then \
        JAVA_BIN=$(readlink -f "$(command -v javac)"); \
        export JAVA_HOME="${JAVA_BIN%/bin/javac}"; \
        echo "[build] Derived JAVA_HOME from javac: ${JAVA_HOME}"; \
      else \
        JAVA_BIN=$(readlink -f "$(command -v java)"); \
        export JAVA_HOME="${JAVA_BIN%/bin/java}"; \
        echo "[build] Derived JAVA_HOME from java: ${JAVA_HOME}"; \
      fi; \
      export PATH="${JAVA_HOME}/bin:${PATH}"; \
      sed -i "s|__MIRROR_URL__|${MAVEN_MIRROR_URL}|g" /root/.m2/settings.xml; \
      mvn -s /root/.m2/settings.xml -q -DskipTests -f "$BUILD_POM" -pl dts-admin -am package; \
    else \
      echo "[build] Using prebuilt jar from ${PREBUILT_JAR}"; \
      mkdir -p /workspace/dts-admin/target; \
      cp "${PREBUILT_JAR}" /workspace/dts-admin/target/dts-admin-prebuilt.jar; \
    fi

# Normalize artifact name to a deterministic jar path for the runtime stage
RUN set -eux; \
    mkdir -p /workspace/dist; \
    if [ -f /workspace/dts-admin/target/dts-admin-prebuilt.jar ]; then \
      cp /workspace/dts-admin/target/dts-admin-prebuilt.jar /workspace/dist/app.jar; \
    else \
      JAR=$(find /workspace/dts-admin/target -maxdepth 1 -type f -name '*.jar' ! -name '*.original' | head -n 1); \
      test -n "${JAR:-}"; \
      cp "$JAR" /workspace/dist/app.jar; \
    fi

# ---------- runtime stage ----------
# Use Temurin JRE 21 to run the compiled jar
FROM ${RUNTIME_BASE_IMAGE} AS runtime
ARG UBUNTU_PORTS_MIRROR=https://mirrors.aliyun.com/ubuntu-ports
ARG UBUNTU_ARCHIVE_MIRROR=https://mirrors.aliyun.com/ubuntu
COPY --from=build /usr/share/keyrings/ubuntu-archive-2021.gpg /usr/share/keyrings/ubuntu-archive-2021.gpg
COPY --from=build /etc/apt/trusted.gpg.d/ubuntu-archive-2021.gpg /etc/apt/trusted.gpg.d/ubuntu-archive-2021.gpg
RUN set -eux; \
    ARCH=$(dpkg --print-architecture); \
    case "$ARCH" in \
      arm*|aarch64) MIRROR="${UBUNTU_PORTS_MIRROR}";; \
      *) MIRROR="${UBUNTU_ARCHIVE_MIRROR}";; \
    esac; \
    MIRROR="${MIRROR%/}"; \
    printf '%s\n' \
      "Types: deb" \
      "URIs: ${MIRROR}/" \
      "Suites: noble noble-updates noble-backports" \
      "Components: main universe restricted multiverse" \
      "Trusted: yes" \
      "" \
      "Types: deb" \
      "URIs: ${MIRROR}/" \
      "Suites: noble-security" \
      "Components: main universe restricted multiverse" \
      "Trusted: yes" \
      > /etc/apt/sources.list.d/ubuntu.sources
RUN printf '%s\n' \
      'APT::Get::AllowUnauthenticated "true";' \
      'Acquire::AllowInsecureRepositories "true";' \
      'Acquire::AllowDowngradeToInsecureRepositories "true";' \
      'Acquire::https::Verify-Peer "false";' \
      'Acquire::https::Verify-Host "false";' \
      > /etc/apt/apt.conf.d/99allow-insecure
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    DEBIAN_FRONTEND=noninteractive \
    JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom -XX:MaxRAMPercentage=75.0 --add-exports=java.base/sun.security.x509=ALL-UNNAMED --add-exports=java.base/sun.security.util=ALL-UNNAMED --add-opens=java.base/sun.security.x509=ALL-UNNAMED --add-opens=java.base/sun.security.util=ALL-UNNAMED"
RUN set -eux; \
    apt-get -o Acquire::AllowInsecureRepositories=true -o Acquire::AllowDowngradeToInsecureRepositories=true update || true; \
    apt-get install -y --no-install-recommends tzdata ca-certificates; \
    cp /usr/share/zoneinfo/$TZ /etc/localtime; \
    echo $TZ > /etc/timezone; \
    rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/dist/app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
