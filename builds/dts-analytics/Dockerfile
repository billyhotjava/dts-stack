# syntax=docker/dockerfile:1
#
# Online build image for dts-analytics (mirrors the general approach used by other modules):
# - Build inside Docker with Maven
# - Produce a runnable runtime image with the built Spring Boot fat jar
#
# Recommended build context: repository root
#   docker build -t dts-analytics:TAG -f builds/dts-analytics/Dockerfile .
#

ARG BUILD_IMAGE_MAVEN=maven:3.9.9-eclipse-temurin-21
ARG RUNTIME_BASE_IMAGE=eclipse-temurin:21-jre

FROM ${BUILD_IMAGE_MAVEN} AS build

WORKDIR /workspace
COPY . .

# Ensure we were built with the correct context so module paths resolve.
RUN set -eux; \
    if [ -f /workspace/pom.xml ] && [ -d /workspace/dts-analytics ]; then \
      echo "/workspace/pom.xml" > /workspace/.build-pom; \
    elif [ -f /workspace/source/pom.xml ] && [ -d /workspace/source/dts-analytics ]; then \
      if [ ! -e /workspace/dts-analytics ]; then \
        ln -s source/dts-analytics /workspace/dts-analytics; \
      fi; \
      echo "/workspace/source/pom.xml" > /workspace/.build-pom; \
    else \
      echo >&2 "Missing source modules. Run build with repository root as context, e.g.:"; \
      echo >&2 "  docker build -f builds/dts-analytics/Dockerfile ."; \
      exit 1; \
    fi; \
    test -f /workspace/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/filter/RequestIdFilter.java; \
    test -f /workspace/dts-analytics/src/main/java/com/yuzhi/dts/analytics/domain/AnalyticsUser.java; \
    test -f /workspace/dts-analytics/src/main/java/com/yuzhi/dts/analytics/repository/AnalyticsUserRepository.java

ARG MAVEN_ARGS="-B -e -DskipTests"
RUN set -eux; \
    BUILD_POM=$(cat /workspace/.build-pom); \
    mvn ${MAVEN_ARGS} -f "${BUILD_POM}" -pl dts-analytics -am package

RUN set -eux; \
    mkdir -p /workspace/dist; \
    JAR="$(find /workspace/dts-analytics/target -maxdepth 1 -type f -name '*.jar' ! -name '*.original' | head -n 1)"; \
    test -n "${JAR:-}"; \
    cp "${JAR}" /workspace/dist/app.jar

FROM ${RUNTIME_BASE_IMAGE} AS runtime

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom -XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Shanghai"

WORKDIR /app
COPY --from=build /workspace/dist/app.jar /app/app.jar

EXPOSE 3000
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
