# ---------- build stage ----------
# Build stage uses Maven 3.9.9 with Temurin JDK 21
ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
ARG RUNTIME_BASE_IMAGE=eclipse-temurin:21-jre
FROM ${MAVEN_IMAGE} AS build

ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates && \
    rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY . .
RUN set -eu; \
    if [ -f pom.xml ] && [ -d dts-platform ] && [ -d dts-common ]; then \
        echo "pom.xml" > /workspace/.build-pom; \
    elif [ -f source/pom.xml ] && [ -d source/dts-platform ] && [ -d source/dts-common ]; then \
        for module in dts-common dts-platform dts-admin; do \
            if [ -d "source/${module}" ] && [ ! -e "${module}" ]; then \
                ln -s "source/${module}" "${module}"; \
            fi; \
        done; \
        echo "source/pom.xml" > /workspace/.build-pom; \
    else \
        echo >&2 "Missing source modules. Run build with repository root or 'source' as context, e.g.:"; \
        echo >&2 "  docker build -f source/dts-platform/Dockerfile source"; \
        exit 1; \
    fi
RUN mkdir -p /root/.m2 && \
    cat > /root/.m2/settings.xml <<'EOF'
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>mirror-default</id>
      <mirrorOf>*</mirrorOf>
      <url>__MIRROR_URL__</url>
    </mirror>
  </mirrors>
</settings>
EOF
RUN BUILD_POM=$(cat /workspace/.build-pom) && \
    sed -i "s|__MIRROR_URL__|${MAVEN_MIRROR_URL}|g" /root/.m2/settings.xml && \
    mvn -s /root/.m2/settings.xml -q -DskipTests -f "$BUILD_POM" -pl dts-platform -am package

# ---------- runtime stage ----------
# Runtime stage uses Temurin JRE 21
FROM ${RUNTIME_BASE_IMAGE} AS runtime
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom -XX:MaxRAMPercentage=75.0"
RUN apt-get update && \
    apt-get install -y --no-install-recommends tzdata && \
    ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone && \
    apt-get purge -y --auto-remove tzdata && \
    rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/dts-platform/target/dts-platform-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
