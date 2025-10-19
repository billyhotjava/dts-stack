# ---------- build stage ----------
# Back to JDK 17 to match downgraded pom <java.version>17>
ARG BASE_IMAGE=openjdk:17-slim
FROM ${BASE_IMAGE} AS build

ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get update && \
    apt-get install -y --no-install-recommends maven ca-certificates && \
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
    mvn -s /root/.m2/settings.xml -q -DskipTests -f "$BUILD_POM" -pl dts-admin -am package

# ---------- runtime stage ----------
# Use JRE 17 to run the compiled jar
ARG BASE_IMAGE=openjdk:17-slim
FROM ${BASE_IMAGE} AS runtime
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
