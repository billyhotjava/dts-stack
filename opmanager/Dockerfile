# syntax=docker/dockerfile:1.6

FROM node:20-alpine AS webapp
WORKDIR /workspace/src/main/webapp
COPY src/main/webapp/package.json ./
COPY src/main/webapp/pnpm-lock.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile
COPY src/main/webapp/ ./
RUN pnpm build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
COPY --from=webapp /workspace/src/main/resources/static ./src/main/resources/static
RUN mvn -q -DskipTests package

FROM docker:27-cli AS docker-cli

FROM eclipse-temurin:21-jre
WORKDIR /opt/dts-opmanager

COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker-cli /usr/local/libexec/docker/cli-plugins/docker-compose /usr/local/lib/docker/cli-plugins/docker-compose
COPY --from=backend /workspace/target/dts-opmanager-*.jar /opt/dts-opmanager/dts-opmanager.jar

RUN mkdir -p /var/lib/dts-opmanager/packages /var/lib/dts-opmanager/jobs /var/lib/dts-opmanager/logs

ENV OPMANAGER_DATA_DIR=/var/lib/dts-opmanager
ENV OPMANAGER_PACKAGE_ROOTS=/var/lib/dts-opmanager/packages
ENV OPMANAGER_TARGET_STACK_DIR=/opt/dts-stack
ENV OPMANAGER_DOCKER_ENABLED=true
ENV OPMANAGER_SERVER_PORT=18090

EXPOSE 18090

ENTRYPOINT ["java", "-jar", "/opt/dts-opmanager/dts-opmanager.jar"]
