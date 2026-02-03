# Multi-stage build for Addax ETL engine.
# No platform-specific directives — builds natively on ARM64 or AMD64.

# ── Build stage ──────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-17 AS builder

ARG ADDAX_VERSION=6.0.8
ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

# Configure Maven mirror for faster downloads in China
RUN mkdir -p /root/.m2 && \
    printf '%s\n' \
      '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"' \
      '  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' \
      '  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">' \
      '  <mirrors>' \
      '    <mirror>' \
      '      <id>aliyun</id>' \
      '      <mirrorOf>*</mirrorOf>' \
      "      <url>${MAVEN_MIRROR_URL}</url>" \
      '    </mirror>' \
      '  </mirrors>' \
      '</settings>' \
      > /root/.m2/settings.xml

WORKDIR /build

# Clone Addax source
RUN git clone --depth 1 --branch ${ADDAX_VERSION} https://github.com/wgzhao/addax.git .

# Build: first compile, then create distribution package
RUN mvn -B -e -DskipTests clean package \
 && mvn -B -e -DskipTests package -Pdistribution

# Shrink: deduplicate JARs across plugins, then tar to preserve symlinks
COPY shrink_package.sh /build/shrink_package.sh
RUN chmod +x /build/shrink_package.sh \
 && ADDAX_DIR=$(ls -1d /build/target/addax/addax-* 2>/dev/null | head -n 1) \
 && bash /build/shrink_package.sh "$ADDAX_DIR" \
 && tar cf /tmp/addax.tar -C "$(dirname "$ADDAX_DIR")" "$(basename "$ADDAX_DIR")"

# ── Runtime stage ────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="DTS Build"

RUN apt-get update \
 && apt-get install -y --no-install-recommends procps \
 && rm -rf /var/lib/apt/lists/*

# Use tar to preserve symlinks from the shrink step
COPY --from=builder /tmp/addax.tar /tmp/addax.tar
RUN tar xf /tmp/addax.tar -C /opt/ \
 && mv /opt/addax-* /opt/addax \
 && rm /tmp/addax.tar \
 && chmod +x /opt/addax/bin/*.sh

# Jobs and drivers are mounted at runtime via Docker volumes
VOLUME ["/opt/addax/jobs"]

WORKDIR /opt/addax

ENTRYPOINT ["sh", "-lc"]
CMD ["/opt/addax/bin/addax.sh"]
