# ---------- build stage ----------
# Build against Maven 3.9.9 with Temurin JDK 21
ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
# Eclipse Temurin stopped publishing the floating `21-jre` tag; pin to the latest LTS patch to avoid auth errors.
ARG RUNTIME_BASE_IMAGE=eclipse-temurin:21.0.5_11-jre
FROM ${MAVEN_IMAGE} AS build

# Embed Ubuntu archive signing key so apt works on older ARM mirrors
RUN set -eux; \
    install -d /usr/share/keyrings /etc/apt/trusted.gpg.d; \
    printf '%s' \
      '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' \
    | base64 -d >/usr/share/keyrings/ubuntu-archive-keyring.gpg; \
    chmod 0644 /usr/share/keyrings/ubuntu-archive-keyring.gpg; \
    cp /usr/share/keyrings/ubuntu-archive-keyring.gpg /etc/apt/trusted.gpg.d/ubuntu-archive-2021.gpg; \
    apt-key add /usr/share/keyrings/ubuntu-archive-keyring.gpg >/dev/null
ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates && \
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
      '</settings>' > /root/.m2/settings.xml
RUN BUILD_POM=$(cat /workspace/.build-pom) && \
    sed -i "s|__MIRROR_URL__|${MAVEN_MIRROR_URL}|g" /root/.m2/settings.xml && \
    mvn -s /root/.m2/settings.xml -q -DskipTests -f "$BUILD_POM" -pl dts-admin -am package

# ---------- runtime stage ----------
# Use Temurin JRE 21 to run the compiled jar
FROM ${RUNTIME_BASE_IMAGE} AS runtime
COPY --from=build /usr/share/keyrings/ubuntu-archive-keyring.gpg /usr/share/keyrings/ubuntu-archive-keyring.gpg
COPY --from=build /etc/apt/trusted.gpg.d/ubuntu-archive-2021.gpg /etc/apt/trusted.gpg.d/ubuntu-archive-2021.gpg
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom -XX:MaxRAMPercentage=75.0 --add-exports=java.base/sun.security.x509=ALL-UNNAMED --add-exports=java.base/sun.security.util=ALL-UNNAMED --add-opens=java.base/sun.security.x509=ALL-UNNAMED --add-opens=java.base/sun.security.util=ALL-UNNAMED"
RUN apt-get update && \
    apt-get install -y --no-install-recommends tzdata && \
    ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone && \
    apt-get purge -y --auto-remove tzdata && \
    rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/dts-admin/target/dts-admin-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
