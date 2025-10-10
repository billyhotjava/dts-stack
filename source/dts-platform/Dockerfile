# syntax=docker/dockerfile:1

# ---------- build stage ----------
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-17
FROM ${MAVEN_IMAGE} AS build
ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
WORKDIR /workspace
COPY . .
RUN set -eu; \
    if [ ! -d dts-platform ]; then \
        if [ -d source/dts-platform ]; then \
            ln -s source/dts-platform dts-platform; \
        else \
            echo >&2 "dts-platform module not found in build context"; \
            exit 1; \
        fi; \
    fi; \
    if [ ! -d dts-common ]; then \
        echo "dts-common module not found; injecting lightweight fallback copy for docker build"; \
        if [ -d src/main/docker/dts-common-fallback ]; then \
            cp -r src/main/docker/dts-common-fallback dts-common; \
        elif [ -d dts-platform/src/main/docker/dts-common-fallback ]; then \
            cp -r dts-platform/src/main/docker/dts-common-fallback dts-common; \
        else \
            echo >&2 "Fallback module directory src/main/docker/dts-common-fallback not found"; \
            exit 1; \
        fi; \
    fi; \
    if [ -f src/main/docker/build-parent.pom ]; then \
        cp src/main/docker/build-parent.pom docker-build-pom.xml; \
    elif [ -f dts-platform/src/main/docker/build-parent.pom ]; then \
        cp dts-platform/src/main/docker/build-parent.pom docker-build-pom.xml; \
    else \
        echo >&2 "build-parent.pom not found in src/main/docker"; \
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
    sed -i "s|__MIRROR_URL__|${MAVEN_MIRROR_URL}|g" /root/.m2/settings.xml && \
    mvn -s /root/.m2/settings.xml -q -DskipTests -f docker-build-pom.xml -pl dts-platform -am package

# ---------- runtime stage ----------
FROM build AS runtime
ENV TZ=UTC \
    LANG=C.UTF-8 \
    JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom -XX:MaxRAMPercentage=75.0"
WORKDIR /app
COPY --from=build /workspace/dts-platform/target/dts-platform-*.jar /app/app.jar
RUN rm -rf /root/.m2 /workspace
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
