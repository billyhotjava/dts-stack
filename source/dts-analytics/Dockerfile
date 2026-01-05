# syntax=docker/dockerfile:1.6
#
# Online build image for dts-analytics (mirrors the general approach used by other modules):
# - Build inside Docker with Maven
# - Produce a runnable runtime image with the built Spring Boot fat jar
#
# Recommended build context: `source/`
#   docker build -t dts-analytics:TAG -f source/dts-analytics/Dockerfile source
#

ARG BUILD_IMAGE_MAVEN=maven:3.9.9-eclipse-temurin-21
ARG RUNTIME_BASE_IMAGE=eclipse-temurin:21-jre

FROM ${BUILD_IMAGE_MAVEN} AS build

WORKDIR /workspace
COPY . .

# Ensure we were built with the correct context (`source/`) so module paths resolve.
RUN set -eux; \
    test -f /workspace/pom.xml; \
    test -d /workspace/dts-analytics; \
    test -f /workspace/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/filter/RequestIdFilter.java; \
    test -f /workspace/dts-analytics/src/main/java/com/yuzhi/dts/analytics/domain/AnalyticsUser.java; \
    test -f /workspace/dts-analytics/src/main/java/com/yuzhi/dts/analytics/repository/AnalyticsUserRepository.java

ARG MAVEN_ARGS="-B -e -DskipTests"
RUN set -eux; \
    mvn ${MAVEN_ARGS} -f /workspace/pom.xml -pl dts-analytics -am package

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

