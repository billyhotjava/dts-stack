FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY source/dts-analytics/pom.xml source/dts-analytics/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -q -f source/dts-analytics/pom.xml -DskipTests dependency:go-offline

COPY source/dts-analytics source/dts-analytics
RUN --mount=type=cache,target=/root/.m2 mvn -f source/dts-analytics/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/source/dts-analytics/target/dts-analytics-*.jar /app/dts-analytics.jar

EXPOSE 3000

ENV JAVA_TOOL_OPTIONS=""
ENTRYPOINT ["sh", "-lc", "exec java $JAVA_TOOL_OPTIONS -jar /app/dts-analytics.jar"]

