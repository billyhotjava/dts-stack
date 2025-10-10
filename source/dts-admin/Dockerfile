# syntax=docker/dockerfile:1

# ---------- build stage ----------
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-17
FROM ${MAVEN_IMAGE} AS build
ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
WORKDIR /workspace
COPY . .
RUN set -eu; \
    if [ -f pom.xml ] && [ -d dts-admin ] && [ -d dts-common ]; then \
        echo "pom.xml" > /workspace/.build-pom; \
    elif [ -f source/pom.xml ] && [ -d source/dts-admin ] && [ -d source/dts-common ]; then \
        if [ ! -e dts-admin ]; then \
            ln -s source/dts-admin dts-admin; \
        fi; \
        echo "source/pom.xml" > /workspace/.build-pom; \
    else \
        echo >&2 "Missing source modules. Run build with repository root or 'source' as context, e.g.:"; \
        echo >&2 "  docker build -f source/dts-admin/Dockerfile source"; \
        exit 1; \
    fi
RUN --mount=type=cache,target=/root/.m2 \
    mkdir -p /root/.m2 && \
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
RUN --mount=type=cache,target=/root/.m2 \
    BUILD_POM=$(cat /workspace/.build-pom); \
    sed -i "s|__MIRROR_URL__|${MAVEN_MIRROR_URL}|g" /root/.m2/settings.xml && \
    mvn -s /root/.m2/settings.xml -q -DskipTests -f "${BUILD_POM}" -pl dts-admin -am package

# ---------- runtime stage ----------
FROM build AS runtime
ENV TZ=UTC \
    LANG=C.UTF-8 \
    JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom -XX:MaxRAMPercentage=75.0"
WORKDIR /app
COPY --from=build /workspace/dts-admin/target/dts-admin-*.jar /app/app.jar
RUN rm -rf /root/.m2 /workspace
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
