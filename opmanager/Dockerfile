# syntax=docker/dockerfile:1.6

FROM docker:27-cli AS docker-cli

FROM eclipse-temurin:21-jre
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
WORKDIR /app/dts-opmanager

COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker-cli /usr/local/libexec/docker/cli-plugins/docker-compose /usr/local/lib/docker/cli-plugins/docker-compose
COPY target/dts-opmanager-*.jar /app/dts-opmanager/dts-opmanager.jar

RUN mkdir -p /opt/dts-opmanager/data /opt/dts-opmanager/packages

ENV OPMANAGER_DATA_DIR=/opt/dts-opmanager/data
ENV OPMANAGER_PACKAGE_ROOTS=/opt/dts-opmanager/packages
ENV OPMANAGER_TARGET_STACK_DIR=/opt/dts-stack
ENV OPMANAGER_DOCKER_ENABLED=true
ENV OPMANAGER_SERVER_PORT=18090

EXPOSE 18090

ENTRYPOINT ["/opt/java/openjdk/bin/java", "-jar", "/app/dts-opmanager/dts-opmanager.jar"]
